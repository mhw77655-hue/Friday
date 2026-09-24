package com.jarvis.app.threads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THREAD-OBJECTS (Gate 3a, priority 2) — store/subsystem-level unit tests.
 *
 * AC1: `segment_message()` splits one message into its separate thoughts; each
 *      becomes a [ThreadObjects.OpenThread] with its own completeness score.
 * AC2: `thread_acknowledge()` produces exactly one clause per open thread —
 *      acknowledgment, never a checklist (no numbering, one clause each).
 * AC3: an unfinished thought is stored as TRAILING_OFF, never auto-completed or
 *      invented across later turns.
 * AC4: `resurface_policy()` is gated by the decay clock AND an idle/related
 *      turn — the clock alone never resurfaced a thread (not a fixed timer).
 * AC5: `close_detect()` notices when the user resolves a thread themselves.
 * AC7 (unit half): a disabled tracker is inert — every operation the
 *      three_thoughts_test depends on is absent.
 *
 * The fixtures carry all three languages the story demands: English
 * ("Green tea smells pleasant"), Egyptian Arabic in Arabic script
 * ("بابا راح يشترى نضارة"), and Franco-Arabic ("ana kent bas faaker fel safar …").
 */
class ThreadObjectsTest {

    /** One message with three unrelated thoughts, one half-finished — mixed English /
     *  Egyptian Arabic / Franco-Arabic (AC6 fixture family). */
    val THREE_THOUGHTS = "Green tea smells pleasant. بابا راح يشترى نضارة. ana kent bas faaker fel safar ..."

    private fun lenientTracker(now: () -> Long = { 0L }): ThreadTracker = ThreadTracker(
        minimumHoldTurns = 1,
        maximumOpenTurns = 10,
        minimumHoldMs = 0,
        maximumOpenMs = 10_000_000L,
        now = now
    )

    // ── AC1 ────────────────────────────────────────────────────────────────

    @Test
    fun `segment_message splits one message into its separate thoughts`() {
        val segments = ThreadObjects.segment_message(THREE_THOUGHTS)

        assertEquals("three distinct thoughts from one message", 3, segments.size)
        assertEquals("thought 1", "Green tea smells pleasant.", segments[0])
        assertEquals("thought 2 (Egyptian Arabic)", "بابا راح يشترى نضارة.", segments[1])
        assertEquals("thought 3 (Franco-Arabic, half-finished)", "ana kent bas faaker fel safar …", segments[2])
    }

    // ── AC3 (marking half) ────────────────────────────────────────────────

    @Test
    fun `an unfinished thought is marked trailing off not finished`() {
        val segments = ThreadObjects.segment_message(THREE_THOUGHTS)
        val completeness = segments.map { ThreadObjects.completenessOf(it) }

        assertEquals(ThreadObjects.Completeness.FINISHED, completeness[0])
        assertEquals(ThreadObjects.Completeness.FINISHED, completeness[1])
        assertEquals(
            "the half-finished Franco-Arabic thought must be marked incomplete",
            ThreadObjects.Completeness.TRAILING_OFF, completeness[2]
        )
    }

    // ── AC1 (thread objects half) ─────────────────────────────────────────

    @Test
    fun `each thought becomes a thread with its own completeness and the main anchor`() {
        val tracker = lenientTracker()
        val created = tracker.ingestTurn(1L, THREE_THOUGHTS)

        assertEquals(3, created.size)
        assertEquals(
            listOf(
                ThreadObjects.Completeness.FINISHED,
                ThreadObjects.Completeness.FINISHED,
                ThreadObjects.Completeness.TRAILING_OFF
            ),
            created.map { it.completeness }
        )
        assertTrue("every thread links back to the turn it came from", created.all { it.createdAtTurn == 1L })
        assertTrue(created.all { it.id.startsWith("thread-1-") })

        // The main (first) thought is the main_anchor that survives every tangent.
        val mainAnchor = tracker.mainAnchorId(1L)
        assertEquals("thread-1-0", mainAnchor)
        assertTrue("the main thread is its own anchor", created[0].mainAnchorId == mainAnchor)
        assertTrue(
            "every tangent points its main_anchor at the main thread",
            created.drop(1).all { it.mainAnchorId == mainAnchor }
        )
    }

