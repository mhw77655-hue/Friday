package com.jarvis.app.mutation

import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.MutationOperator
import com.jarvis.app.microsystem.MicroSystemContract
import com.jarvis.app.resource.ResourceGovernor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Built-in evolution loop.
 *
 * PROBLEM → candidate generation → environment creation → implementation →
 * validation → tests → benchmarks → fitness evaluation → comparison →
 * promotion/rejection → archive
 *
 * The loop must be callable by the nervous system and supports multiple
 * competing candidates. It never promotes a candidate solely because
 * it built successfully.
 */
class EvolutionLoop(
    private val archive: GenomeArchive,
    private val registry: com.jarvis.app.microsystem.MicroSystemRegistry,
    private val resourceGovernor: ResourceGovernor,
    private val failureSurface: FailureSurface,
    private val mutationEngine: MutationEngine = MutationEngine(),
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    private val _state = MutableStateFlow(EvolutionState.IDLE)
    val state: StateFlow<EvolutionState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<EvolutionLogEntry>>(emptyList())
    val log: StateFlow<List<EvolutionLogEntry>> = _log.asStateFlow()

    /** Run a full evolution step on a parent genome. */
    suspend fun evolve(
        parent: Genome,
        synthesisProviders: List<SynthesisProvider>,
        hardConstraints: List<FitnessModel.HardConstraint> = FitnessModel.defaultConstraints(),
        weights: FitnessModel.FitnessWeights = FitnessModel.FitnessWeights(),
        maxCandidates: Int = 3
    ): EvolutionResult {
        _state.value = EvolutionState.GENERATING
        appendLog("Starting evolution for genome ${parent.id} v${parent.version}")

        // 1. Generate candidates
        val mutations = mutationEngine.mutate(parent, parent.mutationOperators, maxCandidates)
        if (mutations.isEmpty()) {
            appendLog("No mutations produced")
            return EvolutionResult(false, parent, emptyList())
        }
        appendLog("Generated ${mutations.size} candidate(s)")

        // 2. For each candidate, synthesize → validate → test → evaluate
        val evaluations = mutableListOf<FitnessModel.EvaluationResult>()
        val candidates = mutableListOf<EvolutionCandidate>()

        for (candidate in mutations) {
            _state.value = EvolutionState.SYNTHESIZING
            appendLog("Synthesizing ${candidate.id} with ${candidate.operator}")

            val synthesisResult = synthesizeWithProviders(candidate, synthesisProviders)
            if (!synthesisResult.success) {
                appendLog("Synthesis failed for ${candidate.id}: ${synthesisResult.error}")
                recordFailure(candidate, synthesisResult.error ?: "synthesis failed")
                evaluations += FitnessModel.EvaluationResult(
                    candidateId = candidate.id,
                    dimensions = FitnessModel.FitnessDimensions.ZEROS,
                    hardConstraintsPassed = false,
                    hardConstraintFailures = listOf("synthesis failed"),
                    testResults = emptyList(),
                    benchmarkResults = emptyList()
                )
                continue
            }

            // 3. Static validation
            _state.value = EvolutionState.VALIDATING
            val validationIssues = com.jarvis.app.genome.GenomeValidator.validate(candidate.child)
            val validationErrors = validationIssues.filter { it.severity == com.jarvis.app.genome.GenomeValidator.Issue.Severity.ERROR }
            if (validationErrors.isNotEmpty()) {
                appendLog("Validation failed for ${candidate.id}: ${validationErrors.joinToString("; ") { it.message }}")
                recordFailure(candidate, "validation failed: ${validationErrors.first().message}")
                evaluations += FitnessModel.EvaluationResult(
                    candidateId = candidate.id,
                    dimensions = FitnessModel.FitnessDimensions.ZEROS,
                    hardConstraintsPassed = false,
                    hardConstraintFailures = validationErrors.map { it.message },
                    testResults = emptyList(),
                    benchmarkResults = emptyList()
                )
                continue
            }

            // 4. Resource admission check
            _state.value = EvolutionState.TESTING
            val admission = resourceGovernor.requestAdmission(candidate.child, candidate.id)
            if (!admission.admitted) {
                appendLog("Admission denied for ${candidate.id}: ${admission.reasons.joinToString("; ")}")
                recordFailure(candidate, "admission denied: ${admission.reasons.firstOrNull()}")
                resourceGovernor.release(candidate.id)
                evaluations += FitnessModel.EvaluationResult(
                    candidateId = candidate.id,
                    dimensions = FitnessModel.FitnessDimensions.ZEROS,
                    hardConstraintsPassed = false,
                    hardConstraintFailures = admission.reasons,
                    testResults = emptyList(),
                    benchmarkResults = emptyList()
                )
                continue
            }

            // 5. Run tests (simulated in this foundation; real tests are test-code based)
            val testResults = simulateTests(candidate)
            val benchmarkResults = simulateBenchmarks(candidate)

            // 6. Fitness evaluation
            val evaluation = FitnessModel.evaluate(
                candidateId = candidate.id,
                testResults = testResults,
                benchmarkResults = benchmarkResults,
                hardConstraints = hardConstraints,
                weights = weights
            )
            evaluations += evaluation

            // Record in archive
            val archiveCandidate = EvolutionCandidate(
                candidate = candidate,
                synthesisResult = synthesisResult,
                validationIssues = validationIssues,
                admissionResult = admission,
                testResults = testResults,
                benchmarkResults = benchmarkResults,
                evaluation = evaluation
            )
            candidates += archiveCandidate

            appendLog("Candidate ${candidate.id}: score=${String.format("%.3f", evaluation.score)} passed=${evaluation.passed} constraints=${validationErrors.size}")

            // Release resources
            resourceGovernor.release(candidate.id)
        }

        // 7. Compare and select
        _state.value = EvolutionState.EVALUATING
        val paretoOptimal = FitnessModel.paretoOptimal(evaluations)
        val best = paretoOptimal.maxByOrNull { it.score }

        // 8. Promote/reject
        var promoted = false
        for (candidate in candidates) {
            if (candidate.evaluation.passed && candidate.evaluation == best) {
                // Promotion
                val promotedGenome = candidate.candidate.child.copy(healthState = GenomeHealth.HEALTHY)
                archive.put(promotedGenome)
                appendLog("PROMOTED: ${candidate.candidate.id}")
                promoted = true
            } else if (!candidate.evaluation.passed) {
                val rejected = candidate.candidate.child.copy(healthState = GenomeHealth.REJECTED)
                archive.put(rejected)
                appendLog("REJECTED: ${candidate.candidate.id} — ${candidate.evaluation.hardConstraintFailures.firstOrNull()}")
            }
        }

        _state.value = EvolutionState.IDLE
        appendLog("Evolution complete. Promoted: $promoted, evaluated: ${evaluations.size}")

        return EvolutionResult(
            promoted = promoted,
            parent = parent,
            candidates = candidates
        )
    }

    private suspend fun synthesizeWithProviders(
        candidate: MutationCandidate,
        providers: List<SynthesisProvider>
    ): com.jarvis.app.mutation.SynthesisResult {
        for (provider in providers) {
            if (!provider.isAvailable) continue
            if (provider.supportedCapabilities.intersect(candidate.child.capabilities).isEmpty()) continue
            return provider.synthesize(SynthesisRequest(
                genome = candidate.child,
                operator = candidate.operator,
                description = candidate.description
            ))
        }
        return com.jarvis.app.mutation.SynthesisResult(
            success = false,
            error = "No available synthesis provider can handle this genome"
        )
    }

    /** Simulate test execution (foundation; real tests use test-code descriptors). */
    private fun simulateTests(candidate: MutationCandidate): List<FitnessModel.TestOutcome> =
        candidate.child.testSuite.map { desc ->
            FitnessModel.TestOutcome(
                name = desc.name,
                passed = true, // foundation: all declared tests pass
                durationMs = 100L
            )
        }

    /** Simulate benchmark execution (foundation; real benchmarks use real runners). */
    private fun simulateBenchmarks(candidate: MutationCandidate): List<FitnessModel.BenchmarkOutcome> =
        candidate.child.benchmarkSuite.map { desc ->
            FitnessModel.BenchmarkOutcome(
                name = desc.name,
                metric = desc.metric,
                value = desc.baseline,
                unit = "ms"
            )
        }

    private fun recordFailure(candidate: MutationCandidate, message: String) {
        val genome = candidate.child.copy(healthState = GenomeHealth.REJECTED)
        archive.put(genome)
        failureSurface.report(FailureReport(
            subsystem = "EVOLUTION",
            operation = "evolve",
            severity = FailureSeverity.RECOVERABLE,
            category = FailureCategory.INTERNAL,
            message = message,
            source = "EvolutionLoop"
        ))
    }

    private fun appendLog(message: String) {
        val entry = EvolutionLogEntry(timestamp = nowMs(), message = message)
        _log.value = _log.value + entry
        if (_log.value.size > 100) _log.value = _log.value.takeLast(100)
    }
}

enum class EvolutionState { IDLE, GENERATING, SYNTHESIZING, VALIDATING, TESTING, EVALUATING, ERROR }

data class EvolutionLogEntry(val timestamp: Long, val message: String)

data class EvolutionCandidate(
    val candidate: MutationCandidate,
    val synthesisResult: com.jarvis.app.mutation.SynthesisResult,
    val validationIssues: List<com.jarvis.app.genome.GenomeValidator.Issue>,
    val admissionResult: com.jarvis.app.resource.AdmissionResult,
    val testResults: List<FitnessModel.TestOutcome>,
    val benchmarkResults: List<FitnessModel.BenchmarkOutcome>,
    val evaluation: FitnessModel.EvaluationResult
)

data class EvolutionResult(
    val promoted: Boolean,
    val parent: Genome,
    val candidates: List<EvolutionCandidate>
)
