package com.jarvis.app.diagnostics

import java.time.Instant
import java.util.ArrayDeque

/**
 * Bounded transition log for technical diagnostics.
 *
 * A subsystem is recorded only when its meaningful state changes. Volatile
 * samples such as microphone amplitude and voice ping latency are deliberately
 * excluded so a polling loop cannot fill the report with repeated snapshots.
 * Screen text is never recorded; only whether context is available.
 */
class DiagnosticEventLog(private val capacity: Int = 250) {
    data class Event(val atUtc: String, val subsystem: String, val state: String)

    private val lock = Any()
    private val events = ArrayDeque<Event>()
    private val lastStateBySubsystem = LinkedHashMap<String, String>()

    init {
        require(capacity > 0) { "Diagnostic event capacity must be positive" }
    }

    fun record(
        subsystem: String,
        rawState: String,
        atUtc: String = Instant.now().toString()
    ): Boolean {
        require(subsystem.isNotBlank())
        val state = meaningfulState(subsystem, rawState).take(240)
        synchronized(lock) {
            if (lastStateBySubsystem[subsystem] == state) return false
            lastStateBySubsystem[subsystem] = state
            events.addLast(Event(atUtc, subsystem, state))
            while (events.size > capacity) events.removeFirst()
            return true
        }
    }

    fun snapshot(): List<Event> = synchronized(lock) { events.toList() }

    private fun meaningfulState(subsystem: String, raw: String): String = when (subsystem) {
        "microphone" -> if (raw.startsWith("Mic: energy monitor active")) "energy monitor active" else "off"
        "screen_context" -> when {
            raw.isBlank() || raw == "Screen: " || raw.contains("not read yet", ignoreCase = true) ->
                "unavailable"
            else -> "available; content omitted"
        }
        "voice_process" -> raw.substringBefore(" ping=").trim()
        // Recognition text is private user content. Keep only lifecycle/status signals.
        "speech_recognition" -> when {
            raw.contains("heard", ignoreCase = true) -> "recognition_result_received; transcript omitted"
            raw.contains("error", ignoreCase = true) || raw.contains("fail", ignoreCase = true) -> "recognition_error"
            raw.contains("listen", ignoreCase = true) || raw.contains("record", ignoreCase = true) -> "recognition_active"
            raw.contains("idle", ignoreCase = true) -> "idle"
            else -> "status_changed; transcript omitted"
        }
        else -> raw.trim()
    }
}
