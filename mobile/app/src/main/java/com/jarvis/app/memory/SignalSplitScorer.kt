package com.jarvis.app.memory

/**
 * Six independent signals for a memory record (SIGNAL-SPLIT).
 *
 * Holds each signal as its own field and intentionally exposes NO combined
 * single score: computing a blend is a LATER story, gated on a head-to-head
 * test against a simpler baseline (most-recent-N or keyword-match). Until
 * then no consumer may read a pre-collapsed scalar.
 *
 * The six signals:
 *  - [relevance]:  semantic match to the current conversation context
 *  - [importance]: how much this fact matters to the user, INDEPENDENT of
 *                  how surprising it is; repetition never lowers it
 *  - [uncertainty]: how hedged/uncertain the stated fact is
 *  - [novelty]:     how new/surprising it is; this is what decays with
 *                   repetition, and it is a DIFFERENT axis from importance
 *  - [consent]:     whether the user explicitly stated/directed the fact vs.
 *                   the system inferring it
 *  - [cost]:        cost-to-retrieve/execute; no consumer computes it yet
 */
data class SignalProfile(
    val relevance: Float,
    val importance: Float,
    val uncertainty: Float,
    val novelty: Float,
    val consent: Float,
    val cost: Float
)

/**
 * Computes the six per-record signals of SIGNAL-SPLIT as six INDEPENDENT
 * values — never one collapsed importance number.
 *
 * The bug this fixes: a single prediction-error scalar drives encoding
 * strength, so a repeated, explicitly-stated boundary (unsurprising by
 * definition) loses weight on every mention. Here importance and novelty are
 * separate axes: repetition RAISES importance (the user keeps restating the
 * fact, so it matters more, and it must never lose weight) while novelty
 * decays along the same repetition axis. A naive prediction-error model that
 * equates "surprising" with "important" collapses the two — that collapse is
 * exactly what this scorer forbids by construction.
 *
 * `repetitionCount` is how many times this fact has been stated in total
 * (e.g. the length of its supersession chain in [MemoryGraphStore]); the
 * caller derives it from stored evidence — it is never guessed by this scorer.
 */
class SignalSplitScorer(
    private val embeddingProvider: EmbeddingProvider
) {
    /**
     * Compute the six signals for [node]. Never returns a single score — the
     * caller gets a [SignalProfile] and must persist it field-by-field via
     * [MemoryNode.withSignals].
     *
     * @param node the memory/thread record to score
     * @param currentContext current conversation text (used by relevance only)
     * @param repetitionCount total statements of this fact (0 = first mention)
     */
    fun computeProfile(
        node: MemoryNode,
        currentContext: String,
        repetitionCount: Int = 0
    ): SignalProfile {
        val consent = consentSignal(node)
        return SignalProfile(
            relevance = relevanceSignal(node, currentContext),
            importance = importanceSignal(repetitionCount = repetitionCount, explicit = consent),
            uncertainty = uncertaintySignal(node),
            novelty = noveltySignal(repetitionCount),
            consent = consent,
            cost = 0f
        )
    }

    /**
     * Importance saturates UP with repetition and never decays. An explicitly
     * stated fact (consent = 1) starts on a higher floor than an inferred one
     * and keeps climbing toward 1 on later mentions — so a boundary stated 5
     * times retains its top-tier importance on the 5th mention.
     */
    private fun importanceSignal(repetitionCount: Int, explicit: Float): Float {
        val sat = saturation(repetitionCount.toFloat())
        return if (explicit >= 0.5f) {
            (0.6f + 0.4f * sat).coerceIn(0f, 1f)
        } else {
            (0.4f + 0.2f * sat).coerceIn(0f, 1f)
        }
    }

    /**
     * Novelty decays monotonically with repetition (1/(1+r)) — the surprisal
     * axis a naive prediction-error scalar would have called "importance".
     * Kept as its own signal so a repeated boundary can be both UNsurprising
     * (low novelty) and HIGHLY important.
     */
    private fun noveltySignal(repetitionCount: Int): Float =
        (1f / (1f + repetitionCount)).coerceIn(0f, 1f)

    /** Explicit user directive markers -> consent 1.0; otherwise inferred -> 0. */
    private fun consentSignal(node: MemoryNode): Float {
        val text = node.`object`.lowercase()
        return if (EXPLICIT_DIRECTIVE_MARKERS.any { text.contains(it) }) 1f else 0f
    }

    /** Hedged text -> high uncertainty; plain declarative -> low. */
    private fun uncertaintySignal(node: MemoryNode): Float {
        val text = node.`object`.lowercase()
        return if (HEDGE_MARKERS.any { text.contains(it) }) 0.8f else 0.15f
    }

    /** Semantic relevance: binary-quantized Hamming similarity to the context. */
    private fun relevanceSignal(node: MemoryNode, currentContext: String): Float {
        if (currentContext.isBlank()) return 0f
        return try {
            val nodeVec = node.embedding ?: embeddingProvider.embed(node.`object`)
            val ctxVec = embeddingProvider.embed(currentContext)
            EmbeddingMath.hammingSimilarity(
                EmbeddingMath.binaryQuantize(nodeVec),
                EmbeddingMath.binaryQuantize(ctxVec)
            )
        } catch (_: Throwable) {
            0f
        }
    }

    private fun saturation(x: Float): Float {
        val k = 3f
        return x / (x + k)
    }

    companion object {
        /** First/second-person directive phrasing — an explicit user statement. */
        private val EXPLICIT_DIRECTIVE_MARKERS = listOf(
            "please", "never", "don't", "dont", "do not", "i want", "i need", "allow", "must", "require", "always"
        )

        /** Hedging/softening phrasing — a low-confidence statement. */
        private val HEDGE_MARKERS = listOf(
            "maybe", "perhaps", "probably", "likely", "i think", "not sure", "i guess", "approximately", "seems"
        )
    }
}

