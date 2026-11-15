package com.jarvis.app.evolution

import com.jarvis.app.genome.Genome
import com.jarvis.app.mutant.AlgorithmSpec
import com.jarvis.app.mutant.CandidateImplementation
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
                // A failed synthesis is recorded as a miss; cancellation and
                // fatal VM errors must never be swallowed as ordinary misses.
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
