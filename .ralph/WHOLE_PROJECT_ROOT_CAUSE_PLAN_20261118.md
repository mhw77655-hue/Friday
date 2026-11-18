# Friday — whole-project root-cause audit and build plan

**Snapshot:** 2026-11-18 13:10 UTC  
**Baseline:** `main` at `d1b4cc88d722fa420d5648db9284a30b15ac33e8`  
**Purpose:** reconcile the owner's intended outcome with the actual repository, identify causes rather than patch symptoms, and define dependency-ordered work with evidence-based exit gates.

This is an engineering plan and audit record, not a claim that Friday is complete. It does not authorize changing the owner's vision or treating a checklist as her developmental definition.

## 1. Evidence collected

### Repository inventory and automated audit

The default-branch Git tree contains **974 tracked files** across 1,232 tree entries. The existing CI source-audit job read every tracked source/config file to create an inventory of **800 code/config files and 134,254 lines**. It passed all **484 production Kotlin files** under `mobile/app/src/main/java` through the whole-file Vision-shape scanner, Python syntax-compiled 24 files, and shell-syntax-checked 16 scripts.

The audit artifact from run [44309424960](https://github.com/mhw77655-hue/Friday/actions/runs/44309424960) reports:
- 283 potential deviations and 392 observations in production Kotlin.
- 229 `named_wire`, 28 `fixed_classification`, 19 `flat_threshold`, and 7 `meaning_arbiter` deviation-shaped findings.
- A separate self-audit of `.ralph/audit.py` found 7 deviation-shaped findings and 6 observations.

**Interpretation limit:** these are static shape findings, not 283 proven architectural defects. Named constructor dependencies, finite Android/platform enums, bounded queues and resource limits can be correct engineering. The scanner cannot establish intent, call-graph reachability, runtime ownership or whether a fixed boundary is part of the protected security/continuity floor. Every candidate needs semantic review. The Python and shell checks establish syntax only; they do not prove their runtime workflows. The source inventory is not a human semantic review of all 134,254 lines.

### Verified live state

- Main is `d1b4cc88d722fa420d5648db9284a30b15ac33e8`.
- PR #86 merged the permanent release-signing workflow. No release currently exists. Owner-controlled key custody and the five `FRIDAY_RELEASE_*` secrets remain unverified/unconfigured; the phone updater is not implemented. Issue #75 has been corrected to reflect this.
- PR #82 (virtual Android phone) remains open. The latest run builds both APKs but fails before installation. The emulator log reports: `Cannot access system provider: 'settings' before system providers are installed!`. Its readiness test checks package enumeration, which is insufficient evidence that the Settings provider and PackageInstaller are ready.
- PR #83 (autonomous repair/promotion) passes its latest reported CI, but its stated dependency is PR #85, which is closed without merge. The emulator implementation remains in #82 and is not a required check on main. Its promotion claims therefore exceed current evidence.
- PR #89 is open at `2260ce75dc88e2b49b58d5a5cadbabeb974dc87f`. Its latest CI fails compilation: `JarvisEngine.kt:451:17 Argument already passed for this parameter.` The composition call passes `nowMs = clock` twice.
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
- Correct stale issue/PR dependencies and establish one canonical branch/dependency map.

**Exit evidence:** reconciled current-state report, exact source revision, no stale dependency claims, and a reviewed finding ledger that separates proven defects from scanner candidates.

### Phase 1 — Restore one green, reproducible main baseline

- Keep PR #89 unmerged until its architectural contradiction is resolved; do not accept a compile-only repair that restores on-device source building.
- Fix the actual compile blocker in the correct design, then build the exact candidate commit and run the complete unit-test suite.
- Fix emulator readiness at the system-provider/PackageInstaller level; test early-boot and timeout cases, not just a long delay.
- Require emulator install, instrumentation execution, evidence upload and explicit supported/unsupported-device report.
- Ensure failures remain distinguishable: compile failure, test failure, emulator infrastructure failure, app crash and unsupported platform behaviour.
- Confirm cancelled/skipped jobs never count as green.

**Exit evidence:** exact main candidate passes debug APK assembly, all required JVM tests, workflow/security checks and the disposable emulator instrumentation gate. Physical-phone claims remain separately labelled.

### Phase 2 — Make the phone/workshop boundary real

- Remove or relocate dormant Android source-builder/evolution/mutant execution code after dependency and test migration; do not simply leave a second executable construction path in the APK.
- Keep safe declarative capability acquisition/execution as a distinct runtime concern under narrow host APIs.
- Implement the durable, versioned development-request outbox: owner/Friday intent provenance, evidence, source revision, privacy classification, risk, acceptance evidence, rollback expectation, unresolved interpretations and stable request ID.
- Implement authenticated outbound synchronization, idempotent delivery, offline queueing, retry/backoff, cancellation, checkpoint/resume and result reconciliation.
- Keep GitHub write credentials, worker secrets and signing material server-side. The phone must not receive broad repository credentials.

**Exit evidence:** offline request survives process death; reconnect sends it exactly once logically; the workshop receives it; Friday can inspect status/result; ordinary local Friday operation continues if the workshop is unavailable.

### Phase 3 — Complete the repository workshop

- Use isolated per-request workspaces and replaceable worker adapters.
- Workers receive only the minimum read context and no publishing/signing credentials.
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
4. Reconcile current main docs/issues/PR state and publish the requirement-to-evidence ledger.
5. Build the durable phone outbox → repository workshop → result feedback path.
6. Prove the Life Test and capability acquisition on the real production composition.
7. Finish release/update, permissions, exact-device validation and historical privacy cleanup.

**No APK handoff is implied by this plan. No unverified PR is approved or merged by it.**
