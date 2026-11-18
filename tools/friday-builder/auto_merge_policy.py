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
    "mobile/app/src/main/res/",
    "mobile/app/src/test/",
    "mobile/app/src/androidTest/",
    "docs/",
)
ALLOWED_ROOT_FILES = {"README.md", "CHANGELOG.md"}
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
    "memorysnapshot",
    "capabilityfabric",
    "capabilityexecutor",
    "capabilityregistry",
    "operationcontract",
)


def evaluate(paths: Any) -> tuple[bool, str]:
    if not isinstance(paths, list) or not paths or len(paths) > 200:
        return False, "PR file list must be a non-empty list of at most 200 paths"
    seen: set[str] = set()
    for raw in paths:
        if not isinstance(raw, str) or not raw or "\x00" in raw:
            return False, "PR contains an invalid path"
        path = raw.replace("\\", "/")
        parts = PurePosixPath(path).parts
        if path.startswith("/") or any(part in {"", ".", "..", ".git"} for part in parts):
            return False, f"unsafe path: {raw}"
        if path in seen:
            return False, f"duplicate path: {path}"
        seen.add(path)
        name = parts[-1]
        lower_parts = {part.lower() for part in parts}
        lower_name = name.lower()
        if name in BLOCKED_NAMES:
            return False, f"protected file: {path}"
        if any(part in BLOCKED_SEGMENTS for part in lower_parts):
            return False, f"protected subsystem: {path}"
        if any(fragment in lower_name for fragment in BLOCKED_NAME_PARTS):
            return False, f"protected component: {path}"
        if not (path.startswith(ALLOWED_PREFIXES) or path in ALLOWED_ROOT_FILES):
            return False, f"path is outside the automatic-promotion allowlist: {path}"
    return True, f"{len(paths)} changed path(s) are within the low-risk allowlist"


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
