package ai.friday.assistant.body

import ai.friday.assistant.companioncore.contract.AlertLevel
import ai.friday.assistant.companioncore.contract.PresenceMode
import ai.friday.assistant.companioncore.engine.FridayRuntimeIntegration
import ai.friday.assistant.companioncore.presence.CompanionCoreHolder
import ai.friday.assistant.companioncore.presence.PresenceEngine
import ai.friday.assistant.vf.reactor.ReactorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Bridge between BodyCoordinator state and Visual Foundation (Orb/Reactor).
 *
 * Drives the visual system from actual nervous system state — no fake
 * animations. The orb's presence mode comes from [PresenceEngine], so a body
 * state change is pushed as a transition request on the Companion Core's tick
 * lane; the alert level (WARNING/CRITICAL/OFFLINE) is layered on top.
 */
class VisualStateBridge(
    private val bodyState: StateFlow<BodyState>,
    private val visualState: StateFlow<VisualState>,
    private val companionIntegration: FridayRuntimeIntegration,
    private val failureWorstSeverity: kotlinx.coroutines.flow.StateFlow<ai.friday.assistant.failure.FailureSeverity>,
    private val scope: CoroutineScope
) {
    private var lastBodyState: BodyState = BodyState.IDLE
    private var lastFailureAlert: AlertLevel = AlertLevel.NONE

    init {
        scope.launch(Dispatchers.Main) {
            bodyState.collect { state ->
                onBodyStateChanged(state)
            }
        }

        scope.launch(Dispatchers.Main) {
            visualState.collect { vState ->
                onVisualStateChanged(vState)
            }
        }

        // Failure layer: a failing body must be visible in the orb even when
        // the turn state looks idle. When the nervous system reports an active
        // WARNING+ failure, raise the alert override above whatever the body
        // state alone would produce.
        scope.launch(Dispatchers.Main) {
            failureWorstSeverity.collect { severity ->
                val alert = mapFailureToAlert(severity)
                if (alert != lastFailureAlert) {
                    lastFailureAlert = alert
                    refreshAlert(alert)
                }
            }
        }
    }

    private fun mapFailureToAlert(severity: ai.friday.assistant.failure.FailureSeverity): AlertLevel = when {
        severity.rank >= ai.friday.assistant.failure.FailureSeverity.ERROR.rank -> AlertLevel.CRITICAL
        severity.rank >= ai.friday.assistant.failure.FailureSeverity.WARNING.rank -> AlertLevel.WARNING
        else -> AlertLevel.NONE
    }

    private fun onBodyStateChanged(state: BodyState) {
        if (state == lastBodyState) return
        lastBodyState = state

        // Push the nervous-system mode into the Companion Core presence engine
        // so the orb follows real body state (requestTransition is resolved on
        // the next tick; requester "body" distinguishes it from boot/other).
        val presenceEngine = CompanionCoreHolder.instance()?.presenceEngine ?: return
        val mode = mapToPresenceMode(state)
        presenceEngine.requestTransition(
            mode = mode,
            requester = "body",
            reason = "nervous system → ${state.name}"
        )

        updateCompanionPresence(state)
    }

    private fun onVisualStateChanged(vState: VisualState) {
        // Emotion/arousal drive the orb's brightness & pulse on the next frame;
        // the mode transition was already requested above.
    }

    /** Body interaction → Friday's continuous presence plus transient body expression. */
    private fun mapToPresenceMode(bodyState: BodyState): PresenceMode = when (bodyState) {
        BodyState.LISTENING, BodyState.HEARING, BodyState.INTERRUPTED ->
            PresenceMode.LISTENING
        BodyState.SPEAKING -> PresenceMode.SPEAKING
        // Processing, recovery, learning and user-absence are activities or
        // context changes of the same present individual, never ASLEEP/IDLE/
        // THINKING existence modes.
        BodyState.IDLE, BodyState.WAKE, BodyState.THINKING, BodyState.RETRIEVING,
        BodyState.RESPONDING, BodyState.LEARNING, BodyState.ERROR,
        BodyState.USER_PRESENT, BodyState.USER_ABSENT -> PresenceMode.PRESENT
    }

    private fun updateCompanionPresence(state: BodyState) {
        // Alert layer: explicit body ERROR/INTERRUPTED outrank the HC-derived
        // alert; otherwise fall through to whatever HC reported this tick.
        val alert = when (state) {
            BodyState.ERROR -> AlertLevel.CRITICAL
            BodyState.INTERRUPTED -> AlertLevel.WARNING
            else -> companionIntegration.alertLevel()
        }
        refreshAlert(alert)
    }

    /** Merge the body-state alert with the nervous-system failure alert and
     *  publish the worst of the two. */
    private fun refreshAlert(stateAlert: AlertLevel) {
        val merged = maxOf(stateAlert.rank, lastFailureAlert.rank)
        companionIntegration.setAlertOverride(AlertLevel.entries.first { it.rank == merged })
    }
}
