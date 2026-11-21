# Objective 1 — adversarial foundation validation

**Status:** owner-defined final validation stage; not yet executed  
**Initial adversarial baseline:** `95f4f150f495d39212da0e54591da2724b005fbe`  
**Current validation head:** freeze from `main` only after the recovery regression CI passes  
**Purpose:** attempt to falsify the Objective 1 foundation before the owner declares it complete.

## 1. Mission and honesty boundary

This is not another compile check, source review, or ordinary happy-path test run. The test campaign must actively attempt to make every retained Objective 1 foundation subsystem fail, then verify that failures are contained, reported truthfully, recovered safely where recovery is promised, and never recorded as success without evidence.

Literal exhaustive execution of every possible input, schedule, device state, permission combination and environment is mathematically and operationally infeasible. The acceptance standard is therefore **measured, risk-weighted, reproducible adversarial coverage**, not a claim that every possible state has been tested.

No test result may be generalized beyond its execution environment. JVM tests do not prove Android framework behaviour; emulator results do not prove the owner's physical handset; abstract Lean proofs do not prove Kotlin/C++ refinement; a passing build does not prove runtime correctness.

## 2. Scope inventory and test mapping

Start by enumerating every tracked executable/configuration artifact and every production entry path. Assign each item one of:
- directly executed by a test;
- exercised through an integration or end-to-end path;
- statically checked only;
- generated/vendor/build artifact;
- intentionally excluded, with a written reason and compensating evidence.

For every production source file, record its owning subsystem, entry path, critical invariants, associated tests, coverage result and unresolved risks. Unmapped production code is a validation gap, not an implicit pass.

Initial repository inventory at the target baseline:
- 932 tracked files;
- 475 production Kotlin files;
- 251 Kotlin JVM test files;
- 6 Android instrumented-test files;
- 7 native C/C++ source files;
- 14 Python files.

These are file counts, not test counts or coverage percentages. Recompute them from the tested commit.

## 3. Adversarial test layers

### A. Static, build and dependency integrity
- Build debug and release variants from a clean checkout.
- Run Android Lint and compiler/static checks available in the repository.
- Validate manifests, exported components, permissions, package identity, native ABI packaging and release metadata.
- Audit dependency versions, licenses, known vulnerabilities and unexpected network/runtime dependencies.
- Verify all CI lanes are required and that missing, skipped, empty or stale test reports cannot become green.
- Exercise clean build, incremental build and cache-disabled build paths.

### B. Unit, boundary and property-based tests
For each critical pure function and state transition, generate valid, invalid, boundary and adversarial inputs. Use deterministic seeds and preserve the failing seed and minimized input.
Target invariants include identity stability, monotonic versions, bounded resource use, authorization-before-effect, no false completion, provenance preservation, idempotency, atomic writes and valid state transitions.

### C. Mutation testing — prove the tests can catch bugs
Deliberately introduce temporary mutations in isolated worktrees or generated copies: invert conditions, remove authorization checks, skip verification, corrupt version comparisons, change retry limits, drop persistence writes, reorder transitions and replace failure returns with success.
The relevant tests must fail. Surviving critical mutants expose weak or missing tests. Never merge a mutation into production.

### D. Fuzz and malformed-input testing
Fuzz parsers, serializers, command/intent resolution, capability manifests, model/release metadata, update manifests, IPC payloads, archive extraction, persistence decoding and native/JNI boundaries.
Include truncation, oversized lengths, invalid encodings, duplicate keys, deeply nested structures, path traversal, zip-slip, integer overflow, malformed UTF-8 and unexpected null/empty fields.
Require bounded execution, no process crash, no memory corruption and explicit rejection where input is invalid.

### E. State-machine and model-based testing
Generate long action sequences and compare implementation behaviour with explicit reference models for:
- task planning, invocation, independent verification, completion and failure;
- owner authentication, authorization, approval, revocation and privileged effects;
- capability registration, selection, execution, evidence and retirement;
- memory writes, reads, provenance, conflict resolution and restart;
- update discovery, signature/integrity checks, staging, install handoff, migration, health checks and rollback;
- bootstrap, process lifecycle, cancellation and recovery.

