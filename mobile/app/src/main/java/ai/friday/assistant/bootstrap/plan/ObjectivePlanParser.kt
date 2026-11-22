package ai.friday.assistant.bootstrap.plan

import org.json.JSONArray
import org.json.JSONObject

/** Extracts the canonical top-level delivery objectives without inventing dependencies. */
object ObjectivePlanParser {
    private val heading = Regex("^### Objective ([A-G])(?:\\s+[—-]\\s*(.*))?$")

    data class Objective(val id: String, val title: String, val markdown: String)
    data class Phase(
        val id: Int,
        val title: String,
        val markdown: String,
        val exitEvidence: String
    )

    private val phaseHeading = Regex("^### Phase ([0-7])\\s+[—-]\\s*(.+)$")

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


    /**
     * Objective 1 is explicitly an eight-phase gate (0 through 7).
     * Preserve the plan's actual wording and exit evidence; never invent
     * dependencies or infer acceptance from a heading existing.
     */
    fun parseObjectiveOnePhases(markdown: String): List<Phase> {
        val lines = markdown.lines()
        val objectiveStart = lines.indexOfFirst {
            it.trim().startsWith("## Objective A / Objective 1")
        }
        require(objectiveStart >= 0) { "Objective 1 phase section was not found." }
        val objectiveEnd = (objectiveStart + 1 until lines.size).firstOrNull {
            lines[it].startsWith("## ")
        } ?: lines.size
        val phaseStarts = (objectiveStart + 1 until objectiveEnd).mapNotNull { index ->
            phaseHeading.matchEntire(lines[index].trim())?.let { index to it }
        }
        val phases = phaseStarts.mapIndexed { index, (start, match) ->
            val end = phaseStarts.getOrNull(index + 1)?.first ?: objectiveEnd
            val section = lines.subList(start, end).joinToString("\\n").trim()
            val evidence = Regex(
                "(?is)\\*\\*Exit evidence:\\*\\*\\s*(.*?)(?:\\n\\n|$)"
            ).find(section)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            Phase(
                id = match.groupValues[1].toInt(),
                title = match.groupValues[2].trim(),
                markdown = section,
                exitEvidence = evidence
            )
        }
        require(phases.map { it.id } == (0..7).toList()) {
            "Objective 1 must contain exactly phases 0 through 7 in canonical order."
        }
        require(phases.all { it.exitEvidence.isNotBlank() }) {
            "Every Objective 1 phase must retain its explicit exit evidence."
        }
        return phases
    }

    fun phasesToJson(phases: List<Phase>): JSONArray = JSONArray().apply {
        phases.forEach { phase ->
            put(JSONObject()
                .put("id", phase.id)
                .put("title", phase.title)
                .put("status", "UNASSESSED")
                .put("exit_evidence", phase.exitEvidence)
                .put("scope_markdown", phase.markdown))
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
