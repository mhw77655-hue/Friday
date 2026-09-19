package com.jarvis.app.humancore.store

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Internal Dialogue Log (§11): a bounded, append-only log of "what
 * JARVIS's internal narrative is" at key moments. Never a reasoning trace —
 * the module exists specifically to keep internal monologue OUT of the
 * Reasoning subsystem's task context and OUT of the user-facing response
 * verbatim (§11 Constraints).
 *
 * Each entry records the trigger reason, a short reflective text, a
 * reference to the Human Core state it was generated from (the snapshot at
 * write time), and whether it was ever surfaced (paraphrased, gated by the
 * Companion Behavior Orchestrator — never dumped raw).
 *
 * Writer discipline: the ONLY writer is the Internal Dialogue Engine
 * (§11 Responsibilities). Entries are generated on trigger (async, Integration
 * Pass — never on the critical path, §11 Lifecycle).
 *
 * Persistence: moderate tier (§21.1) — useful for continuity of
 * self-reflection but not safety-critical; pruned aggressively under storage
 * pressure before Relationship data is touched (§11 Failure handling).
 */
class DialogueLog(
    private val storage: StoragePort,
    private val clock: () -> Long
) {

    private val entries: MutableList<DialogueEntry> = mutableListOf()
    private val lock = Any()
    private val logLimit = 150

    fun load() {
        synchronized(lock) {
            entries.clear()
            val raw = storage.read(StoreKind.DIALOGUE)
            if (raw == null) return
            raw.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                parseEntry(line)?.let { entries.add(it) }
            }
            pruneIfNeeded()
        }
    }

    fun read(): List<DialogueEntry> = synchronized(lock) { entries.toList() }

    fun since(ts: Long): List<DialogueEntry> = synchronized(lock) { entries.filter { it.ts > ts } }

    fun size(): Int = synchronized(lock) { entries.size }

    /** The only write path — Internal Dialogue Engine only. */
    internal fun append(entry: DialogueEntry) {
        synchronized(lock) {
            entries.add(entry)
            pruneIfNeeded()
        }
        // Append outside the lock: storage.append is itself synchronized
        // per-store, and holding the in-memory lock during I/O would only
        // serialize readers for no benefit.
        storage.append(StoreKind.DIALOGUE, serializeEntry(entry))
    }

    /** Mark an entry as surfaced (paraphrased to the user, gated). */
    internal fun markSurfaced(ts: Long) {
        synchronized(lock) {
            val idx = entries.indexOfFirst { it.ts == ts }
            if (idx >= 0) {
                entries[idx] = entries[idx].copy(surfaced = true)
                persist()
            }
        }
    }

    /** Storage-pressure pruning: oldest entries are evicted first (§11). */
    internal fun prune(keep: Int = logLimit) {
        synchronized(lock) {
            if (entries.size > keep) {
                val removed = entries.size - keep
                entries.subList(0, removed).clear()
                persist()
            }
        }
    }

    internal fun persist() {
        synchronized(lock) {
            val content = entries.joinToString("\n") { serializeEntry(it) }
            if (content.isBlank()) {
                storage.write(StoreKind.DIALOGUE, "")
            } else {
                storage.write(StoreKind.DIALOGUE, content)
            }
        }
    }

    private fun pruneIfNeeded() {
        if (entries.size > logLimit) {
            val removed = entries.size - logLimit
            entries.subList(0, removed).clear()
            persist()
        }
    }

    private fun serializeEntry(e: DialogueEntry): String {
        return JSONObject()
            .put("ts", e.ts)
            .put("trigger", e.trigger)
            .put("text", e.text)
            .put("snapshotRef", e.snapshotRef?.let { it as Any } ?: JSONObject.NULL)
            .put("surfaced", e.surfaced)
            .toString()
    }

    private fun parseEntry(line: String): DialogueEntry? {
        return try {
            val o = JSONObject(line)
            DialogueEntry(
                ts = o.optLong("ts"),
                trigger = o.optString("trigger"),
                text = o.optString("text"),
                snapshotRef = if (o.isNull("snapshotRef")) null else o.optString("snapshotRef"),
                surfaced = o.optBoolean("surfaced")
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** One internal-dialogue entry (§11 owned data). */
data class DialogueEntry(
    val ts: Long,
    val trigger: String,
    val text: String,
    /** Reference (e.g. StateSnapshot identity) to the state this was generated from. */
    val snapshotRef: String?,
    /** True once paraphrased to the user through a gated companion behavior. */
    val surfaced: Boolean = false
)
