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
Release workflow follow-up: corrected the changed-file list formatting in the freshness diagnostic output in commit `b2d1f175970764a9939f9264c8cdab58f4004c15`. The newest exact-main build for that workflow commit is [#3671](https://github.com/mhw77655-hue/Friday/actions/runs/46042860804), queued at the last check. The workflow freshness logic itself is in `a3235b54af4151ff6e2250771d586ba0c2e4f9f4`; re-fetch the current main SHA and newest build before resuming.

### 2026-11-27 — latest main CI green; signed production build underway

- Main build [#3671](https://github.com/mhw77655-hue/Friday/actions/runs/46042860804) for Android source SHA `b2d1f175970764a9939f9264c8cdab58f4004c15` completed successfully. All six JVM shards, aggregate JVM evidence, disposable Android instrumented emulator, whole-repository source audit, debug APK assembly and APK promotion passed.
- Main currently has advanced to `f0cef4f5cfec8bd1b5df30b78d361c8a77f749ca` through documentation-only handoff commits. The release freshness gate permits the verified app build because no Android product/build inputs changed after `b2d1f175970764a9939f9264c8cdab58f4004c15`.
- Important correction: signed-release run [#593](https://github.com/mhw77655-hue/Friday/actions/runs/46043893871) reports overall `success` but **did not build or publish an APK**. Its job log shows it was skipped by the freshness guard because the bundled delivery-plan asset changed after its target SHA. Never interpret a green workflow conclusion alone as a published release; inspect the step outcomes and GitHub Releases.
- The newer signed-release run [#597](https://github.com/mhw77655-hue/Friday/actions/runs/46045218687) passed permanent signing-secret checks, canonical-vs-bundled plan parity, freshness validation, Android SDK setup, pinned build-tools/NDK installation and keystore decoding. It detected Android product changes since the previous release and is currently at **Build the signed production APK**. Signature verification, metadata generation and publication are still pending.
- The latest published release observed before #597 completed remains `friday-v1.0.588`; it predates the PR #183 voice fixes. Do not install that as the voice repair update.
- Existing diagnostics implementation confirmed: `DiagnosticsViewModel.createExportReport()` produces `friday-device-report-v2`, including source revision, app/version/device, model/host bootstrap, subsystem status (Shizuku/mic/STT/TTS/voice), bounded events, up to 50 failure records, crash summary, Android process-exit history and self-diagnosis. It explicitly marks chat, speech transcripts, notifications, screen contents and credentials/tokens as excluded. UI route: Friday → System → Export full diagnostic report. Inspect and redact any unexpected sensitive field before sharing.
- The signed APK is not yet published and no physical-phone test has been performed. Preserve the current installed app and data; do not uninstall or clear data.
### Voice path source audit — after PR #183

- Confirmed the live voice output is actually connected in `FridayEngine`: it constructs `VoiceOrganismHost`, `VoiceOutputPath`, attaches the output to `BodyCoordinator`, and routes `LatencyLayer.setStreamingSpeak` through `VoiceOutputPath.speak(...)`. The legacy `StreamingTts` is the fallback when the isolated habitat is not ready or native synthesis fails. This is source wiring, not proof of handset audio.
- Confirmed `BodyCoordinator.startPlatformRecognition()` selects `ar-EG` or `en-US` from the active language route, clears partial text before recognition, records only the final cleaned result in `SpeechBridge`, and emits one `BodyEvent.SttResult` to close the listening path. Null/blank results still close the listening state.
- Confirmed `VoiceService` registers Piper English (default/priority), Kokoro English, Nabra Arabic, Pocket reference-conditioned English and Android system TTS. Voice models are lazy-downloaded on first synthesis and admitted against the voice habitat resource gate. The default profile prefers Piper English; the Arabic path selects a provider advertising Arabic (`Nabra` or system fallback). Nabra is explicitly described in source as Modern Standard Arabic, not Egyptian dialect.
- Operational implication: first voice use may require network access and storage for a model download (Piper manifest entry is 64 MB; Kokoro int8 103 MB; Nabra Arabic int8 85 MB). If model acquisition/admission fails, the app-side path can fall back to Android `StreamingTts`; diagnostics must distinguish which provider actually spoke. Do not call a successful fallback proof of Friday's native Kokoro/Piper voice.
- `VoiceOutputPath` now uses the merged PCM16 RIFF reader and utterance identity for cancellation. The source path is coherent enough to test end-to-end; next evidence must come from the handset's exported report and actual audible test.
### Release publication blocker and durable APK fallback

- Inspected failed signed-release run [#595](https://github.com/mhw77655-hue/Friday/actions/runs/46044452369) end-to-end. The production Gradle build succeeded in 8m28s; `apksigner verify` succeeded; the actual certificate fingerprint exactly matched the pinned signer `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8`; APK SHA-256 and release metadata generation succeeded. Only `gh release create` failed with `HTTP 403: Resource not accessible by integration`. That run's candidate output was version `1.0.595`, code `1595`, but it was not published.
- Current signed-release run [#597](https://github.com/mhw77655-hue/Friday/actions/runs/46045218687) is still at `Build the signed production APK` at last check. It uses the earlier workflow revision and may hit the same publication 403. Do not call a release available until a release tag and assets are visible.
- Hardened `.github/workflows/release.yml` in commits `36ac88048219aa3bcec3509a67f7499f5077652e`, `e5e507fa3184b032109e1dc9c2fb50e6dd1bd921`, `465337c1137fd1e444792a7ac968b4e319440c08` and `399a97e0cfc290c61447c697bf0d5fce7e6aff39`: after certificate verification it now uploads the signed APK + metadata + checksum as a 30-day Actions artifact before attempting GitHub Release publication; it also supports an optional owner-controlled `FRIDAY_RELEASE_PUBLISH_TOKEN` secret for the release-write API call and writes explicit permission-repair guidance if publishing fails. The publish step binds directly to that optional secret or falls back to `github.token`.
- Latest main build [#3675](https://github.com/mhw77655-hue/Friday/actions/runs/46046039956), SHA `399a97e0cfc290c61447c697bf0d5fce7e6aff39`, was queued at the last check. Older build runs #3672–#3674 cover intermediate workflow commits. Re-fetch current main and use the newest matching build before declaring this workflow fix validated.
- If the next release still receives 403 and no dedicated publisher token is configured, the signed APK will at least be retained as a downloadable Actions artifact. To make a formal GitHub Release publish, repository Actions settings must allow GITHUB_TOKEN read/write Contents permission, or an owner-controlled fine-grained token with Contents: read/write must be stored as `FRIDAY_RELEASE_PUBLISH_TOKEN`. Do not ask for the signing keystore; it is already configured and verified.
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