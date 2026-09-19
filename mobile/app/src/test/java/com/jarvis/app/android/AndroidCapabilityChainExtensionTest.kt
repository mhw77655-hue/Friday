package com.jarvis.app.android

import com.jarvis.app.humancore.HumanCoreGraph
import com.jarvis.app.humancore.protocol.OwnerBinding
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.identity.owner.BiometricAuthResult
import com.jarvis.app.identity.owner.BiometricType
import com.jarvis.app.identity.owner.OwnerBiometricBinder
import com.jarvis.app.identity.owner.OwnerBindingOutcome
import com.jarvis.app.identity.owner.OwnerBindingPort
import com.jarvis.app.selfreconfig.JarvisOrganGraph
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ANDROID-CAPABILITY-CHAIN-EXTENSION proof harness.
 *
 * Replicates the composition JarvisEngine.init builds for the android
 * capability adapter stack (phase-A/B technique: init is Android-bound, so the
 * JVM drives the SAME production classes through the SAME call path the hosted
 * requestDeviceControl() method runs on the device).
 *
 *  - AC1: a second real backend (MediaSessionControlBackend) exists, wired into
 *    DeviceControlRouter behind the same RiskGate as ScreenBridgeControlBackend.
 *  - AC2: fallback ordering between backends is REAL — the router's chain is
 *    sorted by rung order, media-session BEFORE shizuku, proven with the real
 *    router and the real backend classes.
 *  - AC3: the new backend is reachable through the hosted requestDeviceControl
 *    composition (owner binder -> RiskGate -> router), returning the same
 *    DeviceControlOutcome.Executed shape the hosted method returns.
 *  - AC4: JarvisOrganGraph carries the new backend node and the router edge.
 */
class AndroidCapabilityChainExtensionTest {

    private class FakeMediaSessionPort(
        sessions: List<MediaSessionControlPort.Session>
    ) : MediaSessionControlPort {
        private val entries = sessions.toMutableList()

        override fun activeSessions(): List<MediaSessionControlPort.Session> = entries.toList()

        override fun dispatch(sessionKey: String, command: MediaTransportCommand): Boolean {
            val index = entries.indexOfFirst { it.key == sessionKey }
            if (index < 0) return false
            val current = entries[index]
            val next = when (command) {
                MediaTransportCommand.PLAY -> current.copy(state = 3, positionMs = current.positionMs + 500)
                MediaTransportCommand.PAUSE -> current.copy(state = 2)
                MediaTransportCommand.NEXT_TRACK,
                MediaTransportCommand.PREV_TRACK -> current.copy(positionMs = 0L)
            }
            entries[index] = next
            return true
        }
    }

    private fun freshDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "chain-$name-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    /**
     * A REAL owner-binding store backed by persisted HumanCoreGraph data on
     * isolated storage (the OwnerBiometricBindingTest Device pattern) —
     * deliberately NOT the process-global HumanCore singleton, so this harness
     * never mutates the shared singleton state other test classes rely on.
     */
    private class IsolatedOwnerStore(dir: File) : OwnerBindingPort {
        private val graph = HumanCoreGraph(storage = FileStorage(dir)).also { it.registry.loadAll() }

        override fun bindOwner(binding: OwnerBinding): Boolean = graph.bindOwner(binding)
        override fun ownerBinding(): OwnerBinding? = graph.ownerBinding()
    }

    private fun authenticatedBinder(): OwnerBiometricBinder {
        val binder = OwnerBiometricBinder(IsolatedOwnerStore(freshDir("owner")))
        val outcome = binder.bind(
            BiometricAuthResult.Success(
                authenticators = BiometricType.BIOMETRIC_STRONG,
                authenticatedAtEpochMs = 1_700_000_000_002L
            )
        )
        assertTrue(
            "real persisted store accepts the biometric result",
            outcome is OwnerBindingOutcome.Bound || outcome is OwnerBindingOutcome.ReAuthenticated
        )
        assertTrue("biometric gate signal reads owner-bound", binder.isOwnerBound())
        return binder
    }

    private fun productionRouter(port: MediaSessionControlPort): DeviceControlRouter =
        DeviceControlRouter(
            backends = listOf(
                MediaSessionControlBackend(port),
                ScreenBridgeControlBackend()
            )
        )

