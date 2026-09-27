package com.jarvis.app.termux

import com.jarvis.app.cognition.workspace.ClaimKind
import com.jarvis.app.cognition.workspace.MentalStateClaims
import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.model.FakeModelBackend
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * COGNITION-WORKSPACE (Gate 3c) AC1 — the PRODUCTION-path half of the acceptance
 * evidence.
 *
 * The two migrated organs are proven on the real turn path, not in a unit test in
 * isolation:
 *
 *   /api/chat -> LatencyPipeline.onUserInput
 *            -> CognitiveEngine.process
 *            -> ContinuityGate.snapshotForTurn -> UserMentalStateEstimator
 *                 PUBLISHES the turn's MENTAL_STATE claim
 *            -> ContextWindowAssembler.assembleFrom
 *                 READS the MENTAL_STATE claim, PUBLISHES the CONTEXT_WINDOW claim
 *
 * That is the REAL JVM-executable composition root (TermuxJarvisServer, the
 * mirror of JarvisEngine.init with only the Android-bound stores swapped). Both
 * organs reach the SAME store instance the composition root constructed, and
 * neither names the other.
 */
class WorkspaceProductionWiringTest {

    private val servers = mutableListOf<TermuxJarvisServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
    }

    private fun newServer(): TermuxJarvisServer =
        TermuxJarvisServer(port = 0, backendOverride = FakeModelBackend())
            .also { servers.add(it) }

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
    fun `a real pipeline turn publishes both migrated organs' claims into the shared workspace`(): Unit =
        runBlocking {
            val server = newServer()
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            val input = "Green tea smells pleasant"

            pipeline.onUserInput(input)

            // The turn really went through the model seam, so the claims below
            // were published by the production path rather than by a fixture.
            assertTrue("the real turn must reach the generation seam", captured.isNotEmpty())

            // MIGRATED ORGAN 1: the estimator published this turn's reading.
            val mental = server.workspace.current(ClaimKind.MENTAL_STATE)
            assertNotNull("the real turn must publish a MENTAL_STATE claim", mental)
            assertEquals(
                "the claim records the organ that published it",
                "identity.mentalStateEstimator",
                mental!!.sourceOrgan
            )
            assertTrue("a published claim must be a real instant", mental.createdAt > 0L)

            val reading = MentalStateClaims.decode(mental.payload)
            assertNotNull("the claim text must decode back to a real hypothesis", reading)
            assertEquals(
                "the published confidence is the vocabulary's own rule applied to this " +
                    "turn's real reading — the server's estimator is the rule-based default, " +
                    "so an unanchored reading publishes the declared prior instead of an " +
                    "invented measurement",
                MentalStateClaims.confidenceFor(reading!!),
                mental.confidence,
                1e-9
            )
            assertTrue("the reading is a real goal, not a placeholder", reading.goal.isNotBlank())
            assertTrue("the reading is a real mood, not a placeholder", reading.mood.isNotBlank())

            // MIGRATED ORGAN 2: the assembler published the window it assembled.
            val window = server.workspace.current(ClaimKind.CONTEXT_WINDOW)
            assertNotNull("the real turn must publish a CONTEXT_WINDOW claim", window)
            assertEquals(
                "the claim records the organ that published it",
                "cognitive.contextWindowAssembler",
                window!!.sourceOrgan
            )
            assertTrue(
                "the window claim carries this turn's segment turns",
                window.payload.contains(input)
            )
        }

    @Test
    fun `the window's mental state is the published claim, not a second copy`(): Unit = runBlocking {
        val server = newServer()
        val captured = mutableListOf<String>()
        val pipeline = recordingPipeline(server, captured)
        val input = "I hate the slow weather"

        pipeline.onUserInput(input)

        val fromClaim = MentalStateClaims.read(server.workspace)
        assertNotNull("the turn's reading must be readable as a claim", fromClaim)
        // The real reading for a signal-bearing utterance, carried through the
        // claim losslessly. (The server's estimator is the rule-based default;
        // the emotion-fusion provider that anchors a measured confidence is
        // wired in JarvisEngine.init, and that branch is proven against the real
        // engine by EmotionFusionTier1ProductionPathTest and by
        // WorkspaceCoreTest's round trip.)
        assertEquals("resolve a pain point", fromClaim!!.goal)
        assertEquals("frustrated", fromClaim.mood)
        assertTrue(
            "the inferred unstated need rides along with the claim",
            fromClaim.unstatedNeed.isNotBlank()
        )
    }

    @Test
    fun `the engine surfaces exactly the hypothesis the workspace published`(): Unit = runBlocking {
        val server = newServer()
        val input = "Green tea smells pleasant"

        // The exact production call the pipeline makes, with the bridge seam
        // swapped for a recorder so the result object is available.
        val result = server.engine.process(input, modelCall = { payload -> "Noted: green tea." })

        val assembled = result.assembledMentalState
        assertNotNull("the real engine must still surface a per-turn mental state", assembled)
        assertEquals(
            "the assembled window's mental state IS the published claim — a real " +
                "substitution, not a second copy of the same estimate",
            MentalStateClaims.read(server.workspace),
            assembled
        )
        val claim = server.workspace.current(ClaimKind.MENTAL_STATE)!!
        assertEquals(
            "the claim's confidence is the reading's own anchored confidence when the " +
                "emotion layer observed a signal, and the declared unanchored prior when " +
                "nothing anchored it",
            MentalStateClaims.confidenceFor(assembled!!),
            claim.confidence,
            1e-9
        )
    }

    @Test
    fun `each turn supersedes the previous turn's claims instead of deleting them`(): Unit = runBlocking {
        val server = newServer()
        val captured = mutableListOf<String>()
        val pipeline = recordingPipeline(server, captured)

        pipeline.onUserInput("Green tea smells pleasant")
        val firstMental = server.workspace.current(ClaimKind.MENTAL_STATE)!!.id
        val firstWindow = server.workspace.current(ClaimKind.CONTEXT_WINDOW)!!.id
        pipeline.onUserInput("The sky looks blue today")

        val secondMental = server.workspace.current(ClaimKind.MENTAL_STATE)!!
        assertTrue("turn 2 is a different claim than turn 1", secondMental.id != firstMental)
        assertEquals("turn 2 supersedes turn 1", firstMental, secondMental.supersedes)
        assertEquals(2, server.workspace.claims(ClaimKind.MENTAL_STATE).size)
        assertEquals(
            "turn 2 supersedes turn 1's window too",
            firstWindow,
            server.workspace.current(ClaimKind.CONTEXT_WINDOW)!!.supersedes
        )
        assertEquals(2, server.workspace.claims(ClaimKind.CONTEXT_WINDOW).size)
    }

    @Test
    fun `the claim store belongs to the composition that built it, not to a global`(): Unit = runBlocking {
        val first = newServer()
        val second = newServer()
        val captured = mutableListOf<String>()

        recordingPipeline(first, captured).onUserInput("Green tea smells pleasant")

        assertNotNull(first.workspace.current(ClaimKind.MENTAL_STATE))
        assertNotNull(first.workspace.current(ClaimKind.CONTEXT_WINDOW))
        assertNull(
            "a second composition's workspace must start empty — the store is wired, not global",
            second.workspace.current(ClaimKind.MENTAL_STATE)
        )
        assertNull(second.workspace.current(ClaimKind.CONTEXT_WINDOW))
    }
}
