package com.jarvis.app.memory

import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.termux.TermuxJarvisServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/**
 * CORRECTION-CHAIN — a correction SUPERSEDES a memory, it never overwrites it,
 * and consolidation never inflates a fact's confidence.
 *
 * Every fixture is driven through the REAL conversation composition —
 * TermuxJarvisServer → its LatencyPipeline → CognitiveEngine.process — over the
 * real production graph store class ([TermuxJarvisServer.InMemoryGraph]) and the
 * real [ConsolidationDaemon]. The only thing a test swaps is the final bridge hop
 * (a recording `bridgeSend` that captures the assembled generation payload), so
 * "recall", "the assembled prompt" and "the stored chain" are all read off the
 * same live objects the app uses.
 *
 * The fixture utterances are deliberately free of the pronoun-referent
 * substrings (`it`/`that`/`this`) and of the vague-action verbs that
 * IntentInference flags, so every turn stays a low-doubt DIRECT_REPLY and the
 * live write-back under test actually runs.
 */
class CorrectionChainTest {

    private val servers = mutableListOf<TermuxJarvisServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private companion object {
        const val SUBJECT = "user"
        const val PREDICATE = "stated"

        const val FIRST = "the meeting is at 5"
        const val CORRECTION = "no, the meeting is at 6"
        const val ASSERTED = "the meeting is at 6"
        const val SUPERSEDED = "the meeting is at 5"

        const val LATER_TURN = "Green tea smells pleasant"

        const val FIRST_AR = "الاجتماع الساعة ٥"
        const val CORRECTION_AR = "لا الاجتماع الساعة ٦"
        const val ASSERTED_AR = "الاجتماع الساعة ٦"
        const val SUPERSEDED_AR = "الاجتماع الساعة ٥"

        /** Consolidation passes AC3 runs over the corrected fact. */
        const val PASSES = 10

        /**
         * The naive confidence model AC5 forbids: every reinforcement pass makes
         * the fact MORE certain, i.e. its uncertainty is multiplied down.
         */
        const val NAIVE_CONFIDENCE_DECAY = 0.8f

        /**
         * AC3's predicate, factored out so the real daemon and the naive scorer
         * in AC5 are judged by the SAME question: is the stored uncertainty
         * signal byte-identical after the consolidation passes?
         */
        fun uncertaintyHeld(before: Float?, after: Float?): Boolean =
            before != null && after != null && before.toRawBits() == after.toRawBits()
    }

    // ── real-path harness ─────────────────────────────────────────────────────

    private fun newServer(graph: MemoryGraphStore): TermuxJarvisServer =
        TermuxJarvisServer(
            port = 0,
            backendOverride = FakeModelBackend(),
            graphStoreOverride = graph
        ).also { servers.add(it) }

    /**
     * The real pipeline with the model bridge replaced by a recorder — the same
     * single swapped hop the production-wiring tests use. `dispatch` runs the
     * turn inline, so `onUserInput` has fully completed when it returns.
     */
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

    /** A capture list that is safe to read while a turn appends to it. */
    private fun captures(): MutableList<String> = Collections.synchronizedList(mutableListOf<String>())

    private fun snapshot(captured: MutableList<String>): List<String> =
        synchronized(captured) { captured.toList() }

    /** The single live ("user","stated") node — i.e. what a reader sees NOW. */
    private fun currentAssertion(graph: MemoryGraphStore): MemoryNode =
        graph.query(subject = SUBJECT, predicate = PREDICATE).single()

    // ── the correction lexicon the write-back reads ───────────────────────────

