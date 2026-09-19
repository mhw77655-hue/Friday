package com.jarvis.app.cognitive.execution

import com.jarvis.app.cognitive.CognitiveEngine.CognitiveEvent
import com.jarvis.app.cognitive.DecisionOutcome
import com.jarvis.app.cognitive.PlanStatus
import com.jarvis.app.cognitive.planning.GoalPlanner
import com.jarvis.app.cognitive.planning.PlanGraph
import com.jarvis.app.cognitive.planning.PlanNode
import com.jarvis.app.cognitive.planning.nextActionable
import kotlinx.coroutines.delay

/**
 * The plan mutation boundary between execution and cognition. [CognitiveEngine]
 * implements this; tests use a fake. The execution engine never touches plan
 * internals directly — it only asks the driver to advance the plan.
 */
interface PlanDriver {
    fun getPlanGraph(): PlanGraph?
    fun completePlanStep(nodeId: String, outcome: String? = null): PlanGraph?
    fun failPlanStep(nodeId: String, cause: String): PlanGraph?
    fun replanPlan(failedNodeId: String, cause: String): PlanGraph?
    /** Feed the ACTUAL result of an executed step back to the last decision. */
    fun recordDecisionOutcome(outcome: DecisionOutcome)
    /** Emit a cognitive/execution event onto the shared bus. */
    fun emit(event: CognitiveEvent)
}

/**
 * ExecutionEngine — the first execution layer.
 *
 * Flow: current PlanGraph -> next actionable step -> ActionRequest ->
 * ActionPort -> ExecutionResult -> complete/fail step -> replan on
 * non-recoverable failure -> update plan/goal state -> repeat until a terminal
 * boundary (completed / blocked / failed / cancelled / paused).
 *
 * The engine knows nothing about how capabilities are implemented. It drives
 * [PlanDriver] and dispatches through [ActionPort] only.
 *
 * Bounds: retries are capped by [RetryPolicy], replans and total steps by
 * [ExecutionConfig] — there is no infinite loop.
 */
