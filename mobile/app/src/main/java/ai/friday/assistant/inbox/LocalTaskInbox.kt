package ai.friday.assistant.inbox

import android.util.Log

import ai.friday.assistant.FridayEngine
import ai.friday.assistant.humancore.store.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

/**
 * Local Task Inbox - persists tasks to filesDir/inbox/tasks.jsonl
 * Mirrors OS Portal's inbox but works fully offline.
 * Syncs with OS Portal when attached.
 */
class LocalTaskInbox(
    private val fileStorage: FileStorage,
    private val scope: CoroutineScope
) {
    private val inboxDir = fileStorage.baseDir.resolve("inbox")
    private val tasksFile = inboxDir.resolve("tasks.jsonl")
    private val idCounter = AtomicLong(System.currentTimeMillis())
    private val mutationMutex = Mutex()
    private val _tasks = MutableStateFlow<List<InboxTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    // Task status flow for UI
    private val _pendingTasks = MutableStateFlow<List<InboxTask>>(emptyList())
    val pendingTasks = _pendingTasks.asStateFlow()

    private val _runningTasks = MutableStateFlow<List<InboxTask>>(emptyList())
    val runningTasks = _runningTasks.asStateFlow()

    private val _completedTasks = MutableStateFlow<List<InboxTask>>(emptyList())
    val completedTasks = _completedTasks.asStateFlow()

    private val _failedTasks = MutableStateFlow<List<InboxTask>>(emptyList())
    val failedTasks = _failedTasks.asStateFlow()

    init {
        inboxDir.mkdirs()
        scope.launch { loadTasks() }
    }

    /** UI-friendly observe method returning LiveData of TaskEntry */
    fun observeTasksForUI(): LiveData<List<TaskEntry>> {
        return tasks
            .map { it.filter { it.status == InboxTaskStatus.PENDING || it.status == InboxTaskStatus.RUNNING || it.status == InboxTaskStatus.WAITING_APPROVAL }
                .map { TaskEntry.from(it) } }
            .asLiveData(Dispatchers.Main)
    }

    /** Submit a new task to the inbox */
    suspend fun submit(
        source: String,
        description: String,
        capability: String,
        arguments: Map<String, Any>,
        requiresApproval: Boolean = false
    ): InboxTask {
        val task = InboxTask(
            id = "task-${idCounter.incrementAndGet()}",
            source = source,
            description = description,
            capability = capability,
            arguments = arguments,
            status = if (requiresApproval) InboxTaskStatus.WAITING_APPROVAL else InboxTaskStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            requiresApproval = requiresApproval
        )
        addTask(task)
        return task
    }

    /**
     * Idempotently import an externally received proposal under a stable ID.
     * The task is always WAITING_APPROVAL: external model output is evidence,
     * never an executable instruction.
     */
    suspend fun submitOnce(
        id: String,
        source: String,
        description: String,
        capability: String,
        arguments: Map<String, Any>,
        requiresApproval: Boolean = true
    ): InboxTask = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9._:-]{8,160}"))) { "Stable task ID is malformed." }
        mutationMutex.withLock {
            _tasks.value.firstOrNull { it.id == id }?.let { return@withLock it }
            val task = InboxTask(
                id = id,
                source = source,
                description = description,
                capability = capability,
                arguments = arguments,
                status = if (requiresApproval) InboxTaskStatus.WAITING_APPROVAL else InboxTaskStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                requiresApproval = requiresApproval
            )
            // Persist first: if disk I/O fails, do not expose an in-memory
            // task that a retry could mistake for a durable import.
            persistTask(task)
            _tasks.value = _tasks.value + task
            refreshDerivedFlows()
            task
        }
    }

    /** Get a task by ID */
    suspend fun getTask(id: String): InboxTask? = withContext(Dispatchers.IO) {
        _tasks.value.find { it.id == id }
    }

    /** List tasks by status */
    suspend fun listTasks(status: InboxTaskStatus? = null): List<InboxTask> = withContext(Dispatchers.IO) {
        _tasks.value.filter { status == null || it.status == status }
    }

    /** Retry a failed task */
    suspend fun retry(taskId: String): Result<InboxTask> = withContext(Dispatchers.IO) {
        val task = _tasks.value.find { it.id == taskId }
            ?: return@withContext Result.failure(IllegalArgumentException("Task not found"))
        val updated = task.copy(
            status = InboxTaskStatus.PENDING,
            attempts = task.attempts + 1,
            lastAttemptAt = System.currentTimeMillis(),
            error = null
        )
        updateTask(updated)
        Result.success(updated)
    }

    /** Cancel a pending/running task */
    suspend fun cancel(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val task = _tasks.value.find { it.id == taskId }
            ?: return@withContext Result.failure(IllegalArgumentException("Task not found"))
        if (task.status == InboxTaskStatus.COMPLETED || task.status == InboxTaskStatus.FAILED) {
            return@withContext Result.failure(IllegalStateException("Cannot cancel finished task"))
        }
        val updated = task.copy(status = InboxTaskStatus.CANCELLED)
        updateTask(updated)
        Result.success(Unit)
    }

    /** Process a task - called by worker */
    suspend fun claimNext(): InboxTask? = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val pending = _tasks.value
                .filter { it.status == InboxTaskStatus.PENDING }
                .sortedBy { it.createdAt }
                .firstOrNull()

            pending?.let { task ->
                val updated = task.copy(
                    status = InboxTaskStatus.RUNNING,
                    startedAt = System.currentTimeMillis()
                )
                updateTaskLocked(updated)
                updated
            }
        }
    }

    /**
     * Return an interrupted RUNNING task to the durable queue. Used when a
     * worker is cancelled so the task is not stranded in RUNNING forever.
     */
    suspend fun requeueInterrupted(taskId: String, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        val task = _tasks.value.find { it.id == taskId }
            ?: return@withContext Result.failure(IllegalArgumentException("Task not found"))
        if (task.status != InboxTaskStatus.RUNNING) {
            return@withContext Result.failure(IllegalStateException("Only a running task can be requeued"))
        }
        val now = System.currentTimeMillis()
        val nextAttempts = task.attempts + 1
        val exhausted = nextAttempts >= MAX_INTERRUPTED_REQUEUES
        val updated = task.copy(
            status = if (exhausted) InboxTaskStatus.FAILED else InboxTaskStatus.PENDING,
            startedAt = null,
            completedAt = if (exhausted) now else null,
            attempts = nextAttempts,
            lastAttemptAt = now,
            error = if (exhausted) "Interrupted execution retry budget exhausted" else null,
            history = task.history + TaskHistoryEntry(
                timestamp = now,
                action = if (exhausted) "INTERRUPTED_RETRY_EXHAUSTED" else "INTERRUPTED_REQUEUED",
                detail = reason.take(160)
            )
        )
        updateTask(updated)
        Result.success(Unit)
    }

    /** Complete a task with result */
    suspend fun complete(taskId: String, result: Map<String, Any>?): Result<Unit> = withContext(Dispatchers.IO) {
        val task = _tasks.value.find { it.id == taskId }
            ?: return@withContext Result.failure(IllegalArgumentException("Task not found"))
        val updated = task.copy(
            status = InboxTaskStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
            result = result
        )
        updateTask(updated)
        Result.success(Unit)
    }

    /** Fail a task with error */
    suspend fun fail(taskId: String, error: String): Result<Unit> = withContext(Dispatchers.IO) {
        val task = _tasks.value.find { it.id == taskId }
            ?: return@withContext Result.failure(IllegalArgumentException("Task not found"))
        val updated = task.copy(
            status = InboxTaskStatus.FAILED,
            completedAt = System.currentTimeMillis(),
            error = error
        )
        updateTask(updated)
        Result.success(Unit)
    }

    /** Approve a waiting-approval task */
    suspend fun approve(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val task = _tasks.value.find { it.id == taskId }
            ?: return@withContext Result.failure(IllegalArgumentException("Task not found"))
        if (task.status != InboxTaskStatus.WAITING_APPROVAL) {
            return@withContext Result.failure(IllegalStateException("Task not awaiting approval"))
        }
        val updated = task.copy(
            status = InboxTaskStatus.PENDING,
            requiresApproval = false
        )
        updateTask(updated)
        Result.success(Unit)
    }

    /** Deny a waiting-approval task */
    suspend fun deny(taskId: String, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        val task = _tasks.value.find { it.id == taskId }
            ?: return@withContext Result.failure(IllegalArgumentException("Task not found"))
        val updated = task.copy(
            status = InboxTaskStatus.FAILED,
            completedAt = System.currentTimeMillis(),
            error = "Approval denied: $reason"
        )
        updateTask(updated)
        Result.success(Unit)
    }

    private suspend fun addTask(task: InboxTask) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val current = _tasks.value
            _tasks.value = current + task
            persistTask(task)
            refreshDerivedFlows()
        }
    }

    private suspend fun updateTask(task: InboxTask) = withContext(Dispatchers.IO) {
        mutationMutex.withLock { updateTaskLocked(task) }
    }

    private fun updateTaskLocked(task: InboxTask) {
        val current = _tasks.value
        val index = current.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            val updated = current.toMutableList().apply { set(index, task) }
            _tasks.value = updated
            rewriteAllTasks()
            refreshDerivedFlows()
        }
    }

    private fun refreshDerivedFlows() {
        val all = _tasks.value
        _pendingTasks.value = all.filter { it.status == InboxTaskStatus.PENDING }
        _runningTasks.value = all.filter { it.status == InboxTaskStatus.RUNNING }
        _completedTasks.value = all.filter { it.status == InboxTaskStatus.COMPLETED }
        _failedTasks.value = all.filter { it.status == InboxTaskStatus.FAILED }
    }

    private suspend fun loadTasks() = withContext(Dispatchers.IO) {
        try {
            mutationMutex.withLock {
                if (!tasksFile.exists()) return@withLock
                val tasks = mutableListOf<InboxTask>()
                tasksFile.readLines().forEach { line ->
                    if (line.isNotBlank()) {
                        try {
                            tasks.add(InboxTask.fromJson(JSONObject(line)))
                        } catch (failure: Exception) {
                            // Skip corrupted lines; later journal entries remain recoverable.
                            Log.w("LocalTaskInbox", "Skipping one malformed task journal entry", failure)
                        }
                    }
                }
                // Merge rather than discard: a task may have been submitted while
                // the initial disk read was in flight. Current in-memory records
                // win for matching IDs; disk-only RUNNING tasks belonged to a
                // previous process and are safely returned to PENDING.
                val recovered = mergeAndRecoverInboxTasks(
                    diskTasks = tasks,
                    currentTasks = _tasks.value,
                    now = System.currentTimeMillis()
                )
                _tasks.value = recovered
                rewriteAllTasks()
                refreshDerivedFlows()
            }
        } catch (failure: Exception) {
            Log.e("LocalTaskInbox", "Could not load or reconcile the persisted task inbox", failure)
        }
    }

    private fun persistTask(task: InboxTask) {
        fileStorage.append(tasksFile, task.toJson().toString())
    }

    private fun rewriteAllTasks() {
        val content = _tasks.value.joinToString("\n") { it.toJson().toString() } + "\n"
        fileStorage.write(tasksFile, content)
    }

    companion object {
        private val inboxExceptionHandler = CoroutineExceptionHandler { _, failure ->
            Log.e("LocalTaskInbox", "Inbox background operation failed", failure)
        }

        fun create(): LocalTaskInbox = LocalTaskInbox(
            fileStorage = FileStorage(FridayEngine.appContext().filesDir),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + inboxExceptionHandler)
        )

        /** Worker-safe factory that does not depend on the activity/engine singleton being initialized. */
        fun create(context: android.content.Context): LocalTaskInbox = LocalTaskInbox(
            fileStorage = FileStorage(context.applicationContext.filesDir),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + inboxExceptionHandler)
        )
    }
}

