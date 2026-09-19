"""Phase 1 exit criteria: core runs on-device, completes one full
input->response cycle through the execution layer."""

import json
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))
from core import jarvis_core

LOG = Path(__file__).parent.parent / "logs" / "execution.jsonl"


def test_full_cycle_logged():
    before = LOG.read_text().count("\n") if LOG.exists() else 0

    output = jarvis_core.run_cycle("status")
    assert "Phase 1" in output

    after = LOG.read_text().count("\n")
    assert after == before + 1, "execution layer did not log the cycle"

    entry = json.loads(LOG.read_text().strip().splitlines()[-1])
    assert entry["decision"]["approved"] is True
    assert entry["result"]["applied"] is True
    print("PHASE 1 EXIT CRITERIA: PASS")


if __name__ == "__main__":
    test_full_cycle_logged()
