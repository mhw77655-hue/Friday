package com.jarvis.app.humancore.protocol

/**
 * Session/context metadata attached to each inbound message (§0.7 Inputs).
 *
 * The Human Core must know the channel and time context of an exchange but
 * never owns the scheduling or transport mechanics — it only consumes this
 * metadata. Fields are intentionally optional/nullable so the Perception Pass
 * works identically when a field is unavailable (offline, first contact, no
 * presence history yet).
 */
data class SessionContext(
    /** Channel the message arrived on. Mobile app today; Telegram is a future channel (§0.7). */
    val channel: Channel,
    /** Millis since midnight local time — used by Companion Behavior / tone calibration. */
    val localHour: Int,
    /** Seconds since the previous interaction with this user, if known (§12 Presence). */
    val secondsSinceLastContact: Long?,
    /** Whether JARVIS was "away" when this message arrived (§12). */
    val wasAway: Boolean,
    /**
     * Optional prosody/tone metadata from the mobile voice pipeline (§0.14).
     * Null for plain text input (Telegram) — the EI module never requires it.
     */
    val prosody: ProsodyHint?
) {
    enum class Channel { MOBILE_APP, TELEGRAM }
}

/**
 * Optional tone/prosody enrichment from the mobile voice pipeline (§0.14).
 * The Emotional Intelligence input contract accepts this as an optional
 * field so the module works identically with or without it. Field semantics
 * are intentionally loose — they are hints, not a phonetic analysis.
 */
data class ProsodyHint(
    /** Approximate speaking pace hint, if the pipeline can supply it. */
    val paceFast: Boolean? = null,
    /** Approximate loudness/energy hint, if available. */
    val loud: Boolean? = null,
    /** Free-form classifier hint ("frustrated", "excited", ...) if available. */
    val marker: String? = null
)
