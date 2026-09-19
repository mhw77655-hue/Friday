package com.jarvis.app.cognitive.planning

import com.jarvis.app.cognitive.Plan
import com.jarvis.app.cognitive.PlanStatus
import com.jarvis.app.cognitive.PlanStep
import com.jarvis.app.cognitive.PlanStepStatus

/**
 * Structured plan as a dependency graph (DAG), not a flat list.
 *
 * A plan is the decomposition of a goal into ordered, dependency-aware nodes
 * (steps). Every graph operation here is deterministic: identical inputs yield
 * identical ordering, blocking, and next-actionable results, so plans are
 * inspectable and reproducible.
 *
 * The plan does NOT duplicate Goal/Subgoal — it references them by id. Use
 * [PlanGraph.toPlan] to project the graph onto the existing [Plan] value for
 * state storage / context building.
 */
data class PlanGraph(
    val goalId: String,
    val goalDescription: String,
    val nodes: List<PlanNode>,
    val status: PlanStatus = PlanStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val failureCause: String? = null,
    val replanning: ReplanState? = null
) {
    val nodeIds: Set<String> get() = nodes.map { it.id }.toSet()

    val completedCount: Int get() = nodes.count { it.status == PlanStepStatus.COMPLETED }
    val failedCount: Int get() = nodes.count { it.status == PlanStepStatus.FAILED }

    /** Fraction of nodes completed (0..1); 1.0 for an empty plan. */
    val progress: Float
        get() = if (nodes.isEmpty()) 1.0f else completedCount.toFloat() / nodes.size

    val blockedNodeIds: List<String>
        get() = nodes.filter { it.blocked }.map { it.id }

    val failedNodeIds: List<String>
        get() = nodes.filter { it.status == PlanStepStatus.FAILED }.map { it.id }

    val isComplete: Boolean
        get() = nodes.isNotEmpty() && nodes.all { it.status == PlanStepStatus.COMPLETED }

    /** Project this graph onto the existing [Plan] value type. */
    fun toPlan(planId: String = "plan_$goalId"): Plan = Plan(
        id = planId,
        goalId = goalId,
        steps = nodes.map { n ->
            PlanStep(
                id = n.id,
                description = n.description,
                action = n.action,
                expectedOutcome = n.expectedOutcome,
                dependencies = n.prerequisites,
                status = n.status,
                estimatedEffort = n.estimatedEffort
            )
        },
        status = status,
        createdAt = createdAt
    )
}

/**
 * One node (step) in a plan DAG. [prerequisites] are ids of nodes that must be
 * satisfied first; they are the incoming dependency edges of this node.
 */
data class PlanNode(
    val id: String,
    val description: String,
    val action: String = "",
    val expectedOutcome: String = "",
    val prerequisites: List<String> = emptyList(),
    val subgoalId: String? = null,
    val status: PlanStepStatus = PlanStepStatus.PENDING,
    val estimatedEffort: Float = 1.0f,
    val blocked: Boolean = false,
    val failureCause: String? = null,
    val completionCondition: String? = null,
    val outcome: String? = null
) {
    val isPending: Boolean get() = status == PlanStepStatus.PENDING
    val isInProgress: Boolean get() = status == PlanStepStatus.IN_PROGRESS
    val isCompleted: Boolean get() = status == PlanStepStatus.COMPLETED
    val isFailed: Boolean get() = status == PlanStepStatus.FAILED
    val isSkipped: Boolean get() = status == PlanStepStatus.SKIPPED
}

/**
 * Replanning state recorded when a plan step fails. A failed step does NOT
 * restart the goal: the cause is identified, downstream (transitively
 * dependent) nodes are invalidated, and a replacement path is proposed to
 * continue from the current state.
 */
data class ReplanState(
    val originalNodeId: String,
    val cause: String,
    val invalidatedNodeIds: List<String>,
    val replacementPaths: List<List<String>>,
    val status: ReplanStatus
)

enum class ReplanStatus { REQUESTED, IN_PROGRESS, COMPLETED, ABANDONED }

/** Result of validating the plan graph before it is accepted. */
data class GraphValidation(
    val valid: Boolean,
    val cycles: List<List<String>> = emptyList(),
    val missingPrerequisites: List<String> = emptyList(),
    val message: String = ""
) {
    companion object {
        val VALID = GraphValidation(valid = true, message = "valid")
    }
}

// ---------------------------------------------------------------------------
// Deterministic graph algorithms
// ---------------------------------------------------------------------------

/**
 * Detect dependency cycles via DFS. Returns one cycle per strongly connected
 * component found (as a node-id list), empty when the graph is acyclic.
 */
