package com.jarvis.app.companioncore.engine

import com.jarvis.app.companioncore.contract.AlertLevel
import com.jarvis.app.companioncore.contract.CompanionSignal
import com.jarvis.app.companioncore.contract.UserPresenceEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sole writer for CompanionSignal, derived from Friday runtime evidence.
 *
 * The integration is a presentation/read model for Companion Core. It is not
 * a second cognitive authority and does not feed state back into Human Core.
 */
class FridayRuntimeIntegration(
    private val binding: FridayRuntimeBinding,
    private val discriminator: CurrentActionDiscriminator = CurrentActionDiscriminator(),
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val mutableSignal = MutableStateFlow(CompanionSignal.neutral())
    val signal: StateFlow<CompanionSignal> = mutableSignal.asStateFlow()
    val emotionLayer = EmotionExpressionLayer(nowMs = nowMs)

    @Volatile private var lastFreshAtMs: Long = Long.MIN_VALUE
    @Volatile private var lastSystemTierName: String? = null
    @Volatile private var forcedAlert: AlertLevel? = null

    fun alertLevel(): AlertLevel = forcedAlert ?: when (lastSystemTierName) {
        "OFFLINE" -> AlertLevel.OFFLINE
        "DEGRADED" -> AlertLevel.WARNING
        else -> AlertLevel.NONE
    }

    fun setAlertOverride(alert: AlertLevel?) {
        forcedAlert = alert
    }

    val stale: Boolean
        get() {
            val last = lastFreshAtMs
            return last == Long.MIN_VALUE ||
                nowMs() - last > CompanionClock.DEFAULT_STALENESS_THRESHOLD_MS
        }

    fun tick() {
        val snapshot = binding.read()
        snapshot.bridgeStatus?.let(discriminator::onBridgeStatus)
        discriminator.tickIdleTimeout()
        val action = snapshot.currentActionRaw ?: discriminator.current
        lastSystemTierName = snapshot.systemTierName

        val signal = SignalMapper.map(snapshot, action)
        mutableSignal.value = signal
        if (snapshot.isMeaningful) lastFreshAtMs = nowMs()
        emotionLayer.update(signal, stale = stale)
    }

    fun onUserSubmitted() {
        discriminator.onUserSubmitted()
        FridayPresenceEvidenceStore.recordUserInteraction(nowMs())
    }

    fun onFastPathAck() = discriminator.onFastPathAck()
    fun onFirstSegmentReady() = discriminator.onFirstSegmentReady()
    fun onUtteranceCompleted() = discriminator.onUtteranceCompleted()

    /** Record observed presence/lifecycle evidence locally; no external callback. */
    fun sendUserPresenceEvent(event: UserPresenceEvent): Boolean {
        FridayPresenceEvidenceStore.record(event)
        return true
    }
}

