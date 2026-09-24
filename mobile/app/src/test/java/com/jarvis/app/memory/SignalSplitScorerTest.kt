package com.jarvis.app.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Acceptance tests for the SIGNAL-SPLIT story (don't let a single
 * prediction-error scalar drive encoding strength).
 *
 * The bug this fixes: a repeated, explicitly-stated boundary is unsurprising
 * by definition, so a model that scores importance as "how surprising is
 * this" would DRIVE ITS WEIGHT DOWN on every mention. The fix: the six
 * signals (relevance/importance/uncertainty/novelty/consent/cost) are each
 * computed and stored as their OWN field, never pre-collapsed, and combined
 * only later (and only if a baseline comparison proves the combination wins).
 *
 * Fixture (AC3): a boundary is stated 5 times in the graph store
 * (5 `addFact` calls -> a 5-node supersession chain). Its 5th mention must
 * keep a top-tier importance score while its novelty decays.
 */
@RunWith(JUnit4::class)
class SignalSplitScorerTest {

    private val provider = TestEmbeddingProvider(dimension = 256)
    private val scorer = SignalSplitScorer(embeddingProvider = provider)

    /** A boundary the user has stated N times total; returns the Nth mention + N. */
    private fun statedBoundary(n: Int): Pair<MemoryNode, Int> {
        val store = FakeMemoryGraphStore()
        repeat(n) {
            store.addFact(
                subject = "boundary",
                predicate = "stated",
                `object` = "never log raw chat text",
                source = "user"
            )
        }
        val history = store.getHistory("boundary", "stated")
        assertEquals("all $n statements of the boundary must be preserved", n, history.size)
        return history.last() to history.size
    }

    // ── AC2: six signals stored as their own fields, never pre-collapsed ──

    @Test
    fun `computeProfile exposes exactly the six independent signals`() {
        val (node, n) = statedBoundary(3)
        val profile = scorer.computeProfile(node, "please never log chat text", repetitionCount = n)

        // All six signals must be individually present and in legal range.
        assertTrue("relevance in [0,1]: ${profile.relevance}", profile.relevance in 0f..1f)
        assertTrue("importance in [0,1]: ${profile.importance}", profile.importance in 0f..1f)
        assertTrue("uncertainty in [0,1]: ${profile.uncertainty}", profile.uncertainty in 0f..1f)
        assertTrue("novelty in [0,1]: ${profile.novelty}", profile.novelty in 0f..1f)
        assertTrue("consent in [0,1]: ${profile.consent}", profile.consent in 0f..1f)
        assertTrue("cost in [0,1]: ${profile.cost}", profile.cost in 0f..1f)
    }

    @Test
    fun `withSignals stores each signal as its own field on the memory record and survives store round-trip`() {
        val (node, n) = statedBoundary(5)
        val profile = scorer.computeProfile(node, "please never log chat text", repetitionCount = n)

        val stored: MemoryNode = node.withSignals(profile)
        // Each field is independently readable on the record — not collapsed.
        assertEquals(profile.relevance, stored.relevance!!, 0f)
        assertEquals(profile.importance, stored.importance!!, 0f)
        assertEquals(profile.uncertainty, stored.uncertainty!!, 0f)
        assertEquals(profile.novelty, stored.novelty!!, 0f)
        assertEquals(profile.consent, stored.consent!!, 0f)
        assertEquals(profile.cost, stored.cost!!, 0f)

        // Storing the record keeps every field distinct through a store round-trip.
        val store = FakeMemoryGraphStore()
        store.addFakeNode(stored)
        val back = store.query("boundary", "stated")
        assertEquals(1, back.size)
        val node2 = back.single()
        assertEquals(profile.relevance, node2.relevance!!, 0f)
        assertEquals(profile.importance, node2.importance!!, 0f)
        assertEquals(profile.novelty, node2.novelty!!, 0f)
        assertTrue("importance must differ from novelty (not pre-collapsed)", node2.importance != node2.novelty)
    }

    // ── AC1: importance scored separately from novelty ─────────────────────

