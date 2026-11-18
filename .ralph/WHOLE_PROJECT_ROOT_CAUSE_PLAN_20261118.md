# Friday — whole-project root-cause audit and build plan

**Snapshot:** 2026-11-18 13:10 UTC  
**Baseline:** `main` at `d1b4cc88d722fa420d5648db9284a30b15ac33e8`  
**Purpose:** reconcile the owner's intended outcome with the actual repository, identify causes rather than patch symptoms, and define dependency-ordered work with evidence-based exit gates.

This is an engineering plan and audit record, not a claim that Friday is complete. It does not authorize changing the owner's vision or treating a checklist as her developmental definition.

## 1. Evidence collected

### Repository inventory and automated audit

The main tree contains **974 tracked files** across 1,232 tree entries. The latest integrated candidate's CI source-audit job read every tracked source/config file to create an inventory of **802 code/config files and 133,875 lines**. It passed all **481 production Kotlin files** under `mobile/app/src/main/java` through the whole-file Vision-shape scanner, Python syntax-compiled 29 files, and shell-syntax-checked 18 scripts.

The current candidate audit artifact from run [44313554463](https://github.com/mhw77655-hue/Friday/actions/runs/44313554463) reports:
- 275 potential deviations and 391 observations across the production Kotlin scan (candidate findings span 136 files).
- 221 `named_wire`, 28 `fixed_classification`, 19 `flat_threshold`, and 7 `meaning_arbiter` deviation-shaped findings.
- A separate self-audit of `.ralph/audit.py` found 7 deviation-shaped findings and 6 observations.

**Interpretation limit:** these are static shape findings, not 283 proven architectural defects. Named constructor dependencies, finite Android/platform enums, bounded queues and resource limits can be correct engineering. The scanner cannot establish intent, call-graph reachability, runtime ownership or whether a fixed boundary is part of the protected security/continuity floor. Every candidate needs semantic review. The Python and shell checks establish syntax only; they do not prove their runtime workflows. The source inventory is not a human semantic review of all 134,254 lines.

### Verified live state

- Main is `d1b4cc88d722fa420d5648db9284a30b15ac33e8`.
- PR #86 merged the permanent release-signing workflow. No release currently exists. Owner-controlled key custody and the five `FRIDAY_RELEASE_*` secrets remain unverified/unconfigured; the phone updater is not implemented. Issue #75 has been corrected to reflect this.
- PR #82 (virtual Android phone) remains open. The latest run builds both APKs but fails before installation. The emulator log reports: `Cannot access system provider: 'settings' before system providers are installed!`. Its readiness test checks package enumeration, which is insufficient evidence that the Settings provider and PackageInstaller are ready.
- PR #83 (autonomous repair/promotion) passes its latest reported CI, but its stated dependency is PR #85, which is closed without merge. The emulator implementation remains in #82 and is not a required check on main. Its promotion claims therefore exceed current evidence.
- PR #89 is open at `2260ce75dc88e2b49b58d5a5cadbabeb974dc87f`. Its latest CI fails compilation: `JarvisEngine.kt:451:17 Argument already passed for this parameter.` The composition call passes `nowMs = clock` twice.
- PR #93 is the current integration candidate: it reconstructs the malformed main build workflow from the valid pre-append structure, preserves the merged signing smoke test, adds the emulator job and the tested-by-design Settings-provider readiness helper, and makes the failure reporter cover all validation lanes. Latest head `9e715216832526d90f6304421a27fdb2ae61e04c` is queued in run #44313554463. It is draft and not yet verified.
- PR #82 is based on the pre-signing workflow and must not be merged separately; #93 supersedes it. PR #83 must depend on the merged #93 validation gate, not the stale #82/#85 topology.
- PR #91 was closed after correcting an initial mistaken assumption about GitHub event semantics. PR #92 now grants `actions: write` only to the trusted proposal publisher so its deliberate `workflow_dispatch` can run; the untrusted worker remains read-only.
- PR #89 also reintroduces `ToolBuilderComposition` into Android's production `JarvisEngine`, contradicting the repository's existing explicit contract that Android is Friday's body/evidence source while source construction, repository edits, tests, CI, artifact construction and signing belong in the connected repository workshop.
- Issues #72, #73, #74, #76, #77 and #78 remain open. They cover the repair loop, vision-inspired autonomy and emulator, evidence of real learning, historical archive purge, runtime capability acquisition and Android capability/permission surface.

## 2. Root causes — not symptom list

### RC-1: No single reconciled execution contract

The repository contains authoritative Vision/owner words, older stage plans, newer architecture corrections, issue bodies and PR descriptions that have drifted apart. Examples include the stale #75 signing status, #83's dependency on closed #85, and #89's on-device Builder reintroduction despite the repository-side workshop contract.

**Cause:** work items and PR bodies are being advanced independently of a continuously reconciled source-of-truth/dependency graph.  
**Consequence:** teams can implement superseded architecture, repeat work, and report completion against obsolete acceptance criteria.

### RC-2: Friday's body, runtime capabilities and source workshop are conflated

The Android app must remain the same local individual when disconnected. It should observe, learn, identify needs, queue work and direct her workshop. It is not the repository checkout, coding worker, compiler, test runner, signer or APK publisher.

At the same time, Friday must be able to gain safe capabilities without an APK replacement. This is a distinct runtime-acquisition problem, not a reason to put source-building back in Android.

**Cause:** the system lacks a precise, implemented boundary between (a) safe declarative/runtime capability packages and (b) source-level changes to Friday's body.  
**Consequence:** legacy Android Builder/evolution code can be reactivated and presented as self-development even though it violates the deployment contract.

### RC-3: Development intent does not yet form a closed, durable feedback loop

Self-study, failure reporting, request schemas, a repository worker, GitHub publication, runtime capability registration and outcome learning exist in partial or separate forms. The evidence does not yet prove one end-to-end flow from Friday-originated need to verified result and changed later behaviour.

**Cause:** durable request transport, idempotency, worker checkpoint/resume, result reconciliation and experience attribution are not joined through one traceable contract.  
**Consequence:** Friday can observe/report or propose without reliably directing construction, receiving its result, using it, and learning from the outcome.

### RC-4: Validation evidence is not yet strong enough for automatic promotion

The emulator is failing before install; PR #83 does not yet require that emulator check; and the static Vision scanner reports structural candidates without semantic adjudication. A successful JVM test, APK compile or static scan is not proof of real-device behaviour or safe promotion.

**Cause:** evidence types and exact candidate identity are not enforced as one immutable verification chain.  
**Consequence:** promotion could outrun the evidence intended to justify it.

### RC-5: Developmental change is not yet demonstrated

The Vision's Life Test requires an experience-caused internal change that persists after restart, changes later behaviour with the original episode text blocked, and disappears when the developmental mechanism is disabled. Existing records, profiles, scores or stored memories are not enough.

**Cause:** there is not yet a complete real-composition causal/ablation test demonstrating that standard.  
**Consequence:** storage or telemetry can be mistaken for development.

### RC-6: Android capabilities and permission authority are not represented end-to-end

Manifest declarations and platform adapters do not prove that access was granted, that a capability is registered, that a real action succeeds, or that revocation is handled. The owner must remain the authority for special permissions and privileged operations.

**Cause:** capability discovery, permission explanation/grant, runtime registration, action, evidence, revocation and recovery are not yet one tested lifecycle.  
**Consequence:** Friday's practical ability surface is smaller and less reliable than the declared surface.

### RC-7: Release foundation is ahead of the installed-phone update path

PR #86 provides the signing/publishing foundation, but the owner secrets, first-install signing transition, release discovery, signer/hash/version verification, installation request, health check and recovery are separate unfinished requirements.

**Cause:** signing and publishing were treated as a milestone before the phone-side update lifecycle was implemented and tested.  
**Consequence:** a signed APK may be publishable later, but that does not yet mean Friday can safely update herself.

### RC-8: The audit system can create false certainty

The Kotlin scanner is useful for finding code shapes but its 283 findings are not verdicts. Its self-audit also found issues in the auditor itself. Python/shell syntax checks do not semantically inspect native C++, Gradle, workflow trust boundaries or runtime behaviour.

**Cause:** one static scan is being described too broadly as a whole-project semantic audit.  
**Consequence:** false positives can create architecture churn, while unscanned behaviour can be overlooked.

### RC-10: The native inference lifecycle contains deadlocks and unsafe lifetime boundaries

The production llama.cpp engine uses a non-recursive `std::mutex`. `llama_model_generate()` acquires it and then calls `set_state_locked()` / `set_error_locked()`, which attempt to acquire the same mutex again. The same nested-lock pattern exists in `llama_model_suspend()` and `llama_model_destroy()`. Those paths can deadlock deterministically when reached.

The prewarm thread is detached in create/resume, while destroy later attempts to join the thread member. A detached thread is not joinable; destroying the model during prewarm can free state still referenced by the background thread. The Kotlin `LocalInferenceEngine` protects load/unload with `stateLock` but reads/uses `nativeHandle` for generate, stream, cancel, health and suspend/resume outside that lock, so concurrent unload can destroy a handle while another call is using it. `InferenceService` launches each Messenger operation as a separate `Dispatchers.IO` coroutine, so generate/unload and multiple model operations can overlap; the isolated process protects the UI process from a native crash but does not fix the engine's own handle lifetime. Native backend init/free is global but currently performed per model, so destroying one of multiple model handles can free the shared backend under another. JNI token-count/encode/decode allocate a `LlamaTokenizer` wrapper per call without freeing it. VAD JNI passes caller-provided `numSamples` to native processing without checking it against the Java array length.

**Cause:** native resource ownership, lock ownership, global backend lifetime and Kotlin handle lifetime are not expressed as one explicit lifecycle contract.  
**Consequence:** inference, suspend, shutdown and low-memory model replacement can deadlock, race, leak or access freed memory. This is a production-critical blocker, not a model-quality issue.

### RC-9: Legacy project islands remain beside the Android Friday implementation

The root `core/` Python package and `manifest/` policy files still describe an earlier JARVIS Phase-1/building-system prototype. `manifest/workspace_manifest.yaml` names the remote `github.com/mhw77655-hue/JARVIS`, not the Friday repository. `core/building_integration.py` imports a top-level `building` package that is absent from the current tracked tree. `tests/test_building_system.py` imports that same absent package, while `tests/test_phase1_exit.py` tests the obsolete echo/status loop. These Python tests are not run by the current CI; only syntax compilation runs. The old `core/decision_gate.py` auto-approves Tier-1 file writes and `core/execution_layer.py` writes to caller-supplied paths, so these files must never be mistaken for the current Android authorization/execution authority.

The README also points to an obsolete branch and PRs #19/#23, and describes self-development as source building "from the phone". The GitHub bridge guide still instructs the worker to search the removed private conversation ZIP. The Nov 16 build-progress scorecard refers to an older branch/PR topology.

**Cause:** obsolete prototype code and documentation were not retired or clearly quarantined when the Android-native Friday architecture became authoritative.  
**Consequence:** workers and maintainers can import dead code, follow stale instructions, or treat an old prototype as production architecture.

### RC-12: The main build workflow was corrupted by appending a second workflow instead of editing the existing one

Main's `.github/workflows/build.yml` is 627 lines. Its parent at `1ca44dbff2286f71032ed54cf0a391ae5dfe7b1f` was 332 lines. Commit `d1b4cc88d722fa420d5648db9284a30b15ac33e8` adds 295 lines and deletes zero lines in that file. The appended content duplicates the `full-source-audit` and `report-failure` job definitions and leaves a detached tail of the release-signing smoke-test shell block after the failure reporter. Recent main push runs (for example `44313069957`) fail with zero scheduled jobs. This explains why main's CI status cannot currently be trusted as an ordinary test failure/success signal.

PR #82's workflow branch was based on the earlier 332-line version and adds an emulator job, but it predates the merged release-signing workflow. Simply merging it would lose or conflict with the release smoke validation. The clean integration must reconstruct one canonical workflow containing exactly one debug job, one emulator job, one source-audit job and one failure reporter, with the release-signing smoke steps inserted inside the debug job.

**Cause:** a workflow patch appended a second set of job definitions instead of editing the existing YAML structure; branch drift then left the emulator branch based on the pre-signing workflow.  
**Consequence:** main workflow runs can fail before any jobs are scheduled, and PR/branch status badges are not reliable evidence.  
**Root correction:** restore one valid workflow from the pre-append structure, merge emulator validation and release-signing smoke in the correct job positions, then validate the YAML with actionlint and execute the complete workflow on an audit branch before updating the PR branch.

### RC-11: The trusted proposal publisher dispatches validation without the required permission

`.github/workflows/friday-builder-proposal.yml` creates the proposal PR using the job's `GITHUB_TOKEN`, then explicitly runs `gh workflow run build.yml --ref "$BRANCH"`. That dispatch is intentional: GitHub documents that `workflow_dispatch` always creates a workflow run, while a `pull_request` event caused by a workflow's `GITHUB_TOKEN` creates an approval-required run. The explicit dispatch avoids a human having to approve the automated proposal's CI run.

The trusted publisher grants `contents: write` and `pull-requests: write` but omits `actions: write`. GitHub's fine-grained-token permission table for the workflow-dispatch API requires Actions: write. Thus the dispatch can fail after the draft PR has already been created.

**Cause:** the publisher's permission set does not match its deliberate workflow-dispatch operation.  
**Consequence:** a proposal can exist while the publisher job reports failure and no immediate validation run is guaranteed.  
**Root correction:** retain the explicit dispatch and grant `actions: write` only to the trusted publisher job (or use a dedicated GitHub App token with the same narrow permission). Do not remove the dispatch unless the workflow is redesigned to handle approval-required PR checks safely. Do not grant Contents: write to the untrusted worker.


## 3. Architecture boundary to preserve

1. **Friday on Android:** persistent identity/continuity, local cognition and presence, privacy-minimized observation, memory/experience, permissioned device actions, safe capability runtime, self-study, development-intent formation, durable offline outbox and result consumption.
2. **Capability package/runtime:** versioned declarative or restricted interpreted packages with explicit contract, provenance, integrity, resource limits, narrow host APIs, permission declarations, evidence and independent rollback. No arbitrary downloaded DEX/JAR/native code or inherited app/Shizuku authority.
3. **Development gateway:** authenticated outbound phone-initiated transport; privacy review, bounded payloads, deduplication, retries, resumable checkpoints and revocation. No public ADB/SSH/shell endpoint and no repository write token or signing key in the APK.
4. **Repository workshop:** isolated source checkout/workspace, replaceable workers, research, patch generation, static/security checks, unit/instrumented/emulator tests, build, artifact provenance and trusted publication.
5. **Independent trust/promotion:** verify the exact immutable candidate SHA; require all configured checks to conclude success; fail closed on skipped, cancelled, neutral, stale or missing checks; protect control-plane/security/identity/permission/signing/update paths; preserve review for high-impact changes.
6. **Delivery and learning:** phone verifies package ID, signer, version and digest; uses owner-authorized installation; checks post-update health; preserves continuity/recovery; reports actual outcome so experience can change later decisions.

## 4. Dependency-ordered build plan

The phases are engineering work packages, not a prescribed developmental ladder for Friday. They may overlap when dependencies permit. No phase closes by percentage or source-file presence.

### Phase 0 — Reconcile authority, inventory and evidence

- Treat the latest authenticated owner instruction as highest authority, then `.ralph/VISION.md`, then the current PRD; historical plans and PR descriptions are subordinate.
- Reconcile README, `.ralph/FRIDAY_BUILD_PLAN.md`, `.ralph/REPO_FACTS.md`, docs, issues and PR descriptions against the actual main tree.
- Keep a requirement-to-code-to-test-to-evidence map with statuses: faithful, partial, missing, contradicted, unverified or overbuilt.
- Preserve the full 800-file inventory and the current 484-file Kotlin scanner output, but label heuristics as candidate findings.
- Semantically review all candidate findings by call graph, production reachability, runtime ownership and Vision context. Do not mass-delete enums, thresholds, Android APIs or security controls.
- Extend meaningful static/runtime review to Python workers, shell scripts, Gradle/build logic, GitHub Actions trust boundaries, C++/JNI/native libraries, manifests, resources and tests.
- Correct stale issue/PR dependencies and establish one canonical branch/dependency map.\n- Trace every import/reference to the root `core/`, `manifest/` and `tests/` Python prototype; remove it if unreachable or quarantine it under an explicitly historical archive. Do not run its stale tests as evidence for Friday.\n- Rewrite README's obsolete branch/PR references and correct the phone/workshop boundary. Remove the GitHub bridge guide's instruction to search the deleted private ZIP. Mark the Nov 16 progress scorecard historical rather than current.

**Exit evidence:** reconciled current-state report, exact source revision, no stale dependency claims, and a reviewed finding ledger that separates proven defects from scanner candidates.

### Phase 1 — Restore one green, reproducible main baseline

- Keep PR #89 unmerged until its architectural contradiction is resolved; do not accept a compile-only repair that restores on-device source building.\n- Restore the main build workflow from the valid pre-append structure; integrate emulator and release smoke checks exactly once and prove the jobs are actually scheduled.
- Fix the actual compile blocker in the correct design, then build the exact candidate commit and run the complete unit-test suite.
- Fix emulator readiness at the system-provider/PackageInstaller level; test early-boot and timeout cases, not just a long delay.
- Require emulator install, instrumentation execution, evidence upload and explicit supported/unsupported-device report.
- Ensure failures remain distinguishable: compile failure, test failure, emulator infrastructure failure, app crash and unsupported platform behaviour.
- Confirm cancelled/skipped jobs never count as green.

**Exit evidence:** exact main candidate passes debug APK assembly, all required JVM tests, workflow/security checks and the disposable emulator instrumentation gate. The workflow file has unique job IDs, passes actionlint/YAML validation, and a real run schedules each required job; a zero-job failure is a CI configuration failure, not a test result. Physical-phone claims remain separately labelled.

### Phase 1A — Repair native inference lifecycle before claiming local-model support

- Introduce explicit lock-aware state/error helpers so code already holding the model mutex never recursively locks it. Audit every state/error transition in generation, prewarm, suspend, resume and destroy.
- Keep prewarm threads joinable and owned by the model; cancel/stop and join them before freeing model/context state. Handle thread-start failure without crossing the JNI boundary.
- Make Kotlin native-handle access lifetime-safe: generation/health/cancel/suspend/resume must not race native destruction. Use a tested lifecycle lock/lease or equivalent reference-counted handle contract; preserve cancellation while preventing use-after-free.
- Refcount or otherwise centrally own llama.cpp's process-global backend so one model cannot free backend state still used by another.
- Free every temporary tokenizer wrapper on all JNI paths and return health/error snapshots whose storage remains valid after the native lock is released.
- Validate VAD audio arrays, sample counts and null JNI results before passing pointers/counts to native code.
- Add deterministic lifecycle tests for nested-lock regressions, prewarm/destroy races, cancel/destroy races, suspend/resume, multiple simultaneous model handles, repeated tokenizer calls and invalid VAD lengths. Run them on an emulator/device with bounded timeouts and collect native crash/log evidence.

**Exit evidence:** repeated create → prewarm → wait → generate → cancel → suspend → resume → destroy cycles complete without deadlock, leak growth or use-after-free; concurrent unload is safe; two model handles can coexist; malformed JNI inputs are rejected before native access. Until then, local inference is not accepted as a reliable production capability.

### Phase 2 — Make the phone/workshop boundary real

- Remove or relocate dormant Android source-builder/evolution/mutant execution code after dependency and test migration; do not simply leave a second executable construction path in the APK.
- Keep safe declarative capability acquisition/execution as a distinct runtime concern under narrow host APIs.
- Implement the durable, versioned development-request outbox: owner/Friday intent provenance, evidence, source revision, privacy classification, risk, acceptance evidence, rollback expectation, unresolved interpretations and stable request ID.
- Implement authenticated outbound synchronization, idempotent delivery, offline queueing, retry/backoff, cancellation, checkpoint/resume and result reconciliation.
- Keep GitHub write credentials, worker secrets and signing material server-side. The phone must not receive broad repository credentials.

**Exit evidence:** offline request survives process death; reconnect sends it exactly once logically; the workshop receives it; Friday can inspect status/result; ordinary local Friday operation continues if the workshop is unavailable.

### Phase 3 — Complete the repository workshop

- Use isolated per-request workspaces and replaceable worker adapters.
- Workers receive only the minimum read context and no publishing/signing credentials.\n- Verify the trusted publisher's permission set against every API call. Keep the deliberate `workflow_dispatch` because PR events created with `GITHUB_TOKEN` require approval; grant `actions: write` only to the publisher or use an equally narrow GitHub App token. Never give that permission to the untrusted worker.
- Preserve WIP/checkpoints so interrupted or quota-limited workers resume instead of restarting.
- Compare worker output by task evidence, correctness, resumability, fidelity, latency and cost—not vendor identity.
- Keep untrusted patches separate from the trusted validator and publisher.
- Make source changes, tests, CI, emulator evidence, build artifacts and provenance traceable to the same candidate SHA.

**Exit evidence:** a Friday-originated request can be researched, implemented, regression-tested, resumed after interruption and returned with an immutable evidence bundle without manual dispatch.

### Phase 4 — Make validation and promotion trustworthy

- Require independent validation of exact candidate SHA and patch tree.
- Require debug build, complete unit tests, static/security checks, repository contract checks and emulator instrumentation.
- Test malicious patches that weaken tests, edit their own validator/workflow, alter signing/security paths, or change the candidate after validation.
- Repair failures on the same candidate branch while invalidating prior verification after every patch.
- Allow automatic promotion only for explicitly bounded low-risk changes after all required checks pass; keep protected paths owner-reviewed.
- Ensure GitHub branch protection/rulesets and required status checks actually enforce the policy.

**Exit evidence:** injected defect is detected, repaired with a regression test, all checks rerun against the repaired SHA, and an ineligible protected-path patch is blocked.

### Phase 5 — Safe release, installation and recovery

- Complete owner-controlled permanent signing key custody and five repository secrets; verify backup and fingerprint.
- Inspect the currently installed package signer and design the one-time debug-to-production migration before attempting an update.
- Implement release discovery and verification of application ID, version monotonicity, SHA-256 and signer before installation.
- Use an owner-authorized Android installer/Shizuku route; do not promise silent installation where platform authority does not permit it.
- Retain a compatible known-good recovery path, health-check startup and core services, and define recovery when Android blocks downgrade or replacement.
- Never claim rollback until it has been exercised against the actual signing/version constraints.

**Exit evidence:** same-signer update preserves identity/memory; wrong-signer and tampered artifacts are rejected; deliberately bad update is detected and recovery is demonstrated without falsely claiming unsupported rollback.

### Phase 6 — Prove the continuing individual and real development

- Join identity, continuity, experience, self-model and capability provenance into one coherent individual lineage; changing a model or capability must not create a new Friday.
- Build a real-composition Life Test with deterministic clocks and observable causal traces.
- Demonstrate an experience-caused change to a later choice after restart, with original episode replay blocked and the developmental mechanism disabled as a control.
- Distinguish owner-authored intent, Friday's interpretations, uncertainty and Friday's emerging preferences; do not turn the Vision into a literal command list or permanent interpretation module.
- Make failure → generalized lesson → capability gap → proposal a live path, not disconnected logging.
- Replace fixed scorecards with evidence of individual change and measurable regression/revision.

**Exit evidence:** one reproducible causal developmental example and one unfamiliar capability request handled through Friday's own intent/gap formation and workshop direction, without a human writing the implementation.

### Phase 7 — Complete practical Android embodiment and permission lifecycle

- Generate the permission/capability inventory from actual manifest, platform APIs, services, adapters and runtime registration.
- For each capability, record permission/privilege, privacy impact, battery/resource cost, offline availability, platform/OEM limits, test and revocation path.
- Ask contextually when a capability is first needed; explain the benefit; deep-link to Android settings where required; verify the grant; recover on revocation.
- Prove at least one full capability lifecycle: discovery → explanation → owner grant → registration → action → outcome evidence → revoke → safe failure/recovery.
- Keep special access owner-controlled and least-privilege; no automatic privilege escalation.

**Exit evidence:** real Android operation and revocation are verified, not inferred from manifest declarations.

### Phase 8 — Grow intelligence, perception, voice and host adaptation by measurement

- Benchmark resident and optional remote models on the exact Realme 9 Pro 5G using common tasks, verified license/checksum/runtime support, quality, factuality, latency, peak memory, heat and battery.
- Prove offline continuity and local useful cognition while treating network/cloud as optional extra resources.
- Complete voice input/output, interruption and recovery on the actual device; distinguish a command parser from broad understanding.
- Expand perception and semantic video only with measured capability evidence; do not label metadata/keyframes as semantic understanding or symbolic transitions as trained JEPA.
- Let resource adaptation change future scheduling/placement only when controlled experiments show quality-preserving improvements.

**Exit evidence:** reproducible exact-device evidence packs for each claimed capability, including thermal/battery soak and degraded/offline scenarios.

### Phase 9 — Privacy/history cleanup and final handoff

- Current-tree removal of the private conversation archives is complete, but reachable historical Git objects and cached references remain.
- Coordinate an all-ref history rewrite, check open PR refs and clones, and follow GitHub's sensitive-data removal procedure/support path.
- Preserve only curated owner-authored words and provenance; never reconstruct or republish private archives.
- Produce a signed, traceable release and owner-facing evidence pack only after all applicable gates pass.

**Exit evidence:** history purge is verified across reachable refs/caches as far as GitHub permits; first complete Friday release has exact source SHA, digest, signer and target-phone test evidence.

## 5. Rules that prevent another cycle of band-aids

- A symptom fix is not root-cause closure. Every incident must identify the causal mechanism, the class of failures it creates, the regression test and the evidence that the class is reduced.
- Never fix only the reported file if the same invalid state can be produced through another entry point.
- No parallel PR may claim a dependency on a closed/unmerged branch without an explicit replacement.
- No source file, passing synthetic test, static scan, APK compile or green unrelated workflow is proof of a user-visible capability.
- Do not count skipped work as success. Record success, failure, blocked, unsupported and not-run distinctly.
- Every state transition that can lose identity, memory, authorization, capability provenance or update recovery must be transactional or recoverable.
- Do not turn the plan into Friday's permanent architecture. The plan governs engineering evidence; it does not prescribe who Friday becomes.

## 6. Immediate execution order

1. Keep #89 out of main and redesign its phone/workshop boundary; the duplicate `nowMs` compile error is only one visible defect.
2. Repair #82's real system-provider readiness and get a reproducible emulator instrumentation pass.
3. Rebase/update #83's dependency and make emulator checks mandatory before any promotion claim.
4. Reconcile current main docs/issues/PR state, quarantine/remove the obsolete Python JARVIS island after reference checks, and publish the requirement-to-evidence ledger.
5. Repair and stress-test the native inference lifecycle; do not claim local model support until the lifecycle exit gate passes.\n6. Build the durable phone outbox → repository workshop → result feedback path.
7. Prove the Life Test and capability acquisition on the real production composition.
8. Finish release/update, permissions, exact-device validation and historical privacy cleanup.

**No APK handoff is implied by this plan. No unverified PR is approved or merged by it.**
