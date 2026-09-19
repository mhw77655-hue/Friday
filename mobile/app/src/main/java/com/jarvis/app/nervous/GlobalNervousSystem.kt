package com.jarvis.app.nervous

import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.microsystem.MicroSystemContract
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.microsystem.SyncMessage
import com.jarvis.app.resource.ResourceGovernor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global Nervous System (§8) — the federation facade that coordinates multiple
 * intelligent organisms without performing their work.
 *
 *   GlobalNervousSystem
 *     ├─ CapabilityRouter     (who handles an event — cheapest valid)
 *     ├─ OrganismCoordinator  (multi-organism pipelines / fan-out)
 *     ├─ PredictiveRouter     (prepare the likely next step — no execution)
 *     ├─ ArbitrationEngine    (resolve conflicting organism decisions)
 *     ├─ MicroSystemRegistry  (discovery: «What are you? What can you do?»)
 *     └─ ResourceGovernor     (admission control, RAM/CPU/thermal)
 *
 * This is a *higher* coordination layer above the communication-body
 * [com.jarvis.app.body.BodyCoordinator] — it extends, never replaces it. The
 * federation's SyncFabric stays the transport; this class is the stable entry
 * point organisms and future layers query. Dormant when unused (§18).
 */
class GlobalNervousSystem(
    val router: CapabilityRouter,
    val coordinator: OrganismCoordinator,
    val predictive: PredictiveRouter,
    val arbitration: ArbitrationEngine,
    val registry: MicroSystemRegistry,
    val governor: ResourceGovernor,
    val failureSurface: FailureSurface
) {

    private val _state = MutableStateFlow(NervousState.DORMANT)
    val state: StateFlow<NervousState> = _state.asStateFlow()

    fun activate() { _state.value = NervousState.ACTIVE }
    fun suspendSystem() { _state.value = NervousState.DORMANT }

    /** «What can you do?» — every capability the federation can route to. */
    val capabilities: Set<String> get() = coordinator.readyCapabilities

    /** «What are you?» — manifest of every registered organism. */
    fun manifests(): List<com.jarvis.app.organism.OrganismManifests> = coordinator.allManifests()

    /** «Are you healthy?» — health snapshot of every registered organism. */
    fun health(): List<com.jarvis.app.organism.HealthManifest> =
        coordinator.allManifests().map { it.health }

    /** «What resources do you need?» — admission snapshot of every organism. */
    fun resources(): List<com.jarvis.app.organism.ResourceManifest> =
        coordinator.allManifests().map { it.resource }

    /** Register a newly-promoted organism (§22 "nervous system discovers it"). */
    fun register(system: MicroSystemContract) {
        registry.register(system)
    }

    /** Route one capability request through the cheapest valid provider. */
    suspend fun route(capability: String, inputs: Map<String, Any>): RouteResult {
        predictive.prepare(capability)
        return router.route(capability, inputs)
    }

    /** Ask the federation: «does any organism provide X?» */
    fun hasCapability(capability: String): Boolean = registry.provides(capability).isNotEmpty()

    /** Broadcast a synchronous message across the federation. */
    suspend fun publish(message: SyncMessage): Int = coordinator.broadcast(message)

    /** Best-effort admission snapshot for one genome (non-mutating). */
    fun admissionView(genome: com.jarvis.app.genome.Genome) =
        governor.requestAdmission(genome, "view")
}

/** Federation-level nervous state (§18 dormancy). */
enum class NervousState { DORMANT, ACTIVE, DEGRADED }
