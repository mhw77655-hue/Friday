package com.jarvis.app.cognitive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * AttentionEngine - Ranks what matters right now in the cognitive system.
 *
 * Computes attention scores based on:
 * - Salience (intrinsic importance)
 * - Urgency (time pressure)
 * - Goal relevance (alignment with current goal)
 * - Novelty (unexpectedness)
 * - Uncertainty (need for resolution)
 *
 * Produces a ranked attention spotlight for the cognitive cycle.
 */
class AttentionEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val config: Config = Config()
) {

    private val attentionItems = ConcurrentHashMap<String, AttentionItem>()
    private val attentionHistory = ConcurrentHashMap<String, AttentionHistoryEntry>()

    private val _spotlight = MutableStateFlow<AttentionSpotlight>(AttentionSpotlight())
    val spotlightFlow: StateFlow<AttentionSpotlight> = _spotlight.asStateFlow()

    private val _stats = MutableStateFlow<Stats>(Stats())
    val statsFlow: StateFlow<Stats> = _stats.asStateFlow()

    data class Config(
        val maxSpotlightSize: Int = 7,            // Miller's 7±2
        val salienceWeight: Float = 0.25f,
        val urgencyWeight: Float = 0.25f,
        val goalRelevanceWeight: Float = 0.25f,
        val noveltyWeight: Float = 0.15f,
        val uncertaintyWeight: Float = 0.10f,
        val recencyWeight: Float = 0.15f,
        val minScoreForSpotlight: Float = 0.3f,
        val historyWindowMs: Long = 300_000,     // 5 minutes
        val noveltyDecayMs: Long = 60_000        // Novelty decays over 1 minute
    )

    data class Stats(
        val totalItemsTracked: Int = 0,
        val currentSpotlightSize: Int = 0,
        val avgSpotlightScore: Float = 0.0f,
        val topItemId: String? = null,
        val topItemScore: Float = 0.0f
    )

    data class AttentionHistoryEntry(
        val itemId: String,
        val firstSeen: Long,
        val lastSeen: Long,
        val peakScore: Float,
        val timesInSpotlight: Int
    )

    /** Submit an attention item for ranking */
    fun submit(item: AttentionItem): AttentionResult {
        val existing = attentionItems[item.id]

        // Calculate novelty based on history
        val historyEntry = attentionHistory[item.id]
        val novelty = calculateNovelty(item, historyEntry)

        // Calculate attention score
        val score = calculateAttentionScore(item, novelty)

        val updatedItem = item.copy(
            novelty = novelty
        )

        attentionItems[item.id] = updatedItem

        // Update history
        val now = System.currentTimeMillis()
        val newHistory = historyEntry?.copy(
            lastSeen = now,
            peakScore = maxOf(historyEntry.peakScore, score),
            timesInSpotlight = historyEntry.timesInSpotlight + (if (score >= config.minScoreForSpotlight) 1 else 0)
        ) ?: AttentionHistoryEntry(
            itemId = item.id,
            firstSeen = now,
            lastSeen = now,
            peakScore = score,
            timesInSpotlight = if (score >= config.minScoreForSpotlight) 1 else 0
        )
        attentionHistory[item.id] = newHistory

        // Recompute spotlight
        recomputeSpotlight()

        return AttentionResult(
            itemId = item.id,
            score = score,
            inSpotlight = score >= config.minScoreForSpotlight,
            rank = getRank(item.id)
        )
    }

    /** Submit multiple items at once */
    fun submitAll(items: List<AttentionItem>): List<AttentionResult> {
        return items.map { submit(it) }
    }

    /** Get current spotlight (top N items) */
    fun getSpotlight(): AttentionSpotlight = _spotlight.value

    fun getStats(): Stats = _stats.value

    /** Get item by ID */
    fun getItem(itemId: String): AttentionItem? = attentionItems[itemId]

    /** Get all items sorted by score */
    fun getAllRanked(): List<RankedAttentionItem> {
        return attentionItems.values.map { item ->
            val history = attentionHistory[item.id]
            val novelty = calculateNovelty(item, history)
            val score = calculateAttentionScore(item, novelty)
            item to score
        }.sortedByDescending { it.second }
            .mapIndexed { index, (item, score) -> RankedAttentionItem(item, score, index + 1) }
    }

    /** Get items above threshold */
    fun getAboveThreshold(threshold: Float = 0.3f): List<RankedAttentionItem> =
        getAllRanked().filter { it.score >= threshold }

    /** Get rank of an item (1-based, 1 = highest) */
    fun getRank(itemId: String): Int {
        val ranked = getAllRanked()
        return ranked.indexOfFirst { it.item.id == itemId } + 1
    }

    /** Remove an item from attention */
    fun remove(itemId: String): Boolean {
        val removed = attentionItems.remove(itemId) != null
        if (removed) {
            recomputeSpotlight()
        }
        return removed
    }

    /** Clear all attention items */
    fun clear() {
        attentionItems.clear()
        attentionHistory.clear()
        recomputeSpotlight()
    }

    /** Recompute the attention spotlight */
    private fun recomputeSpotlight() {
        val ranked = getAllRanked()
        val spotlightItems = ranked
            .filter { it.score >= config.minScoreForSpotlight }
            .take(config.maxSpotlightSize)
            .map { it.item }

        val topItem = ranked.firstOrNull()
        val avgScore = if (ranked.isNotEmpty()) ranked.map { it.score }.average().toFloat() else 0f

        _spotlight.value = AttentionSpotlight(
            items = spotlightItems,
            timestamp = System.currentTimeMillis(),
            averageScore = avgScore,
            topItem = topItem?.item
        )

        _stats.value = Stats(
            totalItemsTracked = attentionItems.size,
            currentSpotlightSize = spotlightItems.size,
            avgSpotlightScore = if (spotlightItems.isNotEmpty()) spotlightItems.map { scoreForItem(it) }.average().toFloat() else 0f,
            topItemId = topItem?.item?.id,
            topItemScore = topItem?.score ?: 0f
        )
    }

    /** Calculate attention score for an item */
    private fun calculateAttentionScore(item: AttentionItem, novelty: Float): Float {
        val recency = calculateRecencyScore(item.timestamp)

        val uncertainty = item.metadata["uncertainty"]?.toFloatOrNull() ?: 0f

        return (item.salience * config.salienceWeight +
                item.urgency * config.urgencyWeight +
                item.goalRelevance * config.goalRelevanceWeight +
                novelty * config.noveltyWeight +
                uncertainty * config.uncertaintyWeight +
                recency * config.recencyWeight)
    }

    /** Calculate novelty score (0-1) */
    private fun calculateNovelty(item: AttentionItem, history: AttentionHistoryEntry?): Float {
        if (history == null) return 1.0f // Completely novel

        val timeSinceLastSeen = System.currentTimeMillis() - history.lastSeen
        val timeSinceFirstSeen = System.currentTimeMillis() - history.firstSeen

        // Novelty decays over time
        val recencyFactor = when {
            timeSinceLastSeen < config.noveltyDecayMs -> 1.0f
            timeSinceLastSeen < config.noveltyDecayMs * 2 -> 0.7f
            timeSinceLastSeen < config.noveltyDecayMs * 5 -> 0.4f
            timeSinceLastSeen < config.noveltyDecayMs * 10 -> 0.2f
            else -> 0.05f
        }

        // Items seen many times become less novel
        val frequencyFactor = when {
            history.timesInSpotlight == 0 -> 1.0f
            history.timesInSpotlight < 3 -> 0.7f
            history.timesInSpotlight < 10 -> 0.4f
            else -> 0.1f
        }

        return (recencyFactor + frequencyFactor) / 2
    }

    /** Calculate recency score (0-1) */
    private fun calculateRecencyScore(timestamp: Long): Float {
        val ageMs = System.currentTimeMillis() - timestamp
        val ageSeconds = ageMs / 1000.0
        return when {
            ageSeconds < 1 -> 1.0f
            ageSeconds < 10 -> 0.9f
            ageSeconds < 60 -> 0.7f
            ageSeconds < 300 -> 0.5f  // 5 minutes
            ageSeconds < 1800 -> 0.3f // 30 minutes
            else -> 0.1f
        }
    }

    /** Get score for a specific item */
    private fun scoreForItem(item: AttentionItem): Float {
        val history = attentionHistory[item.id]
        val novelty = calculateNovelty(item, history)
        return calculateAttentionScore(item, novelty)
    }

    /** Result of submitting an attention item */
    data class AttentionResult(
        val itemId: String,
        val score: Float,
        val inSpotlight: Boolean,
        val rank: Int
    )

    /** The current attention spotlight */
    data class AttentionSpotlight(
        val items: List<AttentionItem> = emptyList(),
        val timestamp: Long = System.currentTimeMillis(),
        val averageScore: Float = 0.0f,
        val topItem: AttentionItem? = null
    ) {
        fun isEmpty(): Boolean = items.isEmpty()
        fun size(): Int = items.size
    }

    /** Ranked attention item */
    data class RankedAttentionItem(
        val item: AttentionItem,
        val score: Float,
        val rank: Int
    )
}