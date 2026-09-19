package com.jarvis.app.research

/**
 * Domain categories for cross-domain algorithm research.
 */
enum class ResearchDomain {
    BIOLOGY,
    NEUROSCIENCE,
    ANIMALS,
    INSECTS,
    CELLULAR_SYSTEMS,
    IMMUNE_SYSTEMS,
    EVOLUTION,
    ECOLOGY,
    SWARM_INTELLIGENCE,
    ROBOTICS,
    CONTROL_THEORY,
    OPERATING_SYSTEMS,
    DATABASES,
    NETWORKING,
    MATHEMATICS,
    OPTIMIZATION,
    INFORMATION_THEORY,
    DISTRIBUTED_SYSTEMS,
    ECONOMICS,
    LOGISTICS,
    ENGINEERING,
    OTHER
}

/** A mechanism extracted from a source domain. */
data class Mechanism(
    val id: String,
    val name: String,
    val domain: ResearchDomain,
    val source: String,
    val description: String,
    val category: String,
    val complexity: Complexity,
    val properties: Map<String, Any> = emptyMap(),
    val provenance: Provenance
)

enum class Complexity { SIMPLE, MODERATE, COMPLEX, FUNDAMENTAL }

data class Provenance(
    val author: String,
    val sourceReference: String,
    val createdMs: Long = System.currentTimeMillis(),
    val verified: Boolean = false,
    val lastMutation: String = ""
)

/** A candidate algorithm derived from a mechanism. */
data class CandidateAlgorithm(
    val id: String,
    val mechanismId: String,
    val name: String,
    val description: String,
    val implementationSketch: String,
    val targetDomain: ResearchDomain,
    val parameters: Map<String, Any> = emptyMap(),
    val expectedFitness: Map<String, Double> = emptyMap(),
    val provenance: Provenance
)

/** A research pipeline step result. */
data class PipelineResult(
    val step: String,
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
    val durationMs: Long = 0
)
