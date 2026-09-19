package com.jarvis.app.approvals

import com.jarvis.app.JarvisEngine
import com.jarvis.app.humancore.store.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData

/**
 * Local Approval Queue - manages approval requests for high-risk actions.
 * Persists to filesDir/approvals/approvals.jsonl
 * Works offline, syncs with OS Portal when attached.
 */
class LocalApprovalQueue(
    private val fileStorage: FileStorage,
    private val scope: CoroutineScope
) {
    private val approvalsDir = fileStorage.baseDir.resolve("approvals")
    private val approvalsFile = approvalsDir.resolve("approvals.jsonl")
    private val idCounter = AtomicLong(System.currentTimeMillis())

    private val _approvals = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val approvals = _approvals.asStateFlow()

    private val _pendingApprovals = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val pendingApprovals = _pendingApprovals.asStateFlow()

    init {
        approvalsDir.mkdirs()
        scope.launch { loadApprovals() }
    }

    /** UI-friendly observe method returning LiveData of ApprovalEntry */
    fun observeApprovalsForUI(): LiveData<List<ApprovalEntry>> {
        return pendingApprovals
            .map { it.map { ApprovalEntry.from(it) } }
            .asLiveData(Dispatchers.Main)
    }

    /** Request approval for an action */
    suspend fun requestApproval(
        taskId: String,
        capability: String,
        riskLevel: RiskLevel,
        description: String,
        arguments: Map<String, Any>,
        requestedBy: String,
        expiresInMs: Long = 5 * 60 * 1000 // 5 minutes default
    ): ApprovalRequest {
        val approval = ApprovalRequest(
            id = "approval-${idCounter.incrementAndGet()}",
            taskId = taskId,
            capability = capability,
            riskLevel = riskLevel,
            description = description,
            arguments = arguments,
            requestedBy = requestedBy,
            status = ApprovalStatus.PENDING,
            requestedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + expiresInMs,
            decidedAt = null,
            decidedBy = null,
            decision = null
        )
        addApproval(approval)
        return approval
    }

    /** Get approval by ID */
    suspend fun getApproval(id: String): ApprovalRequest? = withContext(Dispatchers.IO) {
        _approvals.value.find { it.id == id }
    }

    /** List approvals by status */
    suspend fun listApprovals(status: ApprovalStatus? = null): List<ApprovalRequest> = withContext(Dispatchers.IO) {
        _approvals.value.filter { status == null || it.status == status }
    }

    /** Approve a request */
    suspend fun approve(approvalId: String, decidedBy: String): Result<Unit> = withContext(Dispatchers.IO) {
        val approval = _approvals.value.find { it.id == approvalId }
            ?: return@withContext Result.failure(IllegalArgumentException("Approval not found"))
        if (approval.status != ApprovalStatus.PENDING) {
            return@withContext Result.failure(IllegalStateException("Approval not pending"))
        }
        val updated = approval.copy(
            status = ApprovalStatus.APPROVED,
            decidedAt = System.currentTimeMillis(),
            decidedBy = decidedBy,
            decision = ApprovalDecision.APPROVE
        )
        updateApproval(updated)
        Result.success(Unit)
    }

    /** Deny a request */
    suspend fun deny(approvalId: String, decidedBy: String, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        val approval = _approvals.value.find { it.id == approvalId }
            ?: return@withContext Result.failure(IllegalArgumentException("Approval not found"))
        if (approval.status != ApprovalStatus.PENDING) {
            return@withContext Result.failure(IllegalStateException("Approval not pending"))
        }
        val updated = approval.copy(
            status = ApprovalStatus.DENIED,
            decidedAt = System.currentTimeMillis(),
            decidedBy = decidedBy,
            decision = ApprovalDecision.DENY
        )
        updateApproval(updated)
        Result.success(Unit)
    }

    /** Defer a request */
    suspend fun defer(approvalId: String, decidedBy: String): Result<Unit> = withContext(Dispatchers.IO) {
        val approval = _approvals.value.find { it.id == approvalId }
            ?: return@withContext Result.failure(IllegalArgumentException("Approval not found"))
        if (approval.status != ApprovalStatus.PENDING) {
            return@withContext Result.failure(IllegalStateException("Approval not pending"))
        }
        val updated = approval.copy(
            status = ApprovalStatus.DEFERRED,
            decidedAt = System.currentTimeMillis(),
            decidedBy = decidedBy,
            decision = ApprovalDecision.DEFER
        )
        updateApproval(updated)
        Result.success(Unit)
    }

    /** Check for expired approvals */
    suspend fun checkExpired(): List<ApprovalRequest> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val expired = _approvals.value.filter {
            it.status == ApprovalStatus.PENDING && it.expiresAt < now
        }
        expired.forEach { approval ->
            val updated = approval.copy(
                status = ApprovalStatus.EXPIRED,
                decidedAt = now,
                decidedBy = "system",
                decision = ApprovalDecision.EXPIRE
            )
            updateApproval(updated)
        }
        expired
    }

    private suspend fun addApproval(approval: ApprovalRequest) = withContext(Dispatchers.IO) {
        val current = _approvals.value
        _approvals.value = current + approval
        persistApproval(approval)
        refreshDerivedFlows()
    }

    private suspend fun updateApproval(approval: ApprovalRequest) = withContext(Dispatchers.IO) {
        val current = _approvals.value
        val index = current.indexOfFirst { it.id == approval.id }
        if (index >= 0) {
            val updated = current.toMutableList().apply { set(index, approval) }
            _approvals.value = updated
            rewriteAllApprovals()
            refreshDerivedFlows()
        }
    }

    private fun refreshDerivedFlows() {
        _pendingApprovals.value = _approvals.value.filter { it.status == ApprovalStatus.PENDING }
    }

    private suspend fun loadApprovals() = withContext(Dispatchers.IO) {
        if (!approvalsFile.exists()) return@withContext
        val approvals = mutableListOf<ApprovalRequest>()
        approvalsFile.readLines().forEach { line ->
            if (line.isNotBlank()) {
                try {
                    approvals.add(ApprovalRequest.fromJson(JSONObject(line)))
                } catch (e: Exception) {
                    // Skip corrupted lines
                }
            }
        }
        // Init-load races with the first writes: if anything landed in memory
        // before this disk read finished, in-memory state is authoritative.
        if (_approvals.value.isEmpty()) {
            _approvals.value = approvals
            refreshDerivedFlows()
        }
    }

    private fun persistApproval(approval: ApprovalRequest) {
        fileStorage.append(approvalsFile, approval.toJson().toString())
    }

    private fun rewriteAllApprovals() {
        val content = _approvals.value.joinToString("\n") { it.toJson().toString() } + "\n"
        fileStorage.write(approvalsFile, content)
    }

    companion object {
        fun create(): LocalApprovalQueue = LocalApprovalQueue(
            fileStorage = FileStorage(JarvisEngine.appContext().filesDir),
            scope = CoroutineScope(Dispatchers.Main)
        )
    }
}

