package com.jarvis.app.language

import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.termux.TermuxJarvisServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EGYPTIAN-ARABIC-TEXT-HALF AC3: prove the dialect signal flows through the
 * REAL production composition (TermuxJarvisServer -> LatencyPipeline ->
 * CognitiveEngine.process -> dialectDetector -> IdentityContext ->
 * formatForPrompt) and lands in the actual outgoing request payload.
 *
 * The only swapped hop is bridgeSend (recording collector instead of
 * modelManager.send) — precisely the seam
 * [CognitiveEngine.process] uses, exactly as in
 * IdentityInChatPipelineGroundTruthTest.
 */
class DialectIntegrationTest {

    private fun recordingPipeline(
        server: TermuxJarvisServer,
        captured: MutableList<String>
    ): LatencyPipeline = LatencyPipeline(
        dispatch = { it() },
        scheduleDelayed = { _, _ -> },
        bridgeSend = { captured.add(it) },
        bridgeStatus = { server.modelManager.status.value },
        beginExchange = { _, _ -> null },
        express = { reply, _ -> StyledResponse.Approved(reply) },
        completeExchange = { _, _, _, _, _ -> },
        sessionContext = { null },
        cognitiveEngine = server.engine
    )

    @Test
    fun `Egyptian Arabic turn carries dialect signal in the outgoing payload`(): Unit =
        runBlocking {
            val server = TermuxJarvisServer(port = 0, backendOverride = FakeModelBackend())
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            try {
                pipeline.onUserInput("ايه رايك كده")
                val payload = captured.last()
                assertTrue("identity section present on DIRECT_REPLY turn", payload.contains("[Identity context]"))
                assertTrue(
                    "Egyptian dialect signal travels in the outgoing payload",
                    payload.contains("user dialect: ar-EG")
                )
            } finally {
                server.stop()
            }
        }

    @Test
    fun `code-switched turn reports the mixed language mix`(): Unit =
        runBlocking {
            val server = TermuxJarvisServer(port = 0, backendOverride = FakeModelBackend())
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            try {
                pipeline.onUserInput("I love مصر so much")
                val payload = captured.last()
                assertTrue(
                    "code-switched mix travels in the payload",
                    payload.contains("user dialect: en+ar-EG")
                )
            } finally {
                server.stop()
            }
        }

    @Test
    fun `pure English turn does not emit a dialect line`(): Unit =
        runBlocking {
            val server = TermuxJarvisServer(port = 0, backendOverride = FakeModelBackend())
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            try {
                pipeline.onUserInput("hello there")
                val payload = captured.last()
                assertTrue("payload still carries the identity section", payload.contains("[Identity context]"))
                assertTrue(
                    "neutral dialect signal is suppressed from the payload",
                    !payload.contains("user dialect:")
                )
            } finally {
                server.stop()
            }
        }

    @Test
    fun `the dialect signal is directly detectable in isolation`() {
        val detector = EgyptianArabicDialectDetector()
        val signal = detector.detect("ايه رايك كده")
        assertEquals("ar-EG", signal.detectedLanguageMix)
        assertNotNull(signal)
        assertTrue(signal.dialectConfidence > 0f)
    }
}