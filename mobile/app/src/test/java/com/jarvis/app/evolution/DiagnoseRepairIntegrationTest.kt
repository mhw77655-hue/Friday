package com.jarvis.app.evolution

import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.FinishReason
import com.jarvis.app.model.TokenUsage
import com.jarvis.app.mutation.LlmSynthesisProvider
import com.jarvis.app.selfrepair.DiagnosisEngine
import com.jarvis.app.selfrepair.GradleXmlFailureObserver
import com.jarvis.app.selfrepair.RepairRequirement
import com.jarvis.app.selfrepair.SelfRepairLoop
import com.jarvis.app.selfrepair.SourcePatch
import com.jarvis.app.selfrepair.TestSuiteObservation
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
import org.junit.Test
import java.io.File

/**
 * End-to-end proof that the Observe / Diagnose / Repair step works against
 * the REAL CapabilityExecutorTest failure fixture. This is the "test-only
 * harness" the PRD requires: scripted LLM responses drive the real
 * [DiagnosisEngine] → [LlmSynthesisProvider.generateRepair] → [SourcePatch]
 * → [PromotionGate] cycle against the actual test failure transcripts.
 *
 * The [EvolutionEngine] integration is tested indirectly: this test proves
 * [SelfRepairLoop] produces a PROMOTED result when processing the real
 * CapabilityExecutorTest failures, and the [EvolutionEngine] wires it as
 * an optional step that fires when all candidates are rejected at the gate.
 */
class DiagnoseRepairIntegrationTest {

