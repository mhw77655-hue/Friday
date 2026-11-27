package ai.friday.assistant.aiworkforce.table

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGPTTaskWorkerTest {
    private val sharedExpiry = System.currentTimeMillis() + 60 * 60 * 1000L

    private fun projection(
        classification: ChatGPTTaskWorker.TaskProjection.Classification =
            ChatGPTTaskWorker.TaskProjection.Classification.PRIVATE,
        context: String = "Only the approved source excerpt."
    ) = ChatGPTTaskWorker.TaskProjection(
        taskId = "friday-task-001",
        goal = "Diagnose a bounded repository defect",
        instructions = "Inspect only the supplied files and return findings.",
        context = context,
        sourceRevision = "abc123",
        acceptanceCriteria = listOf("Findings cite the supplied source revision"),
        classification = classification,
        expiresAtEpochMs = sharedExpiry
    )

    @Test
    fun projectionDigestChangesWhenContextChanges() {
        assertNotEquals(projection(context = "source A").projectionDigest(),
            projection(context = "source B").projectionDigest())
    }

    @Test
    fun projectionDigestChangesWhenClassificationChanges() {
        assertNotEquals(
            projection(classification = ChatGPTTaskWorker.TaskProjection.Classification.PRIVATE).projectionDigest(),
            projection(classification = ChatGPTTaskWorker.TaskProjection.Classification.SANITIZED).projectionDigest()
        )
    }

    @Test
    fun sensitiveClassificationIsRejected() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            projection(classification = ChatGPTTaskWorker.TaskProjection.Classification.SENSITIVE).validate()
        }
        assertTrue(error.message.orEmpty().contains("Sensitive"))
    }

    @Test
    fun oversizedContextIsRejected() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            projection(context = "x".repeat(ChatGPTTaskWorker.MAX_CONTEXT_CHARS + 1)).validate()
        }
        assertTrue(error.message.orEmpty().contains("limit"))
    }

    @Test
    fun invalidTaskIdentifierIsRejected() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            projection().copy(taskId = "not safe / private").validate()
        }
        assertTrue(error.message.orEmpty().contains("task ID"))
    }
}
