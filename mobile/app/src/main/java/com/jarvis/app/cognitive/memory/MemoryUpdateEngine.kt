package com.jarvis.app.cognitive.memory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MemoryUpdateEngine - Handles memory updates and supersession without deleting history.
 *
 * When A is true, then B replaces A:
 * - A is marked SUPERSEDED with reference to B
 * - B is marked ACTIVE/CONSOLIDATED with reference to A
 * - Both preserved with full provenance
 * - System can explain the transition
 */
class MemoryUpdateEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    internal val consolidator: MemoryConsolidator,
    private val config: Config = Config(),
    private val onSupersession: (SupersessionEvent) -> Unit = {},
    private val onUpdate: (UpdateEvent) -> Unit = {}
) {

    data class Config(
        /** Minimum confidence for supersession to occur */
        val minSupersessionConfidence: Float = 0.7f,

        /** Whether to auto-detect supersession from conflicting updates */
        val autoDetectSupersession: Boolean = true,

        /** Whether superseded memories remain queryable */
        val keepSupersededQueryable: Boolean = true
    )

    /**
     * Update an existing memory with new information.
     * Creates a supersession relationship: old -> new.
     */
    fun supersedeMemory(
        oldMemoryId: String,
        newContent: String,
        newConfidence: Float,
        reason: String,
        triggeringExperienceId: String,
        source: String = "user_correction"
    ): SupersessionResult {
        val oldMemory = consolidator.getMemory(oldMemoryId) ?: return SupersessionResult.NOT_FOUND(oldMemoryId)

        if (!oldMemory.isCurrentTruth()) {
            return SupersessionResult.NOT_CURRENT_TRUTH(oldMemoryId, oldMemory.lifecycleState.name)
        }

        if (newConfidence < config.minSupersessionConfidence) {
            return SupersessionResult.INSUFFICIENT_CONFIDENCE(newConfidence, config.minSupersessionConfidence)
        }

        // Create new consolidated memory
        val newMemory = createSupersedingMemory(oldMemory, newContent, newConfidence, reason, triggeringExperienceId, source)

        // Update old memory to SUPERSEDED
        val supersededOld = oldMemory.copy(
            lifecycleState = MemoryLifecycleState.SUPERSEDED,
            supersededBy = newMemory.memoryId,
            updatedAt = System.currentTimeMillis(),
            confidence = oldMemory.confidence * 0.8f, // Reduce confidence for superseded
            metadata = oldMemory.metadata + ("supersessionReason" to reason) + ("supersededBy" to newMemory.memoryId)
        )

        // Update new memory to reference old
        val linkedNew = newMemory.copy(
            supersedes = oldMemoryId,
            provenance = newMemory.provenance.withDerivation(DerivationStep(
                stepType = DerivationStepType.SUPERSESSION,
                description = "Supersedes $oldMemoryId: $reason",
                confidenceBefore = oldMemory.confidence,
                confidenceAfter = newConfidence,
                source = source,
                relatedMemoryIds = listOf(oldMemoryId),
                metadata = mapOf("reason" to reason, "triggeringExperience" to triggeringExperienceId)
            ))
        )

        // Store both
        consolidator.consolidatedMemories[oldMemoryId] = supersededOld
        consolidator.consolidatedMemories[linkedNew.memoryId] = linkedNew

        // Add temporal relationship
        val relationship = TemporalRelationship(
            fromMemoryId = oldMemoryId,
            toMemoryId = linkedNew.memoryId,
            relationshipType = TemporalRelationshipType.SUPERSEDES,
            confidence = newConfidence,
            evidence = listOf(reason),
            triggeringExperienceId = triggeringExperienceId
        )
        addTemporalRelationship(supersededOld, relationship)
        addTemporalRelationship(linkedNew, relationship.inverse())

        // Notify
        val event = SupersessionEvent(
            oldMemoryId = oldMemoryId,
            newMemoryId = linkedNew.memoryId,
            reason = reason,
            triggeringExperienceId = triggeringExperienceId,
            timestamp = System.currentTimeMillis()
        )
        onSupersession(event)

        return SupersessionResult.SUCCESS(supersededOld, linkedNew)
    }

    /**
     * Update a memory's content without full supersession.
     * Used for corrections, additions, confidence updates.
     */
    fun updateMemory(
        memoryId: String,
        newContent: String? = null,
        newConfidence: Float? = null,
        newTags: List<String>? = null,
        newRelevance: Float? = null,
        newGoalAlignment: Float? = null,
        reason: String = "update",
        triggeringExperienceId: String? = null
    ): UpdateResult {
        val memory = consolidator.getMemory(memoryId) ?: return UpdateResult.NOT_FOUND(memoryId)

        val updatedContent = newContent ?: memory.content
        val updatedConfidence = newConfidence ?: memory.confidence
        val updatedTags = newTags ?: memory.tags
        val updatedRelevance = newRelevance ?: memory.relevance
        val updatedGoalAlignment = newGoalAlignment ?: memory.goalAlignment

        val updated = memory.copy(
            content = updatedContent,
            confidence = updatedConfidence.coerceIn(0.0f, 1.0f),
            tags = updatedTags,
            relevance = updatedRelevance.coerceIn(0.0f, 1.0f),
            goalAlignment = updatedGoalAlignment.coerceIn(0.0f, 1.0f),
            updatedAt = System.currentTimeMillis(),
            uncertainty = 1.0f - updatedConfidence.coerceIn(0.0f, 1.0f),
            metadata = memory.metadata + ("reason" to reason),
            provenance = memory.provenance.withDerivation(DerivationStep(
                stepType = DerivationStepType.UPDATE,
                description = "Updated: $reason",
                confidenceBefore = memory.confidence,
                confidenceAfter = updatedConfidence.coerceIn(0.0f, 1.0f),
                source = "system",
                relatedMemoryIds = triggeringExperienceId?.let { listOf(it) } ?: emptyList(),
                metadata = mapOf("reason" to reason)
            ))
        )

        consolidator.consolidatedMemories[memoryId] = updated

        val event = UpdateEvent(
            memoryId = memoryId,
            reason = reason,
            fieldsChanged = buildChangedFields(memory, updated),
            timestamp = System.currentTimeMillis()
        )
        onUpdate(event)

        return UpdateResult.SUCCESS(updated)
    }

    /**
     * Reinforce a memory (increase confidence from recurrence).
     */
    fun reinforceMemory(memoryId: String, experienceId: String, confidenceBoost: Float = 0.1f): ReinforceResult {
        val memory = consolidator.getMemory(memoryId) ?: return ReinforceResult.NOT_FOUND(memoryId)

        val newConfidence = (memory.confidence + confidenceBoost).coerceIn(0.0f, 1.0f)

        val reinforced = memory.copy(
            confidence = newConfidence,
            uncertainty = 1.0f - newConfidence,
            updatedAt = System.currentTimeMillis(),
            provenance = memory.provenance.withDerivation(DerivationStep(
                stepType = DerivationStepType.REINFORCEMENT,
                description = "Reinforced by recurring experience $experienceId",
                confidenceBefore = memory.confidence,
                confidenceAfter = newConfidence,
                source = "recurrence",
                relatedMemoryIds = listOf(experienceId)
            ))
        )

        consolidator.consolidatedMemories[memoryId] = reinforced

        return ReinforceResult.SUCCESS(reinforced, confidenceBoost)
    }

    /**
     * Decay a memory's confidence over time.
     */
    fun decayMemory(memoryId: String, decayRate: Float = 0.01f): DecayResult {
        val memory = consolidator.getMemory(memoryId) ?: return DecayResult.NOT_FOUND(memoryId)

        // Don't decay current truths below a floor
        val floor = if (memory.isCurrentTruth()) 0.3f else 0.1f
        val newConfidence = maxOf(memory.confidence - decayRate, floor)

        val decayed = memory.copy(
            confidence = newConfidence,
            uncertainty = 1.0f - newConfidence,
            updatedAt = System.currentTimeMillis(),
            provenance = memory.provenance.withDerivation(DerivationStep(
                stepType = DerivationStepType.DECAY,
                description = "Confidence decayed over time",
                confidenceBefore = memory.confidence,
                confidenceAfter = newConfidence,
                source = "time"
            ))
        )

        consolidator.consolidatedMemories[memoryId] = decayed

        return DecayResult.SUCCESS(decayed, memory.confidence - newConfidence)
    }

    /**
     * Create a temporal relationship between two memories.
     */
    fun createTemporalRelationship(
        fromMemoryId: String,
        toMemoryId: String,
        relationshipType: TemporalRelationshipType,
        confidence: Float = 1.0f,
        evidence: List<String> = emptyList(),
        triggeringExperienceId: String? = null
    ): TemporalRelationshipResult {
        val fromMem = consolidator.getMemory(fromMemoryId) ?: return TemporalRelationshipResult.NOT_FOUND(fromMemoryId)
        val toMem = consolidator.getMemory(toMemoryId) ?: return TemporalRelationshipResult.NOT_FOUND(toMemoryId)

        val relationship = TemporalRelationship(
            fromMemoryId = fromMemoryId,
            toMemoryId = toMemoryId,
            relationshipType = relationshipType,
            confidence = confidence,
            evidence = evidence,
            triggeringExperienceId = triggeringExperienceId
        )

        addTemporalRelationship(fromMem, relationship)
        addTemporalRelationship(toMem, relationship.inverse())

        return TemporalRelationshipResult.SUCCESS(relationship)
    }

    private fun addTemporalRelationship(memory: ConsolidatedMemory, relationship: TemporalRelationship) {
        val updated = memory.copy(
            temporalRelationships = memory.temporalRelationships + relationship
        )
        consolidator.consolidatedMemories[memory.memoryId] = updated
    }

    /** Create a new memory that supersedes an old one */
    private fun createSupersedingMemory(
        oldMemory: ConsolidatedMemory,
        newContent: String,
        newConfidence: Float,
        reason: String,
        triggeringExperienceId: String,
        source: String
    ): ConsolidatedMemory {
        val provenance = MemoryProvenance(
            originatingExperienceId = triggeringExperienceId,
            experienceSource = source,
            experienceTimestamp = System.currentTimeMillis(),
            experienceConfidence = newConfidence,
            derivationHistory = listOf(
                DerivationStep(
                    stepType = DerivationStepType.INITIAL_CONSOLIDATION,
                    description = "Created as supersession of ${oldMemory.memoryId}",
                    confidenceBefore = 0f,
                    confidenceAfter = newConfidence,
                    source = source,
                    relatedMemoryIds = listOf(oldMemory.memoryId),
                    metadata = mapOf("supersessionReason" to reason, "supersededMemory" to oldMemory.memoryId)
                ),
                DerivationStep(
                    stepType = DerivationStepType.SUPERSESSION,
                    description = "Supersedes ${oldMemory.memoryId}: $reason",
                    confidenceBefore = oldMemory.confidence,
                    confidenceAfter = newConfidence,
                    source = source,
                    relatedMemoryIds = listOf(oldMemory.memoryId),
                    metadata = mapOf("reason" to reason)
                )
            ),
            currentConfidence = newConfidence,
            sourceReliability = if (source == "user_correction") 0.95f else 0.8f
        )

        return ConsolidatedMemory(
            content = newContent,
            memoryType = oldMemory.memoryType,
            // Guarantee a strictly-later createdAt than the superseded memory so
            // temporal ordering ("what was true when") is deterministic even when
            // both are created within the same millisecond.
            createdAt = maxOf(System.currentTimeMillis(), oldMemory.createdAt + 1),
            lifecycleState = MemoryLifecycleState.CONSOLIDATED,
            provenance = provenance,
            tags = oldMemory.tags,
            confidence = newConfidence,
            relevance = 0.7f, // Superseding memories are highly relevant
            goalAlignment = oldMemory.goalAlignment,
            uncertainty = 1.0f - newConfidence,
            source = "supersession",
            supersedes = oldMemory.memoryId,
            metadata = mapOf(
                "supersessionReason" to reason,
                "supersededMemory" to oldMemory.memoryId,
                "triggeringExperience" to triggeringExperienceId
            )
        )
    }

    private fun buildChangedFields(old: ConsolidatedMemory, updated: ConsolidatedMemory): List<String> {
        val changes = mutableListOf<String>()
        if (old.content != updated.content) changes.add("content")
        if (old.confidence != updated.confidence) changes.add("confidence")
        if (old.tags != updated.tags) changes.add("tags")
        if (old.relevance != updated.relevance) changes.add("relevance")
        if (old.goalAlignment != updated.goalAlignment) changes.add("goalAlignment")
        if (old.lifecycleState != updated.lifecycleState) changes.add("lifecycleState")
        return changes
    }

    /** Result types */
    sealed interface SupersessionResult {
        data class SUCCESS(val superseded: ConsolidatedMemory, val superseding: ConsolidatedMemory) : SupersessionResult
        data class NOT_FOUND(val memoryId: String) : SupersessionResult
        data class NOT_CURRENT_TRUTH(val memoryId: String, val currentState: String) : SupersessionResult
        data class INSUFFICIENT_CONFIDENCE(val provided: Float, val required: Float) : SupersessionResult
    }

    sealed interface UpdateResult {
        data class SUCCESS(val updated: ConsolidatedMemory) : UpdateResult
        data class NOT_FOUND(val memoryId: String) : UpdateResult
    }

    sealed interface ReinforceResult {
        data class SUCCESS(val reinforced: ConsolidatedMemory, val boost: Float) : ReinforceResult
        data class NOT_FOUND(val memoryId: String) : ReinforceResult
    }

    sealed interface DecayResult {
        data class SUCCESS(val decayed: ConsolidatedMemory, val decayAmount: Float) : DecayResult
        data class NOT_FOUND(val memoryId: String) : DecayResult
    }

    sealed interface TemporalRelationshipResult {
        data class SUCCESS(val relationship: TemporalRelationship) : TemporalRelationshipResult
        data class NOT_FOUND(val memoryId: String) : TemporalRelationshipResult
    }

    /** Event types */
    data class SupersessionEvent(
        val oldMemoryId: String,
        val newMemoryId: String,
        val reason: String,
        val triggeringExperienceId: String,
        val timestamp: Long
    )

    data class UpdateEvent(
        val memoryId: String,
        val reason: String,
        val fieldsChanged: List<String>,
        val timestamp: Long
    )

    /** Extension for inverse relationship */
    private fun TemporalRelationship.inverse(): TemporalRelationship {
        val inverseType = when (relationshipType) {
            TemporalRelationshipType.BEFORE -> TemporalRelationshipType.AFTER
            TemporalRelationshipType.AFTER -> TemporalRelationshipType.BEFORE
            TemporalRelationshipType.DURING -> TemporalRelationshipType.DURING
            TemporalRelationshipType.CHANGED_TO -> TemporalRelationshipType.SUPERSEDED_BY
            TemporalRelationshipType.SUPERSEDED_BY -> TemporalRelationshipType.CHANGED_TO
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
            fromMemoryId = toMemoryId,
            toMemoryId = fromMemoryId,
            relationshipType = inverseType
        )
    }
}