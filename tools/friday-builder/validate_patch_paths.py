#!/usr/bin/env python3
"""Reject proposal patches that alter the worker/security control plane."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path, PurePosixPath

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
    ".jks",
    ".keystore",
    ".p12",
    ".pfx",
    ".pem",
    ".key",
    ".crt",
    ".der",
    ".mobileprovision",
    ".apk",
    ".aab",
    ".aar",
)
UNSAFE_GIT_MODES = {"120000": "symbolic link", "160000": "git submodule"}


def is_protected(path: str) -> bool:
    if not path or "\x00" in path or path.startswith("/"):
        return True
    parts = PurePosixPath(path).parts
    if any(part in {".", "..", ".git"} for part in parts):
        return True
    name = parts[-1]
    return (
        path.startswith(PROTECTED_PREFIXES)
        or name in PROTECTED_NAMES
        or name.startswith(".env")
        or path.lower().endswith(PROTECTED_SUFFIXES)
    )


def inspect_patch(base: str = "HEAD", repository: Path | str = ".") -> tuple[list[str], list[str], list[str]]:
    """Return changed paths, protected paths, and unsafe Git index entries.

    Rename detection is disabled so both the old and new names are checked.
    File modes are read from the index; symlinks and gitlinks are forbidden
    even when their visible path is inside an otherwise allowed directory.
    """
    changed = subprocess.run(
        ["git", "diff", "--name-only", "-z", "--no-renames", base],
        cwd=repository,
        check=True,
        capture_output=True,
    )
    try:
        paths = [item.decode("utf-8", errors="strict") for item in changed.stdout.split(b"\x00") if item]
    except UnicodeDecodeError as exc:
        raise ValueError("Proposal contains a non-UTF-8 path.") from exc

    protected = [path for path in paths if is_protected(path)]
    unsafe_modes: list[str] = []
    if paths:
        indexed = subprocess.run(
            ["git", "ls-files", "--stage", "-z", "--", *paths],
            cwd=repository,
            check=True,
            capture_output=True,
        )
        for record in indexed.stdout.split(b"\x00"):
            if not record:
                continue
            try:
                metadata, raw_path = record.split(b"\t", 1)
                mode = metadata.split(b" ", 1)[0].decode("ascii")
                indexed_path = raw_path.decode("utf-8", errors="strict")
            except (ValueError, UnicodeDecodeError) as exc:
                raise ValueError("Could not safely inspect proposal file modes.") from exc
            if mode in UNSAFE_GIT_MODES:
                unsafe_modes.append(f"{indexed_path} ({UNSAFE_GIT_MODES[mode]})")
    return paths, protected, unsafe_modes


def main() -> int:
    base = sys.argv[1] if len(sys.argv) > 1 else "HEAD"
    try:
        paths, blocked, unsafe_modes = inspect_patch(base)
    except (OSError, subprocess.CalledProcessError, ValueError) as exc:
        print(f"Could not validate proposal patch safely: {exc}", file=sys.stderr)
        return 3
    if not paths:
        print("Proposal patch is empty.", file=sys.stderr)
        return 2
    if blocked:
        print("Proposal touches protected paths: " + ", ".join(blocked), file=sys.stderr)
        return 3
    if unsafe_modes:
        print("Proposal contains forbidden Git entries: " + ", ".join(unsafe_modes), file=sys.stderr)
        return 3
    print(f"Proposal path check passed ({len(paths)} changed paths; no symlinks or submodules).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
