#!/usr/bin/env python3
"""Search the original chat archive for owner speaker turns only.

The script does not treat assistant messages as owner statements. A user turn
can itself quote or paste an assistant response; those passages remain inside
that turn and must be checked before attributing a sentence to the owner.
"""
from __future__ import annotations

import argparse
import html
import re
import sys
import zipfile
from pathlib import PurePosixPath

TEXT_SUFFIXES = {".txt", ".md", ".html", ".htm", ".xml", ".csv", ".yaml", ".yml"}
ROLE = re.compile(
    r"(?m)^(You|User|Human|ChatGPT|Claude|Assistant|Jules|Friday|GPT):[ \t]*"
)
OWNER_LABELS = {"You", "User", "Human"}


def decode_text(raw: bytes) -> str | None:
    for encoding in ("utf-8-sig", "utf-16", "latin-1"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    return None


def plain_text(name: str, raw: bytes) -> str | None:
    text = decode_text(raw)
    if text is None:
        return None
    suffix = PurePosixPath(name).suffix.lower()
    if suffix in {".html", ".htm"}:
        text = re.sub(r"(?is)<(script|style)\b[^>]*>.*?</\1\s*>", " ", text)
        text = html.unescape(re.sub(r"(?s)<[^>]+>", " ", text))
    elif suffix == ".xml":
        text = re.sub(r"(?s)<[^>]+>", " ", text)
        text = html.unescape(text)
    return text


def owner_turns(archive: str):
    with zipfile.ZipFile(archive) as zf:
        for info in zf.infolist():
            if info.is_dir() or PurePosixPath(info.filename).suffix.lower() not in TEXT_SUFFIXES:
                continue
            if info.file_size > 4_000_000:
                continue
            text = plain_text(info.filename, zf.read(info))
            if text is None:
                continue
            markers = list(ROLE.finditer(text))
            number = 0
            for index, marker in enumerate(markers):
                if marker.group(1) not in OWNER_LABELS:
                    continue
                end = markers[index + 1].start() if index + 1 < len(markers) else len(text)
                body = text[marker.end():end].strip()
                if not body:
                    continue
                number += 1
                yield info.filename, number, body


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", default="New Folder chats.zip")
    parser.add_argument("--search", help="case-insensitive text to find in owner turns")
    parser.add_argument("--max-results", type=int, default=30)
    parser.add_argument("--max-chars", type=int, default=5000)
    parser.add_argument("--index", action="store_true", help="list source files and owner-turn counts")
    args = parser.parse_args()

    try:
        turns = list(owner_turns(args.archive))
    except (OSError, zipfile.BadZipFile) as exc:
        print(f"Could not read owner archive: {exc}", file=sys.stderr)
        return 2

    if args.index:
        counts = {}
        for name, _, _ in turns:
            counts[name] = counts.get(name, 0) + 1
        for name, count in sorted(counts.items()):
            print(f"{count:4d}  {name}")
        print(f"TOTAL_OWNER_TURNS={len(turns)}")
        return 0

    matches = turns
    if args.search:
        needle = args.search.casefold()
        matches = [item for item in turns if needle in item[2].casefold()]

    print("# Owner speaker turns (assistant turns excluded)")
    print(f"Archive: {args.archive}")
    print(f"Matches: {len(matches)}")
    print("Note: user turns may quote pasted text; verify attribution before treating a sentence as owner-authored.")
    for name, number, body in matches[:max(0, args.max_results)]:
        print(f"\n## {name} — owner turn {number}\n")
        excerpt = body[:max(0, args.max_chars)]
        print(excerpt)
        if len(body) > len(excerpt):
            print("\n[excerpt truncated; inspect the original archive entry for the full turn]")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
