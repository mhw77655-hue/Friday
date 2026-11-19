package com.jarvis.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashJournalTest {
    @Test
    fun `crash report stores exception type and useful frames without local variables`() {
        val failure = IllegalStateException("model token=super-secret failed").apply {
            stackTrace = arrayOf(StackTraceElement("FridayEngine", "wake", "FridayEngine.kt", 42))
        }
        val report = CrashJournal.createRecord("main", failure, "2026-11-16T00:00:00Z", "com.jarvis.app")

        assertEquals("2026-11-16T00:00:00Z", report.getString("at_utc"))
        assertEquals("com.jarvis.app", report.getString("process"))
        val first = report.getJSONArray("exception_chain").getJSONObject(0)
        assertEquals("java.lang.IllegalStateException", first.getString("type"))
        assertTrue(first.getString("message").contains("[REDACTED]"))
        assertEquals("FridayEngine", first.getJSONArray("frames").getJSONObject(0).getString("class"))
    }

    @Test
    fun `each Android process receives a separate crash journal filename`() {
        val main = CrashJournal.fileNameFor("com.jarvis.app")
        val voice = CrashJournal.fileNameFor("com.jarvis.app:voice")
        val inference = CrashJournal.fileNameFor("com.jarvis.app:inference")

        assertTrue(main != voice)
        assertTrue(main != inference)
        assertTrue(voice != inference)
        assertTrue(voice.startsWith("last-crash-"))
        assertTrue(voice.endsWith(".json"))
        assertTrue(!voice.contains(":"))
    }

    @Test
    fun `repeated crashes in one process retain distinct event files`() {
        val first = CrashJournal.fileNameFor("com.jarvis.app:voice", "2026-11-19T10:00:00Z")
        val second = CrashJournal.fileNameFor("com.jarvis.app:voice", "2026-11-19T10:00:01Z")
        val otherProcess = CrashJournal.fileNameFor("com.jarvis.app:inference", "2026-11-19T10:00:00Z")

        assertTrue(first != second)
        assertTrue(first != otherProcess)
        assertTrue(second != otherProcess)
        assertTrue(first.endsWith(".json"))
        assertTrue(!first.contains(":"))
    }

    @Test
    fun `crash record preserves cause chain in order`() {
        val failure = IllegalStateException("outer", IllegalArgumentException("inner"))
        val chain = CrashJournal.createRecord("main", failure).getJSONArray("exception_chain")
        assertEquals(2, chain.length())
        assertEquals("java.lang.IllegalStateException", chain.getJSONObject(0).getString("type"))
        assertEquals("java.lang.IllegalArgumentException", chain.getJSONObject(1).getString("type"))
    }
}