internal fun mergeAndRecoverInboxTasks(
    diskTasks: List<InboxTask>,
    currentTasks: List<InboxTask>,
    now: Long
): List<InboxTask> {
    val currentById = currentTasks.associateBy { it.id }
    val merged = LinkedHashMap<String, InboxTask>()
    diskTasks.forEach { disk ->
        val current = currentById[disk.id]
        merged[disk.id] = current ?: if (disk.status == InboxTaskStatus.RUNNING) {
            val nextAttempts = disk.attempts + 1
            val exhausted = nextAttempts >= MAX_INTERRUPTED_REQUEUES
            disk.copy(
                status = if (exhausted) InboxTaskStatus.FAILED else InboxTaskStatus.PENDING,
                startedAt = null,
                completedAt = if (exhausted) now else null,
                attempts = nextAttempts,
                lastAttemptAt = now,
                error = if (exhausted) "Interrupted execution retry budget exhausted" else null,
                history = disk.history + TaskHistoryEntry(
                    timestamp = now,
                    action = if (exhausted) "PROCESS_RESTART_RETRY_EXHAUSTED" else "PROCESS_RESTART_REQUEUED",
                    detail = "Recovered a task left RUNNING by a previous Friday process."
                )
            )
        } else {
            disk
        }
    }
    currentTasks.forEach { current -> merged[current.id] = current }
    return merged.values.sortedBy { it.createdAt }
}

