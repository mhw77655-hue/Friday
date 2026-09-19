package com.jarvis.app.research.universal

/**
 * Cross-Domain Mapper (§7): MECHANISM PROPERTIES → TRANSFERABLE PRINCIPLE →
 * JARVIS CANDIDATE IMPLEMENTATION → EXPERIMENT.
 *
 * Given a mechanism (from the catalog or the extractor), the mapper produces a
 * concrete candidate implementation sketch targeted at a JARVIS subsystem
 * (MEMORY / ROUTING / RESOURCE / NERVOUS / MUTANT), plus an experiment id the
 * evolution loop can run. The candidate is a *hypothesis to test* — it carries
 * the evidence record, so the biology is never mistaken for proof.
 */
object CrossDomainMapper {

    data class MappedCandidate(
        val id: String,
        val mechanismId: String,
        val targetSubsystem: String,
        val principle: String,
        val implementationSketch: String,
        val evidence: ResearchEvidence
    )

    private val subsystemHints = mapOf(
        "memory" to setOf("memory", "recall", "retention", "store", "index", "cache", "associative"),
        "routing" to setOf("route", "routing", "provider", "replica", "path", "trail", "preference"),
        "resource" to setOf("resource", "budget", "setpoint", "admission", "battery", "cpu", "thermal"),
        "nervous" to setOf("nervous", "consensus", "coordinate", "arbitrate", "dispatch"),
        "mutant" to setOf("workspace", "evict", "candidate", "environment", "cleanup")
    )

    /** Map a catalog entry to a JARVIS candidate implementation. */
    fun map(entry: UniversalCatalog.CatalogEntry): MappedCandidate {
        val subsystem = targetFor(entry)
        return MappedCandidate(
            id = "candidate_${entry.mechanismName.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
            mechanismId = entry.evidence.id,
            targetSubsystem = subsystem,
            principle = entry.transferablePrinciple,
            implementationSketch = entry.jarvisImplementation,
            evidence = entry.evidence
        )
    }

    /** Map an extracted mechanism to a candidate using the catalog's principle. */
    fun map(mechanism: MechanismExtractor.ExtractedMechanism): MappedCandidate {
        val subsystem = targetFor(mechanism.properties, mechanism.abstraction)
        return MappedCandidate(
            id = "candidate_${mechanism.id}",
            mechanismId = mechanism.id,
            targetSubsystem = subsystem,
            principle = mechanism.abstraction,
            implementationSketch = "JARVIS ${subsystem.uppercase()}: apply '${mechanism.name}' — ${mechanism.abstraction}",
            evidence = mechanism.evidence
        )
    }

    /** The JARVIS subsystem this mechanism best transfers into. */
    fun targetFor(entry: UniversalCatalog.CatalogEntry): String {
        val corpus = (listOf(entry.problemSolved, entry.transferablePrinciple, entry.jarvisImplementation) +
            entry.mechanismProperties).joinToString(" ").lowercase()
        return subsystemFor(corpus)
    }

    private fun targetFor(properties: List<String>, abstraction: String): String =
        subsystemFor((properties + abstraction).joinToString(" ").lowercase())

    private fun subsystemFor(corpus: String): String {
        var best = "nervous"
        var bestScore = 0
        for ((subsystem, hints) in subsystemHints) {
            // Count every occurrence of every hint — multiplicity matters
            // ("nearest healthy replica AND cache the resolution" is routing
            // evidence twice over, beating a single weak "cache" hint).
            val score = hints.sumOf { hint ->
                Regex(Regex.escape(hint)).findAll(corpus).count()
            }
            if (score > bestScore) {
                best = subsystem
                bestScore = score
            }
        }
        return best
    }
}
