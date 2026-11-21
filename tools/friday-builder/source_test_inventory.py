#!/usr/bin/env python3
"""Create a heuristic production-source to test-file map.

This report is an inventory aid, not proof that a source line or branch ran.
Runtime line/branch evidence comes from JaCoCo coverage reports.
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PRODUCTION = ROOT / "mobile/app/src/main/java"
TEST_ROOTS = (
    ROOT / "mobile/app/src/test/java",
    ROOT / "mobile/app/src/androidTest/java",
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    tests = []
    for root in TEST_ROOTS:
        if root.exists():
            tests.extend(sorted(p for p in root.rglob("*") if p.suffix in {".kt", ".java"}))
    test_text = {p: p.read_text(encoding="utf-8", errors="replace") for p in tests}

    production = sorted(PRODUCTION.rglob("*.kt")) if PRODUCTION.exists() else []
    if not production:
        raise SystemExit("No production Kotlin sources found; refusing an empty map.")

    rows = []
    filename_matches = 0
    symbol_matches = 0
    unmapped = 0
    for source in production:
        stem = source.stem
        relative = source.relative_to(PRODUCTION)
        expected = {
            f"{stem}Test.kt", f"{stem}Tests.kt",
            f"{stem}InstrumentedTest.kt", f"{stem}AndroidTest.kt",
            f"{stem}Test.java", f"{stem}Tests.java",
        }
        candidates = []
        for test in tests:
            same_package = test.parent == (TEST_ROOTS[0] / relative.parent) or test.parent == (TEST_ROOTS[1] / relative.parent)
            if (same_package and test.name in expected):
                candidates.append(test)
        match_kind = "filename"
        if candidates:
            filename_matches += 1
        else:
            # Deliberately conservative lexical reference signal. This can
            # produce false positives and must never be presented as coverage.
            token = re.compile(r"(?<![A-Za-z0-9_])" + re.escape(stem) + r"(?![A-Za-z0-9_])")
            candidates = [p for p, text in test_text.items() if token.search(text)]
            match_kind = "symbol-reference"
            if candidates:
                symbol_matches += 1
            else:
                match_kind = "unmapped"
                unmapped += 1
        rows.append((
            str(source.relative_to(ROOT)),
            match_kind,
            ";".join(str(p.relative_to(ROOT)) for p in candidates),
        ))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8") as handle:
        handle.write("production_source\tmap_kind\tcandidate_test_files\n")
        for row in rows:
            handle.write("\t".join(row) + "\n")

    total = len(production)
    summary = (
        f"Production Kotlin sources: {total}",
        f"Filename-matched test sources: {filename_matches}",
        f"Lexically referenced by test sources (heuristic): {symbol_matches}",
        f"No candidate test mapping found: {unmapped}",
        "WARNING: candidate mapping is not execution coverage; inspect JaCoCo line/branch reports.",
    )
    print("\n".join(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
