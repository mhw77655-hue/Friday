package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.protocol.AffectRead
import com.jarvis.app.humancore.protocol.SessionContext
import java.util.ArrayDeque

/**
 * The Emotional Intelligence module (§7) — the PRIMARY input to the Human
 * Core's Perception Pass.
 *
 * It reads the USER's emotional state from input text (with optional prosody
 * metadata from the mobile voice pipeline, §0.14) and emits an [AffectRead].
 * This is the module that "guesses what the user is feeling" — deliberately
 * heuristic and low-cost by default (§7 Algorithms: the primary pass must be
 * heuristic; a model call may refine later if one is available within latency
 * budget, never on the critical path).
 *
 * Rules honored here:
 *  - named signals are confidence scores, not binary flags;
 *  - "unknown" is a valid, normalized output (low confidence), never an
 *    error or an empty result (§7 Failure handling);
 *  - output feeds the Social Intelligence module and Relationship Modeling;
 *    it is never mistaken for JARVIS's own mood (§6/§7 separation).
 */
class EmotionalIntelligence(private val clock: () -> Long) {

    private val window = ArrayDeque<AffectRead>()
    private val windowLimit = 10
    private val lock = Any()

    /** Rolling bounded window of recent reads — used for trend detection (§7). */
    fun recentReads(): List<AffectRead> = synchronized(lock) { window.toList() }

    /**
     * Produce an affect read for one inbound message.
     *
     * @param message   the user's text (plain for Telegram; voice-derived text for mobile).
     * @param context   session context carrying optional prosody metadata.
     */
    fun read(message: String, context: SessionContext? = null, now: Long = clock()): AffectRead {
        val text = message.lowercase()
        val tokens = text.split(TOKEN_SPLIT).filter { it.isNotBlank() }

        // ---- signal counts (single-word sets match tokens; phrase sets match substrings) ----
        val pos = countWords(tokens, POSITIVE_WORDS) + countPhrases(text, POSITIVE_PHRASES)
        val neg = countWords(tokens, NEGATIVE_WORDS) + countPhrases(text, NEGATIVE_PHRASES)
        val frustration = countWords(tokens, FRUSTRATION_WORDS) + countPhrases(text, FRUSTRATION_PHRASES)
        val stress = countWords(tokens, STRESS_WORDS) + countPhrases(text, STRESS_PHRASES)
        val sadness = countWords(tokens, SADNESS_WORDS) + countPhrases(text, SADNESS_PHRASES)
        val anger = countWords(tokens, ANGER_WORDS) + countPhrases(text, ANGER_PHRASES)
        val excitement = countWords(tokens, EXCITEMENT_WORDS) + countPhrases(text, EXCITEMENT_PHRASES)
        val gratitude = countWords(tokens, GRATITUDE_WORDS) + countPhrases(text, GRATITUDE_PHRASES)
        val sarcasm = countPhrases(text, SARCASTIC_PHRASES)
        val intensifiers = countWords(tokens, INTENSIFIERS)

        // ---- valence: ratio of positive to negative signal, length-independent ----
        val weightedPos = pos + intensifiers.coerceAtMost(pos) * 0.5
        val weightedNeg = neg + intensifiers.coerceAtMost(neg) * 0.5
        val valence = if (weightedPos + weightedNeg > 0) {
            (weightedPos - weightedNeg) / (weightedPos + weightedNeg)
        } else 0.0

        // ---- arousal: active signals minus de-activating ones ----
        val active = excitement + anger + stress
        val calm = sadness
        var arousal = if (active + calm > 0) (active - calm).toDouble() / (active + calm) else 0.0
        if (text.contains("!!!") || text.contains("!?") || isShouting(message)) {
            arousal = (arousal + 0.25).coerceIn(-1.0, 1.0)
        }

        // ---- named signals as confidence scores (0..1) ----
        val signals = linkedMapOf(
            "frustration" to signalScore(frustration),
            "stress" to signalScore(stress),
            "sadness" to signalScore(sadness),
            "anger" to signalScore(anger),
            "excitement" to signalScore(excitement),
            "gratitude" to signalScore(gratitude),
            "sarcasm" to signalScore(sarcasm)
        )

        // ---- optional prosody refinement (§0.14) ----
        var prosodyUsed = false
        val prosody = context?.prosody
        if (prosody != null) {
            prosodyUsed = true
            prosody.marker?.let { marker ->
                val signal = PROSODY_TO_SIGNAL[marker]
                if (signal != null) {
                    signals[signal] = (signals[signal] ?: 0.0).coerceAtLeast(0.5)
                }
            }
            if (prosody.paceFast == true || prosody.loud == true) {
                arousal = (arousal + 0.2).coerceIn(-1.0, 1.0)
            }
        }

        // ---- confidence: total signal relative to a small constant ----
        val totalSignal = pos + neg + frustration + stress + sadness + anger + excitement + gratitude + sarcasm
        var confidence = if (totalSignal > 0) (totalSignal / 3.0).coerceIn(0.0, 1.0) else 0.0
        if (prosodyUsed) confidence = (confidence + 0.15).coerceAtMost(1.0)
        if (tokens.size < 4 && totalSignal == 0) confidence = 0.1 // short, signal-free -> essentially unknown

        val read = AffectRead(
            valence = com.jarvis.app.humancore.algo.Clamp.unitSymmetric(valence),
            arousal = com.jarvis.app.humancore.algo.Clamp.unitSymmetric(arousal),
            confidence = confidence.coerceIn(0.0, 1.0),
            signals = signals,
            trend = computeTrend(),
            prosodyUsed = prosodyUsed,
            ts = now
        )

        synchronized(lock) {
            window.addLast(read)
            while (window.size > windowLimit) window.removeFirst()
        }
        return read
    }

