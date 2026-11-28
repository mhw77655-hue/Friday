package ai.friday.assistant.cognitive.seed

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.format.DateTimeFormatter

/**
 * Friday's small model-independent language substrate.
 *
 * It composes a few general English patterns into grounded intents and
 * structured local facts. It is not a language model and does not pretend to
 * understand arbitrary prose; optional models can extend expression without
 * owning identity or memory.
 */
class FridayModelFreeMind(
    private val substrate: FridaySeedSubstrate,
    private val identityName: String = "Friday",
    private val clock: Clock = Clock.systemDefaultZone(),
    /** Ablation switch for proving that later behavior depends on persisted learning. */
    private val learningEnabled: Boolean = true
) {
    fun observe(experienceText: String) {
        if (!learningEnabled) return
        val features = features(experienceText)
        if (features.isNotEmpty()) substrate.observe(features)
    }

    fun observeOutcome(input: String, observedOutcome: String) {
        if (!learningEnabled) return
        val context = features(input)
        val outcome = features(observedOutcome)
        if (context.isNotEmpty() && outcome.isNotEmpty()) substrate.observeOutcome(context, outcome)
    }

    fun respond(input: String): String {
        val normalized = input.trim().lowercase()
        val associations = if (learningEnabled) substrate.infer(features(input), limit = 8) else emptyMap()
            .filterKeys { it.startsWith("word:") }
            .keys.map { it.removePrefix("word:") }

        MY_NAME.matchEntire(input.trim())?.groupValues?.getOrNull(1)?.let { rawName ->
            val name = rawName.trim().trimEnd('.', '!', '?').take(80)
            if (name.isNotBlank() && learningEnabled && substrate.rememberFact("user_name", name)) {
                return "I'll remember that your name is $name. I stored that fact in my local memory."
            }
        }

        LIKE.matchEntire(input.trim())?.groupValues?.getOrNull(1)?.let { rawValue ->
            val value = rawValue.trim().trimEnd('.', '!', '?').take(120)
            if (value.isNotBlank() && learningEnabled && substrate.rememberFact("user_likes", value)) {
                return "I've noted that you like $value. You can correct me if that changes."
            }
        }

        DISLIKE.matchEntire(input.trim())?.groupValues?.getOrNull(1)?.let { rawValue ->
            val value = rawValue.trim().trimEnd('.', '!', '?').take(120)
            if (value.isNotBlank() && learningEnabled && substrate.rememberFact("user_dislikes", value)) {
                return "I've noted that you don't like $value. You can correct me if that changes."
            }
        }

        return when {
            GREETING.containsMatchIn(normalized) ->
                "Hey. I'm here."

            ASK_MY_NAME.containsMatchIn(normalized) -> {
                val name = substrate.recallFact("user_name")
                if (name != null) "Your name is $name. You told me that, and I kept it in local memory."
                else "You haven't told me your name yet."
            }

            ASK_LIKES.containsMatchIn(normalized) -> {
                val likes = substrate.recallFact("user_likes")
                if (likes != null) "You told me that you like $likes."
                else "I don't have a preference from you stored yet."
            }

            ASK_DISLIKES.containsMatchIn(normalized) -> {
                val dislikes = substrate.recallFact("user_dislikes")
                if (dislikes != null) "You told me that you don't like $dislikes."
                else "I don't have a dislike from you stored yet."
            }

            HOW_ARE_YOU.containsMatchIn(normalized) ->
                "I'm here and running locally. My identity and experience memory do not depend on a language model. My open-ended understanding is still limited, and I won't pretend otherwise."

            THANKS.containsMatchIn(normalized) ->
                "You're welcome. I'm here."

            WHAT_CAN_YOU_DO.containsMatchIn(normalized) ->
                "I can keep my local identity, record experiences, recall facts you've explicitly told me, and use phone capabilities that are actually connected and permitted. Broad open-ended reasoning is still developing."

            TIME_QUERY.containsMatchIn(normalized) ->
                "It's " + clock.instant().atZone(clock.zone).toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a")) + "."

            DATE_QUERY.containsMatchIn(normalized) ->
                "Today is " + clock.instant().atZone(clock.zone).toLocalDate().format(DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu")) + "."

            calculateArithmetic(input) != null ->
                calculateArithmetic(input)!!

            IDENTITY.containsMatchIn(normalized) ->
                "I'm $identityName. My identity and continuity live in this app, not inside a language model. Models can extend what I can understand or express, but they are not what keeps my local runtime and experiences alive."

            LEARNING.containsMatchIn(normalized) -> {
                val facts = listOfNotNull(
                    substrate.recallFact("user_name")?.let { "your name is $it" },
                    substrate.recallFact("user_likes")?.let { "you like $it" },
                    substrate.recallFact("user_dislikes")?.let { "you don't like $it" }
                )
                val links = associations.distinct().take(6)
                when {
                    facts.isNotEmpty() -> "I remember that " + facts.joinToString("; ") + "."
                    links.isNotEmpty() -> "My local experience substrate has associated these earlier signals: " +
                        links.joinToString(", ") + ". These are learned associations, not verified meanings or facts."
                    else -> "I don't yet have enough local experience to report a useful learned association. I won't invent one."
                }
            }

            associations.isNotEmpty() ->
                "I notice an association with earlier experiences: " +
                    associations.distinct().take(5).joinToString(", ") +
                    ". These are learned feature links, not verified meanings; I don't yet know why they are connected."

            else ->
                "I received your message and recorded its local feature pattern. I don't yet have enough grounded language structure to interpret this request reliably without an optional language model. I won't pretend that I understood it."
        }
    }

    private fun calculateArithmetic(input: String): String? {
        val match = ARITHMETIC.matchEntire(input.trim()) ?: return null
        return runCatching {
            val left = BigDecimal(match.groupValues[1])
            val right = BigDecimal(match.groupValues[3])
            val result = when (match.groupValues[2]) {
                "+" -> left.add(right)
                "-" -> left.subtract(right)
                "*", "x", "×" -> left.multiply(right)
                "/" -> {
                    if (right.compareTo(BigDecimal.ZERO) == 0) return "Division by zero is undefined."
                    left.divide(right, 8, RoundingMode.HALF_UP).stripTrailingZeros()
                }
                else -> return null
            }
            "The answer is " + result.stripTrailingZeros().toPlainString() + "."
        }.getOrNull()
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
        private val GREETING = Regex("^(hi|hello|hey|hay|hiya|yo|good morning|good evening|good afternoon)(\\b|[.!?])")
        private val IDENTITY = Regex("\\b(who are you|what are you|your identity|are you friday|what is friday)\\b")
        private val LEARNING = Regex("\\b(what have you learned|what do you remember|what do you know about me|what do you remember about me|what do you recall|what did you learn|show your associations)\\b")
        private val MY_NAME = Regex("^my name is\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
        private val ASK_MY_NAME = Regex("\\b(what('?s| is) my name|do you know my name|who am i)\\b")
        private val LIKE = Regex("^i (?:really )?like\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
        private val DISLIKE = Regex("^i (?:really )?(?:do not|don't) like\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
        private val ASK_LIKES = Regex("\\b(what do i like|what are my likes)\\b")
        private val ASK_DISLIKES = Regex("\\b(what don't i like|what do i dislike)\\b")
        private val HOW_ARE_YOU = Regex("\\b(how are you|how are things with you)\\b")
        private val THANKS = Regex("^(thanks|thank you|cheers)(\\b|[.!?])")
        private val WHAT_CAN_YOU_DO = Regex("\\b(what can you do|what are your capabilities|what do you do)\\b")
        private val TIME_QUERY = Regex("\\b(what time is it|current time)\\b")
        private val DATE_QUERY = Regex("\\b(what day is it|what is today's date|what's today's date|today's date)\\b")
        private val ARITHMETIC = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*([+*/x×-])\\s*(-?\\d+(?:\\.\\d+)?)\\s*[?!.]?\\s*$", RegexOption.IGNORE_CASE)
    }
}
