package com.jarvis.app.language

/**
 * Lexicon/rule-based Egyptian Arabic dialect and code-switch detection.
 *
 * Derived from per-turn text using:
 * - Egyptian Arabic lexicon markers (common Egyptian-specific words/particles)
 * - Arabic script presence detection (Unicode ranges)
 * - Arabizi numeral-substitution patterns (3=ع, 7=ح, etc.)
 * - Code-switch boundary detection (script transitions)
 * - Register inference (informal Egyptian markers vs formal MSA/English)
 *
 * Zero new model weights — fully deterministic and offline.
 */
class EgyptianArabicDialectDetector {

    /**
     * Detect dialect signal from the user's per-turn text.
     */
    fun detect(text: String): DialectSignal {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return DialectSignal.neutral()

        val hasArabicScript = hasArabicScript(trimmed)
        val hasLatin = hasLatinChars(trimmed)
        val egyptianMarkers = findEgyptianMarkers(trimmed)
        val arabiziCount = countArabiziPatterns(trimmed)
        val codeSwitchPoints = detectCodeSwitchPoints(trimmed)
        val register = inferRegister(egyptianMarkers, arabiziCount, trimmed)

        val hasEgyptian = egyptianMarkers.isNotEmpty() || arabiziCount > 0
        val arabicRatio = if (hasArabicScript) {
            countArabicScriptChars(trimmed).toFloat() / trimmed.length
        } else {
            0f
        }

        val detectedMix = when {
            hasArabicScript && hasLatin && codeSwitchPoints.isNotEmpty() -> {
                val arabFirst = trimmed.indexOfFirst { isArabicScriptChar(it) } <
                    trimmed.indexOfFirst { it.isLetter() && !isArabicScriptChar(it) }
                if (arabFirst) "ar-EG+en" else "en+ar-EG"
            }
            hasArabicScript -> "ar-EG"
            hasLatin -> "en"
            else -> "en"
        }

        val dialectConfidence = when {
            !hasArabicScript && arabiziCount == 0 -> 0f
            hasEgyptian -> {
                val markerScore = (egyptianMarkers.size.coerceAtMost(5)) * 0.12f
                val arabiziScore = (arabiziCount.coerceAtMost(4)) * 0.08f
                val scriptScore = if (hasArabicScript) 0.3f else 0f
                (markerScore + arabiziScore + scriptScore).coerceIn(0.1f, 1.0f)
            }
            hasArabicScript -> 0.15f
            else -> 0f
        }

        return DialectSignal(
            detectedLanguageMix = detectedMix,
            dialectConfidence = dialectConfidence,
            codeSwitchPoints = codeSwitchPoints,
            register = register
        )
    }

    // ── Arabic script detection ──

    private fun hasArabicScript(text: String): Boolean =
        text.any { isArabicScriptChar(it) }

    private fun isArabicScriptChar(c: Char): Boolean =
        (c in '\u0600'..'\u06FF') ||  // Arabic
        (c in '\u0750'..'\u077F') ||  // Arabic Supplement
        (c in '\u08A0'..'\u08FF') ||  // Arabic Extended-A
        (c in '\uFB50'..'\uFDFF') ||  // Arabic Presentation Forms-A
        (c in '\uFE70'..'\uFEFF')     // Arabic Presentation Forms-B

    private fun hasLatinChars(text: String): Boolean =
        text.any { it.isLetter() && it.code in 0x41..0x7A }

    private fun countArabicScriptChars(text: String): Int =
        text.count { isArabicScriptChar(it) }

    // ── Egyptian Arabic lexicon markers ──

    private fun findEgyptianMarkers(text: String): List<String> {
        val lower = text.lowercase()
        return EGYPTIAN_MARKERS.filter { lower.contains(it) }
    }

    /**
     * Common Egyptian Arabic particles, pronouns, and dialect-specific words
     * that distinguish Egyptian Arabic from MSA or other dialects.
     */
    private val EGYPTIAN_MARKERS = listOf(
        // Egyptian pronouns
        "انا", "انت", "انتي", "هو", "هي", "احنا", "انتو", "هم",
        // Egyptian particles
        "بص", "يلا", "ماشي", "تمام", "كده", "كدا", "برضو",
        // Egyptian negation patterns
        "مش", "مفيش", "ملوش", "مابي",
        // Egyptian verb forms
        "عايز", "عايزة", "عاوز", "عند", "ها",
        // Common Egyptian words
        "ايه", "ليه", "ازاي", "فين", "امتي", "اهلا",
        // Casual particles
        "يعني", "طيب"
    )

    // ── Arabizi numeral-substitution patterns ──

    /**
     * Count occurrences of Arabizi-style numeral substitutions where digits
     * replace Arabic letters: 3=ع, 5=خ, 7=ح, 9=ق, 2=ء/ؤ, 6=ط, 8=ذ,
     * and mixed Latin+digit patterns like "3amel" (عمل) or "7aga" (حاجة).
     */
    private fun countArabiziPatterns(text: String): Int {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        var count = 0
        for (word in words) {
            if (ARABIZI_PATTERN.containsMatchIn(word)) count++
        }
        return count
    }

    private val ARABIZI_PATTERN = Regex(
        """\d+[a-zA-Z]|[a-zA-Z]+\d""",
        RegexOption.IGNORE_CASE
    )

    // ── Code-switch point detection ──

    private fun detectCodeSwitchPoints(text: String): List<Int> {
        val points = mutableListOf<Int>()
        var prevType: CharType = CharType.OTHER

        for (i in text.indices) {
            val c = text[i]
            val currentType = when {
                isArabicScriptChar(c) -> CharType.ARABIC
                c.isLetter() && c.code in 0x41..0x7A -> CharType.LATIN
                c.isDigit() && i > 0 && text[i - 1].isLetter() -> prevType
                else -> CharType.OTHER
            }
            if (currentType != prevType && currentType != CharType.OTHER && prevType != CharType.OTHER) {
                points.add(i)
            }
            if (currentType != CharType.OTHER) prevType = currentType
        }
        return points
    }

    private enum class CharType { ARABIC, LATIN, OTHER }

    // ── Register inference ──

    private fun inferRegister(egyptianMarkers: List<String>, arabiziCount: Int, text: String): String {
        val lower = text.lowercase()
        val hasInformalMarkers = egyptianMarkers.isNotEmpty() || arabiziCount > 0
        val hasArabicScript = hasArabicScript(text)
        val hasFormalArabic = hasArabicScript && FORMAL_ARABIC_MARKERS.any { lower.contains(it) }
        val hasCasualEnglish = CASUAL_ENGLISH_MARKERS.any { lower.contains(it) }

        return when {
            hasInformalMarkers -> "informal"
            hasFormalArabic -> "formal"
            hasCasualEnglish -> "informal"
            hasArabicScript -> "formal"
            else -> "neutral"
        }
    }

    private val FORMAL_ARABIC_MARKERS = listOf(
        "الذي", "التي", "كما", "ولكن", "بما أن", "إذن"
    )

    private val CASUAL_ENGLISH_MARKERS = listOf(
        "lol", "omg", "btw", "gonna", "wanna", "yeah", "nah", "yep", "nope"
    )
}
