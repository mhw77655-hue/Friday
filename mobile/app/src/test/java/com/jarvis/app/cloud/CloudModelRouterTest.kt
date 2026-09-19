package com.jarvis.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudModelRouterTest {

    private class FakeCloudProvider(
        override val id: String,
        private val behavior: (String) -> CloudResult
    ) : CloudProvider {
        var calls: Int = 0
        override fun generate(prompt: String): CloudResult {
            calls++
            return behavior(prompt)
        }
    }

    private fun failing(id: String, reason: String = "provider down", rateLimited: Boolean = false) =
        FakeCloudProvider(id) { CloudResult.Failure(id, reason, rateLimited) }

    private fun succeeding(id: String, echo: String) =
        FakeCloudProvider(id) { CloudResult.Success("[$echo] reply to request", id) }

    @Test
    fun `pool tries members in order`() {
        val p1 = failing("pool-a")
        val p2 = succeeding("pool-b", "poolB")
        val router = CloudModelRouter(listOf(p1, p2))

        val result = router.submit(CloudReasoningRequest("hello"))

        assertEquals(1, p1.calls)
        assertEquals(1, p2.calls)
        assertEquals("pool-b", result.succeededProviderId)
        assertTrue(result.succeeded)
    }

    @Test
    fun `rotates on rate limiting`() {
        val p1 = failing("pool-a", reason = "rate limit exceeded", rateLimited = true)
        val p2 = succeeding("pool-b", "poolB")
        val router = CloudModelRouter(listOf(p1, p2))

        val result = router.submit(CloudReasoningRequest("hello"))

        assertEquals("pool-a exhausted (rate limit) -> must rotate to pool-b", "pool-b", result.succeededProviderId)
        assertTrue(result.failures.single().rateLimited)
    }

    @Test
    fun `returns aggregate failure when entire pool fails`() {
        val p1 = failing("pool-a")
        val p2 = failing("pool-b")
        val router = CloudModelRouter(listOf(p1, p2))

        val result = router.submit(CloudReasoningRequest("hello"))

        assertFalse(result.succeeded)
        assertNull(result.succeededProviderId)
        assertEquals(2, result.failures.size)
        assertEquals(listOf("pool-a", "pool-b"), result.failures.map { it.providerId })
    }

    @Test
    fun `rotation cursor advances round-robin per submit like ralph sh iterations`() {
        val p1 = succeeding("pool-a", "poolA")
        val p2 = succeeding("pool-b", "poolB")
        val router = CloudModelRouter(listOf(p1, p2))

        // Submit 1 starts at cursor 0 -> pool-a wins (no rotation needed).
        assertEquals("pool-a", router.submit(CloudReasoningRequest("first")).succeededProviderId)
        assertEquals(1, p1.calls)

        // Submit 2 starts at cursor 1 -> pool-b wins.
        assertEquals("pool-b", router.submit(CloudReasoningRequest("second")).succeededProviderId)
        assertEquals(1, p2.calls)

        // Submit 3 starts at cursor 0 again (round-robin) -> pool-a.
        assertEquals("pool-a", router.submit(CloudReasoningRequest("third")).succeededProviderId)
        assertEquals(2, p1.calls)
    }

    @Test
    fun `provider throwing is treated as a failure and rotates`() {
        val p1 = FakeCloudProvider("pool-a") { throw RuntimeException("boom") }
        val p2 = succeeding("pool-b", "poolB")
        val router = CloudModelRouter(listOf(p1, p2))

        val result = router.submit(CloudReasoningRequest("hello"))

        assertEquals("pool-b", result.succeededProviderId)
        assertEquals("boom", result.failures.single().reason)
    }

    @Test
    fun `rate limit marker heuristic matches ralph sh markers`() {
        val router = CloudModelRouter(listOf<CloudProvider>())
        assertTrue(router.isRateLimited("Quota exceeded"))
        assertTrue(router.isRateLimited("rate limit reached"))
        assertTrue(router.isRateLimited("capacity is busy"))
        assertTrue(router.isRateLimited("Cannot connect to host"))
        assertTrue(router.isRateLimited("model exhausted"))
        assertTrue(router.isRateLimited("HTTP 429"))
        assertFalse(router.isRateLimited("normal completion"))
    }

    @Test
    fun `no capability execution path exists - router exposes only reasoning text`() {
        val router = CloudModelRouter(listOf(succeeding("pool-a", "poolA")))

        val methods = CloudModelRouter::class.java.methods.map { it.name }
        val poolResultMethods = CloudModelRouter.PoolResult::class.java.methods.map { it.name }

        assertTrue("router must not execute capabilities directly", methods.none {
            it.contains("execute") || it.contains("capability") || it.contains("dispatch")
        })
        assertTrue("pool result must carry no action / capability handles", poolResultMethods.none {
            it.contains("execute") || it.contains("action") || it.contains("capability")
        })

        // The result of a successful submit is plain text — a reasoning feed
        // into CognitiveEngine, never a capability invocation.
        val result = router.submit(CloudReasoningRequest("think"))
        assertTrue(result.succeeded)
        assertTrue(result.text!!.startsWith("[poolA]"))
    }
}