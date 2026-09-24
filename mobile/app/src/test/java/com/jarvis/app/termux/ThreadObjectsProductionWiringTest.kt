package com.jarvis.app.termux

import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.threads.ThreadObjects
import com.jarvis.app.threads.ThreadTracker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THREAD-OBJECTS (Gate 3a, priority 2) — the PRODUCTION-path half of the
 * acceptance evidence.
 *
 * AC6 demands proof against the REAL entry point, not a mock/test-only path.
 * This drives the REAL JVM-executable composition root (TermuxJarvisServer —
 * the mirror of JarvisEngine.init with only the Android-bound stores swapped)
 * through its REAL LatencyPipeline down to CognitiveEngine.process, i.e. the
 * exact call path
 *
 *   /api/chat -> LatencyPipeline.onUserInput
 *            -> CognitiveEngine.process(text, ctx, sendBlock = bridgeSend)
 *
 * Exactly like TurnTraceProductionWiringTest, the ONLY swapped hop is the last
 * one: a recording collector replaces modelManager.send (the bridge seam),
 * because asserting on the model's reply text would be nondeterministic. The
 * ThreadTracker itself is REAL and runs end-to-end inside the engine.
 *
 * AC6 (`[Open threads]` fixture — English + Egyptian Arabic + Franco-Arabic):
 *   1. a three-thought message becomes three tracked threads — the half-finished
 *      one marked incomplete, tangents anchored to the main task;
 *   2. the reply acknowledges every open thread in one clause each;
 *   3. a related follow-up resurfaces the half-finished thought;
 *   4. a resolving follow-up ("Zabatna el safar") closes it — no return.
 *
 * AC7: the negative control through the real path — with the tracker DISABLED
 * the three_thoughts_test assertions provably fail (no threads, no
 * acknowledgment block, no resurface), proving the wiring is not a stub and
 * the assertions are not vacuous.
 *
 * NOTE: the decay clock params are tightened (minimumHoldMs = 0) so the
 * sequence resurfacing is deterministic in test time; production defaults
 * (60 s bake) are untouched.
 */
class ThreadObjectsProductionWiringTest {

    private val servers = mutableListOf<TermuxJarvisServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
    }

    /** Real production composition, with a real in-memory ThreadTracker. */
    private fun serverWithTracker(tracker: ThreadTracker): TermuxJarvisServer {
        val server = TermuxJarvisServer(
            port = 0,
            backendOverride = FakeModelBackend(),
            threadTracker = tracker
        )
        servers.add(server)
        return server
    }

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
    fun `three_thoughts_test`(): Unit = runBlocking {
        val tracker = ThreadTracker(minimumHoldMs = 0)
        val server = serverWithTracker(tracker)
        val captured = mutableListOf<String>()
        val pipeline = recordingPipeline(server, captured)

        // 1. One message, three thoughts — one half-finished, the tangents
        //    anchored to the main task. Mixed English (the drink context),
        //    Egyptian Arabic in Arabic script, and Franco-Arabic.
        val threeThoughts =
            "Green tea smells pleasant. بابا راح يشترى نضارة. ana kent bas faaker fel safar ..."
        pipeline.onUserInput(threeThoughts)

        val turn1Threads = tracker.openThreads()
        assertEquals("three tracked thread objects from three thoughts", 3, turn1Threads.size)
        assertEquals(
            "every thought becomes a thread object with its own completeness",
            listOf(
                ThreadObjects.Completeness.FINISHED,
                ThreadObjects.Completeness.FINISHED,
                ThreadObjects.Completeness.TRAILING_OFF
            ),
            turn1Threads.map { it.completeness }
        )
        val mainAnchor = tracker.mainAnchorId(1L)
        assertNotNull("the first thought is the main thread", mainAnchor)
        assertEquals("thread-1-0", mainAnchor)
        assertTrue("the main thread is its own anchor",
            turn1Threads.first().mainAnchorId == mainAnchor)
        assertTrue("every tangent carries the main thread as its anchor",
            turn1Threads.drop(1).all { it.mainAnchorId == mainAnchor })

        // 2. The reply acknowledges every open thread — one clause each, and the
        //    half-finished thought is marked open/held, never auto-completed.
        val reply1 = captured.last()
        assertTrue("the reply carries an OpenThreads section",
            reply1.contains("\n\n[Open threads]:"))
        assertTrue("the main thought is noted",
            reply1.contains("- noted: Green tea smells pleasant"))
        assertTrue("the Egyptian Arabic thought is noted",
            reply1.contains("- noted: بابا راح يشترى نضارة"))
        assertTrue("the Franco-Arabic thought is noted, exactly as said",
            reply1.contains("- noted open: ana kent bas faaker fel safar"))
        assertTrue("the half-finished thought is held open, not completed",
            reply1.contains("(held open, not completed)"))

        // 3. A related follow-up resurfaces the half-finished thought (decay
        //    window open, topic gate passed — Franco-Arabic token "safar").
        pipeline.onUserInput("El safar gamed awy")
        val reply2 = captured.last()
        assertTrue("a related turn resurfaces the held-open thought",
            reply2.contains("returning: ana kent bas faaker fel safar"))
        assertTrue("the resurfaced thread is flagged with its origin turn",
            reply2.contains("(open from turn 1)"))
        assertTrue("the related turn's own thought is acknowledged",
            reply2.contains("- noted: El safar gamed awy"))

        // 4. The user resolves the open thought themselves ("Zabatna el safar") —
        //    close_detect closes it; nothing resurfaces any more.
        pipeline.onUserInput("Zabatna el safar")
        val resolved = tracker.thread("thread-1-2")
        assertNotNull(resolved)
        assertTrue("the user-settled thread is closed", resolved!!.closed)
        assertEquals(3L, resolved.resolvedAtTurn)
        val reply3 = captured.last()
        assertFalse("no thread resurfaces after its resolution",
            reply3.contains("returning:"))
        assertTrue("the resolving turn's own thought is acknowledged",
            reply3.contains("- noted: Zabatna el safar"))
    }

    @Test
    fun `disabled tracker - the three thought outcomes are absent through the real production path`(): Unit =
        runBlocking {
            val tracker = ThreadTracker(minimumHoldMs = 0).apply { setEnabled(false) }
            val server = serverWithTracker(tracker)
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)

            val threeThoughts =
                "Green tea smells pleasant. بابا راح يشترى نضارة. ana kent bas faaker fel safar ..."
            pipeline.onUserInput(threeThoughts)

            // Negative of three_thoughts_test's first assertion: no threads born.
            assertTrue("while disabled no thread objects are born", tracker.openThreads().isEmpty())
            assertFalse("while disabled the thread section never reaches the reply",
                captured.last().contains("[Open threads]"))

            pipeline.onUserInput("El safar gamed awy")
            assertFalse("while disabled nothing resurfaces",
                captured.last().contains("returning:"))
            assertTrue(tracker.openThreads().isEmpty())
        }
}