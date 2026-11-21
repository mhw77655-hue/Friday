package ai.friday.assistant.failure

import ai.friday.assistant.humancore.store.StoragePort
import ai.friday.assistant.humancore.store.StoreKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.LinkedHashMap

/**
 * Failure memory — persists meaningful failures/recovery evidence to the
 * EXISTING FileStorage (failures.jsonl JSONL store), never a second memory
 * system. Only events at or above [persistMinSeverity] are written, so
 * transient INFO/WARNING noise never floods disk.
 *
 * The stored trail lets FRIDAY later know "Arabic recognizer failed 7 times
 * because language pack unavailable — reload recovered it" (queryable via
 * [loadRecent]).
 */
class FailureStore(
    private val fileStorage: StoragePort,
    private val scope: CoroutineScope,
    private val persistMinSeverity: FailureSeverity = FailureSeverity.RECOVERABLE,
    private val maxPersistedIdsInMemory: Int = 512
) {

    /** Session-local LRU of ids successfully appended (bounded). */
    private val seenIds = LinkedHashMap<String, Unit>()
    private val seenLock = Any()

    init {
        require(maxPersistedIdsInMemory > 0) { "maxPersistedIdsInMemory must be positive" }
    }

    private fun hasSeen(id: String): Boolean = synchronized(seenLock) {
        seenIds.containsKey(id)
    }

    private fun markSeen(id: String) = synchronized(seenLock) {
        seenIds[id] = Unit
        while (seenIds.size > maxPersistedIdsInMemory) {
            val oldest = seenIds.entries.iterator()
            if (!oldest.hasNext()) break
            oldest.next()
            oldest.remove()
        }
    }

    /** Attach to a live surface and persist every new meaningful event. */
    fun attach(surface: FailureSurface) {
        scope.launch {
            // collect, not collectLatest: cancelling an in-flight append when a
            // new snapshot arrives can lose events after their ids are marked.
            surface.recentFailures.collect { events ->
                val fresh = events.filter { ev ->
                    ev.severity.rank >= persistMinSeverity.rank && !hasSeen(ev.id)
                }.asReversed() // oldest → newest for append order
                if (fresh.isNotEmpty()) persist(fresh)
            }
        }
    }

    private suspend fun persist(events: List<FailureEvent>) = withContext(Dispatchers.IO) {
        for (event in events) {
            var attempt = 0
            var persisted = false
            while (attempt < 3 && !persisted) {
                try {
                    fileStorage.append(StoreKind.FAILURES, toJson(event).toString())
                    markSeen(event.id)
                    persisted = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    attempt++
                    if (attempt < 3) {
                        delay(100L shl (attempt - 1))
                    } else {
                        android.util.Log.w("FailureStore", "persist failed after $attempt attempts", failure)
                    }
                }
            }
        }
    }

    /** Load the most recent persisted failures (newest first). Best-effort. */
    fun loadRecent(limit: Int = 50): List<FailureEvent> {
        if (limit <= 0) return emptyList()
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
