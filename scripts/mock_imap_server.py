#!/usr/bin/env python3
"""A tiny mock IMAP server for developing and screenshotting ImapNotesX.

It speaks just enough IMAP4rev1 (+ UIDPLUS) for the JavaMail client inside the
app: login, select, list, uid search/fetch/store, append, copy, expunge.  The
mailbox is seeded from a directory of .eml files, which is also where APPENDed
notes are written back to, so the same tree can be reused for the next run.

    python3 scripts/mock_imap_server.py                      # port 1143, user/pw "notes"
    python3 scripts/mock_imap_server.py --maildir dev/notes --user a --password b

The Android emulator reaches the host loopback as 10.0.2.2, so in the app use
server "10.0.2.2", port 1143 and security "None".  State is kept in memory and
mirrored to --state (JSON), which makes the server restartable without changing
UIDs (changing UIDs would make the app re-download everything).
"""

import argparse
import base64
import email
import email.policy
import getpass
import json
import os
import socketserver
import sys
import threading
import time
from email.utils import parsedate_to_datetime

CRLF = b"\r\n"


def imap_quoted(value):
    """A quoted string - what real servers put in an ENVELOPE.  JavaMail's ENVELOPE
    parser is picky, so avoid literals in there and escape instead."""
    if value is None:
        return b"NIL"
    raw = value.encode("utf-8") if isinstance(value, str) else value
    return b'"' + raw.replace(b"\\", b"\\\\").replace(b'"', b'\\"') + b'"'


def imap_str(value):
    """Quote a string the way an IMAP server has to: literals for anything odd."""
    if value is None:
        return b"NIL"
    raw = value.encode("utf-8") if isinstance(value, str) else value
    return b"{" + str(len(raw)).encode() + b"}" + CRLF + raw


class SeqSet:
    """An IMAP set like "1", "1:3", "1,4,7", "*".  Kept as ranges on purpose: "1:*"
    means the whole mailbox and must never be expanded."""

    def __init__(self, spec):
        self.ranges = []
        for part in str(spec).split(","):
            part = part.strip()
            if not part:
                continue
            if ":" in part:
                low, high = part.split(":", 1)
                self.ranges.append((low or "*", high or "*"))
            else:
                self.ranges.append((part, part))

    def contains(self, number, star_value):
        for low, high in self.ranges:
            a = star_value if low == "*" else int(low)
            b = star_value if high == "*" else int(high)
            if min(a, b) <= number <= max(a, b):
                return True
        return False

    def as_uids(self, box):
        """Materialise only the UIDs that really exist (UID STORE / UID EXPUNGE)."""
        star = box.messages[-1].uid if box.messages else 0
        return {m.uid for m in box.messages if self.contains(m.uid, star)}


def parse_set(spec):
    return SeqSet(spec)


class Message:
    """One stored note: RFC822 bytes plus its UID and flags."""

    def __init__(self, raw, uid=None, flags=None, internaldate=None):
        self.raw = raw
        self.uid = uid
        self.flags = set(flags or ["\\Seen"])
        self.parsed = email.message_from_bytes(raw, policy=email.policy.default)
        # an ENVELOPE has to carry the headers as they are on the wire (encoded words
        # included), so keep an unparsed copy for it
        self.wire = email.message_from_bytes(raw)
        if internaldate:
            self.internaldate = internaldate
        else:
            try:
                dt = parsedate_to_datetime(self.parsed.get("Date"))
                if dt is None:
                    raise ValueError
            except Exception:
                dt = datetime_now()
            self.internaldate = dt

    def body(self):
        payload = self.parsed.get_payload(decode=True)
        return payload if payload is not None else b""

    def headers(self):
        head, _, _ = self.raw.partition(b"\r\n\r\n")
        return head + CRLF + CRLF

    def header_fields(self, names):
        keep = [n.lower() for n in names]
        lines = []
        for name in keep:
            for value in self.parsed.get_all(name, []):
                lines.append(("%s: %s" % (name, value)).encode("utf-8"))
        return CRLF.join(lines) + (CRLF + CRLF if lines else b"")

    def section(self, spec):
        spec = spec.upper()
        if spec == "":
            return self.raw
        if spec == "HEADER":
            return self.headers()
        if spec.startswith("HEADER.FIELDS"):
            names = spec[spec.index("(") + 1: spec.index(")")].split()
            return self.header_fields(names)
        if spec.startswith("HEADER.FIELDS.NOT"):
            keep = [n for n in self.parsed.keys() if n.lower() not in
                    [x.lower() for x in spec[spec.index("(") + 1: spec.index(")")].split()]]
            return self.header_fields(keep)
        if spec == "TEXT":
            return self.body()
        if spec.replace(".", "").isdigit():
            return self.body()
        return b""


