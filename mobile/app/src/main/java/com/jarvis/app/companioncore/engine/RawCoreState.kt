package com.jarvis.app.companioncore.engine

import com.jarvis.app.companioncore.contract.CurrentAction

/**
 * Binding-neutral raw Human Core state (plan §4.2).
 *
 * The type every [HumanCoreBinding] produces and the [SignalMapper]
 * consumes, so the mapper and the rest of the Companion Core never depend on
 * Human Core internals. Nullable fields mean "unavailable" — never a default
 * (mirrors the frozen HC's §10 rule: distinguish "unknown" from "neutral").
 *
 * @property ts epoch millis the state was sampled at.
 * @property moodValence HC mood valence [-1,1], null when unknown.
 * @property moodArousal HC mood arousal [0,1], null when unknown.
 * @property lastUserConfidence HC's last user-affect read confidence.
 * @property trust HC trust toward the user.
 * @property bondDepth HC bond depth.
 * @property totalInteractions lifetime interaction count.
 * @property secondsSinceLastContact seconds since the last user contact.
 * @property lastUserSignals named affect signals from the last perception read.
 * @property presenceModeName HC presence mode name (ACTIVE/AWAY/DEGRADED),
 *   null when unknown.
 * @property systemTierName HC system availability tier name
 *   (FULL/DEGRADED/OFFLINE/UNKNOWN), null when unknown — Phase 2 feeds the
 *   Orb State Machine's AlertLevel (WARNING/OFFLINE) derivation.
 * @property currentActionRaw the 7-step-loop step when a binding can supply it
 *   directly (remote binding); null when the local discriminator must derive it.
 * @property bridgeStatus raw brain-bridge status string (local binding only),
 *   consumed by [CurrentActionDiscriminator].
 * @property lastUtteranceText the text about to be or currently being spoken.
 * @property fabricationEvidence true ONLY when a caught-and-corrected claim is
 *   confirmed (audit A-6/V-3); never inferred by the mapper.
 */
data class RawCoreState(
    val ts: Long,
    val moodValence: Double?,
    val moodArousal: Double?,
    val lastUserConfidence: Double?,
    val trust: Double?,
    val bondDepth: Double?,
    val totalInteractions: Long,
    val secondsSinceLastContact: Long?,
    val lastUserSignals: Map<String, Double>,
    val presenceModeName: String?,
    val systemTierName: String? = null,
    val currentActionRaw: CurrentAction?,
    val bridgeStatus: String?,
    val lastUtteranceText: String?,
    val fabricationEvidence: Boolean
) {

    /**
     * Whether this read carries any substantive HC data. `[empty]` reads
     * (HC uninitialized / remote unreachable) must NOT count as a fresh
     * signal, or staleness would never trigger for a dead core. A valid
     * state has at least one non-null substantive field.
     */
    val isMeaningful: Boolean
        get() = moodValence != null || trust != null || bondDepth != null ||
            secondsSinceLastContact != null || presenceModeName != null ||
            lastUtteranceText != null || lastUserSignals.isNotEmpty()

    companion object {
        /** Empty state when a binding has no readable signal (HC uninitialized / remote unreachable). */
        fun empty(ts: Long): RawCoreState = RawCoreState(
            ts = ts,
            moodValence = null,
            moodArousal = null,
            lastUserConfidence = null,
            trust = null,
            bondDepth = null,
            totalInteractions = 0L,
            secondsSinceLastContact = null,
            lastUserSignals = emptyMap(),
            presenceModeName = null,
            currentActionRaw = null,
            bridgeStatus = null,
            lastUtteranceText = null,
            fabricationEvidence = false
        )
    }
}
