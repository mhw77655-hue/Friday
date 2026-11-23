package ai.friday.assistant.runtime

import ai.friday.assistant.env.ModelProviderType
import ai.friday.assistant.env.ModelSource
import ai.friday.assistant.runtime.discovery.LanRuntimeDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RuntimeBinder - manages connection to local or remote Friday runtimes.
 * Handles: local llama-server/Ollama, remote Friday OS Portal, LAN discovery.
 */
class RuntimeBinder(
    private val modelManager: ai.friday.assistant.model.ModelManager,
    private val scope: CoroutineScope
) {
    // Active connection
    private val _activeConnection = MutableStateFlow<RuntimeConnection?>(null)
    val activeConnection = _activeConnection.asStateFlow()

    // Discovered runtimes
    private val _availableRuntimes = MutableStateFlow<List<RuntimeInfo>>(emptyList())
    val availableRuntimes = _availableRuntimes.asStateFlow()

    // Connection events
    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>(extraBufferCapacity = 10)
    val connectionEvents = _connectionEvents.asSharedFlow()

    // LAN discovery
    private val lanDiscovery = LanRuntimeDiscovery(scope)

    /** Initialize and start LAN discovery */
    fun initialize() {
        lanDiscovery.startDiscovery { runtimes ->
            _availableRuntimes.value = runtimes
        }
    }

    /** Attach to local runtime (llama.cpp or Ollama) */
    suspend fun attachLocal(modelSource: ModelSource): Result<Unit> = try {
        // For local, we just ensure ModelManager is configured
        // The actual connection is managed by ModelManager's adapters
        // Do not equate "configured" with "connected". Use the provider's
        // latest real health observation; an unprobed provider stays unavailable.
        val providerHealth = modelManager.providerHealth.value
        val observedHealth = runtimeHealthFromProviderObservation(providerHealth)
        val connection = RuntimeConnection(
            connectionType = ConnectionType.LOCAL,
            endpoint = when (modelSource) {
                is ModelSource.Local -> modelSource.path
                is ModelSource.Remote -> modelSource.host
                else -> "local"
            },
            isConnected = observedHealth is RuntimeHealth.HEALTHY,
            health = observedHealth,
            capabilities = setOf("generate", "stream", "health")
        )
        _activeConnection.value = connection
        if (!connection.isConnected) {
            val failure = IllegalStateException(
                when (val health = observedHealth) {
                    is RuntimeHealth.UNHEALTHY -> health.reason
                    RuntimeHealth.UNKNOWN -> "Local provider health has not been verified."
                    is RuntimeHealth.DEGRADED -> health.reason
                    RuntimeHealth.HEALTHY -> "Local runtime is unavailable."
                }
            )
            _connectionEvents.emit(ConnectionEvent.Failed(ConnectionType.LOCAL, failure))
            return Result.failure(failure)
        }
        _connectionEvents.emit(ConnectionEvent.Connected(connection))
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Attach to remote Friday runtime (OS Portal) */
    suspend fun attachToRemote(modelSource: ModelSource): Result<Unit> = try {
        val endpoint = when (modelSource) {
            is ModelSource.Remote -> if (modelSource.host == "auto-discover") {
                // Use first discovered or default
                _availableRuntimes.value.firstOrNull()?.endpoint ?: "127.0.0.1:8150"
            } else {
                modelSource.host
            }
            is ModelSource.Local -> modelSource.path
            else -> "127.0.0.1:8150"
        }

        // Discovery/configuration is not an authenticated connection. Until a
        // real remote health/identity handshake exists, fail closed and do not
        // publish a Connected event or install a phantom active connection.
        val failure = UnsupportedOperationException(
            "Remote Friday connection is not available: authenticated health and identity handshake is not implemented."
        )
        _connectionEvents.emit(ConnectionEvent.Failed(ConnectionType.REMOTE_FRIDAY, failure))
        Result.failure(failure)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Detach from current runtime */
    suspend fun detach() {
        _activeConnection.value?.let { conn ->
            _connectionEvents.emit(ConnectionEvent.Disconnected(conn.connectionType))
        }
        _activeConnection.value = null
    }

    /** Refresh discovered runtimes */
    suspend fun refreshDiscovery() {
        lanDiscovery.triggerScan()
    }
}

/** Runtime connection types */
enum class ConnectionType {
    LOCAL,          // Local llama.cpp / Ollama
    REMOTE_FRIDAY,  // Another Friday host (OS Portal)
    LAN_DISCOVERED  // Discovered via mDNS
}

/** Runtime connection state */
data class RuntimeConnection(
    val connectionType: ConnectionType,
    val endpoint: String,
    var isConnected: Boolean = true,
    var health: RuntimeHealth = RuntimeHealth.UNKNOWN,
    val capabilities: Set<String>
) {
}

/** Runtime health status */
sealed class RuntimeHealth {
    object HEALTHY : RuntimeHealth()
    data class DEGRADED(val reason: String) : RuntimeHealth()
    data class UNHEALTHY(val reason: String) : RuntimeHealth()
    object UNKNOWN : RuntimeHealth()
}

/** Discovered runtime info */
data class RuntimeInfo(
    val id: String,
    val name: String,
    val endpoint: String,
    val model: String,
    val capabilities: List<String>,
    val lastSeen: Long = System.currentTimeMillis(),
    val isLocal: Boolean = false,
    val providerType: ModelProviderType = ModelProviderType.REMOTE_FRIDAY
)

/** Connection lifecycle events */
sealed interface ConnectionEvent {
    data class Connected(val connection: RuntimeConnection) : ConnectionEvent
    data class Disconnected(val connectionType: ConnectionType) : ConnectionEvent
    data class Failed(val connectionType: ConnectionType, val error: Throwable) : ConnectionEvent
    data class HealthChanged(val connection: RuntimeConnection, val oldHealth: RuntimeHealth, val newHealth: RuntimeHealth) : ConnectionEvent
}

/** Map an actual provider observation without turning "not checked" into failure. */
internal fun runtimeHealthFromProviderObservation(
    observation: ai.friday.assistant.model.ProviderHealth
): RuntimeHealth = when {
    observation.isHealthy -> RuntimeHealth.HEALTHY
    observation.latencyMs < 0L && observation.error.isNullOrBlank() &&
        !observation.modelLoaded && observation.currentModel == null -> RuntimeHealth.UNKNOWN
    else -> RuntimeHealth.UNHEALTHY(
        observation.error?.take(240) ?: "The provider health check did not pass."
    )
}
