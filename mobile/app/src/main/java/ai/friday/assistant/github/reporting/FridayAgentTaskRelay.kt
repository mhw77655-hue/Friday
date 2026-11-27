package ai.friday.assistant.github.reporting

import ai.friday.assistant.humancore.store.FileStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

enum class AgentTaskMessageKind {
    REQUEST,
    PROGRESS,
    EVIDENCE_REQUEST,
    RESULT,
    FAILURE,
    CANCEL
}

/**
 * Versioned, bounded message shared by Friday and a collaborating cloud agent.
 * The payload is task-scoped data only; callers decide what evidence is allowed.
 */
data class AgentTaskEnvelope(
    val taskId: String,
    val messageId: String,
    val sequence: Long,
    val sender: String,
    val recipient: String,
    val kind: AgentTaskMessageKind,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val sourceRevision: String,
    val payload: JSONObject,
    val schemaVersion: Int = SCHEMA_VERSION
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) { "Unsupported Friday agent message schema." }
        require(taskId.matches(TASK_ID_PATTERN)) { "Friday task ID is malformed." }
        require(messageId.matches(MESSAGE_ID_PATTERN)) { "Friday task message ID is malformed." }
        require(sequence >= 0L) { "Friday task message sequence is invalid." }
        require(sender.matches(ACTOR_PATTERN) && recipient.matches(ACTOR_PATTERN)) {
            "Friday task sender or recipient is malformed."
        }
        require(createdAtEpochMs > 0L && expiresAtEpochMs > createdAtEpochMs) {
            "Friday task message timestamps are invalid."
        }
        require(expiresAtEpochMs - createdAtEpochMs <= MAX_TTL_MS) {
            "Friday task message expiry exceeds the allowed lifetime."
        }
        require(sourceRevision.isNotBlank() && sourceRevision.length <= 80) {
            "Friday task source revision is invalid."
        }
        require(payload.toString().length <= MAX_PAYLOAD_CHARS) {
            "Friday task payload exceeds its size limit."
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("schema_version", schemaVersion)
        .put("task_id", taskId)
        .put("message_id", messageId)
        .put("sequence", sequence)
        .put("sender", sender)
        .put("recipient", recipient)
        .put("kind", kind.name.lowercase())
        .put("created_at_epoch_ms", createdAtEpochMs)
        .put("expires_at_epoch_ms", expiresAtEpochMs)
        .put("source_revision", sourceRevision)
        .put("payload", JSONObject(payload.toString()))

    companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_PAYLOAD_CHARS = 9_000
        const val MAX_TTL_MS = 30L * 24L * 60L * 60L * 1000L
        private val TASK_ID_PATTERN = Regex("[A-Za-z0-9._-]{8,80}")
        private val MESSAGE_ID_PATTERN = Regex("[A-Za-z0-9._:-]{8,160}")
        private val ACTOR_PATTERN = Regex("[A-Za-z0-9._-]{2,80}")

        fun fromJson(json: JSONObject): AgentTaskEnvelope {
            val kind = runCatching {
                AgentTaskMessageKind.valueOf(json.getString("kind").uppercase())
            }.getOrElse { throw IllegalArgumentException("Friday task message kind is unknown.") }
            return AgentTaskEnvelope(
                schemaVersion = json.optInt("schema_version", -1),
                taskId = json.optString("task_id"),
                messageId = json.optString("message_id"),
                sequence = json.optLong("sequence", -1L),
                sender = json.optString("sender"),
                recipient = json.optString("recipient"),
                kind = kind,
                createdAtEpochMs = json.optLong("created_at_epoch_ms", 0L),
                expiresAtEpochMs = json.optLong("expires_at_epoch_ms", 0L),
                sourceRevision = json.optString("source_revision"),
                payload = json.optJSONObject("payload")
                    ?: throw IllegalArgumentException("Friday task payload is missing.")
            )
        }
    }
}

