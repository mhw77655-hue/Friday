package com.jarvis.app.github

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceObserverReportTest {
    @Test
    fun crashSummaryKeepsUsefulFramesButNeverExportsExceptionMessages() {
        val privateMessage = "failed for user@example.com at /data/user/0/private/path"
        val records = listOf(
            JSONObject()
                .put("at_utc", "2026-11-19T20:00:00Z")
                .put("process", "com.jarvis.app:inference")
                .put(
                    "exception_chain",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "java.lang.IllegalStateException")
                            .put("message", privateMessage)
                            .put(
                                "frames",
                                JSONArray().put(
                                    JSONObject()
                                        .put("class", "com.jarvis.app.model.InferenceService")
                                        .put("method", "onCreate")
                                        .put("file", "InferenceService.kt")
                                        .put("line", 95)
                                )
                            )
                    )
                )
        )

        val result = DeviceObserverReport.crashSummary(records)
        val encoded = result.toString()

        assertEquals(1, result.length())
        assertTrue(encoded.contains("java.lang.IllegalStateException"))
        assertTrue(encoded.contains("InferenceService.kt"))
        assertTrue(encoded.contains("onCreate"))
        assertFalse(encoded.contains(privateMessage))
        assertFalse(encoded.contains("user@example.com"))
        assertFalse(result.getJSONObject(0).getJSONArray("exception_chain")
            .getJSONObject(0).has("message"))
    }

    @Test
    fun crashSummaryIsBoundedToTheMostRecentRecords() {
        val records = (1..8).map { index ->
            JSONObject()
                .put("at_utc", "2026-11-19T20:00:0${index}Z")
                .put("process", "main")
                .put("exception_chain", JSONArray())
        }

        val result = DeviceObserverReport.crashSummary(records)

        assertEquals(4, result.length())
        assertEquals("2026-11-19T20:00:05Z", result.getJSONObject(0).getString("at_utc"))
        assertEquals("2026-11-19T20:00:08Z", result.getJSONObject(3).getString("at_utc"))
    }
}
