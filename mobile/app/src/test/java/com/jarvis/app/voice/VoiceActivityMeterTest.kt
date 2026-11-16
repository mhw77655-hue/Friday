package com.jarvis.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActivityMeterTest {

    @Test
    fun silenceProducesNoMouthActivity() {
        assertEquals(0f, pcmWindowLevel(byteArrayOf(0, 0, 0, 0), 0, 4), 0.0001f)
    }

    @Test
    fun fullScalePcmIsClampedForUiUse() {
        val loud = byteArrayOf(0xFF.toByte(), 0x7F, 0xFF.toByte(), 0x7F)
        assertEquals(1f, pcmWindowLevel(loud, 0, loud.size), 0.0001f)
    }

    @Test
    fun meterReadsOnlyTheRequestedPlaybackWindow() {
        val pcm = byteArrayOf(
            0, 0, // silence before the playhead window
            0xFF.toByte(), 0x7F,
            0xFF.toByte(), 0x7F
        )
        assertEquals(0f, pcmWindowLevel(pcm, 0, 2), 0.0001f)
        assertTrue(pcmWindowLevel(pcm, 2, pcm.size) > 0.9f)
    }

    @Test
    fun invalidAndEmptyWindowsAreSafe() {
        assertEquals(0f, pcmWindowLevel(byteArrayOf(1), 0, 1), 0.0001f)
        assertEquals(0f, pcmWindowLevel(byteArrayOf(1, 2), 2, 1), 0.0001f)
    }
}
