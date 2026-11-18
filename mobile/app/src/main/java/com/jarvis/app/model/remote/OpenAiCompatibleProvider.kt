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
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong

enum class RemoteProviderProtocol { OPENAI_RESPONSES, OPENAI_COMPATIBLE, ANTHROPIC_MESSAGES, GEMINI_GENERATE_CONTENT }

/**
 * Native Android adapter for major hosted model APIs and OpenAI-compatible endpoints.
 * This is a reasoning provider only: it cannot execute Friday capabilities or write memory.
 */
class OpenAiCompatibleProvider(
    override val config: ProviderConfig,
    private val apiKey: String,
    override val providerType: ModelProviderType = ModelProviderType.CLOUD,
    private val protocol: RemoteProviderProtocol = RemoteProviderProtocol.OPENAI_COMPATIBLE
) : ModelProvider {
    private val promptTokenTotal = AtomicLong(0)
    private val completionTokenTotal = AtomicLong(0)
    private val endpoint = ProviderEndpointPolicy.validate(
        config.parameters["endpoint"] ?: throw IllegalArgumentException("Provider endpoint is required"),
        allowInsecureLoopbackHttp = config.parameters["allowInsecureHttp"] == "true"
    )
    private val model = config.modelId?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("A model id is required")

    override suspend fun generate(request: GenerateRequest): GenerateResult = withContext(Dispatchers.IO) {
        require(request.maxTokens in 1..32768) { "maxTokens must be between 1 and 32768" }
        val body = when (protocol) {
            RemoteProviderProtocol.OPENAI_RESPONSES -> openAiResponsesBody(request)
            RemoteProviderProtocol.OPENAI_COMPATIBLE -> openAiBody(request)
            RemoteProviderProtocol.ANTHROPIC_MESSAGES -> anthropicBody(request)
            RemoteProviderProtocol.GEMINI_GENERATE_CONTENT -> geminiBody(request)
        }
        val url = when (protocol) {
            RemoteProviderProtocol.OPENAI_RESPONSES -> "$endpoint/responses"
            RemoteProviderProtocol.OPENAI_COMPATIBLE -> "$endpoint/chat/completions"
            RemoteProviderProtocol.ANTHROPIC_MESSAGES -> "$endpoint/messages"
            RemoteProviderProtocol.GEMINI_GENERATE_CONTENT ->
                "$endpoint/models/${URLEncoder.encode(request.modelId ?: model, "UTF-8")}:generateContent"
        }
        parseResponse(execute(url, body), request.modelId ?: model).also { result ->
            result.usage?.let { usage ->
                promptTokenTotal.addAndGet(usage.promptTokens.toLong())
                completionTokenTotal.addAndGet(usage.completionTokens.toLong())
            }
        }
    }

    /** Honest non-streaming fallback: one completed response event, not simulated token streaming. */
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
            val modelsUrl = when (protocol) {
                RemoteProviderProtocol.GEMINI_GENERATE_CONTENT ->
                    "${endpoint}/models/${URLEncoder.encode(model, "UTF-8")}" 
                else -> "${endpoint}/models"
            }
            execute(modelsUrl, null, "GET")
            ProviderHealth(true, System.currentTimeMillis() - started, false, model)
        } catch (e: Exception) {
            ProviderHealth(false, System.currentTimeMillis() - started, false, model,
                e.message ?: "Provider health check failed")
        }
    }

    override suspend fun load(request: LoadRequest) = LoadResult(true, request.modelId)
    override suspend fun unload(request: UnloadRequest) = UnloadResult(true, request.modelId)

    override suspend fun modelInfo() = ModelInfo(
        id = model, name = model, providerType = providerType,
        contextWindow = 0, maxOutputTokens = 32768,
        supportsStreaming = false, supportsTools = false, supportsVision = false,
        parameters = mapOf("endpoint" to endpoint, "protocol" to protocol.name)
    )

    override suspend fun tokenUsage(): TokenUsage {
        val prompt = promptTokenTotal.get().toInt()
        val completion = completionTokenTotal.get().toInt()
        return TokenUsage(prompt, completion, prompt + completion, 0.0)
    }

    private fun openAiResponsesBody(request: GenerateRequest): JSONObject {
        val instructions = request.messages.filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n") { it.content }
        return JSONObject().apply {
            put("model", request.modelId ?: model)
            put("max_output_tokens", request.maxTokens.coerceAtLeast(16))
            put("store", false)
            if (instructions.isNotBlank()) put("instructions", instructions)
            put("input", JSONArray().apply {
                request.messages.filter { it.role != MessageRole.SYSTEM && it.role != MessageRole.TOOL }
                    .forEach { message ->
                        put(JSONObject()
                            .put("role", if (message.role == MessageRole.ASSISTANT) "assistant" else "user")
                            .put("content", message.content))
                    }
            })
        }
    }
    private fun openAiBody(request: GenerateRequest) = JSONObject().apply {
        put("model", request.modelId ?: model)
        put("max_tokens", request.maxTokens)
        put("temperature", request.temperature.toDouble())
        put("top_p", request.topP.toDouble())
        put("stream", false)
        put("messages", JSONArray().apply {
            request.messages.forEach { m ->
                put(JSONObject().apply {
                    put("role", when (m.role) {
                        MessageRole.SYSTEM -> "system"
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.TOOL -> "tool"
                    })
                    put("content", m.content)
                    m.name?.let { put("name", it) }
                    m.toolCallId?.let { put("tool_call_id", it) }
                })
            }
        })
    }

    private fun anthropicBody(request: GenerateRequest): JSONObject {
        val system = request.messages.filter { it.role == MessageRole.SYSTEM }.joinToString("\n") { it.content }
        return JSONObject().apply {
            put("model", request.modelId ?: model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature.toDouble())
            if (system.isNotBlank()) put("system", system)
            put("messages", JSONArray().apply {
                request.messages.filter { it.role != MessageRole.SYSTEM && it.role != MessageRole.TOOL }
                    .forEach { m ->
                        put(JSONObject().put("role", if (m.role == MessageRole.ASSISTANT) "assistant" else "user")
                            .put("content", m.content))
                    }
            })
        }
    }

    private fun geminiBody(request: GenerateRequest): JSONObject {
        val system = request.messages.filter { it.role == MessageRole.SYSTEM }.joinToString("\n") { it.content }
        return JSONObject().apply {
            if (system.isNotBlank()) put("systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().apply {
                request.messages.filter { it.role != MessageRole.SYSTEM && it.role != MessageRole.TOOL }
                    .forEach { m ->
                        put(JSONObject()
                            .put("role", if (m.role == MessageRole.ASSISTANT) "model" else "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", m.content))))
                    }
            })
            put("generationConfig", JSONObject()
                .put("maxOutputTokens", request.maxTokens)
                .put("temperature", request.temperature.toDouble())
                .put("topP", request.topP.toDouble()))
        }
    }

    private fun execute(url: String, body: JSONObject?, method: String = "POST"): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = config.timeoutMs.coerceIn(1_000, 120_000).toInt()
            readTimeout = config.timeoutMs.coerceIn(1_000, 120_000).toInt()
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            when (protocol) {
                RemoteProviderProtocol.OPENAI_RESPONSES, RemoteProviderProtocol.OPENAI_COMPATIBLE ->
                    if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
                RemoteProviderProtocol.ANTHROPIC_MESSAGES -> {
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("anthropic-version", "2023-06-01")
                }
                RemoteProviderProtocol.GEMINI_GENERATE_CONTENT ->
                    setRequestProperty("x-goog-api-key", apiKey)
            }
            doOutput = body != null
        }
        try {
            if (body != null) connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $status"
                throw RemoteProviderException(status, detail.take(500))
            }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(json: JSONObject, requestedModel: String): GenerateResult {
        val content: String
        val prompt: Int
        val completion: Int
        val finish: FinishReason
        when (protocol) {
            RemoteProviderProtocol.OPENAI_RESPONSES -> {
                val output = json.optJSONArray("output")
                content = output?.let { items ->
                    (0 until items.length()).mapNotNull itemsLoop@{ index ->
                        val item = items.optJSONObject(index) ?: return@itemsLoop null
                        if (item.optString("type") != "message") return@mapNotNull null
                        item.optJSONArray("content")?.let { parts ->
                            (0 until parts.length()).mapNotNull partsLoop@{ partIndex ->
                                val part = parts.optJSONObject(partIndex) ?: return@partsLoop null
                                if (part.optString("type") == "output_text") part.optString("text") else null
                            }.filter { it.isNotBlank() }.joinToString("\n")
                        }
                    }.filter { it.isNotBlank() }.joinToString("\n")
                }.orEmpty()
                prompt = json.optJSONObject("usage")?.optInt("input_tokens", 0) ?: 0
                completion = json.optJSONObject("usage")?.optInt("output_tokens", 0) ?: 0
                val incomplete = json.optJSONObject("incomplete_details")?.optString("reason")
                finish = if (json.optString("status") == "incomplete" || incomplete == "max_output_tokens") FinishReason.LENGTH else FinishReason.STOP
            }
            RemoteProviderProtocol.OPENAI_COMPATIBLE -> {
                val choice = json.optJSONArray("choices")?.optJSONObject(0)
                    ?: throw IllegalStateException("Provider response contained no choices")
                content = choice.optJSONObject("message")?.optString("content").orEmpty()
                prompt = json.optJSONObject("usage")?.optInt("prompt_tokens", 0) ?: 0
                completion = json.optJSONObject("usage")?.optInt("completion_tokens", 0) ?: 0
                finish = if (choice.optString("finish_reason") == "length") FinishReason.LENGTH else FinishReason.STOP
            }
            RemoteProviderProtocol.ANTHROPIC_MESSAGES -> {
                content = json.optJSONArray("content")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("text") }
                        .filter { it.isNotBlank() }.joinToString("\n")
                }.orEmpty()
                prompt = json.optJSONObject("usage")?.optInt("input_tokens", 0) ?: 0
                completion = json.optJSONObject("usage")?.optInt("output_tokens", 0) ?: 0
                finish = if (json.optString("stop_reason") == "max_tokens") FinishReason.LENGTH else FinishReason.STOP
            }
            RemoteProviderProtocol.GEMINI_GENERATE_CONTENT -> {
                content = json.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")?.let { a ->
                        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("text") }
                            .filter { it.isNotBlank() }.joinToString("\n")
                    }.orEmpty()
                prompt = json.optJSONObject("usageMetadata")?.optInt("promptTokenCount", 0) ?: 0
                completion = json.optJSONObject("usageMetadata")?.optInt("candidatesTokenCount", 0) ?: 0
                finish = if (json.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason") == "MAX_TOKENS")
                    FinishReason.LENGTH else FinishReason.STOP
            }
        }
        if (content.isBlank()) throw IllegalStateException("Provider returned an empty response")
        val usage = if (prompt + completion > 0) TokenUsage(prompt, completion, prompt + completion, 0.0) else null
        return GenerateResult(content, usage = usage, finishReason = finish, model = requestedModel)
    }
}

class RemoteProviderException(val httpStatus: Int, message: String) :
    IllegalStateException("Remote provider error ($httpStatus): $message")