    // ── AC2 ───────────────────────────────────────────────────────────────

    @Test
    fun `thread_acknowledge gives exactly one clause per open thread without a checklist`() {
        val tracker = lenientTracker()
        tracker.ingestTurn(1L, THREE_THOUGHTS)

        val clauses = tracker.thread_acknowledge(1L)
        assertEquals("one clause per thread", 3, clauses.size)
        assertTrue("each clause acknowledges its thread's content",
            clauses[0].contains("Green tea smells pleasant"))
        assertTrue("each clause acknowledges its thread's content",
            clauses[1].contains("بابا راح يشترى نضارة"))
        assertTrue("the open thought is acknowledged as open, held, not completed",
            clauses[2].contains("ana kent bas faaker fel safar"))
        assertTrue("the open thought is marked open/held, never auto-completed",
            clauses[2].contains("held open"))
        assertFalse("no numbering — acknowledgment must not be a checklist",
            clauses.any { Regex("^\\d+[.)]").containsMatchIn(it) })
        assertFalse("no two clauses in one line — one clause each",
            clauses.any { it.contains("\n") })

        assertEquals("only threads born on the asked turn are acknowledged",
            emptyList<String>(), tracker.thread_acknowledge(2L))
    }

    // ── AC3 (never-completed half) ────────────────────────────────────────

    @Test
    fun `an unfinished thought stays incomplete and unmodified across later turns`() {
        val tracker = lenientTracker()
        tracker.ingestTurn(1L, THREE_THOUGHTS)

        tracker.ingestTurn(2L, "The sky looks blue today")

        val open = tracker.thread("thread-1-2")
        assertNotNull(open)
        assertEquals(
            "the stored half-finished thought is never auto-completed",
            ThreadObjects.Completeness.TRAILING_OFF, open!!.completeness
        )
        assertEquals(
            "its content is the user's raw words — nothing invented",
            "ana kent bas faaker fel safar …", open.content
        )
    }

    // ── AC4 ───────────────────────────────────────────────────────────────

    @Test
    fun `resurface_policy does not fire from the decay clock alone - a related or idle turn is required`() {
        val tracker = lenientTracker()
        tracker.ingestTurn(1L, THREE_THOUGHTS)

        // Same turn cadence, but the turn is neither idle nor related → nothing resurfaces,
        // even though the decay window is fully open. This is "not a fixed timer alone".
        val unrelatedTurn = tracker.resurface_policy(2L, "The sky looks blue today")
        assertTrue("clock alone never resurfaces a thread", unrelatedTurn.isEmpty())
    }

    @Test
    fun `resurface_policy brings an open thread back on a related topic`() {
        val tracker = lenientTracker()
        tracker.ingestTurn(1L, THREE_THOUGHTS)

        val resurfaced = tracker.resurface_policy(2L, "El safar gamed awy")
        assertEquals(1, resurfaced.size)
        assertEquals("thread-1-2", resurfaced.single().id)
    }

    @Test
    fun `resurface_policy brings an open thread back on an idle moment`() {
        val tracker = lenientTracker()
        tracker.ingestTurn(1L, THREE_THOUGHTS)

        val resurfaced = tracker.resurface_policy(2L, "Ok")
        assertEquals(1, resurfaced.size)
        assertEquals("thread-1-2", resurfaced.single().id)
    }

    @Test
    fun `resurface_policy is gated by the decay clock - too fresh and too stale never resurface`() {
        // Freshness gate: minimumHoldTurns = 2 → the bake window is not open on turn 2.
        val fresh = ThreadTracker(
            minimumHoldTurns = 2, maximumOpenTurns = 10,
            minimumHoldMs = 0, maximumOpenMs = 10_000_000L, now = { 0L }
        )
        fresh.ingestTurn(1L, THREE_THOUGHTS)
        assertTrue("too fresh must not resurface",
            fresh.resurface_policy(2L, "El safar gamed awy").isEmpty())
        assertEquals("baked open by turn 3", "thread-1-2",
            fresh.resurface_policy(3L, "El safar gamed awy").single().id)

        // Staleness gate: maximumOpenTurns = 2 → by turn 4 the resurface window is closed.
        val stale = ThreadTracker(
            minimumHoldTurns = 1, maximumOpenTurns = 2,
            minimumHoldMs = 0, maximumOpenMs = 10_000_000L, now = { 0L }
        )
        stale.ingestTurn(1L, THREE_THOUGHTS)
        assertEquals(1, stale.resurface_policy(3L, "El safar gamed awy").size)
        assertTrue("stale thread must stop resurfacing",
            stale.resurface_policy(4L, "El safar gamed awy").isEmpty())
    }

