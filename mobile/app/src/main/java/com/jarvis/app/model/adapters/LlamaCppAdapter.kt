package com.jarvis.app.model.adapters

import android.content.Context
import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.model.GenerateRequest
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.LoadRequest
import com.jarvis.app.model.LoadResult
import com.jarvis.app.model.ModelInfo
import com.jarvis.app.model.ModelProvider
import com.jarvis.app.model.ProviderHealth
import com.jarvis.app.model.StreamEvent
import com.jarvis.app.model.TokenUsage
import com.jarvis.app.model.UnloadRequest
import com.jarvis.app.model.UnloadResult
import com.jarvis.app.model.config.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LlamaCppAdapter - HTTP adapter for llama.cpp / llama-server
 * Supports both /v1/chat/completions (OpenAI compatible) and /completion (legacy)
 */
class LlamaCppAdapter(private val context: Context?) : ModelProvider {
    override val providerType = ModelProviderType.LLAMA_CPP

    private var baseUrl = "http://127.0.0.1:8080"
    private var authToken: String? = null
    private var currentModel = "llama3.2-3b"
    private var currentConfig: ProviderConfig = ProviderConfig.Default
    override val config: ProviderConfig? get() = currentConfig

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    /** Configure the adapter with model source and provider config */
    fun configure(modelSource: com.jarvis.app.env.ModelSource, config: ProviderConfig) {
        currentConfig = config
        when (modelSource) {
            is com.jarvis.app.env.ModelSource.Local -> {
                // Normalize the canonical "llama-server:8080" profile host: it is
                // a label, not a resolvable DNS name, and the on-device llama-server
                // is always reachable at loopback.
                val host = modelSource.path
                    .replaceFirst("llama-server", "127.0.0.1")
                baseUrl = "http://$host"
            }
            is com.jarvis.app.env.ModelSource.Remote -> {
                if (modelSource.host != "auto-discover") {
                    baseUrl = "http://${modelSource.host}"
                }
            }
            is com.jarvis.app.env.ModelSource.None -> {
                baseUrl = "http://127.0.0.1:8080"
            }
        }
        authToken = config.authToken
        currentModel = config.modelId ?: currentModel
    }

    override suspend fun generate(request: GenerateRequest): GenerateResult = withContext(Dispatchers.IO) {
        val response = sendRequest(request, stream = false)
        parseGenerateResponse(response)
    }

