package com.jarvis.app.selfrepair

import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.FinishReason
import com.jarvis.app.model.TokenUsage
import com.jarvis.app.mutant.AlgorithmSpec
import com.jarvis.app.mutant.DeterministicSpec
import com.jarvis.app.mutant.ResourceSandbox
import com.jarvis.app.mutant.SpecOutput
import com.jarvis.app.mutation.LlmSynthesisProvider
import com.jarvis.app.sandbox.LocalTestSandboxExecutor
import com.jarvis.app.validation.PromotionGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gate 2 proof: a deliberately-broken candidate (one that crashes on a
 * boundary input) is fed through the full SelfRepairLoop flow and confirmed
 * BLOCKED by the sandbox gate before it ever reaches [PromotionGate].
 *
 * The broken patch "fixes" the original failure but introduces a new crash
 * on empty inputs. Normal verification passes (the original failure is
 * fixed). The sandbox stress test catches the new crash via adversarial
 * inputs (empty list boundary) and returns [SelfRepairLoop.Outcome.SANDBOX_REJECTED]
 * — the patch is restored from backups without the gate ever evaluating it.
 */
class SandboxGateTest {

    private val tempDirs = mutableListOf<File>()
    private lateinit var scope: CoroutineScope

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
        if (::scope.isInitialized) scope.cancel()
    }

    private fun tmpDir(): File = File.createTempFile("sandbox-gate-test", "").apply {
        delete(); mkdirs(); tempDirs += this
    }

    // ------------------------------------------------------------------
    // Fixtures — same GoalPlanner as DiagnoseRepairIntegrationTest
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
        ROOT_CAUSE: replan removes the failed node from the plan graph; downstream consumers expect it retained as a FAILED record.
        FAULTY_UNIT: GoalPlanner.replan
        FIX_STRATEGY: Keep the failed node with FAILED status in the remaining list.
        RATIONALE: All failures trace to the missing failed node in the graph after replanning.
    """.trimIndent()

    /** A BROKEN repair: fixes the original failure but crashes on empty input. */
    private val brokenRepair = """
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
                val first = remaining.first()
                return PlannerResult(success = true, nodes = remaining)
            }
        ```
    """.trimIndent()

    /** A GOOD repair: fixes the original failure and handles empty input gracefully. */
    private val goodRepair = """
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
        val promoCtrl: com.jarvis.app.evolution.PromotionController,
        val rollbackCtrl: com.jarvis.app.evolution.RollbackController,
        val gate: PromotionGate
    )

    private fun harness(): Harness {
        val dir = tmpDir()
        val storage = FileStorage(dir)
        val archive = GenomeArchive(storage, scope)
        val registry = MicroSystemRegistry()
        val genomeRegistry = GenomeRegistry()
        val surface = FailureSurface()
        val promo = com.jarvis.app.evolution.PromotionController(registry, archive, genomeRegistry, surface)
        val rollback = com.jarvis.app.evolution.RollbackController(registry, archive, genomeRegistry, surface)
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

    /**
     * Build a SelfRepairLoop wired with a [LocalTestSandboxExecutor] whose
     * specProducer creates a DeterministicSpec that exhibits the SAME broken
     * behavior as the scripted repair: crashes on empty list input (the
     * `.first()` call on an empty list after the map).
     */
    private fun buildLoopWithSandbox(root: File, h: Harness): SelfRepairLoop {
        val diagnoser = DiagnosisEngine(
            complete = { _ ->
                GenerateResult(content = scriptedDiagnosis, finishReason = FinishReason.STOP,
                    usage = TokenUsage(promptTokens = 10, completionTokens = 50, totalTokens = 60))
            }
        )
        val synth = LlmSynthesisProvider(
            complete = { _ ->
                GenerateResult(content = brokenRepair, finishReason = FinishReason.STOP,
                    usage = TokenUsage(promptTokens = 10, completionTokens = 50, totalTokens = 60))
            }
        )

        // The specProducer creates a DeterministicSpec that mimics the broken
        // patch: the replan function crashes on empty list input (.first()
        // on empty list after map).
        val brokenSpec: AlgorithmSpec = DeterministicSpec(
            capability = "self_repair",
            description = "Retain the failed node as a FAILED record",
            source = brokenRepair,
            strategy = AlgorithmSpec.Strategy.DETERMINISTIC,
            fn = { input ->
                @Suppress("UNCHECKED_CAST")
                val nodes = (input["nodes"] as? List<String>) ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val failed = input["failed"] as? String ?: ""
                val remaining = nodes.map { if (it == failed) "\$it:FAILED" else it }
                // BUG: crashes on empty input — .first() on empty list
                val first = remaining.first()
                SpecOutput.Success(mapOf("nodes" to remaining, "first" to first))
            }
        )

        val sandbox = ResourceSandbox(defaultTimeoutMs = 1000)
        val sandboxExecutor = LocalTestSandboxExecutor(
            sandbox = sandbox,
            specProducer = { _ -> brokenSpec }
        )

        return SelfRepairLoop(
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
            rollbackController = h.rollbackCtrl,
            sandboxExecutor = sandboxExecutor
        )
    }

    // ------------------------------------------------------------------
    // Test
    // ------------------------------------------------------------------

    @Test
    fun `sandbox gate blocks broken candidate before PromotionGate`() = runBlocking {
        scope = CoroutineScope(Dispatchers.IO)
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val loop = buildLoopWithSandbox(root, h)

        val requirement = RepairRequirement(
            component = "cognitive/planning GoalPlanner.replan",
            requirement = "Replan must retain failed nodes as FAILED records",
            constraints = emptyList()
        )

        // Run the full SelfRepairLoop.repair() cycle:
        // 1. observeAndDiagnose: reads real XML failures
        // 2. proposeRepair: generates the broken patch via scripted LLM
        // 3. apply: writes the broken patch to GoalPlanner.kt
        // 4. verify: verifier returns empty (normal verification passes)
        // 5. conclude: sandbox stress test catches the broken spec BEFORE gate
        val result = loop.repair(requirement, "") { _ -> emptyList() }

        // The sandbox gate must catch the broken candidate
        assertEquals(SelfRepairLoop.Outcome.SANDBOX_REJECTED, result.outcome)

        // No receipt — the candidate never reached PromotionGate
        assertNull("no promotion receipt from sandbox rejection", result.receipt)

        // No gate decision — gate was never called
        assertNull("no gate decision from sandbox rejection", result.decision)

        // Patch was applied then restored from backups by the sandbox gate
        assertFalse("backup should be cleaned up after sandbox rollback",
            File(root, "com/example/planner/GoalPlanner.kt.selfrepair.bak").exists())
        assertEquals("source restored to original after sandbox rejection",
            miniPlannerSource,
            File(root, "com/example/planner/GoalPlanner.kt").readText())

        // The organism was NOT registered in the micro-system registry
        assertEquals("no organism should be registered after sandbox rejection",
            0, h.registry.count())
    }

    @Test
    fun `sandbox gate passes when candidate survives adversarial inputs`() = runBlocking {
        scope = CoroutineScope(Dispatchers.IO)
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()

        val diagnoser = DiagnosisEngine(
            complete = { _ ->
                GenerateResult(content = scriptedDiagnosis, finishReason = FinishReason.STOP,
                    usage = TokenUsage(promptTokens = 10, completionTokens = 50, totalTokens = 60))
            }
        )
        val synth = LlmSynthesisProvider(
            complete = { _ ->
                GenerateResult(content = goodRepair, finishReason = FinishReason.STOP,
                    usage = TokenUsage(promptTokens = 10, completionTokens = 50, totalTokens = 60))
            }
        )

        // A GOOD spec: handles empty input correctly (no crash)
        val goodSpec: AlgorithmSpec = DeterministicSpec(
            capability = "self_repair",
            description = "Retain the failed node as a FAILED record",
            source = scriptedDiagnosis,
            strategy = AlgorithmSpec.Strategy.DETERMINISTIC,
            fn = { input ->
                @Suppress("UNCHECKED_CAST")
                val nodes = (input["nodes"] as? List<String>) ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val failed = input["failed"] as? String ?: ""
                val remaining = nodes.map { if (it == failed) "\$it:FAILED" else it }
                // No crash: empty input returns empty list gracefully
                SpecOutput.Success(mapOf("nodes" to remaining))
            }
        )

        val sandbox = ResourceSandbox(defaultTimeoutMs = 1000)
        val sandboxExecutor = LocalTestSandboxExecutor(
            sandbox = sandbox,
            specProducer = { _ -> goodSpec }
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
            rollbackController = h.rollbackCtrl,
            sandboxExecutor = sandboxExecutor
        )

        val requirement = RepairRequirement(
            component = "cognitive/planning GoalPlanner.replan",
            requirement = "Replan must retain failed nodes",
            constraints = emptyList()
        )

        // The verifier returns a clean observation (all tests pass)
        // and the sandbox stress test passes (good spec handles all inputs)
        // → conclude() proceeds to the gate → gate PROMOTES
        val result = loop.repair(requirement, "") { _ ->
            listOf(TestSuiteObservation("CapabilityExecutorTest", 18, emptyList()))
        }

        // The sandbox gate passes; the gate promotes
        assertEquals(SelfRepairLoop.Outcome.PROMOTED, result.outcome)
        assertNotNull("receipt should exist after promotion", result.receipt)
        assertTrue("organism should be registered", h.registry.has(result.receipt!!.systemId))
    }

    @Test
    fun `SANDBOX_REJECTED outcome exists in the Outcome enum`() {
        val outcome = SelfRepairLoop.Outcome.SANDBOX_REJECTED
        assertEquals("SANDBOX_REJECTED", outcome.name)
    }
}