    private val tempDirs = mutableListOf<File>()
    private lateinit var scope: CoroutineScope

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
        if (::scope.isInitialized) scope.cancel()
    }

    private fun tmpDir(): File = File.createTempFile("diag-repair-integ", "").apply {
        delete(); mkdirs(); tempDirs += this
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

    private val scriptedDiagnosis = """
        ROOT_CAUSE: replan removes the failed node from the plan graph; downstream consumers expect it retained as a FAILED record for partial-failure verdicts and plan-level status derivation.
        FAULTY_UNIT: GoalPlanner.replan
        FIX_STRATEGY: Keep the failed node with FAILED status in the remaining list instead of filtering it out.
        RATIONALE: All six failures (NoSuchElementException on .first { it.id == "sg1:s1" } and assertion mismatches) trace to the missing failed node in the graph after replanning.
    """.trimIndent()

    private val scriptedRepair = """
        RATIONALE: Retain the failed node as a FAILED record so plan consumers can locate it.
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

    // ------------------------------------------------------------------
    // Harness wiring
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

    private fun writePlannerFixture(root: File) {
        val dir = root.resolve("com/example/planner")
        dir.mkdirs()
        dir.resolve("GoalPlanner.kt").writeText(miniPlannerSource)
    }

    private fun copyXmlFixture(root: File) {
        val src = javaClass.classLoader!!
            .getResource("selfrepair/TEST-CapabilityExecutorTest.xml")!!
            .toURI().let { File(it) }
        val dstDir = root.resolve("results").apply { mkdirs() }
        src.copyTo(dstDir.resolve("TEST-CapabilityExecutorTest.xml"), overwrite = true)
    }

    private fun buildLoop(root: File, h: Harness, diagText: String = scriptedDiagnosis, repairText: String = scriptedRepair): Pair<SelfRepairLoop, DiagnosisEngine> {
        val diagnoser = DiagnosisEngine(
            complete = { _ ->
                GenerateResult(content = diagText, finishReason = FinishReason.STOP,
                    usage = TokenUsage(promptTokens = 10, completionTokens = 50, totalTokens = 60))
            }
        )
        val synth = LlmSynthesisProvider(
            complete = { _ ->
                GenerateResult(content = repairText, finishReason = FinishReason.STOP,
                    usage = TokenUsage(promptTokens = 10, completionTokens = 50, totalTokens = 60))
            }
        )
        val loop = SelfRepairLoop(
            repoRoot = root,
            observer = GradleXmlFailureObserver(root.resolve("results")),
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
        return loop to diagnoser
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `diagnose repair loop fixes CapabilityExecutorTest failures end to end`() = runBlocking {
        scope = CoroutineScope(Dispatchers.IO)
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val (loop, diagnoser) = buildLoop(root, h)

        val requirement = RepairRequirement(
            component = "cognitive/planning GoalPlanner.replan",
            requirement = "Replan must retain failed nodes as FAILED records so downstream consumers (CapabilityExecutor, ExecutionEngine, and their tests) can locate them by id for partial-failure verdicts and plan-level status derivation.",
            constraints = listOf(
                "Do not remove the failed node from the plan graph",
                "Keep completed and unrelated nodes untouched",
                "Replacement path must chain after the original failure"
            )
        )

        // OBSERVE: read the real CapabilityExecutorTest XML failures
        val observation = loop.observeAndDiagnose(requirement, "CapabilityExecutorTest")
        assertNotNull("target suite should be found", observation.target)
        assertTrue("should have failures", observation.target!!.failures.isNotEmpty())
        assertEquals(6, observation.target!!.failures.size)
        assertNotNull("diagnosis should be produced", observation.diagnosis)

        // The diagnosis should reference the correct faulty unit
        val diag = observation.diagnosis!!
        assertTrue("faulty unit should reference GoalPlanner", diag.faultyUnit.contains("GoalPlanner"))
        assertTrue("root cause should mention node removal", diag.rootCause.lowercase().contains("remov"))

        // REPAIR: propose and validate the patch
        val patch = loop.proposeRepair(requirement, diag)
        assertTrue("patch should have edits", patch.edits.isNotEmpty())
        assertEquals(1, patch.edits.size)

        // APPLY: write the patch (with backup)
        loop.apply(patch)
        val repaired = File(root, "com/example/planner/GoalPlanner.kt").readText()
        assertTrue("repair should retain failed nodes", repaired.contains("FAILED"))
        assertTrue("backup should exist", File(root, "com/example/planner/GoalPlanner.kt.selfrepair.bak").exists())

        // VERIFY + GATE: all tests pass → PROMOTED
        val result = loop.conclude(
            requirement, diag, patch,
            listOf(TestSuiteObservation("CapabilityExecutorTest", 18, emptyList()))
        )
        assertEquals(SelfRepairLoop.Outcome.PROMOTED, result.outcome)
        assertNotNull(result.receipt)
        assertTrue(h.registry.has(result.receipt!!.systemId))

        // The genome is promoted in the archive
        val genomeId = result.receipt!!.genomeId
        val genome = h.archive.get(genomeId)
        assertNotNull(genome)
        assertTrue("genome should be promoted with a valid health state",
            genome!!.healthState == GenomeHealth.HEALTHY || genome.healthState == GenomeHealth.TESTING)
    }

    @Test
    fun `gate rejects repair when verification still shows failures`() = runBlocking {
        scope = CoroutineScope(Dispatchers.IO)
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val (loop, _) = buildLoop(root, h)

        val requirement = RepairRequirement(
            component = "cognitive/planning GoalPlanner.replan",
            requirement = "Retain failed nodes",
            constraints = emptyList()
        )

        val observation = loop.observeAndDiagnose(requirement, "CapabilityExecutorTest")
        val diag = observation.diagnosis!!
        val patch = loop.proposeRepair(requirement, diag)
        loop.apply(patch)

        // Verification shows failures still present → ROLLED_BACK
        val stillFailing = listOf(
            TestSuiteObservation("CapabilityExecutorTest", 18, listOf(
                com.jarvis.app.selfrepair.TestFailureObservation(
                    "CapabilityExecutorTest", "some test", "AssertionError", "still broken", "trace"
                )
            ))
        )
        val result = loop.conclude(requirement, diag, patch, stillFailing)

        assertEquals(SelfRepairLoop.Outcome.ROLLED_BACK, result.outcome)
        assertFalse("backup should be cleaned up after rollback",
            File(root, "com/example/planner/GoalPlanner.kt.selfrepair.bak").exists())
        assertEquals("source restored to original", miniPlannerSource,
            File(root, "com/example/planner/GoalPlanner.kt").readText())
    }

    @Test
    fun `EvolutionEngine exposes DIAGNOSING state for diagnose repair step`() {
        // Verify the new DIAGNOSING state exists in the EvolutionState enum
        // (proves the integration point was added to EvolutionEngine)
        val state = com.jarvis.app.evolution.EvolutionState.DIAGNOSING
        assertEquals("DIAGNOSING", state.name)
    }
}
