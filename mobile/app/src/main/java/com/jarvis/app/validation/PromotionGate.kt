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
        resource: ResourceReport,
        /**
         * True only when the caller has independently checked both the live
         * registry and durable genome registry and proved this capability did
         * not exist before the build. A genuinely new capability has no old
         * behavior to regress; an existing capability with missing tests must
         * still be rejected.
         */
        baselineAbsenceVerified: Boolean = false
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

        val candidateIds = evaluation.testResults.map { it.name }
        val regressionIds = regression.map { it.name }
        if (candidateIds.any(String::isBlank) || candidateIds.toSet().size != candidateIds.size) {
            reasons += "candidate: test identifiers must be nonblank and unique"
        }
        if (regressionIds.any(String::isBlank) || regressionIds.toSet().size != regressionIds.size) {
            reasons += "regression: baseline test identifiers must be nonblank and unique"
        }
        if (candidateIds.toSet().intersect(regressionIds.toSet()).isNotEmpty()) {
            reasons += "regression: baseline test identifiers overlap candidate tests"
        }

        val verifiedNewCapability = regression.isEmpty() && baselineAbsenceVerified
        val regressionRate = if (regression.isEmpty() && !verifiedNewCapability) {
            reasons += "regression: no baseline test evidence and no verified new-capability absence"
            0.0
        } else if (verifiedNewCapability) {
            // This is not a fabricated passing test: it records that the
            // capability was absent before the build, so there was no prior
            // implementation whose behavior could regress.
            1.0
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
        val evaluationEvidenceMatchesResource = sameMeasurementsByMetric(
            evaluation.benchmarkResults,
            resource.benchmarks
        )
        val validResourceEvidence =
            resource.iterations > 0 &&
                resource.averageLatencyMs.isFinite() &&
                resource.averageLatencyMs >= 0.0 &&
                validLatency &&
                validMemory &&
                allMeasurementsValid &&
                averageMatchesMeasurements &&
                evaluationEvidenceMatchesResource
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
            correctness = evaluation.dimensions.correctness,
            baselineAbsenceVerified = verifiedNewCapability
        )
    }

    /**
     * The fitness score and promotion decision must refer to the same measured
     * run. Otherwise a caller could score one candidate's cheap measurements
     * while presenting a different, still-valid resource report to the gate.
     */
    private fun sameMeasurementsByMetric(
        evaluated: List<FitnessModel.BenchmarkOutcome>,
        reported: List<FitnessModel.BenchmarkOutcome>
    ): Boolean {
        val metrics = evaluated.map { it.metric }.toSet() + reported.map { it.metric }.toSet()
        return metrics.all { metric ->
            val left = evaluated.filter { it.metric == metric }.map { it.value }.sorted()
            val right = reported.filter { it.metric == metric }.map { it.value }.sorted()
            left.size == right.size && left.zip(right).all { (a, b) ->
                a.isFinite() && b.isFinite() && abs(a - b) <= 0.001
            }
        }
    }
}

enum class GateVerdict { PROMOTE, MUTATE, REJECT }

data class GateDecision(
    val verdict: GateVerdict,
    val reasons: List<String>,
    val score: Double,
    val regressionRate: Double,
    val testCoverage: Double,
    val correctness: Double,
    val baselineAbsenceVerified: Boolean = false
)
