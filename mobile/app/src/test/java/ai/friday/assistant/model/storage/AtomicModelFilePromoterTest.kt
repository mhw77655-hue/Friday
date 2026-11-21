package ai.friday.assistant.model.storage

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomicModelFilePromoterTest {
    private fun tempDir(): File = File.createTempFile("friday-model-promote-", ".dir").apply {
        delete()
        mkdirs()
    }

    @Test
    fun `validated replacement atomically supersedes an existing model`() {
        val directory = tempDir()
        val target = File(directory, "model.gguf").apply { writeText("old-model") }
        val partial = File(directory, "model.gguf.part").apply { writeText("new-model") }

        AtomicModelFilePromoter.replace(partial, target)

        assertEquals("new-model", target.readText())
        assertFalse(partial.exists())
        directory.deleteRecursively()
    }

    @Test
    fun `failed promotion leaves previous target untouched`() {
        val directory = tempDir()
        val target = File(directory, "model.gguf").apply {
            mkdirs()
            File(this, "sentinel").writeText("old-model")
        }
        val partial = File(directory, "model.gguf.part").apply { writeText("new-model") }

        try {
            AtomicModelFilePromoter.replace(partial, target)
            throw AssertionError("Expected replacement of a non-empty directory to fail")
        } catch (_: IOException) {
            assertTrue(File(target, "sentinel").isFile)
            assertEquals("old-model", File(target, "sentinel").readText())
            assertTrue(partial.isFile)
        }
        directory.deleteRecursively()
    }
}
