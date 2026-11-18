package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.execution.ActionRequest

/**
 * Narrow production verifier for the first capability family that exposes a
 * real postcondition check: Android media control.
 *
 * The media adapter only emits this evidence after DeviceControlRouter receives
 * VerificationStage.VERIFIED from a concrete backend. Invocation success by
 * itself is never sufficient. Unknown capability families remain unverified
 * until they provide their own independently checkable evidence contract.
 */
object BuiltInCapabilityEvidenceVerifier {
    private val verifiedAndroidMediaOperations = setOf(
        "media.play",
        "media.pause",
        "media.next_track",
        "media.previous_track"
    )

    fun verify(request: ActionRequest, invocation: CapabilityInvocation): Boolean {
        if (invocation.refused || !invocation.success) return false
        if (invocation.capabilityId != "android.media_control") return false
        if (request.action !in verifiedAndroidMediaOperations) return false

        val result = invocation.result ?: return false
        val backendId = result.evidence["backend_id"]?.takeIf { it.isNotBlank() } ?: return false

        return result.evidence["verification_stage"] == "VERIFIED" &&
            result.output["verified"] == "true" &&
            result.output["backend_id"] == backendId
    }
}
