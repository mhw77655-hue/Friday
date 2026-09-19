package com.jarvis.app.cognitive.memory

import kotlin.math.abs


/**
 * MemoryConflict - Structured representation of a conflict between memories.
 *
 * When two memories conflict, we do NOT silently choose one.
 * Instead, we create a structured conflict containing:
 * - Competing memories
 * - Evidence for each
 * - Timestamps
 * - Source reliability
 * - Confidence
 * - Resolution state
 *
 * Possible outcomes: RESOLVED, UNRESOLVED, SUPERSEDED, UNKNOWN
 */
data class MemoryConflict(
    /** Unique conflict ID */
    val conflictId: String = "conf_${System.currentTimeMillis()}_${(0..9).map { (Math.random() * 36).toInt().toChar() }.joinToString("")}",

    /** First memory ID */
    val memoryAId: String,

    /** Second memory ID */
    val memoryBId: String,

    /** Content of memory A */
    val memoryAContent: String,

    /** Content of memory B */
    val memoryBContent: String,

    /** Type of conflict */
    val conflictType: ConflictType,

    /** Evidence supporting memory A */
    val evidenceA: List<String> = emptyList(),

    /** Evidence supporting memory B */
    val evidenceB: List<String> = emptyList(),

    /** Timestamp of memory A */
    val timestampA: Long,

    /** Timestamp of memory B */
    val timestampB: Long,

    /** Confidence in memory A (0.0 - 1.0) */
    val confidenceA: Float,

    /** Confidence in memory B (0.0 - 1.0) */
    val confidenceB: Float,

    /** Source reliability of memory A (0.0 - 1.0) */
    val sourceReliabilityA: Float,

    /** Source reliability of memory B (0.0 - 1.0) */
    val sourceReliabilityB: Float,

    /** Current resolution state */
    val resolutionState: ConflictResolutionState = ConflictResolutionState.UNRESOLVED,

    /** How the conflict was resolved (if resolved) */
    val resolution: ConflictResolution? = null,

    /** When this conflict was detected */
    val detectedAt: Long = System.currentTimeMillis(),

    /** When this conflict was resolved (if resolved) */
    val resolvedAt: Long = 0,

    /** Tags for categorization */
    val tags: List<String> = emptyList(),

    /** Additional metadata */
    val metadata: Map<String, String> = emptyMap()
) {
    /** Get a summary for logging/debugging */
    fun summary(): String = "MemoryConflict(id=$conflictId, type=${conflictType.name}, " +
        "A=$memoryAId[${String.format("%.2f", confidenceA)}], B=$memoryBId[${String.format("%.2f", confidenceB)}], " +
        "state=${resolutionState.name})"

    /** Check if this conflict is resolved */
    fun isResolved(): Boolean = resolutionState == ConflictResolutionState.RESOLVED

    /** Check if this conflict is unresolved */
    fun isUnresolved(): Boolean = resolutionState == ConflictResolutionState.UNRESOLVED

    /** Get the memory with higher confidence */
    fun getHigherConfidenceMemory(): Pair<String, Float> =
        if (confidenceA >= confidenceB) memoryAId to confidenceA else memoryBId to confidenceB

    /** Get the memory with higher source reliability */
    fun getHigherReliabilityMemory(): Pair<String, Float> =
        if (sourceReliabilityA >= sourceReliabilityB) memoryAId to sourceReliabilityA else memoryBId to sourceReliabilityB

    /** Get the more recent memory */
    fun getMoreRecentMemory(): Pair<String, Long> =
        if (timestampA >= timestampB) memoryAId to timestampA else memoryBId to timestampB
}

/** Types of memory conflicts */
enum class ConflictType(val label: String) {
    /** Direct content contradiction: "X is true" vs "X is false" */
    CONTENT_CONTRADICTION("Content contradiction"),

    /** Attribute contradiction: "X is red" vs "X is blue" */
    ATTRIBUTE_CONTRADICTION("Attribute contradiction"),

