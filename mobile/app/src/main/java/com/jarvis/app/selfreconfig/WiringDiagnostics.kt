package com.jarvis.app.selfreconfig

/**
 * Integration / Wiring Diagnostics: walks the real call graph from the real
 * entry point (LatencyPipeline.onUserInput) and flags any organ present in
 * SystemGraph but unreachable from it.
 *
 * Uses the one true SystemGraph as its source of truth — no second graph
 * structure.
 */
class WiringDiagnostics(private val graph: SystemGraph) {

    data class DiagnosticResult(
        val entryPointId: String,
        val reachableCount: Int,
        val unreachableCount: Int,
        val unreachableNodes: List<SystemGraph.SystemNode>,
        val allInvariantNodesPresent: Boolean,
        val missingInvariants: List<String>
    )

    fun diagnose(entryPointId: String = "entry.latencyPipeline"): DiagnosticResult {
        val result = graph.computeReachability(entryPointId)

        val requiredInvariants = listOf(
            "identity.root",
            "authorization.root",
            "approval.mechanism",
            "upgrade.provenance",
            "rollback.mechanism",
            "security.boundaries",
            "core.recovery"
        )

        val presentInvariantIds = graph.fixedInvariants().map { it.id }.toSet()
        val missingInvariants = requiredInvariants.filter { it !in presentInvariantIds }

        return DiagnosticResult(
            entryPointId = entryPointId,
            reachableCount = result.reachableIds.size,
            unreachableCount = result.unreachableIds.size,
            unreachableNodes = result.unreachableNodes,
            allInvariantNodesPresent = missingInvariants.isEmpty(),
            missingInvariants = missingInvariants
        )
    }
}
