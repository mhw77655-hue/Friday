package com.jarvis.app.language

/**
 * Per-turn dialect/language detection signal. Derived from the user's text
 * via lexicon/rule-based Egyptian Arabic marker detection, code-switch
 * segmentation, and Arabizi pattern recognition. Zero new model weights —
 * all detection is deterministic and offline.
 *
 * Constructed per-turn by [EgyptianArabicDialectDetector] and fed into
 * the generation prompt via [com.jarvis.app.cognitive.ContextWindowAssembler]
 * so replies match the user's register/dialect instead of defaulting to
 * formal Arabic or English.
 */
data class DialectSignal(
    /**
     * The detected dominant language mix: "en", "ar-EG", "ar-EG+en", or
     * "en+ar-EG" (order reflects which appears first / dominates).
     */
    val detectedLanguageMix: String,

    /**
     * Confidence that the Arabic present is specifically Egyptian dialect
     * (as opposed to MSA or another dialect). 0.0 when no Arabic/Arabizi is
     * detected; >=0.15 whenever Arabic script is present (script baseline);
     * Egyptian markers and Arabizi numerals push it higher. Rule-based, not
     * a model score.
     */
    val dialectConfidence: Float,

    /**
     * Character offsets where the language switches between English and
     * Arabic (Arabic script or Arabizi). Empty when the text is monolingual.
     * Each point is the index of the first character of the new-language run.
     */
    val codeSwitchPoints: List<Int>,

    /**
     * The inferred register: "informal" when Egyptian markers, Arabizi
     * numerals, or casual particles are present; "formal" for MSA-like
     * or formal English; "neutral" when detection is inconclusive.
     */
    val register: String
) {
    companion object {
        /** Default signal when no dialect detection is wired. */
        fun neutral() = DialectSignal(
            detectedLanguageMix = "en",
            dialectConfidence = 0f,
            codeSwitchPoints = emptyList(),
            register = "neutral"
        )
    }
}
