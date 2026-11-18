package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.execution.ExecutionResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInCapabilityEvidenceVerifierTest {
    private fun invocation(
        capabilityId: String = "android.media_control",
        action: String = "media.play",
        verified: Boolean = true,
        outputBackend: String = "media-session",
        evidenceBackend: String = "media-session",
        stage: String = "VERIFIED",
        refused: Boolean = false
    ): CapabilityInvocation {
        val capability = FakeCapability(descriptor(capabilityId, operation = action))
        val result = ExecutionResult.ok(
            output = mapOf("verified" to verified.toString(), "backend_id" to outputBackend),
            evidence = mapOf("backend_id" to evidenceBackend, "verification_stage" to stage)
        )
        return CapabilityInvocation(
            requestId = "plan|step|$action",
            capabilityId = capabilityId,
            operation = action,
            stepId = "step",
            resolution = Resolution.Success(capability, action),
            result = result,
            startedAt = 1L,
            durationMs = 2L,
            state = CapabilityLifecycle.AVAILABLE,
            refused = refused
        )
    }

    @Test
    fun `verified Android media backend evidence is accepted`() {
        val request = request(action = "media.play")
        assertTrue(BuiltInCapabilityEvidenceVerifier.verify(request, invocation()))
    }

    @Test
    fun `invocation success without verified backend evidence is rejected`() {
        val request = request(action = "media.play")
        assertFalse(BuiltInCapabilityEvidenceVerifier.verify(
            request, invocation(stage = "TARGET_CONFIRMED")
        ))
    }

    @Test
    fun `backend identity mismatch is rejected`() {
        val request = request(action = "media.play")
        assertFalse(BuiltInCapabilityEvidenceVerifier.verify(
            request, invocation(outputBackend = "intent", evidenceBackend = "media-session")
        ))
    }

    @Test
    fun `unverified output claim is rejected`() {
        val request = request(action = "media.play")
        assertFalse(BuiltInCapabilityEvidenceVerifier.verify(
            request, invocation(verified = false)
        ))
    }

    @Test
    fun `unknown capability families cannot borrow Android media trust`() {
        val request = request(action = "media.play")
        assertFalse(BuiltInCapabilityEvidenceVerifier.verify(
            request, invocation(capabilityId = "some.other.capability")
        ))
    }

    @Test
    fun `refused invocation cannot become learning evidence`() {
        val request = request(action = "media.play")
        assertFalse(BuiltInCapabilityEvidenceVerifier.verify(
            request, invocation(refused = true)
        ))
    }

    @Test
    fun `only the verified media operations are eligible`() {
        val request = request(action = "open.settings")
        assertFalse(BuiltInCapabilityEvidenceVerifier.verify(
            request, invocation(action = "open.settings")
        ))
    }
}
