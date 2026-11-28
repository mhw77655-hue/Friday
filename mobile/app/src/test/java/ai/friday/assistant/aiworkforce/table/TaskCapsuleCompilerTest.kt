package ai.friday.assistant.aiworkforce.table

import ai.friday.assistant.memory.MemoryNode
import ai.friday.assistant.memory.RankedMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCapsuleCompilerTest {
    private val compiler = TaskCapsuleCompiler()

    private fun request(
        privateContextApproved: Boolean = false,
        maxContextChars: Int = 12_000
    ) = TaskCapsuleCompiler.Request(
        taskId = "task-friday-001",
        objective = "Improve Friday's local continuity.",
        instructions = "Use only the selected evidence.",
        acceptanceCriteria = listOf("Demonstrate a later behavior change."),
        constraints = listOf("Do not export the full conversation."),
        allowedTools = listOf("github.read_file"),
        outputSchema = """{"type":"object","required":["findings"]}""",
        privateContextApproved = privateContextApproved,
        maxContextChars = maxContextChars
    )

    @Test
    fun `only relevant eligible retrieved memories enter the capsule`() {
        val evidence = listOf(
            TaskCapsuleCompiler.MemoryEvidence("relevant", "Friday retained the same identity after restart.", "individual", "exp-1", 10L, 0.98, TaskCapsuleCompiler.Sensitivity.SANITIZED),
            TaskCapsuleCompiler.MemoryEvidence("unrelated", "The user asked about a recipe.", "chat", "exp-2", 11L, 0.05, TaskCapsuleCompiler.Sensitivity.PUBLIC),
            TaskCapsuleCompiler.MemoryEvidence("private", "A private conversation detail.", "chat", "exp-3", 12L, 0.99, TaskCapsuleCompiler.Sensitivity.PRIVATE),
            TaskCapsuleCompiler.MemoryEvidence("sensitive", "A secret credential.", "settings", "exp-4", 13L, 1.0, TaskCapsuleCompiler.Sensitivity.SENSITIVE)
        )
        val capsule = compiler.compile(request(), evidence)
        assertEquals(listOf("relevant"), capsule.memories.map { it.id })
        assertFalse(capsule.context.contains("private conversation detail"))
        assertFalse(capsule.context.contains("secret credential"))
        assertTrue(capsule.context.contains("provenance=exp-1"))
    }

    @Test
    fun `production ranked memory results compile with provenance and explicit sensitivity classification`() {
        val node = MemoryNode(
            id = "memory-identity-1",
            subject = "Friday",
            predicate = "identity",
            `object` = "persists across restart",
            source = "owner-confirmed",
            validFrom = 123L
        )
        val ranked = RankedMemory(node, 0.93f, RankedMemory.MatchSource.HAMMING_SEED)
        val capsule = compiler.compileRetrieved(
            request(),
            listOf(ranked),
            classifySensitivity = { TaskCapsuleCompiler.Sensitivity.SANITIZED }
        )
        assertEquals(listOf("memory-identity-1"), capsule.memories.map { it.id })
        assertEquals("memory-identity-1", capsule.memories.single().provenanceId)
        assertTrue(capsule.context.contains("Friday identity persists across restart"))
    }

    @Test
    fun `private memory requires explicit task scoped approval and sensitive memory is never included`() {
        val evidence = listOf(
            TaskCapsuleCompiler.MemoryEvidence("private", "Private but approved task detail.", "memory", "p-1", 10L, 0.9, TaskCapsuleCompiler.Sensitivity.PRIVATE),
            TaskCapsuleCompiler.MemoryEvidence("sensitive", "Never export this.", "vault", "s-1", 11L, 1.0, TaskCapsuleCompiler.Sensitivity.SENSITIVE)
        )
        val denied = compiler.compile(request(), evidence)
        val approved = compiler.compile(request(privateContextApproved = true), evidence)
        assertTrue(denied.memories.isEmpty())
        assertEquals(listOf("private"), approved.memories.map { it.id })
        assertFalse(approved.context.contains("Never export this"))
    }

    @Test
    fun `credentials are redacted from objective instructions and selected memory`() {
        val request = request().copy(
            objective = "Use api_key=sk-1234567890abcdef123456 to fix this.",
            instructions = "Authorization: Bearer abc.def.ghi"
        )
        val evidence = listOf(
            TaskCapsuleCompiler.MemoryEvidence(
                "m1", "token=ghp_abcdefghijklmnop123456", "memory", "p1", 1L, 1.0,
                TaskCapsuleCompiler.Sensitivity.SANITIZED
            )
        )
        val capsule = compiler.compile(request, evidence)
        val serialized = listOf(capsule.objective, capsule.instructions, capsule.context).joinToString("\n")
        assertFalse(serialized.contains("sk-1234567890abcdef123456"))
        assertFalse(serialized.contains("abc.def.ghi"))
        assertFalse(serialized.contains("ghp_abcdefghijklmnop123456"))
        assertTrue(serialized.contains("[REDACTED"))
    }

    @Test
    fun `capsule digest is deterministic and changes when selected evidence changes`() {
        val a = TaskCapsuleCompiler.MemoryEvidence("m1", "Relevant fact A.", "memory", "p1", 1L, 0.9, TaskCapsuleCompiler.Sensitivity.PUBLIC)
        val b = a.copy(content = "Relevant fact B.")
        val first = compiler.compile(request(), listOf(a))
        val repeated = compiler.compile(request(), listOf(a))
        val changed = compiler.compile(request(), listOf(b))
        assertEquals(first.digestSha256, repeated.digestSha256)
        assertNotEquals(first.digestSha256, changed.digestSha256)
        assertEquals(64, first.digestSha256.length)
    }

    @Test
    fun `capsule verification rejects post compilation tampering`() {
        val capsule = compiler.compile(request(), emptyList())
        assertTrue(compiler.verify(capsule))
        assertFalse(compiler.verify(capsule.copy(context = "injected unrelated memory")))
        assertFalse(compiler.verify(capsule.copy(allowedTools = listOf("device.shell"))))
    }

    @Test
    fun `context budget is hard and artifact bytes are never embedded`() {
        val evidence = (1..8).map {
            TaskCapsuleCompiler.MemoryEvidence(
                "m$it", "Evidence $it " + "x".repeat(500), "memory", "p$it", it.toLong(), 1.0 - it / 100.0,
                TaskCapsuleCompiler.Sensitivity.PUBLIC
            )
        }
        val artifact = TaskCapsuleCompiler.ArtifactReference(
            id = "src-main", sha256 = "a".repeat(64), mediaType = "text/plain",
            sourceRevision = "abc123", label = "Source file reference"
        )
        val capsule = compiler.compile(request(maxContextChars = 400), evidence, listOf(artifact))
        assertTrue(capsule.context.length <= 400)
        assertTrue(capsule.memories.isEmpty())
        assertTrue(capsule.context.contains("sha256=" + "a".repeat(64)) || capsule.artifacts.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tool names must be explicit allowlisted identifiers`() {
        compiler.compile(request().copy(allowedTools = listOf("*")), emptyList())
    }
}
