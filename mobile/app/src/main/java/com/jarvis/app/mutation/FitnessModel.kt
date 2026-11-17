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
        fun weightedScore(weights: FitnessWeights = FitnessWeights()): Double {
            val totalWeight = weights.totalWeight()
            val weighted =
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
            return (weighted / totalWeight).coerceIn(0.0, 1.0)
        }

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
    ) {
        init {
            val values = listOf(
                correctness, reliability, latency, memoryCost, cpuCost,
                energyCost, failureRate, compatibility, maintainability, testCoverage
            )
            require(values.all { it.isFinite() && it >= 0.0 }) {
                "fitness weights must be finite and non-negative"
            }
            require(values.sum().isFinite() && values.any { it > 0.0 }) {
                "fitness weights must have a finite positive total"
            }
        }

        internal fun totalWeight(): Double =
            correctness + reliability + latency + memoryCost + cpuCost +
                energyCost + failureRate + compatibility + maintainability + testCoverage
    }

    /** Result of evaluating one candidate against the fitness model. */
    data class EvaluationResult(
        val candidateId: String,
        val dimensions: FitnessDimensions,
        val hardConstraintsPassed: Boolean,
        val hardConstraintFailures: List<String>,
        val testResults: List<TestOutcome>,
        val benchmarkResults: List<BenchmarkOutcome>,
        val weights: FitnessWeights = FitnessWeights()
    ) {
        val passed: Boolean get() = hardConstraintsPassed
        val score: Double get() = dimensions.weightedScore(weights)
    }

    /** Evaluate a candidate: check hard constraints, then score dimensions. */
    fun evaluate(
        candidateId: String,
        testResults: List<TestOutcome>,
        benchmarkResults: List<BenchmarkOutcome>,
        hardConstraints: List<HardConstraint> = defaultConstraints(),
        weights: FitnessWeights = FitnessWeights()
    ): EvaluationResult {
        val dimensions = computeDimensions(testResults, benchmarkResults)
        val constraintFailures = mutableListOf<String>()
        val provisional = EvaluationResult(
            candidateId = candidateId,
            dimensions = dimensions,
            hardConstraintsPassed = true,
            hardConstraintFailures = emptyList(),
            testResults = testResults,
            benchmarkResults = benchmarkResults,
            weights = weights
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
        benchmarkResults: List<BenchmarkOutcome>
    ): FitnessDimensions {
        val total = testResults.size.coerceAtLeast(1)
        val passed = testResults.count { it.passed }
        val failures = testResults.count { !it.passed }

        // Missing or invalid measurements are unknown, never silently scored
        // as zero cost / perfect performance.
        fun measuredAverage(metric: String): Double? {
            val values = benchmarkResults.filter { it.metric == metric }.map { it.value }
            if (values.isEmpty() || values.any { !it.isFinite() || it < 0.0 }) return null
            return values.average().takeIf { it.isFinite() && it >= 0.0 }
        }

        val avgLatency = measuredAverage("latency_ms")
        val avgMemory = measuredAverage("memory_mb")
        val avgCpu = measuredAverage("cpu_percent")
        val avgEnergy = measuredAverage("energy_mah")

        return FitnessDimensions(
            correctness = passed.toDouble() / total,
            reliability = (total - failures).toDouble() / total,
            latency = avgLatency?.let { 1.0 - (it / 5000.0).coerceIn(0.0, 1.0) } ?: 0.0,
            memoryCost = avgMemory?.let { 1.0 - (it / 2048.0).coerceIn(0.0, 1.0) } ?: 0.0,
            cpuCost = avgCpu?.let { 1.0 - (it / 100.0).coerceIn(0.0, 1.0) } ?: 0.0,
            energyCost = avgEnergy?.let { 1.0 - (it / 500.0).coerceIn(0.0, 1.0) } ?: 0.0,
            failureRate = (total - failures).toDouble() / total,
            compatibility = 0.0, // no compatibility measurement is wired yet
            maintainability = 0.0, // no maintainability measurement is wired yet
            testCoverage = passed.toDouble() / total
        )
    }

    fun defaultConstraints() = listOf(
        HardConstraint(
            "min_correctness",
            check = { it.testResults.isNotEmpty() && it.dimensions.testCoverage >= 0.5 },
            rejectReason = "candidate tests must all pass"
        ),
        HardConstraint(
            "latency_evidence",
            check = { result ->
                val measurements = result.benchmarkResults.filter { it.metric == "latency_ms" }
                measurements.isNotEmpty() && measurements.all { it.value.isFinite() && it.value >= 0.0 }
            },
            rejectReason = "measured latency benchmark evidence missing or invalid"
        ),
        HardConstraint(
            "memory_evidence",
            check = { result ->
                val measurements = result.benchmarkResults.filter { it.metric == "memory_mb" }
                measurements.isNotEmpty() && measurements.all { it.value.isFinite() && it.value >= 0.0 }
            },
            rejectReason = "measured memory benchmark evidence missing or invalid"
        )
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
