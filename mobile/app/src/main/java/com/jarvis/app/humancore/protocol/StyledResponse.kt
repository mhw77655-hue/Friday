package com.jarvis.app.humancore.protocol

/**
 * Outcome of the Expression Pass (§0.5, §13, §19) for one outbound message.
 *
 * The Conversation Style Controller transforms the Reasoning subsystem's
 * content-level response into JARVIS's actual words; the Consistency &
 * Authenticity Guard then reviews it and approves, softens, or blocks.
 *
 * Spec constraints honored here:
 *  - content is never altered, only expression (§13);
 *  - a veto is rare, bounded, and always carries a stated, loggable reason —
 *    silent vetoes are not permitted (§0.9 two-key veto pattern, §19);
 *  - if the Guard itself fails to run, the subsystem must block and fall back
 *    to a minimal safe response rather than pass unchecked content through
 *    (§19 Failure handling — the one deliberate fail-closed path in the spec).
 */
sealed class StyledResponse {

    /** Approved: [text] is what reaches the user. */
    data class Approved(val text: String) : StyledResponse()

    /**
     * Softened: the original content survived but tone was adjusted.
     * [originalText] is kept for audit/tests (never shown as an alternative).
     */
    data class Softened(val text: String, val originalText: String, val reason: String) : StyledResponse()

    /**
     * Blocked: a clear values violation or a failed Guard. [reason] must be
     * logged; [fallbackText] is a minimal safe, values-compliant response.
     */
    data class Vetoed(val reason: String, val fallbackText: String, val originalText: String) : StyledResponse()

    /** The text that should actually be sent to the user. */
    val outboundText: String
        get() = when (this) {
            is Approved -> text
            is Softened -> text
            is Vetoed -> fallbackText
        }
}
