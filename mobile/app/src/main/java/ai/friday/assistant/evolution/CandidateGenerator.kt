package ai.friday.assistant.evolution

import ai.friday.assistant.genome.Genome
import ai.friday.assistant.mutant.AlgorithmSpec
import ai.friday.assistant.mutant.CandidateImplementation
import kotlinx.coroutines.CancellationException

/**
 * Multi-hybrid Candidate Generator (§6 — "divide and outperform").
 *
 * A difficult capability is never reduced to a single implementation guess.
 * The generator runs every capable [SpecSynthesizer] against the same
 * [CapabilityRequirement], so the evolution engine tests several independent
 * candidate strategies (deterministic, cached, heuristic, model, research-
 * derived, tool-adapted) against the *same* objective and promotes the winner.
 *
 * A synthesizer that fails to produce a candidate is skipped loudly — a gap in
 * generation is itself a failure to record, never a silent empty result.
 */
class CandidateGenerator(private val synthesizers: List<SpecSynthesizer>) {

    private val failureLock = Any()
    @Volatile private var synthesisFailures: List<String> = emptyList()

    /** Failures from the most recent generation cycle, retained for diagnosis. */
    fun lastFailures(): List<String> = synthesisFailures

    /** Begin a fresh objective without discarding failures from the prior objective elsewhere. */
    fun beginGeneration() {
        synchronized(failureLock) { synthesisFailures = emptyList() }
    }

    private fun recordFailure(message: String) {
        synchronized(failureLock) { synthesisFailures = synthesisFailures + message }
    }

    /** Every strategy flavor that participated in the last generation. */
    val strategies: List<AlgorithmSpec.Strategy> get() = synthesizers.map { it.strategy }.distinct()

    /** Produce one independent candidate per capable synthesizer. */
    suspend fun generate(genome: Genome, requirement: CapabilityRequirement): List<GeneratedCandidate> =
        synthesizers.mapNotNull { synth ->
            if (!synth.canHandle(genome, requirement)) return@mapNotNull null
            val impl = try {
                synth.synthesize(genome, requirement)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Preserve the failure as evidence. A missing candidate must
                // never look like a synthesizer that was not even attempted.
                recordFailure("${synth.name}: ${failure.javaClass.simpleName}: ${failure.message.orEmpty()}")
                return@mapNotNull null
            }
            GeneratedCandidate(
                id = "${genome.id}_${synth.name}",
                genome = genome,
                implementation = impl,
                strategy = synth.strategy,
                synthesizerName = synth.name
            )
        }

    fun canGenerateAny(genome: Genome, requirement: CapabilityRequirement): Boolean =
        synthesizers.any { it.canHandle(genome, requirement) }
}

/** One independent candidate: the child genome + its executable implementation. */
data class GeneratedCandidate(
    val id: String,
    val genome: Genome,
    val implementation: CandidateImplementation,
    val strategy: AlgorithmSpec.Strategy,
    val synthesizerName: String
)