def datetime_now():
    from datetime import datetime
    return datetime.now().astimezone()


class Mailbox:
    def __init__(self, name, uidvalidity):
        self.name = name
        self.uidvalidity = uidvalidity
        self.messages = []
        self.next_uid = 1

    def add(self, raw, flags=None, internaldate=None):
        msg = Message(raw, uid=self.next_uid, flags=flags, internaldate=internaldate)
        self.next_uid += 1
        self.messages.append(msg)
        return msg

    def uidnext(self):
        return self.next_uid

    def by_uid(self, uid):
        for msg in self.messages:
            if msg.uid == uid:
                return msg
        return None

    def purge(self):
        gone = [m for m in self.messages if "\\Deleted" in m.flags]
        self.messages = [m for m in self.messages if "\\Deleted" not in m.flags]
        return gone


class Store:
    """All mailboxes; seeded from a directory of .eml files, mirrored to a JSON state file."""

    def __init__(self, maildir, state_path, separator="/"):
        self.separator = separator
        self.maildir = maildir
        self.state_path = state_path
        self.lock = threading.RLock()
        self.mailboxes = {}
        self.load()

    # -------------------------------------------------------------- persistence
    def load(self):
        if self.state_path and os.path.exists(self.state_path):
            with open(self.state_path, "r", encoding="utf-8") as handle:
                data = json.load(handle)
            for name, box in data.get("mailboxes", {}).items():
                mailbox = Mailbox(name, box["uidvalidity"])
                mailbox.next_uid = box["next_uid"]
                for entry in box["messages"]:
                    mailbox.messages.append(Message(
                        base64.b64decode(entry["raw"]), entry["uid"], entry["flags"],
                        entry.get("internaldate")))
                self.mailboxes[name] = mailbox
            return
        if self.maildir and os.path.isdir(self.maildir):
            names = sorted(os.listdir(self.maildir))
            eml = [n for n in names if n.lower().endswith(".eml")]
            box = self.get("Notes")
            for name in eml:
                with open(os.path.join(self.maildir, name), "rb") as handle:
                    raw = handle.read()
                if raw.startswith(b"From "):          # "From " mbox glue, not IMAP
                    raw = raw.split(CRLF, 1)[1]
                # the store always uses CRLF; the seed files may use LF or CRLF
                box.add(raw.replace(b"\r\n", b"\n").replace(b"\n", CRLF))
            self.save()

    def save(self):
        if not self.state_path:
            return
        data = {"mailboxes": {}}
        for name, box in self.mailboxes.items():
            data["mailboxes"][name] = {
                "uidvalidity": box.uidvalidity,
                "next_uid": box.next_uid,
                "messages": [{"uid": m.uid, "flags": sorted(m.flags),
                              "internaldate": m.internaldate.strftime("%d-%b-%Y %H:%M:%S %z"),
                              "raw": base64.b64encode(m.raw).decode()} for m in box.messages],
            }
        tmp = self.state_path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as handle:
            json.dump(data, handle, indent=1)
        os.replace(tmp, self.state_path)

    # ---------------------------------------------------------------- mailbox
    def get(self, name):
        name = name.strip('"')
        if name not in self.mailboxes:
            self.mailboxes[name] = Mailbox(name, 2026010101)
        return self.mailboxes[name]

    def names(self):
        return sorted(self.mailboxes)


