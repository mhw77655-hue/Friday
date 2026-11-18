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
        val implemented = genome.indexedCapabilities()
        if (implemented.isEmpty()) return
        for (cap in implemented) {
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

    /** Restore a complete capability index after an aborted multi-organ promotion. */
    fun restoreBestByCapability(snapshot: Map<String, Genome>, currentBest: Genome?) {
        bestByCapability.clear()
        bestByCapability.putAll(snapshot.filterValues { it.isAccepted() })
        _currentBest.value = currentBest?.takeIf { it.isAccepted() }
    }

    fun count(): Int = bestByCapability.size

    /**
     * Replace the current best outright (rollback). Passing null clears the
     * pointer so the capability is again a gap the evolution layer can fill.
     */
    fun setCurrentBest(genome: Genome?) {
        bestByCapability.clear()
        val implemented = genome?.takeIf { it.isAccepted() }?.indexedCapabilities().orEmpty()
        if (genome != null && implemented.isNotEmpty()) {
            for (cap in implemented) bestByCapability[cap] = genome
            _currentBest.value = genome
        } else {
            _currentBest.value = null
        }
    }
}

/** A genome is a live current-best candidate only if it is accepted. */
fun Genome.isAccepted(): Boolean =
    healthState == GenomeHealth.HEALTHY || healthState == GenomeHealth.DEGRADED

/**
 * A genome may carry inherited capability metadata, but a single persisted
 * SynthProgram artifact implements exactly one operation. Never advertise
 * inherited operations as callable through that one artifact.
 *
 * Legacy genomes without an artifact remain indexed as known-good metadata so
 * the gap detector can rebuild missing executable artifacts.
 */
private fun Genome.indexedCapabilities(): Set<String> = when {
    !artifactCapability.isNullOrBlank() -> setOf(artifactCapability)
    artifactPath != null -> emptySet()
    else -> capabilities
}
