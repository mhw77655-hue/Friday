package com.jarvis.app.cognitive.planning

import com.jarvis.app.cognitive.Goal
import com.jarvis.app.cognitive.GoalStatus
import com.jarvis.app.cognitive.PlanStatus
import com.jarvis.app.cognitive.PlanStepStatus
import com.jarvis.app.cognitive.Subgoal
import com.jarvis.app.cognitive.SubgoalStatus

/**
 * Spec for a subgoal during decomposition. [dependencies] are ids of OTHER
 * subgoal specs that must be satisfied first.
 */
data class SubgoalSpec(
    val id: String,
    val description: String,
    val dependencies: List<String> = emptyList(),
    val completionCondition: String? = null
)

/** Spec for a single plan node (step). [prerequisites] are node ids. */
data class StepSpec(
    val id: String,
    val description: String,
    val action: String = "",
    val expectedOutcome: String = "",
    val prerequisites: List<String> = emptyList(),
    val completionCondition: String? = null
)

/** Decomposition knowledge source for the planner — deterministic by contract. */
interface DecompositionStrategy {
    /** Decompose a goal into subgoal specs. Return empty when unknown. */
    fun decomposeGoal(goal: Goal): List<SubgoalSpec> = emptyList()

    /** Decompose a subgoal into step specs. Return empty when unknown. */
    fun decomposeSubgoal(subgoal: SubgoalSpec): List<StepSpec> = emptyList()

    /** Propose replacement step specs for a failed node, given its cause. */
    fun proposeReplacement(failed: PlanNode, cause: String): List<StepSpec> = emptyList()
}

/** A no-knowledge strategy: never invents steps, so plans mark blocked/uncertain. */
object EmptyDecompositionStrategy : DecompositionStrategy

/** Result of validating a goal before planning. */
data class GoalValidation(
    val valid: Boolean,
    val missingFields: List<String> = emptyList(),
    val warning: String? = null
)

/** Outcome of a planning run. */
data class PlanResult(
    val graph: PlanGraph,
    val subgoals: List<Subgoal>,
    val validation: GoalValidation,
    val ordering: List<String>,
    val nextActionable: PlanNode?,
    val created: Boolean
)

/**
 * GoalPlanner — converts a structured [Goal] into validated, ordered subgoals
 * and a dependency-graph plan. All public operations are deterministic: the
 * same inputs always produce the same plan, ordering, and lifecycle results.
 *
 * Insufficient information never fabricates steps: an undecomposable goal or
 * subgoal is marked blocked/uncertain instead.
 */
