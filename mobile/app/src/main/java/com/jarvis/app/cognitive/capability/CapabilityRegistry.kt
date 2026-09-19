package com.jarvis.app.cognitive.capability

/**
 * The single authoritative capability registry for the execution path.
 *
 * Owns every registered [Capability], its [CapabilityLifecycle] state, and the
 * deterministic indexes (by operation / category / dependency). There is no
 * global mutable registration surface — the registry is constructed once and
 * passed to the resolver/invoker.
 *
 * Determinism: duplicate ids are rejected with a fixed result, and all list
 * queries return capabilities sorted by id.
 */
class CapabilityRegistry(
    /** Lifecycle-change hook (e.g. to emit CapabilityStateChanged). Pure by default. */
    private val onStateChanged: (id: String, from: CapabilityLifecycle, to: CapabilityLifecycle) -> Unit = { _, _, _ -> }
) {

    private val byId = LinkedHashMap<String, Capability>()
    private val byOperation = mutableMapOf<String, MutableList<String>>()
    private val byCategory = mutableMapOf<String, MutableList<String>>()
    private val byDependency = mutableMapOf<String, MutableList<String>>() // dep -> {requires}
    private val lifecycle = mutableMapOf<String, CapabilityLifecycle>()

    /** Result of a registration attempt. */
    sealed class RegisterResult {
        data class Ok(val id: String) : RegisterResult()
        /** Duplicate id — rejected deterministically. */
        data class Duplicate(val id: String) : RegisterResult()
    }

    /** Result of a lifecycle transition. */
    sealed class TransitionResult {
        data class Changed(val from: CapabilityLifecycle, val to: CapabilityLifecycle) : TransitionResult()
        /** Invalid transition for the current state. */
        data class Rejected(val from: CapabilityLifecycle, val target: CapabilityLifecycle) : TransitionResult()
    }

    /** Register a capability. Rejects duplicate ids deterministically. */
    fun register(capability: Capability): RegisterResult {
        val id = capability.descriptor.id
        if (byId.containsKey(id)) return RegisterResult.Duplicate(id)

        byId[id] = capability
        capability.descriptor.supportedOperations.forEach { op ->
            byOperation.getOrPut(op) { mutableListOf() }.add(id)
        }
        byCategory.getOrPut(capability.descriptor.category) { mutableListOf() }.add(id)
        capability.descriptor.dependencies.forEach { dep ->
            byDependency.getOrPut(dep) { mutableListOf() }.add(id)
        }
        lifecycle[id] = CapabilityLifecycle.REGISTERED
        return RegisterResult.Ok(id)
    }

    /** Remove a capability and all its indexes. Returns false when unknown. */
    fun unregister(id: String): Boolean {
        val capability = byId.remove(id) ?: return false
        capability.descriptor.supportedOperations.forEach { op ->
            byOperation[op]?.remove(id)
            if (byOperation[op].isNullOrEmpty()) byOperation.remove(op)
        }
        byCategory[capability.descriptor.category]?.remove(id)
        capability.descriptor.dependencies.forEach { dep ->
            byDependency[dep]?.remove(id)
        }
        lifecycle.remove(id)
        return true
    }

    /** Atomically replace an existing capability (keeps its lifecycle state). */
    fun replace(id: String, capability: Capability): Boolean {
        if (!byId.containsKey(id)) return false
        // Keep current lifecycle; swap implementation + indexes.
        val state = lifecycle[id] ?: CapabilityLifecycle.REGISTERED
        unregister(id)
        byId[id] = capability
        capability.descriptor.supportedOperations.forEach { op ->
            byOperation.getOrPut(op) { mutableListOf() }.add(id)
        }
        byCategory.getOrPut(capability.descriptor.category) { mutableListOf() }.add(id)
        capability.descriptor.dependencies.forEach { dep ->
            byDependency.getOrPut(dep) { mutableListOf() }.add(id)
        }
        lifecycle[id] = state
        return true
    }

    fun get(id: String): Capability? = byId[id]
    fun contains(id: String): Boolean = byId.containsKey(id)
    fun size(): Int = byId.size

    /** Deterministic list — sorted by id. */
    fun list(): List<Capability> = byId.keys.sorted().map { byId[it]!! }

    /** Deterministic — sorted by id. */
    fun lookupByOperation(operation: String): List<Capability> =
        (byOperation[operation] ?: emptyList()).sorted().map { byId[it]!! }

    /** Deterministic — sorted by id. */
    fun lookupByCategory(category: String): List<Capability> =
        (byCategory[category] ?: emptyList()).sorted().map { byId[it]!! }

    /** Capabilities that transitively-or-directly declare a dependency. Sorted. */
    fun lookupByDependency(dependencyId: String): List<Capability> =
        (byDependency[dependencyId] ?: emptyList()).sorted().map { byId[it]!! }

    fun stateOf(id: String): CapabilityLifecycle? = lifecycle[id]

    /** Deterministic lifecycle transition; invalid transitions rejected. */
    fun transitionTo(id: String, target: CapabilityLifecycle): TransitionResult {
        val from = lifecycle[id] ?: return TransitionResult.Rejected(CapabilityLifecycle.REGISTERED, target)
        if (!from.canTransitionTo(target)) return TransitionResult.Rejected(from, target)
        lifecycle[id] = target
        onStateChanged(id, from, target)
        return TransitionResult.Changed(from, target)
    }
}
