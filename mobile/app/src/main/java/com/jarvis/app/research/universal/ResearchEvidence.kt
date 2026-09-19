package com.jarvis.app.research.universal

/**
 * Research Evidence (§7): the discipline that separates SOURCE FACT from
 * HYPOTHESIS — "do not treat biological analogy as proof."
 *
 * Every mechanism the Universal Research Engine proposes carries an evidence
 * record with a verification state and a ranked set of SOURCE FACTS that do
 * (or do not) support it. An unverified analogy can still be a promising
 * candidate — but it is flagged HYPOTHESIS, never presented as proven, and
 * the [UniversalResearchEngine] will only feed it to the evolution loop as an
 * experiment to run, not as a conclusion to adopt.
 */
enum class EvidenceStatus {
    /** Direct empirical observation from the source domain. */
    SOURCE_FACT,
    /** Extracted, abstracted mechanism — credible but cross-domain transfer unproven. */
    ABSTRACTION,
    /** Transferable principle proposed from the mechanism (needs verification). */
    HYPOTHESIS,
    /** Verified against JARVIS's own experiment (the strongest tier). */
    VERIFIED
}

data class SourceFact(
    val id: String,
    val domain: String,
    val claim: String,
    val reference: String,
    val observed: Boolean = true
)

data class ResearchEvidence(
    val id: String,
    val status: EvidenceStatus,
    val supportingFacts: List<SourceFact>,
    val confidence: Double,          // 0..1 — confidence in the transfer, not in the analogy
    val verifiedBy: String = "",     // experiment id once VERIFIED
    val caveats: List<String> = emptyList()
) {
    /** §7: an analogy that has never been verified is a hypothesis, not proof. */
    val isProof: Boolean get() = status == EvidenceStatus.VERIFIED && confidence >= 0.9
}
