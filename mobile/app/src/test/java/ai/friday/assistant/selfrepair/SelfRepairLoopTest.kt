package ai.friday.assistant.selfrepair

import ai.friday.assistant.failure.FailureSurface
import ai.friday.assistant.model.GenerateResult
import ai.friday.assistant.model.FinishReason
import ai.friday.assistant.model.TokenUsage
import ai.friday.assistant.mutation.LlmSynthesisProvider
import ai.friday.assistant.mutation.SynthesisRequest
import ai.friday.assistant.validation.PromotionGate
import ai.friday.assistant.mutation.FitnessModel
import ai.friday.assistant.validation.ResourceBenchmark
import ai.friday.assistant.sandbox.SandboxExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * End-to-end proof that the Observe / Diagnose / Repair cycle drives the
 * real PromotionGate and the source-patch restore path. Uses the REAL fixture
 * XML (shipped in test-resources) for observation, a temporary copy of a
 * mini-GoalPlanner file as the repair target, and scripted model transcripts
 * for diagnose / repair. It deliberately does not register a fake runtime tool.
 */
class SelfRepairLoopTest {

    private val tempDirs = mutableListOf<File>()
    private lateinit var scope: CoroutineScope

    @Before
    fun setup() {
        scope = CoroutineScope(Dispatchers.IO)
    }

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
        scope.cancel()
    }

    private fun tmpDir(): File = File.createTempFile("repair-loop-test", "").apply {
        delete(); mkdirs(); tempDirs += this
    }

    // ------------------------------------------------------------------
    // Wire real dependencies (same pattern as PromotionRollbackTest)
    // ------------------------------------------------------------------

    private data class Harness(
        val surface: FailureSurface,
        val gate: PromotionGate
    )

    private fun harness(): Harness = Harness(
        surface = FailureSurface(),
        gate = PromotionGate()
    )

    // ------------------------------------------------------------------
    // Fixtures — mini GoalPlanner.kt as the repair target
    // ------------------------------------------------------------------

    private val miniPlannerSource = """
        package com.example.planner
        data class PlannerResult(val success: Boolean, val nodes: List<String>)
        class GoalPlanner {
            fun replan(nodes: List<String>, failed: String): PlannerResult {
                val remaining = nodes.filter { it != failed }
                return PlannerResult(success = true, nodes = remaining)
            }
        }
    """.trimIndent() + "\n"

    private val repairedPlannerSource = """
        package com.example.planner
        data class PlannerResult(val success: Boolean, val nodes: List<String>)
        class GoalPlanner {
            fun replan(nodes: List<String>, failed: String): PlannerResult {
                val remaining = nodes.map { if (it == failed) "${"$"}it:FAILED" else it }
                return PlannerResult(success = true, nodes = remaining)
            }
        }
    """.trimIndent() + "\n"

    private val scriptedDiagnosis = """
        ROOT_CAUSE: replan removes the failed node; consumers expect it retained.
        FAULTY_UNIT: GoalPlanner.replan
        FIX_STRATEGY: Keep the failed node with FAILED status in the remaining list.
        RATIONALE: All failures trace to NoSuchElementException caused by missing nodes.
    """.trimIndent()

    private val scriptedRepair = """
        RATIONALE: Retain failed node as FAILED record.
        ```patch
        FILE: com/example/planner/GoalPlanner.kt
        FIND:
            fun replan(nodes: List<String>, failed: String): PlannerResult {
                val remaining = nodes.filter { it != failed }
                return PlannerResult(success = true, nodes = remaining)
            }
        REPLACE:
            fun replan(nodes: List<String>, failed: String): PlannerResult {
                val remaining = nodes.map { if (it == failed) "${"$"}it:FAILED" else it }
                return PlannerResult(success = true, nodes = remaining)
            }
        ```
    """.trimIndent()

    private val observationFixture = TestSuiteObservation(
        suiteName = "CapabilityExecutorTest",
        totalTests = 18,
        failures = listOf(
            TestFailureObservation("CapabilityExecutorTest", "non-recoverable", "NoSuchElementException", "no match", "trace"),
            TestFailureObservation("CapabilityExecutorTest", "replan preserves", "AssertionError", "expected:<4> but was:<3>", "trace")
        )
    )

    private fun allPassObservation() = TestSuiteObservation("CapabilityExecutorTest", 18, emptyList())

    private fun baselinePassObservation() = TestSuiteObservation("BaselineSuite", 18, emptyList())

    private fun failingObservation() = TestSuiteObservation(
        "CapabilityExecutorTest", 18, listOf(
            TestFailureObservation("CapabilityExecutorTest", "re-recur", "AssertionError", "still broken", "trace")
        )
    )

    private fun baselineFailingObservation() = failingObservation().copy(suiteName = "BaselineSuite")

    private fun validResourceReport() = ResourceBenchmark.ResourceReport(
        benchmarks = listOf(
            FitnessModel.BenchmarkOutcome("latency", "latency_ms", 12.0, "ms"),
            FitnessModel.BenchmarkOutcome("memory", "memory_mb", 1.0, "mb")
        ),
        averageLatencyMs = 12.0,
        iterations = 20
    )

    private fun validEvidence(
        candidateSuites: List<TestSuiteObservation> = listOf(allPassObservation()),
        regressionSuites: List<TestSuiteObservation> = listOf(baselinePassObservation()),
        resourceReport: ResourceBenchmark.ResourceReport = validResourceReport()
    ) = RepairVerificationEvidence(candidateSuites, regressionSuites, resourceReport)

    private fun failingEvidence() = validEvidence(
        regressionSuites = listOf(baselineFailingObservation())
    )

    private fun emptyEvidence() = RepairVerificationEvidence(
        candidateSuites = emptyList(),
        regressionSuites = emptyList(),
        resourceReport = ResourceBenchmark.ResourceReport(emptyList(), 0.0, 0)
    )

    // ------------------------------------------------------------------
    // Loop constructor
    // ------------------------------------------------------------------

    private val passingSandbox = object : SandboxExecutor {
        override suspend fun stressTest(
            patch: SourcePatch,
            candidateDescription: String
        ) = SandboxExecutor.StressResult(passed = true, log = listOf("explicit test-only sandbox stub"))
    }

    private fun loopWith(
        tmpRoot: File,
        h: Harness,
        diagText: String = scriptedDiagnosis,
        repairText: String = scriptedRepair,
        sandbox: SandboxExecutor = passingSandbox
    ): Pair<SelfRepairLoop, LlmSynthesisProvider> {
        var callCount = 0
        val diagnoser = DiagnosisEngine(
            complete = { _ ->
                GenerateResult(content = diagText, finishReason = FinishReason.STOP, usage = TokenUsage(promptTokens = 10, completionTokens = 50, totalTokens = 60))
            }
        )
        val synth = LlmSynthesisProvider(
            complete = { req ->
                callCount++
                val content = if (callCount == 1) repairText else "```synth\nresult = 0\n```"
                GenerateResult(content = content, finishReason = FinishReason.STOP, usage = TokenUsage(promptTokens = 10, completionTokens = 50, totalTokens = 60))
            }
        )
        val loop = SelfRepairLoop(
            repoRoot = tmpRoot,
            observer = GradleXmlFailureObserver(tmpRoot.resolve("results")),
            diagnoser = diagnoser,
            synthesizer = synth,
            gate = h.gate,
            failureSurface = h.surface,
            sandboxExecutor = sandbox
        )
        return loop to synth
    }

    private fun writePlannerFixture(root: File) {
        val dir = root.resolve("com/example/planner")
        dir.mkdirs()
        dir.resolve("GoalPlanner.kt").writeText(miniPlannerSource)
    }

    private fun copyXmlFixture(root: File) {
        val src = javaClass.classLoader
            .getResource("selfrepair/TEST-CapabilityExecutorTest.xml")!!
            .toURI().let { File(it) }
        val dstDir = root.resolve("results").apply { mkdirs() }
        src.copyTo(dstDir.resolve("TEST-CapabilityExecutorTest.xml"), overwrite = true)
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `NOTHING_TO_REPAIR when no failures observed`() = runBlocking {
        val root = tmpDir()
        val h = harness()
        val (loop, _) = loopWith(root, h)
        // empty results dir → observer throws; put an empty fixture instead
        root.resolve("results").mkdirs()
        root.resolve("results/TEST-clean.xml").writeText(
            """<?xml version="1.0"?><testsuite name="clean" tests="1" failures="0"><testcase classname="C" name="a"/></testsuite>"""
        )
        val result = loop.repair(
            RepairRequirement("com", "req", emptyList()),
            "clean"
        ) { _ -> emptyEvidence() }
        assertEquals(SelfRepairLoop.Outcome.NOTHING_TO_REPAIR, result.outcome)
    }

    @Test
    fun `empty verification restores the applied patch`() = runBlocking {
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val (loop, _) = loopWith(root, h)

        val result = loop.repair(
            RepairRequirement("GoalPlanner", "Retain failed nodes", emptyList()),
            "CapabilityExecutorTest"
        ) { _ -> emptyEvidence() }

        assertEquals(SelfRepairLoop.Outcome.VALIDATION_FAILED, result.outcome)
        assertEquals(miniPlannerSource, File(root, "com/example/planner/GoalPlanner.kt").readText())
        assertFalse(File(root, "com/example/planner/GoalPlanner.kt.selfrepair.${result.patch!!.transactionId}.bak").exists())
        assertTrue(h.surface.currentFailures.value.any { it.subsystem == "SELF_REPAIR" })
    }

    @Test
    fun `direct conclude with invalid evidence restores the applied patch`() = runBlocking {
        val root = tmpDir()
        writePlannerFixture(root)
        val h = harness()
        val (loop, _) = loopWith(root, h)
        val requirement = RepairRequirement("GoalPlanner", "Retain failed nodes", emptyList())
        val diagnosis = RepairDiagnosis(
            rootCause = "failed node is discarded",
            faultyUnit = "GoalPlanner.replan",
            fixStrategy = "retain failed node as FAILED",
            rationale = "preserve the failure record"
        )
        val patch = loop.proposeRepair(requirement, diagnosis)
        loop.apply(patch)

        val result = loop.conclude(requirement, diagnosis, patch, emptyEvidence())

        assertEquals(SelfRepairLoop.Outcome.VALIDATION_FAILED, result.outcome)
        assertEquals(miniPlannerSource, File(root, "com/example/planner/GoalPlanner.kt").readText())
        assertFalse(File(root, "com/example/planner/GoalPlanner.kt.selfrepair.${patch.transactionId}.bak").exists())
    }

    @Test
    fun `verifier exception restores the applied patch`() = runBlocking {
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val (loop, _) = loopWith(root, h)

        val result = loop.repair(
            RepairRequirement("GoalPlanner", "Retain failed nodes", emptyList()),
            "CapabilityExecutorTest"
        ) { throw IllegalStateException("Gradle verification unavailable") }

        assertEquals(SelfRepairLoop.Outcome.VALIDATION_FAILED, result.outcome)
        assertEquals(miniPlannerSource, File(root, "com/example/planner/GoalPlanner.kt").readText())
        assertFalse(File(root, "com/example/planner/GoalPlanner.kt.selfrepair.${result.patch!!.transactionId}.bak").exists())
        assertTrue(h.surface.currentFailures.value.any { it.subsystem == "SELF_REPAIR" })
    }


    @Test
    fun `sandbox exception restores patch and prevents promotion`() = runBlocking {
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val brokenSandbox = object : SandboxExecutor {
            override suspend fun stressTest(
                patch: SourcePatch,
                candidateDescription: String
            ): SandboxExecutor.StressResult {
                throw IllegalStateException("sandbox unavailable")
            }
        }
        val (loop, _) = loopWith(root, h, sandbox = brokenSandbox)

        val result = loop.repair(
            RepairRequirement("GoalPlanner", "Retain failed nodes", emptyList()),
            "CapabilityExecutorTest"
        ) { _ -> validEvidence() }

        assertEquals(SelfRepairLoop.Outcome.VALIDATION_FAILED, result.outcome)
        assertEquals(miniPlannerSource, File(root, "com/example/planner/GoalPlanner.kt").readText())
        assertFalse(File(root, "com/example/planner/GoalPlanner.kt.selfrepair.${result.patch!!.transactionId}.bak").exists())
        assertTrue(h.surface.currentFailures.value.any { it.subsystem == "SELF_REPAIR" })
    }

    @Test
    fun `full cycle returns a verified source patch without fake runtime registration`() = runBlocking {
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val (loop, _) = loopWith(root, h)

        val result = loop.repair(
            RepairRequirement("GoalPlanner", "Retain failed nodes", emptyList()),
            "CapabilityExecutorTest"
        ) { _ -> validEvidence() }

        assertEquals(SelfRepairLoop.Outcome.PATCH_VERIFIED, result.outcome)
        assertNotNull(result.diagnosis)
        assertNotNull(result.patch)
        assertEquals("GoalPlanner.replan", result.diagnosis!!.faultyUnit)
        assertTrue(result.patch!!.edits.isNotEmpty())

        // patch actually written
        assertTrue(File(root, "com/example/planner/GoalPlanner.kt").readText().contains("FAILED"))
        // backup exists
        assertTrue(File(root, "com/example/planner/GoalPlanner.kt.selfrepair.${result.patch!!.transactionId}.bak").exists())

        // Source repair is not a micro-system capability. No fake runtime
        // organism is registered by this tool.
        assertTrue(result.log.any { it.contains("sourcePatch: VERIFIED") })
    }

    @Test
    fun `ROLLBACK restores backups and reports on failure surface`() = runBlocking {
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val (loop, _) = loopWith(root, h)

        val result = loop.repair(
            RepairRequirement("GoalPlanner", "Retain failed nodes", emptyList()),
            "CapabilityExecutorTest"
        ) { _ -> failingEvidence() }

        assertEquals(SelfRepairLoop.Outcome.ROLLED_BACK, result.outcome)
        // patch restored from backup
        assertEquals(miniPlannerSource, File(root, "com/example/planner/GoalPlanner.kt").readText())
        assertFalse(File(root, "com/example/planner/GoalPlanner.kt.selfrepair.${result.patch!!.transactionId}.bak").exists())
        // failure surface warning recorded
        assertTrue(h.surface.currentFailures.value.any { it.subsystem == "SELF_REPAIR" })
    }

    @Test
    fun `conclude restores a previously verified source patch after regression`() = runBlocking {
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val (loop, _) = loopWith(root, h)

        // Verify first; the source patch remains applied pending trusted repository promotion.
        val promoted = loop.repair(
            RepairRequirement("GoalPlanner", "Retain failed nodes", emptyList()),
            "CapabilityExecutorTest"
        ) { _ -> validEvidence() }
        assertEquals(SelfRepairLoop.Outcome.PATCH_VERIFIED, promoted.outcome)

        // now simulate a regression
        val regression = loop.conclude(
            RepairRequirement("GoalPlanner", "Retain failed nodes", emptyList()),
            promoted.diagnosis!!,
            promoted.patch!!,
            failingEvidence()
        )
        assertEquals(SelfRepairLoop.Outcome.ROLLED_BACK, regression.outcome)
        // The source file is restored from its backup; no runtime registry
        // promotion or rollback is involved.
        assertEquals(miniPlannerSource, File(root, "com/example/planner/GoalPlanner.kt").readText())
    }

    @Test
    fun `proposeRepair validates against tree before applying`() = runBlocking {
        val root = tmpDir()
        val h = harness()
        val (loop, _) = loopWith(root, h)
        val diagnosis = RepairDiagnosis(
            rootCause = "removed node",
            faultyUnit = "Planner.replan",
            fixStrategy = "keep node",
            rationale = "all failures"
        )
        // no fixture file exists → validation must throw
        try {
            loop.proposeRepair(
                RepairRequirement("Planner", "retain", emptyList()),
                diagnosis
            )
            throw AssertionError("should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("validation failed"))
        }
    }
}
