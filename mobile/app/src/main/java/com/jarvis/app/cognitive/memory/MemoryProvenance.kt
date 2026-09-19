package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryType


/**
 * MemoryProvenance - Complete provenance tracking for consolidated memories.
 *
 * Every consolidated memory must retain full provenance to protect against
 * memory poisoning and enable traceability.
 */
data class MemoryProvenance(
    /** Originating experience that created this memory */
    val originatingExperienceId: String,

    /** Source of the originating experience */
    val experienceSource: String,

    /** Timestamp when the originating experience occurred */
    val experienceTimestamp: Long,

    /** Confidence in the originating experience (0.0 - 1.0) */
    val experienceConfidence: Float,

    /** Derivation history - each step in the memory's evolution */
    val derivationHistory: List<DerivationStep> = emptyList(),

    /** Current confidence after all updates/derivations (0.0 - 1.0) */
    val currentConfidence: Float,

    /** Source reliability score (0.0 - 1.0) */
    val sourceReliability: Float = 0.8f,

    /** Whether this memory has been independently verified */
    val verified: Boolean = false,

    /** Verification timestamp */
    val verifiedAt: Long = 0,

    /** Verification method/source */
    val verificationMethod: String? = null,

    /** Tags for provenance categorization */
    val provenanceTags: List<String> = emptyList()
) {
    /** Add a derivation step */
    fun withDerivation(step: DerivationStep): MemoryProvenance {
        return copy(derivationHistory = derivationHistory + step)
    }

    /** Update confidence */
    fun withConfidence(newConfidence: Float): MemoryProvenance {
        return copy(currentConfidence = newConfidence.coerceIn(0.0f, 1.0f))
    }

    /** Mark as verified */
    fun withVerification(method: String): MemoryProvenance {
        return copy(
            verified = true,
            verifiedAt = System.currentTimeMillis(),
            verificationMethod = method
        )
    }

    /** Get full provenance chain as human-readable string */
    fun toProvenanceString(): String {
        val sb = StringBuilder()
        sb.append("Provenance:\n")
        sb.append("  Origin: $originatingExperienceId (${experienceSource})\n")
        sb.append("  Experience time: ${java.time.Instant.ofEpochMilli(experienceTimestamp)}\n")
        sb.append("  Original confidence: ${String.format("%.2f", experienceConfidence)}\n")
        sb.append("  Current confidence: ${String.format("%.2f", currentConfidence)}\n")
        sb.append("  Source reliability: ${String.format("%.2f", sourceReliability)}\n")
        sb.append("  Verified: $verified")
        if (verified) {
            sb.append(" at ${java.time.Instant.ofEpochMilli(verifiedAt)} via $verificationMethod")
        }
        sb.append("\n")
        if (derivationHistory.isNotEmpty()) {
            sb.append("  Derivation history:\n")
            for ((i, step) in derivationHistory.withIndex()) {
                sb.append("    ${i + 1}. ${step.stepType.name}: ${step.description}\n")
                sb.append("       Confidence change: ${String.format("%.2f", step.confidenceBefore)} -> ${String.format("%.2f", step.confidenceAfter)}\n")
                sb.append("       Timestamp: ${java.time.Instant.ofEpochMilli(step.timestamp)}\n")
            }
        }
        return sb.toString()
    }
}

/** A single step in memory derivation/evolution */
data class DerivationStep(
    /** Type of derivation step */
    val stepType: DerivationStepType,

    /** Description of what happened */
    val description: String,

    /** Confidence before this step */
    val confidenceBefore: Float,

    /** Confidence after this step */
    val confidenceAfter: Float,

    /** Timestamp of this step */
    val timestamp: Long = System.currentTimeMillis(),

    /** Source of this derivation (user, system, inference, etc.) */
    val source: String = "system",

    /** Related memory IDs involved in this step */
    val relatedMemoryIds: List<String> = emptyList(),

    /** Additional metadata */
    val metadata: Map<String, String> = emptyMap()
)

