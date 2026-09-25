package com.jarvis.app.memory

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pure-Kotlin, zero-native [VectorStore] reference implementation for JVM unit
 * tests.
 *
 * It persists embeddings as binary-quantized BLOBs (1 bit per dimension, packed
 * into dimension/8 bytes — [EmbeddingMath.binaryQuantize]) and computes real
 * nearest neighbours by actual Hamming distance over every persisted row
 * (XOR + popcount, [EmbeddingMath.hammingDistance]), ordering ascending and
 * returning the top k. This is a genuine KNN computation (deterministic,
 * offline, no mocking, no network, no native library, no vector extension) —
 * the same Hamming-over-BLOB semantics the production [AndroidVectorStore]
 * performs against android.database.sqlite rows.
 */
class KotlinVectorStore(
    private val dimension: Int,
    private val modelId: String = "jarvis-neural-embed-v1",

    /**
     * PROVENANCE-LEDGER: optional durable local record of which source
     * memories every derived artifact came from. When wired, each [upsert]
     * appends one INDEX_ENTRY naming the id being indexed as its source —
     * the same seam the production [AndroidVectorStore] carries.
     */
    private val provenanceLedger: com.jarvis.app.memory.provenance.ProvenanceLedger? = null
) : VectorStore {

    private data class Row(
        val id: String,
        val content: String,
        val blob: ByteArray,
        val metadata: Map<String, String>
    )

    private val rows = CopyOnWriteArrayList<Row>()

    override fun upsert(id: String, content: String, vector: FloatArray, metadata: Map<String, String>) {
        if (vector.size != dimension) {
            throw IllegalArgumentException("vector dimension ${vector.size} != store dimension $dimension")
        }
        val blob = EmbeddingMath.binaryQuantize(vector)
        val existingIdx = rows.indexOfFirst { it.id == id }
        val row = Row(id, content, blob, metadata)
        if (existingIdx >= 0) rows[existingIdx] = row else rows.add(row)
        // PROVENANCE-LEDGER: the index entry derives from the memory it indexes.
        provenanceLedger?.record(
            derivedId = "index-$id",
            kind = com.jarvis.app.memory.provenance.ProvenanceKind.INDEX_ENTRY,
            sourceIds = listOf(id)
        )
    }

    override fun nearest(query: FloatArray, k: Int): List<ScoredMemory> {
        if (query.size != dimension) {
            throw IllegalArgumentException("query dimension ${query.size} != store dimension $dimension")
        }
        val qblob = EmbeddingMath.binaryQuantize(query)
        return rows
            .map { r ->
                ScoredMemory(
                    id = r.id,
                    content = r.content,
                    distance = EmbeddingMath.hammingDistance(qblob, r.blob),
                    metadata = r.metadata
                )
            }
            .sortedBy { it.distance }
            .take(k)
    }

    override fun close() {
        rows.clear()
    }

    // FORGET-PROPAGATION: the same remove/removeContaining/contents contract the
    // production AndroidVectorStore executes against android.database.sqlite rows,
    // mirrored here over the in-memory row list (CopyOnWriteArrayList removal via
    // removeIf — the COW iterator does not support structural removal).
    override fun remove(id: String): Boolean =
        rows.removeIf { it.id == id }

    override fun removeContaining(text: String): Int {
        val before = rows.size
        rows.removeIf { it.content.contains(text, ignoreCase = true) }
        return before - rows.size
    }

    override fun contents(): List<String> = rows.map { it.content }
}
