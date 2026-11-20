package com.jarvis.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ActionTraceStoreTest {
    @Test
    fun `records bounded action timeline and marks executor success unverified`() {
        val directory = Files.createTempDirectory("friday-action-journal").toFile()
        val store = ActionTraceStore(directory, "test-build", 35, sourceRevision = "abc123", maxEvents = 2, sessionId = "session-test")

        store.record("OpenApp", "accessibility", "success_reported", 12)
        store.record("SetMediaVolume", "accessibility", "verified", 5, verificationState = "verified")
        store.record("Back", "accessibility", "success_reported", 3)

        assertEquals(2, store.eventCount())
        val json = store.exportJson()
        assertTrue(json.contains("session-test"))
        assertTrue(json.contains("abc123"))
        assertTrue(json.contains("SetMediaVolume"))
        assertFalse(json.contains("\"OpenApp\""))
        assertTrue(json.contains("not_independently_verified"))
        assertTrue(json.contains("verification_state\\\": \\\"verified"))
        assertTrue(json.contains("success_reported"))
    }

    @Test
    fun `report excludes user payloads and free form error messages`() {
        val directory = Files.createTempDirectory("friday-action-privacy").toFile()
        val store = ActionTraceStore(directory, "0.2-test", 36)
        store.record("TypeText", "accessibility", "success_reported", 7)

        val capabilities = org.json.JSONObject()
            .put("microphone_permission", true)
            .put("accessibility_service_enabled", false)
            .put("accessibility_service_connected", false)
            .put("write_settings_permission", true)
            .put("notification_listener_enabled", false)
            .put("shizuku_status", "Shizuku not running")
        val report = store.exportText(capabilities)
        assertTrue(report.contains("FRIDAY LIVE TEST REPORT"))
        assertTrue(report.contains("DEVICE CAPABILITIES AT EXPORT"))
        assertTrue(report.contains("microphone_permission: true"))
        assertTrue(report.contains("shizuku_status: Shizuku not running"))
        assertTrue(report.contains("\"device_capabilities\""))
        assertTrue(report.contains("MACHINE_READABLE_JSON"))
        assertFalse(report.contains("password123"))
        assertFalse(report.contains("person@example.com"))
        assertFalse(report.contains("screen contents are recorded"))
    }

    @Test
    fun `crash report projection preserves stack evidence but excludes exception messages`() {
        val crash = org.json.JSONObject()
            .put("at_utc", "2026-11-20T20:00:00Z")
            .put("process", "com.jarvis.app")
            .put("thread", "main")
            .put("exception_chain", org.json.JSONArray().put(
                org.json.JSONObject()
                    .put("type", "java.lang.IllegalStateException")
                    .put("message", "private-token=do-not-export")
                    .put("frames", org.json.JSONArray().put(
                        org.json.JSONObject()
                            .put("class", "com.jarvis.app.MainActivity")
                            .put("method", "onCreate")
                            .put("file", "MainActivity.kt")
                            .put("line", 42)
                    ))
            ))

        val safe = sanitizedCrashSummary(crash).toString()
        assertTrue(safe.contains("java.lang.IllegalStateException"))
        assertTrue(safe.contains("MainActivity.kt"))
        assertTrue(safe.contains("onCreate"))
        assertFalse(safe.contains("private-token"))
        assertFalse(safe.contains("do-not-export"))
        assertFalse(safe.contains("message"))
    }

    @Test
    fun `unknown verification labels cannot be promoted to verified`() {
        val directory = Files.createTempDirectory("friday-verification-state").toFile()
        val store = ActionTraceStore(directory, "test", 35)
        store.record("Back", "accessibility", "verified", 1, verificationState = "trust-me")

        val json = store.exportJson()
        assertTrue(json.contains("not_independently_verified"))
        assertFalse(json.contains("trust-me"))
    }

    @Test
    fun `clear removes stored traces`() {
        val directory = Files.createTempDirectory("friday-action-clear").toFile()
        val store = ActionTraceStore(directory, "test", 35)
        store.record("Back", "accessibility", "success_reported", 1)
        store.clear()
        assertEquals(0, store.eventCount())
        assertTrue(store.exportText().contains("No phone-control actions"))
    }
}