    /**
     * The lexicon is the only thing that decides whether a turn is stored as a
     * correction, so it is pinned directly: a correction yields the bare
     * ASSERTION (the marker is not part of the claim any reader can interpret),
     * while every ordinary statement — including one that merely CONTAINS a
     * marker word, and every hedge about the current turn — is stored verbatim.
     * Without this, a marker-stripping bug would silently mangle unrelated facts.
     */
    @Test
    fun `correction lexicon strips only a leading marker and never a hedge`() {
        // Corrections: English and Egyptian Arabic markers, both directions of
        // the same act, each yielding the bare assertion.
        assertEquals("the meeting is at 6", MemoryCorrection.parse(CORRECTION)!!.assertion)
        assertEquals("no", MemoryCorrection.parse(CORRECTION)!!.marker)
        assertTrue("an English correction is recognised", MemoryCorrection.isCorrection(CORRECTION))
        assertEquals(
            "actually",
            MemoryCorrection.parse("actually the train leaves at 4")!!.marker
        )
        assertEquals(
            "the train leaves at 4",
            MemoryCorrection.parse("actually the train leaves at 4")!!.assertion
        )
        assertEquals("الاجتماع الساعة ٦", MemoryCorrection.parse(CORRECTION_AR)!!.assertion)
        assertEquals("لا", MemoryCorrection.parse(CORRECTION_AR)!!.marker)
        assertTrue("an Arabic correction is recognised", MemoryCorrection.isCorrection(CORRECTION_AR))
        assertEquals(
            "القطار الساعة ٤",
            MemoryCorrection.parse("بدل القطار الساعة ٤")!!.assertion
        )

        // Ordinary statements are stored verbatim — a correction never rewrites a
        // fact the user did not correct.
        assertNull("a plain statement is not a correction", MemoryCorrection.parse(FIRST))
        assertNull(
            "a marker word inside a statement is not a correction",
            MemoryCorrection.parse("the train does not leave at 4")
        )
        assertNull(
            "a correction word after the first word is not a correction",
            MemoryCorrection.parse("my answer is wrong, the train leaves at 4")
        )

        // Hedges about the CURRENT turn are not retractions of a stored one, and
        // a bare marker leaves nothing to assert.
        assertNull("a hedge is not a correction", MemoryCorrection.parse("not sure the train leaves at 4"))
        assertNull("a hedge is not a correction", MemoryCorrection.parse("no idea where we meet"))
        assertNull("a hedge is not a correction", MemoryCorrection.parse("not certain about the hour"))
        assertNull("a bare marker is not a correction", MemoryCorrection.parse("no"))
        assertNull("empty input is not a correction", MemoryCorrection.parse("   "))
    }

    // ── AC1 + AC2 ─────────────────────────────────────────────────────────────

    @Test
    fun `AC1 AC2 a correction supersedes the old assertion on the real conversation path`(): Unit =
        runBlocking {
            val graph = TermuxJarvisServer.InMemoryGraph()
            val server = newServer(graph)
            val captured = captures()
            val pipeline = recordingPipeline(server, captured)

            // The fact as first stated, then the user's correction — both turns
            // real, through the real engine write-back.
            pipeline.onUserInput(FIRST)
            pipeline.onUserInput(CORRECTION)

            // ── AC1: a CHAIN, not an overwrite ────────────────────────────────
            val history = graph.getHistory(SUBJECT, PREDICATE)
            assertEquals("both assertions survive — nothing was overwritten", 2, history.size)
            val superseded = history[0]
            val corrected = history[1]
            assertEquals("the chain is ordered oldest first", SUPERSEDED, superseded.`object`)
            assertEquals("the chain ends on the corrected assertion", ASSERTED, corrected.`object`)
            assertNotNull("the old assertion's validity window is closed", superseded.validUntil)
            assertEquals(
                "the old assertion is marked superseded with a pointer to the new one",
                corrected.id,
                superseded.supersededBy
            )
            assertNull("the current assertion supersedes nothing", corrected.supersededBy)
            assertNull("the current assertion is still valid", corrected.validUntil)
            assertEquals("the correction is tagged as a correction", "live-correction", corrected.source)
            assertTrue(
                "forgetting/correcting never deletes a row",
                graph.nodeCount() >= 2L
            )

            // ── AC1: recall returns the correction ────────────────────────────
            assertEquals(ASSERTED, currentAssertion(graph).`object`)
            val recalled = server.retriever.retrieve("when is the meeting", now = System.currentTimeMillis())
            assertTrue(
                "the real retriever returns the corrected assertion",
                recalled.any { it.node.`object` == ASSERTED }
            )
            assertTrue(
                "the superseded assertion is never presented as current",
                recalled.none { it.node.`object` == SUPERSEDED }
            )

            // ── AC2: the assembled prompt of a LATER turn ──────────────────────
            val before = snapshot(captured).size
            pipeline.onUserInput(LATER_TURN)
            val newPayloads = snapshot(captured).drop(before)
            assertEquals("the later turn produced one generation payload", 1, newPayloads.size)
            val payload = newPayloads.single()
            assertTrue(
                "the corrected fact reached the later turn through the galaxy seam",
                payload.contains("[Cross-session memory]") && payload.contains(ASSERTED)
            )
            assertFalse(
                "the superseded assertion never reaches a prompt",
                payload.contains(SUPERSEDED)
            )
        }

    // ── AC4 ───────────────────────────────────────────────────────────────────

    @Test
    fun `AC4 an egyptian arabic correction produces the same chain behaviour`(): Unit =
        runBlocking {
            val graph = TermuxJarvisServer.InMemoryGraph()
            val server = newServer(graph)
            val pipeline = recordingPipeline(server, captures())

            pipeline.onUserInput(FIRST_AR)
            pipeline.onUserInput(CORRECTION_AR)

            val history = graph.getHistory(SUBJECT, PREDICATE)
            assertEquals("the Arabic correction chains exactly like the English one", 2, history.size)
            assertEquals(SUPERSEDED_AR, history[0].`object`)
            assertEquals(ASSERTED_AR, history[1].`object`)
            assertNotNull(history[0].validUntil)
            assertEquals(history[1].id, history[0].supersededBy)
            assertNull(history[1].supersededBy)
            assertEquals("live-correction", history[1].source)
            assertFalse(
                "the Arabic correction marker is not stored as part of the claim",
                history[1].`object`.startsWith("لا")
            )

            val live = currentAssertion(graph)
            assertEquals(ASSERTED_AR, live.`object`)
            val recalled = server.retriever.retrieve("الاجتماع", now = System.currentTimeMillis())
            assertTrue("recall returns the corrected Arabic assertion", recalled.any { it.node.`object` == ASSERTED_AR })
            assertTrue("recall never returns the superseded Arabic assertion", recalled.none { it.node.`object` == SUPERSEDED_AR })
        }

