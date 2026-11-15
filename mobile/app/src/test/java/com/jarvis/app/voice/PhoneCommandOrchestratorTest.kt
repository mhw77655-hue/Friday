package com.jarvis.app.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCommandOrchestratorTest {
    @Test fun `recognized low risk command executes locally without Jev key`() = runBlocking {
        var executions = 0
        val tool = FridayPhoneDecisionTool { _, _, _ -> null }
        val router = PhoneCommandOrchestrator(
            decisionTool = { tool },
            remoteConfigured = { false },
            execute = { executions++; PhoneControlResult.Success }
        )

        val result = router.handle("go home")

        assertTrue(result is PhoneCommandOrchestrator.Outcome.Executed)
        assertEquals(1, executions)
    }

    @Test fun `Jev outage does not disable deterministic low risk command`() = runBlocking {
        var executions = 0
        val tool = FridayPhoneDecisionTool { _, _, _ -> null }
        val router = PhoneCommandOrchestrator(
            decisionTool = { tool },
            remoteConfigured = { true },
            execute = { executions++; PhoneControlResult.Success }
        )

        val result = router.handle("open settings")

        assertTrue(result is PhoneCommandOrchestrator.Outcome.Executed)
        assertEquals(1, executions)
    }

    @Test fun `high risk command waits for exact spoken confirmation`() = runBlocking {
        var executions = 0
        val tool = FridayPhoneDecisionTool { _, _, _ ->
            error("High-risk commands must be held by local policy before remote Jev")
        }
        val router = PhoneCommandOrchestrator(
            decisionTool = { tool },
            remoteConfigured = { true },
            execute = { executions++; PhoneControlResult.Success }
        )

        val first = router.handle("tap Delete account")
        assertTrue(first is PhoneCommandOrchestrator.Outcome.ConfirmationNeeded)
        assertEquals(0, executions)
        assertTrue(router.hasPendingConfirmation())

        val confirmed = router.handle("yes")
        assertTrue(confirmed is PhoneCommandOrchestrator.Outcome.Executed)
        assertEquals(1, executions)
        assertFalse(router.hasPendingConfirmation())
    }

    @Test fun `cancel clears pending command without executing it`() = runBlocking {
        var executions = 0
        val router = PhoneCommandOrchestrator(
            decisionTool = { FridayPhoneDecisionTool { _, _, _ -> null } },
            remoteConfigured = { false },
            execute = { executions++; PhoneControlResult.Success }
        )

        router.handle("type confidential text")
        val cancelled = router.handle("cancel")

        assertEquals(PhoneCommandOrchestrator.Outcome.Cancelled, cancelled)
        assertEquals(0, executions)
        assertFalse(router.hasPendingConfirmation())
    }

    @Test fun `unrelated utterance invalidates pending confirmation`() = runBlocking {
        var executions = 0
        val router = PhoneCommandOrchestrator(
            decisionTool = { FridayPhoneDecisionTool { _, _, _ -> null } },
            remoteConfigured = { false },
            execute = { executions++; PhoneControlResult.Success }
        )

        router.handle("tap Send")
        assertNull(router.handle("what time is it"))
        assertNull(router.handle("yes"))
        assertEquals(0, executions)
    }

    @Test fun `expired confirmation cannot authorize a later yes`() = runBlocking {
        var now = 100L
        var executions = 0
        val router = PhoneCommandOrchestrator(
            decisionTool = { FridayPhoneDecisionTool { _, _, _ -> null } },
            remoteConfigured = { false },
            execute = { executions++; PhoneControlResult.Success },
            nowMs = { now },
            confirmationWindowMs = 500L
        )

        router.handle("tap Confirm")
        now = 601L
        assertFalse(router.hasPendingConfirmation())
        assertNull(router.handle("yes"))
        assertEquals(0, executions)
    }

    @Test fun `Android execution exception becomes a contained unavailable result`() = runBlocking {
        val router = PhoneCommandOrchestrator(
            decisionTool = { FridayPhoneDecisionTool { _, _, _ -> null } },
            remoteConfigured = { false },
            execute = { throw IllegalStateException("platform bridge failure") }
        )

        val result = router.handle("go home")

        assertTrue(result is PhoneCommandOrchestrator.Outcome.Executed)
        assertTrue(
            (result as PhoneCommandOrchestrator.Outcome.Executed).result is PhoneControlResult.Unavailable
        )
    }

    @Test fun `Jev rejection blocks action execution`() = runBlocking {
        var executions = 0
        val router = PhoneCommandOrchestrator(
            decisionTool = {
                FridayPhoneDecisionTool { _, _, _ ->
                    JevActionDecision("reject", 0.99, mapOf("reject" to 0.99))
                }
            },
            remoteConfigured = { true },
            execute = { executions++; PhoneControlResult.Success }
        )

        val result = router.handle("go home")

        assertTrue(result is PhoneCommandOrchestrator.Outcome.Rejected)
        assertEquals(0, executions)
    }
}
