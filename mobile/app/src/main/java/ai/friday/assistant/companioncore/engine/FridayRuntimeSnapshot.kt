package ai.friday.assistant.companioncore.engine

import ai.friday.assistant.companioncore.contract.CurrentAction

/**
 * Read-only snapshot of evidence emitted by Friday's own Android runtime.
 *
 * Null means that Friday has no validated measurement for that dimension.
 * In particular, no emotion, trust, bond, confidence or motivation values are
 * derived from absence of evidence.
 */
data class FridayRuntimeSnapshot(
    val ts: Long,
    val moodValence: Double? = null,
    val moodArousal: Double? = null,
    val lastUserConfidence: Double? = null,
    val trust: Double? = null,
    val bondDepth: Double? = null,
    val totalInteractions: Long = 0L,
    val secondsSinceLastContact: Long? = null,
    val lastUserSignals: Map<String, Double> = emptyMap(),
    /** User-presence evidence: ACTIVE/AWAY, null when the sensor has no result. */
    val presenceModeName: String? = null,
    /** Actual serving health label, if the host can report one. */
    val systemTierName: String? = null,
    val currentActionRaw: CurrentAction? = null,
    /** Model-manager status string; consumed by CurrentActionDiscriminator. */
    val bridgeStatus: String? = null,
    val lastUtteranceText: String? = null,
    /** True only when Friday's own referee confirms a caught-and-corrected fabrication. */
    val fabricationEvidence: Boolean = false
) {
    val isMeaningful: Boolean
        get() = moodValence != null || moodArousal != null ||
            lastUserConfidence != null || trust != null || bondDepth != null ||
            secondsSinceLastContact != null || presenceModeName != null ||
            systemTierName != null || currentActionRaw != null ||
            !bridgeStatus.isNullOrBlank() || !lastUtteranceText.isNullOrBlank() ||
            totalInteractions > 0 || lastUserSignals.isNotEmpty()

    companion object {
        fun empty(ts: Long) = FridayRuntimeSnapshot(ts = ts)
    }
}

