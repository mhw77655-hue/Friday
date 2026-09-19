package com.jarvis.app.companioncore.contract

/**
 * The Human Core → Companion Core signal (§1, spec §2.30).
 *
 * Pull-based, read-only snapshot of how JARVIS is right now, assembled once
 * per Presence Engine tick by the sole writer — `HumanCoreIntegration` —
 * and consumed immutably by every Companion Core subsystem. No subsystem
 * mutates this type (sole-writer discipline, audit R-I6): it is published as
 * a `StateFlow` snapshot and has no setters.
 *
 * Field mappings follow plan §4.2. Where the Kotlin Human Core exposes no
 * scalar, Phase 1 uses a **documented heuristic** in `SignalMapper` rather
 * than extending the frozen Human Core (plan §4.2 GAP rows).
 *
 * @property emotionVector mirrored emotion vector, refreshed once per tick,
 *   never mutated locally; null HC values surface as neutral.
 * @property confidence JARVIS self-confidence in [0,1]. GAP heuristic:
 *   0.25 + 0.35·|valence| + 0.4·trust (no single HC self-confidence scalar).
 * @property energy in [0,1]. GAP heuristic: derived from arousal.
 * @property motivation in [0,1]. GAP heuristic: derived from arousal +
 *   presence awayness.
 * @property attentionFocus nullable; spec-deferred — the Kotlin HC exposes
 *   no scalar, and the Attention Engine (§2.3) supplies it in a later phase.
 * @property currentAction derived from the HC 7-step loop's current step via
 *   the `CurrentActionDiscriminator` (plan §4.2.1); the conservative local
 *   fallback maps tool paths to GENERATING.
 * @property lastUtteranceText the text about to be or currently being spoken;
 *   owned by `HumanCoreIntegration` (audit R-I1), never written by the
 *   ViewModel directly.
 * @property fabricationFlag set ONLY when a caught-and-corrected claim is
 *   confirmed (audit A-6/V-3), never inferred. Companion Core uses it to
 *   slightly lower expressed confidence, never to editorialize.
 */
data class CompanionSignal(
    val emotionVector: EmotionVector,
    val confidence: Double,
    val energy: Double,
    val motivation: Double,
    val attentionFocus: String?,
    val currentAction: CurrentAction,
    val lastUtteranceText: String,
    val fabricationFlag: Boolean
) {
    companion object {
        /** Neutral pre-first-tick / post-failure default (never a fabricated read). */
        fun neutral(): CompanionSignal = CompanionSignal(
            emotionVector = EmotionVector(valence = 0.0, arousal = 0.5, confidence = 0.0),
            confidence = 0.0,
            energy = 0.5,
            motivation = 0.5,
            attentionFocus = null,
            currentAction = CurrentAction.IDLE,
            lastUtteranceText = "",
            fabricationFlag = false
        )
    }
}

/** The emotion vector {valence, arousal, confidence}, mirrored read-only (§1). */
data class EmotionVector(
    /** [-1, 1]. */
    val valence: Double,
    /** [0, 1]. */
    val arousal: Double,
    /** [0, 1]. */
    val confidence: Double
)

/**
 * The Human Core 7-step loop's current step, as seen by the Companion Core
 * (§1). TOOL_CALL is not produced by the local discriminator (conservative
 * fallback maps tool paths to GENERATING, plan §4.2.1) but is kept in the
 * enum because a remote binding may supply it directly.
 */
enum class CurrentAction { IDLE, RECEIVING_INPUT, TOOL_CALL, GENERATING, DONE }
