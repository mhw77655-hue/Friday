package com.jarvis.app.builder

import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.nervous.CapabilityRouter

/**
 * Capability Gap Detector (§1, §15) — the entry point of the fundamental loop.
 *
 * Given a required capability it answers the first question of the law:
 * «does JARVIS already have this, or must he build it?»
 *
 * The check is three-layered:
 *   1. a live registered organism provides it  → no gap
 *   2. a healthy archived genome exists but its executable artifact is not
 *      resident → rebuild/recover is required (metadata alone is not executable)
 *   3. nothing → a real gap: search + synthesis is required
 */
class CapabilityGapDetector(
    private val registry: MicroSystemRegistry,
    private val genomeRegistry: GenomeRegistry,
    private val router: CapabilityRouter? = null,
    /** Native capabilities already resident in the primary cognitive fabric. */
    private val nativeProviderExists: (String) -> Boolean = { false }
) {

    fun detect(capability: String): GapAnalysis {
        val live = registry.provides(capability)
        if (live.isNotEmpty()) {
            return GapAnalysis(
                capability = capability,
                gap = false,
                status = "SATISFIED",
                providerId = live.first().id,
                routeCost = router?.costTier(live.first()) ?: 0
            )
        }
        if (nativeProviderExists(capability)) {
            return GapAnalysis(
                capability = capability,
                gap = false,
                status = "SATISFIED_NATIVE",
                providerId = "cognitive-capability-fabric"
            )
        }
        val bestGenome = genomeRegistry.bestFor(capability)
        if (bestGenome != null) {
            return GapAnalysis(
                capability = capability,
                gap = true,
                status = "KNOWN_GOOD_ARTIFACT_MISSING",
                genomeId = bestGenome.id,
                note = "genome metadata exists, but no executable artifact is resident; recover or rebuild before use"
            )
        }
        return GapAnalysis(
            capability = capability,
            gap = true,
            status = "GAP"
        )
    }

    /** The set of capabilities nothing provides (a gap scan across a surface). */
    fun gaps(required: Collection<String>): List<GapAnalysis> =
        required.map { detect(it) }.filter { it.gap }
}

data class GapAnalysis(
    val capability: String,
    val gap: Boolean,
    val status: String,
    val providerId: String? = null,
    val genomeId: String? = null,
    val routeCost: Int? = null,
    val note: String? = null
)
