package com.jarvis.app.memory

import com.jarvis.app.cognitive.ContextWindowAssembler
import com.jarvis.app.cognitive.IntentInference
import com.jarvis.app.cognitive.ReferenceStore
import com.jarvis.app.cognitive.SalienceScorer
import com.jarvis.app.cognitive.TopicTracker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Acceptance tests for BLENDED-RETRIEVAL (Stage 02 redesign, Galaxy Memory).
 *
 * Proves the retriever is a STAGED pipeline — seed (binary-quantized Hamming),
 * THEN weighted multi-hop graph traversal, THEN salience ranking — and NOT the
 * flat weighted-sum-of-three-signals blend that this story explicitly replaced.
 *
 *  1. A 2-hop-only-reachable fact is returned (impossible under a flat blend)
 *  2. A graph-connected, textually-unrelated fact appears via traversal
 *  3. A highly salient older memory outranks a trivial more-similar recent one
 *  4. The "described, not named" case resolves via seed-then-traverse
 *  5. ContextWindowAssembler includes cross-session results
 */
@RunWith(JUnit4::class)
class BlendedMemoryRetrieverTest {

    private val provider = TestEmbeddingProvider(dimension = 256)
    private val scorer = MemoryImportanceScorer(
        embeddingProvider = provider,
        salienceFloor = 0.1f,
        baseHalfLifeMs = 3_600_000L
    )

    private fun createRetriever(
        graphStore: FakeMemoryGraphStore,
        seedCount: Int = 4,
        maxHops: Int = 3
    ) = BlendedMemoryRetriever(
        graphStore = graphStore,
        embeddingProvider = provider,
        scorer = scorer,
        seedCount = seedCount,
        maxHops = maxHops
    )

    private fun addNode(
        store: FakeMemoryGraphStore,
        id: String,
        subject: String,
        predicate: String,
        `object`: String,
        salience: Float = 0.5f,
        validFrom: Long = 1000L
    ): MemoryNode {
        val node = MemoryNode(
            id = id, subject = subject, predicate = predicate,
            `object` = `object`, source = "test",
            validFrom = validFrom, salience = salience,
            embedding = provider.embed(`object`)
        )
        store.addFakeNode(node)
        return node
    }

    // ── AC1: 2-hop-only-reachable fact (staged traversal, not flat blend) ─

    @Test
    fun `2-hop reachable fact is returned even though far outside top-K Hamming and unreachable in a flat blend`() {
        val store = FakeMemoryGraphStore()
        // Seed S: textually matches the query.
        addNode(store, "S", "user", "prefers", "terse concise answers", salience = 0.5f)
        // Intermediate M: shares predicate "prefers" with S (weight 0.4).
        addNode(store, "M", "person", "prefers", "Alice", salience = 0.5f)
        // Target T: shares subject "person" with M (weight 0.6), but shares
        // NOTHING with S -> reachable only via 2 hops S->M->T. Its text is
        // unrelated to the query, so it is far outside top-K Hamming seeds and
        // no single-hop flat blend could ever surface it.
        addNode(store, "T", "person", "is", "a doctor at the downtown clinic", salience = 0.5f)
        // A textual distractor close to the query, to crowd out T from seeds.
        addNode(store, "D", "writer", "uses", "terse concise short style", salience = 0.5f)

        val retriever = createRetriever(store, seedCount = 2, maxHops = 3)
        val results = retriever.retrieve("concise answers", "writing context", now = 1000L)

        val ids = results.map { it.node.id }.toSet()
        // S is the Hamming seed.
        assertTrue("seed S must be returned", "S" in ids)

        // Recompute the retriever's step-1 seed selection (top-2 nodes by
        // binary-quantized Hamming distance to the query) exactly as the
        // production pipeline does, and prove T is NOT one of them — its text
        // shares no word with the query, so it is far outside the top-K seeds.
        // (Asserting against the final SALIENCE ranking would be wrong: a node
        // can rank in the top-2 by composite score yet never have been a seed.)
        val queryBlob = EmbeddingMath.binaryQuantize(provider.embed("concise answers"))
        val seedSet = store.query()
            .map { n ->
                n.id to EmbeddingMath.hammingDistance(
                    queryBlob,
                    EmbeddingMath.binaryQuantize(n.embedding ?: provider.embed(n.`object`))
                )
            }
            .sortedBy { it.second }
            .take(2)
            .map { it.first }
            .toSet()
        assertTrue("S must be a Hamming seed", "S" in seedSet)
        assertFalse(
            "T must be outside the top-K Hamming seeds (not textually similar to the query)",
            "T" in seedSet
        )

        // T is 2 hops away via M and NOT textually similar -> reachable only by
        // a multi-hop traversal. A flat blend (vector match + 1-hop graph +
        // salience) would never include it.
        val tResult = results.find { it.node.id == "T" }
        assertNotNull("2-hop-only fact T must be returned via staged traversal", tResult)
        assertEquals(
            "T must be reached by traversal, not as a Hamming seed",
            RankedMemory.MatchSource.GRAPH_TRAVERSAL, tResult!!.source
        )
    }

