package com.jarvis.app.companioncore.contract

/**
 * The in-memory Companion State (§1) — rebuilt every Presence Engine tick,
 * never persisted directly. This is the full system state broadcast to all
 * subsystems each tick by the Presence Engine (§2.1).
 *
 * Phase 1 declares the contract type. The Presence Engine (Phase 2) becomes
 * the sole producer; until then callers use [initial] for type-level wiring
 * only — no Phase-1 subsystem depends on a live `CompanionState`.
 *
 * @property presenceMode the continuously present identity with transient listening/speaking/shutdown expressions.
 * @property emotionVector mirrored from Human Core via §2.30/§2.9, never
 *   mutated locally (§1).
 * @property attentionTarget where JARVIS's attention is aimed.
 * @property activeTheme reference into the Visual Theme System (nullable
 *   until §2.27 exists).
 * @property lastRenderTimestamp for frame-pacing (null before first render).
 * @property interruptFlags pending preemption reasons (bitset).
 */
data class CompanionState(
    val presenceMode: PresenceMode,
    val emotionVector: EmotionVector,
    val attentionTarget: AttentionTarget,
    val activeTheme: String?,
    val lastRenderTimestamp: Long?,
    val interruptFlags: Set<InterruptFlag>
) {
    companion object {
        /** A safe, all-neutral initial state. */
        fun initial(): CompanionState = CompanionState(
            presenceMode = PresenceMode.PRESENT,
            emotionVector = EmotionVector(valence = 0.0, arousal = 0.5, confidence = 0.0),
            attentionTarget = AttentionTarget.NONE,
            activeTheme = null,
            lastRenderTimestamp = null,
            interruptFlags = emptySet()
        )
    }
}

/** Canonical presence expressions; legacy labels normalize to PRESENT in PresenceEngine. */
enum class PresenceMode {
    PRESENT, LISTENING, SPEAKING, SHUTTING_DOWN,
    /** Legacy activity labels are normalized to PRESENT by PresenceEngine. */
    @Deprecated("Friday is continuously present; use PRESENT") ASLEEP,
    @Deprecated("Friday is continuously present; use PRESENT") WAKING,
    @Deprecated("Friday is continuously present; use PRESENT") IDLE,
    @Deprecated("Processing is an activity, not a presence mode; use PRESENT") THINKING
}

/** Where Friday's attention is aimed. */
enum class AttentionTarget { USER, NONE, NOTIFICATION, SELF_TASK }

/** Pending preemption reasons (§1 interrupt_flags bitset). */
enum class InterruptFlag { INCOMING_CALL, NEW_USER_INPUT, WAKE_WORD, URGENT_NOTIFICATION, LOW_RESOURCE }
