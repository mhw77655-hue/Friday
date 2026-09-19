package com.jarvis.app.anchor

import com.jarvis.app.cognitive.UncertaintyProfile
import com.jarvis.app.model.CognitiveAdmissionDecision
import com.jarvis.app.model.CognitiveAdmissionPolicy
import com.jarvis.app.model.ModelHandle
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.ModelTier
import com.jarvis.app.model.OrganRole

/**
 * ANCHOR-ENGINE-FOUNDATION — the first real implementation of the anchor
 * engine, wired into the production composition through its two real
 * dependencies (no spec existed for anchor_engine; these mappings are grounded
 * against the actual classes that exist in this repository):
 *
 *  - `model_manager` -> [ModelManager] — the ONE load/unload authority over
 *    the single model backend, gated by the ResourceGovernor. There is no
 *    second, parallel loader anywhere in the app.
 *  - `cognitive_runtime_gateway` -> [CognitiveAdmissionPolicy] — the real
 *    per-turn admission authority that decides which cognitive runtime tier
 *    (RESIDENT vs REASONING) serves each turn. It is deliberately the SAME
 *    instance the CognitiveEngine uses (see JarvisEngine.init) — a second
 *    decision authority would split the runtime's admission.
 *
 * A runtime is truthful to its anchor when the RESIDENT tier — the one tier
 * the cooldown sweep never auto-unloads — is loaded and held. That is the
 * process-wide stable base every organ can fall back to. [anchor] establishes
 * and holds that base through the single loading path, and [serve] drives each
 * turn through the runtime gateway, verifying the anchor still holds
 * afterwards and re-establishing it the moment a turn ever finds it missing.
 */
class AnchorEngine(
    private val modelManager: ModelManager,
    private val gateway: CognitiveAdmissionPolicy
) {

    /** Whether the resident anchor tier currently holds a loaded model. */
    fun anchorHeld(): Boolean = modelManager.isTierLoaded(ModelTier.RESIDENT)

    /**
     * Establish or confirm the resident anchor. The RESIDENT tier is the only
     * tier the cooldown sweep never auto-unloads, so this is a one-shot
     * holding operation, not a per-turn wake.
     *
     * @return the resident handle, with [AnchorResult.recovered] true exactly
     * when the anchor tier was missing and had to be loaded anew.
     */
    suspend fun anchor(): AnchorResult {
        val wasHeld = anchorHeld()
        val handle = modelManager.request(OrganRole.RESIDENT, task = "anchor-engine.anchor")
        return AnchorResult(handle = handle, recovered = !wasHeld)
    }

    /**
     * Serve one turn through the real cognitive runtime gateway: decide the
     * tier on [uncertainty], wake the on-demand reasoning organ through the
     * same ModelManager when the gateway asks for it, and afterwards verify
     * the resident anchor is still held — re-establishing the moment a turn
     * ever finds it missing, so no turn completes on an unanchored runtime.
     */
    suspend fun serve(uncertainty: UncertaintyProfile): AnchorServeOutcome {
        val admitted = gateway.serveTurn(uncertainty) {
            modelManager.wake(OrganRole.REASONING, task = "anchor-engine.serve")
        }
        var recovered = false
        if (!anchorHeld()) {
            modelManager.wake(OrganRole.RESIDENT, task = "anchor-engine.recover")
            recovered = true
        }
        return AnchorServeOutcome(
            decision = admitted.decision,
            requestedTier = admitted.requestedTier,
            servedTier = admitted.servedTier,
            degraded = admitted.degraded,
            anchorHeld = anchorHeld(),
            anchorRecovered = recovered,
            servedHandle = admitted.servedHandle
        )
    }
}

/** Result of establishing the resident anchor. */
data class AnchorResult(
    val handle: ModelHandle,
    /** True when the anchor tier was missing and had to be loaded anew. */
    val recovered: Boolean
)

/**
 * Observable outcome of serving one turn through the anchor: which tier the
 * gateway requested, which tier actually served, whether admission degraded,
 * whether the resident anchor held through the turn, and whether the anchor
 * had to be re-established. This is what makes the anchor's behavior
 * verifiable through the real production call path.
 */
data class AnchorServeOutcome(
    val decision: CognitiveAdmissionDecision,
    val requestedTier: ModelTier,
    val servedTier: ModelTier,
    val degraded: Boolean,
    val anchorHeld: Boolean,
    val anchorRecovered: Boolean,
    /** The handle the turn is served from, when the reasoning organ was woken. */
    val servedHandle: ModelHandle? = null
)