    override fun stream(request: GenerateRequest) = flow {
        val response = sendRequest(request, stream = true)
        val source = response.body?.source() ?: return@flow

        var accumulated = ""
        var finishReason = com.jarvis.app.model.FinishReason.STOP
        var usage: TokenUsage? = null

        source.use { bufferedSource ->
            while (true) {
                val line = bufferedSource.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.substring(6)
                    if (data == "[DONE]") {
                        emit(com.jarvis.app.model.StreamEvent.Done(usage))
                        break
                    }
                    try {
                        val json = JSONObject(data)
                        val choice = json.getJSONArray("choices").getJSONObject(0)
                        val delta = choice.optJSONObject("delta")
                        val content = delta?.optString("content") ?: ""
                        if (content.isNotEmpty()) {
                            accumulated += content
                            emit(com.jarvis.app.model.StreamEvent.Token(content, accumulated))
                        }
                        if (choice.has("finish_reason") && !choice.isNull("finish_reason")) {
                            finishReason = com.jarvis.app.model.FinishReason.valueOf(
                                choice.getString("finish_reason").uppercase()
                            )
                        }
                        if (json.has("usage")) {
                            val u = json.getJSONObject("usage")
                            usage = TokenUsage(
                                u.getInt("prompt_tokens"),
                                u.getInt("completion_tokens"),
                                u.getInt("total_tokens")
                            )
                        }
                        // Tool calls would be parsed here
                    } catch (e: Exception) {
                        emit(com.jarvis.app.model.StreamEvent.Error("Parse error: ${e.message}"))
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun health(): ProviderHealth = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val healthy = response.isSuccessful
                val modelLoaded = healthy
                val model = if (healthy) {
                    try {
                        response.body?.string()?.let { JSONObject(it).optString("model", currentModel) }
                    } catch (e: Exception) {
                        currentModel
                    }
                } else null
                ProviderHealth(
                    isHealthy = healthy,
                    latencyMs = latency,
                    modelLoaded = modelLoaded,
                    currentModel = model,
                    error = if (!healthy) "HTTP ${response.code}" else null
                )
            }
        } catch (e: Exception) {
            ProviderHealth(
                isHealthy = false,
                latencyMs = System.currentTimeMillis() - start,
                modelLoaded = false,
                currentModel = null,
                error = e.message
            )
        }
    }

    override suspend fun load(request: LoadRequest): LoadResult = withContext(Dispatchers.IO) {
        // llama-server doesn't support dynamic model loading via API
        // This would require restarting the server with a different model
        LoadResult(
            success = false,
            modelId = request.modelId,
            error = "llama-server does not support dynamic model loading"
        )
    }

    override suspend fun unload(request: UnloadRequest): UnloadResult = withContext(Dispatchers.IO) {
        UnloadResult(
            success = false,
            modelId = request.modelId,
            error = "llama-server does not support dynamic model unloading"
        )
    }

    override suspend fun modelInfo(): ModelInfo = withContext(Dispatchers.IO) {
        ModelInfo(
            id = currentModel,
            name = "Llama.cpp ($currentModel)",
            providerType = providerType,
            contextWindow = 4096, // Default, would need to query
            maxOutputTokens = 2048,
            supportsStreaming = true,
            supportsTools = false, // llama.cpp supports tools via grammar
            supportsVision = false
        )
    }

    override suspend fun tokenUsage(): TokenUsage = withContext(Dispatchers.IO) {
        // Not available from llama-server without tracking
        TokenUsage(0, 0, 0)
    }

    override fun requestChat(
        messages: List<Pair<String, String>>,
        maxTokens: Int,
        timeoutMs: Long
    ): String? = try {
        val shortClient = client.newBuilder()
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        val messagesArr = JSONArray()
        messages.forEach { (role, content) ->
            messagesArr.put(JSONObject().apply {
                put("role", role)
                put("content", content)
            })
        }
        val body = JSONObject().apply {
            put("messages", messagesArr)
            put("model", currentModel)
            put("max_tokens", maxTokens)
            put("stream", false)
        }
        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        shortClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JSONObject(response.body?.string() ?: return@use null)
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
                .takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        null
    }

    private fun sendRequest(request: GenerateRequest, stream: Boolean): okhttp3.Response {
        val messages = JSONArray()
        request.messages.forEach { msg ->
            val obj = JSONObject()
            obj.put("role", msg.role.name.lowercase())
            obj.put("content", msg.content)
            msg.name?.let { obj.put("name", it) }
            msg.toolCallId?.let { obj.put("tool_call_id", it) }
            msg.toolCalls?.let { calls ->
                val arr = JSONArray()
                calls.forEach { call ->
                    val callObj = JSONObject()
                    callObj.put("id", call.id)
                    callObj.put("type", "function")
                    val fn = JSONObject()
                    fn.put("name", call.name)
                    fn.put("arguments", call.arguments)
                    callObj.put("function", fn)
                    arr.put(callObj)
                }
                obj.put("tool_calls", arr)
            }
            messages.put(obj)
        }

        val body = JSONObject()
        body.put("messages", messages)
        body.put("model", currentModel)
        body.put("max_tokens", request.maxTokens)
        body.put("temperature", request.temperature)
        body.put("top_p", request.topP)
        body.put("stream", stream)
        request.stopSequences.ifNotEmpty { body.put("stop", JSONArray(it)) }
        request.tools.ifNotEmpty { tools ->
            val toolsArray = JSONArray()
            tools.forEach { tool ->
                val toolObj = JSONObject()
                toolObj.put("type", "function")
                val fn = JSONObject()
                fn.put("name", tool.name)
                fn.put("description", tool.description)
                fn.put("parameters", JSONObject(tool.parameters))
                toolObj.put("function", fn)
                toolsArray.put(toolObj)
            }
            body.put("tools", toolsArray)
        }

        val requestBuilder = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(body.toString().toRequestBody(jsonMediaType))

        authToken?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }

        return client.newCall(requestBuilder.build()).execute()
    }

    private fun parseGenerateResponse(response: okhttp3.Response): GenerateResult {
        val body = response.body?.string() ?: return GenerateResult("", finishReason = com.jarvis.app.model.FinishReason.ERROR)
        if (!response.isSuccessful) {
            return GenerateResult("", finishReason = com.jarvis.app.model.FinishReason.ERROR)
        }
        try {
            val json = JSONObject(body)
            val choice = json.getJSONArray("choices").getJSONObject(0)
            val message = choice.getJSONObject("message")
            val content = message.getString("content")
            val finishReason = com.jarvis.app.model.FinishReason.valueOf(
                choice.optString("finish_reason", "STOP").uppercase()
            )
            val usage = json.optJSONObject("usage")?.let { u ->
                TokenUsage(u.getInt("prompt_tokens"), u.getInt("completion_tokens"), u.getInt("total_tokens"))
            }
            return GenerateResult(content, finishReason = finishReason, usage = usage)
        } catch (e: Exception) {
            return GenerateResult("", finishReason = com.jarvis.app.model.FinishReason.ERROR)
        }
    }
}