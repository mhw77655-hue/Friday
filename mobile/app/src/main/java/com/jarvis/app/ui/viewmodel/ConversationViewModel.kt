package com.jarvis.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.latency.LatencyLayer
import kotlinx.coroutines.flow.StateFlow

data class Turn(val fromJarvis: Boolean, val text: String, val verified: Boolean = true)

/**
 * Conversation UI adapter over the latency-first layer.
 *
 * The turn pipeline now lives in [LatencyLayer] (app-scoped): it owns the
 * pending-queue correlation, the reply completion, the ack speech, the model
 * warm-up, and the Companion Core reactor seams — and it runs the slow path
 * off the UI thread. This ViewModel is a thin adapter: it exposes the layer's
 * [turns]/[phase] flows and forwards [send] into [LatencyLayer.onUserInput],
 * which flips the reactor to THINKING, speaks a short pre-warmed ack, and only
 * then dispatches the deep reasoning path.
 *
 * [onCleared] still ends the Human Core session (MainActivity also ends it on
 * ON_STOP, guarded against config changes — endSession is idempotent).
 */
class ConversationViewModel : ViewModel() {

    val turns: StateFlow<List<Turn>> = LatencyLayer.turns
    val phase: StateFlow<LatencyLayer.Phase> = LatencyLayer.phase

    fun send(text: String) {
        if (text.isBlank()) return
        LatencyLayer.onUserInput(text)
    }

    override fun onCleared() {
        HumanCore.endSession()
        super.onCleared()
    }
}
