package com.jarvis.app.diagnostics

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.app.Application
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashJournalStorageTest {
    private val application: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun repeatedCrashEventsSurviveAndAcknowledgingOnePreservesTheOther() {
        val directory = File(application.cacheDir, "crash-journal-test-${System.nanoTime()}")
        try {
            val first = CrashJournal.createRecord(
                "main",
                IllegalStateException("first"),
                atUtc = "2026-11-19T10:00:00Z",
                processName = "com.jarvis.app"
            )
            val second = CrashJournal.createRecord(
                "main",
                IllegalStateException("second"),
                atUtc = "2026-11-19T10:00:01Z",
                processName = "com.jarvis.app"
            )

            CrashJournal.writeRecord(directory, first)
            CrashJournal.writeRecord(directory, second)

            val persisted = CrashJournal.readAllFrom(directory)
            assertEquals(2, persisted.size)
            assertTrue(CrashJournal.acknowledgeFrom(directory, first))

            val remaining = CrashJournal.readAllFrom(directory)
            assertEquals(1, remaining.size)
            assertEquals(second.getString("at_utc"), remaining.single().getString("at_utc"))
            assertTrue(CrashJournal.acknowledgeFrom(directory, second))
            assertTrue(CrashJournal.readAllFrom(directory).isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun acknowledgementCannotDeleteARecordWithDifferentContent() {
        val directory = File(application.cacheDir, "crash-journal-mismatch-${System.nanoTime()}")
        try {
            val stored = CrashJournal.createRecord(
                "main",
                IllegalStateException("stored"),
                atUtc = "2026-11-19T11:00:00Z",
                processName = "com.jarvis.app:voice"
            )
            val different = CrashJournal.createRecord(
                "main",
                IllegalStateException("different"),
                atUtc = "2026-11-19T11:00:00Z",
                processName = "com.jarvis.app:voice"
            )
            CrashJournal.writeRecord(directory, stored)

            assertFalse(CrashJournal.acknowledgeFrom(directory, different))
            assertEquals(1, CrashJournal.readAllFrom(directory).size)
        } finally {
            directory.deleteRecursively()
        }
    }
}