enum class DerivationStepType {
    INITIAL_CONSOLIDATION,     // First promotion from candidate
    SUPERSESSION,              // Superseded by newer memory
    UPDATE,                    // Updated with new information
    CONFLICT_RESOLUTION,       // Resolved from conflict
    MERGE,                     // Merged with another memory
    SPLIT,                     // Split from another memory
    VERIFICATION,              // Independently verified
    CORRECTION,                // Corrected by user/system
    DECAY,                     // Confidence decayed over time
    REINFORCEMENT,             // Reinforced by recurrence
    RECALL,                    // Recalled from archive
    ARCHIVAL                   // Moved to archive
}

/**
 * TemporalRelationship - Explicit temporal relationships between memories.
 *
 * Enables reconstruction of "what was true when" without losing history.
 */
data class TemporalRelationship(
    /** Unique ID for this relationship */
    val relationshipId: String = "rel_${System.currentTimeMillis()}_${(0..9).map { (Math.random() * 36).toInt().toChar() }.joinToString("")}",

    /** Source memory ID */
    val fromMemoryId: String,

    /** Target memory ID */
    val toMemoryId: String,

    /** Type of temporal relationship */
    val relationshipType: TemporalRelationshipType,

    /** When this relationship was established */
    val establishedAt: Long = System.currentTimeMillis(),

    /** Confidence in this relationship (0.0 - 1.0) */
    val confidence: Float = 1.0f,

    /** Evidence for this relationship */
    val evidence: List<String> = emptyList(),

    /** Optional: the experience that triggered this relationship */
    val triggeringExperienceId: String? = null,

    /** Optional: validity period (null = indefinite) */
    val validFrom: Long? = null,
    val validUntil: Long? = null
) {
    /**
     * The inverse relationship, swapping from/to and inverting the type.
     * E.g. A SUPERSEDES B → B SUPERSEDED_BY A.
     */
    fun inverse(): TemporalRelationship {
        val invertedType = when (relationshipType) {
            TemporalRelationshipType.BEFORE -> TemporalRelationshipType.AFTER
            TemporalRelationshipType.AFTER -> TemporalRelationshipType.BEFORE
            TemporalRelationshipType.DURING -> TemporalRelationshipType.DURING
            TemporalRelationshipType.CHANGED_TO -> TemporalRelationshipType.CHANGED_TO
            TemporalRelationshipType.SUPERSEDED_BY -> TemporalRelationshipType.SUPERSEDES
            TemporalRelationshipType.SUPERSEDES -> TemporalRelationshipType.SUPERSEDED_BY
            TemporalRelationshipType.DERIVED_FROM -> TemporalRelationshipType.SOURCE_OF
            TemporalRelationshipType.SOURCE_OF -> TemporalRelationshipType.DERIVED_FROM
            TemporalRelationshipType.CONCURRENT -> TemporalRelationshipType.CONCURRENT
            TemporalRelationshipType.CAUSED -> TemporalRelationshipType.CAUSED_BY
            TemporalRelationshipType.CAUSED_BY -> TemporalRelationshipType.CAUSED
            TemporalRelationshipType.CONTRADICTS -> TemporalRelationshipType.CONTRADICTED_BY
            TemporalRelationshipType.CONTRADICTED_BY -> TemporalRelationshipType.CONTRADICTS
        }
        return copy(
            relationshipId = "rel_inv_$relationshipId",
            fromMemoryId = toMemoryId,
            toMemoryId = fromMemoryId,
            relationshipType = invertedType
        )
    }
}

enum class TemporalRelationshipType(val label: String) {
    /** A happened before B */
    BEFORE("before"),

    /** A happened after B */
    AFTER("after"),

    /** A happened during B */
    DURING("during"),

    /** A changed to B (supersession) */
    CHANGED_TO("changed-to"),

    /** A was superseded by B */
    SUPERSEDED_BY("superseded-by"),

    /** B supersedes A (inverse of SUPERSEDED_BY) */
    SUPERSEDES("supersedes"),

