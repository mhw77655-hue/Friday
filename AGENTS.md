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
