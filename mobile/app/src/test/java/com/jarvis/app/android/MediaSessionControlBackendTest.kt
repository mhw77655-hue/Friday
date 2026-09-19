package com.jarvis.app.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionControlBackendTest {

    /** Deterministic in-memory media-session port (mirrors PlaybackState ints). */
    private class FakeMediaSessionPort(
        initial: List<MediaSessionControlPort.Session> = emptyList()
    ) : MediaSessionControlPort {
        private val entries = initial.toMutableList()

        fun replace(sessions: List<MediaSessionControlPort.Session>) {
            entries.clear()
            entries.addAll(sessions)
        }

        override fun activeSessions(): List<MediaSessionControlPort.Session> = entries.toList()

        override fun dispatch(sessionKey: String, command: MediaTransportCommand): Boolean {
            val index = entries.indexOfFirst { it.key == sessionKey }
            if (index < 0) return false
            val current = entries[index]
            val next = when (command) {
                MediaTransportCommand.PLAY -> current.copy(state = 3, positionMs = current.positionMs + 500)
                MediaTransportCommand.PAUSE -> current.copy(state = 2)
                MediaTransportCommand.NEXT_TRACK,
                MediaTransportCommand.PREV_TRACK -> current.copy(positionMs = 0L)
            }
            entries[index] = next
            return true
        }
    }

    private fun action(type: ActionType) = ControlAction(
        id = "a1",
        actionType = type,
        riskTier = RiskTier.LOW
    )

    private fun playing(key: String, positionMs: Long = 15_000L, packageName: String = "com.spotify.music") =
        MediaSessionControlPort.Session(key, packageName, state = 3, positionMs = positionMs)

    private fun paused(key: String, packageName: String = "com.spotify.music") =
        MediaSessionControlPort.Session(key, packageName, state = 2, positionMs = 10_000L)

    @Test
    fun `available only when the platform exposes active media sessions`() {
        val port = FakeMediaSessionPort()
        val backend = MediaSessionControlBackend(port)
        assertFalse("no sessions -> unavailable", backend.available())

        port.replace(listOf(playing("spotify")))
        assertTrue("an active session makes the rung available", backend.available())
    }

    @Test
    fun `play dispatch reports verified evidence only when playback state confirms playing`() {
        val backend = MediaSessionControlBackend(FakeMediaSessionPort(listOf(paused("spotify"))))
        val evidence = backend.execute(action(ActionType.PLAY))

        assertEquals(VerificationStage.VERIFIED, evidence.stage)
        assertTrue("evidence names the backend rung", evidence.backendId == "media-session")
        assertTrue("evidence carries a real confirmation detail", evidence.detail.contains("PLAY"))
    }

    @Test
    fun `pause dispatch reports verified evidence on paused state`() {
        val backend = MediaSessionControlBackend(FakeMediaSessionPort(listOf(playing("spotify"))))
        val evidence = backend.execute(action(ActionType.PAUSE))

        assertEquals(VerificationStage.VERIFIED, evidence.stage)
        assertTrue(evidence.detail.contains("PAUSE"))
    }

    @Test
    fun `next and previous track verify through position reset`() {
        val nextBackend = MediaSessionControlBackend(FakeMediaSessionPort(listOf(playing("spotify", positionMs = 30_000L))))
        val nextEvidence = nextBackend.execute(action(ActionType.NEXT_TRACK))
        assertEquals(VerificationStage.VERIFIED, nextEvidence.stage)
        assertTrue(nextEvidence.detail.contains("NEXT_TRACK"))

        val prevBackend = MediaSessionControlBackend(FakeMediaSessionPort(listOf(playing("spotify", positionMs = 30_000L))))
        val prevEvidence = prevBackend.execute(action(ActionType.PREV_TRACK))
        assertEquals(VerificationStage.VERIFIED, prevEvidence.stage)
        assertTrue(prevEvidence.detail.contains("PREV_TRACK"))
    }

    @Test
    fun `unsupported action is not attempted`() {
        val backend = MediaSessionControlBackend(FakeMediaSessionPort(listOf(playing("spotify"))))
        val evidence = backend.execute(action(ActionType.SCREENSCAPE_SUMMARY))

        assertEquals(VerificationStage.NOT_ATTEMPTED, evidence.stage)
        assertTrue(evidence.detail.contains("unsupported action"))
    }

    @Test
    fun `no active session yields no verification`() {
        val backend = MediaSessionControlBackend(FakeMediaSessionPort())
        val evidence = backend.execute(action(ActionType.PLAY))

        assertEquals(VerificationStage.NOT_ATTEMPTED, evidence.stage)
        assertTrue(evidence.detail.contains("no active media session"))
    }

    @Test
    fun `declined dispatch is not verified`() {
        val declining = object : MediaSessionControlPort {
            override fun activeSessions(): List<MediaSessionControlPort.Session> =
                listOf(playing("spotify"))

            override fun dispatch(sessionKey: String, command: MediaTransportCommand): Boolean = false
        }
        val evidence = MediaSessionControlBackend(declining).execute(action(ActionType.PLAY))

        assertEquals(VerificationStage.NOT_ATTEMPTED, evidence.stage)
        assertTrue(evidence.detail.contains("declined"))
    }

    @Test
    fun `dispatch with no observable state change is never fire-and-assume`() {
        // Session is already at position 0 playing; a NEXT_TRACK dispatch that the
        // port reflects as no change must NOT be reported verified.
        val backend = MediaSessionControlBackend(
            FakeMediaSessionPort(listOf(playing("spotify", positionMs = 0L)))
        )
        val evidence = backend.execute(action(ActionType.NEXT_TRACK))

        assertEquals(VerificationStage.NOT_ATTEMPTED, evidence.stage)
        assertTrue(evidence.detail.contains("no observable playback-state change"))
    }
}