# Friday installed-app repair — live progress log

**Controlling task:** [Issue #184](https://github.com/mhw77655-hue/Friday/issues/184)
**Forensic source of truth:** [FRIDAY_1.0.510_INSTALLED_APK_REPAIR_DOSSIER.md](FRIDAY_1.0.510_INSTALLED_APK_REPAIR_DOSSIER.md)
**Full vision and chronological plan:** [`.ralph/FRIDAY_DELIVERY_PLAN.md`](../.ralph/FRIDAY_DELIVERY_PLAN.md)
**Installed artifact:** [`friday-v1.0.510`](https://github.com/mhw77655-hue/Friday/releases/tag/friday-v1.0.510)

This file is the handoff log. Every session must read this file, the dossier, and Issue #184 before asking the owner for anything. Do not ask for the v1.0.510 APK again by default; retrieve the exact release asset from GitHub and verify the recorded digest.

## 2026-11-27 — voice repair merged; main validation started

### Completed
- Reviewed the complete PR #183 diff.
- Confirmed exact PR head `03ce04c641295c1b20b8a58d0ab99a97a6ec66bc`.
- Confirmed exact-head Actions run [#3662](https://github.com/mhw77655-hue/Friday/actions/runs/46039764560) completed successfully:
  - six JVM shards passed;
  - aggregate JVM evidence passed;
  - disposable Android instrumented emulator passed;
  - whole-repository source inventory/Kotlin vision audit passed;
  - debug APK assembly passed;
  - verified debug APK promotion passed;
  - obsolete-run cancellation passed.
- Merged [PR #183](https://github.com/mhw77655-hue/Friday/pull/183) using squash after checks passed.
- Merge commit: `add503a53ffca183677815311ffa2c29b6df9c9e`.
- The merged changes repair RIFF/WAV little-endian PCM16 parsing, use utterance identity for cancellation/state cleanup, make Android offline STT preference conditional on actual on-device recognizer availability, and strengthen typed UI acceptance to require a new assistant response.
- The exact PR #183 code changes are now in main. This is source/CI evidence, not yet proof of audible playback on the owner's phone.

### Main CI state at last check

- PR #183 merge commit is `add503a53ffca183677815311ffa2c29b6df9c9e`.
- After the merge, the forensic dossier and canonical/APK-bundled roadmap were updated on main so the work survives future sessions. Those documentation/plan commits advanced main beyond the merge commit.
- Main build #3667 for the merge commit was queued; it is now superseded by subsequent main commits.
- At the latest check, main build [#3669](https://github.com/mhw77655-hue/Friday/actions/runs/46042520902), SHA `bd3266e27f791c6720ab395335183a79f6c860dc`, was queued. Build #3668 covered the preceding dossier update. Re-fetch current main and find the newest build for its exact SHA before continuing; never assume #3667 is the latest run.
- Earlier signed-release workflow runs #586/#588 targeted SHA `b8fbbc0ffa0ec1a039fe37baa574f078ed6470c1`, not the PR #183 merge. They do not prove a release containing the voice fixes exists.
- The signed-release workflow triggers after a successful main build. Follow the build for the current main SHA and its subsequent signed-release run. Verify signer, package identity, increasing version code, APK digest and canonical/bundled plan parity. It intentionally does not publish a debug APK as a production update.

### Release freshness correction

A release-pipeline edge case was found and fixed. The signed-release workflow previously required the exact CI build SHA to equal current main. Documentation-only handoff updates after a valid Android build could therefore make the release skip even though no APK inputs changed. The freshness gate now allows an ancestor CI build when **no Android product/build inputs** changed after it; it still rejects non-ancestor builds and any later changes under `mobile/`, `gradle/`, root Gradle build/settings/properties, `jniLibs/` or `local-repo/`. This lets the verified app build publish without weakening protection against stale APK code.

The workflow change is commit `a3235b54af4151ff6e2250771d586ba0c2e4f9f4`. Its exact-main build [#3670](https://github.com/mhw77655-hue/Friday/actions/runs/46042805074) was queued at the last check. Build [#3669](https://github.com/mhw77655-hue/Friday/actions/runs/46042520902) for the Android code + plan SHA `bd3266e27f791c6720ab395335183a79f6c860dc` was also queued; because only release-workflow/docs changes followed it, the new freshness gate should permit its release if #3669 passes. Re-check actual outcomes—do not infer release success from queue state.
### Next actions — continue in this exact order
1. Re-fetch current main and run #3667. Inspect failures and fix them; do not claim main is green until the merge SHA has a successful full build.
2. Follow the signed-release workflow triggered by the successful main build. Verify the release artifact's package `ai.friday.assistant`, versionCode greater than 1510, pinned signer fingerprint `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8`, APK SHA-256 and metadata. If secrets/configuration prevent release, record the exact blocker; do not fabricate a signed APK or substitute a debug APK.
3. Use the existing in-app diagnostic export first: open Friday → System → **Export full diagnostic report**. The current `DiagnosticsViewModel.createExportReport()` already emits a privacy-conscious JSON report with app/device/version, model/host bootstrap state, runtime/subsystem state, Shizuku/microphone/STT/TTS/voice-process status, bounded event history, up to 50 redacted failure records, crash journal, Android process-exit history and self-diagnosis. It explicitly excludes chat messages, speech transcripts, notification contents, screen contents, credentials/tokens and personal files. Inspect the report schema and redact any unexpected sensitive value before sharing. If Friday cannot open or export, fall back to a narrowly filtered Android logcat/device report. The required evidence still includes startup/crash state, typed-turn result, private model/voice readiness, recognizer/provider/language, microphone permission and Shizuku manager/server/authorization/Binder status.
4. Deliver the verified signed APK as an **in-place update**. Preserve package, signer and app data. Do not uninstall or clear data.
5. Verify typed input -> production turn -> real response -> persistence on the phone.
6. Verify push-to-talk -> recognition -> final transcript exactly once -> same production turn path -> configured reasoning -> voice model load -> PCM16 synthesis -> audible output -> interruption -> idle state.
7. Fix phone-only defects revealed by evidence and repeat build/sign/install/acceptance. Keep CI/emulator evidence separate from physical handset evidence.
8. After the installed app is working and speaking, continue the wider Friday individual/evolution, Shizuku immune system and live Builder -> candidate sandbox -> REA -> bounded repair -> independent verification programme. The wider vision is not dropped; it must not displace the installed-app repair critical path.

## Permanent artifact identity — do not request re-upload by default
- Release tag: `friday-v1.0.510`
- Source commit: `24e653589fa623ce5819387a9179dfe878a0a720`
- Package: `ai.friday.assistant`
- Version code: `1510`
- APK SHA-256: `e8446047acb04b83d3d57d0f332ee59c8f86a0f32c1e710c941b45d118a7398e`
- Signing certificate SHA-256: `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8`

## Evidence boundary
- Merged PR and green CI prove source changes compile and pass the recorded automated checks.
- Emulator instrumentation is not a physical-phone test.
- Static APK inspection cannot see the owner's private app storage or runtime grants.
- A published signed release is not proof it has been installed.
- A transcript is not audible voice output.
- Do not report the installed Friday as fixed until the owner's actual phone passes the typed and full voice acceptance above.