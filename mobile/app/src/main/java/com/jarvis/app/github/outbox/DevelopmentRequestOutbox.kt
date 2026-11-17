package com.jarvis.app.github.outbox

import com.jarvis.app.humancore.store.FileStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

/**
 * A bounded repository-development request before it is persisted.
 *
 * The outbox owns validation and identity: [logicalKey] lets a caller say
 * "this is the same request" even when surrounding evidence changes, while
 * content identity is derived from the request itself when no key is given.
 */
data class DevelopmentRequestDraft(
    val intendedOutcome: String,
    val evidence: List<String>,
    val acceptanceCriteria: List<String>,
    val risks: List<String> = emptyList(),
    val rollbackExpectation: String,
    val unresolvedQuestions: List<String> = emptyList(),
    val sourceRevision: String = "main",
    val createdAt: String? = null,
    val requestId: String? = null,
    val logicalKey: String? = null,
    val privacyStatus: String = "approved",
    val privacyReviewedBy: String,
    val privacyReviewedAt: String? = null,
    val aggregateRuntimeEvidenceEnabled: Boolean? = null,
    val aggregateRuntimeEvidenceScope: String? = null
)

/** Technical delivery state of one durable outbox entry. */
enum class DeliveryState {
    PENDING,
    DELIVERING,
    DELIVERED,
    FAILED
}

/** One request as it exists on disk, including its delivery bookkeeping. */
data class QueuedDevelopmentRequest(
    val requestId: String,
    val dedupKey: String,
    val state: DeliveryState,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val requestJson: String,
    val attempts: Int = 0,
    val lastAttemptAtMs: Long? = null,
    val deliveredAtMs: Long? = null,
    val lastError: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("request_id", requestId)
        put("dedup_key", dedupKey)
        put("state", state.name)
        put("created_at_ms", createdAtMs)
        put("updated_at_ms", updatedAtMs)
        put("request_json", requestJson)
        put("attempts", attempts)
        lastAttemptAtMs?.let { put("last_attempt_at_ms", it) }
        deliveredAtMs?.let { put("delivered_at_ms", it) }
        lastError?.let { put("last_error", it) }
    }

    companion object {
        fun fromJson(json: JSONObject): QueuedDevelopmentRequest {
            val requestId = json.getString("request_id")
            val requestJson = json.getString("request_json")
            val payload = runCatching { JSONObject(requestJson) }
                .getOrElse { throw OutboxStorageException("Queued request payload is not valid JSON.") }
            if (payload.optString("request_id") != requestId) {
                throw OutboxStorageException("Queued request payload id does not match its entry.")
            }
            val state = runCatching { DeliveryState.valueOf(json.getString("state")) }
                .getOrElse { throw OutboxStorageException("Queued request has an unknown delivery state.") }
            return QueuedDevelopmentRequest(
                requestId = requestId,
                dedupKey = json.getString("dedup_key"),
                state = state,
                createdAtMs = json.getLong("created_at_ms"),
                updatedAtMs = json.getLong("updated_at_ms"),
                requestJson = requestJson,
                attempts = json.optInt("attempts", 0),
                lastAttemptAtMs = json.optionalLong("last_attempt_at_ms"),
                deliveredAtMs = json.optionalLong("delivered_at_ms"),
                lastError = json.optString("last_error").takeIf { it.isNotBlank() }
            )
        }
    }
}

/** Result of an enqueue attempt. Invalid input is rejected, never partially stored. */
sealed interface EnqueueOutcome {
    data class Enqueued(val request: QueuedDevelopmentRequest) : EnqueueOutcome
    data class Duplicate(val request: QueuedDevelopmentRequest) : EnqueueOutcome
    data class Rejected(val reason: String) : EnqueueOutcome
}

/** Delivery outcome returned by a transport adapter. */
sealed interface DeliveryResult {
    object Delivered : DeliveryResult
    data class Retryable(val reason: String) : DeliveryResult
    data class Permanent(val reason: String) : DeliveryResult
}

/** The transport boundary. Production uses GitHub; tests use a deterministic fake. */
interface DevelopmentRequestDelivery {
    suspend fun deliver(request: QueuedDevelopmentRequest): DeliveryResult
}

