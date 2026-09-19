package com.jarvis.app.memory

import android.content.Context
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

    private val helper = object : SQLiteOpenHelper(context, dbName, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE nodes (" +
                    "id TEXT PRIMARY KEY, " +
                    "subject TEXT NOT NULL, " +
                    "predicate TEXT NOT NULL, " +
                    "object TEXT NOT NULL, " +
                    "source TEXT NOT NULL DEFAULT '', " +
                    "validFrom INTEGER NOT NULL, " +
                    "validUntil INTEGER)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_nodes_subject_pred ON nodes(subject, predicate)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_nodes_valid ON nodes(validFrom, validUntil)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // No migrations yet.
        }
    }

    private val db: SQLiteDatabase = helper.writableDatabase

    override fun addFact(subject: String, predicate: String, `object`: String, source: String) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE nodes SET validUntil = ? WHERE subject = ? AND predicate = ? AND validUntil IS NULL",
                arrayOf<Any>(now, subject, predicate)
            )
            db.execSQL(
                "INSERT INTO nodes (id, subject, predicate, object, source, validFrom, validUntil) VALUES (?,?,?,?,?,?,NULL)",
                arrayOf<Any>(java.util.UUID.randomUUID().toString(), subject, predicate, `object`, source, now)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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
            "SELECT id, subject, predicate, object, source, validFrom, validUntil FROM nodes " +
                "WHERE $where ORDER BY validFrom ASC", args
        )
    }

    override fun getHistory(subject: String, predicate: String): List<MemoryNode> {
        return queryNodes(
            "SELECT id, subject, predicate, object, source, validFrom, validUntil FROM nodes " +
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

    private fun queryNodes(sql: String, args: Array<out Any>): List<MemoryNode> {
        val out = mutableListOf<MemoryNode>()
        db.rawQuery(sql, args.map { it.toString() }.toTypedArray()).use { c ->
            while (c.moveToNext()) {
                val validUntilIdx = c.getColumnIndexOrThrow("validUntil")
                val validUntil = if (c.isNull(validUntilIdx)) null else c.getLong(validUntilIdx)
                out.add(
                    MemoryNode(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        subject = c.getString(c.getColumnIndexOrThrow("subject")),
                        predicate = c.getString(c.getColumnIndexOrThrow("predicate")),
                        `object` = c.getString(c.getColumnIndexOrThrow("object")),
                        source = c.getString(c.getColumnIndexOrThrow("source")),
                        validFrom = c.getLong(c.getColumnIndexOrThrow("validFrom")),
                        validUntil = validUntil
                    )
                )
            }
        }
        return out
    }

    override fun close() {
        try {
            db.close()
            helper.close()
        } catch (_: Throwable) {
        }
    }
}