class ExecutionEngine(
    private val actionPort: ActionPort,
    private val driver: PlanDriver,
    private val goalPlanner: GoalPlanner = GoalPlanner(),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val config: ExecutionConfig = ExecutionConfig()
) {

    private var state = ExecutionState()
    private val executed = mutableListOf<StepOutcome>()
    private var replanCount = 0

    val executionState: ExecutionState get() = state
    fun getState(): ExecutionState = state

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Load the driver's current plan and start. Idempotent — a paused,
     *  cancelled, or already-terminal engine is left untouched. */
    fun load(): ExecutionState {
        if (state.status != ExecutionStatus.IDLE) return state
        val graph = driver.getPlanGraph()
        if (graph == null || graph.nodes.isEmpty()) {
            state = state.copy(status = ExecutionStatus.FAILED, failureCause = "plan has no steps")
            graph?.let { driver.emit(CognitiveEvent.PlanExecutionFailed(it.goalId, "plan has no steps", 0)) }
            return state
        }
        state = state.copy(
            planId = graph.goalId,
            status = ExecutionStatus.EXECUTING,
            startedAt = System.currentTimeMillis()
        )
        driver.emit(CognitiveEvent.ExecutionStarted(graph.goalId))
        return state
    }

    fun pause(): ExecutionState {
        if (state.status == ExecutionStatus.EXECUTING || state.status == ExecutionStatus.RETRYING) {
            state = state.copy(status = ExecutionStatus.PAUSED)
        }
        return state
    }

    fun resume(): ExecutionState {
        if (state.status == ExecutionStatus.PAUSED) {
            state = state.copy(status = ExecutionStatus.EXECUTING)
        }
        return state
    }

    fun cancel(): ExecutionState {
        if (state.status == ExecutionStatus.EXECUTING || state.status == ExecutionStatus.RETRYING ||
            state.status == ExecutionStatus.PAUSED
        ) {
            state = state.copy(status = ExecutionStatus.CANCELLED)
            state.planId?.let { driver.emit(CognitiveEvent.PlanExecutionCancelled(it)) }
        }
        return state
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    /** Execute one full step (including bounded retries and a possible replan). */
    suspend fun executeNextStep(): StepOutcome? {
        val graph = driver.getPlanGraph() ?: return terminalFrom(state)
        if (graph.nodes.isEmpty()) return terminalFrom(state)
        if (graph.isComplete) {
            state = state.copy(status = ExecutionStatus.COMPLETED)
            driver.emit(CognitiveEvent.PlanExecutionCompleted(graph.goalId, state.completedSteps))
            return null
        }

        val node = nextActionable(graph.nodes)
        if (node == null) {
            // Every remaining node done (or the sole failure recovered by the
            // active replacement path) -> the plan finished its work.
            val recoveredOriginal = graph.replanning?.originalNodeId
            if (graph.nodes.all { it.isCompleted || it.isSkipped || it.id == recoveredOriginal }) {
                state = state.copy(status = ExecutionStatus.COMPLETED)
                driver.emit(CognitiveEvent.PlanExecutionCompleted(graph.goalId, state.completedSteps))
                return null
            }
            // no actionable step remains — honour the plan's derived state
            val blockedIds = graph.blockedNodeIds
            val (terminal, cause) = when {
                graph.status == PlanStatus.FAILED -> ExecutionStatus.FAILED to (graph.failureCause ?: "plan failed")
                graph.status == PlanStatus.BLOCKED || blockedIds.isNotEmpty() ->
                    ExecutionStatus.BLOCKED to "blocked: ${blockedIds.take(3)}"
                else -> ExecutionStatus.FAILED to "no actionable step"
            }
            // Emit the terminal event only once (state may already be terminal).
            if (state.status != terminal) {
                when (terminal) {
                    ExecutionStatus.BLOCKED -> driver.emit(CognitiveEvent.StepBlocked(graph.goalId, null, blockedIds))
                    ExecutionStatus.FAILED -> driver.emit(CognitiveEvent.PlanExecutionFailed(graph.goalId, cause, state.completedSteps))
                    else -> Unit
                }
            }
            state = state.copy(status = terminal, failureCause = cause)
            return null
        }

        return executeNode(graph, node)
    }

    private suspend fun executeNode(graph: PlanGraph, node: PlanNode): StepOutcome {
        var attempt = 0
        while (true) {
            attempt++
            state = state.copy(
                currentStepId = node.id,
                currentAction = node.action,
                status = ExecutionStatus.EXECUTING,
                attemptCount = attempt,
                lastExecutedAt = System.currentTimeMillis()
            )
            driver.emit(CognitiveEvent.StepStarted(graph.goalId, node.id, node.action))

            val request = ActionRequest(
                planId = graph.goalId,
                stepId = node.id,
                action = node.action,
                description = node.description,
                context = buildContext(node),
                constraints = emptyList()
            )
            val result = try {
                actionPort.execute(request)
            } catch (e: Exception) {
                ExecutionResult.fail(
                    ActionFailure.Error("PORT_EXCEPTION", e.message ?: "action port threw", recoverable = false)
                )
            }

            if (result.success) {
                val outcomeText = result.output["outcome"] ?: node.expectedOutcome
                val updated = driver.completePlanStep(node.id, outcomeText) ?: graph
                executed.add(StepOutcome(node.id, result, attempt, nextStepId = nextActionable(updated.nodes)?.id))
                state = state.copy(
                    lastResult = result,
                    completedSteps = state.completedSteps + 1,
                    executedSteps = state.executedSteps + 1,
                    retryable = false,
                    failureCause = null
                )
                driver.recordDecisionOutcome(DecisionOutcome.SUCCESS)
                driver.emit(CognitiveEvent.StepCompleted(graph.goalId, node.id, result.output, result.durationMs))
                return executed.last()
            }

            // Failure path — retryability comes from the structured result only.
            val cause = failureText(result)
            val recoverable = result.retryable

            if (recoverable && retryPolicy.canRetry(attempt)) {
                state = state.copy(
                    status = ExecutionStatus.RETRYING,
                    retryable = true,
                    failureCause = cause,
                    attemptCount = attempt
                )
                driver.emit(CognitiveEvent.StepFailed(graph.goalId, node.id, cause, retryable = true))
                driver.emit(CognitiveEvent.StepRetrying(graph.goalId, node.id, attempt, attempt + 1))
                if (retryPolicy.backoffMs > 0) delay(retryPolicy.backoffMs)
                continue
            }

            // Retries exhausted or non-recoverable -> fail the step, then replan.
            driver.failPlanStep(node.id, cause)
            executed.add(StepOutcome(node.id, result, attempt, replanned = false))
            state = state.copy(
                lastResult = result,
                failedSteps = state.failedSteps + 1,
                executedSteps = state.executedSteps + 1,
                retryable = false,
                failureCause = cause
            )
            driver.recordDecisionOutcome(DecisionOutcome.FAILED)
            driver.emit(CognitiveEvent.StepFailed(graph.goalId, node.id, cause, retryable = false))

            if (replanCount < config.maxReplans) {
                replanCount++
                state = state.copy(replanTriggered = true)
                driver.emit(CognitiveEvent.ReplanTriggered(graph.goalId, node.id, cause))
                val replanned = driver.replanPlan(node.id, cause) ?: graph
                executed.add(StepOutcome(node.id, result, attempt, nextStepId = nextActionable(replanned.nodes)?.id, replanned = true))
                return executed.last()
            }

            // Replan budget exhausted -> terminal failure.
            state = state.copy(status = ExecutionStatus.FAILED, failureCause = cause)
            driver.emit(CognitiveEvent.PlanExecutionFailed(graph.goalId, cause, state.completedSteps))
            return executed.last()
        }
    }

    /** Run to a terminal boundary with an explicit step budget. */
    suspend fun runToTerminal(): ExecutionSummary {
        load()
        var guard = 0
        while (guard < config.maxStepsPerRun) {
            if (state.status == ExecutionStatus.CANCELLED || state.status == ExecutionStatus.PAUSED) break
            val next = executeNextStep() ?: break
            guard++
            if (next.replanned) continue // replan step consumed, loop re-selects
        }
        return ExecutionSummary(
            planId = state.planId ?: "",
            status = state.status,
            stepsCompleted = state.completedSteps,
            stepsFailed = state.failedSteps,
            failureCause = state.failureCause,
            replanTriggered = state.replanTriggered,
            steps = executed.toList()
        )
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun buildContext(node: PlanNode): Map<String, String> = buildMap {
        put("step", node.description)
        put("expectedOutcome", node.expectedOutcome)
        node.subgoalId?.let { put("subgoal", it) }
        node.completionCondition?.let { put("completionCondition", it) }
    }

    private fun failureText(result: ExecutionResult): String = when (val f = result.failure) {
        is ActionFailure.Error -> "${f.code}: ${f.message}"
        is ActionFailure.Timeout -> "timeout after ${f.durationMs}ms"
        is ActionFailure.Unsupported -> "unsupported action: ${f.action}"
        null -> "failure"
    }

    private fun terminalFrom(prev: ExecutionState): StepOutcome? {
        if (prev.status != ExecutionStatus.CANCELLED) {
            state = prev.copy(status = ExecutionStatus.FAILED, failureCause = state.failureCause ?: "no plan")
        }
        return null
    }
}