    @Test
    fun `explicit repeated instruction scores high importance regardless of mention count`() {
        val (first, _) = statedBoundary(1)
        val (fifth, n5) = statedBoundary(5)

        val profile1 = scorer.computeProfile(first, "please never log chat text", repetitionCount = 1)
        val profile5 = scorer.computeProfile(fifth, "please never log chat text", repetitionCount = n5)

        // The 5th mention must not lose weight versus the 1st.
        assertTrue(
            "importance on 5th mention (${profile5.importance}) must not decay below the 1st (${profile1.importance})",
            profile5.importance >= profile1.importance
        )
        // Novelty, the surprisal axis, decays along the same repetition curve.
        assertTrue(
            "novelty on 5th mention (${profile5.novelty}) must decay below the 1st (${profile1.novelty})",
            profile5.novelty < profile1.novelty
        )
        // Explicitly stated -> consent signal is at maximum.
        assertEquals(1f, profile5.consent, 0f)
    }

    // ── AC3: the fixture — 5th mention of a boundary keeps top-tier importance

    @Test
    fun `boundary stated 5 times retains top-tier importance on the 5th mention`() {
        val (node, n) = statedBoundary(5)
        val profile = scorer.computeProfile(node, "please never log chat text", repetitionCount = n)

        assertTrue(
            "5th mention importance ${profile.importance} must stay >= $TOP_TIER_IMPORTANCE, not decay",
            profile.importance >= TOP_TIER_IMPORTANCE
        )
        // While importance stays top-tier, novelty (surprise) is exhausted.
        assertTrue(
            "5th mention novelty ${profile.novelty} must be low",
            profile.novelty < 0.5f
        )

        // Discriminating fixture: the 1st mention is NOT yet top-tier, so the
        // 5th-mention result is not vacuous.
        val (first, _) = statedBoundary(1)
        val profile1 = scorer.computeProfile(first, "please never log chat text", repetitionCount = 1)
        assertTrue(
            "1st mention importance ${profile1.importance} must be below top tier so AC3 is non-vacuous",
            profile1.importance < TOP_TIER_IMPORTANCE
        )
        assertTrue(
            "importance (${profile.importance}) must clear novelty (${profile.novelty}) on the 5th mention",
            profile.importance > profile.novelty
        )
    }

    // ── AC4: no combined single score is computed or used ──────────────────

    @Test
    fun `importance and novelty are decoupled - a single scalar cannot express the 5x-boundary`() {
        val (first, _) = statedBoundary(1)
        val (fifth, n5) = statedBoundary(5)

        val profile1 = scorer.computeProfile(first, "", repetitionCount = 1)
        val profile5 = scorer.computeProfile(fifth, "", repetitionCount = n5)

        // Two axes that move in OPPOSITE directions with repetition:
        // importance rises, novelty falls — impossible for one collapsed scalar.
        assertTrue(
            "importance must rise (${profile1.importance} -> ${profile5.importance})",
            profile5.importance > profile1.importance
        )
        assertTrue(
            "novelty must fall (${profile1.novelty} -> ${profile5.novelty})",
            profile5.novelty < profile1.novelty
        )
    }

    // ── AC5: negative control — naive single prediction-error scalar fails ─

    @Test
    fun `reverting to a naive single prediction-error scalar fails AC3's fixture`() {
        val (node, n) = statedBoundary(5)
        val naive = SinglePredictionErrorScorer().score(node, repetitionCount = n)

        // The naive model equates importance with prediction error (surprisal);
        // an unsurprising 5th mention collapses to a low, non-top-tier score.
        assertTrue(
            "naive scalar ${naive} must drop below $TOP_TIER_IMPORTANCE on the 5th mention",
            naive < TOP_TIER_IMPORTANCE
        )
        // Same fixture, same node, same repetition evidence — the split scorer
        // keeps the boundary top-tier, proving the negative control is about
        // the MODEL (single scalar) and not the fixture.
        val split = scorer.computeProfile(node, "", repetitionCount = n).importance
        assertTrue(
            "SignalSplitScorer importance ${split} must stay >= $TOP_TIER_IMPORTANCE on the same fixture",
            split >= TOP_TIER_IMPORTANCE
        )
        assertTrue("split importance $split must clear naive scalar $naive", split > naive)
    }

    companion object {
        /** "Top-tier" importance threshold used by the AC3 fixture. */
        private const val TOP_TIER_IMPORTANCE = 0.8f
    }
}

/**
 * The collapsed model SIGNAL-SPLIT rejects (test-local negative control):
 * importance == prediction error == surprisal == 1/(1+repetitions). Under it a
 * boundary stated 5 times is maximally unsurprising and loses nearly all
 * weight on the 5th mention — exactly the bug AC3's fixture catches.
 */
private class SinglePredictionErrorScorer {
    fun score(node: MemoryNode, repetitionCount: Int): Float =
        (1f / (1f + repetitionCount)).coerceIn(0f, 1f)
}