    @Test
    fun `resurface_policy is gated by the wall clock decay`() {
        val clock = arrayOf(1_000L)
        val tracker = ThreadTracker(
            minimumHoldTurns = 1, maximumOpenTurns = 10,
            minimumHoldMs = 500L, maximumOpenMs = 10_000L,
            now = { clock[0] }
        )
        tracker.ingestTurn(1L, THREE_THOUGHTS) // createdAtMs = 1_000

        // Elapsed 0ms (a "fixed timer" would still fire on the elapsed-turn count) —
        // the wall-clock decay gate holds until minimumHoldMs has truly passed.
        assertTrue("wall-clock decay gate holds before minimumHoldMs",
            tracker.resurface_policy(2L, "El safar gamed awy").isEmpty())

        clock[0] = 1_600L // 600ms elapsed ≥ 500ms → window open
        assertEquals("thread-1-2", tracker.resurface_policy(2L, "El safar gamed awy").single().id)

        clock[0] = 12_000L // past maximumOpenMs → stale on the wall clock too
        assertTrue("thread stops resurfacing after the wall-clock staleness",
            tracker.resurface_policy(2L, "El safar gamed awy").isEmpty())
    }

    // ── AC5 ───────────────────────────────────────────────────────────────

    @Test
    fun `close_detect closes a thread the user resolves themselves`() {
        val tracker = lenientTracker()
        tracker.ingestTurn(1L, THREE_THOUGHTS)

        val closed = tracker.close_detect(2L, "Zabatna el safar")
        assertEquals(listOf("thread-1-2"), closed)
        val resolved = tracker.thread("thread-1-2")!!
        assertTrue("the resolved thread is closed", resolved.closed)
        assertEquals(2L, resolved.resolvedAtTurn)
        assertEquals("only two threads remain open", 2, tracker.openThreads().size)
        assertTrue("a closed thread never resurfaces",
            tracker.resurface_policy(3L, "El safar gamed awy").isEmpty())
    }

    @Test
    fun `close_detect does not close a thread on mere mention without a resolution marker`() {
        val tracker = lenientTracker()
        tracker.ingestTurn(1L, THREE_THOUGHTS)

        assertTrue("related talk without a resolution marker must NOT close the thread",
            tracker.close_detect(2L, "El safar gamed awy").isEmpty())
        assertFalse("thread stays open after mere related mention",
            tracker.thread("thread-1-2")!!.closed)
    }

    // ── AC7 (unit half) ───────────────────────────────────────────────────

    @Test
    fun `disabled tracker is inert - the three thought outcomes are absent`() {
        val tracker = lenientTracker().apply { setEnabled(false) }

        assertTrue("no threads born while disabled",
            tracker.ingestTurn(1L, THREE_THOUGHTS).isEmpty())
        assertTrue("zero open threads while disabled", tracker.openThreads().isEmpty())
        assertTrue("no acknowledgment clauses while disabled",
            tracker.thread_acknowledge(1L).isEmpty())
        assertTrue("no resurfacing while disabled",
            tracker.resurface_policy(2L, "El safar gamed awy").isEmpty())
        assertTrue("no closing while disabled",
            tracker.close_detect(2L, "Zabatna el safar").isEmpty())

        // Re-enabling restores the behavior — proves the OFF state was a real switch.
        tracker.setEnabled(true)
        assertEquals(3, tracker.ingestTurn(3L, THREE_THOUGHTS).size)
    }

    @Test
    fun `threads born without input are none`() {
        val tracker = lenientTracker()
        assertTrue(tracker.ingestTurn(1L, "").isEmpty())
        assertTrue(tracker.ingestTurn(1L, "   ").isEmpty())
    }
}