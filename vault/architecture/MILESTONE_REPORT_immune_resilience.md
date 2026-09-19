# Milestone: Immune & Resilience — Build 01D

**Scope:** Resilience/immune subsystem only. No capability implementations, no tools, no voice/vision/cloud/APK/native runtime, no mutated environments, no autonomous repair.
**Status:** Main compiles clean; 38 new immune/resilience tests pass; full module suite green (623 tests, 01A/01B/01C intact).

---

## Existing mechanisms reused (extended, not replaced)

- **FailureSurface** (`com.jarvis.app.failure`) — authoritative failure sink; `FailureEvent`/`FailureAggregate`/`SubsystemHealth`/`RecoveryEvent` already answered "what/where/when/why/what state". Extended **additively** with structured context fields (all defaulted).
- **FailureModel** — existing subsystem `FailureCategory`, `FailureSeverity`, `Recoverability`, `RecoveryAction`, `RecoveryResult`, `FailureStatus` reused as-is.
- **RecoveryController** — left authoritative for async retry scheduling; the new `ImmuneSystem` coordinates decisions/observations around it rather than replacing it.
- **CircuitBreaker** — already CLOSED→OPEN→HALF_OPEN→CLOSED. Strengthened **additively**: `probeAttempts`, `trippedCount`, `cooldownRemainingMs`, explicit `probe()` using the injected clock.
- **SelfDiagnosis** — already answered the 9 base questions. Strengthened **additively** with `detailedDiagnosis`: frequency, recurrence, dependents, recovery attempts/success, resource-relatedness, likely-determinism.
- **ResourceGovernor / ResourceState / ThermalState / CapabilityState** (`cognitive`) — reused as the resource-input seam.
- **PlanGraph** (`planning`) — reused directly in partial-failure evaluation (`transitivelyDownstream`).
- **CognitiveEvent / CognitiveEngine** — the single event bus; 11 immune events added as `CognitiveEvent` subtypes. **No second event system.**

## New mechanisms

- **`failure`** — `FailureCause` taxonomy (16 modes: TIMEOUT, RESOURCE_EXHAUSTION, MEMORY_PRESSURE, CPU_PRESSURE, THERMAL_PRESSURE, INVALID_INPUT, INVALID_OUTPUT, UNSUPPORTED, DEPENDENCY_FAILURE, PROCESS_FAILURE, NATIVE_FAILURE, IO_FAILURE, STATE_CORRUPTION, PERMISSION_FAILURE, INTERNAL_ERROR, UNKNOWN); `FailureRecord` fields on `FailureEvent`/`FailureReport` (`failureCause`, `relatedStepId`, `relatedCapability`, `environment`, `rootCauseHypothesis`, `resourceState`); `ContainmentStatus`, `DegradationLevel`, deterministic `FailureSignature`, `RecoveryOutcomeRecord` (evidence), sealed `EnvironmentFailure` contract + `toFailureCause()` mapping. `BackoffPolicy` (exponential, bounded jitter, deadline, cause-based retry classification).
- **`cognitive/immune`** —
  - `DependencyGraph` (A requires B; transitive `affectedDependents`; cycle detection; deterministic sorted output).
  - `ContainmentRegistry` (per-subsystem isolation — one failure never poisons unrelated subsystems).
  - `DegradedModeController` (FULL/DEGRADED/LIMITED/OFFLINE/UNAVAILABLE/RECOVERING; enter/exit).
  - `ResourceFailureHandler` (ResourceState → response hierarchy NORMAL→REDUCE_CONCURRENCY→STOP_OPTIONAL_WORK→UNLOAD_EXPENSIVE→SWITCH_TO_LOWER_COST→PAUSE→REJECT, plus degradation level + compact snapshot).
  - `PartialFailureEvaluator` (PlanGraph + step outcomes → usable / acceptable / blockedSteps / alternativeExists / needsReplan; a failing step does not auto-fail the plan).
  - `ImmuneMemory` (signature-keyed failure memory, `resembles()` hit with confidence, evidence ledger for later research/evolution).
  - `CapabilityEnvironment` interface + `EnvironmentOutcome` (process death / environment unavailable / malformed output / resource exceed become observable failures; real environments NOT built).
  - `ImmuneSystem` — the coordinator: detect → classify → contain → degrade → recover → remember, with per-operation CircuitBreaker, bounded recovery budget, and all observations emitted as `CognitiveEvent` subtypes.

## Failure taxonomy

