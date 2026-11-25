package ai.friday.assistant.approval

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TaskWritePathPolicyTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun allowsRegularFilesInsidePrivateRoot() {
        val root = temporary.newFolder("friday-files")
        val target = TaskWritePathPolicy.resolve(root, "workspace/report.json")
        assertEquals(root.canonicalFile.toPath().resolve("workspace/report.json").toFile(), target)
    }

    @Test
    fun rejectsParentTraversalOutsidePrivateRoot() {
        val root = temporary.newFolder("friday-files")
        assertTrue(runCatching {
            TaskWritePathPolicy.resolve(root, "../outside.txt")
        }.isFailure)
    }

    @Test
    fun rejectsAbsoluteTargetsOutsidePrivateRoot() {
        val root = temporary.newFolder("friday-files")
        val outside = temporary.newFile("outside.txt")
        assertTrue(runCatching {
            TaskWritePathPolicy.resolve(root, outside.absolutePath)
        }.isFailure)
    }

    @Test
    fun rejectsSymlinkThatRedirectsAnInRootWriteOutside() {
        val root = temporary.newFolder("friday-files")
        val outside = temporary.newFolder("outside")
        Files.createSymbolicLink(root.toPath().resolve("redirect"), outside.toPath())
        assertTrue(runCatching {
            TaskWritePathPolicy.resolve(root, "redirect/identity.json")
        }.isFailure)
    }

    @Test
    fun rejectsSymlinkFileEvenWhenItsTargetIsInsideRoot() {
        val root = temporary.newFolder("friday-files")
        val protected = root.toPath().resolve("identity.json")
        Files.write(protected, "protected".toByteArray())
        Files.createSymbolicLink(root.toPath().resolve("ordinary.txt"), protected)
        assertTrue(runCatching {
            TaskWritePathPolicy.resolve(root, "ordinary.txt")
        }.isFailure)
    }
}
