"""Single Decision Gate — Phase 1. Every action passes through here first."""

import json
from pathlib import Path
from datetime import datetime, timezone

PROTECTED_PATTERNS = [
    "manifest.json",
    "core/decision_gate.py",
    "core/execution_layer.py",
    "protected_paths.py",
]

GATE_LOG = Path(__file__).parent.parent / "logs" / "decision_gate.jsonl"


def _is_tier2(action: dict) -> bool:
    target = action.get("target_path") or ""
    return any(p in target for p in PROTECTED_PATTERNS)


def review(action: dict) -> dict:
    tier = 2 if _is_tier2(action) else 1

    if tier == 1:
        decision = {"approved": True, "tier": 1, "reason": "auto-approved (Tier 1)"}
    else:
        decision = {"approved": False, "tier": 2,
                     "reason": "Tier 2 requires explicit human confirmation — not wired until later phase"}

    _log(action, decision)
    return decision


def _log(action: dict, decision: dict):
    GATE_LOG.parent.mkdir(parents=True, exist_ok=True)
    entry = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "mission": action.get("mission"),
        "type": action.get("type"),
        "target_path": action.get("target_path"),
        "decision": decision,
    }
    with open(GATE_LOG, "a") as f:
        f.write(json.dumps(entry) + "\n")
