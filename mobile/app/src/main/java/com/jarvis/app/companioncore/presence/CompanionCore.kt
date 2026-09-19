package com.jarvis.app.companioncore.presence

import com.jarvis.app.companioncore.contract.CompanionState
import com.jarvis.app.companioncore.contract.PresenceMode
import com.jarvis.app.companioncore.contract.RenderIntent
import com.jarvis.app.companioncore.contract.UserPresenceEvent
import com.jarvis.app.companioncore.contract.VisualState
import com.jarvis.app.companioncore.engine.CompanionClock
import com.jarvis.app.companioncore.engine.HumanCoreIntegration
import com.jarvis.app.companioncore.render.AnimationController
import com.jarvis.app.companioncore.render.OrbRenderParams
import com.jarvis.app.companioncore.render.OrbStateMachine
import com.jarvis.app.companioncore.resource.MobileResourceManagement
import com.jarvis.app.companioncore.resource.MobileResourceManagement.DeviceConditions
import com.jarvis.app.vf.motion.MotionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive

/**
 * §2.1 + §1.6 — Companion Core coordinator.
 *
 * The Application-scoped root of the Companion Core. Owns the **Engine Tick
 * Lane** (plan §1.6: a single-thread dispatcher; never blocks, no I/O, no
 * framework-UI calls, <1ms per tick) and the fixed per-tick fan-out order:
 *
 * ```
 * HumanCoreIntegration.tick()  (§2.30 signal + §2.9 emotion)
 *         └──► PresenceEngine.tick()   (§2.1 — owns presence_mode, sole producer of CompanionState)
 *                 └──► OrbStateMachine  (§2.6 — pure params + §1 RenderIntent)
 *                         └──► AnimationController.submit(intent)  (§2.21 arbitration)
 * MobileResourceManagement.evaluate()  (§2.31 — tier caps consumed by the render lane)
 * ```
 *
 * [start] launches the tick loop on the lane scope at ~30Hz foreground and
 * 0.1Hz background ([onBackground]/[onForeground], plan §1.7). The UI/Render
 * lane collects the immutable StateFlows ([state], [emotion], [renderParams],
 * [renderIntent]) and never writes to them. [reportFrame] is the render lane's
 * one inbound channel (frame-budget watchdog).
 *
 * Pure-JVM testable: all collaborators are injectable; [tick] is deterministic.
 */