    private fun computeTrend(): String? {
        val reads = synchronized(lock) { window.toList() }
        if (reads.size < 6) return null
        val recent = reads.takeLast(3)
        val older = reads.dropLast(3).takeLast(4)
        val recentValence = recent.map { it.valence }.average()
        val olderValence = older.map { it.valence }.average()
        val recentArousal = recent.map { it.arousal }.average()
        val olderArousal = older.map { it.arousal }.average()
        val vDiff = recentValence - olderValence
        val aDiff = recentArousal - olderArousal
        return when {
            vDiff <= -0.3 && aDiff >= 0.2 -> "user has seemed more stressed across recent exchanges"
            vDiff <= -0.3 -> "user has seemed more negative across recent exchanges"
            vDiff >= 0.3 && aDiff >= 0.2 -> "user has seemed more energized and positive recently"
            vDiff >= 0.3 -> "user has seemed more positive across recent exchanges"
            else -> null
        }
    }

    private fun signalScore(matches: Int): Double = (matches / 2.0).coerceIn(0.0, 1.0)

    private fun countWords(tokens: List<String>, words: Set<String>): Int {
        var n = 0
        for (t in tokens) if (t in words) n++
        return n
    }

    private fun countPhrases(text: String, phrases: Set<String>): Int {
        var n = 0
        for (p in phrases) if (text.contains(p)) n++
        return n
    }

    /**
     * Shouting = an all-caps word of length >= 3 in the RAW message. The
     * tokens used elsewhere are lowercased, so the uppercase check must run on
     * the original text (HUMAN_CORE_AUDIT m-2).
     */
    private fun isShouting(rawMessage: String): Boolean =
        rawMessage.split(Regex("[^A-Za-z']+")).any { it.length >= 3 && it.all { c -> c.isLetter() && c.isUpperCase() } }