    // ── AC3 ───────────────────────────────────────────────────────────────────

    @Test
    fun `AC3 ten consolidation passes move accessibility only and never a stored signal`(): Unit =
        runBlocking {
            val graph = TermuxJarvisServer.InMemoryGraph()
            val server = newServer(graph)
            val pipeline = recordingPipeline(server, captures())

            // The corrected fact, stored through the real path.
            pipeline.onUserInput(FIRST)
            pipeline.onUserInput(CORRECTION)

            val stored = currentAssertion(graph)
            val uncertaintyBefore = stored.uncertainty
            val accessibilityBefore = stored.accessibility
            assertNotNull(
                "the live write-back recorded the six split signals on the stored node",
                uncertaintyBefore
            )
            assertEquals(
                "a freshly stored fact starts at the accessibility floor",
                MemoryAccessibility.initial(),
                accessibilityBefore!!,
                0f
            )

            // The REAL consolidation daemon over the SAME graph, with an empty
            // episodic queue: every pass is pure reinforcement.
            val daemon = ConsolidationDaemon(
                graphStore = graph,
                scorer = MemoryImportanceScorer(embeddingProvider = TestEmbeddingProvider(dimension = 256)),
                episodicStore = mutableListOf()
            )

            var previous: Float = MemoryAccessibility.initial()
            repeat(PASSES) { pass ->
                val result = daemon.consolidate(now = 4242L + pass)
                assertTrue(
                    "pass ${pass + 1} reported reinforcing accessibility",
                    result.accessibilityReinforced >= 1
                )
                val now = currentAssertion(graph).accessibility!!
                assertTrue("pass ${pass + 1} raised accessibility", now > previous)
                previous = now
            }

            val after = currentAssertion(graph)
            // The AC3 core: ten passes later the stored uncertainty is the SAME
            // BYTES, not merely a close float.
            assertTrue(
                "ten consolidation passes left the stored uncertainty signal byte-identical",
                uncertaintyHeld(uncertaintyBefore, after.uncertainty)
            )
            // ... while accessibility moved by the computed amount: PASSES * STEP.
            assertTrue(
                "accessibility is the only axis consolidation moved",
                after.accessibility != accessibilityBefore
            )
            assertEquals(
                "accessibility moved by the computed amount (PASSES * STEP)",
                MemoryAccessibility.STEP * PASSES,
                after.accessibility!!,
                1e-6f
            )
            // A superseded assertion is never reinforced.
            assertEquals(
                "the superseded assertion keeps the floor it was stored with",
                MemoryAccessibility.initial(),
                graph.getHistory(SUBJECT, PREDICATE).first().accessibility!!,
                0f
            )
        }

    // ── AC5: negative control ─────────────────────────────────────────────────

    @Test
    fun `AC5 a consolidation that raises confidence fails AC3 on the same fixture`(): Unit =
        runBlocking {
            // The IDENTICAL real fixture: the same two turns, the same graph.
            val graph = TermuxJarvisServer.InMemoryGraph()
            val server = newServer(graph)
            val pipeline = recordingPipeline(server, captures())
            pipeline.onUserInput(FIRST)
            pipeline.onUserInput(CORRECTION)

            val before = currentAssertion(graph).uncertainty!!

            // The naive model, run through the SAME seam the real store-time
            // scorer uses: every consolidation pass re-scores the fact and makes
            // it more certain. This is the behaviour AC3 forbids.
            repeat(PASSES) {
                val node = currentAssertion(graph)
                graph.recordSignals(
                    node.id,
                    SignalProfile(
                        relevance = node.relevance ?: 0f,
                        importance = node.importance ?: 0f,
                        uncertainty = (node.uncertainty ?: 0f) * NAIVE_CONFIDENCE_DECAY,
                        novelty = node.novelty ?: 0f,
                        consent = node.consent ?: 0f,
                        cost = node.cost ?: 0f
                    )
                )
            }

            val after = currentAssertion(graph).uncertainty!!
            assertTrue(
                "the naive model really did raise confidence",
                after < before
            )
            assertFalse(
                "a confidence-raising consolidation FAILS AC3 on the same fixture",
                uncertaintyHeld(before, after)
            )
        }
}
