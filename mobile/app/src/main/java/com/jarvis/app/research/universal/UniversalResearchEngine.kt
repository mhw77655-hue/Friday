package com.jarvis.app.research.universal

import com.jarvis.app.cloud.CloudModelRouter
import com.jarvis.app.research.Mechanism
import com.jarvis.app.research.ResearchDomain
import com.jarvis.app.research.universal.CrossDomainMapper.MappedCandidate

/**
 * Universal Research Engine (§7) — JARVIS's highest-level research mechanism:
 * search across domains, extract mechanisms, map them to candidate
 * implementations, and hand them to the evolution loop as experiments.
 *
 *   DOMAIN → MECHANISM → PROBLEM SOLVED → PROPERTIES → TRANSFERABLE PRINCIPLE
 *     → JARVIS CANDIDATE → EXPERIMENT
 *
 * The engine is *demand-driven*: a research request is created only when a
 * capability gap or a hard problem reaches the evolution layer (§18 — no
 * permanent autonomous research loop). The catalog is the built-in knowledge
 * base; web/academic providers plug in behind the same evidence contract.
 */
class UniversalResearchEngine(
    private val catalog: UniversalCatalog = UniversalCatalog,
    private val extractor: MechanismExtractor = MechanismExtractor,
    private val mapper: CrossDomainMapper = CrossDomainMapper,
    private val cloudRouter: CloudModelRouter? = null,
    private val fabricatorChannel: FabricatorChannel? = null,
    private val store: MechanismsStore? = null
) {

    /** The candidate JARVIS implementations the engine hands to evolution. */
    data class ResearchResult(
        val problemStatement: String,
        val candidates: List<MappedCandidate>,
        val mechanismsSurveyed: Int,
        val verifiedOnly: Boolean
    )

    /**
     * Result of the persisted research pipeline. [storedCount] is the number
     * of candidates written into the [MechanismsStore].
     */
    data class PersistedResearchResult(
        val problemStatement: String,
        val candidates: List<MechanismCandidate>,
        val channels: Set<CandidateSource>,
        val storedCount: Int
    )

    /**
     * Research a problem. [requireVerified] filters out unproven analogies
     * (per §7); the default keeps them as hypotheses to *test*, clearly marked.
     */
    fun research(
        problemStatement: String,
        targetDomains: Set<ResearchDomain> = emptySet(),
        requireVerified: Boolean = false,
        maxResults: Int = 6
    ): ResearchResult {
        val catalogCandidates = catalog.search(problemStatement, targetDomains)
            .map(mapper::map)

        // Supplement the catalog with a direct extraction if the problem names
        // a domain-specific behavior — shows the full SOURCE FACT → ABSTRACTION
        // path rather than only catalog lookups.
        val extracted = if (targetDomains.isNotEmpty()) {
            val domain = targetDomains.first()
            extractor.extract(
                SourceFact(
                    id = "fact_${domain.name.lowercase()}_direct",
                    domain = domain.name,
                    claim = "observed behavior relevant to: $problemStatement",
                    reference = "problem statement"
                ),
                domain
            )
        } else null

        val mapped = buildList {
            addAll(catalogCandidates)
            extracted?.let { add(mapper.map(it)) }
        }

        val filtered = if (requireVerified) mapped.filter { it.evidence.isProof } else mapped
        val limited = filtered.take(maxResults)

        return ResearchResult(
            problemStatement = problemStatement,
            candidates = limited,
            mechanismsSurveyed = limited.size,
            verifiedOnly = requireVerified
        )
    }

    /**
     * The transferable principles surfaced for a problem (for diagnostics).
     */
    fun principles(problemStatement: String, targetDomains: Set<ResearchDomain> = emptySet()): List<String> =
        catalog.search(problemStatement, targetDomains).map { it.transferablePrinciple }

    /**
     * Full persisted research pipeline: given a capability gap description,
     * research it through every wired channel (local catalog always;
     * CloudModelRouter reasoning and Fabricator fiction-mining when wired) and
     * write every candidate [MechanismCandidate] into the [MechanismsStore].
     *
     * Channels are consulted independently and ALL results land in the same
     * store, so cloud-research and Fabricator output are interchangeable input
     * paths to one real table.
     */
    suspend fun researchPersist(
        gapDescription: String,
        targetDomains: Set<ResearchDomain> = emptySet(),
        maxResults: Int = 10
    ): PersistedResearchResult {
        val candidates = mutableListOf<MechanismCandidate>()
        val channels = mutableSetOf<CandidateSource>()

        // Channel 1: local catalog + extraction (the engine's own research).
        val local = research(gapDescription, targetDomains, requireVerified = false, maxResults = maxResults)
        candidates += local.candidates.map { toMechanismCandidate(it) }
        channels += CandidateSource.LOCAL_CATALOG

        // Channel 2: cloud reasoning via the real CloudModelRouter.
        if (cloudRouter != null) {
            val provider = CloudResearchProvider(cloudRouter)
            val cloudMechanisms = provider.search(gapDescription, targetDomains.ifEmpty { ResearchDomain.values().toSet() }, emptyList())
            candidates += cloudMechanisms.take(maxResults).map { toMechanismCandidate(gapDescription, it) }
            channels += CandidateSource.CLOUD_REASONING
        }

        // Channel 3: Fabricator's fiction-mining output channel (same shape).
        if (fabricatorChannel != null) {
            candidates += fabricatorChannel.research(gapDescription).take(maxResults)
            channels += CandidateSource.FABRICATOR
        }

        val storedCount = store?.let { s -> candidates.count { s.write(it) } } ?: 0

        return PersistedResearchResult(
            problemStatement = gapDescription,
            candidates = candidates,
            channels = channels,
            storedCount = storedCount
        )
    }

    private fun toMechanismCandidate(mapped: MappedCandidate): MechanismCandidate = MechanismCandidate(
        id = "local_${mapped.id}",
        name = mapped.principle,
        description = mapped.implementationSketch,
        structure = "local catalog mechanism mapped to ${mapped.targetSubsystem}",
        inputs = listOf("gap description"),
        outputs = listOf("implementable candidate for ${mapped.targetSubsystem}"),
        constraints = listOf("cross-domain transfer is a hypothesis until JARVIS experiments"),
        confidence = mapped.evidence.confidence,
        source = CandidateSource.LOCAL_CATALOG,
        targetDomain = ResearchDomain.OTHER,
        principle = mapped.principle,
        implementationSketch = mapped.implementationSketch,
        evidence = mapped.evidence
    )

    private fun toMechanismCandidate(gapDescription: String, mechanism: Mechanism): MechanismCandidate = MechanismCandidate(
        id = "cloud_${mechanism.id}",
        name = mechanism.name,
        description = mechanism.description,
        structure = mechanism.properties.toList().joinToString(", ") { "${it.first}=${it.second}" }
            .ifEmpty { "category=${mechanism.category}" },
        inputs = listOf(gapDescription),
        outputs = listOf("adapted mechanism for the gap: $gapDescription"),
        constraints = listOf(mechanism.provenance.sourceReference, "cloud-proposed, unverified"),
        confidence = mechanism.provenance.verified.takeIf { it }?.let { 0.9 } ?: 0.5,
        source = CandidateSource.CLOUD_REASONING,
        targetDomain = mechanism.domain,
        principle = mechanism.description,
        implementationSketch = "Adapt '${mechanism.name}' to resolve: $gapDescription",
        evidence = ResearchEvidence(
            id = "ev_${mechanism.id}",
            status = EvidenceStatus.HYPOTHESIS,
            supportingFacts = listOf(
                SourceFact(mechanism.id, mechanism.domain.name, mechanism.description, mechanism.provenance.sourceReference)
            ),
            confidence = 0.5,
            caveats = listOf("cloud-proposed mechanism is a hypothesis until JARVIS experiments")
        )
    )
}