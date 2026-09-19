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
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OllamaAdapter - HTTP adapter for Ollama
 * Uses /api/generate and /api/chat endpoints
 */
class OllamaAdapter(private val context: Context?) : ModelProvider {
    override val providerType = ModelProviderType.OLLAMA

    private var baseUrl = "http://127.0.0.1:11434"
    private var currentModel = "llama3.2:3b"
    private var currentConfig: ProviderConfig = ProviderConfig.Default
    override val config: ProviderConfig? get() = currentConfig

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    fun configure(modelSource: com.jarvis.app.env.ModelSource, config: ProviderConfig) {
        currentConfig = config
        when (modelSource) {
            is com.jarvis.app.env.ModelSource.Local -> {
                baseUrl = "http://${modelSource.path}"
            }
            is com.jarvis.app.env.ModelSource.Remote -> {
                if (modelSource.host != "auto-discover") {
                    baseUrl = "http://${modelSource.host}"
                }
            }
            is com.jarvis.app.env.ModelSource.None -> {
                baseUrl = "http://127.0.0.1:11434"
            }
        }
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
        var usage: TokenUsage? = null

        source.use { bufferedSource ->
            while (true) {
                val line = bufferedSource.readUtf8Line() ?: break
                if (line.isNotBlank()) {
                    try {
                        val json = JSONObject(line)
                        val response = json.optString("response", "")
                        if (response.isNotEmpty()) {
                            accumulated += response
                            emit(StreamEvent.Token(response, accumulated))
                        }
                        if (json.getBoolean("done")) {
                            val promptTokens = json.optInt("prompt_eval_count", 0)
                            val completionTokens = json.optInt("eval_count", 0)
                            usage = TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens)
                            emit(StreamEvent.Done(usage))
                            break
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
                .url("$baseUrl/api/tags")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val healthy = response.isSuccessful
                val modelLoaded = healthy
                ProviderHealth(
                    isHealthy = healthy,
                    latencyMs = latency,
                    modelLoaded = modelLoaded,
                    currentModel = if (healthy) currentModel else null,
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

    /**
     * List the models the Ollama server actually registers (real GET /api/tags).
     * Throws when the server is unreachable or answers non-2xx. This is the
     * ground-truth probe a caller uses to confirm a model is really there —
     * never a filesystem guess.
     */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/tags")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("listModels failed: HTTP ${response.code}")
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            val array = json.optJSONArray("models") ?: JSONArray()
            val names = mutableListOf<String>()
            for (i in 0 until array.length()) {
                array.getJSONObject(i).optString("name", "").takeIf { it.isNotBlank() }?.let { names.add(it) }
            }
            names
        }
    }

    override suspend fun load(request: LoadRequest): LoadResult = withContext(Dispatchers.IO) {
        // Ollama has no separate "load only" verb: a model is available the
        // moment the server has it registered. /api/pull fails for locally
        // imported modelfile models ("pull model manifest: file does not
        // exist"), so first probe the real registry (/api/tags) and only fall
        // back to /api/pull when the model is genuinely absent.
        try {
            val registered = listModels()
            if (request.modelId in registered) {
                return@withContext LoadResult(success = true, modelId = request.modelId)
            }
            val body = JSONObject().put("name", request.modelId)
            val req = Request.Builder()
                .url("$baseUrl/api/pull")
                .post(body.toString().toRequestBody(jsonMediaType))
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
        // Ollama doesn't have explicit unload, but we can note it
        UnloadResult(true, request.modelId)
    }

    override suspend fun modelInfo(): ModelInfo = withContext(Dispatchers.IO) {
        ModelInfo(
            id = currentModel,
            name = "Ollama ($currentModel)",
            providerType = providerType,
            contextWindow = 4096,
            maxOutputTokens = 2048,
            supportsStreaming = true,
            supportsTools = false,
            supportsVision = false
        )
    }

    override suspend fun tokenUsage(): TokenUsage = withContext(Dispatchers.IO) {
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
            put("model", currentModel)
            put("messages", messagesArr)
            put("stream", false)
            put("options", JSONObject().apply { put("num_predict", maxTokens) })
        }
        val request = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        shortClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JSONObject(response.body?.string() ?: return@use null)
            json.getJSONObject("message")
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
            messages.put(obj)
        }

        val body = JSONObject()
        body.put("model", request.modelId ?: currentModel)
        body.put("messages", messages)
        body.put("stream", stream)
        body.put("options", JSONObject().apply {
            put("temperature", request.temperature)
            put("top_p", request.topP)
            put("num_predict", request.maxTokens)
        })

        return client.newCall(
            Request.Builder()
                .url("$baseUrl/api/chat")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
        ).execute()
    }

    private fun parseGenerateResponse(response: okhttp3.Response): GenerateResult {
        val body = response.body?.string() ?: return GenerateResult("", finishReason = com.jarvis.app.model.FinishReason.ERROR)
        if (!response.isSuccessful) {
            return GenerateResult("", finishReason = com.jarvis.app.model.FinishReason.ERROR)
        }
        try {
            val json = JSONObject(body)
            val message = json.getJSONObject("message")
            val content = message.getString("content")
            return GenerateResult(content, finishReason = com.jarvis.app.model.FinishReason.STOP)
        } catch (e: Exception) {
            return GenerateResult("", finishReason = com.jarvis.app.model.FinishReason.ERROR)
        }
    }
}