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
        store.record("SetMediaVolume", "accessibility", "unavailable", 5)
        store.record("Back", "accessibility", "success_reported", 3)

        assertEquals(2, store.eventCount())
        val json = store.exportJson()
        assertTrue(json.contains("session-test"))
        assertTrue(json.contains("abc123"))
        assertTrue(json.contains("SetMediaVolume"))
        assertFalse(json.contains("\"OpenApp\""))
        assertTrue(json.contains("not_independently_verified"))
        assertTrue(json.contains("success_reported"))
    }

    @Test
    fun `report excludes user payloads and free form error messages`() {
        val directory = Files.createTempDirectory("friday-action-privacy").toFile()
        val store = ActionTraceStore(directory, "0.2-test", 36)
        store.record("TypeText", "accessibility", "success_reported", 7)

        val report = store.exportText()
        assertTrue(report.contains("FRIDAY LIVE TEST REPORT"))
        assertTrue(report.contains("MACHINE_READABLE_JSON"))
        assertFalse(report.contains("password123"))
        assertFalse(report.contains("person@example.com"))
        assertFalse(report.contains("screen contents are recorded"))
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