class CompanionCore(
    val integration: HumanCoreIntegration,
    val presenceEngine: PresenceEngine,
    private val orbStateMachine: OrbStateMachine,
    val animationController: AnimationController,
    val resourceManagement: MobileResourceManagement,
    private val clock: CompanionClock = CompanionClock(),
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    /** Supplies thermal/battery conditions (Android side); defaults to "unknown". */
    private val conditionsProvider: () -> DeviceConditions = {
        DeviceConditions(
            thermalThrottling = false,
            thermalUnknown = true,
            batteryPercent = -1,
            charging = true,
            consecutiveFrameMisses = 0
        )
    }
) {

    private val _renderParams = MutableStateFlow(OrbRenderParams.idle())
    private val _renderIntent = MutableStateFlow(RenderIntent(visualState = VisualState.IDLE_BREATHE))
    private val _audioAmplitude = MutableStateFlow(0f)

    // §2.31 render-lane inputs: the current budget and the background-suspension
    // flag, refreshed each tick so the render lane never reads the governor's
    // mutable state directly (audit F3). StateFlow equality keeps emission at
    // the rate the values actually change.
    private val _budget = MutableStateFlow(resourceManagement.budget)
    private val _suspended = MutableStateFlow(resourceManagement.backgroundSuspended)

    private var tickCounter = 0

    @Volatile
    private var backgrounded: Boolean = false

    private var tickJob: Job? = null
    private var wokeOnce: Boolean = false

    /** The current CompanionState — emitted by the Presence Engine each tick. */
    val state: StateFlow<CompanionState> get() = presenceEngine.state

    /**
     * The single active presence mode, as a rarely-changing derived flow.
     * Safe to collect in the UI layer for labels/diagnostics without pulling
     * the full 30Hz `CompanionState` into composition (audit R-P2).
     */
    val presenceMode: kotlinx.coroutines.flow.Flow<PresenceMode> =
        presenceEngine.state.map { it.presenceMode }

    /** The single active presence mode (stateful read; engine tick lane view). */
    val currentPresenceMode: PresenceMode get() = presenceEngine.mode

    /** Frozen emotion snapshot per tick (§2.9). */
    val emotion get() = integration.emotionLayer.snapshots

    /** Resolved orb render parameters for the current frame (§2.6). */
    val renderParams: StateFlow<OrbRenderParams> = _renderParams.asStateFlow()

    /** The §1 RenderIntent the Animation Controller is arbitrating. */
    val renderIntent: StateFlow<RenderIntent> = _renderIntent.asStateFlow()

    /** Live audio amplitude 0..1 — the voice/visualization hook input (§2.6). */
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    /** §2.31 current resource budget — collected by the render lane for pacing. */
    val budget: StateFlow<MobileResourceManagement.ResourceBudget> = _budget.asStateFlow()

    /** True when rendering is suspended (whole process backgrounded, plan §1.7). */
    val suspended: StateFlow<Boolean> = _suspended.asStateFlow()

    /** Start the tick loop on [scope] (the Engine Tick Lane). Idempotent. */
    fun start(scope: CoroutineScope) {
        if (tickJob != null) return
        if (!wokeOnce) {
            wokeOnce = true
            presenceEngine.requestTransition(PresenceMode.WAKING, "CompanionCore", "cold-start")
        }
        tickJob = scope.launch {
            while (isActive) {
                tick()
                delay(if (backgrounded) BACKGROUND_TICK_INTERVAL_MS else TICK_INTERVAL_MS)
            }
        }
    }

    /** Cancel the tick loop. */
    fun stop() {
        tickJob?.cancel()
        tickJob = null
    }

    /**
     * One engine tick — MUST complete <1ms and never block. Runs on the Engine
     * Tick Lane. See class doc for the fan-out order.
     */
    fun tick() {
        integration.tick()
        val signal = integration.signal.value
        val emotion = integration.emotionLayer.snapshots.value
        val alert = integration.hcAlertLevel()

        // Wake handoff: the WAKING bloom lasts the §12 startup window, then
        // the engine settles to IDLE unless something explicit preempts it.
        if (presenceEngine.mode == PresenceMode.WAKING &&
            nowMs() - presenceEngine.modeEnteredAt >= MotionEngine.DurStartupShutdown
        ) {
            presenceEngine.requestTransition(PresenceMode.IDLE, "CompanionCore", "wake-complete")
        }

        presenceEngine.tick(
            signal = signal,
            emotion = emotion,
            stale = integration.stale,
            lastRenderEpochMs = animationController.lastRenderEpochMs
        )

        val state = presenceEngine.state.value
        _renderParams.value = orbStateMachine.map(
            state,
            alert,
            _audioAmplitude.value,
            maxParticleDensity = resourceManagement.budget.maxParticleDensity
        )
        val intent = orbStateMachine.resolveIntent(state, alert)
        _renderIntent.value = intent
        // Authoritative per-tick baseline: always applied, so an alert that
        // clears (URGENT → NORMAL) cannot leave a stale alert intent active.
        animationController.submitState(intent)

        // §2.31 governor — evaluate thermal/battery only ~1Hz, not every 33ms
        // tick: the Android system-service reads (getThermalHeadroom +
        // BatteryManager props) are the priciest call on the lane and their
        // signal changes far slower than a tick (audit F5). In the 0.1Hz
        // background tick this becomes ~every 5min, which is fine — rendering
        // is suspended there anyway.
        tickCounter++
        if (tickCounter >= CONDITIONS_EVAL_INTERVAL_TICKS) {
            tickCounter = 0
            val conditions = conditionsProvider()
            resourceManagement.evaluate(
                conditions.copy(consecutiveFrameMisses = animationController.consecutiveFrameMisses)
            )
        }
        // Keep the render-lane flows in lock-step with the governor each tick.
        _budget.value = resourceManagement.budget
        _suspended.value = resourceManagement.backgroundSuspended
    }

    // ------------------------------------------------------------- lifecycle

    /** App backgrounded (process ON_STOP): suspend rendering, keep low-rate tick. */
    fun onBackground() {
        if (backgrounded) return
        backgrounded = true
        resourceManagement.setBackgroundSuspended(true)
        _suspended.value = true
        integration.sendUserPresenceEvent(
            UserPresenceEvent(
                eventType = UserPresenceEvent.EventType.DEVICE_BACKGROUNDED,
                timestamp = nowMs(),
                confidence = 1.0
            )
        )
    }

    /** App foregrounded (process ON_START): resume render, full-rate tick. */
    fun onForeground() {
        if (!backgrounded) return
        backgrounded = false
        resourceManagement.setBackgroundSuspended(false)
        _suspended.value = false
        integration.sendUserPresenceEvent(
            UserPresenceEvent(
                eventType = UserPresenceEvent.EventType.USER_PRESENT,
                timestamp = nowMs(),
                confidence = 1.0
            )
        )
    }

    // ------------------------------------------------------------- render lane inbound

    /**
     * Report one rendered frame (called from the render lane via
     * `withFrameNanos`). Feeds the Animation Controller frame-budget watchdog
     * → §2.31 tier steps.
     */
    fun reportFrame(frameStartElapsedNanos: Long, frameDurationNanos: Long) {
        val period = animationController.framePeriodNanos(resourceManagement.budget.targetFps)
        animationController.recordFrame(frameStartElapsedNanos, frameDurationNanos, period)
    }

    /**
     * Master-clock frame period for a target fps — the single source of truth
     * the render lane's pacing reads (§2.21 R-P5/M-16, audit F3).
     */
    fun framePeriodNanos(targetFps: Double): Long = animationController.framePeriodNanos(targetFps)

    /**
     * Convert a render-lane frame timestamp (`withFrameNanos`'s value, on the
     * `System.nanoTime()` base) into master-elapsed nanoseconds — the domain
     * the Animation Controller watchdog expects (audit F4).
     */
    fun masterElapsedNanos(frameTimeNanos: Long): Long = clock.masterElapsedNanos(frameTimeNanos)

    /** Voice/visualization hook — set live audio amplitude 0..1 (0 = silent). */
    fun setAudioAmplitude(amplitude: Float) {
        _audioAmplitude.value = amplitude.coerceIn(0f, 1f)
    }

    companion object {
        /** Foreground tick cadence (~30Hz, spec §2.1). */
        const val TICK_INTERVAL_MS = 33L

        /** Background tick cadence (0.1Hz, plan §1.7 / §2.31). */
        const val BACKGROUND_TICK_INTERVAL_MS = 10_000L

        /** §2.31 device-condition evaluation every N ticks (30 @ 30Hz ≈ 1Hz). */
        const val CONDITIONS_EVAL_INTERVAL_TICKS = 30
    }
}
