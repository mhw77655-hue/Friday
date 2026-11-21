package ai.friday.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import ai.friday.assistant.latency.LatencyLayer
import kotlinx.coroutines.flow.StateFlow

data class Turn(val fromFriday: Boolean, val text: String, val verified: Boolean = true)

/**
 * Conversation UI adapter over the latency-first layer.
 *
 * The turn pipeline now lives in [LatencyLayer] (app-scoped): it owns the
 * pending-queue correlation, the reply completion, the ack speech, the model
 * warm-up, and the Companion Core reactor seams — and it runs the slow path
 * off the UI thread. This ViewModel is a thin adapter: it exposes the layer's
 * [turns]/[phase] flows only. Input submission belongs to BodyCoordinator so
 * typed and spoken input share phone-action routing, confirmation and
 * conversation execution rather than bypassing the body-owned interaction path.
 */
class ConversationViewModel : ViewModel() {

    val turns: StateFlow<List<Turn>> = LatencyLayer.turns
    val phase: StateFlow<LatencyLayer.Phase> = LatencyLayer.phase

}
