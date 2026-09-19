package com.jarvis.app.selfrepair

import com.jarvis.app.evolution.PromotionController
import com.jarvis.app.evolution.PromotionReceipt
import com.jarvis.app.evolution.RollbackController
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.genome.MutationOperator
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.mutation.FitnessModel
import com.jarvis.app.mutation.LlmSynthesisProvider
import com.jarvis.app.mutation.SynthesisRequest
import com.jarvis.app.mutant.AlgorithmSpec
import com.jarvis.app.mutant.CandidateImplementation
import com.jarvis.app.mutant.DeterministicSpec
import com.jarvis.app.mutant.SpecOutput
import com.jarvis.app.validation.GateDecision
import com.jarvis.app.sandbox.SandboxExecutor
import com.jarvis.app.validation.GateVerdict
import com.jarvis.app.validation.PromotionGate
import com.jarvis.app.validation.ResourceBenchmark
import java.io.File

/**
 * The missing Observe / Diagnose / Repair step of the evolution loop.
 *
 *   OBSERVE   real runner output captured by [GradleXmlFailureObserver]
 *   DIAGNOSE  [DiagnosisEngine] sends the raw transcripts + original
 *             requirement to the model authority ([com.jarvis.app.model.ModelManager])
 *             and parses a structured diagnosis
 *   REPAIR    [LlmSynthesisProvider.generateRepair] regenerates a concrete fix
 *             (a validated [SourcePatch]) using that diagnosis
 *   VERIFY    an injected [RepairVerifier] re-runs the affected suite FOR REAL
 *             (in tooling this boundary is crossed by invoking Gradle outside
 *             this process, then feeding the fresh results back via [conclude])
 *   GATE      [PromotionGate] decides from the real re-run results; only a
 *             PROMOTE verdict registers the repaired genome through
 *             [PromotionController]; a regression is reversed through
 *             [RollbackController]. A refused candidate is restored from its
 *             backups — never applied blind.
 *
 * This subsystem is TEST-AND-TOOLING-ONLY: nothing here is instantiated in the
 * live JarvisEngine until the loop has proven itself reliable.
 */
