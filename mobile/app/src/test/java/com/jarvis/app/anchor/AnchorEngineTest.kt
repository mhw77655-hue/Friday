package com.jarvis.app.anchor

import com.jarvis.app.cognitive.UncertaintyProfile
import com.jarvis.app.model.AdmissionDecision
import com.jarvis.app.model.CognitiveAdmissionDecision
import com.jarvis.app.model.CognitiveAdmissionPolicy
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.ModelTier
import com.jarvis.app.model.OrganWakeRequest
import com.jarvis.app.model.OrganRole
import com.jarvis.app.model.ResourceGovernor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ANCHOR-ENGINE-FOUNDATION acceptance fixtures.
 *
 * AC1 (real class, real logic — not a stub), AC2/AC3 (constructed through the
 * real composition point and producing observable behavior) are proven by
 * replicating JarvisEngine.init's construction of the anchor: the REAL
 * [AnchorEngine] over the REAL [ModelManager] (its single-loading authority,
 * with only the injected [FakeModelBackend] swapped so no native inference
 * touches the device) plus the REAL shared [CognitiveAdmissionPolicy] gateway.
 * The anchor's behavior is then observed through its real [AnchorEngine.anchor]
 * and [AnchorEngine.serve] paths — the same engine instance init would build.
 *
 * Zero real model output / GGUF / native inference: generation goes through the
 * injected fake backend and self-identifies as "[fake-model-backend] echo:".
 */
class AnchorEngineTest {

    /** Deterministic composition harness mirroring JarvisEngine.init's wiring. */
    private class AnchorHarness(
        val backend: FakeModelBackend = FakeModelBackend(loadLatencyMs = 20),
        governor: ResourceGovernor = ResourceGovernor()
    ) {
        var nowMs = 0L
        val modelManager = ModelManager(
            context = null,
            scope = CoroutineScope(Dispatchers.IO),
            backend = backend,
            resourceGovernor = governor,
            cooldownMs = 1_000L,
            clock = { nowMs },
            autoSweep = false
        )
        val policy = CognitiveAdmissionPolicy()
        val engine = AnchorEngine(modelManager, policy)
    }

    /** Always degrades one hop down the ladder (governor pressure path). */
    private class DegradeGovernor : ResourceGovernor() {
        override fun admit(request: OrganWakeRequest): AdmissionDecision =
            AdmissionDecision.DEGRADE_TO(request.tier.lowerTier ?: request.tier)
    }

    private val lowDoubt = UncertaintyProfile(overall = 0.1f, intentAmbiguity = 0.1f)
    private val highDoubt = UncertaintyProfile(overall = 0.9f)

    @Test
    fun `anchor holds the resident tier through the single loading path and it is never auto-unloaded`() =
        runBlocking {
            val h = AnchorHarness()
            val backend = h.backend

            val first = h.engine.anchor()
            assertTrue("resident anchor must be loaded", h.engine.anchorHeld())
            assertTrue("the single loading path must report the resident handle loaded", backend.isLoaded(first.handle))
            assertEquals(1, backend.loadCount)

            // A second anchor() reuses the SAME cached resident handle — the
            // anchor is a holding operation, not a per-turn reload.
            val second = h.engine.anchor()
            assertEquals("re-anchoring must not reload", 1, backend.loadCount)
            assertEquals("re-anchoring must return the same handle", first.handle, second.handle)
            assertFalse("an already-held anchor is not a recovery", second.recovered)

            // The cooldown sweep must NEVER touch the resident anchor.
            h.nowMs += 5_000L
            h.modelManager.runCooldownSweep()
            assertTrue("the resident anchor is never auto-unloaded", h.engine.anchorHeld())
            assertEquals("no tier was ever unloaded", 0, backend.unloadCount)
        }

    @Test
    fun `anchor reports recovered exactly when the resident tier was missing`() = runBlocking {
        val h = AnchorHarness()

        // Fresh runtime, no resident loaded: the first anchor re-establishes it.
        assertFalse("before anchoring the runtime holds no anchor", h.engine.anchorHeld())
        val first = h.engine.anchor()
        assertTrue(first.recovered)
        assertTrue(h.engine.anchorHeld())

        val second = h.engine.anchor()
        assertFalse(second.recovered)
    }

