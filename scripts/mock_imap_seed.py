#!/usr/bin/env python3
"""(Re)create the sample mailbox used by scripts/mock_imap_server.py.

Writes RFC822 notes in the Apple Notes wire format that ImapNotesX understands
(X-Uniform-Type-Identifier: com.apple.mail-note, text/html body, a
background-color:<note colour name> on <body>).  Dates are spread over the last
week so screenshots always look lived-in.

    python3 scripts/mock_imap_seed.py [--dir dev/mock_mailbox] [--days 7]
"""

import argparse
import datetime
import os
import uuid

NOTE_COLOURS = ["none", "yellow", "green", "blue", "red", "brown", "gray", "pink", "white"]

NOTES = [
    dict(
        subject="Welcome to ImapNotesX",
        colour="none",
        body=[
            '<div><b>Notes that live in your IMAP mailbox.</b></div>',
            '<div>&nbsp;</div>',
            '<div>Every note is a plain HTML e&#8209;mail, so Apple Notes, Thunderbird and '
            'ImapNotesX can all read them. Nothing is hidden in a proprietary database.</div>',
            '<div>&nbsp;</div>',
            '<div>Swipe a note to delete it, swipe an account to edit or remove it.</div>',
            '<div>#welcome #imap #notes</div>',
        ],
    ),
    dict(
        subject="Shopping list",
        colour="yellow",
        body=[
            '<div><b>Shopping list</b></div>',
            '<div>&nbsp;</div>',
            '<div><ul style="list-style-type: disc;"><li>Oat milk</li>'
            '<li>Speeltjes ( Rijst )</li><li>Boerenkool</li>'
            '<li>Hagelslag &#8211; both flavours</li><li>Print paper A4</li></ul></div>',
            '<div>&nbsp;</div>',
            '<div>#groceries</div>',
        ],
    ),
    dict(
        subject="Cycling route: Utrechtse Heuvelrug",
        colour="green",
        body=[
            '<div><b>Cycling route: Utrechtse Heuvelrug</b></div>',
            '<div>&nbsp;</div>',
            '<div>62&#8201;km, two long climbs, no wind forecast. Start at Driebergen&#8211;Rh '
            'einen, loop via Amerongen back.</div>',
            '<div>&nbsp;</div>',
            '<div>Carry: 2&#8201;L, patches, pump.</div>',
            '<div>#bike #weekend</div>',
        ],
    ),
    dict(
        subject="Sync design notes",
        colour="blue",
        body=[
            '<div><b>Sync design notes</b></div>',
            '<div>&nbsp;</div>',
            '<div><i>UIDValidity</i> changes mean the mailbox was rebuilt &#8211; everything has '
            'to be fetched again.</div>',
            '<div>Read&#8209;only folders are fine: notes are still downloaded, only uploads are '
            'refused.</div>',
            '<div>A refresh fetches UIDs and flags for the whole folder in <b>one</b> '
            'FetchProfile.</div>',
            '<div>#dev #sync</div>',
        ],
    ),
    dict(
        subject="Books to read",
        colour="pink",
        body=[
            '<div><b>Books to read</b></div>',
            '<div>&nbsp;</div>',
            '<div><ul style="list-style-type: square;"><li>De Generaal</li>'
            '<li>Structure and Interpretation of Computer Programs</li>'
            '<li>The Design of Everyday Things</li></ul></div>',
            '<div>#reading</div>',
        ],
    ),
    dict(
        subject="Fix the keyboard layout",
        colour="brown",
        body=[
            '<div><b>Fix the keyboard layout</b></div>',
            '<div>&nbsp;</div>',
            '<div>Coffee, then a quiet hour. Bring the Allen key.</div>',
            '<div>#todo</div>',
        ],
    ),
]


def message_for(note, when):
    uu = str(uuid.uuid4()).upper() + "-" + str(uuid.uuid4())[:12]
    # the app only writes a background-color when the note has a colour of its own
    style = "" if note["colour"] == "none" else ' style="background-color:%s;"' % note["colour"]
    # no <style> block: the app's list snippet is the text of the body, and a style
    # rule would show up in it
    html = ('<html><head><meta name="waid" content="%s"><meta name="sync-text" content="">'
            '</head><body%s>%s</body></html>' % (uu, style, "".join(note["body"])))
    headers = [
        "Date: " + when.strftime("%a, %d %b %Y %H:%M:%S +0100"),
        "From: =?utf-8?q?My_Notes?= <notes@example.com>",
        "To: =?utf-8?q?My_Notes?= <notes@example.com>",
        "Subject: =?utf-8?q?%s?=" % quote(note["subject"]),
        "X-Uniform-Type-Identifier: com.apple.mail-note",
        "X-Universally-Unique-Identifier: " + uu,
        "X-Mailer: ImapNotesX sample data",
        "Message-ID: <%s@example.com>" % uu,
        "MIME-Version: 1.0",
        'Content-Type: text/html; charset="utf-8"',
        "Content-Transfer-Encoding: 8bit",
    ]
    return ("\r\n".join(headers) + "\r\n\r\n" + html + "\r\n").encode("utf-8")


def quote(text):
    out = ""
    for char in text:
        if char == " ":
            out += "_"
        elif 0x21 <= ord(char) <= 0x7E and char not in "=_?":
            out += char
        else:
            for byte in char.encode("utf-8"):
                out += "=%02X" % byte
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--dir", default=os.path.join(os.path.dirname(__file__), os.pardir,
                                                     "dev", "mock_mailbox"))
    parser.add_argument("--days", type=int, default=6, help="spread the notes over N days")
    args = parser.parse_args()

    target = os.path.abspath(args.dir)
    os.makedirs(target, exist_ok=True)
    now = datetime.datetime.now().astimezone()
    step = args.days * 24 * 60 // max(len(NOTES), 1)
    for index, note in enumerate(NOTES):
        when = now - datetime.timedelta(minutes=step * (len(NOTES) - index) + 37)
        name = "%02d-%s.eml" % (index + 1, "".join(c for c in note["subject"].lower()
                                                   if c.isalnum() or c in " -").strip().replace(" ", "-")[:32])
        path = os.path.join(target, name)
        with open(path, "wb") as handle:
            handle.write(message_for(note, when))
        print("wrote %s  (%s, %s)" % (path, note["colour"], when.strftime("%Y-%m-%d %H:%M")))
    print("%d sample notes in %s - serve them with scripts/mock_imap_server.py" %
          (len(NOTES), target))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
