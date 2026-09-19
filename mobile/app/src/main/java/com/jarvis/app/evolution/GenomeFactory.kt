package com.jarvis.app.evolution

import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.IsolationLevel
import com.jarvis.app.genome.MutationOperator
import com.jarvis.app.genome.TestType

/**
 * Genome Factory — DESIGN step of the loop: derives the developmental genome
 * (the "modular developmental genome" of §2) from a [CapabilityRequirement].
 *
 * The genome is fully declarative: identity, capability, ports, resource and
 * latency budgets, declared tests, and the mutation operators the evolution
 * engine may apply. Everything downstream (synthesis, environment, validation)
 * reads only this specification.
 */
object GenomeFactory {

    fun genomeFor(requirement: CapabilityRequirement): Genome =
        GenomeBuilder("g_${requirement.capability}")
            .capability(requirement.capability)
            .input(requirement.behavior.inputName, inputType(requirement.behavior))
            .output("result", "Any")
            .resourceBudget(maxMemoryMb = requirement.maxMemoryMb, maxCpuPercent = requirement.maxCpuPercent)
            .latencyBudget(maxFirstTokenMs = 500, maxFullResponseMs = 2_000)
            .runtime(isolation = IsolationLevel.PROCESS_LOCAL)
            .test("unit_${requirement.capability}", TestType.UNIT)
            .test("boundary_${requirement.capability}", TestType.BEHAVIORAL)
            .test("failure_${requirement.capability}", TestType.BEHAVIORAL)
            .benchmark("latency_${requirement.capability}", "latency_ms", 250.0)
            .mutationOperator(MutationOperator.PARAMETER_MUTATION)
            .mutationOperator(MutationOperator.STRATEGY_REPLACEMENT)
            .mutationOperator(MutationOperator.ALGORITHM_MUTATION)
            .author("genome-factory")
            .sourceContext(requirement.description)
            .build()

    private fun inputType(behavior: Behavior): String = when (behavior) {
        is Behavior.Normalize, is Behavior.Clamp, is Behavior.Invert -> "Double"
        is Behavior.ReverseWords, is Behavior.Lookup -> "String"
    }
}
