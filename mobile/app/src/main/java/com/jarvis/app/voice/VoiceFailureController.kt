package com.jarvis.app.voice

import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.Recoverability
import com.jarvis.app.failure.RecoveryAction

/**
 * R2 — classifies in-habitat voice failures into [FailureReport]s that cross
 * the IPC boundary to the app's FailureSurface (via [com.jarvis.app.voice.VoiceOrganismHost]).
 *
 * One controller, one classification policy — the provider/model/synthesis
 * failure taxonomy lives here, not scattered across providers.
 */
class VoiceFailureController(
    private val onReport: (FailureReport) -> Unit
) {

    fun onSynthesisFailure(providerId: String, error: String?, attempt: Int = 0) {
        onReport(
            FailureReport(
                subsystem = "VOICE_SYNTH",
                operation = "synthesize",
                severity = if (attempt == 0) FailureSeverity.RECOVERABLE else FailureSeverity.WARNING,
                category = FailureCategory.TTS,
                message = "voice synthesis failed ($providerId): ${error ?: "unknown"}",
                recoverability = Recoverability.RECOVERABLE,
                recoveryAction = if (attempt < 2) RecoveryAction.RETRY else RecoveryAction.NONE,
                attempt = attempt
            )
        )
    }

    fun onModelFailure(modelId: String, error: String?) {
        onReport(
            FailureReport(
                subsystem = "VOICE_MODEL",
                operation = "model_load",
                severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.MODEL,
                message = "voice model $modelId unavailable: ${error ?: "unknown"}",
                recoverability = Recoverability.RETRYABLE,
                recoveryAction = RecoveryAction.RETRY_WITH_BACKOFF
            )
        )
    }

    fun onProviderUnhealthy(providerId: String, error: String?) {
        onReport(
            FailureReport(
                subsystem = "VOICE_PROVIDER",
                operation = "health_check",
                severity = FailureSeverity.DEGRADED,
                category = FailureCategory.TTS,
                message = "voice provider $providerId unhealthy: ${error ?: "unknown"}",
                recoverability = Recoverability.RETRYABLE,
                recoveryAction = RecoveryAction.NONE
            )
        )
    }
}
