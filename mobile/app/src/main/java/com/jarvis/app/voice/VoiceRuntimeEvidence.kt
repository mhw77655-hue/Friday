package com.jarvis.app.voice

import org.json.JSONObject

/**
 * Content-free operational evidence for the isolated voice organ.
 * It reports lifecycle and output activity only, never speech text or audio.
 */
internal data class VoiceRuntimeEvidence(
    val hostAttached: Boolean,
    val habitatState: String,
    val voiceState: String,
    val voiceProcessAlive: Boolean,
    val voicePidPresent: Boolean,
    val binderPingLatencyMs: Long,
    val restartCount: Int,
    val lastRestartMs: Long,
    val outputPathAttached: Boolean,
    val isSpeaking: Boolean,
    val audioOutputActive: Boolean
) {
    fun toJson(): JSONObject = JSONObject()
        .put("host_attached", hostAttached)
        .put("habitat_state", habitatState.take(32))
        .put("voice_state", voiceState.take(32))
        .put("voice_process_alive", voiceProcessAlive)
        .put("voice_pid_present", voicePidPresent)
        .put("binder_ping_latency_ms", binderPingLatencyMs.coerceAtLeast(-1L))
        .put("restart_count", restartCount.coerceAtLeast(0))
        .put("last_restart_ms", lastRestartMs.coerceAtLeast(-1L))
        .put("output_path_attached", outputPathAttached)
        .put("is_speaking", isSpeaking)
        .put("audio_output_active", audioOutputActive)
}