    // ── AC2: graph-connected, textually-unrelated fact via traversal ──────

    @Test
    fun `graph-connected fact appears via traversal hop even if not textually similar`() {
        val store = FakeMemoryGraphStore()
        // Vector seed: user prefers terse answers
        addNode(store, "n1", "user", "prefers", "terse concise answers", salience = 0.5f)
        // Graph-connected but not textually similar to query: user name is Alice
        addNode(store, "n2", "user", "name", "Alice", salience = 0.5f)
        // Distractors (more textually similar to "brief responses" than n2)
        addNode(store, "d1", "style", "prefers", "brief rapid quick responses", salience = 0.5f)
        addNode(store, "d2", "writer", "uses", "brief concise short paragraph style", salience = 0.5f)

        val retriever = createRetriever(store, seedCount = 2)
        val results = retriever.retrieve("brief responses", "user context", now = 1000L)

        val foundIds = results.map { it.node.id }.toSet()
        assertTrue("graph-connected n2 must appear in results", "n2" in foundIds)
        val n2Result = results.find { it.node.id == "n2" }
        assertNotNull(n2Result)
        assertEquals(
            "n2 must be reached via traversal (not a top-K seed)",
            RankedMemory.MatchSource.GRAPH_TRAVERSAL, n2Result!!.source
        )
    }

    // ── AC3: highly salient older memory outranks trivial more-similar one ─

    @Test
    fun `highly salient older memory outranks trivial more textually similar recent memory`() {
        val store = FakeMemoryGraphStore()
        // Highly salient, older, less textually similar to the query
        addNode(store, "important", "user", "requires", "emergency medical supplies for surgery",
            salience = 0.95f, validFrom = 1000L)
        // Low salience, recent, more textually similar to "user needs supplies"
        addNode(store, "trivial", "user", "mentioned", "office supplies for the desk",
            salience = 0.15f, validFrom = 5000L)

        val retriever = createRetriever(store)
        val results = retriever.retrieve("user needs supplies", "medical context", now = 5000L)

        assertTrue("should return results", results.isNotEmpty())
        val importantResult = results.find { it.node.id == "important" }
        val trivialResult = results.find { it.node.id == "trivial" }

        if (importantResult != null && trivialResult != null) {
            assertTrue(
                "highly salient important (${importantResult.score}) must outrank trivial (${trivialResult.score})",
                importantResult.score > trivialResult.score
            )
        }
        assertNotNull("important must be returned", importantResult)
    }

    // ── AC4: "described, not named" via seed-then-traverse ────────────────

