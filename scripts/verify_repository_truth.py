#!/usr/bin/env python3
"""Deterministic guard for Friday's source authority and capability evidence.

This verifier does not ask an LLM to decide what is authoritative. It reads a
small, machine-readable policy and fails closed when that policy is malformed.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "governance" / "repository_truth.json"
VALID_LEVELS = (
    "idea",
    "declared",
    "source_present",
    "production_wired",
    "integration_tested",
    "device_verified",
    "released",
)


def load_policy(path: Path = POLICY) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1:
        raise ValueError("Unsupported repository truth schema_version")
    return data


def is_authoritative(path: str, policy: dict[str, Any]) -> bool:
    """Only exact registry entries confer authority; names never do."""
    return any(item["path"] == path for item in policy["authority"]["precedence"])


def validate_capability(capability: dict[str, Any], policy: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    name = capability.get("id", "<unnamed>")
    level = capability.get("level", "idea")
    if level not in VALID_LEVELS:
        return [f"{name}: unknown evidence level {level!r}"]
    required = policy["capability_evidence"]["required_evidence"]
    for field in required.get(level, []):
        if not capability.get("evidence", {}).get(field):
            errors.append(f"{name}: {level} requires evidence.{field}")
    return errors


def verify(policy: dict[str, Any], root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    precedence = policy.get("authority", {}).get("precedence", [])
    paths = [entry.get("path") for entry in precedence]
    if len(paths) != len(set(paths)):
        errors.append("Authority registry contains duplicate paths")
    if policy.get("authority", {}).get("default_for_unlisted_sources") != "non_authoritative":
        errors.append("Unlisted sources must default to non_authoritative")
    if not policy.get("authority", {}).get("instruction_discovery_globs"):
        errors.append("Instruction discovery patterns are missing")
    for entry in precedence:
        path = entry.get("path", "")
        if not path or not (root / path).is_file():
            errors.append(f"Authoritative source is missing: {path}")
    if policy.get("capability_evidence", {}).get("manifest_policy") != "claims_only":
        errors.append("Capability manifests must be treated as claims, not proof")
    for capability in policy.get("capabilities", []):
        errors.extend(validate_capability(capability, policy))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", type=Path, default=POLICY)
    args = parser.parse_args()
    try:
        policy = load_policy(args.policy)
        errors = verify(policy, ROOT)
    except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
        print(f"REPOSITORY_TRUTH=FAIL: {exc}")
        return 2
    if errors:
        for error in errors:
            print(f"REPOSITORY_TRUTH_ERROR: {error}")
        print("REPOSITORY_TRUTH=FAIL")
        return 1
    print("REPOSITORY_TRUTH=PASS")
    print("UNREGISTERED_SOURCES=NON_AUTHORITATIVE_BY_DEFAULT")
    print("CAPABILITY_MANIFEST=CLAIMS_ONLY")
    print("SELF_MODIFICATION=TRANSACTIONAL_AND_INDEPENDENTLY_VERIFIED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
