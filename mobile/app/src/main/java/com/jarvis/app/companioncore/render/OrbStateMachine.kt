package com.jarvis.app.companioncore.render

import androidx.compose.ui.graphics.Color
import com.jarvis.app.companioncore.contract.AlertLevel
import com.jarvis.app.companioncore.contract.AudioState
import com.jarvis.app.companioncore.contract.CompanionState
import com.jarvis.app.companioncore.contract.PresenceMode
import com.jarvis.app.companioncore.contract.RenderIntent
import com.jarvis.app.companioncore.contract.RenderPriority
import com.jarvis.app.companioncore.identity.VisualIdentity
import com.jarvis.app.companioncore.identity.VisualIdentity.SemanticRole
import com.jarvis.app.vf.motion.MotionEngine
import com.jarvis.app.vf.reactor.ReactorEngine

/**
 * §2.6 — Orb State Machine.
 *
 * The definitive, pure mapping from `(presence_mode, emotion_vector, alert)`
 * to a concrete orb render parameter set ([OrbRenderParams]) plus the §1
 * [RenderIntent] the Animation Controller arbitrates. It is a pure function
 * of its inputs — no renderer dependency — so the state→params mapping is
 * unit-tested independently of any Canvas (spec §2.6 testing requirement).
 *
 * The numeric vocabulary (breathing cycle, pulse mode, ring count, particle
 * density) is delegated to the frozen Visual Foundation [ReactorEngine.spec],
 * so this machine never re-derives design values and stays in lock-step with
 * the VF tokens. Alert levels ([AlertLevel]) override the base mode's
 * reactor state — e.g. a WARNING layers the §3.8 sharp 1Hz pulse on top of
 * whatever the mode would otherwise express.
 */
class OrbStateMachine {

    /**
     * Resolve the concrete render parameters for one frame.
     *
     * @param state the current [CompanionState] (presence mode + emotion).
     * @param alert degradation/alert overlay (derived from HC `PresenceState`
     *   by the integration, or forced by a caller).
     * @param audioAmplitude live audio amplitude 0..1 (voice visualization
     *   hook; default 0 = no audio-reactive modulation).
     */
    fun map(
        state: CompanionState,
        alert: AlertLevel = AlertLevel.NONE,
        audioAmplitude: Float = 0f,
        /** §2.31 cap (from [MobileResourceManagement.budget]); Int.MAX = uncapped. */
        maxParticleDensity: Int = Int.MAX_VALUE
    ): OrbRenderParams {
        val mode = state.presenceMode
        val reactorState = effectiveReactorState(mode, alert)
        val spec = ReactorEngine.spec(reactorState)

        return OrbRenderParams(
            clip = clipFor(mode, alert),
            reactorState = reactorState,
            color = colorFor(mode, alert),
            glowIntensity = glowFor(reactorState, alert),
            particleDensity = minOf(spec.particleCount, maxParticleDensity),
            ringCount = spec.ringCount,
            breathingMs = spec.breathingMs,
            breathingAmplitude = spec.breathingAmplitude,
            pulseMode = spec.pulseMode,
            crossfadeMs = crossfadeFor(mode),
            audioAmplitude = audioAmplitude.coerceIn(0f, 1f)
        )
    }

    /**
     * Resolve the §1 [RenderIntent] for a frame — the contract the Animation
     * Controller consumes. [visualState] is the clip; [audioState] declares the
     * audio channel that should accompany the visual (playback is a later
     * phase); [priority] escalates for alert states so intents arbitrate
     * correctly (calls/wake-word/urgent notifications preempt).
     */
    fun resolveIntent(state: CompanionState, alert: AlertLevel = AlertLevel.NONE): RenderIntent {
        val mode = state.presenceMode
        return RenderIntent(
            visualState = clipFor(mode, alert).visualState,
            audioState = audioFor(mode, alert),
            durationHintMs = 0L, // the presence engine persists the mode; no hint
            priority = when (alert) {
                AlertLevel.CRITICAL -> RenderPriority.URGENT
                AlertLevel.WARNING -> RenderPriority.HIGH
                else -> RenderPriority.NORMAL
            }
        )
    }

    // ------------------------------------------------------------------ mapping

    /** The base reactor state a mode renders, absent an alert override. */
    private fun baseReactorState(mode: PresenceMode): ReactorEngine.ReactorSpecState = when (mode) {
        PresenceMode.ASLEEP -> ReactorEngine.ReactorSpecState.SLEEPING   // §9.12
        PresenceMode.WAKING -> ReactorEngine.ReactorSpecState.IDLE       // bloom toward idle
        PresenceMode.IDLE -> ReactorEngine.ReactorSpecState.IDLE         // §9.1
        PresenceMode.LISTENING -> ReactorEngine.ReactorSpecState.LISTENING // §9.2
        PresenceMode.THINKING -> ReactorEngine.ReactorSpecState.THINKING // §9.3
        PresenceMode.SPEAKING -> ReactorEngine.ReactorSpecState.BUILDING // §9.7 (output produced)
        PresenceMode.SHUTTING_DOWN -> ReactorEngine.ReactorSpecState.OFFLINE // dim/fade target
    }

