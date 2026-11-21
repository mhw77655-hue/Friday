package ai.friday.assistant.symbiosis

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageDirectorySafetyTest {
    @Test
    fun createsMissingInferenceDirectoryBeforeUse() {
        val root = Files.createTempDirectory("friday-storage-test").toFile()
        try {
            val preferred = File(root, "files/inference")
            val fallback = File(root, "files")
            val selected = ensureStorageDirectory(preferred, fallback)
            assertEquals(preferred.canonicalFile, selected.canonicalFile)
            assertTrue(preferred.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun fallsBackToExistingPrivateDirectoryWhenPreferredCannotBeCreated() {
        val root = Files.createTempDirectory("friday-storage-fallback").toFile()
        try {
            val fallback = File(root, "files").apply { mkdirs() }
            // A regular file at the requested directory path makes mkdirs fail.
            val blocker = File(root, "inference").apply { writeText("not a directory") }
            val selected = ensureStorageDirectory(blocker, fallback)
            assertEquals(fallback.canonicalFile, selected.canonicalFile)
            assertTrue(selected.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }
}
