package com.jarvis.app.cognition.workspace

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * CONTINUITY-LAW: the durable local [ChangeLog] — one JSON object per line,
 * append-only, no network client.
 *
 * The shape is deliberately the same one [com.jarvis.app.memory.provenance.JsonlProvenanceLedger]
 * already uses (and that TurnTrace's store uses), because the requirement here
 * is the same one: a local-only append-only JSONL file is the right shape for a
 * durable local record that has to survive a restart, and the reader is the
 * honest test that it does.
 *
 * Restart proof: a NEW [FileChangeLog] over the same file reproduces identical
 * [entries], [entriesFor] and [lastChangeAt] — which matters beyond tidiness,
 * because the SLOW rate limit reads [lastChangeAt]. A gate whose interval
 * accounting lived only in memory would forget every accepted change on
 * restart and let a field be rewritten immediately after a process death.
 */
class FileChangeLog(private val file: File) : ChangeLog {

    /** The append-only local change-log file. */
    val changeLogFile: File get() = file

    override fun append(entry: ChangeLogEntry) {
        file.parentFile?.mkdirs()
        val line = entry.toJson().toString()
        synchronized(this) { file.appendText("$line\n", Charsets.UTF_8) }
    }

    override fun entries(): List<ChangeLogEntry> = readEntries()

    override fun entriesFor(target: String): List<ChangeLogEntry> =
        readEntries().filter { it.target == target }

    override fun lastChangeAt(target: String): Long? =
        entriesFor(target).maxByOrNull { it.timestamp }?.timestamp

    override fun latestValue(target: String): String? =
        entriesFor(target).maxByOrNull { it.timestamp }?.newValue

    /** Number of accepted-change lines recorded to the file. */
    fun count(): Long {
        if (!file.exists()) return 0L
        return file.useLines { it.count() }.toLong()
    }

    private fun readEntries(): List<ChangeLogEntry> {
        if (!file.exists()) return emptyList()
        val entries = mutableListOf<ChangeLogEntry>()
        synchronized(this) {
            file.useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    // A line this reader cannot parse is DROPPED, not repaired and
                    // not guessed at. The discipline that keeps that honest is the
                    // reader test: it asserts count() == entries().size, so a
                    // silently-skipped line fails the suite instead of quietly
                    // shortening an accepted-change history.
                    runCatching { ChangeLogEntry.fromJson(JSONObject(line)) }
                        .onSuccess { entries.add(it) }
                }
            }
        }
        return entries
    }

    private fun ChangeLogEntry.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("target", target)
        .put("layer", layer.name)
        .put("oldValue", oldValue ?: JSONObject.NULL)
        .put("newValue", newValue)
        .put("evidenceClaimIds", JSONArray(evidenceClaimIds))
        .put("acceptedClaimId", acceptedClaimId)
        .put("timestamp", timestamp)

    private companion object {
        fun ChangeLogEntry.fromJson(json: JSONObject): ChangeLogEntry = ChangeLogEntry(
            id = json.getString("id"),
            target = json.getString("target"),
            layer = ChangeLayer.valueOf(json.getString("layer")),
            oldValue = if (json.isNull("oldValue")) null else json.getString("oldValue"),
            newValue = json.getString("newValue"),
            evidenceClaimIds = json.getJSONArray("evidenceClaimIds")
                .let { a -> (0 until a.length()).map { a.getString(it) } },
            acceptedClaimId = json.getString("acceptedClaimId"),
            timestamp = json.getLong("timestamp")
        )
    }
}
