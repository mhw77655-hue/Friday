package com.jarvis.app.cognitive.capability

import com.jarvis.app.failure.DegradationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionTest {

    private val registry = CapabilityRegistry()
    private val resolver = CapabilityResolver(registry, degradedLevelOf = { DegradationLevel.FULL })

    private fun register(c: FakeCapability) = registry.register(c)

    @Test
    fun `matching operation resolves to the capability`() {
        register(FakeCapability(descriptor("CALC", operation = "compute")))
        val r = resolver.resolve(request(action = "compute"), emptySet())
        assertTrue(r is Resolution.Success)
        assertEquals("CALC", (r as Resolution.Success).capability.descriptor.id)
    }

    @Test
    fun `unknown operation is not found`() {
        register(FakeCapability(descriptor("CALC", operation = "compute")))
        assertEquals(
            ResolutionReason.OPERATION_NOT_FOUND,
            (resolver.resolve(request(action = "fly"), emptySet()) as Resolution.Failure).reason
        )
    }

    @Test
    fun `disabled capability is rejected`() {
        register(FakeCapability(descriptor("CALC", operation = "compute", enabled = false)))
        assertEquals(
            ResolutionReason.CAPABILITY_DISABLED,
            (resolver.resolve(request(action = "compute"), emptySet()) as Resolution.Failure).reason
        )
    }

    @Test
    fun `offline capability is unavailable`() {
        register(FakeCapability(descriptor("CALC", operation = "compute", availability = Availability.OFFLINE)))
        assertEquals(
            ResolutionReason.CAPABILITY_UNAVAILABLE,
            (resolver.resolve(request(action = "compute"), emptySet()) as Resolution.Failure).reason
        )
    }

    @Test
    fun `missing dependency is rejected`() {
        register(FakeCapability(descriptor("BROWSER", operation = "search", deps = setOf("NETWORK"))))
        // NETWORK not registered
        assertEquals(
            ResolutionReason.DEPENDENCY_UNAVAILABLE,
            (resolver.resolve(request(action = "search"), emptySet()) as Resolution.Failure).reason
        )
    }

    @Test
    fun `missing permission is rejected`() {
        register(FakeCapability(
            descriptor("NET", operation = "fetch", permissions = setOf(CapabilityPermission.NETWORK))
        ))
        val r = resolver.resolve(request(action = "fetch"), granted = emptySet())
        assertEquals(ResolutionReason.PERMISSION_DENIED, (r as Resolution.Failure).reason)
    }

    @Test
    fun `granted permission allows resolution`() {
        register(FakeCapability(
            descriptor("NET", operation = "fetch", permissions = setOf(CapabilityPermission.NETWORK))
        ))
        val r = resolver.resolve(request(action = "fetch"), granted = setOf(CapabilityPermission.NETWORK))
        assertTrue(r is Resolution.Success)
    }

    @Test
    fun `ambiguous when multiple eligible capabilities match`() {
        register(FakeCapability(descriptor("A", operation = "compute")))
        register(FakeCapability(descriptor("B", operation = "compute")))
        val r = resolver.resolve(request(action = "compute"), emptySet())
        assertEquals(ResolutionReason.AMBIGUOUS, (r as Resolution.Failure).reason)
    }

    @Test
    fun `resource-degraded capability is rejected at resolution`() {
        val r = CapabilityRegistry()
        r.register(FakeCapability(descriptor("CALC", operation = "compute")))
        val degraded = CapabilityResolver(r, degradedLevelOf = { DegradationLevel.OFFLINE })
        assertEquals(
            ResolutionReason.CAPABILITY_UNAVAILABLE,
            (degraded.resolve(request(action = "compute"), emptySet()) as Resolution.Failure).reason
        )
    }
}
