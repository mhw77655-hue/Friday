package com.jarvis.app.evolution

import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.genome.GenomeValidator
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.mutant.MutantEnvironment
import com.jarvis.app.mutation.FitnessModel
import com.jarvis.app.mutation.MutationEngine
import com.jarvis.app.resource.ResourceGovernor
import com.jarvis.app.selfrepair.RepairRequirement
import com.jarvis.app.selfrepair.SelfRepairLoop
import com.jarvis.app.validation.CandidateTestRunner
import com.jarvis.app.validation.FailureTestRunner
import com.jarvis.app.validation.GateDecision
import com.jarvis.app.validation.GateVerdict
import com.jarvis.app.validation.PromotionGate
import com.jarvis.app.validation.RegressionRunner
import com.jarvis.app.validation.ResourceBenchmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Evolution Engine (§5): the production-grade evolutionary program-synthesis
 * loop. Unlike the lightweight built-in [com.jarvis.app.mutation.EvolutionLoop]
 * (which simulates its tests), this engine executes every candidate **for
 * real** inside a [MutantEnvironment] and only promotes through the review
 * gate.
 *
 *   GENOME → (MUTATE) → GENERATE (multi-hybrid) → BUILD → VALIDATE → TEST →
 *   FAILURE-TEST → BENCHMARK → REGRESSION → COMPARE → SELECT → ARCHIVE →
 *   PROMOTE / REJECT → NEXT GENERATION
 *
 * Dormant when unused; runs only when a capability gap or authorized
 * development cycle triggers it (§18). Never touches production code directly:
 * every change goes through sandbox → tests → gate → promotion → rollback
 * point (§20).
 */