Explore every transition in small finite models; use randomized sequence generation and state-space reduction for larger models. Any mismatch is a defect.

### F. Concurrency and race stress
Exercise simultaneous requests, duplicate callbacks, cancellation during writes, process death during transactions, competing updates, mailbox contention, lock ordering, retries and lifecycle overlap. Use controlled schedule/model checking where available and repeated stress runs for schedules not deterministically controlled. Record seeds and traces.

### G. Fault injection and resilience
Inject one fault at a time, then combinations:
- network unavailable, DNS failure, timeout, partial download and connection reset;
- missing/corrupt model, inference timeout, backend crash and malformed model response;
- denied, revoked or changed permissions; Shizuku unavailable or binder death;
- SQLite busy/corrupt/full disk, interrupted write and failed migration;
- low memory, CPU pressure, thermal throttling, battery constraints and storage exhaustion;
- process kill, force-stop, reboot, Activity recreation, service death and callback duplication;
- invalid signature, digest mismatch, wrong package, signer mismatch, replay and downgrade;
- unavailable capability, lying capability result and independent verifier failure.

Expected behaviour must be specified before each injection. Friday must never claim an action or update succeeded when the independent evidence is absent. Recovery must preserve identity and durable state or report precisely what could not be recovered.

### H. Android application and lifecycle testing
Run feature/application journeys on disposable emulators across selected API levels and configurations. Include install/upgrade, first launch, typed input, local persistence, restart, process recreation, permission changes, notification and background-work constraints, configuration changes, app data migration and offline operation.
Use Android instrumented tests for framework/device-dependent behaviour. Do not substitute JVM tests for Android lifecycle tests.

### I. Native boundary testing
Build and test native code with available compiler warnings and sanitizers (ASan/UBSan where supported). Fuzz JNI/native inputs and test ABI packaging, allocation failure, invalid handles, cancellation and native resource cleanup. If the CI host cannot execute a sanitizer or target ABI, report that gap and use a suitable runner/device later; do not silently skip it.

### J. Security and trust-boundary testing
Probe path traversal, archive extraction, signature and digest verification, replay/downgrade, untrusted capability inputs, permission escalation, exported Android components, intent spoofing, secret leakage and arbitrary downloaded code execution. A failure that crosses the owner-authorization or integrity boundary is a release blocker.

### K. Soak, performance and resource stress
Run repeated and long-duration scenarios for memory growth, thread leaks, queue growth, battery/resource policy, startup, latency distribution, storage growth and recovery. Report p50/p95/p99, maxima, failure counts and environment. Emulator performance is diagnostic only; real-device performance requires physical hardware.


### L. Self-healing and recovery-system attacks

Treat recovery as a first-class subsystem, not as a retry helper. Friday's intended immune-system cycle is detect → diagnose → contain → select recovery → execute → independently verify → restore/resume → learn. The recovery system itself must be attacked.

For each recoverable component, define:
- health signals and a steady-state contract;
- failure classification and confidence (observed fact vs hypothesis);
- containment boundary and protected state;
- allowed recovery actions, retry budget, backoff and circuit-breaker policy;
- independent health probe and post-recovery invariant checks;
- task/state resumption semantics and idempotency rules;
- user-facing state, progress and ETA confidence (never invent an ETA);
- escalation conditions when repair is unsafe, irreversible or outside granted authority.

Attack cases:
- kill a supervised secondary process and verify the primary identity process survives;
- kill the primary process and verify durable work/state survives a later Android-managed restart where the OS permits it;
- kill the recovery worker while recovery is in progress;
- cause repeated crashes until the restart budget is exhausted, then verify the circuit opens and no infinite restart storm occurs;
- deliver duplicate and reordered death callbacks and verify one recovery transaction is performed;
- make the health probe lie, time out or return stale evidence; Friday must not mark the component healthy;
- corrupt or remove the recovery recipe, checkpoint, journal or last-known-good metadata;
- fail midway through state restoration and verify rollback or an explicit degraded state;
- exhaust memory/storage or revoke permissions during recovery;
- make the proposed replacement implementation fail its own verification;
- restart while a task is partially executed and verify no duplicate irreversible effect occurs;
- verify recovery notifications distinguish detecting, diagnosing, recovering, verifying, recovered and needs-owner states.

