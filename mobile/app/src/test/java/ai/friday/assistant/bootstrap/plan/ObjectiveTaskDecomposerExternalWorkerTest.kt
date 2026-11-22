package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.aiworkforce.AiWorkerConsultationPort
import ai.friday.assistant.aiworkforce.AiWorkerReply
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ObjectiveTaskDecomposerExternalWorkerTest {
    private class FakeWorkers(
        private val replies: List<AiWorkerReply>
    ) : AiWorkerConsultationPort {
        override suspend fun consultAvailable(prompt: String): List<AiWorkerReply> {
            check(prompt.contains("Canonical scope:"))
            return replies
        }
    }

    private val validResponse = """
        {
          "work_items": [{
            "id": "inspect-plan-ledger",
            "goal": "Inspect the current plan ledger and record verified state.",
            "acceptance_criteria": ["The ledger state is inspected and summarized."],
            "evidence_needed": ["A source-backed ledger inspection record."],
            "risk": "LOW",
            "reversible": true,
            "requires_owner_approval": false,
            "dependencies": [],
            "dependency_state": "UNRESOLVED"
          }]
        }
    """.trimIndent()

    private val parent = JSONObject()
        .put("task_id", "test-task")
        .put("objective_id", "A")
        .put("phase_id", 1)
        .put("goal", "Assess the phase")
        .put("acceptance_criteria", "Verified evidence exists")
        .put("scope_markdown", "Canonical objective scope")

    @Test fun `uses authorized subscription worker when local model is unavailable`() = runBlocking {
        val decomposer = ObjectiveTaskDecomposer(
            modelManager = null,
            externalWorkers = FakeWorkers(
                listOf(AiWorkerReply("OPENAI_CODEX", validResponse, "TERMUX_NATIVE"))
            )
        )
        val result = decomposer.decompose(parent)
        assertEquals("subscription-worker:OPENAI_CODEX@TERMUX_NATIVE", result.modelId)
        assertEquals(1, result.items.length())
    }

    @Test fun `rejects invalid external worker output`() = runBlocking {
        val decomposer = ObjectiveTaskDecomposer(
            modelManager = null,
            externalWorkers = FakeWorkers(
                listOf(AiWorkerReply("OPENAI_CODEX", """{"work_items":[]}""", "TERMUX_NATIVE"))
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { decomposer.decompose(parent) }
        }
    }
}
