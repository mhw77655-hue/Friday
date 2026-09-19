package com.jarvis.app.resolution

import com.jarvis.app.capability.CapabilityRegistry
import com.jarvis.app.capability.CapabilityRouter
import com.jarvis.app.capability.DeterministicCapabilityRouter
import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.memory.FakeMemoryGraphStore
import com.jarvis.app.memory.MemoryImportanceScorer
import com.jarvis.app.memory.RankedMemory
import com.jarvis.app.memory.TestEmbeddingProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * FUZZY-COMMAND-RESOLUTION fixtures.
 *
 * The retrieval engine is the REAL production [BlendedMemoryRetriever]
 * (binary-quantized Hamming seeding + weighted graph traversal + salience
 * ranking). It is backed by the JVM reference store/embedder
 * ([FakeMemoryGraphStore], [TestEmbeddingProvider]) because
 * android.database.sqlite + llama-server cannot launch inside unit tests on
 * this host; in the APK the same contracts are served by
 * AndroidMemoryGraphStore + NeuralEmbeddingProvider. The routing stack is the
 * REAL [DeterministicCapabilityRouter] over a real [CapabilityRegistry].
 */
class FuzzyCommandResolverTest {

    private lateinit var graphStore: FakeMemoryGraphStore
    private lateinit var registry: CapabilityRegistry
    private lateinit var index: CapabilityMemoryIndex
    private lateinit var resolver: FuzzyCommandResolver

    @Before
    fun setUp() {
        graphStore = FakeMemoryGraphStore()
        val embedding = TestEmbeddingProvider()
        val scorer = MemoryImportanceScorer(embedding)
        val retriever = BlendedMemoryRetriever(graphStore, embedding, scorer)
        registry = CapabilityRegistry()
        index = CapabilityMemoryIndex(graphStore)
        resolver = FuzzyCommandResolver(retriever, DeterministicCapabilityRouter(registry), registry)
    }

    private fun register(
        id: String,
        name: String,
        category: CapabilityRegistry.Category,
        quality: CapabilityRegistry.QualityTier = CapabilityRegistry.QualityTier.HIGH,
        health: CapabilityRegistry.Health = CapabilityRegistry.Health.HEALTHY,
        confidence: Float = 1.0f
    ): CapabilityRegistry.Capability {
        val capability = CapabilityRegistry.Capability(
            id = id,
            version = "1.0",
            name = name,
            function = "runs $name",
            category = category,
            quality = quality,
            confidence = confidence,
            currentState = CapabilityRegistry.State.LOADED,
            health = health,
            languages = setOf("en"),
            ramEstimateMb = 128,
            latencyMs = 100
        )
        registry.register(capability)
        return capability
    }

    private fun indexVoice(): CapabilityRegistry.Capability {
        val voice = register("voice_organism_v1", "Voice organism", CapabilityRegistry.Category.TTS)
        index.indexCapability(
            voice,
            "reads the screen and messages aloud in a polished clear speaking voice"
        )
        return voice
    }

    private fun indexMedia(): CapabilityRegistry.Capability {
        val media = register("media_continuity_v1", "Media continuity", CapabilityRegistry.Category.SYSTEM)
        index.indexCapability(
            media,
            "resumes the music track or playlist that was playing"
        )
        return media
    }

    // ── AC2: candidates come from REAL Galaxy Memory, not string matching ──

