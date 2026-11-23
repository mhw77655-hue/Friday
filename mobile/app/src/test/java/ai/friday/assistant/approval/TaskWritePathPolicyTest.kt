package ai.friday.assistant.approval

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TaskWritePathPolicyTest {
    private val temporary = TemporaryFolder()

    @Test
    fun allowsRegularFilesInsidePrivateRoot() {
        val root = temporary.newFolder("friday-files")
        val target = TaskWritePathPolicy.resolve(root, "workspace/report.json")
        assertEquals(root.canonicalFile.toPath().resolve("workspace/report.json").toFile(), target)
    }

    @Test
    fun rejectsParentTraversalOutsidePrivateRoot() {
        val root = temporary.newFolder("friday-files")
        val rejected = runCatching {
            TaskWritePathPolicy.resolve(root, "../outside.txt")
        }
        assertTrue(rejected.isFailure)
    }

    @Test
    fun rejectsAbsoluteTargetsOutsidePrivateRoot() {
        val root = temporary.newFolder("friday-files")
        val outside = temporary.newFile("outside.txt")
        val rejected = runCatching {
            TaskWritePathPolicy.resolve(root, outside.absolutePath)
        }
        assertTrue(rejected.isFailure)
    }

    @Test
    fun rejectsSymlinkThatRedirectsAnInRootWriteOutside() {
        val root = temporary.newFolder("friday-files")
        val outside = temporary.newFolder("outside")
        val link = root.toPath().resolve("redirect")
        Files.createSymbolicLink(link, outside.toPath())

        val rejected = runCatching {
            TaskWritePathPolicy.resolve(root, "redirect/identity.json")
        }
        assertTrue(rejected.isFailure)
    }

    @Test
    fun rejectsSymlinkFileEvenWhenItsTargetIsInsideRoot() {
        val root = temporary.newFolder("friday-files")
        val protected = root.toPath().resolve("identity.json")
        Files.writeString(protected, "protected")
        Files.createSymbolicLink(root.toPath().resolve("ordinary.txt"), protected)

        val rejected = runCatching {
            TaskWritePathPolicy.resolve(root, "ordinary.txt")
        }
        assertTrue(rejected.isFailure)
    }
}
