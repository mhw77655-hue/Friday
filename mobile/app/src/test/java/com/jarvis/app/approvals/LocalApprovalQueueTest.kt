package com.jarvis.app.approvals

import com.jarvis.app.humancore.store.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Approval queue behavior: requests enter as PENDING, decisions route to
 * APPROVED/DENIED and leave the pending feed, double-decisions are rejected,
 * and history survives a simulated restart.
 */
class LocalApprovalQueueTest {

    private fun tempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "approvals-test-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun queue(dir: File): LocalApprovalQueue = LocalApprovalQueue(
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
    fun `requestApproval creates a pending request with a future expiry`() = runBlocking {
        val q = queue(tempDir())
        val req = q.requestApproval(
            taskId = "task-1",
            capability = "SHELL_EXEC",
            riskLevel = RiskLevel.HIGH,
            description = "Delete temp files",
            arguments = mapOf("path" to "/tmp/x"),
            requestedBy = "test",
            expiresInMs = 60_000
        )
        assertEquals(ApprovalStatus.PENDING, req.status)
        assertTrue("expiry must be in the future", req.expiresAt > System.currentTimeMillis())
        assertTrue("request must appear in pending feed", q.pendingApprovals.value.any { it.id == req.id })
    }

    @Test
    fun `approve routes the request to APPROVED and leaves pending`() = runBlocking {
        val q = queue(tempDir())
        val req = q.requestApproval("t1", "SHELL_EXEC", RiskLevel.HIGH, "d", emptyMap(), "test")
        assertTrue(q.approve(req.id, "user").isSuccess)
        val updated = q.getApproval(req.id)!!
        assertEquals(ApprovalStatus.APPROVED, updated.status)
        assertEquals("user", updated.decidedBy)
        assertTrue(q.pendingApprovals.value.none { it.id == req.id })
    }

    @Test
    fun `deny routes the request to DENIED with a reason`() = runBlocking {
        val q = queue(tempDir())
        val req = q.requestApproval("t1", "SHELL_EXEC", RiskLevel.HIGH, "d", emptyMap(), "test")
        assertTrue(q.deny(req.id, "user", "Nope").isSuccess)
        val updated = q.getApproval(req.id)!!
        assertEquals(ApprovalStatus.DENIED, updated.status)
        assertEquals("user", updated.decidedBy)
        assertEquals(ApprovalDecision.DENY, updated.decision)
    }

    @Test
    fun `a second decision on the same request is rejected`() = runBlocking {
        val q = queue(tempDir())
        val req = q.requestApproval("t1", "SHELL_EXEC", RiskLevel.HIGH, "d", emptyMap(), "test")
        assertTrue(q.approve(req.id, "user").isSuccess)
        assertTrue("re-deciding an already-decided request must fail", q.approve(req.id, "user").isFailure)
    }

    @Test
    fun `deciding an unknown request fails cleanly`() = runBlocking {
        val q = queue(tempDir())
        assertTrue(q.approve("approval-does-not-exist", "user").isFailure)
    }

    @Test
    fun `approvals survive a simulated restart`() = runBlocking {
        val dir = tempDir()
        val q1 = queue(dir)
        val req = q1.requestApproval("t1", "SHELL_EXEC", RiskLevel.HIGH, "d", emptyMap(), "test")

        val q2 = queue(dir)
        awaitCondition { q2.approvals.value.any { it.id == req.id } }
        val restored = q2.approvals.value.first { it.id == req.id }
        assertEquals("d", restored.description)
        assertEquals(ApprovalStatus.PENDING, restored.status)
    }
}
