#!/usr/bin/env python3
"""Take the F-Droid phone screenshots from a running emulator.

The app must already have an account that syncs from the mock IMAP server, e.g.

    python3 scripts/mock_imap_server.py --port 1143 --state /tmp/mock_state.json &
    python3 scripts/dev_setup_account.py --wipe --name "My Notes" --name "Work Notes"
    python3 scripts/dev_screenshots.py

Writes six images (light theme first, then dark) into
fastlane/metadata/android/en-US/images/phoneScreenshots/:
account overview, notes list, note editor.
"""
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dev_setup_account import (adb, dismiss_dialog, dump, find, go_home, nodes,
                          open_first_account, re, tap, wait_for)

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "fastlane", "metadata", "android", "en-US", "images", "phoneScreenshots")
NOTE_TO_OPEN = "Shopping list"        # the yellow note with the tick list
SETTLE = 3.0                          # let Snackbars fade and the WebView paint


def set_theme(night):
    adb("shell", "cmd", "uimode", "night", "yes" if night else "no")
    time.sleep(1.5)


def shot(name):
    out = subprocess.run(["adb", "-s", "emulator-5554", "exec-out", "screencap", "-p"],
                         capture_output=True)
    if not out.stdout.startswith(b"\x89PNG"):
        sys.exit("screencap failed for %s: %s" % (name, out.stderr.decode(errors="replace")))
    path = os.path.join(OUT, name)
    with open(path, "wb") as handle:
        handle.write(out.stdout)
    print("%-8s %6d bytes" % (name, len(out.stdout)))


def note_row(title):
    """Tap the notes-list row whose title is `title`."""
    for line in nodes(dump()):
        attrs = dict(re.findall(r'([\w:.-]+)="([^"]*)"', line))
        if attrs.get("text", "").strip() == title and \
                attrs.get("resource-id", "").endswith("/noteTitle"):
            left, top, right, bottom = (int(v) for v in
                                        re.findall(r"-?\d+", attrs["bounds"]))
            adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            return True
    return False


def capture(theme_index, night):
    set_theme(night)
    go_home()
    time.sleep(SETTLE)
    if not find("accountName")[1]:
        sys.exit("the account overview is empty - run dev_setup_account.py first")
    shot("%d.png" % (theme_index * 3 + 1))

    open_first_account()
    time.sleep(SETTLE)
    shot("%d.png" % (theme_index * 3 + 2))

    for attempt in range(3):
        if not note_row(NOTE_TO_OPEN):
            sys.exit("note %r not found in the list" % NOTE_TO_OPEN)
        if wait_for("bodyView", timeout=8):
            break
        adb("shell", "input", "keyevent", "KEYCODE_BACK")   # still on the list, try again
        time.sleep(1.5)
    else:
        sys.exit("the editor did not open")
    time.sleep(SETTLE)
    shot("%d.png" % (theme_index * 3 + 3))
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.0)


def main():
    os.makedirs(OUT, exist_ok=True)
    for index, night in enumerate((False, True)):
        capture(index, night)
    print("written to %s" % OUT)


if __name__ == "__main__":
    main()
