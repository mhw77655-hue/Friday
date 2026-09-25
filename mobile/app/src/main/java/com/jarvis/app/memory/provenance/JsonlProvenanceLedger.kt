package com.jarvis.app.memory.provenance

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * PROVENANCE-LEDGER: append-only, local-file JSON Lines [ProvenanceLedger].
 *
 * Every [record] appends one line (one JSON object) and flushes, so the file is
 * durable and grow-only; there is no rewrite of history. Disabling the ledger
 * ([setEnabled]false]) makes [record] a no-op — the file length is then provably
 * unchanged across the same real creation points (AC4 negative control), proving
 * the wiring is not a stub.
 *
 * Local-only by construction: the target is a [File] on this device; there is
 * no network hop anywhere in this class, and the same local-only
 * invariant is enforced on every file in this package by the story's static
 * no-network greps.
 *
 * [derivedFrom]/[sourcesOf] read the file back (simulated restart: a new
 * [JsonlProvenanceLedger] over the same file reproduces identical answers —
 * AC3).
 */
class JsonlProvenanceLedger(
    private val file: File,
    initiallyEnabled: Boolean = true
) : ProvenanceLedger {

    @Volatile
    private var _enabled: Boolean = initiallyEnabled

    override val enabled: Boolean get() = _enabled

    override fun setEnabled(enabled: Boolean) {
        _enabled = enabled
    }

    /** The append-only local provenance file. */
    val ledgerFile: File get() = file

    override fun record(derivedId: String, kind: ProvenanceKind, sourceIds: List<String>) {
        if (!_enabled) return
        file.parentFile?.mkdirs()
        val line = ProvenanceEntry(derivedId, kind, sourceIds).toJson().toString()
        synchronized(this) {
            // Double-guard the enabled flag (same pattern as JsonlTurnTraceStore):
            // a disable racing an in-flight record must not slip a line into the file.
            if (!_enabled) return
            file.appendText("$line\n", Charsets.UTF_8)
        }
    }

    override fun derivedFrom(sourceId: String): List<DerivedRef> =
        readEntries()
            .filter { it.sourceIds.contains(sourceId) }
            .map { DerivedRef(it.derivedId, it.kind) }
            .distinct()

    override fun sourcesOf(derivedId: String): Set<String> =
        readEntries()
            .filter { it.derivedId == derivedId }
            .flatMap { it.sourceIds }
            .toSet()

    /** Number of provenance lines recorded to the file. */
    fun count(): Long {
        if (!file.exists()) return 0L
        return file.useLines { it.count() }.toLong()
    }

    override fun redact(derivedId: String): Int = synchronized(this) {
        val all = readEntries()
        val kept = all.filter { it.derivedId != derivedId }
        val removed = all.size - kept.size
        if (removed > 0) rewriteEntries(kept)
        removed
    }

    override fun redactSource(derivedId: String, sourceId: String): Int = synchronized(this) {
        val all = readEntries()
        var rewritten = 0
        val kept = all.map { entry ->
            if (entry.derivedId == derivedId && entry.sourceIds.contains(sourceId)) {
                rewritten++
                entry.copy(sourceIds = entry.sourceIds.filter { it != sourceId })
            } else {
                entry
            }
        }.filter { it.sourceIds.isNotEmpty() }
        if (rewritten > 0) rewriteEntries(kept)
        rewritten
    }

    /**
     * FORGET-PROPAGATION: destructive rewrite compaction. Normal recording is
     * append-only; this is ONLY reached through redact/redactSource, which the
     * forget path calls exclusively for an explicit user forget. Keeping the
     * surviving entries byte-identical (same toJson) means no unrelated
     * provenance line changes.
     */
    private fun rewriteEntries(entries: List<ProvenanceEntry>) {
        file.writeText("", Charsets.UTF_8)
        entries.forEach { file.appendText("${it.toJson()}\n", Charsets.UTF_8) }
    }

    private data class ProvenanceEntry(
        val derivedId: String,
        val kind: ProvenanceKind,
        val sourceIds: List<String>
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("derivedId", derivedId)
            .put("kind", kind.name)
            .put("sourceIds", JSONArray(sourceIds))

        companion object {
            fun fromJson(json: JSONObject): ProvenanceEntry = ProvenanceEntry(
                derivedId = json.getString("derivedId"),
                kind = ProvenanceKind.valueOf(json.getString("kind")),
                sourceIds = json.getJSONArray("sourceIds")
                    .let { a -> (0 until a.length()).map { a.getString(it) } }
            )
        }
    }

    /** Ordered list of every provenance entry read back from the file. */
    private fun readEntries(): List<ProvenanceEntry> {
        if (!file.exists()) return emptyList()
        val entries = mutableListOf<ProvenanceEntry>()
        synchronized(this) {
            file.useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    runCatching { ProvenanceEntry.fromJson(JSONObject(line)) }
                        .onSuccess { entries.add(it) }
                }
            }
        }
        return entries
    }
}