package com.jarvis.app.emotion

/**
 * Tier 1 (text-only) of the Emotional Intelligence Fusion Layer.
 *
 * A deterministic, offline [EmotionHypothesis] reader: it scores a fixed state
 * space from per-turn text signals (lexicon + light negation/emphasis
 * semantics) and folds them into confidence-weighted valence/arousal/dominance/
 * tension. Zero model dependencies — it is a pure function of the turn text and
 * never makes a network or storage call.
 *
 * The produced hypothesis is consumed by the REAL
 * [com.jarvis.app.identity.UserMentalStateEstimator] seam in
 * [com.jarvis.app.JarvisEngine.init]: the estimator's provider is wired to
 * fold `estimate(text)` into its per-turn mental-state hypothesis, so the
 * emotion reading rides the existing single identity path into the assembled
 * context window — no second identity/emotion pipeline.
 *
 * Sarcasm and multi-turn trends are explicitly OUT of scope for tier 1 (they
 * belong to higher tiers); this reader is honest about that by keeping
 * confidence bounded and the evidence verbatim.
 */
class FusionLayerTier1 {

    private class Signal(val token: String, val state: String, val weight: Double, val evidenceLabel: String)

    private data class DimensionalDeltas(
        val valenceD: Double,
        val arousalD: Double,
        val dominanceD: Double,
        val tensionD: Double
    )

    /** The text signal lexicon. Each entry boosts one state and moves the
     *  dimensional accumulators; tokens are checked as lowercase substrings so
     *  inflections (frustrated / frustrating / frustration) all hit. */
    private val signals: List<Signal> = listOf(
        // Pleasant / positive-affect words
        Signal("happy", "pleasant", 1.4, "positive-affect"),
        Signal("love", "pleasant", 1.4, "positive-affect"),
        Signal("glad", "pleasant", 1.2, "positive-affect"),
        Signal("great", "pleasant", 1.1, "positive-affect"),
        Signal("awesome", "pleasant", 1.3, "positive-affect"),
        Signal("wonderful", "pleasant", 1.4, "positive-affect"),
        Signal("excited", "pleasant", 1.2, "positive-affect"),
        Signal("thank", "pleasant", 1.0, "positive-affect"),
        Signal("nice", "pleasant", 0.8, "positive-affect"),
        Signal("perfect", "pleasant", 1.2, "positive-affect"),
        // Frustration / irritation words
        Signal("frustrat", "frustrated", 1.6, "frustration"),
        Signal("annoy", "frustrated", 1.4, "frustration"),
        Signal("upset", "frustrated", 1.3, "frustration"),
        Signal("angry", "frustrated", 1.5, "frustration"),
        Signal("hate", "frustrated", 1.5, "frustration"),
        Signal("tired", "frustrated", 1.0, "frustration"),
        Signal("broken", "frustrated", 1.3, "frustration"),
        Signal("crash", "frustrated", 1.4, "frustration"),
        Signal("failing", "frustrated", 1.3, "frustration"),
        Signal("not working", "frustrated", 1.6, "frustration"),
        Signal("stupid", "frustrated", 1.5, "frustration"),
        // Anxiety / worry words
        Signal("worried", "anxious", 1.5, "anxiety"),
        Signal("scared", "anxious", 1.5, "anxiety"),
        Signal("anxious", "anxious", 1.6, "anxiety"),
        Signal("nervous", "anxious", 1.4, "anxiety"),
        Signal("stressed", "anxious", 1.5, "anxiety"),
        Signal("panic", "anxious", 1.6, "anxiety"),
        Signal("afraid", "anxious", 1.4, "anxiety"),
        Signal("terrified", "anxious", 1.6, "anxiety"),
        // Curiosity / information-seeking words
        Signal("how does", "curious", 1.1, "curiosity"),
        Signal("how do", "curious", 1.1, "curiosity"),
        Signal("what is", "curious", 1.0, "curiosity"),
        Signal("what does", "curious", 1.0, "curiosity"),
        Signal("why", "curious", 1.0, "curiosity"),
        Signal("where is", "curious", 1.0, "curiosity"),
        Signal("explain", "curious", 1.1, "curiosity"),
        Signal("curious", "curious", 1.2, "curiosity"),
        Signal("wondering", "curious", 1.1, "curiosity"),
        // Urgency / demand words
        Signal("now", "urgent", 1.2, "urgency"),
        Signal("immediately", "urgent", 1.5, "urgency"),
        Signal("asap", "urgent", 1.5, "urgency"),
        Signal("urgent", "urgent", 1.5, "urgency"),
        Signal("quickly", "urgent", 1.1, "urgency"),
        Signal("hurry", "urgent", 1.3, "urgency"),
        Signal("right now", "urgent", 1.5, "urgency"),
        // Politeness markers
        Signal("please", "polite", 1.1, "politeness"),
        Signal("could you", "polite", 1.0, "politeness"),
        Signal("can you", "polite", 0.9, "politeness"),
        Signal("would you", "polite", 1.0, "politeness"),
        Signal("may i ask", "polite", 1.1, "politeness"),
        // Certainty / confidence markers (agency)
        Signal("definitely", "confident", 1.3, "certainty"),
        Signal("must", "confident", 1.1, "certainty"),
        Signal("absolutely", "confident", 1.3, "certainty"),
        Signal("i know", "confident", 1.1, "certainty"),
        // Uncertainty markers (discount confidence)
        Signal("maybe", "neutral", 0.0, "uncertainty"),
        Signal("perhaps", "neutral", 0.0, "uncertainty"),
        Signal("not sure", "neutral", 0.0, "uncertainty"),
        Signal("unsure", "neutral", 0.0, "uncertainty"),
        Signal("might", "neutral", 0.0, "uncertainty")
    )

