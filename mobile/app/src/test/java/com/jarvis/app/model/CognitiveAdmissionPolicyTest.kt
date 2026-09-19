package com.jarvis.app.model

import com.jarvis.app.cognitive.UncertaintyProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FEP-WAKE-GATE acceptance fixtures.
 *
 * The doubt signal is the cognitive engine's existing [CognitiveState]
 * [com.jarvis.app.cognitive.UncertaintyProfile] — reused, never
 * recomputed. CognitiveAdmissionPolicy only thresholds it and gates a
 * [ModelManager.wake]. The manager runs on a FakeModelBackend with the real
 * ResourceGovernor (low-battery essential-degrade for the AC3 path) and
 * autoSweep off for determinism.
 */
class CognitiveAdmissionPolicyTest {

    private fun manager(
        backend: FakeModelBackend,
        governor: ResourceGovernor = ResourceGovernor()
    ): ModelManager = ModelManager(
        context = null,
        scope = CoroutineScope(Dispatchers.Default),
        backend = backend,
        resourceGovernor = governor,
        autoSweep = false,
        cooldownMs = 1_000L,
        clock = { 0L }
    )

    @Test
    fun `low-doubt turn never wakes the reasoning tier - resident handles it`() = runBlocking {
        val backend = FakeModelBackend()
        val mm = manager(backend)
        val policy = CognitiveAdmissionPolicy()
        var reasoningWakes = 0

        val lowDoubt = UncertaintyProfile(overall = 0.1f, intentAmbiguity = 0f)
        assertEquals(
            "below-threshold doubt must decide RESIDENT",
            CognitiveAdmissionDecision.RESIDENT,
            policy.decide(lowDoubt)
        )

        val outcome = policy.serveTurn(lowDoubt) {
            reasoningWakes++
            mm.wake(OrganRole.REASONING, task = "ac1-reactive", modelId = "reasoning-model")
        }
        assertEquals(CognitiveAdmissionDecision.RESIDENT, outcome.decision)
        assertEquals(ModelTier.RESIDENT, outcome.servedTier)
        assertFalse(outcome.degraded)
        assertEquals("low doubt must never call request() for the reasoning tier", 0, reasoningWakes)

        // The resident tier alone handles the turn: a resident wake loads cleanly.
        val resident = mm.wake(OrganRole.RESIDENT, task = "ac1-serve", modelId = "resident-model")
        assertTrue("resident tier must serve the low-doubt turn", backend.isLoaded(resident.handle))
        assertEquals("exactly one load for a resident-only turn", 1, backend.loadCount)
    }

    @Test
    fun `high-doubt turn does wake the reasoning tier`() = runBlocking {
        val backend = FakeModelBackend()
        val mm = manager(backend)
        val policy = CognitiveAdmissionPolicy()
        var reasoningWakes = 0

        val highDoubt = UncertaintyProfile(overall = 0.7f, intentAmbiguity = 0.5f)
        assertEquals(
            "at-or-above-threshold doubt must decide REASONING",
            CognitiveAdmissionDecision.REASONING,
            policy.decide(highDoubt)
        )
val outcome = policy.serveTurn(highDoubt) {
            reasoningWakes++
            mm.wake(OrganRole.REASONING, task = "ac2-reactive", modelId = "reasoning-model")
        }
        assertEquals(CognitiveAdmissionDecision.REASONING, outcome.decision)
        assertEquals("high doubt must wake the reasoning tier exactly once", 1, reasoningWakes)
        assertEquals(ModelTier.ON_DEMAND_REASONING, outcome.servedTier)
        assertFalse("ample resources must NOT degrade", outcome.degraded)
        assertTrue("the woken reasoning organ must serve this turn", backend.isLoaded(outcome.servedHandle!!))
        assertEquals("one load for the reasoning wake", 1, backend.loadCount)
    }

    @Test
    fun `governor denial on a high-doubt turn completes on the resident tier flagged degraded`() = runBlocking {
        // Low battery, not charging: the REAL governor DEGRADEs an essential
        // reasoning wake to the resident tier (never denies it bare).
        val strained = FakeResourceSnapshot(
            availableMemoryMb = 4_096,
            cpuLoadPercent = 10.0,
            thermalLevel = 0,
            batteryPercent = 5,
            isCharging = false
        )
        val backend = FakeModelBackend()
        val governor = ResourceGovernor(snapshotProvider = { strained })
        val mm = manager(backend, governor)
        val policy = CognitiveAdmissionPolicy()

        val outcome = policy.serveTurn(
            UncertaintyProfile(overall = 0.9f, unknowns = listOf("what caused the outage?"))
        ) {
            mm.wake(OrganRole.REASONING, task = "ac3-reactive", modelId = "reasoning-model")
        }

        assertEquals(CognitiveAdmissionDecision.REASONING, outcome.decision)
        assertEquals(
            "DENY/DEGRADE must be served from the next-lower (resident) tier",
            ModelTier.RESIDENT,
            outcome.servedTier
        )
        assertTrue("the degrade must be flagged internally as a degraded response", outcome.degraded)
        assertTrue(
            "the degraded turn must still complete on the resident handle, never crash/hang",
            backend.isLoaded(outcome.servedHandle!!)
        )
    }

    @Test
    fun `governor DEGRADE under RAM pressure completes flagged degraded`() = runBlocking {
        // RAM pressure alone always DEGRADEs (never bare-denies) with fine battery.
        val pressured = FakeResourceSnapshot(
            availableMemoryMb = 64,
            cpuLoadPercent = 90.0,
            thermalLevel = 2,
            batteryPercent = 80,
            isCharging = true
        )
        val backend = FakeModelBackend()
        val governor = ResourceGovernor(snapshotProvider = { pressured })
        val mm = manager(backend, governor)
        val policy = CognitiveAdmissionPolicy()

        val outcome = policy.serveTurn(
            UncertaintyProfile(overall = 0.85f)
        ) {
            mm.wake(OrganRole.REASONING, task = "ac3b-reactive", modelId = "reasoning-model")
        }

        assertTrue(outcome.degraded)
        assertEquals(ModelTier.RESIDENT, outcome.servedTier)
        assertTrue(backend.isLoaded(outcome.servedHandle!!))
    }
}