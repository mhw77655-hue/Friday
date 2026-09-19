package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryType
import com.jarvis.app.cognitive.CognitiveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * ContinuityManager - Reconstructs current cognitive state from memory system.
 *
 * Given:
 * - Current situation (user, goal, entities)
 * - Relevant memories from consolidated store
 *
 * Produces:
 * - CURRENT RECONSTRUCTED STATE containing:
 *   - Active truths (current beliefs)
 *   - Superseded truths when relevant (historical context)
 *   - Unresolved conflicts
 *   - Relevant historical context
 *   - Provenance/confidence for each
 *
 * Does NOT dump the whole memory store - bounded, focused reconstruction.
 */
class ContinuityManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val consolidator: MemoryConsolidator,
    private val updateEngine: MemoryUpdateEngine,
    private val conflictResolver: MemoryConflictResolver,
    private val config: Config = Config()
) : ContinuityPort {

    /** ContinuityPort: bounded reconstruction for the cognitive seam. */
    override suspend fun reconstructContinuity(
        query: String,
        currentGoal: String?,
        activeEntities: List<String>
    ): ContinuitySnapshot {
        val state = reconstructCurrentState(query, currentGoal, activeEntities)
        return ContinuitySnapshot(
            activeTruths = state.activeTruths.map {
                TruthEntry(it.content, it.memoryType, it.confidence, it.provenance.originatingExperienceId)
            },
            superseded = state.historicalContext.map {
                TruthEntry(it.content, it.memoryType, it.confidence, it.provenance.originatingExperienceId)
            },
            conflicts = state.unresolvedConflicts.map {
                ConflictEntry(it.conflictDetails, it.involvedMemoryIds, it.confidence)
            },
            unknowns = state.unknowns.map { it.description },
            overallConfidence = state.overallConfidence
        )
    }

    data class Config(
        /** Maximum memories to include in reconstruction */
        val maxMemories: Int = 50,

        /** Maximum historical memories to include */
        val maxHistorical: Int = 10,

        /** Maximum conflicts to include */
        val maxConflicts: Int = 5,

        /** Minimum confidence for inclusion */
        val minConfidence: Float = 0.3f,

        /** Include provenance in output */
        val includeProvenance: Boolean = true,

        /** Include temporal relationships */
        val includeTemporalRelationships: Boolean = true
    )

    /**
     * Reconstruct current state for a cognitive context.
     *
     * This is the main entry point for CognitiveContextBuilder.
     */
    suspend fun reconstructCurrentState(
        query: String,
        currentGoal: String? = null,
        activeEntities: List<String> = emptyList(),
        sessionContext: Map<String, String> = emptyMap()
    ): ContinuityState {
        // 1. Retrieve relevant consolidated memories
        val allMemories = consolidator.getAllConsolidated()

        // 2. Score and filter by relevance
        val scoredMemories = allMemories.map { mem ->
            val score = calculateRelevanceScore(mem, query, currentGoal, activeEntities)
            mem to score
        }.filter { it.second >= config.minConfidence }
            .sortedByDescending { it.second }
            .take(config.maxMemories)

        // 3. Separate into current truths, historical, conflicts
        val currentTruths = scoredMemories.filter { it.first.isCurrentTruth() }.map { it.first }
        val historical = scoredMemories.filter { it.first.isHistorical() }.map { it.first }.take(config.maxHistorical)
        val conflicted = scoredMemories.filter { it.first.hasUnresolvedConflict() }.map { it.first }.take(config.maxConflicts)

        // 4. Build active truths with provenance
        val activeTruths = currentTruths.map { buildActiveTruth(it) }

        // 5. Build historical context (superseded but relevant)
        val historicalContext = historical.map { buildHistoricalContext(it) }

        // 6. Build unresolved conflicts
        val unresolvedConflicts = conflicted.map { buildConflictSummary(it) }

        // 7. Build temporal narrative for key entities
        val temporalNarratives = buildTemporalNarratives(currentTruths + historical, activeEntities)

        // 8. Detect gaps/unknowns
        val unknowns = detectUnknowns(query, currentGoal, activeEntities, currentTruths)

        // 9. Calculate overall confidence
        val overallConfidence = calculateOverallConfidence(activeTruths)

        return ContinuityState(
            query = query,
            currentGoal = currentGoal,
            activeEntities = activeEntities,
            activeTruths = activeTruths,
            historicalContext = historicalContext,
            unresolvedConflicts = unresolvedConflicts,
            temporalNarratives = temporalNarratives,
            unknowns = unknowns,
            overallConfidence = overallConfidence,
            reconstructedAt = System.currentTimeMillis(),
            memoryCount = scoredMemories.size
        )
    }

    /**
     * Get a quick continuity summary for a specific entity or topic.
     */
    suspend fun getContinuitySummary(entityOrTopic: String): ContinuitySummary {
        val memories = consolidator.getAllConsolidated().filter { mem ->
            mem.content.lowercase().contains(entityOrTopic.lowercase()) ||
            mem.tags.any { it.lowercase().contains(entityOrTopic.lowercase()) }
        }.take(20)

        val currentTruths = memories.filter { it.isCurrentTruth() }
        val historical = memories.filter { it.isHistorical() }
        val conflicted = memories.filter { it.hasUnresolvedConflict() }

        return ContinuitySummary(
            topic = entityOrTopic,
            currentTruthCount = currentTruths.size,
            historicalCount = historical.size,
            conflictCount = conflicted.size,
            latestTruth = currentTruths.maxByOrNull { it.updatedAt }?.content,
            oldestMemory = memories.minByOrNull { it.createdAt }?.let { "Created ${java.time.Instant.ofEpochMilli(it.createdAt)}" },
            mostRecentUpdate = memories.maxByOrNull { it.updatedAt }?.let { "Updated ${java.time.Instant.ofEpochMilli(it.updatedAt)}" },
            averageConfidence = if (memories.isNotEmpty()) memories.map { it.confidence }.average() else 0.0,
            hasUnresolvedConflicts = conflicted.isNotEmpty()
        )
    }

    /**
     * Explain a specific memory's provenance and history.
     */
    fun explainMemory(memoryId: String): MemoryExplanation? {
        val memory = consolidator.getMemory(memoryId) ?: return null

        val superseded = memory.supersededBy?.let { consolidator.getMemory(it) }
        val supersedes = memory.supersedes?.let { consolidator.getMemory(it) }
        val conflicts = memory.conflictIds.mapNotNull { consolidator.getMemory(it) }

        return MemoryExplanation(
            memory = memory,
            supersededBy = superseded,
            supersedes = supersedes,
            relatedConflicts = conflicts,
            provenance = if (config.includeProvenance) memory.provenance.toProvenanceString() else "Provenance not included",
            temporalRelationships = if (config.includeTemporalRelationships) {
                memory.temporalRelationships.map { rel ->
                    "${rel.relationshipType.name} ${rel.toMemoryId} (conf: ${String.format("%.2f", rel.confidence)})"
                }.joinToString("\n")
            } else "Temporal relationships not included"
        )
    }

    /**
     * Get the full temporal chain for a topic (what was believed when).
     */
    fun getTemporalChain(topic: String): List<TemporalChainLink> {
        val memories = consolidator.getAllConsolidated().filter { mem ->
            mem.content.lowercase().contains(topic.lowercase()) ||
            mem.tags.any { it.lowercase().contains(topic.lowercase()) }
        }.sortedBy { it.createdAt }

        val chain = mutableListOf<TemporalChainLink>()

        for (mem in memories) {
            val link = TemporalChainLink(
                memoryId = mem.memoryId,
                content = mem.content,
                confidence = mem.confidence,
                timestamp = mem.createdAt,
                state = when {
                    mem.isCurrentTruth() -> ChainState.CURRENT
                    mem.isHistorical() -> ChainState.SUPERSEDED
                    mem.hasUnresolvedConflict() -> ChainState.CONFLICTED
                    else -> ChainState.ARCHIVED
                },
                supersededBy = mem.supersededBy,
                supersedes = mem.supersedes,
                provenance = mem.provenance
            )
            chain.add(link)
        }

        return chain
    }

    // ================================================================
    // Private helper methods
    // ================================================================

    private fun calculateRelevanceScore(
        memory: ConsolidatedMemory,
        query: String,
        currentGoal: String?,
        activeEntities: List<String>
    ): Float {
        var score = memory.confidence * 0.3f + memory.relevance * 0.2f

        // Query match
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        val memWords = memory.content.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        if (queryWords.isNotEmpty()) {
            val overlap = queryWords.intersect(memWords).size
            score += (overlap.toFloat() / queryWords.size) * 0.3f
        }

        // Goal alignment
        if (currentGoal != null) {
            val goalWords = currentGoal.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
            if (goalWords.isNotEmpty()) {
                val overlap = goalWords.intersect(memWords).size
                score += (overlap.toFloat() / goalWords.size) * 0.15f
            }
        }

        // Entity match
        if (activeEntities.isNotEmpty()) {
            val entityMatches = activeEntities.count { entity ->
                memory.content.lowercase().contains(entity.lowercase()) ||
                memory.tags.any { it.lowercase().contains(entity.lowercase()) }
            }
            score += (entityMatches.toFloat() / activeEntities.size) * 0.15f
        }

        // Recency boost for current truths
        if (memory.isCurrentTruth()) {
            val hoursAgo = (System.currentTimeMillis() - memory.updatedAt) / (1000 * 60 * 60)
            if (hoursAgo < 24) score += 0.1f * (1f - hoursAgo / 24f)
        }

        return score.coerceIn(0.0f, 1.0f)
    }

    private fun buildActiveTruth(memory: ConsolidatedMemory): ActiveTruth {
        return ActiveTruth(
            memoryId = memory.memoryId,
            content = memory.content,
            memoryType = memory.memoryType,
            confidence = memory.confidence,
            relevance = memory.relevance,
            goalAlignment = memory.goalAlignment,
            uncertainty = memory.uncertainty,
            provenance = if (config.includeProvenance) memory.provenance else MemoryProvenance(
                originatingExperienceId = "",
                experienceSource = "",
                experienceTimestamp = 0,
                experienceConfidence = 0f,
                currentConfidence = memory.confidence
            ),
            tags = memory.tags,
            lastUpdated = memory.updatedAt,
            accessCount = memory.accessCount
        )
    }

    private fun buildHistoricalContext(memory: ConsolidatedMemory): HistoricalContext {
        val superseding = memory.supersededBy?.let { consolidator.getMemory(it) }

        return HistoricalContext(
            memoryId = memory.memoryId,
            content = memory.content,
            memoryType = memory.memoryType,
            confidence = memory.confidence,
            supersededBy = superseding?.memoryId,
            supersedingContent = superseding?.content,
            supersessionReason = memory.metadata["supersessionReason"],
            supersededAt = superseding?.createdAt ?: memory.updatedAt,
            provenance = if (config.includeProvenance) memory.provenance else MemoryProvenance(
                originatingExperienceId = "",
                experienceSource = "",
                experienceTimestamp = 0,
                experienceConfidence = 0f,
                currentConfidence = memory.confidence
            )
        )
    }

    private fun buildConflictSummary(memory: ConsolidatedMemory): ConflictSummary {
        // Get the actual conflict details from the first conflict ID
        val conflictDetails = memory.conflictIds.firstOrNull()?.let { conflictId ->
            // In real implementation, would look up conflict object
            "Conflict with ${memory.conflictIds.size} other memories"
        } ?: "Unspecified conflict"

        return ConflictSummary(
            memoryId = memory.memoryId,
            content = memory.content,
            conflictCount = memory.conflictIds.size,
            conflictDetails = conflictDetails,
            confidence = memory.confidence,
            involvedMemoryIds = memory.conflictIds
        )
    }

    private fun buildTemporalNarratives(
        memories: List<ConsolidatedMemory>,
        activeEntities: List<String>
    ): List<TemporalNarrative> {
        val narratives = mutableListOf<TemporalNarrative>()

        // Group by entity/topic
        val entityGroups = mutableMapOf<String, MutableList<ConsolidatedMemory>>()
        for (mem in memories) {
            for (entity in activeEntities) {
                if (mem.content.lowercase().contains(entity.lowercase()) ||
                    mem.tags.any { it.lowercase().contains(entity.lowercase()) }) {
                    entityGroups.getOrPut(entity) { mutableListOf() }.add(mem)
                }
            }
        }

        for ((entity, entityMemories) in entityGroups) {
            val sorted = entityMemories.sortedBy { it.createdAt }
            val narrative = TemporalNarrative(
                entity = entity,
                timeline = sorted.map { mem ->
                    TimelineEvent(
                        timestamp = mem.createdAt,
                        content = mem.content,
                        state = when {
                            mem.isCurrentTruth() -> "CURRENT"
                            mem.isHistorical() -> "SUPERSEDED"
                            mem.hasUnresolvedConflict() -> "CONFLICTED"
                            else -> "ARCHIVED"
                        },
                        confidence = mem.confidence,
                        supersededBy = mem.supersededBy
                    )
                },
                currentBelief = sorted.lastOrNull { it.isCurrentTruth() }?.content,
                previousBelief = sorted.filter { it.isHistorical() }.lastOrNull()?.content
            )
            narratives.add(narrative)
        }

        return narratives
    }

    private fun detectUnknowns(
        query: String,
        currentGoal: String?,
        activeEntities: List<String>,
        currentTruths: List<ConsolidatedMemory>
    ): List<UnknownGap> {
        val unknowns = mutableListOf<UnknownGap>()

        // Check for entities with no memories
        for (entity in activeEntities) {
            val hasMemory = currentTruths.any { mem ->
                mem.content.lowercase().contains(entity.lowercase()) ||
                mem.tags.any { it.lowercase().contains(entity.lowercase()) }
            }
            if (!hasMemory) {
                unknowns.add(UnknownGap(
                    gapType = UnknownType.MISSING_ENTITY,
                    description = "No current memory for active entity: $entity",
                    impact = 0.5f,
                    relatedEntities = listOf(entity)
                ))
            }
        }

        // Check for goal with no supporting memories
        if (currentGoal != null) {
            val hasGoalMemory = currentTruths.any { mem ->
                mem.content.lowercase().contains(currentGoal.lowercase()) ||
                mem.tags.any { it.lowercase().contains(currentGoal.lowercase()) }
            }
            if (!hasGoalMemory) {
                unknowns.add(UnknownGap(
                    gapType = UnknownType.MISSING_GOAL_CONTEXT,
                    description = "No memory supporting current goal: $currentGoal",
                    impact = 0.6f,
                    relatedEntities = emptyList()
                ))
            }
        }

        // Check for contradictory memories flagged as conflicts
        val conflictedTruths = currentTruths.filter { it.hasUnresolvedConflict() }
        for (mem in conflictedTruths) {
            unknowns.add(UnknownGap(
                gapType = UnknownType.UNRESOLVED_CONFLICT,
                description = "Unresolved conflict in memory: ${mem.content.take(50)}...",
                impact = 0.7f,
                relatedEntities = mem.tags
            ))
        }

        // Check for low confidence memories on important topics
        val lowConfidence = currentTruths.filter { it.confidence < 0.5f }
        for (mem in lowConfidence.take(3)) {
            unknowns.add(UnknownGap(
                gapType = UnknownType.LOW_CONFIDENCE,
                description = "Low confidence memory (${String.format("%.2f", mem.confidence)}): ${mem.content.take(50)}...",
                impact = 1.0f - mem.confidence,
                relatedEntities = mem.tags
            ))
        }

        return unknowns
    }

    private fun calculateOverallConfidence(activeTruths: List<ActiveTruth>): Float {
        if (activeTruths.isEmpty()) return 0.0f
        return activeTruths.map { it.confidence }.average().toFloat()
    }
}

