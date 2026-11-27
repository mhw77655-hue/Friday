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
1. Re-fetch current main and the newest main build. At last check, #3675 for SHA `399a97e0cfc290c61447c697bf0d5fce7e6aff39` was queued; #3672–#3674 were intermediate workflow commits. Wait for the newest current-main build to pass all jobs.
2. Follow the signed-release run triggered by that successful build. The earlier #597 run uses the pre-artifact-fallback workflow and may fail publication with the known 403; inspect its outcome, but do not block on it if the newer run is queued.
3. In the newer release run, verify the production Gradle build, signer verification and metadata. Confirm the 30-day `friday-signed-release-*` Actions artifact is uploaded before release publication. If publication gets 403, use the retained artifact and follow the run summary's repository-permission / `FRIDAY_RELEASE_PUBLISH_TOKEN` guidance.
4. Verify release or artifact metadata: package `ai.friday.assistant`, version code strictly greater than 1510, signer fingerprint `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8`, APK SHA-256, and source commit. Inspect APK manifest/signature if tools permit. Never substitute a debug APK.
5. Deliver the verified signed APK for an **in-place update**. Preserve the installed package and app data; do not uninstall or clear data.
6. Use Friday → System → **Export full diagnostic report**. Inspect `friday-device-report-v2`, redact any unexpected sensitive values, and use the evidence to isolate startup, model/provider, STT/TTS, microphone and Shizuku failures. If Friday cannot launch/export, use narrowly filtered Android logs.
7. Verify on the physical handset: launch → typed message → real response → persistence; then push-to-talk → recognizer → one final transcript → same production turn → configured reasoning → voice synthesis → audible playback → interruption → idle. Distinguish native Piper/Kokoro/Nabra voice from Android TTS fallback.
8. Fix phone-only failures using the report, rebuild/sign/update in place, and repeat acceptance. Only after the existing app is functional and speaking should the wider evolving-individual, Shizuku immune-system, Builder → candidate sandbox → REA → bounded repair → independent verification work regain the critical path.


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

### 2026-11-27 — signed production update published; move to handset diagnosis

- Main Android build [#3675](https://github.com/mhw77655-hue/Friday/actions/runs/46046039956) completed successfully for commit `399a97e0cfc290c61447c697bf0d5fce7e6aff39`. Verified job inventory included all six JVM shards, aggregate JVM evidence, Android instrumentation, source audit and verified debug APK promotion.
- The later signed release is now published: [Friday 1.0.601](https://github.com/mhw77655-hue/Friday/releases/tag/friday-v1.0.601). The official release contains `app-release.apk` (label `friday.apk`, 129,721,072 bytes), `friday-release-metadata.json`, and `friday.apk.sha256`.
- APK release asset SHA-256 from GitHub's release asset metadata: `f838f8542acbcb0792a7fcb3fc8c683f0447f497767496baa1b767a85bbdacf3`. Release target commit: `399a97e0cfc290c61447c697bf0d5fce7e6aff39`. The earlier #597 failure was superseded by this later successful release; the 403 is no longer the current blocker.
- Release workflow source computes versionCode as `1000 + GITHUB_RUN_NUMBER` and versionName as `1.0.GITHUB_RUN_NUMBER`; release tag 1.0.601 therefore corresponds to expected versionCode 1601. Treat the release's attached metadata as the definitive verification record; do not infer signer/package solely from the tag.
- Current GitHub release asset is available for an in-place update. Do not uninstall Friday or clear data. The owner must install this update on the existing package; installation and data preservation have not been physically verified.
- Next acceptance: after installation, export **Friday → System → Export full diagnostic report** (`friday-device-report-v2`). Inspect/redact unexpected sensitive values before sharing. Use that report to diagnose app startup, model/provider readiness, typed turn path, mic/STT, voice/TTS and Shizuku. Then run the real audible conversation test.
- The actual phone's private app state is not accessible through repository CI. Do not claim handset acceptance until report and live interaction evidence are observed.
- Canonical delivery plan and APK-bundled plan parity checked in this session: BYTE-IDENTICAL.


### Progress estimate at 2026-11-27 20:26 UTC+02:00

**Immediate objective only: repair installed Friday 1.0.510 and reach a real working voice conversation. Estimated engineering completion: 35%; remaining: 65%.** This is a weighted estimate, not a test result or a percentage of the entire long-term Friday vision.

Completed (approximately 35%):
- Exact installed APK identified and forensic dossier preserved.
- Confirmed source-level PCM16 WAV parsing defect, utterance lifecycle defect and STT offline-preference defect corrected in merged PR #183.
- Exact-head automated validation and subsequent main Android build #3675 passed.
- Signed production update 1.0.601 published with APK, metadata and checksum assets. Release workflow only reaches publication after pinned signer verification.
- Canonical and APK-bundled delivery plans remain byte-identical.

Remaining (approximately 65%):
- Install 1.0.601 as an in-place update over the existing package; verify signer compatibility and preserve app data.
- Obtain and inspect the app's friday-device-report-v2 export (or bounded filtered device logs if the app cannot open).
- Isolate and fix the actual dead-app runtime/cognitive/model/provider issue; static APK and CI evidence do not settle it.
- Verify typed input → production cognitive turn → real response → persisted continuity.
- Verify microphone → STT final transcript → same production turn → configured reasoning response.
- Verify voice model/provider readiness → synthesized audio → audible playback → interruption → idle.
- Repeat on the physical handset after any fixes and record actual acceptance.

**Physical handset acceptance remains 0% proven** because no post-update diagnostic report or live phone test has been observed. This is why the immediate objective is not complete despite a production update being available. The percentage above measures implementation and delivery preparation, not proof that the user's phone works.

The whole living-Friday vision is intentionally not assigned a completion percentage here; it is a much larger open-ended programme and must not be conflated with this repair milestone.
