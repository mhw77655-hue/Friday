package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.contract.AlertLevel
import com.jarvis.app.companioncore.contract.AttentionTarget
import com.jarvis.app.companioncore.contract.AudioState
import com.jarvis.app.companioncore.contract.CompanionState
import com.jarvis.app.companioncore.contract.EmotionVector
import com.jarvis.app.companioncore.contract.PresenceMode
import com.jarvis.app.companioncore.contract.RenderPriority
import com.jarvis.app.companioncore.contract.VisualState
import com.jarvis.app.companioncore.identity.VisualIdentity
import com.jarvis.app.companioncore.render.OrbClip
import com.jarvis.app.companioncore.render.OrbStateMachine
import com.jarvis.app.vf.reactor.ReactorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §2.6 — Orb State Machine tests.
 *
 * The pure state→params mapping is tested independently of any renderer
 * (spec §2.6 testing requirement): every `(mode, alert)` combination resolves
 * a coherent [OrbRenderParams], the OrbClip set aligns 1:1 with the §1
 * `RenderIntent.VisualState` set, and the §1 RenderIntent carries the right
 * audio/priority semantics for the Animation Controller.
 */
class OrbStateMachineTest {

    private val machine = OrbStateMachine()

    private fun state(mode: PresenceMode): CompanionState = CompanionState(
        presenceMode = mode,
        emotionVector = EmotionVector(valence = 0.3, arousal = 0.6, confidence = 0.8),
        attentionTarget = AttentionTarget.NONE,
        activeTheme = null,
        lastRenderTimestamp = null,
        interruptFlags = emptySet()
    )

    // ------------------------------------------------------------------ mode mapping

    @Test
    fun `every presence mode maps to a reactor state and clip`() {
        assertMapping(PresenceMode.ASLEEP, ReactorEngine.ReactorSpecState.SLEEPING, OrbClip.SLEEP_DIM)
        assertMapping(PresenceMode.WAKING, ReactorEngine.ReactorSpecState.IDLE, OrbClip.WAKE_BLOOM)
        assertMapping(PresenceMode.IDLE, ReactorEngine.ReactorSpecState.IDLE, OrbClip.IDLE_BREATHE)
        assertMapping(PresenceMode.LISTENING, ReactorEngine.ReactorSpecState.LISTENING, OrbClip.LISTENING_RIPPLE)
        assertMapping(PresenceMode.THINKING, ReactorEngine.ReactorSpecState.THINKING, OrbClip.THINKING_SWIRL)
        assertMapping(PresenceMode.SPEAKING, ReactorEngine.ReactorSpecState.BUILDING, OrbClip.SPEAKING_PULSE)
        assertMapping(PresenceMode.SHUTTING_DOWN, ReactorEngine.ReactorSpecState.OFFLINE, OrbClip.SHUTDOWN_FADE)
    }

    private fun assertMapping(mode: PresenceMode, reactor: ReactorEngine.ReactorSpecState, clip: OrbClip) {
        val p = machine.map(state(mode))
        assertEquals("$mode → reactor state", reactor, p.reactorState)
        assertEquals("$mode → clip", clip, p.clip)
    }

    @Test
    fun `listening carries the audio-reactive pulse mode`() {
        val p = machine.map(state(PresenceMode.LISTENING))
        assertEquals(ReactorEngine.ReactorSpec.PulseMode.AUDIO, p.pulseMode)
    }

    @Test
    fun `speaking renders the building family (near-white output)`() {
        val p = machine.map(state(PresenceMode.SPEAKING))
        assertEquals(VisualIdentity.color(VisualIdentity.SemanticRole.SPEAKING), p.color)
        assertTrue(p.breathingMs > 0L)
    }

    // ------------------------------------------------------------------ alert overrides

    @Test
    fun `warning alert layers the sharp 1Hz pulse on any mode`() {
        val p = machine.map(state(PresenceMode.IDLE), AlertLevel.WARNING)
        assertEquals(ReactorEngine.ReactorSpecState.WARNING, p.reactorState)
        assertEquals(ReactorEngine.ReactorSpec.PulseMode.SHARP_1HZ, p.pulseMode)
        assertEquals(VisualIdentity.color(VisualIdentity.SemanticRole.WARNING), p.color)
    }

