package com.jarvis.app.cognitive.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecycleTest {

    private val registry = CapabilityRegistry()
    private fun register() {
        registry.register(FakeCapability(descriptor("CALC")))
    }

    @Test
    fun `valid happy-path transitions`() {
        register()
        assertEquals(
            CapabilityRegistry.TransitionResult.Changed(CapabilityLifecycle.REGISTERED, CapabilityLifecycle.AVAILABLE),
            registry.transitionTo("CALC", CapabilityLifecycle.AVAILABLE)
        )
        assertEquals(
            CapabilityRegistry.TransitionResult.Changed(CapabilityLifecycle.AVAILABLE, CapabilityLifecycle.RUNNING),
            registry.transitionTo("CALC", CapabilityLifecycle.RUNNING)
        )
        assertEquals(
            CapabilityRegistry.TransitionResult.Changed(CapabilityLifecycle.RUNNING, CapabilityLifecycle.AVAILABLE),
            registry.transitionTo("CALC", CapabilityLifecycle.AVAILABLE)
        )
    }

    @Test
    fun `invalid transitions are rejected deterministically`() {
        register()
        registry.transitionTo("CALC", CapabilityLifecycle.AVAILABLE)
        // AVAILABLE -> REGISTERED is invalid
        assertEquals(
            CapabilityRegistry.TransitionResult.Rejected(CapabilityLifecycle.AVAILABLE, CapabilityLifecycle.REGISTERED),
            registry.transitionTo("CALC", CapabilityLifecycle.REGISTERED)
        )
        // REGISTERED -> RUNNING is invalid (must be AVAILABLE first)
        val fresh = CapabilityRegistry()
        fresh.register(FakeCapability(descriptor("CALC")))
        assertEquals(
            CapabilityRegistry.TransitionResult.Rejected(CapabilityLifecycle.REGISTERED, CapabilityLifecycle.RUNNING),
            fresh.transitionTo("CALC", CapabilityLifecycle.RUNNING)
        )
    }

    @Test
    fun `failure then recovery transition`() {
        register()
        registry.transitionTo("CALC", CapabilityLifecycle.AVAILABLE)
        registry.transitionTo("CALC", CapabilityLifecycle.RUNNING)
        assertEquals(
            CapabilityRegistry.TransitionResult.Changed(CapabilityLifecycle.RUNNING, CapabilityLifecycle.FAILED),
            registry.transitionTo("CALC", CapabilityLifecycle.FAILED)
        )
        assertEquals(
            CapabilityRegistry.TransitionResult.Changed(CapabilityLifecycle.FAILED, CapabilityLifecycle.AVAILABLE),
            registry.transitionTo("CALC", CapabilityLifecycle.AVAILABLE)
        )
    }

    @Test
    fun `isolation and recovery to available`() {
        register()
        registry.transitionTo("CALC", CapabilityLifecycle.AVAILABLE)
        assertEquals(
            CapabilityRegistry.TransitionResult.Changed(CapabilityLifecycle.AVAILABLE, CapabilityLifecycle.ISOLATED),
            registry.transitionTo("CALC", CapabilityLifecycle.ISOLATED)
        )
        assertEquals(
            CapabilityRegistry.TransitionResult.Changed(CapabilityLifecycle.ISOLATED, CapabilityLifecycle.AVAILABLE),
            registry.transitionTo("CALC", CapabilityLifecycle.AVAILABLE)
        )
    }

    @Test
    fun `state changes are observable via the hook`() {
        val seen = mutableListOf<Pair<CapabilityLifecycle, CapabilityLifecycle>>()
        val r = CapabilityRegistry(onStateChanged = { _, from, to -> seen.add(from to to) })
        r.register(FakeCapability(descriptor("CALC")))
        r.transitionTo("CALC", CapabilityLifecycle.AVAILABLE)
        r.transitionTo("CALC", CapabilityLifecycle.RUNNING)
        assertEquals(
            listOf(
                CapabilityLifecycle.REGISTERED to CapabilityLifecycle.AVAILABLE,
                CapabilityLifecycle.AVAILABLE to CapabilityLifecycle.RUNNING
            ),
            seen
        )
        assertTrue(seen.size == 2)
    }
}