    @Test
    fun `item reached by description not by its name via seed then traverse`() {
        val store = FakeMemoryGraphStore()
        // The item's own label/name text (shares only the word "sports" with
        // the query — not enough to be a top-K seed).
        addNode(store, "item", "user", "owns", "a Falcon sports coupe", salience = 0.7f)
        // A descriptive node whose text MATCHES the query description, sharing
        // subject "user" with the item so traversal can hop to it.
        addNode(store, "desc", "user", "drives", "red sports car in the garage", salience = 0.5f)
        // Distractors that are textually close to the description.
        addNode(store, "d1", "brand", "makes", "red sports car model", salience = 0.3f)
        addNode(store, "d2", "toy", "sells", "mini red sports car", salience = 0.3f)

        val retriever = createRetriever(store, seedCount = 2)
        // Query describes the vehicle by its characteristics, not its name.
        val results = retriever.retrieve("the red sports car", "user context", now = 1000L)

        val itemResult = results.find { it.node.id == "item" }
        assertNotNull(
            "item must be retrieved by description even though its stored name/label is not in the query",
            itemResult
        )
        assertEquals(
            "item must be reached via traversal from the descriptive seed, not as a seed itself",
            RankedMemory.MatchSource.GRAPH_TRAVERSAL, itemResult!!.source
        )
    }

    // ── Empty graph / basic sanity ────────────────────────────────────────

    @Test
    fun `retrieve returns empty list for empty graph`() {
        val store = FakeMemoryGraphStore()
        val retriever = createRetriever(store)
        assertTrue(retriever.retrieve("anything", now = 1000L).isEmpty())
    }

    @Test
    fun `retrieve returns ranked results sorted by descending score`() {
        val store = FakeMemoryGraphStore()
        addNode(store, "n1", "user", "prefers", "terse concise answers", salience = 0.8f)
        addNode(store, "n2", "user", "name", "Alice", salience = 0.5f)
        addNode(store, "n3", "project", "uses", "Kotlin programming language", salience = 0.3f)

        val retriever = createRetriever(store)
        val results = retriever.retrieve("user likes brief responses", "user preferences", now = 1000L)
        assertTrue(results.isNotEmpty())
        for (i in 1 until results.size) {
            assertTrue(
                "results must be sorted by descending score",
                results[i - 1].score >= results[i].score
            )
        }
    }

    // ── AC5: ContextWindowAssembler includes cross-session results ────────

    @Test
    fun `context window assembler includes cross-session memories from blended retriever`() {
        val graphStore = FakeMemoryGraphStore()
        addNode(graphStore, "prior", "user", "prefers", "terse concise answers", salience = 0.8f, validFrom = 500L)

        val retriever = createRetriever(graphStore)
        val referenceStore = ReferenceStore()
        val topicTracker = TopicTracker()
        val cogSalienceScorer = SalienceScorer(referenceStore)

        val assembler = ContextWindowAssembler(
            topicTracker = topicTracker,
            salienceScorer = cogSalienceScorer,
            blendedRetriever = retriever
        )

        topicTracker.recordTurn(0, "How is the project going?")
        referenceStore.recordMentions(0, listOf(
            IntentInference.Entity("e1", "project", IntentInference.EntityType.CONCEPT)
        ))

        val window = assembler.assemble(
            currentTurnIndex = 0,
            currentSegmentId = 0,
            currentTurnText = "user prefers brief answers"
        )

        assertTrue("cross-session memories must be included", window.crossSessionMemories.isNotEmpty())
        assertTrue(
            "cross-session memory should contain the prior fact",
            window.crossSessionMemories.any { it.content.contains("terse") }
        )
        val prompt = assembler.formatForPrompt(window)
        assertTrue(
            "formatted prompt must include cross-session memories section",
            prompt.contains("Relevant memories from prior sessions")
        )
    }

    @Test
    fun `context window assembler works without retriever (backward compatible)`() {
        val referenceStore = ReferenceStore()
        val topicTracker = TopicTracker()
        val cogSalienceScorer = SalienceScorer(referenceStore)

        val assembler = ContextWindowAssembler(
            topicTracker = topicTracker,
            salienceScorer = cogSalienceScorer
        )

        topicTracker.recordTurn(0, "Hello there")
        val window = assembler.assemble(currentTurnIndex = 0, currentSegmentId = 0)

        assertTrue("cross-session memories should be empty when no retriever", window.crossSessionMemories.isEmpty())
        assertEquals(1, window.turns.size)
    }
}
