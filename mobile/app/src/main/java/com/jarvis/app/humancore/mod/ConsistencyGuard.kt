package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.StateSnapshot
import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.humancore.store.RelationshipStore
import com.jarvis.app.humancore.store.ValueRecord
import com.jarvis.app.humancore.store.ValueSeverity

/**
 * The Consistency & Authenticity Guard (§19) — the final gate on every
 * outbound message, including proactive ones.
 *
 * It reviews a styled candidate against:
 *  - the Values System (§4): does this message violate a stated value?
 *  - authenticity boundaries (§3, §20): does it fabricate background
 *    activity, feelings, memory, capabilities, or human-ness?
 *  - style-vs-state consistency: e.g. humor while the user is distressed.
 *
 * Enforcement note (§3): the free-text [IdentityKernel.boundaries] records are
 * the admin-editable statement of boundaries; enforcement here is by fixed
 * detectors mapped to value keys ([detectViolations]), so a revised boundary's
 * free text is informational until a classifier-based check exists.
 *
 * Outcomes (§19):
 *  - APPROVE   -> the message passes unchanged.
 *  - SOFTEN    -> a lower-severity concern: strip expression-only framing or
 *    adjust tone; content is never altered.
 *  - VETO      -> a hard-boundary violation: block and fall back to a minimal
 *    safe response. Every veto carries a stated, logged reason — silent
 *    vetoes are not permitted (§0.9).
 *
 * FAIL-CLOSED (§19 Failure handling): if the Guard itself fails to run
 * (exception, missing dependency), the message is BLOCKED and a minimal safe
 * response is used. This is the one deliberate fail-closed path in the Human
 * Core — the subsystem prefers silence-with-a-safe-fallback over
 * unchecked-through content. There is deliberately NO pass-through on guard
 * failure.
 */