/**
 * Store a computed [SignalProfile] as its own field on this memory record.
 * Returns a copy with each of the six signals set independently — no signal is
 * collapsed into another or into a single score.
 */
fun MemoryNode.withSignals(profile: SignalProfile): MemoryNode = copy(
    relevance = profile.relevance,
    importance = profile.importance,
    uncertainty = profile.uncertainty,
    novelty = profile.novelty,
    consent = profile.consent,
    cost = profile.cost
)

/**
 * CORRECTION-CHAIN: the ACCESSIBILITY axis of a memory record.
 *
 * Accessibility answers one question only: how reachable is this memory right
 * now? Consolidation passes and repeated recall may raise it; nothing else may.
 *
 * It is deliberately NOT a seventh signal and NOT a combined score:
 *  - it is not stored in [SignalProfile] and no scorer computes it, so
 *  - no consumer can blend it, and
 *  - a consolidation pass physically cannot reach a stored signal, because
 *    [withRaisedAccessibility] copies [MemoryNode.accessibility] and nothing
 *    else — in particular never [MemoryNode.uncertainty].
 *
 * A stated fact's uncertainty is decided ONCE, when the fact is stored, by
 * [SignalSplitScorer.uncertaintySignal]. Consolidation does not get to make a
 * fact "more certain" just because it was consolidated more often; only how
 * easily it comes back changes. The formula is a saturating fixed step, so a
 * given number of passes always yields the same computed amount.
 */
object MemoryAccessibility {
    /** Accessibility added by ONE consolidation/recall pass. */
    const val STEP: Float = 0.05f

    /** Accessibility a freshly stored memory starts at. */
    fun initial(): Float = 0f

    /**
     * The computed accessibility after [passes] more passes, saturating at 1.
     * [current] null means "never reinforced", i.e. [initial].
     */
    fun raise(current: Float?, passes: Int = 1): Float {
        val base = current ?: initial()
        return (base + STEP * passes).coerceIn(0f, 1f)
    }
}

/**
 * CORRECTION-CHAIN: a copy of this node with [passes] more consolidation passes
 * applied to its accessibility ONLY.
 *
 * Every other field — including [MemoryNode.uncertainty] and all six split
 * signals — is carried over untouched, which is the structural reason a
 * consolidation pass can never inflate a fact's confidence: the copy names one
 * argument, and it is not a signal.
 */
fun MemoryNode.withRaisedAccessibility(passes: Int = 1): MemoryNode =
    copy(accessibility = MemoryAccessibility.raise(accessibility, passes))