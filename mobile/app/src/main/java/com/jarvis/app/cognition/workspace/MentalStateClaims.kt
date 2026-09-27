package com.jarvis.app.cognition.workspace

import com.jarvis.app.emotion.EmotionHypothesis
import com.jarvis.app.identity.MentalStateHypothesis
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * The shared vocabulary for the per-turn MENTAL_STATE claim: how a
 * [MentalStateHypothesis] becomes claim text, and how a reader gets it back.
 *
 * Two things make this a real substitution rather than a renamed field:
 *  - [encode]/[decode] are LOSSLESS. Every field of the hypothesis, including
 *    the full [com.jarvis.app.emotion.EmotionHypothesis] evidence list, survives
 *    the round trip, so a claim read back through the workspace is equal to what
 *    the estimator returned — the assembled window and the returned hypothesis
 *    stay byte-identical.
 *  - [confidenceFor] never invents a probability. A reading that observed a
 *    signal publishes that reading's own anchored confidence; a rule-based
 *    hypothesis with no emotion signal carries confidence 0.0, which would make
 *    the claim dead on arrival, so it is published at the DECLARED, documented
 *    [UNANCHORED_CONFIDENCE] prior — meaning "a real per-turn reading exists,
 *    but no emotion signal anchored it".
 */
object MentalStateClaims {

    /** The kind this vocabulary publishes. */
    val KIND: ClaimKind = ClaimKind.MENTAL_STATE

    /** The organ that publishes it (matches the organ graph's node id). */
    const val SOURCE_ORGAN: String = "identity.mentalStateEstimator"

    /**
     * Confidence lost per [Workspace.TICK_INTERVAL_MS]. A per-turn reading is
     * replaced by the next turn's claim (supersession), so decay is a safety net
     * for a claim nothing supersedes, not the main freshness mechanism.
     */
    const val DECAY_RATE: Double = 0.25

    /**
     * The DECLARED prior for a hypothesis whose emotion layer observed no
     * signal. Not a measured probability — see [confidenceFor].
     */
    const val UNANCHORED_CONFIDENCE: Double = 0.25

    /**
     * Claim ids are minted from a process-local counter. It carries no meaning
     * about the user: it exists so two readings published in the same millisecond
     * are still two distinct claims.
     */
    private val SEQUENCE = AtomicLong(0L)

    /** A fresh, unique claim id for this organ. */
    fun nextId(): String = "cognition.mentalState.${SEQUENCE.incrementAndGet()}"

    /** The claim confidence for [hypothesis] — measured when anchored. */
    fun confidenceFor(hypothesis: MentalStateHypothesis): Double =
        if (hypothesis.emotion.confidence > 0.0) hypothesis.emotion.confidence
        else UNANCHORED_CONFIDENCE

    /** The claim text for [hypothesis]: lossless JSON of every field. */
    fun encode(hypothesis: MentalStateHypothesis): String = JSONObject()
        .put("goal", hypothesis.goal)
        .put("mood", hypothesis.mood)
        .put("unstatedNeed", hypothesis.unstatedNeed)
        .put(
            "emotion",
            JSONObject()
                .put("valence", hypothesis.emotion.valence)
                .put("arousal", hypothesis.emotion.arousal)
                .put("dominance", hypothesis.emotion.dominance)
                .put("tension", hypothesis.emotion.tension)
                .put("confidence", hypothesis.emotion.confidence)
                .put("likelyState", hypothesis.emotion.likelyState)
                .put("alternatives", JSONArray(hypothesis.emotion.alternatives))
                .put("evidence", JSONArray(hypothesis.emotion.evidence))
                .put("temporalTrend", hypothesis.emotion.temporalTrend)
        )
        .toString()

    /**
     * The hypothesis behind [payload], or null when [payload] is not a mental
     * state this vocabulary wrote. A reader that cannot read a claim must say so
     * rather than invent a neutral hypothesis.
     */
    fun decode(payload: String): MentalStateHypothesis? = runCatching {
        val json = JSONObject(payload)
        val emotionJson = json.getJSONObject("emotion")
        MentalStateHypothesis(
            goal = json.getString("goal"),
            mood = json.getString("mood"),
            unstatedNeed = json.getString("unstatedNeed"),
            emotion = EmotionHypothesis(
                valence = emotionJson.getDouble("valence"),
                arousal = emotionJson.getDouble("arousal"),
                dominance = emotionJson.getDouble("dominance"),
                tension = emotionJson.getDouble("tension"),
                confidence = emotionJson.getDouble("confidence"),
                likelyState = emotionJson.getString("likelyState"),
                alternatives = emotionJson.getJSONArray("alternatives")
                    .let { a -> (0 until a.length()).map { a.getString(it) } },
                evidence = emotionJson.getJSONArray("evidence")
                    .let { a -> (0 until a.length()).map { a.getString(it) } },
                temporalTrend = emotionJson.getString("temporalTrend")
            )
        )
    }.getOrNull()

    /**
     * The claim for [hypothesis], superseding the current MENTAL_STATE claim so
     * the newest reading is the live one and the previous reading stays
     * queryable instead of being deleted.
     */
    fun claimFor(
        workspace: Workspace,
        hypothesis: MentalStateHypothesis,
        createdAt: Long = System.currentTimeMillis()
    ): Claim = Claim(
        id = nextId(),
        kind = KIND,
        payload = encode(hypothesis),
        confidence = confidenceFor(hypothesis),
        sourceOrgan = SOURCE_ORGAN,
        decayRate = DECAY_RATE,
        createdAt = createdAt,
        supersedes = workspace.current(KIND)?.id
    )

    /** Publish [hypothesis] into [workspace] and return the claim. */
    fun publish(
        workspace: Workspace,
        hypothesis: MentalStateHypothesis,
        createdAt: Long = System.currentTimeMillis()
    ): Claim = claimFor(workspace, hypothesis, createdAt).also { workspace.publish(it) }

    /** The hypothesis currently published as a MENTAL_STATE claim, if any. */
    fun read(workspace: Workspace, personId: String? = null): MentalStateHypothesis? =
        workspace.current(KIND, personId)?.payload?.let { decode(it) }
}
