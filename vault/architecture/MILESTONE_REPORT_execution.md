# Milestone: Execution — Build 01C

**Scope:** First execution layer only. No capability implementations, no tools, no voice/vision/cloud/APK/native runtime, no app-runtime wiring, no redesign of 01A/01B.
**Status:** Main compiles clean; 18 new execution tests pass; full module unit-test suite green (01A + 01B intact).

---

## Reused components

- **PlanGraph** (`cognitive/planning`) — consumed directly: `nextActionable`, `nodes`, `status`, `blockedNodeIds`, `isComplete`. No duplicate plan type.
- **GoalPlanner** (`cognitive/planning`) — reused for plan mutation: `completeNode`, `failNode`, `replan` (which already implements "failed step → identify cause → invalidate downstream → replacement path → continue").
- **CognitiveEngine** — reused as the integration host; it now **implements `PlanDriver`** (additive `override` on the existing `completePlanStep`/`failPlanStep`/`replanPlan`/`recordDecisionOutcome` methods + `getPlanGraph`; `getCurrentPlanGraph()` kept as an alias). No rewriting.
- **CognitiveEvent** — the single event bus was **extended additively**; no competing channel was created. All execution events are `CognitiveEvent` subtypes flowing through the same `observeEvents()` stream.
- **DecisionOutcome** / `recordDecisionOutcome` — reused to close the decision → action → actual-result feedback loop.
- **WorkingMemory / AttentionEngine / CognitiveContextBuilder** — untouched, still the context path.

## New components

- **`cognitive/execution/ActionPort.kt`** — the capability-agnostic boundary:
  - `ActionRequest(planId, stepId, action, description, context, constraints)` — structured request.
  - `ActionFailure` (sealed): `Error(code, message, recoverable, evidence)`, `Timeout(durationMs)`, `Unsupported(action)`.
  - `ExecutionResult(success, failure, output, evidence, durationMs, retryable)` with `ok()`/`fail()` factories.
  - `ActionPort` (`suspend fun execute(request): ExecutionResult`). The executor knows nothing about how capabilities work.
- **`cognitive/execution/ExecutionState.kt`** — structured state, never text logs:
  - `ExecutionStatus` (IDLE / EXECUTING / RETRYING / PAUSED / CANCELLED / BLOCKED / COMPLETED / FAILED).
  - `ExecutionState(planId, currentStepId, currentAction, status, attemptCount, lastResult, failureCause, retryable, replanTriggered, startedAt, lastExecutedAt, completedSteps, failedSteps, executedSteps)`.
  - `RetryPolicy(maxAttempts, backoffMs)` (bounded), `ExecutionConfig(maxStepsPerRun, maxReplans)` (explicit boundaries — no infinite loop), `StepOutcome`, `ExecutionSummary`.
- **`cognitive/execution/ExecutionEngine.kt`** — the coordinator:
  - `PlanDriver` interface — the plan-mutation boundary the engine drives.
  - `load()` (idempotent), `executeNextStep()`, `runToTerminal()` (bounded by `maxStepsPerRun`), `pause()`, `resume()`, `cancel()`, `getState()`.
  - Dispatching: current graph → `nextActionable` → `ActionRequest` → `ActionPort` → `ExecutionResult` → complete/fail/replan through the driver.
  - Observability: `executionState` answers *what step / why selected / how many attempts / what happened / why failed / was it retried / was a replan triggered / what is the new next actionable step* (`nextStepId` on each `StepOutcome`).

## Execution flow

```
current PlanGraph
  → nextActionable() selects the first pending, dependency-satisfied, unblocked node
  → ActionRequest(planId, stepId, action, context, constraints)
  → ActionPort.execute()            (boundary; capability-agnostic)
  → ExecutionResult
      success  → driver.completePlanStep(stepId, outcome)  + recordDecisionOutcome(SUCCESS)  + StepCompleted
      failure  → bounded retry per RetryPolicy (StepRetrying)
               → after retries exhausted / non-retryable → driver.failPlanStep + StepFailed + recordDecisionOutcome(FAILED)
               → driver.replanPlan(failedNodeId, cause) + ReplanTriggered   (never restarts the goal)
  → repeat until COMPLETED / BLOCKED / FAILED / CANCELLED / PAUSED
```
Terminals honour the plan's own derived state: a graph whose plan status is FAILED terminates FAILED even when its remaining nodes are blocked; a blocked plan terminates BLOCKED with a `StepBlocked` event.

