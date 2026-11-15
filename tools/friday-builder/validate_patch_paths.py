#!/usr/bin/env python3
"""Reject proposal patches that alter the worker/security control plane."""
from __future__ import annotations

import subprocess
import sys

PROTECTED_PREFIXES = (
    ".github/",
    ".ralph/",
    "tools/friday-builder/",
    "development-requests/inbox/",
)
PROTECTED_NAMES = {
    "local.properties",
    "key.properties",
    "gradle.properties.local",
}
PROTECTED_SUFFIXES = (".jks", ".keystore", ".p12", ".pfx")


def main() -> int:
    base = sys.argv[1] if len(sys.argv) > 1 else "HEAD"
    result = subprocess.run(
        ["git", "diff", "--name-only", base],
        check=True, text=True, capture_output=True
    )
    paths = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if not paths:
        print("Proposal patch is empty.", file=sys.stderr)
        return 2

    blocked = []
    for path in paths:
        name = path.rsplit("/", 1)[-1]
        if path.startswith(PROTECTED_PREFIXES) or name in PROTECTED_NAMES:
            blocked.append(path)
        elif path.lower().endswith(PROTECTED_SUFFIXES) or name.startswith(".env"):
            blocked.append(path)

    if blocked:
        print("Proposal touches protected paths:", file=sys.stderr)
        for path in blocked:
            print(f"  {path}", file=sys.stderr)
        return 3

    print(f"Proposal path check passed ({len(paths)} changed paths).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