/** Inbox task entity */
data class InboxTask(
    val id: String,
    val source: String,
    val description: String,
    val capability: String,
    val arguments: Map<String, Any>,
    val status: InboxTaskStatus,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val requiresApproval: Boolean = false,
    val approvalId: String? = null,
    val result: Map<String, Any>? = null,
    val error: String? = null,
    val history: List<TaskHistoryEntry> = emptyList()
) {
    /** Duration in ms if running/completed */
    val durationMs: Long?
        get() = if (startedAt != null) {
            (completedAt ?: System.currentTimeMillis()) - startedAt!!
        } else null

    /** Manual JSON round-trip (kotlinx-serialization is not in the build). */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("source", source)
        put("description", description)
        put("capability", capability)
        put("arguments", JSONObject(arguments.mapValues { (_, v) -> jsonValue(v) }))
        put("status", status.name)
        put("createdAt", createdAt)
        startedAt?.let { put("startedAt", it) }
        completedAt?.let { put("completedAt", it) }
        put("attempts", attempts)
        lastAttemptAt?.let { put("lastAttemptAt", it) }
        put("requiresApproval", requiresApproval)
        approvalId?.let { put("approvalId", it) }
        result?.let { put("result", JSONObject(it.mapValues { (_, v) -> jsonValue(v) })) }
        error?.let { put("error", it) }
        put("history", org.json.JSONArray(history.map { it.toJson() }))
    }

    companion object {
        fun fromJson(o: JSONObject): InboxTask = InboxTask(
            id = o.getString("id"),
            source = o.optString("source"),
            description = o.optString("description"),
            capability = o.optString("capability"),
            arguments = o.optJSONObject("arguments")?.let { fromJsonObject(it) } ?: emptyMap(),
            status = runCatching { InboxTaskStatus.valueOf(o.getString("status")) }
                .getOrDefault(InboxTaskStatus.PENDING),
            createdAt = o.optLong("createdAt"),
            startedAt = if (o.has("startedAt")) o.optLong("startedAt") else null,
            completedAt = if (o.has("completedAt")) o.optLong("completedAt") else null,
            attempts = o.optInt("attempts"),
            lastAttemptAt = if (o.has("lastAttemptAt")) o.optLong("lastAttemptAt") else null,
            requiresApproval = o.optBoolean("requiresApproval"),
            approvalId = o.optString("approvalId").takeIf { it.isNotBlank() },
            result = o.optJSONObject("result")?.let { fromJsonObject(it) },
            error = o.optString("error").takeIf { it.isNotBlank() },
            history = o.optJSONArray("history")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { TaskHistoryEntry.fromJson(arr.getJSONObject(i)) }.getOrNull()
                }
            } ?: emptyList()
        )

        /** JSONObject value -> plain Kotlin value (JSONObject.NULL -> null). */
        private fun fromJsonObject(jo: JSONObject): Map<String, Any> {
            val out = mutableMapOf<String, Any>()
            jo.keys().forEach { key ->
                val v = jo.opt(key)
                if (v != null && v != org.json.JSONObject.NULL) out[key] = v
            }
            return out
        }
    }
}

