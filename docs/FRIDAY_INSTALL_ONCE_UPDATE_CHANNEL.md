# Friday install-once update channel

**Purpose:** the first production-signed Friday APK is the continuing Android body. Normal development must not require uninstalling Friday, clearing app data, manually finding CI artifacts, or replacing her identity.

## Implementation status — 2026-11-21

The repository now contains the first working update path:
- One-time and daily WorkManager release discovery, constrained to unmetered connectivity, adequate battery and storage.
- Background download and verification from the official GitHub Releases endpoint.
- Package ID, monotonic version, APK SHA-256 and signing-certificate checks.
- Durable local state for the staged APK so Settings can recover it after process restart.
- A Settings screen to review the staged version and request installation.
- A second digest/package/version/signer verification immediately before creating an Android PackageInstaller session.
- A status Activity that handles Android's pending-user-action confirmation and reports the platform result.

A permanent-signed seed release, Friday 1.0.543, was published on 2026-11-25 from commit `65aaf89168c07ee1dd25a6989664ade3143ea68f`. The published APK asset is 129,738,880 bytes with SHA-256 `3d618dedd6fa083b38026a97f2cfe49fc2898577f7378ecfabc23df0defc29c6`; the release workflow states that its certificate was checked against the pinned production fingerprint. See [Friday 1.0.543](https://github.com/mhw77655-hue/Friday/releases/tag/friday-v1.0.543).

The native update recovery changes in PR #164 were merged to `main` in `26f8f7528969c28a926e409c87ec8dd797c919fc`; PR #165 added protection against newer releases replacing a failed recovery APK and was merged in `b659e68f97c09a605a7276af37ebb6a5be6dda36`. Both PRs passed exact-head full CI, including Android emulator instrumentation. A follow-on audit found the manual Settings updater still bypassed the recovery-aware state-store methods; PR #166 now routes it through the same guards and adds forward-recovery semantics: failed release APKs and their identity/failure records are archived separately, while only a strictly higher-version signed candidate may be staged as a repair. The failure ledger is cleared only after that repair passes the post-install identity check. PR #166 is awaiting exact-head CI. The seed release 1.0.543 predates these merged changes, so a new signed release must be produced from a fully validated current `main` before it can represent this code. These automated gates do not prove a production-signed same-key in-place upgrade, app-data continuity, owner-device installation, or automatic last-known-good rollback. The existence of a published signed APK also does not prove that two independent offline keystore backups have been made and tested. Do not describe the update channel as fully accepted until those remaining gates pass.

## Release identity is locked before the first install

- Package ID: ai.friday.assistant.
- The owner explicitly authorized the clean identity and confirmed the previous APK was deleted. No compatibility with the former package is required.
- Production APKs use one permanent owner-controlled signing key and monotonically increasing version codes.
- The private signing key is never committed to GitHub, shipped in the APK, or exposed to source-editing workers.
- CI's disposable debug/release smoke keys are validation-only. They are never install candidates for the owner's permanent phone.
- Before first install, verify the production certificate fingerprint, back up the key offline, and prove an in-place update from one production-signed APK to a second APK with preserved app-private data.
- The first installation is a clean install of the permanent-signed Friday seed. After that first install, every update must retain this package ID and the same permanent signing identity.

## Two evolution lanes, one Friday

### Lane 1: capability and knowledge evolution without replacing the APK

Where the installed host already supports the artifact format, Friday may fetch versioned, signed and compatible resources such as model weights, speech/vision assets, knowledge packs, evaluation sets, and declarative capability graphs. Validate source, hash, format, compatibility, resource budget and license; stage atomically; retain the previous known-good version; and test before activation.

These resources do not get arbitrary access to Android APIs. Downloaded executable code, DEX/JAR/native libraries, scripts with ambient host access, or permission-changing components are not treated as harmless data packs. New native code or platform integration goes through Lane 2.

### Lane 2: signed Android body updates

GitHub Actions builds the exact main revision, runs the required test lanes, and publishes a production-signed APK plus metadata containing package ID, version code/name, source commit, certificate fingerprint, APK SHA-256, supported Android/API/ABI constraints and release notes.

The installed updater:
1. Checks the official Friday release endpoint on app start and a conservative periodic schedule.
2. Checks channel, package ID, monotonic version, Android/API/ABI compatibility and available storage.
3. Downloads to app-private staging; verifies metadata and APK SHA-256; never installs a partial or mismatched artifact.
4. Uses Android PackageInstaller to stage and commit an in-place update. Android verifies APK signing compatibility at installation. The client handles pending user action and process death.
5. Records the offered revision, installed revision, install result and post-update health state in the diagnostic journal.
6. On first launch after replacement, runs bounded health checks and confirms identity/experience stores are readable. A failed health check marks the release unhealthy and initiates recovery using the last known-good signed package when Android permits; never silently wipe data.

## Minimize owner involvement without inventing privilege

Preferred order:
1. An eligible self-update through Android PackageInstaller, requesting no user action only where Android's documented conditions are satisfied.
2. A previously owner-authorized Shizuku shell installation route, when Shizuku is actually connected and the device permits it.
3. The standard Android package-installer confirmation flow.

The updater must handle STATUS_PENDING_USER_ACTION and present one clear approval, not loop or claim silent success. Android can still require confirmation. Fully managed device-owner deployment can install without user interaction, but it changes device-management posture and is optional—not silently enabled. Root, Shizuku, Accessibility and ordinary permissions are not interchangeable.

## Build and deployment states

source changed -> CI built -> tests passed -> production signature verified -> release published -> phone discovered -> artifact staged -> platform install accepted -> new process healthy -> installed revision reported

Every transition has explicit evidence. A GitHub green check is not proof that the phone updated. A download is not an installation. A successful package-manager callback is not proof that Friday's identity and experience survived.

## Required acceptance tests before first install

- The first APK is the permanent production-signed release, not a debug artifact or disposable CI-key APK.
- The production signing key has two verified offline backups and the certificate fingerprint is pinned in GitHub Actions secrets.
- A second APK with the same package ID and key updates the first in place without uninstalling or clearing app data.
- A simulated interrupted download, invalid checksum, wrong signer, downgrade, incompatible ABI/API, low-storage condition and interrupted install all fail safely.
- Update preserves identity, memory, owner settings, capability provenance and pending work.
- The diagnostic report exposes installed version, source commit, signer fingerprint, update channel state and last update outcome.
- The owner can decline or defer an update; Friday remains usable on the last known-good version.

## What this means for the first phone installation

Never install a CI debug APK as the permanent body. The production-signed seed already exists as [Friday 1.0.543](https://github.com/mhw77655-hue/Friday/releases/tag/friday-v1.0.543). Before asking the owner to install it, verify the offline signing-key backups and complete a same-key in-place upgrade plus data-continuity test on an emulator. Then install the production seed once on the phone. Subsequent source improvements should flow through the signed update channel; uninstalling or clearing app data must not be a routine development step.
