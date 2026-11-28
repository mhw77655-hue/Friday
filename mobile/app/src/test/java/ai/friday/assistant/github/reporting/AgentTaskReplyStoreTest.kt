package ai.friday.assistant.github.reporting

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTaskReplyStoreTest {
    @Test
    fun corruptJournalFailsClosedAndPreservesOriginalBytes() {
        val directory = Files.createTempDirectory("friday-agent-replies-corrupt").toFile()
        try {
            val journal = java.io.File(directory, "friday-development/agent-task-replies.jsonl")
            journal.parentFile.mkdirs()
            val original = """{"task_id":"incomplete-record"}\\nnot-json\\n"""
            journal.writeText(original)

            val store = AgentTaskReplyStore(directory)
            val readFailure = runCatching { store.all() }.exceptionOrNull()
            assertTrue(readFailure is java.io.IOException)
            assertEquals(original, journal.readText())

            val appendFailure = runCatching {
                val envelope = AgentTaskEnvelope(
                    taskId = "task-2026-005",
                    messageId = "task-2026-005:in:1",
                    sequence = 1L,
                    sender = "chatgpt-cloud",
                    recipient = "friday",
                    kind = AgentTaskMessageKind.RESULT,
                    createdAtEpochMs = 1_795_800_000_000L,
                    expiresAtEpochMs = 1_795_800_060_000L,
                    sourceRevision = "main",
                    payload = JSONObject().put("data_classification", "public")
                )
                store.appendIfAbsent(
                    StoredAgentTaskReply(
                        taskId = envelope.taskId,
                        issueNumber = 322,
                        issueUrl = "https://github.com/mhw77655-hue/Friday/issues/322",
                        commentId = 9006L,
                        authorLogin = "mhw77655-hue",
                        createdAt = "2026-11-28T00:00:00Z",
                        receivedAtEpochMs = 1_795_800_000_500L,
                        envelope = envelope
                    )
                )
            }.exceptionOrNull()
            assertTrue(appendFailure is java.io.IOException)
            assertEquals(original, journal.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun persistsRepliesDeduplicatesAndMarksReadAcrossReopen() {
        val directory = Files.createTempDirectory("friday-agent-replies").toFile()
        try {
            val envelope = AgentTaskEnvelope(
                taskId = "task-2026-004",
                messageId = "task-2026-004:in:1",
                sequence = 1L,
                sender = "chatgpt-cloud",
                recipient = "friday",
                kind = AgentTaskMessageKind.RESULT,
                createdAtEpochMs = 1_795_800_000_000L,
                expiresAtEpochMs = 1_795_800_060_000L,
                sourceRevision = "main",
                payload = JSONObject()
                    .put("data_classification", "public")
                    .put("summary", "source audit completed")
            )
            val reply = StoredAgentTaskReply(
                taskId = envelope.taskId,
                issueNumber = 321,
                issueUrl = "https://github.com/mhw77655-hue/Friday/issues/321",
                commentId = 9005L,
                authorLogin = "mhw77655-hue",
                createdAt = "2026-11-27T20:00:00Z",
                receivedAtEpochMs = 1_795_800_000_500L,
                envelope = envelope
            )

            val first = AgentTaskReplyStore(directory)
            assertTrue(first.appendIfAbsent(reply))
            assertFalse(first.appendIfAbsent(reply))
            assertEquals(1, first.unreadCount())

            val reopened = AgentTaskReplyStore(directory)
            assertEquals(1, reopened.all().size)
            assertEquals("source audit completed", reopened.latest().single().envelope.payload.getString("summary"))
            reopened.markRead(envelope.messageId)

            val recovered = AgentTaskReplyStore(directory)
            assertEquals(0, recovered.unreadCount())
            assertTrue(recovered.all().single().isRead)
        } finally {
            directory.deleteRecursively()
        }
    }
}
