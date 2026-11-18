# REPO_FACTS.md — current verified facts for Friday

**Snapshot:** 2026-11-18  
**Repository:** `mhw77655-hue/Friday`  
**Default branch:** `main` at `d1b4cc88d722fa420d5648db9284a30b15ac33e8`

This file is auto-prepended to Ralph/Builder prompts. Keep it factual, dated and short. Update it when the repository changes. Historical branch names, old PR numbers, local Termux paths and unverified feature claims must not be copied forward.

## Product authority and architecture

- Friday is one Android-resident developing individual. Her identity is not a chat interface, LLM, cloud worker, repository or Builder.
- The latest authenticated owner instruction is authoritative. The written owner contract is `.ralph/VISION.md`; curated, provenance-labelled owner-authored excerpts are in `.ralph/OWNER_WORDS.md`.
- Private conversation archives were removed from the public repository. Never search for, reconstruct, request or republish them. If curated owner material leaves a genuine ambiguity, preserve uncertainty and ask the owner.
- Android is Friday's body: local identity/continuity, cognition, experience, presence, perception, permissioned device actions, safe runtime capabilities, self-study and development-intent/outbox.
- The repository workshop owns source checkout/edits, coding workers, research, tests, CI, emulator, APK construction, signing and trusted promotion. The Android app is not a source checkout/compiler or arbitrary code execution environment.
- Safe declarative/restricted runtime capability acquisition is a separate concern from source-level changes. Do not reactivate the legacy Android source Builder to solve runtime capability growth.
- Phone and workshop are resources of the same Friday. The phone may send bounded authenticated requests and receive verifiable results; it must not contain repository Contents:write credentials, worker secrets or signing keys.

## Build system and commands

- Android Gradle project root is the repository root; app module is `mobile/app/`.
- Use JDK 17 and Android SDK components matching the Gradle configuration.
- From repository root:
  - `./gradlew :mobile:app:assembleDebug`
  - `./gradlew :mobile:app:testDebugUnitTest`
- If the wrapper executable bit is unavailable in a local environment, use `bash ./gradlew <task>`. Do not assume an Android/Termux path such as `/sdcard/jarvis-repo`.
- Current GitHub build workflow runs owner-vision checks, Friday Builder contract tests, workflow lint, debug APK assembly, JVM unit tests and a whole-repository source inventory/Kotlin static audit.
- The disposable Android emulator gate is still in open PR #82 and is not yet part of main's required validation. Do not claim emulator or physical-phone success until evidence exists.
- A green compile/JVM suite does not prove exact-device microphone, background survival, model inference, Shizuku, upgrade, install or rollback behaviour.

## Development bridge and trust boundary

- The phone bridge uses an owner-provided fine-grained GitHub PAT encrypted with AES-GCM using Android Keystore. The documented minimum permissions are Actions: read/write, Pull requests: read, Issues: read/write and Metadata: read for this repository only. Do not grant Contents: write.
- This PAT is a bearer credential for workflow dispatch/proposal inspection; never log, transmit in payloads or embed it in source/APK. The app can dispatch requests and inspect proposals, not push source.
- The repository coding worker is untrusted and has read-only repository access. It produces a patch artifact. A separate trusted publisher validates changed paths and may create a draft PR only.
- A draft PR is not a repair. No worker may merge, sign, release or install. Promotion must validate the exact immutable candidate SHA with all required checks; skipped/cancelled/neutral checks are not success.
- Requests and approved diagnostic payloads are public because this repository is public. Never send conversation text, transcripts, screen, microphone, camera, location, contacts, notifications, arbitrary files or unrelated personal data. Respect each explicit owner consent scope.
- The trusted publisher must use only permissions needed for its operations. PR creation already triggers the normal `pull_request` build; do not add redundant workflow dispatch or broaden Actions permissions without a demonstrated need.

## Verified release state

- PR #86 merged the permanent release-signing/publishing workflow foundation.
- No production GitHub release exists as of this snapshot.
- Owner-controlled permanent keystore custody/backup and all five `FRIDAY_RELEASE_*` repository secrets remain unverified/unconfigured.
- The installed package's current signer must be inspected before first production installation. A debug-to-permanent-signer transition may require a one-time data-preserving migration.
- Phone-side release discovery, signer/hash/version verification, owner-authorized installation, post-update health check and tested recovery/rollback are incomplete.
- Do not claim Shizuku grants root, silent installation or Android downgrade bypass.

