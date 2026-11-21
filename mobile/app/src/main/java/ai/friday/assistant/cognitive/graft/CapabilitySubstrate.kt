package ai.friday.assistant.cognitive.graft

/**
 * Typed transfer contract for acquiring model capabilities.
 *
 * A capability is evidenced behaviour, not an assumption that a donor's
 * skill occupies one removable layer. Payloads are content-addressed and
 * materialized outside this metadata layer.
 */
enum class CapabilityTransferMethod {
    PARAMETER_DELTA, ADAPTER, KNOWLEDGE_DISTILLATION, REPRESENTATION_ALIGNMENT, ALGORITHM_EXTRACTION
}

enum class CapabilityPayloadKind {
    PARAMETER_DELTA, ADAPTER, DISTILLED_STUDENT, REPRESENTATION_BRIDGE, EXECUTABLE_ALGORITHM
}

data class DonorModelManifest(
    val modelId: String,
    val architectureFingerprint: String,
    val tokenizerFingerprint: String,
    val baseCheckpointFingerprint: String,
    val licenseId: String,
    val permitsDerivativeTransfer: Boolean
)

data class CapabilityContract(
    val capabilityId: String,
    val version: Int = 1,
    val inputContractFingerprint: String,
    val outputContractFingerprint: String,
    val modalities: Set<String> = emptySet(),
    val dependencies: Set<String> = emptySet(),
    val description: String = ""
)

data class CapabilityPayload(
    val kind: CapabilityPayloadKind,
    val sha256: String,
    val sizeBytes: Long,
    val architectureFingerprint: String? = null,
    val tokenizerFingerprint: String? = null,
    val baseCheckpointFingerprint: String? = null,
    val trainingCorpusFingerprint: String? = null,
    val alignmentCorpusFingerprint: String? = null,
    val sourceRepresentationDimensions: Int? = null,
    val targetRepresentationDimensions: Int? = null,
    val executableTestSuiteFingerprint: String? = null,
    val executableTestsPassed: Boolean = false
)

data class CapabilityEvidence(
    val evaluationSuiteFingerprint: String,
    val sampleCount: Int,
    val passedSamples: Int,
    val qualityScore: Double,
    val p50LatencyMs: Long,
    val peakMemoryMb: Long,
    val evidenceRef: String
)

data class CapabilityGraft(
    val graftId: String,
    val donor: DonorModelManifest,
    val contract: CapabilityContract,
    val method: CapabilityTransferMethod,
    val payload: CapabilityPayload,
    val evidence: CapabilityEvidence
)

data class SubstrateTarget(
    val architectureFingerprint: String,
    val tokenizerFingerprint: String,
    val baseCheckpointFingerprint: String,
    val alreadyAvailableCapabilities: Set<String> = emptySet()
)

data class ReconstitutionBudget(
    val maxAdditionalMemoryMb: Long,
    val maxPayloadBytes: Long,
    val minimumQualityScore: Double = 0.0
)

enum class GraftRejection {
    MISSING_IDENTITY_OR_PROVENANCE,
    DERIVATIVE_TRANSFER_NOT_LICENSED,
    INVALID_PAYLOAD_DIGEST,
    INVALID_PAYLOAD_SIZE,
    INVALID_EVIDENCE,
    LOW_QUALITY_EVIDENCE,
    TRANSFER_METHOD_PAYLOAD_MISMATCH,
    INCOMPATIBLE_PARAMETER_LINEAGE,
    MISSING_DISTILLATION_CORPUS,
    MISSING_REPRESENTATION_ALIGNMENT,
    REPRESENTATION_DIMENSION_INVALID,
    EXECUTABLE_ALGORITHM_UNTESTED,
    MISSING_EXECUTABLE_TEST_SUITE,
    DUPLICATE_GRAFT_ID,
    DUPLICATE_CAPABILITY,
    MISSING_CAPABILITY_DEPENDENCY,
    RESOURCE_BUDGET_EXCEEDED
}

data class GraftAssessment(
    val graft: CapabilityGraft,
    val accepted: Boolean,
    val rejections: Set<GraftRejection>
)