    @Test
    fun `resolves an underspecified voice reference from galaxy memory without requiring the exact name`() {
        indexVoice()
        indexMedia()

        runBlocking<Unit> {
            val reference = "the one that goes like a clear spoken voice reading aloud"
            val outcome = resolver.resolve(reference)

            assertTrue("expected Resolved but was $outcome", outcome is FuzzyCommandResolver.ResolveOutcome.Resolved)
            val resolved = outcome as FuzzyCommandResolver.ResolveOutcome.Resolved

            assertEquals("voice_organism_v1", resolved.selected.capability.id)
            assertEquals("voice_organism_v1", resolved.selected.rankedMemory.node.subject)
            assertEquals(RankedMemory.MatchSource.HAMMING_SEED, resolved.selected.rankedMemory.source)

            // Not found by exact name or title: neither the id nor the name is
            // present in the reference, yet it still resolves.
            assertFalse(reference.lowercase().contains("voice_organism_v1"))
            assertFalse(reference.lowercase().contains("voice organism"))

            // The candidate was handed to the REAL router and legitimately matched.
            val routed = resolved.selected.routed
            assertTrue("expected Matched but was $routed", routed is CapabilityRouter.RouteResult.Matched)
            val matched = routed as CapabilityRouter.RouteResult.Matched
            assertEquals(CapabilityRouter.MatchTactic.EXACT_CATEGORY, matched.matchedBy)
            assertSame("must be the very object registered in the real registry", registry.get("voice_organism_v1"), matched.capability)
        }
    }

    @Test
    fun `returned candidates are ranked by the real retriever - best description match first`() {
        indexMedia()
        indexVoice()

        runBlocking<Unit> {
            val outcome = resolver.resolve("the best clear spoken voice")
            assertTrue(outcome is FuzzyCommandResolver.ResolveOutcome.Resolved)
            val resolved = outcome as FuzzyCommandResolver.ResolveOutcome.Resolved

            val ids = resolved.rankedCandidates.map { it.capability.id }
            assertEquals("voice description must outrank music for a voice query", "voice_organism_v1", ids.first())
            assertTrue("both indexed capabilities must surface as ranked candidates", ids.contains("media_continuity_v1"))

            val scores = resolved.rankedCandidates.map { it.rankedMemory.score }
            assertEquals("retriever order must be score-descending", scores.sortedDescending(), scores)
        }
    }

    @Test
    fun `non-capability memories are retrieved but never treated as a routeable candidate`() {
        indexVoice()
        // A strongly matching USER memory, not a capability declaration.
        graphStore.addFact(
            subject = "user",
            predicate = "stated",
            `object` = "we listened to the loud british jazz playlist from yesterday",
            source = "episodic"
        )

        runBlocking<Unit> {
            val outcome = resolver.resolve("the loud british jazz playlist from yesterday")
            assertTrue(outcome is FuzzyCommandResolver.ResolveOutcome.Resolved)
            val resolved = outcome as FuzzyCommandResolver.ResolveOutcome.Resolved

            // Only capability declarations are candidates; the user memory (which
            // textually matches better) is filtered out by predicate.
            assertEquals("voice_organism_v1", resolved.selected.capability.id)
            assertTrue(resolved.rankedCandidates.all { it.rankedMemory.node.predicate == FuzzyCommandResolver.CAPABILITY_PREDICATE })
            assertTrue(resolved.rankedCandidates.none { it.rankedMemory.node.subject == "user" })
        }
    }

    // ── AC3: resolved candidate is handed to the real CapabilityRouter ────

    @Test
    fun `every candidate is routed through the real capability router`() {
        val voice = indexVoice()
        val media = indexMedia()

        runBlocking<Unit> {
            val outcome = resolver.resolve("the polished clear voice that reads aloud")
            val resolved = outcome as FuzzyCommandResolver.ResolveOutcome.Resolved

            for (candidate in resolved.rankedCandidates) {
                val routed = candidate.routed
                if (routed is CapabilityRouter.RouteResult.Matched) {
                    assertSame("router matched against the real registry", registry.get(routed.capability.id), routed.capability)
                    assertEquals("router matched within the candidate's own category", candidate.capability.category, routed.capability.category)
                }
            }
            // The routed capability is exactly the registered, indexed capability.
            assertSame(voice, resolved.selected.capability)
            assertEquals(CapabilityRegistry.Category.TTS, resolved.selected.capability.category)
            // And the media capability was routed through the same real router.
            val routedMedia = resolved.rankedCandidates.first { it.capability.id == media.id }.routed
            assertTrue("media candidate routed as well", routedMedia is CapabilityRouter.RouteResult.Matched)
        }
    }

