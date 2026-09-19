package com.jarvis.app.cognitive.immune

import com.jarvis.app.failure.ContainmentStatus
import com.jarvis.app.failure.DegradationLevel

/**
 * Lightweight subsystem dependency model. `A requires B` means B failing can
 * only affect A and, transitively, whoever requires A. Subsystems that share
 * no edge are provably independent and are never touched by propagation.
 */
class DependencyGraph {
    private val requires = mutableMapOf<String, MutableSet<String>>() // A -> {B : A requires B}

    /** Register that [subsystem] requires [dependsOn]. */
    fun addRequirement(subsystem: String, dependsOn: String) {
        requires.getOrPut(subsystem) { mutableSetOf() }.add(dependsOn)
    }

    fun addRequirements(subsystem: String, vararg dependsOn: String) {
        dependsOn.forEach { addRequirement(subsystem, it) }
    }

    /** Remove one declared requirement. No-op when absent. */
    fun removeRequirement(subsystem: String, dependsOn: String) {
        requires[subsystem]?.remove(dependsOn)
        if (requires[subsystem].isNullOrEmpty()) requires.remove(subsystem)
    }

    /** Remove a set of declared requirements. No-op when absent. */
    fun removeRequirements(subsystem: String, dependsOn: Collection<String>) {
        dependsOn.forEach { removeRequirement(subsystem, it) }
    }

    /** Subsystems [subsystem] directly requires. */
    fun dependenciesOf(subsystem: String): Set<String> = requires[subsystem] ?: emptySet()

    /** Subsystems that directly require [subsystem]. */
    fun directDependents(subsystem: String): Set<String> =
        requires.filterValues { subsystem in it }.keys

    /**
     * Every subsystem transitively affected when [subsystem] fails — the
     * closure of the dependent relation. Sorted for determinism (the underlying
     * adjacency map has no stable iteration order).
     */
    fun affectedDependents(subsystem: String): List<String> {
        val seen = HashSet<String>()
        val queue = ArrayDeque<String>()
        directDependents(subsystem).forEach { seen.add(it); queue.add(it) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            directDependents(current).forEach { d ->
                if (seen.add(d)) queue.add(d)
            }
        }
        return seen.sorted()
    }

    /** Whether [dependent] transitively depends on [dependency]. */
    fun dependsOn(dependent: String, dependency: String): Boolean {
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        dependenciesOf(dependent).forEach { seen.add(it); queue.add(it) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == dependency) return true
            dependenciesOf(current).forEach { d -> if (seen.add(d)) queue.add(d) }
        }
        return false
    }

    /** A dependency cycle (list of subsystem ids), or null when acyclic. */
    fun findCycle(): List<String>? {
        val state = mutableMapOf<String, Int>()
        val stack = mutableListOf<String>()
        fun visit(id: String): List<String>? {
            when (state[id]) {
                1 -> {
                    val start = stack.indexOf(id)
                    if (start >= 0) return stack.subList(start, stack.size).toList()
                    return null
                }
                2 -> return null
            }
            state[id] = 1
            stack.add(id)
            (requires[id] ?: emptySet()).forEach { dep ->
                val cycle = visit(dep)
                if (cycle != null) return cycle
            }
            stack.removeAt(stack.size - 1)
            state[id] = 2
            return null
        }
        requires.keys.forEach { k -> if (state[k] != 2) visit(k)?.let { return it } }
        return null
    }
}

/**
 * Fault containment — the per-subsystem isolation status. One subsystem
 * failing must not mark unrelated subsystems as failed: each subsystem has its
 * own slot, and only dependency-aware propagation (via [DependencyGraph])
 * moves neighbours to DEGRADED.
 */
class ContainmentRegistry {
    private val statuses = mutableMapOf<String, ContainmentStatus>()

    fun statusOf(subsystem: String): ContainmentStatus = statuses[subsystem] ?: ContainmentStatus.HEALTHY

    fun isHealthy(subsystem: String): Boolean = statusOf(subsystem) == ContainmentStatus.HEALTHY

    /**
     * Set a subsystem's containment status. Returns (old, new) for event
     * emission; no-op (null) when unchanged.
     */
    fun setStatus(subsystem: String, status: ContainmentStatus): Pair<ContainmentStatus, ContainmentStatus>? {
        val old = statusOf(subsystem)
        if (old == status) return null
        statuses[subsystem] = status
        return old to status
    }

    fun all(): Map<String, ContainmentStatus> = statuses.toMap()
}

/**
 * Explicit degradation levels — a subsystem provides reduced functionality
 * instead of disappearing (FULL → DEGRADED → LIMITED → OFFLINE/UNAVAILABLE,
 * with RECOVERING on the way back).
 */
class DegradedModeController {
    private val levels = mutableMapOf<String, DegradationLevel>()

    fun levelOf(capability: String): DegradationLevel = levels[capability] ?: DegradationLevel.FULL

    /** Enter a degraded level. Returns (old, new), null when unchanged. */
    fun enter(capability: String, level: DegradationLevel): Pair<DegradationLevel, DegradationLevel>? {
        val old = levelOf(capability)
        if (old == level) return null
        levels[capability] = level
        return old to level
    }

    /** Return a capability to FULL. Returns (old, new), null when already full. */
    fun exit(capability: String): Pair<DegradationLevel, DegradationLevel>? {
        val old = levelOf(capability)
        if (old == DegradationLevel.FULL) return null
        levels[capability] = DegradationLevel.FULL
        return old to DegradationLevel.FULL
    }

    fun all(): Map<String, DegradationLevel> = levels.toMap()
}