class Session(threading.Thread):
    CAPABILITY = b"IMAP4rev1 UIDPLUS IDLE NAMESPACE UNSELECT QUOTA LITERAL+"

    def __init__(self, sock, addr, store, options):
        super().__init__(daemon=True)
        self.sock, self.addr, self.store, self.opts = sock, addr, store, options
        self.buffer = b""
        self.selected = None
        self.readonly = False
        self.user = None
        self.running = True

    # ---------------------------------------------------------------- plumbing
    def log(self, *args):
        if self.opts.verbose:
            print("[%s] %s" % (self.addr[0] + ":" + str(self.addr[1]), " ".join(str(a) for a in args)),
                  flush=True)

    def send(self, data):
        if isinstance(data, str):
            data = data.encode("utf-8")
        self.sock.sendall(data)

    def untagged(self, data):
        self.send(b"* " + (data if isinstance(data, bytes) else data.encode()) + CRLF)

    def done(self, tag, kind="OK", code=b"", text=""):
        # always spell out a reason phrase: strict clients (imaplib) do not accept
        # a bare "tag OK" completion line
        code = (" " + code.decode()) if code else ""
        self.send((tag + " " + kind + code + " " + (text or "completed")).encode() + CRLF)

    def read_line(self):
        global_end = None
        while CRLF not in self.buffer:
            chunk = self.sock.recv(65536)
            if not chunk:
                return None
            self.buffer += chunk
        line, self.buffer = self.buffer.split(CRLF, 1)
        return line.decode("utf-8", "replace")

    def read_bytes(self, count):
        while len(self.buffer) < count:
            chunk = self.sock.recv(65536)
            if not chunk:
                break
            self.buffer += chunk
        data, self.buffer = self.buffer[:count], self.buffer[count:]
        return data

    def run(self):
        self.untagged("OK ImapNotesX mock IMAP server ready")
        try:
            while self.running:
                line = self.read_line()
                if line is None:
                    break
                if not line.strip():
                    continue
                tag, rest = line.split(" ", 1)
                parts = rest.split(None, 1)
                command = parts[0].upper()
                args = parts[1] if len(parts) > 1 else ""
                self.log("<-", line)
                if command == "LOGOUT":
                    self.untagged("BYE bye")
                    self.done(tag)
                    break
                handler = getattr(self, "cmd_" + command.lower(), None)
                if handler is None:
                    self.done(tag, "BAD", text="unrecognized command " + command)
                    continue
                handler(tag, args)
        except (ConnectionResetError, BrokenPipeError, TimeoutError):
            pass
        except Exception as exc:                                   # keep the dev loop alive
            self.log("!! server error:", repr(exc))
            import traceback
            traceback.print_exc()
        finally:
            self.sock.close()
            self.log("-- closed")

    # ------------------------------------------------------------------ basics
    def cmd_capability(self, tag, args):
        self.untagged(b"CAPABILITY " + self.CAPABILITY)
        self.done(tag)

    def cmd_id(self, tag, args):
        self.untagged('ID (NIL NIL)')
        self.done(tag)

    def cmd_noop(self, tag, args):
        self.done(tag)

    def cmd_login(self, tag, args):
        user, password = split_two(args)
        if user == self.opts.user and password == self.opts.password:
            self.user = user
            self.done(tag, text="LOGIN completed")
        else:
            self.done(tag, "NO", text="[AUTHENTICATIONFAILED] invalid credentials")

    def cmd_authenticate(self, tag, args):
        self.done(tag, "NO", text="only LOGIN is supported")

    def cmd_starttls(self, tag, args):
        self.done(tag, "NO", text="plaintext only - choose security None")

    def cmd_namespace(self, tag, args):
        sep = '"' + self.store.separator + '"'
        self.untagged('NAMESPACE (("" %s)) NIL NIL' % sep)
        self.done(tag)

    def cmd_list(self, tag, args):
        _ref, pattern = split_two(args)
        wanted = (pattern or "*").strip('"')
        for name in self.store.names():
            if wanted != "*" and wanted != name:
                if not wanted.startswith(name) and not name.startswith(wanted.rstrip("%*")):
                    continue
            self.untagged('LIST (\\HasNoChildren) "%s" "%s"' % (self.store.separator, name))
        self.done(tag)

    def cmd_lsub(self, tag, args):
        self.cmd_list(tag, args)

    def cmd_create(self, tag, args):
        name = unquote(args)
        existed = name in self.store.names()
        self.store.get(name)
        self.store.save()
        self.done(tag, text=("already exists" if existed else "created"))

    def cmd_status(self, tag, args):
        name, items = arg_pair(args)
        box = self.store.get(name)
        out = []
        for item in items.strip("()").split():
            item = item.upper()
            if item == "MESSAGES":
                out.append("MESSAGES %d" % len(box.messages))
            elif item == "UNSEEN":
                out.append("UNSEEN %d" % len([m for m in box.messages if "\\Seen" not in m.flags]))
            elif item == "UIDNEXT":
                out.append("UIDNEXT %d" % box.uidnext())
            elif item == "UIDVALIDITY":
                out.append("UIDVALIDITY %d" % box.uidvalidity)
        self.untagged('STATUS "%s" (%s)' % (name, " ".join(out)))
        self.done(tag)

    def cmd_select(self, tag, args, readonly=False):
        name = unquote(args)
        box = self.store.get(name)
        self.selected = box
        self.readonly = readonly
        self.untagged("%d EXISTS" % len(box.messages))
        self.untagged("0 RECENT")
        self.untagged("OK [UIDVALIDITY %d]" % box.uidvalidity)
        self.untagged("OK [UIDNEXT %d]" % box.uidnext())
        self.untagged('FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft)')
        self.untagged('OK [PERMANENTFLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft \\*)]')
        self.untagged('OK [UNSEEN %d]' % len([m for m in box.messages if "\\Seen" not in m.flags]))
        self.done(tag, code=b"[READ-ONLY]" if readonly else b"[READ-WRITE]", text="Select completed")

    def cmd_examine(self, tag, args):
        self.cmd_select(tag, args, readonly=True)

    def cmd_close(self, tag, args):
        if self.selected:
            self.selected.purge()
            self.store.save()
        self.selected = None
        self.done(tag)

    def cmd_unselect(self, tag, args):
        self.selected = None
        self.done(tag)

    # ----------------------------------------------------------------- search
    def cmd_search(self, tag, args, by_uid=False):
        box = self.ensure(tag)
        if box is None:
            return
        found = []
        for index, msg in enumerate(box.messages, start=1):
            if self.matches(msg, index, args):
                found.append(msg.uid if by_uid else index)
        self.untagged("SEARCH " + " ".join(str(n) for n in found))
        self.done(tag)

    def cmd_uid(self, tag, args):
        sub, rest = args.split(None, 1) if len(args.split(None, 1)) > 1 else (args.upper(), "")
        sub = sub.upper()
        if sub == "SEARCH":
            self.cmd_search(tag, rest, by_uid=True)
        elif sub in ("FETCH", "STORE"):
            getattr(self, "cmd_" + sub.lower())(tag, rest, by_uid=True)
        elif sub in ("EXPUNGE",):
            self.cmd_expunge(tag, rest, by_uid=True)
        elif sub == "COPY":
            self.cmd_copy(tag, rest, by_uid=True)
        elif sub == "MOVE":
            self.cmd_move(tag, rest, by_uid=True)
        else:
            self.done(tag, "BAD", text="unsupported UID " + sub)

    def matches(self, msg, index, criteria):
        crit = criteria.strip()
        if not crit or crit.upper() == "ALL":
            return True
        tokens = crit.split()
        upper = [t.upper() for t in tokens]
        if "UNSEEN" in upper:
            if "\\Seen" in msg.flags:
                return False
        if "UNFLAGGED" in upper and "\\Flagged" in msg.flags:
            return False
        for pos, token in enumerate(upper):
            if token in ("UID", "SEQSET"):
                spec = tokens[pos + 1] if pos + 1 < len(tokens) else "1:*"
                return parse_set(spec).contains(msg.uid, self.star_uid())
            if token == "NOT" and pos + 1 < len(tokens):
                return not self.matches(msg, index, tokens[pos + 1])
            if token in ("TEXT", "SUBJECT", "BODY"):
                needle = tokens[pos + 1].strip('"').lower()
                return needle in msg.raw.decode("utf-8", "replace").lower()
        return True

    # ------------------------------------------------------------------ fetch
    def cmd_fetch(self, tag, args, by_uid=False):
        box = self.ensure(tag)
        if box is None:
            return
        setpart, items = split_paren(args)
        wanted = parse_items(items)
        requested = parse_set(setpart)
        star = self.star_uid() if by_uid else len(box.messages)
        hits = [(index, msg) for index, msg in enumerate(box.messages, start=1)
                if requested.contains(msg.uid if by_uid else index, star)]
        peek = any(i[0] == "BODY.PEEK" for i in wanted)
        mark_seen = not peek and any(i[0].startswith("BODY") for i in wanted)
        for index, msg in hits:
            parts = []
            for item, extra in wanted:
                if item == "UID":
                    parts.append("UID %d" % msg.uid)
                elif item == "FLAGS":
                    parts.append("FLAGS (" + " ".join(sorted(msg.flags)) + ")")
                elif item == "INTERNALDATE":
                    parts.append('INTERNALDATE "' +
                                 msg.internaldate.strftime("%d-%b-%Y %H:%M:%S %z") + '"')
                elif item == "RFC822.SIZE":
                    parts.append("RFC822.SIZE %d" % len(msg.raw))
                elif item == "ENVELOPE":
                    parts.append(b"ENVELOPE " + envelope(msg))
                elif item == "BODYSTRUCTURE":
                    parts.append(b"BODYSTRUCTURE " + bodystructure(msg))
                elif item.startswith("BODY"):
                    spec, rng = extra
                    data = msg.section(spec)
                    if rng:
                        start, count = rng
                        data = data[start:start + count] if count else data[start:]
                    if mark_seen:
                        msg.flags.add("\\Seen")
                    parts.append(("BODY%s " % ("[" + spec + "]" + fmt_range(rng) if rng else
                                               "[" + spec + "]")).encode() + imap_str(data.decode("latin-1")))
            joined = b" ".join(p if isinstance(p, bytes) else p.encode() for p in parts)
            self.untagged(("%d FETCH (" % index).encode() + joined + b")")
        self.done(tag, text="Fetch completed")

    def cmd_store(self, tag, args, by_uid=False):
        box = self.ensure(tag)
        if box is None:
            return
        setpart, rest = args.split(None, 1)
        silent = False
        if rest.startswith("."):
            silent, rest = True, rest[1:]
        action = "+FLAGS"
        if rest.upper().startswith("+FLAGS") or rest.upper().startswith("-FLAGS") \
                or rest.upper().startswith("FLAGS"):
            action, rest = rest.split(None, 1)[0], rest.split(None, 1)[1] if len(
                rest.split(None, 1)) > 1 else rest
        requested = parse_set(setpart)
        star = self.star_uid() if by_uid else len(box.messages)
        new_flags = set(f for f in rest.strip("()").split())
        add = action.startswith("+")
        remove = action.startswith("-")
        for index, msg in enumerate(box.messages, start=1):
            key = msg.uid if by_uid else index
            if not requested.contains(key, star):
                continue
            if remove:
                msg.flags -= new_flags
            elif add:
                msg.flags |= new_flags
            else:
                msg.flags = {f for f in msg.flags if f in ("\\Recent",)} | new_flags
            if not silent:
                self.untagged('%d FETCH (FLAGS (%s))' % (index, " ".join(sorted(msg.flags))))
        self.store.save()
        self.done(tag, text="Store completed")

    def cmd_expunge(self, tag, args="", by_uid=False):
        box = self.ensure(tag)
        if box is None:
            return
        uids = None
        if by_uid and args.strip():
            uids = parse_set(args).as_uids(box)
        for msg in list(box.messages):
            if "\\Deleted" in msg.flags and (uids is None or msg.uid in uids):
                index = box.messages.index(msg) + 1
                box.messages.remove(msg)
                self.untagged("%d EXPUNGE" % index)
        self.store.save()
        self.done(tag, text="Expunge completed")

    def cmd_append(self, tag, args):
        name, rest = arg_pair(args)
        box = self.store.get(name)
        flags, date, literal = parse_append_args(rest)
        size_text = rest[rest.rindex("{") + 1: rest.rindex("}")]
        literal_plus = size_text.endswith("+")            # LITERAL+: bytes follow right away
        size = int(size_text.rstrip("+"))
        if not literal_plus:
            self.send(b"+ " + CRLF)
        literal = self.read_bytes(size)
        box.add(literal, flags=flags)
        self.store.save()
        self.done(tag, code=("[APPENDUID %d %d]" % (box.uidvalidity, box.messages[-1].uid)).encode(),
                  text="Append completed")

    def cmd_copy(self, tag, args, by_uid=False):
        box = self.ensure(tag)
        if box is None:
            return
        setpart, dest = args.rsplit(None, 1)
        target = self.store.get(unquote(dest))
        requested = parse_set(setpart)
        star = self.star_uid() if by_uid else len(box.messages)
        for index, msg in enumerate(box.messages, start=1):
            key = msg.uid if by_uid else index
            if requested.contains(key, star):
                target.add(msg.raw, flags=set(msg.flags))
        self.store.save()
        self.done(tag, code=b"[COPYUID %d 0 0]" % target.uidvalidity, text="Copy completed")

    def cmd_move(self, tag, args, by_uid=False):
        self.cmd_copy(tag, args, by_uid)
        self.cmd_store(tag, args.split(None, 1)[0] + " +FLAGS (\\Deleted)", by_uid)
        self.cmd_expunge(tag, by_uid=by_uid)

    def cmd_check(self, tag, args):
        self.done(tag)

    def cmd_getquotaroot(self, tag, args):
        self.untagged('QUOTAROOT "%s" ""' % unquote(args))
        self.untagged("QUOTA "" (STORAGE 0 999999)")
        self.done(tag)

    def cmd_idle(self, tag, args):
        self.send(b"+ idling" + CRLF)
        deadline = time.time() + 60
        while time.time() < deadline:
            line = self.read_line()
            if line is None or line.strip().upper() == "DONE":
                break
        self.done(tag, text="IDLE completed")

    def star_uid(self):
        if self.selected is None or not self.selected.messages:
            return 0
        return self.selected.messages[-1].uid

    def ensure(self, tag):
        if self.selected is None:
            self.done(tag, "NO", text="no mailbox selected")
            return None
        return self.selected


