package com.jarvis.app.cognitive.graft

import org.junit.Assert.*
import org.junit.Test

class CognitiveReconstitutionPlannerTest {

    private val target = SubstrateTarget(
        architectureFingerprint = "arch-a",
        tokenizerFingerprint = "tok-a",
        baseCheckpointFingerprint = "base-a",
        alreadyAvailableCapabilities = setOf("language-core")
    )

    private fun graft(
        id: String = "graft-1",
        capability: String = "debugging",
        method: CapabilityTransferMethod = CapabilityTransferMethod.KNOWLEDGE_DISTILLATION,
        payloadKind: CapabilityPayloadKind = CapabilityPayloadKind.DISTILLED_STUDENT,
        permits: Boolean = true,
        quality: Double = 0.9,
        memoryMb: Long = 100,
        sizeBytes: Long = 1_000,
        dependencies: Set<String> = setOf("language-core"),
        payloadOverrides: CapabilityPayload.() -> CapabilityPayload = { this }
    ): CapabilityGraft {
        val donor = DonorModelManifest(
            modelId = "donor-code",
            architectureFingerprint = "arch-a",
            tokenizerFingerprint = "tok-a",
            baseCheckpointFingerprint = "base-a",
            licenseId = "Apache-2.0",
            permitsDerivativeTransfer = permits
        )
        val payload = CapabilityPayload(
            kind = payloadKind,
            sha256 = "a".repeat(64),
            sizeBytes = sizeBytes,
            architectureFingerprint = "arch-a",
            tokenizerFingerprint = "tok-a",
            baseCheckpointFingerprint = "base-a",
            trainingCorpusFingerprint = "corpus-1",
            alignmentCorpusFingerprint = "align-1",
            sourceRepresentationDimensions = 64,
            targetRepresentationDimensions = 64,
            executableTestSuiteFingerprint = "tests-1",
            executableTestsPassed = true
        ).payloadOverrides()
        return CapabilityGraft(
            graftId = id,
            donor = donor,
            contract = CapabilityContract(
                capabilityId = capability,
                inputContractFingerprint = "input-v1",
                outputContractFingerprint = "output-v1",
                dependencies = dependencies
            ),
            method = method,
            payload = payload.copy(kind = payloadKind),
            evidence = CapabilityEvidence(
                evaluationSuiteFingerprint = "suite-1",
                sampleCount = 20,
                passedSamples = 18,
                qualityScore = quality,
                p50LatencyMs = 120,
                peakMemoryMb = memoryMb,
                evidenceRef = "evidence://donor-code/debugging"
            )
        )
    }

    private fun budget(
        memoryMb: Long = 1_000,
        bytes: Long = 10_000,
        quality: Double = 0.5
    ) = ReconstitutionBudget(memoryMb, bytes, quality)

    @Test
    fun `distilled capability with corpus and evidence is accepted without matching donor architecture`() {
        val donor = graft().copy(
            donor = graft().donor.copy(
                architectureFingerprint = "different-architecture",
                tokenizerFingerprint = "different-tokenizer",
                baseCheckpointFingerprint = "different-base"
            )
        )
        val result = CognitiveReconstitutionPlanner.assess(listOf(donor), target, budget())
        assertTrue(result.canCompose)
        assertEquals("debugging", result.acceptedGrafts.single().contract.capabilityId)
    }

    @Test
    fun `parameter delta rejects incompatible architecture tokenizer or base lineage`() {
        val candidate = graft(
            method = CapabilityTransferMethod.PARAMETER_DELTA,
            payloadKind = CapabilityPayloadKind.PARAMETER_DELTA
        ).copy(payload = graft().payload.copy(
            kind = CapabilityPayloadKind.PARAMETER_DELTA,
            architectureFingerprint = "other-architecture"
        ))
        val result = CognitiveReconstitutionPlanner.assess(listOf(candidate), target, budget())
        assertFalse(result.canCompose)
        assertTrue(result.rejectedGrafts.single().rejections.contains(
            GraftRejection.INCOMPATIBLE_PARAMETER_LINEAGE
        ))
    }

