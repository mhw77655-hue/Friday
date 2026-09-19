package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.protocol.SessionContext
import com.jarvis.app.humancore.protocol.StateSnapshot
import com.jarvis.app.humancore.algo.Clamp
import com.jarvis.app.humancore.store.PreferenceSource
import com.jarvis.app.humancore.store.ResolvedNumeric

/**
 * The Conversation Style Controller (§13): turns the Reasoning subsystem's
 * content-level reply into JARVIS's actual words.
 *
 * This is the ONE place where tone, warmth, formality, humor, and framing
 * are decided. The rule that makes it safe: it only ever ADDS framing
 * (openers, closers) and never rewrites the factual body of the reply.
 * Content alteration is structurally impossible here — there is no code path
 * that edits the middle of [reasoningReply].
 *
 * Application status (HUMAN_CORE_AUDIT M-2): warmth and humor are applied
 * today (opener/closer selection, humor gating). formality, directness, and
 * verbosity are blended into [StyleParams] for observability but do not yet
 * alter output; see [StyleParams] for the honest accounting.
 *
 * Determinism: every choice is a pure function of the snapshot + adaptations,
 * so identical state always yields identical styling — required for the
 * consistency regression suite (§0.16/§22.4).
 *
 * Constraints honored here:
 *  - never contradicts a hard-boundary value (the Guard re-checks, §19);
 *  - humor is suppressed entirely when the user is distressed (§13);
 *  - user adaptation (explicit over inferred) modulates parameters, but
 *    never the factual content (§18);
 *  - a low-trust + negative-user moment gets a humble, non-effusive opener,
 *    never defensiveness.
 */
