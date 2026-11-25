package ai.friday.assistant.env

import ai.friday.assistant.approval.ApprovalGate
import ai.friday.assistant.approval.GateConfig
import ai.friday.assistant.humancore.store.FileStorage
import ai.friday.assistant.model.ModelManager
import ai.friday.assistant.runtime.RuntimeBinder
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LiquidEnvironmentManagerInstrumentedTest {
    private fun fixture(): Triple<LiquidEnvironmentManager, CoroutineScope, EnvironmentProfile> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val storage = FileStorage(File(context.cacheDir, "environment-manager-audit"))
        val models = ModelManager(context = null, scope = scope)
        val runtime = RuntimeBinder(models, scope)
        val manager = LiquidEnvironmentManager(context, storage, models, runtime, scope)
        val profile = EnvironmentProfile.BUILT_IN_PROFILES
            .first { it.id == "offline-persona" }
            .copy(
                id = "audit-safe-profile",
                label = "Audit Safe Profile",
                isBuiltIn = false,
                permissionPolicy = PermissionPolicy.RESTRICTED,
                safetyRestrictions = SafetyRestrictions.READ_ONLY
            )
        return Triple(manager, scope, profile)
    }

    @Test
    fun successfulActivationAppliesTheProfileAuthorizationPolicy() = runBlocking {
        val (manager, scope, profile) = fixture()
        val previous = ApprovalGate.configured()
        try {
            assertTrue(manager.register(profile).isSuccess)
            val result = manager.activate(profile)

            assertTrue("registered offline profile should activate", result.isSuccess)
            assertEquals(profile.id, manager.activeEnvironment.value?.id)
            assertEquals(
                GateConfig(profile.permissionPolicy, profile.safetyRestrictions),
                ApprovalGate.configured()
            )
        } finally {
            ApprovalGate.configure(previous)
            scope.cancel()
        }
    }

    @Test
    fun activationOfUnregisteredProfileReturnsFailureAndPreservesPreviousEnvironment() = runBlocking {
        val (manager, scope, profile) = fixture()
        val previous = ApprovalGate.configured()
        try {
            assertTrue(manager.register(profile).isSuccess)
            assertTrue(manager.activate(profile).isSuccess)

            val missing = profile.copy(id = "not-registered", label = "Missing")
            val result = manager.activate(missing)

            assertTrue("activation must not report success for an unregistered profile", result.isFailure)
            assertEquals("failed activation must preserve the prior active environment", profile.id, manager.activeEnvironment.value?.id)
            assertEquals(
                GateConfig(profile.permissionPolicy, profile.safetyRestrictions),
                ApprovalGate.configured()
            )
        } finally {
            ApprovalGate.configure(previous)
            scope.cancel()
        }
    }

    @Test
    fun authorizationDefaultsFailClosedBeforeAnyEnvironmentIsActivated() {
        assertEquals(PermissionPolicy.SAFE_MODE, GateConfig().permissionPolicy)
        assertEquals(SafetyRestrictions.MAX, GateConfig().safetyRestrictions)
    }
}
