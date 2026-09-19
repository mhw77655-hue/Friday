package com.jarvis.app.research.universal

/**
 * Pluggable input channel for Fabricator's fiction-mining output.
 * Fabricator does not yet exist (PLANNED organ in SystemGraph); this
 * interface defines the contract so the research pipeline can consume
 * Fabricator output when it arrives, same shape as the pipeline's own
 * research channel output.
 *
 * Both the pipeline's own research and the Fabricator channel produce
 * [MechanismCandidate]s that land in the same [MechanismsStore].
 */
fun interface FabricatorChannel {
    /**
     * Produce candidate mechanisms from Fabricator's fiction-mining output.
     * The gap description is the same input the pipeline's own research uses.
     */
    suspend fun research(gapDescription: String): List<MechanismCandidate>
}
