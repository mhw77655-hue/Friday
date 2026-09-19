package com.jarvis.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 — the benchmark hook contract. RTF must be honest: synthesis wall-clock
 * divided by produced audio duration, computed over the rolling window.
 */
class VoiceMetricsTest {

    @Test
    fun `rolling Rtf divides wall clock by audio duration`() {
        VoiceMetrics.clear()
        // 1s wall clock for 2s of audio → RTF 0.5
        VoiceMetrics.recordSynthesis("sherpa", "en", wallClockMs = 1_000, audioMs = 2_000, sampleRate = 24000)
        val rtf = VoiceMetrics.rollingRtf()
        assertNotNull(rtf)
        assertEquals(0.5, rtf!!, 0.001)
    }

    @Test
    fun `snapshot carries provider and language`() {
        VoiceMetrics.clear()
        VoiceMetrics.recordSynthesis("system", "ar-EG", wallClockMs = 500, audioMs = 800)
        val snap = VoiceMetrics.snapshot()
        assertEquals(1, snap.size)
        assertEquals("system", snap[0].provider)
        assertEquals("ar-EG", snap[0].language)
    }

    @Test
    fun `model load and errors are recorded`() {
        VoiceMetrics.clear()
        VoiceMetrics.recordModelLoad("kokoro-int8-en-v0_19", durationMs = 1_200, ok = true)
        VoiceMetrics.recordError("sherpa", "boom")
        val snap = VoiceMetrics.snapshot()
        assertTrue(snap.any { it.kind == VoiceMetrics.Kind.MODEL_LOAD })
        assertTrue(snap.any { it.kind == VoiceMetrics.Kind.ERROR })
    }

    @Test
    fun `empty metrics yield null rtf`() {
        VoiceMetrics.clear()
        assertNull(VoiceMetrics.rollingRtf())
    }
}
