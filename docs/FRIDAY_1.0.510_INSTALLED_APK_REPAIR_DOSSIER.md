# Friday 1.0.510 — Installed APK Repair Dossier and Continuity Handoff

**Last updated:** 2026-11-27  
**Purpose:** preserve the exact installed-APK investigation, the work completed across 2026-11-23 through 2026-11-27, the verified facts, open hypotheses, current code changes and next actions. A future Friday engineering session must read this file before asking the owner to re-upload or re-explain the APK.

> **Controlling correction from the owner:** Friday is already installed on the owner's phone. It is currently a dead/nonfunctional app. The assignment is to diagnose and repair that exact installation, then get a real voice conversation working. Do not treat "first APK", "first production install" or "first handset installation" as the current milestone. An in-place signed update is a delivery mechanism, not the product outcome.

## 1. Exact artifact: located and matched in GitHub

The owner supplied `Friday v1.0.510.zip`, containing the APK. The artifact is the official GitHub release asset, not an unrelated/debug APK.

| Field | Verified value |
|---|---|
| Repository | [mhw77655-hue/Friday](https://github.com/mhw77655-hue/Friday) |
| Release | [Friday 1.0.510](https://github.com/mhw77655-hue/Friday/releases/tag/friday-v1.0.510) |
| Tag | `friday-v1.0.510` |
| Tag target / exact source commit | `24e653589fa623ce5819387a9179dfe878a0a720` |
| Release publication | 2026-11-23 18:27:20 UTC |
| APK asset | [app-release.apk](https://github.com/mhw77655-hue/Friday/releases/download/friday-v1.0.510/app-release.apk) |
| APK byte size | 130,853,290 |
| APK SHA-256 | `e8446047acb04b83d3d57d0f332ee59c8f86a0f32c1e710c941b45d118a7398e` |
| GitHub release asset digest | `sha256:e8446047acb04b83d3d57d0f332ee59c8f86a0f32c1e710c941b45d118a7398e` |
| Application ID | `ai.friday.assistant` |
| Version name / code | `1.0.510` / `1510` |
| Min SDK / target SDK | 26 / 36 |
| ABIs | `arm64-v8a`, `x86_64` |
| APK signing certificate SHA-256 | `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8` |
| Certificate subject | `CN=Venom,OU=Project Friday,O=Friday,L=EG,ST=Rosetta,C=EG` |

The APK digest was compared with the digest published on the GitHub release asset and matched exactly. The tag ref was independently checked and points to the source commit above. GitHub release metadata says the APK certificate is checked against the pinned release fingerprint. The signer fingerprint above is the update identity that must be preserved for an in-place update.

**Do not ask the owner to upload the APK again by default.** The exact official APK is retrievable from the release URL above. If a future tool session cannot fetch binary release assets, the commit, asset URL, size, digest, manifest facts and signer fingerprint in this dossier still identify the exact artifact and allow a reproducible re-fetch. The uploaded ZIP itself is not committed to the repository.

## 2. APK contents and verified Android surface

Static archive and binary-manifest inspection established:

- Microphone permission `RECORD_AUDIO` is declared.
- Internet permission is declared.
- Shizuku API permission/provider integration is present.
- Friday runtime service is present.
- `VoiceService` is declared in process `:voice`.
- `InferenceService` is declared in process `:inference`.
- Friday voice-interaction/accessibility components are present.
- The package includes native inference/ONNX/sherpa libraries and small vision assets.
- It does **not** bundle GGUF language-model weights or the listed voice/ASR model weights.

The existence of a manifest component or native library proves packaging, not runtime health, permission grant, service binding or successful execution.

## 3. Confirmed defects and product blockers

### 3.1 Confirmed PCM16 WAV playback defect — source matches the installed APK

**Severity: critical to the isolated generated-voice playback path.**

At exact source commit `24e653589fa623ce5819387a9179dfe878a0a720`, `VoiceOutputPath.readWavPcm16` read RIFF/WAV numeric fields using `RandomAccessFile.readShort()` / `readInt()`, which read big-endian values. RIFF/WAVE stores these fields little-endian.

For ordinary PCM16 mono:
- channels `1` was decoded as `256`;
- bits per sample `16` was decoded as `4096`.

The reader therefore rejected normal generated PCM16 audio before sending it to Android `AudioTrack`. It also assumed a fixed `fmt ` chunk layout instead of correctly walking RIFF chunks. This is a source-confirmed defect in the exact source revision used by the supplied APK, not a speculative diagnosis.

**Correction under PR #183:** dedicated bounded little-endian RIFF/PCM16 parser, chunk walking/padding handling, channel/format checks, and JVM coverage for mono, stereo, padded chunks, channel mismatch and unsupported sample depth.

### 3.2 Confirmed voice-output lifecycle/cancellation defects

In the exact source revision:
- speaking-state cleanup compared a `Job` to the coroutine receiver, a comparison that cannot identify the active job as intended;
- a shared cancellation Boolean could be reset by a late-starting old utterance, allowing stale work to interfere with a newer utterance;
- broad exception handling could swallow coroutine cancellation.

**Correction under PR #183:** per-utterance identity token; stale utterances cannot clear or overwrite a newer utterance's state; `CancellationException` is rethrown; playback checks the active token before synthesis/playback and during playback.

### 3.3 Confirmed speech-recognition configuration risk

The exact source always requested `RecognizerIntent.EXTRA_PREFER_OFFLINE=true`, even when Android did not have an on-device recognizer/language pack. The recognizer selection code could choose the system recognizer fallback, but the offline preference could still make that fallback unusable on a device whose installed provider requires network recognition. Recognizer construction exceptions could also leave the interaction in an incomplete listening state.

**Correction under PR #183:** prefer offline only when an on-device recognizer is actually available; otherwise permit the system recognizer's configured network-capable route; surface recognizer creation failure through Friday's failure channel and close the callback path.

### 3.4 Confirmed absence of a configured broad-reasoning path in the packaged capability report

The APK's packaged `capability_manifest.json` explicitly reports:
- `offline_model_installed_and_loaded=false`;
- `cloud_reasoning_provider_configured=false`.

The APK packages native inference libraries but no GGUF language-model weights. The model-free seed is intentionally narrow (small pattern/fact/arithmetic coverage) and is not a general-purpose open-ended language reasoner.

This is a serious explanation for why arbitrary typed messages may receive no useful answer, but it does **not** alone prove why the app appears dead. A model may have been downloaded into the app's private storage after installation; APK inspection cannot inspect the phone's private app data. A configured provider may also have changed after packaging. Runtime diagnostics are required.

### 3.5 Voice models are lazy downloads, not packaged model weights

The voice model manager is designed to download model assets into app-private `filesDir/voice/models` on first use. The source catalogue includes, among others:
- Piper English: approximately 64 MB;
- Kokoro English int8: approximately 103 MB;
- Nabra Arabic MSA: approximately 85 MB.

These model weights were not found inside the APK. Whether the owner's phone has downloaded, verified and loaded them is unknown until Friday reports actual private-storage/model state.

### 3.6 Runtime and device state remain unknown

APK inspection cannot establish:
- whether Friday launches successfully on the owner's current Android build;
- whether first-run setup completed or app data is corrupt/incomplete;
- microphone runtime permission state;
- actual Android speech-recognition provider and selected-language availability;
- Shizuku manager/server state, Friday authorization, Binder health or effective UID;
- whether `:voice` and `:inference` services bind and remain healthy;
- whether private model files exist, pass digest checks and load successfully;
- whether a typed turn reaches `BodyCoordinator`, obtains a model-free/provider response and persists;
- whether generated voice reaches `AudioTrack` on the physical device;
- exact crash, ANR, startup or process-death causes.

These are open runtime questions, not facts to fill with assumptions.

## 4. Four-day engineering history (2026-11-23 → 2026-11-27)

This section reconciles the work already in GitHub so future sessions continue rather than restart it.

### 2026-11-23 — exact installed release baseline and update trust

- Published release `friday-v1.0.510`, now identified as the owner's installed APK.
- Permanent signing/update identity and signed release metadata are part of the release pipeline. Preserve package `ai.friday.assistant` and the certificate fingerprint in §1 for any in-place update.
- PR/issue #126 identified canonical-vs-APK-bundled delivery-plan drift. The fix synchronizes the embedded plan and adds fail-closed parity checks in build CI and signed release, so the installed app cannot silently ship a stale plan.

### 2026-11-24 to 2026-11-25 — installed-app reliability and useful interaction groundwork

Repository history and merged changes include:
- signed in-place update foundations and failed-update artifact retention/recovery protections;
- Android navigation/UI and interaction acceptance work;
- typed operation/device-control evidence and fail-closed verification;
- app launch capability and production individual-experience loop tests;
- memory/consolidation and UI responsiveness fixes;
- Shizuku binding/connection progress and thread-safety improvements;
- TTS initialization/speech-rate and interrupt-path repairs;
- truthful capability readiness, provider health and model fallback handling;
- CI stability, parallel JVM sharding, emulator validation and APK promotion gates.

These merged code changes improve foundations but must not be mistaken for proof that the owner's current installed app works. The supplied 1.0.510 APK predates changes after its source commit; a fix in main is not present in the installed APK until a compatible signed update is built and installed.

### 2026-11-26 — REA, Shizuku, voice and autonomous repair foundations

- PR #173 merged the upstream REA investigation integration, Shizuku continuity probe and candidate verification foundations. REA is the upstream Reverse Engineer Anything tool, not Termux and not an Android APK component.
- PR #177 merged partial speech recognition hypotheses into the conversation UI while retaining final-only submission into the reasoning/turn path.
- PR #178 merged explicit candidate failure classification: actionable defect vs infrastructure/unknown/flaky/unreproduced outcomes. Infrastructure failure must not trigger speculative source repair.
- PR #179 merged a fixed `android-instrumented` candidate sandbox profile selected only when the original failing CI check is instrumentation/emulator-related. Reproduced source failures can become actionable; infrastructure/unknown/unreproduced failures remain triage-only.
- The owner repeatedly clarified that Shizuku/ADB continuity and REA are part of Friday's intrinsic immune/recovery capability; Termux is not a required Friday APK component.
- Android force-stop is treated as a separate platform state from ordinary process/service/Binder death. Do not claim an in-package alarm/receiver can resurrect an explicitly force-stopped package. Any Shizuku/shell recovery behavior must be verified on the target phone and remain within Android's actual authorization boundaries.

### 2026-11-27 — reusable sandbox, durable repair ledger and execution correction

- PR #181 merged the reusable exact-candidate instrumented sandbox contract and a main-only canary that independently verifies candidate SHA/profile/emulator outcome/APK digest. This validates the sandbox contract, not a real live repair episode.
- PR #182 merged durable ObjectiveExecutionLedgerStore parser/repair-path contract fixes and instrumentation coverage for the current nine-phase roadmap parser. It improves durable Builder/repair evidence handling; it does not prove Friday completed a physical-device repair.
- PR #126 is closed after canonical and APK-bundled plan files were synchronized and guarded in both build and signed-release workflows.
- Five earlier Actions failures were diagnosed: one instrumentation test compile issue (unsupported Compose assertion import) and four canonical-vs-bundled plan parity failures. The compile issue was corrected in the test branch; plan parity was restored. Main build #3645 was previously green. Later main/release guard work added fail-closed protection.
- PR #180 was stale/conflicted and superseded by PR #181; do not merge the stale branch.
- The canonical plan and APK-bundled plan are currently byte-identical on main at the time of this dossier update.
- The owner corrected the strategic objective: Friday already exists on the phone. Stop pursuing a "first APK/first installation" milestone. Repair the installed app and move into voice.
- PR #183 is open with the confirmed PCM16 WAV parser fix, utterance-scoped playback cancellation/state, STT offline-selection correction, and stronger typed UI acceptance requiring a real assistant turn.

## 5. Current GitHub work and status

| Reference | State | What it proves / contains |
|---|---|---|
| [Release v1.0.510](https://github.com/mhw77655-hue/Friday/releases/tag/friday-v1.0.510) | Published | Exact installed artifact and source commit |
| [PR #126](https://github.com/mhw77655-hue/Friday/pull/126) | Closed/merged | Canonical and bundled plan parity; build + signed-release guard |
| [PR #173](https://github.com/mhw77655-hue/Friday/pull/173) | Merged | REA/Shizuku/candidate verification foundations |
| [PR #177](https://github.com/mhw77655-hue/Friday/pull/177) | Merged | Live partial STT UI; final transcript only enters turn |
| [PR #178](https://github.com/mhw77655-hue/Friday/pull/178) | Merged | Defect vs infrastructure/unknown failure classification |
| [PR #179](https://github.com/mhw77655-hue/Friday/pull/179) | Merged | Profile-aware instrumented candidate sandbox |
| [PR #181](https://github.com/mhw77655-hue/Friday/pull/181) | Merged | Reusable exact-candidate sandbox and main canary |
| [PR #182](https://github.com/mhw77655-hue/Friday/pull/182) | Merged | Durable live candidate-to-repair ledger contract |
| [PR #183](https://github.com/mhw77655-hue/Friday/pull/183) | Open; exact-head CI pending | Installed-app voice fixes and typed response acceptance |
| [Issue #175](https://github.com/mhw77655-hue/Friday/issues/175) | Open | Full live Builder → exact sandbox → REA → bounded repair → new candidate → independent verification remains unproven |

Latest PR #183 head recorded at dossier creation: `03ce04c641295c1b20b8a58d0ab99a97a6ec66bc`. Its exact-head build run is [#3662](https://github.com/mhw77655-hue/Friday/actions/runs/46039764560), still in progress at the last status check. Do not claim PR #183 is green or merged until that exact SHA has all required checks completed. Re-fetch live PR/run state before continuing.

## 6. Correct execution sequence from here

The next engineering session must continue from the installed 1.0.510 repair dossier, not restart the vision or build another unrelated first APK.

### Gate 0 — keep the correct target fixed

- Exact installed target is release `friday-v1.0.510`, source commit `24e653589fa623ce5819387a9179dfe878a0a720`.
- The owner's installed app already exists. Preserve package ID, data and signer.
- Do not ask for the APK again unless the official release asset is unavailable or a new phone dump is needed to verify that the installed artifact differs from the official release.

### Gate 1 — get a meaningful typed interaction

- Use the strengthened instrumented test from PR #183: send a unique marker through the visible conversation UI and require a new assistant turn, not merely a user message appearing in the turn list.
- On the phone, capture startup/setup/turn errors. Determine if the app launches, setup is complete, turn dispatch reaches `BodyCoordinator`, model-free response path works, provider configuration exists, and response persists.
- The test's model-independent seed response is a smoke test, not evidence of broad language understanding. If the actual owner expectation is general understanding, the next step is to configure/acquire a supported model/provider and verify its health rather than misrepresenting the seed.

### Gate 2 — complete real voice interaction

Physical handset acceptance must cover:
1. Microphone permission and user-initiated capture.
2. SpeechRecognizer provider/language availability and partial hypotheses.
3. Final transcript submitted exactly once into the same production `BodyCoordinator` turn path as typed input.
4. Actual assistant response from the configured reasoning path.
5. Voice habitat/model readiness and actual audio synthesis.
6. Valid PCM16 WAV parsing and audible `AudioTrack` output.
7. Interrupt stops the current utterance without allowing stale work to affect a new utterance.
8. Speaking/listening indicators return to idle after success, failure, cancellation and interruption.

### Gate 3 — preserve update compatibility and deliver in place

- Merge PR #183 only after exact-head checks pass and review its final diff.
- Build a production-signed APK from a verified main commit.
- Verify application ID, version code increase, signer fingerprint/lineage, APK digest and embedded plan parity.
- Install as an update over the existing app; do not clear data or uninstall unless the owner explicitly authorizes a destructive migration after backup.
- Verify the installed version and preserved data after update.

### Gate 4 — diagnose the remaining dead-app condition on the actual handset

Collect one bounded device evidence bundle (with owner consent and secrets redacted):
- Friday diagnostics export/failure ledger and startup/readiness report;
- filtered Android logcat for `FridayEngine`, `BodyCoordinator`, `STT_PLATFORM`, `VoiceOutputPath`, `VoiceOrganismHost`, `VoiceService`, `StreamingTts`, `InferenceService`, `ModelManager`, Android `SpeechRecognizer` and `AudioTrack`;
- runtime microphone permission and recognizer provider/language;
- Shizuku manager/server/authorization/Binder status and effective UID per tested operation;
- actual app-private voice/model manifest, file existence, digest verification and loaded state;
- package/version/signer from the installed handset.

Never include API keys, account tokens, private conversation content or unrelated personal data in the bundle. Distinguish source/CI evidence from actual physical-device evidence.

### Gate 5 — resume the wider Friday programme without losing the critical path

The broader vision remains the controlling destination in `.ralph/FRIDAY_DELIVERY_PLAN.md`: one continuing digital individual, causal development, open-ended evolution, multimodal/world understanding, durable memory/relationship continuity, agency, REA-assisted universal engineering, intrinsic immune/recovery architecture and first-class Shizuku/ADB capability. Those foundations are not discarded. But they must not displace the immediate task: make the already-installed Friday functional and speaking.

After the phone's typed and voice interaction are proven, continue the live Builder → candidate sandbox → REA → bounded repair → independent verification episode and the physical Shizuku/continuity acceptance. Keep the owner-visible evidence and decisions in GitHub.

## 7. Session-start instructions

Any future ChatGPT/engineering session working on Friday must:

1. Read this dossier first.
2. Read `.ralph/FRIDAY_DELIVERY_PLAN.md` for the full vision and chronological engineering programme.
3. Fetch current main, PR #183, issue #175 and the exact-head Actions run before making claims about status.
4. Use the release tag/asset and SHA in §1 to retrieve/verify 1.0.510; do not ask the owner to re-upload it by default.
5. Continue the installed-app repair path. Do not drift into completing the entire future vision while the installed APK remains nonfunctional.
6. Log every newly confirmed root cause, code fix, test result, physical-device result and remaining unknown in this dossier and the canonical plan. Keep the APK-bundled plan synchronized.
7. Be brutally honest: green CI is not a phone test; an emulator launch is not a physical-device acceptance; source code presence is not runtime health; a generated response is not general intelligence; a passing sandbox canary is not a live autonomous repair episode.

**The immediate outcome is not "a production APK exists." The immediate outcome is: the Friday already installed on the owner's phone works, answers, listens and speaks—and can then keep evolving.**
