package ai.friday.assistant.bootstrap

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.friday.assistant.FridayEngine
import ai.friday.assistant.EngineReadinessState
import ai.friday.assistant.humancore.HumanCore
import ai.friday.assistant.humancore.store.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BootstrapManagerInstrumentedTest {
    private suspend fun manager(hooks: BootstrapHooks): BootstrapManager {
        withTimeout(30_000) {
            FridayEngine.readinessState.first { it is EngineReadinessState.Ready }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        return BootstrapManager(
            context = context,
            fileStorage = FileStorage(context.filesDir),
            humanCore = HumanCore,
            modelManager = checkNotNull(FridayEngine.modelManager),
            runtimeBinder = checkNotNull(FridayEngine.runtimeBinder),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            hooks = hooks
        )
    }

    @Test
    fun runtimeExceptionBecomesRecoverableFailureAndRetryResumesOnlyFailedStep() = runBlocking {
        val identityCalls = AtomicInteger()
        val modelHealthCalls = AtomicInteger()
        val runtimeCalls = AtomicInteger()
        val hooks = BootstrapHooks(
            identityDescription = { identityCalls.incrementAndGet(); "Friday" },
            modelHealth = {
                modelHealthCalls.incrementAndGet()
                BootstrapModelHealth(isHealthy = true, currentModel = "test-model", error = null)
            },
            startHealthChecks = {},
            initializeRuntime = {
                if (runtimeCalls.incrementAndGet() == 1) {
                    throw IllegalStateException("injected runtime initialization failure")
                }
            }
        )
        val manager = manager(hooks)

        val first = manager.runBootstrap()
        assertTrue(first is BootstrapResult.Failure)
        assertEquals(BootstrapStep.INITIALIZE_COMPONENTS, (first as BootstrapResult.Failure).failedStep)
        assertTrue(manager.bootstrapState.value is BootstrapState.FAILED)
        assertEquals(1, identityCalls.get())
        assertEquals(1, modelHealthCalls.get())
        assertEquals(1, runtimeCalls.get())

        manager.retryCurrentStep()

        assertEquals(BootstrapState.COMPLETED, manager.bootstrapState.value)
        assertEquals(1, identityCalls.get())
        assertEquals(1, modelHealthCalls.get())
        assertEquals(2, runtimeCalls.get())
    }

    @Test
    fun missingLocalModelDoesNotBlockBootstrap() = runBlocking {
        val hooks = BootstrapHooks(
            identityDescription = { "Friday" },
            modelHealth = { throw IllegalStateException("no local model installed") },
            startHealthChecks = {},
            initializeRuntime = {}
        )
        val manager = manager(hooks)

        val result = manager.runBootstrap()

        assertEquals(BootstrapResult.Success, result)
        assertEquals(BootstrapState.COMPLETED, manager.bootstrapState.value)
        assertTrue(manager.notices.value.any { it.contains("no local model installed") })
    }

    @Test
    fun blankIdentityFailsAtIdentityStep() = runBlocking {
        val hooks = BootstrapHooks(
            identityDescription = { "" },
            modelHealth = { BootstrapModelHealth(true, "test-model", null) },
            startHealthChecks = {},
            initializeRuntime = {}
        )
        val manager = manager(hooks)

        val result = manager.runBootstrap()

        assertTrue(result is BootstrapResult.Failure)
        assertEquals(BootstrapStep.LOAD_IDENTITY, (result as BootstrapResult.Failure).failedStep)
        assertTrue(manager.bootstrapState.value is BootstrapState.FAILED)
    }
}
