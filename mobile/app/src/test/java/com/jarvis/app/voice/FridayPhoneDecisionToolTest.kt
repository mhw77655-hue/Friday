package com.jarvis.app.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    @Test fun `local policy always confirms free form screen actions`() = runBlocking {
        val tool = FridayPhoneDecisionTool { _, _, _ -> error("remote must not override local invariant") }
        val result = tool.judge(
            utterance = "tap delete",
            command = PhoneCommandInterpreter.parse("tap delete")!!,
            remoteConfigured = true
        )
        assertEquals(FridayPhoneDecisionTool.Verdict.CONFIRM, result.verdict)
        assertEquals(FridayPhoneDecisionTool.Source.FRIDAY_LOCAL_POLICY, result.source)
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
