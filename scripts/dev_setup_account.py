#!/usr/bin/env python3
"""Create the mock-server account in the app through the UI (uiautomator).

Development helper: used to set up the emulator for screenshots.  Run with

    adb -s emulator-5554 shell pm clear nl.enimatek.imapnotesx
    python3 scripts/dev_setup_account.py
"""

import os
import re
import subprocess
import sys
import time

PKG = "nl.enimatek.imapnotesx"
ACTIVITY = "nl.enimatek.imapnotesx.AccountListActivity"
APK_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "ImapNotesX",
    "build",
    "outputs",
    "apk",
    "debug",
)
APK = os.path.join(APK_DIR, "ImapNotesX-debug.apk")
_matches = sorted(
    (f for f in os.listdir(APK_DIR) if f.endswith("-debug.apk")),
    key=lambda f: os.path.getmtime(os.path.join(APK_DIR, f)),
)
if _matches:  # newest debug build, versionName varies
    APK = os.path.join(APK_DIR, _matches[-1])
FIELDS = [  # (resource id, text to type)
    ("accountnameEdit", "My Notes"),
    ("usernameEdit", "notes"),
    ("passwordEdit", "notes"),
    ("serverEdit", "10.0.2.2"),
    ("securitySpinner", None),  # first entry = no encryption
    ("portnumEdit", "1143"),  # typed after security: it sets the port
]


def adb(*args, binary=False):
    cmd = ["adb", "-s", "emulator-5554"] + list(args)
    out = subprocess.run(cmd, capture_output=True)
    if out.returncode:
        sys.exit(
            "adb failed: %s\n%s" % (" ".join(cmd), out.stderr.decode(errors="replace"))
        )
    return out.stdout if binary else out.stdout.decode(errors="replace")


def dump():
    """A fresh uiautomator dump.  The dump fails while the screen animates, so retry."""
    path = "/sdcard/window_dump.xml"
    subprocess.run(
        ["adb", "-s", "emulator-5554", "shell", "rm", "-f", path], capture_output=True
    )
    for _ in range(8):
        out = subprocess.run(
            ["adb", "-s", "emulator-5554", "shell", "uiautomator", "dump", path],
            capture_output=True,
        )
        if b"dumped to" in out.stdout:
            break
        time.sleep(0.6)
    return adb("shell", "cat", path)


def nodes(xml):
    return re.findall(r"<node[^>]*>", xml)


def find(needle, xml=None):
    xml = xml if xml is not None else dump()
    for line in nodes(xml):
        attrs = dict(re.findall(r'([\w:.-]+)="([^"]*)"', line))
        hay = " ".join(
            [
                attrs.get("resource-id", ""),
                attrs.get("text", ""),
                attrs.get("content-desc", ""),
            ]
        )
        if needle.lower() in hay.lower():
            return line, attrs
    return None, None


def tap(needle):
    _, attrs = find(needle)
    if not attrs:
        sys.exit("cannot find %r on screen" % needle)
    left, top, right, bottom = (int(v) for v in re.findall(r"-?\d+", attrs["bounds"]))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
    return (left + right) // 2, (top + bottom) // 2


def type_text(value):
    # quoted so that the device shell keeps a value with spaces in one piece
    adb("shell", "input", "text", "'%s'" % value)


def set_field(res_id, value):
    """Focus a field, throw away whatever is in it, then type."""
    tap(res_id)
    time.sleep(0.5)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(12):
        adb("shell", "input", "keyevent", "KEYCODE_DEL")
    type_text(value)
    time.sleep(0.3)


def wait_for(needle, timeout=20.0):
    end = time.time() + timeout
    while time.time() < end:
        line, attrs = find(needle)
        if line:
            return attrs
        time.sleep(0.6)
    return None


def scroll_form_down():
    """Scroll the account form up (content moves down) to reveal fields below the fold."""
    adb("shell", "input", "swipe", "540", "1700", "540", "700", "400")
    time.sleep(0.8)


def dismiss_keyboard():
    """Close the soft keyboard, which otherwise hides the bottom of the form."""
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.8)


def swipe_row(left=950, right=140):
    _, attrs = find("rowContent")
    if not attrs:
        return False
    l, top, r, bottom = (int(v) for v in re.findall(r"-?\d+", attrs["bounds"]))
    y = (top + bottom) // 2
    adb("shell", "input", "swipe", str(left), str(y), str(right), str(y), "350")
    return True


