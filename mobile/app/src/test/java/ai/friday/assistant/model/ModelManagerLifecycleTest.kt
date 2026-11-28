package ai.friday.assistant.model

import ai.friday.assistant.env.ModelProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MODEL-MANAGER-LIFECYCLE acceptance fixtures.
 *
 * ModelManager is exercised with an injected in-memory [FakeModelBackend] and
 * a deterministic fake clock (autoSweep = false, sweeps driven by hand). The
 * production ResourceGovernor is injected unwired (always-healthy snapshot),
 * so the ACCEPT / DENY / DEGRADE_TO matrix is deterministic.
 */
class ModelManagerLifecycleTest {

    private var now = 0L

    private fun manager(
        backend: FakeModelBackend,
        governor: ResourceGovernor = ResourceGovernor(),
        cooldownMs: Long = 1_000L
    ): ModelManager = ModelManager(
        context = null,
        scope = CoroutineScope(Dispatchers.Default),
        backend = backend,
        resourceGovernor = governor,
        cooldownMs = cooldownMs,
        clock = { now },
        autoSweep = false
    )

    /** Always refuses — proves ModelManager degrades instead of throwing/hanging. */
    private class DenyGovernor : ResourceGovernor() {
        override fun admit(request: OrganWakeRequest): AdmissionDecision = AdmissionDecision.DENY
    }

    /** Always degrades one hop down the ladder. */
    private class DegradeGovernor : ResourceGovernor() {
        override fun admit(request: OrganWakeRequest): AdmissionDecision =
            AdmissionDecision.DEGRADE_TO(request.tier.lowerTier ?: request.tier)
    }

    @Test
    fun `resident tier stays warm through arbitrary idle time without memory pressure`() = runBlocking {
        val backend = FakeModelBackend()
        val m = manager(backend)

        val resident = m.request(OrganRole.RESIDENT, task = "resident-ac1", modelId = "resident-model")
        assertTrue("resident request must come back loaded", backend.isLoaded(resident))

        // Arbitrarily long idle without Android memory pressure keeps the resident tier warm.
        now = 1_000_000L
        m.runCooldownSweep()
        assertTrue("resident must survive arbitrary idle time", backend.isLoaded(resident))
        assertEquals("cooldown must never unload the resident tier", 0, backend.unloadCount)

        // And a later resident request still returns the same loaded handle.
        val again = m.request(OrganRole.RESIDENT, task = "resident-ac1-again", modelId = "resident-model")
        assertEquals("resident requests must reuse the resident handle", resident.id, again.id)
        assertEquals("no second load for the resident tier", 1, backend.loadCount)
    }

    @Test
    fun `resident language model is reclaimed only under Android memory pressure after idle grace`() = runBlocking {
        val backend = FakeModelBackend()
        val pressure = object : ResourceSnapshot by ResourceSnapshot.alwaysHealthy() {
            override val isLowMemory: Boolean = true
        }
        val m = manager(
            backend,
            governor = ResourceGovernor(snapshotProvider = { pressure })
        )
        val resident = m.request(OrganRole.RESIDENT, task = "resident-pressure", modelId = "resident-model")
        now = ModelManager.PRESSURE_RECLAIM_GRACE_MS - 1
        m.runCooldownSweep()
        assertTrue("resident model remains during the safety grace", backend.isLoaded(resident))
        now = ModelManager.PRESSURE_RECLAIM_GRACE_MS
        m.runCooldownSweep()
        assertFalse("idle resident LLM can be reclaimed to protect Android", backend.isLoaded(resident))
        assertEquals(1, backend.unloadCount)
    }

    @Test
    fun `memory pressure never reclaims a model during active generation`() = runBlocking {
        val backend = FakeModelBackend(generateLatencyMs = 250L)
        val pressure = object : ResourceSnapshot by ResourceSnapshot.alwaysHealthy() {
            override val isLowMemory: Boolean = true
        }
        val m = manager(
            backend,
            governor = ResourceGovernor(snapshotProvider = { pressure })
        )
        val resident = m.request(OrganRole.RESIDENT, task = "resident-active", modelId = "resident-model")
        val generation = async { m.generateOnTier(ModelTier.RESIDENT, "keep this model pinned") }
        delay(40L)
        now = ModelManager.PRESSURE_RECLAIM_GRACE_MS * 2
        m.runCooldownSweep()
        assertTrue("active inference pins the resident model", backend.isLoaded(resident))
        assertTrue(generation.await().content.isNotBlank())
        now += ModelManager.PRESSURE_RECLAIM_GRACE_MS
        m.runCooldownSweep()
        assertFalse("model becomes reclaimable after generation ends and idle grace passes", backend.isLoaded(resident))
    }

