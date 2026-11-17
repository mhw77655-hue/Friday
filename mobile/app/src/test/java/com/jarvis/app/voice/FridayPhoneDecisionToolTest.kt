package com.jarvis.app.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Test

class FridayPhoneDecisionToolTest {
    @Test fun `local policy executes only recognized low risk actions`() = runBlocking {
        val tool = FridayPhoneDecisionTool { _, _, _ -> error("remote must not be called") }
        val result = tool.judge(
            utterance = "go home",
            command = PhoneCommandInterpreter.parse("go home")!!,
            remoteConfigured = false
        )
        assertEquals(FridayPhoneDecisionTool.Verdict.EXECUTE, result.verdict)
        assertEquals(FridayPhoneDecisionTool.Source.FRIDAY_LOCAL_POLICY, result.source)
    }

    @Test fun `Jev evaluates consequential screen actions but cannot bypass local confirmation`() = runBlocking {
        val remoteCalled = AtomicBoolean(false)
        val tool = FridayPhoneDecisionTool { _, _, risk ->
            remoteCalled.set(true)
            assertEquals("high", risk)
            JevActionDecision(
                choice = "execute",
                confidence = 0.99,
                probabilities = mapOf("execute" to 0.99),
                riskScore = 0.1,
                riskConfidence = 0.99,
                explicitAuthorization = 0.99,
                targetClarity = 0.99
            )
        }
        val result = tool.judge(
            utterance = "tap delete",
            command = PhoneCommandInterpreter.parse("tap delete")!!,
            remoteConfigured = true
        )
        assertTrue("Jev should be consulted for consequential actions", remoteCalled.get())
        assertEquals(FridayPhoneDecisionTool.Verdict.CONFIRM, result.verdict)
        assertEquals(FridayPhoneDecisionTool.Source.FRIDAY_LOCAL_POLICY, result.source)
    }

    @Test fun `Jev can approve routine visible navigation without a second confirmation`() = runBlocking {
        val tool = FridayPhoneDecisionTool { _, _, risk ->
            // A free-form accessibility tap is medium risk even when the
            // requested target is routine; Jev can approve it with clear evidence.
            assertEquals("medium", risk)
            JevActionDecision(
                choice = "execute",
                confidence = 0.99,
                probabilities = mapOf("execute" to 0.99),
                riskScore = 0.1,
                riskConfidence = 0.99,
                explicitAuthorization = 0.99,
                targetClarity = 0.99
            )
        }

        val result = tool.judge(
            utterance = "tap Settings",
            command = PhoneCommandInterpreter.parse("tap Settings")!!,
            remoteConfigured = true
        )

        assertEquals(FridayPhoneDecisionTool.Verdict.EXECUTE, result.verdict)
        assertEquals(FridayPhoneDecisionTool.Source.TYPESAFE_JEV, result.source)
    }

    @Test fun `typed private content is redacted before Jev evaluation`() = runBlocking {
        var sentText = ""
        val tool = FridayPhoneDecisionTool { utterance, _, _ ->
            sentText = utterance
            null
        }
        val result = tool.judge(
            utterance = "type my private password 12345",
            command = PhoneCommandInterpreter.parse("type my private password 12345")!!,
            remoteConfigured = true
        )
        assertEquals(
            "The user explicitly requested text entry into the currently focused field.",
            sentText
        )
        assertFalse(sentText.contains("12345"))
        assertEquals(FridayPhoneDecisionTool.Verdict.CONFIRM, result.verdict)
    }

    @Test fun `configured but unavailable Jev falls back to local allowlist for low risk actions`() = runBlocking {
        val tool = FridayPhoneDecisionTool { _, _, _ -> null }
        val result = tool.judge(
            utterance = "open settings",
            command = PhoneCommandInterpreter.parse("open settings")!!,
            remoteConfigured = true
        )
        assertEquals(FridayPhoneDecisionTool.Verdict.EXECUTE, result.verdict)
        assertEquals(FridayPhoneDecisionTool.Source.FRIDAY_LOCAL_POLICY, result.source)
    }

    @Test fun `Jev rejection is preserved`() = runBlocking {
        val tool = FridayPhoneDecisionTool { _, _, _ ->
            JevActionDecision("reject", 0.99, mapOf("reject" to 0.99))
        }
        val result = tool.judge(
            utterance = "open settings",
            command = PhoneCommandInterpreter.parse("open settings")!!,
            remoteConfigured = true
        )
        assertEquals(FridayPhoneDecisionTool.Verdict.REJECT, result.verdict)
        assertEquals(FridayPhoneDecisionTool.Source.TYPESAFE_JEV, result.source)
    }

    @Test fun `uncertain Jev decision requires confirmation`() = runBlocking {
        val tool = FridayPhoneDecisionTool { _, _, _ ->
            JevActionDecision("execute", 0.65, mapOf("execute" to 0.65, "confirm" to 0.35))
        }
        val result = tool.judge(
            utterance = "open settings",
            command = PhoneCommandInterpreter.parse("open settings")!!,
            remoteConfigured = true
        )
        assertEquals(FridayPhoneDecisionTool.Verdict.CONFIRM, result.verdict)
    }
}
