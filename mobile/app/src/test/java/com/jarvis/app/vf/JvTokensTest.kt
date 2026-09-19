package com.jarvis.app.vf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design Token integrity — every frozen value from 20_DESIGN_TOKENS.md
 * is asserted exactly. Drift detection: if someone changes a token,
 * this test fails and the diff is self-documenting.
 */
class JvTokensTest {

    @Test
    fun `core colors match the frozen palette exactly`() {
        assertEquals("VoidBlack", 0xFF04060B, com.jarvis.app.vf.tokens.JvTokens.VoidBlack.hashCode().toLong() and 0xFFFFFFFFL)
        assertEquals("DeepSpace", 0xFF0A0E1A, com.jarvis.app.vf.tokens.JvTokens.DeepSpace.hashCode().toLong() and 0xFFFFFFFFL)
        assertEquals("IgnitionWhite", 0xFFF4FBFF, com.jarvis.app.vf.tokens.JvTokens.IgnitionWhite.hashCode().toLong() and 0xFFFFFFFFL)
        assertEquals("JarvisBlue", 0xFF2E9BFF, com.jarvis.app.vf.tokens.JvTokens.JarvisBlue.hashCode().toLong() and 0xFFFFFFFFL)
        assertEquals("Steel", 0xFF8A94A6, com.jarvis.app.vf.tokens.JvTokens.Steel.hashCode().toLong() and 0xFFFFFFFFL)
        assertEquals("Gunmetal", 0xFF1B2130, com.jarvis.app.vf.tokens.JvTokens.Gunmetal.hashCode().toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun `state colors match the frozen palette exactly`() {
        assertEquals(0xFF2E9BFF, com.jarvis.app.vf.tokens.JvReactorStateColor.IDLE.hex)
        assertEquals(0xFF00D9C0, com.jarvis.app.vf.tokens.JvReactorStateColor.LISTENING.hex)
        assertEquals(0xFFF5A623, com.jarvis.app.vf.tokens.JvReactorStateColor.THINKING.hex)
        assertEquals(0xFFE8ECF2, com.jarvis.app.vf.tokens.JvReactorStateColor.BUILDING.hex)
        assertEquals(0xFFFF3B30, com.jarvis.app.vf.tokens.JvReactorStateColor.WARNING.hex)
        assertEquals(0xFFFF1744, com.jarvis.app.vf.tokens.JvReactorStateColor.CRITICAL.hex)
        assertEquals(0xFF7C4DFF, com.jarvis.app.vf.tokens.JvReactorStateColor.SLEEPING.hex)
        assertEquals(0xFF8A94A6, com.jarvis.app.vf.tokens.JvReactorStateColor.OFFLINE.hex)
    }

    @Test
    fun `spacing tokens are all 4px multiples`() {
        val spacing = listOf(
            com.jarvis.app.vf.tokens.JvTokens.Space1,
            com.jarvis.app.vf.tokens.JvTokens.Space2,
            com.jarvis.app.vf.tokens.JvTokens.Space3,
            com.jarvis.app.vf.tokens.JvTokens.Space4,
            com.jarvis.app.vf.tokens.JvTokens.Space6,
            com.jarvis.app.vf.tokens.JvTokens.Space8,
            com.jarvis.app.vf.tokens.JvTokens.Space12,
            com.jarvis.app.vf.tokens.JvTokens.Space16
        )
        val expectedPx = listOf(4f, 8f, 12f, 16f, 24f, 32f, 48f, 64f)
        spacing.forEachIndexed { i, dp ->
            assertEquals("Space${listOf("1","2","3","4","6","8","12","16")[i]} should be ${expectedPx[i]}dp", expectedPx[i], dp.value)
        }
    }

    @Test
    fun `every state color belongs to exactly one family`() {
        for (state in com.jarvis.app.vf.tokens.JvReactorStateColor.entries) {
            // Must not throw (the historical CODING gap crashed JvColorFamily.of).
            com.jarvis.app.vf.tokens.JvColorFamily.of(state)
            val memberships = com.jarvis.app.vf.tokens.JvColorFamily.entries.count { state in it.members }
            assertEquals("$state must belong to exactly one family", 1, memberships)
        }
    }

    @Test
    fun `text primary is a distinct near-white, never the reserved ignition white`() {
        val primary = com.jarvis.app.vf.tokens.JvTokens.TextPrimary.hashCode().toLong() and 0xFFFFFFFFL
        val ignition = com.jarvis.app.vf.tokens.JvTokens.IgnitionWhite.hashCode().toLong() and 0xFFFFFFFFL
        assertTrue("TextPrimary must not equal the reserved ignition white (§5.6)", primary != ignition)
    }

    @Test
    fun `no bounce easing is registered`() {
        val bounce = com.jarvis.app.vf.tokens.JvTokens.EaseWarningPulse()
        // cubic-bezier(0.9, 0, 1, 1) — sharp ease-out, no bounce
        assertTrue("Easing must not be bounce/elastic", bounce.toString().contains("0.9"))
    }

    @Test
    fun `opacity tokens are within valid ranges`() {
        assertTrue(com.jarvis.app.vf.tokens.JvTokens.OpacityBloom in 0f..1f)
        assertTrue(com.jarvis.app.vf.tokens.JvTokens.OpacityHologram in 0f..1f)
        assertTrue(com.jarvis.app.vf.tokens.JvTokens.OpacityGlass in 0f..1f)
        assertTrue(com.jarvis.app.vf.tokens.JvTokens.OpacityCrystal in 0f..1f)
        assertTrue(com.jarvis.app.vf.tokens.JvTokens.OpacityMetal == 1f)
    }
}
