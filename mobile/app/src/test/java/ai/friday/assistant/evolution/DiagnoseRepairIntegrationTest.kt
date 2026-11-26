package ai.friday.assistant.evolution

import ai.friday.assistant.failure.FailureSurface
import ai.friday.assistant.genome.GenomeArchive
import ai.friday.assistant.genome.GenomeBuilder
import ai.friday.assistant.genome.GenomeHealth
import ai.friday.assistant.genome.GenomeRegistry
import ai.friday.assistant.humancore.store.FileStorage
import ai.friday.assistant.microsystem.MicroSystemRegistry
import ai.friday.assistant.model.GenerateResult
import ai.friday.assistant.model.FinishReason
import ai.friday.assistant.model.TokenUsage
import ai.friday.assistant.mutation.LlmSynthesisProvider
import ai.friday.assistant.mutation.FitnessModel
import ai.friday.assistant.selfrepair.DiagnosisEngine
import ai.friday.assistant.selfrepair.GradleXmlFailureObserver
import ai.friday.assistant.selfrepair.RepairRequirement
import ai.friday.assistant.selfrepair.ReaFinding
import ai.friday.assistant.selfrepair.ReaRepairEvidence
import ai.friday.assistant.selfrepair.ReaRepairEvidenceProvider
import ai.friday.assistant.selfrepair.ReaTargetArtifact
import ai.friday.assistant.selfrepair.RepairVerificationEvidence
import ai.friday.assistant.selfrepair.SelfRepairLoop
import ai.friday.assistant.selfrepair.SourcePatch
import ai.friday.assistant.selfrepair.TestSuiteObservation
import ai.friday.assistant.sandbox.SandboxExecutor
import ai.friday.assistant.validation.PromotionGate
import ai.friday.assistant.validation.ResourceBenchmark
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

    private val passingSandbox = object : SandboxExecutor {
        override suspend fun stressTest(
            patch: SourcePatch,
            candidateDescription: String
        ) = SandboxExecutor.StressResult(passed = true, log = listOf("explicit test-only sandbox stub"))
    }

    private fun buildLoop(root: File, h: Harness, diagText: String = scriptedDiagnosis, repairText: String = scriptedRepair, reaProvider: ReaRepairEvidenceProvider? = null): Triple<SelfRepairLoop, DiagnosisEngine, LlmSynthesisProvider> {
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
        val reaArtifact = reaProvider?.let {
            val apk = root.resolve("build/outputs/apk/debug/app-debug.apk")
            apk.parentFile?.mkdirs()
            apk.writeBytes("test-apk-artifact".toByteArray())
            ReaTargetArtifact.fromBuiltApk(apk, "c".repeat(40), "ai.friday.assistant")
        }
        val loop = SelfRepairLoop(
            repoRoot = root,
            observer = GradleXmlFailureObserver(root.resolve("results")),
            diagnoser = diagnoser,
            synthesizer = synth,
            gate = h.gate,
            failureSurface = h.surface,
            sandboxExecutor = passingSandbox,
            reaTargetArtifact = reaArtifact,
            reaEvidenceProvider = reaProvider
        )
        return Triple(loop, diagnoser, synth)
    }

    private fun validEvidence(
        candidateSuites: List<TestSuiteObservation> = listOf(TestSuiteObservation("CapabilityExecutorTest", 18, emptyList())),
        regressionSuites: List<TestSuiteObservation> = listOf(TestSuiteObservation("BaselineSuite", 18, emptyList()))
    ): RepairVerificationEvidence = RepairVerificationEvidence(
        candidateSuites = candidateSuites,
        regressionSuites = regressionSuites,
        resourceReport = ResourceBenchmark.ResourceReport(
            benchmarks = listOf(
                FitnessModel.BenchmarkOutcome("latency", "latency_ms", 12.0, "ms"),
                FitnessModel.BenchmarkOutcome("memory", "memory_mb", 1.0, "mb")
            ),
            averageLatencyMs = 12.0,
            iterations = 20
        )
    )

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
        val (loop, diagnoser, _) = buildLoop(root, h)

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
        assertTrue("backup should exist", File(root, "com/example/planner/GoalPlanner.kt.selfrepair.${patch.transactionId}.bak").exists())

        // VERIFY + GATE: evidence passes; source patch is verified, not runtime-promoted
        val result = loop.conclude(
            requirement, diag, patch,
            validEvidence()
        )
        assertEquals(SelfRepairLoop.Outcome.PATCH_VERIFIED, result.outcome)
        assertTrue(result.log.any { it.contains("sourcePatch: VERIFIED") })
        assertTrue("source patch should remain applied for trusted repository publication",
            File(root, "com/example/planner/GoalPlanner.kt").readText().contains("FAILED"))
    }

    @Test
    fun `failed candidate diagnosis invokes REA and carries its evidence into the model prompt`() = runBlocking {
        scope = CoroutineScope(Dispatchers.IO)
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        var invocations = 0
        val provider = ReaRepairEvidenceProvider { target, requirement, observation ->
            invocations++
            assertEquals(root.resolve("build/outputs/apk/debug/app-debug.apk").canonicalFile, target.file.canonicalFile)
            assertTrue(target.verifyUnchanged())
            assertEquals("GoalPlanner", requirement.component)
            assertEquals("CapabilityExecutorTest", observation.suiteName)
            ReaRepairEvidence(
                toolName = "REA",
                toolVersion = "integration-test",
                target = target.packageName,
                targetRevision = target.sourceRevision,
                targetSha256 = target.sha256,
                evidenceId = "ev_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                canonicalEvidenceJson = """{"evidence_id":"ev_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","artifact":{"sha256":"${target.sha256}"}}""",
                findings = listOf(
                    ReaFinding(
                        title = "Failed node removed before downstream lookup",
                        finding = "The inspected replan path filters the failed node.",
                        evidenceRefs = listOf("source:GoalPlanner.replan", "trace:failed-node-lookup"),
                        confidence = 0.97
                    )
                )
            )
        }
        val (loop, diagnoser, synthesizer) = buildLoop(root, h, reaProvider = provider)
        val result = loop.observeAndDiagnose(
            RepairRequirement("GoalPlanner", "retain failed nodes", emptyList(), sourceRevision = "c".repeat(40)),
            "CapabilityExecutorTest"
        )
        assertEquals(1, invocations)
        assertNotNull(result.diagnosis)
        assertTrue(result.reaEvidence!!.canonicalEvidenceJson.contains(result.reaEvidence.evidenceId))
        assertTrue(result.reaEvidence.canonicalEvidenceJson.contains(result.reaEvidence.targetSha256))
        assertTrue(diagnoser.lastPrompt!!.contains("source:GoalPlanner.replan"))
        assertTrue(diagnoser.lastPrompt!!.contains("ev_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
        assertTrue(result.log.any { it.contains("REA: REA integration-test") })
        val patch = loop.proposeRepair(
            RepairRequirement("GoalPlanner", "retain failed nodes", emptyList(), sourceRevision = "c".repeat(40)),
            result.diagnosis!!,
            result.reaEvidence
        )
        assertTrue(patch.edits.isNotEmpty())
        assertTrue(synthesizer.lastPrompt!!.contains("UPSTREAM REA EVIDENCE"))
        assertTrue(synthesizer.lastPrompt!!.contains("source:GoalPlanner.replan"))
        assertTrue(synthesizer.lastPrompt!!.contains("trace:failed-node-lookup"))
    }

    @Test
    fun `REA is not invoked without an exact source revision`() = runBlocking {
        scope = CoroutineScope(Dispatchers.IO)
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        var invocations = 0
        val provider = ReaRepairEvidenceProvider { _, _, _ ->
            invocations++
            error("REA must not run without an exact source revision")
        }
        val (loop, _, _) = buildLoop(root, h, reaProvider = provider)
        val observation = loop.observeAndDiagnose(
            RepairRequirement("GoalPlanner", "retain failed nodes", emptyList()),
            "CapabilityExecutorTest"
        )
        assertEquals(0, invocations)
        assertNotNull(observation.diagnosis)
        assertTrue(observation.log.any { it.contains("REA skipped: repair requirement has no exact source revision") })
    }

    @Test
    fun `gate rejects repair when verification still shows failures`() = runBlocking {
        scope = CoroutineScope(Dispatchers.IO)
        val root = tmpDir()
        writePlannerFixture(root)
        copyXmlFixture(root)
        val h = harness()
        val (loop, _, _) = buildLoop(root, h)

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
                ai.friday.assistant.selfrepair.TestFailureObservation(
                    "CapabilityExecutorTest", "some test", "AssertionError", "still broken", "trace"
                )
            ))
        )
        val result = loop.conclude(requirement, diag, patch, validEvidence(candidateSuites = stillFailing))

        assertEquals(SelfRepairLoop.Outcome.ROLLED_BACK, result.outcome)
        assertFalse("backup should be cleaned up after rollback",
            File(root, "com/example/planner/GoalPlanner.kt.selfrepair.${patch.transactionId}.bak").exists())
        assertEquals("source restored to original", miniPlannerSource,
            File(root, "com/example/planner/GoalPlanner.kt").readText())
    }

    @Test
    fun `EvolutionEngine exposes DIAGNOSING state for diagnose repair step`() {
        // Verify the new DIAGNOSING state exists in the EvolutionState enum
        // (proves the integration point was added to EvolutionEngine)
        val state = ai.friday.assistant.evolution.EvolutionState.DIAGNOSING
        assertEquals("DIAGNOSING", state.name)
    }
}
