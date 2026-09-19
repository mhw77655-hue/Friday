package com.jarvis.app.emotion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Tier-1 text/semantic emotion reader
 * (EMOTIONAL-INTELLIGENCE-FUSION-LAYER-TIER-1).
 *
 * The reader is deterministic and offline: every assertion here must hold
 * for a pure function of the turn text, with no model and no network.
 */
class FusionLayerTier1Test {

    private val tier = FusionLayerTier1()

    private fun assertInRange(value: Double, lo: Double, hi: Double, tag: String) {
        assertTrue("$tag ($value) must be within [$lo, $hi]", value >= lo && value <= hi)
    }

    @Test
    fun `blank and garbled text yields the empty-neutral hypothesis`(): Unit {
        val blank = tier.estimate("")
        assertEquals(EmotionHypothesis.neutral(), blank)
        val garbage = tier.estimate("asdkjasd kjalkjda x qqq zzz")
        assertEquals(EmotionHypothesis.neutral(), garbage)
        assertTrue(garbage.evidence.isEmpty())
        assertEquals("neutral", garbage.likelyState)
        assertEquals(0.0, garbage.confidence, 1e-9)
    }

    @Test
    fun `frustrated turn reads negative valence high tension and concrete evidence`(): Unit {
        val h = tier.estimate("I'm so frustrated, this keeps crashing and still not working")

        assertTrue("valence must be negative", h.valence < 0.0)
        assertTrue("tension must be elevated", h.tension > 0.2)
        assertInRange(h.arousal, 0.0, 1.0, "arousal")
        assertTrue("confidence must be anchored, not zero", h.confidence > 0.3)
        assertTrue("confidence must be honest, at most 1", h.confidence <= 1.0)
        assertEquals("frustrated", h.likelyState)
        assertTrue("evidence must be non-empty", h.evidence.isNotEmpty())
        assertTrue(
            "evidence must list the matched signal",
            h.evidence.any { it.contains("frustrat") }
        )
        assertEquals("escalating", h.temporalTrend)
    }

    @Test
    fun `pleasant turn reads positive valence low tension`(): Unit {
        val h = tier.estimate("I'm really happy and excited, thank you!")

        assertTrue("valence must be positive", h.valence > 0.0)
        assertTrue("arousal must be positive with exclamation", h.arousal > 0.0)
        assertTrue("tension must be low/suppressed", h.tension <= 0.05)
        assertEquals("pleasant", h.likelyState)
        assertTrue("confidence must be anchored", h.confidence > 0.3)
        assertTrue(h.evidence.any { it.contains("thank") })
    }

    @Test
    fun `anxious turn reads as anxious with high tension and low dominance`(): Unit {
        val h = tier.estimate("I am worried and really stressed about this")

        assertEquals("anxious", h.likelyState)
        assertInRange(h.tension, 0.2, 1.0, "tension")
        assertTrue("valence must be negative", h.valence < 0.0)
        assertTrue("dominance must be low", h.dominance < 0.5)
        assertTrue(h.evidence.any { it.contains("worried") })
    }

    @Test
    fun `information-seeking turn reads as curious`(): Unit {
        val h = tier.estimate("Can you explain how this works and why it does that?")

        // "can you" (polite) and "explain"/"how does" (curious) both fire; the
        // curious bloc must win as the stronger signal.
        assertEquals("curious", h.likelyState)
        assertTrue("alternatives must carry the runner-up states", h.alternatives.isNotEmpty())
        assertTrue(h.alternatives.contains("polite"))
    }

    @Test
    fun `negated pleasant cue flips the affective polarity without committing to a state`(): Unit {
        val h = tier.estimate("I am not happy about this at all")

        // "not happy" negates the pleasant cue: the reading cannot be pleasant,
        // but the reader stays honest (no asserted positive label).
        assertNotEquals("pleasant", h.likelyState)
        assertTrue("negated pleasant cue must read as unpleasant", h.valence < 0.0)
        assertTrue("evidence must record the signal", h.evidence.isNotEmpty())
    }

    @Test
    fun `uncertainty cues discount the confidence`(): Unit {
        val definite = tier.estimate("I am frustrated, this is broken")
        val hedged = tier.estimate("I am maybe frustrated, this is perhaps broken")

        assertTrue(
            "hedged reading must be less confident than the definite one",
            hedged.confidence < definite.confidence
        )
        assertTrue(
            "hedged reading must record the confidence discount",
            hedged.evidence.any { it.contains("discount") }
        )
    }

    @Test
    fun `resolving temporal cue reads as resolving instead of steady`(): Unit {
        val h = tier.estimate("finally, the thing got fixed and it is working now")

        assertEquals("resolving", h.temporalTrend)
        // The pleasant-ish resolution may still carry positive affect. The
        // important contract: the trend field is derived from the turn text.
        assertTrue(h.confidence > 0.0)
    }

    @Test
    fun `estimate is a deterministic pure function of the text`(): Unit {
        val text = "this keeps crashing and it still fails, please fix it now!"
        assertEquals(tier.estimate(text), tier.estimate(text))
    }

    @Test
    fun `produced hypotheses always honor the field contracts`(): Unit {
        val samples = listOf(
            "",
            "hello",
            "i love this so much!",
            "how does the engine work",
            "please do it immediately",
            "i absolutely know the answer",
            "not sure if it works, maybe try later",
            "why why why is this happening again",
            "i am terrified and panicking"
        )
        for (sample in samples) {
            val h = tier.estimate(sample)
            assertInRange(h.valence, -1.0, 1.0, "valence")
            assertInRange(h.arousal, 0.0, 1.0, "arousal")
            assertInRange(h.dominance, 0.0, 1.0, "dominance")
            assertInRange(h.tension, 0.0, 1.0, "tension")
            assertInRange(h.confidence, 0.0, 1.0, "confidence")
            assertTrue(h.likelyState.isNotBlank())
            assertTrue("likelyState must come from the known state space or neutral",
                h.likelyState in EmotionHypothesis.STATE_SPACE + "neutral")
            assertTrue(
                "alternatives must not repeat the likely state",
                h.likelyState !in h.alternatives
            )
            assertFalse("alternatives must not be duplicate-heavy", h.alternatives.size > 2)
            assertTrue(h.temporalTrend in listOf("escalating", "resolving", "steady"))
        }
    }
}