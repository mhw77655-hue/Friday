package com.jarvis.app.mutation

/**
 * Multi-dimensional fitness model for evaluating genome candidates.
 *
 * Supports Pareto-optimal selection (no permanent single scalar score).
 * Hard constraints can automatically reject candidates before scoring.
 */
object FitnessModel {

    /** Hard constraints that automatically reject a candidate. */
    data class HardConstraint(
        val name: String,
        val check: (EvaluationResult) -> Boolean, // true = passes
        val rejectReason: String
    )

    /** The dimensions of fitness. Each preserves individual metrics. */
    data class FitnessDimensions(
        val correctness: Double = 0.0,       // 0-1: does it work correctly?
        val reliability: Double = 0.0,       // 0-1: failure rate inverse
        val latency: Double = 0.0,           // 0-1: normalized speed (1=fastest)
        val memoryCost: Double = 0.0,        // 0-1: normalized cost (1=cheapest)
        val cpuCost: Double = 0.0,           // 0-1: normalized cost (1=cheapest)
        val energyCost: Double = 0.0,        // 0-1: normalized cost (1=cheapest)
        val failureRate: Double = 1.0,       // 0-1: inverse of failures (1=no failures)
        val compatibility: Double = 0.0,     // 0-1: how well it fits the system
        val maintainability: Double = 0.0,   // 0-1: code quality metric
        val testCoverage: Double = 0.0       // 0-1: fraction of tests passing
    ) {
        /** Simple weighted scalar (used only for ranking ties, NOT for promotion). */
        fun weightedScore(weights: FitnessWeights = FitnessWeights()): Double =
            correctness * weights.correctness +
                reliability * weights.reliability +
                latency * weights.latency +
                memoryCost * weights.memoryCost +
                cpuCost * weights.cpuCost +
                energyCost * weights.energyCost +
                failureRate * weights.failureRate +
                compatibility * weights.compatibility +
                maintainability * weights.maintainability +
                testCoverage * weights.testCoverage

        companion object {
            val ZEROS = FitnessDimensions()
        }
    }

    /** Default weights (sum ≈ 1). */
    data class FitnessWeights(
        val correctness: Double = 0.25,
        val reliability: Double = 0.15,
        val latency: Double = 0.15,
        val memoryCost: Double = 0.10,
        val cpuCost: Double = 0.05,
        val energyCost: Double = 0.05,
        val failureRate: Double = 0.15,
        val compatibility: Double = 0.05,
        val maintainability: Double = 0.05,
        val testCoverage: Double = 0.10
    )

    /** Result of evaluating one candidate against the fitness model. */
    data class EvaluationResult(
        val candidateId: String,
        val dimensions: FitnessDimensions,
        val hardConstraintsPassed: Boolean,
        val hardConstraintFailures: List<String>,
        val testResults: List<TestOutcome>,
        val benchmarkResults: List<BenchmarkOutcome>
    ) {
        val passed: Boolean get() = hardConstraintsPassed
        val score: Double get() = dimensions.weightedScore()
    }

    /** Evaluate a candidate: check hard constraints, then score dimensions. */
    fun evaluate(
        candidateId: String,
        testResults: List<TestOutcome>,
        benchmarkResults: List<BenchmarkOutcome>,
        hardConstraints: List<HardConstraint> = defaultConstraints(),
        weights: FitnessWeights = FitnessWeights()
    ): EvaluationResult {
        val dimensions = computeDimensions(testResults, benchmarkResults, weights)
        val constraintFailures = mutableListOf<String>()
        val provisional = EvaluationResult(
            candidateId = candidateId,
            dimensions = dimensions,
            hardConstraintsPassed = true,
            hardConstraintFailures = emptyList(),
            testResults = testResults,
            benchmarkResults = benchmarkResults
        )
        for (c in hardConstraints) {
            if (!c.check(provisional)) {
                constraintFailures += c.rejectReason
            }
        }

        return provisional.copy(
            hardConstraintsPassed = constraintFailures.isEmpty(),
            hardConstraintFailures = constraintFailures
        )
    }

