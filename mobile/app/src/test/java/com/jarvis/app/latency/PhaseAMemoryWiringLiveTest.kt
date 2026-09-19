package com.jarvis.app.latency

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.cognitive.UserModel
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.memory.FakeMemoryGraphStore
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.MemoryImportanceScorer
import com.jarvis.app.memory.TestEmbeddingProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE-A-MEMORY-WIRING-AUDIT — proves Galaxy Memory (MemoryGraphStore +
 * BlendedMemoryRetriever) is genuinely wired into the REAL live conversation
 * turn, not just correct in isolation.
 *
 * The live entry point a user message travels is:
 *
 *   ConversationViewModel.send() -> LatencyLayer.onUserInput()
 *     -> LatencyPipeline.onUserInput() -> CognitiveEngine.process()
 *
 * (LatencyPipeline.onUserInput is the pure-JVM live turn entry point; this
 * test drives it with dispatch={it()} so each onUserInput fully synchronously
 * drives one real turn, matching Stage04LiveWiringTest.)
 *
 * Walking the REAL current code (pre-fix):
 *   - CognitiveEngine.processInput retrieves turn context from
 *     MemoryStorePort (body/MemoryStore) — the BODY/store memory, NOT Galaxy
 *     Memory.
 *   - CognitiveEngine constructed ContextWindowAssembler(topicTracker,
 *     salienceScorer) with NO BlendedMemoryRetriever (CognitiveEngine.kt:104),
 *     even though ContextWindowAssembler has an optional blendedRetriever seam
 *     (ContextWindowAssembler.kt:33).
 *   - JarvisEngine.init constructs CognitiveEngine (JarvisEngine.kt:186-192)
 *     with no MemoryGraphStore/AndroidMemoryGraphStore/BlendedMemoryRetriever.
 *   => GENUINELY MISSING: no Galaxy Memory retrieval fed live-turn context and
 *      no durable write-back to MemoryGraphStore existed from the live turn.
 *
 * This test proves, AFTER the fix, through the real entry point:
 *   - AC3: a durable fact the user states in one real turn is written back to
 *     MemoryGraphStore, and is retrievable by BlendedMemoryRetriever in a later
 *     real turn.
 *   - AC2: a stored memory fact actually changes the context/response of a
 *     later real turn — the later turn's sendBlock receives a message that
 *     includes the cross-session memory retrieved for it.
 */
class PhaseAMemoryWiringLiveTest {

