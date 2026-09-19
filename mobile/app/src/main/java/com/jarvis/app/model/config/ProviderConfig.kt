package com.jarvis.app.model.config

/**
 * Provider configuration - passed when configuring an adapter.
 * Stored securely (auth tokens in Keystore).
 */
data class ProviderConfig(
    val modelId: String? = null,
    val authToken: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 60_000,
    val maxRetries: Int = 2
) {
    companion object {
        val Default = ProviderConfig()
    }
}

/** Model source configuration for UI */
data class ModelSourceConfig(
    val type: SourceType,
    val endpoint: String? = null,      // For remote: "host:port"
    val modelPath: String? = null,     // For local: file path or server endpoint
    val authToken: String? = null
) {
    enum class SourceType {
        LOCAL_LLAMA_CPP,
        LOCAL_OLLAMA,
        REMOTE_JARVIS,
        CLOUD,
        AUTO_DISCOVER
    }
}

/** Discovered runtime info */
data class DiscoveredRuntime(
    val id: String,
    val name: String,
    val endpoint: String,
    val model: String,
    val capabilities: List<String>,
    val lastSeen: Long,
    val isLocal: Boolean
)
