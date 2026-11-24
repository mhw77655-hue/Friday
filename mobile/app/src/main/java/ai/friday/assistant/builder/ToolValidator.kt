package ai.friday.assistant.builder

import ai.friday.assistant.genome.GenomeValidator
import ai.friday.assistant.mutant.CandidateImplementation

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
        val hasDeterministicSynthesizer = ai.friday.assistant.evolution.BehaviorSynthesizer.supports(spec.behavior)
        val hasModelSynthesisContract = spec.behavior is ai.friday.assistant.evolution.Behavior.Custom &&
            spec.behavior.capability.isNotBlank() &&
            spec.behavior.port.isNotBlank() &&
            spec.behavior.type in setOf("String", "Number", "Boolean", "Double", "Long", "Int")
        if (spec.behavior is ai.friday.assistant.evolution.Behavior.Custom &&
            spec.behavior.capability != spec.capability) {
            issues += "custom behavior capability must match the requested capability"
        }
        if (!hasDeterministicSynthesizer && !hasModelSynthesisContract) {
            issues += "behavior '${spec.behavior.capabilityName}' has no valid synthesis contract"
        }
        if (spec.maxMemoryMb <= 0) issues += "memory budget must be positive"
        if (spec.maxCpuPercent <= 0 || spec.maxCpuPercent > 100) issues += "cpu budget must be in 0-100"
        return Validation(issues.isEmpty(), issues)
    }

    fun validateImplementation(impl: CandidateImplementation): Validation {
        val issues = mutableListOf<String>()
        if (impl.spec.capability.isBlank()) issues += "spec capability must not be blank"
        if (impl.tests.isEmpty()) {
            issues += "candidate has no generated tests (§12)"
        }
        if (impl.sourceCode.isBlank()) issues += "candidate has no source"
        return Validation(issues.isEmpty(), issues)
    }

    fun validateGenome(genome: ai.friday.assistant.genome.Genome): List<GenomeValidator.Issue> =
        GenomeValidator.validate(genome)
}
