package com.jarvis.app.phaseb

import com.jarvis.app.android.ActionType
import com.jarvis.app.android.ControlAction
import com.jarvis.app.android.ControlBackend
import com.jarvis.app.android.DeviceControlRouter
import com.jarvis.app.android.RiskGate
import com.jarvis.app.android.RiskTier
import com.jarvis.app.android.VerificationEvidence
import com.jarvis.app.android.VerificationStage
import com.jarvis.app.cloud.CloudModelRouter
import com.jarvis.app.cloud.CloudProvider
import com.jarvis.app.cloud.CloudResult
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.identity.owner.BiometricAuthResult
import com.jarvis.app.identity.owner.BiometricType
import com.jarvis.app.identity.owner.HumanCoreOwnerBindingPort
import com.jarvis.app.identity.owner.OwnerBiometricBinder
import com.jarvis.app.identity.owner.OwnerBindingOutcome
import com.jarvis.app.identity.owner.PlatformBiometricPromptResultMapper
import com.jarvis.app.research.universal.CandidateSource
import com.jarvis.app.research.universal.InMemoryMechanismsStore
import com.jarvis.app.research.universal.UniversalResearchEngine
import com.jarvis.app.selfreconfig.JarvisOrganGraph
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE-B-DISCONNECTED-SUBSYSTEM-WIRING proof harness.
 *
 * Mirrors the phase-A technique: JarvisEngine.init is Android-bound (object +
 * HandlerThread), so its composition is replicated on the JVM by constructing
 * the SAME production classes with JVM-safe stand-ins where the app would use
 * an Android-bound resource (HumanCore via real FileStorage, a JVM cloud model
 * provider, a JVM device-control backend), then driving the same call paths the
 * now-hosted host methods (requestResearch / requestDeviceControl) invoke on
 * the device.
 *
 *  - AC1: UniversalResearchEngine constructed in JarvisEngine.init and reachable
 *    from a real production call path (the engine itself + the hosted
 *    requestResearch() path over the real CloudModelRouter + real store).
 *  - AC2: the real owner-binding entry point (OwnerBiometricBinder.bind over the
 *    real HumanCoreOwnerBindingPort) invoked from a live constructed path.
 *  - AC3: android capability adapter stack has a real production caller (the
 *    hosted requestDeviceControl() path through RiskGate + DeviceControlRouter).
 *  - AC4: JarvisOrganGraph reflects the three now-real subsystems and their
 *    dependency edges.
 */
class PhaseBDisconnectedSubsystemWiringTest {

    // ── Shared fixtures ──────────────────────────────────────────────────────

    private val cloudMechanismsText = """
        MECHANISM: name=pheromone routing|domain=INSECTS|description=trail reinforcement decays over time|category=routing|complexity=SIMPLE|properties=decay=slow,reinforce=fast
        MECHANISM: name=health-aware replica|domain=NETWORKING|description=route to nearest healthy server and cache|category=routing|complexity=MODERATE|properties=health=checked,cache=yes
    """.trimIndent()

    private class MechanismCloudProvider(
        override val id: String,
        private val reply: String
    ) : CloudProvider {
        override fun generate(prompt: String): CloudResult = CloudResult.Success(reply, id)
    }

    private class VerifyingBackend : ControlBackend {
        override val id: String = "fake-verified"
        override fun available(): Boolean = true
        override fun execute(action: ControlAction): VerificationEvidence =
            VerificationEvidence(VerificationStage.VERIFIED, id, action.id)
    }

