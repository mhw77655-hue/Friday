package ai.friday.assistant.github.reporting

import ai.friday.assistant.humancore.store.FileStorage
import ai.friday.assistant.inbox.InboxTaskStatus
import ai.friday.assistant.inbox.LocalTaskInbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AgentTaskReplyInboxBridgeTest {
    @Test
    fun importsValidatedReplyAsOneApprovalGatedLocalTask() = runBlocking {
        val directory = File(System.getProperty("java.io.tmpdir"), "agent-reply-bridge-${System.nanoTime()}")
        directory.mkdirs()
        try {
            val inbox = LocalTaskInbox(FileStorage(directory), CoroutineScope(Dispatchers.Default))
            val bridge = AgentTaskReplyInboxBridge(
                context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                inbox = inbox
            )
            val envelope = AgentTaskEnvelope(
                taskId = "task-2026-bridge",
                messageId = "task-2026-bridge:in:1",
                sequence = 1,
                sender = "chatgpt-cloud",
                recipient = "friday",
                kind = AgentTaskMessageKind.RESULT,
                createdAtEpochMs = System.currentTimeMillis(),
                expiresAtEpochMs = System.currentTimeMillis() + 60_000,
                sourceRevision = "0123456789abcdef0123456789abcdef01234567",
                payload = JSONObject()
                    .put("data_classification", "public")
                    .put("summary", "Review the proposed repository repair")
                    .put("findings", "One bounded finding")
            )
            val reply = StoredAgentTaskReply(
                taskId = envelope.taskId,
                issueNumber = 456,
                issueUrl = "https://github.com/mhw77655-hue/Friday/issues/456",
                commentId = 789,
                authorLogin = "mhw77655-hue",
                createdAt = "2026-11-28T00:00:00Z",
                receivedAtEpochMs = System.currentTimeMillis(),
                envelope = envelope
            )

            val first = bridge.import(reply)
            val replay = bridge.import(reply)
            assertEquals(first.id, replay.id)
            assertEquals(InboxTaskStatus.WAITING_APPROVAL, first.status)
            assertTrue(first.requiresApproval)
            assertEquals("task-2026-bridge:in:1", first.arguments["message_id"])
            assertEquals("review_external_agent_proposal", first.capability)
        } finally {
            directory.deleteRecursively()
        }
    }
}
