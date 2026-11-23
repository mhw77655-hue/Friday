package ai.friday.assistant.bootstrap.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostAcceptanceEvidenceTest {
    @Test
    fun allObservedFoundationProbesPassWithoutClaimingFullObjectiveAcceptance() {
        val evidence = HostAcceptanceEvidence.evaluate(
            HostAcceptanceEvidence.Checks(
                workspaceDirectoriesReady = true,
                workspaceReadWriteVerified = true,
                hostProfileObserved = true,
                resourcePolicyBounded = true,
                localInferenceAdmissionHonest = true,
                canonicalPlanBundled = true,
                appIdentityPresent = true
            ),
            sourceRevision = "test-revision"
        )

        assertEquals(HostAcceptanceEvidence.FOUNDATION_READY, evidence.getString("status"))
        assertEquals(7, evidence.getInt("passed_count"))
        assertEquals(0, evidence.getInt("failed_count"))
        assertEquals(
            HostAcceptanceEvidence.ACCEPTANCE_SCOPE,
            evidence.getString("scope")
        )
        assertTrue(evidence.getJSONArray("explicitly_not_proven").length() >= 5)
    }

    @Test
    fun anyFailedProbeBlocksFoundationReadiness() {
        val evidence = HostAcceptanceEvidence.evaluate(
            HostAcceptanceEvidence.Checks(
                workspaceDirectoriesReady = true,
                workspaceReadWriteVerified = false,
                hostProfileObserved = true,
                resourcePolicyBounded = true,
                localInferenceAdmissionHonest = true,
                canonicalPlanBundled = true,
                appIdentityPresent = true
            ),
            sourceRevision = "test-revision"
        )

        assertEquals(HostAcceptanceEvidence.FOUNDATION_BLOCKED, evidence.getString("status"))
        assertEquals(1, evidence.getInt("failed_count"))
        assertEquals(
            "workspace_read_write_verified",
            evidence.getJSONArray("failed_probes").getString(0)
        )
    }
}
