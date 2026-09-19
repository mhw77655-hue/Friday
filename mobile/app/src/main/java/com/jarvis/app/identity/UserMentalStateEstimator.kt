package com.jarvis.app.identity

import com.jarvis.app.emotion.EmotionHypothesis

/**
 * An explicit, SHORT-LIVED hypothesis of the user's CURRENT goal / mood /
 * unstated need for the active turn — the ToM-style (ToMAgent) per-turn mental
 * state estimate, distinct from the durable [UserProfile].
 *
 * This object is intentionally EPHEMERAL: it produces a fresh hypothesis per
 * turn and NEVER persists it anywhere (no graph write, no storage call). The
 * hypothesis decays with the turn/session. It is consumed by the context
 * assembler as an additional signal alongside the durable profile — the two are
 * never conflated (the exact mistake 2026 research flags: treating a dynamic
 * per-turn state as a durable profile, or vice versa).
 */
class UserMentalStateEstimator(
    private val hypothesisProvider: (String) -> MentalStateHypothesis = ::ruleBasedHypothesis
) {

    /** Generate the mental-state hypothesis for the current user turn. */
    fun estimateForTurn(userText: String): MentalStateHypothesis =
        hypothesisProvider(userText.trim())

    /** Mark that a new session/turn boundary began; retained for symmetry with
     *  the durable profile, this estimator keeps no cross-session state. */
    fun newSession() {}
}

/**
 * The per-turn hypothesis of the user's mental state. This is a dynamic,
 * short-lived signal — it is NOT stored as a durable fact.
 */
data class MentalStateHypothesis(
    /** What the user most likely wants to achieve this turn. */
    val goal: String,
    /** Estimated affective state of the user. */
    val mood: String,
    /** An unstated need the estimator infers (may be empty). */
    val unstatedNeed: String,
    /**
     * The confidence-weighted emotion reading behind this hypothesis, produced
     * by the Tier-1 Emotional Intelligence Fusion Layer. Defaults to the
     * empty-neutral reading so legacy rule-based providers still construct a
     * valid hypothesis; the live production wiring (JarvisEngine.init) always
     * folds a real [EmotionHypothesis] in.
     */
    val emotion: EmotionHypothesis = EmotionHypothesis.neutral()
) {
    companion object {
        /** An empty-neutral hypothesis used when no signal is present. */
        fun neutral() = MentalStateHypothesis("observe", "neutral", "", EmotionHypothesis.neutral())

        /**
         * Fold a Tier-1 [EmotionHypothesis] into the ephemeral per-turn
         * hypothesis the existing [UserMentalStateEstimator] seam carries. The
         * label/mood is only the confidence-weighted argmax state; the full
         * dimensional, evidence-backed reading rides along in [emotion].
         */
        fun fromEmotion(emotion: EmotionHypothesis): MentalStateHypothesis = MentalStateHypothesis(
            goal = goalFor(emotion.likelyState),
            mood = emotion.likelyState,
            unstatedNeed = unstatedNeedFor(emotion.likelyState),
            emotion = emotion
        )

        private fun goalFor(state: String): String = when (state) {
            "frustrated" -> "resolve a pain point"
            "anxious" -> "seek reassurance"
            "curious" -> "seek information"
            "urgent" -> "get quick action"
            "polite" -> "request assistance"
            "pleasant" -> "acknowledge positive state"
            "confident" -> "confirm direction"
            else -> "continue conversation"
        }

        private fun unstatedNeedFor(state: String): String = when (state) {
            "frustrated" -> "needs empathy before more questions"
            "anxious" -> "wants certainty and a clear next step"
            "curious" -> "wants a clear, concrete answer"
            "urgent" -> "wants speed over thoroughness"
            "polite" -> "wants the request fulfilled reliably"
            "pleasant" -> "wants the good state acknowledged"
            "confident" -> "wants the plan confirmed and executed"
            else -> ""
        }
    }
}

/**
 * Deterministic, offline rule-based default hypothesis generator. Chosen so the
 * estimate is a real, per-turn-computed signal (not a hardcoded constant) that
 * measurably changes turn-to-turn; a production wiring can replace it with an
 * LLM via [UserMentalStateEstimator]'s injected provider.
 */
private fun ruleBasedHypothesis(text: String): MentalStateHypothesis {
    val lower = text.lowercase()
    return when {
        lower.contains("?") && (lower.contains("how") || lower.contains("what") || lower.contains("why")) ->
            MentalStateHypothesis(
                goal = "seek information",
                mood = "curious",
                unstatedNeed = "wants a clear, concrete answer"
            )
        text.endsWith("!") || lower.contains(" immediately") || lower.contains("now") ->
            MentalStateHypothesis(
                goal = "get quick action",
                mood = "urgent",
                unstatedNeed = "wants speed over thoroughness"
            )
        lower.contains("frustrat") || lower.contains("annoy") || lower.contains("upset") ||
            lower.contains("tired") || lower.contains("hate") ->
            MentalStateHypothesis(
                goal = "resolve a pain point",
                mood = "frustrated",
                unstatedNeed = "wants empathy before more questions"
            )
        lower.contains("?" ) ->
            MentalStateHypothesis(
                goal = "seek information",
                mood = "neutral",
                unstatedNeed = "wants a direct reply"
            )
        lower.startsWith("please") || lower.contains("could you") || lower.contains("can you") ->
            MentalStateHypothesis(
                goal = "request assistance",
                mood = "polite",
                unstatedNeed = "wants the request fulfilled reliably"
            )
        else ->
            MentalStateHypothesis(
                goal = "continue conversation",
                mood = "neutral",
                unstatedNeed = ""
            )
    }
}
