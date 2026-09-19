package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.CognitiveEngine.CognitiveEvent
import com.jarvis.app.cognitive.DecisionOutcome
import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.execution.ExecutionResult
import com.jarvis.app.cognitive.execution.ExecutionState
import com.jarvis.app.cognitive.execution.ExecutionStatus
import com.jarvis.app.cognitive.execution.PlanDriver
import com.jarvis.app.cognitive.immune.PartialFailureEvaluator
import com.jarvis.app.cognitive.immune.PartialFailureVerdict
import com.jarvis.app.cognitive.planning.GoalPlanner
import com.jarvis.app.cognitive.planning.PlanGraph
import com.jarvis.app.cognitive.planning.PlanNode
import com.jarvis.app.cognitive.planning.nextActionable
import com.jarvis.app.cognitive.PlanStatus
import kotlinx.coroutines.CancellationException

/**
 * 01F — capability-resolved planning & execution.
 *
 * Connects the existing builds end-to-end without adding capabilities:
 *
 *   PlanGraph → nextActionable → ActionRequest
 *   → CapabilityResolver (explicit resolution, for state + observability)
 *   → CapabilityInvoker (through the fabric boundary — never the raw capability)
 *   → ExecutionResult → PlanDriver update → DecisionOutcome
 *   → PartialFailureEvaluator → GoalPlanner.replan when required
 *
 * Reuses every existing contract: [ActionRequest] / [ExecutionResult], the 01C
 * [PlanDriver] seam, 01B [GoalPlanner], 01D [PartialFailureEvaluator] and the
 * immune layer (the fabric invoker is the only execution path), and 01E
 * [CapabilityFabric] / [Resolution] / [CapabilityInvocation]. No new retry or
 * circuit system exists here — resolution/invocation/degradation all flow
 * through the fabric and the immune system.
 *
 * Resolution is explicit because the executor needs the structured [Resolution]
 * (which capability, or which reason) before committing to invocation; the
 * fabric's [CapabilityInvoker.invokeResolved] then enforces the same boundary
 * checks without re-resolving.
 */
