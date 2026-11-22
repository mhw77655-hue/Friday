package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.env.ModelProviderType
import ai.friday.assistant.model.FinishReason
import ai.friday.assistant.model.GenerateRequest
import ai.friday.assistant.model.Message
import ai.friday.assistant.model.MessageRole
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
        val providerType = modelManager.activeProvider.value.providerType
        val localProvider = when (providerType) {
            ModelProviderType.LLAMA_CPP -> true
            ModelProviderType.OLLAMA -> {
                val endpoint = modelManager.activeProvider.value.config?.parameters?.get("endpoint")
                    ?: "http://127.0.0.1:11434"
                val host = runCatching { java.net.URI(endpoint).host }.getOrNull()
                host == "localhost" || host == "127.0.0.1" || host == "::1"
            }
            else -> false
        }
        require(localProvider) {
            "No approved on-device model provider is active; cloud and heuristic providers are not used for offline planning."
        }
        require(modelManager.isTierLoaded(ai.friday.assistant.model.ModelTier.RESIDENT)) {
            "Friday's resident local model is not loaded."
        }

        val modelId = modelManager.activeProvider.value.config?.modelId ?: "friday-resident:latest"
        val prompt = buildString {
            appendLine("Parent task: " + parent.optString("task_id"))
            appendLine("Objective: " + parent.optString("objective_id"))
            appendLine("Phase: " + parent.opt("phase_id"))
            appendLine("Goal: " + parent.optString("goal"))
            appendLine("Acceptance evidence: " + parent.optString("acceptance_criteria"))
            appendLine("Canonical scope:")
            appendLine(parent.optString("scope_markdown").take(MAX_SCOPE_CHARS))
        }
        val request = GenerateRequest(
            messages = listOf(
                Message(
                    role = MessageRole.SYSTEM,
                    content = SYSTEM_PROMPT
                ),
                Message(
                    role = MessageRole.USER,
                    content = prompt
                )
            ),
            maxTokens = MAX_OUTPUT_TOKENS,
            temperature = 0.1f,
            topP = 0.9f,
            modelId = "friday-resident:latest"
        )
        val generated = modelManager.generate(request)
        require(generated.finishReason != FinishReason.ERROR &&
            generated.finishReason != FinishReason.CONTENT_FILTER &&
            generated.content.isNotBlank()
        ) { "The local model did not return a usable task decomposition." }
        val root = parseJson(generated.content)
        val items = root.optJSONArray("work_items")
            ?: throw IllegalArgumentException("The local model omitted work_items.")
        ObjectiveTaskDecompositionValidator.validate(items)
        return ResultPacket(
            modelId = generated.model ?: modelId,
            items = items
        )
    }

    private fun parseJson(content: String): JSONObject {
        val trimmed = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "The local model did not return JSON." }
        return JSONObject(trimmed.substring(start, end + 1))
    }

    companion object {
        private const val MAX_ITEMS = 4
        private const val MAX_SCOPE_CHARS = 6_000
        private const val MAX_OUTPUT_TOKENS = 900
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
