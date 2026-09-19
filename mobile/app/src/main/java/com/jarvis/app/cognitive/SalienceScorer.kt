package com.jarvis.app.cognitive

import kotlin.math.pow

/**
 * Computes composite salience scores for entities tracked by [ReferenceStore],
 * combining recency, frequency, and current-topic relevance signals.
 *
 * No LLM calls — pure heuristic scoring. No new entity extraction pipeline;
 * this story only scores/ranks entities [ReferenceStore] already tracks.
 */
class SalienceScorer(
    private val referenceStore: ReferenceStore,
    /**
     * Half-life in turns for the recency decay. After this many turns without
     * a mention, the recency component drops to 0.5.
     */
    val recencyHalfLife: Double = 5.0,
    /** Weight of the recency component in the composite score. */
    val recencyWeight: Double = 0.4,
    /** Weight of the frequency component in the composite score. */
    val frequencyWeight: Double = 0.3,
    /** Weight of the topic-relevance component in the composite score. */
    val topicWeight: Double = 0.3,
    /** Bonus added to the topic-relevance component when the entity belongs to the current segment. */
    val currentSegmentBonus: Double = 0.5,
    /** Penalty applied to the topic-relevance component when the entity belongs to a prior (closed) segment. */
    val priorSegmentPenalty: Double = 0.3
) {

    /**
     * Score a single entity. The score is a value in roughly [0, 1] range
     * (can exceed 1.0 with bonuses, but that's fine for ranking).
     *
     * @param entity The entity to score.
     * @param currentTurnIndex The latest turn index (used for recency calculation).
     * @param currentSegmentId The current topic segment id from [TopicTracker].
     */
    fun score(
        entity: IntentInference.Entity,
        currentTurnIndex: Long,
        currentSegmentId: Int
    ): Double {
        val mentions = referenceStore.all().filter { it.entity == entity }
        if (mentions.isEmpty()) return 0.0

        // Recency: exponential decay based on turns since last mention
        val lastMentionTurn = mentions.maxOf { it.turnIndex }
        val turnsSince = (currentTurnIndex - lastMentionTurn).toDouble()
        val recency = if (turnsSince <= 0.0) {
            1.0
        } else {
            0.5.pow(turnsSince / recencyHalfLife)
        }

        // Frequency: normalized by total turns so far (avoids unbounded growth)
        val frequency = mentions.size.toDouble() / currentTurnIndex.coerceAtLeast(1).toDouble()

        // Topic relevance: bonus if any mention is in the current segment, penalty if all are in prior segments
        val topicRelevance = computeTopicRelevance(mentions, currentSegmentId)

        return recencyWeight * recency +
            frequencyWeight * frequency +
            topicWeight * topicRelevance
    }

    /**
     * Return the [n] highest-scoring known entities in descending score order.
     * Returns fewer if fewer than [n] entities are known.
     *
     * @param n Maximum number of entities to return.
     * @param currentTurnIndex The latest turn index.
     * @param currentSegmentId The current topic segment id from [TopicTracker].
     */
    fun topSalient(
        n: Int,
        currentTurnIndex: Long,
        currentSegmentId: Int
    ): List<Pair<IntentInference.Entity, Double>> {
        // Collect all unique entities from the reference store
        val entities = referenceStore.all().map { it.entity }.distinct()
        if (entities.isEmpty()) return emptyList()

        // Score each entity and sort descending
        return entities
            .map { entity -> entity to score(entity, currentTurnIndex, currentSegmentId) }
            .sortedByDescending { it.second }
            .take(n)
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    /**
     * Compute topic-relevance score for an entity based on which segment(s)
     * it was mentioned in.
     *
     * - If any mention is in the current segment: [currentSegmentBonus]
     * - If all mentions are in a prior segment: -[priorSegmentPenalty]
     * - If segment data is unavailable (segment id = 0 for all): neutral (0.0)
     */
    private fun computeTopicRelevance(
        mentions: List<ReferenceStore.Mention>,
        currentSegmentId: Int
    ): Double {
        if (currentSegmentId == 0) return 0.0 // no segment info yet

        val inCurrentSegment = mentions.any { it.segmentId == currentSegmentId }
        val inPriorSegment = mentions.all { it.segmentId < currentSegmentId }

        return when {
            inCurrentSegment -> currentSegmentBonus
            inPriorSegment -> -priorSegmentPenalty
            else -> 0.0
        }
    }
}
