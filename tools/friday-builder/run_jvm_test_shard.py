#!/usr/bin/env python3
"""Run one deterministic package-balanced shard of Friday's JVM unit tests.

All source packages are assigned exactly once. Gradle's TestFilter performs the
actual class selection; the normal task-level exclusions (including live Ollama
tests) remain in force. A shard with no matching JUnit tests is rejected by the
workflow's per-shard XML validation rather than counted as a pass.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

SOURCE_ROOT = Path("mobile/app/src/test/java/ai/friday/assistant")
PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)", re.MULTILINE)
SHARD_COUNT = 6


def collect_groups() -> dict[str, list[Path]]:
    if not SOURCE_ROOT.is_dir():
        raise SystemExit(f"Test source root not found: {SOURCE_ROOT}")
    groups: dict[str, list[Path]] = defaultdict(list)
    for path in sorted(SOURCE_ROOT.rglob("*")):
        if path.suffix not in {".kt", ".java"} or not path.is_file():
            continue
        source = path.read_text(encoding="utf-8")
        match = PACKAGE_RE.search(source)
        if not match:
            raise SystemExit(f"Test source has no package declaration: {path}")
        package = match.group(1)
        prefix = "ai.friday.assistant"
        if package != prefix and not package.startswith(prefix + "."):
            raise SystemExit(f"Unexpected test package {package!r} in {path}")
        group = package[len(prefix):].lstrip(".").split(".", 1)[0] or "(root)"
        groups[group].append(path)
    if not groups:
        raise SystemExit("No JVM test source files were discovered")
    return groups


def assign_shards(groups: dict[str, list[Path]]) -> list[list[str]]:
    bins: list[list[str]] = [[] for _ in range(SHARD_COUNT)]
    weights = [0] * SHARD_COUNT
    for group, paths in sorted(groups.items(), key=lambda item: (-len(item[1]), item[0])):
        target = min(range(SHARD_COUNT), key=lambda index: (weights[index], index))
        bins[target].append(group)
        weights[target] += len(paths)
    return bins


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--shard", type=int, required=True, choices=range(SHARD_COUNT))
    parser.add_argument("--gradle", default="./gradlew")
    args = parser.parse_args()

    groups = collect_groups()
    shards = assign_shards(groups)
    selected = sorted(shards[args.shard])
    patterns: list[str] = []
    for group in selected:
        if group == "(root)":
            for path in groups[group]:
                patterns.append(f"ai.friday.assistant.{path.stem}")
        else:
            patterns.append(f"ai.friday.assistant.{group}.*")
    if not patterns:
        raise SystemExit(f"Shard {args.shard} has no assigned packages")

    print(
        f"Friday JVM test shard {args.shard + 1}/{SHARD_COUNT}: "
        f"{sum(len(groups[group]) for group in selected)} source files across "
        f"{len(selected)} package groups",
        flush=True,
    )
    print("Included Gradle test patterns:", *patterns, sep="\n  ", flush=True)
    command = [args.gradle, "--console=plain", ":mobile:app:testDebugUnitTest"]
    for pattern in patterns:
        command.extend(["--tests", pattern])
    return subprocess.run(command, check=False).returncode


if __name__ == "__main__":
    sys.exit(main())
