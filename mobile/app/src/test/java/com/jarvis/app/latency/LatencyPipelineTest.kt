package com.jarvis.app.latency

import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.ui.viewmodel.Turn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Latency-layer pipeline — pure JVM, every effect injected so the phase
 * ladder, burst correlation, and fallback behavior are deterministic:
 * dispatch runs inline, timers are captured instead of fired, and the clock
 * is fixed at 0.
 */
class LatencyPipelineTest {

    private class Recorder {
        val spoken = mutableListOf<String>()
        val fullSpoken = mutableListOf<String>()
        val sent = mutableListOf<String>()
        val logged = mutableListOf<Pair<Boolean, String>>()
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        var submitted = 0
        var fastAck = 0
        var firstSegment = 0
        var utteranceDone = 0
        var completeCount = 0
        var status = "ok (local)"

        fun pipeline(): LatencyPipeline = LatencyPipeline(
            dispatch = { it() },
            scheduleDelayed = { ms, block -> scheduled += ms to block },
            ackSpeak = { spoken += it },
            fullSpeak = { fullSpoken += it },
            logObsidian = { fromJarvis, text -> logged += fromJarvis to text },
            warmPrime = {},
            reactorSubmitted = { submitted++ },
            reactorFastPathAck = { fastAck++ },
            reactorFirstSegment = { firstSegment++ },
            reactorUtteranceDone = { utteranceDone++ },
            bridgeSend = { sent += it },
            bridgeStatus = { status },
            beginExchange = { _, _ -> null },
            express = { reply, _ -> StyledResponse.Approved(reply) },
            completeExchange = { _, _, _, _, _ -> completeCount++ },
            sessionContext = { null },
            nowMs = { 0L }
        )
    }

    // ----------------------------------------------------------- fast path

    @Test
    fun `input runs the fast path then the slow path`() {
        val r = Recorder()
        val p = r.pipeline()

        p.onUserInput("hello")

        // Slow path ran inline, so the phase settles on THINKING.
        assertEquals(LatencyLayer.Phase.THINKING, p.phase.value)
        // Fast-path seams fired exactly once each.
        assertEquals(1, r.submitted)
        assertEquals(1, r.fastAck)
        // One short spoken ack (non-blank), and the brain got the text.
        assertEquals(1, r.spoken.size)
        assertTrue(r.spoken.single().isNotBlank())
        assertEquals(listOf("hello"), r.sent)
        // User turn is on screen immediately; Obsidian write is off-thread.
        assertEquals(listOf(Turn(fromJarvis = false, text = "hello")), p.turns.value)
        assertEquals(listOf(false to "hello"), r.logged)
    }

    @Test
    fun `burst sends only ack once within the ack gap`() {
        val r = Recorder()
        val p = r.pipeline()

        p.onUserInput("first")
        p.onUserInput("second")

        assertEquals(1, r.spoken.size) // fixed clock: second ack is inside the gap
        assertEquals(listOf("first", "second"), r.sent)
    }

    // ------------------------------------------------------------ completion

    @Test
    fun `reply completes the exchange end to end`() {
        val r = Recorder()
        val p = r.pipeline()

        p.onUserInput("hello")
        p.onReplyReady("hi there")

        assertEquals(LatencyLayer.Phase.IDLE, p.phase.value)
        assertEquals(listOf("hi there"), r.fullSpoken)
        assertEquals(1, r.firstSegment)
        assertEquals(1, r.utteranceDone)
        assertEquals(1, r.completeCount)
        assertEquals(
            listOf(Turn(fromJarvis = true, text = "hi there", verified = true)),
            p.turns.value.filter { it.fromJarvis }
        )
        assertTrue(r.logged.contains(true to "hi there"))
    }

    @Test
    fun `burst keeps reply-source correlation (queue head is the source)`() {
        val r = Recorder()
        val p = r.pipeline()

        p.onUserInput("first")
        p.onUserInput("second")
        p.onReplyReady("reply-1")
        p.onReplyReady("reply-2")

        assertEquals(
            listOf("reply-1", "reply-2"),
            p.turns.value.filter { it.fromJarvis }.map { it.text }
        )
        assertEquals(2, r.completeCount)
    }

    // ------------------------------------------------------ fallback ladder

    @Test
    fun `slow reasoning escalates to thinking-long, visually only`() {
        val r = Recorder()
        val p = r.pipeline()

        p.onUserInput("slow")
        assertEquals(LatencyLayer.Phase.THINKING, p.phase.value)

        val slowTimer = r.scheduled.first { it.first == LatencyPipeline.SLOW_THRESHOLD_MS }
        slowTimer.second()

        assertEquals(LatencyLayer.Phase.THINKING_LONG, p.phase.value)
        assertTrue(r.fullSpoken.isEmpty()) // no spoken nudge by default
    }

    @Test
    fun `escalation does not fire after the reply landed`() {
        val r = Recorder()
        val p = r.pipeline()

        p.onUserInput("q")
        p.onReplyReady("a")
        r.scheduled.forEach { (_, block) -> block() }

        assertEquals(LatencyLayer.Phase.IDLE, p.phase.value)
    }

    @Test
    fun `confirmed error emits one soft turn and drains pending exchanges`() {
        val r = Recorder()
        val p = r.pipeline()

        p.onUserInput("a")
        p.onUserInput("b")
        p.onBridgeStatus("local brain unreachable after 3 attempts")

        assertEquals(LatencyLayer.Phase.IDLE, p.phase.value)
        assertEquals(
            listOf(LatencyPipeline.SOFT_ERROR_TEXT),
            p.turns.value.filter { it.fromJarvis }.map { it.text }
        )

        // A second error status for the same exchange never double-emits.
        p.onBridgeStatus("local brain unreachable after 3 attempts")
        assertEquals(1, p.turns.value.filter { it.fromJarvis }.size)
    }

    // ------------------------------------------------------------- ack only

    @Test
    fun `ack-only records both turns, speaks, and never calls the brain`() {
        val r = Recorder()
        val p = r.pipeline()

        p.ackOnly("check the screen", "Let me check the screen.")

        assertEquals(
            listOf(
                Turn(fromJarvis = false, text = "check the screen"),
                Turn(fromJarvis = true, text = "Let me check the screen.", verified = false)
            ),
            p.turns.value
        )
        assertEquals(listOf("Let me check the screen."), r.spoken)
        assertEquals(emptyList<String>(), r.sent)
        assertTrue(r.completeCount == 0)

        r.scheduled.first { it.first == LatencyPipeline.ACK_ONLY_IDLE_MS }.second()
        assertEquals(LatencyLayer.Phase.IDLE, p.phase.value)
    }
}
