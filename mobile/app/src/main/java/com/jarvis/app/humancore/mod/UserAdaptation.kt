package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.algo.Clamp
import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.AffectRead
import com.jarvis.app.humancore.store.AdaptationStore
import com.jarvis.app.humancore.store.NamedPreference
import com.jarvis.app.humancore.store.Preference
import com.jarvis.app.humancore.store.PreferenceSource
import com.jarvis.app.humancore.store.ResolvedNumeric

/**
 * The User Adaptation module (§18): the continuously-adjusted map of "how
 * this user prefers JARVIS to talk."
 *
 * Two sources, with a strict precedence (explicit beats inferred beats
 * default):
 *  - EXPLICIT: the user said so, in words. Highest confidence, highest
 *    precedence, durable until explicitly overridden. Detected from the
 *    message by [captureFromMessage].
 *  - INFERRED: JARVIS noticed a pattern (e.g. user repeatedly gets more
 *    frustrated after long replies). Lower confidence, lower precedence, and
 *    only meaningful after repeated signals — a single negative reply is not
 *    an inference.
 *
 * Persistence: all preferences live in the [AdaptationStore] (High tier for
 * explicit, Moderate for inferred — §18, §21.1) and survive process death
 * (HUMAN_CORE_AUDIT C-6). The module is a read/write façade over the store;
 * it keeps no in-memory copy of its own.
 *
 * Constraints honored here:
 *  - adaptation adjusts EXPRESSION, never identity, values, or the factual
 *    content of replies (§18 Constraints);
 *  - adaptation must not be able to contradict a hard-boundary value — the
 *    Consistency Guard enforces that downstream (§19);
 *  - naming ("call me X") is a user preference, never an identity/values
 *    change — it affects address only (§18).
 */
