# Friday independent life core — implementation record

## Contract

Friday's identity and continuity must not be owned by a language model. Model providers are optional capability resources. They may contribute language understanding, synthesis, planning, and other expensive computation, but they do not own identity, persistent state, experience history, or the app's ability to be present.

## Implemented in this branch

- IndependentLifeCore is initialized by JarvisApplication before JarvisEngine.init.
- It persists the identity label, creation timestamp, activity timestamp, event/experience counters, and explicit event-kind association counts under filesDir/friday-life/.
- It appends a local JSONL event ledger.
- It persists explicit goals and their active/completed status.
- It receives app foreground/background lifecycle events.
- The home/presence surface reads the independent life substrate and no longer requires BodyCoordinator to render.
- The navigation host no longer blocks the whole app behind the turn-engine readiness gate.

## What this does not claim

This is not consciousness, human-equivalent understanding, semantic learning, self-authorship, or unrestricted autonomy. The associations are explicit frequency reinforcement over event kinds. The maintenance loop is housekeeping, not subjective reflection. Android may terminate the process; durable storage supports continuity after restart, not an immortal process.

## Release blockers still to implement and verify

1. Remove the fixed seven-mode state machine as the owner of Friday's internal state. Keep UI animation states as presentation outputs only.
2. Make the turn/model subsystem optional at composition time, with a genuine no-model path that does not instantiate a hardcoded local Ollama provider as Friday's sole cognitive backend.
3. Replace chatbot-first navigation with a presence/workspace surface driven by events, goals, capabilities, memory, and user-approved actions.
4. Wire perception sources through explicit Android permission boundaries and durable event provenance.
5. Add causal experience records, outcome evaluation, adaptive policies, and safe goal execution. Distinguish measured learning from counters.
6. Add tests for process recreation, persistence corruption, concurrent writes, app lifecycle, and model-provider absence.
7. Build the APK in GitHub Actions and verify on the user's actual Android device.

## Validation status

Pending CI. No APK is claimed by this commit.
