package com.jarvis.app.memory

/**
 * Contract for an on-device text embedding provider used by Galaxy Memory.
 *
 * Implementations produce a fixed-dimension float vector for a piece of text.
 * The provider is intentionally DECOUPLED from whatever Stage 4 eventually
 * picks as the full reasoning backbone: it is a small, dedicated embedding
 * model whose only job is producing semantic vectors. Swapping in a different
 * model (e.g. a neural embedding via the llama.cpp embeddings path) requires
 * no changes to callers as long as it satisfies this interface.
 */
interface EmbeddingProvider {
    /** Fixed, known dimension of every vector this provider emits. */
    val dimension: Int

    /** Stable identity of the underlying embedding model (for swap detection). */
    val modelId: String

    /**
     * Embed [text] into a fixed-dimension vector. The result is a REAL vector
     * (never a stub/random/zero placeholder) with [dimension] entries.
     */
    fun embed(text: String): FloatArray
}

/**
 * Shared vector math used across Galaxy Memory. Pure Kotlin + java.nio so it
 * is safe to use on both the Android runtime and the JVM unit-test runtime.
 */
object EmbeddingMath {
    /**
     * Cosine similarity in [-1, 1]; 1 = identical direction, 0 = orthogonal.
     * Both vectors must share the same dimension.
     */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "vector dimension mismatch: ${a.size} vs ${b.size}" }
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
        if (denom == 0f) return 0f
        return dot / denom
    }

    /**
     * Binary-quantize a float vector to 1 bit per dimension: a component is 1
     * when its value is >= 0, else 0. The bits are packed MSB-first into a
     * byte array of length [dimension]/8 (dimension must be a multiple of 8).
     * This is the representation EMBEDDING-PROVIDER persists as a BLOB in the
     * Android-native SQLite store; nearest-neighbour search is Hamming distance
     * over these packed bytes (XOR + popcount) entirely in Kotlin.
     */
    fun binaryQuantize(v: FloatArray): ByteArray {
        require(v.size % 8 == 0) { "dimension must be a multiple of 8, got ${v.size}" }
        val out = ByteArray(v.size / 8)
        for (i in v.indices) {
            val bit = if (v[i] >= 0f) 1 else 0
            if (bit == 1) out[i / 8] = (out[i / 8].toInt() or (1 shl (7 - (i % 8)))).toByte()
        }
        return out
    }

    /**
     * Hamming distance between two binary-quantized [ByteArray]s of equal
     * length: number of differing bits (XOR per byte, then popcount). Lower =
     * more similar.
     */
    fun hammingDistance(a: ByteArray, b: ByteArray): Int {
        require(a.size == b.size) { "blob length mismatch: ${a.size} vs ${b.size}" }
        var d = 0
        for (i in a.indices) {
            // Mask to unsigned byte: Byte.toInt() sign-extends, which would
            // otherwise count phantom high bits (e.g. 0xFF -> 32 ones).
            val x = (a[i].toInt() and 0xFF) xor (b[i].toInt() and 0xFF)
            d += x.countOneBits()
        }
        return d
    }

    /**
     * Hamming similarity in [0, 1]: 1 - (hamming distance / total bits). The
     * total bit count is 8 * [a].size, so the caller must pass [a] (and [b])
     * of equal length. 1 = identical binary codes, 0 = maximally different.
     */
    fun hammingSimilarity(a: ByteArray, b: ByteArray): Float {
        require(a.size == b.size) { "blob length mismatch: ${a.size} vs ${b.size}" }
        val totalBits = a.size * 8
        if (totalBits == 0) return 1f
        return 1f - hammingDistance(a, b) / totalBits.toFloat()
    }
}
