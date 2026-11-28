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

**Second confirmed defect to investigate**- Friday's failure evidence records an ENGINE error at 2026-11-27 18:43:09.511 UTC (20:43:09.511 local): `IllegalStateException: Unknown inference service operation.`
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

**Release: Friday 1.0.610**

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

### 2026-11-27 — GitHub collaboration transport and Android relay integration both pass exact-head CI

- Core transport PR #194 exact head: `130c2ba482982529521d0c331915d341a03b5109`; run #46069437763 completed successfully. All six JVM shards, debug APK build, whole-repository source audit, disposable Android instrumentation, aggregate evidence and debug APK promotion passed.
- Android integration PR #195 exact head: `4818ac99f92cb87a2c578e02681105a47db8e054`; run #46068400888 completed successfully. All six JVM shards, debug APK build, whole-repository source audit, disposable Android instrumentation, aggregate evidence and debug APK promotion passed.
- Both PRs remain draft and open. #195 is stacked on #194; its base must be synchronized/reviewed against the current #194 head before merge. Green CI does not equal a live handset connection.
- #194 implements the public GitHub task-thread transport; #195 adds Android opt-in consent, trusted actors, WorkManager polling, durable local reply inbox and Settings UI. GitHub public threads contain project metadata only. Private ChatGPT context and private device observations are not routed through public GitHub.
- Neither PR is included in signed release 1.0.610. That release remains the app-repair baseline. The next relay release must be built and signed only after the stacked PRs are reconciled, reviewed and merged.
- Remaining gates: owner installs 1.0.610 in-place and supplies fresh handset diagnostics plus typed/STT/TTS acceptance; merge/release relay code; configure repository-scoped GitHub access in Friday; implement the private SIWC ChatGPT task adapter and authenticated read-only phone broker (#188); then prove the complete Friday-owned task lifecycle with crash recovery and local validation.
- Correct interpretation: ChatGPT is Friday's external engineering collaborator, not the model replacing Friday in the owner's conversation.


### 2026-11-28 — owner feedback after installing 1.0.610: visual quality and no-billing requirement

- Owner confirms the updated app is in hand. Their assessment: the current glass appearance is primitive and does not meet the intended visual quality. Treat the existing glass work as a rough first pass, not a finished design. Next UI pass must redesign the visual system and core screens coherently (depth/material hierarchy, lighting/refraction restraint, typography, spacing, motion, navigation and AMOLED blacks), not merely increase blur or add translucent cards. Require screenshot-based owner review and accessibility/contrast checks before calling it done.
- Owner reports the ChatGPT linking path requires billing. Do not ask the owner to add an API payment method or buy API credits as a prerequisite. OpenAI documents Sign in with ChatGPT as two distinct grants: identity and optional ChatGPT-plan Responses API usage. The open-source SIWC path can use an eligible user's existing ChatGPT plan without an API key; plan eligibility and separate plan-use consent apply. ChatGPT/API billing remain separate. If the app sends the owner to API Platform billing, that is the wrong path for the no-billing requirement and must be corrected.
- Source audit: `ChatGPTPlanAuth.kt` requests the SIWC scopes `openid profile email offline_access resource.invoke chatgpt.tokens.use.direct`, and `ChatGPTPlanSeat.kt` uses the authorized bearer token against Responses API with `store=false`, explicit public-data gating and no paid API-key fallback. However, production `TurnPathAssembly.kt` still constructs `CloudModelRouter(emptyList())`; the provider is not wired into Friday's live reasoning pool. This is a concrete implementation gap, independent of whether the user's ChatGPT plan is eligible.
- Next engineering order: (1) trace the exact Settings action and OAuth consent result that the owner sees; ensure identity-only sign-in is not misrepresented as plan inference and never redirects to API billing; (2) wire the existing SIWC seat into the Friday-owned cloud pool with clear eligibility/consent/limit states and no API-key fallback; (3) provide a genuinely no-cost fallback path that does not pretend to be ChatGPT; (4) redesign the UI beyond the primitive glass pass; (5) continue GitHub collaboration transport and the scheduler/diagnostic stewardship loop.
- Do not assume the owner has a Plus/Pro eligible plan or that SIWC plan-use authorization succeeded. If their account is ineligible, disclose that limitation and retain a no-cost alternative; do not recommend upgrading as the default.


### 2026-11-28 — visual architecture audit: adopt Haze for real Compose backdrop glass

- Inspected current app Gradle and UI sources. Friday already uses Jetpack Compose + Material 3, Navigation Compose, WorkManager, custom Friday theme/tokens, and a custom `GlassPanel`. Current Compose BOM is `2024.09.00`.
- The current `GlassPanel` is a translucent gradient/tint + border/reflection treatment; it is not a true backdrop-glass material that samples and refracts content behind the surface. This explains why the owner's visual assessment can reasonably be "primitive" despite the glass styling pass.
- Recommended visual effect dependency: Chris Banes Haze `2.1.0` with `dev.chrisbanes.haze:haze` and `dev.chrisbanes.haze:haze-glass`; Haze Glass is stable as of 2.1.0. Use its source-backed backdrop inputs and GlassStyle (Regular/Clear) with restrained Friday-specific tokens. Do not use Haze as a replacement for the app's design system.
- Keep Material 3 as the semantic component/accessibility foundation; evaluate current Material 3 Expressive APIs and update Compose BOM from the stale 2024.09.00 only after dependency compatibility, minSdk 26, Kotlin/AGP, APK size, GPU/memory and screenshot/performance tests. Haze native Android backdrop backend remains experimental; use source-backed rendering as the reliable cross-version path.
- Next UI work is a full authored design-system pass (materials, layout, typography, navigation, motion, interaction, accessibility, AMOLED true-black hierarchy), not a one-library makeover. Build screenshot/golden comparisons on Home, Conversation, Settings and system/diagnostic screens; owner review before release.
- No dependency or production code has been changed by this audit. This is an implementation decision and investigation record only.


### 2026-11-28 — consolidate Friday public GitHub task relay onto current main

- The previous relay implementation existed as two stacked draft PRs (#194/#195), based on older main revisions. Rather than merge stale stacked branches, copied the reviewed implementation files onto a fresh branch from current main and reapplied the small SettingsScreen integration against current source.
- Consolidated branch: `feat/github-task-relay-integrated`. Includes the versioned public-only task envelope/relay, repository client, trusted actor and default-OFF consent controls, durable local reply inbox, WorkManager polling, Settings integration, unit tests and consent instrumentation.
- Public GitHub threads remain strictly limited to public project metadata and public engineering context. Private task projections, phone snapshots, conversations, memory, credentials and diagnostics are excluded.
- Existing stacked PRs #194/#195 remain open pending replacement-branch CI and review; no merge or physical-device acceptance is claimed.
- Separate private ChatGPT task-worker implementation is being developed in PR #197. It uses the existing SIWC token provider, requires a one-shot approval bound to the exact task projection, rejects sensitive classification, bounds context and expiry, uses Responses API `store=false` and `stream=true`, and never accepts GitHub credentials. It is an outbound API worker, not a connection to this exact ChatGPT UI conversation.
- Remaining to complete the link: wire explicit per-task approval into Friday UI/task lifecycle; persist task checkpoints and completed Responses IDs/results before acknowledgement; connect public GitHub task metadata to the private worker without putting private payloads in public issues; add authenticated read-only device broker (#188); run exact-head CI and physical-phone acceptance.


- Follow-up hardening on the consolidated relay branch: public payload validation now scans serialized content for common GitHub/OpenAI/Google/Slack bearer/API-key and private-key patterns in addition to forbidden private-field names; regression test added. The Settings handshake now embeds the exact 40-character BuildConfig source revision when available, falling back explicitly to an app-version marker rather than pretending the version is a commit SHA.
- Added the exact ChatGPT v1 reply-envelope contract to `docs/FRIDAY_AGENT_TASK_RELAY.md`, so the connected GitHub collaborator can post machine-ingestible replies instead of unstructured comments.


- Closed the reply-to-Friday gap in the relay branch: accepted GitHub comments are now durably journaled, then imported idempotently into the canonical `LocalTaskInbox` under a stable message-derived ID as `WAITING_APPROVAL` proposals, before GitHub acknowledgement. Crash replay is safe; duplicate replies do not create duplicate local tasks; no external proposal is auto-executed.
- Added `LocalTaskInbox.submitOnce`, a worker-safe application-context factory, the `AgentTaskReplyInboxBridge`, and tests for stable import/deduplication and the approval gate. The relay now connects GitHub replies to Friday's local task surface rather than merely showing them in Settings.


- Additional crash/replay hardening: LocalTaskInbox now persists an external proposal before exposing it in memory, and stable-ID deduplication rejects reuse of the same idempotency key with different proposal content. This closes the case where a failed disk write or altered duplicate message could otherwise be acknowledged as if safely imported.


- Review correction: the GitHub reply bridge now targets the already-running `FridayEngine.taskInbox` when available, rather than always creating a second `LocalTaskInbox` instance. This prevents a stale in-memory StateFlow while preserving the cold-start path (a worker-created durable inbox is recovered by normal engine startup). CI run #3771 is validating this current relay head.


### 2026-11-28 — owner correction: agent collaboration, not a chat route

- Reaffirmed the owner's intended topology: Friday remains an independent phone-resident agent and the owner speaks to Friday directly. GPT is an external peer/worker that receives scoped task envelopes, returns typed findings/proposals and requests evidence. GitHub is the durable engineering/task record and source-control system. Do not route Friday's owner-facing conversation through ChatGPT and do not describe this as Friday chatting with ChatGPT.
- Corrected implementation framing: PR #198 was the public, asynchronous agent task mailbox. It is not a live chat session or a push wake-up. It uses structured task/result envelopes, trusted GitHub actor checks, default-OFF owner consent, durable local checkpoints and an approval-gated LocalTaskInbox import. Public GitHub is restricted to public project/engineering context. It cannot carry private phone state or user context.
- PR #198 merged after exact-head CI run #3789 passed all JVM shards, source audit, Android debug/lint and disposable-device instrumentation. Merge commit: 076eaa376654bae037f3598ff1f35b892ca46556. Main push build #3797 is now validating the merged main commit. No production APK or physical handset handshake is claimed yet.
- PR #197 is a separate Friday-triggerable SIWC Responses API worker, not this connected ChatGPT session. Its exact-head CI run #3796 passed all available CI jobs. It remains draft because owner-facing per-task approval UI and binding into Friday's canonical task lifecycle are not complete. Its encrypted task-result journal now recovers completed work after process recreation and preserves interrupted output as INCOMPLETE.
- Next engineering work: (1) validate #3797 and release the merged public task channel in a same-signature APK; (2) implement the single Friday-owned task lifecycle that can dispatch a public engineering task through the GitHub mailbox or a private task through the separately authorized SIWC worker, preserving one task ID, consent model, checkpoint/recovery and provenance; (3) complete the private worker approval/recovery UI; (4) build the read-only device evidence broker (#188) so GPT can request allowlisted live diagnostics through Friday rather than reading private data from GitHub.
- Do not claim the exact ChatGPT UI session can be automatically woken by Friday. The GitHub mailbox is asynchronous and processed when the connected GPT collaborator is active; SIWC is the direct Friday-triggered model endpoint. These are two adapters behind one task fabric, not a replacement for Friday's identity or a user-facing chat route.


### 2026-11-28 — live Friday ↔ GPT inspection implementation resumed (02:50 UTC)

Current repository state re-verified against GitHub, not inferred from prior chat:
- Default branch is `main`, current commit `443f6ad5d931421a1b6903f9a03ed28789509aa7`.
- Public structured task channel PR #198 is merged at `076eaa376654bae037f3598ff1f35b892ca46556`; exact-head build #3789 passed and post-merge main build #3797 passed.
- Terminology clarification PR #199 has now been merged at `90ef28f64151d89d0075872c55aef6ccf1238399`; exact-head CI run #3799 passed.
- Signed release workflow run #611 (`46089683391`) is still in progress for main commit `443f6ad...`; do not claim a signed APK exists until the workflow completes and its artifact/signature/digest are verified.
- PR #197 remains draft/open. It is a Friday-triggered SIWC Responses API worker, not the active GPT collaborator session. It must not be presented as satisfying the owner's requested live-session connection.
- Issue #188 remains the controlling missing capability: authenticated, device-bound, read-only inspection of the actual running Friday instance.

Architecture correction for implementation:
- Keep GitHub as source control, CI/release evidence and the public engineering task ledger. Do not place private device evidence in this public repository.
- The active GPT session already has repository-scoped GitHub access and can inspect source, diffs, issues, commits and workflow evidence through its connected GitHub integration.
- The missing live-runtime leg requires a private authenticated tool surface callable by ChatGPT. The technically appropriate integration target is a remote MCP server exposing narrowly scoped read-only Friday inspection tools; the phone maintains an outbound authenticated connection to a relay and answers scoped inspection requests from its local allowlisted broker.
- A remote MCP server must be hosted on reachable HTTPS or connected through a supported Secure MCP Tunnel. ChatGPT custom MCP availability depends on account/workspace plan and settings; the user must make a one-time connection/authorization if the product surface requires it. Do not claim this session can see phone state before that connection is actually active.
- The current connected GitHub integration cannot itself reach Android runtime state. The existing `FRIDAY_LIVE_TEST_DIAGNOSTIC_BRIDGE.md` is a local report/copy/share path, not the requested live cloud inspection channel.
- OpenAI SIWC plan-use is separate and optional: it provides an authorized Responses API worker, not access to this ChatGPT session or its conversation. Do not substitute it for the live-session MCP bridge.

Implementation order:
1. Finish and verify release workflow #611, including APK package/version, same-signer fingerprint, SHA-256 and release asset.
2. Build the Android read-only inspection broker with a hard-coded allowlist of runtime health/capability/process/service/provider/voice states, bounded sanitized event windows, freshness/provenance and revocation. No taps, shell, file writes, arbitrary screen content, microphone/transcript/notification/contact access.
3. Build the private outbound relay + remote MCP surface. MCP tools must be read-only: obtain status, read allowlisted snapshot, read bounded events, and submit a scoped evidence request that Friday may accept/deny locally. No arbitrary RPC or mutation tool.
4. Add device-bound pairing, short-lived credentials, challenge/response, replay protection, rate limits, explicit owner-visible connected indicator, local audit and immediate revocation.
5. Connect the remote MCP server to the supported ChatGPT surface and verify from this active GPT environment that it can retrieve a timestamped live snapshot from the physical phone.
6. Only after live inspection works, connect the same task identity/provenance to GitHub source changes, candidate PRs, CI and owner-approved release flow.

Acceptance remains a real physical-phone round trip. Green CI, a public GitHub task issue, a SIWC model request, an exported diagnostic report or a release artifact alone does not satisfy it.


## 2026-11-28 — owner correction: build the live ChatGPT ↔ Friday inspection link

The owner restated the required operating model: Friday is an independent agent; this ChatGPT engineering agent must be able to inspect the complete repository and the actual running Friday app, diagnose it, implement/review changes, validate CI and feed verified results back into Friday. The owner does not want a user-facing chat replacement, a keyboard/chat UI, or repeated intermediate APKs.

### Verified live repository state
- Merged PR [#198](https://github.com/mhw77655-hue/Friday/pull/198), merge commit `076eaa376654bae037f3598ff1f35b892ca46556`, supplies the public-only GitHub task relay and Android inbox. It does **not** expose private/live handset state to this ChatGPT session.
- PR [#197](https://github.com/mhw77655-hue/Friday/pull/197) remains draft/open at `708cda9cec4f1b2da6d5f07b4ceffc519d08f3c1`; exact-head Actions run [#3796](https://github.com/mhw77655-hue/Friday/actions/runs/46087803792) completed successfully. It is an outbound SIWC Responses API worker, not a bridge into this exact ChatGPT UI session. Its PR description still lists approval UI/task lifecycle integration and physical-phone/live-account acceptance as incomplete.
- PR [#199](https://github.com/mhw77655-hue/Friday/pull/199) exact-head Actions run [#3799](https://github.com/mhw77655-hue/Friday/actions/runs/46089097031) completed successfully. It is terminology/UI wording only.
- Issue [#188](https://github.com/mhw77655-hue/Friday/issues/188) has no implementation PR yet. The authenticated read-only phone broker and private live inspection relay remain the actual missing capability.
- This ChatGPT session has connected GitHub repository read/write access and can inspect/modify source, PRs, issues and CI. It has no direct Android/ADB/physical handset connector; no fresh live runtime snapshot has been retrieved.
- Repository README requires authorized changes directly on `main`; do not create feature branches.

### Correct ChatGPT-side integration route
OpenAI's current documentation supports custom remote MCP apps in ChatGPT conversations. A Friday MCP server can expose strictly read-only tools for live device status, diagnostic summary, service/capability readiness and bounded safe event evidence. Private phone data must never go through this public GitHub repository. The MCP endpoint needs a Friday-owned private HTTPS host, authentication and a one-time ChatGPT web connection; account/workspace permissions determine whether custom read-only MCP is available. Sources: [ChatGPT custom MCP connections](https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt), [MCP servers](https://developers.openai.com/api/docs/guides/tools-connectors-mcp), [Secure MCP Tunnel](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels).

The only Supabase project visible to this session is `venture-autopilot`; it is not Friday infrastructure and must not be repurposed for private phone telemetry. No Friday-specific hosted MCP/relay deployment config was found at repository root. Do not claim the exact ChatGPT session can inspect the handset until its MCP tool can retrieve a fresh, authenticated, timestamped snapshot and revocation is tested.

### Critical next implementation sequence
1. Implement a phone-side read-only diagnostic broker with a strict allowlist, data minimisation, timestamp/source revision, redaction and local enforcement. No shell, taps, UI actions, arbitrary files, transcripts, memory or credentials.
2. Implement a private device-bound relay and remote MCP server with short-lived credentials, mutual authentication, replay/rate limits, revocation and audit.
3. Deploy the server to Friday-owned infrastructure; connect it to ChatGPT web if this account/workspace supports the required custom MCP read/fetch permission.
4. Prove from this ChatGPT session: live snapshot read, freshness/provenance check, revoke, and denied access after revocation.
5. Keep GitHub as repository/CI/review/release workspace; Friday's local task journal remains the coordination authority.

### Honest status
The repository and GitHub side are accessible here. The live phone side is **not connected yet**. The required MCP host/ChatGPT connection is not deployed or registered. This is the current critical path, not a completed handshake.

### 2026-11-28 — terminology correction merged

- PR [#199](https://github.com/mhw77655-hue/Friday/pull/199) was squash-merged to main as `90ef28f64151d89d0075872c55aef6ccf1238399` after exact-head Actions run [#3799](https://github.com/mhw77655-hue/Friday/actions/runs/46089097031) completed successfully. This changes terminology/UI wording to describe structured agent task exchange rather than chat; it does not add live phone inspection.
- A workflow run associated with the merge commit was not returned by the commit-to-PR-workflow lookup; do not claim a post-merge main build passed until an exact-SHA run is observed.
- Critical path remains issue #188: implement the private read-only Android diagnostic broker and a Friday-hosted remote MCP endpoint, then connect that endpoint to ChatGPT web and verify live read/revoke. No fresh handset snapshot has been retrieved by this ChatGPT session.


### 2026-11-28 — first live-inspection privacy boundary implemented

- Re-verified repository main at 89186231b3dc0bdd0d8ab182d160f56536fb8b13 before the change. Existing DiagnosticsViewModel.createExportReport() already produces friday-device-report-v2; it is intentionally richer than a remote endpoint should expose.
- Added FridayInspectionSnapshot, a strict projection from the local support report to friday-live-inspection-v1. It copies only app version/version code/source revision, Android API, device-capture time, normalised subsystem readiness states, available RAM/storage and event/failure counts.
- Status values are reduced to a closed vocabulary (ready, starting, idle, permission_required, unavailable, unknown). Raw subsystem text is not forwarded.
- Added unit tests covering the allowlist, unsupported report schemas, hostile version/revision strings, and exclusion of transcript, credential, model-name, private event and raw failure content.
- Added docs/FRIDAY_LIVE_INSPECTION_BRIDGE.md with the privacy boundary, transport requirements and Proxima decision.
- Commits on main: fe5a2bbffddcb2f6efe4c65c209cd4f96690bca8 (projection), b8f40e641a92dae7ca0a83b3987d049ad03a34ee (tests), 656ffa3c1494af6a14c32ba9e536a654945158f6 (bridge contract), a5db33359f158b71a40fa78e321afcef5e62f73f (boolean permission normalisation).
- NOT YET COMPLETE: no device-bound pairing/session, outbound relay, hosted MCP server, deployment or ChatGPT custom MCP registration exists yet. No physical phone snapshot has been retrieved by this ChatGPT session. This code is the data-minimisation boundary, not a live connection. Run current-main CI and fix any failures before proceeding.
- Proxima upstream was inspected at Zen4-bit/Proxima. It is a desktop-local gateway and its current Personal Use License is non-commercial; do not embed or redistribute it as part of Friday's commercial product. It is not a phone-to-cloud tunnel and does not grant this ChatGPT session access to a local Proxima process.


### 2026-11-28 — live-inspection bridge source implementation advanced

- Added a phone-side allowlisted snapshot projection and Android provider. Remote payload: app/source identity, Android API, network/battery/Shizuku/microphone/model readiness, available RAM/storage and event/failure counts. Raw diagnostic text, stack traces, transcripts, audio, screen/notification content, personal memory, files, credentials and model names are excluded.
- Added a P-256 device identity in Android Keystore, one-time HTTPS pairing, nonce/timestamp-bound signed WebSocket authentication, outbound-only device connection, fresh snapshot request/response, local key deletion and device-signed server-side revocation.
- Added owner controls in Settings → Live ChatGPT inspection. Consent is off until pairing. Connection is maintained while Friday is foregrounded and closes when the app backgrounds. It is read-only: no shell, taps, text entry, file browsing, Shizuku action or settings mutation.
- Added Node 22 remote service at services/friday-inspection with Streamable HTTP MCP, read-only tools friday_live_status and friday_inspection_snapshot, authenticated WebSocket relay, exact snapshot schema validation, freshness checks, P-256 proof verification, bearer protection, one-time pairing, admin/device revocation, request-size bounds, rate limits and privacy-safe audit events. Latest service source is commit 80df5241ca1588b453cdd5e739a505945504271f; later service refinements are in main after that commit.
- Added Railway deployment config and docs. Requires persistent volume /data and env vars PUBLIC_HOST, FRIDAY_MCP_TOKEN, FRIDAY_ADMIN_TOKEN and FRIDAY_STATE_PATH=/data/state.json. Secrets are not in GitHub. Railway plugin was suggested but is not connected, so deployment cannot be performed from this session yet.
- Earlier service CI run #46091181556 passed npm install and npm test. Later service runs #46091282977 and #46091358316 are queued; they are not yet verified. Android main build #46091278923 for commit 0aaf3366d89c332b77aae1b92e99d5f1c2d47cf1 is queued; no APK has been published.
- ChatGPT custom MCP registration remains a one-time owner action on ChatGPT web, subject to account/workspace permissions. Server is not deployed, Friday is not paired, no live handset snapshot has been retrieved by this ChatGPT session, and revocation acceptance has not been run. Do not describe the phone as connected until those checks pass.


### 2026-11-28 — OAuth correction before deployment

- Rechecked current OpenAI custom MCP requirements. ChatGPT custom MCP apps use OAuth or no authentication; a static bearer API-key gate is not the correct production authorization path. Do not deploy the current FRIDAY_MCP_TOKEN-only MCP gate as the ChatGPT integration.
- Identified Supabase Auth OAuth 2.1 Server as the standards-compliant provider: authorization code + PKCE, refresh tokens, dynamic client registration, OIDC discovery and JWKS; Supabase explicitly documents this for MCP authentication. Official docs: https://supabase.com/docs/guides/auth/oauth-server and https://supabase.com/docs/guides/auth/oauth-server/mcp-authentication.
- The connected Supabase account exposes one organization and one existing project (venture-autopilot). That project will NOT be repurposed for Friday. A separate Friday-auth project is required. Supabase quoted $0/month for a new project in the available organization; project creation still requires the owner's explicit organization choice. No project has been created.
- Corrected next auth sequence: create separate Friday Supabase project after owner authorization; enable OAuth 2.1 + dynamic client registration; host owner login/consent UI with the Friday MCP service; verify Supabase JWTs via JWKS and bind access to the single owner; advertise OAuth protected-resource metadata; then register ChatGPT custom MCP from ChatGPT web.
- Railway remains the deployment target for the persistent Node/WebSocket MCP service. The Railway plugin has been suggested but is not connected. No service deployment, ChatGPT registration, phone pairing or live handset snapshot has occurred.
- Android live-inspection client keeps its outbound connection while the app is visible or the existing owner-enabled Friday foreground runtime is active; it stops when neither is active. Android build remains queued; no APK release has been published.
- Current main head at this log update: 3af5f9480b22705eb25b32aa27b7551d88677efe. Service CI run #46091421859 passed syntax checks and unit tests. Latest Android build must be rechecked before any release claim.


### 2026-11-28 — dedicated Friday authentication project created

- Owner explicitly approved creating a separate Friday-auth Supabase project in the only connected Supabase organization and instructed not to modify Venture Autopilot.
- Created Supabase project `friday-auth`, ref `dtvhwuhmcynpbqfehlvs`, region `eu-central-1` (Frankfurt), organization `mldpsjfgfehfdiwmztdd`. Creation quote and confirmation were $0/month. Supabase reports `ACTIVE_HEALTHY`.
- Re-listed all Supabase projects after creation: `friday-auth` and existing `venture-autopilot` are both `ACTIVE_HEALTHY`; Venture Autopilot's project ref and database version remain unchanged. No SQL, auth configuration, secrets, or data were written to Venture Autopilot.
- Friday auth API origin: `https://dtvhwuhmcynpbqfehlvs.supabase.co`. No secret or service-role key has been retrieved or placed in GitHub.
- Supabase OAuth 2.1 is currently documented as Public Beta and must be enabled under Authentication → OAuth Server; the Supabase MCP connector exposes no project-auth-settings mutation action. Authorization path and OAuth consent UI also require a deployed frontend. The discovery URL could not be independently fetched through the current web reader, so OAuth enablement is NOT verified.
- Railway plugin is now connected. Railway identity has one personal workspace (`2d10472f-4540-4705-9eff-950fad7c8b3d`) and zero projects. A separate `Friday Live Inspection` Railway project was created (`de7508a5-fd29-46d1-830c-3e319f8c8d49`), production environment `339e8209-af18-4a2b-8b18-3e0288f602b1`. No service, volume, deployment or domain has been created yet. Do not claim service deployment.
- Next work: finish OAuth consent frontend and token-validation design, enable Supabase OAuth 2.1 + dynamic registration, deploy the persistent Node/WebSocket relay to Railway with a `/data` volume and production secrets, verify service health, then register the endpoint in ChatGPT web and pair Friday. Live handset snapshot and revocation acceptance remain outstanding.


### 2026-11-28 — Railway service provisioned; GitHub source authorization blocks first deploy

- Created the Railway service `friday-inspection` in the existing Friday Live Inspection production environment. Service ID `04b164e6-7882-47fd-ae50-5c672ee1ced8`; root directory `/services/friday-inspection`; start command `npm start`; health check `/health`; sleep disabled.
- Created 500 MB persistent volume `friday-inspection-state`, mounted at `/data` (500 MB is the current Railway plan maximum; 1 GB request was rejected, so used the maximum allowed).
- Generated HTTPS domain `friday-inspection-production.up.railway.app` on port 8080.
- Configured service variables: `NODE_ENV=production`, `PUBLIC_HOST`, `SUPABASE_URL` for `friday-auth`, its publishable API key, and `FRIDAY_STATE_PATH=/data/state.json`. No service-role key or static MCP bearer token was set.
- Replaced the remote MCP static bearer-token gate in service source with Supabase Auth `getUser(access_token)` validation, fail-closed owner email allowlist, OAuth Protected Resource Metadata endpoints and owner-only pairing-code MCP tool. Added a Supabase OAuth consent UI page and server route. Main commits: `9f54b0ca58511c75797844fe4a394745b48e3a39`, `6b0ba5da4b79ee08ad97898cbc41083cae799288`, `0d2e85487b778c1bed841c5b3bcfd15179a87292`, `eac37eca6078d0400d4565b6ed2f47becf043883`. Service CI for `9f54b0c` and `6b0ba5d` passed; run #46093224028 for consent HTML is in progress; run #46093227942 for server route is queued.
- First deployment has NOT started. `connect_service_source` was rejected with `User does not have access to the repo`: Railway account is not yet authorized to access `mhw77655-hue/Friday`. Owner must connect/install the Railway GitHub App for this repository in Railway once; then reconnect source to existing service. Do not create another service.
- Supabase OAuth 2.1 server and dynamic client registration are still not enabled/verified. Consent page is in source but not deployed. Owner email allowlist `FRIDAY_OWNER_EMAIL` is intentionally unset; all MCP/admin requests fail closed until configured.
- No live phone connection or snapshot exists. After Railway GitHub source authorization, deploy, verify health/metadata and invalid-token rejection, configure Supabase OAuth Site URL/consent path + OAuth 2.1 + DCR, set owner email, connect ChatGPT custom MCP on web, then pair Android and prove fresh snapshot + revocation.
- Current main head at this log update: eac37eca6078d0400d4565b6ed2f47becf043883. Venture Autopilot remains untouched.

### 2026-11-28 — Railway first deployment verified; OAuth setup is now the blocker

- Owner confirmed Railway GitHub authorization. The existing Railway service is connected to `mhw77655-hue/Friday`, branch `main`, root directory `/services/friday-inspection`; no duplicate service was created.
- Railway deployment `301cc907-2b40-46f3-86f9-cf8f178c4557` completed with status `SUCCESS`. Runtime logs confirm the Node service started on port 8080 and Railway's configured `/health` check succeeded. The 500 MB persistent volume is mounted at `/data`; production environment is healthy with no Railway service issues.
- Independently fetched the public endpoints: `GET /health` returns `{"service":"friday-live-inspection","status":"ok"}`; `GET /` returns service status ok and identifies Supabase OAuth 2.1 as the intended auth; `GET /oauth/consent` serves the Friday read-only access approval page. Protected-resource metadata at `/.well-known/oauth-protected-resource` returns the MCP resource and Friday-auth authorization-server URL.
- Supabase Friday-auth project `dtvhwuhmcynpbqfehlvs` remains `ACTIVE_HEALTHY`; Venture Autopilot remains a separate active project and was not modified. Supabase OIDC discovery at `/auth/v1/.well-known/openid-configuration` responds with authorization/token/JWKS endpoints. However, the Supabase MCP OAuth discovery URL `/.well-known/oauth-authorization-server/auth/v1` returned HTTP 404, so OAuth 2.1 MCP discovery and dynamic client registration are NOT yet verified/enabled.
- Railway currently has variables named `NODE_ENV`, `PUBLIC_HOST`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and `FRIDAY_STATE_PATH`. `FRIDAY_OWNER_EMAIL` is absent. The service deliberately fails closed for MCP/admin access until the owner allowlist is configured; do not weaken this gate or infer the owner's identity from connector metadata.
- Build logs reported two high-severity npm dependency advisories. The deployment itself succeeded, but dependency audit details must be reviewed and patched without blind `npm audit fix --force` before treating the service as production-hardened.
- Current verified state: Railway deployment + health endpoint work; protected-resource metadata is served; consent page is served; no ChatGPT MCP OAuth connection, owner authorization, phone pairing, live device snapshot, or revocation acceptance has occurred.
- Next: owner enables OAuth 2.1 server and dynamic client registration in Friday-auth → Authentication → OAuth Server; configure Site URL/authorization path to the deployed consent page; create/sign in to the Friday-auth owner account and set `FRIDAY_OWNER_EMAIL` to that account's email in Railway Variables; then retest discovery, consent, token validation, MCP initialize/tools/list, pairing, and revocation. Only after those tests should ChatGPT be registered in ChatGPT web and Friday paired.
### 2026-11-28 — memory-pressure inference pinning hardening

- Audited the latest RAM-pressure changes against the actual `ModelManager` production path.
- Found a concurrency window: a turn-booked model handle was read from the tier cache, then pinned in a separate critical section. The pressure sweeper could detach/unload the model between those operations. This could turn a valid turn into a model-generation error precisely while the device was under pressure.
- Fixed this on canonical `main` in commit `54ed112c8c0a070cc34f309ddc8b5e64051bd91e`: tier lookup and active-generation pinning now happen atomically; the conversation send path pins the booked handle synchronously before scheduling its coroutine; retries retain the pin; release occurs in a `finally` block. `generateOnTier` uses the same atomic pin/unpin discipline.
- Added a deterministic paused-dispatcher regression test in commit `ce16ba4e9c4dcf46fd71666c0c955b215b00c5a9`. It holds the send coroutine before execution, triggers a pressure sweep, and asserts that the already-booked model remains loaded until the send completes, then becomes reclaimable.
- Source/test content was re-fetched from GitHub and the intended changes were confirmed present. This is source verification only; CI has not yet been independently observed for these two commits.
- Live Friday inspection currently reports enrolled and not revoked, but disconnected. No fresh phone RAM/PSI/PSS capture or physical-device acceptance is available from this session.
- Next: verify main CI for `ce16ba4`; inspect any failure; then obtain a fresh handset memory report when Friday reconnects. Do not change Android/OEM memory settings or kill other apps based on estimates alone.
### 2026-11-28 — owner-first privacy boundary and inference race follow-through

- Corrected the prior model pinning patch's retry exception path: the retry now retains `pinnedHandle` rather than referring to the removed `tierHandle` variable. This avoids a Kotlin compile failure and preserves the active-generation pin through both error and empty-response retries (`33c13a9`).
- Changed the live-inspection relay from pairing-implies-enabled to explicit opt-in. Pairing now stores credentials but leaves the relay disabled; a one-time preference migration disables previously enabled installations after this privacy change (`cc77f55`).
- Added a distinct pause operation that closes the WebSocket immediately while preserving pairing; full revoke remains separate and deletes the device key (`8b1e224`).
- Rewrote the in-app disclosure to state exactly which coarse technical fields may be sent and which categories are excluded. The switch is now labelled as an external inspection session and OFF by default (`e1d03b7`).
- Added Android instrumentation coverage for fresh-install opt-in defaults, pairing without auto-enable, persistent pause, and migration of an old enabled installation to OFF (`5ef253f`).
- Documented the owner-trust contract in `docs/FRIDAY_LIVE_INSPECTION_BRIDGE.md` (`9ce9e70`).
- Verification boundary: source files were re-read and the expected code paths confirmed. GitHub Actions build run for `5ef253f` was queued at the latest observation; no Android build or instrumented-test result is claimed yet.
- This does not require a phone connection. Friday's internal identity, memory, self-diagnosis and resource adaptation remain local; external inspection is optional and independently controlled.
- Further tightened the inspection opt-in into a 15-minute maximum session. The expiry is persisted, checked on app/process restart, and automatically closes the socket; explicit pause and revoke also clear the expiry timer (`1ba2824`, `860d797`, `b18fe94`).
- Added instrumentation coverage for expiry and updated the disclosure/contract to make the time limit visible (`c21b830`, `b73f6ea`, `559027b`).
- Follow-up status handling keeps the UI at 'Inspection paused' after the socket closes rather than presenting a user-requested pause as a connection failure (`c304028`).
### 2026-11-28 — CI failure triage and booked offline-turn repair

- Re-read the failed Android validation run rather than treating it as a generic build failure. `debug-apk` compiled the debug and instrumentation APKs, Android Lint passed, and the release variant compiled; the overall gate failed on JVM assertions plus an emulator UI timeout.
- Fixed a real production-path issue in `ModelManager`: a typed offline-gap handle is deliberately unavailable and therefore not cached as a warm tier, but it remains a valid booked handle for the current turn. The send path previously failed to associate that handle with a tier and silently fell through to the heuristic provider. Booked, loaded, untracked gap handles are now passed to the backend without a reclamation pin; cached handles retain normal atomic pinning (`3e966ec`).
- Corrected a lifecycle test that expected a backend-unloaded handle to be reused even though the fake backend contract explicitly mints a distinct handle for each successful load (`cd75a41`).
- Corrected the LFM2.5 fallback filename aliases: the catalog pins Q4_K_S, while the path resolver still pointed those aliases at Q4_K_M (`805b6af`).
- The prior CI run also exposed an instrumented Compose test timeout while waiting for the Chat screen after first-launch setup. The failure is at `TypedConversationInstrumentedTest.kt:43`; it is not a compile failure. The disposable emulator also reports no `/dev/kvm` and failed one UI acceptance test. Investigate the onboarding navigation/test isolation separately; do not weaken the gate or report this as passing.
- Fresh build workflows have been triggered for the three repair commits. Latest observed runs were still in progress/queued at the time of this note; no green CI or installable APK is claimed until the newest canonical-main run completes.
- Corrected the UI acceptance test's stale navigation label: the production bottom bar is `Talk`, not `Chat`; the test was waiting for a node that does not exist, so it could never reach message submission (`377804d`).
### 2026-11-28 — cached-model liveness guard correction

- The newest JVM shard isolated the remaining lifecycle regression: `ModelBackend.isAvailable()` defaults to `true` for ordinary backends, so `ModelManager.wake()` could reuse a cached handle after the backend had independently unloaded it. The prior test correction was right to expect a fresh handle; production wake logic was missing the separate `isLoaded()` check.
- Fixed on `main` in `6a1176f992dc2c5cafc1fcd8a5a1f699c2b5aee4`: cached tier reuse now requires both `modelBackend.isLoaded(handle)` and `modelBackend.isAvailable(handle)`. This preserves the typed-gap distinction while rejecting stale ordinary handles.
- Exact canonical-main validation is running at [Actions #46173185181](https://github.com/mhw77655-hue/Friday/actions/runs/46173185181). At the latest observation all validation lanes had started; no green result or signed APK is claimed yet.
- The earlier conversation UI test failure was not solved merely by changing `Chat` to `Talk`: the timeout was on the second wait for the Talk tab after the setup screen was dismissed. The next run must confirm whether Friday's engine readiness reaches `Ready` on the emulator; do not weaken the production turn-path acceptance.
- Release readiness remains gated on successful exact-main validation, the follow-on permanent-signed release, signer/package/version/digest verification, and then owner-side in-place update. Current installed app is 1.0.631 per the latest owner correction; never uninstall or clear app data.
- Release inventory check: the newest published production release is `friday-v1.0.657` from `e4e53c6`; current main validation candidate `6a1176f` is 98 commits ahead. Do not direct the owner to install 1.0.657 as the current repair/update candidate; wait for exact-main CI and the matching permanent-signed release.
### 2026-11-28 — signed update published; parallel individual and cloud-task work

- Published `friday-v1.0.747` from exact CI-verified source commit `6a1176f992dc2c5cafc1fcd8a5a1f699c2b5aee4`. Signed-release workflow [#46174213300](https://github.com/mhw77655-hue/Friday/actions/runs/46174213300) completed successfully; release [1.0.747](https://github.com/mhw77655-hue/Friday/releases/tag/friday-v1.0.747) is public. APK version code `1747`, application ID `ai.friday.assistant`; signer SHA-256 `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8`; APK SHA-256 `69d27bf936c57a4eec4a69c37080f457c2db7dc6ed09255a21e478e32294fd7d`. Release assets include APK, metadata and checksum. This is the in-place update candidate; physical installation and handset acceptance still require the owner's device.
- Began Issue #189 / #191 cloud-work path with `TaskCapsuleCompiler` (`5768ec6`) and tests (`5e490b8`, `7931899`, `653feea`). The compiler accepts already-retrieved evidence rather than whole chat/memory stores; ranks and filters by a minimum relevance floor; excludes SENSITIVE material; requires an explicit task-specific approval flag for PRIVATE material; redacts common bearer/API credentials; preserves evidence provenance and artifact hashes; enforces a hard context budget; and computes a deterministic SHA-256 capsule digest. It is local-only and has no network or tool-execution authority.
- Follow-up code review tightened the compiler so only evidence actually serialized within the context budget is returned as selected evidence; low-relevance evidence is excluded. A budget test was corrected to use an oversized memory block rather than accidentally expecting a fitting block to be dropped.
- Build workflows for the compiler commits were queued at the latest observation; no passing test result is claimed yet. The compiler is a foundation and is not yet wired to the Anthropic Claude provider or app UI. Issue #191 clarifies Anthropic Claude is the required cloud reasoning provider; ChatGPT and GitHub are not substitutes.
- Issue #74 already has a production-composition held-out model-free learning experiment in `TurnPathAssemblyTest` with a learning-enabled versus disabled control. This is a bounded associative-learning demonstration, not broad semantic understanding. Keep extending the same individual/substrate and do not create another memory/identity system.
- Next parallel work: (1) physical owner-side installation and typed-turn/persistence acceptance for 1.0.747, without uninstall/data clear; (2) run and repair TaskCapsuleCompiler CI, then connect the compiler to the existing local `BlendedMemoryRetriever` and build the Anthropic Claude task adapter with encrypted credentials, per-task consent, usage accounting and durable checkpoints; (3) strengthen the developmental experiment with correction/reversal and a later interaction that is not a direct explicit-fact recall.
### 2026-11-28 — Claude task path and local learning work in parallel

- Extended Issue #189's task compiler to accept ranked results from Friday's existing `BlendedMemoryRetriever` through `compileRetrieved`; sensitivity is supplied by an explicit classifier and is never inferred from text (`e2070cd`, with production adapter test `3af8b4e`). Added capsule integrity verification so post-compilation edits to context or tool scope are rejected (`5bcd494`, `224841a`).
- Implemented the first Anthropic Messages API transport (`df3fdcf`) with streamed SSE text, configurable model ID, no callable Claude tools, 401/403 handling, 429/529 rate-limit classification, and parsed input/output/cache token usage. API key storage uses Android Keystore AES-GCM (`AnthropicApiKeyStore`). Usage and request metadata now survive `CloudModelRouter` (`0b3f2f7`, `41206ca`, `59b2924`).
- Added `AnthropicTaskWorker` (`d7cafa9`): accepts a verified task capsule only, requires a one-shot approval bound to the exact digest, submits only selected context and artifact references, and consumes approval before network I/O so an automatic retry cannot duplicate a billable task. Added a Claude connection/test/revoke section to Settings (`bd572b5`).
- Wired owner-configured Claude into `PlatformPorts` and the canonical `TurnPathAssembly` cloud provider pool (`0a43301`, `376e3d8`, `cf2022f`). No API key is present in the repository; provider registration is empty until the owner saves one. After saving, Friday must be restarted for the composition to pick up the provider. No live Claude request is claimed.
- Added JVM tests for streaming/usage/cache metadata, missing credentials, rate-limit behavior, router usage propagation, capsule privacy/redaction/relevance/budget/digest checks, and Android instrumentation for encrypted credential storage, task approval replay prevention, and exact outbound capsule minimization (`4ded302`, `101ac3d`, plus the earlier compiler tests).
- Latest canonical build run for the complete source tree is [Actions #46176583927](https://github.com/mhw77655-hue/Friday/actions/runs/46176583927), queued at the latest observation. No passing CI result is claimed. The 1.0.747 APK remains the last verified installable release and does not include these new Claude/task-compiler commits.
- Current boundary: the provider, local compiler, one-shot approval and test UI are committed; durable multi-step Claude checkpoints/recovery, usage/budget UI, retrieval sensitivity policy wired to production memory, and Builder/REA workflow integration remain unfinished. Continue with the same individual and existing memory substrate; do not split Friday or create a second identity.

### 2026-11-28 — Installed 1.0.747 feedback: reply integrity and speech handoff

- Owner tested 1.0.747 and observed generic prompts returning token/feature associations such as "hay, hey, here", rather than grounded project recall. Source inspection confirms this is the intended FridayModelFreeMind fallback when no language model is available; the associations are explicitly not semantic understanding. The cognitive/memory substrate can process and retrieve evidence, but the model-free expression path does not compose a grounded project answer from that evidence.
- Fixed the immediate misleading-response failure: project/conversation continuity questions now receive an honest limitation instead of arbitrary learned feature links (eda931e16); added regression coverage for both reported prompt forms (6931a39fa). This does not yet solve open-ended reasoning; that requires the reasoning provider and a grounded context-to-answer path to be active.
- Fixed the speech overlap root cause (ec36cd899): LatencyLayer.speakReply can choose the streaming speech callback, bypassing SpeechEngine.fullSpeak, which previously owned the acknowledgement flush. The full-reply handoff now explicitly stops active cached-WAV/live-TTS/cloud playback before starting the real reply.
- Live phone inspection at this check: enrolled, not revoked, but disconnected. No remote UI interaction or on-device verification was possible.
- CI for the new commits is queued; no green build or APK is claimed. Installed 1.0.747 predates these fixes. Do not ask owner to reinstall until the new source passes CI and a signed release artifact is verified.
- Next: get the queued build to execute and pass; then implement and wire a grounded project/conversation memory response path (not token association), verify speech handoff in instrumentation/device test, and package the next signed APK.


### 2026-11-28 — owner-triggered self-diagnosis capability opened

- Owner requested a workflow where he can describe a Friday symptom in ordinary language and the engineering agent can inspect relevant evidence and repair it without requiring the owner to reconnect live phone inspection or manually gather logs.
- Created [Issue #204](https://github.com/mhw77655-hue/Friday/issues/204) as the controlling implementation task for a privacy-bounded, owner-triggered diagnostic and repair handoff.
- Required architecture: bounded local structured event buffer; lifecycle correlation IDs; no message bodies/prompts/transcripts/audio/screens/notifications/files/secrets in diagnostics; preview-before-export; one-time owner-approved handoff; exact source-revision mapping; ranked evidence-based hypotheses; candidate patch, regression test, independent verification and durable case ledger.
- First vertical slice is voice acknowledgement overlap, followed by incorrect response routing/memory retrieval. No always-on inspection, background upload or arbitrary remote phone control is permitted.
- Repository facts read this session: canonical product line is `main`; Android source is under `mobile/app`; existing System → Export full diagnostic report produces `friday-device-report-v2`. This existing export is a manually initiated artifact, not yet proof of the requested automated symptom-to-diagnosis workflow.
- **Status:** issue created; no Android source implementation, CI run or APK has been produced for #204 yet. Next engineering action is to inspect the existing diagnostics implementation and add the first end-to-end event-capture → approved export → diagnosis correlation slice without broadening the live inspection bridge.


### 2026-11-28 — first executable self-diagnosis slice

- Added `TurnDiagnosticJournal`: a bounded, app-private, restart-persistent journal with closed enums for conversation lifecycle stage, provider route and outcome. It accepts no free-form text, prompts, transcript, audio, memory, exception message, URL or credential fields.
- Initialized the journal only in Friday's main Android process. It records turn correlation IDs and stage markers from the production `LatencyPipeline`: input accepted, acknowledgement started, route selected (model vs model-free), model request started, reply received, reply delivery, acknowledgement flush request, speech handoff, timeout and settlement.
- Added a dedicated one-time incident-evidence export in System settings. The owner chooses a destination through Android's document picker; nothing is uploaded or relayed automatically. This export contains only app/source/API metadata plus timestamps, exchange IDs and fixed lifecycle vocabulary. The broad support report remains separate and is not used as the default incident handoff.
- Added unit tests for bounded retention, restart recovery, correlation, fixed vocabulary and privacy metadata.
- Commits: `85dbbbd8` (journal), `f8ee4fdb` (tests), `842c57b5` (main-process initialization), `74bb0614` (pipeline instrumentation), `e3f558f8` (export API), `99e6fa5c` (System UI export), `e64494c6` (ack flush stage), `aeb49228` (additional lifecycle markers), `59ba6068` (exchange correlation correction), `d9d1fe33` (privacy test assertions).
- Current main: `d9d1fe338d9f9abea9726118680fc7a368f4fa44`.
- CI: Android build workflows are queued for the commits above; no green result is claimed yet. The signed-release workflow for the UI-only intermediate commit was skipped as expected because the release gate requires a verified candidate.
- Phone: enrolled but disconnected. No live handset state is claimed. This slice is designed to work without enabling Live Inspection; after the app update, the owner can explicitly export the narrow incident file if a symptom needs device-specific correlation.
- Remaining for #204: validate CI; add regression coverage for the acknowledgement/reply handoff; instrument memory retrieval/provider fallback with safe IDs and outcome classes; add the in-app problem-report/share handoff and durable case ledger; connect approved evidence to the REA/engineering workspace. Do not broaden telemetry or make any background upload.

- Follow-up CI triage: the prior queued run `46179639090` exposed a pre-existing Kotlin compile blocker in `TaskCapsuleCompiler.kt:274`: `values.forEach(::field)` is illegal because `field` is both a member and an extension. Replaced it with an explicit lambda (`values.forEach { value -> field(value) }`) in commit `214c78f6`. The downstream unresolved references in `AnthropicTaskWorker.kt` were compiler fallout from that first error. The exact-head build for `214c78f6` is queued as run `46179844956`; this fix is not yet validated.
## Autonomous development and inspection update — 2026-11-28

- The existing ObjectiveExecutionWorker is the production Builder loop: it is scheduled at app startup and every six hours, reconciles the canonical plan, recovers interrupted tasks, delegates bounded decomposition to the resident model or separately consented ChatGPT plan worker, creates isolated low-risk candidate PRs, polls CI, dispatches an exact-commit disposable Android sandbox, and requests an independent review. Candidate promotion remains gated; background Builder consent does not grant merge or release authority.
- The Builder's public ChatGPT background-use disclosure now states that bounded public objective context and selected public source excerpts may be sent. Private conversations, personal memories, phone snapshots and private files are not part of that task projection.
- The live inspection snapshot now includes bounded lifecycle event totals, crash/process-exit record totals, and aggregate counts by the fixed turn stage, route and outcome enums. The raw local event journal, exception messages, stack traces, prompts, transcripts, audio, memories, screen state and credentials remain excluded.
- Model-free project-continuity responses now take precedence over generic learned word associations, preventing the local response path from presenting loose feature links as conversation memories.
- CI exposed and the source/test suite was repaired for a missing task-capsule compiler import, a GitHub comment JSON assertion, a model-free continuity branch-order defect, and the wall-clock allowlist entry for the cloud task worker's security timestamps.
- Validation for the latest commits is queued in GitHub Actions; no green build or new APK is claimed yet. The live handset is enrolled but currently disconnected, so no fresh device snapshot has been observed in this session.