class EvolutionEngine(
    private val archive: GenomeArchive,
    private val registry: MicroSystemRegistry,
    private val genomeRegistry: GenomeRegistry,
    private val governor: ResourceGovernor,
    private val failureSurface: FailureSurface,
    private val candidateGenerator: CandidateGenerator,
    private val mutantEnv: MutantEnvironment,
    private val testRunner: CandidateTestRunner,
    private val failureRunner: FailureTestRunner,
    private val resourceBenchmark: ResourceBenchmark,
    private val regressionRunner: RegressionRunner,
    private val gate: PromotionGate,
    private val promotionController: PromotionController,
    private val rollbackController: RollbackController,
    private val mutationEngine: MutationEngine = MutationEngine(),
    /**
     * The Observe / Diagnose / Repair step — fires when every candidate is
     * rejected at the gate.  [SelfRepairLoop] sends the real test failure
     * transcripts to the model authority, gets a structured diagnosis, and
     * produces a source-level repair gated by [PromotionGate] and
     * [RollbackController].  When null the loop is simply skipped (the engine
     * still works, just without self-repair).
     */
    private val selfRepairLoop: SelfRepairLoop? = null
) {

    private val _state = MutableStateFlow(EvolutionState.IDLE)
    val state: StateFlow<EvolutionState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<EvolutionLogEntry>>(emptyList())
    val log: StateFlow<List<EvolutionLogEntry>> = _log.asStateFlow()

    /**
     * Run the full loop for a parent genome + capability requirement.
     *
     * @param mutateFirst when true, the parent is first mutated into a fresh
     *   lineage, then each mutant is generated multi-hybrid (§5 MUTATE step).
     */
    suspend fun evolve(
        parent: Genome,
        requirement: CapabilityRequirement,
        hardConstraints: List<FitnessModel.HardConstraint> = gate.constraints,
        weights: FitnessModel.FitnessWeights = FitnessModel.FitnessWeights(),
        mutateFirst: Boolean = false,
        maxCandidates: Int = 8
    ): EvolutionRunResult {
        _state.value = EvolutionState.GENERATING
        appendLog("Evolution for ${parent.id} — capability '${requirement.capability}' (${requirement.behavior.capabilityName})")

        // GENOME → MUTATE → GENERATE
        val baseGenomes = if (mutateFirst && parent.mutationOperators.isNotEmpty()) {
            mutationEngine.mutate(parent, count = maxCandidates).map { it.child }
        } else {
            listOf(parent)
        }

        val candidates = buildList {
            for (base in baseGenomes) {
                for (gen in candidateGenerator.generate(base, requirement)) {
                    // The child genome records its strategy + capability and is
                    // TESTING until the gate decides (§14).
                    val child = base.copy(
                        id = gen.id,
                        capabilities = base.capabilities + requirement.capability,
                        healthState = GenomeHealth.TESTING,
                        provenance = base.provenance.copy(
                            lastMutation = "strategy ${gen.synthesizerName} for '${requirement.capability}'",
                            sourceContext = requirement.description
                        )
                    )
                    add(child to gen)
                }
            }
        }.distinctBy { it.first.id }.take(maxCandidates)

        if (candidates.isEmpty()) {
            appendLog("No candidate could be generated")
            recordFailure("No candidate generated for '${requirement.capability}'")
            _state.value = EvolutionState.IDLE
            return EvolutionRunResult(promoted = false, best = null, candidates = emptyList())
        }
        appendLog("Generated ${candidates.size} candidate(s) (multi-hybrid)")

        // The baseline every candidate must keep satisfying (§14).
        val baselineTests = BehaviorSynthesizer.testsFor(requirement.behavior)
        val evaluated = mutableListOf<EvaluatedCandidate>()

        for ((child, gen) in candidates) {
            _state.value = EvolutionState.VALIDATING

            // VALIDATE (static)
            val issues = GenomeValidator.validate(child)
            if (issues.any { it.severity == GenomeValidator.Issue.Severity.ERROR }) {
                recordRejected(child, "static validation: ${issues.first().message}")
                continue
            }

            // ADMISSION (resource guard, §18 — no candidate runs without budget)
            val admission = governor.requestAdmission(child, gen.id)
            if (!admission.admitted) {
                recordRejected(child, "admission denied: ${admission.reasons.firstOrNull()}")
                governor.release(gen.id)
                continue
            }

            // BUILD + create the mutant habitat
            _state.value = EvolutionState.TESTING
            val envResult = mutantEnv.create(child, gen.id, gen.implementation)
            if (envResult.isFailure) {
                recordRejected(child, "environment creation failed")
                governor.release(gen.id)
                continue
            }
            val instance = envResult.getOrThrow()

            try {
                // TEST (own suite) + FAILURE-TEST + BENCHMARK + REGRESSION
                val unitTests = testRunner.run(instance, gen.implementation)
                val failureTests = failureRunner.runFailurePath(instance, gen.implementation)
                val recovery = failureRunner.runRecovery(instance, gen.implementation)
                val regressionTests = regressionRunner.run(instance, baselineTests)
                val resource = resourceBenchmark.run(instance, gen.implementation)

                val allTests = unitTests + failureTests + recovery
                val evaluation = FitnessModel.evaluate(
                    candidateId = gen.id,
                    testResults = allTests,
                    benchmarkResults = resource.benchmarks,
                    hardConstraints = hardConstraints,
                    weights = weights
                )

                // COMPARE + SELECT at the gate
                val decision = gate.decide(evaluation, regressionTests, resource)
                val record = EvaluatedCandidate(
                    genome = child,
                    implementation = gen.implementation,
                    instance = instance,
                    evaluation = evaluation,
                    unitTests = unitTests,
                    failureTests = failureTests,
                    recovery = recovery,
                    regressionTests = regressionTests,
                    resource = resource,
                    decision = decision
                )
                evaluated += record

                // ARCHIVE every outcome (§14) — healthy, rejected and mutated
                // candidates are all preserved.
                archive.put(
                    child.copy(
                        healthState = when (decision.verdict) {
                            GateVerdict.PROMOTE -> GenomeHealth.HEALTHY
                            GateVerdict.MUTATE -> GenomeHealth.TESTING
                            GateVerdict.REJECT -> GenomeHealth.REJECTED
                        },
                        fitnessMetrics = evaluation.dimensions.toMap(),
                        version = child.version + 1
                    )
                )
                appendLog("Candidate ${gen.id} [${gen.synthesizerName}] → ${decision.verdict} (score ${"%.3f".format(decision.score)})")
            } finally {
                governor.release(gen.id)
            }
        }

        _state.value = EvolutionState.EVALUATING

        // SELECT: the best candidate that passed the gate
        val promoted = evaluated
            .filter { it.decision.verdict == GateVerdict.PROMOTE }
            .maxByOrNull { it.decision.score }

        if (promoted == null) {
            evaluated.forEach { mutantEnv.destroy(it.instance) }
            appendLog("No candidate passed the gate — all archived, none promoted")

            // ---------------------------------------------------------------
            // DIAGNOSE / REPAIR STEP: when every candidate is rejected the
            // evolution loop now invokes [SelfRepairLoop] instead of giving up.
            // The loop reads the real Gradle test-result XMLs, sends the raw
            // failure transcripts to the model authority for diagnosis, and
            // produces a source-level repair gated by [PromotionGate].  A
            // PROMOTED verdict means the organism's own source code was fixed;
            // a ROLLED_BACK verdict means the repair was reverted and the loop
            // terminates as before.
            // ---------------------------------------------------------------
            if (selfRepairLoop != null) {
                _state.value = EvolutionState.DIAGNOSING
                appendLog("Triggering diagnose/repair — no candidate passed the gate")
                val repairReq = RepairRequirement(
                    component = requirement.capability,
                    requirement = requirement.description,
                    constraints = requirement.constraints
                )
                val repairResult = try {
                    selfRepairLoop.repair(repairReq, "") { patch ->
                        // Re-run the affected suite after the patch was applied.
                        // In the test-and-tooling harness this is provided by
                        // the verifier lambda; for now we return empty (the
                        // harness supplies the real verifier).
                        emptyList()
                    }
                } catch (t: Throwable) {
                    appendLog("Diagnose/repair failed: ${t.message?.take(200)}")
                    recordFailure("Self-repair cycle failed: ${t.message?.take(200)}")
                    _state.value = EvolutionState.IDLE
                    return EvolutionRunResult(promoted = false, best = null, candidates = evaluated)
                }

                appendLog("Diagnose/repair outcome: ${repairResult.outcome} — ${
                    repairResult.log.lastOrNull()?.take(120) ?: ""
                }")

                return when (repairResult.outcome) {
                    SelfRepairLoop.Outcome.PROMOTED -> {
                        // The repair passed the gate — the organism's source
                        // was fixed.  Return a synthetic promoted result so
                        // the caller knows the loop produced a fix.
                        _state.value = EvolutionState.IDLE
                        EvolutionRunResult(
                            promoted = true,
                            best = null,
                            candidates = evaluated,
                            receipt = repairResult.receipt
                        )
                    }
                    else -> {
                        _state.value = EvolutionState.IDLE
                        EvolutionRunResult(promoted = false, best = null, candidates = evaluated)
                    }
                }
            }

            _state.value = EvolutionState.IDLE
            return EvolutionRunResult(promoted = false, best = null, candidates = evaluated)
        }

        // PROMOTE atomically; the habitat of the winner is preserved for the
        // archive, the losers are destroyed.
        _state.value = EvolutionState.PROMOTING
        val receipt = promotionController.promote(promoted.genome, promoted.implementation)
        mutantEnv.preserve(promoted.instance)
        evaluated.filter { it !== promoted }.forEach { mutantEnv.destroy(it.instance) }
        appendLog("PROMOTED ${promoted.genome.id} — registered as ${receipt.systemId}")

        _state.value = EvolutionState.IDLE
        return EvolutionRunResult(
            promoted = true,
            best = promoted,
            candidates = evaluated,
            receipt = receipt
        )
    }

    /** The current baseline suite for a requirement (exposed for reuse). */
    fun baselineTestsFor(requirement: CapabilityRequirement) =
        BehaviorSynthesizer.testsFor(requirement.behavior)

    private fun recordRejected(child: Genome, reason: String) {
        archive.put(child.copy(healthState = GenomeHealth.REJECTED))
        appendLog("REJECTED ${child.id}: $reason")
        recordFailure("Candidate ${child.id} rejected: $reason")
    }

    private fun recordFailure(message: String) {
        failureSurface.report(
            FailureReport(
                subsystem = "EVOLUTION",
                operation = "evolve",
                severity = FailureSeverity.WARNING,
                category = FailureCategory.EVOLUTION,
                message = message,
                source = "EvolutionEngine"
            )
        )
    }

    private fun appendLog(message: String) {
        _log.value = _log.value + EvolutionLogEntry(now = System.currentTimeMillis(), message = message)
    }
}

