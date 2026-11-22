package ai.friday.assistant.bootstrap.plan

import org.json.JSONArray

/** Deterministic validation boundary for untrusted local-model work plans. */
internal object ObjectiveTaskDecompositionValidator {
    private const val MAX_ITEMS = 4

    fun validate(items: JSONArray) {
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
}
