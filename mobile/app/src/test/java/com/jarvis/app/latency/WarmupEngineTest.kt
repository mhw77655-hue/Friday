package com.jarvis.app.latency

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Warm-up ping gating — the pure decision helper. The ping must never compete
 * with a real request (sending/retrying), never hammer a down server, and
 * never double-fire.
 */
class WarmupEngineTest {

    @Test
    fun `pings only idle or ok statuses`() {
        assertTrue(WarmupEngine.canPing("idle", backedOff = false, pingInFlight = false))
        assertTrue(WarmupEngine.canPing("ok (local)", backedOff = false, pingInFlight = false))
    }

    @Test
    fun `never pings while a real request is in flight`() {
        assertFalse(WarmupEngine.canPing("sending (local)", backedOff = false, pingInFlight = false))
        assertFalse(WarmupEngine.canPing("retrying local (attempt 2)", backedOff = false, pingInFlight = false))
    }

    @Test
    fun `never pings while backed off or already pinging`() {
        assertFalse(WarmupEngine.canPing("idle", backedOff = true, pingInFlight = false))
        assertFalse(WarmupEngine.canPing("idle", backedOff = false, pingInFlight = true))
    }

    @Test
    fun `never pings a failed server`() {
        assertFalse(WarmupEngine.canPing("local brain unreachable after 3 attempts", backedOff = false, pingInFlight = false))
    }
}
