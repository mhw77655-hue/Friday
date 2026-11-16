# Friday build progress

**Snapshot:** 2026-11-16  
**Working branch:** `audit/friday-offline-runtime`  
**Overall end-state build completion:** **25%**

This is an equal-weight, 16-outcome engineering estimate. Each outcome contributes 6.25 percentage points; the table shows the estimated fraction of that outcome implemented. Code that has not passed current CI, Life Tests or target-phone validation is discounted. Separate draft branches are not counted as integrated functionality.

25% does **not** mean a quarter of a finished app is ready to install. It means the repository contains meaningful foundations across several areas, while the end-to-end individual, developer, and real-device acceptance gates remain largely open.

## Outcome scorecard

| # | Outcome | Completion | What still blocks closure |
|---:|---|---:|---|
| 1 | Independent Android foundation | 48% | Green current-head build and tests; process-death recovery; exact-phone lifecycle validation; release hardening |
| 2 | Persistent identity, history and experience | 35% | Demonstrate identity and developmental state across restart, offline use and model replacement; protected recovery |
| 3 | Lightweight English intelligence and model routing | 30% | Pin and benchmark candidate models on the exact handset; verify quality, memory, speed, thermal and battery behaviour; prove safe routing |
| 4 | Voice, hearing and interruption | 42% | Reliable end-to-end speech lifecycle, interruption and recovery; offline English ASR; real-device audio tests |
| 5 | Continuous presence and environmental awareness | 35% | Prove lifecycle continuity and camera/presence behaviour on the handset; broaden perception beyond presence signals |
| 6 | Nano Liquid Symbiosis host adaptation | 35% | Calibrate learned strategies against measured quality and energy; broader runtime adaptation and long-run tests |
| 7 | Experience-driven developmental learning | 20% | One durable later-behaviour change with restart, replay-blocked and mechanism-off controls |
| 8 | Predictive cognition / JEPA-inspired mechanisms | 15% | Train/validate learned representations and prediction error; useful prediction-led compute; do not label symbolic transition counts as JEPA |
| 9 | Self-model and individuality | 25% | Evidence-calibrated self-knowledge and persistent preference/strategy development |
| 10 | Capability Foundry / Builder | 15% | Remove or relocate dormant Android builder/evolution execution code; harden Cloud Ralph into a branch-only, credential-isolated worker; add per-request intake, independent gates and owner-controlled promotion |
| 11 | Native phone capabilities | 45% | Reliable multi-step workflows, visible permissions, recovery and exact-device validation |
| 12 | Autonomous self-development | 10% | Durable request envelope and synchronization, connected repository worker, independent Vision gate, approval, deployment feedback and Life Test |
| 13 | Security, provenance and rollback | 35% | Complete end-to-end authorization model, auditable worker provenance, signed update and tested rollback |
| 14 | Life Tests | 15% | Real-composition harness proving continuity and one experience-driven developmental change under controls |
| 15 | Target-device validation (Realme 9 Pro 5G) | 0% | No current evidence pack or verified model/voice/video/thermal soak from the exact handset |
| 16 | First complete Friday release | 0% | No release-candidate acceptance, signed production artifact or owner-authorized handoff |

**Arithmetic:** the 16 milestone scores sum to 405 percentage points; divided by 16 gives 25.3125%, rounded to 25%.

## Existing stage PRD status (separate from the end-state score)

The checked-in `.ralph/prd.json` contains 16 stories for the older Stage 3 / Gate 3c scope. At this snapshot, **10 are marked passed and 6 remain open**. Those six are still acceptance gates and must not be inferred complete from the overall 25% score:

| Priority | Open story | What must be demonstrated |
|---:|---|---|
| 8.2 | `LAUNCH-WAKE-ORDER` | Typed engine readiness; safe access while wiring, failed wiring and concurrent screen access; CI tests and incident prevention. Real first-launch race still requires the phone. |
| 9 | `ONE-2-COMPOSITION` | Exactly one Android production composition, injected time, test-only fakes, no Termux dependency and Android integration proof. Its current AC2 requires legacy Human Core to be live, which conflicts with the Vision's Human Core quarantine; revise that criterion before closing the story. |
| 10 | `ONE-3-LIFETEST` | Real-composition scenario runner, restart/model replacement, expected-fail controls and evidence packs. |
| 11 | `ONE-4-RECORDER-LEDGER` | Structured flight recorder, append-only developmental ledger, continuity-law enforcement and a visible Mirror screen. |
| 12 | `ONE-5-IDENTITY-SYNC-FLOOR` | Friday/she identity through owner-authorized admin revision, repository/phone sync checks and protected-floor file tripwires. |
| 13 | `ONE-6-SEED-REPORT` | Final stage report, DEV-1 replay-blocked/mechanism-off developmental evidence, full CI/APK and evidence-pack links. |