def pick_spinner_option(anchor, wanted):
    """Tap the dropdown entry of `anchor` whose text is `wanted` (case-insensitive)."""
    for line in nodes(dump()):
        attrs = dict(re.findall(r'([\w:.-]+)="([^"]*)"', line))
        if attrs.get("text", "").strip().lower() == wanted.lower() and attrs.get(
            "class", ""
        ).endswith("TextView"):
            left, top, right, bottom = (
                int(v) for v in re.findall(r"-?\d+", attrs["bounds"])
            )
            adb(
                "shell",
                "input",
                "tap",
                str((left + right) // 2),
                str((top + bottom) // 2),
            )
            return attrs["text"]
    return None


def go_home():
    """Restart the app so the account overview is on screen.

    A cold start restores the last opened account, so the notes list is usually on top;
    Back steps out of it. The first dump after a start is often still empty, hence polling.
    """
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", "%s/%s" % (PKG, ACTIVITY))
    end = time.time() + 25
    while time.time() < end:
        time.sleep(2.0)
        xml = dump()
        if "accountList" in xml or "addAccountButton" in xml:
            return
        if "notesList" in xml or "noteTitle" in xml:
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            continue
    sys.exit("the account overview did not come up")


def wipe():
    """Delete every account through the swipe-reveal delete button."""
    go_home()
    for _ in range(10):
        if not find("accountName")[1]:
            return
        swipe_row()
        time.sleep(0.8)
        if not find("deleteAccountRowButton")[1]:
            swipe_row(1000, 120)  # try again, slower
            time.sleep(0.8)
        tap("deleteAccountRowButton")
        time.sleep(1.0)
        dismiss_dialog()  # the confirmation dialog offers YES / NO
        end = time.time() + 12
        while time.time() < end and find("accountName")[1]:
            time.sleep(0.6)


def create_account(name):
    """Fill and submit the account setup form for one mock-server account."""
    tap("addAccountButton")
    if not wait_for("accountnameEdit", timeout=10):
        sys.exit("the account setup form did not open")
    for res_id, value in FIELDS:
        if res_id == "accountnameEdit":
            value = name
        if res_id in ("securitySpinner", "portnumEdit"):
            # both live in the collapsible "more settings" section
            if not find("portnumEdit")[1]:
                # a focused text field raises the soft keyboard, which hides the
                # lower part of the form from uiautomator entirely
                dismiss_keyboard()
                if not find("portnumEdit")[1]:
                    tap("BtnExpandAccountSettings")
                    if not wait_for("portnumEdit", timeout=6):
                        scroll_form_down()
                        if not wait_for("portnumEdit", timeout=6):
                            sys.exit("the extended account settings did not open")
        if res_id == "securitySpinner":
            tap("securitySpinner")
            time.sleep(1.0)
            picked = pick_spinner_option("securitySpinner", "none")
            if picked is None:
                sys.exit("the security dropdown did not open")
            print("security:", picked)
            time.sleep(0.8)
            continue
        set_field(res_id, value)
        time.sleep(0.2)
    dismiss_keyboard()
    if not wait_for("next", timeout=3):  # the button can be clipped at the fold
        scroll_form_down()
    tap("Next")
    attrs = wait_for("Account has been added", timeout=25)
    print("result:", attrs.get("text") if attrs else "TIMEOUT")
    if not attrs:
        for res in ("resultHeading", "resultMessage"):
            _, found = find(res)
            if found:
                print("%s: %s" % (res, found.get("text")))
        sys.exit(1)
    if find("resultButton")[1]:
        tap("resultButton")
    time.sleep(1.5)


def alert_present():
    """True when a simple alert dialog is on screen (uiautomator omits the leading @)."""
    for line in nodes(dump()):
        attrs = dict(re.findall(r'([\w:.-]+)="([^"]*)"', line))
        res = attrs.get("resource-id", "")
        if (
            res.endswith("/alertTitle")
            or res.endswith("/message")
            or attrs.get("class", "").endswith("AlertDialog")
        ):
            return True
    return False


def dismiss_dialog():
    """Tap the positive button of a simple alert if one is on screen."""
    if not alert_present():
        return False
    for label in ("button1", "OK", "I understand", "I UNDERSTAND"):
        for line in nodes(dump()):
            attrs = dict(re.findall(r'([\w:.-]+)="([^"]*)"', line))
            if (
                attrs.get("resource-id", "").endswith("/" + label)
                or attrs.get("text", "").strip().upper() == label.upper()
            ):
                left, top, right, bottom = (
                    int(v) for v in re.findall(r"-?\d+", attrs["bounds"])
                )
                adb(
                    "shell",
                    "input",
                    "tap",
                    str((left + right) // 2),
                    str((top + bottom) // 2),
                )
                time.sleep(1.2)
                return True
    return False


def open_first_account():
    """Open the first account row: that starts the first sync."""
    if not wait_for("rowContent", timeout=15):
        sys.exit("the account did not show up in the account list")
    tap("rowContent")
    for _ in range(4):
        time.sleep(1.5)
        dismiss_dialog()  # e.g. the free-SDK warning on first start
    if not wait_for("noteTitle", timeout=60):
        sys.exit("no notes arrived from the mock server")
    time.sleep(1.0)


def reinstall(apk):
    """Uninstall and install again - that also drops stray AccountManager accounts."""
    adb("shell", "am", "force-stop", PKG)
    subprocess.run(  # already-uninstalled is fine
        ["adb", "-s", "emulator-5554", "uninstall", PKG], capture_output=True
    )
    out = subprocess.run(
        ["adb", "-s", "emulator-5554", "install", "-g", apk], capture_output=True
    )
    if b"Success" not in out.stdout:
        sys.exit("install failed: %s" % out.stdout.decode(errors="replace"))


def main():
    if "--reinstall" in sys.argv:
        reinstall(APK)
    if "--wipe" in sys.argv:
        wipe()
    if "--clear" in sys.argv:
        adb("shell", "pm", "clear", PKG)
    go_home()
    if not wait_for("addAccountButton", timeout=15):
        for label in ("OK", "I understand", "Allow", "Allow notifications"):
            if find(label)[1]:
                tap(label)
                time.sleep(1.0)
        if not wait_for("addAccountButton", timeout=10):
            sys.exit("no account screen: " + dump()[:400])
    names = [sys.argv[i + 1] for i, v in enumerate(sys.argv) if v == "--name"] or [
        "My Notes"
    ]
    for name in names:
        create_account(name)
        go_home()
    open_first_account()


if __name__ == "__main__":
    main()
