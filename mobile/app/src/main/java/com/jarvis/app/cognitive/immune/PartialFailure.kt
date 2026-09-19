package com.jarvis.app.cognitive.immune

import com.jarvis.app.cognitive.planning.PlanGraph
import com.jarvis.app.cognitive.planning.PlanNode
import com.jarvis.app.cognitive.planning.transitivelyDownstream

/**
 * Verdict for a partially-executed plan: A and B succeeded, C failed. The whole
 * plan must NOT automatically become FAILED — decide whether the partial result
 * is usable, which steps are now blocked, whether an alternative exists, and
 * whether replanning is needed.
 */
data class PartialFailureVerdict(
    val failedSteps: List<String>,
    val successfulSteps: List<String>,
    /** Whether the successful portion is enough to count as usable output. */
    val usable: Boolean,
    /** Whether the partial result is acceptable as-is (no replan). */
    val partialResultAcceptable: Boolean,
    /** Steps blocked transitively because they depended on a failed step. */
    val blockedSteps: List<String>,
    /** Whether the graph already carries an alternative/replacement path. */
    val alternativeCapabilityExists: Boolean,
    val needsReplan: Boolean
)

/**
 * Deterministic evaluation of partial failure against a [PlanGraph].
 */
object PartialFailureEvaluator {

    /**
     * @param failures stepId -> true when the step FAILED (false = succeeded).
     * @param usableThreshold fraction of steps that must succeed for usability.
     * @param acceptableThreshold fraction for the result to be acceptable as-is.
     */
    fun evaluate(
        graph: PlanGraph,
        failures: Map<String, Boolean>,
        usableThreshold: Float = 0.6f,
        acceptableThreshold: Float = 0.8f
    ): PartialFailureVerdict {
        val failedSteps = failures.filterValues { it }.keys.toList()
        val successfulSteps = failures.filterValues { !it }.keys.toList()

        // Steps blocked because they (transitively) depend on a failed step.
        val blocked = LinkedHashSet<String>()
        failedSteps.forEach { failed ->
            transitivelyDownstream(graph.nodes, failed).forEach { blocked.add(it) }
        }
        // The failed steps themselves are also not runnable.
        blocked.addAll(failedSteps)

        val total = graph.nodes.size.coerceAtLeast(1)
        val successFraction = successfulSteps.size.toFloat() / total

        val usable = successFraction >= usableThreshold
        val acceptable = successFraction >= acceptableThreshold
        val alternativeExists = graph.replanning != null // a replacement path is already present
        val needsReplan = failedSteps.isNotEmpty() && !acceptable

        return PartialFailureVerdict(
            failedSteps = failedSteps,
            successfulSteps = successfulSteps,
            usable = usable,
            partialResultAcceptable = acceptable,
            blockedSteps = blocked.toList(),
            alternativeCapabilityExists = alternativeExists,
            needsReplan = needsReplan
        )
    }
}

/** Convenience: fold ExecutionResult-style step outcomes into the failures map. */
fun planFailuresFrom(outcomes: List<com.jarvis.app.cognitive.execution.StepOutcome>): Map<String, Boolean> =
    outcomes.associate { it.stepId to !it.result.success }
