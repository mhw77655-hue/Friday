package ai.friday.assistant.github.reporting

import ai.friday.assistant.humancore.store.FileStorage
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class StoredAgentTaskReply(
    val taskId: String,
    val issueNumber: Int,
    val issueUrl: String,
    val commentId: Long,
    val authorLogin: String,
    val createdAt: String,
    val receivedAtEpochMs: Long,
    val envelope: AgentTaskEnvelope,
    val isRead: Boolean = false
) {
    init {
        require(taskId == envelope.taskId)
        require(issueNumber > 0 && issueUrl.startsWith("https://github.com/"))
        require(commentId > 0L && authorLogin.isNotBlank())
        require(receivedAtEpochMs > 0L)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("task_id", taskId)
        .put("issue_number", issueNumber)
        .put("issue_url", issueUrl)
        .put("comment_id", commentId)
        .put("author_login", authorLogin)
        .put("created_at", createdAt)
        .put("received_at_epoch_ms", receivedAtEpochMs)
        .put("envelope", envelope.toJson())
        .put("is_read", isRead)

    companion object {
        fun fromJson(json: JSONObject) = StoredAgentTaskReply(
            taskId = json.getString("task_id"),
            issueNumber = json.getInt("issue_number"),
            issueUrl = json.getString("issue_url"),
            commentId = json.getLong("comment_id"),
            authorLogin = json.getString("author_login"),
            createdAt = json.optString("created_at"),
            receivedAtEpochMs = json.getLong("received_at_epoch_ms"),
            envelope = AgentTaskEnvelope.fromJson(json.getJSONObject("envelope")),
            isRead = json.optBoolean("is_read", false)
        )
    }
}

/**
 * Durable local inbox for external agent replies. Replies are persisted before
 * the GitHub thread is acknowledged, so process death cannot consume a result.
 */
class AgentTaskReplyStore(directory: File) {
    private val file = File(directory, "friday-development/agent-task-replies.jsonl")
    private val storage = FileStorage(directory)
    private val lock = lockFor(file)

    fun all(): List<StoredAgentTaskReply> = synchronized(lock) { readLocked() }

    fun latest(limit: Int = 20): List<StoredAgentTaskReply> {
        require(limit in 1..100)
        return all().sortedByDescending { it.receivedAtEpochMs }.take(limit)
    }

    fun unreadCount(): Int = all().count { !it.isRead }

    /** Persist a reply once; false means the message was already in the journal. */
    fun appendIfAbsent(reply: StoredAgentTaskReply): Boolean = synchronized(lock) {
        val existing = readLocked()
        if (existing.any { it.envelope.messageId == reply.envelope.messageId }) return false
        val retained = (existing + reply)
            .sortedBy { it.receivedAtEpochMs }
            .takeLast(MAX_RETAINED_REPLIES)
        val content = retained.joinToString("\n") { it.toJson().toString() } + "\n"
        storage.write(file, content)
        true
    }

    fun markRead(messageId: String) = synchronized(lock) {
        require(messageId.isNotBlank())
        val existing = readLocked()
        val updated = existing.map { reply ->
            if (reply.envelope.messageId == messageId) reply.copy(isRead = true) else reply
        }
        writeLocked(updated)
    }

    fun markAllRead() = synchronized(lock) {
        writeLocked(readLocked().map { it.copy(isRead = true) })
    }

    private fun readLocked(): List<StoredAgentTaskReply> {
        if (!file.isFile) return emptyList()
        return try {
            file.readLines(Charsets.UTF_8).mapNotNull { line ->
                if (line.isBlank()) null
                else runCatching { StoredAgentTaskReply.fromJson(JSONObject(line)) }.getOrNull()
            }
        } catch (failure: Exception) {
            throw IOException("Friday agent reply journal could not be read; existing data was not overwritten.", failure)
        }
    }

    private fun writeLocked(replies: List<StoredAgentTaskReply>) {
        val content = replies.sortedBy { it.receivedAtEpochMs }
            .takeLast(MAX_RETAINED_REPLIES)
            .joinToString("\n") { it.toJson().toString() } + "\n"
        storage.write(file, content)
    }

    companion object {
        private const val MAX_RETAINED_REPLIES = 2_000
        private val locks = ConcurrentHashMap<String, Any>()

        private fun lockFor(file: File): Any {
            val key = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            return locks.computeIfAbsent(key) { Any() }
        }
    }
}