class UserAdaptation(
    private val store: AdaptationStore,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    // Thresholds that define "neutral" so we don't re-assert the same
    // preference every single message.
    private val explicitHigh: Double = 0.85
    private val explicitLow: Double = 0.15

    fun currentNumeric(dimension: String): Preference? = store.readNumeric()[dimension]

    fun currentNamed(dimension: String): NamedPreference? = store.readNamed()[dimension]

    fun allNumeric(): Map<String, Preference> = store.readNumeric()

    fun allNamed(): Map<String, NamedPreference> = store.readNamed()

    /**
     * Scan an inbound message for explicit preference statements. Returns the
     * captured preferences (so the Integration Pass can also log a
     * relationship significant-event of category "communication_preference",
     * §9).
     */
    fun captureFromMessage(text: String, now: Long = clock()): List<Preference> {
        val lower = text.lowercase()
        val captured = mutableListOf<Preference>()

        // verbosity
        if (lower.contains("more concise") || lower.contains("be concise") ||
            lower.contains("shorter") || lower.contains("keep it short") ||
            lower.contains("get to the point") || lower.contains("less detail") ||
            lower.contains("too long") || lower.contains("stop rambling")
        ) {
            captured += setExplicit(DIM_VERBOSITY, explicitLow, "explicit request: concise")
        }
        if (lower.contains("more detail") || lower.contains("be detailed") ||
            lower.contains("explain more") || lower.contains("longer") ||
            lower.contains("more thorough") || lower.contains("elaborate")
        ) {
            captured += setExplicit(DIM_VERBOSITY, explicitHigh, "explicit request: thorough")
        }
        // formality
        if (lower.contains("less formal") || lower.contains("be casual") ||
            lower.contains("don't be so formal") || lower.contains("chill out") ||
            lower.contains("relax") || lower.contains("drop the formal")
        ) {
            captured += setExplicit(DIM_FORMALITY, explicitLow, "explicit request: casual")
        }
        if (lower.contains("more formal") || lower.contains("be professional") ||
            lower.contains("be proper")
        ) {
            captured += setExplicit(DIM_FORMALITY, explicitHigh, "explicit request: formal")
        }
        // warmth
        if (lower.contains("be warmer") || lower.contains("friendlier") ||
            lower.contains("less cold") || lower.contains("more warmth")
        ) {
            captured += setExplicit(DIM_WARMTH, explicitHigh, "explicit request: warm")
        }
        if (lower.contains("less warm") || lower.contains("more neutral") ||
            lower.contains("less chummy") || lower.contains("tone it down")
        ) {
            captured += setExplicit(DIM_WARMTH, explicitLow, "explicit request: neutral")
        }
        // humor
        if (lower.contains("be funnier") || lower.contains("more jokes") ||
            lower.contains("less serious") || lower.contains("make me laugh")
        ) {
            captured += setExplicit(DIM_HUMOR, explicitHigh, "explicit request: humorous")
        }
        if (lower.contains("fewer jokes") || lower.contains("less funny") ||
            lower.contains("stop being so funny") || lower.contains("be serious") ||
            lower.contains("no jokes")
        ) {
            captured += setExplicit(DIM_HUMOR, explicitLow, "explicit request: serious")
        }
        // directness
        if (lower.contains("be direct") || lower.contains("stop being vague") ||
            lower.contains("be blunt") || lower.contains("get straight to it") ||
            lower.contains("give it to me straight")
        ) {
            captured += setExplicit(DIM_DIRECTNESS, explicitHigh, "explicit request: direct")
        }
        // naming (how to address the user)
        val callMe = Regex("call me ([a-zA-Z0-9 _-]{1,24})").find(lower)
        if (callMe != null) {
            val name = callMe.groupValues[1].trim()
            // "call me when you're ready" / "call me back" are timing/action
            // phrasings, not naming requests — the captured token must be an
            // actual name, not a common non-name word (HUMAN_CORE_AUDIT m-8).
            if (name.isNotBlank() && name.length >= 2 && name.split(Regex("\\s+")).all { it !in NON_NAME_STOPWORDS }) {
                store.writeNamed(
                    DIM_PREFERRED_NAME,
                    NamedPreference(
                        dimension = DIM_PREFERRED_NAME,
                        value = name,
                        source = PreferenceSource.EXPLICIT,
                        confidence = 1.0,
                        ts = now
                    )
                )
                captured += Preference(DIM_PREFERRED_NAME, 0.0, source = PreferenceSource.EXPLICIT, confidence = 1.0, ts = now)
            }
        }

        captured.forEach { p ->
            if (p.dimension != DIM_PREFERRED_NAME) {
                bus.publish(HcEvent.AdaptationUpdated(ts = now, reason = p.reason, dimension = p.dimension))
            }
        }
        return captured
    }

    /**
     * Inferred adaptation: repeated evidence of the user reacting negatively
     * to a behavior nudges the corresponding dimension. Called by the
     * Integration Pass only after the message *and* the reaction are known
     * (§18).
     */
    fun infer(text: String, userAffect: AffectRead?, lastReplyWasLong: Boolean, now: Long = clock()) {
        if (userAffect == null || userAffect.isUnknown) return

        // Repeated frustration paired with a long reply -> prefer concise.
        val frustration = userAffect.signals["frustration"] ?: 0.0
        val anger = userAffect.signals["anger"] ?: 0.0
        val negativeReaction = frustration + anger

        if (negativeReaction >= 0.5 && lastReplyWasLong) {
            adjustInferred(DIM_VERBOSITY, -0.05, now, "user reacted negatively to a long reply")
        }
        // User consistently negative after humor -> reduce humor.
        if (negativeReaction >= 0.5) {
            adjustInferred(DIM_HUMOR, -0.03, now, "user reacted negatively during a light exchange")
        }
        // Warm/positive affect -> warmth slightly up.
        if (userAffect.signals["gratitude"]?.let { it >= 0.4 } == true ||
            userAffect.valence > 0.5
        ) {
            adjustInferred(DIM_WARMTH, 0.02, now, "user responded warmly")
        }
    }

    private fun adjustInferred(dimension: String, delta: Double, now: Long, reason: String) {
        val current = store.readNumeric()[dimension]
        if (current?.source == PreferenceSource.EXPLICIT) return // explicit always wins
        val base = current?.value ?: 0.5
        val newValue = Clamp.unit(base + delta)
        val confidence = (current?.confidence ?: 0.2).coerceAtMost(0.5)
        store.writeNumeric(dimension, Preference(dimension, newValue, PreferenceSource.INFERRED, confidence, now, reason))
        bus.publish(HcEvent.AdaptationUpdated(ts = now, reason = reason, dimension = dimension))
    }

    private fun setExplicit(dimension: String, value: Double, reason: String): Preference {
        val p = Preference(dimension, value, PreferenceSource.EXPLICIT, confidence = 0.95, ts = clock(), reason = reason)
        store.writeNumeric(dimension, p)
        return p
    }

    /** Resolved numeric value with precedence: explicit > inferred > default. */
    fun resolved(dimension: String, default: Double): ResolvedNumeric {
        val p = store.readNumeric()[dimension] ?: return ResolvedNumeric(default, PreferenceSource.DEFAULT)
        return ResolvedNumeric(p.value, p.source)
    }

    companion object {
        const val DIM_VERBOSITY = "verbosity"
        const val DIM_FORMALITY = "formality"
        const val DIM_WARMTH = "warmth"
        const val DIM_HUMOR = "humor"
        const val DIM_DIRECTNESS = "directness"
        const val DIM_PREFERRED_NAME = "preferredName"

        /** Words that follow "call me" in timing/action phrasings, never names. */
        private val NON_NAME_STOPWORDS = setOf(
            "when", "if", "after", "as", "once", "later", "back", "again",
            "soon", "before", "then", "you", "your", "maybe", "please", "tomorrow",
            "tonight", "now", "first", "later", "okay", "ok", "sometime", "tonight"
        )
    }
}
