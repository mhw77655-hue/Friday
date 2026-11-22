package ai.friday.assistant.bootstrap.plan

import org.json.JSONArray
import org.json.JSONObject

/** Extracts the canonical top-level delivery objectives without inventing dependencies. */
object ObjectivePlanParser {
    private val heading = Regex("^### Objective ([A-G])(?:\\s+[—-]\\s*(.*))?$")

    data class Objective(val id: String, val title: String, val markdown: String)

    fun parse(markdown: String): List<Objective> {
        require(markdown.isNotBlank()) { "The canonical objectives plan is empty." }
        val lines = markdown.lines()
        val starts = lines.mapIndexedNotNull { index, line ->
            heading.matchEntire(line.trim())?.let { index to it }
        }
        require(starts.isNotEmpty()) { "No canonical Objective A-G headings were found." }
        return starts.mapIndexed { position, (start, match) ->
            val end = starts.getOrNull(position + 1)?.first ?: lines.size
            val id = match.groupValues[1]
            val title = match.groupValues.getOrNull(2).orEmpty().ifBlank { "Objective $id" }
            Objective(id, title, lines.subList(start, end).joinToString("\n").trim())
        }
    }

    fun toJson(objectives: List<Objective>): JSONArray = JSONArray().apply {
        objectives.forEach { objective ->
            put(JSONObject()
                .put("id", objective.id)
                .put("title", objective.title)
                .put("status", "UNASSESSED")
                .put("dependencies", JSONArray())
                .put("scope_markdown", objective.markdown))
        }
    }
}
