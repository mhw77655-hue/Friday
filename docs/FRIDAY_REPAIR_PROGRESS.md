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


### 2026-11-27 20:32 UTC+02:00 — resume verification and correct the active blocker

This entry supersedes any earlier statement that release publication is still blocked by run #597.

- Re-fetched PR #183: merged, merge commit `add503a53ffca183677815311ffa2c29b6df9c9e`.
- Re-fetched main build [#3675](https://github.com/mhw77655-hue/Friday/actions/runs/46046039956): completed successfully for `399a97e0cfc290c61447c697bf0d5fce7e6aff39`. The six JVM shards, aggregate JVM evidence, instrumented emulator, source audit, debug APK build and promotion all passed.
- Re-fetched signed-release run [#597](https://github.com/mhw77655-hue/Friday/actions/runs/46045218687): it failed only at the final release upload/create step with `HTTP 403: Resource not accessible by integration`. Signing-secret checks, release APK build and pinned certificate verification passed. The verified signer fingerprint in its logs is `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8`.
- Crucial correction: the #597 publication failure was subsequently superseded. GitHub release [Friday 1.0.601](https://github.com/mhw77655-hue/Friday/releases/tag/friday-v1.0.601) exists and has uploaded APK, metadata and checksum assets. Release API reports APK asset digest `sha256:f838f8542acbcb0792a7fcb3fc8c683f0447f497767496baa1b767a85bbdacf3`, size 129,721,072 bytes, and target commit `399a97e0cfc290c61447c697bf0d5fce7e6aff39`. The release workflow's #597 build logs independently show the pinned signer fingerprint; the later release notes say the certificate is checked against the same pinned fingerprint. Do not continue treating 403 as the active blocker.
- Release workflow computes versionCode as 1000 + run number and versionName as 1.0.run_number; the release tag is 1.0.601 (expected code 1601). Treat attached metadata as authoritative when it can be retrieved; do not infer more than the release record proves.
- GitHub release assets are published, but **physical installation and data preservation are not verified**. Do not claim Friday is fixed or speaking.
- The next critical step is handset acceptance: install 1.0.601 as an in-place update over 1.0.510 without uninstalling or clearing data, open Friday, then export Friday → System → Export full diagnostic report (`friday-device-report-v2`). That single redacted report is the next required evidence for diagnosing private runtime/model/provider/permission state. If the app cannot open/export, collect a bounded filtered Android log instead.
- After evidence arrives, fix actual runtime/cognitive/provider blockers, rebuild/sign/update, and test typed response plus audible voice end-to-end. CI/emulator success remains distinct from physical-device acceptance.

**Current weighted estimate for the narrow objective (repair the already-installed Friday 1.0.510 and reach a real voice conversation): 35% engineering/delivery preparation complete; 65% remains.** Physical handset acceptance itself remains 0% proven. This is a subjective weighted engineering estimate, not a measured test metric. It is not a completion estimate for the whole living-Friday vision.



### 2026-11-27 — resumed production-path audit: model bootstrap is a material capability gate

Re-opened the current main source after the 1.0.601 release and traced the Android composition root, production turn assembly, model manager, resident-model worker and BodyCoordinator.

**Confirmed source facts**
- `TurnPathAssembly.assemble()` constructs `CloudModelRouter(emptyList())`. The repository explicitly treats this as "none configured today"; the cloud router returns "No cloud providers are configured." There is no hidden cloud fallback in this production composition.
- The packaged `capability_manifest.json` explicitly reports `cloud_reasoning_provider_configured=false` and `offline_model_installed_and_loaded=false`. Its semantics correctly distinguish source-wired code from installed/configured runtime capability.
- The Android production inference path is process-isolated through `ProcessIsolatedModelBackend` and `:inference`; it requires a real GGUF file in Friday's private model storage and a successful resource-admitted load. Native libraries alone do not provide a reasoning model.
- `FridayEngine` enqueues `ResidentModelBootstrapWorker` only after the complete composition root is published and the readiness gate is marked ready. The worker waits for the matching current-build host report, checks native inference availability, chooses a pinned resident candidate using live resource admission, verifies the downloaded/installed file, requests the resident model and records `MODEL_READY` only after a successful load.
- The initial WorkManager request itself is unconstrained so an already-installed model can load offline. However, when weights are absent, the worker's own `isSafeToDownload()` gate requires an unmetered network and charging, with storage/battery safety checks. Until those conditions occur and the model loads, arbitrary local reasoning is not available.
- The production turn pipeline explicitly receives `modelAvailable` and a model-free seed reply function. Therefore a missing model should be represented as degraded capability, not mistaken for proof that the entire app or voice transport is dead. The physical UI/turn outcome still needs device evidence.
- BodyCoordinator's typed text and final STT transcript converge on the same serialized input queue and ordinary cognition route. Phone-action interception is upstream of the same normal response path. The previously fixed STT/WAV defects remain merged in PR #183.

**What this does and does not establish**
- It establishes a concrete reason Friday 1.0.510 may feel unintelligent even if the UI and transport are alive: the original APK manifest advertised no installed model and no configured cloud reasoning, and the current architecture still requires successful private model bootstrap for broad offline reasoning.
- It does NOT prove the actual handset lacks a downloaded model today, that the bootstrap worker failed, or that this is the only reason the owner describes the app as dead. Only the device report can establish private storage, worker state, model load, permissions, process exits and actual turn failures.
- No speculative cloud provider, fake model-ready state, or automatic credential collection was added. A cloud path must be a real provider with explicit user-controlled configuration and secure credentials; the local model path must be verified by digest, integrity, admission and live inference.

**Next engineering sequence**
1. Obtain the redacted `friday-device-report-v2` from Friday 1.0.601, or bounded filtered logcat if the app cannot export it.
2. Compare the report's engine readiness, host-bootstrap report, resident-model bootstrap state, model file inventory/digests, inference service health, provider health, BodyCoordinator/LatencyLayer turn trace, voice state, permissions and Shizuku state.
3. Fix the first evidenced failure in the production path, add a regression test, run the full exact-head CI suite, then publish a same-signer in-place update.
4. Acceptance remains physical: typed prompt → non-empty production answer → persisted turn; then microphone → final STT transcript → same turn → actual reasoning provider → synthesized audio → audible output → interruption → idle.

**Release verification refresh:** GitHub release API re-confirmed `friday-v1.0.601`, target commit `399a97e0cfc290c61447c697bf0d5fce7e6aff39`, APK asset `app-release.apk`, 129,721,072 bytes, digest `sha256:f838f8542acbcb0792a7fcb3fc8c683f0447f497767496baa1b767a85bbdacf3`; metadata and checksum assets are also published. This is still not evidence that the owner installed it.

The narrow repair objective remains **35% engineering/delivery preparation complete; physical handset acceptance 0% proven**. The source audit improves diagnosis but does not substitute for the missing handset report or acceptance.



### 2026-11-27 — GitHub-native inspection of uploaded log.zip

The uploaded root-level `log.zip` was located directly in the public repository. Rather than asking the owner to upload it again, a repository-local GitHub Actions workflow was added at `.github/workflows/analyze-device-logs.yml` to validate and inspect the ZIP on GitHub's own runner.

- Analyzer run: https://github.com/mhw77655-hue/Friday/actions/runs/46053115518
- Run status: successful.
- Archive integrity: valid ZIP.
- Archive inventory: 3 file entries.
- Parsed text sources: 2.
- Total uncompressed bytes inspected: within the workflow's 200 MiB safety bound.
- Diagnostic matches: 2,454 before deduplication and prioritization.
- Raw archive contents were not copied into the repository or printed in the first run's job log; a second pass is configured to emit only a privacy-filtered diagnostic report into the job output for direct review.

The workflow rejects unsafe archive paths, enforces compressed/uncompressed size and entry-count limits, recursively inspects nested ZIPs to bounded depth, and redacts common credentials, email addresses, IP addresses, device identifiers and app-private paths in excerpts. Redaction is best-effort, not a guarantee; the source archive itself remains in the public repository and may contain sensitive system data.

This establishes that the archive can be processed entirely through GitHub Actions; no manual re-upload or local extraction is required. Root-cause analysis is pending retrieval and correlation of the prioritized diagnostic excerpts.



### 2026-11-27 — targeted log correlation pass queued

The first ZIP pass confirmed 3 archive entries, including a nested device log (37,558 lines / 5,985,183 bytes) and Friday diagnostic JSON (920 lines / 36,021 bytes). A focused second pass has now been added to emit the structured Friday report fields and exact `ai.friday.assistant` / `FridayShellService` / `ShizukuServiceStarter` timeline, rather than letting the many unrelated Shizuku/system messages crowd out Friday-specific signals. The workflow emits privacy-filtered excerpts only.



The focused pass is being rerun under an isolated Actions concurrency key because earlier queued analyzer runs were not being scheduled promptly. The first pass's key interpretation correction is already established: Android `ApplicationExitInfo.REASON_PACKAGE_UPDATED` is numeric reason 16, so the three Friday process exits at 20:42:59 local time are update-related lifecycle exits, **not crashes**. The prior diagnostic phrase that grouped `REASON_16` with crash/ANR/resource exits is therefore an exporter classification defect, not evidence of a Friday crash. Android's official constant table confirms this mapping.



**Correction to the preceding note after source verification:** Android `REASON_16` is indeed `REASON_PACKAGE_UPDATED`, but the diagnostic's abnormal-exit count is computed by an explicit allowlist of ANR, Java crash, native crash, low-memory and excessive-resource reasons. Therefore the reported "1 of 12" count is consistent with the one `NATIVE_CRASH` record; the three `REASON_16` package-update exits were included only in the detail list and were not counted as abnormal. The actual diagnostics issue is narrower: `DiagnosticRunner.probeApplicationExits()` does not label `REASON_PACKAGE_UPDATED` by name, so it prints the opaque `REASON_16` label. The separate `ProcessExitDiagnostics.reasonName()` already maps it correctly. Do not treat this as a false crash count.



### 2026-11-27 — first device-report findings (GitHub-extracted)

The structured report from the focused GitHub Actions run establishes the following live handset state for Friday **1.0.601 / version code 1601**, source revision `399a97e0cfc290c61447c697bf0d5fce7e6aff39`:

- Device model `RMX3472`, SoC `SM6375`, Android app memory class 384 MB; device total RAM about 7.76 GB, available RAM about 2.35 GB, app storage free about 24.6 GB.
- Battery 67% and charging; Wi-Fi active; Android did not flag the device as low-memory.
- Friday microphone permission is granted, speech recognition reports active, and local TTS reports ready with high-quality `en-gb-x-gba-local`.
- Friday's voice process is bound to the app process, ping 1 ms, zero restarts at capture.
- Friday's accessibility service is enabled, battery optimization is ignored, and Shizuku binder is reachable; subsystem summary says Shizuku is running and granted.
- **The local reasoning model is not loaded.** Runtime selected `LLAMA_CPP`, but the report says "no model confirmed loaded." Model bootstrap is `WAITING_FOR_HOST_CAPACITY`, and the pinned-model recommendation is zero bytes.
- The host-bootstrap report recorded Android thermal status 3 at boot. Friday maps Android thermal statuses 3–6 to its critical bucket, and `NanoSymbioticPolicy.admitModel()` refuses a model when thermal level is at least 2. Available RAM and free storage in the exported snapshot are above that policy's hard minimums, so thermal pressure and/or CPU load are the remaining likely gate(s); the exact live values are being extracted in a follow-up pass.
- The Shizuku system log explicitly shows `ShizukuServiceStarter` starting `ai.friday.assistant.FridayShellService` at 20:44:02.716 and sending a binder to the Shizuku manager at 20:44:02.992. This proves the service-start request and binder handoff occurred; it does not by itself prove Friday's client completed every subsequent privileged operation.
- Android's process-exit history includes a real native crash of `ai.friday.assistant:voice` (reason code 5, status 6, trace available) at 2026-11-25 21:47:35 UTC, while the three exits at 2026-11-27 18:42:59 UTC (reason 16) are package-update exits, not crashes. The report's "1 of 12" abnormal-exit count is therefore consistent with the single historical native crash.
- The system log also records an ANR for the Shizuku manager package at approximately 20:43:37 local time. It occurs before the captured log window and is not evidence of a Friday ANR.

**Initial diagnosis:** the phone's permissions, microphone, STT, TTS, Friday voice-process binding and Shizuku binder are all reported healthy at capture. The strongest current explanation for Friday's lack of general reasoning is the model-admission/bootstrap gate: the native backend is selected but no resident model is loaded because the host-adaptation policy is refusing model admission. This is now a much narrower and evidence-based target than the earlier broad "dead app" uncertainty. It is not yet proven that thermal pressure is the only blocking input; the follow-up extracts exact CPU/thermal values and active diagnostic findings.

The historical voice native crash needs its trace inspected separately; it is not the same event as the 1.0.601 package-update process restarts.



### 2026-11-27 — full structured device report and cross-system findings

The second focused GitHub Actions pass completed successfully (run https://github.com/mhw77655-hue/Friday/actions/runs/46053755389). It extracted the structured Friday report fields and the exact Friday/Shizuku service timeline from the nested log.

**Confirmed live state at capture**
- App identity: Friday 1.0.601, version code 1601, exact source revision `399a97e0cfc290c61447c697bf0d5fce7e6aff39`.
- Host: RMX3472 / SM6375, 8 CPU cores, Android app memory class 384 MB, 7.76 GB total RAM, 2.34 GB available, 23.47 GB app storage free, battery 67% charging, validated Wi-Fi. Android does not report low-memory state.
- Host thermal status is `3` (Android `THERMAL_STATUS_SEVERE`). The Android resource adapter maps status 3–6 into Friday's critical thermal bucket. `HostBootstrapPolicy` only permits local model loading at raw thermal status 0 or 1; `NanoSymbioticPolicy.admitModel()` blocks every model at thermal level >=2.
- The resident-model bootstrap is `WAITING_FOR_HOST_CAPACITY`; its recorded reason is live RAM/CPU/thermal admission, and host bootstrap recommended model size is 0 bytes. Free storage and available RAM are above their hard minimums, so severe thermal pressure is an evidenced blocker, not a storage shortage. The exact capture does not include a separately serialized CPU-load value, so do not claim CPU was or was not contributing.
- Model runtime: `LLAMA_CPP` selected, but **no model confirmed loaded**. The primary pinned resident candidate is a 1.56 GB MiniCPM5 2B GGUF; the smallest pinned fallback is a 517 MB Qwen3.5 0.8B GGUF. The bootstrap worker's live admission must approve a candidate before it downloads/loads. Do not bypass the severe-thermal safety gate; retry after the phone cools and verify that the 517 MB fallback can be admitted.
- The app's 384 MB Android memory class is not itself the NAE hard rejection criterion. The direct policy rejection is thermal status >=2; available RAM in this snapshot was 2.34 GB and private storage 23.47 GB.
- Shizuku: Friday reports binder reachable and granted. The system log shows Shizuku's server sending a binder to UID 10276 / PID 2575 at 20:44:01.130; Friday's voice-process report identifies the app PID as 2575. At 20:44:02.716 Shizuku's `ServiceStarter` starts `ai.friday.assistant.FridayShellService`, then sends the manager binder at 20:44:02.992. This is strong evidence the Shizuku-to-Friday service path was invoked and a binder was handed to Friday's process; it does not prove every privileged command succeeded.
- Voice: microphone permission granted, speech recognition reports active, TTS initialized and ready (`en-gb-x-gba-local`, high quality), voice process bound (voice PID 5095, app PID 2575), 1 ms ping, zero restarts at capture. These are readiness signals, not proof of a real STT-to-TTS audible round trip. The report itself explicitly says STT and TTS still require feature-level tests. Hotword is unknown; manual tap-to-listen only.
- UI thread probe passed at 21 ms. Memory/storage/private-storage/network checks passed. Process PSS was 120 MB, native heap 30 MB. Individual-memory counters show 2 experiences and 6 associations, but a write/restart/retrieval cycle remains unproven.
- Active diagnostics: 22 findings, 2 errors, 3 warnings. The errors are the historical abnormal process-exit finding and the ENGINE failure surface. Warnings include hotword unknown, model runtime with no model loaded, and speech recognition not proven end-to-end.
- Process-exit history: one genuine native crash of `ai.friday.assistant:voice` (reason 5, status 6, trace available) at 2026-11-25 21:47:35 UTC. The three 2026-11-27 18:42:59 UTC exits with reason 16 are Android package-update exits, not crashes. The count of 1 abnormal exit out of 12 is consistent with the single native crash. The exported JSON says the native trace is available but contains no excerpt; the archive does not include the trace itself.
- System log records an ANR for `moe.shizuku.privileged.api` around 20:43:37 local time, before the captured log window. Shizuku is later reachable and successfully starts FridayShellService. Do not attribute this ANR to Friday.

**Second confirmed defect to investigate**
- Friday's failure evidence records an ENGINE error at 2026-11-27 18:43:09.511 UTC (20:43:09.511 local): `IllegalStateException: Unknown inference service operation.`
- Stack: `ProcessIsolatedModelBackend.clientMessenger$lambda$0(ProcessIsolatedModelBackend.kt:46)`.
- In the exact 1.0.601 source, the client throws this after receiving an error response from `InferenceService`; that service returns this message only when it receives a Messenger `message.what` outside its supported `MSG_LOAD=1`, `MSG_GENERATE=2`, `MSG_UNLOAD=3` operations.
- This proves an invalid/unknown IPC operation reached the inference service, but the current report does not include the actual `message.what` value or caller. It is not yet safe to guess whether this is an unexpected client message, protocol mismatch or stale/incorrect Messenger endpoint. Next source fix should log the numeric operation, request ID and sender-side operation at the IPC boundary, add a regression test for unknown messages, then reproduce on device. Do not silently swallow it.
- Separate historical model-load failures report the resident weights missing or the in-app runtime unavailable. They are retryable and consistent with the current no-model state.

**Root-cause assessment**
The handset is not globally dead: the UI, permissions, Shizuku binder, Friday shell-service launch, voice process binding, STT recognizer initialization and TTS initialization are all present. The principal reason Friday lacks general reasoning is now evidenced: no GGUF model is loaded, and severe device thermal status causes Friday's host-adaptation/resource gate to refuse local model admission. A second inference IPC protocol error is recorded and must be fixed/diagnosed independently. The historic voice native crash remains a separate follow-up.

**Next engineering actions**
1. Inspect the IPC protocol at the exact release revision; instrument and test the unknown operation rather than guessing.
2. Verify the bootstrap retry schedule and model candidate admission after the device returns below severe thermal status; prefer the pinned 517 MB fallback when the 1.56 GB resident model cannot fit.
3. Keep the severe thermal guard. Add a real cloud reasoning provider only through an explicit, secure user-controlled configuration path; Wi-Fi availability alone is not a configured provider.
4. Retrieve the 2026-11-25 voice native-crash trace if Android still retains it, then test microphone capture → STT → model response → TTS audio → actual playback.
5. Run physical typed and audible acceptance; diagnostics alone do not prove the user-facing experience.

**Privacy note:** `log.zip` remains in the public repository. The Actions report is redacted on a best-effort basis, but the original raw ZIP is not redacted by this workflow and may contain private system identifiers or other sensitive details. It should be moved to a private location or removed after the investigation if the repository is intended to remain public.



### 2026-11-27 — Owner clarified the full operating model: local memory, cloud task execution, and app evolution without routine APK replacement

This clarification supersedes any assumption that a local reasoning model is installed or required. The owner explicitly states that there are no models on the phone. Treat Friday as **without an installed local LLM** until a fresh live device report proves otherwise. Cloud reasoning must work without a local model.

Three new controlling work items were opened:

- **Issue #189 — Local-memory / task-scoped Cloud AI:** https://github.com/mhw77655-hue/Friday/issues/189
  - Friday owns all identity, memory, conversation history and continuity locally.
  - A local retrieval/task compiler selects only the minimum relevant memory and source artifacts for each cloud task.
  - Cloud receives an explicit work order and bounded evidence, not the entire conversation or memory.
  - Results return as structured task outputs; Friday validates, stores and decides what becomes memory.
  - Multi-step jobs use compact checkpoints, stable artifact references, hashes and provider caching where supported. No claim that caching makes input free.
  - Must support Cloud AI with no on-device model installed.

- **Issue #190 — Update-without-reinstall evolution runtime:** https://github.com/mhw77655-hue/Friday/issues/190
  - Stable Android host/kernel plus signed, versioned evolution packages for declarative UI, workflows, task definitions, capability manifests, resources and sandboxed interpreted modules.
  - Atomic activation, last-known-good rollback, compatibility checks, signatures, key rotation, audit, staged rollout and explicit capability grants.
  - Minimize bandwidth through content-addressed artifacts, delta/chunk delivery, compression, deduplication, resume and Wi-Fi/metered-data controls.
  - Most substantial feature and experience changes should not need a complete APK reinstall when the installed host already contains the required runtime primitives.
  - Be explicit: native Android code, manifest permissions or new platform integrations that the host does not contain still require a signed host update. Do not evade Android or Play restrictions by downloading DEX/JAR/native code outside approved distribution. See official Android and Play references in Issue #190.

Related existing work:
- #186: ChatGPT plan as an explicit opt-in provider for normal conversation.
- #188: secure, live, read-only inspection bridge from the actual phone to ChatGPT Cloud.
- #189: task-scoped cloud reasoning with local memory ownership.
- #190: remote evolution without routine host APK replacement.

The intended system is now explicit: Friday compiles a task locally, sends only necessary context to cloud reasoning, receives a result, validates it locally, and evolves through signed compatible packages. Cloud AI is a worker, not the owner of Friday's memory. GitHub is the engineering collaboration and artifact ledger. The phone remains the trust and identity boundary.

**No new physical-device capability is claimed by opening these issues.** They are architecture requirements and implementation work, not delivered features.


### 2026-11-27 — Owner clarified cloud AI provider and zero-local-model constraint

The owner explicitly corrected the architecture:
- **There are no reasoning models installed on the phone.** This is a hard current-state fact. Do not assume GGUF weights, a local LLM, local embeddings or local model inference are present. Cloud reasoning must be able to work without them.
- The requested external AI worker is **Anthropic Claude**, not GitHub Cloud. GitHub is the repository/workspace and engineering evidence ledger; Claude is a separate cloud reasoning provider.
- Friday's memory and conversation history must remain local and authoritative. Claude receives a bounded task capsule, not the entire conversation or full memory. A deterministic local retrieval/task compiler selects the minimum relevant context and records exactly what was sent.
- Multi-step work resumes from compact checkpoints and stable artifact hashes; do not replay the whole history. Prompt caching can reduce repeated-prefix costs but does not make usage free or remove usage limits.
- The user wants rapid evolution without downloading/reinstalling a full APK for every change. Issue #190 defines a stable Android host/kernel plus signed declarative UI, workflow, resource and sandboxed extension packages, atomic activation, rollback and bandwidth-efficient delivery. New native capabilities or missing host primitives still require a signed host update; no policy-evasion code loading.
- The intended outcome is autonomous progress without repeated manual instructions. One-time account authorization, device pairing and Android permission grants remain unavoidable trust-boundary actions; the engineering should automate all subsequent task packaging, execution, validation, logging and updates within those grants.

New controlling issue:
- **#191 — Integrate Anthropic Claude as Friday's task-scoped cloud worker:** https://github.com/mhw77655-hue/Friday/issues/191

Existing controlling issues:
- #188 — read-only live-device bridge.
- #189 — local-memory/task-scoped cloud AI protocol.
- #190 — update-without-reinstall evolution runtime.
- #186 — ChatGPT as optional provider for normal conversation (separate provider from Claude).
- #184 — installed APK repair.

Anthropic billing caveat verified against current official documentation: Claude Pro does not include Console API usage. Eligible Max, Ultra and Team plans can claim scoped monthly API credits by linking a Claude Console organization. Prompt caching reduces repeated-prefix costs but does not eliminate usage. Do not assume the owner's Claude plan or API entitlement; expose the actual connected billing route and hard budget controls.

Official references:
- https://support.claude.com/en/articles/9876003-why-is-claude-api-usage-billed-separately-from-my-paid-claude-plan
- https://platform.claude.com/docs/en/about-claude/api-credits-for-subscribers
- https://platform.claude.com/docs/en/build-with-claude/prompt-caching
- https://developer.android.com/privacy-and-security/risks/dynamic-code-loading

No local model installation, Claude connection, live phone bridge or update-without-reinstall capability is claimed as delivered merely because these requirements are logged.


### 2026-11-27 — Preserve canonical roadmap priority; collaboration research is a later track

The owner explicitly directed that recently suggested Claude, local-memory delegation, update-without-reinstall and multi-AI communication requirements must be placed in their correct architectural positions, not promoted above the existing delivery priorities.

The canonical `.ralph/FRIDAY_DELIVERY_PLAN.md` remains authoritative and unchanged. Continue its chronological dependency order:
1. Phase 0 — truthful green engineering baseline.
2. Phase 1 — prove and repair the already-installed `ai.friday.assistant` on the physical phone, preserve identity/data, and establish useful interaction/voice. The phone has no local reasoning LLM installed; do not assume one.
3. Phase 2 — demonstrate one real REA-assisted failed-candidate repair from exact candidate evidence through independent verification and durable learning.
4. Phase 3 — persistent engineering station and authority.
5. Subsequent phases — full individuality, cognition, agency, NAE, universal capability acquisition and evolution.

Placement of the owner's additional requirements:
- Anthropic Claude is a replaceable external reasoning/engineering resource in the dynamic worker pool / Builder architecture, not a new top-level product priority.
- Local memory and task-scoped context projection belong in the continuity, privacy and worker collaboration contracts.
- The update-without-routine-reinstall requirement belongs in the install-once identity/update contract (Section 3.3) and later capability-evolution delivery, not ahead of repairing the installed seed.
- ChatGPT/Claude/Friday collaboration belongs in the persistent engineering workspace and authority plane, with live device inspection as a separate scoped capability.
- Novel collaboration research is tracked as future work in Issue #192 — Continuity-First Task Fabric: https://github.com/mhw77655-hue/Friday/issues/192. It is explicitly not a blocker for Phases 0–2.

The proposed Task Fabric is a research hypothesis, not a claim of proven global novelty or a delivered system. Compare against MCP, A2A, group/room protocols, event sourcing, blackboard systems, workflow engines, context retrieval and caching. The possible differentiator is Friday-owned continuity plus temporary provider-specific context projections, narrow evidence/capability leases and a shared outcome ledger; do not merely rebrand an existing protocol.

The immediate repair remains Issue #184. Its known source defects and release lineage remain controlling. Physical-device acceptance still requires genuine handset evidence; do not mark it complete based on CI/emulator evidence.


### 2026-11-27 — owner reaffirmed delivery order; new requirements kept subordinate

Owner's explicit execution order: **fix the already-installed Friday app first; then connect ChatGPT, Friday and GitHub; then continue bringing Friday to life.** The canonical `.ralph/FRIDAY_DELIVERY_PLAN.md` remains authoritative; new requirements extend it and do not replace its priority.

1. Phase 0: restore truthful green engineering baseline and Builder/REA contracts.
2. Phase 1: repair the existing `ai.friday.assistant` installation in place, preserve data/signing lineage, and prove typed interaction plus audible voice on the physical phone.
3. Phase 2: prove the real failed-candidate → sandbox → REA investigation → bounded repair → independent verification cycle.
4. Phase 3: persistent engineering station, owner-authorized GitHub authority, live read-only device observation and ChatGPT-to-Friday integration.
5. Continue the complete vision: continuity, individuality, cognition, agency, NAE and evolution.

Priority corrections:
- PR #185 (compact settings, glass visuals, AMOLED) is draft; known run #3683 was cancelled, not passed. Keep this work, but it must not displace functional repair.
- ChatGPT identity sign-in, permission for ChatGPT plan inference, and ChatGPT powering ordinary Friday conversations are three distinct states. Main-turn integration remains to be implemented and verified.
- GitHub authorization is separate; do not assume a GitHub token has been entered on the phone.
- Owner explicitly confirms **no local language model is installed**. Do not assume one or make local LLM installation a prerequisite.
- Anthropic Claude is a replaceable cloud worker, not GitHub Cloud. Local-memory task compilation and provider-specific context belong in later worker orchestration.
- Update-without-routine-APK-replacement belongs in the protected update/evolution architecture. Use signed, compatible modules/resources and bandwidth-efficient delivery; do not promise arbitrary native-code replacement without a host update or evade Android security constraints.
- Issue #192's distinctive collaboration fabric is future research, subordinate to Phases 0–2. Compare prior art honestly; do not claim global novelty.

Evidence discipline: source/CI/release evidence is not physical-phone acceptance. Do not claim the app is fixed, audible voice works, or live ChatGPT-to-phone inspection exists until demonstrated. Do not ask the owner to repeat the vision or re-upload the original APK.

Immediate work remains Phase 1: inspect and repair the production turn and voice paths without assuming a local model; validate PR #185 independently and preserve its visual/settings work without letting it become the critical path.


### 2026-11-27 — production turn-path audit: no cloud reasoning provider is configured

A fresh source-level audit of the current `main` branch traced the actual conversation entry path:

- `BodyCoordinator.routeInputText()` routes ordinary typed and recognized speech into `LatencyLayer.onUserInput()`.
- `LatencyLayer` delegates to the one installed `LatencyPipeline`; its normal bridge send calls `ModelManager.send(text)`.
- `TurnPathAssembly.assemble()` constructs `CloudModelRouter(emptyList())`. The router is therefore explicitly empty in the production composition.
- The same assembly supplies a model-free seed responder as a fallback, but this is not a general cloud reasoning provider.
- `FridayEngine` exposes the assembled router for research components, but this does not connect ChatGPT to ordinary conversation.
- `ChatGPTPlanSeat` is an advisory Table seat, not the normal conversation provider. The current main-branch implementation also still contains a `system` role and `max_output_tokens` in that Seat request; PR #185 proposes correcting its SIWC request contract, but PR #185 is still draft and not merged.

This is a concrete source-level explanation for why ChatGPT sign-in did not make Friday answer ordinary conversations with ChatGPT. The production app currently has no configured cloud reasoning provider in its central router. This finding does not, by itself, prove every cause of the handset's observed failure; physical runtime evidence remains separate.

Immediate repair implication: do not install or assume a local LLM. Implement a supported, explicitly consented cloud provider adapter in the single production turn path, starting with the already-authorized ChatGPT plan integration where its grant permits it; preserve the model-free seed as an honest offline fallback. Add request-contract tests, provider absence/expiry/rate-limit tests, no-history-leak tests, and a physical typed-response acceptance. Do not route private full conversation history to the provider by default.


### 2026-11-27 — owner correction: ChatGPT must communicate WITH Friday, not become Friday's user-facing voice

The owner corrected a fundamental direction error. The intended relationship is:

- The owner talks directly to Friday on the phone. Friday is the owner's companion/agent and must develop its own user-facing voice, memory, continuity and agency.
- ChatGPT (this engineering/orchestration agent) communicates directly with the Friday runtime as a peer/authorized collaborator: inspect Friday's real state, assign bounded engineering/research tasks, receive evidence and status, and help Friday improve itself.
- GitHub is the durable engineering record/source-control channel, not a substitute for a live Friday connection.
- ChatGPT must not be made the model behind Friday's ordinary conversation as the proposed way to make Friday talk to its owner. The previous "ChatGPT main-turn provider" direction was a misunderstanding and is superseded by this correction. Do not implement that as the primary integration.
- A cloud model may still be used internally by Friday if Friday's own architecture chooses it, but it is not the goal of the ChatGPT↔Friday integration and must never collapse the two agents into one.

Revised integration target: a secure, bidirectional, task-oriented ChatGPT↔Friday agent channel. Friday owns its context and decides what evidence/context to expose; ChatGPT receives only scoped task envelopes and permitted evidence. The channel must preserve task identity/state across reconnects, support streaming/progress/cancellation and typed outcomes, authenticate both participants, enforce least privilege, and log auditable handoffs. Device observation starts read-only; any future action capability is separately permissioned and approval-gated. The owner-facing Friday conversation remains independent.

The IETF agent-communication draft is useful background for dialog correlation, independent task context, delegation, identity/authentication and streaming/cancellation, but it is an Internet-Draft and not a finished architecture. We must not simply wrap Friday in A2A/MCP and call the distinctive design achieved. Research existing protocols, then design Friday's task/evidence/authority model around the owner's actual requirements. Reference: https://datatracker.ietf.org/doc/html/draft-agentic-ai-usecases-requirements-07

Immediate corrective action: re-scope issue #186 and the branch `feat/chatgpt-main-turn-provider`; do not merge or ship a ChatGPT ordinary-conversation provider as a solution to this requirement. Retain the passing PR #185 settings/glass work as a separate app-repair track. Continue fixing Friday's own interaction path independently of ChatGPT.


### 2026-11-27 — Settings and glass UI rebuild started (PR #185)

Branch: feat/glass-settings-chatgpt-connection  
PR: https://github.com/mhw77655-hue/Friday/pull/185

Implemented in this draft:
- Reorganized the single long Settings scroll into collapsible glass groups: Friday updates, ChatGPT connection, personalization/voice, phone access, runtime/capabilities, and GitHub/advanced development.
- Added a real ChatGPT inference health check in Settings. It discovers a visible account model and makes a minimal Responses request, then displays the actual completed reply rather than treating OAuth login as proof of inference.
- Corrected the existing ChatGPT Plan Seat Responses request: SIWC direct-plan usage rejects system-role items and unsupported max_output_tokens; the Seat now uses developer and omits that unsupported field.
- Added instrumented coverage for the ChatGPT connection test and the request contract.
- Added a shared translucent glass surface and applied it to Home's capability panel, Conversation header, user bubbles and message composer.
- Kept the app/system chrome true AMOLED black. Friday already had an AMOLED-black base; this pass layers translucent glass styling over it rather than changing the black substrate.

Important truth about the ChatGPT login:
- The current OAuth integration is wired to the AI Table/Builder advisory Seat, not the ordinary Friday conversation model. A successful sign-in therefore did not visibly change normal conversation.
- This PR makes the connection testable, but does not silently route private conversation text to ChatGPT. Main-chat use needs a separate explicit user opt-in and provider integration; this is a distinct next engineering step.
- GitHub access is separate from ChatGPT. The owner has not yet entered a GitHub fine-grained token in Friday, so Friday's on-device GitHub client cannot act as the owner until that credential is saved. The token must be entered on the phone; it is not available to this GitHub-connected engineering session and must never be copied into source or logs.

CI: PR validation run https://github.com/mhw77655-hue/Friday/actions/runs/46056623855 was created and is pending. This is a draft PR, not a merged change or a released APK. No physical-device visual or account test has been claimed.



### 2026-11-27 — ChatGPT sign-in consent-state bug identified and corrected in PR #185

A second issue explains why a user could complete ChatGPT sign-in yet Friday appeared disconnected: the existing OAuth handler required the optional chatgpt.tokens.use.direct scope before saving anything. OpenAI's current SIWC contract distinguishes identity sign-in from permission to use the user's ChatGPT plan. An identity-only grant should be retained and shown as signed in, while plan inference remains disabled.

PR #185 now:
- Stores a verified identity-only session when OpenAI returns identity scopes without chatgpt.tokens.use.direct.
- Exposes separate isSignedIn() and isConnected() states (identity vs plan inference permission).
- Shows "Signed in · plan use not enabled" rather than pretending no account was connected.
- Offers "Enable ChatGPT plan usage" as the reauthorization action.
- Keeps the actual inference test and background Builder use disabled until the direct-plan scope is granted.
- Adds an instrumented regression test proving identity-only sign-in is retained but validAccessToken() remains unavailable.

This follows OpenAI's SIWC guidance: https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery and https://developers.openai.com/siwc/token-sharing-open-source/sign-in. The app must not infer plan-use consent from identity sign-in.


### 2026-11-27 — concrete inference IPC race repaired in candidate branch

Branch: `fix/inference-messenger-message-recycling`

The handset diagnostic archive recorded an inference failure surfaced to the main process at `ProcessIsolatedModelBackend.kt:46`: `Unknown inference service operation.` The installed release was 1.0.601 / versionCode 1601. The diagnostic also showed severe thermal status and no model loaded; those are separate constraints and are not claimed fixed by this patch.

Concrete source-level race found in `InferenceService`: its main-thread Messenger Handler captured the framework-owned incoming Android `Message` object in a coroutine dispatched to `Dispatchers.IO`. Android recycles a Message after the Handler callback returns. Reading `message.what`, `message.arg1`, `message.replyTo` or `message.data` later from that coroutine can therefore observe mutated/reused fields. This race can produce the reported unknown-operation failure even though the client sends only MSG_LOAD=1, MSG_GENERATE=2 and MSG_UNLOAD=3. The old device report did not retain the numeric incoming operation, so causality is strongly plausible but not proven until the patched build is exercised on the handset.

Candidate fix:
- Snapshot operation, request ID, reply Messenger and a defensive Bundle copy synchronously inside the Handler callback.
- The coroutine consumes only this immutable request envelope; it never touches the recycled Message.
- Unknown-operation failures now include the numeric operation and request ID for future diagnosis without logging prompt/model data.
- Added an Android instrumented regression test that mutates/reuses the original Message and Bundle after snapshot and asserts the captured envelope remains unchanged.

This is a source-level fix candidate plus a regression test. CI has not yet run, and no physical-phone retest has occurred. The severe thermal gate remains intact. The handset logs still show no local model loaded, so even after this IPC repair Friday's general reasoning capability needs the separate authorized cloud/Friday↔ChatGPT agent connection and/or a legitimate model installation strategy; no local model is assumed or required for the ChatGPT collaboration bridge.

Next: open PR, require the complete build workflow, inspect test results, publish only a same-signer in-place update if green, then obtain a fresh redacted device report and verify typed/STT/TTS behavior on the handset.


### 2026-11-27 — app repair source merged; combined release validation underway

PR #185 (Settings/glass/AMOLED and ChatGPT identity-vs-plan consent UX) merged: `c77a5e952f9f37562357db7d7aea88b2e83b686a`. Exact-head CI run #46064333709 passed source audit, all six JVM shards, Android lint, release-variant build and emulator instrumentation.

PR #193 (isolated inference Messenger lifecycle race) merged: `3212211c915644e2ceb2f8e524baaee4dad644b4`. Exact-head CI run #46065644117 passed debug and release-variant builds, Android lint, all six JVM shards, whole-repository source audit and disposable-emulator instrumentation. The handler now snapshots Message-owned state before asynchronous dispatch; the numeric operation/request ID is retained in diagnostics.

Combined main validation run #46067087494 is in progress. Release `friday-v1.0.609` has now published from the earlier PR #185-only commit `c77a5e9`; it does not contain the Messenger repair and is not the release to install. Its APK SHA-256 is `d67ecde02bb6ffb7cb0e373bc28a373449133794ea83a467febdc1662d42c913`. The combined main build #46067087494 is still in progress; the combined signed release follows it.

Handset truth remains unchanged: the uploaded diagnostics report 1.0.601 installed; no local model is installed or required; ModelManager defaults to the model-free HEURISTIC provider. The optional isolated inference tier is repaired at source/CI level, but the physical handset has not yet been retested. Do not uninstall Friday or clear its data. The owner should install only the combined same-signer release in-place once published, then export a fresh redacted diagnostic bundle.

Next: verify the combined signed release's package/version/signing fingerprint/SHA-256; request in-place install; validate typed input, STT, TTS and process recovery on the physical phone; then complete the public-only GitHub collaboration UI/WorkManager integration and private SIWC/device bridge.


### 2026-11-27 — combined app repair release 1.0.610 published

<badge>Version 1.0.610</badge>

- Release: https://github.com/mhw77655-hue/Friday/releases/tag/friday-v1.0.610
- APK: https://github.com/mhw77655-hue/Friday/releases/download/friday-v1.0.610/app-release.apk
- Version code: `1610`
- APK size: `129,737,452` bytes
- APK SHA-256: `dc31e57e2d3eee60e89174942560eee367faa943550a476f3fa828cc374e3487`
- Signing certificate SHA-256: `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8` (matches the existing 1.0.601 install lineage; same-signature in-place update verified by the release workflow)
- Release target commit: `3212211c915644e2ceb2f8e524baaee4dad644b4`
- Combined main CI run: #46067087494 passed; signed release run #46068259021 passed permanent-keystore verification and publication.

This is the first release containing both merged PR #185 (compact Settings, glass/AMOLED surfaces and corrected ChatGPT connection/consent UX) and PR #193 (Messenger request snapshot/lifecycle repair). Release #609 is the earlier UI-only build; do not use it as the repair target.

Physical handset acceptance remains pending. The owner must install 1.0.610 over the existing app without uninstalling or clearing data, then export a fresh diagnostic bundle. Do not claim the phone is fixed until this real-device evidence is reviewed.

Collaboration continues in stacked draft PRs #194 and #195: public-only GitHub task transport and its Android polling/Settings integration. Private SIWC ChatGPT task work and the private read-only device broker remain separate follow-on adapters.