    private fun mediaAction() = ControlAction(
        id = "play-1",
        actionType = ActionType.PLAY,
        riskTier = RiskTier.MEDIUM
    )

    // ── AC1 + AC2: second real backend, real fallback ordering ──────────────

    @Test
    fun `media session is a real backend wired into the router chain`() {
        val router = productionRouter(
            FakeMediaSessionPort(
                listOf(MediaSessionControlPort.Session("spotify", "com.spotify.music", 2, 10_000L))
            )
        )
        val winner = router.execute(mediaAction())

        assertEquals("media-session", winner?.id)
        assertTrue(
            "attempt carries verified evidence from the real backend",
            router.attempts.last().evidence.isComplete
        )
    }

    @Test
    fun `chain probes media-session before shizuku in real rung order`() {
        // ScreenBridgeControlBackend reports unavailable on the JVM (Shizuku not
        // bound). With the media-session rung also unavailable, the router must
        // attempt media-session FIRST, log it unavailable, then probe the
        // shizuku rung — proving the rung order, not just a comment.
        val router = productionRouter(FakeMediaSessionPort(emptyList()))

        val winner = router.execute(mediaAction())

        assertNull("nothing verifies when both rungs are unavailable", winner)
        assertEquals(
            "real chain order: media-session before shizuku",
            listOf("media-session", "shizuku"),
            router.lastTriedOrder()
        )
        assertTrue(router.attempts.all { !it.succeeded })
    }

    @Test
    fun `winning media-session rung short-circuits before the shizuku rung is probed`() {
        val router = productionRouter(
            FakeMediaSessionPort(
                listOf(MediaSessionControlPort.Session("spotify", "com.spotify.music", 2, 10_000L))
            )
        )

        val winner = router.execute(mediaAction())

        assertEquals("media-session", winner?.id)
        assertEquals(
            "shizuku rung must not be attempted after media-session verified",
            listOf("media-session"),
            router.lastTriedOrder()
        )
    }

    // ── AC3: reachable through the hosted requestDeviceControl composition ──

    @Test
    fun `gated media action resolves to the new backend through the hosted call path`() {
        // Replicates requestDeviceControl(): biometric signal from the REAL owner
        // binder -> RiskGate admission -> DeviceControlRouter over the real chain.
        val binder = authenticatedBinder()

        val gate = RiskGate()
        val action = mediaAction()
        val admitted = gate.admit(
            action,
            RiskGate.Authorization(
                biometricVerified = binder.isOwnerBound(),
                confirmationProvided = true
            )
        )
        assertTrue("MEDIUM media action passes the gate with confirmation", admitted is RiskGate.GateOutcome.Allowed)

        val router = productionRouter(
            FakeMediaSessionPort(
                listOf(MediaSessionControlPort.Session("spotify", "com.spotify.music", 3, 20_000L))
            )
        )
        val backend = router.execute(action)

        assertNotNull("router resolves the media action", backend)
        assertEquals("media-session", backend?.id)

        // Exact outcome shape JarvisEngine.requestDeviceControl returns.
        val outcome = com.jarvis.app.JarvisEngine.DeviceControlOutcome.Executed(
            backend?.id ?: "none",
            router.attempts.last().evidence
        )
        assertEquals("media-session", outcome.backendId)
        assertTrue(outcome.evidence.isComplete)
    }

    // ── AC4: the organ graph carries the new backend node and edge ──────────

    @Test
    fun `organ graph registers the media-session backend node and router edge`() {
        val graph = JarvisOrganGraph.build()

        val node = graph.node("android.mediaSessionBackend")
        assertNotNull("media-session backend node present", node)
        assertEquals(
            "com.jarvis.app.android.MediaSessionControlBackend",
            node?.qualifiedClassName
        )

        assertTrue(
            "device-control router resolves through the media-session backend",
            graph.edgesFrom("android.deviceControlRouter").any {
                it.toId == "android.mediaSessionBackend"
            }
        )
        assertTrue(
            "device-control router still resolves through the shizuku rung",
            graph.edgesFrom("android.deviceControlRouter").any {
                it.toId == "android.screenBridgeBackend"
            }
        )
    }
}