    /** Assertive words that also push valence/directness (subset of [signals]). */
    private val assuranceTokens = listOf("definitely", "absolutely")

    /** Emphasis cues handled outside the lexicon: exclamations + intensifiers. */
    private val intensifierTokens = listOf("really", "very", "truly", "lowkey", "so ").map { " $it" }

    /**
     * Read one turn's text into a confidence-weighted [EmotionHypothesis].
     * A turn with no observable signal yields [EmotionHypothesis.neutral].
     */
    fun estimate(text: String): EmotionHypothesis {
        val lower = text.lowercase()
        var valence = 0.0
        var arousal = 0.0
        var dominance = 0.0
        var tension = 0.0
        var rawSignal = 0.0
        var uncertaintyCount = 0
        val stateScores = mutableMapOf<String, Double>()
        val evidence = mutableListOf<String>()

        fun boost(state: String, w: Double, label: String, token: String) {
            val current = stateScores[state] ?: 0.0
            stateScores[state] = (current + w).coerceAtLeast(0.0)
            evidence.add("matched token '$token' -> $state (+%.2f)".format(w))
            rawSignal += kotlin.math.abs(w)
        }

        fun applyDeltas(state: String, weight: Double) {
            val (valenceD, arousalD, dominanceD, tensionD) = deltasFor(state)
            val magnitude = kotlin.math.abs(weight)
            // Negated signals flip the affective polarity of the valence
            // component (a negated pleasant cue reads as unpleasant) while the
            // activation/certainty components stay magnitude-weighted.
            val polarity = if (weight < 0.0) -1.0 else 1.0
            valence += valenceD * magnitude * polarity
            arousal += arousalD * magnitude
            dominance += dominanceD * magnitude
            tension += tensionD * magnitude
        }

        for (signal in signals) {
            if (lower.contains(signal.token)) {
                if (signal.state == "neutral") {
                    uncertaintyCount += 1
                    evidence.add("matched token '${signal.token}' -> confidence discount")
                    continue
                }
                val weight = if (isNegated(lower, signal.token)) -signal.weight * 0.6 else signal.weight
                boost(signal.state, weight, signal.evidenceLabel, signal.token)
                applyDeltas(signal.state, weight)
            }
        }

        val exclamations = lower.count { it == '!' }
        if (exclamations > 0) {
            arousal += 0.25
            tension += 0.15
            evidence.add("emphasis: $exclamations exclamation point(s)")
            rawSignal += 0.6
        }
        for (token in intensifierTokens) {
            if (lower.contains(token)) {
                arousal += 0.15
                evidence.add("intensifier '$token'")
                rawSignal += 0.4
            }
        }
        for (token in assuranceTokens) {
            if (lower.contains(token)) {
                dominance += 0.4
                evidence.add("assurance token '$token'")
                rawSignal += 0.6
            }
        }

        val top = stateScores.entries
            .maxByOrNull { it.value }
        if (top == null && uncertaintyCount == 0 && evidence.isEmpty()) {
            return EmotionHypothesis.neutral()
        }
        val winnerPositive = top != null && top.value > 0.0

        val secondScore = stateScores.entries
            .filter { it.key != top?.key && it.value > 0.0 }
            .maxByOrNull { it.value }?.value ?: 0.0
        val separation = if (winnerPositive) {
            (top.value - secondScore) / (top.value + 1e-9)
        } else {
            0.0
        }

        val alternatives = if (winnerPositive) {
            stateScores.entries
                .filter { it.key != top.key && it.value > 0.0 }
                .sortedByDescending { it.value }
                .take(2)
                .map { it.key }
        } else {
            emptyList()
        }

        val magnitude = rawSignal / (rawSignal + 2.5)
        val separationFactor = 0.65 + 0.35 * separation.coerceIn(0.0, 1.0)
        val uncertaintyDiscount = 1.0 / (1.0 + uncertaintyCount)
        val confidence = ((0.25 + 0.75 * magnitude) * separationFactor * uncertaintyDiscount)
            .coerceIn(0.0, 1.0)

        return EmotionHypothesis(
            valence = valence.coerceIn(-1.0, 1.0),
            arousal = arousal.coerceIn(0.0, 1.0),
            dominance = dominance.coerceIn(0.0, 1.0),
            tension = tension.coerceIn(0.0, 1.0),
            confidence = confidence,
            likelyState = if (winnerPositive) top.key else "neutral",
            alternatives = alternatives,
            evidence = evidence,
            temporalTrend = trendOf(lower, confidence)
        )
    }

