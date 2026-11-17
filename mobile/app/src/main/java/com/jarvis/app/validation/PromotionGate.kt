package com.jarvis.app.validation

import com.jarvis.app.mutation.FitnessModel
import com.jarvis.app.validation.ResourceBenchmark.ResourceReport
import kotlin.math.abs

/**
 * Promotion Gate (§20, §24): the review gate between sandbox and production.
 *
 * Promotion requires independent candidate tests, a non-empty baseline
 * regression run, and measured resource evidence. Missing evidence is never
 * interpreted as a perfect pass or zero-cost candidate.
 */
class PromotionGate(
    private val hardConstraints: List<FitnessModel.HardConstraint> = FitnessModel.defaultConstraints(),
    private val minRegressionPassRate: Double = 1.0,
    private val maxLatencyMs: Long = 2_000
) {
    init {
        require(minRegressionPassRate.isFinite() && minRegressionPassRate in 0.0..1.0) {
            "minimum regression pass rate must be between 0 and 1"
        }
        require(maxLatencyMs > 0L) { "maximum latency must be positive" }
    }

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

        if (evaluation.testResults.isEmpty()) {
            reasons += "candidate: no test evidence"
        } else if (evaluation.testResults.any { !it.passed }) {
            val passed = evaluation.testResults.count { it.passed }
            reasons += "candidate: $passed/${evaluation.testResults.size} tests passed"
        }

        val candidateNames = evaluation.testResults.map { it.name }.toSet()
        val regressionNames = regression.map { it.name }.toSet()
        if (candidateNames.intersect(regressionNames).isNotEmpty()) {
            reasons += "regression: baseline test identifiers overlap candidate tests"
        }

        val regressionRate = if (regression.isEmpty()) {
            reasons += "regression: no baseline test evidence"
            0.0
        } else {
            regression.count { it.passed }.toDouble() / regression.size
        }
        if (regression.isNotEmpty() && regressionRate < minRegressionPassRate) {
            reasons += "regression: ${regression.count { it.passed }}/${regression.size} baseline tests pass"
        }

        val latencyMeasurements = resource.benchmarks
            .filter { it.metric == "latency_ms" }
            .map { it.value }
        val validLatency = latencyMeasurements.isNotEmpty() &&
            latencyMeasurements.all { it.isFinite() && it >= 0.0 }
        val memoryMeasurements = resource.benchmarks
            .filter { it.metric == "memory_mb" }
            .map { it.value }
        val validMemory = memoryMeasurements.isNotEmpty() &&
            memoryMeasurements.all { it.isFinite() && it >= 0.0 }
        val allMeasurementsValid = resource.benchmarks.all {
            it.value.isFinite() && it.value >= 0.0
        }
        val measuredAverageLatency = latencyMeasurements.takeIf { validLatency }?.average()
        val averageMatchesMeasurements = measuredAverageLatency != null &&
            abs(measuredAverageLatency - resource.averageLatencyMs) <= 0.001
        val validResourceEvidence =
            resource.iterations > 0 &&
                resource.averageLatencyMs.isFinite() &&
                resource.averageLatencyMs >= 0.0 &&
                validLatency &&
                validMemory &&
                allMeasurementsValid &&
                averageMatchesMeasurements
        if (!validResourceEvidence) {
            reasons += "resource: missing or invalid measured latency/memory benchmark evidence"
        } else if (resource.averageLatencyMs > maxLatencyMs) {
            reasons += "latency ${resource.averageLatencyMs}ms exceeds ${maxLatencyMs}ms budget"
        }

        val verdict = when {
            reasons.isEmpty() -> GateVerdict.PROMOTE
            // A candidate may be mutated after a bounded performance/fitness
            // issue, but it is never promoted while any evidence is missing.
            evaluation.dimensions.correctness >= 0.8 && reasons.size == 1 &&
                reasons.none { it.startsWith("regression:") || it.startsWith("resource:") || it.startsWith("candidate:") } ->
                GateVerdict.MUTATE
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
