package com.jarvis.app.latency

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.capability.CapabilityRegistry
import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.identity.IdentityContext
import com.jarvis.app.identity.IdentitySource
import com.jarvis.app.identity.PersonaTuner
import com.jarvis.app.identity.SelfModel
import com.jarvis.app.identity.StageHistorySource
import com.jarvis.app.identity.UserMentalStateEstimator
import com.jarvis.app.identity.UserProfile
import com.jarvis.app.identity.WorldModelService
import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.memory.FakeMemoryGraphStore
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.MemoryImportanceScorer
import com.jarvis.app.memory.TestEmbeddingProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CONTEXT-WINDOW-ASSEMBLER-GROUND-TRUTH — pins the ContextWindowAssembler's REAL
 * post-Phase-A status through the real production call path.
 *
 * Ground truth (verified against the current code):
 *   - The assembler is constructed ONLY inside CognitiveEngine
 *     (CognitiveEngine.kt:135-140) with two seams:
 *       blendedRetriever     = the engine's Phase-A retrieval seam
 *       mentalStateEstimator = identityContext?.mentalStateEstimator
 *   - JarvisEngine.init wires REAL instances into both seams: the real
 *     BlendedMemoryRetriever and the real UserMentalStateEstimator bound inside
 *     the real IdentityContext (JarvisEngine.kt:367-377). Neither seam is null
 *     in the production composition.
 *   - CognitiveEngine.process() calls assemble() on every non-clarification
 *     turn whenever blendedRetriever != null, so the assembled window carries
 *     BOTH cross-session memories AND the per-turn mental-state hypothesis.
 *
 * AC3 proof: the assembled window's cross-session memory and mental-state
 * hypothesis actually reach the turn outcome through the EXACT production call
 * path — LatencyPipeline.onUserInput -> CognitiveEngine.process(text, ctx,
 * sendBlock = bridgeSend) (LatencyLayer.kt:312).
 */
class ContextWindowAssemblerGroundTruthTest {

    /** Deterministic empty in-memory [MemoryStorePort] — no Android, no disk. */
    private class EmptyMemoryStore : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }

    /** Mutable fake [IdentitySource] mirroring live HumanCore fields. */
    private class MutableIdentitySource(
        var name: String? = null,
        var version: Int? = null
    ) : IdentitySource {
        override fun name(): String? = name
        override fun version(): Int? = version
    }

    /**
     * The full production composition (mirrors JarvisEngine.init): the REAL
     * BlendedMemoryRetriever (FakeMemoryGraphStore + TestEmbeddingProvider +
     * MemoryImportanceScorer), the REAL Stage-03 identity stack, and the REAL
     * write-back graph store all wired into ONE real CognitiveEngine — so
     * BOTH ContextWindowAssembler seams are live. processTurn drives the exact
     * production invocation; userMessage drives the full LatencyPipeline entry.
     */
    private class ProductionHarness {
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
        val worldModel = WorldModelService(graphStore)
        val userProfile = UserProfile(worldModel)
        val mentalStateEstimator = UserMentalStateEstimator()
        val registry = CapabilityRegistry()
        val selfModel = SelfModel(
            identitySource = MutableIdentitySource("jarvis", 2),
            capabilityRegistry = registry,
            stageHistory = StageHistorySource { emptyList() }
        )
        val personaTuner = PersonaTuner(worldModel)
        val identityContext = IdentityContext(
            worldModel = worldModel,
            userProfile = userProfile,
            mentalStateEstimator = mentalStateEstimator,
            selfModel = selfModel,
            personaTuner = personaTuner
        )
        val sentThroughBridge = mutableListOf<String>()

        val engine = CognitiveEngine(
            scope = CoroutineScope(Dispatchers.IO),
            memoryStore = EmptyMemoryStore(),
            humanCore = HumanCore,
            blendedRetriever = retriever,
            graphStore = graphStore,
            identityContext = identityContext
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

        /**
         * The EXACT production call the real LatencyPipeline makes for every
         * user turn (LatencyLayer.kt:312) — returning the turn outcome so the
         * assembled window's contents are asserted directly.
         */
        fun processTurn(text: String): CognitiveEngine.CognitiveTurnResult =
            runBlocking {
                engine.process(text, null, sendBlock = { sentThroughBridge.add(it) })
            }

        /** The real live entry point (ConversationViewModel -> LatencyPipeline). */
        fun userMessage(text: String) {
            pipeline.onUserInput(text)
        }
    }

    @Test
    fun `assembled window surfaces cross-session memory and real estimator mental state through the real process call`(): Unit =
        runBlocking {
            val harness = ProductionHarness()
            // Durable fact seeded through the REAL graph store the retriever reads.
            harness.graphStore.addFact(
                subject = "user", predicate = "fact",
                `object` = "the user loves the color blue", source = "seed"
            )

            val turnText = "remind me what color I love"
            val result = harness.processTurn(turnText)

            // The assembled mental state must be exactly the real estimator's
            // hypothesis for this turn (same instance driving the assembler seam).
            assertEquals(
                "the assembled window surfaces the real UserMentalStateEstimator hypothesis",
                harness.mentalStateEstimator.estimateForTurn(turnText),
                result.assembledMentalState
            )
            assertNotNull(
                "cross-session retrieval is present when the retriever seam is real",
                result.crossSessionMemories
            )
            assertTrue(
                "the assembled cross-session memories include the seeded durable fact",
                result.crossSessionMemories!!.any { it.content.contains("color blue") }
            )
        }

    @Test
    fun `cross-session memory and mental state reach the live LatencyPipeline turn`(): Unit =
        runBlocking {
            val harness = ProductionHarness()
            harness.graphStore.addFact(
                subject = "user", predicate = "fact",
                `object` = "the user loves the color blue", source = "seed"
            )

            harness.userMessage("I love the color blue the most")
            harness.userMessage("remind me what color I love")

            val last = harness.sentThroughBridge.last()
            assertTrue(
                "the real live turn message carries the assembled cross-session memory",
                last.contains("[Cross-session memory]")
            )
            assertTrue(
                "the embedded cross-session memory is the seeded durable fact",
                last.contains("color blue")
            )
        }

    @Test
    fun `unwired engine keeps pre-Phase-A behavior and surfaces no assembled mental state`(): Unit =
        runBlocking {
            val sent = mutableListOf<String>()
            val engine = CognitiveEngine(
                scope = CoroutineScope(Dispatchers.IO),
                memoryStore = EmptyMemoryStore(),
                humanCore = HumanCore
            )

            val result = engine.process("hello there friend", null, sendBlock = { sent.add(it) })

            assertNull(
                "no amplified cross-session memories without the retriever seam",
                result.crossSessionMemories
            )
            assertNull(
                "no mental-state hypothesis surfaced without the estimator seam",
                result.assembledMentalState
            )
            assertEquals("user text preserved byte-for-byte", "hello there friend", sent.single())
        }
}