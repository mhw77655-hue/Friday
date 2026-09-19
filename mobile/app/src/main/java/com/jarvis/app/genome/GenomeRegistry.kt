package com.jarvis.app.genome

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Genome Registry — the nervous system's index of genomes by capability and
 * the CURRENT BEST pointer (§14).
 *
 * Distinguishes from [GenomeArchive] (the full evolutionary memory) by keeping
 * only the *current* state the nervous system should route against: for each
 * capability, the best known genome. Promotion swaps the pointer; rollback
 * restores it. The archive still holds every genome ever created.
 *
 * Dormant when unused; no background processing.
 */
class GenomeRegistry {

    private val bestByCapability = ConcurrentHashMap<String, Genome>()

    private val _currentBest = MutableStateFlow<Genome?>(null)
    val currentBest: StateFlow<Genome?> = _currentBest.asStateFlow()

    /** Index a genome under every capability it declares (promotion). */
    fun register(genome: Genome) {
        if (!genome.isAccepted()) return
        for (cap in genome.capabilities) {
            val existing = bestByCapability[cap]
            if (existing == null || genome.version >= existing.version) {
                bestByCapability[cap] = genome
            }
        }
        _currentBest.value = genome
    }

    /** The best known genome for a capability, or null if none is healthy. */
    fun bestFor(capability: String): Genome? = bestByCapability[capability]

    fun has(capability: String): Boolean = bestByCapability.containsKey(capability)

    fun allBest(): Map<String, Genome> = bestByCapability.toMap()

    fun count(): Int = bestByCapability.size

    /**
     * Replace the current best outright (rollback). Passing null clears the
     * pointer so the capability is again a gap the evolution layer can fill.
     */
    fun setCurrentBest(genome: Genome?) {
        bestByCapability.clear()
        if (genome != null && genome.isAccepted()) {
            for (cap in genome.capabilities) bestByCapability[cap] = genome
        }
        _currentBest.value = genome
    }
}

/** A genome is a live current-best candidate only if it is accepted. */
fun Genome.isAccepted(): Boolean =
    healthState == GenomeHealth.HEALTHY || healthState == GenomeHealth.DEGRADED