fun findCycles(nodes: List<PlanNode>): List<List<String>> {
    val nodeMap = nodes.associateBy { it.id }
    val state = mutableMapOf<String, Int>() // 0=unvisited 1=in-stack 2=done
    val stack = mutableListOf<String>()
    val cycles = mutableListOf<List<String>>()

    fun visit(id: String) {
        when (state[id]) {
            1 -> {
                // id already on the stack -> record the cycle slice
                val cycleStart = stack.indexOf(id)
                if (cycleStart >= 0) {
                    cycles.add(stack.subList(cycleStart, stack.size).toList())
                }
                return
            }
            2 -> return
        }
        // state[id] == null -> unvisited, proceed
        state[id] = 1
        stack.add(id)
        nodeMap[id]?.prerequisites?.forEach { dep -> visit(dep) }
        stack.removeAt(stack.size - 1)
        state[id] = 2
    }

    nodes.forEach { n -> if (state[n.id] != 2) visit(n.id) }
    return cycles
}

/** Prerequisites that reference a node id which does not exist in the graph. */
fun missingPrerequisites(nodes: List<PlanNode>): List<String> {
    val ids = nodes.map { it.id }.toSet()
    return nodes.flatMap { n -> n.prerequisites.filter { it !in ids } }.distinct()
}

/**
 * Kahn topological order. Deterministic: ready nodes are emitted in input
 * order, so equal inputs produce equal output. Returns null when the graph
 * has a cycle (callers should reject cyclic plans first).
 */
fun topologicalOrder(nodes: List<PlanNode>): List<String>? {
    val nodeMap = nodes.associateBy { it.id }
    val indegree = mutableMapOf<String, Int>()
    val dependents = mutableMapOf<String, MutableList<String>>()

    nodes.forEach { n ->
        indegree[n.id] = n.prerequisites.size
        n.prerequisites.forEach { dep -> dependents.getOrPut(dep) { mutableListOf() }.add(n.id) }
    }

    val ready = nodes.filter { it.prerequisites.isEmpty() }.map { it.id }.toMutableList()
    val order = mutableListOf<String>()
    var readyIndex = 0
    while (readyIndex < ready.size) {
        val id = ready[readyIndex++]
        order.add(id)
        dependents[id]?.forEach { dependent ->
            val newCount = (indegree[dependent] ?: 0) - 1
            indegree[dependent] = newCount
            if (newCount == 0) ready.add(dependent)
        }
    }
    return if (order.size == nodes.size) order else null
}

/**
 * Recompute blocking for every node: a node is blocked when it is explicitly
 * blocked, or any prerequisite is missing, failed, blocked, or skipped.
 * Deterministic — depends only on the node list.
 */
fun computeBlocked(nodes: List<PlanNode>): List<PlanNode> {
    val nodeMap = nodes.associateBy { it.id }
    val blocked = mutableMapOf<String, Boolean>()

    fun isBlocked(id: String): Boolean {
        blocked[id]?.let { return it }
        val node = nodeMap[id] ?: return true // missing prerequisite
        val result = node.blocked ||
            node.prerequisites.any { dep ->
                val depNode = nodeMap[dep]
                depNode == null ||
                    isBlocked(dep) || // transitive blocking
                    depNode.status == PlanStepStatus.FAILED ||
                    depNode.status == PlanStepStatus.SKIPPED
            }
        blocked[id] = result
        return result
    }

    return nodes.map { n -> if (isBlocked(n.id)) n.copy(blocked = true) else n.copy(blocked = false) }
}

/**
 * Next actionable node: a PENDING (or IN_PROGRESS) node whose prerequisites are
 * all COMPLETED and which is not blocked. Returns the first in [topologicalOrder].
 * Deterministic.
 */
fun nextActionable(nodes: List<PlanNode>): PlanNode? {
    val recomputed = computeBlocked(nodes)
    // Order only over nodes that are not blocked; a node with a missing
    // prerequisite is blocked and must not stall the ordering of the rest.
    val order = topologicalOrder(recomputed.filter { !it.blocked }) ?: return null
    val byId = recomputed.associateBy { it.id }
    return order.asSequence()
        .mapNotNull { byId[it] }
        .firstOrNull { n ->
            !n.isCompleted && !n.isFailed && !n.isSkipped &&
                (n.isPending || n.isInProgress) &&
                n.prerequisites.all { dep -> byId[dep]?.isCompleted == true }
        }
}

/** Validate a proposed node set as a DAG (cycles + missing prerequisites). */
fun validateGraph(nodes: List<PlanNode>): GraphValidation {
    if (nodes.isEmpty()) {
        return GraphValidation(valid = false, message = "plan has no steps")
    }
    val cycles = findCycles(nodes)
    if (cycles.isNotEmpty()) {
        return GraphValidation(valid = false, cycles = cycles, message = "cyclic dependency detected: $cycles")
    }
    val missing = missingPrerequisites(nodes)
    if (missing.isNotEmpty()) {
        return GraphValidation(
            valid = false,
            missingPrerequisites = missing,
            message = "missing prerequisites: $missing"
        )
    }
    return GraphValidation.VALID
}
