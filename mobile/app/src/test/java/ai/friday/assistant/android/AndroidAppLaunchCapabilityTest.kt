package ai.friday.assistant.android

import ai.friday.assistant.cognitive.execution.ActionRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAppLaunchCapabilityTest {

    private class FakeBackend(
        private val stage: VerificationStage = VerificationStage.VERIFIED
    ) : ControlBackend {
        override val id: String = "shizuku"
        var launchedPackage: String? = null
        override fun available(): Boolean = true
        override fun execute(action: ControlAction): VerificationEvidence {
            launchedPackage = action.packageName
            return VerificationEvidence(stage, id, "status=${if (stage == VerificationStage.VERIFIED) "verified" else "not-verified"}")
        }
    }

    private fun request(packageName: String? = "com.example.app") = ActionRequest(
        planId = "app-launch-test",
        stepId = "launch-1",
        action = AndroidAppLaunchCapability.OPERATION,
        context = packageName?.let { mapOf("arg:package_name" to it) }.orEmpty()
    )

    private fun capability(
        backend: FakeBackend,
        launchable: (String) -> Boolean = { true },
        foreground: suspend (String) -> Boolean = { true }
    ) = AndroidAppLaunchCapability(
        packageIsLaunchable = launchable,
        foregroundVerifier = foreground,
        routerProvider = { DeviceControlRouter(listOf(backend)) },
        riskGateProvider = { RiskGate() }
    )

    @Test
    fun missingPackageIsRejectedBeforeBackendExecution() = runBlocking {
        val backend = FakeBackend()
        val result = capability(backend).invoke(request(null))
        assertFalse(result.success)
        assertEquals("APP_PACKAGE_REQUIRED", (result.failure as ai.friday.assistant.cognitive.execution.ActionFailure.Error).code)
        assertEquals(null, backend.launchedPackage)
    }

    @Test
    fun uninstalledOrInvisiblePackageIsRejected() = runBlocking {
        val backend = FakeBackend()
        val result = capability(backend, launchable = { false }).invoke(request())
        assertFalse(result.success)
        assertEquals("APP_NOT_LAUNCHABLE", (result.failure as ai.friday.assistant.cognitive.execution.ActionFailure.Error).code)
        assertEquals(null, backend.launchedPackage)
    }

    @Test
    fun launchRequiresVerifiedBackendEvidence() = runBlocking {
        val backend = FakeBackend(VerificationStage.NOT_ATTEMPTED)
        val result = capability(backend).invoke(request())
        assertFalse(result.success)
        assertEquals(null, result.output["verified"])
    }

    @Test
    fun postconditionIndependentlyRechecksForegroundPackage() = runBlocking {
        val backend = FakeBackend()
        val capability = capability(backend, foreground = { false })
        val request = request()
        val result = capability.invoke(request)
        assertTrue(result.success)
        assertFalse(capability.verifyPostcondition(request, result))
        assertEquals("com.example.app", backend.launchedPackage)
    }

    @Test
    fun successfulLaunchRequiresSecondForegroundReadback() = runBlocking {
        val backend = FakeBackend()
        val capability = capability(backend, foreground = { it == "com.example.app" })
        val request = request()
        val result = capability.invoke(request)
        assertTrue(result.success)
        assertTrue(capability.verifyPostcondition(request, result))
        assertEquals("shizuku", result.output["backend_id"])
    }

    @Test
    fun appLaunchCapabilityIsVisibleInTheProductionOrganGraph() {
        val graph = ai.friday.assistant.selfreconfig.FridayOrganGraph.build()
        assertTrue(graph.node("android.appLaunchCapability") != null)
        assertTrue(
            graph.edgesFrom("capability.capabilityFabric").any {
                it.toId == "android.appLaunchCapability"
            }
        )
        assertTrue(
            graph.edgesFrom("android.appLaunchCapability").any {
                it.toId == "android.deviceControlRouter"
            }
        )
    }

}