# ------------------------------------------------------------------ helpers
def arg_pair(text):
    """(first argument, remainder) - the first one may be quoted."""
    first, rest = _first_arg(text)
    return first, rest


def unquote(text):
    """Take the first (possibly quoted) argument off a command line."""
    return _first_arg(text)[0]


def _first_arg(text):
    text = (text or "").strip()
    if not text:
        return "", ""
    if text.startswith('"'):
        end = text.index('"', 1)
        return text[1:end], text[end + 1:].strip()
    if text.startswith("("):
        end = text.index(")")
        return text[:end + 1], text[end + 1:].strip()
    parts = text.split(None, 1)
    return parts[0], (parts[1] if len(parts) > 1 else "")


def split_two(args):
    first, rest = arg_pair(args)
    second, _rest = arg_pair(rest)
    return first, second


def split_paren(args):
    """`1:* (UID FLAGS)` -> ("1:*", "(UID FLAGS)"); the set may itself be parenthesised."""
    args = args.strip()
    if args.startswith("("):
        end = args.index(")")
        first = args[1:end].strip()
        rest = args[end + 1:].strip()
        # rest looks like "UID FLAGS)" because the opener was the item list start
        return first, "(" + rest
    first, rest = args.split(None, 1)
    return first, rest.strip()


def parse_items(items):
    """`BODY.PEEK[]` / `BODY[HEADER.FIELDS (SUBJECT)]<0.100>` -> (kind, (spec, range))."""
    items = items.strip()
    if items.startswith("(") and items.endswith(")"):
        items = items[1:-1]
    out, pos = [], 0
    while pos < len(items):
        while pos < len(items) and items[pos] in " \t":
            pos += 1
        start = pos
        while pos < len(items) and items[pos] not in " \t[":
            pos += 1
        kind = items[start:pos].upper()
        if pos < len(items) and items[pos] == "[":
            depth, end = 1, pos + 1
            while end < len(items) and depth:
                if items[end] == "[":
                    depth += 1
                elif items[end] == "]":
                    depth -= 1
                end += 1
            spec = items[pos + 1:end - 1]
            pos = end
            rng = None
            if pos < len(items) and items[pos] == "<":
                close = items.index(">", pos)
                body = items[pos + 1:close]
                if "." in body:
                    a, b = body.split(".", 1)
                    rng = (int(a), int(b) if b else 0)
                pos = close + 1
            out.append((kind, (spec, rng)))
        else:
            out.append((kind, None))
    return out


