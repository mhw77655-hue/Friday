package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.model.FinishReason
import ai.friday.assistant.aiworkforce.AiWorkerConsultationPort
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
    private val modelManager: ModelManager?,
    private val externalWorkers: AiWorkerConsultationPort? = null
) {
    data class ResultPacket(
        val modelId: String,
        val items: JSONArray
    )

    suspend fun decompose(parent: JSONObject): ResultPacket {
        val prompt = SYSTEM_PROMPT.trimIndent() + "\n\n" + buildString {
            appendLine("Parent task: " + parent.optString("task_id"))
            appendLine("Objective: " + parent.optString("objective_id"))
            appendLine("Phase: " + parent.opt("phase_id"))
            appendLine("Goal: " + parent.optString("goal"))
            appendLine("Acceptance evidence: " + parent.optString("acceptance_criteria"))
            appendLine("Canonical scope:")
            appendLine(parent.optString("scope_markdown").take(MAX_SCOPE_CHARS))
        }

        // Prefer Friday's local resident model. If it is unavailable or its
        // output fails the strict schema validator, ask already-authorized
        // subscription CLI workers. External output remains untrusted.
        val local = modelManager?.takeIf {
            it.isTierLoaded(ai.friday.assistant.model.ModelTier.RESIDENT)
        }
        if (local != null) {
            val generated = runCatching {
                local.generateOnTier(ai.friday.assistant.model.ModelTier.RESIDENT, prompt)
            }.getOrNull()
            if (generated != null &&
                generated.finishReason != FinishReason.ERROR &&
                generated.finishReason != FinishReason.CONTENT_FILTER &&
                generated.content.isNotBlank()
            ) {
                val accepted = runCatching {
                    val items = parseJson(generated.content).optJSONArray("work_items")
                        ?: throw IllegalArgumentException("The local model omitted work_items.")
                    ObjectiveTaskDecompositionValidator.validate(items)
                    items
                }.getOrNull()
                if (accepted != null) {
                    return ResultPacket(
                        modelId = generated.model ?: "friday-resident:latest",
                        items = accepted
                    )
                }
            }
        }

        val workers = externalWorkers
            ?: throw IllegalStateException("No local resident model or authorized external AI worker is available.")
        val replies = workers.consultAvailable(prompt)
        if (replies.isEmpty()) {
            throw IllegalStateException(
                "No authorized subscription AI worker is available; the task remains queued for local execution."
            )
        }
        val rejected = mutableListOf<String>()
        for (reply in replies) {
            val accepted = runCatching {
                val items = parseJson(reply.response).optJSONArray("work_items")
                    ?: throw IllegalArgumentException("Worker omitted work_items.")
                ObjectiveTaskDecompositionValidator.validate(items)
                items
            }.getOrElse {
                rejected += reply.providerId + ": " + (it.message ?: "invalid response")
                null
            }
            if (accepted != null) {
                return ResultPacket(
                    modelId = "subscription-worker:" + reply.providerId + "@" + reply.executionEnvironment,
                    items = accepted
                )
            }
        }
        throw IllegalArgumentException(
            "All subscription worker responses failed Friday's task schema validation: " +
                rejected.joinToString("; ").take(500)
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
