package com.jarvis.app.selfrepair

import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePatchTest {

    private fun tmpDir(): File = File.createTempFile("patch-test", "").apply { delete(); mkdirs() }

    private val miniSource = """
        package com.example
        val original = "hello world"
        val other = "no change"
    """.trimIndent() + "\n"

    private fun writeFixture(dir: File, path: String, content: String = miniSource): File {
        val file = File(dir, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    @Test
    fun `parse extracts rationale and edits`() {
        val text = """
            RATIONALE: this patch fixes things.
            ```patch
            FILE: src/Target.kt
            FIND:
            val original = "hello world"
            REPLACE:
            val original = "repaired"
            ```
        """.trimIndent()
        val patch = SourcePatch.parse(text)
        assertEquals("this patch fixes things.", patch.rationale)
        assertEquals(1, patch.edits.size)
        assertEquals("src/Target.kt", patch.edits[0].file)
        assertTrue(patch.edits[0].find.contains("hello world"))
        assertTrue(patch.edits[0].replace.contains("repaired"))
    }

    @Test
    fun `multiple edits parsed in order`() {
        val text = """
            RATIONALE: multi-edit
            ```patch
            FILE: a.kt
            FIND:
            AAA
            REPLACE:
            BBB
            ```
            ```patch
            FILE: b.kt
            FIND:
            CCC
            REPLACE:
            DDD
            ```
        """.trimIndent()
        val patch = SourcePatch.parse(text)
        assertEquals(2, patch.edits.size)
        assertEquals("a.kt", patch.edits[0].file)
        assertEquals("b.kt", patch.edits[1].file)
    }

    @Test
    fun `validate passes when find matches exactly once`() {
        val root = tmpDir()
        writeFixture(root, "Target.kt", "val x = 1\nval y = 2\n")
        val patch = SourcePatch(listOf(PatchEdit("Target.kt", "val x = 1\n", "val x = 99\n")))
        assertTrue(patch.validate(root).isEmpty())
    }

    @Test
    fun `validate returns errors when find is missing`() {
        val root = tmpDir()
        writeFixture(root, "Target.kt", "val x = 1\n")
        val patch = SourcePatch(listOf(PatchEdit("Target.kt", "val x = 999\n", "val x = 0\n")))
        val problems = patch.validate(root)
        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("FIND block not found"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `apply fails loudly when find is missing`() {
        val root = tmpDir()
        writeFixture(root, "Target.kt", "val x = 1\n")
        SourcePatch(listOf(PatchEdit("Target.kt", "val x = 999\n", "val x = 0\n"))).applyTo(root)
    }

    @Test
    fun `apply writes replacement and creates transaction backup`() {
        val root = tmpDir()
        writeFixture(root, "Target.kt", "val x = 1\n")
        val patch = SourcePatch(listOf(PatchEdit("Target.kt", "val x = 1\n", "val x = 2\n")))
        patch.applyTo(root)
        val backup = File(root, "Target.kt.selfrepair.${patch.transactionId}.bak")
        assertEquals("val x = 2\n", File(root, "Target.kt").readText())
        assertTrue(backup.isFile)
        assertEquals("val x = 1\n", backup.readText())
    }

    @Test
    fun `restoreFromBackup reverts only its transaction`() {
        val root = tmpDir()
        writeFixture(root, "Target.kt", "original")
        val patch = SourcePatch(listOf(PatchEdit("Target.kt", "original", "patched")))
        patch.applyTo(root)
        assertEquals("patched", File(root, "Target.kt").readText())
        patch.restoreFromBackup(root)
        assertEquals("original", File(root, "Target.kt").readText())
        assertFalse(File(root, "Target.kt.selfrepair.${patch.transactionId}.bak").exists())
    }

    @Test
    fun `successive patches retain independent rollback points`() {
        val root = tmpDir()
        writeFixture(root, "Target.kt", "one")
        val first = SourcePatch(listOf(PatchEdit("Target.kt", "one", "two")))
        val second = SourcePatch(listOf(PatchEdit("Target.kt", "two", "three")))
        first.applyTo(root)
        second.applyTo(root)
        assertEquals("three\n", File(root, "Target.kt").readText())
        second.restoreFromBackup(root)
        assertEquals("one\n", File(root, "Target.kt").readText())
        first.restoreFromBackup(root)
        assertEquals("one", File(root, "Target.kt").readText())
    }

    @Test
    fun `parent traversal is rejected without touching outside file`() {
        val root = tmpDir()
        val outside = File(root.parentFile, "outside-${UUID.randomUUID()}.kt")
        outside.writeText("owner data")
        try {
            val patch = SourcePatch(listOf(PatchEdit("../${outside.name}", "owner data", "modified")))
            assertTrue(patch.validate(root).any { it.contains("unsafe path") })
            assertTrue(runCatching { patch.applyTo(root) }.isFailure)
            assertEquals("owner data", outside.readText())
        } finally {
            outside.delete()
        }
    }

    @Test
    fun `absolute path is rejected`() {
        val root = tmpDir()
        val outside = File.createTempFile("outside-patch", ".kt")
        try {
            val patch = SourcePatch(listOf(PatchEdit(outside.absolutePath, "a", "b")))
            assertTrue(patch.validate(root).any { it.contains("unsafe path") })
        } finally {
            outside.delete()
        }
    }

    @Test
    fun `symlink escaping repository root is rejected`() {
        val root = tmpDir()
        val outside = File.createTempFile("outside-symlink", ".kt")
        try {
            val link = File(root, "linked.kt")
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            val patch = SourcePatch(listOf(PatchEdit("linked.kt", "a", "b")))
            assertTrue(patch.validate(root).any { it.contains("unsafe path") })
        } finally {
            outside.delete()
            root.deleteRecursively()
        }
    }

    @Test
    fun `invalid edit prevents every file from being changed`() {
        val root = tmpDir()
        writeFixture(root, "valid.kt", "before")
        val patch = SourcePatch(listOf(
            PatchEdit("valid.kt", "before", "after"),
            PatchEdit("../escape.kt", "x", "y")
        ))
        assertTrue(runCatching { patch.applyTo(root) }.isFailure)
        assertEquals("before", File(root, "valid.kt").readText())
    }

    @Test
    fun `validate returns error for missing file`() {
        val root = tmpDir()
        val patch = SourcePatch(listOf(PatchEdit("nope.kt", "a", "b")))
        val problems = patch.validate(root)
        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("file not found"))
    }

    @Test(expected = IllegalStateException::class)
    fun `missing patch fence throws`() {
        SourcePatch.parse("RATIONALE: no block here\nnothing in fences")
    }

    @Test(expected = IllegalStateException::class)
    fun `empty patch is rejected`() {
        SourcePatch.parse("RATIONALE: nothing to do\n```patch\n\n```")
    }
}