    private fun isNegated(lower: String, token: String): Boolean {
        val negationPrefixes = listOf("not ", "no ", "never ", "don't ", "doesn't ", "didn't ")
        return negationPrefixes.any { lower.contains("$it$token") } || lower.contains("n't $token")
    }

    private fun deltasFor(state: String): DimensionalDeltas = when (state) {
        "pleasant" -> DimensionalDeltas(0.55, 0.15, 0.05, -0.05)
        "frustrated" -> DimensionalDeltas(-0.60, 0.30, -0.10, 0.45)
        "anxious" -> DimensionalDeltas(-0.45, 0.35, -0.25, 0.65)
        "curious" -> DimensionalDeltas(0.10, 0.20, 0.05, -0.05)
        "urgent" -> DimensionalDeltas(-0.05, 0.50, 0.15, 0.25)
        "polite" -> DimensionalDeltas(0.25, -0.10, -0.10, -0.10)
        "confident" -> DimensionalDeltas(0.05, 0.05, 0.40, -0.10)
        else -> DimensionalDeltas(0.0, 0.0, 0.0, 0.0)
    }

    private fun trendOf(lower: String, confidence: Double): String {
        if (confidence <= 0.0) return "steady"
        val escalating = listOf("keeps", " still ", "again", "every time", "more and more", "always")
        val resolving = listOf("finally", "at last", "resolved", "fixed", "better now", "got it working", "done")
        if (escalating.any { lower.contains(it) }) return "escalating"
        if (resolving.any { lower.contains(it) }) return "resolving"
        return "steady"
    }
}