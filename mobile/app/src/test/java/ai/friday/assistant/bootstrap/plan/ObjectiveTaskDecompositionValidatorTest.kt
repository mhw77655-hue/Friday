package ai.friday.assistant.bootstrap.plan

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test

class ObjectiveTaskDecompositionValidatorTest {
    private fun item(
        id: String,
        risk: String = "LOW",
        reversible: Boolean = true,
        approval: Boolean = false,
        dependencies: List<String> = emptyList(),
        dependencyState: String = "UNRESOLVED"
    ) = JSONObject()
        .put("id", id)
        .put("goal", "Collect and verify bounded evidence for $id.")
        .put("acceptance_criteria", JSONArray().put("A verifiable evidence record exists."))
        .put("evidence_needed", JSONArray().put("Source and timestamp are recorded."))
        .put("risk", risk)
        .put("reversible", reversible)
        .put("requires_owner_approval", approval)
        .put("dependencies", JSONArray(dependencies))
        .put("dependency_state", dependencyState)

    @Test fun `accepts bounded low-risk tasks with unresolved dependencies`() {
        ObjectiveTaskDecompositionValidator.validate(
            JSONArray().put(item("phase-zero-audit"))
        )
    }

    @Test fun `rejects high-risk work without owner approval`() {
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveTaskDecompositionValidator.validate(
                JSONArray().put(item("privileged-change", risk = "HIGH", approval = false))
            )
        }
    }

    @Test fun `rejects irreversible work without owner approval`() {
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveTaskDecompositionValidator.validate(
                JSONArray().put(item("destructive-change", reversible = false, approval = false))
            )
        }
    }

    @Test fun `rejects dependencies that point outside the generated batch`() {
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveTaskDecompositionValidator.validate(
                JSONArray().put(item("task-alpha", dependencies = listOf("missing-task"), dependencyState = "EXPLICIT"))
            )
        }
    }

    @Test fun `rejects cyclic dependencies`() {
        val a = item("task-alpha", dependencies = listOf("task-bravo"), dependencyState = "EXPLICIT")
        val b = item("task-bravo", dependencies = listOf("task-alpha"), dependencyState = "EXPLICIT")
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveTaskDecompositionValidator.validate(JSONArray().put(a).put(b))
        }
    }

    @Test fun `does not allow the model to claim no dependencies without explicit evidence`() {
        assertThrows(IllegalArgumentException::class.java) {
            ObjectiveTaskDecompositionValidator.validate(
                JSONArray().put(item("task-alpha", dependencyState = "EXPLICIT"))
            )
        }
    }
}
