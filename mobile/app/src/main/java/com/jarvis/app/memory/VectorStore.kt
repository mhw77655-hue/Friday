package com.jarvis.app.memory

/**
 * Backend-agnostic contract for persisting and retrieving embeddings.
 *
 * Embeddings are stored as binary-quantized BLOBs: each float vector produced
 * by an [EmbeddingProvider] is reduced to 1 bit per dimension and packed into a
 * byte array of length dimension/8 (see [EmbeddingMath.binaryQuantize]).
 * Nearest-neighbour search is then plain Hamming distance over those packed
 * bytes, computed entirely in Kotlin.
 *
 * Implementations may persist the BLOB rows anywhere (the production Galaxy
 * Memory backend is [AndroidVectorStore], backed by
 * android.database.sqlite.SQLiteDatabase), but callers depend only on this
 * interface with Hamming-distance semantics. JVM unit tests exercise the same
 * contract via a pure-Kotlin zero-native reference store (KotlinVectorStore).
 */
interface VectorStore {
    /** Store or replace an embedding for [id] with associated [content]/[metadata]. */
    fun upsert(id: String, content: String, vector: FloatArray, metadata: Map<String, String> = emptyMap())

    /**
     * Return up to [k] nearest neighbours of [query] ordered by ascending
     * Hamming distance over the stored binary-quantized BLOBs (lower = closer).
     */
    fun nearest(query: FloatArray, k: Int = 1): List<ScoredMemory>

    /** Close/release resources. */
    fun close()

    /**
     * FORGET-PROPAGATION: remove the stored row for [id]. Implementations that
     * never forget (or that have no on-disk storage yet) may keep the default
     * no-op. Returns true when a row was actually removed.
     */
    fun remove(id: String): Boolean = false

    /**
     * FORGET-PROPAGATION: remove every stored row whose content carries [text]
     * (case-insensitive) — the index/cache byte-scan surface of a forget. The
     * Default implementation is a no-op returning 0; the real SQLite store and
     * the Kotlin reference store both override it. Returns the count removed.
     */
    fun removeContaining(text: String): Int = 0

    /**
     * FORGET-PROPAGATION: every stored row's content, in store order — the
     * on-disk index/cache content surface scanned for forgotten plaintext.
     */
    fun contents(): List<String> = emptyList()
}

/**
 * A persisted memory together with its distance from a query vector.
 * [distance] is the Hamming distance over binary-quantized BLOBs: ascending,
 * smaller = more similar.
 */
data class ScoredMemory(
    val id: String,
    val content: String,
    val distance: Int,
    val metadata: Map<String, String> = emptyMap()
)
