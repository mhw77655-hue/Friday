package com.jarvis.app.model.remote

import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.model.*
import com.jarvis.app.model.config.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android-native adapter for OpenAI Chat Completions compatible endpoints.
 *
 * It deliberately owns no memory, tools, identity, or action execution. It
 * sends only the messages in the caller's request and returns model output.
 * The endpoint must be explicitly configured by the owner.
 */
class OpenAiCompatibleProvider(
    override val config: ProviderConfig,
    private val apiKey: String,
    override val providerType: ModelProviderType = ModelProviderType.CLOUD
) : ModelProvider {
    private val endpoint: String = config.parameters["endpoint"]
        ?.trim()?.trimEnd('/')
        ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        ?: throw IllegalArgumentException("A valid HTTP(S) provider endpoint is required")
    private val model: String = config.modelId?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("A model id is required")

    override suspend fun generate(request: GenerateRequest): GenerateResult = withContext(Dispatchers.IO) {
        require(request.maxTokens in 1..32768) { "maxTokens must be between 1 and 32768" }
        val body = JSONObject().apply {
            put("model", request.modelId?.takeIf { it.isNotBlank() } ?: model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature.toDouble())
            put("top_p", request.topP.toDouble())
            put("stream", false)
            put("messages", JSONArray().apply {
                request.messages.forEach { message ->
                    put(JSONObject().apply {
                        put("role", when (message.role) {
                            MessageRole.SYSTEM -> "system"
                            MessageRole.USER -> "user"
                            MessageRole.ASSISTANT -> "assistant"
                            MessageRole.TOOL -> "tool"
                        })
                        put("content", message.content)
                        message.name?.let { put("name", it) }
                        message.toolCallId?.let { put("tool_call_id", it) }
                    })
                }
            })
        }
        val response = execute("POST", "$endpoint/chat/completions", body)
        parseCompletion(response, request.modelId ?: model)
    }

    /** Non-streaming endpoint: emit one completed token event, never fake token streaming. */
    override fun stream(request: GenerateRequest): Flow<StreamEvent> = flow {
        try {
            val result = generate(request)
            emit(StreamEvent.Token(result.content, result.content))
            emit(StreamEvent.Done(result.usage))
        } catch (e: Exception) {
            emit(StreamEvent.Error(e.message ?: "Remote provider request failed"))
        }
    }

    override suspend fun health(): ProviderHealth = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        try {
            val response = execute("GET", "$endpoint/models", null)
            ProviderHealth(
                isHealthy = response.optJSONArray("data") != null || response.has("models"),
                latencyMs = System.currentTimeMillis() - started,
                modelLoaded = false,
                currentModel = model,
                error = null
            )
        } catch (e: Exception) {
            ProviderHealth(
                isHealthy = false,
                latencyMs = System.currentTimeMillis() - started,
                modelLoaded = false,
                currentModel = model,
                error = e.message ?: "Provider health check failed"
            )
        }
    }

    override suspend fun load(request: LoadRequest) =
        LoadResult(success = true, modelId = request.modelId, error = null)

    override suspend fun unload(request: UnloadRequest) =
        UnloadResult(success = true, modelId = request.modelId, error = null)

    override suspend fun modelInfo() = ModelInfo(
        id = model,
        name = model,
        providerType = providerType,
        contextWindow = 0,
        maxOutputTokens = 32768,
        supportsStreaming = false,
        supportsTools = false,
        supportsVision = false,
        parameters = mapOf("endpoint" to endpoint, "protocol" to "openai-chat-completions")
    )

    override suspend fun tokenUsage() = TokenUsage(0, 0, 0, 0.0)

    private fun execute(method: String, url: String, body: JSONObject?): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = config.timeoutMs.coerceIn(1_000, 120_000).toInt()
            readTimeout = config.timeoutMs.coerceIn(1_000, 120_000).toInt()
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                // Keep provider errors useful without leaking credentials or full request data.
                val detail = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $status"
                throw RemoteProviderException(status, detail.take(500))
            }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCompletion(json: JSONObject, requestedModel: String): GenerateResult {
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: throw IllegalStateException("Provider response contained no choices")
        val message = choice.optJSONObject("message")
            ?: throw IllegalStateException("Provider response contained no message")
        val content = message.optString("content").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Provider returned an empty response")
        val usageJson = json.optJSONObject("usage")
        val prompt = usageJson?.optInt("prompt_tokens", 0) ?: 0
        val completion = usageJson?.optInt("completion_tokens", 0) ?: 0
        val usage = if (usageJson != null) TokenUsage(prompt, completion, prompt + completion, 0.0) else null
        val finish = when (choice.optString("finish_reason")) {
            "length" -> FinishReason.LENGTH
            "tool_calls" -> FinishReason.TOOL_CALLS
            else -> FinishReason.STOP
        }
        return GenerateResult(
            content = content,
            usage = usage,
            finishReason = finish,
            model = json.optString("model", requestedModel)
        )
    }
}

class RemoteProviderException(val httpStatus: Int, message: String) :
    IllegalStateException("Remote provider error ($httpStatus): $message")
