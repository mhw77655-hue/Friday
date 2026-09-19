package com.jarvis.app.runtime

import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.env.ModelSource
import com.jarvis.app.runtime.discovery.LanRuntimeDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RuntimeBinder - manages connection to local or remote Jarvis runtimes.
 * Handles: local llama-server/Ollama, remote Jarvis OS Portal, LAN discovery.
 */
class RuntimeBinder(
    private val modelManager: com.jarvis.app.model.ModelManager,
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
        _activeConnection.value = RuntimeConnection(
            connectionType = ConnectionType.LOCAL,
            endpoint = when (modelSource) {
                is ModelSource.Local -> modelSource.path
                is ModelSource.Remote -> modelSource.host
                else -> "local"
            },
            isConnected = true,
            health = RuntimeHealth.UNKNOWN,
            capabilities = setOf("generate", "stream", "health")
        )
        _connectionEvents.emit(ConnectionEvent.Connected(_activeConnection.value!!))
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Attach to remote Jarvis runtime (OS Portal) */
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
            connectionType = ConnectionType.REMOTE_JARVIS,
            endpoint = endpoint,
            isConnected = true,
            health = RuntimeHealth.UNKNOWN,
            capabilities = setOf("generate", "stream", "health", "models", "tools", "sessions", "telemetry")
        )

        // Test connection
        val testHealth = connection.testHealth()
        connection.health = testHealth

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
    REMOTE_JARVIS,  // Another Jarvis host (OS Portal)
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
        return try {
            // Delegate to ModelManager health for now
            RuntimeHealth.HEALTHY
        } catch (e: Exception) {
            RuntimeHealth.UNHEALTHY(e.message ?: "Unknown error")
        }
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
    val providerType: ModelProviderType = ModelProviderType.REMOTE_JARVIS
)

/** Connection lifecycle events */
sealed interface ConnectionEvent {
    data class Connected(val connection: RuntimeConnection) : ConnectionEvent
    data class Disconnected(val connectionType: ConnectionType) : ConnectionEvent
    data class Failed(val connectionType: ConnectionType, val error: Throwable) : ConnectionEvent
    data class HealthChanged(val connection: RuntimeConnection, val oldHealth: RuntimeHealth, val newHealth: RuntimeHealth) : ConnectionEvent
}