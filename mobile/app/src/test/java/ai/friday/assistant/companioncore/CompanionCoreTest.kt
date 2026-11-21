package ai.friday.assistant.companioncore

import ai.friday.assistant.companioncore.contract.AlertLevel
import ai.friday.assistant.companioncore.contract.PresenceMode
import ai.friday.assistant.companioncore.contract.RenderPriority
import ai.friday.assistant.companioncore.engine.CompanionClock
import ai.friday.assistant.companioncore.engine.CurrentActionDiscriminator
import ai.friday.assistant.companioncore.engine.HumanCoreIntegration
import ai.friday.assistant.companioncore.engine.RawCoreState
import ai.friday.assistant.companioncore.presence.CompanionCore
import ai.friday.assistant.companioncore.presence.PresenceEngine
import ai.friday.assistant.companioncore.render.AnimationController
import ai.friday.assistant.companioncore.render.OrbClip
import ai.friday.assistant.companioncore.render.OrbStateMachine
import ai.friday.assistant.companioncore.resource.MobileResourceManagement
import ai.friday.assistant.vf.reactor.ReactorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §2.1/§1.6 — Companion Core coordinator tests.
 *
 * The Engine Tick Lane fan-out (integration → presence engine → orb state
 * machine → render intent/params), continuous presence attachment, alert override →
 * render params, the voice/visualization hook, lifecycle background presence
 * handling, and the frame-budget watchdog inbound channel. All driven
 * deterministically via [CompanionCore.tick].
 */
class CompanionCoreTest {

    private val clock = ManualEpochClock()
    private val clockSource = ManualCompanionClockSource()

    private fun meaningfulState() = RawCoreState(
        ts = clock.now,
        moodValence = 0.5,
        moodArousal = 0.6,
        lastUserConfidence = 0.8,
        trust = 0.7,
        bondDepth = 0.4,
        totalInteractions = 10L,
        secondsSinceLastContact = 5L,
        lastUserSignals = emptyMap(),
        presenceModeName = "ACTIVE",
        systemTierName = "FULL",
        currentActionRaw = null,
        bridgeStatus = "ok (local)",
        lastUtteranceText = null,
        fabricationEvidence = false
    )

    private fun build(): Pair<CompanionCore, FakeBinding> {
        val binding = FakeBinding(state = meaningfulState())
        val integration = HumanCoreIntegration(
            binding = binding,
            discriminator = CurrentActionDiscriminator(now = { clock.now }),
            nowMs = { clock.now }
        )
        val core = CompanionCore(
            integration = integration,
            presenceEngine = PresenceEngine(nowMs = { clock.now }),
            orbStateMachine = OrbStateMachine(),
            animationController = AnimationController(clockSource.make()),
            resourceManagement = MobileResourceManagement(nowMs = { clock.now }),
            clock = CompanionClock(
                monotonicNanos = { clockSource.monotonicNanos },
                epochMs = { clockSource.epochMs }
            ),
            nowMs = { clock.now }
        )
        return core to binding
    }

    // ------------------------------------------------------------------ tick lane

    @Test
    fun `tick fans out through integration, engine and state machine`() {
        val (core, _) = build()
        core.presenceEngine.requestTransition(PresenceMode.PRESENT, "test", "model-work")
        core.tick()

        assertEquals(PresenceMode.PRESENT, core.presenceEngine.mode)
        assertEquals(ReactorEngine.ReactorSpecState.IDLE, core.renderParams.value.reactorState)
        assertEquals(OrbClip.IDLE_BREATHE, core.renderParams.value.clip)
        assertEquals(OrbClip.IDLE_BREATHE.visualState, core.renderIntent.value.visualState)
        assertNotNull(core.animationController.currentIntent)
    }

    @Test
    fun `signal is fresh after a meaningful binding read - emotion layer advances`() {
        val (core, _) = build()
        assertTrue("nothing read yet → stale", core.integration.stale)
        core.tick()
        assertTrue("a meaningful read must keep the integration fresh", !core.integration.stale)
        assertEquals(0.5, core.emotion.value.valence, 0.0)
    }

    @Test
    fun `legacy waking request never changes Friday's continuous presence`() {
        val (core, _) = build()
        core.presenceEngine.requestTransition(PresenceMode.WAKING, "legacy-caller", "wake")
        core.tick()
        assertEquals(PresenceMode.PRESENT, core.presenceEngine.mode)
        clock.advance(60_000)
        core.tick()
        assertEquals(PresenceMode.PRESENT, core.presenceEngine.mode)
    }

    @Test
    fun `start is idempotent and attaches the presence lane without waking from sleep`() {
        val (core, _) = build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            core.start(scope)
            // The lane starts over the already-present identity; no asleep/waking phase.
            assertEquals(PresenceMode.PRESENT, core.presenceEngine.mode)
            core.start(scope) // idempotent — must not double-request
        } finally {
            core.stop()
        }
    }

    // ------------------------------------------------------------------ alert override

    @Test
    fun `warning alert flows into the render params and intent`() {
        val (core, _) = build()
        core.integration.setAlertOverride(AlertLevel.WARNING)
        core.tick()

        assertEquals(ReactorEngine.ReactorSpecState.WARNING, core.renderParams.value.reactorState)
        assertEquals(ReactorEngine.ReactorSpec.PulseMode.SHARP_1HZ, core.renderParams.value.pulseMode)
        assertEquals(RenderPriority.HIGH, core.renderIntent.value.priority)
    }

    @Test
    fun `critical alert escalates to urgent`() {
        val (core, _) = build()
        core.integration.setAlertOverride(AlertLevel.CRITICAL)
        core.tick()
        assertEquals(ReactorEngine.ReactorSpec.PulseMode.SHARP_2HZ, core.renderParams.value.pulseMode)
        assertEquals(RenderPriority.URGENT, core.renderIntent.value.priority)
    }

    // ------------------------------------------------------------------ voice hook

    @Test
    fun `audio amplitude hook reaches the render params`() {
        val (core, _) = build()
        core.presenceEngine.requestTransition(PresenceMode.LISTENING, "test", "listen")
        core.setAudioAmplitude(0.8f)
        core.tick()
        assertEquals(0.8f, core.renderParams.value.audioAmplitude, 0f)
        assertEquals(ReactorEngine.ReactorSpec.PulseMode.AUDIO, core.renderParams.value.pulseMode)
    }

    @Test
    fun `audio amplitude is clamped`() {
        val (core, _) = build()
        core.setAudioAmplitude(5f)
        core.tick()
        assertEquals(1.0f, core.renderParams.value.audioAmplitude, 0f)
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    fun `background suspends rendering and clears on resume`() {
        val (core, _) = build()
        core.onBackground()
        assertTrue(core.resourceManagement.backgroundSuspended)
        core.onForeground()
        assertTrue(!core.resourceManagement.backgroundSuspended)
    }

    @Test
    fun `background is idempotent - second call does not double-send`() {
        val (core, _) = build()
        core.onBackground()
        core.onBackground() // no-op
        assertTrue(core.resourceManagement.backgroundSuspended)
    }

    // ------------------------------------------------------------------ watchdog

    @Test
    fun `report frame feeds the frame-budget watchdog`() {
        val (core, _) = build()
        // HIGH tier → 60fps → 16.6ms budget; a 20ms frame is a miss.
        core.reportFrame(1_000L, 20_000_000L)
        assertEquals(1, core.animationController.consecutiveFrameMisses)
        core.reportFrame(30_000_000L, 5_000_000L)
        assertEquals(0, core.animationController.consecutiveFrameMisses)
    }
}
