package com.jarvis.app.jvmtest

import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.model.FakeModelBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IDENTITY-IN-CHAT-PIPELINE-GROUND-TRUTH-AND-FIX — asserts on the ACTUAL
 * OUTGOING REQUEST PAYLOAD, never on the model's (nondeterministic) reply text.
 *
 * Ground truth of this test-only JVM harness call path (not the Android app path):
 *
 *   JvmCompositionHarness /api/chat -> pipeline.onUserInput
 *     -> LatencyPipeline.onUserInput (LatencyLayer.kt:278)
 *     -> CognitiveEngine.process(text, ctx, sendBlock = bridgeSend) (LatencyLayer.kt:312)
 *
 * In this harness the bridgeSend lambda hands the built context
 * message verbatim to ModelManager.send, which wraps it into
 * `Message(MessageRole.USER, <that text>)` inside the GenerateRequest the active
 * provider translates into the request sent to Ollama (ModelManager.kt:473-488).
 * So the text captured at bridgeSend IS the outgoing request payload content.
 *
 * The harness uses Friday's owner-seeded identity record, not a Human Core
 * singleton or a second identity-injection path. Android integration still
 * requires separate device-level proof. The test drives the same platform-neutral
 * TurnPathAssembly as the Android app:
 *   AC1 - the real composition resolves Friday / she;
 *   AC2 - real identity/persona content is wired into the outgoing payload
 *         through the existing IdentityContext.formatForPrompt mechanism, with
 *         the identity stack built by JvmCompositionHarness itself (no second
 *         identity-injection path created).
 */
class IdentityInChatPipelineGroundTruthTest {

    /** Recording pipeline over the REAL JVM test harness server engine: the only swapped
     *  hop is the last one (recording collector instead of modelManager.send),
     *  which is precisely the bridgeSend seam [CognitiveEngine.process] uses. */
    private fun recordingPipeline(
        server: JvmCompositionHarness,
        captured: MutableList<String>
    ): LatencyPipeline = LatencyPipeline(
        dispatch = { it() },
        scheduleDelayed = { _, _ -> },
        bridgeSend = { captured.add(it) },
        bridgeStatus = { server.modelManager.status.value },
        sessionContext = { null },
        cognitiveEngine = server.engine
    )

    @Test
    fun `real JVM test harness composition resolves Friday identity`() {
        val server = JvmCompositionHarness(port = 0, backendOverride = FakeModelBackend())
        try {
            assertEquals(
                "the composition reads Friday's owner-seeded identity record rather than a legacy Human Core singleton",
                "Friday",
                server.selfModel.identity().name
            )
            assertEquals("identity version is the real fallback baseline", 1, server.selfModel.identity().version)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `Friday identity reaches the actual outgoing request payload`(): Unit =
        runBlocking {
            val server = JvmCompositionHarness(port = 0, backendOverride = FakeModelBackend())
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            try {
                // The exact turn whose reply the story's symptom documents
                // ("I don't have a name"): self-referential, through the same
                // entry the /api/chat handler drives.
                pipeline.onUserInput("What is your name?")
                val payload = captured.last()
                assertTrue("identity section present in outgoing payload", payload.contains("[Identity context]"))
                assertTrue(
                    "Friday identity name travels in the outgoing request payload " +
                        "(pre-fix this was 'unknown v0')",
                    payload.contains("self: identity=Friday")
                )
            } finally {
                server.stop()
            }
        }

    @Test
    fun `durable PersonaTuning content reaches the actual outgoing request payload`(): Unit =
        runBlocking {
            val server = JvmCompositionHarness(port = 0, backendOverride = FakeModelBackend())
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            try {
                server.personaTuner.ingest("Please be more direct with me")
                pipeline.onUserInput("Remind me of the plan")
                val payload = captured.last()
                assertTrue(
                    "durable persona trait travels in the outgoing request payload",
                    payload.contains("persona trait directness = high")
                )
                assertTrue(
                    "persona trait persisted onto the user node through the real composition",
                    payload.contains("world: Venon persona:directness")
                )
            } finally {
                server.stop()
            }
        }

    @Test
    fun `IdentityContext content is present in the outgoing payload on an ordinary turn`(): Unit =
        runBlocking {
            val server = JvmCompositionHarness(port = 0, backendOverride = FakeModelBackend())
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            try {
                pipeline.onUserInput("hello")
                val payload = captured.last()
                assertTrue("identity section travels on every DIRECT_REPLY turn", payload.contains("[Identity context]"))
                assertTrue("per-turn mental-state hypothesis travels in the payload", payload.contains("user mental state:"))
            } finally {
                server.stop()
            }
        }
}