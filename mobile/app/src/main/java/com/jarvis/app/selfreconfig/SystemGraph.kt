package com.jarvis.app.selfreconfig

/**
 * Machine-readable map of JARVIS's own architecture: nodes for every real organ,
 * dependency edges, and an explicit fixed-invariants set that this mechanism
 * can never mark mutable.
 *
 * This is the one source of truth for organ topology — no second/parallel graph
 * structure exists or should be built.
 */
class SystemGraph {

    enum class OrganType {
        CORE,
        COGNITIVE,
        MEMORY,
        IDENTITY,
        MODEL,
        CAPABILITY,
        SAFETY,
        VOICE,
        ENTRY,
        TRACE,
        PLANNED,
        RESOLUTION,
        CLOUD,
        RESEARCH,
        ANDROID,
        BUILDER,
        INFRA
    }

    data class SystemNode(
        val id: String,
        val name: String,
        val organType: OrganType,
        val qualifiedClassName: String? = null,
        val isFixedInvariant: Boolean = false,
        val description: String = ""
    ) {
        init {
            require(id.isNotBlank()) { "Node id must not be blank" }
            require(name.isNotBlank()) { "Node name must not be blank" }
        }
    }

    data class DependencyEdge(
        val fromId: String,
        val toId: String,
        val kind: EdgeKind = EdgeKind.DEPENDS_ON
    ) {
        enum class EdgeKind {
            DEPENDS_ON,
            SENDS_TO,
            READS_FROM,
            GATES
        }
    }

    data class ReachabilityResult(
        val entryPointId: String,
        val reachableIds: Set<String>,
        val unreachableIds: Set<String>,
        val unreachableNodes: List<SystemNode>
    )

    private val nodes = mutableMapOf<String, SystemNode>()
    private val edges = mutableListOf<DependencyEdge>()

    fun registerNode(node: SystemNode): SystemNode {
        require(node.id !in nodes) { "Duplicate node id: ${node.id}" }
        nodes[node.id] = node
        return node
    }

    fun addEdge(fromId: String, toId: String, kind: DependencyEdge.EdgeKind = DependencyEdge.EdgeKind.DEPENDS_ON): DependencyEdge {
        require(fromId in nodes) { "Source node not registered: $fromId" }
        require(toId in nodes) { "Target node not registered: $toId" }
        val edge = DependencyEdge(fromId, toId, kind)
        edges.add(edge)
        return edge
    }

    fun markFixedInvariant(nodeId: String) {
        val node = nodes[nodeId] ?: throw IllegalArgumentException("Node not registered: $nodeId")
        require(!node.isFixedInvariant) { "Node '$nodeId' is already a fixed invariant — cannot be unset" }
        nodes[nodeId] = node.copy(isFixedInvariant = true)
    }

    fun tryMarkMutable(nodeId: String): Boolean {
        val node = nodes[nodeId] ?: throw IllegalArgumentException("Node not registered: $nodeId")
        if (node.isFixedInvariant) return false
        return true
    }

    fun node(id: String): SystemNode? = nodes[id]

    fun allNodes(): List<SystemNode> = nodes.values.toList()

    fun edgesFrom(nodeId: String): List<DependencyEdge> = edges.filter { it.fromId == nodeId }

    fun edgesTo(nodeId: String): List<DependencyEdge> = edges.filter { it.toId == nodeId }

    fun nodeCount(): Int = nodes.size

    fun edgeCount(): Int = edges.size

    fun fixedInvariants(): List<SystemNode> = nodes.values.filter { it.isFixedInvariant }

    fun computeReachability(entryPointId: String): ReachabilityResult {
        require(entryPointId in nodes) { "Entry point not registered: $entryPointId" }
        val reachable = mutableSetOf(entryPointId)
        val queue = ArrayDeque<String>()
        queue.add(entryPointId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (edge in edgesFrom(current)) {
                if (edge.toId !in reachable) {
                    reachable.add(edge.toId)
                    queue.add(edge.toId)
                }
            }
        }
        val allIds = nodes.keys
        val unreachableIds = allIds - reachable
        val unreachableNodes = unreachableIds.mapNotNull { nodes[it] }
        return ReachabilityResult(entryPointId, reachable.toSet(), unreachableIds, unreachableNodes)
    }
}
