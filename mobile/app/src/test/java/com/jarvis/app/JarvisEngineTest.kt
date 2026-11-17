package com.jarvis.app

import com.jarvis.app.jvmtest.JvmCompositionHarness
import com.jarvis.app.model.FakeModelBackend
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM production-host smoke test. The host delegates composition to the same
 * TurnPathAssembly used by Android; it must not hand-build a parallel graph.
 */
class JarvisEngineTest {

    @Test
    fun `jvm host exposes one Friday turn composition and reaches its backend`() = runBlocking {
        val backend = FakeModelBackend()
        val host = JvmCompositionHarness(port = 0, backendOverride = backend)
        try {
            host.bootstrap()

            assertNotNull(host.engine)
            assertNotNull(host.pipeline)
            assertNotNull(host.modelManager)
            assertNotNull(host.identityContext)
            assertNotNull(host.worldModel)
            assertEquals("Friday", host.selfModel.identity().name)
            assertEquals("she/her", host.selfModel.identity().pronouns)

            host.pipeline.onUserInput("Hello Friday")
            withTimeout(5_000) {
                while (backend.generateCount < 1) delay(10)
            }
            assertTrue("the shared pipeline must call the configured backend", backend.lastPrompt!!.contains("Hello Friday"))
        } finally {
            host.stop()
        }
    }

    @Test
    fun `identity context and capability resolver share the assembled registry`() {
        val host = JvmCompositionHarness(port = 0)
        try {
            assertNotNull(host.identityContext)
            assertNotNull(host.worldModel)
            assertNotNull(host.fuzzyCommandResolver)
            assertNotNull(host.cloudModelRouter)
            assertTrue(
                "SelfModel reads the same live registry exposed by the composition",
                host.selfModel.capabilities().map { it.id }.toSet().containsAll(
                    host.registry.getAll().map { it.id }
                )
            )
        } finally {
            host.stop()
        }
    }
}