// ================================================================
// Output Data Classes
// ================================================================

/** Full reconstructed continuity state */
data class ContinuityState(
    val query: String,
    val currentGoal: String?,
    val activeEntities: List<String>,
    val activeTruths: List<ActiveTruth>,
    val historicalContext: List<HistoricalContext>,
    val unresolvedConflicts: List<ConflictSummary>,
    val temporalNarratives: List<TemporalNarrative>,
    val unknowns: List<UnknownGap>,
    val overallConfidence: Float,
    val reconstructedAt: Long,
    val memoryCount: Int
) {
    /** Get a compact summary for logging */
    fun summary(): String = "ContinuityState(query='${query.take(30)}', truths=${activeTruths.size}, " +
        "historical=${historicalContext.size}, conflicts=${unresolvedConflicts.size}, " +
        "unknowns=${unknowns.size}, confidence=${String.format("%.2f", overallConfidence)})"
}

/** Quick summary for a topic/entity */
data class ContinuitySummary(
    val topic: String,
    val currentTruthCount: Int,
    val historicalCount: Int,
    val conflictCount: Int,
    val latestTruth: String?,
    val oldestMemory: String?,
    val mostRecentUpdate: String?,
    val averageConfidence: Double,
    val hasUnresolvedConflicts: Boolean
)

