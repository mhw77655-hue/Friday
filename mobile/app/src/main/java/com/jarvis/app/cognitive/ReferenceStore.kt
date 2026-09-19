package com.jarvis.app.cognitive

/**
 * Tracks recently-mentioned entities per conversation turn, ordered by recency
 * (most recent mention first). Updated by [CognitiveEngine] after each turn.
 *
 * The entities stored here are those extracted from the user's turn text by
 * the existing [IntentInference.extractEntities] seam (quoted strings,
 * capitalized proper-noun pairs). Future stories may enrich this with richer
 * NLP extraction.
 */
class ReferenceStore {

    /**
     * A single mention record: the entity extracted from a turn, tagged with
     * the turn index in which it appeared.
     */
    data class Mention(
        val entity: IntentInference.Entity,
        val turnIndex: Long,
        /** Topic segment id at the time of mention (set by [TopicTracker]). */
        val segmentId: Int = 0
    )

    // Backing list — most recent first (newer mentions are prepended).
    private val mentions = mutableListOf<Mention>()

    /**
     * Record the entities extracted from a single turn.  Newer entries are
     * prepended so that `mostRecent()` is O(1).
     *
     * @param segmentId The topic segment id at the time of this turn (from [TopicTracker]).
     */
    fun recordMentions(turnIndex: Long, entities: List<IntentInference.Entity>, segmentId: Int = 0) {
        for (entity in entities) {
            mentions.add(0, Mention(entity, turnIndex, segmentId))
        }
    }

    /**
     * Return the most recently mentioned entity, optionally filtered by type.
     * Returns `null` when no entities match.
     */
    fun mostRecent(typeFilter: IntentInference.EntityType? = null): IntentInference.Entity? {
        if (typeFilter == null) return mentions.firstOrNull()?.entity
        return mentions.firstOrNull { it.entity.type == typeFilter }?.entity
    }

    /**
     * All mention records in recency order (most recent first).
     */
    fun all(): List<Mention> = mentions.toList()
}
