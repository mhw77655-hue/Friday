package com.jarvis.app.validation

import com.jarvis.app.mutation.FitnessModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotionGateTest {
    private val passing = FitnessModel.TestOutcome("candidate-test", true)
    private val failing = FitnessModel.TestOutcome("baseline-test", false, error = "regression")

    private fun validBenchmarks() = listOf(
        FitnessModel.BenchmarkOutcome("latency", "latency_ms", 12.0, "ms"),
        FitnessModel.BenchmarkOutcome("memory", "memory_mb", 4.0, "mb")
    )

    private fun validResource() = ResourceBenchmark.ResourceReport(
        benchmarks = validBenchmarks(),
        averageLatencyMs = 12.0,
        iterations = 20
    )

    private fun evaluation(
        tests: List<FitnessModel.TestOutcome> = listOf(passing),
        benchmarks: List<FitnessModel.BenchmarkOutcome> = validBenchmarks()
    ) = FitnessModel.evaluate("candidate", tests, benchmarks)

    private val gate = PromotionGate()

    @Test
    fun `promotes only when candidate baseline and resource evidence are complete`() {
        val decision = gate.decide(evaluation(), listOf(passing), validResource())
        assertEquals(GateVerdict.PROMOTE, decision.verdict)
        assertEquals(1.0, decision.regressionRate, 0.0)
    }

    @Test
    fun `empty baseline regression evidence is rejected`() {
        val decision = gate.decide(evaluation(), emptyList(), validResource())
        assertEquals(GateVerdict.REJECT, decision.verdict)
        assertEquals(0.0, decision.regressionRate, 0.0)
        assertTrue(decision.reasons.any { it.contains("no baseline test evidence") })
    }

    @Test
    fun `empty resource evidence is rejected`() {
        val empty = ResourceBenchmark.ResourceReport(emptyList(), 0.0, 0)
        val decision = gate.decide(evaluation(), listOf(passing), empty)
        assertEquals(GateVerdict.REJECT, decision.verdict)
        assertTrue(decision.reasons.any { it.contains("resource") })
    }

    @Test
    fun `candidate test failures are never promoted`() {
        val decision = gate.decide(evaluation(listOf(passing, failing)), listOf(passing), validResource())
        assertEquals(GateVerdict.REJECT, decision.verdict)
        assertTrue(decision.reasons.any { it.contains("candidate") })
    }

    @Test
    fun `baseline regression failures are never promoted`() {
        val decision = gate.decide(evaluation(), listOf(passing, failing), validResource())
        assertEquals(GateVerdict.REJECT, decision.verdict)
        assertTrue(decision.reasons.any { it.startsWith("regression:") })
    }

    @Test
    fun `non-finite resource measurements are rejected`() {
        val invalid = ResourceBenchmark.ResourceReport(
            benchmarks = listOf(
                FitnessModel.BenchmarkOutcome("latency", "latency_ms", Double.NaN, "ms"),
                FitnessModel.BenchmarkOutcome("memory", "memory_mb", 4.0, "mb")
            ),
            averageLatencyMs = Double.NaN,
            iterations = 20
        )
        val decision = gate.decide(evaluation(), listOf(passing), invalid)
        assertEquals(GateVerdict.REJECT, decision.verdict)
    }

    @Test
    fun `latency budget excess requests mutation not promotion`() {
        val slow = ResourceBenchmark.ResourceReport(
            benchmarks = listOf(
                FitnessModel.BenchmarkOutcome("latency", "latency_ms", 3_000.0, "ms"),
                FitnessModel.BenchmarkOutcome("memory", "memory_mb", 4.0, "mb")
            ),
            averageLatencyMs = 3_000.0,
            iterations = 20
        )
        val decision = gate.decide(evaluation(), listOf(passing), slow)
        assertEquals(GateVerdict.MUTATE, decision.verdict)
    }
}
