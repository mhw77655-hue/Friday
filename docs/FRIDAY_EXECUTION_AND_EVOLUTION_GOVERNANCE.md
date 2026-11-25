# Friday — Execution, Evidence and Evolution Governance

**Status:** normative cross-objective engineering standard  
**Authority:** `.ralph/VISION.md` and `.ralph/FRIDAY_DELIVERY_PLAN.md`  
**Scope:** every objective, production subsystem, research candidate, self-repair path and release  
**Purpose:** make the existing Friday vision executable without freezing her eventual cognitive architecture.

This document is not a competing architecture, roadmap or new objective. It defines how engineering and research decisions must be investigated, implemented, challenged and accepted under the one active delivery plan.

## 1. Governing principle

Friday is one continuing individual. Models, algorithms, representations, tools, voices, devices, cloud services, source implementations and runtime processes are replaceable resources. No current implementation is presumed to be her permanent brain.

The engineering process must preserve two things simultaneously:

1. **Continuity and protected invariants:** identity lineage, owner authorization, privacy, security, durable experience, provenance, signing, recovery and rollback.
2. **Evolutionary freedom:** the ability to test materially different computational mechanisms and replace inferior implementations without dividing Friday into multiple identities or silently narrowing the owner's intended outcome.

Do not optimize for module count, source-code volume, benchmark vanity or architectural novelty. Optimize for demonstrated outcomes and the ability to improve them safely.

## 2. Evidence and claim discipline

Every material engineering statement must be labelled as one of:

- **DESIGNED:** specified, but not implemented.
- **IMPLEMENTED:** source exists; production reachability is not yet demonstrated.
- **UNIT-VERIFIED:** isolated logic passes its stated tests.
- **INTEGRATION-VERIFIED:** the real production composition passes an integration test.
- **CI-VERIFIED:** a named commit passed a named workflow and its required jobs.
- **EMULATOR-VERIFIED:** a named build passed on a named emulator image/API level.
- **DEVICE-VERIFIED:** a named build was installed and exercised on an identified physical device; exact test and observed postconditions are recorded.
- **LONGITUDINALLY-EVALUATED:** behaviour was measured over repeated experiences, restarts and held-out situations.
- **UNKNOWN / BLOCKED:** evidence is missing or an external prerequisite prevents the test.

A successful build does not establish runtime correctness. A passing mock does not establish production wiring. A UI click callback does not establish the requested postcondition. A selected model provider does not establish that model weights were loaded or that inference succeeded. A stored experience does not establish developmental learning.

Every accepted claim links to its source revision, implementation entry point, test or experiment, result artifact and verifier.

## 3. Intent-to-outcome traceability

For each important owner requirement, preserve this chain:

1. Original owner-authored statement or approved source.
2. Interpreted outcome and constraints, with ambiguities recorded rather than silently resolved.
3. Acceptance criteria expressed as observable behaviour.
4. Production source entry points and dependencies.
5. Runtime observations and actual postconditions.
6. Independent verification and retained evidence.
7. Any deviation, decision and owner authorization.

Assistant summaries are navigation aids, not replacements for owner-authored source. If an implementation cannot be mapped to a requirement, classify it as research, optional infrastructure, historical code or unexplained behaviour; do not imply it satisfies the vision.

## 4. Whole-application tracing standard

A trace must follow a real execution path, not merely find similarly named symbols.

Use complementary evidence:

### 4.1 Source and compiler-aware analysis

- Git history, blame, changed-file review and dependency reports establish source provenance.
- Kotlin compiler/IDE symbol resolution establishes declarations, references, overrides, extension functions and type relationships.
- Gradle compile tasks and dependency resolution establish the configured build graph.
- Java and Kotlin call paths must include lifecycle callbacks, coroutines, Flow collectors, dependency injection, reflection and generated code where applicable.
- Native analysis must include JNI registrations, exported symbols, C/C++ call sites, build flags and packaged ABI libraries.
- Android manifest components, intents, services, receivers, permissions, foreground-service types and exported boundaries must be included.

A lexical scanner or name-based graph may help navigate source, but it must be labelled lexical. It is not a compiler-proven call graph and must never be reported as proof of runtime reachability.

### 4.2 Runtime tracing

For a reproduced action or failure, capture a privacy-minimised correlated trace containing:

- Exact app version, source revision and artifact digest where available.
- Device/emulator model, Android API, ABI and relevant resource state.
- User-authorized action identifier and timestamp.
- UI/Compose semantics or accessibility observations.
- Coroutine/job, service, process and IPC transitions where relevant.
- Permission and effective privilege state at the time of the operation.
- First Friday-owned exception frame and relevant cause chain.
- Expected versus observed postcondition.
- Independent verification result.
- Recovery attempt, checkpoint and rollback outcome.

