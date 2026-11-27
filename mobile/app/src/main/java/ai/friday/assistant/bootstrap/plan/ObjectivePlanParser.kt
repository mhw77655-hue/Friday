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

    private val phaseHeading = Regex("^### Phase ([0-8])\\s+[—-]\\s*(.+)$")

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
     * Parse the chronological delivery roadmap. The roadmap—not an obsolete
     * nested "Objective 1" section—is the canonical source of Phase 0 through
     * Phase 8. Exit gates are retained verbatim as the phase acceptance evidence.
     */
    fun parseObjectiveOnePhases(markdown: String): List<Phase> {
        val lines = markdown.lines()
        val roadmapStart = lines.indexOfFirst {
            it.trim().startsWith("## 8. Chronological delivery roadmap")
        }
        require(roadmapStart >= 0) { "Canonical chronological delivery roadmap was not found." }
        val continuousStart = (roadmapStart + 1 until lines.size).firstOrNull {
            lines[it].trim().startsWith("### Continuous tracks")
        } ?: lines.size
        val phaseStarts = (roadmapStart + 1 until continuousStart).mapNotNull { index ->
            phaseHeading.matchEntire(lines[index].trim())?.let { index to it }
        }
        val phases = phaseStarts.mapIndexed { index, (start, match) ->
            val end = phaseStarts.getOrNull(index + 1)?.first ?: continuousStart
            val section = lines.subList(start, end).joinToString("\n").trim()
            val evidence = Regex(
                "(?is)\\*\\*(?:Exit gate|Exit evidence):\\*\\*\\s*(.*?)(?:\\n\\n|$)"
            ).find(section)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            Phase(
                id = match.groupValues[1].toInt(),
                title = match.groupValues[2].trim(),
                markdown = section,
                exitEvidence = evidence
            )
        }
        require(phases.map { it.id } == (0..8).toList()) {
            "Canonical roadmap must contain exactly phases 0 through 8 in order."
        }
        require(phases.all { it.exitEvidence.isNotBlank() }) {
            "Every canonical roadmap phase must retain its explicit exit gate."
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