data class ReconstitutionAssessment(
    val grafts: List<GraftAssessment>,
    val estimatedAdditionalMemoryMb: Long,
    val estimatedPayloadBytes: Long
) {
    val acceptedGrafts: List<CapabilityGraft>
        get() = grafts.filter { it.accepted }.map { it.graft }
    val rejectedGrafts: List<GraftAssessment>
        get() = grafts.filterNot { it.accepted }
    val canCompose: Boolean
        get() = grafts.isNotEmpty() && rejectedGrafts.isEmpty()
}

/**
 * Validates transfer semantics before constructing a candidate architecture.
 * This is not a weight extractor or neural merger: it prevents incompatible
 * tensors, unlicensed derivatives, untested code and unsupported skill claims
 * from being treated as inherited intelligence.
 */
object CognitiveReconstitutionPlanner {

    fun assess(
        grafts: List<CapabilityGraft>,
        target: SubstrateTarget,
        budget: ReconstitutionBudget
    ): ReconstitutionAssessment {
        val graftIdCounts = grafts.groupingBy { it.graftId }.eachCount()
        val capabilityCounts = grafts.groupingBy { it.contract.capabilityId }.eachCount()
        val candidateCapabilities = grafts.map { it.contract.capabilityId }.toSet()

        val assessments = grafts.map { graft ->
            val issues = linkedSetOf<GraftRejection>()
            val donor = graft.donor
            val payload = graft.payload
            val evidence = graft.evidence

            if (graft.graftId.isBlank() || donor.modelId.isBlank() || donor.licenseId.isBlank() ||
                graft.contract.capabilityId.isBlank() || graft.contract.version < 1 ||
                graft.contract.inputContractFingerprint.isBlank() ||
                graft.contract.outputContractFingerprint.isBlank() || evidence.evidenceRef.isBlank()
            ) issues += GraftRejection.MISSING_IDENTITY_OR_PROVENANCE

            if (!donor.permitsDerivativeTransfer) issues += GraftRejection.DERIVATIVE_TRANSFER_NOT_LICENSED
            if (!payload.sha256.matches(Regex("(?i)^[a-f0-9]{64}$"))) {
                issues += GraftRejection.INVALID_PAYLOAD_DIGEST
            }
            if (payload.sizeBytes <= 0L || evidence.peakMemoryMb < 0L || evidence.p50LatencyMs < 0L) {
                issues += GraftRejection.INVALID_PAYLOAD_SIZE
            }
            if (evidence.evaluationSuiteFingerprint.isBlank() || evidence.sampleCount <= 0 ||
                evidence.passedSamples !in 0..evidence.sampleCount || evidence.qualityScore !in 0.0..1.0
            ) issues += GraftRejection.INVALID_EVIDENCE
            if (evidence.qualityScore < budget.minimumQualityScore) {
                issues += GraftRejection.LOW_QUALITY_EVIDENCE
            }

            when (graft.method) {
                CapabilityTransferMethod.PARAMETER_DELTA -> {
                    if (payload.kind != CapabilityPayloadKind.PARAMETER_DELTA) {
                        issues += GraftRejection.TRANSFER_METHOD_PAYLOAD_MISMATCH
                    }
                    if (donor.architectureFingerprint.isBlank() ||
                        donor.tokenizerFingerprint.isBlank() ||
                        donor.baseCheckpointFingerprint.isBlank() ||
                        payload.architectureFingerprint != target.architectureFingerprint ||
                        payload.tokenizerFingerprint != target.tokenizerFingerprint ||
                        payload.baseCheckpointFingerprint != target.baseCheckpointFingerprint ||
                        donor.architectureFingerprint != target.architectureFingerprint ||
                        donor.tokenizerFingerprint != target.tokenizerFingerprint ||
                        donor.baseCheckpointFingerprint != target.baseCheckpointFingerprint
                    ) issues += GraftRejection.INCOMPATIBLE_PARAMETER_LINEAGE
                }
                CapabilityTransferMethod.ADAPTER -> {
                    if (payload.kind != CapabilityPayloadKind.ADAPTER) {
                        issues += GraftRejection.TRANSFER_METHOD_PAYLOAD_MISMATCH
                    }
                    if (donor.architectureFingerprint.isBlank() ||
                        donor.tokenizerFingerprint.isBlank() ||
                        donor.baseCheckpointFingerprint.isBlank() ||
                        payload.architectureFingerprint != target.architectureFingerprint ||
                        payload.tokenizerFingerprint != target.tokenizerFingerprint ||
                        payload.baseCheckpointFingerprint != target.baseCheckpointFingerprint ||
                        donor.architectureFingerprint != target.architectureFingerprint ||
                        donor.tokenizerFingerprint != target.tokenizerFingerprint ||
                        donor.baseCheckpointFingerprint != target.baseCheckpointFingerprint
                    ) issues += GraftRejection.INCOMPATIBLE_PARAMETER_LINEAGE
                }
                CapabilityTransferMethod.KNOWLEDGE_DISTILLATION -> {
                    if (payload.kind != CapabilityPayloadKind.DISTILLED_STUDENT) {
                        issues += GraftRejection.TRANSFER_METHOD_PAYLOAD_MISMATCH
                    }
                    if (payload.trainingCorpusFingerprint.isNullOrBlank()) {
                        issues += GraftRejection.MISSING_DISTILLATION_CORPUS
                    }
                }
                CapabilityTransferMethod.REPRESENTATION_ALIGNMENT -> {
                    if (payload.kind != CapabilityPayloadKind.REPRESENTATION_BRIDGE) {
                        issues += GraftRejection.TRANSFER_METHOD_PAYLOAD_MISMATCH
                    }
                    if (payload.alignmentCorpusFingerprint.isNullOrBlank()) {
                        issues += GraftRejection.MISSING_REPRESENTATION_ALIGNMENT
                    }
                    if ((payload.sourceRepresentationDimensions ?: 0) <= 0 ||
                        (payload.targetRepresentationDimensions ?: 0) <= 0
                    ) issues += GraftRejection.REPRESENTATION_DIMENSION_INVALID
                }
                CapabilityTransferMethod.ALGORITHM_EXTRACTION -> {
                    if (payload.kind != CapabilityPayloadKind.EXECUTABLE_ALGORITHM) {
                        issues += GraftRejection.TRANSFER_METHOD_PAYLOAD_MISMATCH
                    }
                    if (payload.executableTestSuiteFingerprint.isNullOrBlank()) {
                        issues += GraftRejection.MISSING_EXECUTABLE_TEST_SUITE
                    }
                    if (!payload.executableTestsPassed) {
                        issues += GraftRejection.EXECUTABLE_ALGORITHM_UNTESTED
                    }
                }
            }

            if ((graftIdCounts[graft.graftId] ?: 0) > 1) issues += GraftRejection.DUPLICATE_GRAFT_ID
            if ((capabilityCounts[graft.contract.capabilityId] ?: 0) > 1) {
                issues += GraftRejection.DUPLICATE_CAPABILITY
            }
            val missing = graft.contract.dependencies -
                target.alreadyAvailableCapabilities - candidateCapabilities
            if (missing.isNotEmpty()) issues += GraftRejection.MISSING_CAPABILITY_DEPENDENCY

            GraftAssessment(graft, issues.isEmpty(), issues)
        }

        // Admit the complete composition against one aggregate budget.
        val initiallyAccepted = assessments.filter { it.accepted }
        val memoryMb = initiallyAccepted.sumOf { it.graft.evidence.peakMemoryMb }
        val payloadBytes = initiallyAccepted.sumOf { it.graft.payload.sizeBytes }
        val overBudget = memoryMb > budget.maxAdditionalMemoryMb || payloadBytes > budget.maxPayloadBytes
        val finalAssessments = if (!overBudget) assessments else assessments.map { item ->
            if (!item.accepted) item else item.copy(
                accepted = false,
                rejections = item.rejections + GraftRejection.RESOURCE_BUDGET_EXCEEDED
            )
        }

        return ReconstitutionAssessment(finalAssessments, memoryMb, payloadBytes)
    }
}
