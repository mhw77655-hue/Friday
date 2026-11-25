package ai.friday.assistant.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceControlRouterTest {

    /** Deterministic test backend that verifies when [succeeds] is true. */
    private class FakeBackend(
        override val id: String,
        val succeeds: Boolean,
        val available: Boolean = true,
        val evidenceBackendId: String = id
    ) : ControlBackend {
        var callCount: Int = 0
        override fun available(): Boolean = available

        override fun execute(action: ControlAction): VerificationEvidence {
            callCount++
            return if (succeeds) {
                VerificationEvidence(VerificationStage.VERIFIED, evidenceBackendId, "verified by $id")
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
    fun `verified evidence must identify the backend that produced it`() {
        val spoofed = FakeBackend(
            id = "intent",
            succeeds = true,
            evidenceBackendId = "adb"
        )
        val router = routerWith(spoofed)

        val winner = router.execute(action)

        assertNull("mismatched evidence must not produce a winner", winner)
        val attempt = router.attempts.single()
        assertFalse(attempt.succeeded)
        assertEquals(VerificationStage.VERIFIED, attempt.evidence.stage)
        assertEquals("adb", attempt.evidence.backendId)
        assertEquals("verification_identity", attempt.failureStage)
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
        assertTrue("backend duration must never be negative", attempt.durationMs >= 0)
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
    @Test
    fun `unknown backends are ordered after known fallback rungs`() {
        val unknown = FakeBackend("custom-backend", succeeds = true)
        val media = FakeBackend("media-session", succeeds = true)
        val router = DeviceControlRouter(listOf(unknown, media))

        router.execute(action)

        assertEquals(listOf("media-session"), router.lastTriedOrder())
        assertEquals(0, unknown.callCount)
    }

    @Test
    fun `duplicate backend identities are rejected at composition time`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            DeviceControlRouter(
                listOf(
                    FakeBackend("media-session", succeeds = true),
                    FakeBackend("media-session", succeeds = false)
                )
            )
        }
    }

    @Test
    fun `concurrent executions return their own evidence snapshots`() {
        val entered = java.util.concurrent.CountDownLatch(2)
        val release = java.util.concurrent.CountDownLatch(1)
        val backend = object : ControlBackend {
            override val id = "media-session"
            override fun available() = true
            override fun execute(action: ControlAction): VerificationEvidence {
                entered.countDown()
                check(release.await(2, java.util.concurrent.TimeUnit.SECONDS))
                return VerificationEvidence(
                    VerificationStage.VERIFIED,
                    id,
                    "verified:${action.id}"
                )
            }
        }
        val router = DeviceControlRouter(listOf(backend))
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<DeviceControlExecution> {
                router.executeWithEvidence(action.copy(id = "first"))
            }
            val second = pool.submit<DeviceControlExecution> {
                router.executeWithEvidence(action.copy(id = "second"))
            }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            release.countDown()
            assertEquals("verified:first", first.get(2, java.util.concurrent.TimeUnit.SECONDS).attempts.single().evidence.detail)
            assertEquals("verified:second", second.get(2, java.util.concurrent.TimeUnit.SECONDS).attempts.single().evidence.detail)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

}
