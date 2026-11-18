package com.jarvis.app.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashRepairBridgeTest {
    @Test
    fun `crash becomes a bounded GitHub repair request with stable identity`() {
        val crash = JSONObject()
            .put("at_utc", "2026-11-18T10:00:00Z")
            .put("process", "com.jarvis.app")
            .put("thread", "main")
            .put("exception_chain", JSONArray().put(
                JSONObject()
                    .put("type", "java.lang.IllegalStateException")
                    .put("message", "engine failed")
                    .put("frames", JSONArray().put(
                        JSONObject()
                            .put("class", "com.jarvis.app.JarvisEngine")
                            .put("method", "wake")
                            .put("file", "JarvisEngine.kt")
                            .put("line", 42)
                    ))
            ))

        val first = CrashRepairBridge.createDraft(crash, "2026-11-18T10:01:00Z")
        val second = CrashRepairBridge.createDraft(crash, "2026-11-18T10:02:00Z")

        assertNotNull(first)
        assertEquals(first!!.requestId, second!!.requestId)
        assertEquals(first.logicalKey, second.logicalKey)
        assertTrue(first.requestId!!.startsWith("crash-"))
        assertTrue(first.evidence.single().contains("JarvisEngine.wake"))
        assertTrue(first.acceptanceCriteria.any { it.contains("regression test") })
        assertEquals("friday-crash-bridge-v1", first.privacyReviewedBy)
    }

    @Test
    fun `crash evidence excludes email and bearer credentials`() {
        val crash = JSONObject()
            .put("at_utc", "2026-11-18T10:00:00Z")
            .put("process", "com.jarvis.app")
            .put("thread", "main")
            .put("exception_chain", JSONArray().put(
                JSONObject()
                    .put("type", "java.lang.IllegalStateException")
                    .put("message", "failed for person@example.com Bearer abc.def.ghi token=secret-value")
                    .put("frames", JSONArray())
            ))

        val draft = CrashRepairBridge.createDraft(crash, "2026-11-18T10:01:00Z")!!
        val evidence = draft.evidence.single()
        assertFalse(evidence.contains("person@example.com"))
        assertFalse(evidence.contains("abc.def.ghi"))
        assertFalse(evidence.contains("secret-value"))
        assertTrue(evidence.contains("[REDACTED_EMAIL]"))
        assertTrue(evidence.length <= 7_800)
    }

    @Test
    fun `empty crash journal is not submitted as a repair request`() {
        assertNull(CrashRepairBridge.createDraft(JSONObject().put("exception_chain", JSONArray())))
        assertNull(CrashRepairBridge.createDraft(JSONObject()))
    }
}
