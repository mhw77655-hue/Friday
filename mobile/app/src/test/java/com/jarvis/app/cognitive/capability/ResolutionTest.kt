package com.jarvis.app.cognitive.capability

import com.jarvis.app.failure.DegradationLevel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResolutionTest {

    private lateinit var registry: CapabilityRegistry
    private lateinit var resolver: CapabilityResolver

    @Before
    fun setUp() {
        // Each test gets a fresh registry; capabilities from one test must
        // never leak into the next test's candidate set.
        registry = CapabilityRegistry()
        resolver = CapabilityResolver(registry, degradedLevelOf = { DegradationLevel.FULL })
    }

    private fun register(c: FakeCapability) = registry.register(c)

    @Test
    fun `matching operation resolves to the capability`() {
        register(FakeCapability(descriptor("CALC", operation = "compute")))
        val r = resolver.resolve(request(action = "compute"), emptySet())
        assertTrue(r is Resolution.Success)
        assertEquals("CALC", (r as Resolution.Success).capability.descriptor.id)
    }

    @Test
    fun `unknown operation is not found`() {
        register(FakeCapability(descriptor("CALC", operation = "compute")))
        assertEquals(
            ResolutionReason.OPERATION_NOT_FOUND,
            (resolver.resolve(request(action = "fly"), emptySet()) as Resolution.Failure).reason
        )
    }

    @Test
    fun `disabled capability is rejected`() {
        register(FakeCapability(descriptor("CALC", operation = "compute", enabled = false)))
        assertEquals(
            ResolutionReason.CAPABILITY_DISABLED,
            (resolver.resolve(request(action = "compute"), emptySet()) as Resolution.Failure).reason
        )
    }

    @Test
    fun `offline capability is unavailable`() {
        register(FakeCapability(descriptor("CALC", operation = "compute", availability = Availability.OFFLINE)))
        assertEquals(
            ResolutionReason.CAPABILITY_UNAVAILABLE,
            (resolver.resolve(request(action = "compute"), emptySet()) as Resolution.Failure).reason
        )
    }

    @Test
    fun `missing dependency is rejected`() {
        register(FakeCapability(descriptor("BROWSER", operation = "search", deps = setOf("NETWORK"))))
        // NETWORK not registered
        assertEquals(
            ResolutionReason.DEPENDENCY_UNAVAILABLE,
            (resolver.resolve(request(action = "search"), emptySet()) as Resolution.Failure).reason
        )
    }

    @Test
    fun `missing permission is rejected`() {
        register(FakeCapability(
            descriptor("NET", operation = "fetch", permissions = setOf(CapabilityPermission.NETWORK))
        ))
        val r = resolver.resolve(request(action = "fetch"), granted = emptySet())
        assertEquals(ResolutionReason.PERMISSION_DENIED, (r as Resolution.Failure).reason)
    }

    @Test
    fun `granted permission allows resolution`() {
        register(FakeCapability(
            descriptor("NET", operation = "fetch", permissions = setOf(CapabilityPermission.NETWORK))
        ))
        val r = resolver.resolve(request(action = "fetch"), granted = setOf(CapabilityPermission.NETWORK))
        assertTrue(r is Resolution.Success)
    }

    @Test
    fun `ambiguous when multiple eligible capabilities match`() {
        register(FakeCapability(descriptor("A", operation = "compute")))
        register(FakeCapability(descriptor("B", operation = "compute")))
        val r = resolver.resolve(request(action = "compute"), emptySet())
        assertEquals(ResolutionReason.AMBIGUOUS, (r as Resolution.Failure).reason)
    }

    private fun evidenceDirectory(): File = File.createTempFile("friday-resolution-evidence-", ".dir").apply {
        delete()
        mkdirs()
    }

    private fun recordOutcome(
        ledger: CapabilityEvidenceLedger,
        capabilityId: String,
        outcome: EvidenceOutcome,
        at: Long,
        verified: Boolean = true
    ) {
        ledger.record(
            CapabilityEvidence(
                capabilityId = capabilityId,
                operation = "compute",
                outcome = outcome,
                observedAtMs = at,
                source = "resolver-test",
                verified = verified,
                eventId = "$capabilityId:$at:${outcome.name}"
            )
        )
    }

    @Test
    fun `repeated independently verified outcomes change a later capability choice`() {
        val directory = evidenceDirectory()
        try {
            val ledger = CapabilityEvidenceLedger(directory)
            val learnedResolver = CapabilityResolver(
                registry = registry,
                degradedLevelOf = { DegradationLevel.FULL },
                evidenceLedger = ledger
            )
            register(FakeCapability(descriptor("A", operation = "compute")))
            register(FakeCapability(descriptor("B", operation = "compute")))

            assertEquals(
                ResolutionReason.AMBIGUOUS,
                (learnedResolver.resolve(request(action = "compute"), emptySet()) as Resolution.Failure).reason
            )

            // Only independent postcondition verification earns routing weight.
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 1)
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 2)
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 3)
            recordOutcome(ledger, "B", EvidenceOutcome.SUCCEEDED, at = 4)
            recordOutcome(ledger, "B", EvidenceOutcome.SUCCEEDED, at = 5)

            val result = learnedResolver.resolve(request(action = "compute"), emptySet()) as Resolution.Success
            assertEquals("A", result.capability.descriptor.id)
            assertEquals(ResolutionSelectionBasis.VERIFIED_OUTCOME_HISTORY, result.selectionBasis)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `one candidate history cannot outrank an unobserved alternative`() {
        val directory = evidenceDirectory()
        try {
            val ledger = CapabilityEvidenceLedger(directory)
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 1)
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 2)
            register(FakeCapability(descriptor("A", operation = "compute")))
            register(FakeCapability(descriptor("B", operation = "compute")))
            val learnedResolver = CapabilityResolver(
                registry = registry,
                degradedLevelOf = { DegradationLevel.FULL },
                evidenceLedger = ledger
            )
            assertEquals(
                ResolutionReason.AMBIGUOUS,
                (learnedResolver.resolve(request(action = "compute"), emptySet()) as Resolution.Failure).reason
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `a later verified failure removes previously earned reusable selection evidence`() {
        val directory = evidenceDirectory()
        try {
            val ledger = CapabilityEvidenceLedger(directory)
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 1)
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 2)
            assertTrue(ledger.knowledge().single { it.capabilityId == "A" }.reusable)
            recordOutcome(ledger, "A", EvidenceOutcome.FAILED, at = 3)

            register(FakeCapability(descriptor("A", operation = "compute")))
            register(FakeCapability(descriptor("B", operation = "compute")))
            val learnedResolver = CapabilityResolver(
                registry = registry,
                degradedLevelOf = { DegradationLevel.FULL },
                evidenceLedger = ledger
            )
            assertEquals(
                ResolutionReason.AMBIGUOUS,
                (learnedResolver.resolve(request(action = "compute"), emptySet()) as Resolution.Failure).reason
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `equal verified outcome histories preserve ambiguity`() {
        val directory = evidenceDirectory()
        try {
            val ledger = CapabilityEvidenceLedger(directory)
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 1)
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 2)
            recordOutcome(ledger, "B", EvidenceOutcome.SUCCEEDED, at = 3)
            recordOutcome(ledger, "B", EvidenceOutcome.SUCCEEDED, at = 4)
            register(FakeCapability(descriptor("A", operation = "compute")))
            register(FakeCapability(descriptor("B", operation = "compute")))
            val learnedResolver = CapabilityResolver(
                registry = registry,
                degradedLevelOf = { DegradationLevel.FULL },
                evidenceLedger = ledger
            )
            assertEquals(
                ResolutionReason.AMBIGUOUS,
                (learnedResolver.resolve(request(action = "compute"), emptySet()) as Resolution.Failure).reason
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `permissions and eligibility are checked before outcome guidance`() {
        val directory = evidenceDirectory()
        try {
            val ledger = CapabilityEvidenceLedger(directory)
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 1)
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 2)
            register(FakeCapability(descriptor(
                "A", operation = "compute", permissions = setOf(CapabilityPermission.NETWORK)
            )))
            register(FakeCapability(descriptor("B", operation = "compute")))
            val learnedResolver = CapabilityResolver(
                registry = registry,
                degradedLevelOf = { DegradationLevel.FULL },
                evidenceLedger = ledger
            )
            val result = learnedResolver.resolve(request(action = "compute"), emptySet()) as Resolution.Success
            assertEquals("B", result.capability.descriptor.id)
            assertEquals(ResolutionSelectionBasis.DETERMINISTIC, result.selectionBasis)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `unverified success and blocked outcomes cannot guide selection`() {
        val directory = evidenceDirectory()
        try {
            val ledger = CapabilityEvidenceLedger(directory)
            recordOutcome(ledger, "A", EvidenceOutcome.SUCCEEDED, at = 1, verified = false)
            recordOutcome(ledger, "A", EvidenceOutcome.BLOCKED, at = 2, verified = true)
            recordOutcome(ledger, "A", EvidenceOutcome.SKIPPED, at = 3, verified = true)
            register(FakeCapability(descriptor("A", operation = "compute")))
            register(FakeCapability(descriptor("B", operation = "compute")))
            val learnedResolver = CapabilityResolver(
                registry = registry,
                degradedLevelOf = { DegradationLevel.FULL },
                evidenceLedger = ledger
            )
            assertEquals(
                ResolutionReason.AMBIGUOUS,
                (learnedResolver.resolve(request(action = "compute"), emptySet()) as Resolution.Failure).reason
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `resource-degraded capability is rejected at resolution`() {
        val r = CapabilityRegistry()
        r.register(FakeCapability(descriptor("CALC", operation = "compute")))
        val degraded = CapabilityResolver(r, degradedLevelOf = { DegradationLevel.OFFLINE })
        assertEquals(
            ResolutionReason.CAPABILITY_UNAVAILABLE,
            (degraded.resolve(request(action = "compute"), emptySet()) as Resolution.Failure).reason
        )
    }
}
