package com.jarvis.app.vf

import com.jarvis.app.vf.particle.ParticleEngine
import com.jarvis.app.vf.tokens.JvReactorStateColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ParticleEngine: lifecycle, direction, density, orbital computation.
 */
class ParticleEngineTest {

    @Test
    fun `lifecycle opacity fades in then out`() {
        // Pre-fade (0-15%)
        assertEquals(0f, ParticleEngine.lifecycleOpacity(0f), 0.01f)
        assertEquals(0.5f, ParticleEngine.lifecycleOpacity(0.075f), 0.01f)
        // Full opacity (15-75%)
        assertEquals(1f, ParticleEngine.lifecycleOpacity(0.5f), 0.01f)
        assertEquals(1f, ParticleEngine.lifecycleOpacity(0.15f), 0.01f)
        assertEquals(1f, ParticleEngine.lifecycleOpacity(0.75f), 0.01f)
        // Fade out (75-100%)
        assertEquals(0.5f, ParticleEngine.lifecycleOpacity(0.875f), 0.01f)
        assertEquals(0f, ParticleEngine.lifecycleOpacity(1f), 0.01f)
    }

    @Test
    fun `particle count is zero for sleep and offline`() {
        assertEquals(0, ParticleEngine.stateCount(JvReactorStateColor.SLEEPING))
        assertEquals(0, ParticleEngine.stateCount(JvReactorStateColor.OFFLINE))
    }

    @Test
    fun `particle count is highest for research`() {
        val research = ParticleEngine.stateCount(JvReactorStateColor.RESEARCH)
        val idle = ParticleEngine.stateCount(JvReactorStateColor.IDLE)
        assertTrue("research should have more particles than idle", research > idle)
    }

    @Test
    fun `direction grammar thinking is inward`() {
        assertEquals(ParticleEngine.ParticleDirection.INWARD, ParticleEngine.stateDirection(JvReactorStateColor.THINKING))
        assertEquals(ParticleEngine.ParticleDirection.OUTWARD, ParticleEngine.stateDirection(JvReactorStateColor.BUILDING))
        assertEquals(ParticleEngine.ParticleDirection.SPIRAL_IN, ParticleEngine.stateDirection(JvReactorStateColor.LEARNING))
        assertEquals(ParticleEngine.ParticleDirection.NONE, ParticleEngine.stateDirection(JvReactorStateColor.OFFLINE))
    }

    @Test
    fun `precomputeFixed produces requested count`() {
        val particles = ParticleEngine.precomputeFixed(100, seed = 42)
        assertEquals("precompute should produce requested count", 100, particles.size)
    }

    @Test
    fun `precomputeFixed is deterministic with same seed`() {
        val a = ParticleEngine.precomputeFixed(50, seed = 123)
        val b = ParticleEngine.precomputeFixed(50, seed = 123)
        for (i in a.indices) {
            assertEquals("angle[$i]", a[i].angle, b[i].angle)
            assertEquals("radialFraction[$i]", a[i].radialFraction, b[i].radialFraction, 1e-6f)
        }
    }
}
