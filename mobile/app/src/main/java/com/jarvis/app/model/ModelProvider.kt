package com.jarvis.app.model

import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.model.config.ProviderConfig
import kotlinx.coroutines.flow.Flow

/**
 * Core model provider interface — all provider adapters implement this.
 * This is the Model Fabric layer that abstracts away provider differences.
 *
 * ModelProviderType is defined once in the env package (com.jarvis.app.env)
 * so environments and providers share a single canonical vocabulary.
 */
interface ModelProvider {
    val providerType: ModelProviderType

    /** Last configuration applied to this provider, if any. */
    val config: ProviderConfig? get() = null

    /** Non-streaming generation */
    suspend fun generate(request: GenerateRequest): GenerateResult

    /** Streaming generation - emits tokens/events as they arrive */
    fun stream(request: GenerateRequest): Flow<StreamEvent>

    /** Health check - returns provider status */
    suspend fun health(): ProviderHealth

    /** Load a model (for providers that support dynamic loading) */
    suspend fun load(request: LoadRequest): LoadResult

    /** Unload a model */
    suspend fun unload(request: UnloadRequest): UnloadResult

    /** Get model metadata */
    suspend fun modelInfo(): ModelInfo

    /** Get token usage statistics */
    suspend fun tokenUsage(): TokenUsage

    /**
     * Synchronous, short-timeout chat for the Human Core model port
     * (`humancore/mod/ModelPort.kt`). Off-main-thread only (bounded by
     * [timeoutMs]); callers MUST fall back when this returns null.
     *
     * Default null: providers that cannot serve a short-timeout chat are
     * treated as unavailable, which routes the caller to its offline
     * fallback. This is the one seam where the Human Core reaches the model
     * fabric without owning a coroutine scope.
     */
    fun requestChat(
        messages: List<Pair<String, String>>,
        maxTokens: Int = 120,
        timeoutMs: Long = 5_000L
    ): String? = null
}

/** Generate request */
data class GenerateRequest(
    val messages: List<Message>,
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val tools: List<ToolSchema> = emptyList(),
    val sessionId: String? = null,
    val stopSequences: List<String> = emptyList(),
    /**
     * REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE: the Ollama model to route this
     * request to. Null keeps the adapter's configured model; a tier-served
     * generation ([OllamaModelBackend.generate] on an ON_DEMAND_REASONING
     * handle) pins it to the handle's distinct modelId so the reasoning tier
     * genuinely answers instead of the resident model.
     */
    val modelId: String? = null
)

/** Chat message */
data class Message(
    val role: MessageRole,
    val content: String,
    val name: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall>? = null
)

enum class MessageRole {
    SYSTEM, USER, ASSISTANT, TOOL
}

/** Generate result (non-streaming) */
data class GenerateResult(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val usage: TokenUsage? = null,
    val finishReason: FinishReason = FinishReason.STOP,
    val model: String? = null
)

/** Stream event types */
sealed interface StreamEvent {
    data class Token(val content: String, val accumulated: String) : StreamEvent
    data class ToolCall(val toolCall: ToolCall) : StreamEvent
    data class ToolResult(val toolCallId: String, val result: String) : StreamEvent
    data class Error(val message: String) : StreamEvent
    data class Done(val usage: TokenUsage? = null) : StreamEvent
}

/** Tool call in a message */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String // JSON string
)

/** Tool schema for function calling */
data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: String // JSON Schema as string
)

enum class FinishReason {
    STOP, LENGTH, TOOL_CALLS, CONTENT_FILTER, ERROR
}

/** Provider health status */
data class ProviderHealth(
    val isHealthy: Boolean,
    val latencyMs: Long,
    val modelLoaded: Boolean,
    val currentModel: String?,
    val error: String? = null,
    val checkedAt: Long = System.currentTimeMillis()
)

/** Load model request */
data class LoadRequest(
    val modelId: String,
    val parameters: Map<String, Any> = emptyMap()
) {
    // Workaround for Any serialization - use string map
    data class SerializableLoadRequest(
        val modelId: String,
        val parameters: Map<String, String>
    )
}

/** Load result */
data class LoadResult(
    val success: Boolean,
    val modelId: String,
    val error: String? = null
)

/** Unload request */
data class UnloadRequest(
    val modelId: String
)

/** Unload result */
data class UnloadResult(
    val success: Boolean,
    val modelId: String,
    val error: String? = null
)

/** Model metadata */
data class ModelInfo(
    val id: String,
    val name: String,
    val providerType: ModelProviderType,
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val supportsStreaming: Boolean,
    val supportsTools: Boolean,
    val supportsVision: Boolean,
    val parameters: Map<String, String> = emptyMap(),
    val loadedAt: Long? = null
)

/** Token usage */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val estimatedCostUsd: Double = 0.0
)

// NOTE: ModelProviderType lives in com.jarvis.app.env (EnvironmentProfile.kt).
// It is intentionally NOT redeclared here — environments and providers must
// share one canonical enum so ModelManager.switchProvider and the adapters
// compare equal values.
