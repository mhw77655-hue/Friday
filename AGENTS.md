# Friday repository instructions

## Authority and scope
- The sole active product and delivery authority is `.ralph/FRIDAY_DELIVERY_PLAN.md`.
- Owner-authored vision and instructions are authoritative inputs; reconcile all implementation work to the delivery plan.
- Older PRDs, prompts, phase plans, audit reports and architecture documents are historical evidence unless explicitly incorporated into the delivery plan.
- Friday is the product identity. Do not describe JARVIS/Jarvis as the current product name or codename.

## Repository and Git workflow
- The canonical product line is `main`. Commit authorized work directly to `main`; do not create feature branches or competing product lines.
- Keep one Android application as the production host. Termux, Python utilities, browser experiments and desktop/server prototypes are not production dependencies unless the delivery plan explicitly adopts and verifies them.
- Before deleting or replacing code, establish whether the real Android production composition, tests, build, workflows or release process reference it. Preserve useful algorithms and historical evidence; remove dead scaffolding only after dependency checks.
- The owner explicitly authorized the one-time pre-seed migration from com.jarvis.app to ai.friday.assistant after deleting the previous APK. Complete it before the permanent seed; keep signing lineage, update continuity and data migration requirements for all later releases.

## Evidence and verification
- Distinguish implemented, wired, runnable, tested, CI-verified, emulator-verified and physical-device-verified.
- A class, test, workflow, README claim or green compile is not proof that a capability is connected to the live application.
- Do not claim physical-device behavior without physical-device evidence.
- Fix skipped, cancelled and failing checks; do not count them as successful.
- Keep diagnostic fixtures and fake implementations clearly test-only. Never allow test doubles to be reported as production capability.

## Product constraints
- Friday owns identity and durable continuity; models, runtimes, tools and cloud services are replaceable capabilities.
- Local durable state is the source of truth for offline continuity. Cloud services are optional extensions.
- Optimize verified task outcomes and time to first useful action, not isolated model inference speed.
- Preserve Laya as a distinct decision capability; do not conflate it with ASR or TTS.
- Protect owner authorization, signing trust, permissions, provenance, rollback and recovery.
