#!/usr/bin/env python3
"""Strict allowlist for Friday Builder's automatic PR promotion.

A passing CI run is not enough to auto-promote changes to identity, memory,
permissions, credentials, security, privileged phone integrations, signing,
updates, or the Builder's own trust-control plane. Those changes remain draft
for owner review.
"""
from __future__ import annotations

import json
import sys
from pathlib import PurePosixPath
from typing import Any

ALLOWED_PREFIXES = (
    "mobile/app/src/main/java/",
    "mobile/app/src/test/",
    "mobile/app/src/androidTest/",
    "docs/",
)
ALLOWED_ROOT_FILES = {"README.md", "CHANGELOG.md"}
# These are declarative local skill definitions/tests only; they do not execute
# phone actions or grant permissions. Their outputs still pass the orchestrator.
SAFE_VOICE_EXTENSION_PATHS = {
    "mobile/app/src/main/java/com/jarvis/app/voice/LocalPhoneActionSkillRegistry.kt",
    "mobile/app/src/test/java/com/jarvis/app/voice/PhoneActionPlannerTest.kt",
}
BLOCKED_SEGMENTS = {
    ".github", ".ralph", "tools", "development-requests",
    "github", "identity", "memory", "permissions", "permission",
    "security", "auth", "token", "diagnostics", "crash",
    "accessibility", "notification", "shizuku", "biometric",
    "voice", "runtime", "update", "installer", "signing",
    "release", "owner",
}
BLOCKED_NAMES = {
    "AndroidManifest.xml",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "SettingsScreen.kt",
    "network_security_config.xml",
    "friday_accessibility_service.xml",
}
BLOCKED_NAME_PARTS = (
    "riskgate",
    "devicecontrolrouter",
    "crashrepair",
    "githubdevelopmentclient",
    "notificationlistener",
    "shizuk",
    "ownerbiometric",
    "identity",
    "selfmodel",
    "experiencestore",
    "experiencejournal",
    "seedsubstrate",
    "goalcontinuity",
    "goalstore",
    "longtermmemory",
    "persistentmemory",
    "memorymanager",
    "memorysnapshot",
    "capabilityfabric",
    "capabilityexecutor",
    "capabilityregistry",
    "operationcontract",
    "permission",
    "accessibility",
    "notification",
    "security",
    "privacy",
    "auth",
    "token",
    "release",
    "update",
    "installer",
    "signing",
    "crash",
    "diagnostic",
    "github",
    "runtime",
    "devicestatecapsule",
)


def evaluate(paths: Any) -> tuple[bool, str]:
    if not isinstance(paths, list) or not paths or len(paths) > 200:
        return False, "PR file list must be a non-empty list of at most 200 paths"
    seen: set[str] = set()
    for raw in paths:
        if not isinstance(raw, str) or not raw or "\x00" in raw:
            return False, "PR contains an invalid path"
        path = raw.replace("\\", "/")
        if len(path) > 240 or path.startswith("/") or any(ord(char) < 32 for char in path):
            return False, f"unsafe path: {raw}"
        raw_parts = path.split("/")
        if any(part in {"", ".", "..", ".git"} for part in raw_parts):
            return False, f"unsafe path: {raw}"
        parts = tuple(raw_parts)
        if path in seen:
            return False, f"duplicate path: {path}"
        seen.add(path)
        name = parts[-1]
        lower_parts = {part.lower() for part in parts}
        lower_name = name.lower()
        if lower_name in {item.lower() for item in BLOCKED_NAMES}:
            return False, f"protected file: {path}"
        blocked_segments = lower_parts & BLOCKED_SEGMENTS
        if blocked_segments and not (
            path in SAFE_VOICE_EXTENSION_PATHS and blocked_segments == {"voice"}
        ):
            return False, f"protected subsystem: {path}"
        if any(fragment in lower_name for fragment in BLOCKED_NAME_PARTS):
            return False, f"protected component: {path}"
        if not (path.startswith(ALLOWED_PREFIXES) or path in ALLOWED_ROOT_FILES):
            return False, f"path is outside the automatic-promotion allowlist: {path}"

    # Never promote a source-only patch. Every changed production Kotlin file
    # must have a changed regression test in its matching package directory.
    # Test-only changes are also held for review because they can weaken gates.
    production = [
        path for path in seen
        if path.startswith("mobile/app/src/main/java/") and path.endswith(".kt")
    ]
    test_paths = [
        path for path in seen
        if path.startswith("mobile/app/src/test/java/") or
           path.startswith("mobile/app/src/androidTest/java/")
    ]
    if not production:
        if any(path.startswith("mobile/app/src/test/") or path.startswith("mobile/app/src/androidTest/") for path in seen):
            return False, "test-only changes require review; tests are part of the trust boundary"
        return True, f"{len(paths)} documentation-only path(s) are within the allowlist"

    for source in production:
        relative = source.removeprefix("mobile/app/src/main/java/")
        package_dir = relative.rsplit("/", 1)[0] if "/" in relative else ""
        expected_dirs = (
            f"mobile/app/src/test/java/{package_dir}/",
            f"mobile/app/src/androidTest/java/{package_dir}/",
        )
        if not any(test.startswith(expected_dirs) for test in test_paths):
            return False, f"production source lacks a changed regression test in its package: {source}"

    return True, f"{len(paths)} changed path(s) are allowlisted and every production source has a package-local changed test"


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: auto_merge_policy.py PR_FILES.json", file=sys.stderr)
        return 2
    try:
        paths = json.loads(open(sys.argv[1], encoding="utf-8").read())
    except Exception as exc:
        print(f"Could not read PR file list: {exc}", file=sys.stderr)
        return 2
    eligible, reason = evaluate(paths)
    if eligible:
        print("AUTO_MERGE_ELIGIBLE: " + reason)
        return 0
    print("OWNER_REVIEW_REQUIRED: " + reason)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