The current Objective 1 scope proves bounded containment/restart and truthful reporting for existing recovery paths. It does **not** authorize unrestricted on-device source rewriting. Future code repair must be staged as a candidate, built/tested in isolation, security-reviewed, signed through the trusted update chain, health-checked after installation and rolled back on failure. Nano/host-adaptation algorithms may inform resource-aware scheduling and recovery budgets, but must not override owner authorization or the protected continuity floor.

## 4. Test oracles and severity

Every scenario needs:
1. a stated precondition and expected outcome;
2. an independent oracle, not merely the implementation's own success flag;
3. captured logs, traces, environment and random seed;
4. a reproducible minimal failure case;
5. an explicit recovery/data-integrity assertion where applicable.

Severity:
- **Critical:** unauthorized effect, false success, signature/identity bypass, arbitrary code execution, unrecoverable identity/data corruption.
- **High:** crash, deadlock, persistent state corruption, update/rollback failure, broken recovery or major capability path failure.
- **Medium:** bounded degraded behaviour, resource leak or retry/latency defect with a safe fallback.
- **Low:** non-critical usability, diagnostics or performance issue.

Critical and high findings block Objective 1 sign-off until fixed and retested. Medium findings require a documented disposition and evidence that the protected floor remains intact. Flaky tests are defects in the test system and cannot be counted as passes.

## 5. Execution sequence

1. Freeze and identify the exact tested commit; preserve the current passing baseline.
2. Build the source-to-test map and baseline line/branch coverage. Treat coverage as a gap-finding metric, not proof of correctness.
3. Run all existing tests and verify reports are complete and non-empty.
4. Add property, model-based and fault-injection cases around critical invariants.
5. Add mutation testing to prove critical tests detect deliberate defects.
6. Run fuzzing and concurrency stress with reproducible seeds and bounded resource/time budgets.
7. Run emulator application/lifecycle and update transaction scenarios.
8. Run native sanitizer/fuzz lanes where supported.
9. Run soak/performance tests and publish distributions and resource traces.
10. Triage every finding, fix confirmed defects, add regression tests, and repeat the relevant attack plus full required CI.
11. Publish a final adversarial report listing tested code/subsystems, exact counts, coverage, mutants killed/survived, fuzz executions/time, injected faults, findings, fixes, residual risks and untested environments.

## 6. Exit criteria

The stage is not complete merely because the suite is green. It is complete only when:
- every production source file and critical path has a test mapping or explicit justified exclusion;
- test reports and coverage are complete and tied to the exact commit;
- all critical invariants have positive tests and negative/fault tests;
- critical mutants are killed, with surviving mutants explained and resolved or explicitly justified;
- fuzzing and stateful tests produce no unresolved critical/high defect within the declared campaign budget;
- injected failures produce the specified safe, truthful behaviour;
- no unresolved critical/high finding remains;
- all fixes have regression tests and the complete required CI passes again;
- the final report states residual uncertainty and environments not tested.

This stage is an adversarial evidence campaign. It cannot guarantee the absence of all future bugs, but it is designed to expose weaknesses before they reach the owner's phone.

## 7. Current known gaps at baseline

- The current build workflow runs JVM unit tests, one disposable emulator lane and a whole-source audit; that is not full mutation/fuzz/concurrency/soak coverage.
- Two live-Ollama test classes are explicitly excluded from the default unit-test task and require a separately configured live backend; they must be classified as optional integration evidence, not silently included in the default pass.
- The current emulator lane is not a physical-device performance or compatibility guarantee.
- A first adversarial emulator run exposed `VoiceSubstrateTest` racing a second test-created `VoiceOrganismHost` against the production `FridayEngine` supervisor. The test has been changed to exercise the single production supervisor, wait for engine readiness, and allow a bounded cold restart. This is a suspected test-isolation defect until the new emulator run confirms the result.
- The Lean model is abstract and does not automatically prove Kotlin/C++ implementation refinement.
- The repository inventory counts source files, not executable line/branch coverage.