def fmt_range(rng):
    """A partial fetch is answered with just the start offset: ``BODY[TEXT]<0>``."""
    return "<%d>" % rng[0] if rng else ""


def envelope(msg):
    def addrs(header):
        values = msg.wire.get_all(header, [])
        if not values:
            return b"NIL"
        out = b"("
        for value in values:
            for name, address in address_pairs(value):
                mailbox, _, host = address.partition("@")
                out += (b"(" + imap_quoted(name or None) + b" NIL " + imap_quoted(mailbox) +
                        b" " + imap_quoted(host or None) + b")")
        return out + b")"

    def head(header):
        value = msg.wire.get(header)
        return imap_quoted(str(value)) if value else b"NIL"

    parts = [
        imap_quoted(msg.wire.get("Date") or msg.internaldate.strftime("%a, %d %b %Y %H:%M:%S %z")),
        head("Subject"),
        addrs("From"),
        addrs("Sender") if msg.parsed.get("Sender") else addrs("From"),
        addrs("Reply-To"),
        addrs("To"),
        addrs("Cc"),
        addrs("Bcc"),
        # RFC 3501: exactly ten elements - date, subject, from, sender, reply-to,
        # to, cc, bcc, in-reply-to, message-id.  JavaMail's ENVELOPE parser counts
        # them and fails on anything else.
        head("In-Reply-To"),
        head("Message-Id"),
    ]
    return b"(" + b" ".join(parts) + b")"


