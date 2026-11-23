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
        val observedHealth = runtimeHealthFromProviderObservation(
            isHealthy = providerHealth.isHealthy,
            error = providerHealth.error
        )
        _activeConnection.value = RuntimeConnection(
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
        _connectionEvents.emit(ConnectionEvent.Connected(_activeConnection.value!!))
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

        val connection = RuntimeConnection(
            connectionType = ConnectionType.REMOTE_FRIDAY,
            endpoint = endpoint,
            isConnected = true,
            health = RuntimeHealth.UNKNOWN,
            capabilities = setOf("generate", "stream", "health", "models", "tools", "sessions", "telemetry")
        )

        // The current RuntimeConnection has no authenticated remote health
        // protocol. Never claim a remote endpoint is healthy without a real probe.
        val testHealth = connection.testHealth()
        connection.health = testHealth
        connection.isConnected = testHealth is RuntimeHealth.HEALTHY

        _activeConnection.value = connection
        _connectionEvents.emit(ConnectionEvent.Connected(connection))
        Result.success(Unit)
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
    suspend fun testHealth(): RuntimeHealth {
        // This value is intentionally UNKNOWN until a real authenticated
        // health endpoint is implemented. Returning HEALTHY here was a
        // false-positive that made unreachable remotes appear connected.
        return RuntimeHealth.UNKNOWN
    }
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

/** Pure mapping for provider health observations; unknown stays unknown. */
internal fun runtimeHealthFromProviderObservation(
    isHealthy: Boolean,
    error: String?
): RuntimeHealth = if (isHealthy) {
    RuntimeHealth.HEALTHY
} else {
    RuntimeHealth.UNHEALTHY(error?.take(240) ?: "No healthy provider observation is available.")
}
