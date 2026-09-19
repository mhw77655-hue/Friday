package com.jarvis.app.humancore.mod

/**
 * Small shared, stateless text heuristics used by more than one Human Core
 * module. Centralized so attribution logic ("is the user talking about
 * JARVIS?") is defined once and stays consistent everywhere it is used —
 * Trust Modeling and the Internal Dialogue Engine both depend on it.
 */
object TextAnalysis {

    private val MENTION_PATTERNS = listOf(
        "you", "jarvis", "your answer", "your response", "your advice", "that advice",
        "your help", "the ai", "this ai", "chatbot", "you keep", "you always"
    )

    /** Does the message plausibly reference JARVIS or JARVIS's behavior? */
    fun mentionsJarvis(text: String): Boolean {
        val lower = text.lowercase()
        return MENTION_PATTERNS.any { lower.contains(it) }
    }

    /** A reply that is long enough to count as "verbose" for adaptation logic. */
    fun isLongReply(reply: String): Boolean = reply.length > 240
}
