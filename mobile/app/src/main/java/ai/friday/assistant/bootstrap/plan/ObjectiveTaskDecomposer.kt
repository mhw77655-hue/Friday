package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.model.FinishReason
import ai.friday.assistant.model.ModelManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Uses Friday's already-loaded on-device resident model to turn a canonical
 * objective/phase assessment packet into bounded work items. The model is a
 * planner, not an authority: output is schema-checked, risk-gated and persisted
 * as queued work. No task is marked executed or accepted here.
 */
class ObjectiveTaskDecomposer(
    private val modelManager: ModelManager
) {
    data class ResultPacket(
        val modelId: String,
        val items: JSONArray
    )

    suspend fun decompose(parent: JSONObject): ResultPacket {
        require(modelManager.isTierLoaded(ai.friday.assistant.model.ModelTier.RESIDENT)) {
            "Friday's resident local model is not loaded."
        }

        val modelId = "friday-resident:latest"
        val prompt = SYSTEM_PROMPT.trimIndent() + "\n\n" + buildString {
            appendLine("Parent task: " + parent.optString("task_id"))
            appendLine("Objective: " + parent.optString("objective_id"))
            appendLine("Phase: " + parent.opt("phase_id"))
            appendLine("Goal: " + parent.optString("goal"))
            appendLine("Acceptance evidence: " + parent.optString("acceptance_criteria"))
            appendLine("Canonical scope:")
            appendLine(parent.optString("scope_markdown").take(MAX_SCOPE_CHARS))
        }
        val generated = modelManager.generateOnTier(
            ai.friday.assistant.model.ModelTier.RESIDENT,
            prompt
        )
        require(generated.finishReason != FinishReason.ERROR &&
            generated.finishReason != FinishReason.CONTENT_FILTER &&
            generated.content.isNotBlank()
        ) { "The local model did not return a usable task decomposition." }
        return parseCloudResponse(generated.content, generated.model ?: modelId)
    }

    companion object {
        /** Shared schema gate for both local and explicitly consented cloud proposals. */
        fun parseCloudResponse(content: String, modelId: String): ResultPacket {
            val trimmed = content.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            require(start >= 0 && end > start) { "The planner did not return JSON." }
            val root = JSONObject(trimmed.substring(start, end + 1))
            val items = root.optJSONArray("work_items")
                ?: throw IllegalArgumentException("The planner omitted work_items.")
            ObjectiveTaskDecompositionValidator.validate(items)
            return ResultPacket(modelId = modelId, items = items)
        }

        private const val MAX_SCOPE_CHARS = 6_000
        private const val SYSTEM_PROMPT = """
You are Friday's local development planner. Convert the supplied canonical task into 1-4 small, independently verifiable work items.

Return only JSON with this exact shape:
{
  "work_items": [
    {
      "id": "short-lowercase-id",
      "goal": "one bounded outcome",
      "acceptance_criteria": ["observable criterion"],
      "evidence_needed": ["specific proof to collect"],
      "risk": "LOW|MEDIUM|HIGH",
      "reversible": true,
      "requires_owner_approval": false,
      "dependencies": [],
      "dependency_state": "UNRESOLVED"
    }
  ]
}

Rules:
- Do not claim anything is implemented, tested, accepted or complete.
- Do not invent facts about the phone, repository, tests, credentials or permissions.
- Do not infer dependencies. Use dependencies only when the supplied plan explicitly states them; otherwise leave the array empty and set dependency_state to UNRESOLVED.
- Never set dependency_state to EXPLICIT with an empty dependency list.
- High-risk or irreversible work must set requires_owner_approval=true.
- Prefer read-only evidence gathering and reversible, bounded tasks first.
- Do not ask the owner to choose routine tasks. Friday owns prioritisation.
- No code, shell commands, secrets, API keys, signing operations, releases, merges or permission escalation in this decomposition.
"""
    }
}
