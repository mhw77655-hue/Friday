# Milestone: Goal + Planning — Build 01B

**Scope:** Goal + Planning subsystem only. No executor, capabilities, tools, voice, vision, cloud, APK, native runtime, or nervous-system wiring.
**Status:** Main compiles clean; 53 new planning tests pass; full module unit-test suite green (Build 01A intact).

---

## 1. What was reused

- **CognitiveState** — the existing `Goal`, `Subgoal`, `Plan`, `PlanStep`, `DecisionOption`, `DecisionRecord`, `ResourceState`, `CapabilityState`, `UncertaintyProfile` types. No duplicate representations were created.
- **CognitiveEngine** — reused as the integration host; its existing `CognitiveEvent` sealed interface and state-transition methods were **extended additively** (new event types + new methods), never rewritten.
- **WorkingMemory / AttentionEngine / CognitiveContextBuilder** — consumed as-is; the planner/decider do not dump memory and rely on the existing context path for relevance.
- **MemoryStorePort** (Build 01A) — untouched; the engine continues to consume memory through the port.
- **Build 01A lifecycle enums** — extended **additively** so existing 01A callers still compile: `GoalStatus` gained `CREATED/BLOCKED/PAUSED/CANCELLED` (kept `SUSPENDED/ABANDONED`), `SubgoalStatus` gained `PAUSED/CANCELLED`, `PlanStatus` gained `BLOCKED`, `PlanStepStatus` gained `BLOCKED/PAUSED/CANCELLED`. `GoalPriority` untouched (the only exhaustive `when` in the package).

## 2. What was added

- **`com.jarvis.app.cognitive.planning.PlanGraph`** — the structured plan as a **DAG**, not a flat list: `PlanNode` (step + prerequisites + subgoal + status + blocked + failureCause + completionCondition + outcome), `PlanGraph` (goal refs, nodes, status, progress, blocked/failed sets, `ReplanState`), plus deterministic graph algorithms: `findCycles`, `missingPrerequisites`, `topologicalOrder` (Kahn), `computeBlocked` (transitive), `nextActionable`, `validateGraph`, `transitivelyDownstream`. `toPlan()` projects the graph onto the existing `Plan` value for state/context.
- **`GoalPlanner`** — goal validation (blank description invalid; missing success criteria is a warning, not invented), hierarchical decomposition via a `DecompositionStrategy` (goal → subgoal specs → step specs), subgoal dependency wiring (first-node-of-B depends on last-node-of-A), sentinel **BLOCKED** nodes for undecomposable subgoals (dependents become blocked, nothing invented), lifecycle transitions (`transitionGoal`/`transitionSubgoal`, `deriveSubgoalStatus`/`deriveGoalStatus`), node ops (`completeNode`/`failNode`/`blockNode`/`skipNode`), and **replanning** that never restarts the goal: records the cause, invalidates transitively-dependent nodes, proposes a replacement path, rewires downstream to the replacement tail, and continues from kept completed state.
- **`DecisionEngine`** — option generation via `OptionGenerator`, feasibility checks (resource budget, capability availability, tag constraints), weighted evaluation across goal alignment / outcome / risk / uncertainty / reversibility / effort, deterministic ranking + selection, and a `DecisionRecord` with **structured reason metadata** (`DecisionReason`: per-factor scores, applied constraints, uncertainty/resource/capability snapshot, rejected options — no prose required), `DecisionStateSnapshot` of the governing cognitive state, and `expectedOutcome`. Rejects all-options-infeasible without recording a decision.
- **Decision structures** — `DecisionOption` extended (defaults) with `confidence`, `resourceRequirements`, `capabilityRequirements`, `constraints`, `reversibility`; `DecisionRecord` extended (defaults) with `reasonMetadata`, `stateSnapshot`, `expectedOutcome`; new `DecisionReason`, `DecisionFactor`, `DecisionEvaluationType`, `DecisionStateSnapshot`.
- **`CognitiveEngine` integration** — `planGoal`, `updateGoalStatus`, `completePlanStep`, `failPlanStep`, `replanPlan`, `evaluateDecision`, `recordDecisionOutcome`, `getCurrentPlanGraph`, with state sync (plan, subgoal, goal status derivation). New events: `GoalCreated`, `SubgoalCreated`, `PlanCreated`, `PlanBlocked`, `PlanFailed`, `PlanCompleted`, `ReplanRequested`, `DecisionProposed`. Not wired into JarvisEngine/MainActivity.

## 3. Tests added

53 tests in `com.jarvis.app.cognitive.planning`:
- **GoalPlannerTest (21)** — goal creation/validation, decomposition, subgoal creation, dependency ordering, blocked/completed/failed/cancelled goals, next-actionable subgoal, undecomposable→blocked sentinel, replanning, failed-step recovery, cyclic-dependency rejection, empty goal, impossible goal, missing prerequisite, determinism (same input → same plan, same replan).
- **DecisionEngineTest (13)** — option generation, ranking, goal alignment, uncertainty handling, resource constraints, capability constraints, capability derivation, contradictory constraints, decision selection/recording, reason metadata, no-feasible case, determinism.
- **PlanGraphTest (12)** — topological order, deterministic order, cycle detection (incl. self-cycle), missing prerequisites, empty-plan invalid, transitive blocking, missing-prereq blocking, next-actionable, progress/completion, `toPlan` projection.
- **CognitivePlanningIntegrationTest (7)** — `planGoal` events + state, plan→goal completion, plan failure, replan recovery, `updateGoalStatus`, `evaluateDecision` (DecisionProposed/DecisionMade), `recordDecisionOutcome`.

## 4. Test result

- `:mobile:app:compileDebugKotlin` — clean.
- `com.jarvis.app.cognitive.planning.*` — **53/53 pass**.
- Full `:mobile:app:testDebugUnitTest` — **BUILD SUCCESSFUL** (Build 01A's 75 cognitive tests + 01B's 53 + all other module tests).
- No APK build, no native runtime, no unrelated subsystem.

## 5. Remaining limitations

- **No execution** — nothing consumes the plan yet; `PlanStep.action` is descriptive only.
- **Strategy knowledge** — decomposition/replacement knowledge lives behind `DecompositionStrategy`; a real planner needs an actual strategy source (model-guided or procedural). The default is deliberately empty so plans degrade to BLOCKED rather than inventing steps.
- **No persistence** — plans/goals/decisions live in memory only; no goal history or plan recovery across restarts.
- **No model population** — `SelfModel`/`UserModel`/`WorldModel` remain defaults; nothing feeds real user/device data in yet.
- **Replanning is single-step** — `replan` proposes one replacement path per failed node; multi-node simultaneous failure and iterative replan refinement are not yet modeled.
- **Decision outcomes** are supplied manually via `recordDecisionOutcome`; nothing yet closes the loop from actual execution results.

## 6. What Build 01C should be

**Build 01C: the Execution subsystem.** A step-driver that consumes the `PlanGraph` produced here, dispatches each `nextActionable` step through an `ActionPort` interface (capability-agnostic, so no capability implementations yet), feeds outcomes back via `completePlanStep`/`failPlanStep`, and triggers `replanPlan` on failure — closing the loop that records real decision outcomes. It is the single direct consumer of Build 01B, stays one subsystem, and needs no APK/voice/native runtime, consistent with the build sequence. (Memory consolidation — promoting salient working memory into the long-term `MemoryStore` — is the alternative next subsystem if execution is deferred.)
