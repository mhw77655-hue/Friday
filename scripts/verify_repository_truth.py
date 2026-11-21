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
    if not isinstance(data, dict):
        raise ValueError("Repository truth policy root must be a JSON object")
    if data.get("schema_version") != 1:
        raise ValueError("Unsupported repository truth schema_version")
    return data


def is_authoritative(path: str, policy: dict[str, Any]) -> bool:
    """Only exact, well-formed registry entries confer authority; names never do."""
    authority = policy.get("authority") if isinstance(policy, dict) else None
    precedence = authority.get("precedence") if isinstance(authority, dict) else None
    return isinstance(precedence, list) and any(
        isinstance(item, dict) and item.get("path") == path for item in precedence
    )


def validate_capability(capability: dict[str, Any], policy: dict[str, Any]) -> list[str]:
    if not isinstance(capability, dict):
        return ["<invalid>: capability entry must be a JSON object"]
    errors: list[str] = []
    name = capability.get("id", "<unnamed>")
    level = capability.get("level", "idea")
    if not isinstance(level, str) or level not in VALID_LEVELS:
        return [f"{name}: unknown evidence level {level!r}"]

    capability_evidence = policy.get("capability_evidence", {}) if isinstance(policy, dict) else {}
    required = capability_evidence.get("required_evidence") if isinstance(capability_evidence, dict) else None
    if not isinstance(required, dict):
        return [f"{name}: required_evidence must be a JSON object"]
    fields = required.get(level, [])
    if not isinstance(fields, list) or any(not isinstance(field, str) for field in fields):
        return [f"{name}: required_evidence.{level} must be a list of strings"]
    evidence = capability.get("evidence", {})
    if not isinstance(evidence, dict):
        return [f"{name}: evidence must be a JSON object"]
    for field in fields:
        if not evidence.get(field):
            errors.append(f"{name}: {level} requires evidence.{field}")
    return errors


def verify(policy: dict[str, Any], root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    if not isinstance(policy, dict):
        return ["Policy root must be a JSON object"]

    authority = policy.get("authority")
    if not isinstance(authority, dict):
        errors.append("Authority policy must be a JSON object")
        authority = {}
    precedence = authority.get("precedence", [])
    if not isinstance(precedence, list):
        errors.append("Authority precedence must be a JSON list")
        precedence = []
    paths = []
    valid_entries = []
    for index, entry in enumerate(precedence):
        if not isinstance(entry, dict) or not isinstance(entry.get("path"), str) or not entry.get("path"):
            errors.append(f"Authority registry entry {index} must contain a non-empty string path")
            continue
        paths.append(entry["path"])
        valid_entries.append(entry)
    if len(paths) != len(set(paths)):
        errors.append("Authority registry contains duplicate paths")
    if authority.get("default_for_unlisted_sources") != "non_authoritative":
        errors.append("Unlisted sources must default to non_authoritative")
    globs = authority.get("instruction_discovery_globs")
    if not isinstance(globs, list) or not globs or any(not isinstance(item, str) or not item for item in globs):
        errors.append("Instruction discovery patterns are missing or malformed")
    for entry in valid_entries:
        path = entry["path"]
        if not (root / path).is_file():
            errors.append(f"Authoritative source is missing: {path}")

    capability_policy = policy.get("capability_evidence")
    if not isinstance(capability_policy, dict):
        errors.append("Capability evidence policy must be a JSON object")
        capability_policy = {}
    if capability_policy.get("manifest_policy") != "claims_only":
        errors.append("Capability manifests must be treated as claims, not proof")
    if not isinstance(capability_policy.get("required_evidence"), dict):
        errors.append("Required capability evidence must be a JSON object")

    capabilities = policy.get("capabilities", [])
    if not isinstance(capabilities, list):
        errors.append("Capabilities must be a JSON list")
        capabilities = []
    for capability in capabilities:
        errors.extend(validate_capability(capability, policy))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", type=Path, default=POLICY)
    args = parser.parse_args()
    try:
        policy = load_policy(args.policy)
        errors = verify(policy, ROOT)
    except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError, AttributeError) as exc:
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