    /** Deterministic empty in-memory [MemoryStorePort] — no Android, no disk. */
    private class EmptyMemoryStore : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }

    /**
     * Build the full live-turn stack with Galaxy Memory wired through the
     * engine's existing retrieval/write-back seams: real pipeline -> real
     * engine (with FakeMemoryGraphStore + BlendedMemoryRetriever) -> real
     * ContextWindowAssembler seam.
     */
    private class LiveHarness {
        val graphStore: MemoryGraphStore = FakeMemoryGraphStore()
        val provider = TestEmbeddingProvider(dimension = 256)
        val scorer = MemoryImportanceScorer(
            embeddingProvider = provider,
            salienceFloor = 0.1f,
            baseHalfLifeMs = 3_600_000L
        )
        val retriever = BlendedMemoryRetriever(
            graphStore = graphStore,
            embeddingProvider = provider,
            scorer = scorer,
            seedCount = 4,
            maxHops = 3
        )
        val sentThroughBridge = mutableListOf<String>()

        val engine = CognitiveEngine(
            scope = CoroutineScope(Dispatchers.IO),
            memoryStore = EmptyMemoryStore(),
            humanCore = HumanCore,
            blendedRetriever = retriever,
            graphStore = graphStore
        )

        val pipeline = LatencyPipeline(
            dispatch = { it() },
            scheduleDelayed = { _, _ -> },
            bridgeSend = { sentThroughBridge.add(it) },
            bridgeStatus = { "idle" },
            beginExchange = { _, _ -> null },
            express = { reply, _ -> com.jarvis.app.humancore.protocol.StyledResponse.Approved(reply) },
            completeExchange = { _, _, _, _, _ -> },
            sessionContext = { null },
            cognitiveEngine = engine
        )

        init {
            engine.updateUserModel(UserModel(knownName = "Alice"))
        }

        fun userMessage(text: String) {
            pipeline.onUserInput(text)
        }
    }

    @Test
    fun `durable fact stated in one real turn is written back and changes a later real turn's context`(): Unit =
        runBlocking {
            val harness = LiveHarness()
            val graphStore = harness.graphStore

            // -----------------------------------------------------------------
            // Turn 1: the user states a durable fact through the real entry
            // point. The engine writes it back to MemoryGraphStore.
            // -----------------------------------------------------------------
            val stated = "I use Python and FastAPI for all my projects"
            harness.userMessage(stated)

            // Write-back fired through the real live path (AC3 first half):
            // the stated turn produced a durable fact in the graph store.
            val written = graphStore.query(subject = "user", predicate = "stated")
            assertEquals(1, written.size)
            assertTrue(
                "durable fact written back from the live turn must be the stated text",
                written[0].`object`.contains("Python")
            )

            // -----------------------------------------------------------------
            // Turn 2: a later real turn references the same subject. Because
            // the retriever is wired and the write-back path exists, the earlier
            // stated fact is now retrievable by BlendedMemoryRetriever.
            // -----------------------------------------------------------------
            val query = "How do I share my Python work?"
            val retrieved = harness.retriever.retrieve(query).map { it.node.`object` }
            assertTrue(
                "fact stated in a prior real turn is retrievable by BlendedMemoryRetriever (AC3)",
                retrieved.any { it.contains("Python") }
            )

            // The engine's cross-session memory context for this later turn
            // should reflect the stored fact (via the real ContextWindowAssembler
            // seam), and that context must actually be fed into the direct-reply
            // generation (sendBlock) so a stored fact changes a later turn's
            // response (AC2).
            val bridgeBefore = harness.sentThroughBridge.size
            harness.userMessage(query)

            // sendBlock saw the direct-reply for turn 2 carrying the retrieved
            // cross-session memory context appended to the raw user text.
            val lastBridge = harness.sentThroughBridge.last()
            assertTrue("sendBlock fired for the second real turn", harness.sentThroughBridge.size == bridgeBefore + 1)
            assertTrue(
                "stored memory fact changes a later real turn's context: the sent message embeds the retrieved fact",
                lastBridge.contains("[Cross-session memory]")
            )
            assertTrue(
                "the embedded cross-session memory is the stored fact from the prior turn",
                lastBridge.contains("Python")
            )
            assertFalse(
                "the user's raw text is preserved at the head of the sent message",
                lastBridge.startsWith("[Cross-session memory]")
            )
        }

    @Test
    fun `live path is unchanged when Galaxy Memory is not wired`(): Unit =
        runBlocking {
            // With no retriever/store wired, the engine's live-path behavior is
            // byte-for-byte the pre-Phase-A default: sendBlock receives exactly
            // the user text, nothing appended.
            val sent = mutableListOf<String>()
            val engine = CognitiveEngine(
                scope = CoroutineScope(Dispatchers.IO),
                memoryStore = EmptyMemoryStore(),
                humanCore = HumanCore
            )
            val pipeline = LatencyPipeline(
                dispatch = { it() },
                scheduleDelayed = { _, _ -> },
                bridgeSend = { sent.add(it) },
                bridgeStatus = { "idle" },
                beginExchange = { _, _ -> null },
                express = { reply, _ -> com.jarvis.app.humancore.protocol.StyledResponse.Approved(reply) },
                completeExchange = { _, _, _, _, _ -> },
                sessionContext = { null },
                cognitiveEngine = engine
            )
            engine.updateUserModel(UserModel(knownName = "Alice"))

            pipeline.onUserInput("Hello there friend")
            assertEquals(1, sent.size)
            assertEquals("Hello there friend", sent[0])
            assertTrue("no cross-session memory context when Galaxy Memory is unwired", !sent[0].contains("[Cross-session memory]"))
        }
}
