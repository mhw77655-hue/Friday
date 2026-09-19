package com.jarvis.app.research

import com.jarvis.app.mutation.FitnessModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Research pipeline interface.
 *
 * PROBLEM → abstraction → cross-domain search → mechanism extraction →
 * mechanism translation → candidate algorithm → simulation → benchmark →
 * integration candidate
 *
 * Multiple research providers can be plugged in. The orchestrator drives
 * the pipeline in sequence. Providers are NOT autonomous researchers; this
 * is the architecture for future research providers to plug into.
 */
interface ResearchProvider {
    /** Human-readable name. */
    val name: String

    /** Domains this provider knows about. */
    val domains: Set<ResearchDomain>

    /** Search for mechanisms relevant to a problem statement. */
    suspend fun search(
        problemStatement: String,
        targetDomains: Set<ResearchDomain>,
        constraints: List<String> = emptyList()
    ): List<Mechanism>

    /** Translate a mechanism into a candidate algorithm for a target domain. */
    suspend fun translate(
        mechanism: Mechanism,
        targetDomain: String,
        parameters: Map<String, Any> = emptyMap()
    ): CandidateAlgorithm

    /** Simulate a candidate algorithm (run in a sandbox or estimate). */
    suspend fun simulate(algorithm: CandidateAlgorithm): SimulationResult

    /** Benchmark a candidate algorithm against expected metrics. */
    suspend fun benchmark(algorithm: CandidateAlgorithm): List<FitnessModel.BenchmarkOutcome>

    /** Health check. */
    suspend fun health(): Boolean
}

data class SimulationResult(
    val success: Boolean,
    val output: Any? = null,
    val metrics: Map<String, Double> = emptyMap(),
    val durationMs: Long = 0,
    val error: String? = null
)

/**
 * Orchestrates the full research pipeline across multiple providers.
 * PROBLEM → abstraction → cross-domain search → mechanism extraction →
 * mechanism translation → candidate algorithm → simulation → benchmark.
 */
class ResearchOrchestrator(private val providers: List<ResearchProvider>) {

    private val _state = MutableStateFlow(ResearchState.IDLE)
    val state: StateFlow<ResearchState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<PipelineLogEntry>>(emptyList())
    val log: StateFlow<List<PipelineLogEntry>> = _log.asStateFlow()

    /** Run a full research pass. */
    suspend fun research(
        problemStatement: String,
        targetDomains: Set<ResearchDomain> = ResearchDomain.values().toSet(),
        maxResults: Int = 10
    ): ResearchResult {
        _state.value = ResearchState.SEARCHING
        val startTime = System.currentTimeMillis()

        // 1. Cross-domain search across all providers
        val mechanisms = mutableListOf<Mechanism>()
        for (provider in providers) {
            val relevant = provider.domains.intersect(targetDomains)
            if (relevant.isEmpty()) continue
            val results = provider.search(problemStatement, relevant)
            mechanisms.addAll(results)
            appendLog(PipelineLogEntry(step = "search:${provider.name}", success = true, message = "Found ${results.size}"))
        }
        appendLog(PipelineLogEntry(step = "search", success = mechanisms.isNotEmpty(), message = "Found ${mechanisms.size} mechanisms"))

        // 2. Mechanism translation → candidate algorithms
        _state.value = ResearchState.EXTRACTING
        val candidates = mutableListOf<CandidateAlgorithm>()
        for (mechanism in mechanisms.take(maxResults)) {
            val provider = providers.firstOrNull { mechanism.domain in it.domains } ?: continue
            val algorithm = provider.translate(mechanism, problemStatement, mechanism.properties)
            candidates += algorithm
        }
        appendLog(PipelineLogEntry(step = "translate", success = candidates.isNotEmpty(), message = "Generated ${candidates.size} candidates"))

        // 3. Simulate each candidate
        _state.value = ResearchState.SIMULATING
        val simulations = mutableListOf<SimulationResult>()
        for (candidate in candidates) {
            val provider = providers.firstOrNull { candidate.targetDomain in it.domains } ?: continue
            val result = provider.simulate(candidate)
            simulations += result
            appendLog(PipelineLogEntry(step = "simulate:${candidate.id}", success = result.success, message = result.error ?: "ok"))
        }

        // 4. Benchmark the successful candidates
        _state.value = ResearchState.BENCHMARKING
        val benchmarks = mutableListOf<FitnessModel.BenchmarkOutcome>()
        for ((i, candidate) in candidates.withIndex()) {
            if (simulations.getOrNull(i)?.success != true) continue
            val provider = providers.firstOrNull { candidate.targetDomain in it.domains } ?: continue
            benchmarks += provider.benchmark(candidate)
        }

        _state.value = ResearchState.IDLE
        val elapsed = System.currentTimeMillis() - startTime

        return ResearchResult(
            mechanisms = mechanisms,
            candidates = candidates,
            simulations = simulations,
            benchmarks = benchmarks,
            totalDurationMs = elapsed
        )
    }

    private fun appendLog(entry: PipelineLogEntry) {
        _log.value = _log.value + entry
        if (_log.value.size > 100) _log.value = _log.value.takeLast(100)
    }
}

enum class ResearchState { IDLE, SEARCHING, EXTRACTING, SIMULATING, BENCHMARKING }

data class ResearchResult(
    val mechanisms: List<Mechanism>,
    val candidates: List<CandidateAlgorithm>,
    val simulations: List<SimulationResult>,
    val benchmarks: List<FitnessModel.BenchmarkOutcome>,
    val totalDurationMs: Long
)

data class PipelineLogEntry(
    val step: String,
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