## Current PR/CI snapshot — 2026-11-18

- PR #82: disposable Android emulator. Earlier run compiled APKs but failed before install because SettingsProvider was not ready. A helper requiring boot completion, PackageManager enumeration and a live Settings-provider read is being validated on audit branch `audit/virtual-phone-system-readiness-20261118`, commit `f929dd6e812546d3b5ae5db796c9c20b9fa21906`. Builder unit tests and source audit passed on that SHA; APK/emulator jobs were still running at the time of this snapshot.
- PR #83: autonomous repair/promotion. Its stated dependency on PR #85 is stale; #85 closed without merge. Signing foundation #86 is merged; emulator work is #82. Promotion must not claim emulator-backed validation before #82 is integrated and required.
- PR #89: phone-resident source Builder proposal. Do not merge as-is: it reintroduces source construction into Android contrary to the workshop boundary and its observed head fails compilation because `nowMs` is passed twice in `JarvisEngine.kt`.
- PR #90: draft whole-project root-cause plan. It records audit scope, proven findings, uncertainty and dependency-ordered phases.
- PR #91: closed after correcting an initial mistaken assumption about GITHUB_TOKEN-triggered pull_request approval. PR #92 adds `actions: write` only to the trusted publisher so the deliberate workflow_dispatch can run; the untrusted worker remains read-only. PR #92 is not yet verified.
- Do not treat open PRs, draft plans, code presence or branch commits as merged/working functionality.

## Audit scope and interpretation

- Latest whole-repository inventory: 974 tracked files / 1,232 tree entries; 800 code/config files and 134,254 inventoried lines.
- All 484 production Kotlin files were passed through the current whole-file Vision-shape scanner; 24 Python files were syntax-compiled and 16 shell scripts syntax-checked.
- The scanner reported 283 candidate deviations and 392 observations. These are structural leads, not semantic verdicts; review call paths, ownership, intent and production reachability before changing code. The scanner's self-audit also found 7 candidate deviations and 6 observations.
- Python/shell syntax checks do not prove runtime behaviour. The root `tests/` directory is not run by the current CI; `tests/test_building_system.py` imports a top-level `building` package absent from the current tracked tree.
- Root `core/`, `manifest/` and `tests/` contain an older JARVIS Python prototype, not the current Android production architecture. Its manifest names a different `JARVIS` repository and its legacy decision/execution layer must not be treated as Friday's authority. It is under audit for quarantine/removal after reference checks.

## Production-critical native inference findings

- The production inference service is isolated in Android's `:inference` process, but process isolation does not fix native lifetime bugs.
- `local_inference_engine.cpp` contains nested locking: generation, suspend and destroy hold a non-recursive mutex then call helpers that lock it again. Those paths can deadlock.
- The prewarm thread is detached but destroy later attempts to join it; destroying during prewarm can free state still used by the thread.
- Kotlin `LocalInferenceEngine` does not hold a native-handle lifetime lock around generate/stream/health/cancel/suspend/resume, while the isolated service launches concurrent IO operations. Concurrent unload can race native use.
- llama.cpp backend init/free is process-global but currently handled per model; temporary JNI tokenizer wrappers are allocated without being freed; VAD JNI must validate sample count against array length.
- Do not claim local inference reliable until native/Kotlin lifetime ownership is refactored and stress-tested across create/prewarm/generate/cancel/suspend/resume/unload and multiple handles.

## Historical/legacy files

- `.ralph/FRIDAY_BUILD_PLAN.md`, `.ralph/REPO_UNIFICATION_AUDIT.md` and `docs/FRIDAY_BUILD_PROGRESS.md` contain historical state. Use `.ralph/WHOLE_PROJECT_ROOT_CAUSE_PLAN_20261118.md` for current dependencies and evidence; do not copy old branch/PR/archive claims into new work.
- `core/`, `manifest/` and `tests/` are legacy JARVIS prototype candidates, not a second source of truth.