class SelfRepairLoop(
    private val repoRoot: File,
    private val observer: GradleXmlFailureObserver,
    private val diagnoser: DiagnosisEngine,
    private val synthesizer: LlmSynthesisProvider,
    private val gate: PromotionGate,
    private val archive: GenomeArchive,
    private val registry: MicroSystemRegistry,
    private val genomeRegistry: GenomeRegistry,
    private val failureSurface: FailureSurface,
    private val promotionController: PromotionController,
    private val rollbackController: RollbackController,
    /**
     * Gate 2: isolated stress-test sandbox. Runs the repaired candidate
     * against adversarial inputs (boundary values, malformed data,
     * concurrent-style repeated calls) BEFORE [PromotionGate] sees it.
     * When null the sandbox gate is simply skipped.
     */
    private val sandboxExecutor: SandboxExecutor? = null
) {

    /** Re-run the affected suite for real after a patch was applied. */
    fun interface RepairVerifier {
        suspend fun verify(patch: SourcePatch): List<TestSuiteObservation>
    }

    enum class Outcome {
        NOTHING_TO_REPAIR,
        VALIDATION_FAILED,
        APPLIED_PENDING_VERIFICATION,
        PROMOTED,
        ROLLED_BACK,
        SANDBOX_REJECTED
    }

    data class Observation(
        val suites: List<TestSuiteObservation>,
        val target: TestSuiteObservation?,
        val diagnosis: RepairDiagnosis?,
        val log: List<String>
    )

    data class Result(
        val outcome: Outcome,
        val diagnosis: RepairDiagnosis?,
        val patch: SourcePatch?,
        val decision: GateDecision?,
        val receipt: PromotionReceipt?,
        val verification: List<TestSuiteObservation>,
        val log: List<String>
    )

    // ------------------------------------------------------------------
    // Step-wise API (tooling drives real Gradle between steps)
    // ------------------------------------------------------------------

    /** OBSERVE + DIAGNOSE against the currently captured runner output. */
    suspend fun observeAndDiagnose(
        requirement: RepairRequirement,
        suiteFilter: String
    ): Observation {
        val log = mutableListOf<String>()
        val suites = observer.observe(suiteFilter)
        val failingSuites = suites.filter { it.failures.isNotEmpty() }
        if (failingSuites.isEmpty()) {
            log.add("observe: no failures in suites matching '$suiteFilter'")
            return Observation(suites, null, null, log)
        }
        val target = failingSuites.first()
        log.add("observe: ${target.failures.size} failing test(s) in ${target.suiteName}")
        val diagnosis = diagnoser.diagnose(requirement, target)
        log.add("diagnose: ${diagnosis.faultyUnit} — ${diagnosis.rootCause.take(160)}")
        return Observation(suites, target, diagnosis, log)
    }

    /**
     * REPAIR: regenerate a fix through the LLM synthesis provider using the
     * diagnosis, then validate it against the tree WITHOUT writing anything.
     */
    suspend fun proposeRepair(
        requirement: RepairRequirement,
        diagnosis: RepairDiagnosis
    ): SourcePatch {
        val request = SynthesisRequest(
            genome = GenomeBuilder("selfrepair_candidate")
                .capability("self_repair")
                .mutationOperator(MutationOperator.REPAIR_MUTATION)
                .build(),
            operator = MutationOperator.REPAIR_MUTATION,
            description = requirement.requirement,
            capability = "self_repair",
            constraints = requirement.constraints,
            diagnosis = diagnosis.render()
        )
        val repairText = synthesizer.generateRepair(request)
        val patch = SourcePatch.parse(repairText)
        val problems = patch.validate(repoRoot)
        require(problems.isEmpty()) { "patch validation failed:\n${problems.joinToString("\n")}" }
        return patch
    }

    /** Apply the patch (with per-file backups). */
    fun apply(patch: SourcePatch) {
        patch.applyTo(repoRoot)
    }

    /** Restore every file the patch touched (rollback point). */
    fun restore(patch: SourcePatch) {
        patch.restoreFromBackup(repoRoot)
    }

    // ------------------------------------------------------------------
    // Full cycle (single process — used by the proof harness/tests)
    // ------------------------------------------------------------------

    suspend fun repair(
        requirement: RepairRequirement,
        suiteFilter: String,
        verifier: RepairVerifier
    ): Result {
        val observation = observeAndDiagnose(requirement, suiteFilter)
        val log = observation.log.toMutableList()
        val target = observation.target
            ?: return Result(Outcome.NOTHING_TO_REPAIR, null, null, null, null, observation.suites, log)
        val diagnosis = observation.diagnosis!!

        val patch = try {
            proposeRepair(requirement, diagnosis)
        } catch (t: Throwable) {
            log.add("repair: candidate refused (${t.message?.take(200)})")
            recordWarning("Self-repair candidate refused: ${t.message?.take(200)}")
            return Result(Outcome.VALIDATION_FAILED, diagnosis, null, null, null, emptyList(), log)
        }
        apply(patch)
        log.add("repair: applied ${patch.edits.size} edit(s)")

        val verification = verifier.verify(patch)
        return conclude(requirement, diagnosis, patch, verification, log)
    }

    /**
     * GATE + PROMOTE/ROLLBACK on REAL post-fix verification results. Called by
     * [repair] directly, or by the tooling flow after the external re-run.
     *
     * Gate 2 (SandboxExecutor) runs BEFORE [PromotionGate]: the repaired
     * candidate is stressed with adversarial inputs in isolation. If the
     * sandbox catches a failure, the candidate is rolled back immediately —
     * [PromotionGate] never sees it.
     */
    suspend fun conclude(
        requirement: RepairRequirement,
        diagnosis: RepairDiagnosis,
        patch: SourcePatch,
        verification: List<TestSuiteObservation>,
        logIn: List<String> = emptyList()
    ): Result {
        val log = logIn.toMutableList()

        // Gate 2: sandbox stress test — runs BEFORE [PromotionGate] and
        // independently of verification results. The sandbox tests the
        // repaired code against adversarial inputs (boundary values,
        // malformed data, repeated calls) and catches broken candidates
        // that would otherwise reach the gate.
        if (sandboxExecutor != null) {
            log.add("sandboxGate: running adversarial stress test")
            val stressResult = sandboxExecutor.stressTest(patch, requirement.requirement)
            if (!stressResult.passed) {
                val failureDetail = stressResult.failures.joinToString("; ") { it.description }
                log.add("sandboxGate: REJECTED — $failureDetail")
                recordWarning(
                    "Self-repair candidate rejected by sandbox gate: $failureDetail"
                )
                // Rollback immediately — gate never sees this candidate.
                restore(patch)
                log.add("sandboxGate: patch restored from backups")
                return Result(
                    Outcome.SANDBOX_REJECTED, diagnosis, patch, null, null,
                    verification, log + stressResult.log
                )
            }
            log.add("sandboxGate: PASSED — candidate survived adversarial inputs")
            log.addAll(stressResult.log)
        }

        val observed = verification.firstOrNull()
            ?: throw IllegalArgumentException("verification produced no suite observation")

        val testOutcomes = observed.toTestOutcomes()
        val evaluation = FitnessModel.evaluate(
            candidateId = "selfrepair_${observed.suiteName}",
            testResults = testOutcomes,
            benchmarkResults = emptyList()
        )
        val decision = gate.decide(
            evaluation = evaluation,
            regression = testOutcomes,
            resource = ResourceBenchmark.ResourceReport(
                benchmarks = emptyList(), averageLatencyMs = 0.0, iterations = 0
            )
        )
        log.add("gate: ${decision.verdict} (${decision.reasons.joinToString("; ").ifEmpty { "clean" }})")

        return when (decision.verdict) {
            GateVerdict.PROMOTE -> promote(diagnosis, patch, decision, observed, verification, log)
            else -> rollback(decision, diagnosis, patch, verification, log)
        }
    }

    // ------------------------------------------------------------------

    private suspend fun promote(
        diagnosis: RepairDiagnosis,
        patch: SourcePatch,
        decision: GateDecision,
        observed: TestSuiteObservation,
        verification: List<TestSuiteObservation>,
        log: MutableList<String>
    ): Result {
        val genome = GenomeBuilder("selfrepair_${abs(observed.suiteName.hashCode())}_v${System.currentTimeMillis()}")
            .capability("self_repair")
            .mutationOperator(MutationOperator.REPAIR_MUTATION)
            .sourceContext("diagnosed repair of ${diagnosis.faultyUnit}")
            .build()
            .copy(healthState = GenomeHealth.TESTING)
        val implementation = CandidateImplementation(
            spec = DeterministicSpec(
                capability = "self_repair",
                description = "Validated source repair of ${diagnosis.faultyUnit}",
                source = patch.rationale.ifBlank { diagnosis.fixStrategy },
                strategy = AlgorithmSpec.Strategy.DETERMINISTIC,
                fn = { _ -> SpecOutput.Success(mapOf("repaired" to true)) }
            ),
            tests = emptyList(),
            language = "kotlin-patch",
            sourceCode = patch.edits.joinToString("\n\n") { "${it.file}\n---\n${it.replace}" },
            dependencies = listOf("none")
        )
        val receipt = promotionController.promote(genome, implementation)
        activeReceipts[observed.suiteName] = receipt
        log.add("promote: registered ${receipt.systemId}")
        return Result(Outcome.PROMOTED, diagnosis, patch, decision, receipt, verification, log)
    }

    private suspend fun rollback(
        decision: GateDecision,
        diagnosis: RepairDiagnosis?,
        patch: SourcePatch,
        verification: List<TestSuiteObservation>,
        log: MutableList<String>
    ): Result {
        // If an earlier cycle already PROMOTED this repair and it has since
        // regressed, reverse the promotion atomically through the canonical
        // RollbackController (unregister organism, archive REJECTED).
        activeReceipts.values.toList().forEach { receipt ->
            rollbackController.rollback(receipt, "regression after self-repair promotion")
            log.add("rollback: reversed promotion ${receipt.systemId}")
        }
        activeReceipts.clear()
        restore(patch)
        log.add("rollback: patch restored from backups")
        recordWarning(
            "Self-repair candidate rejected at the gate (${decision.verdict}): " +
                decision.reasons.joinToString("; ")
        )
        return Result(Outcome.ROLLED_BACK, diagnosis, patch, decision, null, verification, log)
    }

    /** Live promotion receipts of this loop — the rollback points. */
    private val activeReceipts = linkedMapOf<String, com.jarvis.app.evolution.PromotionReceipt>()

    private fun TestSuiteObservation.toTestOutcomes(): List<FitnessModel.TestOutcome> {
        val failing = failures.map { f ->
            FitnessModel.TestOutcome(
                name = f.testName,
                passed = false,
                error = "${f.failureType ?: "failure"}: ${f.message ?: ""}"
            )
        }
        val passingCount = (totalTests - failures.size).coerceAtLeast(0)
        val passing = (0 until passingCount).map { i ->
            FitnessModel.TestOutcome(name = "${suiteName}_pass_$i", passed = true)
        }
        return passing + failing
    }

    private fun recordWarning(message: String) {
        failureSurface.report(
            FailureReport(
                subsystem = "SELF_REPAIR",
                operation = "repair",
                severity = FailureSeverity.WARNING,
                category = FailureCategory.EVOLUTION,
                message = message,
                source = "SelfRepairLoop"
            )
        )
    }
}

private fun abs(v: Int): Int = if (v < 0) -v else v