    companion object {
        private val TOKEN_SPLIT = Regex("[^a-z']+")

        private val POSITIVE_WORDS = setOf(
            "great", "awesome", "amazing", "wonderful", "excellent", "fantastic", "brilliant",
            "love", "like", "loved", "liked", "happy", "glad", "thanks", "thank", "appreciate",
            "nice", "good", "beautiful", "perfect", "enjoy", "enjoyed", "cool", "super", "best",
            "excited", "favorite", "helpful", "works", "working", "solved", "fixed", "yay",
            "impressed", "relieved", "relief"
        )
        private val POSITIVE_PHRASES = setOf("can't wait", "looking forward", "look forward", "great news", "love it")

        private val NEGATIVE_WORDS = setOf(
            "bad", "hate", "terrible", "awful", "horrible", "sucks", "worst", "wrong", "broken",
            "useless", "annoying", "frustrating", "stupid", "disappointed", "unhappy", "sad",
            "tired", "sick", "hurt", "pain", "angry", "mad", "furious", "upset", "pissed",
            "stressed", "overwhelmed", "anxious", "worried", "scared", "afraid", "lonely",
            "depressed", "down", "crying", "nightmare", "disaster", "fail", "failed", "failure",
            "problem", "problems", "issue", "issues", "bug", "crashed", "crash", "ridiculous",
            "unhelpful", "cannot", "frustrated", "hopeless", "exhausted", "annoyed"
        )
        private val NEGATIVE_PHRASES = setOf("can't stand", "so tired of", "fed up with", "sick of")

        private val FRUSTRATION_WORDS = setOf(
            "frustrating", "frustrated", "annoying", "annoyed", "ugh", "argh", "seriously",
            "useless", "stupid", "damn", "whatever", "ridiculous", "absurd"
        )
        private val FRUSTRATION_PHRASES = setOf("fed up", "not again", "sick of", "tired of")

        private val STRESS_WORDS = setOf(
            "stressed", "overwhelmed", "anxious", "worried", "panic", "deadline", "deadlines",
            "pressure", "exhausted", "behind"
        )
        private val STRESS_PHRASES = setOf("too much", "can't keep up", "burned out", "burnt out", "over my head")

        private val SADNESS_WORDS = setOf(
            "sad", "unhappy", "lonely", "depressed", "disappointed", "down", "crying",
            "lost", "grief", "hurt", "heartbroken", "miserable", "hopeless"
        )
        private val SADNESS_PHRASES = setOf("down in the dumps", "feeling blue")

        private val ANGER_WORDS = setOf(
            "angry", "mad", "pissed", "furious", "hate", "fuming", "rage",
            "ridiculous", "infuriating", "livid"
        )
        private val ANGER_PHRASES = setOf("fed up with", "had it with", "at the end of my rope")

        private val EXCITEMENT_WORDS = setOf(
            "excited", "awesome", "amazing", "yay", "finally", "hyped",
            "fantastic", "brilliant", "woohoo", "amazing"
        )
        private val EXCITEMENT_PHRASES = setOf("can't wait", "looking forward", "great news", "so excited")

        private val GRATITUDE_WORDS = setOf(
            "thanks", "thank", "appreciate", "grateful", "helpful", "cheers"
        )
        private val GRATITUDE_PHRASES = setOf("thank you", "really appreciate")

        /** Sarcasm is phrase-only — single words like "great" are ambiguous and must not trigger it. */
        private val SARCASTIC_PHRASES = setOf(
            "oh great", "of course", "just great", "so helpful", "just what i needed",
            "yeah sure", "very helpful", "thanks a lot", "great job", "that's perfect",
            "i'm so happy", "how wonderful"
        )

        private val INTENSIFIERS = setOf(
            "really", "so", "very", "extremely", "absolutely", "totally", "super", "completely"
        )

        private val PROSODY_TO_SIGNAL = mapOf(
            "frustrated" to "frustration",
            "frustration" to "frustration",
            "angry" to "anger",
            "excited" to "excitement",
            "happy" to "excitement",
            "sad" to "sadness",
            "stressed" to "stress"
        )
    }
}
