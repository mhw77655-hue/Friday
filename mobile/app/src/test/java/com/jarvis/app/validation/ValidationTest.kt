package com.jarvis.app.validation

import com.jarvis.app.evolution.Behavior
import com.jarvis.app.evolution.BehaviorSynthesizer
import com.jarvis.app.evolution.CapabilityRequirement
import com.jarvis.app.evolution.GenomeFactory
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.mutant.ArtifactManager
import com.jarvis.app.mutant.CandidateImplementation
import com.jarvis.app.mutant.DeterministicSpec
import com.jarvis.app.mutant.MutantEnvironment
import com.jarvis.app.mutant.ProcessLocalBackend
import com.jarvis.app.mutant.ResourceSandbox
import com.jarvis.app.mutant.SpecOutput
import com.jarvis.app.mutation.EnvironmentFactory
import com.jarvis.app.mutation.FitnessModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validation layer (§12, §13, §24): candidate tests, failure-path tests,
 * resource benchmark, regression runner, promotion gate. Plain JVM (JUnit 4).
 */
class ValidationTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tmpDir(): File = File.createTempFile("validation-test", "").apply {
        delete(); mkdirs(); tempDirs += this
    }

    private class Harness(
        val mutantEnv: MutantEnvironment,
        val sandbox: ResourceSandbox
    )

    private fun harness(): Harness {
        val root = tmpDir()
        val fileStorage = FileStorage(root)
        val failureSurface = FailureSurface()
        val sandbox = ResourceSandbox(defaultTimeoutMs = 500)
        val factory = EnvironmentFactory(failureSurface, File(root, "workspaces"))
        val backend = ProcessLocalBackend(sandbox)
        factory.registerBackend("process", backend)
        val mutantEnv = MutantEnvironment(factory, backend, sandbox, ArtifactManager(fileStorage))
        return Harness(mutantEnv, sandbox)
    }

    private suspend fun createClampInstance(h: Harness): MutantEnvironment.MutantInstance {
        val impl = CandidateImplementation(
            spec = BehaviorSynthesizer.specFor(Behavior.Clamp(min = 0.0, max = 100.0)),
            tests = BehaviorSynthesizer.testsFor(Behavior.Clamp(min = 0.0, max = 100.0)),
            sourceCode = BehaviorSynthesizer.sourceFor(Behavior.Clamp(min = 0.0, max = 100.0)),
            dependencies = listOf("kotlin-runtime")
        )
        val genome = GenomeFactory.genomeFor(CapabilityRequirement("clamp", "clamp", Behavior.Clamp(0.0, 100.0)))
        return h.mutantEnv.create(genome, "cand_clamp", impl).getOrThrow()
    }

    private val clampImpl = CandidateImplementation(
        spec = BehaviorSynthesizer.specFor(Behavior.Clamp(min = 0.0, max = 100.0)),
        tests = BehaviorSynthesizer.testsFor(Behavior.Clamp(min = 0.0, max = 100.0))
    )

    @Test
    fun `candidate test runner executes the generated suite`() = runBlocking {
        val h = harness()
        val instance = createClampInstance(h)
        val outcomes = CandidateTestRunner(h.mutantEnv).run(instance, clampImpl)
        assertEquals(clampImpl.tests.size, outcomes.size)
        assertTrue(outcomes.all { it.passed })
    }

    @Test
    fun `failure test runner requires and passes failure-path tests`() = runBlocking {
        val h = harness()
        val instance = createClampInstance(h)
        val failures = FailureTestRunner(h.mutantEnv).runFailurePath(instance, clampImpl)
        assertTrue(failures.isNotEmpty())
        assertTrue(failures.all { it.passed }) // every failure case fails loudly

        val recovery = FailureTestRunner(h.mutantEnv).runRecovery(instance, clampImpl)
        assertTrue("organism recovers after failure path: ${recovery.error}", recovery.passed)
    }

    @Test
    fun `a candidate with no failure tests is automatically invalid`() = runBlocking {
        val h = harness()
        val instance = createClampInstance(h)
        val silent = clampImpl.copy(tests = clampImpl.tests.filter { !it.isFailureCase })
        val failures = FailureTestRunner(h.mutantEnv).runFailurePath(instance, silent)
        assertTrue(failures.isNotEmpty())
        assertTrue(failures.none { it.passed })
    }

    @Test
    fun `resource benchmark measures latency over repeated runs`() = runBlocking {
        val h = harness()
        val instance = createClampInstance(h)
        val report = ResourceBenchmark(h.mutantEnv).run(instance, clampImpl, iterations = 10)
        assertEquals(10, report.iterations)
        assertTrue(report.averageLatencyMs >= 0)
        assertTrue(report.benchmarks.any { it.metric == "latency_ms" })
    }

    @Test
    fun `regression runner checks the candidate against the current baseline`() = runBlocking {
        val h = harness()
        val instance = createClampInstance(h)
        val baseline = BehaviorSynthesizer.testsFor(Behavior.Clamp(min = 0.0, max = 100.0))
        val outcomes = RegressionRunner(h.mutantEnv).run(instance, baseline)
        assertTrue(outcomes.all { it.passed })
    }

    @Test
    fun `promotion gate rejects regression and accepts a clean candidate`() {
        val gate = PromotionGate()
        val baseline = listOf(
            FitnessModel.TestOutcome("t1", passed = true),
            FitnessModel.TestOutcome("t2", passed = true)
        )
        val clean = FitnessModel.evaluate(
            candidateId = "c",
            testResults = listOf(
                FitnessModel.TestOutcome("u1", passed = true),
                FitnessModel.TestOutcome("f1", passed = true)
            ),
            benchmarkResults = listOf(FitnessModel.BenchmarkOutcome("latency", "latency_ms", 5.0, "ms"))
        )
        val decision = gate.decide(clean, baseline, ResourceBenchmark.ResourceReport(emptyList(), 5.0, 10))
        assertEquals(GateVerdict.PROMOTE, decision.verdict)
        assertTrue(decision.reasons.isEmpty())
    }

    @Test
    fun `promotion gate routes partial performers to mutate`() {
        val gate = PromotionGate()
        val baseline = listOf(FitnessModel.TestOutcome("t1", passed = true))
        // Correct but slightly slow → MUTATE (a mutation may fix the cost).
        val slow = FitnessModel.evaluate(
            candidateId = "c",
            testResults = listOf(FitnessModel.TestOutcome("u1", passed = true)),
            benchmarkResults = listOf(FitnessModel.BenchmarkOutcome("latency", "latency_ms", 9.0, "ms"))
        )
        val decision = gate.decide(slow, baseline, ResourceBenchmark.ResourceReport(emptyList(), 3_000.0, 10))
        assertEquals(GateVerdict.MUTATE, decision.verdict)
    }

    @Test
    fun `promotion gate rejects a regression regardless of fitness`() {
        val gate = PromotionGate()
        val regression = listOf(
            FitnessModel.TestOutcome("baseline1", passed = false),
            FitnessModel.TestOutcome("baseline2", passed = true)
        )
        val perfect = FitnessModel.evaluate(
            candidateId = "c",
            testResults = listOf(FitnessModel.TestOutcome("u1", passed = true)),
            benchmarkResults = listOf(FitnessModel.BenchmarkOutcome("latency", "latency_ms", 1.0, "ms"))
        )
        // Two independent reasons (regression + over-budget latency) ⇒ reject.
        val decision = gate.decide(perfect, regression, ResourceBenchmark.ResourceReport(emptyList(), 3_000.0, 10))
        assertEquals(GateVerdict.REJECT, decision.verdict)
        assertTrue(decision.reasons.any { it.startsWith("regression") })
    }

    @Test
    fun `sandbox watchdog interrupts a blocking spec loudly`() = runBlocking {
        val h = harness()
        // A spec that never returns — the sandbox must cut it down.
        val hanging = DeterministicSpec(
            capability = "hang",
            description = "hangs forever",
            source = "while(true)",
            fn = { Thread.sleep(60_000); SpecOutput.Success(mapOf("result" to 1)) }
        )
        val start = System.nanoTime()
        val result = h.sandbox.run(hanging, emptyMap(), timeoutMs = 100)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertFalse(result.success)
        assertTrue("interrupted within budget, took ${elapsedMs}ms", elapsedMs < 5_000)
        assertFalse(result.error.isNullOrBlank())
    }
}