Use Logcat, Android process-exit history, Perfetto, Compose UI Test, UIAutomator/Espresso, Macrobenchmark, native diagnostics and bounded host-side ADB instrumentation where appropriate. ADB is a development harness, not a hidden production permission bypass.

### 4.3 Trace result

Each trace identifies:
- Confirmed path and unobserved edges.
- First failing boundary.
- Direct cause and contributing conditions.
- Affected dependents and failure class.
- Evidence that would falsify the diagnosis.
- Minimal repair candidate and regression test.

Do not equate a source reference with execution, an execution event with correctness, or a restart with recovery.

## 5. Research from raw mechanisms

Research begins with original papers, algorithms, reference implementations, datasets, benchmark protocols, source code, technical documentation and licensing/provenance—not assistant summaries or technology rankings.

For each candidate, record:
- Original source, version, license and provenance.
- Mechanism and assumptions, not just the product name.
- Capabilities and known failure modes.
- What could transfer to Friday and what cannot.
- Runtime, memory, energy, latency and platform requirements.
- Data and rights requirements.
- Experiment design and rejection criteria.

Relevant research families include developmental and multi-timescale learning, predictive/world models, associative and reconstructive memory, causal inference, action-conditioned learning, recurrent and continuous-time computation, sparse/conditional computation, non-neural algorithms, program synthesis, evolutionary optimization, resource-aware execution and resilient distributed systems.

No research family is a mandatory cognitive department. No donor's architecture is imported wholesale merely because it performs well.

## 6. Experimental method for architectural evolution

Material architectural changes require a falsifiable hypothesis and a comparison against a baseline.

Use the smallest experiment that can distinguish competing explanations. Appropriate methods include:

- Ablation studies to test whether a mechanism contributes.
- Controlled A/B tests using the same task distribution and resource conditions.
- Differential and replay testing to preserve externally observable behaviour.
- Property-based tests and fuzzing for broad input/state coverage.
- Fault injection, concurrency tests and resource-pressure experiments.
- Causal controls and negative controls to rule out shortcut explanations.
- Held-out cross-domain tests to measure generalization.
- Longitudinal evaluation across experiences, process death and restart.
- Pareto analysis across quality, latency, memory, energy, privacy, offline availability and reliability.
- Independent specification and security review.

Record negative results. Do not retain a mechanism merely because it already exists. Do not accept a replacement merely because it is newer or faster on one metric.

For component replacement, preserve a behaviour corpus first; implement behind a stable interface; compare old and new paths; use shadow execution only where side effects are safe; promote only after correctness and regression gates pass.

## 7. Architectural drift review

Before accepting a significant design or implementation, examine whether it accidentally introduces:

- Permanent ownership of a cognitive function.
- A mandatory fixed cognitive sequence.
- A closed capability taxonomy.
- A specific model or provider as Friday's identity or brain.
- Hardcoded personality, emotional or developmental outcomes.
- Transcript replay or stored profile text presented as learning.
- A scheduler, registry or orchestrator becoming an unchallengeable executive.
- Local/cloud workers being treated as separate individuals.
- Resource optimization that silently degrades the intended outcome or continuity.
- Self-repair that certifies its own changes.
- A mutable component controlling its own trust boundary.

A detected pattern is a review trigger, not an automatic rejection. Explain the actual conflict with the owner-approved outcome and propose the least constraining correction.

## 8. Self-development and repair promotion

Friday's eventual development loop is:

**observe → explain → hypothesize → design → simulate → prototype → measure → adversarially validate → compare → promote or reject → monitor → roll back if regressed → retain the learning.**

The loop may improve its own implementation, but it cannot remove or bypass the protected floor.

Required promotion sequence:

1. Preserve the current known-good version and task state.
2. Reproduce the defect or define the capability gap.
3. Map the real production path and original behavioural requirement.
4. Produce a candidate with provenance, dependency and permission declarations.
5. Run in an isolated workspace/process/runtime with bounded resources and no implicit privileged access.
6. Run targeted regression, broad regression, adversarial, security and compatibility checks.
7. Obtain independent evaluation; the candidate or its authoring model cannot be its sole judge.
8. Require owner authorization for privileged, destructive, identity/signing, privacy-sensitive or irreversible changes.
9. Promote through the trusted signed delivery path.
10. Verify health and postconditions after promotion.
11. Automatically roll back on defined regression or failed health checks.
12. Preserve the failed candidate and evidence for diagnosis; do not erase the failure history.

Low-risk recovery may retry idempotent work, select an already verified fallback, restart an isolated process, rebuild disposable caches or restore a verified checkpoint. Source changes, dependency replacement and privileged operations require stronger gates.

