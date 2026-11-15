package com.jarvis.app.voice

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
