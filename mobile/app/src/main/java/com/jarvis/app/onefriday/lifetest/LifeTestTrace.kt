package com.jarvis.app.onefriday.lifetest

import org.json.JSONArray
import org.json.JSONObject

/**
 * ONE-3-LIFETEST: Open trace/record for evidence pack.
 */
data class TraceEntry(
    val type: String,
    val timestamp: Long,
    val data: Map<String, Any?>
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("type", type)
        obj.put("timestamp", timestamp)
        val d = JSONObject()
        data.forEach { (k, v) ->
            when (v) {
                is JSONObject -> d.put(k, v)
                is JSONArray -> d.put(k, v)
                is Map<*, *> -> d.put(k, JSONObject(v.mapKeys { it.key.toString() }))
                is List<*> -> d.put(k, JSONArray(v))
                else -> d.put(k, v)
            }
        }
        obj.put("data", d)
        return obj
    }
}

class LifeTestTrace {
    private val entries = mutableListOf<TraceEntry>()
    
    fun record(type: String, timestamp: Long = System.currentTimeMillis(), data: Map<String, Any?> = emptyMap()) {
        entries.add(TraceEntry(type, timestamp, data))
    }
    
    fun getEntries(): List<TraceEntry> = entries.toList()
    
    fun toJson(): JSONArray {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        return arr
    }
    
    fun findAll(type: String): List<TraceEntry> = entries.filter { it.type == type }
}
