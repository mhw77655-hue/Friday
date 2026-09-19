package com.jarvis.app.companioncore.contract

/**
 * The Companion Core's internal render pipeline contract (§1).
 *
 * Promoted to the REAL pipeline type (audit A-2/M-7): state bundles produce a
 * `RenderIntent` rather than emitting renderer params directly, so the
 * interrupt / priority / duration-hint semantics of §1 have a home. The
 * Animation Controller (Phase 2) arbitrates these intents; the Orb/Avatar/Audio
 * renderers consume the resolved intent.
 *
 * @property visualState the visual expression to render.
 * @property audioState the audio channel to play alongside (if any).
 * @property gestureId nullable gesture modifier (no gesture when null).
 * @property durationHintMs how long the visual state should persist before the
 *   engine may move on (0 = no hint).
 * @property priority used to arbitrate concurrent intents (calls, wake word,
 *   urgent notifications preempt).
 */
data class RenderIntent(
    val visualState: VisualState,
    val audioState: AudioState = AudioState.NONE,
    val gestureId: GestureId? = null,
    val durationHintMs: Long = 0L,
    val priority: RenderPriority = RenderPriority.NORMAL
)

/** Visual expressions the renderers can produce (§2.6 OrbClip-aligned set). */
enum class VisualState {
    IDLE_BREATHE, LISTENING_RIPPLE, THINKING_SWIRL, SPEAKING_PULSE,
    WAKE_BLOOM, SLEEP_DIM, SHUTDOWN_FADE, NOTIFICATION_GLOW
}

/** Audio channels behind a render intent (§2.28). */
enum class AudioState {
    NONE, WAKE_EARCON, LISTENING_EARCON, SPEAKING, NOTIFY_LOW, NOTIFY_URGENT
}

/** Fixed gesture vocabulary (§2.22). */
enum class GestureId { NOD, TILT_CURIOUS, PULSE_ACKNOWLEDGE, SOFT_BLINK }

/** Arbitration priority — preempts lower intents (§2.1 priority table). */
enum class RenderPriority { LOW, NORMAL, HIGH, URGENT }