/** Approval request entity */
data class ApprovalRequest(
    val id: String,
    val taskId: String,
    val capability: String,
    val riskLevel: RiskLevel,
    val description: String,
    val arguments: Map<String, Any>,
    val requestedBy: String,
    val status: ApprovalStatus,
    val requestedAt: Long,
    val expiresAt: Long,
    val decidedAt: Long?,
    val decidedBy: String?,
    val decision: ApprovalDecision?
) {
    /** Manual JSON round-trip (kotlinx-serialization is not in the build). */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("taskId", taskId)
        put("capability", capability)
        put("riskLevel", riskLevel.name)
        put("description", description)
        put("arguments", JSONObject(arguments.mapValues { (_, v) -> jsonApprovalValue(v) }))
        put("requestedBy", requestedBy)
        put("status", status.name)
        put("requestedAt", requestedAt)
        put("expiresAt", expiresAt)
        decidedAt?.let { put("decidedAt", it) }
        decidedBy?.let { put("decidedBy", it) }
        decision?.let { put("decision", it.name) }
    }

    companion object {
        fun fromJson(o: JSONObject): ApprovalRequest = ApprovalRequest(
            id = o.getString("id"),
            taskId = o.optString("taskId"),
            capability = o.optString("capability"),
            riskLevel = runCatching { RiskLevel.valueOf(o.getString("riskLevel")) }
                .getOrDefault(RiskLevel.MEDIUM),
            description = o.optString("description"),
            arguments = o.optJSONObject("arguments")?.let { fromJsonArguments(it) } ?: emptyMap(),
            requestedBy = o.optString("requestedBy"),
            status = runCatching { ApprovalStatus.valueOf(o.getString("status")) }
                .getOrDefault(ApprovalStatus.PENDING),
            requestedAt = o.optLong("requestedAt"),
            expiresAt = o.optLong("expiresAt"),
            decidedAt = if (o.has("decidedAt")) o.optLong("decidedAt") else null,
            decidedBy = o.optString("decidedBy").takeIf { it.isNotBlank() },
            decision = o.optString("decision").takeIf { it.isNotBlank() }
                ?.let { runCatching { ApprovalDecision.valueOf(it) }.getOrNull() }
        )

        private fun fromJsonArguments(jo: JSONObject): Map<String, Any> {
            val out = mutableMapOf<String, Any>()
            jo.keys().forEach { key ->
                val v = jo.opt(key)
                if (v != null && v != org.json.JSONObject.NULL) out[key] = v
            }
            return out
        }
    }
}

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    DENIED,
    DEFERRED,
    EXPIRED
}

enum class ApprovalDecision {
    APPROVE,
    DENY,
    DEFER,
    EXPIRE
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/** UI-friendly approval entry */
data class ApprovalEntry(
    val id: String,
    val taskId: String,
    val capability: String,
    val riskLevel: String,
    val description: String,
    val status: String,
    val expiresAt: Long
) {
    companion object {
        fun from(approval: ApprovalRequest): ApprovalEntry = ApprovalEntry(
            id = approval.id,
            taskId = approval.taskId,
            capability = approval.capability,
            riskLevel = approval.riskLevel.name,
            description = approval.description,
            status = approval.status.name,
            expiresAt = approval.expiresAt
        )
    }
}

/** Map value that JSONObject can store directly. */
private fun jsonApprovalValue(v: Any): Any = when (v) {
    is Int, is Long, is Double, is Float, is Boolean, is String -> v
    is Map<*, *> -> JSONObject(v.mapKeys { it.key.toString() }.mapValues { jsonApprovalValue(it.value!!) })
    is List<*> -> org.json.JSONArray(v.map { jsonApprovalValue(it!!) })
    null -> org.json.JSONObject.NULL
    else -> v.toString()
}
