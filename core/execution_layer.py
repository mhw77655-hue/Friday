"""Single Execution Layer — Phase 1. Only path through which any change applies."""

import json
import shutil
from pathlib import Path
from datetime import datetime, timezone

EXEC_LOG = Path(__file__).parent.parent / "logs" / "execution.jsonl"
SNAPSHOT_DIR = Path(__file__).parent.parent / "snapshots"


class ExecutionError(Exception):
    pass


def apply(action: dict, decision: dict) -> dict:
    if not decision.get("approved"):
        result = {"applied": False, "reason": decision.get("reason", "not approved")}
        _log(action, decision, result)
        return result

    action_type = action.get("type")

    if action_type == "respond":
        result = {"applied": True, "output": action.get("payload")}
    elif action_type == "write":
        target = Path(action["target_path"])
        _snapshot(target)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(action["payload"])
        result = {"applied": True, "output": f"wrote {target}"}
    elif action_type == "noop":
        result = {"applied": True, "output": "noop"}
    else:
        raise ExecutionError(f"unknown action type: {action_type}")

    _log(action, decision, result)
    return result


def _snapshot(target: Path):
    if not target.exists():
        return
    SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    dest = SNAPSHOT_DIR / f"{target.name}.{stamp}.bak"
    shutil.copy2(target, dest)


def _log(action: dict, decision: dict, result: dict):
    EXEC_LOG.parent.mkdir(parents=True, exist_ok=True)
    entry = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "mission": action.get("mission"),
        "type": action.get("type"),
        "decision": decision,
        "result": result,
    }
    with open(EXEC_LOG, "a") as f:
        f.write(json.dumps(entry) + "\n")
