package com.jarvis.app.humancore.store

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.fallback.FallbackIdentity
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Identity Kernel + Values System stores (§3, §4), physically bundled
 * into one record (the spec explicitly allows this to simplify backup while
 * keeping them logically distinct records) but logically two: the immutable
 * identity and its values.
 *
 * Write discipline — this is the entire point of the store:
 *  - The ONLY writer is [com.jarvis.app.humancore.mod.IdentityRevisionHandler]
 *    through [applyRevision], which requires a revision token that no
 *    message-handling path can obtain.
 *  - No conversational code path may write here (§3 Constraints, §0.13):
 *    requests like "pretend you are X" are handled by the Consistency Guard
 *    declining in-character, never by touching this store.
 *  - Revisions are versioned and appended to an audit log; the current
 *    snapshot must always match the newest audit entry (§3 Synchronization).
 *  - Never deleted, only versioned (old versions retained for rollback).
 *
 * Failure handling: if the store is unreadable, fall back to
 * [FallbackIdentity.record] and flag a severity-1 self-repair event — this is
 * the one Human Core store whose loss must alert rather than silently
 * degrade (§3, §21.1 Critical tier).
 */
class IdentityStore(
    private val storage: StoragePort,
    private val clock: () -> Long,
    private val bus: StateBus = StateBus
) {
    @Volatile private var current: IdentityRecord = FallbackIdentity.record
    private val auditLog: MutableList<IdentityRevisionEntry> = mutableListOf()
    private val logLimit = 50

    fun load() {
        val raw = storage.read(StoreKind.IDENTITY)
        if (raw == null) {
            // Cold start: begin from the documented fallback, do not persist
            // until the first real revision so a later wipe always recovers
            // the same honest baseline.
            current = FallbackIdentity.record
            return
        }
        val parsed = parse(raw)
        if (parsed == null) {
            // Corrupt/unreadable Identity store — severity-1 event, fall back.
            // Identity loss must alert rather than silently degrade (§3, §21.1;
            // HUMAN_CORE_AUDIT m-13). The publish is best-effort: a broken bus
            // must never prevent the fallback.
            try {
                bus.publish(
                    HcEvent.IdentityIntegrity(
                        ts = clock(), reason = "identity store corrupt; fell back to fallback baseline",
                        severity = 1
                    )
                )
            } catch (_: Exception) {
                // fallback stands even if auditing is unavailable
            }
            current = FallbackIdentity.record
            return
        }
        current = parsed.first
        auditLog.addAll(parsed.second)
    }

    fun read(): IdentityRecord = current

    fun auditLog(): List<IdentityRevisionEntry> = auditLog.toList()

    fun currentVersion(): Int = current.version

    fun isFallback(): Boolean = current.version == FallbackIdentity.record.version &&
        current.name == FallbackIdentity.record.name &&
        current.selfDescription == FallbackIdentity.record.selfDescription

    /**
     * The only write path in the entire subsystem. Requires a revision token
     * ([IdentityRevisionAuth]) that only the developer/admin revision entry
     * point can construct — structurally unreachable from parsed user text.
     */
    internal fun applyRevision(newRecord: IdentityRecord, entry: IdentityRevisionEntry, auth: IdentityRevisionAuth) {
        if (newRecord.version != current.version + 1) {
            // Version-gate: prevents out-of-order/overlapping revisions from
            // clobbering history. A revision must be based on the current state.
            throw IdentityRevisionException(
                "revision version ${newRecord.version} not based on current version ${current.version}"
            )
        }
        auditLog.add(entry)
        while (auditLog.size > logLimit) auditLog.removeAt(0)
        current = newRecord
        persist()
    }

    internal fun persist() {
        storage.write(StoreKind.IDENTITY, serialize(current, auditLog))
    }

    private fun serialize(record: IdentityRecord, log: List<IdentityRevisionEntry>): String {
        val root = JSONObject()
        root.put("name", record.name)
        root.put("selfDescription", record.selfDescription)
        root.put("version", record.version)
        root.put("lastRevisionEpochMs", record.lastRevisionEpochMs)

        val valuesArr = JSONArray()
        record.values.forEach { v ->
            valuesArr.put(
                JSONObject()
                    .put("key", v.key)
                    .put("statement", v.statement)
                    .put("severity", v.severity.name)
                    .put("justification", v.justification)
            )
        }
        root.put("values", valuesArr)

        val boundariesArr = JSONArray()
        record.boundaries.forEach { b -> boundariesArr.put(b) }
        root.put("boundaries", boundariesArr)

        val auditArr = JSONArray()
        log.forEach { e ->
            auditArr.put(
                JSONObject()
                    .put("ts", e.ts)
                    .put("trigger", e.trigger)
                    .put("authorizer", e.authorizer)
                    .put("summary", e.summary)
            )
        }
        root.put("audit", auditArr)
        return root.toString()
    }

    private fun parse(json: String): Pair<IdentityRecord, List<IdentityRevisionEntry>>? {
        return try {
            val root = JSONObject(json)
            val valuesArr = root.optJSONArray("values") ?: JSONArray()
            val values = mutableListOf<ValueRecord>()
            for (i in 0 until valuesArr.length()) {
                val v = valuesArr.getJSONObject(i)
                values.add(
                    ValueRecord(
                        key = v.optString("key"),
                        statement = v.optString("statement"),
                        severity = ValueSeverity.valueOf(v.optString("severity", ValueSeverity.HARD_BOUNDARY.name)),
                        justification = v.optString("justification")
                    )
                )
            }
            val boundariesArr = root.optJSONArray("boundaries") ?: JSONArray()
            val boundaries = mutableListOf<String>()
            for (i in 0 until boundariesArr.length()) boundaries.add(boundariesArr.getString(i))

            val auditArr = root.optJSONArray("audit") ?: JSONArray()
            val log = mutableListOf<IdentityRevisionEntry>()
            for (i in 0 until auditArr.length()) {
                val e = auditArr.getJSONObject(i)
                log.add(
                    IdentityRevisionEntry(
                        ts = e.optLong("ts"),
                        trigger = e.optString("trigger"),
                        authorizer = e.optString("authorizer"),
                        summary = e.optString("summary")
                    )
                )
            }
            IdentityRecord(
                name = root.optString("name", FallbackIdentity.name),
                selfDescription = root.optString("selfDescription", FallbackIdentity.selfDescription),
                values = values.ifEmpty { FallbackIdentity.values },
                boundaries = boundaries.ifEmpty { FallbackIdentity.boundaries },
                version = root.optInt("version", 1),
                lastRevisionEpochMs = root.optLong("lastRevisionEpochMs", 0L)
            ) to log
        } catch (e: Exception) {
            null
        }
    }
}

