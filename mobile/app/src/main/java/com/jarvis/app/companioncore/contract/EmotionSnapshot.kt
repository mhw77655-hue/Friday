package com.jarvis.app.companioncore.contract

/**
 * A frozen, tick-stamped emotion snapshot (§2.9).
 *
 * Taken ONCE at the start of each tick by the Emotion Expression Layer and
 * held immutable for that tick's duration across every expressive consumer —
 * preventing one subsystem seeing valence=0.6 while another reads a
 * newer/older value mid-tick (cross-channel consistency by construction).
 *
 * @property valence [-1, 1]
 * @property arousal [0, 1]
 * @property confidence [0, 1]
 * @property timestamp master-clock time the snapshot was taken (epoch millis
 *   from `CompanionClock`, §1.5).
 */
data class EmotionSnapshot(
    val valence: Double,
    val arousal: Double,
    val confidence: Double,
    val timestamp: Long
) {
    /** Project this snapshot into the §1 `emotion_vector` tuple ([EmotionVector]). */
    fun toVector(): EmotionVector = EmotionVector(valence = valence, arousal = arousal, confidence = confidence)

    companion object {
        /**
         * Neutral fallback (spec §2.30 failure handling): when no fresh signal
         * arrives within the staleness threshold, consumers get a neutral
         * snapshot — never a fabricated one. `neutral()` is truthful: it
         * represents "no reliable emotion data" as flat-neutral, not as "calm".
         */
        fun neutral(timestamp: Long): EmotionSnapshot = EmotionSnapshot(
            valence = 0.0,
            arousal = 0.5,
            confidence = 0.0,
            timestamp = timestamp
        )
    }
}
