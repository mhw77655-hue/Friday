package com.jarvis.app.github

import org.json.JSONArray
import org.json.JSONObject

/**
 * Produces a bounded crash-history view for the read-only observer channel.
 *
 * Exception messages are deliberately omitted because platform/library
 * exceptions can embed paths, user input, URLs or other private values.
 * Class/method/file/line frames retain enough evidence to locate the failing
 * code without exporting the message or arbitrary process state.
 */
internal object DeviceObserverReport {
    const val MAX_RECORDS = 4

    fun crashSummary(records: List<JSONObject>, maxRecords: Int = MAX_RECORDS): JSONArray {
        require(maxRecords in 1..20) { "Crash summary limit must be between 1 and 20." }
        return JSONArray().apply {
            records.takeLast(maxRecords).forEach { record ->
                val chain = record.optJSONArray("exception_chain") ?: JSONArray()
                val safeChain = JSONArray()
                for (index in 0 until minOf(chain.length(), 4)) {
                    val item = chain.optJSONObject(index) ?: continue
                    val frames = item.optJSONArray("frames") ?: JSONArray()
                    val safeFrames = JSONArray()
                    for (frameIndex in 0 until minOf(frames.length(), 5)) {
                        val frame = frames.optJSONObject(frameIndex) ?: continue
                        safeFrames.put(
                            JSONObject()
                                .put("class", frame.optString("class").take(100))
                                .put("method", frame.optString("method").take(80))
                                .put("file", frame.optString("file").take(80))
                                .put("line", frame.optInt("line", -1))
                        )
                    }
                    safeChain.put(
                        JSONObject()
                            .put("type", item.optString("type").take(160))
                            .put("frames", safeFrames)
                    )
                }
                put(
                    JSONObject()
                        .put("at_utc", record.optString("at_utc").take(40))
                        .put("process", record.optString("process").take(120))
                        .put("exception_chain", safeChain)
                )
            }
        }
    }
}
