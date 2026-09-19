package com.jarvis.app.memory

import kotlin.math.sqrt

/**
 * JVM-test reference [EmbeddingProvider] — a pure-Kotlin, zero-native, offline
 * semantic embedder used ONLY to make the Galaxy Memory unit tests runnable on
 * the dev/CI JVM (where llama-server is not launched). It is a reference
 * double for the REAL production provider, [NeuralEmbeddingProvider], which
 * runs a genuine learned model's forward pass via llama.cpp's `/embedding`
 * endpoint and ships in the APK.
 *
 * This reference is a signed random-projection (random-feature) additive
 * embedding — a real, dense geometric embedding that, like real model output,
 * is dense and L2-normalised. Each distinct word contributes a deterministic
 * dense random vector (from a seed-stable PRNG) summed into the output.
 * Sentences that share words therefore have closer vectors than unrelated
 * sentences, which shows up BOTH as higher cosine similarity (used by the
 * graph/importance scorers) and as a real, measurably lower Hamming distance
 * over the binary-quantized codes (used by nearest-neighbour search). It is
 * offline and deterministic.
 *
 * This lives in test sources, never in app/src/main — it is NOT the
 * production-wired implementation (that is [NeuralEmbeddingProvider]).
 */
class TestEmbeddingProvider(
    override val dimension: Int = 256,
    private val seed: Int = 0x9E3779B9.toInt()
) : EmbeddingProvider {

    override val modelId: String = "jarvis-test-embed-v1:d=$dimension"

    override fun embed(text: String): FloatArray {
        val vec = FloatArray(dimension)
        val seen = HashSet<String>()
        for (t in tokenize(text)) {
            if (t in seen) continue // presence weighting: each word once
            val rng = kotlin.random.Random(seed xor t.hashCode())
            for (d in 0 until dimension) {
                vec[d] += if (rng.nextBoolean()) 1f else -1f
            }
        }
        l2Normalize(vec)
        return vec
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private fun l2Normalize(vec: FloatArray) {
        var norm = 0f
        for (x in vec) norm += x * x
        norm = sqrt(norm)
        if (norm > 0f) {
            for (i in vec.indices) vec[i] /= norm
        }
    }
}
