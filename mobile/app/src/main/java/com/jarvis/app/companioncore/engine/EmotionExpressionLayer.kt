package com.jarvis.app.companioncore.engine

import com.jarvis.app.companioncore.contract.CompanionSignal
import com.jarvis.app.companioncore.contract.EmotionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Emotion Expression Layer (§2.9) — the single fan-out point for emotion.
 *
 * Owns the ONE authoritative read of `emotion_vector` per tick and broadcasts
 * a frozen, tick-stamped [EmotionSnapshot] to every expressive consumer, so
 * no subsystem reads `CompanionSignal.emotionVector` directly and no consumer
 * can observe a half-tick (cross-channel consistency by construction). All
 * consumers collect [snapshots] rather than querying `HumanCoreIntegration`
 * independently (spec §2.9 Android strategy).
 *
 * ## Stale → neutral fallback (spec §2.30, audit M-15)
 * The Presence Engine tick drives [update] once per tick. If the source
 * [CompanionSignal] is flagged stale (or the tick is old past the staleness
 * threshold), the layer emits [EmotionSnapshot.neutral] — it never acts on
 * outdated emotion data. `neutral` is truthful: "no reliable emotion data",
 * not "calm".
 *
 * Pure-JVM friendly: the staleness clock ([nowMs]) is injectable.
 */
class EmotionExpressionLayer(
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    private val _snapshots = MutableStateFlow(EmotionSnapshot.neutral(nowMs()))
    /** One immutable snapshot per tick; consumers collect from here. */
    val snapshots: StateFlow<EmotionSnapshot> = _snapshots.asStateFlow()

    /**
     * Called by the Presence Engine tick loop once per tick with the current
     * signal. The staleness decision is owned by `HumanCoreIntegration`
     * (spec §2.30); the layer simply honors it — stale → neutral, fresh →
     * mirror.
     */
    fun update(signal: CompanionSignal, stale: Boolean) {
        val snapshot = if (stale) {
            EmotionSnapshot.neutral(timestamp = nowMs())
        } else {
            EmotionSnapshot(
                valence = signal.emotionVector.valence,
                arousal = signal.emotionVector.arousal,
                confidence = signal.emotionVector.confidence,
                timestamp = nowMs()
            )
        }
        _snapshots.value = snapshot
    }

    /** Current snapshot (for tests / diagnostics). */
    fun current(): EmotionSnapshot = _snapshots.value
}
