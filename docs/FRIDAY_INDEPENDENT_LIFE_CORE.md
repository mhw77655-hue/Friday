# Friday independent life core

## Intent
Friday's identity, continuity and ability to remain present must not depend on a language model. A model is an optional computational resource; it must not own Friday's persistent self-state.

## Implemented in this branch
- IndependentLifeCore is initialized before JarvisEngine.
- A local continuity snapshot stores the Friday identity label, creation/activity timestamps, event/experience counters and explicit event-kind association counts.
- A local JSONL event ledger records application lifecycle and observed events.
- Explicit goals persist with active/completed status.
- App foreground/background transitions are delivered to the independent core.
- The home presence surface can render without BodyCoordinator.
- The navigation host no longer blocks the entire app behind turn-engine readiness.

## Honest limits
This is a model-independent continuity and event substrate, not consciousness, human-equivalent understanding, semantic learning or unrestricted autonomy. The associations are explicit event-kind frequency reinforcement. The low-frequency loop performs durable maintenance; it is not subjective reflection. Android can terminate the process; persisted state enables recovery, not an immortal process.

## Required next engineering work
1. Remove the fixed seven-mode state machine as the owner of Friday's internal state. Animation modes must be presentation outputs, not the cognitive architecture.
2. Make model/turn assembly optional at composition time; remove the hardwired local Ollama assumption and implement a fully operational no-model path.
3. Replace chatbot-first navigation with a presence/workspace driven by events, goals, capabilities, memory and approved actions.
4. Connect permission-bounded device/environment perception to the event substrate with provenance and revocation handling.
5. Implement causal experience records, outcome evaluation, adaptive policies and safe goal execution; measure actual learning rather than counters.
6. Add tests for process recreation, storage corruption, concurrent writes, lifecycle transitions and provider absence.
7. Build the APK in CI and verify on the actual device.

## Validation
Pending GitHub Actions. No APK or device acceptance is claimed by this branch yet.
