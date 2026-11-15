package com.jarvis.app

import android.content.Context
import android.util.Log
import com.jarvis.app.diagnostics.DiagnosticRedactor
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

private const val TAG = "Telemetry"
private const val LOG_FILE = "telemetry.jsonl"
private const val MAX_LOG_BYTES = 2 * 1024 * 1024

object Telemetry {
    @Volatile private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE)
    }

    fun logStt(usedCloud: Boolean) {
        append(JSONObject().apply {
            put("event", "stt")
            put("source", if (usedCloud) "cloud" else "local")
        })
    }

    fun logTts(usedCloud: Boolean) {
        append(JSONObject().apply {
            put("event", "tts")
            put("source", if (usedCloud) "cloud" else "local")
        })
    }

    fun logRateLimit(endpoint: String) {
        val safeEndpoint = endpoint.substringBefore("?").substringBefore("#").take(160)
        append(JSONObject().apply {
            put("event", "rate_limit")
            put("endpoint", DiagnosticRedactor.redact(safeEndpoint) ?: "[redacted]")
        })
    }

    /**
     * Record a non-content operational signal. Callers must pass state labels,
     * never transcripts, prompts, screen text, or notification bodies.
     */
    fun logSignal(signal: String, value: String) {
        val safeName = DiagnosticRedactor.safeSignalName(signal) ?: return
        append(JSONObject().apply {
            put("event", "signal")
            put("signal", safeName)
            put("value", DiagnosticRedactor.redact(value) ?: "")
        })
    }

    fun summarize(): String {
        val file = logFile ?: return "telemetry not initialized"
        if (!file.exists()) return "no telemetry recorded yet"
        var sttCloud = 0; var sttLocal = 0
        var ttsCloud = 0; var ttsLocal = 0
        var rateLimits = 0
        runCatching {
            file.forEachLine { line ->
                try {
                    val obj = JSONObject(line)
                    when (obj.optString("event")) {
                        "stt" -> if (obj.optString("source") == "cloud") sttCloud++ else sttLocal++
                        "tts" -> if (obj.optString("source") == "cloud") ttsCloud++ else ttsLocal++
                        "rate_limit" -> rateLimits++
                    }
                } catch (_: Exception) { }
            }
        }
        return "stt: $sttCloud cloud / $sttLocal local. tts: $ttsCloud cloud / $ttsLocal local. rate limits: $rateLimits."
    }

    /**
     * Return only allow-listed structured events, bounded by byte and line
     * count. This prevents accidental export of arbitrary app-private files.
     */
    @Synchronized
    fun exportForDiagnostics(maxBytes: Int = 512 * 1024, maxLines: Int = 2500): String {
        val file = logFile ?: return ""
        if (!file.isFile || maxBytes <= 0 || maxLines <= 0) return ""

        val raw = runCatching { file.readBytes() }.getOrNull() ?: return ""
        val bounded = if (raw.size > maxBytes) raw.copyOfRange(raw.size - maxBytes, raw.size) else raw
        var text = String(bounded, StandardCharsets.UTF_8)
        if (raw.size > maxBytes) text = text.substringAfter('\n', "")
        val safeLines = text.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                runCatching {
                    val input = JSONObject(line)
                    val event = input.optString("event")
                    val output = JSONObject().put("event", event).put("ts", input.optLong("ts"))
                    when (event) {
                        "stt", "tts" -> output.put("source", input.optString("source"))
                        "rate_limit" -> output.put(
                            "endpoint",
                            DiagnosticRedactor.redact(input.optString("endpoint").substringBefore("?")) ?: "[redacted]"
                        )
                        "signal" -> {
                            val name = DiagnosticRedactor.safeSignalName(input.optString("signal")) ?: return@mapNotNull null
                            output.put("signal", name)
                            output.put("value", DiagnosticRedactor.redact(input.optString("value")) ?: "")
                        }
                        else -> return@mapNotNull null
                    }
                    output.toString()
                }.getOrNull()
            }
            .toList()
            .takeLast(maxLines)
        return safeLines.joinToString(separator = "\n", postfix = if (safeLines.isEmpty()) "" else "\n")
    }

    @Synchronized
    private fun append(obj: JSONObject) {
        val file = logFile ?: run {
            Log.w(TAG, "logged before init() -- dropped event ${obj.optString("event")}")
            return
        }
        try {
            obj.put("ts", System.currentTimeMillis())
            file.appendText(obj.toString() + "\n", Charsets.UTF_8)
            if (file.length() > MAX_LOG_BYTES) {
                val bytes = file.readBytes()
                val kept = bytes.copyOfRange((bytes.size - MAX_LOG_BYTES / 2).coerceAtLeast(0), bytes.size)
                val text = String(kept, StandardCharsets.UTF_8).substringAfter('\n', "")
                file.writeText(text, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.w(TAG, "append failed: ${e.message}")
        }
    }
}
