package com.jarvis.app.cognition.workspace

/**
 * A per-task cognitive slice. Organs do not call each other by name; they
 * compete to contribute from the claims currently available in the workspace.
 */
data class WorkspaceTask(
    val episodeId: String,
    val taskKind: String,
    val riskLevel: RiskLevel,
    val nowMillis: Long = System.currentTimeMillis()
)

enum class RiskLevel { LOW, NORMAL, HIGH, CRITICAL }

/** A replaceable organ that declares relevance to the current task's claims. */
interface WorkspaceOrgan {
    val organId: String
    fun relevance(claims: List<Claim>, task: WorkspaceTask): Int
    fun contribute(workspace: Workspace, task: WorkspaceTask)
}

/**
 * Liquid task composition: the same shared workspace is queried each turn,
 * then only organs that declare relevance are run. The composer owns no
 * cognitive policy and contains no fixed organ-to-organ wiring.
 */
class LiquidTaskComposer(organs: Collection<WorkspaceOrgan>) {
    private val registry = organs.associateBy { it.organId }

    fun compose(workspace: Workspace, task: WorkspaceTask): List<String> {
        val claims = ClaimKind.values().mapNotNull(workspace::current)
        val selected = registry.values
            .mapNotNull { organ ->
                val score = organ.relevance(claims, task)
                if (score <= 0) null else organ to score
            }
            .sortedWith(compareByDescending<Pair<WorkspaceOrgan, Int>> { it.second }
                .thenBy { it.first.organId })

        selected.forEach { (organ, _) -> organ.contribute(workspace, task) }
        return selected.map { it.first.organId }
    }
}