    /** Temporal contradiction: "X happened before Y" vs "X happened after Y" */
    TEMPORAL_CONTRADICTION("Temporal contradiction"),

    /** Source contradiction: different sources claim different things */
    SOURCE_CONTRADICTION("Source contradiction"),

    /** Preference contradiction: "User prefers A" vs "User prefers B" */
    PREFERENCE_CONTRADICTION("Preference contradiction"),

    /** Fact contradiction: "Fact: X" vs "Fact: not X" */
    FACT_CONTRADICTION("Fact contradiction"),

    /** Semantic contradiction: meanings conflict but not direct negation */
    SEMANTIC_CONTRADICTION("Semantic contradiction"),

    /** Duplicate with different confidence */
    DUPLICATE_DIVERGENCE("Duplicate with divergent confidence")
}

/** Conflict resolution state */
enum class ConflictResolutionState(val description: String) {
    /** Conflict detected, no resolution attempted */
    UNRESOLVED("Unresolved - awaiting resolution"),

    /** Resolution in progress */
    RESOLVING("Resolution in progress"),

    /** Resolved - one memory chosen, other superseded/rejected */
    RESOLVED("Resolved - decision made"),

    /** Resolved by supersession - newer memory supersedes older */
    SUPERSEDED("Resolved by supersession"),

    /** Resolved by merging - both memories partially true */
    MERGED("Resolved by merging"),

    /** Cannot be resolved with current information */
    UNKNOWN("Unknown - insufficient evidence"),

    /** Explicitly deferred for later resolution */
    DEFERRED("Deferred for later resolution")
}

/** Conflict resolution outcome */
data class ConflictResolution(
    /** How the conflict was resolved */
    val resolutionMethod: ResolutionMethod,

    /** The chosen memory ID (if one was chosen) */
    val chosenMemoryId: String? = null,

    /** The rejected/superseded memory ID (if applicable) */
    val rejectedMemoryId: String? = null,

    /** The merged memory ID (if merged) */
    val mergedMemoryId: String? = null,

    /** Reasoning for the resolution */
    val reasoning: String,

    /** Confidence in the resolution (0.0 - 1.0) */
    val resolutionConfidence: Float,

    /** Who/what resolved it */
    val resolvedBy: String,

    /** Evidence considered in resolution */
    val consideredEvidence: List<String> = emptyList(),

    /** Additional metadata */
    val metadata: Map<String, String> = emptyMap()
)

/** Methods for resolving conflicts */
enum class ResolutionMethod(val description: String) {
    /** Higher confidence wins */
    HIGHEST_CONFIDENCE("Highest confidence"),

    /** More recent wins (LWW) */
    LAST_WRITER_WINS("Last writer wins"),

    /** Higher source reliability wins */
    HIGHEST_RELIABILITY("Highest source reliability"),

    /** Explicit user instruction wins */
    USER_INSTRUCTION("User instruction"),

    /** Merge both memories */
    MERGE("Merge memories"),

    /** Supersession - newer replaces older */
    SUPERSESSION("Supersession"),

    /** Deferred for manual resolution */
    DEFERRED("Deferred"),

    /** Cannot determine */
    UNKNOWN("Unknown")
}

/**
 * MemoryConflictResolver - Resolves memory conflicts using deterministic policies.
 *
 * Does not fabricate certainty. Creates structured resolution with reasoning.
 */
