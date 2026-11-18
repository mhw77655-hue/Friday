# Friday

Friday is an Android-resident developing digital individual. Her identity is not a chat interface, language model, codebase, or cloud worker. Her phone runtime is her local body; hosted compute and the repository workshop are resources she can direct when available.

The owner's authoritative product intent is recorded in [`.ralph/VISION.md`](.ralph/VISION.md) and the curated, provenance-labelled owner excerpts in [`.ralph/OWNER_WORDS.md`](.ralph/OWNER_WORDS.md). The owner's latest explicit instruction always outranks historical documents and assistant summaries. These documents describe intended outcomes and invariants, not a fixed component diagram or developmental checklist.

## Architecture boundary

- **Friday on Android:** persistent identity and continuity, local cognition, presence and perception, memory/experience, permissioned device actions, safe runtime capabilities, self-study, development-intent formation, durable offline request queue and result feedback.
- **Runtime capability growth:** Friday should be able to acquire, compose, adapt and retire safe declarative/restricted capability packages without replacing the APK. Packages use narrow host APIs, explicit permissions, provenance, integrity checks, resource limits, tests and rollback. Arbitrary untrusted DEX/JAR/native code is not the extension model.
- **Repository workshop:** source checkout and edits, coding workers, research, tests, CI, disposable Android emulator, APK construction, signing and trusted promotion belong in the connected repository environment—not inside the installed Android app.
- **Phone/workshop connection:** Friday may direct the workshop through an authenticated, privacy-bounded, phone-initiated request. Requests must survive offline periods and process death, be deduplicated and resumable, and return verifiable commit/test/artifact/outcome evidence. The phone must not contain repository write credentials, worker secrets or signing keys.
- **One continuing individual:** local and cloud resources extend the same Friday. Network loss changes available resources, not her identity or right to continue developing locally.

## Build and test

With JDK 17 and Android SDK components matching the Gradle configuration:

```bash
./gradlew :mobile:app:assembleDebug
./gradlew :mobile:app:testDebugUnitTest
```

The GitHub Actions workflow also runs repository-builder contract checks, owner-vision checks, source inventory/static audit and (once integrated and stable) disposable Android emulator instrumentation. A green JVM test or APK build is not proof of physical-device behaviour. Device-specific claims require evidence from the target handset.

## Current verified state

As of 2026-11-18:

- The main branch contains the Android-native Friday foundation, owner-grounded vision files, a phone-initiated GitHub request/outbox path, repository-side proposal worker, crash-report proposal bridge, and permanent release-signing workflow foundation.
- PR #86 merged the signed-release workflow. No production release is published yet; owner-controlled signing key custody/secrets and the phone-side updater/recovery path remain incomplete.
- PR #82's disposable Android emulator path is still under validation. Its latest observed run compiled APKs but failed before installation because Android's Settings provider was not ready. A readiness correction is being tested separately; do not treat the emulator gate as passed yet.
- PR #83's autonomous repair/promotion path remains open and must be reconciled with the actual emulator dependency and exact-candidate verification gates.
- PR #89's phone-resident source Builder proposal is not accepted as the architecture: it conflicts with the repository-side workshop boundary and its observed CI head has a duplicate `nowMs` argument compile failure.
- The whole-project root-cause findings and dependency-ordered implementation plan are tracked in [PR #90](https://github.com/mhw77655-hue/Friday/pull/90).

This repository is not yet a finished, release-ready Friday. No APK handoff or successful physical-device acceptance is implied by the presence of source code or open PRs.

## GitHub development bridge

The Android app currently supports a user-provided fine-grained GitHub token encrypted with Android Keystore. The documented minimum is Actions: read/write, Pull requests: read, Issues: read/write and Metadata: read for this repository; **do not grant Contents: write**. The phone uses the token to dispatch a workflow and inspect proposals, not to push source or publish releases. The untrusted coding worker has read-only repository access; a separate trusted publisher can create draft PRs only.

Requests and diagnostic evidence are public because the repository is public. The app must obtain explicit consent for each relevant sharing scope and exclude conversations, transcripts, notifications, screen contents, location, contacts, media and unrelated personal data. The token can be revoked at GitHub or removed in Friday Settings.

See [the phone/workshop bridge contract](docs/FRIDAY_GITHUB_PHONE_BRIDGE.md) and [the repository development contract](docs/FRIDAY_REPOSITORY_DEVELOPMENT.md).

## Safety and evidence

- Never put tokens, signing keys or private credentials in source files or public artifacts.
- Do not confuse manifest declarations with granted Android permissions or working capabilities.
- Do not count skipped/cancelled work as successful validation.
- A static scan reports candidates, not semantic verdicts; every consequential finding requires source/context review.
- A draft PR is not a merged repair. A merged repair is not a signed release. A signed release is not proof of a successful phone update.
- Preserve Friday's identity, memory, authorization and recovery state through capability changes, process death and app upgrades.
- Android/OEM rules remain authoritative. Do not promise silent installation, guaranteed background execution, root access or Shizuku privileges that have not been granted.