def address_pairs(value):
    value = str(value)
    out = []
    for piece in value.split(","):
        piece = piece.strip()
        if not piece:
            continue
        if "<" in piece and piece.endswith(">"):
            name, _, addr = piece.partition("<")
            out.append((name.strip().strip('"'), addr[:-1].strip()))
        else:
            out.append(("", piece))
    return out


def bodystructure(msg):
    ctype = msg.parsed.get_content_type()
    maintype, subtype = ctype.split("/", 1)
    body = msg.body()
    lines = str(body.count(b"\n")).encode()
    return (b'("' + maintype.upper().encode() + b'" "' + subtype.upper().encode() +
            b'" NIL NIL NIL "8BIT" ' + str(len(body)).encode() + b" " + lines + b")")


def parse_append_args(rest):
    flags, date, literal = None, None, None
    rest = rest.strip()
    if rest.startswith("("):
        end = rest.index(")")
        flags = rest[1:end].split()
        rest = rest[end + 1:].strip()
    if rest.startswith('"'):
        end = rest.index('"', 1)
        date = rest[1:end]
        rest = rest[end + 1:].strip()
    if rest.endswith("}"):
        literal = None                     # caller asks first, then reads the literal
    return flags, date, literal


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--port", type=int, default=1143)
    parser.add_argument("--host", default="127.0.0.1",
                        help="127.0.0.1 is enough: the emulator reaches the host via 10.0.2.2")
    parser.add_argument("--user", default="notes")
    parser.add_argument("--password", default="notes")
    parser.add_argument("--maildir", default=os.path.join(os.path.dirname(__file__), "..",
                                                         "dev", "mock_mailbox"))
    parser.add_argument("--state", default="", help="JSON state file (default: in memory)")
    parser.add_argument("--quiet", action="store_true")
    options = parser.parse_args()

    store = Store(os.path.abspath(options.maildir), options.state)
    options.verbose = not options.quiet
    print("mock IMAP on %s:%d  user=%s password=%s  mailboxes=%s  messages=%s" % (
        options.host, options.port, options.user, options.password, store.names(),
        {n: len(b.messages) for n, b in store.mailboxes.items()}))

    class ClientHandler(socketserver.BaseRequestHandler):
        def handle(self):
            Session(self.request, self.client_address, store, options).run()

    try:
        Server((options.host, options.port), ClientHandler).serve_forever()
    except KeyboardInterrupt:
        print("\nstopped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
