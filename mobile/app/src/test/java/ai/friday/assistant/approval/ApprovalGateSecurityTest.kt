package ai.friday.assistant.approval

import ai.friday.assistant.approvals.RiskLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalGateSecurityTest {
    private val originalConfig = ApprovalGate.configured()

    @After
    fun restorePolicy() {
        ApprovalGate.configure(originalConfig)
    }

    @Test
    fun shellExecutionCannotBeDowngradedByRespondVerb() {
        val action = FridayAction(type = "respond", capability = "SHELL_EXEC")
        assertEquals(RiskLevel.CRITICAL, ApprovalGate.classifyRisk(action))
    }

    @Test
    fun shellExecutionCannotBeDowngradedByNoopVerb() {
        val action = FridayAction(type = "noop", capability = "SHELL_EXEC")
        assertEquals(RiskLevel.CRITICAL, ApprovalGate.classifyRisk(action))
    }

    @Test
    fun shellExecutionCannotBeDowngradedByWriteVerbOrUnprotectedPath() {
        val action = FridayAction(
            type = "write",
            targetPath = "/tmp/ordinary-output.txt",
            capability = "SHELL_EXEC"
        )
        assertEquals(RiskLevel.CRITICAL, ApprovalGate.classifyRisk(action))
    }

    @Test
    fun shellExecutionCapabilityMatchingIsCaseInsensitive() {
        val action = FridayAction(type = "respond", capability = "shell_exec")
        assertEquals(RiskLevel.CRITICAL, ApprovalGate.classifyRisk(action))
    }
}