    /** A was derived from B */
    DERIVED_FROM("derived-from"),

    /** B was derived from A (inverse of DERIVED_FROM) */
    SOURCE_OF("source-of"),

    /** A and B are concurrent/overlapping */
    CONCURRENT("concurrent"),

    /** A caused B */
    CAUSED("caused"),

    /** B was caused by A (inverse of CAUSED) */
    CAUSED_BY("caused-by"),

    /** A contradicts B */
    CONTRADICTS("contradicts"),

    /** B contradicts A (inverse of CONTRADICTS) */
    CONTRADICTED_BY("contradicted-by")
}

/**
 * ConsolidatedMemory - A fully consolidated memory with full provenance
 * and temporal relationships.
 */
data class ConsolidatedMemory(
    /** Unique memory ID */
    val memoryId: String = "mem_${System.currentTimeMillis()}_${(0..9).map { (Math.random() * 36).toInt().toChar() }.joinToString("")}",

    /** The memory content */
    val content: String,

    /** Memory type */
    val memoryType: MemoryType,

    /** Current lifecycle state */
    val lifecycleState: MemoryLifecycleState = MemoryLifecycleState.CONSOLIDATED,

    /** Full provenance */
    val provenance: MemoryProvenance,

    /** Temporal relationships to other memories */
    val temporalRelationships: List<TemporalRelationship> = emptyList(),

    /** Tags for retrieval */
    val tags: List<String> = emptyList(),

    /** Confidence in current truth (0.0 - 1.0) */
    val confidence: Float = 0.8f,

    /** Relevance score for current context (0.0 - 1.0) */
    val relevance: Float = 0.5f,

    /** Goal alignment score (0.0 - 1.0) */
    val goalAlignment: Float = 0.0f,

    /** Uncertainty level (0.0 - 1.0) */
    val uncertainty: Float = 0.0f,

    /** When this memory was created/consolidated */
    val createdAt: Long = System.currentTimeMillis(),

    /** When this memory was last updated */
    val updatedAt: Long = System.currentTimeMillis(),

    /** When this memory was last accessed */
    val lastAccessed: Long = System.currentTimeMillis(),

    /** Access count */
    val accessCount: Int = 0,

    /** If superseded, the ID of the superseding memory */
    val supersededBy: String? = null,

    /** If this supersedes another, the ID of the superseded memory */
    val supersedes: String? = null,

    /** If in conflict, the IDs of conflicting memories */
    val conflictIds: List<String> = emptyList(),

    /** Source of this memory */
    val source: String = "consolidation",

    /** Additional metadata */
    val metadata: Map<String, String> = emptyMap()
) {
    /** Create a copy with access updated */
    fun accessed(): ConsolidatedMemory = copy(
        lastAccessed = System.currentTimeMillis(),
        accessCount = accessCount + 1
    )

    /** Check if this memory represents current truth */
    fun isCurrentTruth(): Boolean =
        lifecycleState in setOf(MemoryLifecycleState.ACTIVE, MemoryLifecycleState.CONSOLIDATED) &&
        supersededBy == null

    /** Check if this memory is historical (superseded but preserved) */
    fun isHistorical(): Boolean = lifecycleState == MemoryLifecycleState.SUPERSEDED || supersededBy != null

    /** Check if this memory has unresolved conflicts */
    fun hasUnresolvedConflict(): Boolean =
        lifecycleState == MemoryLifecycleState.CONFLICTED || conflictIds.isNotEmpty()

    /** Get a summary for reconstruction */
    fun toReconstructionSummary(): String {
        val status = when {
            isCurrentTruth() -> "CURRENT"
            isHistorical() -> "HISTORICAL (superseded by $supersededBy)"
            hasUnresolvedConflict() -> "CONFLICTED (${conflictIds.size} conflicts)"
            else -> lifecycleState.name
        }
        return "Memory($memoryId: ${content.take(80)}...) [$status, conf=${String.format("%.2f", confidence)}]"
    }
}