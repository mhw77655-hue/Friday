package com.jarvis.app.organism

import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.microsystem.CurrentResourceUsage
import com.jarvis.app.microsystem.MicroSystemContract
import com.jarvis.app.microsystem.MicroSystemHealth
import com.jarvis.app.microsystem.MicroSystemStatus
import com.jarvis.app.microsystem.SyncMessage
import com.jarvis.app.resource.ResourceRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Organism lifecycle (§18 dormancy) — load, suspend, re-activate, disable.
 * Plain JVM (JUnit 4).
 */
class OrganismLifecycleTest {

    private class TrackingSystem : MicroSystemContract {
        var initCount = 0
        var shutdownCount = 0
        override val id = "track"
        override val genome = GenomeBuilder("track").capability("x").build()
        override val capabilities = setOf("x")
        override val requiredCapabilities = emptySet<String>()
        override val isReady get() = _health.value.status == MicroSystemStatus.HEALTHY
        private val _health = MutableStateFlow(MicroSystemHealth(MicroSystemStatus.UNKNOWN))
        override val health: StateFlow<MicroSystemHealth> = _health.asStateFlow()
        override val resourceRequirements = ResourceRequest(50, 5.0, 1000)
        override val currentResources: StateFlow<CurrentResourceUsage> = MutableStateFlow(CurrentResourceUsage()).asStateFlow()
        override val incoming = MutableStateFlow(SyncMessage("t", "t", "t", "x")).asStateFlow()
        override val outgoing = MutableStateFlow(SyncMessage("t", "t", "t", "x")).asStateFlow()
        override val telemetry: StateFlow<Map<String, Any>> = MutableStateFlow<Map<String, Any>>(emptyMap()).asStateFlow()
        override suspend fun initialize() { initCount++; _health.value = MicroSystemHealth(MicroSystemStatus.HEALTHY) }
        override suspend fun onPromoted() { _health.value = MicroSystemHealth(MicroSystemStatus.HEALTHY) }
        override suspend fun shutdown() { shutdownCount++; _health.value = MicroSystemHealth(MicroSystemStatus.DISABLED) }
        override suspend fun checkHealth() = _health.value
        override fun reportFailure(report: com.jarvis.app.failure.FailureReport) {}
        override suspend fun deliver(message: SyncMessage) {}
        override fun emit(message: SyncMessage) {}
    }

    @Test
    fun `activates then suspends then reactivates`() = runBlocking {
        val system = TrackingSystem()
        val lifecycle = OrganismLifecycle(system)
        assertEquals(OrganismPhase.DORMANT, lifecycle.currentPhase)

        assertTrue(lifecycle.activate())
        assertEquals(OrganismPhase.ACTIVE, lifecycle.currentPhase)
        assertEquals(1, system.initCount)

        assertTrue(lifecycle.suspend())
        assertEquals(OrganismPhase.SUSPENDED, lifecycle.currentPhase)
        assertEquals(1, system.shutdownCount)

        // Reactivation re-initializes the organism.
        assertTrue(lifecycle.activate())
        assertEquals(OrganismPhase.ACTIVE, lifecycle.currentPhase)
        assertEquals(2, system.initCount)
    }

    @Test
    fun `disable is terminal`() = runBlocking {
        val lifecycle = OrganismLifecycle(TrackingSystem())
        lifecycle.activate()
        assertTrue(lifecycle.disable())
        assertEquals(OrganismPhase.DISABLED, lifecycle.currentPhase)
        assertFalse(lifecycle.activate())
        assertFalse(lifecycle.canTransition(OrganismPhase.DORMANT))
    }

    @Test
    fun `invalid transitions are rejected`() {
        val lifecycle = OrganismLifecycle(TrackingSystem())
        // DORMANT cannot jump straight to SUSPENDED (must initialize first).
        assertFalse(lifecycle.canTransition(OrganismPhase.SUSPENDED))
        // DORMANT → DISABLED is allowed (never activated).
        assertTrue(lifecycle.canTransition(OrganismPhase.DISABLED))
    }

    @Test
    fun `degraded is marked and recoverable to active`() = runBlocking {
        val lifecycle = OrganismLifecycle(TrackingSystem())
        lifecycle.activate()
        lifecycle.markDegraded()
        assertEquals(OrganismPhase.DEGRADED, lifecycle.currentPhase)
        assertTrue(lifecycle.activate())
        assertEquals(OrganismPhase.ACTIVE, lifecycle.currentPhase)
    }
}
