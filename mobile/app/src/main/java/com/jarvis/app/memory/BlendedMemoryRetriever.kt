package com.jarvis.app.memory

/**
 * A ranked memory result produced by the staged [BlendedMemoryRetriever].
 *
 * [source] tells the caller how this memory entered the activated set — either
 * as a top-K Hamming seed directly matched to the query, or as a node reached
 * only via weighted multi-hop graph traversal from such a seed (a fact that is
 * NOT itself textually/semantically similar to the query but is connected to
 * something that is).
 */
data class RankedMemory(
    val node: MemoryNode,
    /** Composite rank score from [MemoryImportanceScorer] (higher = more relevant). */
    val score: Float,
    val source: MatchSource
) {
    enum class MatchSource {
        /** Found as a top-K binary-quantized Hamming seed for the query. */
        HAMMING_SEED,
        /** Found via weighted multi-hop graph traversal from a seed. */
        GRAPH_TRAVERSAL
    }
}

/**
 * Staged (hippocampal-indexing) cross-session memory retriever. Replaces the
 * old flat three-signal blend (which summed vector + graph + salience as three
 * co-equal weighted signals). Instead this is a strict pipeline:
 *
 *  1. **Seed (Hamming)**: binary-quantize the query and [EmbeddingProvider]
 *     output, then Hamming-distance (XOR + popcount) search maps every
 *     currently-valid node to a distance; the [seedCount] closest nodes become
 *     the entry-point seeds.
 *  2. **Traverse (weighted spreading activation)**: starting from those seeds,
 *     run [maxHops] rounds of activation propagation over the graph. The
 *     strength passed from node u to neighbor v is u's current activation
 *     multiplied by [edgeWeight](u,v) (a real, meaningfully-varying weight
 *     derived from relationship strength — same-subject edges are stronger
 *     than same-predicate-only edges) and a per-hop [activationDecay]. This is
 *     a Personalized-PageRank-style multi-hop spread, NOT a single-hop lookup
 *     and NOT unweighted BFS. It surfaces facts whose own text is unrelated to
 *     the query but which are connected — enabling "find it by how it was
 *     described, not by its literal name."
 *  3. **Rank (salience)**: the activated set (seeds + traversed) is finally
 *     ranked by [MemoryImportanceScorer]'s composite score for output, so a
 *     highly salient older memory can outrank a trivial, more textually
 *     similar recent one.
 *
 * Only step 3 decides the final order — steps 1 and 2 decide which nodes are
 * even candidates. There is no weighted sum of three parallel signals.
 *
 * Wire this into Stage 01C's [com.jarvis.app.cognitive.ContextWindowAssembler]
 * to add cross-session recall alongside the session-scoped topic/salience
 * signals.
 */
class BlendedMemoryRetriever(
    private val graphStore: MemoryGraphStore,
    private val embeddingProvider: EmbeddingProvider,
    private val scorer: MemoryImportanceScorer,
    /** Number of binary-quantized Hamming top matches to use as seeds. */
    private val seedCount: Int = 4,
    /** Number of weighted spreading-activation hops from the seeds. */
    private val maxHops: Int = 3,
    /** Per-hop decay of activation passed along an edge (0 = none spreads). */
    private val activationDecay: Float = 0.5f,
    /** Final result count cap (from the activated, salience-ranked set). */
    private val maxResults: Int = 10,
    /**
     * Real edge weight between two related nodes. Overridable for fixtures
     * that assert traversal ranks by weight. Default derives a varying weight
     * from relationship strength: same subject+predicate (0.9) > same subject
     * (0.6) > same predicate only (0.4); unrelated nodes have 0 weight.
     */
    private val edgeWeight: (MemoryNode, MemoryNode) -> Float = ::defaultBlendedEdgeWeight
) {

    companion object {
        /**
         * The production edge-weight function reused across the memory layer so
         * traversal weight semantics stay consistent (the store itself has no
         * dedicated edges table / neighbors() API - see MEMORY-GRAPH-STORE
         * note). Selectable by, e.g., [com.jarvis.app.identity.WorldModelService]
         * to run multi-hop traversal with the SAME weighted relationship
         * semantics as this retriever rather than an unrelated reimplementation.
         */
        @JvmStatic
        fun blendedEdgeWeight(u: MemoryNode, v: MemoryNode): Float {
            val sameSubject = u.subject == v.subject
            val samePredicate = u.predicate == v.predicate
            return when {
                sameSubject && samePredicate -> 0.9f
                sameSubject -> 0.6f
                samePredicate -> 0.4f
                else -> 0f
            }
        }
    }

    /**
     * Retrieve ranked cross-session memories for [query].
     *
     * @param query the search query text
     * @param context the current conversation context (for salience scoring)
     * @param now current timestamp (injectable for testing)
     * @return ranked list of [RankedMemory], best match first
     */
    fun retrieve(
        query: String,
        context: String = "",
        now: Long = System.currentTimeMillis()
    ): List<RankedMemory> {
        val allNodes = graphStore.query()
        if (allNodes.isEmpty()) return emptyList()

        // ── STEP 1: binary-quantized Hamming seeds ────────────────────────
        val queryQuant = EmbeddingMath.binaryQuantize(embeddingProvider.embed(query))
        val seeds = allNodes
            .map { node ->
                val vec = node.embedding ?: embeddingProvider.embed(node.`object`)
                node to EmbeddingMath.hammingDistance(queryQuant, EmbeddingMath.binaryQuantize(vec))
            }
            .sortedBy { it.second }
            .take(seedCount)
            .map { it.first }

        // ── STEP 2: weighted multi-hop spreading activation from seeds ─────
        val activation = mutableMapOf<String, Float>()
        seeds.forEach { activation[it.id] = 1f }

        repeat(maxHops) {
            val snapshot = activation.toMap()
            for (u in allNodes) {
                val baseAct = snapshot[u.id] ?: 0f
                if (baseAct <= 0f) continue
                for (v in allNodes) {
                    if (v.id == u.id) continue
                    val w = edgeWeight(u, v)
                    if (w <= 0f) continue
                    activation[v.id] = (activation[v.id] ?: 0f) + baseAct * w * activationDecay
                }
            }
        }

        // ── STEP 3: rank the activated set by composite score ─────────────
        val seedIds = seeds.mapTo(mutableSetOf()) { it.id }
        return activation.keys
            .mapNotNull { id -> allNodes.find { it.id == id } }
            .map { node ->
                RankedMemory(
                    node = node,
                    score = scorer.score(node, context, now = now),
                    source = if (node.id in seedIds) {
                        RankedMemory.MatchSource.HAMMING_SEED
                    } else {
                        RankedMemory.MatchSource.GRAPH_TRAVERSAL
                    }
                )
            }
            .sortedByDescending { it.score }
            .take(maxResults)
    }
}

/**
 * Default edge weight between two nodes - delegates to
 * [BlendedMemoryRetriever.blendedEdgeWeight] so WorldModelService and the
 * retriever share one weighted-traversal definition.
 */
private fun defaultBlendedEdgeWeight(u: MemoryNode, v: MemoryNode): Float =
    BlendedMemoryRetriever.blendedEdgeWeight(u, v)