data class AgentTaskThread(
    val taskId: String,
    val issueNumber: Int,
    val issueUrl: String,
    val sourceRevision: String,
    val createdAtEpochMs: Long,
    val nextOutgoingSequence: Long = 1L,
    val acknowledgedCommentIds: Set<Long> = emptySet(),
    val acknowledgedMessageIds: Set<String> = emptySet()
) {
    init {
        require(taskId.matches(Regex("[A-Za-z0-9._-]{8,80}")))
        require(issueNumber > 0 && issueUrl.startsWith("https://github.com/"))
        require(sourceRevision.isNotBlank() && sourceRevision.length <= 80)
        require(createdAtEpochMs > 0L && nextOutgoingSequence >= 1L)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("task_id", taskId)
        .put("issue_number", issueNumber)
        .put("issue_url", issueUrl)
        .put("source_revision", sourceRevision)
        .put("created_at_epoch_ms", createdAtEpochMs)
        .put("next_outgoing_sequence", nextOutgoingSequence)
        .put("acknowledged_comment_ids", JSONArray(acknowledgedCommentIds.sorted()))
        .put("acknowledged_message_ids", JSONArray(acknowledgedMessageIds.sorted()))

    companion object {
        fun fromJson(json: JSONObject): AgentTaskThread {
            val commentIds = json.optJSONArray("acknowledged_comment_ids") ?: JSONArray()
            val messageIds = json.optJSONArray("acknowledged_message_ids") ?: JSONArray()
            return AgentTaskThread(
                taskId = json.getString("task_id"),
                issueNumber = json.getInt("issue_number"),
                issueUrl = json.getString("issue_url"),
                sourceRevision = json.getString("source_revision"),
                createdAtEpochMs = json.getLong("created_at_epoch_ms"),
                nextOutgoingSequence = json.optLong("next_outgoing_sequence", 1L),
                acknowledgedCommentIds = (0 until commentIds.length())
                    .mapNotNull { commentIds.optLong(it).takeIf { id -> id > 0L } }.toSet(),
                acknowledgedMessageIds = (0 until messageIds.length())
                    .mapNotNull { messageIds.optString(it).takeIf(String::isNotBlank) }.toSet()
            )
        }
    }
}

/**
 * Atomic local journal for task-thread identity and acknowledgements. A reply
 * is acknowledged only after the caller has persisted it in Friday's own inbox.
 */
class AgentTaskThreadStore(directory: File) {
    private val file = File(directory, "friday-development/agent-task-threads.json")
    private val storage = FileStorage(directory)
    private val lock = lockFor(file)

    fun get(taskId: String): AgentTaskThread? = synchronized(lock) {
        readLocked().firstOrNull { it.taskId == taskId }
    }

    fun all(): List<AgentTaskThread> = synchronized(lock) {
        readLocked().sortedBy { it.createdAtEpochMs }
    }

    fun put(thread: AgentTaskThread) = synchronized(lock) {
        val existing = readLocked()
        val current = existing.firstOrNull { it.taskId == thread.taskId }
        if (current != null && current.issueNumber != thread.issueNumber) {
            throw IllegalStateException("Friday task ID is already bound to a different GitHub issue.")
        }
        writeLocked(existing.filterNot { it.taskId == thread.taskId } + thread)
    }

    fun claimOutgoingSequence(taskId: String): Long = synchronized(lock) {
        val threads = readLocked()
        val current = threads.firstOrNull { it.taskId == taskId }
            ?: throw IllegalArgumentException("Friday task thread is not registered.")
        val sequence = current.nextOutgoingSequence
        val updated = current.copy(nextOutgoingSequence = sequence + 1L)
        writeLocked(threads.filterNot { it.taskId == taskId } + updated)
        sequence
    }

    fun acknowledge(taskId: String, commentId: Long, messageId: String) = synchronized(lock) {
        require(commentId > 0L && messageId.isNotBlank())
        val threads = readLocked()
        val current = threads.firstOrNull { it.taskId == taskId }
            ?: throw IllegalArgumentException("Friday task thread is not registered.")
        val updated = current.copy(
            acknowledgedCommentIds = current.acknowledgedCommentIds + commentId,
            acknowledgedMessageIds = current.acknowledgedMessageIds + messageId
        )
        writeLocked(threads.filterNot { it.taskId == taskId } + updated)
    }

    private fun readLocked(): List<AgentTaskThread> {
        if (!file.isFile) return emptyList()
        return try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            (0 until array.length()).map { index ->
                AgentTaskThread.fromJson(
                    array.optJSONObject(index)
                        ?: throw IllegalStateException("Friday task journal contains an invalid entry.")
                )
            }
        } catch (failure: Exception) {
            throw IOException("Friday task journal is damaged; existing data was not overwritten.", failure)
        }
    }

    private fun writeLocked(threads: List<AgentTaskThread>) {
        val json = JSONArray()
        threads.sortedBy { it.createdAtEpochMs }.forEach { json.put(it.toJson()) }
        storage.write(file, json.toString())
    }

    companion object {
        private val locks = ConcurrentHashMap<String, Any>()
        private fun lockFor(file: File): Any {
            val key = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            return locks.computeIfAbsent(key) { Any() }
        }
    }
}

