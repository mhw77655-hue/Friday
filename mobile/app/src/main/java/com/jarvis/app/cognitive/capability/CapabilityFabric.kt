package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.CognitiveEngine.CognitiveEvent
import com.jarvis.app.cognitive.immune.ImmuneSystem
import com.jarvis.app.cognitive.immune.PartialFailureEvaluator
import com.jarvis.app.cognitive.immune.PartialFailureVerdict
import com.jarvis.app.cognitive.planning.PlanGraph

/**
 * CapabilityFabric — the composition root of 01E.
 *
 * Owns the single authoritative [CapabilityRegistry], [CapabilityResolver] and
 * [CapabilityInvoker], and wires each capability's identity into the existing
 * immune [ImmuneSystem] DependencyGraph at registration, so failures contain
 * and degrade by capability name exactly like any other subsystem.
 *
 * No event bus of its own: all observation flows through [CognitiveEvent].
 */
class CapabilityFabric(
    private val immune: ImmuneSystem,
    private val emit: (CognitiveEvent) -> Unit = {},
    private val defaultTimeoutMs: Long = 30_000,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    /** The authoritative registry (lifecycle changes emit CapabilityStateChanged). */
    val registry: CapabilityRegistry = CapabilityRegistry(
        onStateChanged = { id, from, to -> emit(CognitiveEvent.CapabilityStateChanged(id, from.name, to.name)) }
    )

    val resolver: CapabilityResolver = CapabilityResolver(registry, degradedLevelOf = { immune.degradation.levelOf(it) })
    val invoker: CapabilityInvoker = CapabilityInvoker(registry, resolver, immune, emit, defaultTimeoutMs, nowMs)

    /**
     * Register a capability and register its identity + declared dependencies
     * into the shared immune DependencyGraph. Duplicate ids are rejected
     * deterministically and no edge is added for a rejected registration.
     */
    fun register(capability: Capability): CapabilityRegistry.RegisterResult {
        val result = registry.register(capability)
        if (result is CapabilityRegistry.RegisterResult.Ok) {
            val d = capability.descriptor
            if (d.dependencies.isNotEmpty()) {
                immune.dependencyGraph.addRequirements(d.id, *d.dependencies.toTypedArray())
            }
            emit(CognitiveEvent.CapabilityRegistered(d.id, d.version, d.supportedOperations))
        }
        return result
    }

    /** Remove a capability. (Its declared edges stay — they are inert once the
     *  capability is gone and nothing fails on it.) */
    fun unregister(id: String): Boolean = registry.unregister(id)

    /**
     * Replace an existing capability implementation, keeping lifecycle state.
     * The immune DependencyGraph edges are reconciled against the new
     * descriptor, so a replacement that changes its declared dependencies does
     * not leave stale containment edges behind.
     */
    fun replace(id: String, capability: Capability): Boolean {
        val existing = registry.get(id) ?: return false
        if (!registry.replace(id, capability)) return false
        existing.descriptor.dependencies.forEach { immune.dependencyGraph.removeRequirement(id, it) }
        val d = capability.descriptor
        if (d.dependencies.isNotEmpty()) {
            immune.dependencyGraph.addRequirements(id, *d.dependencies.toTypedArray())
        }
        return true
    }

    /**
     * Fold a plan's capability invocations into the existing
     * [PartialFailureEvaluator] verdict: a failed capability must not fail the
     * whole plan — the verdict represents the failed step, blocked downstream
     * steps, usable partial result, alternative, and replan requirement.
     */
    fun partialFailureVerdict(graph: PlanGraph, invocations: List<CapabilityInvocation>): PartialFailureVerdict {
        val failures = invocations.associate { it.stepId to !it.success }
        return PartialFailureEvaluator.evaluate(graph, failures)
    }
}