    @Test
    fun `critical alert layers the sharp 2Hz pulse on any mode`() {
        val p = machine.map(state(PresenceMode.THINKING), AlertLevel.CRITICAL)
        assertEquals(ReactorEngine.ReactorSpecState.CRITICAL, p.reactorState)
        assertEquals(ReactorEngine.ReactorSpec.PulseMode.SHARP_2HZ, p.pulseMode)
        assertEquals(VisualIdentity.color(VisualIdentity.SemanticRole.CRITICAL), p.color)
    }

    @Test
    fun `offline alert renders the inert dim ember - no rings no particles`() {
        val p = machine.map(state(PresenceMode.THINKING), AlertLevel.OFFLINE)
        assertEquals(ReactorEngine.ReactorSpecState.OFFLINE, p.reactorState)
        assertEquals(0, p.ringCount)
        assertEquals(0, p.particleDensity)
        assertEquals(ReactorEngine.ReactorSpec.PulseMode.NONE, p.pulseMode)
        assertEquals(VisualIdentity.color(VisualIdentity.SemanticRole.OFFLINE), p.color)
        // The inert state's clip is the fade — the mode's own clip would
        // promise motion that never comes.
        assertEquals(OrbClip.SHUTDOWN_FADE, p.clip)
        assertEquals(VisualState.SHUTDOWN_FADE, machine.resolveIntent(state(PresenceMode.THINKING), AlertLevel.OFFLINE).visualState)
    }

    // ------------------------------------------------------------------ clip / intent alignment

    @Test
    fun `orb clip set aligns one-to-one with render intent visual states`() {
        val visualStates = com.jarvis.app.companioncore.contract.VisualState.entries
        assertEquals("OrbClip and VisualState must be the same size (1:1)", visualStates.size, OrbClip.entries.size)
        for (clip in OrbClip.entries) {
            assertEquals(clip, OrbClip.fromVisualState(clip.visualState))
        }
    }

    @Test
    fun `listening intent declares the listening earcon channel`() {
        val intent = machine.resolveIntent(state(PresenceMode.LISTENING))
        assertEquals(AudioState.LISTENING_EARCON, intent.audioState)
    }

    @Test
    fun `speaking intent declares the speaking audio channel`() {
        val intent = machine.resolveIntent(state(PresenceMode.SPEAKING))
        assertEquals(AudioState.SPEAKING, intent.audioState)
    }

    @Test
    fun `waking intent declares the wake earcon`() {
        val intent = machine.resolveIntent(state(PresenceMode.WAKING))
        assertEquals(AudioState.WAKE_EARCON, intent.audioState)
    }

    @Test
    fun `warning intent escalates to high priority`() {
        val intent = machine.resolveIntent(state(PresenceMode.IDLE), AlertLevel.WARNING)
        assertEquals(RenderPriority.HIGH, intent.priority)
    }

    @Test
    fun `critical intent escalates to urgent priority`() {
        val intent = machine.resolveIntent(state(PresenceMode.IDLE), AlertLevel.CRITICAL)
        assertEquals(RenderPriority.URGENT, intent.priority)
    }

    @Test
    fun `shutdown fade transitions longer than routine changes`() {
        val shutdown = machine.map(state(PresenceMode.SHUTTING_DOWN))
        val idle = machine.map(state(PresenceMode.IDLE))
        assertTrue("shutdown should fade slower than routine", shutdown.crossfadeMs > idle.crossfadeMs)
    }

    // ------------------------------------------------------------------ robustness

    @Test
    fun `every mode times every alert resolves a coherent param set`() {
        for (mode in PresenceMode.entries) {
            for (alert in AlertLevel.entries) {
                val p = machine.map(state(mode), alert, audioAmplitude = 0.5f)
                assertTrue("$mode+$alert breathing must be >= 0", p.breathingMs >= 0L)
                assertTrue("$mode+$alert density must be >= 0", p.particleDensity >= 0)
                assertTrue("$mode+$alert glow in [0,1]", p.glowIntensity in 0f..1f)
            }
        }
    }

    @Test
    fun `audio amplitude is clamped into range`() {
        assertEquals(1.0f, machine.map(state(PresenceMode.LISTENING), audioAmplitude = 1.7f).audioAmplitude, 0f)
        assertEquals(0.0f, machine.map(state(PresenceMode.LISTENING), audioAmplitude = -0.3f).audioAmplitude, 0f)
        assertEquals(0.5f, machine.map(state(PresenceMode.LISTENING), audioAmplitude = 0.5f).audioAmplitude, 0f)
    }
}
