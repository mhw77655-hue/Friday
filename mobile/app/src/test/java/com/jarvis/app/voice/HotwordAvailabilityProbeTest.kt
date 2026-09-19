package com.jarvis.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotwordAvailabilityProbeTest {

    private class FakeHotwordPort(
        var platformStt: Boolean = true,
        var soundTriggerStatus: Int? = null,
        val logs: MutableList<String> = mutableListOf()
    ) : HotwordSupportPort {
        override fun isPlatformSttAvailable(): Boolean = platformStt
        override fun soundTriggerAvailabilityStatus(): Int? = soundTriggerStatus
        override fun log(message: String) { logs.add(message) }
    }

    // ── AC1: classification is honest, never assumes the platform supports hotword ──

    @Test
    fun `max unspecified - no platform STT collapses to unsupported`() {
        assertEquals(
            HotwordAvailability.UNSUPPORTED,
            HotwordAvailabilityProbe.classify(platformSttAvailable = false, soundTriggerStatus = null)
        )
        assertEquals(
            HotwordAvailability.UNSUPPORTED,
            HotwordAvailabilityProbe.classify(platformSttAvailable = false, soundTriggerStatus = HotwordAvailabilityProbe.STATE_KEYPHRASE_ENROLLED)
        )
    }

    @Test
    fun `unknown soundTrigger status is NOT assumed supported`() {
        // Platform STT exists but SoundTrigger status could not be determined —
        // the probe must report UNKNOWN, never pretend hotword is available.
        assertEquals(
            HotwordAvailability.UNKNOWN,
            HotwordAvailabilityProbe.classify(platformSttAvailable = true, soundTriggerStatus = null)
        )
    }

    @Test
    fun `hardware unsupported or service unavailable maps to unsupported`() {
        assertEquals(
            HotwordAvailability.UNSUPPORTED,
            HotwordAvailabilityProbe.classify(true, HotwordAvailabilityProbe.STATE_HARDWARE_UNSUPPORTED)
        )
        assertEquals(
            HotwordAvailability.UNSUPPORTED,
            HotwordAvailabilityProbe.classify(true, HotwordAvailabilityProbe.STATE_SERVICE_UNAVAILABLE)
        )
    }

    @Test
    fun `enrolled or unenrolled keyphrase maps to supported`() {
        assertEquals(
            HotwordAvailability.SUPPORTED,
            HotwordAvailabilityProbe.classify(true, HotwordAvailabilityProbe.STATE_KEYPHRASE_ENROLLED)
        )
        assertEquals(
            HotwordAvailability.SUPPORTED,
            HotwordAvailabilityProbe.classify(true, HotwordAvailabilityProbe.STATE_KEYPHRASE_UNENROLLED)
        )
    }

    @Test
    fun `queryAndLog runs the real probe and publishes the classified result plus a log line`() {
        HotwordAvailabilityProbe.queryAndLog(
            FakeHotwordPort(platformStt = true, soundTriggerStatus = HotwordAvailabilityProbe.STATE_KEYPHRASE_ENROLLED)
        )
        assertEquals(HotwordAvailability.SUPPORTED, HotwordAvailabilityProbe.availability.value)

        HotwordAvailabilityProbe.queryAndLog(FakeHotwordPort(platformStt = true, soundTriggerStatus = null))
        assertEquals(HotwordAvailability.UNKNOWN, HotwordAvailabilityProbe.availability.value)

        val port = FakeHotwordPort(
            platformStt = true,
            soundTriggerStatus = HotwordAvailabilityProbe.STATE_HARDWARE_UNSUPPORTED
        )
        val result = HotwordAvailabilityProbe.queryAndLog(port)
        assertEquals(HotwordAvailability.UNSUPPORTED, result)
        assertEquals(HotwordAvailability.UNSUPPORTED, HotwordAvailabilityProbe.availability.value)
        assertTrue(port.logs.any { it.contains("hardware_unsupported") })
    }

    // ── AC5: only a proven SUPPORTED enables continuous wake; everything else
    //    degrades to the manual tap-to-trigger STT path (tracked gap) ──

    @Test
    fun `wake strategy only continuous on proven support`() {
        assertEquals(WakeStrategy.CONTINUOUS, wakeStrategyFor(HotwordAvailability.SUPPORTED))
        assertEquals(WakeStrategy.MANUAL_TAP_TO_TRIGGER, wakeStrategyFor(HotwordAvailability.UNSUPPORTED))
        assertEquals(WakeStrategy.MANUAL_TAP_TO_TRIGGER, wakeStrategyFor(HotwordAvailability.UNKNOWN))
    }
}