The recovery authority must be smaller and more protected than the mutable systems it supervises. No repair agent, model, CI job or external advisor can independently authorize its own privileged release.

## 9. Independent verification

The developer and the owner are not the only judges of correctness.

Use independent sources:
- Reproducible builds and artifact provenance.
- Automated tests maintained separately from the implementation where practical.
- A disposable Android emulator and isolated test data.
- Physical-device acceptance on the target handset.
- Independent architecture, security and specification review.
- Held-out and longitudinal behavioural evaluations for developmental claims.

Every test records exact source revision, artifact digest, test command, environment, result and retained logs. Skipped, cancelled, timed-out or unexecuted tests are not passes.

Physical-device reports must distinguish the observed fact from Friday's inference. Include only owner-approved and privacy-filtered evidence. Never upload chat content, transcripts, notifications, credentials, screen content or personal files by default.

## 10. Tool allocation

Use tools for the question they can actually answer:

| Question | Methods and tools |
|---|---|
| What source changed and why? | Git, GitHub history, blame, PR diff |
| Does this symbol resolve and where is it referenced? | Kotlin compiler/IDE analysis, Java tooling, Clang/JNI analysis |
| What is packaged? | Gradle, APK Analyzer/apkanalyzer, aapt, apksigner, bundletool as applicable |
| What actually executed? | Logcat, Perfetto, structured trace IDs, Android process-exit history |
| Does the UI action dispatch? | Compose UI Test, UIAutomator, Espresso |
| Does the real effect happen? | Integration test plus independent postcondition probe |
| Is performance better? | Macrobenchmark, Perfetto, memory/energy/thermal measurement |
| Are state/concurrency protocols sound? | TLA+ model checking and adversarial concurrency tests |
| Are structural invariants sound? | Lean 4 proofs and runtime conformance tests |
| Are bounded architecture/permission structures valid? | Alloy analysis |
| Are candidate algorithms better? | Python experiment harnesses, controlled datasets, ablations and held-out evaluations |
| Is a portable capability safe? | Explicit host-call policy, Wasm/WIT only after the sandbox and authorization boundary are specified and tested |

Formal proofs apply to the model and assumptions they actually cover. They do not automatically prove Kotlin/C++ implementation correctness. Link formal invariants to implementation refinements and conformance tests.

## 11. Release and acceptance discipline

A release candidate must pass, as applicable:

- Clean build from the declared source revision.
- Lint, unit, property and relevant integration tests.
- Instrumented tests for every critical visible action.
- Regression tests for every previously reproduced defect.
- Debug and release packaging/signature verification.
- Manifest, permission, ABI and dependency review.
- Install, upgrade and rollback checks.
- Network-disabled, process-death/restart, missing/corrupt model, permission-revocation and low-resource tests.
- Physical-device acceptance for target-device claims.

A successful build is a packaging result, not a product acceptance result. A green emulator run is not proof of physical-device compatibility. A debug APK signed with a disposable CI key is not the permanent signed release.

## 12. Current physical-device evidence baseline

The owner supplied `New folder (1).zip` from the canonical GitHub main branch. It is a diagnostic evidence bundle, not an APK archive: it contains 35 screenshots, two text self-testing records and a JSON device report; no `.apk` entry is present.

The report identifies:
- App version `1.0.510`, source revision prefix `24e6535`.
- Realme `RMX3472`, Android 14 / API 34, ABI `arm64-v8a`.
- Shizuku server running and Friday permission granted, but the privileged UserService failed with `process name suffix must not be null`.
- Friday's runtime preference reported disabled and the foreground service stopped at report time.
- `LLAMA_CPP` selected but no model confirmed loaded.
- Friday identity present but zero recorded experiences and zero associations.
- Voice process bound, but the reported scheduler had completed zero tasks; TTS readiness and STT activity did not independently prove audible playback or a complete speech round-trip.
- Self-diagnosis recorded an active ENGINE failure and an identity-lineage warning; the organ graph reported 27 unreachable nodes, which must be triaged by planned-versus-actionable status rather than treated as 27 confirmed defects.
- Screenshots show primary blue buttons with low-contrast grey labels in that installed build.

The report is evidence about that historical installation and source revision only. It is not evidence that later source changes are installed on the phone.

The repair candidate has since added a non-null Shizuku UserService process-name suffix and a button typography correction, but those changes still require a fresh physical-device run to verify.

A separate CI artifact named `app-debug.apk` was retrieved from workflow run `45476957875`. It is a debug build of the then-current repair candidate, not the owner's `1.0.510` installed APK and not a release-signed artifact. It must not be represented as the APK contained in `New folder (1).zip`.

### Concrete defects now under repair

