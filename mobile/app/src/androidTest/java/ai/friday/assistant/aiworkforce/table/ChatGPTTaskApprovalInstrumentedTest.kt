package ai.friday.assistant.aiworkforce.table

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatGPTTaskApprovalInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun approvalIsBoundToProjectionAndCanOnlyBeConsumedOnce() {
        val prefs = context.getSharedPreferences("friday_chatgpt_task_approvals", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = ChatGPTTaskApprovalStore(context)
        val expiresAt = System.currentTimeMillis() + 60_000L
        store.approve("task-approval-001", "projection-hash-a", expiresAt)

        assertFalse(store.consume("task-approval-001", "projection-hash-b", System.currentTimeMillis()))
        // A mismatched projection consumes the stale approval rather than allowing
        // an attacker or stale caller to replay it with a different payload.
        assertFalse(store.consume("task-approval-001", "projection-hash-a", System.currentTimeMillis()))

        store.approve("task-approval-002", "projection-hash-c", expiresAt)
        assertTrue(store.consume("task-approval-002", "projection-hash-c", System.currentTimeMillis()))
        assertFalse(store.consume("task-approval-002", "projection-hash-c", System.currentTimeMillis()))
    }

    @Test
    fun expiredApprovalIsRejectedAndRemoved() {
        val prefs = context.getSharedPreferences("friday_chatgpt_task_approvals", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = ChatGPTTaskApprovalStore(context)
        store.approve("task-approval-expired", "projection-hash", System.currentTimeMillis() - 1L)
        assertFalse(store.consume("task-approval-expired", "projection-hash", System.currentTimeMillis()))
    }
}
