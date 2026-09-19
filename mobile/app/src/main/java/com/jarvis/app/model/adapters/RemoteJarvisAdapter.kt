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
 * RemoteJarvisAdapter - HTTP adapter for another Jarvis host (OS Portal)
 * Connects to the OS Portal API on port 8150
 */
class RemoteJarvisAdapter(private val context: Context?) : ModelProvider {
    override val providerType = ModelProviderType.REMOTE_JARVIS

    private var baseUrl = "http://127.0.0.1:8150"
    private var authToken: String? = null
    private var currentConfig: ProviderConfig = ProviderConfig.Default
    override val config: ProviderConfig? get() = currentConfig

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    fun configure(modelSource: com.jarvis.app.env.ModelSource, config: ProviderConfig) {
        currentConfig = config
        when (modelSource) {
            is com.jarvis.app.env.ModelSource.Remote -> {
                if (modelSource.host != "auto-discover") {
                    baseUrl = "http://${modelSource.host}"
                }
            }
            is com.jarvis.app.env.ModelSource.Local -> {
                baseUrl = "http://${modelSource.path}"
            }
            is com.jarvis.app.env.ModelSource.None -> {
                baseUrl = "http://127.0.0.1:8150"
            }
        }
        authToken = config.authToken
    }

    override suspend fun generate(request: GenerateRequest): GenerateResult = withContext(Dispatchers.IO) {
        val response = sendOsPortalRequest(request, stream = false)
        parseOsPortalResponse(response)
    }

    override fun stream(request: GenerateRequest) = flow {
        val response = sendOsPortalRequest(request, stream = true)
        val source = response.body?.source() ?: return@flow

        var accumulated = ""
        var usage: TokenUsage? = null

        source.use { bufferedSource ->
            while (true) {
                val line = bufferedSource.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.substring(6)
                    if (data == "[DONE]") {
                        emit(StreamEvent.Done(usage))
                        break
                    }
                    try {
                        val json = JSONObject(data)
                        val choice = json.getJSONArray("choices").getJSONObject(0)
                        val delta = choice.optJSONObject("delta")
                        val content = delta?.optString("content") ?: ""
                        if (content.isNotEmpty()) {
                            accumulated += content
                            emit(StreamEvent.Token(content, accumulated))
                        }
                        if (json.has("usage")) {
                            val u = json.getJSONObject("usage")
                            usage = TokenUsage(
                                u.getInt("prompt_tokens"),
                                u.getInt("completion_tokens"),
                                u.getInt("total_tokens")
                            )
                        }
                    } catch (e: Exception) {
                        emit(StreamEvent.Error("Parse error: ${e.message}"))
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
                ProviderHealth(
                    isHealthy = healthy,
                    latencyMs = latency,
                    modelLoaded = healthy,
                    currentModel = "remote-jarvis",
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
        // OS Portal model switching via /os/api/models/load
        try {
            val body = JSONObject().put("model_id", request.modelId)
            val req = Request.Builder()
                .url("$baseUrl/os/api/models/load")
                .post(body.toString().toRequestBody(jsonMediaType))
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            client.newCall(req).execute().use { response ->
                LoadResult(
                    success = response.isSuccessful,
                    modelId = request.modelId,
                    error = if (!response.isSuccessful) "HTTP ${response.code}" else null
                )
            }
        } catch (e: Exception) {
            LoadResult(false, request.modelId, e.message)
        }
    }

    override suspend fun unload(request: UnloadRequest): UnloadResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("model_id", request.modelId)
            val req = Request.Builder()
                .url("$baseUrl/os/api/models/unload")
                .post(body.toString().toRequestBody(jsonMediaType))
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            client.newCall(req).execute().use { response ->
                UnloadResult(
                    success = response.isSuccessful,
                    modelId = request.modelId,
                    error = if (!response.isSuccessful) "HTTP ${response.code}" else null
                )
            }
        } catch (e: Exception) {
            UnloadResult(false, request.modelId, e.message)
        }
    }

    override suspend fun modelInfo(): ModelInfo = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/os/api/models")
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val active = json.optString("active_model", "unknown")
                    ModelInfo(
                        id = active,
                        name = "Remote Jarvis ($active)",
                        providerType = providerType,
                        contextWindow = json.optInt("context_window", 4096),
                        maxOutputTokens = json.optInt("max_tokens", 2048),
                        supportsStreaming = true,
                        supportsTools = json.optBoolean("supports_tools", false),
                        supportsVision = json.optBoolean("supports_vision", false)
                    )
                } else null
            }
        }.getOrNull() ?: ModelInfo(
            id = "unknown",
            name = "Remote Jarvis",
            providerType = providerType,
            contextWindow = 4096,
            maxOutputTokens = 2048,
            supportsStreaming = true,
            supportsTools = false,
            supportsVision = false
        )
    }

    override suspend fun tokenUsage(): TokenUsage = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/os/api/telemetry")
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val usage = json.optJSONObject("token_usage")
                    if (usage != null) {
                        TokenUsage(
                            usage.optInt("prompt_tokens", 0),
                            usage.optInt("completion_tokens", 0),
                            usage.optInt("total_tokens", 0)
                        )
                    } else null
                } else null
            }
        }.getOrNull() ?: TokenUsage(0, 0, 0)
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
            put("max_tokens", maxTokens)
            put("stream", false)
        }
        val request = Request.Builder()
            .url("$baseUrl/v1/complete")
            .post(body.toString().toRequestBody(jsonMediaType))
            .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
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

    private fun sendOsPortalRequest(request: GenerateRequest, stream: Boolean): okhttp3.Response {
        val messages = JSONArray()
        request.messages.forEach { msg ->
            val obj = JSONObject()
            obj.put("role", msg.role.name.lowercase())
            obj.put("content", msg.content)
            msg.name?.let { obj.put("name", it) }
            messages.put(obj)
        }

        val body = JSONObject()
        body.put("messages", messages)
        body.put("max_tokens", request.maxTokens)
        body.put("temperature", request.temperature)
        body.put("top_p", request.topP)
        body.put("stream", stream)
        request.stopSequences.ifNotEmpty { body.put("stop", JSONArray(it)) }

        val requestBuilder = Request.Builder()
            .url("$baseUrl/v1/complete")
            .post(body.toString().toRequestBody(jsonMediaType))

        authToken?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }

        return client.newCall(requestBuilder.build()).execute()
    }

    private fun parseOsPortalResponse(response: okhttp3.Response): GenerateResult {
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