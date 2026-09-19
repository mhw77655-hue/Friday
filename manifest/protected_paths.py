"""
protected_paths.py -- Structural manifest enforcement (Blueprint Section 4).

Called by the execution layer on every file read/write before the operation
touches disk. If a path is not covered by workspace_manifest.yaml, the
operation fails closed and the attempt is logged.
"""

from __future__ import annotations

import json
import os
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

import yaml

MANIFEST_PATH = Path(__file__).parent / "workspace_manifest.yaml"
LOG_PATH = Path(__file__).parent.parent / "logs" / "manifest_events.jsonl"

Action = Literal["read", "write"]


class ManifestViolation(Exception):
    pass


@dataclass
class ManifestCheck:
    allowed: bool
    path: str
    reason: str
    manifest_version: int


def _load_manifest() -> dict:
    with open(MANIFEST_PATH, "r") as f:
        return yaml.safe_load(f)


def _normalize(path: str) -> str:
    return str(Path(path).resolve())


def _is_under_any_root(target: str, roots: list, repo_base: str) -> bool:
    target_p = Path(target)
    for entry in roots:
        root = entry["path"] if isinstance(entry, dict) else entry
        root_p = Path(repo_base) / root
        try:
            root_resolved = root_p.resolve()
        except (OSError, RuntimeError):
            continue
        if target_p == root_resolved or root_resolved in target_p.parents:
            return True
    return False


def check_path(path: str, action: Action, repo: str, repo_base: str) -> ManifestCheck:
    manifest = _load_manifest()
    version = manifest.get("manifest_version", -1)
    normalized = _normalize(path)

    repo_cfg = manifest.get("repos", {}).get(repo)
    if repo_cfg is None:
        result = ManifestCheck(False, normalized, f"unknown repo '{repo}'", version)
        _log_event(result, action)
        return result

    roots = repo_cfg.get("roots", [])
    allowed = _is_under_any_root(normalized, roots, repo_base)

    if allowed and action == "write":
        protected = manifest.get("protected_paths", [])
        for p in protected:
            protected_abs = str((Path(repo_base) / p).resolve())
            if normalized == protected_abs or normalized.startswith(protected_abs + os.sep):
                result = ManifestCheck(
                    False, normalized,
                    f"path is Tier 2 protected ({p}) -- requires explicit human approval",
                    version,
                )
                _log_event(result, action)
                return result

    reason = "within manifest roots" if allowed else "not listed in any manifest root -- denied by default"
    result = ManifestCheck(allowed, normalized, reason, version)
    _log_event(result, action)
    return result


def _log_event(result: ManifestCheck, action: Action) -> None:
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    entry = {
        "ts": time.time(),
        "iso": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "action": action,
        "path": result.path,
        "allowed": result.allowed,
        "reason": result.reason,
        "manifest_version": result.manifest_version,
    }
    with open(LOG_PATH, "a") as f:
        f.write(json.dumps(entry) + "\n")


def require(path: str, action: Action, repo: str, repo_base: str) -> None:
    result = check_path(path, action, repo, repo_base)
    if not result.allowed:
        raise ManifestViolation(f"{action} denied for {result.path}: {result.reason}")
