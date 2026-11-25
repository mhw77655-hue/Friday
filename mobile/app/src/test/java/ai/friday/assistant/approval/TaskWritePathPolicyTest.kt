package ai.friday.assistant.approval

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskWritePathPolicyTest {
    @Test
    fun `relative target resolves inside Friday private root`() {
        val root = Files.createTempDirectory("friday-write-root").toFile()
        val target = TaskWritePathPolicy.resolve(root, "workspace/notes.txt")

        assertTrue(target.toPath().startsWith(root.canonicalFile.toPath()))
        assertFalse(target.exists())
    }

    @Test
    fun `parent traversal and external absolute paths are rejected`() {
        val root = Files.createTempDirectory("friday-write-root").toFile()
        val outside = Files.createTempDirectory("friday-outside").resolve("escape.txt").toFile()

        assertRejected { TaskWritePathPolicy.resolve(root, "../escape.txt") }
        assertRejected { TaskWritePathPolicy.resolve(root, outside.absolutePath) }
    }

    @Test
    fun `symbolic link components are rejected`() {
        val root = Files.createTempDirectory("friday-write-root")
        val outside = Files.createTempDirectory("friday-outside")
        Files.createSymbolicLink(root.resolve("escape"), outside)

        assertRejected { TaskWritePathPolicy.resolve(root.toFile(), "escape/pwned.txt") }
    }

    private fun assertRejected(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("unsafe write path must be rejected", rejected)
    }
}