/** Task history entry for audit trail */
data class TaskHistoryEntry(
    val timestamp: Long,
    val action: String,
    val detail: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("action", action)
        detail?.let { put("detail", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): TaskHistoryEntry = TaskHistoryEntry(
            timestamp = o.optLong("timestamp"),
            action = o.optString("action"),
            detail = o.optString("detail").takeIf { it.isNotBlank() }
        )
    }
}

/** Task status enum */
enum class InboxTaskStatus {
    PENDING,
    RUNNING,
    WAITING_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED
}

/** UI-friendly task entry */
data class TaskEntry(
    val id: String,
    val title: String,
    val capability: String,
    val source: String,
    val status: String
) {
    companion object {
        fun from(task: InboxTask): TaskEntry = TaskEntry(
            id = task.id,
            title = task.description,
            capability = task.capability,
            source = task.source,
            status = task.status.name
        )
    }
}

/** Map value that JSONObject can store directly. */
internal fun jsonValue(v: Any?): Any = when (v) {
    is Int, is Long, is Double, is Float, is Boolean, is String -> v
    is Map<*, *> -> JSONObject(v.mapKeys { it.key.toString() }.mapValues { jsonValue(it.value) })
    is List<*> -> org.json.JSONArray(v.map { jsonValue(it) })
    null -> org.json.JSONObject.NULL
    else -> v.toString()
}

internal const val MAX_INTERRUPTED_REQUEUES = 5
