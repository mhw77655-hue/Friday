package com.jarvis.app

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

private const val TAG = "Telemetry"
private const val LOG_FILE = "telemetry.jsonl"

object Telemetry {
    private var logFile: File? = null

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
        append(JSONObject().apply {
            put("event", "rate_limit")
            put("endpoint", endpoint)
        })
    }

    fun summarize(): String {
        val file = logFile ?: return "telemetry not initialized"
        if (!file.exists()) return "no telemetry recorded yet"
        var sttCloud = 0; var sttLocal = 0
        var ttsCloud = 0; var ttsLocal = 0
        var rateLimits = 0
        file.forEachLine { line ->
            try {
                val obj = JSONObject(line)
                when (obj.optString("event")) {
                    "stt" -> if (obj.optString("source") == "cloud") sttCloud++ else sttLocal++
                    "tts" -> if (obj.optString("source") == "cloud") ttsCloud++ else ttsLocal++
                    "rate_limit" -> rateLimits++
                }
            } catch (e: Exception) { }
        }
        return "stt: $sttCloud cloud / $sttLocal local. tts: $ttsCloud cloud / $ttsLocal local. rate limits: $rateLimits."
    }

    private fun append(obj: JSONObject) {
        val file = logFile ?: run {
            Log.w(TAG, "logged before init() -- dropped: $obj")
            return
        }
        try {
            obj.put("ts", System.currentTimeMillis())
            file.appendText(obj.toString() + "\n")
        } catch (e: Exception) {
            Log.w(TAG, "append failed: ${e.message}")
        }
    }
}
