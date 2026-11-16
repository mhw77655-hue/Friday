package com.jarvis.app.companioncore.engine

import com.jarvis.app.companioncore.contract.CompanionSignal
import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.contract.EmotionVector

/**
 * Maps Friday's validated runtime evidence into the Companion Core render
 * signal. Missing affect/trust/confidence measurements stay unknown: no
 * inferred personality, mood, motivation or relationship values are invented.
 */
object SignalMapper {
    fun map(raw: FridayRuntimeSnapshot, action: CurrentAction): CompanionSignal {
        val valence = raw.moodValence?.coerceIn(-1.0, 1.0) ?: 0.0
        val arousal = raw.moodArousal?.coerceIn(0.0, 1.0) ?: 0.5
        val confidence = raw.lastUserConfidence?.coerceIn(0.0, 1.0) ?: 0.0
        val energy = raw.moodArousal?.coerceIn(0.0, 1.0) ?: 0.5
        // No measured motivation signal exists yet; 0.5 is the neutral renderer
        // fallback and confidence=0 marks affect/confidence as unavailable.
        val motivation = 0.5

        return CompanionSignal(
            emotionVector = EmotionVector(
                valence = valence,
                arousal = arousal,
                confidence = confidence
            ),
            confidence = confidence,
            energy = energy,
            motivation = motivation,
            attentionFocus = null,
            currentAction = action,
            lastUtteranceText = raw.lastUtteranceText.orEmpty(),
            fabricationFlag = raw.fabricationEvidence
        )
    }
}
