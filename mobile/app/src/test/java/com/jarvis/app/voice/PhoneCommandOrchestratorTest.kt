package com.jarvis.app.voice

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
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

        assertTrue(result is PhoneCommandOrchestrator.Outcome.ActionResult)
        assertEquals(1, executions)
    }

    @Test fun `arbitrary screen tap requires confirmation when Jev is not configured`() = runBlocking {
        var executions = 0
        val router = PhoneCommandOrchestrator(
            decisionTool = { FridayPhoneDecisionTool { _, _, _ -> null } },
            remoteConfigured = { false },
            execute = { executions++; PhoneControlResult.Success }
        )

        val result = router.handle("tap Continue")

        assertTrue(result is PhoneCommandOrchestrator.Outcome.ConfirmationNeeded)
        assertEquals(0, executions)
    }

    @Test fun `configured Jev is bypassed for allowlisted low risk action`() = runBlocking {
        var executions = 0
        var jevCalls = 0
        val tool = FridayPhoneDecisionTool { _, _, _ ->
            jevCalls++
            JevActionDecision("reject", 0.99, mapOf("reject" to 0.99))
        }
        val router = PhoneCommandOrchestrator(
            decisionTool = { tool },
            remoteConfigured = { true },
            execute = { executions++; PhoneControlResult.Success }
        )

        val result = router.handle("go home")

        assertTrue(result is PhoneCommandOrchestrator.Outcome.ActionResult)
        assertEquals(1, executions)
        assertEquals(0, jevCalls)
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

        assertTrue(result is PhoneCommandOrchestrator.Outcome.ActionResult)
        assertEquals(1, executions)
    }

    @Test fun `Jev evaluates high risk command but spoken confirmation remains mandatory`() = runBlocking {
        var executions = 0
        var jevCalls = 0
        val tool = FridayPhoneDecisionTool { _, _, _ ->
            jevCalls++
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
        val router = PhoneCommandOrchestrator(
            decisionTool = { tool },
            remoteConfigured = { true },
            execute = { executions++; PhoneControlResult.Success }
        )

        val first = router.handle("tap Delete account")
        assertTrue(first is PhoneCommandOrchestrator.Outcome.ConfirmationNeeded)
        assertEquals(1, jevCalls)
        assertEquals(0, executions)
        assertTrue(router.hasPendingConfirmation())

        val confirmed = router.handle("yes")
        assertTrue(confirmed is PhoneCommandOrchestrator.Outcome.ActionResult)
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

    @Test fun `overlapping voice commands are serialized`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val router = PhoneCommandOrchestrator(
            decisionTool = { FridayPhoneDecisionTool { _, _, _ -> null } },
            remoteConfigured = { false },
            execute = {
                started.complete(Unit)
                release.await()
                PhoneControlResult.Success
            }
        )

        val first = async { router.handle("go home") }
        started.await()
        val second = router.handle("go back")
        assertTrue(second is PhoneCommandOrchestrator.Outcome.Unavailable)
        release.complete(Unit)
        assertTrue(first.await() is PhoneCommandOrchestrator.Outcome.ActionResult)
    }

    @Test fun `Android execution exception becomes a contained unavailable result`() = runBlocking {
        val router = PhoneCommandOrchestrator(
            decisionTool = { FridayPhoneDecisionTool { _, _, _ -> null } },
            remoteConfigured = { false },
            execute = { throw IllegalStateException("platform bridge failure") }
        )

        val result = router.handle("go home")

        assertTrue(result is PhoneCommandOrchestrator.Outcome.ActionResult)
        assertTrue(
            (result as PhoneCommandOrchestrator.Outcome.ActionResult).result is PhoneControlResult.Unavailable
        )
    }

    @Test fun `Friday model planned action uses the same Jev and confirmation gate`() = runBlocking {
        var executions = 0
        var jevCalls = 0
        val router = PhoneCommandOrchestrator(
            decisionTool = {
                FridayPhoneDecisionTool { _, _, _ ->
                    jevCalls++
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
            },
            remoteConfigured = { true },
            execute = { executions++; PhoneControlResult.Success }
        )

        val planned = router.handlePlanned(
            "Please tap Send",
            PhoneControlCommand.TapText("Send")
        )

        assertTrue(planned is PhoneCommandOrchestrator.Outcome.ConfirmationNeeded)
        assertEquals(1, jevCalls)
        assertEquals(0, executions)
        val confirmed = router.handle("yes")
        assertTrue(confirmed is PhoneCommandOrchestrator.Outcome.ActionResult)
        assertEquals(1, executions)
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

        val result = router.handle("tap Continue")

        assertTrue(result is PhoneCommandOrchestrator.Outcome.Rejected)
        assertEquals(0, executions)
    }
}