    @Test
    fun `serve routes a low-doubt turn to the resident tier and never wakes reasoning`() =
        runBlocking {
            val h = AnchorHarness()
            val backend = h.backend
            h.engine.anchor()
            assertEquals(1, backend.loadCount)

            val outcome = h.engine.serve(lowDoubt)

            assertEquals(CognitiveAdmissionDecision.RESIDENT, outcome.decision)
            assertEquals(ModelTier.RESIDENT, outcome.requestedTier)
            assertEquals(ModelTier.RESIDENT, outcome.servedTier)
            assertFalse(outcome.degraded)
            assertNull("a resident turn has no reasoning handle", outcome.servedHandle)
            assertTrue("anchor held through the low-doubt turn", outcome.anchorHeld)
            assertFalse("no anchor recovery was needed", outcome.anchorRecovered)
            assertEquals("reasoning was never woken for a low-doubt turn", 1, backend.loadCount)
        }

    @Test
    fun `serve wakes the reasoning organ on a high-doubt turn through the real gateway`() =
        runBlocking {
            val h = AnchorHarness()
            val backend = h.backend
            h.engine.anchor()
            assertEquals(1, backend.loadCount)

            val outcome = h.engine.serve(highDoubt)

            assertEquals(CognitiveAdmissionDecision.REASONING, outcome.decision)
            assertEquals(ModelTier.ON_DEMAND_REASONING, outcome.requestedTier)
            assertEquals(ModelTier.ON_DEMAND_REASONING, outcome.servedTier)
            assertFalse(outcome.degraded)
            assertNotNull("high-doubt turns are served from a handle", outcome.servedHandle)
            assertEquals(
                "2 loads total: resident anchor + woken reasoning organ",
                2,
                backend.loadCount
            )
            val served = outcome.servedHandle!!
            assertTrue(
                "generation output self-identifies as the fake — zero real model output",
                backend.generate(served, "probe").startsWith("[fake-model-backend] echo:")
            )
            assertTrue("anchor still held while the reasoning organ was live", outcome.anchorHeld)
            assertFalse(outcome.anchorRecovered)
        }

    @Test
    fun `serve re-establishes a missing anchor and reports the recovery`() = runBlocking {
        val h = AnchorHarness()
        val backend = h.backend

        // The runtime was never anchored: the first serve finds the resident
        // anchor missing, re-establishes it, and reports the recovery.
        val outcome = h.engine.serve(lowDoubt)

        assertEquals(CognitiveAdmissionDecision.RESIDENT, outcome.decision)
        assertTrue("the serve recovered the missing anchor", outcome.anchorRecovered)
        assertTrue("after recovery the anchor holds", outcome.anchorHeld)
        assertTrue("the recovery went through the single loading path", backend.isLoaded(backend.loaded.first()))
        assertEquals(1, backend.loadCount)
    }

    @Test
    fun `serve degrades onto the resident anchor when the governor denies the reasoning wake`() =
        runBlocking {
            val h = AnchorHarness(governor = DegradeGovernor())
            val backend = h.backend
            h.engine.anchor()
            assertEquals(1, backend.loadCount)

            val outcome = h.engine.serve(highDoubt)

            assertEquals("the gateway still requests reasoning", CognitiveAdmissionDecision.REASONING, outcome.decision)
            assertEquals(ModelTier.ON_DEMAND_REASONING, outcome.requestedTier)
            assertEquals(
                "a denied reasoning wake degrades onto the resident anchor, not a crash",
                ModelTier.RESIDENT,
                outcome.servedTier
            )
            assertTrue("degraded admission is flagged, never hidden", outcome.degraded)
            assertTrue("the resident anchor held through the degraded turn", outcome.anchorHeld)
            assertEquals("only the resident anchor was ever loaded", 1, backend.loadCount)
        }
}