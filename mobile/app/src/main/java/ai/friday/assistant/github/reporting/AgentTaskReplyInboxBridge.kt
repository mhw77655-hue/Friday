package ai.friday.assistant.github.reporting

import ai.friday.assistant.inbox.InboxTask
import ai.friday.assistant.inbox.LocalTaskInbox
import java.security.MessageDigest

/**
 * Imports a durable, validated public GitHub reply into Friday's canonical
 * local task inbox as a proposal requiring owner approval. Stable IDs make the
 * bridge safe to replay after a crash between local persistence and GitHub ack.
 */
class AgentTaskReplyInboxBridge(
    private val inbox: LocalTaskInbox = LocalTaskInbox.create()
) {
    suspend fun import(reply: StoredAgentTaskReply): InboxTask {
        require(reply.envelope.dataClassification == "public") {
            "Only public-classified GitHub replies may enter this inbox bridge."
        }
        val payload = reply.envelope.payload
        val summary = payload.optString("summary")
            .ifBlank { "External ChatGPT proposal for Friday task ${reply.taskId}" }
            .take(240)
        val stableId = "agent-reply:" + sha256(reply.envelope.messageId).take(40)
        return inbox.submitOnce(
            id = stableId,
            source = "chatgpt-cloud",
            description = summary,
            capability = "review_external_agent_proposal",
            arguments = mapOf(
                "task_id" to reply.taskId,
                "message_id" to reply.envelope.messageId,
                "issue_number" to reply.issueNumber,
                "issue_url" to reply.issueUrl,
                "source_revision" to reply.envelope.sourceRevision,
                "message_kind" to reply.envelope.kind.name,
                "author_login" to reply.authorLogin,
                "payload_json" to payload.toString()
            ),
            requiresApproval = true
        )
    }

    companion object {
        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
