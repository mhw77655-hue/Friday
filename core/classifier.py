"""Minimal classifier — Phase 1. Rule-based only. Deeper reasoning is Phase 3."""

INTENTS = {
    "status": ["status", "health", "how are you"],
    "help": ["help", "what can you do"],
}


def classify(text: str) -> str:
    lowered = text.lower().strip()
    for intent, keywords in INTENTS.items():
        if any(k in lowered for k in keywords):
            return intent
    return "echo"
