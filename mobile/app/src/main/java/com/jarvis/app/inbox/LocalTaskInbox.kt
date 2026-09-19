package com.jarvis.app.inbox

import com.jarvis.app.JarvisEngine
import com.jarvis.app.humancore.store.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val pending = _tasks.value
            .filter { it.status == InboxTaskStatus.PENDING }
            .sortedBy { it.createdAt }
            .firstOrNull()

        pending?.let { task ->
            val updated = task.copy(
                status = InboxTaskStatus.RUNNING,
                startedAt = System.currentTimeMillis()
            )
            updateTask(updated)
            updated
        }
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
        val current = _tasks.value
        _tasks.value = current + task
        persistTask(task)
        refreshDerivedFlows()
    }

    private suspend fun updateTask(task: InboxTask) = withContext(Dispatchers.IO) {
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
        if (!tasksFile.exists()) return@withContext
        val tasks = mutableListOf<InboxTask>()
        tasksFile.readLines().forEach { line ->
            if (line.isNotBlank()) {
                try {
                    tasks.add(InboxTask.fromJson(JSONObject(line)))
                } catch (e: Exception) {
                    // Skip corrupted lines
                }
            }
        }
        // Init-load races with the first writes: if anything landed in memory
        // before this disk read finished, in-memory state is authoritative.
        if (_tasks.value.isEmpty()) {
            _tasks.value = tasks
            refreshDerivedFlows()
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
        fun create(): LocalTaskInbox = LocalTaskInbox(
            fileStorage = FileStorage(JarvisEngine.appContext().filesDir),
            scope = CoroutineScope(Dispatchers.Main)
        )
    }
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
private fun jsonValue(v: Any): Any = when (v) {
    is Int, is Long, is Double, is Float, is Boolean, is String -> v
    is Map<*, *> -> JSONObject(v.mapKeys { it.key.toString() }.mapValues { jsonValue(it.value!!) })
    is List<*> -> org.json.JSONArray(v.map { jsonValue(it!!) })
    null -> org.json.JSONObject.NULL
    else -> v.toString()
}
