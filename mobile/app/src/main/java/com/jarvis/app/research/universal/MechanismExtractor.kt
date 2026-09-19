package com.jarvis.app.research.universal

import com.jarvis.app.research.ResearchDomain

/**
 * Mechanism Extractor (§7): the SOURCE FACT → MECHANISM → ABSTRACTION step.
 *
 * A raw source fact ("ants lay pheromone trails") is abstracted into a
 * mechanism ("route preference accumulates with recency and decays with
 * age"). The extraction *loses the domain specifics* — that loss is what makes
 * the mechanism transferable. The evidence record keeps the source fact
 * attached so the analogy is never mistaken for proof (status = ABSTRACTION
 * at most until JARVIS verifies it).
 */
object MechanismExtractor {

    data class ExtractedMechanism(
        val id: String,
        val name: String,
        val domain: ResearchDomain,
        val problemSolved: String,
        val properties: List<String>,
        val abstraction: String,
        val evidence: ResearchEvidence
    )

    /** Abstract a source fact into a transferable mechanism. */
    fun extract(source: SourceFact, domain: ResearchDomain): ExtractedMechanism {
        val abstraction = abstractFromDomain(domain, source.claim)
        return ExtractedMechanism(
            id = "mech_${source.id}",
            name = abstraction.name,
            domain = domain,
            problemSolved = abstraction.problemSolved,
            properties = abstraction.properties,
            abstraction = abstraction.principle,
            evidence = ResearchEvidence(
                id = "ev_${source.id}",
                status = EvidenceStatus.ABSTRACTION,
                supportingFacts = listOf(source),
                confidence = 0.5, // abstraction is plausible but unverified
                caveats = listOf("cross-domain transfer is a hypothesis until JARVIS experiments")
            )
        )
    }

    private data class Abstraction(
        val name: String,
        val problemSolved: String,
        val properties: List<String>,
        val principle: String
    )

    private fun abstractFromDomain(domain: ResearchDomain, claim: String): Abstraction = when (domain) {
        ResearchDomain.IMMUNE_SYSTEMS -> Abstraction(
            "immune memory", "repeated-exposure retention",
            listOf("clonal-selection", "durable-retention"),
            "retain a small high-affinity record per known threat; boost on re-exposure"
        )
        ResearchDomain.INSECTS -> Abstraction(
            "stigmergic trail", "collective pathfinding without a leader",
            listOf("positive-feedback", "evaporating-trail"),
            "reinforce recent success in a shared medium; let stale preference decay"
        )
        ResearchDomain.NEUROSCIENCE -> Abstraction(
            "sparse associative indexing", "storage of many items without a central directory",
            listOf("pointer-store", "associative-recall"),
            "keep a small sparse index of pointers; load the heavy payload only on recall"
        )
        ResearchDomain.DATABASES -> Abstraction(
            "ordered-index lookup", "key retrieval independent of store size",
            listOf("prefix-sharing", "ordered-keys"),
            "share traversal over common prefixes to amortize lookup cost"
        )
        ResearchDomain.NETWORKING -> Abstraction(
            "health-aware replica routing", "deliver a name to the nearest healthy server",
            listOf("hierarchy", "health-aware-replica", "caching"),
            "resolve a capability to its cheapest healthy replica and cache the resolution"
        )
        ResearchDomain.ECOLOGY -> Abstraction(
            "niche partitioning", "many consumers sharing one resource without exhaustion",
            listOf("specialization", "reduced-competition"),
            "give competing consumers non-overlapping subspaces so total throughput rises"
        )
        ResearchDomain.ECONOMICS -> Abstraction(
            "market clearing", "pricing that matches supply to demand",
            listOf("price-signal", "equilibrium"),
            "expose a price signal so allocation self-balances without a central scheduler"
        )
        ResearchDomain.SWARM_INTELLIGENCE -> Abstraction(
            "decentralized consensus", "group decision without a leader",
            listOf("local-rules", "emergence"),
            "aggregate many cheap local decisions into one robust global one"
        )
        else -> Abstraction(
            "cross-domain mechanism", "unspecified problem",
            listOf("abstract", "transferable"),
            "abstract the claim to its functional principle for JARVIS"
        )
    }
}
