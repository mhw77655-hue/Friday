#!/usr/bin/env python3
"""Run one deterministic, disjoint shard of Android JVM unit tests."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

SOURCE_ROOT = Path("mobile/app/src/test")
SHARD_COUNT = 6
SOURCE_SUFFIXES = {".kt", ".java"}

PACKAGE_RE = re.compile(r"(?m)^[ \t]*package[ \t]+([A-Za-z_][A-Za-z0-9_.]*)")
CLASS_RE = re.compile(
    r"(?m)^(?:(?:public|protected|private|internal|abstract|open|final|sealed|data|enum|annotation|expect|actual)\s+)*(?:class|object)\s+([A-Za-z_][A-Za-z0-9_]*)\b"
)
TEST_ANNOTATION_RE = re.compile(
    r"(?m)^[ \t]*@(?:(?:org\.junit|kotlin\.test)\.)?(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b"
)


@dataclass(frozen=True)
class TestSource:
    path: Path
    package: str
    class_names: tuple[str, ...]
    weight: int


def parse_test_source(path: Path) -> TestSource | None:
    source = path.read_text(encoding="utf-8")
    package_match = PACKAGE_RE.search(source)
    package = package_match.group(1) if package_match else ""
    declared_classes = tuple(sorted(set(CLASS_RE.findall(source))))
    annotation_count = len(TEST_ANNOTATION_RE.findall(source))

    if not declared_classes:
        if annotation_count:
            raise SystemExit(f"Test annotations found without a top-level class: {path}")
        return None

    named_test_classes = tuple(
        name
        for name in declared_classes
        if name.endswith(("Test", "Tests", "TestCase")) or name == path.stem
    )
    class_names = named_test_classes or (declared_classes if annotation_count else ())
    if not class_names:
        return None

    return TestSource(
        path=path,
        package=package,
        class_names=class_names,
        weight=max(1, annotation_count),
    )


def collect_test_sources(source_root: Path = SOURCE_ROOT) -> list[TestSource]:
    sources: list[TestSource] = []
    owners: dict[str, Path] = {}
    for path in sorted(source_root.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in SOURCE_SUFFIXES:
            continue
        source = parse_test_source(path)
        if source is None:
            continue
        for class_name in source.class_names:
            qualified_name = (
                f"{source.package}.{class_name}" if source.package else class_name
            )
            previous = owners.get(qualified_name)
            if previous is not None and previous != path:
                raise SystemExit(
                    f"Duplicate JVM test class {qualified_name}: {previous} and {path}"
                )
            owners[qualified_name] = path
        sources.append(source)

    if not sources:
        raise SystemExit(f"No JVM test classes found under {source_root}")
    return sources


def assign_shards(
    sources: list[TestSource], shard_count: int = SHARD_COUNT
) -> tuple[list[list[TestSource]], list[int]]:
    if shard_count < 1:
        raise ValueError("shard_count must be positive")

    shards: list[list[TestSource]] = [[] for _ in range(shard_count)]
    weights = [0] * shard_count
    ordered = sorted(
        sources,
        key=lambda item: (-item.weight, item.package, item.path.as_posix()),
    )
    for source in ordered:
        target = min(range(shard_count), key=lambda index: (weights[index], index))
        shards[target].append(source)
        weights[target] += source.weight
    return shards, weights


def class_patterns_for_sources(sources: list[TestSource]) -> list[str]:
    patterns = {
        f"{source.package}.{class_name}" if source.package else class_name
        for source in sources
        for class_name in source.class_names
    }
    return sorted(patterns)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--shard", type=int, required=True, choices=range(SHARD_COUNT))
    parser.add_argument("--gradle", default="./gradlew")
    args = parser.parse_args(argv)

    sources = collect_test_sources()
    shards, weights = assign_shards(sources)
    selected = shards[args.shard]
    patterns = class_patterns_for_sources(selected)
    if not patterns:
        raise SystemExit(f"JVM shard {args.shard} has no exact test-class filters")

    annotation_count = sum(source.weight for source in selected)
    print(
        f"JVM shard {args.shard + 1}/{SHARD_COUNT}: "
        f"{len(selected)} source files, {annotation_count} test annotations, "
        f"{len(patterns)} exact class filters; shard weights={weights}"
    )
    print("Selected test classes:\n  " + "\n  ".join(patterns))

    command = [args.gradle, ":mobile:app:testDebugUnitTest"]
    for pattern in patterns:
        command.extend(["--tests", pattern])
    return subprocess.run(command, check=False).returncode


if __name__ == "__main__":
    sys.exit(main())
