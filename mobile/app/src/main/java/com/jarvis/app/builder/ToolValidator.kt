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
        val hasDeterministicSynthesizer = com.jarvis.app.evolution.BehaviorSynthesizer.supports(spec.behavior)
        val hasModelSynthesisContract = spec.behavior is com.jarvis.app.evolution.Behavior.Custom &&
            spec.behavior.capability.isNotBlank() &&
            spec.behavior.port.isNotBlank() &&
            spec.behavior.type in setOf("String", "Number", "Boolean", "Double", "Long", "Int")
        if (spec.behavior is com.jarvis.app.evolution.Behavior.Custom &&
            spec.behavior.capability != spec.capability) {
            issues += "custom behavior capability must match the requested capability"
        }
        if (!hasDeterministicSynthesizer && !hasModelSynthesisContract) {
            issues += "behavior '${spec.behavior.capabilityName}' has no valid synthesis contract"
        }
        when (val behavior = spec.behavior) {
            is com.jarvis.app.evolution.Behavior.Normalize -> {
                if (!behavior.sourceMin.isFinite() || !behavior.sourceMax.isFinite() ||
                    behavior.sourceMax <= behavior.sourceMin
                ) issues += "normalize source_max must be finite and greater than source_min"
            }
            is com.jarvis.app.evolution.Behavior.Clamp -> {
                if (!behavior.min.isFinite() || !behavior.max.isFinite() || behavior.max < behavior.min) {
                    issues += "clamp bounds must be finite and max must be >= min"
                }
            }
            is com.jarvis.app.evolution.Behavior.Invert -> {
                if (!behavior.domain.isFinite() || behavior.domain < 0.0) {
                    issues += "invert domain must be finite and non-negative"
                }
            }
            is com.jarvis.app.evolution.Behavior.ReverseWords -> {
                if (behavior.delimiter.isEmpty()) issues += "reverse_words delimiter must not be empty"
            }
            is com.jarvis.app.evolution.Behavior.Lookup -> {
                if (behavior.table.size > 128) issues += "lookup table exceeds 128 entries"
                if (behavior.table.any { (key, value) -> key.length > 512 || value.length > 512 }) {
                    issues += "lookup keys and values must not exceed 512 characters"
                }
            }
            is com.jarvis.app.evolution.Behavior.Custom -> Unit
        }
        if (spec.maxMemoryMb <= 0 || spec.maxMemoryMb > 1024) issues += "memory budget must be in 1-1024 MB"
        if (!spec.maxCpuPercent.isFinite() || spec.maxCpuPercent <= 0 || spec.maxCpuPercent > 100) issues += "cpu budget must be in 0-100"
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
