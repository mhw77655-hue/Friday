package com.jarvis.app.memory.provenance

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * FORGET-PROPAGATION: durable fingerprint of a forgotten memory.
 *
 * The content of a forgotten memory is never persisted raw. Two stable,
 * one-way fingerprints are derived instead (all token/character work stays
 * local — the only API used is the JDK's SHA-256, so this package keeps its
 * no-network invariant intact in both code and comments):
 *
 *  - [contentHash]: a SHA-256 over the full normalized content's sorted token
 *    set — one stable identity for a forgotten memory;
 *  - [tokenHashes]: one SHA-256 per ≥4-char token, used by
 *    [TombstoneStore.matchesAny] to block ANY incidental re-learning — a later
 *    consolidation pass or live turn carrying a tombstoned token is suppressed,
 *    so the forgotten fact cannot be re-created by ingesting the fragment again.
 *
 * [TombstoneStore.remember] lifts a tombstone by token-family overlap: an
 * explicit "remember X" re-asserts the content, so every tombstone sharing a
 * token with X yields — the incidental-relearning guard must not fight a
 * deliberate re-statement (AC4 lifts the whole family, e.g. when one value was
 * forgotten in two languages).
 *
 * Normalization lowercases and splits on non-letter/digit runs; Arabic tokens
 * (Egyptian colloquial) are kept verbatim, so both English and Arabic forget
 * resolve onto one fingerprint space (AC2 covers the Arabic path).
 */
object TombstoneFingerprint {
    fun tokens(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 4 }
            .distinct()

    fun sha256Hex(data: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun contentHash(text: String): String =
        sha256Hex(tokens(text).sorted().joinToString("\u0001"))

    fun tokenHashes(text: String): List<String> =
        tokens(text).map { sha256Hex(it) }

    fun tokenSet(text: String): Set<String> = tokens(text).toSet()
}

/**
 * FORGET-PROPAGATION: durable registry of forgotten memories.
 *
 * [tombstone] appends one record per forgotten memory; [matchesAny] blocks
 * re-learning; [remember] clears a tombstone (explicit "remember X" re-opens
 * the memory to learning — AC4). [isTombstoned]/[isTombstonedId] answer the
 * exact-carrier queries used by the live engine's write-back guard.
 *
 * Storage is a local JSON-Lines file, appended + flushed (grow-only, like the
 * ledger and trace store). Because only hashes are written, a byte scan of the
 * tombstone file itself can never surface the forgotten plaintext — the file
 * can be included in the forget byte-scan corpus without polluting it. The only
 * rewrite ever performed is [remember], an explicit user action; automatic runs
 * never touch history.
 */
interface TombstoneStore {
    val enabled: Boolean

    fun setEnabled(enabled: Boolean)

    /** Record [sourceId] + the fingerprints of [content] as forgotten. */
    fun tombstone(sourceId: String, content: String)

    /** Lift the tombstones overlapping [content]'s token family. Returns count cleared. */
    fun remember(content: String): Int

    /** Whether the exact source memory [sourceId] is tombstoned. */
    fun isTombstoned(sourceId: String): Boolean

    /** Whether ANY tombstoned content token appears in [text] (re-learning guard). */
    fun matchesAny(text: String): Boolean

    /** Number of tombstone records persisted. */
    fun count(): Long

    /** The local file backing this registry (byte-scan surface). */
    val tombstoneFile: File?
}

/**
 * FORGET-PROPAGATION: local-file JSON-Lines [TombstoneStore].
 *
 * Each record persists only the fingerprints: sourceId, contentHash, tokenHashes
 * and the forgottenAt wall-clock. Nothing here touches a network or socket, and
 * the file this writes is a local File on this device (the same local-only
 * invariant the ledger and trace store already hold).
 */
class JsonlTombstoneStore(
    private val file: File,
    initiallyEnabled: Boolean = true
) : TombstoneStore {

    @Volatile
    private var _enabled: Boolean = initiallyEnabled

    override val enabled: Boolean get() = _enabled

    override fun setEnabled(enabled: Boolean) {
        _enabled = enabled
    }

    override val tombstoneFile: File get() = file

    private data class TombstoneRecord(
        val sourceId: String,
        val contentHash: String,
        val tokenHashes: List<String>,
        val forgottenAt: Long
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("sourceId", sourceId)
            .put("contentHash", contentHash)
            .put("tokenHashes", JSONArray(tokenHashes))
            .put("forgottenAt", forgottenAt)

        companion object {
            fun fromJson(json: JSONObject): TombstoneRecord = TombstoneRecord(
                sourceId = json.getString("sourceId"),
                contentHash = json.getString("contentHash"),
                tokenHashes = json.getJSONArray("tokenHashes")
                    .let { a -> (0 until a.length()).map { a.getString(it) } },
                forgottenAt = json.getLong("forgottenAt")
            )
        }
    }

    override fun tombstone(sourceId: String, content: String) {
        if (!_enabled) return
        val record = TombstoneRecord(
            sourceId = sourceId,
            contentHash = TombstoneFingerprint.contentHash(content),
            tokenHashes = TombstoneFingerprint.tokenHashes(content),
            forgottenAt = System.currentTimeMillis()
        )
        synchronized(this) {
            if (!_enabled) return
            file.parentFile?.mkdirs()
            file.appendText("${record.toJson()}\n", Charsets.UTF_8)
        }
    }

    override fun remember(content: String): Int {
        val lifted = TombstoneFingerprint.tokenSet(content)
        if (lifted.isEmpty()) return 0
        val liftedHashes = lifted.map { TombstoneFingerprint.sha256Hex(it) }.toSet()
        synchronized(this) {
            val all = readRecords()
            val kept = all.filter { rec -> rec.tokenHashes.none { it in liftedHashes } }
            val cleared = all.size - kept.size
            if (cleared > 0) rewrite(kept)
            return cleared
        }
    }

    override fun isTombstoned(sourceId: String): Boolean =
        readRecords().any { it.sourceId == sourceId }

    override fun matchesAny(text: String): Boolean {
        val candidates = TombstoneFingerprint.tokenSet(text)
        if (candidates.isEmpty()) return false
        val records = readRecords()
        if (records.isEmpty()) return false
        val stored = records.flatMap { it.tokenHashes }.toSet()
        return candidates.any { TombstoneFingerprint.sha256Hex(it) in stored }
    }

    override fun count(): Long {
        if (!file.exists()) return 0L
        return file.useLines { it.count() }.toLong()
    }

    private fun readRecords(): List<TombstoneRecord> {
        if (!file.exists()) return emptyList()
        val records = mutableListOf<TombstoneRecord>()
        synchronized(this) {
            file.useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    runCatching { TombstoneRecord.fromJson(JSONObject(line)) }
                        .onSuccess { records.add(it) }
                }
            }
        }
        return records
    }

    /** Rewrite the whole file (only the explicit-remember path does this). */
    private fun rewrite(records: List<TombstoneRecord>) {
        file.writeText("", Charsets.UTF_8)
        records.forEach { file.appendText("${it.toJson()}\n", Charsets.UTF_8) }
    }
}