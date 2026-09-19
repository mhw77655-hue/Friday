"""JARVIS Core — Phase 1: receive input -> classify -> respond,
through the one decision gate and the one execution layer."""

from . import classifier, decision_gate, execution_layer

RESPONSES = {
    "status": "Core online. Phase 1 loop active.",
    "help": "I can currently classify input and respond. Nothing else is wired yet.",
}


def run_cycle(user_input: str) -> str:
    intent = classifier.classify(user_input)
    payload = RESPONSES.get(intent, f"heard: {user_input}")

    action = {
        "mission": "phase1-core-cycle",
        "type": "respond",
        "target_path": None,
        "payload": payload,
    }

    decision = decision_gate.review(action)
    result = execution_layer.apply(action, decision)

    if not result["applied"]:
        return f"[blocked] {result['reason']}"
    return result["output"]


if __name__ == "__main__":
    import sys
    text = " ".join(sys.argv[1:]) or "status"
    print(run_cycle(text))
