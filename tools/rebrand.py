#!/usr/bin/env python3
"""Rename the app in every string resource after a Molly merge.

Molly's build already takes the app title and package id from
app/gradle.properties, but its translated strings spell "Molly" out.
This rewrites those mentions to the Wren name and leaves MollySocket
(the separate UnifiedPush server) alone.

Usage: tools/rebrand.py [--check]
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
NAME = "Wren"
PATTERN = re.compile(r"Molly(?!Socket)")
INSTALL_URL = ("https://molly.im/install", "https://github.com/munzzyy/wren#install")


def targets():
    for values in sorted(RES.glob("values*")):
        for path in sorted(values.glob("strings*.xml")):
            yield path


def rewrite(text):
    for old, new in (INSTALL_URL,):
        text = text.replace(old, new)
    return PATTERN.sub(NAME, text)


def main(argv):
    check = "--check" in argv
    changed = 0
    for path in targets():
        before = path.read_text(encoding="utf-8")
        after = rewrite(before)
        if before == after:
            continue
        changed += 1
        if check:
            print(f"needs rebrand: {path.relative_to(ROOT)}")
        else:
            path.write_text(after, encoding="utf-8")
    if check and changed:
        print(f"{changed} file(s) still mention Molly", file=sys.stderr)
        return 1
    print(f"{'would change' if check else 'rewrote'} {changed} file(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
