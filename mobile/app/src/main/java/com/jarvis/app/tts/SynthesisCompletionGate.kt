package com.jarvis.app.tts

import java.io.File

/**
 * One asynchronous file-synthesis request. The TTS callback is the only
 * authority that a temporary file has finished being written.
 */
data class SynthesisTicket(
    val utteranceId: String,
    val cacheKey: String,
    val temporaryFile: File,
    val completedFile: File
)

sealed class SynthesisReservation {
    data class Started(val utteranceId: String) : SynthesisReservation()
    data class Joined(val activeUtteranceId: String) : SynthesisReservation()
}

sealed class SynthesisOutcome {
    data class Completed(
        val ticket: SynthesisTicket,
        val shouldPlay: Boolean,
        val isFinal: Boolean
    ) : SynthesisOutcome()

    data class Failed(
        val ticket: SynthesisTicket,
        val shouldPlay: Boolean,
        val isFinal: Boolean,
        val reason: String
    ) : SynthesisOutcome()
}

/**
 * Coordinates the two independent success signals for asynchronous TTS:
 * synthesizeToFile() accepted the request AND onDone() confirmed the output
 * was written. Either signal may arrive first.
 *
 * Concurrent first requests for one cache key join the same in-flight work.
 */
class SynthesisCompletionGate {
    private data class Flight(
        val ticket: SynthesisTicket,
        var accepted: Boolean = false,
        var completed: Boolean = false,
        var shouldPlay: Boolean = false,
        var isFinal: Boolean = false
    )

    private val flightsById = mutableMapOf<String, Flight>()
    private val flightsByCacheKey = mutableMapOf<String, Flight>()

    @Synchronized
    fun reserve(
        ticket: SynthesisTicket,
        shouldPlay: Boolean,
        isFinal: Boolean
    ): SynthesisReservation {
        val existing = flightsByCacheKey[ticket.cacheKey]
        if (existing != null) {
            existing.shouldPlay = existing.shouldPlay || shouldPlay
            existing.isFinal = existing.isFinal || isFinal
            return SynthesisReservation.Joined(existing.ticket.utteranceId)
        }

        val flight = Flight(
            ticket = ticket,
            shouldPlay = shouldPlay,
            isFinal = isFinal
        )
        flightsById[ticket.utteranceId] = flight
        flightsByCacheKey[ticket.cacheKey] = flight
        return SynthesisReservation.Started(ticket.utteranceId)
    }

    /**
     * Records the synchronous return value. SUCCESS means queued, not finished.
     */
    @Synchronized
    fun markAccepted(
        utteranceId: String,
        accepted: Boolean,
        failureReason: String = "synthesizeToFile rejected the request"
    ): SynthesisOutcome? {
        val flight = flightsById[utteranceId] ?: return null
        if (!accepted) return fail(flight, failureReason)

        flight.accepted = true
        return completeIfReady(flight)
    }

    /** Called only from UtteranceProgressListener.onDone(). */
    @Synchronized
    fun markCompleted(utteranceId: String): SynthesisOutcome? {
        val flight = flightsById[utteranceId] ?: return null
        flight.completed = true
        return completeIfReady(flight)
    }

    /** Called from onError(), onStop(), or a synchronous exception. */
    @Synchronized
    fun markFailed(utteranceId: String, reason: String): SynthesisOutcome? {
        val flight = flightsById[utteranceId] ?: return null
        return fail(flight, reason)
    }

    @Synchronized
    fun isPending(utteranceId: String): Boolean = flightsById.containsKey(utteranceId)

    /** Releases a reservation when a completed cache entry won the race. */
    @Synchronized
    fun forget(utteranceId: String): Boolean {
        val flight = flightsById[utteranceId] ?: return false
        remove(flight)
        return true
    }

    /** Cancel any outstanding work when a reply is interrupted or superseded. */
    @Synchronized
    fun cancelAll(reason: String): List<SynthesisOutcome.Failed> {
        val pending = flightsById.values.toList()
        flightsById.clear()
        flightsByCacheKey.clear()
        return pending.map {
            SynthesisOutcome.Failed(
                ticket = it.ticket,
                shouldPlay = it.shouldPlay,
                isFinal = it.isFinal,
                reason = reason
            )
        }
    }

    private fun completeIfReady(flight: Flight): SynthesisOutcome? {
        if (!flight.accepted || !flight.completed) return null
        remove(flight)
        return SynthesisOutcome.Completed(
            ticket = flight.ticket,
            shouldPlay = flight.shouldPlay,
            isFinal = flight.isFinal
        )
    }

    private fun fail(flight: Flight, reason: String): SynthesisOutcome.Failed {
        remove(flight)
        return SynthesisOutcome.Failed(
            ticket = flight.ticket,
            shouldPlay = flight.shouldPlay,
            isFinal = flight.isFinal,
            reason = reason
        )
    }

    private fun remove(flight: Flight) {
        flightsById.remove(flight.ticket.utteranceId)
        if (flightsByCacheKey[flight.ticket.cacheKey] === flight) {
            flightsByCacheKey.remove(flight.ticket.cacheKey)
        }
    }
}

/**
 * Files become cache hits only after promotion following onDone(). In-flight
 * output uses a distinct temporary path; abandoned partial files are never
 * mistaken for completed audio after a process restart.
 */
class CompletedAudioCache(
    private val directory: File,
    private val minimumAudioBytes: Long = 44L
) {
    fun completedFile(cacheKey: String): File = File(directory, "$cacheKey.wav")

    private fun completionMarker(cacheKey: String): File =
        File(directory, "$cacheKey.complete")

    fun ticket(cacheKey: String, utteranceId: String): SynthesisTicket =
        SynthesisTicket(
            utteranceId = utteranceId,
            cacheKey = cacheKey,
            temporaryFile = File(directory, "$cacheKey.$utteranceId.partial.wav"),
            completedFile = completedFile(cacheKey)
        )

    fun isComplete(cacheKey: String): Boolean {
        val output = completedFile(cacheKey)
        val marker = completionMarker(cacheKey)
        return output.isFile &&
            output.length() >= minimumAudioBytes &&
            marker.isFile &&
            marker.length() > 0L
    }

    /** Atomically promote only a sufficiently large file after onDone(). */
    fun promote(ticket: SynthesisTicket): Boolean {
        if (!ticket.temporaryFile.isFile ||
            ticket.temporaryFile.length() < minimumAudioBytes
        ) {
            discard(ticket)
            return false
        }

        if (!directory.exists() && !directory.mkdirs()) {
            discard(ticket)
            return false
        }

        val marker = completionMarker(ticket.cacheKey)
        marker.delete()

        if (ticket.completedFile.exists() && !ticket.completedFile.delete()) {
            discard(ticket)
            return false
        }

        if (!ticket.temporaryFile.renameTo(ticket.completedFile)) {
            discard(ticket)
            return false
        }

        return try {
            marker.writeText(ticket.utteranceId)
            true
        } catch (_: Exception) {
            ticket.completedFile.delete()
            marker.delete()
            false
        }
    }

    fun discard(ticket: SynthesisTicket) {
        ticket.temporaryFile.delete()
    }

    fun invalidate(cacheKey: String) {
        completedFile(cacheKey).delete()
        completionMarker(cacheKey).delete()
    }

    fun remove(cacheKey: String) {
        invalidate(cacheKey)
    }
}