## Failure / replan behavior

- **Retryable failure** (result `retryable`/`Error.recoverable`): retried up to `RetryPolicy.maxAttempts` with `StepRetrying` events. A permanently retryable failure is NOT retried forever — bounded attempts, then step failure.
- **Non-retryable / exhausted failure**: step failed via `driver.failPlanStep` (real `ActionFailure` cause recorded, `DecisionOutcome.FAILED` fed back), then `driver.replanPlan` generates a replacement path via the existing GoalPlanner replanning — downstream nodes invalidated and rewired to the replacement tail, completed work kept.
- **Port throws**: treated as a non-recoverable `PORT_EXCEPTION` failure.
- **Repeated failure**: bounded by `ExecutionConfig.maxReplans`; after the budget is exhausted the plan terminates FAILED with a single `PlanExecutionFailed` event.
- **No restart**: replanning never restarts the goal; it continues from the current completed state.

## Events (all on the existing bus)

`ExecutionStarted`, `StepStarted`, `StepCompleted`, `StepFailed`, `StepRetrying`, `StepBlocked`, `ReplanTriggered`, `PlanExecutionCompleted`, `PlanExecutionFailed`, `PlanExecutionCancelled`.

## Tests — 18 (fake in-memory ActionPort, no real tool)

`ExecutionEngineTest` (16):
- next-actionable selection & dispatch; ActionRequest contents (planId/stepId/action)
- successful multi-step completion in dependency order; state transitions to COMPLETED
- retryable failure retried-then-succeeds (exact attempt counts)
- no infinite retry on a permanently retryable failure (3 attempts max, StepRetrying twice)
- non-retryable failure with no replacement → FAILED
- replan triggered on non-retryable failure, execution continues through the replacement node
- invalid action → StepFailed("unsupported action") + replan
- port throwing → PORT_EXCEPTION non-recoverable failure
- repeated failure bounded by maxReplans
- blocked plan → BLOCKED + StepBlocked
- cancelled → CANCELLED + PlanExecutionCancelled; paused → PAUSED
- empty plan → FAILED on load
- same plan + port → deterministic summary

`ExecutionIntegrationTest` (2):
- execution events flow through the existing `CognitiveEvent` bus via `engine.startExecution(port)`
- actual step results close the decision-outcome loop (`lastDecision.outcome` null → SUCCESS after a real run)

## Limitations

- **No real capabilities** — `ActionPort` has no implementations; `action` verbs are symbolic until the Capability Fabric exists.
- **Single ActionPort per execution** — no routing/dispatch to multiple capabilities yet.
- **Outcome granularity** — every successful step records `DecisionOutcome.SUCCESS`; partial outcomes and per-decision→step correlation are not modeled.
- **No persistence** — execution state is in-memory; no resume across process restarts.
- **Retry is synchronous backoff** — `backoffMs` is a simple delay; no jitter/expiry/circuit-breaking.
- **Determinism** — plan/port inputs are deterministic; wall-clock timestamps (`startedAt`, `lastExecutedAt`) are intentionally not part of the comparison surface.

## What Build 01D should be

**Build 01D: the Capability Fabric** — a registry/routing layer that maps the symbolic `action` verbs from `ActionRequest` to concrete `ActionPort` implementations, with the first pure-JVM actions (compute, lookup, file) so the execution loop can actually do something. It is the single direct consumer of 01C's `ActionPort` boundary, keeps the cognition↔world split clean, and needs no APK/voice/native runtime. (Memory consolidation — promoting salient working memory into the long-term `MemoryStore` — is the alternative next subsystem if real actions are deferred.)

## Final principle

01A: "What is happening?" · 01B: "What should I accomplish, and how?" · 01C: "Execute the next step and learn what actually happened." Nothing beyond that boundary was built.
