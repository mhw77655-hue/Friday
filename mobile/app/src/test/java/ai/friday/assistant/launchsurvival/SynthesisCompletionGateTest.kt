package ai.friday.assistant.launchsurvival

import ai.friday.assistant.tts.CompletedAudioCache
import ai.friday.assistant.tts.SynthesisCompletionGate
import ai.friday.assistant.tts.SynthesisOutcome
import ai.friday.assistant.tts.SynthesisReservation
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthesisCompletionGateTest {

    private fun ticket(directory: File, cacheKey: String, id: String) =
        CompletedAudioCache(directory).ticket(cacheKey, id)

    @Test
    fun `queued success is not completion until onDone arrives`() {
        val directory = Files.createTempDirectory("tts-gate").toFile()
        try {
            val gate = SynthesisCompletionGate()
            val request = ticket(directory, "phrase_en", "tts_1")

            assertTrue(gate.reserve(request, shouldPlay = true, isFinal = true) is SynthesisReservation.Started)
            assertNull(gate.markAccepted("tts_1", accepted = true))
            assertTrue(gate.isPending("tts_1"))

            val outcome = gate.markCompleted("tts_1")
            assertTrue(outcome is SynthesisOutcome.Completed)
            val completed = outcome as SynthesisOutcome.Completed
            assertEquals("phrase_en", completed.ticket.cacheKey)
            assertTrue(completed.shouldPlay)
            assertTrue(completed.isFinal)
            assertFalse(gate.isPending("tts_1"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `completion callback may arrive before synthesizeToFile returns`() {
        val directory = Files.createTempDirectory("tts-order").toFile()
        try {
            val gate = SynthesisCompletionGate()
            val request = ticket(directory, "phrase_ar", "tts_2")

            gate.reserve(request, shouldPlay = true, isFinal = false)
            assertNull(gate.markCompleted("tts_2"))
            assertTrue(gate.isPending("tts_2"))

            val outcome = gate.markAccepted("tts_2", accepted = true)
            assertTrue(outcome is SynthesisOutcome.Completed)
            assertFalse(gate.isPending("tts_2"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `onError removes the partial request and produces a failure`() {
        val directory = Files.createTempDirectory("tts-error").toFile()
        try {
            val gate = SynthesisCompletionGate()
            val request = ticket(directory, "phrase_error", "tts_3")

            gate.reserve(request, shouldPlay = true, isFinal = true)
            gate.markAccepted("tts_3", accepted = true)
            val outcome = gate.markFailed("tts_3", "Android TTS onError")

            assertTrue(outcome is SynthesisOutcome.Failed)
            assertEquals("Android TTS onError", (outcome as SynthesisOutcome.Failed).reason)
            assertFalse(gate.isPending("tts_3"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `onStop removes the interrupted request and never reports it as completed`() {
        val directory = Files.createTempDirectory("tts-stop").toFile()
        try {
            val gate = SynthesisCompletionGate()
            val request = ticket(directory, "phrase_stop", "tts_4")

            gate.reserve(request, shouldPlay = false, isFinal = false)
            gate.markAccepted("tts_4", accepted = true)
            val outcome = gate.markFailed("tts_4", "stopped while synthesis was pending")

            assertTrue(outcome is SynthesisOutcome.Failed)
            assertFalse(gate.isPending("tts_4"))
            assertNull(gate.markCompleted("tts_4"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `a playback request joining a preload inherits playback intent even when synthesis fails`() {
        val directory = Files.createTempDirectory("tts-join-failure").toFile()
        try {
            val gate = SynthesisCompletionGate()
            val preload = ticket(directory, "shared_phrase", "preload_join")
            val playback = ticket(directory, "shared_phrase", "tts_join")

            gate.reserve(preload, shouldPlay = false, isFinal = false)
            assertTrue(
                gate.reserve(playback, shouldPlay = true, isFinal = true) is SynthesisReservation.Joined
            )

            val outcome = gate.markAccepted(
                "preload_join",
                accepted = false,
                failureReason = "synchronous synthesis rejection"
            )
            assertTrue(outcome is SynthesisOutcome.Failed)
            assertTrue((outcome as SynthesisOutcome.Failed).shouldPlay)
            assertTrue(outcome.isFinal)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `preload completes without requesting playback`() {
        val directory = Files.createTempDirectory("tts-preload").toFile()
        try {
            val gate = SynthesisCompletionGate()
            val request = ticket(directory, "phrase_preload", "preload_5")

            gate.reserve(request, shouldPlay = false, isFinal = false)
            gate.markAccepted("preload_5", accepted = true)
            val outcome = gate.markCompleted("preload_5")

            assertTrue(outcome is SynthesisOutcome.Completed)
            assertFalse((outcome as SynthesisOutcome.Completed).shouldPlay)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `two first requests for one cache key share one synthesis and merge playback intent`() {
        val directory = Files.createTempDirectory("tts-join").toFile()
        try {
            val gate = SynthesisCompletionGate()
            val first = ticket(directory, "same_phrase", "preload_6")
            val second = ticket(directory, "same_phrase", "tts_7")

            assertTrue(gate.reserve(first, shouldPlay = false, isFinal = false) is SynthesisReservation.Started)
            val joined = gate.reserve(second, shouldPlay = true, isFinal = true)

            assertTrue(joined is SynthesisReservation.Joined)
            assertEquals("preload_6", (joined as SynthesisReservation.Joined).activeUtteranceId)
            gate.markAccepted("preload_6", accepted = true)
            val outcome = gate.markCompleted("preload_6")

            assertTrue(outcome is SynthesisOutcome.Completed)
            assertTrue((outcome as SynthesisOutcome.Completed).shouldPlay)
            assertTrue(outcome.isFinal)
            assertNull(gate.markCompleted("tts_7"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `a reservation can be released when a completed cache entry wins a race`() {
        val directory = Files.createTempDirectory("tts-cache-race").toFile()
        try {
            val gate = SynthesisCompletionGate()
            val first = ticket(directory, "racing_phrase", "tts_10")
            assertTrue(gate.reserve(first, shouldPlay = true, isFinal = true) is SynthesisReservation.Started)
            assertTrue(gate.forget("tts_10"))
            assertFalse(gate.isPending("tts_10"))
            assertFalse(gate.forget("tts_10"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `cache hit requires completion marker and a sufficiently large file`() {
        val directory = Files.createTempDirectory("tts-cache").toFile()
        try {
            val cache = CompletedAudioCache(directory)
            val output = cache.completedFile("known_phrase")
            assertTrue(directory.mkdirs() || directory.isDirectory)
            output.writeBytes(ByteArray(128) { 1 })

            assertFalse(cache.isComplete("known_phrase"))
            File(directory, "known_phrase.complete").writeText("utterance-1")
            assertTrue(cache.isComplete("known_phrase"))

            output.writeBytes(ByteArray(20) { 1 })
            assertFalse(cache.isComplete("known_phrase"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `partial output cannot be promoted before it is complete`() {
        val directory = Files.createTempDirectory("tts-promote").toFile()
        try {
            val cache = CompletedAudioCache(directory)
            val request = cache.ticket("promoted_phrase", "tts_8")
            assertTrue(directory.mkdirs() || directory.isDirectory)
            request.temporaryFile.writeBytes(ByteArray(20) { 1 })

            assertFalse(cache.promote(request))
            assertFalse(cache.isComplete("promoted_phrase"))

            val fullRequest = cache.ticket("promoted_phrase", "tts_9")
            fullRequest.temporaryFile.writeBytes(ByteArray(128) { 2 })
            assertTrue(cache.promote(fullRequest))
            assertTrue(cache.isComplete("promoted_phrase"))
            assertTrue(cache.completedFile("promoted_phrase").isFile)
            assertFalse(fullRequest.temporaryFile.exists())
            assertNotNull(cache.completedFile("promoted_phrase").length())
        } finally {
            directory.deleteRecursively()
        }
    }
}