/** Detailed explanation of a single memory */
data class MemoryExplanation(
    val memory: ConsolidatedMemory,
    val supersededBy: ConsolidatedMemory?,
    val supersedes: ConsolidatedMemory?,
    val relatedConflicts: List<ConsolidatedMemory>,
    val provenance: String,
    val temporalRelationships: String
)

/** Temporal chain for a topic */
data class TemporalChainLink(
    val memoryId: String,
    val content: String,
    val confidence: Float,
    val timestamp: Long,
    val state: ChainState,
    val supersededBy: String?,
    val supersedes: String?,
    val provenance: MemoryProvenance
)

enum class ChainState { CURRENT, SUPERSEDED, CONFLICTED, ARCHIVED }

/** An active (current) truth */
data class ActiveTruth(
    val memoryId: String,
    val content: String,
    val memoryType: MemoryType,
    val confidence: Float,
    val relevance: Float,
    val goalAlignment: Float,
    val uncertainty: Float,
    val provenance: MemoryProvenance,
    val tags: List<String>,
    val lastUpdated: Long,
    val accessCount: Int
)

/** Historical (superseded) context */
data class HistoricalContext(
    val memoryId: String,
    val content: String,
    val memoryType: MemoryType,
    val confidence: Float,
    val supersededBy: String?,
    val supersedingContent: String?,
    val supersessionReason: String?,
    val supersededAt: Long,
    val provenance: MemoryProvenance
)

/** Conflict summary */
data class ConflictSummary(
    val memoryId: String,
    val content: String,
    val conflictCount: Int,
    val conflictDetails: String,
    val confidence: Float,
    val involvedMemoryIds: List<String>
)

/** Temporal narrative for an entity */
data class TemporalNarrative(
    val entity: String,
    val timeline: List<TimelineEvent>,
    val currentBelief: String?,
    val previousBelief: String?
)

data class TimelineEvent(
    val timestamp: Long,
    val content: String,
    val state: String,
    val confidence: Float,
    val supersededBy: String?
)

/** Unknown gap in knowledge */
data class UnknownGap(
    val gapType: UnknownType,
    val description: String,
    val impact: Float,
    val relatedEntities: List<String>
)

enum class UnknownType {
    MISSING_ENTITY,
    MISSING_GOAL_CONTEXT,
    UNRESOLVED_CONFLICT,
    LOW_CONFIDENCE,
    TEMPORAL_GAP,
    CAUSAL_GAP
}