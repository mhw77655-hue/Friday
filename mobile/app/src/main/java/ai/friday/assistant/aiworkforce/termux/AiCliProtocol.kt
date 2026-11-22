package ai.friday.assistant.aiworkforce.termux

import org.json.JSONObject

/** Stable, testable command and response envelopes for the first official CLI workers. */
internal object AiCliProtocol {
    fun command(worker: AiCliWorkerId): String = when (worker) {
        AiCliWorkerId.OPENAI_CODEX ->
            "exec codex exec --json --sandbox read-only --ephemeral --ignore-user-config --skip-git-repo-check -"
        AiCliWorkerId.ANTHROPIC_CLAUDE ->
            "exec claude -p --output-format json --tools ''"
    }

    fun extractResponse(worker: AiCliWorkerId, output: String): String = when (worker) {
        AiCliWorkerId.OPENAI_CODEX -> extractCodexFinalMessage(output)
        AiCliWorkerId.ANTHROPIC_CLAUDE -> extractClaudeResult(output)
    }

    private fun extractCodexFinalMessage(output: String): String {
        val finalMessage = output.lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .asReversed()
            .firstNotNullOfOrNull { line ->
                runCatching {
                    val event = JSONObject(line)
                    val item = event.optJSONObject("item")
                    if (item?.optString("type") == "agent_message") item.optString("text") else null
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        return finalMessage ?: output.trim()
    }

    private fun extractClaudeResult(output: String): String {
        val json = runCatching { JSONObject(output.trim()) }.getOrNull()
        return json?.optString("result")?.takeIf { it.isNotBlank() } ?: output.trim()
    }
}
