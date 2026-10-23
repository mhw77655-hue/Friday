package com.jarvis.app.onefriday.recorder

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * ONE-4-RECORDER-LEDGER: Flight recorder for every turn on the unified composition.
 * Appends structured records to a rolling local file. Fields are open key-value.
 */
class FlightRecorder(
    private val file: File,
    private val maxRecords: Int = 200
) {
    @Synchronized
    fun record(turnRecord: TurnRecord) {
        val records = readAll().toMutableList()
        records.add(turnRecord)
        // Keep rolling window
        while (records.size > maxRecords * 2) {
            records.removeAt(0)
        }
        writeAll(records.takeLast(maxRecords))
    }
    
    @Synchronized
    fun readAll(): List<TurnRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            if (content.isBlank()) return emptyList()
            val arr = JSONArray(content)
            (0 until arr.length()).map { i ->
                TurnRecord.fromJson(arr.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    @Synchronized
    fun readLast(n: Int): List<TurnRecord> = readAll().takeLast(n)
    
    @Synchronized
    fun shareLast200(): String {
        val last200 = readLast(200)
        val arr = JSONArray()
        last200.forEach { arr.put(it.toJson()) }
        return arr.toString(2)
    }
    
    @Synchronized
    fun exportLast200To(outFile: File): File {
        outFile.writeText(shareLast200())
        return outFile
    }
    
    private fun writeAll(records: List<TurnRecord>) {
        file.parentFile?.mkdirs()
        val arr = JSONArray()
        records.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }
}

data class TurnRecord(
    val id: String,
    val timestamp: Long,
    val input: String,
    val intent: String? = null,
    val admission: String? = null,
    val tierServed: String? = null,
    val contextBlocks: List<ContextBlock> = emptyList(),
    val requestSize: Int = 0,
    val latencyMs: Long = 0L,
    val degraded: Boolean = false,
    val reply: String = "",
    val postProcessing: List<String> = emptyList(),
    val extras: Map<String, Any?> = emptyMap()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("timestamp", timestamp)
        obj.put("input", input)
        if (intent != null) obj.put("intent", intent)
        if (admission != null) obj.put("admission", admission)
        if (tierServed != null) obj.put("tierServed", tierServed)
        val blocks = JSONArray()
        contextBlocks.forEach { blocks.put(it.toJson()) }
        if (blocks.length() > 0) obj.put("contextBlocks", blocks)
        obj.put("requestSize", requestSize)
        obj.put("latencyMs", latencyMs)
        obj.put("degraded", degraded)
        obj.put("reply", reply)
        val pp = JSONArray()
        postProcessing.forEach { pp.put(it) }
        if (pp.length() > 0) obj.put("postProcessing", pp)
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
        fun fromJson(obj: JSONObject): TurnRecord {
            val blocks = mutableListOf<ContextBlock>()
            val blocksArr = obj.optJSONArray("contextBlocks")
            if (blocksArr != null) {
                for (i in 0 until blocksArr.length()) {
                    blocks.add(ContextBlock.fromJson(blocksArr.getJSONObject(i)))
                }
            }
            val pp = mutableListOf<String>()
            val ppArr = obj.optJSONArray("postProcessing")
            if (ppArr != null) {
                for (i in 0 until ppArr.length()) pp.add(ppArr.getString(i))
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
            return TurnRecord(
                id = obj.optString("id"),
                timestamp = obj.optLong("timestamp"),
                input = obj.optString("input"),
                intent = if (obj.has("intent")) obj.optString("intent") else null,
                admission = if (obj.has("admission")) obj.optString("admission") else null,
                tierServed = if (obj.has("tierServed")) obj.optString("tierServed") else null,
                contextBlocks = blocks,
                requestSize = obj.optInt("requestSize"),
                latencyMs = obj.optLong("latencyMs"),
                degraded = obj.optBoolean("degraded"),
                reply = obj.optString("reply"),
                postProcessing = pp,
                extras = extras
            )
        }
    }
}

data class ContextBlock(
    val name: String,
    val source: String,
    val included: Boolean = true,
    val extras: Map<String, Any?> = emptyMap()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("name", name)
        obj.put("source", source)
        obj.put("included", included)
        val ex = JSONObject()
        extras.forEach { (k, v) -> ex.put(k, v) }
        if (ex.length() > 0) obj.put("extras", ex)
        return obj
    }
    companion object {
        fun fromJson(obj: JSONObject): ContextBlock {
            val extras = mutableMapOf<String, Any?>()
            val ex = obj.optJSONObject("extras")
            if (ex != null) {
                val keys = ex.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    extras[k] = ex.opt(k)
                }
            }
            return ContextBlock(
                name = obj.optString("name"),
                source = obj.optString("source"),
                included = obj.optBoolean("included", true),
                extras = extras
            )
        }
    }
}