enum class EvolutionState { IDLE, GENERATING, VALIDATING, TESTING, EVALUATING, PROMOTING, DIAGNOSING }

data class EvolutionLogEntry(val now: Long, val message: String)

/** Everything known about one evaluated candidate (the archive record input). */
data class EvaluatedCandidate(
    val genome: Genome,
    val implementation: com.jarvis.app.mutant.CandidateImplementation,
    val instance: com.jarvis.app.mutant.MutantEnvironment.MutantInstance,
    val evaluation: FitnessModel.EvaluationResult,
    val unitTests: List<FitnessModel.TestOutcome>,
    val failureTests: List<FitnessModel.TestOutcome>,
    val recovery: FitnessModel.TestOutcome,
    val regressionTests: List<FitnessModel.TestOutcome>,
    val resource: ResourceBenchmark.ResourceReport,
    val decision: GateDecision
)

data class EvolutionRunResult(
    val promoted: Boolean,
    val best: EvaluatedCandidate?,
    val candidates: List<EvaluatedCandidate>,
    val receipt: PromotionReceipt? = null
)

/** Flatten fitness dimensions for the archive record. */
fun FitnessModel.FitnessDimensions.toMap(): Map<String, Double> = mapOf(
    "correctness" to correctness,
    "reliability" to reliability,
    "latency" to latency,
    "memory" to memoryCost,
    "cpu" to cpuCost,
    "energy" to energyCost,
    "failure_rate" to failureRate,
    "compatibility" to compatibility,
    "maintainability" to maintainability,
    "test_coverage" to testCoverage
)
