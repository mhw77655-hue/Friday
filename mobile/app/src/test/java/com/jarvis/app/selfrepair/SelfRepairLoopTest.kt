package com.jarvis.app.selfrepair

import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.FinishReason
import com.jarvis.app.model.TokenUsage
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.mutation.LlmSynthesisProvider
import com.jarvis.app.mutation.SynthesisRequest
import com.jarvis.app.evolution.PromotionController
import com.jarvis.app.evolution.RollbackController
import com.jarvis.app.validation.PromotionGate
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
 * real PromotionGate and RollbackController. Uses the REAL fixture XML
 * (shipped in test-resources) for the observe step, a temporary-copy of a
 * mini-GoalPlanner file as the repair target, and scripted model transcripts
 * for the diagnose / repair steps.
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
        val archive: GenomeArchive,
        val registry: MicroSystemRegistry,
        val genomeRegistry: GenomeRegistry,
        val surface: FailureSurface,
        val promoCtrl: PromotionController,
        val rollbackCtrl: RollbackController,
        val gate: PromotionGate
    )

    private fun harness(): Harness {
        val dir = tmpDir()
        val storage = FileStorage(dir)
        val archive = GenomeArchive(storage, scope)
        val registry = MicroSystemRegistry()
        val genomeRegistry = GenomeRegistry()
        val surface = FailureSurface()
        val promo = PromotionController(registry, archive, genomeRegistry, surface)
        val rollback = RollbackController(registry, archive, genomeRegistry, surface)
        val gate = PromotionGate()
        return Harness(archive, registry, genomeRegistry, surface, promo, rollback, gate)
    }

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

    private fun failingObservation() = TestSuiteObservation(
        "CapabilityExecutorTest", 18, listOf(
            TestFailureObservation("CapabilityExecutorTest", "re-recur", "AssertionError", "still broken", "trace")
        )
    )

    // ------------------------------------------------------------------
    // Loop constructor
    // ------------------------------------------------------------------

    private fun loopWith(tmpRoot: File, h: Harness, diagText: String = scriptedDiagnosis, repairText: String = scriptedRepair): Pair<SelfRepairLoop, LlmSynthesisProvider> {
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
            archive = h.archive,
            registry = h.registry,
            genomeRegistry = h.genomeRegistry,
            failureSurface = h.surface,
            promotionController = h.promoCtrl,
            rollbackController = h.rollbackCtrl
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
            """<?xml version="1.0"?><testsuite name="clean" tests="5" failures="0"><testcase classname="C" name="a"/></testsuite>"""
        )
        val result = loop.repair(
            RepairRequirement("com", "req", emptyList()),
            "clean"
        ) { _ -> emptyList() }
        assertEquals(SelfRepairLoop.Outcome.NOTHING_TO_REPAIR, result.outcome)
    }

    @Test
    fun `full cycle produces PROMOTED and organism is registered`() = runBlocking {
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val (loop, _) = loopWith(root, h)

        val result = loop.repair(
            RepairRequirement("GoalPlanner", "Retain failed nodes", emptyList()),
            "CapabilityExecutorTest"
        ) { _ -> listOf(allPassObservation()) }

        assertEquals(SelfRepairLoop.Outcome.PROMOTED, result.outcome)
        assertNotNull(result.diagnosis)
        assertNotNull(result.patch)
        assertEquals("GoalPlanner.replan", result.diagnosis!!.faultyUnit)
        assertTrue(result.patch!!.edits.isNotEmpty())
        assertNotNull(result.receipt)

        // patch actually written
        assertTrue(File(root, "com/example/planner/GoalPlanner.kt").readText().contains("FAILED"))
        // backup exists
        assertTrue(File(root, "com/example/planner/GoalPlanner.kt.selfrepair.bak").exists())

        // genome promoted and organism registered
        assertTrue(h.registry.has(result.receipt!!.systemId))
        assertTrue(h.archive.get(result.receipt.genomeId) != null)
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
        ) { _ -> listOf(failingObservation()) }

        assertEquals(SelfRepairLoop.Outcome.ROLLED_BACK, result.outcome)
        // patch restored from backup
        assertEquals(miniPlannerSource, File(root, "com/example/planner/GoalPlanner.kt").readText())
        assertFalse(File(root, "com/example/planner/GoalPlanner.kt.selfrepair.bak").exists())
        // failure surface warning recorded
        assertTrue(h.surface.currentFailures.value.any { it.subsystem == "SELF_REPAIR" })
    }

    @Test
    fun `conclude can rollback a previously promoted repair on regression`() = runBlocking {
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val (loop, _) = loopWith(root, h)

        // promote first
        val promoted = loop.repair(
            RepairRequirement("GoalPlanner", "Retain failed nodes", emptyList()),
            "CapabilityExecutorTest"
        ) { _ -> listOf(allPassObservation()) }
        assertEquals(SelfRepairLoop.Outcome.PROMOTED, promoted.outcome)
        val receipt = promoted.receipt!!
        assertTrue(h.registry.has(receipt.systemId))

        // now simulate a regression
        val regression = loop.conclude(
            RepairRequirement("GoalPlanner", "Retain failed nodes", emptyList()),
            promoted.diagnosis!!,
            promoted.patch!!,
            listOf(failingObservation())
        )
        assertEquals(SelfRepairLoop.Outcome.ROLLED_BACK, regression.outcome)
        // organism unregistered by rollback controller
        assertFalse(h.registry.has(receipt.systemId))
        assertEquals(GenomeHealth.REJECTED, h.archive.get(receipt.genomeId)?.healthState)
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