/** Data of the immutable identity record (§3 owned data). */
data class IdentityRecord(
    val name: String,
    val selfDescription: String,
    val values: List<ValueRecord>,
    /** Non-negotiable behavioral boundaries (§3). */
    val boundaries: List<String>,
    val version: Int,
    val lastRevisionEpochMs: Long
)

/**
 * One value commitment (§4). Severity determines how the Consistency Guard
 * treats a violation: a HARD_BOUNDARY blocks the message; a STRONG_PREFERENCE
 * flags it for softening.
 */
data class ValueRecord(
    val key: String,
    val statement: String,
    val severity: ValueSeverity,
    /** Short human-readable justification, for self-reflection, not display. */
    val justification: String
)

enum class ValueSeverity { HARD_BOUNDARY, STRONG_PREFERENCE }

/** One entry in the identity-revision audit log (§3). Append-only, bounded. */
data class IdentityRevisionEntry(
    val ts: Long,
    val trigger: String,
    val authorizer: String,
    val summary: String
)

/**
 * Token proving a revision event is authorized. Only the developer/admin
 * revision entry point can construct it; no message-handling path ever holds
 * one (§0.13). Carries the audit fields so the reason is never lost.
 */
class IdentityRevisionAuth internal constructor(
    val trigger: String,
    val authorizer: String
) {
    companion object {
        /** The single sanctioned way to authorize an identity revision. */
        internal fun authorize(trigger: String, authorizer: String): IdentityRevisionAuth =
            IdentityRevisionAuth(trigger, authorizer)
    }
}

class IdentityRevisionException(message: String) : Exception(message)