class CapabilityExecutor(
    private val fabric: CapabilityFabric,
    private val driver: PlanDriver,
    private val goalPlanner: GoalPlanner = GoalPlanner(),
    private val granted: Set<CapabilityPermission> = emptySet(),
    private val timeoutMs: Long? = null,
    private val maxReplans: Int = 3,
    private val maxSteps: Int = 100,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    var state: ExecutionState = ExecutionState()
        private set

    /** The most recent partial-failure verdict (after the last failed step). */
    var lastVerdict: PartialFailureVerdict? = null
        private set

    private val outcomes = mutableListOf<CapabilityStepOutcome>()

    /** Number of replans performed so far (bounded by [maxReplans]). */
    var replanCount: Int = 0
        private set

    val executedOutcomes: List<CapabilityStepOutcome> get() = outcomes.toList()

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Load the driver's current plan and start. Idempotent. */
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
            startedAt = nowMs()
        )
        driver.emit(CognitiveEvent.ExecutionStarted(graph.goalId))
        return state
    }

    // ------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------

    /** Execute one full step: resolve → invoke → update → (partial-failure
     *  verdict + replan decision). Returns null at a terminal boundary. */
    suspend fun executeNextStep(): CapabilityStepOutcome? {
        val graph = driver.getPlanGraph() ?: return terminal(ExecutionStatus.FAILED, "plan has no steps")
        if (graph.nodes.isEmpty()) return terminal(ExecutionStatus.FAILED, "plan has no steps")
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
            // Nothing actionable — honour the plan's derived state. A FAILED
            // graph with a usable partial result is reported as FAILED (the
            // verdict carries usability); a BLOCKED graph as BLOCKED.
            val blocked = graph.blockedNodeIds
            val (terminalStatus, cause) = when {
                graph.status == PlanStatus.FAILED -> ExecutionStatus.FAILED to (graph.failureCause ?: "plan failed")
                graph.status == PlanStatus.BLOCKED || blocked.isNotEmpty() ->
                    ExecutionStatus.BLOCKED to "blocked: ${blocked.take(3)}"
                else -> ExecutionStatus.FAILED to "no actionable step"
            }
            state = state.copy(status = terminalStatus, failureCause = cause)
            when (terminalStatus) {
                ExecutionStatus.BLOCKED -> driver.emit(CognitiveEvent.StepBlocked(graph.goalId, null, blocked))
                ExecutionStatus.FAILED -> driver.emit(CognitiveEvent.PlanExecutionFailed(graph.goalId, cause, state.completedSteps))
                else -> Unit
            }
            return null
        }

        return executeNode(graph, node)
    }

    /** Run to a terminal boundary with an explicit step budget. */
    suspend fun run(): CapabilityExecutionSummary {
        load()
        var guard = 0
        while (guard < maxSteps) {
            if (state.status == ExecutionStatus.CANCELLED || state.status == ExecutionStatus.PAUSED) break
            val next = executeNextStep() ?: break
            guard++
        }
        return summary()
    }

    private suspend fun executeNode(graph: PlanGraph, node: PlanNode): CapabilityStepOutcome {
        state = state.copy(
            planId = graph.goalId,
            currentStepId = node.id,
            currentAction = node.action,
            status = ExecutionStatus.EXECUTING,
            attemptCount = 1,
            lastExecutedAt = nowMs()
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

        // Resolution is explicit: the executor needs the structured Resolution
        // for state + observability before committing to execution. Resolution
        // itself never executes anything.
        val resolution = fabric.resolver.resolve(request, granted)
        val resolvedId = (resolution as? Resolution.Success)?.capability?.descriptor?.id
        state = state.copy(resolutionResult = resolutionLabel(resolution))
        driver.emit(CognitiveEvent.CapabilityResolved(resolvedId ?: "", request.action, resolutionLabel(resolution)))

        if (resolution is Resolution.Failure) {
            val reason = resolution.reason
            val result = ExecutionResult.fail(
                ActionFailure.Error("RESOLUTION_${reason.name}", reason.name, recoverable = false)
            )
            return failStep(graph, node, request, resolution, result, invocation = null)
        }

        // Invocation through the fabric boundary — never the raw capability.
        driver.emit(CognitiveEvent.CapabilityInvocationStarted(
            capabilityId = resolvedId ?: "",
            operation = request.action,
            requestId = request.stepId
        ))

        val invocation = try {
            fabric.invoker.invokeResolved(request, resolution as Resolution.Success, timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val result = ExecutionResult.fail(
                ActionFailure.Error("PORT_EXCEPTION", e.message ?: "invocation threw", recoverable = false)
            )
            return failStep(graph, node, request, resolution, result, invocation = null)
        }
        val result = CapabilityActionPort.toExecutionResult(invocation)

        // One completion event per invocation — covers success AND refusal
        // (circuit open / permission), reflected in the `success` flag.
        driver.emit(CognitiveEvent.CapabilityInvocationCompleted(
            capabilityId = invocation.capabilityId ?: resolvedId ?: "",
            operation = invocation.operation,
            requestId = invocation.requestId,
            durationMs = invocation.durationMs,
            success = invocation.success
        ))

        state = state.copy(
            currentCapabilityId = invocation.capabilityId,
            invocationResult = invocationLabel(invocation),
            failureIdentity = if (invocation.success || invocation.refused) null else failureIdentityFor(request, result)
        )

        if (result.success) {
            val outcomeText = result.output["outcome"] ?: node.expectedOutcome
            val updated = driver.completePlanStep(node.id, outcomeText) ?: graph
            state = state.copy(
                lastResult = result,
                completedSteps = state.completedSteps + 1,
                executedSteps = state.executedSteps + 1,
                failureCause = null,
                retryable = false
            )
            driver.recordDecisionOutcome(DecisionOutcome.SUCCESS)
            driver.emit(CognitiveEvent.StepCompleted(graph.goalId, node.id, result.output, result.durationMs))
            val outcome = CapabilityStepOutcome(
                stepId = node.id,
                action = node.action,
                resolution = resolution,
                invocation = invocation,
                result = result,
                attempt = 1,
                nextStepId = nextActionable(updated.nodes)?.id
            )
            outcomes.add(outcome)
            return outcome
        }

        return failStep(graph, node, request, resolution, result, invocation)
    }

    private suspend fun failStep(
        graph: PlanGraph,
        node: PlanNode,
        request: ActionRequest,
        resolution: Resolution,
        result: ExecutionResult,
        invocation: CapabilityInvocation?
    ): CapabilityStepOutcome {
        val cause = failureText(result)
        val failed = driver.failPlanStep(node.id, cause) ?: graph
        state = state.copy(
            lastResult = result,
            failedSteps = state.failedSteps + 1,
            executedSteps = state.executedSteps + 1,
            retryable = result.retryable,
            failureCause = cause,
            invocationResult = state.invocationResult ?: "FAILURE",
            failureIdentity = state.failureIdentity ?: failureIdentityFor(request, result)
        )
        driver.recordDecisionOutcome(DecisionOutcome.FAILED)
        driver.emit(CognitiveEvent.StepFailed(graph.goalId, node.id, cause, retryable = result.retryable))

        // Partial-failure evaluation over every outcome so far INCLUDING the
        // current failed step: a failed step must not fail unrelated steps; the
        // verdict decides whether replanning is required. The executor never
        // restarts the goal — completed work stays completed (failNode/replan
        // preserve it).
        val failures = outcomes.associate { it.stepId to !it.result.success } + (node.id to true)
        val verdict = PartialFailureEvaluator.evaluate(failed, failures)
        lastVerdict = verdict
        state = state.copy(alternativeAvailable = verdict.alternativeCapabilityExists)

        val outcome = CapabilityStepOutcome(
            stepId = node.id,
            action = node.action,
            resolution = resolution,
            invocation = invocation,
            result = result,
            attempt = 1,
            failureIdentity = state.failureIdentity,
            verdict = verdict
        )
        outcomes.add(outcome)

        if (verdict.needsReplan && replanCount < maxReplans) {
            replanCount++
            state = state.copy(replanTriggered = true)
            driver.emit(CognitiveEvent.ReplanTriggered(graph.goalId, node.id, cause))
            val replanned = driver.replanPlan(node.id, cause) ?: failed
            // After a successful replan the verdict reflects the replacement
            // path: an alternative capability now exists for the failed step.
            val refreshed = verdict.copy(alternativeCapabilityExists = true)
            lastVerdict = refreshed
            state = state.copy(alternativeAvailable = true)
            val finalOutcome = outcome.copy(
                replanned = true,
                verdict = refreshed,
                nextStepId = nextActionable(replanned.nodes)?.id
            )
            outcomes[outcomes.lastIndex] = finalOutcome
            return finalOutcome
        }

        // No replan needed (acceptable partial result, or replan budget spent):
        // the next executeNextStep() call naturally picks up any unrelated
        // actionable step, or reaches a terminal boundary.
        return outcome
    }

    // ------------------------------------------------------------------
    // State / helpers
    // ------------------------------------------------------------------

    private fun summary(): CapabilityExecutionSummary = CapabilityExecutionSummary(
        planId = state.planId ?: "",
        status = state.status,
        stepsCompleted = state.completedSteps,
        stepsFailed = state.failedSteps,
        failureCause = state.failureCause,
        replanTriggered = state.replanTriggered,
        lastVerdict = lastVerdict,
        steps = outcomes.toList()
    )

    private fun terminal(status: ExecutionStatus, cause: String): CapabilityStepOutcome? {
        state = state.copy(status = status, failureCause = cause)
        return null
    }

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

    private fun resolutionLabel(resolution: Resolution): String = when (resolution) {
        is Resolution.Success -> "AVAILABLE"
        is Resolution.Failure -> resolution.reason.name
    }

    private fun invocationLabel(invocation: CapabilityInvocation): String = when {
        invocation.refused -> "REFUSED"
        invocation.success -> "SUCCESS"
        else -> "FAILURE"
    }

    private fun requestId(request: ActionRequest): String = "${request.planId}|${request.stepId}|${request.action}"

    private fun failureIdentityFor(request: ActionRequest, result: ExecutionResult): String {
        val code = when (val f = result.failure) {
            is ActionFailure.Error -> f.code
            is ActionFailure.Timeout -> "TIMEOUT"
            is ActionFailure.Unsupported -> "UNSUPPORTED"
            null -> "FAILURE"
        }
        return "${requestId(request)}:$code"
    }
}

/** One resolved + executed plan step. Structured trace of the 01F path. */
data class CapabilityStepOutcome(
    val stepId: String,
    val action: String,
    val resolution: Resolution,
    val invocation: CapabilityInvocation?,
    val result: ExecutionResult,
    val attempt: Int,
    val failureIdentity: String? = null,
    val verdict: PartialFailureVerdict? = null,
    val nextStepId: String? = null,
    val replanned: Boolean = false
) {
    val resolvedCapabilityId: String?
        get() = (resolution as? Resolution.Success)?.capability?.descriptor?.id
    val success: Boolean get() = result.success
}

/** Terminal summary of a capability-resolved execution run. */
data class CapabilityExecutionSummary(
    val planId: String,
    val status: ExecutionStatus,
    val stepsCompleted: Int,
    val stepsFailed: Int,
    val failureCause: String? = null,
    val replanTriggered: Boolean = false,
    val lastVerdict: PartialFailureVerdict? = null,
    val steps: List<CapabilityStepOutcome> = emptyList()
)