    @Test
    fun `on-demand tier loads once reuses the same handle and auto-unloads after cooldown`() = runBlocking {
        val backend = FakeModelBackend()
        val m = manager(backend)

        val first = m.request(OrganRole.REASONING, task = "reasoning-ac2", modelId = "reasoning-model")
        assertEquals("first use loads exactly once", 1, backend.loadCount)
        assertTrue(backend.isLoaded(first))

        val second = m.request(OrganRole.REASONING, task = "reasoning-ac2-again", modelId = "reasoning-model")
        assertEquals("second use must reuse the handle, not reload", 1, backend.loadCount)
        assertEquals("same logical handle served", first.id, second.id)

        // Cooldown expires with no new request -> automatic unload.
        now = 1_000L
        m.runCooldownSweep()
        assertEquals("expired cooldown must trigger the automatic unload", 1, backend.unloadCount)
        assertFalse("handle must be invalidated by the automatic unload", backend.isLoaded(first))

        // A later request loads a fresh handle again.
        val third = m.request(OrganRole.REASONING, task = "reasoning-ac2-later", modelId = "reasoning-model")
        assertEquals("post-unload request must reload", 2, backend.loadCount)
        assertNotEquals("fresh handle after unload", first.id, third.id)
    }

    @Test
    fun `DENY from the governor serves the request from the next-lower tier`() = runBlocking {
        val backend = FakeModelBackend()
        val m = manager(backend, governor = DenyGovernor())

        val handle = m.request(OrganRole.REASONING, task = "reasoning-ac3-deny", modelId = "reasoning-model")
        assertTrue("DENY must degrade to the resident tier instead of throwing or hanging", backend.isLoaded(handle))
        assertEquals("degraded wake must still load through the single backend", 1, backend.loadCount)
    }

    @Test
    fun `DEGRADE_TO from the governor serves the request from the next-lower tier`() = runBlocking {
        val backend = FakeModelBackend()
        val m = manager(backend, governor = DegradeGovernor())

        val handle = m.request(OrganRole.REASONING, task = "reasoning-ac3-degrade", modelId = "reasoning-model")
        assertTrue("DEGRADE_TO must drop to the lower tier instead of throwing or hanging", backend.isLoaded(handle))
        assertEquals(1, backend.loadCount)
    }

    @Test
    fun `concurrent requests for the same on-demand tier share a single in-flight load`() = runBlocking {
        val backend = FakeModelBackend(loadLatencyMs = 150L)
        val m = manager(backend)

        val first = async { m.request(OrganRole.REASONING, task = "reasoning-ac4-a", modelId = "shared-model") }
        val second = async { m.request(OrganRole.REASONING, task = "reasoning-ac4-b", modelId = "shared-model") }

        val a = first.await()
        val b = second.await()
        assertEquals("both callers must receive the same handle", a.id, b.id)
        assertEquals("concurrent requests must share one in-flight load", 1, backend.loadCount)
        assertTrue(backend.isLoaded(a))
    }
    @Test
    fun `generateOnTier uses the process-isolated resident backend rather than the active heuristic adapter`() = runBlocking {
        val backend = FakeModelBackend(cannedResponse = { "local-resident:" + it.take(40) })
        val m = manager(backend)

        val resident = m.request(OrganRole.RESIDENT, task = "offline-planner", modelId = "friday-resident:latest")
        assertTrue(backend.isAvailable(resident))
        assertEquals(ModelProviderType.HEURISTIC, m.activeProvider.value.providerType)

        val result = m.generateOnTier(ModelTier.RESIDENT, "decompose this local task")
        assertEquals("local-resident:decompose this local task", result.content)
        assertEquals("friday-resident:latest", result.model)
        assertEquals(1, backend.generateCount)
    }

}