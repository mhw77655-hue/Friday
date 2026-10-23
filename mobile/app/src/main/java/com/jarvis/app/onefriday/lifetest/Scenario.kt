package com.jarvis.app.onefriday.lifetest

import org.json.JSONArray
import org.json.JSONObject

/**
 * ONE-3-LIFETEST: open data scenario model. Steps are data fields, not an enum.
 */
data class Scenario(
    val id: String,
    val name: String = "",
    val expect: String = "pass",
    val steps: List<ScenarioStep> = emptyList(),
    val controls: Map<String, Boolean> = emptyMap()
) {
    companion object {
        fun fromJson(json: JSONObject): Scenario {
            val stepsArr = json.optJSONArray("steps") ?: JSONArray()
            val steps = mutableListOf<ScenarioStep>()
            for (i in 0 until stepsArr.length()) {
                val s = stepsArr.optJSONObject(i) ?: continue
                steps.add(ScenarioStep.fromJson(s))
            }
            val controls = mutableMapOf<String, Boolean>()
            val c = json.optJSONObject("controls")
            if (c != null) {
                val it = c.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    controls[k] = c.optBoolean(k)
                }
            }
            return Scenario(
                id = json.optString("id", json.optString("name", "unknown")),
                name = json.optString("name", json.optString("id", "")),
                expect = json.optString("expect", "pass"),
                steps = steps,
                controls = controls
            )
        }
    }
}

data class ScenarioStep(
    val type: String,
    val fields: Map<String, Any?>
) {
    companion object {
        fun fromJson(json: JSONObject): ScenarioStep {
            // Open data fields - take all keys
            val map = mutableMapOf<String, Any?>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.opt(k)
            }
            return ScenarioStep(type = json.optString("type", ""), fields = map)
        }
    }
    
    fun getString(key: String, default: String = ""): String = fields[key]?.toString() ?: default
    fun getLong(key: String, default: Long = 0L): Long = when (val v = fields[key]) {
        is Number -> v.toLong()
        else -> v?.toString()?.toLongOrNull() ?: default
    }
    fun getInt(key: String, default: Int = 0): Int = when (val v = fields[key]) {
        is Number -> v.toInt()
        else -> v?.toString()?.toIntOrNull() ?: default
    }
    fun getBoolean(key: String, default: Boolean = false): Boolean = when (val v = fields[key]) {
        is Boolean -> v
        else -> v?.toString()?.toBoolean() ?: default
    }
}
