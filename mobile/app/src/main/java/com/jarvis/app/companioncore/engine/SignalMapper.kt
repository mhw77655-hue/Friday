package com.jarvis.app.companioncore.engine

import com.jarvis.app.companioncore.contract.CompanionSignal
import com.jarvis.app.companioncore.contract.CurrentAction
import com.jarvis.app.companioncore.contract.EmotionVector
import kotlin.math.abs

/**
 * Pure mapping from binding-neutral [RawCoreState] to the §1 [CompanionSignal]
 * contract (plan §4.2). No Human Core imports; no Android imports — fully
 * unit-testable on the JVM.
 *
 * Where the Kotlin Human Core exposes a field, it maps directly. Where it
 * exposes no scalar, the plan §4.2 GAP rows are resolved by **documented
 * heuristics** — never by extending the frozen Human Core:
 *
 * - [CompanionSignal.confidence]: HC has no single self-confidence scalar.
 *   Heuristic `0.25 + 0.35·|valence| + 0.4·trust`, clamped to [0,1]. Higher
 *   |valence| (a more decided emotional state) and higher trust raise it.
 * - [CompanionSignal.energy]: no HC scalar. Heuristic: mirrored from arousal
 *   (the closest available proxy, both in [0,1]).
 * - [CompanionSignal.motivation]: no HC scalar. Heuristic: a base term plus
 *   energy, discounted when the HC presence is AWAY:
 *   `0.4 + 0.3·energy − 0.3·(away ? 1 : 0)`, clamped to [0,1].
 * - [CompanionSignal.attentionFocus]: spec-deferred — the Attention Engine
 *   (§2.3, Phase 4) owns this; until then it is `null`.
 *
 * [CompanionSignal.fabricationFlag] passes through `RawCoreState` unchanged:
 * the binding/integration is responsible for only ever setting it on a
 * confirmed caught-correction (audit A-6/V-3). The mapper never infers it.
 */
object SignalMapper {

    fun map(raw: RawCoreState, action: CurrentAction): CompanionSignal {
        val valence = (raw.moodValence ?: 0.0).coerceIn(-1.0, 1.0)
        val arousal = (raw.moodArousal ?: 0.5).coerceIn(0.0, 1.0)
        val trust = (raw.trust ?: 0.5).coerceIn(0.0, 1.0)

        val confidence = (0.25 + 0.35 * abs(valence) + 0.4 * trust).coerceIn(0.0, 1.0)
        val energy = arousal
        val away = raw.presenceModeName == "AWAY"
        val motivation = (0.4 + 0.3 * energy - 0.3 * (if (away) 1.0 else 0.0)).coerceIn(0.0, 1.0)

        return CompanionSignal(
            emotionVector = EmotionVector(valence = valence, arousal = arousal, confidence = confidence),
            confidence = confidence,
            energy = energy,
            motivation = motivation,
            attentionFocus = null, // spec-deferred: Attention Engine (§2.3, Phase 4)
            currentAction = action,
            lastUtteranceText = raw.lastUtteranceText ?: "",
            fabricationFlag = raw.fabricationEvidence
        )
    }
}
