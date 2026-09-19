package com.jarvis.app.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceControlRouterTest {

    /** Deterministic test backend that verifies when [succeeds] is true. */
    private class FakeBackend(
        override val id: String,
        val succeeds: Boolean,
        val available: Boolean = true
    ) : ControlBackend {
        var callCount: Int = 0
        override fun available(): Boolean = available

        override fun execute(action: ControlAction): VerificationEvidence {
            callCount++
            return if (succeeds) {
                VerificationEvidence(VerificationStage.VERIFIED, id, "verified by $id")
            } else {
                VerificationEvidence(VerificationStage.LAUNCHED, id, "launched but not verified")
            }
        }
    }

    private val allOrdered = DeviceControlRouter.ORDER
        .associateWith { id ->
            FakeBackend(id, succeeds = false)
        }

    private fun routerWith(vararg backends: FakeBackend): DeviceControlRouter {
        return DeviceControlRouter(backends.toList())
    }

    private val action = ControlAction(
        id = "play",
        actionType = ActionType.PLAY,
        riskTier = RiskTier.LOW
    )

    @Test
    fun `fallback chain is tried in the required order`() {
        val backends = allOrdered.values.toList()
        val router = DeviceControlRouter(backends)

        router.execute(action)

        assertEquals(
            "Must try official-api -> intent -> media-session -> accessibility -> shizuku -> adb",
            DeviceControlRouter.ORDER,
            router.lastTriedOrder()
        )
    }

    @Test
    fun `first successful backend is used`() {
        val intent = FakeBackend("intent", succeeds = true)
        val mediaSession = FakeBackend("media-session", succeeds = true)
        val router = routerWith(
            FakeBackend("official-api", succeeds = false),
            intent,
            mediaSession
        )

        val winner = router.execute(action)

        assertEquals("intent", winner?.id)
        assertEquals(1, intent.callCount)
        assertEquals(0, mediaSession.callCount)
        assertTrue(router.lastSuccessfulBackends() == listOf("intent"))
    }

    @Test
    fun `an attempted backend is logged even when it fails`() {
        val failing = FakeBackend("official-api", succeeds = false)
        val router = routerWith(failing, FakeBackend("intent", succeeds = false))

        val winner = router.execute(action)

        assertNull("winning backend is none when nothing verifies", winner)
        assertTrue(router.attempts.all { !it.succeeded })
        assertTrue(router.attempts.any { it.backendId == "official-api" })
        assertTrue(router.attempts.any { it.backendId == "intent" })
    }

    @Test
    fun `a capability call returns real verification evidence not fire-and-assume`() {
        val verified = FakeBackend("shizuku", succeeds = true)
        val router = routerWith(verified)

        router.execute(action)

        val attempt = router.attempts.first()
        assertEquals("shizuku", attempt.backendId)
        assertEquals(VerificationStage.VERIFIED, attempt.evidence.stage)
        assertTrue("evidence must carry a real success detail", attempt.evidence.detail.isNotBlank())
        assertTrue(attempt.succeeded)
    }

    @Test
    fun `unavailable backends are skipped not tried`() {
        val official = FakeBackend("official-api", succeeds = false, available = false)
        val intent = FakeBackend("intent", succeeds = true)
        val router = routerWith(official, intent)

        val winner = router.execute(action)

        assertEquals("intent", winner?.id)
        assertEquals(0, official.callCount)
        assertTrue(router.attempts.any { it.backendId == "official-api" && it.error == "unavailable" })
    }
}
