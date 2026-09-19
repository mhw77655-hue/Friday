package com.jarvis.app.selfrepair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourcePatchTest {

    private fun tmpDir(): File = File.createTempFile("patch-test", "").apply { delete(); mkdirs() }

    private val miniSource = """
        package com.example
        val original = "hello world"
        val other = "no change"
    """.trimIndent() + "\n"

    private fun writeFixture(dir: File, path: String, content: String = miniSource): File {
        val f = File(dir, path)
        f.parentFile?.mkdirs()
        f.writeText(content)
        return f
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
        val patch = SourcePatch(edits = listOf(PatchEdit("Target.kt", "val x = 1\n", "val x = 99\n")))
        assertTrue(patch.validate(root).isEmpty())
    }

    @Test
    fun `validate returns errors when find is missing`() {
        val root = tmpDir()
        writeFixture(root, "Target.kt", "val x = 1\n")
        val patch = SourcePatch(edits = listOf(PatchEdit("Target.kt", "val x = 999\n", "val x = 0\n")))
        val problems = patch.validate(root)
        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("FIND block not found"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `apply fails loudly when find is missing`() {
        val root = tmpDir()
        writeFixture(root, "Target.kt", "val x = 1\n")
        val patch = SourcePatch(edits = listOf(PatchEdit("Target.kt", "val x = 999\n", "val x = 0\n")))
        patch.applyTo(root)
    }

    @Test
    fun `apply writes replacement and creates backup`() {
        val root = tmpDir()
        writeFixture(root, "Target.kt", "val x = 1\n")
        val patch = SourcePatch(edits = listOf(PatchEdit("Target.kt", "val x = 1\n", "val x = 2\n")))
        patch.applyTo(root)
        assertEquals("val x = 2\n", File(root, "Target.kt").readText())
        assertTrue(File(root, "Target.kt.selfrepair.bak").exists())
        assertEquals("val x = 1\n", File(root, "Target.kt.selfrepair.bak").readText())
    }

    @Test
    fun `restoreFromBackup reverts the original content`() {
        val root = tmpDir()
        writeFixture(root, "Target.kt", "original")
        val patch = SourcePatch(edits = listOf(PatchEdit("Target.kt", "original", "patched")))
        patch.applyTo(root)
        assertEquals("patched", File(root, "Target.kt").readText())
        patch.restoreFromBackup(root)
        assertEquals("original", File(root, "Target.kt").readText())
        assertTrue(!File(root, "Target.kt.selfrepair.bak").exists())
    }

    @Test
    fun `validate returns error for missing file`() {
        val root = tmpDir()
        val patch = SourcePatch(edits = listOf(PatchEdit("nope.kt", "a", "b")))
        val problems = patch.validate(root)
        assertEquals(1, problems.size)
        assertTrue(problems[0].contains("file not found"))
    }

    @Test(expected = IllegalStateException::class)
    fun `missing patch fence throws`() {
        SourcePatch.parse("RATIONALE: no block here\nnothing in fences")
    }

    @Test
    fun `empty patch body is allowed as the only block`() {
        val patch = SourcePatch.parse("RATIONALE: nothing to do\n```patch\n\n```")
        assertEquals(0, patch.edits.size)
    }
}
