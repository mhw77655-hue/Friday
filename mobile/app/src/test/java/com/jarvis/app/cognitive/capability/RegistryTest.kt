package com.jarvis.app.cognitive.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryTest {

    private val registry = CapabilityRegistry()

    @Test
    fun `registration succeeds and is discoverable`() {
        val c = FakeCapability(descriptor("CALC", operation = "compute"))
        assertEquals(CapabilityRegistry.RegisterResult.Ok("CALC"), registry.register(c))
        assertTrue(registry.contains("CALC"))
        assertEquals("CALC", registry.get("CALC")!!.descriptor.id)
        assertEquals(1, registry.size())
    }

    @Test
    fun `duplicate id is rejected deterministically`() {
        registry.register(FakeCapability(descriptor("CALC")))
        val dup = registry.register(FakeCapability(descriptor("CALC")))
        assertEquals(CapabilityRegistry.RegisterResult.Duplicate("CALC"), dup)
        assertEquals(1, registry.size())
    }

    @Test
    fun `replacement swaps implementation but keeps lifecycle`() {
        val original = FakeCapability(descriptor("CALC"))
        registry.register(original)
        registry.transitionTo("CALC", CapabilityLifecycle.AVAILABLE)

        val replacement = FakeCapability(descriptor("CALC", operation = "compute"))
        assertTrue(registry.replace("CALC", replacement))
        assertTrue(registry.get("CALC") === replacement)
        assertEquals(CapabilityLifecycle.AVAILABLE, registry.stateOf("CALC"))
    }

    @Test
    fun `replacement of unknown id is rejected`() {
        assertFalse(registry.replace("NOPE", FakeCapability(descriptor("NOPE"))))
    }

    @Test
    fun `unregister removes capability and its indexes`() {
        val c = FakeCapability(descriptor("CALC", operation = "compute", category = "MATH"))
        registry.register(c)
        assertTrue(registry.unregister("CALC"))
        assertFalse(registry.contains("CALC"))
        assertEquals(0, registry.lookupByOperation("compute").size)
        assertEquals(0, registry.lookupByCategory("MATH").size)
        assertNull(registry.stateOf("CALC"))
        assertFalse(registry.unregister("CALC")) // already gone
    }

    @Test
    fun `lookup by operation category and dependency`() {
        registry.register(FakeCapability(descriptor("B", operation = "compute", category = "MATH")))
        registry.register(FakeCapability(descriptor("A", operation = "compute", category = "MATH")))
        registry.register(FakeCapability(descriptor("NET", operation = "network", category = "NET", deps = setOf("CALC"))))

        // deterministic (sorted by id)
        assertEquals(listOf("A", "B"), registry.lookupByOperation("compute").map { it.descriptor.id })
        assertEquals(listOf("A", "B"), registry.lookupByCategory("MATH").map { it.descriptor.id })
        assertEquals(listOf("NET"), registry.lookupByDependency("CALC").map { it.descriptor.id })
    }

    @Test
    fun `listing is deterministic and sorted by id`() {
        registry.register(FakeCapability(descriptor("Z")))
        registry.register(FakeCapability(descriptor("A")))
        registry.register(FakeCapability(descriptor("M")))
        assertEquals(listOf("A", "M", "Z"), registry.list().map { it.descriptor.id })
        // stable across repeated calls
        assertEquals(registry.list(), registry.list())
    }
}
