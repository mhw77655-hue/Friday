package com.jarvis.app.humancore.protocol

/**
 * Structured read of the USER's emotional state, produced by the Perception
 * Pass (Emotional Intelligence module, spec §7).
 *
 * This is JARVIS's empathy input — it is a read of the other person, never
 * JARVIS's own mood (which lives in MoodStore and is owned by the Mood
 * System / Emotional Regulation, §6).
 *
 * Spec constraints honored here:
 *  - named signals are confidence scores, not binary flags;
 *  - "unknown" is a valid state (low confidence), never an error (§7 Failure
 *    handling);
 *  - valence/arousal/confidence are the stable core; the named signals are an
 *    additive extension (§7 Extension points).
 */
data class AffectRead(
    /** Positive↔negative estimate of the user's current affect, in [-1, 1]. */
    val valence: Double,
    /** Calm↔energized estimate of the user's current affect, in [-1, 1]. */
    val arousal: Double,
    /** Confidence in this estimate, in [0, 1]. Low confidence means "unknown". */
    val confidence: Double,
    /**
     * Named affect signals (frustration, stress, excitement, sadness, ...),
     * each with a confidence score in [0, 1]. Absent signals are simply not
     * present in the map — consumers must treat absence as "not detected",
     * never as an error.
     */
    val signals: Map<String, Double>,
    /**
     * Trend across the rolling window, e.g. "user has seemed stressed across
     * the last several exchanges". Absent/empty when there is insufficient
     * history for a trend (§7: short rolling window, bounded length).
     */
    val trend: String?,
    /**
     * True when the optional prosody/tone metadata from the mobile voice
     * pipeline (§0.14) was used to refine this read. Always null-safe: the
     * module works identically for plain text (Telegram) and voice.
     */
    val prosodyUsed: Boolean,
    /** Epoch millis at which this read was computed. */
    val ts: Long
) {
    val isUnknown: Boolean get() = confidence < 0.35

    companion object {
        /** Neutral, clearly-labeled "getting to know you" read (§0.11 cold start). */
        val NEUTRAL = AffectRead(
            valence = 0.0, arousal = 0.0, confidence = 0.0,
            signals = emptyMap(), trend = null, prosodyUsed = false, ts = 0L
        )
    }
}
