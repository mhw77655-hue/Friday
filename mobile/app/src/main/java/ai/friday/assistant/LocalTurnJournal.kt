package ai.friday.assistant

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor

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
    private const val MAX_ENTRY_CHARS = 20_000
    private const val FILE_NAME = "turns.jsonl"
    private val lock = Any()

    @Volatile
    private var journalDirectory: File? = null

    private val journalExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(128),
        { runnable -> Thread(runnable, "FridayTurnJournal").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    fun initialize(context: Context) {
        initialize(File(context.applicationContext.filesDir, "memory/turn-journal"))
    }

    internal fun initialize(directory: File) {
        journalDirectory = directory
        // Directory creation is disk I/O; preserve ordering on the journal lane
        // without making Application.onCreate or the first UI frame wait for it.
        journalExecutor.execute {
            synchronized(lock) {
                runCatching { directory.mkdirs() }.onFailure {
                    Log.e("LocalTurnJournal", "Could not create private journal directory", it)
                }
            }
        }
    }

    fun logTurn(fromFriday: Boolean, text: String) {
        if (text.isBlank()) return
        val directory = journalDirectory ?: return
        journalExecutor.execute {
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
                        append(escapeJson(text.take(MAX_ENTRY_CHARS)))
                        append("\"}")
                    }
                    file.appendText(entry + "\n", StandardCharsets.UTF_8)
                    if (file.length() > MAX_BYTES) {
                        val retainedText = RandomAccessFile(file, "r").use { input ->
                            val start = (input.length() - MAX_BYTES).coerceAtLeast(0L)
                            input.seek(start)
                            val bytes = ByteArray((input.length() - start).toInt())
                            input.readFully(bytes)
                            String(bytes, StandardCharsets.UTF_8).substringAfter('\n', "")
                        }
                        val retained = retainedText.lineSequence()
                            .filter { it.isNotBlank() }
                            .takeLast(MAX_ENTRIES)
                            .toList()
                        file.writeText(
                            retained.joinToString(separator = "\n", postfix = if (retained.isEmpty()) "" else "\n"),
                            StandardCharsets.UTF_8
                        )
                    }
                }.onFailure {
                    Log.e("LocalTurnJournal", "Could not append private turn journal entry", it)
                }
            }
        }
    }

    /** Test-only barrier for deterministic assertions after queued journal writes. */
    internal fun flushForTesting(timeoutMs: Long = 5_000L): Boolean {
        val barrier = CountDownLatch(1)
        journalExecutor.execute { barrier.countDown() }
        return barrier.await(timeoutMs, TimeUnit.MILLISECONDS)
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
