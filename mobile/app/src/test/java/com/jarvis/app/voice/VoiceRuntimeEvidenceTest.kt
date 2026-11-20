package com.jarvis.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRuntimeEvidenceTest {
    @Test
    fun `serializes operational voice health without speech content`() {
        val evidence = VoiceRuntimeEvidence(
            hostAttached = true,
            habitatState = "BOUND",
            voiceState = "READY",
            voiceProcessAlive = true,
            voicePidPresent = true,
            binderPingLatencyMs = 8,
            restartCount = 2,
            lastRestartMs = 140,
            outputPathAttached = true,
            isSpeaking = true,
            audioOutputActive = true
        )

        val json = evidence.toJson()

        assertTrue(json.getBoolean("host_attached"))
        assertEquals("BOUND", json.getString("habitat_state"))
        assertEquals("READY", json.getString("voice_state"))
        assertTrue(json.getBoolean("voice_process_alive"))
        assertEquals(2, json.getInt("restart_count"))
        assertTrue(json.getBoolean("audio_output_active"))
        assertFalse(json.has("transcript"))
        assertFalse(json.has("speech_text"))
        assertFalse(json.has("audio_file"))
    }

    @Test
    fun `unavailable voice path is reported explicitly`() {
        val json = VoiceRuntimeEvidence(
            hostAttached = false,
            habitatState = "UNAVAILABLE",
            voiceState = "UNAVAILABLE",
            voiceProcessAlive = false,
            voicePidPresent = false,
            binderPingLatencyMs = -1,
            restartCount = 0,
            lastRestartMs = -1,
            outputPathAttached = false,
            isSpeaking = false,
            audioOutputActive = false
        ).toJson()

        assertEquals("UNAVAILABLE", json.getString("habitat_state"))
        assertFalse(json.getBoolean("host_attached"))
        assertFalse(json.getBoolean("output_path_attached"))
        assertEquals(-1, json.getLong("binder_ping_latency_ms"))
    }
}