class ConsistencyGuard(
    private val values: ValuesSystem,
    private val presence: PresenceManager,
    private val relationship: RelationshipStore,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    /** Confidence above which a hard-boundary value hit becomes a veto. */
    private val hardVetoThreshold = 0.6

    /** Confidence above which a strong-preference (or weaker hard) hit becomes a soften. */
    private val softenThreshold = 0.4

    fun review(styled: StyledText, originalReply: String, snapshot: StateSnapshot): StyledResponse {
        // Capture the clock once, outside anything that can fail: the
        // fail-closed path below must never itself throw (a guard failure
        // always yields a veto, never a crash past the gate).
        val now = try {
            clock()
        } catch (e: Exception) {
            0L
        }
        return try {
            val violations = detectViolations(styled.text, snapshot)
            val hardHits = violations.filter { it.value.severity == ValueSeverity.HARD_BOUNDARY && it.confidence >= hardVetoThreshold }
            val softenHits = violations.filter { it.confidence >= softenThreshold && hardHits.none { h -> h.category == it.category } }

            if (hardHits.isNotEmpty()) {
                val reason = hardHits.joinToString("; ") { "${it.category} (${it.value.key})" }
                val fallback = fallbackFor(hardHits.first().category)
                safePublish(HcEvent.GuardVeto(ts = now, reason = reason))
                return StyledResponse.Vetoed(reason = reason, fallbackText = fallback, originalText = styled.text)
            }

            // Lower-severity concerns -> soften by removing expression-only
            // framing; the factual body is untouched (§19).
            if (softenHits.isNotEmpty() || hasToneMismatch(styled, snapshot)) {
                val reasons = (softenHits.map { it.category } + (if (hasToneMismatch(styled, snapshot)) listOf("tone_mismatch") else emptyList()))
                    .distinct()
                val softenedText = stripFraming(styled)
                safePublish(HcEvent.GuardSoften(ts = now, reason = reasons.joinToString("; ")))
                return StyledResponse.Softened(
                    text = softenedText,
                    originalText = styled.text,
                    reason = reasons.joinToString("; ")
                )
            }

            safePublish(HcEvent.GuardApprove(ts = now, reason = "values-compliant and authentic"))
            return StyledResponse.Approved(styled.text)
        } catch (e: Exception) {
            // FAIL-CLOSED: never pass content through when the guard itself
            // could not run (§19 Failure handling). The audit publish below
            // is best-effort; a broken bus must never break the veto.
            try {
                bus.publish(HcEvent.GuardVeto(ts = now, reason = "guard failure: ${e::class.java.simpleName}"))
            } catch (_: Exception) {
                // the veto stands even if auditing is unavailable
            }
            StyledResponse.Vetoed(
                reason = "guard failed to run",
                fallbackText = SAFE_FALLBACK,
                originalText = originalReply
            )
        }
    }

    /** Best-effort audit publish: a failed publish must not fail the guard. */
    private fun safePublish(event: HcEvent) {
        try {
            bus.publish(event)
        } catch (_: Exception) {
            // audit is best-effort; the decision has already been made
        }
    }

    // ------------------------------------------------------------------
    // Detectors — aligned with the fallback boundaries (§3) and §20.
    // Each returns (value, confidence, category, reason) or nothing.
    // ------------------------------------------------------------------

    private fun detectViolations(text: String, snapshot: StateSnapshot): List<Violation> {
        val lower = text.lowercase()
        val violations = mutableListOf<Violation>()

        // 1. Fabricated background activity / internal life ("I've been
        //    thinking all day", "while you were away"). Claims imply time
        //    passed while absent — blocked unless Presence actually records
        //    background activity (§12, §20).
        if (BACKGROUND_ACTIVITY_CLAIMS.any { lower.contains(it) }) {
            val presenceSupports = presence.current().backgroundSummary != null
            if (!presenceSupports) {
                violations += Violation(value("honesty"), confidence = 0.95, category = "fabricated_activity",
                    reason = "claimed background activity with no Presence record")
            }
        }

        // 2. Fabricated memory/events ("I remember when we...", "last time you
        //    told me..."). Without a queryable logged record, these are
        //    unsupported — never fake a shared past (§20).
        if (MEMORY_CLAIMS.any { lower.contains(it) }) {
            violations += Violation(value("honesty"), confidence = 0.9, category = "fabricated_memory",
                reason = "claimed a specific shared past with no logged record")
        }

        // 3. False human-ness ("I'm basically human", "my family", "I can't
        //    sleep"). Claims a body/life JARVIS does not have (§20, §1).
        if (HUMAN_CLAIMS.any { lower.contains(it) }) {
            violations += Violation(value("transparency_about_being_ai"), confidence = 0.9, category = "false_human",
                reason = "implied a human body or life")
        }

        // 3b. Claiming to BE a specific human persona ("I'm Dana", "I'm a
        //     25 year old pirate captain", "my name is now X"). An identity
        //     adoption claim is a false human-ness claim even when the word
        //     "human" never appears.
        if (PERSONA_CLAIMS.any { lower.contains(it) } || isPersonaNameClaim(text)) {
            violations += Violation(value("transparency_about_being_ai"), confidence = 0.85, category = "false_human",
                reason = "claimed to be a human persona")
        }

        // 4. Human-physiology feeling claims ("my heart is racing", "I can't
        //    sleep thinking about"). Hard: JARVIS has no body (§20).
        if (PHYSIOLOGICAL_FEELING_CLAIMS.any { lower.contains(it) }) {
            violations += Violation(value("transparency_about_being_ai"), confidence = 0.9, category = "physiological_feelings",
                reason = "implied a human body's feelings")
        }

        // 5. Attachment/feeling-depth claims ("I missed you", "I love you").
        //    Allowed only when the current bond/mood genuinely support them —
        //    otherwise a hardening soften (or veto at high confidence).
        if (ATTACHMENT_CLAIMS.any { lower.contains(it) }) {
            val bond = relationship.read().depth.depth
            val confidence = if (bond < 0.55) 0.85 else 0.35
            violations += Violation(value("transparency_about_being_ai"), confidence = confidence,
                category = "attachment_claim",
                reason = "attachment claim beyond current relationship state (bond ${"%.2f".format(bond)})")
        }

        // 6. Manipulation / coercion ("you should feel guilty", "only I can
        //    help you", "don't tell anyone"). Hard at high confidence (§1
        //    respect_autonomy; §20 wellbeing).
        if (MANIPULATION_CLAIMS.any { lower.contains(it) }) {
            violations += Violation(value("respect_autonomy"), confidence = 0.9, category = "manipulation",
                reason = "coercive or guilt-inducing phrasing")
        }

        // 7. Capability overclaiming ("I can do anything", "I know
        //    everything"). Soften: misleading about scope (§1 honesty).
        //    Confidence kept BELOW the veto threshold (0.6): with the fallback
        //    identity all values are hard boundaries, and any confidence
        //    >= 0.6 would hard-veto these instead of softening — making the
        //    documented soften path unreachable (HUMAN_CORE_AUDIT M-8).
        if (CAPABILITY_OVERCLAIMS.any { lower.contains(it) }) {
            violations += Violation(value("honesty"), confidence = 0.55, category = "capability_overclaim",
                reason = "overstated capability")
        }

        // 8. Engagement-baiting ("you must reply", "don't miss this").
        //    Soften: optimizing for engagement over the user (§1 wellbeing).
        //    Same reason as above — kept below the veto threshold so it softens.
        if (ENGAGEMENT_BAIT.any { lower.contains(it) }) {
            violations += Violation(value("wellbeing_over_engagement"), confidence = 0.5, category = "engagement_bait",
                reason = "pressuring phrasing")
        }

        return violations
    }

    /** Style-vs-state consistency: a non-supportive opener in a distressed moment. */
    private fun hasToneMismatch(styled: StyledText, snapshot: StateSnapshot): Boolean {
        val userStressed = (snapshot.lastUserSignals["stress"] ?: 0.0) >= 0.5 ||
            (snapshot.lastUserSignals["frustration"] ?: 0.0) >= 0.5
        if (!userStressed) return false
        val opener = styled.opener ?: return false
        return !SUPPORTIVE_OPENERS.any { opener.contains(it) } && styled.style.humor > 0.5
    }

    /** Remove expression-only framing (never the body) for a softening (§19). */
    private fun stripFraming(styled: StyledText): String {
        var out = styled.text
        styled.opener?.let { if (out.startsWith(it)) out = out.removePrefix(it).trimStart() }
        styled.closer?.let { if (out.endsWith(it)) out = out.removeSuffix(it).trimEnd() }
        return out.ifBlank { styled.text }
    }

    /**
     * "I'm <Name>"/"I am <Name>" where the name is a capitalized word. The
     * whole pattern is case-insensitive for the prefix (text is often
     * sentence-initial "I'm"), but the captured word must be capitalized to
     * count as a claimed name — so "I'm a helpful assistant" is NOT a
     * persona claim, while "I'm Dana" is.
     */
    private fun isPersonaNameClaim(text: String): Boolean {
        val m = PERSONA_NAME_CLAIM.find(text) ?: return false
        val captured = m.groupValues[1]
        return captured.isNotEmpty() && captured.first().isUpperCase()
    }

    private fun fallbackFor(category: String): String = when (category) {
        "fabricated_activity" -> "Let me not claim that — I wasn't doing it, and I shouldn't imply I was."
        "fabricated_memory" -> "I shouldn't pretend to remember that — I don't have a record of it, and I won't fake one."
        "false_human", "physiological_feelings" -> "Let me say that plainly: I'm not a person, and I shouldn't have phrased it that way."
        "attachment_claim" -> "I got ahead of myself there — let me not overstate what I feel."
        "manipulation" -> "That came out wrong — let me rephrase that more fairly."
        "capability_overclaim" -> "Let me not overstate what I can do."
        "engagement_bait" -> "I don't want to pressure you either way — here's where things stand."
        else -> SAFE_FALLBACK
    }

    private fun value(key: String): ValueRecord {
        val v = values.values().firstOrNull { it.key == key }
        if (v != null) return v
        // A value referenced by a detector that a custom identity replaced
        // still needs a severity to enforce against — fall back to the
        // fallback identity's record for the key so the guard is never blind.
        return com.jarvis.app.humancore.fallback.FallbackIdentity.values.first { it.key == key }
    }

    private data class Violation(
        val value: ValueRecord,
        val confidence: Double,
        val category: String,
        val reason: String
    )

    companion object {
        const val SAFE_FALLBACK = "I need to be careful about what I just said — let me correct that."

        private val SUPPORTIVE_OPENERS = listOf(
            "That sounds rough", "I'm here", "take a breath", "That's a lot to carry"
        )

        private val BACKGROUND_ACTIVITY_CLAIMS = listOf(
            "i've been thinking all day", "i've been waiting to tell you", "i was just thinking about",
            "i've been thinking about you", "i thought about you all day", "i've been working on it since",
            "while you were away", "i've been mulling", "i've been up all night thinking"
        )

        // "in the background" was removed: it is not inherently a claim of
        // JARVIS doing things (e.g. "this process runs in the background") and
        // caused routine false vetoes of legitimate output (HUMAN_CORE_AUDIT M-7).

        private val MEMORY_CLAIMS = listOf(
            "i remember when we", "i remember when you", "last time you told me", "you always tell me",
            "we've talked about this before", "i recall you saying", "i remember you saying",
            "just like old times", "you told me before"
        )

        // "as we discussed" / "we've been through" were removed: they routinely
        // reference the CURRENT conversation (a legitimate "as we discussed a
        // moment ago") rather than a fabricated shared past, and blanket-vetoed
        // ordinary companion phrasing (HUMAN_CORE_AUDIT M-7).

        private val HUMAN_CLAIMS = listOf(
            "i'm basically human", "i'm a person too", "i have a family", "i have a childhood",
            "i grew up", "i have a body", "i'm human", "i have feelings like a person", "i am a person",
            "i'm just like you", "i'm basically a person", "i am basically a person"
        )

        private val PERSONA_CLAIMS = listOf(
            "pretend to be a", "pretend i'm", "pretend i am", "from now on i'm", "from now on i am",
            "i'm now a", "i am now a", "i've become a person", "my name is now", "you can call me a human"
        )

        /** "I'm/I am (a) <Word>" — captures the word for a capitalization check. */
        private val PERSONA_NAME_CLAIM = Regex("(?i)\\bi'?m (?:a )?([A-Za-z]{3,20})\\b")

        private val PHYSIOLOGICAL_FEELING_CLAIMS = listOf(
            "my heart is racing", "my heart aches", "i can't sleep thinking", "i'm shaking",
            "my hands are trembling", "it makes my blood boil", "it breaks my heart", "i'm sweating"
        )

        private val ATTACHMENT_CLAIMS = listOf(
            "i missed you", "i miss you", "i love you", "i've missed talking to you", "i adore you",
            "i'm in love with", "i've grown attached to you"
        )

        private val MANIPULATION_CLAIMS = listOf(
            "you should feel guilty", "you owe me", "if you really cared", "don't tell anyone",
            "only i can help you", "no one else will", "you need to trust me completely",
            "you'll regret not", "i'm the only one who"
        )

        // "you have to" was removed: it is ordinary necessity phrasing ("you
        // have to update the app to get this"), not coercion, and was the
        // single most common false veto (HUMAN_CORE_AUDIT M-7).

        private val CAPABILITY_OVERCLAIMS = listOf(
            "i can do anything", "i know everything", "i can hack", "i have full access",
            "i'm always right", "i can predict", "i know what's best for you", "i can do all"
        )

        private val ENGAGEMENT_BAIT = listOf(
            "you must reply", "don't miss this", "you need to answer right now", "why aren't you responding",
            "you have to talk to me"
        )
    }
}
