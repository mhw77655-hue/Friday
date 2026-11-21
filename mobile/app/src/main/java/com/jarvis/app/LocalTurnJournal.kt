package com.jarvis.app

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Bounded app-private conversation journal.
 *
 * The old Obsidian sidecar wrote turns to shared external storage and required
 * MANAGE_EXTERNAL_STORAGE. Friday keeps the same local continuity value inside
 * its own sandbox; no external-storage permission or network upload is involved.
 */
object LocalTurnJournal {
    private const val MAX_BYTES = 1_048_576L
    private const val MAX_ENTRIES = 1_000
    private const val FILE_NAME = "turns.jsonl"
    private val lock = Any()

    @Volatile
    private var journalDirectory: File? = null

    fun initialize(context: Context) {
        initialize(File(context.applicationContext.filesDir, "memory/turn-journal"))
    }

    internal fun initialize(directory: File) {
        synchronized(lock) {
            directory.mkdirs()
            journalDirectory = directory
        }
    }

    fun logTurn(fromFriday: Boolean, text: String) {
        if (text.isBlank()) return
        val directory = journalDirectory ?: return
        synchronized(lock) {
            runCatching {
                if (!directory.exists()) directory.mkdirs()
                val file = File(directory, FILE_NAME)
                val entry = buildString {
                    append("{\"timestamp\":")
                    append(System.currentTimeMillis())
                    append(",\"speaker\":\"")
                    append(if (fromFriday) "friday" else "user")
                    append("\",\"text\":\"")
                    append(escapeJson(text))
                    append("\"}")
                }
                file.appendText(entry + "\n", StandardCharsets.UTF_8)
                if (file.length() > MAX_BYTES) {
                    val retained = file.readLines(StandardCharsets.UTF_8).takeLast(MAX_ENTRIES)
                    file.writeText(retained.joinToString(separator = "\n", postfix = if (retained.isEmpty()) "" else "\n"), StandardCharsets.UTF_8)
                }
            }
        }
    }

    private fun escapeJson(value: String): String = buildString(value.length + 16) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
    }
}
