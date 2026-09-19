package com.jarvis.app.evolution

import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.MutationOperator
import com.jarvis.app.mutation.LlmSynthesisProvider
import com.jarvis.app.mutant.AlgorithmSpec
import com.jarvis.app.mutant.CandidateImplementation
import com.jarvis.app.mutant.DeterministicSpec
import com.jarvis.app.mutant.SynthProgram

/**
 * The LLM-backed [SpecSynthesizer] — plugs real model-generated candidates
 * into the multi-hybrid evolution loop behind the same seam as the
 * deterministic/cached/heuristic strategy flavors.
 *
 * The requirement is turned into a genuine behavioral prompt by
 * [LlmSynthesisProvider] (backed by the live ModelManager), the generated
 * text is compiled into an executable [SynthProgram], and the result is a
 * normal [CandidateImplementation]: executed for real inside the sandbox,
 * scored by the fitness model, gated, promoted or rolled back.
 *
 * Unlike the strategy flavors, this synthesizer is NOT limited to the five
 * hard-coded foundation behaviors — it can synthesize any capability whose
 * behavior is expressible in the synth language.
 */
class LlmSpecSynthesizer(
    private val provider: LlmSynthesisProvider,
    override val strategy: AlgorithmSpec.Strategy = AlgorithmSpec.Strategy.DETERMINISTIC
) : SpecSynthesizer {

    override val name: String = "llm"

    /** Any capability with a stated need can be attempted — generation is general. */
    override fun canHandle(genome: Genome, requirement: CapabilityRequirement): Boolean =
        requirement.capability.isNotBlank() && requirement.description.isNotBlank()

    override suspend fun synthesize(
        genome: Genome,
        requirement: CapabilityRequirement
    ): CandidateImplementation {
        // Exact input ports are known only when the requirement's behavior
        // genuinely describes this capability; otherwise the prompt asks the
        // model to infer the inputs from the description.
        val inputPorts =
            if (requirement.behavior.capabilityName == requirement.capability) {
                listOf(requirement.behavior.inputName)
            } else {
                emptyList()
            }
        val program = provider.generateProgram(
            com.jarvis.app.mutation.SynthesisRequest(
                genome = genome,
                operator = genome.provenance.mutationOperator
                    ?: MutationOperator.ALGORITHM_MUTATION,
                description = requirement.description,
                targetLanguage = "synth",
                constraints = requirement.constraints,
                capability = requirement.capability,
                inputPorts = inputPorts
            )
        )
        return CandidateImplementation(
            spec = DeterministicSpec(
                capability = requirement.capability,
                description = "LLM-synthesized ${requirement.capability}: ${requirement.description}",
                source = program.source,
                strategy = strategy,
                fn = program::execute
            ),
            tests = program.tests,
            language = "synth",
            sourceCode = program.source,
            dependencies = listOf("kotlin-runtime")
        )
    }
}
