package com.jarvis.app.body

import android.content.Context
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.humancore.store.StoreKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import android.util.Log

private const val TAG = "MemoryStore"
private const val MAX_EPISODIC = 500
private const val MAX_FACTS = 200
private const val MAX_CONTEXT_TURNS = 20

/**
 * Selective local memory store for JARVIS communication body.
 *
 * Supports:
 * - Short-term conversation context (rolling window)
 * - Episodic memories (important conversations)
 * - User facts (persistent)
 * - Learned vocabulary (with pronunciation)
 * - Preferences
 * - Semantic retrieval (simple keyword/tag matching for offline)
 *
 * Memory is SELECTIVE - not every conversation line becomes permanent.
 * Only important exchanges (tagged by Human Core) are promoted.
 */
class MemoryStore(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val onFailure: (com.jarvis.app.failure.FailureReport) -> Unit = {}
) : MemoryStorePort {

    // In-memory caches
    private val episodicMemories = ConcurrentHashMap<String, MemoryItem>()
    private val facts = ConcurrentHashMap<String, MemoryItem>()
    private val preferences = ConcurrentHashMap<String, MemoryItem>()
    private val conversationContext = MutableStateFlow<List<MemoryItem>>(emptyList())
    private val contextOrder = mutableListOf<String>()

    private val fileStorage = FileStorage(context.filesDir)
    private val idCounter = AtomicLong(System.currentTimeMillis())

    init {
        loadFromDisk()
    }

    /** Store a user fact (e.g., "user's name is Ahmed"). */
    fun storeFact(key: String, value: String, tags: List<String> = emptyList()) {
        val item = MemoryItem(
            id = "fact_$key",
            type = MemoryType.FACT,
            content = "$key: $value",
            timestamp = System.currentTimeMillis(),
            tags = tags + "fact",
            relevance = 1.0f
        )
        facts[key] = item
        persistAsync()
    }

    /** Retrieve a user fact. */
    fun getFact(key: String): String? = facts[key]?.content

    /** Store an episodic memory (conversation episode). */
    fun storeEpisodic(content: String, tags: List<String> = emptyList(), relevance: Float = 0.8f) {
        val id = "ep_${idCounter.incrementAndGet()}"
        val item = MemoryItem(
            id = id,
            type = MemoryType.EPISODIC,
            content = content,
            timestamp = System.currentTimeMillis(),
            tags = tags + "episodic",
            relevance = relevance
        )
        episodicMemories[id] = item
        trimEpisodic()
        persistAsync()
    }

    /** Store a user preference. */
    fun storePreference(key: String, value: String) {
        val item = MemoryItem(
            id = "pref_$key",
            type = MemoryType.PREFERENCE,
            content = "$key: $value",
            timestamp = System.currentTimeMillis(),
            tags = listOf("preference", key),
            relevance = 1.0f
        )
        preferences[key] = item
        persistAsync()
    }

    /** Get a user preference. */
    fun getPreference(key: String): String? = preferences[key]?.content?.substringAfter(": ")

    /** Store a full conversation exchange with associated memory items. */
    fun storeConversation(userText: String, assistantText: String, memoryItems: List<MemoryItem> = emptyList()) {
        // Add to rolling context
        addContextTurn(userText, assistantText)

        // Promote important memory items to episodic
        for (item in memoryItems) {
            if (item.relevance >= 0.7f && item.type != MemoryType.CONTEXT) {
                storeEpisodic(
                    content = item.content,
                    tags = item.tags,
                    relevance = item.relevance
                )
            }
        }
    }

    /** Add to rolling conversation context. */
    fun addContextTurn(userText: String, jarvisText: String) {
        val timestamp = System.currentTimeMillis()
        val userItem = MemoryItem(
            id = "ctx_u_${idCounter.incrementAndGet()}",
            type = MemoryType.CONTEXT,
            content = "User: $userText",
            timestamp = timestamp,
            tags = listOf("context", "user"),
            relevance = 0.9f
        )
        val jarvisItem = MemoryItem(
            id = "ctx_j_${idCounter.incrementAndGet()}",
            type = MemoryType.CONTEXT,
            content = "JARVIS: $jarvisText",
            timestamp = timestamp + 1,
            tags = listOf("context", "jarvis"),
            relevance = 0.9f
        )

        scope.launch {
            contextOrder.add(userItem.id)
            contextOrder.add(jarvisItem.id)
            val current = conversationContext.value
            val updated = current + userItem + jarvisItem
            val trimmed = if (updated.size > MAX_CONTEXT_TURNS * 2) {
                updated.drop(updated.size - MAX_CONTEXT_TURNS * 2)
            } else {
                updated
            }
            conversationContext.value = trimmed
            // Trim order list
            while (contextOrder.size > MAX_CONTEXT_TURNS * 2) {
                val oldId = contextOrder.removeAt(0)
                // Remove from episodic if it was promoted
            }
        }
    }

    /** Get recent conversation context. */
    fun getRecentContext(turns: Int = 5): List<MemoryItem> {
        return conversationContext.value.takeLast(turns * 2)
    }

    /** Query memories for context building (alias for retrieve with better name). */
    override fun queryMemories(query: String, limit: Int): List<MemoryItem> {
        val results = retrieve(query)
        return results.take(limit)
    }

    /** Core retrieval: find relevant memories for a query. */
    fun retrieve(query: String, context: List<MemoryItem> = emptyList()): List<MemoryItem> {
        val queryLower = query.lowercase()
        val queryWords = queryLower.split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        val results = mutableListOf<MemoryItem>()

        // Score all memories
        val allMemories = mutableListOf<MemoryItem>()
        allMemories.addAll(facts.values)
        allMemories.addAll(episodicMemories.values)
        allMemories.addAll(preferences.values)
        allMemories.addAll(context)

        for (item in allMemories) {
            val score = calculateRelevance(item, queryLower, queryWords)
            if (score >= BodyConfig.MEMORY_RELEVANCE_THRESHOLD) {
                results.add(item.copy(relevance = score))
            }
        }

        // Sort by relevance descending, then by recency
        results.sortWith(compareByDescending<MemoryItem> { it.relevance }.thenByDescending { it.timestamp })
        return results.take(BodyConfig.MAX_MEMORY_ITEMS)
    }

    /** Calculate relevance score for a memory item. */
    private fun calculateRelevance(item: MemoryItem, queryLower: String, queryWords: Set<String>): Float {
        var score = item.relevance * 0.3f // Base relevance weight

        val contentLower = item.content.lowercase()
        val contentWords = contentLower.split(Regex("\\s+")).filter { it.length > 2 }.toSet()

        // Word overlap
        val overlap = queryWords.intersect(contentWords).size
        if (queryWords.isNotEmpty()) {
            score += (overlap.toFloat() / queryWords.size) * 0.5f
        }

        // Tag matching
        for (tag in item.tags) {
            if (queryLower.contains(tag.lowercase())) {
                score += 0.2f
            }
        }

        // Type boost
        score += when (item.type) {
            MemoryType.FACT -> 0.3f
            MemoryType.PREFERENCE -> 0.2f
            MemoryType.VOCABULARY -> 0.25f
            MemoryType.CONTEXT -> 0.1f
            MemoryType.EPISODIC -> 0.15f
        }

        // Recency boost (last 24h)
        val hoursAgo = (System.currentTimeMillis() - item.timestamp) / (1000 * 60 * 60)
        if (hoursAgo < 24) score += 0.1f * (1f - hoursAgo / 24f)

        return minOf(score, 1.0f)
    }

    /** Promote a conversation exchange to episodic memory (called by Human Core integration). */
    fun promoteToEpisodic(userText: String, jarvisText: String, tags: List<String>, relevance: Float) {
        val content = "User: $userText\nJARVIS: $jarvisText"
        storeEpisodic(content, tags, relevance)
    }

    /** Trim episodic memories to max size. */
    private fun trimEpisodic() {
        if (episodicMemories.size > MAX_EPISODIC) {
            val sorted = episodicMemories.values.toMutableList()
            sorted.sortBy { it.timestamp }
            val toRemove = sorted.take(episodicMemories.size - MAX_EPISODIC)
            for (item in toRemove) {
                episodicMemories.remove(item.id)
            }
        }
    }

    /** Persist to disk asynchronously. */
    private fun persistAsync() {
        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject()
                json.put("episodic", JSONArray(episodicMemories.values.map { toJson(it) }))
                json.put("facts", JSONArray(facts.values.map { toJson(it) }))
                json.put("preferences", JSONArray(preferences.values.map { toJson(it) }))
                json.put("context_order", JSONArray(contextOrder))
                fileStorage.write(StoreKind.DIALOGUE, json.toString(2)) // Reuse DIALOGUE store kind
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.w(TAG, "Persist failed: ${t.message}")
                onFailure(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "MEMORY",
                        operation = "persist",
                        severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                        category = com.jarvis.app.failure.FailureCategory.PERSISTENCE,
                        message = "Memory store persistence failed",
                        source = "MemoryStore",
                        cause = t.message,
                        dependency = "filesDir/humancore/dialogue.jsonl",
                        recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                    )
                )
            }
        }
    }

    /** Load from disk. */
    private fun loadFromDisk() {
        scope.launch(Dispatchers.IO) {
            try {
                val content = fileStorage.read(StoreKind.DIALOGUE)
                if (content.isNullOrBlank()) return@launch

                val json = JSONObject(content)
                loadArray(json, "episodic", episodicMemories)
                loadArray(json, "facts", facts)
                loadArray(json, "preferences", preferences)
                if (json.has("context_order")) {
                    val arr = json.getJSONArray("context_order")
                    for (i in 0 until arr.length()) {
                        contextOrder.add(arr.getString(i))
                    }
                }
                Log.i(TAG, "Loaded: ${episodicMemories.size} episodic, ${facts.size} facts, ${preferences.size} prefs")
            } catch (e: Exception) {
                Log.w(TAG, "Load failed: ${e.message}")
                onFailure(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "MEMORY",
                        operation = "load",
                        severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                        category = com.jarvis.app.failure.FailureCategory.PERSISTENCE,
                        message = "Memory store load failed — started empty",
                        source = "MemoryStore",
                        cause = e.message,
                        dependency = "filesDir/humancore/dialogue.jsonl",
                        recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                    )
                )
            }
        }
    }

    private fun loadArray(json: JSONObject, key: String, target: ConcurrentHashMap<String, MemoryItem>) {
        if (!json.has(key)) return
        val arr = json.getJSONArray(key)
        for (i in 0 until arr.length()) {
            val item = fromJson(arr.getJSONObject(i))
            target[item.id] = item
        }
    }

    private fun toJson(item: MemoryItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("type", item.type.name)
        put("content", item.content)
        put("timestamp", item.timestamp)
        put("tags", JSONArray(item.tags))
        put("relevance", item.relevance)
    }

    private fun fromJson(json: JSONObject): MemoryItem = MemoryItem(
        id = json.getString("id"),
        type = MemoryType.valueOf(json.getString("type")),
        content = json.getString("content"),
        timestamp = json.getLong("timestamp"),
        tags = (0 until json.getJSONArray("tags").length()).map { json.getJSONArray("tags").getString(it) },
        relevance = json.getDouble("relevance").toFloat()
    )
}