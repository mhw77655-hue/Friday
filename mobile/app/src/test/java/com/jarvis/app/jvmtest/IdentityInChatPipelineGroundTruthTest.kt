package com.jarvis.app.jvmtest

import com.jarvis.app.model.FakeModelBackend
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end identity wiring proof over the same TurnPathAssembly used by the
 * Android host. No second CognitiveEngine or LatencyPipeline is constructed.
 */
class IdentityInChatPipelineGroundTruthTest {

    @Test
    fun `Friday identity reaches the outgoing request through the unified pipeline`() = runBlocking {
        val backend = FakeModelBackend()
        val server = JvmCompositionHarness(port = 0, backendOverride = backend)
        try {
            server.bootstrap()
            assertEquals("Friday", server.selfModel.identity().name)
            assertEquals("she/her", server.selfModel.identity().pronouns)
            assertNotNull(server.identityContext)
            assertNotNull(server.pipeline)

            server.pipeline.onUserInput("Who are you?")
            withTimeout(5_000) {
                while (backend.generateCount < 1) delay(10)
            }

            val payload = backend.lastPrompt
            assertNotNull("the unified pipeline must reach the model backend", payload)
            assertTrue("Friday identity must reach the outgoing prompt", payload!!.contains("Friday"))
            assertTrue("she/her identity must reach the outgoing prompt", payload.contains("she/her"))
            assertFalse("the retired JARVIS identity must not leak into the prompt", payload.contains("JARVIS"))
        } finally {
            server.stop()
        }
    }
}
