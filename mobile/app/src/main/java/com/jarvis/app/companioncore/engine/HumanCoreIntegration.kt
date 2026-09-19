package com.jarvis.app.companioncore.engine

import com.jarvis.app.companioncore.contract.AlertLevel
import com.jarvis.app.companioncore.contract.CompanionSignal
import com.jarvis.app.companioncore.contract.UserPresenceEvent
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * §2.30 — the single, narrow adapter through which the ENTIRE Companion Core
 * reads Human Core state. The structural enforcement point for the read-only,
 * one-directional contract (§0.3) and the mandatory choke point between the
 * two Cores: no other Companion Core subsystem imports Human Core internals
 * (the dependency-lint contract test asserts it), and every HC read happens
 * here.
 *
 * ## Sole-writer discipline (audit R-I6)
 * This class is the ONLY writer of [CompanionSignal]. It owns a private
 * [MutableStateFlow] exposed immutably via [signal]; every other subsystem
 * collects snapshots and never writes. `CompanionSignal` is an immutable
 * `@Stable`-style data class with no setters, so the discipline is structural
 * (audit R-I6 test asserts it).
 *
 * ## Tick cadence
 * [tick] is driven by the Presence Engine's tick loop (Phase 2, plan §1.6
 * Engine Tick Lane). Phase 1 ships the [tick] seam itself; tests drive it
 * deterministically. [tick] must complete in <1ms and never block.
 *
 * ## Staleness (spec §2.30 failure handling)
 * If no fresh signal arrives within the staleness threshold, the integration
 * flags [stale] and the Emotion Expression Layer falls back to neutral.
 *
 * ## `UserPresenceEvent` outward path (audit A-7/V-2)
 * [sendUserPresenceEvent] delivers advisories through the additive
 * `HumanCore.adviseUserPresence(...)` facade — never a direct
 * CC→`PresenceManager` call. Fire-and-forget, best-effort.
 */
class HumanCoreIntegration(
    private val binding: HumanCoreBinding,
    private val discriminator: CurrentActionDiscriminator = CurrentActionDiscriminator(),
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    private val _signal = MutableStateFlow(CompanionSignal.neutral())
    /** The current CompanionSignal — immutable snapshot; consumers never write. */
    val signal: StateFlow<CompanionSignal> = _signal.asStateFlow()

    /** The emotion fan-out layer (spec §2.9) — the only emotion reader. */
    val emotionLayer = EmotionExpressionLayer(nowMs = nowMs)

    /** Epoch millis of the last MEANINGFUL signal arrival (Long.MIN = never). */
    @Volatile private var lastFreshAtMs: Long = Long.MIN_VALUE

    /** Last-read HC presence mode name (ACTIVE/AWAY/DEGRADED) — feeds AlertLevel. */
    @Volatile private var lastHcPresenceModeName: String? = null

    /** Last-read HC system tier name (FULL/DEGRADED/OFFLINE/UNKNOWN) — feeds AlertLevel. */
    @Volatile private var lastHcSystemTierName: String? = null

    /**
     * Phase 2 — derive the Orb State Machine's [AlertLevel] from the HC
     * `PresenceState` read on the last tick. An explicit [forceAlert] override
     * (wired by callers for seams not yet driven, e.g. battery/emergency)
     * outranks the HC-derived value; otherwise HC `OFFLINE`/`DEGRADED`
     * availability maps to the matching alert. Never infers beyond what HC
     * actually reported.
     */
    @Volatile private var forceAlertOverride: AlertLevel? = null

    /** Derive the current [AlertLevel] for the render layer (Phase 2, §2.6). */
    fun hcAlertLevel(): AlertLevel = forceAlertOverride ?: when {
        lastHcSystemTierName == "OFFLINE" -> AlertLevel.OFFLINE
        lastHcSystemTierName == "DEGRADED" || lastHcPresenceModeName == "DEGRADED" -> AlertLevel.WARNING
        else -> AlertLevel.NONE
    }

    /** Force an alert overlay for seams not yet HC-driven (null clears the override). */
    fun setAlertOverride(alert: AlertLevel?) {
        forceAlertOverride = alert
    }

    /**
     * True when no fresh signal has arrived within the staleness threshold
     * (spec §2.30: default 2s). Live-computed: if ticks stop, staleness still
     * trips as time passes. An empty read (HC uninitialized / remote
     * unreachable) never counts as a fresh arrival, so staleness latches until
     * a meaningful signal actually arrives.
     */
    val stale: Boolean
        get() {
            val last = lastFreshAtMs
            if (last == Long.MIN_VALUE) return true
            return nowMs() - last > CompanionClock.DEFAULT_STALENESS_THRESHOLD_MS
        }

    /** One tick (driven by the Presence Engine, Phase 2). */
    fun tick() {
        val raw = binding.read()
        val now = nowMs()

        // Derive current_action: a binding that supplies it directly (remote)
        // wins; the local binding derives it via the discriminator from the
        // bridge-status string (plan §4.2.1).
        raw.bridgeStatus?.let { discriminator.onBridgeStatus(it) }
        discriminator.tickIdleTimeout()
        val action = raw.currentActionRaw ?: discriminator.current

        lastHcPresenceModeName = raw.presenceModeName
        lastHcSystemTierName = raw.systemTierName

        val mapped = SignalMapper.map(raw, action)
        _signal.value = mapped

        if (raw.isMeaningful) lastFreshAtMs = now

        emotionLayer.update(mapped, stale = stale)
    }

    // ---- conversation-boundary events feeding the discriminator (plan §4.2.1) ----

    /** User submitted text (from the Conversation loop). */
    fun onUserSubmitted() {
        discriminator.onUserSubmitted()
        (binding as? LocalHumanCoreBinding)?.onUserSubmitted()
    }

    /**
     * Latency-first seam (latency layer): the fast path has issued its spoken
     * acknowledgment, so drive the discriminator to GENERATING immediately —
     * the orb reaches THINKING on the next tick (~33ms) instead of waiting for
     * the bridge's first status observation.
     */
    fun onFastPathAck() = discriminator.onFastPathAck()

    /** The first reply segment is ready to speak (from Speech Timing, later phase). */
    fun onFirstSegmentReady() = discriminator.onFirstSegmentReady()

    /** The utterance finished speaking. */
    fun onUtteranceCompleted() = discriminator.onUtteranceCompleted()

    // ---- outward advisory path (audit A-7/V-2) ----

    /**
     * Deliver a user-presence advisory to the Human Core via the additive
     * facade. Fire-and-forget; returns the facade's acceptance (false when HC
     * is uninitialized or the event needs no HC state change).
     */
    fun sendUserPresenceEvent(event: UserPresenceEvent): Boolean =
        HumanCore.adviseUserPresence(
            eventType = event.eventType.name,
            timestamp = event.timestamp,
            confidence = event.confidence
        )
}