    // ── AC4: one mechanism across two+ domains, no per-domain logic ────────

    @Test
    fun `one resolver routes two different domains through the same mechanism`() {
        indexVoice()
        indexMedia()

        runBlocking<Unit> {
            val voiceOutcome = resolver.resolve("the clear speaking voice that reads papers to me")
            val mediaOutcome = resolver.resolve("resume the music tune that was playing")

            assertTrue(voiceOutcome is FuzzyCommandResolver.ResolveOutcome.Resolved)
            assertTrue(mediaOutcome is FuzzyCommandResolver.ResolveOutcome.Resolved)

            val voice = voiceOutcome as FuzzyCommandResolver.ResolveOutcome.Resolved
            val media = mediaOutcome as FuzzyCommandResolver.ResolveOutcome.Resolved

            assertEquals("voice domain", "voice_organism_v1", voice.selected.capability.id)
            assertEquals("media domain", "media_continuity_v1", media.selected.capability.id)
            assertEquals(CapabilityRegistry.Category.TTS, voice.selected.capability.category)
            assertEquals(CapabilityRegistry.Category.SYSTEM, media.selected.capability.category)
        }
    }

    // ── Unresolved cases ───────────────────────────────────────────────────

    @Test
    fun `no indexed capability means no candidates`() {
        runBlocking<Unit> {
            val outcome = resolver.resolve("that chat from yesterday")
            assertTrue(outcome is FuzzyCommandResolver.ResolveOutcome.Unresolved)
            val unresolved = outcome as FuzzyCommandResolver.ResolveOutcome.Unresolved
            assertEquals(FuzzyCommandResolver.UnresolvedReason.NO_CANDIDATES, unresolved.reason)
            assertTrue(unresolved.rankedCandidates.isEmpty())
        }
    }

    @Test
    fun `a routed capability that fails the router is not silently swapped in`() {
        // Capability is indexed in Galaxy Memory but unhealthy in the registry —
        // the real router refuses it.
        val ghost = register("ghost_cap_v1", "Ghost", CapabilityRegistry.Category.SYSTEM,
            health = CapabilityRegistry.Health.FAILED)
        index.indexCapability(ghost, "the hidden helper that never answers")

        runBlocking<Unit> {
            val outcome = resolver.resolve("the hidden helper")
            assertTrue(outcome is FuzzyCommandResolver.ResolveOutcome.Unresolved)
            val unresolved = outcome as FuzzyCommandResolver.ResolveOutcome.Unresolved
            assertEquals(FuzzyCommandResolver.UnresolvedReason.NO_ROUTABLE_MATCH, unresolved.reason)
            assertEquals(listOf("ghost_cap_v1"), unresolved.rankedCandidates.map { it.capability.id })
        }
    }

    @Test
    fun `router ambiguity on the top candidate stays unresolved and is never guessed`() {
        // Two same-category capabilities with identical confidence x quality make
        // the REAL router report AMBIGUOUS — the resolver must not silently pick.
        val a = register("llm_candidate_a", "LLM candidate A", CapabilityRegistry.Category.LLM,
            quality = CapabilityRegistry.QualityTier.BEST, confidence = 1.0f)
        val b = register("llm_candidate_b", "LLM candidate B", CapabilityRegistry.Category.LLM,
            quality = CapabilityRegistry.QualityTier.BEST, confidence = 1.0f)
        index.indexCapability(a, "a large language model for reasoning over documents")
        index.indexCapability(b, "a large language model for summarising documents")

        runBlocking<Unit> {
            val outcome = resolver.resolve("a large language model")
            assertTrue(outcome is FuzzyCommandResolver.ResolveOutcome.Unresolved)
            val unresolved = outcome as FuzzyCommandResolver.ResolveOutcome.Unresolved
            assertEquals(FuzzyCommandResolver.UnresolvedReason.ROUTER_AMBIGUOUS, unresolved.reason)
            assertEquals(2, unresolved.rankedCandidates.size)
        }
    }
}