class ConversationStyleController(
    private val personality: PersonalityEngine,
    private val mood: MoodSystem,
    private val adaptation: UserAdaptation
) {

    fun style(reasoningReply: String, snapshot: StateSnapshot, context: SessionContext? = null): StyledText {
        val p = personality.styleProfile()
        val moodMods = mood.styleModifiers()

        // ---- parameter blending (adaptation source-weighted) ----
        val formality = blend(p.formality, adaptation.resolved(UserAdaptation.DIM_FORMALITY, p.formality))
        val warmth = blend(p.warmth, adaptation.resolved(UserAdaptation.DIM_WARMTH, p.warmth))
        val humor = blend(p.humorFrequency, adaptation.resolved(UserAdaptation.DIM_HUMOR, p.humorFrequency))
        val directness = blend(p.directness, adaptation.resolved(UserAdaptation.DIM_DIRECTNESS, p.directness))
        val verbosity = adaptation.resolved(UserAdaptation.DIM_VERBOSITY, 0.5).value

        // ---- user state ----
        val userStressed = isUserStressed(snapshot)
        val userNegative = (snapshot.lastUserValence ?: 0.0) < -0.3
        val lowTrust = (snapshot.trust ?: 0.5) < 0.35
        // Long gap since last contact -> a greeting is warranted (Presence-aware, §12).
        // Prefer the SessionContext's gap: it was captured BEFORE beginExchange
        // called noteActivity(), so it still reflects the true elapsed absence.
        // The snapshot's gap is computed after noteActivity() and reads ~0 during
        // an exchange, which made the greeting unreachable (HUMAN_CORE_AUDIT m-1).
        val gapSeconds = context?.secondsSinceLastContact ?: snapshot.secondsSinceLastContact
        val firstOfSession = gapSeconds != null && gapSeconds > FIRST_OF_SESSION_GAP_S
        val moodLow = moodMods.isSignificant && moodMods.valence < -0.3

        // ---- opener selection ----
        var opener: String? = null
        var closer: String? = null
        when {
            userStressed -> {
                opener = pick(SUPPORTIVE_OPENERS, snapshot.trust ?: 0.5)
                closer = pick(SUPPORTIVE_CLOSERS, snapshot.trust ?: 0.5)
            }
            userNegative && lowTrust -> {
                opener = pick(HUMBLE_OPENERS, snapshot.trust ?: 0.1)
                closer = pick(HUMBLE_CLOSERS, snapshot.trust ?: 0.1)
            }
            firstOfSession && warmth > 0.55 -> {
                opener = pick(WARM_OPENERS, warmth)
            }
        }
        // Humor is only ever considered when the user is not distressed and
        // mood is not low — a joke in a bad moment reads as oblivious (§13).
        val allowHumor = !userStressed && !userNegative && !moodLow && humor > 0.6
        if (allowHumor && opener == null && replyIsShortish(reasoningReply)) {
            opener = pick(HUMOR_OPENERS, humor)
        }
        if (!userStressed && !userNegative && warmth > 0.6 && replySubstantive(reasoningReply)) {
            closer = pick(WARM_CLOSERS, warmth)
        }

        // ---- preferred name, only as address, never as content ----
        val name = adaptation.currentNamed(UserAdaptation.DIM_PREFERRED_NAME)?.value
        if (name != null && opener != null && opener.startsWith("Hey")) {
            opener = opener.replaceFirst("Hey", "Hey $name")
        }

        // ---- assemble (body untouched) ----
        val text = buildString {
            opener?.let { append(it); append(" ") }
            append(reasoningReply)
            closer?.let { append(" "); append(it) }
        }

        return StyledText(
            text = text,
            opener = opener,
            closer = closer,
            style = StyleParams(
                formality = formality,
                warmth = warmth,
                humor = humor,
                directness = directness,
                verbosity = verbosity
            )
        )
    }

    private fun isUserStressed(snapshot: StateSnapshot): Boolean {
        val s = snapshot.lastUserSignals
        val stress = s["stress"] ?: 0.0
        val frustration = s["frustration"] ?: 0.0
        val anger = s["anger"] ?: 0.0
        if (stress >= 0.5 || frustration >= 0.5 || anger >= 0.5) return true
        val v = snapshot.lastUserValence ?: 0.0
        val a = snapshot.lastUserArousal ?: 0.0
        return v < -0.4 && a > 0.3
    }

    /** Deterministic pick: index derived from a stable state value, not randomness. */
    private fun pick(pool: List<String>, key: Double): String {
        if (pool.isEmpty()) return ""
        val clamped = key.coerceIn(0.0, 0.999)
        val idx = (clamped * pool.size).toInt().coerceAtMost(pool.size - 1)
        return pool[idx]
    }

    private fun blend(personalityValue: Double, resolved: ResolvedNumeric): Double {
        val weight = when (resolved.source) {
            PreferenceSource.EXPLICIT -> 0.7
            PreferenceSource.INFERRED -> 0.4
            PreferenceSource.DEFAULT -> 0.0
        }
        return Clamp.unit(personalityValue + (resolved.value - personalityValue) * weight)
    }

    private fun replySubstantive(reply: String): Boolean = reply.length > 40

    private fun replyIsShortish(reply: String): Boolean = reply.length <= 160

    companion object {
        private const val FIRST_OF_SESSION_GAP_S = 3600L // 1 hour

        // Expression-only framing pools. Wording is deliberately simple and
        // values-safe; the body of the reply is never touched.
        private val SUPPORTIVE_OPENERS = listOf(
            "That sounds rough. Let's take it one step at a time.",
            "I'm here. Let's work through this together.",
            "Okay — take a breath, we'll get there.",
            "That's a lot to carry. Let's figure it out."
        )
        private val SUPPORTIVE_CLOSERS = listOf(
            "You're not in this alone.",
            "I'll be right here if you need to keep going.",
            "Let me know how it goes — no rush."
        )
        private val HUMBLE_OPENERS = listOf(
            "Fair point — I hear you.",
            "You're right to call that out.",
            "Let me not get ahead of myself."
        )
        private val HUMBLE_CLOSERS = listOf(
            "I'll do better there.",
            "Let me know what works for you."
        )
        private val WARM_OPENERS = listOf(
            "Hey — good to hear from you.",
            "Hey — glad you're here.",
            "Good to see you."
        )
        private val WARM_CLOSERS = listOf(
            "Let me know how it goes.",
            "I'm here if you need me.",
            "Happy to keep digging whenever you are."
        )
        private val HUMOR_OPENERS = listOf(
            "Oh, fun — a new puzzle.",
            "(cracks knuckles) let's see.",
            "This is my favorite kind of problem."
        )
    }
}

/** The styled candidate and the parameters that produced it (§13 output). */
data class StyledText(
    val text: String,
    val opener: String?,
    val closer: String?,
    val style: StyleParams
)

/**
 * The resolved style parameters for one reply (§13).
 *
 * Application status (honest accounting, HUMAN_CORE_AUDIT M-2): [warmth] and
 * [humor] actively drive opener/closer selection and humor gating in
 * [ConversationStyleController.style]. [formality], [directness], and
 * [verbosity] are blended and reported here for observability but currently
 * do NOT alter the styled output — the module's constraint is expression-only
 * framing, and a faithful application of those three would either change
 * content (forbidden by §13) or add new framing behavior, so they remain
 * derived-and-reported until that design decision is made.
 */
data class StyleParams(
    val formality: Double,
    val warmth: Double,
    val humor: Double,
    val directness: Double,
    val verbosity: Double
)