    private fun freshDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "phase-b-$name-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun freshHighAction() = ControlAction(
        id = "toggle-wifi-1",
        actionType = ActionType.TOGGLE_WIFI,
        riskTier = RiskTier.HIGH
    )

    /** The same composition JarvisEngine.init builds for the owner-binding chain. */
    private fun productionOwnerBinder(): OwnerBiometricBinder {
        if (!HumanCore.isInitialized()) {
            HumanCore.init(storage = FileStorage(freshDir("owner")))
        }
        return OwnerBiometricBinder(HumanCoreOwnerBindingPort())
    }

    // ── AC1: Universal Research Engine, real production call path ────────────

    @Test
    fun `research engine composes the real cloud router and store and persists through both channels`() = runBlocking<Unit> {
        val router = CloudModelRouter(listOf(MechanismCloudProvider("free-model-1", cloudMechanismsText)))
        val store = InMemoryMechanismsStore()
        // Same construction as JarvisEngine.init: engine <- cloudModelRouter + mechanisms store.
        val engine = UniversalResearchEngine(cloudRouter = router, store = store)

        // The same researchPersist() call JarvisEngine.requestResearch() hosts.
        val result = engine.researchPersist(
            gapDescription = "JARVIS has no low-cost route to the nearest healthy capability provider"
        )

        assertTrue("local catalog channel always wired", CandidateSource.LOCAL_CATALOG in result.channels)
        assertTrue(
            "cloud reasoning channel routed through the real CloudModelRouter",
            CandidateSource.CLOUD_REASONING in result.channels
        )
        assertTrue(
            "cloud candidates reach the real store",
            store.readWhere { it.source == CandidateSource.CLOUD_REASONING }.isNotEmpty()
        )
        assertTrue("every candidate persisted", result.storedCount == result.candidates.size)
    }

    // ── AC2: real owner-binding entry point over the real HumanCore store ────

    @Test
    fun `owner binding entry point binds the real HumanCore owner relationship when invoked`() {
        val binder = productionOwnerBinder()

        // The typed result a successful Android BiometricPrompt produces.
        val outcome = binder.bind(
            BiometricAuthResult.Success(
                authenticators = BiometricType.BIOMETRIC_STRONG,
                authenticatedAtEpochMs = 1_700_000_000_000L
            )
        )

        assertTrue(
            "production bind accepts the biometric result (fresh bind or same-owner refresh)",
            outcome is OwnerBindingOutcome.Bound || outcome is OwnerBindingOutcome.ReAuthenticated
        )
        assertTrue("owner is bound through the real HumanCore singleton", binder.isOwnerBound())
        assertEquals("Venon", binder.currentBinding()?.ownerKey)
        assertTrue("real HumanCore facade agrees the owner is bound", HumanCore.isOwnerBound())
    }

    @Test
    fun `platform result mapper is JVM-invocable for the consumed error branch`() {
        // The mapper file targets the real android.hardware.biometrics BiometricPrompt
        // (toSuccess is on-device-only); its error branch is JVM-safe and is part of
        // the same typed chain the binder consumes.
        val mapped = PlatformBiometricPromptResultMapper.toError(13, "hardware unavailable")
        assertTrue(mapped is BiometricAuthResult.Error)
        assertEquals(13, (mapped as BiometricAuthResult.Error).errorCode)
    }

    // ── AC3: android capability adapter stack through its real production caller ──

    @Test
    fun `risk gate fails closed on lower authorization tiers`() {
        val gate = RiskGate()
        val high = freshHighAction()

        val noAuth = gate.admit(high, RiskGate.Authorization())
        assertTrue("no auth never passes HIGH", noAuth is RiskGate.GateOutcome.Blocked)

        val confirmationOnly = gate.admit(high, RiskGate.Authorization(confirmationProvided = true))
        assertTrue("missing biometric never passes HIGH", confirmationOnly is RiskGate.GateOutcome.Blocked)

        val medium = ControlAction(id = "m1", actionType = ActionType.VOLUME_CHANGE, riskTier = RiskTier.MEDIUM)
        assertTrue("MEDIUM without confirmation blocked", gate.admit(medium) is RiskGate.GateOutcome.Blocked)
        assertTrue(
            "MEDIUM with confirmation allowed",
            gate.admit(medium, RiskGate.Authorization(confirmationProvided = true)) is RiskGate.GateOutcome.Allowed
        )

        val selfMod = ControlAction(id = "sm1", actionType = ActionType.CUSTOM, riskTier = RiskTier.SELF_MODIFICATION)
        assertTrue(
            "SELF_MODIFICATION without full three-gate blocked",
            gate.admit(
                selfMod,
                RiskGate.Authorization(biometricVerified = true, confirmationProvided = true, authorizationGranted = false)
            ) is RiskGate.GateOutcome.Blocked
        )
    }

    @Test
    fun `gated device control runs the real router chain after the owner binds`() {
        // The requestDeviceControl() composition: biometric signal from the real
        // owner binder, RiskGate admission, then DeviceControlRouter over the real
        // backend chain (a JVM-verifying backend standing in for the Android-bound
        // ScreenBridgeControlBackend in the device composition).
        val binder = productionOwnerBinder()
        binder.bind(
            BiometricAuthResult.Success(
                authenticators = BiometricType.BIOMETRIC_STRONG,
                authenticatedAtEpochMs = 1_700_000_000_001L
            )
        )
        assertTrue("owner bound before device control", binder.isOwnerBound())

        val gate = RiskGate()
        val backend = VerifyingBackend()
        val router = DeviceControlRouter(listOf(backend))
        val high = freshHighAction()

        val admitted = gate.admit(
            high,
            RiskGate.Authorization(
                biometricVerified = binder.isOwnerBound(),
                confirmationProvided = true
            )
        )
        assertTrue("biometric + confirmation admits HIGH", admitted is RiskGate.GateOutcome.Allowed)

        val winner = router.execute(high)
        assertEquals("the verifying backend wins the chain", backend, winner)
        assertTrue("attempts are logged", router.lastSuccessfulBackends().contains(backend.id))
        assertTrue("evidence is verified, never fire-and-assume", router.attempts.last().evidence.isComplete)
    }

    // ── AC4: JarvisOrganGraph maps the three now-real subsystems ─────────────

    @Test
    fun `organ graph registers the phase-b nodes with real class names and dependency edges`() {
        val graph = JarvisOrganGraph.build()

        assertNotNull("research node", graph.node("research.universalResearchEngine"))
        assertEquals(
            "com.jarvis.app.research.universal.UniversalResearchEngine",
            graph.node("research.universalResearchEngine")?.qualifiedClassName
        )
        assertNotNull("owner-binding node", graph.node("identity.ownerBiometricBinder"))
        assertEquals(
            "com.jarvis.app.identity.owner.OwnerBiometricBinder",
            graph.node("identity.ownerBiometricBinder")?.qualifiedClassName
        )
        assertNotNull("risk gate node", graph.node("android.riskGate"))
        assertNotNull("device-control router node", graph.node("android.deviceControlRouter"))
        assertNotNull("screen-bridge backend node", graph.node("android.screenBridgeBackend"))

        assertTrue(
            "research depends on the cloud router",
            graph.edgesFrom("research.universalResearchEngine").any { it.toId == "cloud.cloudModelRouter" }
        )
        assertTrue(
            "owner binder depends on the real HumanCore",
            graph.edgesFrom("identity.ownerBiometricBinder").any { it.toId == "core.humanCore" }
        )
        assertTrue(
            "risk gate reads the owner-binding biometric signal",
            graph.edgesFrom("android.riskGate").any { it.toId == "identity.ownerBiometricBinder" }
        )
        assertTrue(
            "device-control router depends on the gate",
            graph.edgesFrom("android.deviceControlRouter").any { it.toId == "android.riskGate" }
        )
        assertTrue(
            "device-control router resolves through the real backend",
            graph.edgesFrom("android.deviceControlRouter").any { it.toId == "android.screenBridgeBackend" }
        )
    }
}