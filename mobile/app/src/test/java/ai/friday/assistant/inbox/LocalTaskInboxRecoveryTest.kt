package ai.friday.assistant.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTaskInboxRecoveryTest {
    @Test
    fun `previous process running task is requeued while new in memory task is preserved`() {
        val interrupted = task("interrupted", InboxTaskStatus.RUNNING, attempts = 2, startedAt = 10L)
        val newlySubmitted = task("new", InboxTaskStatus.PENDING, createdAt = 30L)

        val merged = mergeAndRecoverInboxTasks(
            diskTasks = listOf(interrupted),
            currentTasks = listOf(newlySubmitted),
            now = 100L
        )

        assertEquals(listOf("interrupted", "new"), merged.map { it.id })
        assertEquals(InboxTaskStatus.PENDING, merged[0].status)
        assertEquals(3, merged[0].attempts)
        assertNull(merged[0].startedAt)
        assertEquals("PROCESS_RESTART_REQUEUED", merged[0].history.last().action)
        assertEquals(newlySubmitted, merged[1])
    }

    @Test
    fun `repeated process interruptions exhaust a bounded retry budget`() {
        val exhausted = task("exhausted", InboxTaskStatus.RUNNING, attempts = MAX_INTERRUPTED_REQUEUES - 1)

        val merged = mergeAndRecoverInboxTasks(listOf(exhausted), emptyList(), now = 100L)

        assertEquals(InboxTaskStatus.FAILED, merged.single().status)
        assertEquals(MAX_INTERRUPTED_REQUEUES, merged.single().attempts)
        assertEquals("Interrupted execution retry budget exhausted", merged.single().error)
        assertEquals("PROCESS_RESTART_RETRY_EXHAUSTED", merged.single().history.last().action)
    }

    @Test
    fun `current in process state wins over stale disk state`() {
        val staleDisk = task("same", InboxTaskStatus.PENDING)
        val activeCurrent = task("same", InboxTaskStatus.RUNNING, startedAt = 90L)

        val merged = mergeAndRecoverInboxTasks(listOf(staleDisk), listOf(activeCurrent), now = 100L)

        assertEquals(1, merged.size)
        assertEquals(InboxTaskStatus.RUNNING, merged.single().status)
        assertEquals(90L, merged.single().startedAt)
        assertTrue(merged.single().history.isEmpty())
    }

    @Test
    fun `completed disk tasks remain completed`() {
        val completed = task("done", InboxTaskStatus.COMPLETED, completedAt = 80L)

        val merged = mergeAndRecoverInboxTasks(listOf(completed), emptyList(), now = 100L)

        assertEquals(InboxTaskStatus.COMPLETED, merged.single().status)
        assertEquals(80L, merged.single().completedAt)
    }

    private fun task(
        id: String,
        status: InboxTaskStatus,
        attempts: Int = 0,
        createdAt: Long = 1L,
        startedAt: Long? = null,
        completedAt: Long? = null
    ) = InboxTask(
        id = id,
        source = "test",
        description = "test task",
        capability = "test",
        arguments = emptyMap(),
        status = status,
        createdAt = createdAt,
        startedAt = startedAt,
        completedAt = completedAt,
        attempts = attempts
    )
}
