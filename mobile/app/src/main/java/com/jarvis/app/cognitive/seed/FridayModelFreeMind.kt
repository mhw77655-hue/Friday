package com.jarvis.app.cognitive.seed

/**
 * Minimal model-free expression layer for the independent seed.
 *
 * It is intentionally honest about its limited natural-language capability.
 * It does not imitate a large language model or invent answers. It can identify
 * a few direct self/greeting/learning questions and expose associations learned
 * by [FridaySeedSubstrate]. Optional language resources can later enrich this
 * layer without owning the seed's continuity.
 */
class FridayModelFreeMind(
    private val substrate: FridaySeedSubstrate
) {
    fun observe(experienceText: String) {
        val features = features(experienceText)
        if (features.isNotEmpty()) substrate.observe(features)
    }

    fun observeOutcome(input: String, observedOutcome: String) {
        val context = features(input)
        val outcome = features(observedOutcome)
        if (context.isNotEmpty() && outcome.isNotEmpty()) substrate.observeOutcome(context, outcome)
    }

    fun respond(input: String): String {
        val normalized = input.trim().lowercase()
        val associations = substrate.infer(features(input), limit = 8)
            .filterKeys { it.startsWith("word:") }
            .keys.map { it.removePrefix("word:") }

        return when {
            GREETING.containsMatchIn(normalized) ->
                "I'm here. My local seed is running inside this app. I can preserve experiences and use the capabilities available locally; without an optional language model, my open-ended language understanding is limited."

            IDENTITY.containsMatchIn(normalized) ->
                "I'm Friday. My identity and continuity are intended to live in this app, not inside a language model. Models can extend what I can understand or express, but they are not what keeps my local runtime and experiences alive."

            LEARNING.containsMatchIn(normalized) && associations.isNotEmpty() ->
                "My local experience substrate has associated these earlier signals: " +
                    associations.distinct().take(6).joinToString(", ") +
                    ". These are learned associations, not verified meanings or facts."

            LEARNING.containsMatchIn(normalized) ->
                "I don't yet have enough local experience to report a useful learned association. I won't invent one."

            else ->
                "I received your message and recorded its local feature pattern. I can continue operating without a language model, but I cannot reliably interpret arbitrary natural language at human level with this initial seed. I won't pretend that I can."
        }
    }

    private fun features(text: String): Map<String, Double> {
        val words = WORD.findAll(text.lowercase())
            .map { it.value }
            .filter { it.length >= 3 }
            .distinct()
            .take(MAX_WORDS)
            .associate { "word:$it" to 1.0 }
        if (words.isEmpty()) return emptyMap()
        val extra = linkedMapOf<String, Double>()
        if (text.contains("?")) extra["form:question"] = 1.0
        if (text.contains("!")) extra["form:emphasis"] = 1.0
        if (text.length > 240) extra["shape:long"] = 1.0 else extra["shape:short"] = 1.0
        return words + extra
    }

    companion object {
        private const val MAX_WORDS = 36
        private val WORD = Regex("[\\p{L}\\p{N}]{3,}")
        private val GREETING = Regex("^(hi|hello|hey|good morning|good evening|good afternoon)(\\b|[.!?])")
        private val IDENTITY = Regex("\\b(who are you|what are you|your identity|are you friday|what is friday)\\b")
        private val LEARNING = Regex("\\b(what have you learned|what do you remember|what did you learn|show your associations)\\b")
    }
}