These six are the remaining stories in the *current stage PRD*, not the whole Friday vision. Voice/hearing, full model validation, semantic video, JEPA, the secure Builder, the wider Life Test and exact-phone release validation remain broader end-state work.


## What exists in the current foundation branch

The following are present in source on `audit/friday-offline-runtime`; presence in source is not the same as verified device behaviour:

- Android-native app and production composition work, including an isolated `:inference` process boundary and an in-app llama.cpp/JNI model path.
- App-private model catalogue/download infrastructure and conservative host-aware model admission. Candidate model descriptions are not proof of capability, accuracy or refusal behaviour.
- Voice output infrastructure, voice process work and lifecycle-aware Android integrations; full speech quality and interruption are not yet proven.
- Camera-based local presence signals; this is not full visual understanding or identity recognition.
- Nano Liquid Symbiosis mechanisms that observe host constraints, learn workload outcomes, contribute a bounded reasoning-memory reserve to model admission, and suppress background self-study when its learned budget is exhausted.
- A structural source index, runtime graph, host observations and local reflection. The source index is metadata, not Kotlin-source comprehension.
- A self-study reflection now produces a bounded repository-development request proposal. A regression test protects the rule that Android is not a source-development environment.
- Added a strict versioned request-envelope schema/validator, privacy approval requirement and credential screening.
- Added patch-path validation and tests to block edits to workflow/security control files and signing/release artifacts.
- Added a read-only OpenCode proposal worker and a separate owner-restricted publisher workflow that can create draft PRs only. The Android build workflow now runs Builder contract tests with a read-only token and checkout credentials disabled.
- These Builder changes are still in the unmerged foundation branch and have not passed a full current-head CI run. The example request is deliberately unapproved; the worker workflow has not been dispatched.

## Repository and verification state

