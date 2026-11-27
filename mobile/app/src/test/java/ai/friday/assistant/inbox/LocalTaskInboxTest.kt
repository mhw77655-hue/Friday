package ai.friday.assistant.inbox

import ai.friday.assistant.humancore.store.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task inbox lifecycle: submit → claim → complete, plus the approval gate and
 * durability across a simulated restart.
 */
class LocalTaskInboxTest {

    private fun tempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "inbox-test-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun inbox(dir: File): LocalTaskInbox = LocalTaskInbox(
        FileStorage(dir),
        CoroutineScope(Dispatchers.Default)
    )

    /** Stores load their persisted state on an async init coroutine; wait for it. */
    private suspend fun awaitCondition(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) break
            kotlinx.coroutines.delay(10)
        }
    }

    @Test
    fun `submit creates a pending task visible to the flow`() = runBlocking {
        val i = inbox(tempDir())
        val task = i.submit("test", "Do the thing", "SHELL_EXEC", mapOf("cmd" to "ls"))
        assertEquals(InboxTaskStatus.PENDING, task.status)
        assertTrue("task must appear in the state flow", i.tasks.value.any { it.id == task.id })
    }

    @Test
    fun `approval-gated tasks start as WAITING_APPROVAL`() = runBlocking {
        val i = inbox(tempDir())
        val task = i.submit("test", "High-risk op", "SHELL_EXEC", mapOf("cmd" to "rm -rf /tmp/x"), requiresApproval = true)
        assertEquals(InboxTaskStatus.WAITING_APPROVAL, task.status)
        assertTrue(task.requiresApproval)
    }

    @Test
    fun `approving a gated task queues it as pending`() = runBlocking {
        val i = inbox(tempDir())
        val task = i.submit("test", "High-risk op", "SHELL_EXEC", emptyMap(), requiresApproval = true)
        val result = i.approve(task.id)
        assertTrue("approve must succeed", result.isSuccess)
        val updated = i.getTask(task.id)!!
        assertEquals(InboxTaskStatus.PENDING, updated.status)
        assertTrue(!updated.requiresApproval)
    }

    @Test
    fun `denying a gated task fails it with a reason`() = runBlocking {
        val i = inbox(tempDir())
        val task = i.submit("test", "High-risk op", "SHELL_EXEC", emptyMap(), requiresApproval = true)
        val result = i.deny(task.id, "Not authorized")
        assertTrue(result.isSuccess)
        val updated = i.getTask(task.id)!!
        assertEquals(InboxTaskStatus.FAILED, updated.status)
        assertTrue("error must carry the denial reason", updated.error?.contains("Not authorized") == true)
    }

    @Test
    fun `claimNext moves a pending task to RUNNING`() = runBlocking {
        val i = inbox(tempDir())
        val task = i.submit("test", "Work", "TASK_SUBMIT", emptyMap())
        val claimed = i.claimNext()
        assertNotNull(claimed)
        assertEquals(task.id, claimed!!.id)
        assertEquals(InboxTaskStatus.RUNNING, claimed.status)
        // Only one task claimed at a time.
        assertTrue(i.claimNext() == null)
    }

    @Test
    fun `completing a task records the result and COMPLETED status`() = runBlocking {
        val i = inbox(tempDir())
        val task = i.submit("test", "Work", "TASK_SUBMIT", emptyMap())
        i.claimNext()
        val result = i.complete(task.id, mapOf("ok" to true))
        assertTrue(result.isSuccess)
        val updated = i.getTask(task.id)!!
        assertEquals(InboxTaskStatus.COMPLETED, updated.status)
        assertTrue(updated.result?.get("ok") == true)
        assertNotNull(updated.completedAt)
    }

    @Test
    fun `cancelling a running task sets CANCELLED`() = runBlocking {
        val i = inbox(tempDir())
        val task = i.submit("test", "Work", "TASK_SUBMIT", emptyMap())
        i.claimNext()
        assertTrue(i.cancel(task.id).isSuccess)
        assertEquals(InboxTaskStatus.CANCELLED, i.getTask(task.id)!!.status)
    }

    @Test
    fun `submitOnce deduplicates external proposals and preserves approval gate`() = runBlocking {
        val dir = tempDir()
        val firstInbox = inbox(dir)
        val first = firstInbox.submitOnce(
            id = "agent-reply:stable-message-id",
            source = "chatgpt-cloud",
            description = "Review external proposal",
            capability = "review_external_agent_proposal",
            arguments = mapOf("message_id" to "stable-message-id"),
            requiresApproval = true
        )
        val duplicate = firstInbox.submitOnce(
            id = "agent-reply:stable-message-id",
            source = "chatgpt-cloud",
            description = "Review external proposal",
            capability = "review_external_agent_proposal",
            arguments = mapOf("message_id" to "stable-message-id"),
            requiresApproval = true
        )

        assertEquals(first.id, duplicate.id)
        val changedContent = runCatching {
            firstInbox.submitOnce(
                id = "agent-reply:stable-message-id",
                source = "chatgpt-cloud",
                description = "Review external proposal",
                capability = "review_external_agent_proposal",
                arguments = mapOf("message_id" to "different-message-id"),
                requiresApproval = true
            )
        }
        assertTrue("stable IDs must not be rebound to changed content", changedContent.isFailure)
        assertEquals(InboxTaskStatus.WAITING_APPROVAL, first.status)
        assertEquals(1, firstInbox.tasks.value.count { it.id == first.id })

        val reopened = inbox(dir)
        awaitCondition { reopened.tasks.value.any { it.id == first.id } }
        val restored = reopened.getTask(first.id)!!
        assertEquals(InboxTaskStatus.WAITING_APPROVAL, restored.status)
        assertEquals("stable-message-id", restored.arguments["message_id"])
    }

    @Test
    fun `tasks survive a simulated restart`() = runBlocking {
        val dir = tempDir()
        val i1 = inbox(dir)
        val task = i1.submit("test", "Persistent work", "TASK_SUBMIT", emptyMap())

        // Simulated process death: new store, same storage.
        val i2 = inbox(dir)
        awaitCondition { i2.tasks.value.any { it.id == task.id } }
        val restored = i2.tasks.value.first { it.id == task.id }
        assertEquals("Persistent work", restored.description)
        assertEquals(InboxTaskStatus.PENDING, restored.status)
    }
}
