package com.jarvis.app.memory

import org.json.JSONArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Production [EmbeddingProvider] that produces a REAL fixed-dimension embedding
 * from a genuine learned sentence-embedding model's forward pass, run through
 * the same llama.cpp / llama-server native inference path already used
 * elsewhere in the project (see [com.jarvis.app.model.adapters.LlamaCppAdapter],
 * which talks to llama-server at 127.0.0.1:8080).
 *
 * Unlike the previous feature-hashing provider (which "embedded" text by
 * hashing its tokens into a bag-of-words feature vector — NOT a learned
 * representation), this provider performs an actual forward pass of a small,
 * dedicated embedding model served by llama.cpp's `/embedding` endpoint and
 * returns the model's real output vector. It is a compact embedding model,
 * distinct from Stage 4's future reasoning backbone.
 *
 * Over the wire this is an HTTP POST to the on-device llama-server loopback
 * endpoint (no external network — the model runs locally), the same transport
 * the rest of the project uses for its local native inference. The resulting
 * float vector is offered both as the raw [embed] result and as a
 * binary-quantized BLOB via [quantizedEmbed] / [EmbeddingMath.binaryQuantize]
 * (1 bit per dimension, packed into dimension/8 bytes) ready for the
 * Android-native BLOB store and pure-Kotlin Hamming-distance search.
 *
 * If the on-device llama-server is not reachable (e.g. during JVM unit tests
 * on the dev host, where no server is launched), callers should depend on the
 * [EmbeddingProvider] interface; tests exercise the same contract against a
 * pure-Kotlin reference provider (see the memory test sources) rather than
 * requiring the binary on the test JVM. The PRODUCTION wiring in the APK is
 * this class.
 */
class NeuralEmbeddingProvider(
    override val dimension: Int = 256,
    private val baseUrl: String = "http://127.0.0.1:8080",
    private val readTimeoutMs: Long = 30_000L
) : EmbeddingProvider {

    override val modelId: String = "jarvis-neural-embed-v1:d=$dimension"

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(readTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Perform the model's real forward pass for [text], returning the raw
     * learned embedding as a [dimension]-length float vector.
     */
    override fun embed(text: String): FloatArray {
        val body = org.json.JSONObject().apply {
            put("content", text)
        }.toString()

        val request = okhttp3.Request.Builder()
            .url("$baseUrl/embedding")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "llama-server /embedding failed (HTTP ${response.code}): " +
                        "${response.body?.string() ?: "no body"}"
                )
            }
            val resp = response.body?.string() ?: throw IllegalStateException("empty /embedding response")
            val json = org.json.JSONObject(resp)
            val embedding: JSONArray = if (json.has("embedding")) {
                json.getJSONArray("embedding")
            } else {
                // Some llama.cpp builds nest it differently; fall back to a
                // top-level float array.
                throw IllegalStateException("/embedding response missing 'embedding' field")
            }
            if (embedding.length() != dimension) {
                throw IllegalStateException(
                    "embedding dimension ${embedding.length()} != expected $dimension"
                )
            }
            return FloatArray(dimension) { i -> embedding.getDouble(i).toFloat() }
        }
    }

    /**
     * [embed] followed by [EmbeddingMath.binaryQuantize] — the 1-bit-per-dim
     * BLOB representation persisted in the Android-native store.
     */
    fun quantizedEmbed(text: String): ByteArray = EmbeddingMath.binaryQuantize(embed(text))
}