class GoalPlanner(
    private val strategy: DecompositionStrategy = EmptyDecompositionStrategy
) {

    /** Validate goal completeness without mutating anything. */
    fun validateGoal(goal: Goal): GoalValidation {
        val missing = mutableListOf<String>()
        if (goal.description.isBlank()) missing.add("description")
        if (goal.successCriteria.isEmpty()) missing.add("successCriteria")

        return if (goal.description.isBlank()) {
            GoalValidation(valid = false, missingFields = missing)
        } else {
            GoalValidation(
                valid = true,
                missingFields = missing,
                warning = if (goal.successCriteria.isEmpty()) "goal has no success criteria; completion will rely on the plan" else null
            )
        }
    }

    /**
     * Decompose a goal into subgoals and a validated plan DAG. Returns a
     * FAILED (invalid goal) or BLOCKED (undecomposable) plan rather than an
     * invented one.
     */
    fun createPlan(goal: Goal): PlanResult {
        val validation = validateGoal(goal)
        if (!validation.valid) {
            return PlanResult(
                graph = PlanGraph(
                    goalId = goal.id, goalDescription = goal.description, nodes = emptyList(),
                    status = PlanStatus.FAILED, failureCause = "invalid goal: ${validation.missingFields.joinToString()}"
                ),
                subgoals = emptyList(), validation = validation,
                ordering = emptyList(), nextActionable = null, created = false
            )
        }

        val subgoalSpecs = strategy.decomposeGoal(goal)
        if (subgoalSpecs.isEmpty()) {
            return PlanResult(
                graph = PlanGraph(
                    goalId = goal.id, goalDescription = goal.description, nodes = emptyList(),
                    status = PlanStatus.BLOCKED,
                    failureCause = "insufficient information to decompose goal"
                ),
                subgoals = emptyList(),
                validation = validation.copy(warning = "goal not decomposed"),
                ordering = emptyList(), nextActionable = null, created = false
            )
        }

        val specById = subgoalSpecs.associateBy { it.id }
        val subgoals = mutableListOf<Subgoal>()
        val nodes = mutableListOf<PlanNode>()
        val subgoalLastNode = mutableMapOf<String, String>()
        val subgoalFirstNode = mutableMapOf<String, String>()

        for (spec in subgoalSpecs) {
            val stepSpecs = strategy.decomposeSubgoal(spec)
            if (stepSpecs.isEmpty()) {
                // Undecomposable subgoal -> a sentinel BLOCKED node, never
                // invented steps. Dependents of this subgoal become blocked too.
                subgoals.add(Subgoal(spec.id, goal.id, spec.description, status = SubgoalStatus.BLOCKED))
                val sentinel = PlanNode(
                    id = "${spec.id}:blocked",
                    description = "Undecomposable subgoal: ${spec.description}",
                    action = "",
                    expectedOutcome = "",
                    subgoalId = spec.id,
                    status = PlanStepStatus.BLOCKED,
                    blocked = true,
                    failureCause = "insufficient information to decompose subgoal"
                )
                nodes.add(sentinel)
                subgoalLastNode[spec.id] = sentinel.id
                subgoalFirstNode[spec.id] = sentinel.id
                continue
            }

            val localNodes = stepSpecs.map { step ->
                PlanNode(
                    id = "${spec.id}:${step.id}",
                    description = step.description,
                    action = step.action,
                    expectedOutcome = step.expectedOutcome,
                    prerequisites = step.prerequisites.map { "${spec.id}:$it" },
                    subgoalId = spec.id,
                    completionCondition = step.completionCondition
                )
            }
            subgoalLastNode[spec.id] = localNodes.last().id
            subgoalFirstNode[spec.id] = localNodes.first().id
            nodes.addAll(localNodes)
            subgoals.add(Subgoal(spec.id, goal.id, spec.description, dependencies = spec.dependencies))
        }

        // Wire cross-subgoal dependencies: the FIRST node of B depends on the
        // LAST node of A, so subgoals sequence B-after-A without threading the
        // dependency through every node of B. A dependency on a subgoal that
        // was NOT produced at all surfaces as a missing prerequisite (a
        // referenced node id that does not exist).
        nodes.indices.forEach { i ->
            val node = nodes[i]
            val owningSpec = specById[node.subgoalId!!] ?: return@forEach
            if (node.id != subgoalFirstNode[owningSpec.id]) return@forEach
            val crossPrereqs = owningSpec.dependencies.map { dep ->
                if (dep !in specById) listOf("${dep}:missing") else subgoalLastNode[dep]?.let { listOf(it) } ?: emptyList()
            }.flatten().distinct()
            if (crossPrereqs.isNotEmpty()) {
                nodes[i] = node.copy(prerequisites = (crossPrereqs + node.prerequisites).distinct())
            }
        }

        // Validate the DAG before accepting it.
        val graphValidation = validateGraph(nodes)
        if (!graphValidation.valid) {
            return PlanResult(
                graph = PlanGraph(
                    goalId = goal.id, goalDescription = goal.description, nodes = nodes,
                    status = PlanStatus.FAILED, failureCause = graphValidation.message
                ),
                subgoals = subgoals, validation = validation.copy(warning = graphValidation.message),
                ordering = emptyList(), nextActionable = null, created = false
            )
        }

        val refreshed = refreshStatus(PlanGraph(goalId = goal.id, goalDescription = goal.description, nodes = nodes))
        val ordering = topologicalOrder(refreshed.nodes) ?: emptyList()

        return PlanResult(
            graph = refreshed,
            subgoals = subgoals,
            validation = validation,
            ordering = ordering,
            nextActionable = nextActionable(refreshed.nodes),
            created = true
        )
    }

    // ------------------------------------------------------------------
    // Goal lifecycle
    // ------------------------------------------------------------------

    /** Move a goal to any lifecycle state. */
    fun transitionGoal(goal: Goal, status: GoalStatus): Goal = goal.copy(status = status)

    /** Move a subgoal to any lifecycle state. */
    fun transitionSubgoal(subgoal: Subgoal, status: SubgoalStatus): Subgoal = subgoal.copy(status = status)

    /**
     * Derive a subgoal's status from its plan nodes' statuses (after explicit
     * PAUSED/CANCELLED which always win).
     */
    fun deriveSubgoalStatus(subgoal: Subgoal, nodes: List<PlanNode>): SubgoalStatus {
        if (subgoal.status == SubgoalStatus.PAUSED || subgoal.status == SubgoalStatus.CANCELLED) {
            return subgoal.status
        }
        val subgoalNodes = nodes.filter { it.subgoalId == subgoal.id }
        if (subgoalNodes.isEmpty()) return SubgoalStatus.BLOCKED
        return when {
            subgoalNodes.all { it.isCompleted } -> SubgoalStatus.COMPLETED
            subgoalNodes.any { it.isFailed } -> SubgoalStatus.FAILED
            subgoalNodes.any { it.blocked } -> SubgoalStatus.BLOCKED
            subgoalNodes.any { it.isInProgress } -> SubgoalStatus.IN_PROGRESS
            else -> SubgoalStatus.PENDING
        }
    }

    /** Derive goal status from its subgoals' statuses. */
    fun deriveGoalStatus(goal: Goal, subgoals: List<Subgoal>): GoalStatus {
        if (goal.status == GoalStatus.PAUSED || goal.status == GoalStatus.CANCELLED) {
            return goal.status
        }
        if (subgoals.isEmpty()) return GoalStatus.CREATED
        return when {
            subgoals.all { it.status == SubgoalStatus.COMPLETED } -> GoalStatus.COMPLETED
            subgoals.any { it.status == SubgoalStatus.FAILED } -> GoalStatus.FAILED
            subgoals.any { it.status == SubgoalStatus.BLOCKED } -> GoalStatus.BLOCKED
            else -> GoalStatus.ACTIVE
        }
    }

    // ------------------------------------------------------------------
    // Plan node operations
    // ------------------------------------------------------------------

    /** Mark a node completed (record its outcome) and refresh blocking/status. */
    fun completeNode(graph: PlanGraph, nodeId: String, outcome: String? = null): PlanGraph {
        val updated = graph.nodes.map {
            if (it.id == nodeId) it.copy(status = PlanStepStatus.COMPLETED, outcome = outcome ?: it.outcome) else it
        }
        return refreshStatus(graph.copy(nodes = updated))
    }

    /** Mark a node failed with a cause; dependent nodes become blocked. */
    fun failNode(graph: PlanGraph, nodeId: String, cause: String): PlanGraph {
        val updated = graph.nodes.map {
            if (it.id == nodeId) it.copy(status = PlanStepStatus.FAILED, failureCause = cause) else it
        }
        return refreshStatus(graph.copy(nodes = updated, failureCause = cause))
    }

    /** Explicitly block a node. */
    fun blockNode(graph: PlanGraph, nodeId: String, reason: String): PlanGraph {
        val updated = graph.nodes.map {
            if (it.id == nodeId) it.copy(status = PlanStepStatus.BLOCKED, failureCause = reason) else it
        }
        return refreshStatus(graph.copy(nodes = updated))
    }

    /** Skip a node. */
    fun skipNode(graph: PlanGraph, nodeId: String): PlanGraph {
        val updated = graph.nodes.map {
            if (it.id == nodeId) it.copy(status = PlanStepStatus.SKIPPED) else it
        }
        return refreshStatus(graph.copy(nodes = updated))
    }

    // ------------------------------------------------------------------
    // Replanning
    // ------------------------------------------------------------------

    /**
     * Replan after a failed node. Does NOT restart the goal: the cause is
     * recorded, transitively-dependent nodes are invalidated (reset), a
     * replacement path is proposed by the strategy, and the plan continues
     * from the current (kept) completed state.
     */
    fun replan(graph: PlanGraph, failedNodeId: String, cause: String): PlanGraph {
        val failed = graph.nodes.firstOrNull { it.id == failedNodeId }
        if (failed == null) {
            return graph.copy(status = PlanStatus.FAILED, failureCause = "replan requested for unknown node $failedNodeId")
        }

        val downstream = transitivelyDownstream(graph.nodes, failedNodeId)
        val replacementSpecs = strategy.proposeReplacement(failed, cause)

        if (replacementSpecs.isEmpty()) {
            // No replacement knowledge -> the plan genuinely fails here.
            return refreshStatus(graph.copy(
                nodes = graph.nodes.map { if (it.id == failedNodeId) it.copy(status = PlanStepStatus.FAILED, failureCause = cause) else it },
                failureCause = "no replacement path for $failedNodeId: $cause"
            ))
        }

        val replacementNodes = replacementSpecs.mapIndexed { i, s ->
            PlanNode(
                id = "${failedNodeId}_r$i",
                description = s.description,
                action = s.action,
                expectedOutcome = s.expectedOutcome,
                prerequisites = if (i == 0) failed.prerequisites else listOf("${failedNodeId}_r${i - 1}"),
                subgoalId = failed.subgoalId,
                completionCondition = s.completionCondition
            )
        }
        val lastReplacement = replacementNodes.last().id

        // Keep completed + unrelated nodes; KEEP the failed node as a permanent
        // failure record (status FAILED) so partial-failure verdicts and plan
        // consumers can see what failed; reset the downstream chain to pending
        // and point its first link at the replacement path's tail.
        val downstreamIds = downstream.toSet()
        val kept = graph.nodes.filter { it.id !in downstreamIds }.map {
            if (it.id == failedNodeId) it.copy(status = PlanStepStatus.FAILED, failureCause = cause) else it
        }
        val rewired = graph.nodes.filter { it.id in downstreamIds }.map { d ->
            d.copy(
                prerequisites = d.prerequisites.map { if (it == failedNodeId) lastReplacement else it },
                status = PlanStepStatus.PENDING,
                blocked = false
            )
        }

        val newNodes = kept + replacementNodes + rewired
        return refreshStatus(graph.copy(
            nodes = newNodes,
            replanning = ReplanState(
                originalNodeId = failedNodeId,
                cause = cause,
                invalidatedNodeIds = downstream,
                replacementPaths = listOf(replacementNodes.map { it.id }),
                status = ReplanStatus.COMPLETED
            )
        ))
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun refreshStatus(graph: PlanGraph): PlanGraph {
        val recomputed = computeBlocked(graph.nodes)
        val status = derivePlanStatus(graph.copy(nodes = recomputed))
        return graph.copy(nodes = recomputed, status = status)
    }

    private fun derivePlanStatus(graph: PlanGraph): PlanStatus = when {
        graph.replanning?.status == ReplanStatus.REQUESTED ||
            graph.replanning?.status == ReplanStatus.IN_PROGRESS -> PlanStatus.REPLANNING
        graph.isComplete -> PlanStatus.COMPLETED
        // A failure already recovered by the active replacement path does not
        // fail the plan — the recorded failure stays visible on its node.
        graph.nodes.any { it.isFailed && it.id != graph.replanning?.originalNodeId } -> PlanStatus.FAILED
        graph.nodes.any { it.blocked || it.status == PlanStepStatus.BLOCKED } -> PlanStatus.BLOCKED
        graph.nodes.any { it.isInProgress } -> PlanStatus.EXECUTING
        graph.nodes.any { it.status == PlanStepStatus.PAUSED } -> PlanStatus.PAUSED
        else -> PlanStatus.PENDING
    }
}

/** All node ids reachable by following prerequisite edges backwards from [startId]. */
fun transitivelyDownstream(nodes: List<PlanNode>, startId: String): List<String> {
    val dependents = mutableMapOf<String, MutableList<String>>()
    nodes.forEach { n -> n.prerequisites.forEach { dep -> dependents.getOrPut(dep) { mutableListOf() }.add(n.id) } }

    val result = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    dependents[startId]?.let { queue.addAll(it) }
    seen.addAll(dependents[startId] ?: emptyList())

    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        result.add(id)
        dependents[id]?.forEach { d ->
            if (seen.add(d)) queue.addLast(d)
        }
    }
    return result
}
