package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.model.FakeModelBackend
import ai.friday.assistant.model.ModelManager
import ai.friday.assistant.model.ModelTier
import ai.friday.assistant.model.OrganRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectiveTaskDecomposerTest {
    private fun manager(response: String): Pair<ModelManager, FakeModelBackend> {
        val backend = FakeModelBackend(cannedResponse = { response })
        val manager = ModelManager(
            context = null,
            scope = CoroutineScope(Dispatchers.Default),
            backend = backend,
            autoSweep = false
        )
        return manager to backend
    }

    private fun parentTask() = JSONObject()
        .put("task_id", "objective-a-phase-0-assess")
        .put("objective_id", "A")
        .put("phase_id", 0)
        .put("goal", "Assess the canonical phase exit evidence.")
        .put("acceptance_criteria", "A verified evidence map exists.")
        .put("scope_markdown", "Read the canonical plan and inspect the relevant repository evidence.")

    @Test fun `local resident model produces validated bounded work items`() = runBlocking {
        val response = """{
            "work_items": [{
                "id": "inventory-source",
                "goal": "Inventory the source paths relevant to phase zero.",
                "acceptance_criteria": ["A path inventory with source revision is recorded."],
                "evidence_needed": ["Repository tree and commit SHA."],
                "risk": "LOW",
                "reversible": true,
                "requires_owner_approval": false,
                "dependencies": [],
                "dependency_state": "UNRESOLVED"
            }]
        }"""
        val (manager, backend) = manager(response)
        manager.request(OrganRole.RESIDENT, "planner-test", "friday-resident:latest")

        val result = ObjectiveTaskDecomposer(manager).decompose(parentTask())
        assertEquals("friday-resident:latest", result.modelId)
        assertEquals(1, result.items.length())
        assertTrue(backend.lastPrompt?.contains("You are Friday's local development planner") == true)
        assertEquals(1, backend.generateCount)
    }

    @Test fun `local model output cannot authorize high-risk work by itself`() = runBlocking {
        val response = """{
            "work_items": [{
                "id": "privileged-operation",
                "goal": "Perform a privileged operation.",
                "acceptance_criteria": ["The operation completes."],
                "evidence_needed": ["A success result."],
                "risk": "HIGH",
                "reversible": true,
                "requires_owner_approval": false,
                "dependencies": [],
                "dependency_state": "UNRESOLVED"
            }]
        }"""
        val (manager, _) = manager(response)
        manager.request(OrganRole.RESIDENT, "planner-test", "friday-resident:latest")
        val error = runCatching { ObjectiveTaskDecomposer(manager).decompose(parentTask()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test fun `decomposition refuses to run without a loaded resident model`() = runBlocking {
        val (manager, _) = manager("{}")
        val error = runCatching { ObjectiveTaskDecomposer(manager).decompose(parentTask()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