Subsystem (`FailureCategory`) and failure mode (`FailureCause`) are orthogonal: a subsystem fails in a mode, the mode drives retryability/containment/diagnosis. Categories from the directive: all 16 implemented; only necessary modes added, none invented beyond the list.

## Containment model

`ContainmentStatus` per subsystem: HEALTHY / ISOLATED / DEGRADED / UNAVAILABLE / RECOVERING. A failing subsystem is set to ISOLATED; only its dependency-graph neighbours move to DEGRADED; unrelated subsystems stay HEALTHY. Verified: VOICE failure never marks BRAIN failed; MEMORY failure degrades BRAIN but leaves STT and MODEL untouched.

## Circuit-breaker behavior

Per (subsystem|operation). CLOSED → OPEN on windowed threshold (each OPEN counts `trippedCount`); OPEN rejects traffic and records `cooldownRemainingMs`; after cooldown, one probe transitions HALF_OPEN; probe success → CLOSED (records `lastProbeSucceeded`), probe failure → OPEN again. Repeated failures stop hammering: after the circuit opens, further `onFailure` calls do not retry the recovery (verified: 1 recovery call across 3 failures).

## Retry policy

`BackoffPolicy`: maxAttempts (bounded), exponential backoff (base × factor^n, capped, bounded jitter), wall-clock deadline, and cause classification — never retry INVALID_INPUT, INVALID_OUTPUT, UNSUPPORTED, PERMISSION_FAILURE, STATE_CORRUPTION. `ImmuneSystem` recovery is bounded by the same budget and stops at the circuit; `Thread.sleep(0)` in tests keeps it deterministic.

## Degraded states

`DegradationLevel` FULL → DEGRADED → LIMITED → OFFLINE → UNAVAILABLE → RECOVERING. Resource pressure raises the level (e.g. high memory → LIMITED), recovery exhaustion raises to OFFLINE + `CapabilityUnavailable`, and a later success exits back to FULL (`DegradedModeExited`).

## Dependency propagation

`DependencyGraph` + `ContainmentRegistry`: failure isolates the source and degrades the transitive dependent set only. Independent branches (e.g. STT vs BRAIN) are provably untouched. Cycles are detectable; no blind propagation.

## Tests — 38 (pure JVM, fakes only)

- `PolicyTest` (10): exponential backoff doubling/cap, attempt budget, non-retryable causes, deadline, circuit open→close, half-open probe recovery, probe failure reopens, cooldown, determinism.
- `ContainmentTest` (10): dependency graph direct/transitive dependents, cycle detection, unrelated-subsystem isolation, containment transitions, degraded-mode enter/exit, resource hierarchy (NORMAL→…→REJECT), partial-failure usable/acceptable/blocked/replan.
- `ImmuneMemoryTest` (4): signature recognition, unseen signature, deterministic signature key, evidence ledger.
- `ImmuneSystemTest` (14): detect→classify→contain→recover→remember pipeline, non-retryable never recovered, bounded recovery (no infinite), circuit opens and stops hammering, probe recovery, subsystem isolation, dependency-aware propagation, degrade→unavailable→recover, resource snapshot, environment-failure mapping, recurrence diagnosis, determinism.

Simulated in tests: timeout, invalid input, OOM/resource exhaustion (via ResourceState), native/process failure (via EnvironmentFailure mapping), malformed output, dependency unavailable, repeated failure, recovery exhaustion.

## Limitations

- **ImmuneMemory is in-memory only** — no long-term promotion (explicitly deferred; the `RecoveryOutcomeRecord` ledger is the future seam).
- **Recovery is synchronous** in `ImmuneSystem`; async scheduling remains the host's job via the existing `RecoveryController`.
- **No real environments** — `CapabilityEnvironment` is a contract only.
- **No autonomous repair** — diagnosis only, per directive.
- **Resource inputs are synthetic** — `ResourceState`/`ResourceGovernor` are not yet wired to live telemetry.
- **Determinism** — signatures, ordering, and policy calculations are deterministic; wall-clock recovery pacing is intentionally excluded from the comparison surface.

## Next build

**BUILD 01E — CAPABILITY FABRIC** (not implemented). Sits on top of this resilience layer: maps the symbolic `action` verbs from `ActionRequest` to concrete `ActionPort` implementations (first pure-JVM actions), with each capability registered in the dependency graph so the immune layer can contain/degrade it by name. Do NOT implement 01E in this milestone.

## Final principle

Jarvis is not "component fails → Jarvis fails". It behaves as: component fails → nervous system observes → immune layer classifies → isolate affected capability → degrade/fallback/recover → preserve the rest of Jarvis → remember the failure.
