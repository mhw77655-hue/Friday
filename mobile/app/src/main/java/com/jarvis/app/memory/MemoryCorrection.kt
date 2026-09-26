package com.jarvis.app.memory

/**
 * CORRECTION-CHAIN: the minimal, auditable lexicon that recognises a user
 * CORRECTION of something they already stated, in English and Egyptian Arabic.
 *
 * The graph already had a supersession chain — a new fact with the same
 * subject+predicate closes the previous node's validity window
 * ([MemoryGraphStore.addFact]) — but nothing in the live turn path knew whether a
 * turn was a correction, so every statement was treated identically. This
 * object supplies that one missing fact and nothing more:
 *
 *  - [parse] returns the ASSERTION the user is correcting to, with the
 *    correction marker removed ("no, the meeting is at 6" ->
 *    "the meeting is at 6"), so the stored fact is the claim itself rather than
 *    the claim prefixed by a negation that no downstream reader can interpret.
 *  - a non-correction returns null and the caller stores the turn verbatim, so
 *    this object can never mangle an ordinary statement.
 *
 * Deliberately a lexicon and not a model: a marker either leads the turn (as a
 * whole word, English or Arabic) or it does not, and the same code path serves
 * both languages. It follows the existing directive-lexicon precedent in
 * [com.jarvis.app.memory.provenance.MemoryForgetter].
 */
object MemoryCorrection {

    /** A turn recognised as a correction: the bare assertion plus the marker found. */
    data class Parsed(val assertion: String, val marker: String)

    /**
     * English markers that lead a correction. The trailing lookahead requires a
     * word boundary the Java regex `\b` cannot express for Arabic script
     * (`\w` is ASCII-only there), so both patterns use an explicit
     * "whitespace/punctuation or end of turn" lookahead instead.
     */
    private val ENGLISH_MARKER = Regex(
        "^(?:actually|instead|correction|wrong|incorrect|i\\s+meant|meant|no|not)" +
            "(?=[\\s,.:;!-]|$)",
        RegexOption.IGNORE_CASE
    )

    /** Egyptian Arabic markers: لا (no) / مش (not) / بدل (instead) / … */
    private val ARABIC_MARKER = Regex(
        "^(?:لا|مش|بدل|خطأ|الصحيح|تصحيح|بصح|انا\\s+قصدي)" +
            "(?=[\\s,.:؛،!؟-]|$)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Phrasings that merely START with a marker word without being a correction.
     * "not sure …" and "no idea …" are hedges about the CURRENT turn, not
     * retractions of a stored one; treating them as corrections would delete the
     * hedge word and store a mangled assertion.
     */
    private val NOT_CORRECTIONS = listOf(
        "not sure",
        "not certain",
        "not exactly",
        "no idea",
        "no one",
        "no way",
        "not really"
    )

    /** True when [text] is recognised as a correction of an earlier statement. */
    fun isCorrection(text: String): Boolean = parse(text) != null

    /**
     * Parse [text] as a correction, or null when it is an ordinary statement.
     * Null is also returned when stripping the marker would leave nothing behind,
     * so a bare "no" is never turned into an empty stored fact.
     */
    fun parse(text: String): Parsed? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (NOT_CORRECTIONS.any { lower.startsWith(it) }) return null

        val match = ENGLISH_MARKER.find(trimmed) ?: ARABIC_MARKER.find(trimmed) ?: return null
        val assertion = trimmed
            .removeRange(match.range)
            .trim()
            .trim(',', '.', ':', ';', '!', '؟', '،')
            .trim()
        if (assertion.isBlank()) return null
        return Parsed(assertion = assertion, marker = match.value.trim())
    }
}
