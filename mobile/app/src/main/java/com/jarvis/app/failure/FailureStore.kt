package com.jarvis.app.failure

import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.humancore.store.StoreKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Failure memory — persists meaningful failures/recovery evidence to the
 * EXISTING FileStorage (failures.jsonl JSONL store), never a second memory
 * system. Only events at or above [persistMinSeverity] are written, so
 * transient INFO/WARNING noise never floods disk.
 *
 * The stored trail lets JARVIS later know "Arabic recognizer failed 7 times
 * because language pack unavailable — reload recovered it" (queryable via
 * [loadRecent]).
 */
class FailureStore(
    private val fileStorage: FileStorage,
    private val scope: CoroutineScope,
    private val persistMinSeverity: FailureSeverity = FailureSeverity.RECOVERABLE,
    private val maxPersistedIdsInMemory: Int = 512
) {

    /** Dedup across restarts: ids already written this session (bounded). */
    private val seenIds = ConcurrentHashMap.newKeySet<String>()

    /** Attach to a live surface and persist every new meaningful event. */
    fun attach(surface: FailureSurface) {
        scope.launch {
            surface.recentFailures.collectLatest { events ->
                // Newest first; only events not seen before, above threshold.
                val fresh = events.filter { ev ->
                    ev.severity.rank >= persistMinSeverity.rank && seenIds.add(ev.id)
                }.asReversed() // oldest → newest for append order
                if (fresh.isEmpty()) return@collectLatest
                persist(fresh)
                trimSeen()
            }
        }
    }

    private suspend fun persist(events: List<FailureEvent>) {
        withContext(Dispatchers.IO) {
            try {
                for (e in events) {
                    fileStorage.append(StoreKind.FAILURES, toJson(e).toString())
                }
            } catch (t: Throwable) {
                // Failure persistence must never take the body down. The
                // in-memory surface is the source of truth; disk is best-effort.
                // Deliberately logging-only here: re-reporting a storage failure
                // of the storage layer would recurse.
                android.util.Log.w("FailureStore", "persist failed", t)
            }
        }
    }

    /** Load the most recent persisted failures (newest first). Best-effort. */
    fun loadRecent(limit: Int = 50): List<FailureEvent> {
        return try {
            val content = fileStorage.read(StoreKind.FAILURES)
                ?: return emptyList()
            content.lineSequence().filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try { fromJson(JSONObject(line)) } catch (_: Exception) { null }
                }
                .toList()
                .takeLast(limit)
                .asReversed()
        } catch (t: Throwable) {
            android.util.Log.w("FailureStore", "load failed", t)
            emptyList()
        }
    }

    private fun trimSeen() {
        if (seenIds.size <= maxPersistedIdsInMemory) return
        // Drop oldest ids — bounded memory.
        val toDrop = seenIds.size - maxPersistedIdsInMemory
        seenIds.iterator().also { itt ->
            var dropped = 0
            while (itt.hasNext() && dropped < toDrop) {
                itt.next()
                itt.remove()
                dropped++
            }
        }
    }

    private fun toJson(e: FailureEvent): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("ts", e.timestamp)
        put("source", e.source)
        put("subsystem", e.subsystem)
        put("operation", e.operation)
        put("severity", e.severity.name)
        put("category", e.category.name)
        put("message", e.message)
        put("cause", e.cause ?: JSONObject.NULL)
        put("state", e.state ?: JSONObject.NULL)
        put("dependency", e.dependency ?: JSONObject.NULL)
        put("recoverability", e.recoverability.name)
        put("correlationId", e.correlationId ?: JSONObject.NULL)
        put("attempt", e.attempt)
        put("recoveryAction", e.recoveryAction.name)
        put("userMessage", e.userMessage ?: JSONObject.NULL)
        if (e.metadata.isNotEmpty()) {
            val m = JSONObject()
            e.metadata.forEach { (k, v) -> m.put(k, v) }
            put("metadata", m)
        }
    }

    private fun fromJson(j: JSONObject): FailureEvent {
        fun optString(key: String): String? =
            if (j.isNull(key)) null else j.optString(key)
        val meta = mutableMapOf<String, String>()
        j.optJSONObject("metadata")?.keys()?.forEach { k ->
            meta[k] = j.getJSONObject("metadata").optString(k)
        }
        fun enumSafe(name: String, fallback: FailureSeverity): FailureSeverity =
            FailureSeverity.entries.firstOrNull { it.name == name } ?: fallback
        fun categorySafe(name: String): FailureCategory =
            FailureCategory.entries.firstOrNull { it.name == name } ?: FailureCategory.UNKNOWN
        fun recovSafe(name: String): Recoverability =
            Recoverability.entries.firstOrNull { it.name == name } ?: Recoverability.NONE
        fun actionSafe(name: String): RecoveryAction =
            RecoveryAction.entries.firstOrNull { it.name == name } ?: RecoveryAction.NONE

        return FailureEvent(
            id = j.optString("id"),
            timestamp = j.optLong("ts"),
            source = j.optString("source"),
            subsystem = j.optString("subsystem"),
            operation = j.optString("operation"),
            severity = enumSafe(j.optString("severity"), FailureSeverity.INFO),
            category = categorySafe(j.optString("category")),
            message = j.optString("message"),
            cause = optString("cause"),
            state = optString("state"),
            dependency = optString("dependency"),
            recoverability = recovSafe(j.optString("recoverability")),
            correlationId = optString("correlationId"),
            attempt = j.optInt("attempt", 1),
            recoveryAction = actionSafe(j.optString("recoveryAction")),
            metadata = meta,
            userMessage = optString("userMessage")
        )
    }
}
