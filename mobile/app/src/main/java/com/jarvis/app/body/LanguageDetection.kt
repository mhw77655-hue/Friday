package com.jarvis.app.body

/**
 * Pure language routing: script-based English vs Egyptian-Arabic detection and
 * code-switching segmentation. No Android dependencies — unit-testable in a
 * plain JVM. [LanguageRouter] delegates here; TTS uses the segments to pick a
 * per-sentence voice.
 */
object LanguageDetection {

    /** Detect the dominant language of [text] from its script. */
    fun detectLanguage(text: String): LanguageRouter.Language {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return LanguageRouter.Language.ENGLISH

        // Check for Arabic script
        val hasArabic = trimmed.any { c ->
            c >= '؀' && c <= 'ۿ' || // Arabic
                c >= 'ݐ' && c <= 'ݿ' || // Arabic Supplement
                c >= 'ࢠ' && c <= 'ࣿ' || // Arabic Extended-A
                c >= 'ﭐ' && c <= '﷿' || // Arabic Presentation Forms-A
                c >= 'ﹰ' && c <= '﻿'   // Arabic Presentation Forms-B
        }

        return if (hasArabic) LanguageRouter.Language.EGYPTIAN_ARABIC else LanguageRouter.Language.ENGLISH
    }

    /** Detect language segments for code-switching. */
    fun detectSegments(text: String): List<LanguageSegment> {
        val segments = mutableListOf<LanguageSegment>()
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        var currentLang: LanguageRouter.Language? = null
        var currentSegment = StringBuilder()

        for (word in words) {
            val lang = detectLanguage(word)
            if (currentLang == null || lang == currentLang) {
                currentLang = lang
                if (currentSegment.isNotEmpty()) currentSegment.append(' ')
                currentSegment.append(word)
            } else {
                segments.add(LanguageSegment(currentSegment.toString(), currentLang))
                currentLang = lang
                currentSegment = StringBuilder(word)
            }
        }

        if (currentSegment.isNotEmpty() && currentLang != null) {
            segments.add(LanguageSegment(currentSegment.toString(), currentLang))
        }

        return segments
    }
}
