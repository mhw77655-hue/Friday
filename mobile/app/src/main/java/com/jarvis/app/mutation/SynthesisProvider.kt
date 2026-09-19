package com.jarvis.app.mutation

import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.MutationOperator

/**
 * Pluggable synthesis provider: generates candidate implementations from genomes.
 * The architecture does NOT hard-code Claude, OpenAI, or a particular model.
 *
 * A synthesis provider is the boundary between the evolution system and
 * the actual code generation (which may be an LLM, a template engine,
 * a rule-based system, or a future coding agent).
 */
interface SynthesisProvider {

    /** Human-readable name for diagnostics. */
    val name: String

    /** Whether this provider is available right now. */
    val isAvailable: Boolean

    /** The capabilities this provider can synthesize (e.g. "kotlin", "python"). */
    val supportedCapabilities: Set<String>

    /** Generate a candidate implementation from a genome + mutation operator. */
    suspend fun synthesize(request: SynthesisRequest): SynthesisResult

    /** Health check. */
    suspend fun health(): SynthesisHealth

    fun interface SynthesisCallback {
        fun onProgress(progress: Float, message: String)
    }
}

data class SynthesisRequest(
    val genome: Genome,
    val operator: MutationOperator,
    val description: String,
    val targetLanguage: String = "kotlin",
    val constraints: List<String> = emptyList(),
    val exampleImplementations: List<String> = emptyList(),
    /** Capability name under construction ("" when synthesizing generically). */
    val capability: String = "",
    /** Declared input port names of the required behavior, when known. */
    val inputPorts: List<String> = emptyList(),
    /**
     * Rendered diagnosis from the self-repair loop's diagnose step
     * ([com.jarvis.app.selfrepair.RepairDiagnosis.render]). When present the
     * provider is REPAIRING a fault, not synthesizing fresh behavior.
     */
    val diagnosis: String? = null
)

data class SynthesisResult(
    val success: Boolean,
    val implementation: ImplementationArtifact? = null,
    val description: String = "",
    val error: String? = null
)

data class ImplementationArtifact(
    val language: String,
    val sourceCode: String,
    val testCode: String? = null,
    val dependencies: List<String> = emptyList(),
    val compilationFlags: List<String> = emptyList()
)

data class SynthesisHealth(
    val available: Boolean,
    val latencyMs: Long = 0,
    val lastUsedMs: Long = 0,
    val error: String? = null
)