- PR [#11](https://github.com/mhw77655-hue/Friday/pull/11) is the foundation branch targeting `main`. It remains an open draft and must not be merged until its current-head CI and acceptance gates pass.
- PR [#14](https://github.com/mhw77655-hue/Friday/pull/14) contains additional English-model and Nano Liquid Symbiosis work, but is a separate unmerged draft with a stale/non-mergeable base. It is not counted as integrated.
- PR [#16](https://github.com/mhw77655-hue/Friday/pull/16) contains video intake and a symbolic predictive-cognition bootstrap, but is a separate unmerged draft with a stale/non-mergeable base. Video import/keyframe sampling is not semantic video understanding; its Hunch Engine is not a trained JEPA model.
- PR [#12](https://github.com/mhw77655-hue/Friday/pull/12) is another separate offline-runtime draft based on a different audit branch. Its overlapping runtime/model/voice changes require a source-level comparison before any part is selected.
- Latest verified foundation run [#721](https://github.com/mhw77655-hue/Friday/actions/runs/43777826363) passed on head `c524720f7cc00f46015d24b4a77ad40faf644efd`: Android debug APK assembly succeeded; the full `:mobile:app:testDebugUnitTest` task succeeded; Builder request/patch contract tests passed (12 Python tests); JUnit results and a debug APK artifact were uploaded.
- This is CI proof only. No exact-phone cold-start test, inference-process death/rebind test, model smoke-load, or target-phone thermal/energy report is available. The APK artifact remains an intermediate CI artifact and has not been handed over.
- Earlier runs were cancelled when later commits superseded them; those cancellations are not compile failures and are not passes.
- `main` is not intentionally modified by this work. No APK is being handed off in this stage.

## Hard boundary: Friday does not build source inside Android

Android is Friday's deployed body and local evidence source. Repository source edits, code execution, tests, CI, artifact construction and signing belong in the connected repository development environment. No Git write token, signing key or arbitrary source-execution environment belongs in the APK.

The Android self-study change now creates a proposal only. It does not synchronize that proposal, assign a worker, read arbitrary repository source, edit code or verify a build. The basic repo-side worker exists, but the durable request transport and safe worker handoff are not implemented. Cloud Ralph is manually PRD-driven and currently has unsafe direct-main/write-credential behaviour; do not connect self-generated requests to it until hardened.

Dormant legacy `ToolBuilder`, evolution and mutant-environment source files still exist under the Android module. They are not constructed by the current production composition, but their location and executable implementation still conflict with the desired architecture. Removing or relocating them requires dependency and test audit; simply leaving them uncalled is not the final architecture.

## Remaining build sequence

### Gate 1 — prove the foundation branch
1. Finish a fresh current-head Android debug build.
2. Run the full baseline JVM unit-test task and fix every failure.
3. Inspect APK artifact contents and confirm no development worker, repository credential or source-build environment is packaged.
4. Exercise native library load, inference IPC, service death/rebind and process isolation with tests. CI green is a code gate, not phone validation.

### Gate 2 — reconcile the competing implementation branches
1. Compare PR #12 against PR #11 and record which changes are unique, duplicated, obsolete or incompatible with the Vision.
2. Rebase PR #14 and PR #16 onto the verified foundation head one at a time.
3. Resolve conflicts by preserving the strongest coherent implementation, not by blindly accepting either branch.
4. Run independent CI for each integrated workstream. Keep each PR draft until its own acceptance criteria pass.

### Gate 3 — make the repository Builder real
1. Remove or relocate legacy Android builder/evolution/mutant-execution source and update dependency/test boundaries.
2. Define a versioned, privacy-reviewed development-request envelope with stable identity, source revision, evidence, acceptance criteria, risk, rollback expectation and unresolved questions.
3. Persist and deduplicate requests locally while offline; synchronize them through an authenticated development gateway when available.
4. Keep GitHub App credentials server-side with least privilege. Never put a PAT, signing key or broad repository token in the APK.
5. Give each request an isolated, resumable repository workspace and replaceable worker adapter.
6. Require independent compile/test/security/Vision evaluation. High-impact changes remain behind explicit owner approval.
7. Return commit, CI, artifact digest and deployment outcome to Friday as evidence. The loop is not complete until she observes the resulting behaviour and learns from it.

### Gate 4 — prove one continuing individual who develops
1. Complete the shared Android production turn path without restoring legacy Human Core as a fixed cognitive authority.
2. Build a real-composition Life Test harness with deterministic time and observable traces.
3. Demonstrate one narrow change caused by actual experience or correction; prove it changes a later choice, survives restart, remains after original-episode replay is blocked, and disappears with the learning mechanism disabled.
4. Add a Development Ledger/Mirror view that distinguishes observed evidence, inference, confidence, reversibility and uncertainty.

### Gate 5 — grow intelligence, senses and symbiosis by measurement
1. Select an English resident model and an on-demand reasoning model using the same task set; verify license, architecture, tokenizer/chat template, checksum and actual runtime support.
2. Benchmark real quality, refusal profile, factuality, cold start, peak process memory, tokens/second, thermal stability and battery cost on the Realme 9 Pro 5G.
3. Extend Nano Liquid Symbiosis only where controlled tests show a quality-preserving improvement; do not equate a learned resource profile with full self-optimization.
4. Complete voice and offline hearing, then local visual/audio understanding and semantic video reasoning.
5. Develop JEPA/successor representations and reflex compilation as separate measurable work. Do not call rule counts or exact-match transitions neural JEPA.
6. Test phone actions as visible, permissioned and reversible multi-step workflows.

### Gate 6 — release and handoff
1. Pass current-head CI, native tests, Life Tests, security/provenance checks and release checks.
2. Produce a traceable signed artifact in CI and document its exact source commit and digest.
3. Install only through the authorized update path, then capture real-device startup, memory, model, voice, battery and thermal evidence.
4. Deliver the APK only after the release gates pass and the owner asks for the handoff.

## Definition of finished

Friday is not finished when code compiles or when a large APK exists. The first complete build is finished only when the same individual remains present across offline/restart/model replacement, can use the phone's supported senses and actions, develops from experience, can direct repository-side construction safely, can judge the resulting work independently, and has passed the target-device and release acceptance gates.

Until then, percentages remain estimates of engineering completion, not claims of sentience or production readiness.