    private fun alertReactorState(alert: AlertLevel): ReactorEngine.ReactorSpecState? = when (alert) {
        AlertLevel.CRITICAL -> ReactorEngine.ReactorSpecState.CRITICAL  // §9.11
        AlertLevel.WARNING -> ReactorEngine.ReactorSpecState.WARNING    // §9.10
        AlertLevel.OFFLINE -> ReactorEngine.ReactorSpecState.OFFLINE    // §9.13
        AlertLevel.NONE -> null
    }

    private fun effectiveReactorState(
        mode: PresenceMode,
        alert: AlertLevel
    ): ReactorEngine.ReactorSpecState = alertReactorState(alert) ?: baseReactorState(mode)

    /**
     * The animation archetype for the current expression. Alert WARNING /
     * CRITICAL keep the mode's own clip — the sharp pulse layers on top
     * (§3.4); OFFLINE renders the inert ember, so its clip is the fade (the
     * mode's own clip would promise motion that never comes).
     */
    private fun clipFor(mode: PresenceMode, alert: AlertLevel): OrbClip = when {
        alert == AlertLevel.OFFLINE -> OrbClip.SHUTDOWN_FADE
        else -> when (mode) {
            PresenceMode.ASLEEP -> OrbClip.SLEEP_DIM
            PresenceMode.WAKING -> OrbClip.WAKE_BLOOM
            PresenceMode.IDLE -> OrbClip.IDLE_BREATHE
            PresenceMode.LISTENING -> OrbClip.LISTENING_RIPPLE
            PresenceMode.THINKING -> OrbClip.THINKING_SWIRL
            PresenceMode.SPEAKING -> OrbClip.SPEAKING_PULSE
            PresenceMode.SHUTTING_DOWN -> OrbClip.SHUTDOWN_FADE
        }
    }

    private fun colorFor(mode: PresenceMode, alert: AlertLevel): Color = when (alert) {
        AlertLevel.CRITICAL -> VisualIdentity.color(SemanticRole.CRITICAL)
        AlertLevel.WARNING -> VisualIdentity.color(SemanticRole.WARNING)
        AlertLevel.OFFLINE -> VisualIdentity.color(SemanticRole.OFFLINE)
        AlertLevel.NONE -> when (mode) {
            PresenceMode.ASLEEP -> VisualIdentity.color(SemanticRole.SLEEP)
            PresenceMode.WAKING -> VisualIdentity.color(SemanticRole.WAKE)
            PresenceMode.IDLE -> VisualIdentity.color(SemanticRole.IDLE)
            PresenceMode.LISTENING -> VisualIdentity.color(SemanticRole.LISTENING)
            PresenceMode.THINKING -> VisualIdentity.color(SemanticRole.THINKING)
            PresenceMode.SPEAKING -> VisualIdentity.color(SemanticRole.SPEAKING)
            PresenceMode.SHUTTING_DOWN -> VisualIdentity.color(SemanticRole.OFFLINE)
        }
    }

    /** Glow strength 0..1 per state (informational; renderer draws its own). */
    private fun glowFor(state: ReactorEngine.ReactorSpecState, alert: AlertLevel): Float = when (alert) {
        AlertLevel.CRITICAL -> 0.85f
        AlertLevel.WARNING -> 0.70f
        AlertLevel.OFFLINE -> 0.15f
        AlertLevel.NONE -> when (state) {
            ReactorEngine.ReactorSpecState.IDLE -> 0.40f
            ReactorEngine.ReactorSpecState.LISTENING -> 0.55f
            ReactorEngine.ReactorSpecState.THINKING -> 0.60f
            ReactorEngine.ReactorSpecState.RESEARCH -> 0.65f
            ReactorEngine.ReactorSpecState.PLANNING -> 0.60f
            ReactorEngine.ReactorSpecState.CODING -> 0.65f
            ReactorEngine.ReactorSpecState.BUILDING -> 0.60f
            ReactorEngine.ReactorSpecState.LEARNING -> 0.60f
            ReactorEngine.ReactorSpecState.WARNING -> 0.70f
            ReactorEngine.ReactorSpecState.CRITICAL -> 0.85f
            ReactorEngine.ReactorSpecState.SLEEPING -> 0.25f
            ReactorEngine.ReactorSpecState.OFFLINE -> 0.15f
        }
    }

    private fun audioFor(mode: PresenceMode, alert: AlertLevel): AudioState = when (alert) {
        AlertLevel.CRITICAL, AlertLevel.WARNING -> AudioState.NOTIFY_URGENT
        AlertLevel.OFFLINE -> AudioState.NONE
        AlertLevel.NONE -> when (mode) {
            PresenceMode.WAKING -> AudioState.WAKE_EARCON
            PresenceMode.LISTENING -> AudioState.LISTENING_EARCON
            PresenceMode.SPEAKING -> AudioState.SPEAKING
            else -> AudioState.NONE
        }
    }

    /** §12.3 transition window — shutdown fades slower than routine changes. */
    private fun crossfadeFor(mode: PresenceMode): Long = when (mode) {
        PresenceMode.SHUTTING_DOWN -> MotionEngine.DurStartupShutdown
        else -> MotionEngine.DurStateChange
    }
}
