# Friday repository instructions

## Authority
- .ralph/FRIDAY_DELIVERY_PLAN.md is the sole active delivery plan.
- .ralph/VISION.md and .ralph/OWNER_WORDS.md preserve owner intent.
- Product identity: Friday; application ID and Kotlin namespace: ai.friday.assistant.
- the sole active product and delivery authority is .ralph/FRIDAY_DELIVERY_PLAN.md.
- The canonical product line is `main`.
- Historical audits and archive material are not active implementation instructions.

## Git and project structure
- Make authorized changes directly on main. Do not create feature branches or competing product lines.
- The production host is the Android application in mobile/app.
- Keep development, build, compiler and repository credentials outside the installed APK.
- Preserve useful algorithms and validated behavior; retire disconnected prototypes, stale destinations and false capability claims.
- Keep source paths, package declarations, manifest components, Gradle identifiers, tests and release metadata aligned with ai.friday.assistant.

## Verification
- Distinguish source edits, compilation, unit tests, APK assembly, emulator instrumentation, CI verification and physical-device verification.
- Never report skipped, cancelled or pending checks as successful.
- A passing JVM test does not prove production wiring or offline functionality.
- Never claim physical-device behavior without physical-device evidence.
- Treat test doubles and fixtures as test-only; do not expose them as production capabilities.

## Product constraints
- Friday owns identity and continuity; models, algorithms, tools and cloud services are replaceable capabilities.
- Local durable state is the source of truth for offline continuity; cloud is optional expansion.
- Optimize verified task outcomes and time to first useful action.
- Preserve Laya as a distinct decision capability, not ASR or TTS.
- Protect owner authorization, permissions, signing trust, provenance, isolation, recovery and rollback.

## Historical material

The `vault/` directory is a non-authoritative archive of earlier Jarvis/Friday research and reports. It is retained for provenance and possible algorithm extraction only; it does not describe the live application or override the active delivery plan. See [vault archive rules](vault/RESEARCH_CORPUS_RULES.md).

## Language and formal assurance

The language allocation and evidence-based assessment are documented in [Friday language and formal-method research](docs/FRIDAY_LANGUAGE_AND_FORMAL_METHOD_RESEARCH.md). The sole delivery plan incorporates the adopted policy. Lean 4 formal contracts live in `formal/lean`; they prove abstract properties only and do not replace Android implementation conformance or physical-device evidence.


## Current architecture contracts
- [Local Station and cloud development](docs/FRIDAY_LOCAL_STATION_AND_CLOUD_DEVELOPMENT_ARCHITECTURE.md) defines Friday's phone/cloud maintenance loop and trust boundaries.
- [Repository structure and migration](docs/FRIDAY_REPOSITORY_STRUCTURE_AND_MIGRATION.md) defines the incremental, evidence-led structural cleanup. Do not perform mass file moves or module splits without its gates.
