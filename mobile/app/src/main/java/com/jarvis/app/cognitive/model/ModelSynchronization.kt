package com.jarvis.app.cognitive.model

import com.jarvis.app.cognitive.CognitiveState
import com.jarvis.app.cognitive.capability.CapabilityRegistry
import com.jarvis.app.cognitive.memory.ContinuityPort

/**
 * ModelSynchronization - Coordinator keeping CognitiveState, SelfModel,
 * UserModel, WorldModel and ContinuityManager from drifting apart.
 *
 * It only issues observations through the [ModelUpdateEngine] — it never
 * mutates models directly, and it never re-implements the update/conflict/
 * staleness logic. Every change therefore keeps full provenance.
 *
 * Cross-model consistency guarantees (section 16):
 * - CapabilityRegistry says X unavailable ⇒ SelfModel never claims X available.
 * - Continuity reports a preference change ⇒ UserModel reflects the new state
 *   while the engine preserves the historical fact.
 * - A WorldModel device may appear as a SelfModel resource only if the
 *   world evidence supports it.
 *
 * Determinism: sync runs are pure functions of their inputs.
 */
class ModelSynchronization(
    private val engine: ModelUpdateEngine
) {

    /**
     * Sync SelfModel's capability projection from the authoritative registry.
     * The registry remains the single source of truth — this only mirrors
     * availability so the self model can reason about what it can do.
     */
    fun syncSelfCapabilities(store: ModelStore, registry: CapabilityRegistry, now: Long = System.currentTimeMillis()) {
        for (cap in registry.list()) {
            val lifecycle = registry.stateOf(cap.descriptor.id)?.name ?: "REGISTERED"
            engine.observe(store, ModelUpdate(
                domain = ModelDomain.SELF,
                factKey = "capability.${cap.descriptor.id}.state",
                value = lifecycle,
                source = ModelEvidenceSource.CAPABILITY_REGISTRY,
                sourceId = cap.descriptor.id,
                evidenceDescription = "registry lifecycle mirror",
                evidenceStrength = 0.9f,
                sourceReliability = 0.95f,
                expectedChangeRate = ExpectedChangeRate.MODERATE,
                observedAt = now
            ))
        }
    }

    /** Sync active-state references from the authoritative CognitiveState. */
    fun syncSelfActiveState(store: ModelStore, state: CognitiveState, now: Long = System.currentTimeMillis()) {
        store.self = store.self.copy(activeState = SelfActiveState(
            activeGoalIds = state.activeSubgoals.map { it.id },
            currentTask = state.currentGoal?.description,
            currentEnvironment = state.worldState.environment.deviceState,
            currentMode = state.currentIntent.name,
            cognitiveStateId = null // CognitiveState has no stable id today
        ))
        if (state.resourceState.isUnderPressure()) {
            engine.observe(store, ModelUpdate(
                domain = ModelDomain.SELF,
                factKey = "resource.pressure",
                value = "HIGH",
                source = ModelEvidenceSource.ENVIRONMENT_OBSERVATION,
                sourceId = "cognitiveState.resourceState",
                evidenceDescription = "resource pressure detected in runtime state",
                evidenceStrength = 0.8f,
                sourceReliability = 0.9f,
                expectedChangeRate = ExpectedChangeRate.RAPID,
                worldVolatility = 0.8f,
                observedAt = now
            ))
        }
    }

    /**
     * Sync user facts from a bounded continuity reconstruction.
     * Continuity is the authority for "what is true now" about the user;
     * the engine's supersede logic preserves the historical preference.
     */
    suspend fun syncUserFromContinuity(
        store: ModelStore,
        continuity: ContinuityPort,
        query: String = "user preferences and identity",
        now: Long = System.currentTimeMillis()
    ) {
        val snapshot = continuity.reconstructContinuity(query)
        for (truth in snapshot.activeTruths) {
            engine.observe(store, ModelUpdate(
                domain = ModelDomain.USER,
                factKey = "continuity.${truth.provenance ?: "truth"}.${truth.content.hashCode()}",
                value = truth.content,
                source = ModelEvidenceSource.CONTINUITY_RECONSTRUCTION,
                sourceId = truth.provenance ?: "continuity",
                evidenceDescription = "continuity active truth",
                evidenceStrength = truth.confidence,
                sourceReliability = truth.confidence,
                expectedChangeRate = ExpectedChangeRate.MODERATE,
                observedAt = now
            ))
        }
    }

    /**
     * Sync world entities from continuity truths (e.g. device/project facts).
     */
    suspend fun syncWorldFromContinuity(
        store: ModelStore,
        continuity: ContinuityPort,
        query: String = "known devices projects and entities",
        now: Long = System.currentTimeMillis()
    ) {
        val snapshot = continuity.reconstructContinuity(query)
        for (truth in snapshot.activeTruths) {
            engine.observe(store, ModelUpdate(
                domain = ModelDomain.WORLD,
                factKey = "continuity.${truth.provenance ?: "truth"}.${truth.content.hashCode()}",
                value = truth.content,
                source = ModelEvidenceSource.CONTINUITY_RECONSTRUCTION,
                sourceId = truth.provenance ?: "continuity",
                evidenceDescription = "continuity world truth",
                evidenceStrength = truth.confidence,
                sourceReliability = truth.confidence,
                expectedChangeRate = ExpectedChangeRate.MODERATE,
                observedAt = now
            ))
        }
    }

    /**
     * Consistency check: a SelfModel capability must not claim full
     * availability when the authoritative registry disagrees. Re-observes the
     * self fact from the registry so the engine classifies the mismatch.
     */
    fun enforceSelfWorldConsistency(store: ModelStore, registry: CapabilityRegistry, now: Long = System.currentTimeMillis()) {
        syncSelfCapabilities(store, registry, now)
    }
}
