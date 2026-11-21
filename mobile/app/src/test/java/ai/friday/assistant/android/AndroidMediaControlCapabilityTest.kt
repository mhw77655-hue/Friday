package ai.friday.assistant.android

import ai.friday.assistant.cognitive.execution.ActionRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidMediaControlCapabilityTest {
    private class Backend(
        override val id: String = "media-session",
        private val stage: VerificationStage = VerificationStage.VERIFIED
    ) : ControlBackend {
        var received: ControlAction? = null
        override fun available() = true
        override fun execute(action: ControlAction): VerificationEvidence {
            received = action
            return VerificationEvidence(stage, id, "observed:${action.actionType}")
        }
    }

    @Test
    fun `registered media operation reaches the Android backend and returns verification`() =
        kotlinx.coroutines.runBlocking {
            val backend = Backend()
            val router = DeviceControlRouter(listOf(backend))
            val capability = AndroidMediaControlCapability({ router }, { RiskGate() })
            val result = capability.invoke(ActionRequest("goal-1", "step-1", "media.play"))
            assertTrue(result.success)
            assertEquals(ActionType.PLAY, backend.received?.actionType)
            assertEquals("step-1", backend.received?.id)
            assertEquals("true", result.output["verified"])
            assertEquals("media-session", result.evidence["backend_id"])
        }

    @Test
    fun `postcondition verifier accepts only explicit verified backend evidence`() =
        kotlinx.coroutines.runBlocking {
            val request = ActionRequest("goal-1", "step-verify", "media.play")
            val capability = AndroidMediaControlCapability(
                { DeviceControlRouter(listOf(Backend())) },
                { RiskGate() }
            )
            val result = capability.invoke(request)

            assertTrue(capability.verifyPostcondition(request, result))
            val downgradedEvidence = result.copy(
                evidence = result.evidence + ("verification_stage" to VerificationStage.LAUNCHED.name)
            )
            assertFalse(capability.verifyPostcondition(request, downgradedEvidence))
        }

    @Test
    fun `missing Android router is reported as unavailable rather than success`() =
        kotlinx.coroutines.runBlocking {
            val capability = AndroidMediaControlCapability({ null }, { RiskGate() })
            val result = capability.invoke(ActionRequest("goal-1", "step-1", "media.pause"))
            assertFalse(result.success)
            assertEquals("DEVICE_CONTROL_UNAVAILABLE", (result.failure as ai.friday.assistant.cognitive.execution.ActionFailure.Error).code)
        }

    @Test
    fun `unverified backend result remains a failure`() = kotlinx.coroutines.runBlocking {
        val backend = Backend(stage = VerificationStage.LAUNCHED)
        val capability = AndroidMediaControlCapability({ DeviceControlRouter(listOf(backend)) }, { RiskGate() })
        val result = capability.invoke(ActionRequest("goal-1", "step-1", "media.next_track"))
        assertFalse(result.success)
        assertEquals("DEVICE_ACTION_UNVERIFIED", (result.failure as ai.friday.assistant.cognitive.execution.ActionFailure.Error).code)
    }

    @Test
    fun `unsupported operation is rejected explicitly`() = kotlinx.coroutines.runBlocking {
        val capability = AndroidMediaControlCapability({ DeviceControlRouter(emptyList()) }, { RiskGate() })
        val result = capability.invoke(ActionRequest("goal-1", "step-1", "device.delete_everything"))
        assertFalse(result.success)
        assertTrue(result.failure is ai.friday.assistant.cognitive.execution.ActionFailure.Unsupported)
    }
}