The supplied report and subsequent CI evidence exposed additional verification gaps that are now represented in the candidate:

- The active diagnostic classifier previously failed to classify the observed string `Failures: ERROR — ENGINE: ERROR x1` as an error because it only recognized numeric "down/degraded" counts. It could therefore report zero diagnostic errors while the FailureSurface reported an active ENGINE failure. A pure severity classifier and regression tests now cover explicit errors, warnings, down/degraded counts, clean and unknown states.
- The journal-clear action showed a Toast after an asynchronous journal operation. The failing instrumented run exposed a Looper/thread crash on this feedback path. The candidate removes the redundant Toast and explicitly returns to Main before updating Compose state.
- The Keep Friday alive setting used an asynchronous lifecycle snapshot and independent service polls without a revision guard. A stale read could overwrite a newer owner toggle in the visible UI. The candidate gates snapshot/poll updates by a local revision, exposes a stable test tag, catches Android foreground-service start/stop runtime exceptions without discarding the owner's persisted preference, and tests the preference through the real Settings switch.
- The System screen diagnosis buttons previously had no visible completion identity for a manual rerun; self-diagnosis also had no running/disabled state. The candidate exposes completed run counters/timestamps, progress labels and instrumented tests that prove each button causes a new run.
- The earlier Jev-removal and journal-clear instrumented tests scrolled controls and asserted visibility immediately. The candidate waits for Compose idle after scrolling before tapping. This corrects the test's viewport synchronization; only the subsequent instrumented result can establish whether the credential-removal production path itself is correct.

These are candidate changes, not accepted repairs until the current workflow passes. The previous 32/34 instrumented result remains historical evidence and must not be overwritten by the newer candidate's claims.

### Required next physical-device checks

1. Install the exact candidate build and record its version, source revision, digest and signing identity.
2. Re-run Shizuku binding; prove the UserService's actual UID and execute only the declared reversible probes.
3. Toggle Keep Friday alive on and off; verify persisted owner preference, foreground-service state and truthful blocked-start handling.
4. Run both System-screen diagnosis buttons and verify that each publishes a new result with updated run identity.
5. Run an actual local model download/load/inference round-trip, or report precisely why no compatible model can be loaded.
6. Verify voice capture, recognition, synthesis and audible playback separately.
7. Record a real experience, restart Friday, retrieve it and test changed behaviour in a new situation.
8. Re-run the complete diagnostic export and compare the exact findings against this baseline.

## 13. Change-control rule

This standard does not authorize a rewrite or automatic scope expansion. It requires evidence before architectural commitments, targeted repair of confirmed defects, and incremental evolution against the existing Objectives A–G.

Update the one active delivery plan when owner intent or evidence justifies a change. Keep research proposals, historical plans and assistant-generated summaries subordinate to that plan. Preserve the original evidence and record why any requirement or implementation changed.


## 14. Local Station and cloud engineering boundary

The phone/cloud operating contract is defined in [FRIDAY_LOCAL_STATION_AND_CLOUD_DEVELOPMENT_ARCHITECTURE.md](FRIDAY_LOCAL_STATION_AND_CLOUD_DEVELOPMENT_ARCHITECTURE.md). The repository migration contract is defined in [FRIDAY_REPOSITORY_STRUCTURE_AND_MIGRATION.md](FRIDAY_REPOSITORY_STRUCTURE_AND_MIGRATION.md). Both are subordinate to the canonical delivery plan.

A phone is a target-device station, not a generic GitHub Actions runner. The installed Android process may execute only authenticated, signed, device-bound and allowlisted diagnostic/test operations. It does not execute arbitrary remote shell, scripts, DEX/JAR/native code or untrusted workflow steps. Heavy builds and broad test suites run on reproducible CI/desktop workers. Device-specific observations flow outward through an authenticated, privacy-minimised durable channel; the station has no inbound open port requirement.

Cloud workers produce untrusted candidate changes in isolated branches/workspaces. A separate verifier checks the exact diff and evidence. GitHub writes, merge, release, signing, secrets and protected repository settings obey least-privilege authorization and owner policy. The code author is never the sole verifier. Phone enrollment keys, GitHub authorization and APK signing keys are separate trust domains.

Do not claim the cloud/phone loop exists because a payload builder, GitHub API client, local task inbox or plan sync worker exists. Prove enrollment, signed task delivery, replay protection, durable execution, result upload, candidate verification and physical-device outcomes separately. See LS-0 through LS-8 in the architecture contract.

Android execution is opportunistic and resumable, not immortal. Use platform-supported scheduling, visible foreground work only where justified, durable checkpoints, idempotency and app-start reconciliation. The system must report when Android defers or prevents work rather than promise uninterrupted execution.
