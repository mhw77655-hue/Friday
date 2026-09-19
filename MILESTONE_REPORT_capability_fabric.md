# Milestone: Capability Fabric — Build 01E

**Scope:** Capability Fabric only. No full brain, no research/builder/mutated-environment, no voice/vision, no cloud, no APK/UI, no native runtime, no real external capabilities.
**Status:** JVM unit suite green — **668 tests / 77 classes, 0 failures, 0 errors** (fabric package: **45 tests**, 0 failures). No APK build performed; only `testDebugUnitTest` (pure JVM) was run.

**Note on provenance:** the `com.jarvis.app.cognitive.*` tree (01C execution, 01D immune, and most of the fabric) existed in the working tree as **uncommitted work** from a prior session. This milestone inspected it, verified it against the directive, closed the gaps below, and proved it with the full JVM suite. The fabric was not written from a blank slate; the changes here are the completion delta.

---

## 1. Existing mechanisms reused

No duplicate memory / failure / event / dependency / permission / result systems were created. The fabric reuses, verbatim:

- **`ActionRequest` + `ExecutionResult` / `ActionFailure`** (`cognitive.execution`) — the single request/result vocabulary. The fabric adds no `ActionResult` type of its own.
- **`ActionPort`** (`cognitive.execution`) — the execution-facing port. `PortBackedCapability` adapts any existing `ActionPort` into a first-class `Capability`, so the 01C execution layer and the fabric stay interchangeable (the directive's `ActionRequest → ActionPort → Capability → implementation` path).
- **`CognitiveEvent`** (in `CognitiveEngine`) — the one event bus. New capability events (`CapabilityRegistered`, `CapabilityResolved`, `CapabilityInvocationStarted`, `CapabilityInvocationCompleted`, `CapabilityInvocationFailed`, `CapabilityUnavailable`, `CapabilityStateChanged`) are all `CognitiveEvent` subtypes; no second bus.
- **`ImmuneSystem`** (01D coordinator) — authoritative for failure containment, degradation, recovery, and the per-dependency `CircuitBreaker`. The fabric *calls into* it and never replaces it.
- **`FailureSurface` / `FailureReport` / `FailureCategory` / `FailureSeverity` / `Recoverability`** (01D) — every capability failure reaches the existing surface through `immune.onFailure`.
- **`DependencyGraph`** (01D, in `ImmuneSystem`) — capability identities + declared dependencies register as graph edges at registration; failures degrade *only* affected dependents. No new dependency mechanism.
- **`ContainmentRegistry` / `DegradedModeController`** (01D) — isolation and per-capability degradation state; the resolver consults the current degradation level (`degradedLevelOf = immune.degradation.levelOf`).
- **`PartialFailureEvaluator` / `PartialFailureVerdict`** (01D) — used as-is for partial-failure evaluation of a plan graph.
- **`ResourceFailureHandler` / `ResourceState` / `ThermalState`** (01D) — the conceptual resource link: `ResourceState → ResourceFailureHandler → DegradationLevel → resolver`. No live telemetry.
- **`PlanGraph` / `PlanNode` / `GoalPlanner`** (01C) — the plan shape that partial-failure and (future) planning operate on.

The legacy top-level **`com.jarvis.app.capability`** package (`CapabilityArbitrator` / `CapabilityRegistry`, Build-01C-era body/model selection) is a different layer — communication-body arbitration for STT/TTS/LLM — and is left untouched. It is *not* the execution fabric and is not a duplicate of it.

## 2. New mechanisms added

All in `com.jarvis.app.cognitive.capability`:

- **`CapabilityDescriptor`** (`Descriptor.kt`) — pure, declarative metadata with every directive-minimum field: `id`, `name`, `description`, `version`, `category`, `requiredPermissions`, `dependencies`, `resourceProfile`, `riskLevel`, `supportedOperations`, `enabled`, `availability`. No implementation logic.
- **`Capability` contract** (`Capability.kt`) — the minimal single seam: `descriptor` + `suspend fun invoke(request): ExecutionResult`. Deliberately minimal; `PortBackedCapability` bridges `ActionPort` implementations.
- **`CapabilityRegistry`** — authoritative registry: `register` / `unregister` / `replace` / `get` / `contains` / `list` / `size`, deterministic indexes by **operation**, **category**, and **dependency**; all list queries sort by id; duplicate ids rejected deterministically (`RegisterResult.Duplicate`). Instance-owned — no global mutable registration surface.
- **`CapabilityResolver`** — pure decision (`resolve`): existence, enabled, availability, dependency availability, permission presence, and ambiguity. Returns a structured `Resolution` (`Success` / `Failure(reason, detail)`). Never executes.
- **`CapabilityInvoker`** — the execution boundary: structural request validation, circuit gate (`refused` when the breaker is open), permission + availability + deadline enforcement, lifecycle transitions, `CapabilityInvocation` observability envelope, failure conversion into the `FailureSurface`/`ImmuneSystem` pathway, no automatic retry.
- **`CapabilityLifecycle`** — all nine states (REGISTERED / AVAILABLE / RUNNING / PAUSED / DEGRADED / ISOLATED / UNAVAILABLE / DISABLED / FAILED) with a fixed transition table; invalid transitions rejected (`TransitionResult.Rejected`).
- **`CapabilityPermission`** — the minimal permission seam (a small enum + the `granted` set checked at the invocation boundary). No permission framework.
- **`CapabilityResourceProfile`** — `cpuCost`, `memoryCostMb`, `thermalCost`, `latencyClass`, plus the optional/critical classification (`optional` field, derived `critical`), and a derived `expensive` gate for resource pressure.
- **`CapabilityFabric`** — composition root owning the single registry/resolver/invoker and wiring each registration's identity into the shared immune `DependencyGraph`.

### Gap-closure delta (this milestone)

1. **Cooperative cancellation, made correct.** The invoker previously caught `CancellationException` as a generic `Exception` and converted a *cancelled* invocation into a fabricated capability failure pushed to the failure surface. Now external cancellation rolls the lifecycle back to AVAILABLE and rethrows — cancellation is not a failure, nothing reaches the immune system. ("Cancelled" is now a clean, real verb.)
2. **Deterministic request validation.** New `ResolutionReason.INVALID_REQUEST`; blank `action` / `planId` / `stepId` are rejected before any capability is consulted, without executing and without immune-system pollution.
3. **Critical classification.** `CapabilityResourceProfile.critical` derived from `!optional`, closing the directive's optional/critical axis.
4. **Replace reconciles immune edges.** `CapabilityFabric.replace` now removes the old dependency edges and adds the replacement's, so a swap that changes dependencies leaves no stale containment edges. Additive 01D extension: `DependencyGraph.removeRequirement(subsystem, dependsOn)` / `removeRequirements(...)`.
5. **Flaky planning test fixed (pre-existing, outside the fabric).** `CognitivePlanningIntegrationTest.planGoal` cancelled its event collector as soon as `GoalCreated` appeared, racing the later `SubgoalCreated`/`PlanCreated` emissions and failing intermittently under full-suite load. It now waits for all three events before cancelling.

## 3. Integration points

| Point | Mechanism |
|---|---|
| Execution layer | `PortBackedCapability` makes any `ActionPort` a capability; the fabric is ActionPort-shaped end to end |
| Immune dependency graph | `fabric.register` / `fabric.replace` add/reconcile `DependencyGraph` edges by capability id |
| Failure surface | `immune.onFailure(FailureReport(..., failureIdentity = capabilityId))` on every failed invocation |
| Containment + degradation | failures → `ISOLATED`; `DEGRADED`/`UNAVAILABLE` levels consulted by the resolver at resolve time |
| Circuit breaker | per `(capabilityId, operation)` via `immune.breaker(...)`; open → invocation `refused` without executing; HALF_OPEN probe on cooldown |
| Recovery | success closes the breaker and restores containment via existing 01D `onSuccess` — recovery cannot bypass immune policy (proven by test) |
| Events | all lifecycle/observation flows through `CognitiveEvent` on the shared bus |
| Partial failure | `fabric.partialFailureVerdict(graph, failures)` → existing `PartialFailureEvaluator` (failed / blocked / successful steps, usable / acceptable, alternative / needsReplan) |
| Resource | `ResourceState` → `ResourceFailureHandler` → `DegradationLevel` → resolver gate; profile `expensive`/`critical` classification |

## 4. Tests added / total tests

Fabric test groups (`cognitive.capability`), all pure JVM:

- **RegistryTest** (7) — registration, duplicate rejection, replacement (keeps lifecycle), replacement-of-unknown rejected, removal, lookups, deterministic listing.
- **ResolutionTest** (9) — matching operation, unknown operation, disabled, offline, missing dependency, missing permission, ambiguous, degraded-to-unavailable, deterministic candidates.
- **LifecycleTest** (5) — happy path, invalid transitions rejected, failure → FAILED, isolation, recovery to AVAILABLE.
- **InvocationTest** (10) — success (+events, back to AVAILABLE), unresolvable action, **malformed request rejected without executing** *(new)*, **external cancellation propagates and is not a failure** *(new)*, unavailable (paused) rejection, permission rejection, timeout, failure propagation, event emission, deterministic result.
- **ImmuneIntegrationTest** (14) — failure reaches FailureSurface; BROWSER_SEARCH → ISOLATED; NETWORK degrades dependents only; unrelated capability stays healthy; circuit prevents hammering (refused, no execution); breaker re-arms on cooldown; success restores containment; resource-pressure rejection; optional capability paused/degraded; **recovery does not bypass immune policy** *(new)*; **replace reconciles dependency edges** *(new)*; **critical classification** *(new)*; **alternative/replan state in partial-failure verdict** *(new)*; duplicate registration adds no edges.

**Added this milestone:** 6 new fabric tests (45 total in the package) + 1 deterministic fix to a pre-existing flaky planning test.

**Full suite:** 77 test classes / **668 tests, 0 failures, 0 errors.**

## 5. Limitations

- **Cancellation is cooperative and coroutine-scoped** — a supervisor cancels the coroutine running the invocation; there is no out-of-band handle to cancel a specific invocation by id. Rolled back to AVAILABLE and propagated, not misreported as failure.
- **Resource profile is descriptive, not telemetric** — the link to `ResourceGovernor` is conceptual (via `ResourceFailureHandler` → degradation). Live admission (`ResourceGovernor.requestAdmission`) is not yet invoked by the fabric.
- **Resolution returns AMBIGUOUS for multiple candidates** — no ranking/selection policy (e.g. by resource, risk, latency) yet; a future planner may add one.
- **`unregister` leaves dependency edges inert** in the immune graph (deliberate — removing them could disturb other dependents' transitive closures; nothing fails on a removed id).
- **`immune.markHealthy` (01D)** still exits the first degraded capability rather than the one that just recovered — pre-existing 01D behavior, deliberately not altered.
- **Legacy `com.jarvis.app.capability` package coexists** as a separate body-arbitration concern; the fabric does not unify with it.

## 6. Intentionally deferred

- Real external capabilities (browser, web search, shell, filesystem tools, coding agent, research, builder, mutated environment) — later milestones, per directive.
- Live resource telemetry and fabric admission gating via `ResourceGovernor`.
- Capability selection/ranking policy for ambiguous resolution.
- The 01D `CapabilityEnvironment` isolated-host runtime (a future seam, not built here).
- Persistence of capability metadata / registration (e.g. reading `assets/capability_manifest.json`).
- Wiring the fabric into `JarvisEngine` / UI (deliberately: no APK/UI, no full brain).

## 7. Recommended Build 01F

**ONE subsystem: Capability-Resolved Planning & Execution.** A capability-aware executor that consumes the fabric end-to-end — the first real consumer of 01E:

- takes a `PlanGraph`, walks `nextActionable`, and for each step builds an `ActionRequest` and calls `fabric.invoker.invoke(...)` (through a capability-aware `ActionPort`-backed adapter),
- folds `PartialFailureEvaluator` verdicts into step outcomes: a failed step neither fails unrelated steps nor hides usable partial results; `needsReplan`/`alternativeCapabilityExists` drive `GoalPlanner.replan`,
- surfaces each step's `CapabilityInvocation` observability record and routes all of it through the existing bus and failure surface.

This binds the fabric's resolution/execution boundary to the 01C planning layer without building the full brain, and it exercises every 01E verb in one subsystem. It must not implement new real capabilities — it only decides and executes against already-registered ones.

*Build 01F is not implemented in this milestone.*
