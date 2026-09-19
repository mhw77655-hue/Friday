package com.jarvis.app.research.universal

import com.jarvis.app.research.ResearchDomain

/**
 * A candidate mechanism produced by the research pipeline.
 * Carries structure, inputs-outputs, constraints, confidence, and provenance
 * so downstream consumers (evolution loop, capability fabric) can evaluate
 * and experiment with the candidate.
 */
data class MechanismCandidate(
    val id: String,
    val name: String,
    val description: String,
    val structure: String,
    val inputs: List<String>,
    val outputs: List<String>,
    val constraints: List<String>,
    val confidence: Double,
    val source: CandidateSource,
    val targetDomain: ResearchDomain,
    val principle: String,
    val implementationSketch: String,
    val evidence: ResearchEvidence,
    val createdMs: Long = System.currentTimeMillis()
)

/** The research channel that produced this candidate. */
enum class CandidateSource {
    /** Produced by the cloud-reasoning research provider. */
    CLOUD_REASONING,
    /** Consumed from Fabricator's fiction-mining output channel. */
    FABRICATOR,
    /** Produced by the local catalog/extraction engine. */
    LOCAL_CATALOG
}