data class AgentTaskReply(
    val commentId: Long,
    val authorLogin: String,
    val createdAt: String,
    val envelope: AgentTaskEnvelope
)

/**
 * Friday-owned collaboration boundary. It does not route owner conversation
 * turns to ChatGPT and does not execute returned proposals.
 */
class FridayAgentTaskRelay(
    private val github: GitHubDevelopmentRepositoryClient,
    private val threads: AgentTaskThreadStore,
    trustedActors: Set<String>,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val trustedActors = trustedActors.map { it.trim().lowercase() }
        .filter { it.isNotBlank() }.toSet()

    init {
        require(this.trustedActors.isNotEmpty()) {
            "At least one trusted GitHub actor must be explicitly configured."
        }
    }

    /** Create or recover a task thread using a stable task ID. */
    fun createTask(
        taskId: String,
        title: String,
        sourceRevision: String,
        allowedTaskPayload: JSONObject,
        ttlMs: Long = DEFAULT_TASK_TTL_MS
    ): AgentTaskThread {
        threads.get(taskId)?.let { return it }
        val now = clock()
        require(ttlMs in 60_000L..AgentTaskEnvelope.MAX_TTL_MS)
        val request = AgentTaskEnvelope(
            taskId = taskId,
            messageId = "$taskId:request",
            sequence = 0L,
            sender = "friday",
            recipient = "chatgpt-cloud",
            kind = AgentTaskMessageKind.REQUEST,
            createdAtEpochMs = now,
            expiresAtEpochMs = now + ttlMs,
            sourceRevision = sourceRevision,
            payload = JSONObject(allowedTaskPayload.toString())
        )
        val issue = github.createOrFindAgentTaskIssue(taskId, title, request.toJson().toString())
        val thread = AgentTaskThread(
            taskId = taskId,
            issueNumber = issue.number,
            issueUrl = issue.url,
            sourceRevision = sourceRevision,
            createdAtEpochMs = now
        )
        threads.put(thread)
        return thread
    }

    /**
     * Send Friday progress/evidence/cancellation. Sequence is durably claimed
     * before network I/O; a crash can leave a harmless sequence gap, never a
     * duplicate message identity.
     */
    fun send(
        taskId: String,
        kind: AgentTaskMessageKind,
        payload: JSONObject,
        ttlMs: Long = DEFAULT_TASK_TTL_MS
    ): GitHubDevelopmentRepositoryClient.AgentTaskComment {
        require(kind in setOf(
            AgentTaskMessageKind.PROGRESS,
            AgentTaskMessageKind.EVIDENCE_REQUEST,
            AgentTaskMessageKind.CANCEL
        )) { "Friday may send only progress, evidence requests or cancellation on an active task." }
        val thread = threads.get(taskId)
            ?: throw IllegalArgumentException("Friday task thread is not registered.")
        val now = clock()
        require(ttlMs in 60_000L..AgentTaskEnvelope.MAX_TTL_MS)
        val sequence = threads.claimOutgoingSequence(taskId)
        val envelope = AgentTaskEnvelope(
            taskId = taskId,
            messageId = "$taskId:out:$sequence",
            sequence = sequence,
            sender = "friday",
            recipient = "chatgpt-cloud",
            kind = kind,
            createdAtEpochMs = now,
            expiresAtEpochMs = now + ttlMs,
            sourceRevision = thread.sourceRevision,
            payload = JSONObject(payload.toString())
        )
        return github.postAgentTaskComment(
            issueNumber = thread.issueNumber,
            taskId = taskId,
            sequence = sequence,
            messageBody = envelope.toJson().toString()
        )
    }

    /**
     * Return trusted, unacknowledged replies. The caller must first persist each
     * reply in Friday's own task inbox, then call acknowledge().
     */
    fun pollReplies(taskId: String): List<AgentTaskReply> {
        val thread = threads.get(taskId)
            ?: throw IllegalArgumentException("Friday task thread is not registered.")
        val now = clock()
        val comments = github.readAgentTaskComments(thread.issueNumber)
        val seenInThisPoll = HashSet<String>()
        return comments.mapNotNull { comment ->
            if (comment.id in thread.acknowledgedCommentIds) return@mapNotNull null
            if (comment.authorLogin.lowercase() !in trustedActors) return@mapNotNull null
            val marker = COMMENT_MARKER.find(comment.body) ?: return@mapNotNull null
            if (marker.groupValues[1] != taskId) return@mapNotNull null
            val sequence = marker.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val jsonText = comment.body.substring(marker.range.last + 1).trim()
            val envelope = runCatching {
                AgentTaskEnvelope.fromJson(JSONObject(jsonText))
            }.getOrNull() ?: return@mapNotNull null
            if (envelope.taskId != taskId || envelope.sequence != sequence) return@mapNotNull null
            if (envelope.messageId in thread.acknowledgedMessageIds ||
                !seenInThisPoll.add(envelope.messageId)
            ) return@mapNotNull null
            if (envelope.sender != "chatgpt-cloud" || envelope.recipient != "friday") return@mapNotNull null
            if (envelope.kind == AgentTaskMessageKind.REQUEST || envelope.kind == AgentTaskMessageKind.CANCEL) {
                return@mapNotNull null
            }
            if (envelope.expiresAtEpochMs <= now || envelope.createdAtEpochMs > now + MAX_CLOCK_SKEW_MS) {
                return@mapNotNull null
            }
            if (envelope.sourceRevision != thread.sourceRevision) return@mapNotNull null
            AgentTaskReply(
                commentId = comment.id,
                authorLogin = comment.authorLogin,
                createdAt = comment.createdAt,
                envelope = envelope
            )
        }.sortedBy { it.envelope.sequence }
    }

    /** Call only after the reply has been durably stored in Friday's local inbox. */
    fun acknowledge(taskId: String, reply: AgentTaskReply) {
        require(reply.envelope.taskId == taskId)
        threads.acknowledge(taskId, reply.commentId, reply.envelope.messageId)
    }

    companion object {
        const val DEFAULT_TASK_TTL_MS = 24L * 60L * 60L * 1000L
        private const val MAX_CLOCK_SKEW_MS = 5L * 60L * 1000L
        private val COMMENT_MARKER = Regex(
            "^<!-- friday-agent-message:v1:([A-Za-z0-9._-]{8,80}):(\\d+) -->"
        )
    }
}