    /** Compare two candidates. Returns positive if a > b. */
    fun compare(a: EvaluationResult, b: EvaluationResult, weights: FitnessWeights = FitnessWeights()): Int {
        val sa = a.dimensions.weightedScore(weights)
        val sb = b.dimensions.weightedScore(weights)
        return sa.compareTo(sb)
    }

    /** Select the Pareto-optimal subset from a list of evaluations. */
    fun paretoOptimal(candidates: List<EvaluationResult>): List<EvaluationResult> {
        if (candidates.size <= 1) return candidates
        return candidates.filter { c ->
            candidates.none { other ->
                other !== c && dominates(other.dimensions, c.dimensions)
            }
        }
    }

    /** a dominates b if a is >= b in all dimensions and strictly > in at least one. */
    private fun dominates(a: FitnessDimensions, b: FitnessDimensions): Boolean {
        val aValues = listOf(a.correctness, a.reliability, a.latency, a.memoryCost, a.cpuCost, a.energyCost, a.failureRate, a.compatibility, a.maintainability, a.testCoverage)
        val bValues = listOf(b.correctness, b.reliability, b.latency, b.memoryCost, b.cpuCost, b.energyCost, b.failureRate, b.compatibility, b.maintainability, b.testCoverage)
        var strictlyBetter = false
        for (i in aValues.indices) {
            if (aValues[i] < bValues[i]) return false
            if (aValues[i] > bValues[i]) strictlyBetter = true
        }
        return strictlyBetter
    }

    private fun computeDimensions(
        testResults: List<TestOutcome>,
        benchmarkResults: List<BenchmarkOutcome>,
        weights: FitnessWeights
    ): FitnessDimensions {
        val total = testResults.size.coerceAtLeast(1)
        val passed = testResults.count { it.passed }
        val avgLatency = benchmarkResults.filter { it.metric == "latency_ms" }.map { it.value }.average().let { if (it.isNaN()) 0.0 else it }
        val avgMemory = benchmarkResults.filter { it.metric == "memory_mb" }.map { it.value }.average().let { if (it.isNaN()) 0.0 else it }
        val avgCpu = benchmarkResults.filter { it.metric == "cpu_percent" }.map { it.value }.average().let { if (it.isNaN()) 0.0 else it }
        val avgEnergy = benchmarkResults.filter { it.metric == "energy_mah" }.map { it.value }.average().let { if (it.isNaN()) 0.0 else it }
        val failures = testResults.count { !it.passed }

        return FitnessDimensions(
            correctness = (passed.toDouble() / total),
            reliability = ((total - failures).toDouble() / total),
            latency = 1.0 - (avgLatency / 5000.0).coerceIn(0.0, 1.0),
            memoryCost = 1.0 - (avgMemory / 2048.0).coerceIn(0.0, 1.0),
            cpuCost = 1.0 - (avgCpu / 100.0).coerceIn(0.0, 1.0),
            energyCost = 1.0 - (avgEnergy / 500.0).coerceIn(0.0, 1.0),
            failureRate = ((total - failures).toDouble() / total),
            compatibility = 1.0, // default: fully compatible
            maintainability = 0.5, // default: neutral
            testCoverage = (passed.toDouble() / total)
        )
    }

    fun defaultConstraints() = listOf(
        HardConstraint("min_correctness", check = { it.dimensions.testCoverage >= 0.5 }, rejectReason = "test coverage below 0.5"),
        HardConstraint("max_memory", check = { it.dimensions.memoryCost >= 0.0 }, rejectReason = "memory cost invalid")
    )

    data class TestOutcome(
        val name: String,
        val passed: Boolean,
        val durationMs: Long = 0,
        val error: String? = null
    )

    data class BenchmarkOutcome(
        val name: String,
        val metric: String,
        val value: Double,
        val unit: String = "",
        val baseline: Double = 0.0
    )
}
