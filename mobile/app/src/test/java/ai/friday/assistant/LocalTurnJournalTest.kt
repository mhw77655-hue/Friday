package ai.friday.assistant

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTurnJournalTest {
    @Test
    fun writesUserAndFridayTurnsToBoundedPrivateJournal() {
        val directory = Files.createTempDirectory("friday-turn-journal").toFile()
        try {
            LocalTurnJournal.initialize(directory)
            LocalTurnJournal.logTurn(false, "hello\nFriday")
            LocalTurnJournal.logTurn(true, "Hello back")
            assertTrue("queued journal writes should flush", LocalTurnJournal.flushForTesting())

            val journal = File(directory, "turns.jsonl")
            val content = journal.readText()
            assertTrue(journal.isFile)
            assertTrue(content.contains("\"speaker\":\"user\""))
            assertTrue(content.contains("\"speaker\":\"friday\""))
            assertTrue(content.contains("hello\\nFriday"))
            assertTrue(content.contains("Hello back"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
