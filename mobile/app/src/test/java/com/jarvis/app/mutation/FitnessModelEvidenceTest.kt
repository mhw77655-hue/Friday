package com.jarvis.app.mutation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessModelEvidenceTest {

    @Test
    fun `missing benchmark measurements are unknown not perfect`() {
        val result = FitnessModel.evaluate(
            candidateId = "no-benchmarks",
            testResults = listOf(FitnessModel.TestOutcome("pass", true)),
            benchmarkResults = emptyList()
        )

        assertEquals(0.0, result.dimensions.latency, 0.0)
        assertEquals(0.0, result.dimensions.memoryCost, 0.0)
        assertEquals(0.0, result.dimensions.cpuCost, 0.0)
        assertEquals(0.0, result.dimensions.energyCost, 0.0)
        assertFalse(result.hardConstraintsPassed)
    }

    @Test
    fun `non-finite and negative metrics do not produce perfect scores`() {
        val result = FitnessModel.evaluate(
            candidateId = "invalid-benchmarks",
            testResults = listOf(FitnessModel.TestOutcome("pass", true)),
            benchmarkResults = listOf(
                FitnessModel.BenchmarkOutcome("latency", "latency_ms", Double.NaN, "ms"),
                FitnessModel.BenchmarkOutcome("memory", "memory_mb", -1.0, "mb")
            )
        )

        assertEquals(0.0, result.dimensions.latency, 0.0)
        assertEquals(0.0, result.dimensions.memoryCost, 0.0)
        assertFalse(result.hardConstraintsPassed)
    }

    @Test
    fun `configured fitness weights affect evaluation score`() {
        val result = FitnessModel.evaluate(
            candidateId = "weighted",
            testResults = listOf(FitnessModel.TestOutcome("pass", true)),
            benchmarkResults = emptyList(),
            hardConstraints = emptyList(),
            weights = FitnessModel.FitnessWeights(
                correctness = 1.0,
                reliability = 0.0,
                latency = 0.0,
                memoryCost = 0.0,
                cpuCost = 0.0,
                energyCost = 0.0,
                failureRate = 0.0,
                compatibility = 0.0,
                maintainability = 0.0,
                testCoverage = 0.0
            )
        )

        assertTrue(result.hardConstraintsPassed)
        assertEquals(1.0, result.score, 0.0)
    }
}
