package com.jarvis.app.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Production Android-native [VectorStore] backed by
 * [android.database.sqlite.SQLiteDatabase] — plain Android SQLite, no JDBC, no
 * bundled/downloaded native .so, and no vector-search extension of any kind.
 * This is the implementation that ships in the APK.
 *
 * Embeddings are stored as binary-quantized BLOBs (1 bit per dimension, packed
 * into a byte array of length dimension/8 — see [EmbeddingMath.binaryQuantize])
 * in a normal `BLOB` column. Nearest-neighbour search is ordinary Kotlin code:
 * Hamming distance (XOR each packed byte against the query's packed bytes,
 * popcount, lower = closer) computed over the persisted BLOB rows.
 *
 * JVM unit tests exercise the same contract via a pure-Kotlin zero-native
 * reference store (KotlinVectorStore) because android.database.sqlite is not
 * available on the JVM unit-test runtime of this host.
 */
class AndroidVectorStore(
    context: Context,
    private val dimension: Int,
    private val modelId: String = "jarvis-neural-embed-v1",
    dbName: String = "galaxy_memory_vectors.db",

    /**
     * PROVENANCE-LEDGER: optional durable local record of which source
     * memories every derived artifact came from. When wired, each [upsert]
     * appends one INDEX_ENTRY naming the id being indexed as its source.
     * Null keeps the pre-provenance path byte-for-byte.
     */
    private val provenanceLedger: com.jarvis.app.memory.provenance.ProvenanceLedger? = null
) : VectorStore {

    private val helper = object : SQLiteOpenHelper(context, dbName, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS memories (" +
                    "id TEXT PRIMARY KEY, content TEXT, embedding BLOB NOT NULL, model_id TEXT, metadata TEXT)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No migrations yet.
        }
    }

    private val db: SQLiteDatabase = helper.writableDatabase

    override fun upsert(id: String, content: String, vector: FloatArray, metadata: Map<String, String>) {
        // Real embedding model output, binary-quantized to dimension/8 bytes.
        validateDimension(vector)
        val blob = EmbeddingMath.binaryQuantize(vector)
        val meta = metadata.entries.joinToString(";") { "${it.key}=${it.value}" }
        db.execSQL(
            "INSERT OR REPLACE INTO memories (id, content, embedding, model_id, metadata) VALUES (?,?,?,?,?)",
            arrayOf<Any>(id, content, blob, modelId, meta)
        )
        // PROVENANCE-LEDGER: the index entry derives from the memory it indexes.
        provenanceLedger?.record(
            derivedId = "index-$id",
            kind = com.jarvis.app.memory.provenance.ProvenanceKind.INDEX_ENTRY,
            sourceIds = listOf(id)
        )
    }

    override fun nearest(query: FloatArray, k: Int): List<ScoredMemory> {
        validateDimension(query)
        val qblob = EmbeddingMath.binaryQuantize(query)
        // Pure-Kotlin Hamming search over persisted BLOB rows — no native call.
        val rows = mutableListOf<ScoredMemory>()
        db.rawQuery(
            "SELECT id, content, embedding, metadata FROM memories", null
        ).use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val contentIdx = c.getColumnIndexOrThrow("content")
            val embIdx = c.getColumnIndexOrThrow("embedding")
            val metaIdx = c.getColumnIndexOrThrow("metadata")
            while (c.moveToNext()) {
                val blob = c.getBlob(embIdx) ?: continue
                rows.add(
                    ScoredMemory(
                        id = c.getString(idIdx),
                        content = c.getString(contentIdx),
                        distance = EmbeddingMath.hammingDistance(qblob, blob),
                        metadata = parseMeta(c.getString(metaIdx))
                    )
                )
            }
        }
        return rows.sortedBy { it.distance }.take(k)
    }

    private fun validateDimension(v: FloatArray) {
        require(v.size == dimension) { "vector dimension ${v.size} != store dimension $dimension" }
    }

    private fun parseMeta(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";").mapNotNull { kv ->
            val eq = kv.indexOf('=')
            if (eq <= 0) null else kv.substring(0, eq) to kv.substring(eq + 1)
        }.toMap()
    }

    // FORGET-PROPAGATION: the index/cache sink of a forget. Rows are the
    // INDEX_ENTRY-deriving artifacts, removed so the vector content byte-scan
    // finds zero plaintext after the forget turn (AC1/AC2). Pure SQL DELETE over
    // the same SQLiteDatabase used above — no new storage or native code.
    override fun remove(id: String): Boolean =
        db.delete("memories", "id = ?", arrayOf(id)) > 0

    override fun removeContaining(text: String): Int =
        db.delete("memories", "content LIKE ?", arrayOf("%$text%"))

    override fun contents(): List<String> =
        db.rawQuery("SELECT content FROM memories", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    override fun close() {
        try {
            db.close()
            helper.close()
        } catch (_: Throwable) {
        }
    }
}
