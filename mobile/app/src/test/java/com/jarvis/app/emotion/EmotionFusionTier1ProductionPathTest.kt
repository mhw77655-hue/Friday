package com.jarvis.app.emotion

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.capability.CapabilityRegistry
import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.identity.IdentityContext
import com.jarvis.app.identity.IdentitySource
import com.jarvis.app.identity.MentalStateHypothesis
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EMOTIONAL-INTELLIGENCE-FUSION-LAYER-TIER-1 — AC3 proof.
 *
 * Pins that a real turn's text produces a REAL, evidence-backed
 * [EmotionHypothesis] through the EXACT production call path
 * (`CognitiveEngine.process` -> `CognitiveTurnResult.assembledMentalState`),
 * not a stub or a constant:
 *
 *   JarvisEngine.init                                     (the wiring this mirrors)
 *     -> FusionLayerTier1.estimate(text)                  (Tier-1 emotion reading)
 *     -> MentalStateHypothesis.fromEmotion                (fold into the existing
 *        UserMentalStateEstimator provider seam)
 *     -> IdentityContext.mentalStateEstimator             (the SAME instance the
 *        CognitiveEngine reads at CognitiveEngine.kt:139)
 *     -> ContextWindowAssembler window.mentalState
 *     -> CognitiveTurnResult.assembledMentalState
 *
 * The harness swaps only the Android-bound backing stores for their deterministic
 * in-memory twins (the established phase-A JVM technique); the wiring shape is
 * byte-for-byte the identity composition JarvisEngine.init constructs.
 */
class EmotionFusionTier1ProductionPathTest {

    private class EmptyMemoryStore : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }

    private class MutableIdentitySource(
        var name: String? = null,
        var version: Int? = null
    ) : IdentitySource {
        override fun name(): String? = name
        override fun version(): Int? = version
    }

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

        // The Tier-1 emotion fusion layer + the SAME fold into the REAL
        // UserMentalStateEstimator seam that JarvisEngine.init wires.
        val emotionTier1 = FusionLayerTier1()
        val mentalStateEstimator = UserMentalStateEstimator(
            hypothesisProvider = { text ->
                MentalStateHypothesis.fromEmotion(emotionTier1.estimate(text))
            }
        )

        val worldModel = WorldModelService(graphStore)
        val userProfile = UserProfile(worldModel)
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

        val engine = CognitiveEngine(
            scope = CoroutineScope(Dispatchers.IO),
            memoryStore = EmptyMemoryStore(),
            humanCore = HumanCore,
            blendedRetriever = retriever,
            graphStore = graphStore,
            identityContext = identityContext
        )

        fun processTurn(text: String): CognitiveEngine.CognitiveTurnResult =
            runBlocking {
                engine.process(text, null, sendBlock = { })
            }
    }

    @Test
    fun `a real frustrated turn produces a real emotion hypothesis through the production call path`(): Unit =
        runBlocking {
            val harness = ProductionHarness()
            val turnText = "remind me what I love, I am so frustrated with the weather"

            val result = harness.processTurn(turnText)
            val assembled = result.assembledMentalState

            assertNotNull("assembled mental state must be present via the real seam", assembled)
            val emotion = assembled!!.emotion
            assertNotEquals(
                "the emotion reading must be real, not the empty-neutral stub",
                EmotionHypothesis.neutral(),
                emotion
            )
            assertTrue("valence must be derived from the frustration signal", emotion.valence < 0.0)
            assertTrue("tension must be elevated for a frustrated turn", emotion.tension > 0.2)
            assertTrue("confidence must be anchored and honest", emotion.confidence > 0.0 && emotion.confidence <= 1.0)
            assertEquals("frustrated", emotion.likelyState)
            assertTrue("evidence must be recorded", emotion.evidence.isNotEmpty())
            assertTrue(
                "evidence must name the exact matched signal",
                emotion.evidence.any { it.contains("frustrat") }
            )
            assertEquals(
                "the emotion reading must be exactly the fusion-tier reading folded through the estimator",
                harness.emotionTier1.estimate(turnText),
                emotion
            )
        }

    @Test
    fun `a real positive turn reads positive on the production call path`(): Unit = runBlocking {
        val harness = ProductionHarness()
        val turnText = "I love the color blue the most"

        val result = harness.processTurn(turnText)
        val emotion = result.assembledMentalState!!.emotion

        assertTrue("valence must be positive for a pleasant turn", emotion.valence > 0.0)
        assertEquals("pleasant", emotion.likelyState)
        assertTrue("confidence must be anchored", emotion.confidence > 0.0)
        assertEquals(
            "assembled mental state must equal the estimator's hypothesis including the emotion field",
            harness.mentalStateEstimator.estimateForTurn(turnText),
            result.assembledMentalState
        )
    }

    @Test
    fun `a neutral turn keeps the empty-neutral emotion reading the estimator itself produces`(): Unit =
        runBlocking {
            val harness = ProductionHarness()
            val turnText = "hello there friend"

            val result = harness.processTurn(turnText)
            assertEquals(
                "no signal -> the estimator's neutral fold, same as the estimator outputs",
                harness.mentalStateEstimator.estimateForTurn(turnText),
                result.assembledMentalState
            )
            assertEquals(EmotionHypothesis.neutral(), result.assembledMentalState!!.emotion)
        }
}