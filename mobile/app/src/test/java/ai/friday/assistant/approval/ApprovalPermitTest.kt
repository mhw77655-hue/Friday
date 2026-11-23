package ai.friday.assistant.approval

import ai.friday.assistant.approvals.LocalApprovalQueue
import ai.friday.assistant.approvals.RiskLevel
import ai.friday.assistant.env.PermissionPolicy
import ai.friday.assistant.env.SafetyRestrictions
import ai.friday.assistant.humancore.store.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApprovalPermitTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun queue(scope: CoroutineScope): LocalApprovalQueue =
        LocalApprovalQueue(FileStorage(temporary.newFolder("approval-store")), scope)

    private fun action(payload: String = "hello") = FridayAction(
        mission = "permit-test",
        type = "respond",
        payload = payload,
        capability = "CHAT"
    )

    @Test
    fun autoApprovalProducesAnActionBoundOneUsePermit() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val previous = ApprovalGate.configured()
        try {
            ApprovalGate.configure(GateConfig(PermissionPolicy.FULL, SafetyRestrictions.NONE))
            val queue = queue(scope)
            val original = action()
            val decision = ApprovalGate.review(original, queue)
            assertTrue(decision is GateResult.AutoApproved)
            assertTrue(TaskExecutor.apply(original, decision).applied)
            assertFalse("permit must not be reusable", TaskExecutor.apply(original, decision).applied)
        } finally {
            ApprovalGate.configure(previous)
            scope.cancel()
        }
    }

    @Test
    fun forgedAutoApprovalCannotExecute() {
        val previous = ApprovalGate.configured()
        try {
            ApprovalGate.configure(GateConfig(PermissionPolicy.FULL, SafetyRestrictions.NONE))
            val forged = GateResult.AutoApproved(RiskLevel.LOW)
            assertFalse(TaskExecutor.apply(action(), forged).applied)
        } finally {
            ApprovalGate.configure(previous)
        }
    }

    @Test
    fun permitCannotBeSubstitutedOntoDifferentAction() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val previous = ApprovalGate.configured()
        try {
            ApprovalGate.configure(GateConfig(PermissionPolicy.FULL, SafetyRestrictions.NONE))
            val original = action()
            val decision = ApprovalGate.review(original, queue(scope))
            assertFalse(TaskExecutor.apply(original.copy(payload = "different"), decision).applied)
        } finally {
            ApprovalGate.configure(previous)
            scope.cancel()
        }
    }

    @Test
    fun environmentPolicyChangeInvalidatesAnExistingPermit() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val previous = ApprovalGate.configured()
        try {
            ApprovalGate.configure(GateConfig(PermissionPolicy.FULL, SafetyRestrictions.NONE))
            val original = action()
            val decision = ApprovalGate.review(original, queue(scope))
            ApprovalGate.configure(GateConfig(PermissionPolicy.SAFE_MODE, SafetyRestrictions.MAX))
            assertFalse(TaskExecutor.apply(original, decision).applied)
        } finally {
            ApprovalGate.configure(previous)
            scope.cancel()
        }
    }

    @Test
    fun humanApprovalMustMatchTheExactPendingActionBeforeExecution() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val previous = ApprovalGate.configured()
        try {
            ApprovalGate.configure(GateConfig(PermissionPolicy.FULL, SafetyRestrictions.CLOUD_ONLY))
            val approvals = queue(scope)
            val original = action()
            val pending = ApprovalGate.review(original, approvals) as GateResult.Pending
            assertTrue(approvals.approve(pending.approvalId, "owner").isSuccess)

            val mismatch = ApprovalGate.resumeApproved(
                original.copy(payload = "substituted"),
                pending.approvalId,
                approvals
            )
            assertTrue(mismatch is GateResult.Denied)

            val approved = ApprovalGate.resumeApproved(original, pending.approvalId, approvals)
            assertTrue(approved is GateResult.Approved)
            assertTrue(TaskExecutor.apply(original, approved).applied)
            assertFalse("human approval permit must also be one-use", TaskExecutor.apply(original, approved).applied)
        } finally {
            ApprovalGate.configure(previous)
            scope.cancel()
        }
    }

    @Test
    fun expiredApprovalCannotBeApproved() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val approvals = queue(scope)
            val request = approvals.requestApproval(
                taskId = "expired",
                capability = "CHAT",
                riskLevel = RiskLevel.CRITICAL,
                description = "expired test",
                arguments = mapOf("type" to "respond", "targetPath" to "", "payload" to ""),
                requestedBy = "test",
                expiresInMs = 0
            )
            assertFalse(approvals.approve(request.id, "owner").isSuccess)
        } finally {
            scope.cancel()
        }
    }
}
