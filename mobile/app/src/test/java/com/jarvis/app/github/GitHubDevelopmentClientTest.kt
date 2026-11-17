package com.jarvis.app.github

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDevelopmentClientTest {
    private val now = Instant.parse("2026-11-17T13:00:00Z")

    @Test
    fun `open proposal blocks first cycle without local history`() {
        assertFalse(shouldDispatchAutonomousCycle(
            hasOpenProposal = true,
            previousRequestAt = null,
            now = now
        ))
    }

    @Test
    fun `recent request remains inside cooldown`() {
        assertFalse(shouldDispatchAutonomousCycle(
            hasOpenProposal = false,
            previousRequestAt = now.minusSeconds(60 * 60),
            now = now
        ))
    }

    @Test
    fun `expired cooldown allows a new cycle`() {
        assertTrue(shouldDispatchAutonomousCycle(
            hasOpenProposal = false,
            previousRequestAt = now.minusSeconds(13 * 60 * 60),
            now = now
        ))
    }

    @Test
    fun `no history and no proposal allows first cycle`() {
        assertTrue(shouldDispatchAutonomousCycle(
            hasOpenProposal = false,
            previousRequestAt = null,
            now = now
        ))
    }
}
