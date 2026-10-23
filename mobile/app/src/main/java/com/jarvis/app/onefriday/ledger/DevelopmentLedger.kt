package com.jarvis.app.onefriday.ledger

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * ONE-4-RECORDER-LEDGER: Development Ledger - durable append-only record.
 * What FRIDAY became because of what happened. No closed list of entry kinds.
 * Writes go through existing continuity law/change log when applicable.
 */
class DevelopmentLedger(
    private val file: File
) {
    @Synchronized
    fun append(entry: LedgerEntry): LedgerEntry {
        val entries = readAll().toMutableList()
        entries.add(entry)
        writeAll(entries)
        return entry
    }
    
    @Synchronized
    fun readAll(): List<LedgerEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            if (content.isBlank()) return emptyList()
            val arr = JSONArray(content)
            (0 until arr.length()).map { i ->
                LedgerEntry.fromJson(arr.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    @Synchronized
    fun getSeededEntries(): List<LedgerEntry> = readAll().filter { it.extras["seeded"] == true }
    
    @Synchronized
    fun getDevelopedEntries(): List<LedgerEntry> = readAll().filter { it.extras["seeded"] != true }
    
    private fun writeAll(entries: List<LedgerEntry>) {
        file.parentFile?.mkdirs()
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }
}

data class LedgerEntry(
    val id: String,
    val timestamp: Long,
    val whatChanged: String,
    val affectsBehaviorOrCognition: String,
    val episodeIds: List<String> = emptyList(),
    val strengthOfEvidence: Double = 0.0,
    val formedAt: Long = System.currentTimeMillis(),
    val lastReinforcedAt: Long = System.currentTimeMillis(),
    val reversible: Boolean = true,
    val reversibleHow: String? = null,
    val remainingUncertainty: String = "",
    val extras: Map<String, Any?> = emptyMap()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("timestamp", timestamp)
        obj.put("whatChanged", whatChanged)
        obj.put("affectsBehaviorOrCognition", affectsBehaviorOrCognition)
        val eps = JSONArray()
        episodeIds.forEach { eps.put(it) }
        obj.put("episodeIds", eps)
        obj.put("strengthOfEvidence", strengthOfEvidence)
        obj.put("formedAt", formedAt)
        obj.put("lastReinforcedAt", lastReinforcedAt)
        obj.put("reversible", reversible)
        if (reversibleHow != null) obj.put("reversibleHow", reversibleHow)
        obj.put("remainingUncertainty", remainingUncertainty)
        val ex = JSONObject()
        extras.forEach { (k, v) -> 
            when (v) {
                is Map<*, *> -> ex.put(k, JSONObject(v.mapKeys { it.key.toString() }))
                is List<*> -> ex.put(k, JSONArray(v))
                else -> ex.put(k, v)
            }
        }
        if (ex.length() > 0) obj.put("extras", ex)
        return obj
    }
    
    companion object {
        fun fromJson(obj: JSONObject): LedgerEntry {
            val eps = mutableListOf<String>()
            val epsArr = obj.optJSONArray("episodeIds")
            if (epsArr != null) {
                for (i in 0 until epsArr.length()) eps.add(epsArr.getString(i))
            }
            val extras = mutableMapOf<String, Any?>()
            val ex = obj.optJSONObject("extras")
            if (ex != null) {
                val keys = ex.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    extras[k] = ex.opt(k)
                }
            }
            return LedgerEntry(
                id = obj.optString("id"),
                timestamp = obj.optLong("timestamp"),
                whatChanged = obj.optString("whatChanged"),
                affectsBehaviorOrCognition = obj.optString("affectsBehaviorOrCognition"),
                episodeIds = eps,
                strengthOfEvidence = obj.optDouble("strengthOfEvidence", 0.0),
                formedAt = obj.optLong("formedAt"),
                lastReinforcedAt = obj.optLong("lastReinforcedAt"),
                reversible = obj.optBoolean("reversible", true),
                reversibleHow = if (obj.has("reversibleHow")) obj.optString("reversibleHow") else null,
                remainingUncertainty = obj.optString("remainingUncertainty"),
                extras = extras
            )
        }
    }
}
