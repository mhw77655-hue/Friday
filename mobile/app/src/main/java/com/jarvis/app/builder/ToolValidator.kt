package com.jarvis.app.builder

import com.jarvis.app.genome.GenomeValidator
import com.jarvis.app.mutant.AlgorithmSpec
import com.jarvis.app.mutant.CandidateImplementation

/**
 * Tool Validator (§15): validates a [ToolSpecification] before building and a
 * generated [CandidateImplementation] before testing. The cheap static gates
 * that keep nonsense out of the expensive sandbox loop.
 */
object ToolValidator {

    data class Validation(val valid: Boolean, val issues: List<String>)

    fun validateSpecification(spec: ToolSpecification): Validation {
        val issues = mutableListOf<String>()
        if (spec.capability.isBlank()) issues += "capability must not be blank"
        if (spec.description.isBlank()) issues += "description must not be blank"
        if (!com.jarvis.app.evolution.BehaviorSynthesizer.supports(spec.behavior)) {
            issues += "behavior '${spec.behavior.capabilityName}' is not supported by any synthesizer"
        }
        if (spec.maxMemoryMb <= 0) issues += "memory budget must be positive"
        if (spec.maxCpuPercent <= 0 || spec.maxCpuPercent > 100) issues += "cpu budget must be in 0-100"
        return Validation(issues.isEmpty(), issues)
    }

    fun validateImplementation(impl: CandidateImplementation): Validation {
        val issues = mutableListOf<String>()
        if (impl.spec.capability.isBlank()) issues += "spec capability must not be blank"
        if (impl.spec is AlgorithmSpec && impl.tests.isEmpty()) {
            issues += "candidate has no generated tests (§12)"
        }
        if (impl.sourceCode.isBlank()) issues += "candidate has no source"
        return Validation(issues.isEmpty(), issues)
    }

    fun validateGenome(genome: com.jarvis.app.genome.Genome): List<GenomeValidator.Issue> =
        GenomeValidator.validate(genome)
}
