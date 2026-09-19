package com.jarvis.app.vf

import com.jarvis.app.vf.reactor.ReactorEngine
import com.jarvis.app.vf.tokens.JvReactorStateColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReactorEngine: all 13 states resolve to correct specs per 07_REACTOR.md §6.
 */
class ReactorEngineTest {

    @Test
    fun `all 13 states produce valid specs`() {
        for (state in ReactorEngine.ReactorSpecState.entries) {
            val spec = ReactorEngine.spec(state)
            assertTrue("spec for $state must have rings ≥ 0", spec.ringCount >= 0)
            assertTrue("spec for $state must have particleCount ≥ 0", spec.particleCount >= 0)
            assertTrue("spec for $state breathingMs must be ≥ 0", spec.breathingMs >= 0)
            assertTrue("spec for $state breathingAmplitude must be in [0, 0.12]", spec.breathingAmplitude in 0f..0.12f)
        }
    }

    @Test
    fun `offline state has no motion`() {
        val spec = ReactorEngine.spec(ReactorEngine.ReactorSpecState.OFFLINE)
        assertEquals(0L, spec.breathingMs)
        assertEquals(0f, spec.breathingAmplitude, 0.001f)
        assertEquals(0f, spec.ringSpeedFactor, 0.001f)
        assertEquals(0, spec.particleCount)
        assertEquals(ReactorEngine.ReactorSpec.PulseMode.NONE, spec.pulseMode)
    }

    @Test
    fun `warning and critical carry the sharp alert pulse`() {
        // §3.8 / §5.7 / §07 §6: Warning ~1Hz, Critical ~2Hz — distinct,
        // non-overlapping flash rates. The renderers read exactly these.
        assertEquals(
            ReactorEngine.ReactorSpec.PulseMode.SHARP_1HZ,
            ReactorEngine.spec(ReactorEngine.ReactorSpecState.WARNING).pulseMode
        )
        assertEquals(
            ReactorEngine.ReactorSpec.PulseMode.SHARP_2HZ,
            ReactorEngine.spec(ReactorEngine.ReactorSpecState.CRITICAL).pulseMode
        )
    }

    @Test
    fun `sleeping state has minimal motion`() {
        val spec = ReactorEngine.spec(ReactorEngine.ReactorSpecState.SLEEPING)
        assertEquals(7000L, spec.breathingMs)
        assertTrue("sleep ring speed very slow", spec.ringSpeedFactor < 0.2f)
        assertEquals(0, spec.particleCount)
        assertEquals(2, spec.ringCount)
    }

    @Test
    fun `thinking states have flicker`() {
        for (state in listOf(
            ReactorEngine.ReactorSpecState.THINKING,
            ReactorEngine.ReactorSpecState.RESEARCH,
            ReactorEngine.ReactorSpecState.PLANNING,
            ReactorEngine.ReactorSpecState.CODING,
            ReactorEngine.ReactorSpecState.LEARNING
        )) {
            assertTrue("$state should have flicker", ReactorEngine.spec(state).hasFlicker)
        }
    }

    @Test
    fun `warning state uses 1Hz pulse`() {
        val spec = ReactorEngine.spec(ReactorEngine.ReactorSpecState.WARNING)
        assertEquals(ReactorEngine.ReactorSpec.PulseMode.SHARP_1HZ, spec.pulseMode)
    }

    @Test
    fun `critical state uses 2Hz pulse`() {
        val spec = ReactorEngine.spec(ReactorEngine.ReactorSpecState.CRITICAL)
        assertEquals(ReactorEngine.ReactorSpec.PulseMode.SHARP_2HZ, spec.pulseMode)
    }

    @Test
    fun `research has a scan interval`() {
        val spec = ReactorEngine.spec(ReactorEngine.ReactorSpecState.RESEARCH)
        assertEquals(2000L, spec.researchScanMs)
    }

    @Test
    fun `planning has ring alignment`() {
        val spec = ReactorEngine.spec(ReactorEngine.ReactorSpecState.PLANNING)
        assertTrue("planning should have ring alignment", spec.ringAlignment)
    }

    @Test
    fun `building and executing share same color`() {
        val building = ReactorEngine.spec(ReactorEngine.ReactorSpecState.BUILDING)
        assertEquals("building and executing should share color", building.state.color.hex, JvReactorStateColor.BUILDING.hex)
        assertTrue("building is building family", building.isBuildingFamily)
    }
}
