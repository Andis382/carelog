#!/usr/bin/env python3
"""Copy the Phosphor glyphs this interface uses into a PHP map.

Phosphor Icons is MIT licensed, (c) 2023 Phosphor Icons. Only the handful this
app actually references is copied, so CareLog needs no icon package, no icon
font and no build step: the path data ships as plain PHP.

    npm i @phosphor-icons/core@2.1.1        # in any scratch directory
    python3 scripts/build-icons.py resources/icons.php
"""
import os
import re
import sys

SRC = os.environ.get("PHOSPHOR", "/tmp/phos/node_modules/@phosphor-icons/core/assets/regular")

WANTED = {
    # navigation
    "home": "house", "calendar": "calendar-dots", "plus": "plus",
    "settings": "gear-six", "users": "users-three", "chart": "chart-line-up",
    "logout": "sign-out", "chevron": "caret-right", "back": "arrow-left",
    "close": "x", "dots": "dots-three", "external": "arrow-square-out",

    # care
    "pill": "pill", "heartbeat": "heartbeat", "pulse": "pulse",
    "first-aid": "first-aid-kit", "syringe": "syringe", "bandage": "bandaids",
    "stethoscope": "stethoscope", "bed": "bed", "hand-heart": "hand-heart",
    "meal": "fork-knife", "drink": "drop-half", "thermometer": "thermometer-simple",
    "mood-good": "smiley", "mood-mid": "smiley-meh", "mood-low": "smiley-sad",
    "notepad": "notepad", "rota": "calendar-check", "supplies": "package",

    # status and actions
    "check": "check", "check-circle": "check-circle", "warning": "warning",
    "warning-circle": "warning-circle", "clock": "clock", "hourglass": "hourglass-medium",
    "prohibit": "prohibit", "phone": "phone", "chat": "chat-circle-text",
    "whatsapp": "whatsapp-logo", "copy": "copy", "printer": "printer",
    "camera": "camera", "search": "magnifying-glass", "user": "user",
    "trash": "trash", "edit": "pencil-simple", "note": "note-pencil",
    "info": "info", "shield": "shield-check", "seal": "seal-check",
    "file": "file-text", "download": "download-simple", "retry": "arrow-clockwise",
    "list": "list-checks", "lock": "lock-simple", "key": "key",
    "link": "link-simple", "eye": "eye", "star": "star", "minus": "minus",
    "question": "question", "outbox": "paper-plane-tilt", "archive": "archive",
    "clipboard": "clipboard-text", "trend-up": "trend-up", "trend-down": "trend-down",
    "sun": "sun", "moon": "moon", "sunrise": "sun-horizon", "timer": "timer",
}

INNER = re.compile(r"<svg[^>]*>(.*)</svg>", re.S)

HEADER = """<?php

/*
 * Phosphor Icons, regular weight, MIT licensed, (c) 2023 Phosphor Icons.
 * https://github.com/phosphor-icons/core
 *
 * Only the glyphs this interface uses, as raw path data on a 256 unit grid, so
 * that the app needs no icon package, no icon font and no build step.
 * Regenerate with scripts/build-icons.py after adding a name.
 *
 * Never emoji. They are drawn by whatever font the device carries, render at a
 * size nobody chose, differ between Android and iOS, and cannot take a colour
 * from a design token -- so the glyph that has to mean "given" or "missed"
 * could not be relied on to look like anything in particular.
 */

return [
"""


def main():
    out, missing = [], []

    for key in sorted(WANTED):
        path = os.path.join(SRC, WANTED[key] + ".svg")
        if not os.path.exists(path):
            missing.append((key, WANTED[key]))
            continue
        match = INNER.search(open(path, encoding="utf-8").read())
        if not match:
            missing.append((key, WANTED[key]))
            continue
        body = match.group(1).strip().replace("\n", "")
        assert "'" not in body, key
        out.append("    '%s' => '%s'," % (key, body))

    if missing:
        print("MISSING:", missing, file=sys.stderr)

    dest = sys.argv[1] if len(sys.argv) > 1 else "resources/icons.php"
    with open(dest, "w", encoding="utf-8") as fh:
        fh.write(HEADER + "\n".join(out) + "\n];\n")
    print("wrote %d icons to %s" % (len(out), dest))


if __name__ == "__main__":
    main()
