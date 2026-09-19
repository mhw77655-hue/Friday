package com.jarvis.app.vf

import com.jarvis.app.vf.animation.AnimationEngine
import com.jarvis.app.vf.tokens.JvReactorStateColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AnimationEngine: transition matrix, crossfade paths, pulse frequencies.
 */
class AnimationEngineTest {

    @Test
    fun `same state gives direct transition`() {
        val path = AnimationEngine.resolveTransition(JvReactorStateColor.IDLE, JvReactorStateColor.IDLE)
        assertTrue(path is AnimationEngine.AnimationPath.Direct)
    }

    @Test
    fun `amber members are direct with each other`() {
        for (from in listOf(JvReactorStateColor.THINKING, JvReactorStateColor.RESEARCH, JvReactorStateColor.PLANNING, JvReactorStateColor.LEARNING)) {
            for (to in listOf(JvReactorStateColor.THINKING, JvReactorStateColor.RESEARCH, JvReactorStateColor.PLANNING, JvReactorStateColor.LEARNING)) {
                val path = AnimationEngine.resolveTransition(from, to)
                assertTrue("amber↔amber should be direct: $from→$to", path is AnimationEngine.AnimationPath.Direct)
            }
        }
    }

    @Test
    fun `thinking to warning goes through neutral`() {
        val path = AnimationEngine.resolveTransition(JvReactorStateColor.THINKING, JvReactorStateColor.WARNING)
        assertTrue("amber→red should pass through neutral", path is AnimationEngine.AnimationPath.ThroughNeutral)
    }

    @Test
    fun `idle to sleep is direct`() {
        val path = AnimationEngine.resolveTransition(JvReactorStateColor.IDLE, JvReactorStateColor.SLEEPING)
        assertTrue("blue→violet is direct", path is AnimationEngine.AnimationPath.Direct)
    }

    @Test
    fun `any to offline goes through neutral`() {
        val path = AnimationEngine.resolveTransition(JvReactorStateColor.IDLE, JvReactorStateColor.OFFLINE)
        assertTrue("any→offline should pass through neutral", path is AnimationEngine.AnimationPath.ThroughNeutral)
    }

    @Test
    fun `resolvedColor at t=0 is from color`() {
        val path = AnimationEngine.resolveTransition(JvReactorStateColor.IDLE, JvReactorStateColor.THINKING)
        val c = AnimationEngine.resolvedColor(path, 0f)
        assertEquals("at t=0, color should be IDLE", JvReactorStateColor.IDLE.hex, c.hashCode().toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun `resolvedColor at t=1 is to color`() {
        val path = AnimationEngine.resolveTransition(JvReactorStateColor.IDLE, JvReactorStateColor.THINKING)
        val c = AnimationEngine.resolvedColor(path, 1f)
        assertEquals("at t=1, color should be THINKING", JvReactorStateColor.THINKING.hex, c.hashCode().toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun `through neutral mid flash is steel`() {
        val path = AnimationEngine.AnimationPath.ThroughNeutral(JvReactorStateColor.IDLE, JvReactorStateColor.WARNING)
        val c = AnimationEngine.resolvedColor(path, 0.5f)
        assertEquals("at t=0.5 through-neutral should be steel", 0xFF8A94A6, c.hashCode().toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun `every state-to-state transition resolves without throwing`() {
        // Regression: CODING was missing from every JvColorFamily, so any
        // transition touching it threw NoSuchElementException. All 12x12 pairs
        // must resolve to a valid path.
        for (from in JvReactorStateColor.entries) {
            for (to in JvReactorStateColor.entries) {
                val path = AnimationEngine.resolveTransition(from, to)
                assertTrue(
                    "$from→$to must resolve to a valid path",
                    path is AnimationEngine.AnimationPath.Direct || path is AnimationEngine.AnimationPath.ThroughNeutral
                )
            }
        }
    }

    @Test
    fun `coding is building-family so coding-building is direct`() {
        val path = AnimationEngine.resolveTransition(JvReactorStateColor.CODING, JvReactorStateColor.BUILDING)
        assertTrue("CODING→BUILDING should be direct (same family)", path is AnimationEngine.AnimationPath.Direct)
    }

    @Test
    fun `event pulse Hz are correct per state`() {
        assertEquals(1, AnimationEngine.stateEventPulseHz(JvReactorStateColor.WARNING))
        assertEquals(2, AnimationEngine.stateEventPulseHz(JvReactorStateColor.CRITICAL))
        assertEquals(0, AnimationEngine.stateEventPulseHz(JvReactorStateColor.IDLE))
        assertEquals(0, AnimationEngine.stateEventPulseHz(JvReactorStateColor.LISTENING))
    }

    @Test
    fun `ring speed factors match spec`() {
        assertEquals(1.0f, AnimationEngine.stateRingSpeedFactor(JvReactorStateColor.IDLE), 0.01f)
        assertEquals(1.4f, AnimationEngine.stateRingSpeedFactor(JvReactorStateColor.THINKING), 0.01f)
        assertEquals(0f, AnimationEngine.stateRingSpeedFactor(JvReactorStateColor.OFFLINE), 0.01f)
    }
}
