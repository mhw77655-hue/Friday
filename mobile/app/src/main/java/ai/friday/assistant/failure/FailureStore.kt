package ai.friday.assistant.failure

import ai.friday.assistant.humancore.store.StoragePort
import ai.friday.assistant.humancore.store.StoreKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Durable incident trail attached to the shared FailureSurface.
 *
 * FailureSurface.recentFailures is a bounded StateFlow snapshot, not an event
 * queue: collectors can observe conflated snapshots and miss intermediate
 * events during bursts. This store therefore subscribes to the surface's
 * ordered event hook and drains a bounded FIFO on an IO coroutine.
 *
 * Queue overflow and exhausted disk retries are counted explicitly. The store
 * never blocks the reporting thread with disk I/O and never claims an event was
 * persisted until append returns successfully.
 */
class FailureStore(
    private val fileStorage: StoragePort,
    private val scope: CoroutineScope,
    private val persistMinSeverity: FailureSeverity = FailureSeverity.RECOVERABLE,
    private val maxPersistedIdsInMemory: Int = 512
) {
    private val seenIds = LinkedHashMap<String, Unit>()
    private val seenLock = Any()
    private val attachedSurfaces = ConcurrentHashMap.newKeySet<FailureSurface>()

    private val persistedCount = AtomicLong(0)
    private val queueOverflowCount = AtomicLong(0)
    private val persistenceFailureCounter = AtomicLong(0)

    val persistedEventCount: Long get() = persistedCount.get()
    val persistenceQueueOverflowCount: Long get() = queueOverflowCount.get()
    val persistenceFailureCount: Long get() = persistenceFailureCounter.get()

    init {
        require(maxPersistedIdsInMemory > 0) { "maxPersistedIdsInMemory must be positive" }
    }

    private fun hasSeen(id: String): Boolean = synchronized(seenLock) {
        val present = seenIds.remove(id) != null
        if (present) seenIds[id] = Unit
        present
    }

    private fun markSeen(id: String) = synchronized(seenLock) {
        seenIds.remove(id)
        seenIds[id] = Unit
        while (seenIds.size > maxPersistedIdsInMemory) {
            val oldest = seenIds.entries.iterator()
            if (!oldest.hasNext()) break
            oldest.next()
            oldest.remove()
        }
    }

    /**
     * Attach once per surface. Existing bounded history is replayed atomically
     * with listener registration; subsequent events enter the same FIFO.
     */
    fun attach(surface: FailureSurface) {
        if (!attachedSurfaces.add(surface)) return

        val queue = Channel<FailureEvent>(capacity = MAX_PENDING_EVENTS)
        val pendingIds = ConcurrentHashMap.newKeySet<String>()
        val listener: (FailureEvent) -> Unit = { event ->
            if (event.severity.rank >= persistMinSeverity.rank &&
                !hasSeen(event.id) && pendingIds.add(event.id)
            ) {
                if (queue.trySend(event).isFailure) {
                    pendingIds.remove(event.id)
                    queueOverflowCount.incrementAndGet()
                }
            }
        }

        surface.addFailureListener(listener)
        val worker = scope.launch {
            for (event in queue) {
                try {
                    persist(event)
                } finally {
                    pendingIds.remove(event.id)
                }
            }
        }
        worker.invokeOnCompletion {
            surface.removeFailureListener(listener)
            queue.close()
            attachedSurfaces.remove(surface)
        }
        if (!worker.isActive) {
            surface.removeFailureListener(listener)
            queue.close()
            attachedSurfaces.remove(surface)
        }
    }

    private suspend fun persist(event: FailureEvent) = withContext(Dispatchers.IO) {
        var attempt = 0
        while (attempt < MAX_APPEND_ATTEMPTS) {
            try {
                fileStorage.append(StoreKind.FAILURES, toJson(event).toString())
                markSeen(event.id)
                persistedCount.incrementAndGet()
                return@withContext
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                attempt++
                if (attempt < MAX_APPEND_ATTEMPTS) {
                    delay(RETRY_BASE_DELAY_MS shl (attempt - 1))
                } else {
                    persistenceFailureCounter.incrementAndGet()
                    android.util.Log.w(
                        "FailureStore",
                        "persist failed after $attempt attempts for ${event.subsystem}/${event.operation}",
                        failure
                    )
                }
            }
        }
    }

    /** Load the most recent persisted failures (newest first). Best-effort. */
    fun loadRecent(limit: Int = 50): List<FailureEvent> {
        if (limit <= 0) return emptyList()
        return try {
            val content = fileStorage.read(StoreKind.FAILURES) ?: return emptyList()
            content.lineSequence().filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try { fromJson(JSONObject(line)) } catch (_: Exception) { null }
                }
                .toList()
                .takeLast(limit)
                .asReversed()
        } catch (failure: Exception) {
            android.util.Log.w("FailureStore", "load failed", failure)
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
            val metadataJson = JSONObject()
            e.metadata.forEach { (key, value) -> metadataJson.put(key, value) }
            put("metadata", metadataJson)
        }
    }

    private fun fromJson(j: JSONObject): FailureEvent {
        fun optString(key: String): String? = if (j.isNull(key)) null else j.optString(key)
        val metadata = mutableMapOf<String, String>()
        j.optJSONObject("metadata")?.keys()?.forEach { key ->
            metadata[key] = j.getJSONObject("metadata").optString(key)
        }
        fun severity(name: String) =
            FailureSeverity.entries.firstOrNull { it.name == name } ?: FailureSeverity.INFO
        fun category(name: String) =
            FailureCategory.entries.firstOrNull { it.name == name } ?: FailureCategory.UNKNOWN
        fun recoverability(name: String) =
            Recoverability.entries.firstOrNull { it.name == name } ?: Recoverability.NONE
        fun action(name: String) =
            RecoveryAction.entries.firstOrNull { it.name == name } ?: RecoveryAction.NONE

        return FailureEvent(
            id = j.optString("id"),
            timestamp = j.optLong("ts"),
            source = j.optString("source"),
            subsystem = j.optString("subsystem"),
            operation = j.optString("operation"),
            severity = severity(j.optString("severity")),
            category = category(j.optString("category")),
            message = j.optString("message"),
            cause = optString("cause"),
            state = optString("state"),
            dependency = optString("dependency"),
            recoverability = recoverability(j.optString("recoverability")),
            correlationId = optString("correlationId"),
            attempt = j.optInt("attempt", 1),
            recoveryAction = action(j.optString("recoveryAction")),
            metadata = metadata,
            userMessage = optString("userMessage")
        )
    }

    companion object {
        const val MAX_PENDING_EVENTS = 1_024
        const val MAX_APPEND_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MS = 100L
    }
}