class MemoryConflictResolver(
    private val updateEngine: MemoryUpdateEngine,
    private val config: Config = Config(),
    private val onResolution: (MemoryConflict, ConflictResolution) -> Unit = { _, _ -> }
) {

    data class Config(
        /** Default resolution method */
        val defaultMethod: ResolutionMethod = ResolutionMethod.HIGHEST_CONFIDENCE,

        /** Minimum confidence difference for automatic resolution */
        val minConfidenceDelta: Float = 0.15f,

        /** Minimum reliability difference for automatic resolution */
        val minReliabilityDelta: Float = 0.15f,

        /** Whether to auto-resolve conflicts */
        val autoResolve: Boolean = true,

        /** Whether to require user input for preference conflicts */
        val requireUserForPreferences: Boolean = true,

        /** Maximum auto-resolutions per cycle */
        val maxAutoResolutionsPerCycle: Int = 5
    )

    private var autoResolutionsThisCycle = 0

    /** Reset cycle counter */
    fun resetCycleCounter() {
        autoResolutionsThisCycle = 0
    }

    /** Resolve a conflict */
    fun resolve(conflict: MemoryConflict): ConflictResolutionResult {
        if (conflict.isResolved()) {
            return ConflictResolutionResult.ALREADY_RESOLVED(conflict.resolution!!)
        }

        // Check if we should auto-resolve
        val shouldAutoResolve = config.autoResolve &&
            autoResolutionsThisCycle < config.maxAutoResolutionsPerCycle &&
            canAutoResolve(conflict)

        val resolution = if (shouldAutoResolve) {
            autoResolutionsThisCycle++
            performAutoResolution(conflict)
        } else {
            // Defer or mark as unknown
            if (config.requireUserForPreferences && conflict.conflictType == ConflictType.PREFERENCE_CONTRADICTION) {
                createDeferredResolution(conflict, "Preference conflict requires user input")
            } else {
                createUnknownResolution(conflict, "Insufficient evidence for automatic resolution")
            }
        }

        // Apply resolution
        val updatedConflict = applyResolution(conflict, resolution)

        // Notify
        onResolution(updatedConflict, resolution)

        // Surface the outcome honestly: DEFERRED/UNKNOWN are not "resolved".
        return when (resolution.resolutionMethod) {
            ResolutionMethod.DEFERRED -> ConflictResolutionResult.DEFERRED(resolution.reasoning)
            ResolutionMethod.UNKNOWN -> ConflictResolutionResult.FAILED(resolution.reasoning)
            else -> ConflictResolutionResult.RESOLVED(resolution, updatedConflict)
        }
    }

    /** Check if conflict can be auto-resolved */
    private fun canAutoResolve(conflict: MemoryConflict): Boolean {
        // Strong confidence delta
        val confidenceDelta = abs(conflict.confidenceA - conflict.confidenceB)
        if (confidenceDelta >= config.minConfidenceDelta) return true

        // Strong reliability delta
        val reliabilityDelta = abs(conflict.sourceReliabilityA - conflict.sourceReliabilityB)
        if (reliabilityDelta >= config.minReliabilityDelta) return true

        // Explicit user instruction on one side
        val hasUserInstruction = conflict.evidenceA.any { it.contains("user", ignoreCase = true) } ||
            conflict.evidenceB.any { it.contains("user", ignoreCase = true) }
        if (hasUserInstruction) return true

        // Direct content/fact contradictions resolve deterministically by
        // recency tiebreak (determineResolutionMethod → LAST_WRITER_WINS).
        // This is an explicit, recorded policy — not a silent choice.
        if (conflict.conflictType in setOf(ConflictType.CONTENT_CONTRADICTION, ConflictType.FACT_CONTRADICTION)) {
            return true
        }

        // Clear temporal ordering for temporal contradictions
        if (conflict.conflictType == ConflictType.TEMPORAL_CONTRADICTION) {
            val timeDelta = abs(conflict.timestampA - conflict.timestampB)
            if (timeDelta > 60000) return true // > 1 minute difference
        }

        return false
    }

    /** Perform automatic resolution based on policy */
    private fun performAutoResolution(conflict: MemoryConflict): ConflictResolution {
        val method = determineResolutionMethod(conflict)

        return when (method) {
            ResolutionMethod.HIGHEST_CONFIDENCE -> resolveByConfidence(conflict)
            ResolutionMethod.LAST_WRITER_WINS -> resolveByRecency(conflict)
            ResolutionMethod.HIGHEST_RELIABILITY -> resolveByReliability(conflict)
            ResolutionMethod.USER_INSTRUCTION -> resolveByUserInstruction(conflict)
            ResolutionMethod.SUPERSESSION -> resolveBySupersession(conflict)
            else -> createUnknownResolution(conflict, "No applicable auto-resolution method")
        }
    }

    /** Determine which resolution method to use */
    private fun determineResolutionMethod(conflict: MemoryConflict): ResolutionMethod {
        // User instruction takes priority
        val aHasUser = conflict.evidenceA.any { it.contains("user", ignoreCase = true) }
        val bHasUser = conflict.evidenceB.any { it.contains("user", ignoreCase = true) }
        if (aHasUser || bHasUser) return ResolutionMethod.USER_INSTRUCTION

        // For temporal contradictions, use recency
        if (conflict.conflictType == ConflictType.TEMPORAL_CONTRADICTION) {
            return ResolutionMethod.LAST_WRITER_WINS
        }

        // For preference contradictions, defer to user
        if (conflict.conflictType == ConflictType.PREFERENCE_CONTRADICTION) {
            return ResolutionMethod.DEFERRED
        }

        // Check confidence delta
        val confidenceDelta = abs(conflict.confidenceA - conflict.confidenceB)
        if (confidenceDelta >= config.minConfidenceDelta) {
            return ResolutionMethod.HIGHEST_CONFIDENCE
        }

        // Check reliability delta
        val reliabilityDelta = abs(conflict.sourceReliabilityA - conflict.sourceReliabilityB)
        if (reliabilityDelta >= config.minReliabilityDelta) {
            return ResolutionMethod.HIGHEST_RELIABILITY
        }

        // Default to recency for supersession-like conflicts
        if (conflict.conflictType in setOf(ConflictType.CONTENT_CONTRADICTION, ConflictType.FACT_CONTRADICTION)) {
            return ResolutionMethod.LAST_WRITER_WINS
        }

        return config.defaultMethod
    }

    /** Resolve by choosing higher confidence */
    private fun resolveByConfidence(conflict: MemoryConflict): ConflictResolution {
        val (chosenId, chosenConfidence) = conflict.getHigherConfidenceMemory()
        val rejectedId = if (chosenId == conflict.memoryAId) conflict.memoryBId else conflict.memoryAId

        return ConflictResolution(
            resolutionMethod = ResolutionMethod.HIGHEST_CONFIDENCE,
            chosenMemoryId = chosenId,
            rejectedMemoryId = rejectedId,
            reasoning = "Chosen memory has higher confidence (${String.format("%.2f", chosenConfidence)})",
            resolutionConfidence = 0.7f + (chosenConfidence * 0.2f),
            resolvedBy = "auto_confidence",
            consideredEvidence = conflict.evidenceA + conflict.evidenceB
        )
    }

    /** Resolve by choosing more recent (LWW) */
    private fun resolveByRecency(conflict: MemoryConflict): ConflictResolution {
        val (chosenId, chosenTime) = conflict.getMoreRecentMemory()
        val rejectedId = if (chosenId == conflict.memoryAId) conflict.memoryBId else conflict.memoryAId

        return ConflictResolution(
            resolutionMethod = ResolutionMethod.LAST_WRITER_WINS,
            chosenMemoryId = chosenId,
            rejectedMemoryId = rejectedId,
            reasoning = "Chosen memory is more recent (${java.time.Instant.ofEpochMilli(chosenTime)})",
            resolutionConfidence = 0.75f,
            resolvedBy = "auto_recency",
            consideredEvidence = conflict.evidenceA + conflict.evidenceB
        )
    }

    /** Resolve by choosing higher source reliability */
    private fun resolveByReliability(conflict: MemoryConflict): ConflictResolution {
        val (chosenId, chosenReliability) = conflict.getHigherReliabilityMemory()
        val rejectedId = if (chosenId == conflict.memoryAId) conflict.memoryBId else conflict.memoryAId

        return ConflictResolution(
            resolutionMethod = ResolutionMethod.HIGHEST_RELIABILITY,
            chosenMemoryId = chosenId,
            rejectedMemoryId = rejectedId,
            reasoning = "Chosen memory has higher source reliability (${String.format("%.2f", chosenReliability)})",
            resolutionConfidence = 0.7f + (chosenReliability * 0.2f),
            resolvedBy = "auto_reliability",
            consideredEvidence = conflict.evidenceA + conflict.evidenceB
        )
    }

    /** Resolve by user instruction */
    private fun resolveByUserInstruction(conflict: MemoryConflict): ConflictResolution {
        val aHasUser = conflict.evidenceA.any { it.contains("user", ignoreCase = true) }
        val chosenId = if (aHasUser) conflict.memoryAId else conflict.memoryBId
        val rejectedId = if (chosenId == conflict.memoryAId) conflict.memoryBId else conflict.memoryAId

        return ConflictResolution(
            resolutionMethod = ResolutionMethod.USER_INSTRUCTION,
            chosenMemoryId = chosenId,
            rejectedMemoryId = rejectedId,
            reasoning = "User explicitly indicated preference",
            resolutionConfidence = 0.95f,
            resolvedBy = "auto_user_instruction",
            consideredEvidence = conflict.evidenceA + conflict.evidenceB
        )
    }

    /** Resolve by supersession (newer replaces older with relationship) */
    private fun resolveBySupersession(conflict: MemoryConflict): ConflictResolution {
        val (newerId, newerTime) = conflict.getMoreRecentMemory()
        val olderId = if (newerId == conflict.memoryAId) conflict.memoryBId else conflict.memoryAId

        return ConflictResolution(
            resolutionMethod = ResolutionMethod.SUPERSESSION,
            chosenMemoryId = newerId,
            rejectedMemoryId = olderId,
            reasoning = "Newer memory supersedes older via temporal supersession",
            resolutionConfidence = 0.8f,
            resolvedBy = "auto_supersession",
            consideredEvidence = conflict.evidenceA + conflict.evidenceB,
            metadata = mapOf("supersessionType" to "temporal")
        )
    }

    /** Create a deferred resolution */
    private fun createDeferredResolution(conflict: MemoryConflict, reason: String): ConflictResolution {
        return ConflictResolution(
            resolutionMethod = ResolutionMethod.DEFERRED,
            reasoning = reason,
            resolutionConfidence = 0.0f,
            resolvedBy = "deferred",
            consideredEvidence = conflict.evidenceA + conflict.evidenceB
        )
    }

    /** Create an unknown resolution */
    private fun createUnknownResolution(conflict: MemoryConflict, reason: String): ConflictResolution {
        return ConflictResolution(
            resolutionMethod = ResolutionMethod.UNKNOWN,
            reasoning = reason,
            resolutionConfidence = 0.0f,
            resolvedBy = "unknown",
            consideredEvidence = conflict.evidenceA + conflict.evidenceB
        )
    }

    /** Apply resolution to conflict and memories */
    private fun applyResolution(conflict: MemoryConflict, resolution: ConflictResolution): MemoryConflict {
        val updatedConflict = conflict.copy(
            resolutionState = resolution.resolutionMethod.toResolutionState(),
            resolution = resolution,
            resolvedAt = if (resolution.resolutionMethod != ResolutionMethod.DEFERRED &&
                resolution.resolutionMethod != ResolutionMethod.UNKNOWN) System.currentTimeMillis() else 0
        )

        // Apply to actual memories if auto-resolved
        when (resolution.resolutionMethod) {
            ResolutionMethod.HIGHEST_CONFIDENCE,
            ResolutionMethod.LAST_WRITER_WINS,
            ResolutionMethod.HIGHEST_RELIABILITY,
            ResolutionMethod.USER_INSTRUCTION,
            ResolutionMethod.SUPERSESSION -> {
                resolution.chosenMemoryId?.let { chosenId ->
                    resolution.rejectedMemoryId?.let { rejectedId ->
                        // Mark rejected as superseded
                        updateEngine.updateMemory(
                            memoryId = rejectedId,
                            newConfidence = 0.1f,
                            reason = "Superseded by conflict resolution: ${resolution.reasoning}",
                            triggeringExperienceId = conflict.conflictId
                        )
                        // Reinforce chosen
                        updateEngine.reinforceMemory(chosenId, conflict.conflictId, 0.1f)
                    }
                }
            }
            ResolutionMethod.MERGE -> {
                // Would create merged memory - not implemented in this version
            }
            ResolutionMethod.DEFERRED,
            ResolutionMethod.UNKNOWN -> {
                // No memory mutation for deferred/unknown resolutions
            }
        }

        return updatedConflict
    }

    /**
     * Get all unresolved conflicts.
     *
     * Conflict objects are recorded on the involved memories (`conflictIds`)
     * rather than in a separate registry, so this surfaces the conflicted
     * memory pairs as best-effort [MemoryConflict] descriptors. When the
     * memory for an involved ID is no longer present, the pair is skipped
     * (it has already been resolved/archived).
     */
    fun getUnresolvedConflicts(): List<MemoryConflict> {
        val conflicts = mutableListOf<MemoryConflict>()
        val byId = updateEngine.consolidator.getAllConsolidated().associateBy { it.memoryId }
        for (mem in byId.values.filter { it.hasUnresolvedConflict() }) {
            for (otherId in mem.conflictIds) {
                val other = byId[otherId] ?: continue
                conflicts.add(
                    MemoryConflict(
                        memoryAId = mem.memoryId,
                        memoryBId = otherId,
                        memoryAContent = mem.content,
                        memoryBContent = other.content,
                        conflictType = ConflictType.CONTENT_CONTRADICTION,
                        evidenceA = mem.provenance.derivationHistory.map { it.description },
                        evidenceB = other.provenance.derivationHistory.map { it.description },
                        timestampA = mem.createdAt,
                        timestampB = other.createdAt,
                        confidenceA = mem.confidence,
                        confidenceB = other.confidence,
                        sourceReliabilityA = mem.provenance.sourceReliability,
                        sourceReliabilityB = other.provenance.sourceReliability,
                        resolutionState = ConflictResolutionState.UNRESOLVED,
                        tags = (mem.tags + other.tags).distinct()
                    )
                )
            }
        }
        return conflicts.distinctBy { setOf(it.memoryAId, it.memoryBId) }
    }

    /** Result types */
    sealed interface ConflictResolutionResult {
        data class RESOLVED(val resolution: ConflictResolution, val updatedConflict: MemoryConflict) : ConflictResolutionResult
        data class ALREADY_RESOLVED(val resolution: ConflictResolution) : ConflictResolutionResult
        data class DEFERRED(val reason: String) : ConflictResolutionResult
        data class FAILED(val reason: String) : ConflictResolutionResult
    }

    /** Extension for ResolutionMethod -> ResolutionState */
    private fun ResolutionMethod.toResolutionState(): ConflictResolutionState = when (this) {
        ResolutionMethod.HIGHEST_CONFIDENCE,
        ResolutionMethod.LAST_WRITER_WINS,
        ResolutionMethod.HIGHEST_RELIABILITY,
        ResolutionMethod.USER_INSTRUCTION,
        ResolutionMethod.SUPERSESSION -> ConflictResolutionState.RESOLVED
        ResolutionMethod.MERGE -> ConflictResolutionState.MERGED
        ResolutionMethod.DEFERRED -> ConflictResolutionState.DEFERRED
        ResolutionMethod.UNKNOWN -> ConflictResolutionState.UNKNOWN
    }
}