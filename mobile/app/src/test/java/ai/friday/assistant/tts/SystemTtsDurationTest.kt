package ai.friday.assistant.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemTtsDurationTest {

    @Test
    fun subOneSpeechRateDoesNotTruncateToZero() {
        assertEquals(6_666L, estimateSystemTtsDurationMs(100, 0.75f))
    }

    @Test
    fun invalidSpeechRateFallsBackToSafeDefault() {
        assertEquals(5_000L, estimateSystemTtsDurationMs(100, 0f))
        assertEquals(5_000L, estimateSystemTtsDurationMs(100, Float.NaN))
        assertEquals(5_000L, estimateSystemTtsDurationMs(100, Float.POSITIVE_INFINITY))
    }

    @Test
    fun emptyTextStillHasMinimumDuration() {
        assertEquals(500L, estimateSystemTtsDurationMs(0, 1f))
        assertTrue(estimateSystemTtsDurationMs(100, 4f) > 0L)
    }
}
