package ai.friday.assistant.validation

import ai.friday.assistant.mutation.FitnessModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotionGateTest {
    private val passing = FitnessModel.TestOutcome("candidate-test", true)
    private val baselinePassing = FitnessModel.TestOutcome("baseline-test", true)
    private val failing = FitnessModel.TestOutcome("baseline-failing-test", false, error = "regression")

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
        val decision = gate.decide(evaluation(), listOf(baselinePassing), validResource())
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
    fun `verified absence of a prior capability permits an empty regression suite`() {
        val decision = gate.decide(
            evaluation(),
            emptyList(),
            validResource(),
            baselineAbsenceVerified = true
        )
        assertEquals(GateVerdict.PROMOTE, decision.verdict)
        assertEquals(1.0, decision.regressionRate, 0.0)
        assertTrue(decision.baselineAbsenceVerified)
    }

    @Test
    fun `unverified empty regression suite remains rejected`() {
        val decision = gate.decide(evaluation(), emptyList(), validResource())
        assertEquals(GateVerdict.REJECT, decision.verdict)
        assertTrue(!decision.baselineAbsenceVerified)
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
        val decision = gate.decide(evaluation(listOf(passing, failing)), listOf(baselinePassing), validResource())
        assertEquals(GateVerdict.REJECT, decision.verdict)
        assertTrue(decision.reasons.any { it.contains("candidate") })
    }

    @Test
    fun `duplicate candidate test identifiers are rejected`() {
        val duplicated = listOf(passing, passing.copy(passed = true))
        val decision = gate.decide(evaluation(duplicated), listOf(baselinePassing), validResource())
        assertEquals(GateVerdict.REJECT, decision.verdict)
        assertTrue(decision.reasons.any { it.contains("nonblank and unique") })
    }

    @Test
    fun `baseline regression failures are never promoted`() {
        val decision = gate.decide(evaluation(), listOf(baselinePassing, failing), validResource())
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
        val decision = gate.decide(evaluation(), listOf(baselinePassing), invalid)
        assertEquals(GateVerdict.REJECT, decision.verdict)
    }


    @Test
    fun `candidate and baseline test identifiers must be independent`() {
        val decision = gate.decide(evaluation(), listOf(passing), validResource())
        assertEquals(GateVerdict.REJECT, decision.verdict)
        assertTrue(decision.reasons.any { it.contains("identifiers overlap") })
    }

    @Test
    fun `every resource measurement must be finite and non-negative`() {
        val invalidMemory = ResourceBenchmark.ResourceReport(
            benchmarks = validBenchmarks() + FitnessModel.BenchmarkOutcome(
                "second-memory-sample", "memory_mb", Double.NaN, "mb"
            ),
            averageLatencyMs = 12.0,
            iterations = 20
        )
        val decision = gate.decide(evaluation(), listOf(baselinePassing), invalidMemory)
        assertEquals(GateVerdict.REJECT, decision.verdict)
    }

    @Test
    fun `resource latency summary must match measured samples`() {
        val inconsistent = ResourceBenchmark.ResourceReport(
            benchmarks = validBenchmarks(),
            averageLatencyMs = 1.0,
            iterations = 20
        )
        val decision = gate.decide(evaluation(), listOf(baselinePassing), inconsistent)
        assertEquals(GateVerdict.REJECT, decision.verdict)
    }

    @Test
    fun `resource evidence requires both latency and memory metrics`() {
        val missingMemory = ResourceBenchmark.ResourceReport(
            benchmarks = listOf(FitnessModel.BenchmarkOutcome("latency", "latency_ms", 12.0, "ms")),
            averageLatencyMs = 12.0,
            iterations = 20
        )
        val decision = gate.decide(evaluation(), listOf(baselinePassing), missingMemory)
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
        val decision = gate.decide(evaluation(benchmarks = slow.benchmarks), listOf(baselinePassing), slow)
        assertEquals(GateVerdict.MUTATE, decision.verdict)
    }
}
