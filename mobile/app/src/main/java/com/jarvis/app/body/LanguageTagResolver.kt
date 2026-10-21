package com.jarvis.app.body

/**
 * LAUNCH-SURVIVAL AC2: language is open.
 *
 * A language tag that arrives from outside the process (a TTS segment tag, an
 * STT hint, a config value) is NEVER handed to `valueOf` or any closed enum.
 * `valueOf` throws on any string that is not exactly an enum constant name,
 * which is exactly what killed the phone: the speech path passed the raw tag
 * "en" to `Language.valueOf("EN")`, and the uncaught exception on the IO
 * worker took the whole process down.
 *
 * This resolver maps an arbitrary tag to a [LanguageRouter.Language] by
 * normalisation and prefix matching, and — when nothing matches — degrades to
 * the default voice and reports the input as UNKNOWN. It never throws.
 *
 * It is pure Kotlin (no android.* type), so the whole contract is provable in a
 * plain-JVM unit test.
 */
object LanguageTagResolver {

    /** The voice a caller falls back to when a tag is unknown or empty. */
    val DEFAULT: LanguageRouter.Language = LanguageRouter.Language.ENGLISH

    /**
     * The outcome of resolving one external tag: the language to speak in, and
     * whether the input matched no known language (so it can be recorded).
     */
    data class Resolution(
        val language: LanguageRouter.Language,
        val unknown: Boolean,
        val rawTag: String
    )

    /**
     * Resolve [rawTag] to a language. Never throws. A blank, differently-cased,
     * region-qualified, or entirely unknown tag routes to [DEFAULT] and is
     * flagged [Resolution.unknown] so a caller can log it as an unknown value
     * instead of assuming it.
     */
    fun resolve(rawTag: String?): Resolution {
        val tag = rawTag?.trim().orEmpty()
        if (tag.isEmpty()) return Resolution(DEFAULT, unknown = true, rawTag = tag)

        val normalized = tag.lowercase().replace('_', '-')
        // BCP-47 primary subtag: "en-US" -> "en", "ar-EG" -> "ar".
        val primary = normalized.substringBefore('-')

        val match = when (primary) {
            "en", "english" -> LanguageRouter.Language.ENGLISH
            "ar", "arabic", "egyptian-arabic", "ar-eg" -> LanguageRouter.Language.EGYPTIAN_ARABIC
            else -> null
        }

        return if (match != null) {
            Resolution(match, unknown = false, rawTag = tag)
        } else {
            Resolution(DEFAULT, unknown = true, rawTag = tag)
        }
    }
}
