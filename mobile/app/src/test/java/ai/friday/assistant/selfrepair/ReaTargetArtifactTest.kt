package ai.friday.assistant.selfrepair

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaTargetArtifactTest {

    @Test
    fun `artifact verifies exact APK bytes and detects replacement`() {
        val dir = createTempDir(prefix = "rea-target-")
        try {
            val apk = File(dir, "candidate.apk").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val target = ReaTargetArtifact.fromBuiltApk(
                file = apk,
                sourceRevision = "a".repeat(40),
                packageName = "ai.friday.assistant"
            )
            assertTrue(target.verifyUnchanged())

            apk.writeBytes(byteArrayOf(4, 3, 2, 1))
            assertFalse(target.verifyUnchanged())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects source directory as an REA target`() {
        val dir = createTempDir(prefix = "rea-source-")
        try {
            ReaTargetArtifact.fromBuiltApk(
                file = dir,
                sourceRevision = "a".repeat(40),
                packageName = "ai.friday.assistant"
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects abbreviated source revision`() {
        val dir = createTempDir(prefix = "rea-target-")
        try {
            val apk = File(dir, "candidate.apk").apply { writeBytes(byteArrayOf(1)) }
            ReaTargetArtifact.fromBuiltApk(
                file = apk,
                sourceRevision = "abc123",
                packageName = "ai.friday.assistant"
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
