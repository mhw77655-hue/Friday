package com.jarvis.app.validation

import com.jarvis.app.mutation.FitnessModel
import com.jarvis.app.validation.ResourceBenchmark.ResourceReport

/**
 * Promotion Gate (§20, §24): the review gate between sandbox and production.
 *
 * A candidate is PROMOTED only when every hard constraint passes AND the
 * current baseline fully regresses cleanly AND the resource cost is within
 * budget. A partial performer is sent back to MUTATE; anything else is REJECTED
 * and preserved in the archive as failed. The gate never promotes a candidate
 * merely because it built or because its own tests pass in isolation.
 */
class PromotionGate(
    private val hardConstraints: List<FitnessModel.HardConstraint> = FitnessModel.defaultConstraints(),
    private val minRegressionPassRate: Double = 1.0,
    private val maxLatencyMs: Long = 2_000
) {

    val constraints: List<FitnessModel.HardConstraint> get() = hardConstraints

    fun decide(
        evaluation: FitnessModel.EvaluationResult,
        regression: List<FitnessModel.TestOutcome>,
        resource: ResourceReport
    ): GateDecision {
        val reasons = mutableListOf<String>()

        if (!evaluation.hardConstraintsPassed) {
            reasons += evaluation.hardConstraintFailures.ifEmpty { listOf("hard constraints failed") }
        }
        val regressionRate = if (regression.isEmpty()) 1.0 else regression.count { it.passed }.toDouble() / regression.size
        if (regressionRate < minRegressionPassRate) {
            reasons += "regression: ${regression.count { it.passed }}/${regression.size} baseline tests fail"
        }
        if (resource.averageLatencyMs > maxLatencyMs) {
            reasons += "latency ${resource.averageLatencyMs}ms exceeds ${maxLatencyMs}ms budget"
        }

        val verdict = when {
            reasons.isEmpty() -> GateVerdict.PROMOTE
            // Works but underperforms on a bounded number of resource dimensions
            // → a mutation of the current candidate may fix it (§5).
            evaluation.dimensions.correctness >= 0.8 && reasons.size <= 1 -> GateVerdict.MUTATE
            else -> GateVerdict.REJECT
        }

        return GateDecision(
            verdict = verdict,
            reasons = reasons,
            score = evaluation.score,
            regressionRate = regressionRate,
            testCoverage = evaluation.dimensions.testCoverage,
            correctness = evaluation.dimensions.correctness
        )
    }
}

enum class GateVerdict { PROMOTE, MUTATE, REJECT }

data class GateDecision(
    val verdict: GateVerdict,
    val reasons: List<String>,
    val score: Double,
    val regressionRate: Double,
    val testCoverage: Double,
    val correctness: Double
)