    @Test
    fun `adapter transfer requires matching architecture and tokenizer`() {
        val candidate = graft(
            method = CapabilityTransferMethod.ADAPTER,
            payloadKind = CapabilityPayloadKind.ADAPTER
        ).copy(donor = graft().donor.copy(tokenizerFingerprint = "different"))
        val result = CognitiveReconstitutionPlanner.assess(listOf(candidate), target, budget())
        assertTrue(result.rejectedGrafts.single().rejections.contains(
            GraftRejection.INCOMPATIBLE_PARAMETER_LINEAGE
        ))
    }

    @Test
    fun `unlicensed donor is rejected`() {
        val result = CognitiveReconstitutionPlanner.assess(
            listOf(graft(permits = false)), target, budget()
        )
        assertTrue(result.rejectedGrafts.single().rejections.contains(
            GraftRejection.DERIVATIVE_TRANSFER_NOT_LICENSED
        ))
    }

    @Test
    fun `algorithm extraction requires a passing executable test suite`() {
        val candidate = graft(
            method = CapabilityTransferMethod.ALGORITHM_EXTRACTION,
            payloadKind = CapabilityPayloadKind.EXECUTABLE_ALGORITHM
        ).copy(payload = graft().payload.copy(
            kind = CapabilityPayloadKind.EXECUTABLE_ALGORITHM,
            executableTestsPassed = false
        ))
        val result = CognitiveReconstitutionPlanner.assess(listOf(candidate), target, budget())
        assertTrue(result.rejectedGrafts.single().rejections.contains(
            GraftRejection.EXECUTABLE_ALGORITHM_UNTESTED
        ))
    }

    @Test
    fun `representation graft requires alignment corpus and positive dimensions`() {
        val candidate = graft(
            method = CapabilityTransferMethod.REPRESENTATION_ALIGNMENT,
            payloadKind = CapabilityPayloadKind.REPRESENTATION_BRIDGE
        ).copy(payload = graft().payload.copy(
            kind = CapabilityPayloadKind.REPRESENTATION_BRIDGE,
            alignmentCorpusFingerprint = null,
            targetRepresentationDimensions = 0
        ))
        val result = CognitiveReconstitutionPlanner.assess(listOf(candidate), target, budget())
        val issues = result.rejectedGrafts.single().rejections
        assertTrue(issues.contains(GraftRejection.MISSING_REPRESENTATION_ALIGNMENT))
        assertTrue(issues.contains(GraftRejection.REPRESENTATION_DIMENSION_INVALID))
    }

    @Test
    fun `composition budget is aggregated across accepted grafts`() {
        val candidates = listOf(
            graft(id = "a", capability = "debugging", memoryMb = 70),
            graft(id = "b", capability = "planning", memoryMb = 70)
        )
        val result = CognitiveReconstitutionPlanner.assess(
            candidates, target, budget(memoryMb = 100)
        )
        assertEquals(140L, result.estimatedAdditionalMemoryMb)
        assertEquals(2, result.rejectedGrafts.size)
        assertTrue(result.rejectedGrafts.all {
            it.rejections.contains(GraftRejection.RESOURCE_BUDGET_EXCEEDED)
        })
    }

    @Test
    fun `missing dependencies prevent composition`() {
        val result = CognitiveReconstitutionPlanner.assess(
            listOf(graft(dependencies = setOf("unavailable-capability"))),
            target,
            budget()
        )
        assertTrue(result.rejectedGrafts.single().rejections.contains(
            GraftRejection.MISSING_CAPABILITY_DEPENDENCY
        ))
    }

    @Test
    fun `low quality evidence is not accepted`() {
        val result = CognitiveReconstitutionPlanner.assess(
            listOf(graft(quality = 0.2)), target, budget(quality = 0.8)
        )
        assertTrue(result.rejectedGrafts.single().rejections.contains(
            GraftRejection.LOW_QUALITY_EVIDENCE
        ))
    }

    @Test
    fun `duplicate capabilities cannot be silently composed`() {
        val result = CognitiveReconstitutionPlanner.assess(
            listOf(
                graft(id = "a", capability = "debugging"),
                graft(id = "b", capability = "debugging")
            ),
            target,
            budget()
        )
        assertEquals(2, result.rejectedGrafts.size)
        assertTrue(result.rejectedGrafts.all {
            it.rejections.contains(GraftRejection.DUPLICATE_CAPABILITY)
        })
    }
}