data class DeliveryReport(
    val delivered: Int,
    val retried: Int,
    val failed: Int,
    val remainingPending: Int
) {
    val processed: Int get() = delivered + retried + failed
}

/** Raised when the on-disk outbox itself is damaged; valid entries are not overwritten. */
class OutboxStorageException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * Durable, deduplicated development-request outbox.
 *
 * There is no in-memory authoritative queue. Every operation reads the
 * snapshot from disk and atomically rewrites it through the existing
 * [FileStorage] writer (temp file, fsync, rename). A new instance over the
 * same directory therefore recovers pending work after process death.
 *
 * Delivery is at-least-once across a crash between the remote acknowledgement
 * and the local DELIVERED write. The request id is stable, so a replay is the
 * same logical request rather than a new one.
 */
class DevelopmentRequestOutbox(
    directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val claimLeaseMs: Long = DEFAULT_CLAIM_LEASE_MS
) {
    private val fileStorage = FileStorage(directory)
    private val file = File(directory, FILE_PATH)
    private val lock = lockFor(file)

    fun enqueue(draft: DevelopmentRequestDraft): EnqueueOutcome = synchronized(lock) {
        val now = clock()
        val dedupKey = dedupKeyFor(draft)
        val requestId = draft.requestId?.trim()?.takeIf { it.isNotEmpty() }
            ?: ("out-" + dedupKey.take(32))
        val createdAt = draft.createdAt?.trim()?.takeIf { it.isNotEmpty() }
            ?: Instant.ofEpochMilli(now).toString()
        val reviewedAt = draft.privacyReviewedAt?.trim()?.takeIf { it.isNotEmpty() }
            ?: createdAt

        validateDraft(draft, requestId, createdAt, reviewedAt)?.let {
            return@synchronized EnqueueOutcome.Rejected(it)
        }

        val payload = buildPayload(draft, requestId, createdAt, reviewedAt)
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_REQUEST_BYTES) {
            return@synchronized EnqueueOutcome.Rejected(
                "Request exceeds the ${MAX_REQUEST_BYTES / 1024} KiB repository limit."
            )
        }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        if (encoded.length >= MAX_WORKFLOW_INPUT_CHARS) {
            return@synchronized EnqueueOutcome.Rejected(
                "Request exceeds the GitHub workflow input limit."
            )
        }

        val requests = readRequests()
        requests.firstOrNull { it.dedupKey == dedupKey }?.let {
            return@synchronized EnqueueOutcome.Duplicate(it)
        }
        if (requests.any { it.requestId == requestId }) {
            return@synchronized EnqueueOutcome.Rejected(
                "request_id already belongs to a different logical request."
            )
        }

        val queued = QueuedDevelopmentRequest(
            requestId = requestId,
            dedupKey = dedupKey,
            state = DeliveryState.PENDING,
            createdAtMs = now,
            updatedAtMs = now,
            requestJson = payload.toString()
        )
        writeRequests(requests + queued)
        EnqueueOutcome.Enqueued(queued)
    }

    fun allRequests(): List<QueuedDevelopmentRequest> = synchronized(lock) { readRequests() }

    fun pendingRequests(): List<QueuedDevelopmentRequest> = synchronized(lock) {
        readRequests().filter { it.state == DeliveryState.PENDING }.sortedBy { it.createdAtMs }
    }

    fun hasPending(): Boolean = synchronized(lock) {
        readRequests().any { it.state == DeliveryState.PENDING || it.state == DeliveryState.DELIVERING }
    }

    /**
     * Atomically claim the oldest pending request. Stale DELIVERING entries
     * (older than the lease) are returned to PENDING first, so a process
     * killed during delivery does not strand the request forever.
     */
    fun claimNext(nowMs: Long = clock()): QueuedDevelopmentRequest? = synchronized(lock) {
        val requests = readRequests()
        val recovered = recoverStale(requests, nowMs)
        val next = recovered
            .filter { it.state == DeliveryState.PENDING }
            .minByOrNull { it.createdAtMs }
        val updated = if (next == null) {
            recovered
        } else {
            recovered.map { request ->
                if (request.requestId == next.requestId) {
                    request.copy(
                        state = DeliveryState.DELIVERING,
                        attempts = request.attempts + 1,
                        lastAttemptAtMs = nowMs,
                        updatedAtMs = nowMs
                    )
                } else {
                    request
                }
            }
        }
        if (updated != requests) writeRequests(updated)
        updated.firstOrNull { it.requestId == next?.requestId }
    }

    fun markDelivered(requestId: String, nowMs: Long = clock()): QueuedDevelopmentRequest? =
        transition(requestId, nowMs) { request ->
            when (request.state) {
                DeliveryState.DELIVERING, DeliveryState.PENDING -> request.copy(
                    state = DeliveryState.DELIVERED,
                    deliveredAtMs = nowMs,
                    lastError = null
                )
                else -> request
            }
        }

    fun markRetryableFailure(
        requestId: String,
        reason: String,
        nowMs: Long = clock()
    ): QueuedDevelopmentRequest? = transition(requestId, nowMs) { request ->
        when (request.state) {
            DeliveryState.DELIVERING, DeliveryState.PENDING -> request.copy(
                state = DeliveryState.PENDING,
                lastError = safeReason(reason)
            )
            else -> request
        }
    }

    fun markPermanentFailure(
        requestId: String,
        reason: String,
        nowMs: Long = clock()
    ): QueuedDevelopmentRequest? = transition(requestId, nowMs) { request ->
        when (request.state) {
            DeliveryState.DELIVERING, DeliveryState.PENDING -> request.copy(
                state = DeliveryState.FAILED,
                lastError = safeReason(reason)
            )
            else -> request
        }
    }

    /** Explicitly return a permanently failed request to the pending queue. */
    fun retry(requestId: String, nowMs: Long = clock()): QueuedDevelopmentRequest? =
        transition(requestId, nowMs) { request ->
            if (request.state == DeliveryState.FAILED) {
                request.copy(state = DeliveryState.PENDING, lastError = null)
            } else {
                request
            }
        }

    /**
     * Deliver every currently pending request once, in creation order.
     * State is persisted before the transport call (DELIVERING) and after it,
     * so another instance sees an accurate queue even if this process dies.
     */
    suspend fun deliverPending(
        delivery: DevelopmentRequestDelivery,
        maxRequests: Int = Int.MAX_VALUE,
        nowMs: () -> Long = clock
    ): DeliveryReport {
        var delivered = 0
        var retried = 0
        var failed = 0
        var processed = 0
        while (processed < maxRequests) {
            val request = claimNext(nowMs()) ?: break
            processed++
            val result = try {
                delivery.deliver(request)
            } catch (cancelled: CancellationException) {
                markRetryableFailure(request.requestId, "Delivery cancelled.", nowMs())
                throw cancelled
            } catch (error: Exception) {
                DeliveryResult.Retryable(error.message ?: error.javaClass.simpleName)
            }
            when (result) {
                DeliveryResult.Delivered -> {
                    markDelivered(request.requestId, nowMs())
                    delivered++
                }
                is DeliveryResult.Retryable -> {
                    markRetryableFailure(request.requestId, result.reason, nowMs())
                    retried++
                    // Do not immediately reclaim the same pending item in this drain.
                    // Let WorkManager apply backoff before attempting it again.
                    break
                }
                is DeliveryResult.Permanent -> {
                    markPermanentFailure(request.requestId, result.reason, nowMs())
                    failed++
                }
            }
        }
        return DeliveryReport(
            delivered = delivered,
            retried = retried,
            failed = failed,
            remainingPending = pendingRequests().size
        )
    }

    private fun transition(
        requestId: String,
        nowMs: Long,
        change: (QueuedDevelopmentRequest) -> QueuedDevelopmentRequest
    ): QueuedDevelopmentRequest? = synchronized(lock) {
        val requests = readRequests()
        var updatedRequest: QueuedDevelopmentRequest? = null
        val updated = requests.map { request ->
            if (request.requestId != requestId) {
                request
            } else {
                val changed = change(request)
                val withTimestamp = if (changed != request) {
                    changed.copy(updatedAtMs = nowMs)
                } else {
                    changed
                }
                updatedRequest = withTimestamp
                withTimestamp
            }
        }
        if (updated != requests) writeRequests(updated)
        updatedRequest
    }

    private fun recoverStale(
        requests: List<QueuedDevelopmentRequest>,
        nowMs: Long
    ): List<QueuedDevelopmentRequest> = requests.map { request ->
        if (request.state == DeliveryState.DELIVERING &&
            nowMs - (request.lastAttemptAtMs ?: request.updatedAtMs) >= claimLeaseMs
        ) {
            request.copy(
                state = DeliveryState.PENDING,
                updatedAtMs = nowMs,
                lastError = "Delivery was interrupted; it is queued again."
            )
        } else {
            request
        }
    }

    private fun readRequests(): List<QueuedDevelopmentRequest> {
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText(Charsets.UTF_8) }
            .getOrElse { throw OutboxStorageException("Could not read the development-request outbox.", it) }
        if (text.isBlank()) return emptyList()
        val root = runCatching { JSONObject(text) }
            .getOrElse { throw OutboxStorageException("The development-request outbox is not valid JSON.", it) }
        if (root.optInt("schema_version", -1) != SCHEMA_VERSION) {
            throw OutboxStorageException("Unsupported development-request outbox schema.")
        }
        val array = root.optJSONArray("requests")
            ?: throw OutboxStorageException("The development-request outbox has no request list.")
        val requests = ArrayList<QueuedDevelopmentRequest>(array.length())
        for (index in 0 until array.length()) {
            val entry = runCatching { array.getJSONObject(index) }
                .getOrElse { throw OutboxStorageException("Outbox entry $index is not an object.", it) }
            requests += runCatching { QueuedDevelopmentRequest.fromJson(entry) }
                .getOrElse { throw OutboxStorageException("Outbox entry $index is malformed.", it) }
        }
        return requests
    }

    private fun writeRequests(requests: List<QueuedDevelopmentRequest>) {
        val array = JSONArray()
        requests.forEach { array.put(it.toJson()) }
        val root = JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("requests", array)
        fileStorage.write(file, root.toString())
    }

    private fun buildPayload(
        draft: DevelopmentRequestDraft,
        requestId: String,
        createdAt: String,
        reviewedAt: String
    ): JSONObject {
        val payload = JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("request_id", requestId)
            .put("created_at", createdAt)
            .put("source_revision", draft.sourceRevision.trim())
            .put("intended_outcome", draft.intendedOutcome.trim())
            .put("evidence", stringArray(draft.evidence))
            .put("acceptance_criteria", stringArray(draft.acceptanceCriteria))
            .put("risks", stringArray(draft.risks))
            .put("rollback_expectation", draft.rollbackExpectation.trim())
            .put("unresolved_questions", stringArray(draft.unresolvedQuestions))
            .put(
                "privacy_review",
                JSONObject()
                    .put("status", draft.privacyStatus.trim())
                    .put("reviewed_by", draft.privacyReviewedBy.trim())
                    .put("reviewed_at", reviewedAt)
                    .apply {
                        draft.aggregateRuntimeEvidenceEnabled?.let {
                            put("aggregate_runtime_evidence_enabled", it)
                        }
                        draft.aggregateRuntimeEvidenceScope?.let {
                            put("aggregate_runtime_evidence_scope", it.trim())
                        }
                    }
            )
        return payload
    }

    private fun validateDraft(
        draft: DevelopmentRequestDraft,
        requestId: String,
        createdAt: String,
        reviewedAt: String
    ): String? {
        if (!REQUEST_ID.matches(requestId)) {
            return "request_id must match [a-z0-9][a-z0-9._-]{0,63}."
        }
        if (!isIsoTimestamp(createdAt)) return "created_at must be an ISO-8601 timestamp with a timezone."
        validateText(draft.sourceRevision, "source_revision", 128)?.let { return it }
        validateText(draft.intendedOutcome, "intended_outcome", 4000)?.let { return it }
        validateList(draft.evidence, "evidence", 1, 12, 8000)?.let { return it }
        validateList(draft.acceptanceCriteria, "acceptance_criteria", 1, 12, 600)?.let { return it }
        validateList(draft.risks, "risks", 0, 8, 500)?.let { return it }
        validateText(draft.rollbackExpectation, "rollback_expectation", 800)?.let { return it }
        validateList(draft.unresolvedQuestions, "unresolved_questions", 0, 8, 500)?.let { return it }
        if (draft.privacyStatus.trim() != "approved") {
            return "privacy_review.status must be approved."
        }
        validateText(draft.privacyReviewedBy, "privacy_review.reviewed_by", 120)?.let { return it }
        if (!isIsoTimestamp(reviewedAt)) {
            return "privacy_review.reviewed_at must be an ISO-8601 timestamp with a timezone."
        }
        draft.aggregateRuntimeEvidenceScope?.let { scope ->
            if (scope.trim() !in AGGREGATE_SCOPES) {
                return "privacy_review.aggregate_runtime_evidence_scope is unsupported."
            }
        }
        return null
    }

    private fun validateList(
        values: List<String>,
        field: String,
        minimum: Int,
        maximum: Int,
        itemMaximum: Int
    ): String? {
        if (values.size !in minimum..maximum) {
            return "$field must contain between $minimum and $maximum items."
        }
        values.forEachIndexed { index, value ->
            validateText(value, "$field[$index]", itemMaximum)?.let { return it }
        }
        return null
    }

    private fun validateText(value: String, field: String, maximum: Int): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "$field must not be empty."
        if (trimmed.length > maximum) return "$field exceeds $maximum characters."
        if (trimmed.any { it.code < 0x20 && it != '\n' && it != '\r' && it != '\t' }) {
            return "$field contains control characters."
        }
        if (SECRET_PATTERNS.any { it.containsMatchIn(trimmed) }) {
            return "$field contains credential-like content."
        }
        return null
    }

    private fun dedupKeyFor(draft: DevelopmentRequestDraft): String {
        draft.logicalKey?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return digest(listOf("logical", it))
        }
        val parts = mutableListOf("content")
        parts += draft.intendedOutcome.trim()
        parts += draft.sourceRevision.trim()
        parts += "evidence"
        draft.evidence.forEach { parts += it.trim() }
        parts += "acceptance"
        draft.acceptanceCriteria.forEach { parts += it.trim() }
        parts += "risks"
        draft.risks.forEach { parts += it.trim() }
        parts += "rollback"
        parts += draft.rollbackExpectation.trim()
        parts += "unresolved"
        draft.unresolvedQuestions.forEach { parts += it.trim() }
        return digest(parts)
    }

    private fun digest(parts: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeReason(reason: String): String =
        reason.lineSequence()
            .joinToString(" ")
            .replace(WHITESPACE, " ")
            .take(600)
            .let { value ->
                SECRET_PATTERNS.fold(value) { text, pattern -> pattern.replace(text, "[REDACTED]") }
            }

    private fun stringArray(values: List<String>): JSONArray = JSONArray().apply {
        values.forEach { put(it.trim()) }
    }

    private fun isIsoTimestamp(value: String): Boolean = runCatching {
        OffsetDateTime.parse(value)
    }.isSuccess

    private object LockRegistry {
        val locks = ConcurrentHashMap<String, Any>()
    }

    private fun lockFor(file: File): Any =
        LockRegistry.locks.computeIfAbsent(file.absoluteFile.normalize().path) { Any() }

    companion object {
        const val SCHEMA_VERSION = 1
        const val FILE_PATH = "development_requests/outbox.json"
        private const val MAX_REQUEST_BYTES = 32 * 1024
        private const val MAX_WORKFLOW_INPUT_CHARS = 60_000
        private const val DEFAULT_CLAIM_LEASE_MS = 10L * 60L * 1000L
        private val REQUEST_ID = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
        private val WHITESPACE = Regex("\\s+")
        private val AGGREGATE_SCOPES = setOf(
            "none",
            "counts_statuses_capability_keys_resource_trends_only",
            "aggregate_metrics_and_model_free_structural_self_study_no_personal_content"
        )
        private val SECRET_PATTERNS = listOf(
            Regex("gh[pousr]_[A-Za-z0-9]{20,}"),
            Regex("github_pat_[A-Za-z0-9_]{20,}"),
            Regex("AKIA[0-9A-Z]{16}"),
            Regex("sk-[A-Za-z0-9]{20,}"),
            Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----")
        )
    }
}

private fun JSONObject.optionalLong(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null
