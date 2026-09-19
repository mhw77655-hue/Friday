package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * WorkingMemory - Bounded, activation-based working memory for cognitive processing.
 *
 * Design principles:
 * - Bounded capacity (configurable max items)
 * - Activation-based retention (highly activated items stay)
 * - Decay over time (activation decreases)
 * - Priority/goal-aligned eviction (low priority + low activation evicted first)
 * - Relevance, recency, and uncertainty tracking
 * - Thread-safe for concurrent access
 */
class WorkingMemory(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val config: Config = Config(),
    private val onEviction: (ActiveMemory) -> Unit = {}
) {

    // In-memory storage
    private val items = ConcurrentHashMap<String, ActiveMemory>()
    private val accessOrder = ConcurrentHashMap<String, Long>() // For LRU tracking

    // State flows for observation
    private val _items = MutableStateFlow<List<ActiveMemory>>(emptyList())
    val itemsFlow: StateFlow<List<ActiveMemory>> = _items.asStateFlow()

    private val _stats = MutableStateFlow<Stats>(Stats())
    val statsFlow: StateFlow<Stats> = _stats.asStateFlow()

    // Event channel for memory changes
    private val _events = Channel<MemoryEvent>(capacity = 64)

    // Lifetime counters (source of truth for Stats — Stats is a plain value
    // snapshot, so totals must not be recomputed from _stats.value).
    private val insertCount = AtomicLong(0)
    private val accessCount = AtomicLong(0)
    private val evictionCount = AtomicLong(0)

    /** Configuration */
    data class Config(
        val maxItems: Int = 50,
        val baseDecayRate: Float = 0.05f,        // Per decay tick
        val decayIntervalMs: Long = 5000,        // How often to decay
        val minActivationForRetention: Float = 0.1f,
        val minRelevanceThreshold: Float = 0.3f, // Query relevance floor
        val goalAlignmentWeight: Float = 0.4f,
        val relevanceWeight: Float = 0.3f,
        val recencyWeight: Float = 0.2f,
        val uncertaintyWeight: Float = 0.1f,
        val activationBoostOnAccess: Float = 0.1f,
        val activationBoostOnGoalMatch: Float = 0.2f
    )

    /** Runtime statistics */
    data class Stats(
        val currentSize: Int = 0,
        val maxSize: Int = 0,
        val totalInsertions: Long = 0,
        val totalEvictions: Long = 0,
        val totalAccesses: Long = 0,
        val avgActivation: Float = 0.0f,
        val oldestItemAge: Long = 0,
        val newestItemAge: Long = 0
    )

    /** Memory events for observers */
    sealed interface MemoryEvent {
        data class Inserted(val item: ActiveMemory) : MemoryEvent
        data class Accessed(val item: ActiveMemory) : MemoryEvent
        data class Evicted(val item: ActiveMemory, val reason: EvictionReason) : MemoryEvent
        data class Decayed(val item: ActiveMemory, val oldActivation: Float, val newActivation: Float) : MemoryEvent
        data class Updated(val item: ActiveMemory) : MemoryEvent
        data class Cleared(val count: Int) : MemoryEvent
    }

    enum class EvictionReason {
        CAPACITY, LOW_ACTIVATION, LOW_RELEVANCE, LOW_GOAL_ALIGNMENT, HIGH_UNCERTAINTY, MANUAL
    }

    init {
        startDecayLoop()
    }

    /** Insert a new memory item into working memory */
    fun insert(item: ActiveMemory): InsertResult {
        val currentSize = items.size

        // Check if we need to evict first
        if (currentSize >= config.maxItems) {
            val evicted = evictOne(EvictionReason.CAPACITY)
            if (evicted == null) {
                return InsertResult.FAILED("Capacity full and no evictable items")
            }
        }

        val newItem = item.copy(
            lastAccessed = System.currentTimeMillis(),
            accessCount = 0,
            activation = minOf(item.activation, 1.0f)
        )

        items[newItem.id] = newItem
        accessOrder[newItem.id] = System.currentTimeMillis()
        insertCount.incrementAndGet()

        updateFlows()
        _events.trySend(MemoryEvent.Inserted(newItem))
        updateStats()

        return InsertResult.SUCCESS(newItem)
    }

    /** Insert from a body MemoryItem */
    fun insertFromBodyMemory(
        memoryItem: MemoryItem,
        source: MemorySource = MemorySource.EPISODIC,
        initialActivation: Float = 0.5f,
        goalAlignment: Float = 0.0f,
        relevance: Float = 0.0f,
        uncertainty: Float = 0.0f
    ): InsertResult {
        val activeMemory = ActiveMemory(
            id = "wm_${memoryItem.id}",
            sourceId = memoryItem.id,
            content = memoryItem.content,
            memoryType = memoryItem.type,
            activation = initialActivation,
            relevance = relevance,
            goalAlignment = goalAlignment,
            uncertainty = uncertainty,
            tags = memoryItem.tags,
            source = source
        )
        return insert(activeMemory)
    }

    /** Access an item (boosts activation, updates recency) */
    fun access(itemId: String): AccessResult {
        val item = items[itemId] ?: return AccessResult.NOT_FOUND

        val accessedItem = item.withAccess()
        items[itemId] = accessedItem
        accessOrder[itemId] = System.currentTimeMillis()
        accessCount.incrementAndGet()

        updateFlows()
        _events.trySend(MemoryEvent.Accessed(accessedItem))
        updateStats()

        return AccessResult.SUCCESS(accessedItem)
    }

    /**
     * Activate a memory item — an explicit activation REQUEST from another
     * subsystem (e.g. attention has placed something in the spotlight, or an
     * inference needs to hold a memory for immediate reasoning).
     *
     * Unlike [access] this does not imply the item was read; it only raises
     * activation so the item resists decay/eviction and ranks higher. Returns
     * NOT_FOUND when the item is not currently in working memory.
     */
    fun activate(itemId: String, amount: Float = 0.15f): AccessResult {
        val item = items[itemId] ?: return AccessResult.NOT_FOUND
        val activated = item.copy(activation = minOf(item.activation + amount, 1.0f))
        items[itemId] = activated
        accessOrder[itemId] = System.currentTimeMillis()

        updateFlows()
        _events.trySend(MemoryEvent.Updated(activated))
        updateStats()
        return AccessResult.SUCCESS(activated)
    }

    /** Perform one decay tick now (deterministic — used by tests and manual cycles). */
    fun decayOnce() = decayTick()

    /** Boost activation for goal-aligned items */
    fun boostForGoal(goalId: String, goalDescription: String) {
        val goalKeywords = goalDescription.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()

        for ((id, item) in items) {
            val contentWords = item.content.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
            val overlap = goalKeywords.intersect(contentWords).size

            if (overlap > 0) {
                val boost = config.activationBoostOnGoalMatch * (overlap.toFloat() / goalKeywords.size)
                val boosted = item.copy(
                    activation = minOf(item.activation + boost, 1.0f),
                    goalAlignment = minOf(item.goalAlignment + boost, 1.0f)
                )
                items[id] = boosted
                _events.trySend(MemoryEvent.Updated(boosted))
            }
        }
        updateFlows()
        updateStats()
    }

    /** Get an item by ID */
    fun get(itemId: String): ActiveMemory? = items[itemId]

    /** Get all items sorted by activation (highest first) */
    fun getAllSortedByActivation(): List<ActiveMemory> =
        items.values.toList().sortedByDescending { it.activation }

    /** Get items above activation threshold */
    fun getActive(threshold: Float = 0.3f): List<ActiveMemory> =
        items.values.filter { it.activation >= threshold }
            .sortedByDescending { it.activation }

    /** Get items relevant to a query */
    fun query(query: String, limit: Int = 10): List<ActiveMemory> {
        val queryLower = query.lowercase()
        val queryWords = queryLower.split(Regex("\\s+")).filter { it.length > 2 }.toSet()

        return items.values.map { item ->
            val contentWords = item.content.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
            val overlap = queryWords.intersect(contentWords).size
            val relevanceScore = if (queryWords.isNotEmpty()) overlap.toFloat() / queryWords.size else 0f

            // Query-keyword overlap dominates; stored importance is only a
            // tiebreaker. Otherwise a memory with high stored relevance but no
            // keyword match would surface for every query.
            val combinedRelevance = relevanceScore * 0.7f + item.relevance * 0.3f

            item.copy(relevance = combinedRelevance)
        }.filter { it.relevance >= config.minRelevanceThreshold }
            .sortedByDescending { it.relevance * it.activation }
            .take(limit)
    }

    /** Get items aligned with current goal */
    fun getGoalAligned(threshold: Float = 0.3f): List<ActiveMemory> =
        items.values.filter { it.goalAlignment >= threshold }
            .sortedByDescending { it.goalAlignment * it.activation }

    /** Get items with high uncertainty (for verification) */
    fun getUncertain(threshold: Float = 0.7f): List<ActiveMemory> =
        items.values.filter { it.uncertainty >= threshold }
            .sortedByDescending { it.uncertainty }

    /** Remove an item manually */
    fun remove(itemId: String, reason: EvictionReason = EvictionReason.MANUAL): Boolean {
        val item = items.remove(itemId)
        accessOrder.remove(itemId)
        if (item != null) {
            evictionCount.incrementAndGet()
            onEviction(item)
            _events.trySend(MemoryEvent.Evicted(item, reason))
            updateFlows()
            updateStats()
            return true
        }
        return false
    }

    /** Clear all items */
    fun clear() {
        val count = items.size
        items.clear()
        accessOrder.clear()
        _events.trySend(MemoryEvent.Cleared(count))
        updateFlows()
        updateStats()
    }

    /** Perform one decay tick on all items */
    private fun decayTick() {
        val now = System.currentTimeMillis()
        var changed = false

        for ((id, item) in items) {
            val oldActivation = item.activation
            val decayed = item.decayed(config.baseDecayRate)
            items[id] = decayed

            if (oldActivation != decayed.activation) {
                _events.trySend(MemoryEvent.Decayed(decayed, oldActivation, decayed.activation))
                changed = true
            }

            // Evict if activation drops below threshold
            if (decayed.activation < config.minActivationForRetention) {
                items.remove(id)
                accessOrder.remove(id)
                evictionCount.incrementAndGet()
                onEviction(decayed)
                _events.trySend(MemoryEvent.Evicted(decayed, EvictionReason.LOW_ACTIVATION))
                changed = true
            }
        }

        if (changed) {
            updateFlows()
            updateStats()
        }
    }

    /** Evict one item based on priority (lowest activation + relevance + goal alignment) */
    private fun evictOne(reason: EvictionReason): ActiveMemory? {
        if (items.isEmpty()) return null

        // Score items for eviction (lower score = more likely to evict)
        val scored = items.values.map { item ->
            val score = calculateEvictionScore(item)
            item to score
        }.sortedBy { it.second }

        val toEvict = scored.first().first
        remove(toEvict.id, reason)
        return toEvict
    }

    /** Calculate eviction score (lower = more evictable) */
    private fun calculateEvictionScore(item: ActiveMemory): Float {
        val activationScore = item.activation * 0.3f
        val relevanceScore = item.relevance * 0.2f
        val goalAlignmentScore = item.goalAlignment * config.goalAlignmentWeight
        val uncertaintyPenalty = item.uncertainty * config.uncertaintyWeight
        val recencyScore = calculateRecencyScore(item.lastAccessed) * config.recencyWeight

        return activationScore + relevanceScore + goalAlignmentScore - uncertaintyPenalty + recencyScore
    }

    /** Calculate recency score (0-1, higher = more recent) */
    private fun calculateRecencyScore(timestamp: Long): Float {
        val ageMs = System.currentTimeMillis() - timestamp
        val ageHours = ageMs / (1000.0 * 60 * 60)
        return when {
            ageHours < 1 -> 1.0f
            ageHours < 24 -> 0.8f
            ageHours < 168 -> 0.5f // 1 week
            else -> 0.1f
        }
    }

    /** Start the decay loop */
    private fun startDecayLoop() {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(config.decayIntervalMs)
                decayTick()
            }
        }
    }

    /** Update the observable flows */
    private fun updateFlows() {
        _items.value = items.values.toList().sortedByDescending { it.activation }
    }

    /** Update statistics */
    private fun updateStats() {
        val values = items.values
        val avgActivation = if (values.isNotEmpty()) values.map { it.activation }.average().toFloat() else 0f
        val now = System.currentTimeMillis()
        val oldestAge = if (values.isNotEmpty()) values.minOf { now - it.lastAccessed } else 0L
        val newestAge = if (values.isNotEmpty()) values.maxOf { now - it.lastAccessed } else 0L

        _stats.value = Stats(
            currentSize = values.size,
            maxSize = config.maxItems,
            totalInsertions = insertCount.get(),
            totalEvictions = evictionCount.get(),
            totalAccesses = accessCount.get(),
            avgActivation = avgActivation,
            oldestItemAge = oldestAge,
            newestItemAge = newestAge
        )
    }

    /** Get current stats snapshot */
    fun getStats(): Stats = _stats.value

    /** Get current items snapshot */
    fun getSnapshot(): List<ActiveMemory> = _items.value

    /** Result types */
    sealed interface InsertResult {
        data class SUCCESS(val item: ActiveMemory) : InsertResult
        data class FAILED(val reason: String) : InsertResult
    }

    sealed interface AccessResult {
        data class SUCCESS(val item: ActiveMemory) : AccessResult
        object NOT_FOUND : AccessResult
    }

    /** Observe memory events */
    fun observeEvents() = _events.receiveAsFlow()
}