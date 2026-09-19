package com.jarvis.app.buildtwin

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * The production [TwinHealthProbe]: a real HTTP GET to the twin's
 * voiceforge /health endpoint (voiceforge/voiceforge_server.py returns
 * `{"healthy": <bool>, "checkpoint": ..., "error": ...}`).
 *
 * JVM + Android safe (java.net + org.json, both present on both runtimes).
 * A malformed/absent payload surfaces honestly as healthy=false with the
 * transport detail — the gate refuses promotion on any read failure.
 */
class HttpTwinHealthProbe(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 15_000
) : TwinHealthProbe {

    override fun probe(spec: BuildTwinSpec): TwinHealth {
        val conn = runCatching { URL(spec.healthUrl) }.getOrElse {
            return TwinHealth(healthy = false, error = "bad url '${spec.healthUrl}': ${it.message}")
        }.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        return try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            TwinHealth(
                healthy = (code in 200..299) && parseHealthy(body),
                checkpoint = parseString(body, "checkpoint"),
                error = parseString(body, "error"),
                rawResponse = body
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun parseHealthy(body: String): Boolean =
        runCatching { JSONObject(body).optBoolean("healthy", false) }
            .getOrDefault(false)

    private fun parseString(body: String, key: String): String? =
        runCatching { JSONObject(body).optString(key).takeIf { it.isNotEmpty() } }
            .getOrNull()
}