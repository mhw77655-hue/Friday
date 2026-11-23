package ai.friday.assistant.runtime

import ai.friday.assistant.env.ModelProviderType
import ai.friday.assistant.env.ModelSource
import ai.friday.assistant.model.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeBinderAttachmentTest {
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun remoteDiscoveryDoesNotBecomeAConnectedSessionWithoutHandshake() = runBlocking {
        val binder = RuntimeBinder(
            ModelManager(context = null, scope = scope()),
            scope()
        )

        val result = binder.attachToRemote(ModelSource.Remote("192.0.2.10:8150"))

        assertTrue(result.isFailure)
        assertNull(binder.activeConnection.value)
    }

    @Test
    fun unprobedLocalProviderDoesNotEmitAConnectedState() = runBlocking {
        val binder = RuntimeBinder(
            ModelManager(context = null, scope = scope()),
            scope()
        )

        val result = binder.attachLocal(ModelSource.Local("127.0.0.1:8080"))

        assertTrue(result.isFailure)
        val connection = binder.activeConnection.value
        assertFalse(connection!!.isConnected)
        assertTrue(connection.health is RuntimeHealth.UNKNOWN)
    }

    @Test
    fun verifiedHealthyHeuristicProviderCanAttachLocally() = runBlocking {
        val manager = ModelManager(context = null, scope = scope())
        assertTrue(manager.setActiveProvider(ModelProviderType.HEURISTIC).isSuccess)
        val binder = RuntimeBinder(manager, scope())

        val result = binder.attachLocal(ModelSource.None)

        assertTrue(result.isSuccess)
        assertTrue(binder.activeConnection.value!!.isConnected)
        assertTrue(binder.activeConnection.value!!.health is RuntimeHealth.HEALTHY)
    }
}
