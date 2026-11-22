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
        require(providerType == ModelProviderType.LLAMA_CPP ||
            providerType == ModelProviderType.OLLAMA
        ) { "No approved local model provider is active; cloud and heuristic providers are not used for offline planning." }
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
        validateItems(items)
        return ResultPacket(
            modelId = generated.model ?: modelId,
            items = items
        )
    }

    private fun validateItems(items: JSONArray) {
        require(items.length() in 1..MAX_ITEMS) {
            "The local model must return between one and $MAX_ITEMS work items."
        }
        val ids = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val id = item.optString("id")
            require(id.matches(Regex("[a-z0-9][a-z0-9-]{2,39}"))) {
                "A generated work item has an invalid id."
            }
            id
        }
        require(ids.toSet().size == ids.size) { "The local model returned duplicate work item ids." }

        val graph = mutableMapOf<String, List<String>>()
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            require(item.optString("goal").isNotBlank() && item.optString("goal").length <= 400) {
                "A generated work item has an invalid goal."
            }
            val acceptance = item.optJSONArray("acceptance_criteria")
                ?: throw IllegalArgumentException("A work item omitted acceptance criteria.")
            require(acceptance.length() in 1..4) { "A work item needs one to four acceptance criteria." }
            for (criterionIndex in 0 until acceptance.length()) {
                val criterion = acceptance.optString(criterionIndex)
                require(criterion.isNotBlank() && criterion.length <= 320) {
                    "A work item has an invalid acceptance criterion."
                }
            }
            val evidence = item.optJSONArray("evidence_needed")
                ?: throw IllegalArgumentException("A work item omitted evidence_needed.")
            require(evidence.length() in 1..5) { "A work item needs one to five evidence requirements." }
            for (evidenceIndex in 0 until evidence.length()) {
                val evidenceItem = evidence.optString(evidenceIndex)
                require(evidenceItem.isNotBlank() && evidenceItem.length <= 320) {
                    "A work item has an invalid evidence requirement."
                }
            }

            val risk = item.optString("risk")
            require(risk in setOf("LOW", "MEDIUM", "HIGH")) { "A work item has an unknown risk level." }
            require(item.has("reversible") && item.has("requires_owner_approval")) {
                "A work item omitted its authority controls."
            }
            val reversible = item.getBoolean("reversible")
            val approval = item.getBoolean("requires_owner_approval")
            require((risk != "HIGH" && reversible) || approval) {
                "High-risk or irreversible work must require owner approval."
            }

            val dependencies = item.optJSONArray("dependencies") ?: JSONArray()
            val dependencyIds = (0 until dependencies.length()).map { dependencies.getString(it) }
            require(dependencyIds.all { it in ids }) {
                "A work item references a dependency outside its decomposition batch."
            }
            graph[item.getString("id")] = dependencyIds
            val dependencyState = item.optString("dependency_state", "UNRESOLVED")
            require(dependencyState == "EXPLICIT" || dependencyState == "UNRESOLVED") {
                "A work item has an invalid dependency state."
            }
            if (dependencyIds.isEmpty()) {
                require(dependencyState == "UNRESOLVED") {
                    "The local planner cannot claim there are no dependencies without explicit plan evidence."
                }
            }
        }
        require(isAcyclic(graph)) { "The local model returned a cyclic work dependency graph." }
    }

    private fun isAcyclic(graph: Map<String, List<String>>): Boolean {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(node: String): Boolean {
            if (node in visiting) return false
            if (node in visited) return true
            visiting += node
            for (dependency in graph[node].orEmpty()) {
                if (!visit(dependency)) return false
            }
            visiting -= node
            visited += node
            return true
        }
        return graph.keys.all(::visit)
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
