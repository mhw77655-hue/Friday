package ai.friday.assistant.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashJournalTest {
    @Test
    fun `crash report stores exception type and useful frames without local variables`() {
        val failure = IllegalStateException("model token=super-secret failed").apply {
            stackTrace = arrayOf(StackTraceElement("FridayEngine", "wake", "FridayEngine.kt", 42))
        }
        val report = CrashJournal.createRecord("main", failure, "2026-11-16T00:00:00Z", "ai.friday.assistant")

        assertEquals("2026-11-16T00:00:00Z", report.getString("at_utc"))
        assertEquals("ai.friday.assistant", report.getString("process"))
        val first = report.getJSONArray("exception_chain").getJSONObject(0)
        assertEquals("java.lang.IllegalStateException", first.getString("type"))
        assertTrue(first.getString("message").contains("[REDACTED]"))
        assertEquals("FridayEngine", first.getJSONArray("frames").getJSONObject(0).getString("class"))
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
