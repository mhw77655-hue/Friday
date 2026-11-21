package ai.friday.assistant.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JevPhoneActionPolicyTest {
    @Test fun `high confidence execute decision may pass policy gate`() {
        assertTrue(JevPhoneActionPolicy.mayExecute(
            JevActionDecision("execute", 0.97, mapOf("execute" to 0.97, "confirm" to 0.02, "reject" to 0.01))
        ))
    }

    @Test fun `low confidence execute decision is held`() {
        assertFalse(JevPhoneActionPolicy.mayExecute(
            JevActionDecision("execute", 0.72, mapOf("execute" to 0.72, "confirm" to 0.25, "reject" to 0.03))
        ))
    }

    @Test fun `high risk Jev score prevents otherwise confident execution`() {
        assertFalse(JevPhoneActionPolicy.mayExecute(
            JevActionDecision(
                "execute", 0.99, mapOf("execute" to 0.99),
                riskScore = 1.8, riskConfidence = 0.96,
                explicitAuthorization = 0.99, targetClarity = 0.99
            )
        ))
    }

    @Test fun `uncertain authorization or target requires confirmation`() {
        val noAuthorization = JevActionDecision(
            "execute", 0.99, mapOf("execute" to 0.99),
            riskScore = 0.0, riskConfidence = 0.99,
            explicitAuthorization = 0.70, targetClarity = 0.99
        )
        val unclearTarget = JevActionDecision(
            "execute", 0.99, mapOf("execute" to 0.99),
            riskScore = 0.0, riskConfidence = 0.99,
            explicitAuthorization = 0.99, targetClarity = 0.72
        )
        assertFalse(JevPhoneActionPolicy.mayExecute(noAuthorization))
        assertFalse(JevPhoneActionPolicy.mayExecute(unclearTarget))
        assertTrue(JevPhoneActionPolicy.requiresConfirmation(noAuthorization))
        assertTrue(JevPhoneActionPolicy.requiresConfirmation(unclearTarget))
    }

    @Test fun `missing Jev typed assessments fail closed`() {
        assertFalse(JevPhoneActionPolicy.mayExecute(
            JevActionDecision(
                "execute", 0.99, mapOf("execute" to 0.99),
                riskScore = 3.0, riskConfidence = 0.0,
                explicitAuthorization = 0.0, targetClarity = 0.0
            )
        ))
    }

    @Test fun `confirm and reject never pass execute gate`() {
        assertFalse(JevPhoneActionPolicy.mayExecute(
            JevActionDecision("confirm", 0.99, mapOf("execute" to 0.01, "confirm" to 0.99))
        ))
        assertFalse(JevPhoneActionPolicy.mayExecute(
            JevActionDecision("reject", 0.99, mapOf("reject" to 0.99))
        ))
        assertTrue(JevPhoneActionPolicy.requiresConfirmation(
            JevActionDecision("confirm", 0.99, mapOf("confirm" to 0.99))
        ))
        assertTrue(JevPhoneActionPolicy.shouldReject(
            JevActionDecision("reject", 0.99, mapOf("reject" to 0.99))
        ))
    }
}
