#!/usr/bin/env python3
"""Reject proposal patches that alter the worker/security control plane."""
from __future__ import annotations

import subprocess
import sys
from pathlib import PurePosixPath

PROTECTED_PREFIXES = (
    ".github/",
    ".ralph/",
    "tools/friday-builder/",
    "development-requests/inbox/",
)
PROTECTED_NAMES = {
    ".gitmodules",
    "local.properties",
    "key.properties",
    "gradle.properties.local",
}
PROTECTED_SUFFIXES = (
    ".jks", ".keystore", ".p12", ".pfx", ".apk", ".aab", ".aar"
)


def is_protected(path: str) -> bool:
    if not path or "\x00" in path or path.startswith("/"):
        return True
    parts = PurePosixPath(path).parts
    if any(part in {".", "..", ".git"} for part in parts):
        return True

    name = parts[-1]
    if path.startswith(PROTECTED_PREFIXES) or name in PROTECTED_NAMES:
        return True
    if name.startswith(".env") or path.lower().endswith(PROTECTED_SUFFIXES):
        return True
    return False


def main() -> int:
    base = sys.argv[1] if len(sys.argv) > 1 else "HEAD"
    result = subprocess.run(
        ["git", "diff", "--name-only", "-z", base],
        check=True, capture_output=True
    )
    try:
        paths = [
            item.decode("utf-8", errors="strict")
            for item in result.stdout.split(b"\x00") if item
        ]
    except UnicodeDecodeError:
        print("Proposal contains a non-UTF-8 path.", file=sys.stderr)
        return 3

    if not paths:
        print("Proposal patch is empty.", file=sys.stderr)
        return 2

    blocked = [path for path in paths if is_protected(path)]
    if blocked:
        print("Proposal touches protected or unsafe paths:", file=sys.stderr)
        for path in blocked:
            print(f"  {path}", file=sys.stderr)
        return 3

    print(f"Proposal path check passed ({len(paths)} changed paths).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
