package com.jarvis.app.memory

import android.content.Context
import android.database.sqlite.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Production Android-native [MemoryGraphStore] backed by
 * [android.database.sqlite.SQLiteDatabase] (real Android SQLite, no JDBC, no
 * desktop-glibc binaries). This is the implementation that ships in the APK;
 * the JVM unit tests exercise the same contract via a pure-Kotlin zero-JDBC
 * reference store (FakeMemoryGraphStore) because android.database.sqlite is not
 * available on the JVM unit-test runtime.
 *
 * Bi-temporal validity: every row carries validFrom/validUntil. A contradicting
 * fact (same subject+predicate) with an open-ended validUntil supersedes the old
 * row by setting its validUntil (it is kept, never hard-deleted), matching the
 * project-wide principle that entities are decayed/superseded, never deleted.
 */
class AndroidMemoryGraphStore(
    context: Context,
    dbName: String = "galaxy_memory_graph.db"
) : MemoryGraphStore {

    private val helper = object : SQLiteOpenHelper(context, dbName, null, SCHEMA_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(CREATE_NODES)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_nodes_subject_pred ON nodes(subject, predicate)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_nodes_valid ON nodes(validFrom, validUntil)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // CORRECTION-CHAIN: v1 -> v2 adds the forward supersession pointer, the
            // accessibility axis and the six split signals. Every existing row
            // keeps its value: each new column is nullable and a superseded row
            // with no known successor simply keeps supersededBy = NULL. A column
            // the device's database already carries is left exactly as it is.
            if (oldVersion < 2) {
                val present = existingColumnNames(db)
                for (column in V2_ADDED_COLUMNS) {
                    if (column.substringBefore(' ') !in present) {
                        db.execSQL("ALTER TABLE nodes ADD COLUMN $column")
                    }
                }
            }
        }
    }

    private val db: SQLiteDatabase = helper.writableDatabase

    override fun addFact(subject: String, predicate: String, `object`: String, source: String): String {
        val now = System.currentTimeMillis()
        val id = java.util.UUID.randomUUID().toString()
        db.beginTransaction()
        try {
            // Supersede-not-overwrite: the open row for this slot is closed AND
            // pointed at the node that replaces it, in the same transaction.
            db.execSQL(
                "UPDATE nodes SET validUntil = ?, supersededBy = ? " +
                    "WHERE subject = ? AND predicate = ? AND validUntil IS NULL",
                arrayOf<Any>(now, id, subject, predicate)
            )
            db.execSQL(
                "INSERT INTO nodes (id, subject, predicate, object, source, validFrom, validUntil) VALUES (?,?,?,?,?,?,NULL)",
                arrayOf<Any>(id, subject, predicate, `object`, source, now)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return id
    }

    override fun query(
        subject: String?,
        predicate: String?,
        asOfTime: Long
    ): List<MemoryNode> {
        val where = buildList {
            if (subject != null) add("subject = ?")
            if (predicate != null) add("predicate = ?")
            add("validFrom <= ?")
            add("(validUntil IS NULL OR validUntil > ?)")
        }.joinToString(" AND ")
        val args = buildList {
            if (subject != null) add(subject)
            if (predicate != null) add(predicate)
            add(asOfTime)
            add(asOfTime)
        }.map { it as Any }.toTypedArray()

        return queryNodes(
            "SELECT $NODE_COLUMNS FROM nodes " +
                "WHERE $where ORDER BY validFrom ASC", args
        )
    }

    override fun getHistory(subject: String, predicate: String): List<MemoryNode> {
        return queryNodes(
            "SELECT $NODE_COLUMNS FROM nodes " +
                "WHERE subject = ? AND predicate = ? ORDER BY validFrom ASC",
            arrayOf(subject, predicate)
        )
    }

    override fun nodeCount(): Long {
        db.rawQuery("SELECT COUNT(*) FROM nodes", null).use { c ->
            c.moveToFirst()
            return c.getLong(0)
        }
    }

    // FORGET-PROPAGATION: forget by EXPIRY, never deletion — the row is kept
    // (nodeCount never decreases) and its validity window closes at now, so
    // query/as-of-now retrievers stop returning the forgotten fact. Same
    // supersede-not-delete move addFact already uses for contradictions, applied
    // to every currently-valid node whose subject/object carries the content.
    // Pure SQL over the same SQLiteDatabase — no new storage or native code.
    override fun removeContaining(text: String): Int {
        val count = db.rawQuery(
            "SELECT COUNT(*) FROM nodes WHERE validUntil IS NULL AND (object LIKE ? OR subject LIKE ?)",
            arrayOf("%$text%", "%$text%")
        ).use { c -> c.moveToFirst(); c.getLong(0) }.toInt()
        if (count > 0) {
            db.execSQL(
                "UPDATE nodes SET validUntil = ? WHERE validUntil IS NULL AND (object LIKE ? OR subject LIKE ?)",
                arrayOf<Any>(System.currentTimeMillis(), "%$text%", "%$text%")
            )
        }
        return count
    }

    // CORRECTION-CHAIN: the six split signals are stored field-by-field, never as
    // one collapsed number, and the accessibility axis is a SEPARATE seam so a
    // consolidation pass can move accessibility without ever reaching a signal.
    override fun recordSignals(id: String, profile: SignalProfile): Boolean {
        db.execSQL(
            "UPDATE nodes SET relevance = ?, importance = ?, uncertainty = ?, " +
                "novelty = ?, consent = ?, cost = ? WHERE id = ?",
            arrayOf<Any>(
                profile.relevance.toDouble(),
                profile.importance.toDouble(),
                profile.uncertainty.toDouble(),
                profile.novelty.toDouble(),
                profile.consent.toDouble(),
                profile.cost.toDouble(),
                id
            )
        )
        return true
    }

    override fun setAccessibility(id: String, accessibility: Float): Boolean {
        db.execSQL(
            "UPDATE nodes SET accessibility = ? WHERE id = ?",
            arrayOf<Any>(accessibility.toDouble(), id)
        )
        return true
    }

    private fun queryNodes(sql: String, args: Array<out Any>): List<MemoryNode> {
        val out = mutableListOf<MemoryNode>()
        db.rawQuery(sql, args.map { it.toString() }.toTypedArray()).use { c ->
            while (c.moveToNext()) {
                out.add(
                    MemoryNode(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        subject = c.getString(c.getColumnIndexOrThrow("subject")),
                        predicate = c.getString(c.getColumnIndexOrThrow("predicate")),
                        `object` = c.getString(c.getColumnIndexOrThrow("object")),
                        source = c.getString(c.getColumnIndexOrThrow("source")),
                        validFrom = c.getLong(c.getColumnIndexOrThrow("validFrom")),
                        validUntil = c.longOrNull("validUntil"),
                        supersededBy = c.stringOrNull("supersededBy"),
                        accessibility = c.floatOrNull("accessibility"),
                        relevance = c.floatOrNull("relevance"),
                        importance = c.floatOrNull("importance"),
                        uncertainty = c.floatOrNull("uncertainty"),
                        novelty = c.floatOrNull("novelty"),
                        consent = c.floatOrNull("consent"),
                        cost = c.floatOrNull("cost")
                    )
                )
            }
        }
        return out
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val idx = getColumnIndexOrThrow(column)
        return if (isNull(idx)) null else getLong(idx)
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val idx = getColumnIndexOrThrow(column)
        return if (isNull(idx)) null else getString(idx)
    }

    private fun Cursor.floatOrNull(column: String): Float? {
        val idx = getColumnIndexOrThrow(column)
        return if (isNull(idx)) null else getFloat(idx)
    }

    override fun close() {
        try {
            db.close()
            helper.close()
        } catch (_: Throwable) {
        }
    }

    private companion object {
        /**
         * The column names SQLite reports for [CREATE_NODES] on this device's
         * database, so a v1 -> v2 upgrade adds only the columns that are truly
         * missing instead of relying on ALTER throwing for the ones that are not.
         */
        private fun existingColumnNames(db: SQLiteDatabase): Set<String> =
            db.rawQuery("PRAGMA table_info(nodes)", null).use { c ->
                val nameIdx = c.getColumnIndexOrThrow("name")
                val names = mutableSetOf<String>()
                while (c.moveToNext()) names.add(c.getString(nameIdx))
                names
            }

        /**
         * v2 = v1 + the forward supersession pointer, the accessibility axis and
         * the six split signals. Bumping the version is what makes the
         * onUpgrade ALTERs run on a device that already has a v1 database.
         */
        const val SCHEMA_VERSION = 2

        const val CREATE_NODES =
            "CREATE TABLE nodes (" +
                "id TEXT PRIMARY KEY, " +
                "subject TEXT NOT NULL, " +
                "predicate TEXT NOT NULL, " +
                "object TEXT NOT NULL, " +
                "source TEXT NOT NULL DEFAULT '', " +
                "validFrom INTEGER NOT NULL, " +
                "validUntil INTEGER, " +
                // v2: supersession pointer + accessibility axis + the six split
                // signals, all nullable so a fresh row needs no value for them.
                "supersededBy TEXT, " +
                "accessibility REAL, " +
                "relevance REAL, " +
                "importance REAL, " +
                "uncertainty REAL, " +
                "novelty REAL, " +
                "consent REAL, " +
                "cost REAL)"

        /** Added in v2; nullable so every existing row keeps its values. */
        val V2_ADDED_COLUMNS = listOf(
            "supersededBy TEXT",
            "accessibility REAL",
            "relevance REAL",
            "importance REAL",
            "uncertainty REAL",
            "novelty REAL",
            "consent REAL",
            "cost REAL"
        )

        /** Every column a read maps back into a [MemoryNode], in one place. */
        const val NODE_COLUMNS =
            "id, subject, predicate, object, source, validFrom, validUntil, " +
                "supersededBy, accessibility, relevance, importance, uncertainty, novelty, consent, cost"
    }
}
