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
import com.jarvis.app.model.storage.ModelPathResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LocalInferenceEngine - Android-native GGUF inference via llama.cpp JNI
 *
 * State machine: DORMANT -> PREWARMING -> READY -> GENERATING -> IDLE -> SUSPENDED -> UNLOADING
 *
 * Replaces the HTTP-based LlamaCppAdapter with direct native inference.
 */
class LocalInferenceEngine(private val context: Context) : ModelProvider {
    override val providerType = ModelProviderType.LLAMA_CPP

    companion object {
        private var nativeLoaded = false
        private val loadLock = Any()

        fun loadNative() {
            synchronized(loadLock) {
                if (!nativeLoaded) {
                    System.loadLibrary("jarvis_llama_jni")
                    nativeLoaded = true
                }
            }
        }

        /** Native state enum matching C++ LlamaState (order must match C++ LlamaState values) */
        enum class NativeState {
            DORMANT,
            PREWARMING,
            READY,
            GENERATING,
            IDLE,
            SUSPENDED,
            UNLOADING,
            FAILED
        }

        /** Native health status */
        data class NativeHealth(
            val state: NativeState,
            val modelLoaded: Boolean,
            val contextReady: Boolean,
            val errorMessage: String?,
            val lastActivityMs: Long,
            val loadTimeMs: Long,
            val firstTokenMs: Long,
            val tokensPerSec: Float,
            val peakRamBytes: Long,
            val currentRamBytes: Long,
            val promptTokens: Long,
            val completionTokens: Long,
            val totalTokens: Long,
            val cpuPercent: Float
        )

        /** Native model configuration */
        data class NativeConfig(
            val modelPath: String,
            val nCtx: Int = 4096,
            val nBatch: Int = 512,
            val nThreads: Int = 4,
            val nGpuLayers: Int = 0,
            val useMmap: Boolean = true,
            val useMlock: Boolean = false,
            val flashAttn: Boolean = false,
            val seed: Int = -1,
            val temp: Float = 0.7f,
            val topP: Float = 0.9f,
            val topK: Int = 40,
            val repeatPenalty: Float = 1.1f,
            val repeatLastN: Int = 64
        )

        /** Native generation configuration */
        data class NativeGenConfig(
            val maxTokens: Int = 512,
            val temperature: Float = 0.7f,
            val topP: Float = 0.9f,
            val topK: Int = 40,
            val repeatPenalty: Float = 1.1f,
            val stopSequences: Array<String> = emptyArray(),
            val stream: Boolean = false
        )

        external fun nativeCreate(config: NativeConfig): Long
        external fun nativeGetState(handle: Long): Int
        external fun nativeGetHealth(handle: Long): NativeHealth
        external fun nativePrewarm(handle: Long): Boolean
        external fun nativeWaitReady(handle: Long, timeoutMs: Int): Boolean
        external fun nativeGenerate(
            handle: Long,
            prompt: String,
            config: NativeGenConfig,
            callback: TokenCallback?
        ): String?
        external fun nativeCancel(handle: Long)
        external fun nativeSuspend(handle: Long): Boolean
        external fun nativeResume(handle: Long): Boolean
        external fun nativeDestroy(handle: Long)

        external fun nativeTokenCount(handle: Long, text: String): Int
        external fun nativeEncode(handle: Long, text: String, output: IntArray): Int
        external fun nativeDecode(handle: Long, tokens: IntArray): String?

        external fun nativeVersion(): String
        external fun nativeSystemInfo(): String
        external fun nativeBenchmark(handle: Long, nThreads: Int, nPrompt: Int, nGen: Int): String

        /** Token streaming callback */
        interface TokenCallback {
            fun onToken(token: String, tokenId: Int, isFinal: Boolean)
        }
    }

    private var nativeHandle: Long = 0
    private var currentConfig: NativeConfig? = null
    private var providerConfig: ProviderConfig? = null
    private val modelPathResolver = ModelPathResolver(context)
    private val stateLock = Any()

    init {
        LocalInferenceEngine.loadNative()
    }

    override val config: ProviderConfig? get() = providerConfig

    /** Configure the engine with model source and provider config */
    fun configure(
        modelSource: com.jarvis.app.env.ModelSource,
        config: ProviderConfig
    ) {
        providerConfig = config

        val params = config.parameters

        val configuredPath = when (modelSource) {
            is com.jarvis.app.env.ModelSource.Local -> modelSource.path
            else -> null
        }

        val resolvedModel = modelPathResolver.resolve(
            configuredPath = configuredPath,
            modelId = config.modelId
        ) ?: throw IllegalStateException(
            "Local GGUF model not found. " +
                "modelId=${config.modelId}, " +
                "configuredPath=$configuredPath"
        )

        currentConfig = NativeConfig(
            modelPath = resolvedModel.absolutePath,
            nCtx = params["context_window"]?.toIntOrNull() ?: 4096,
            nThreads = params["threads"]?.toIntOrNull() ?: 4,
            nGpuLayers = params["gpu_layers"]?.toIntOrNull() ?: 0,
            temp = params["temperature"]?.toFloatOrNull() ?: 0.7f,
            topP = params["top_p"]?.toFloatOrNull() ?: 0.9f,
            topK = params["top_k"]?.toIntOrNull() ?: 40
        )
    }

    /** Get the model path for loading */
    private fun getModelPath(): String {
        return currentConfig?.modelPath
            ?: modelPathResolver.resolve(
                configuredPath = null,
                modelId = providerConfig?.modelId
            )?.absolutePath
            ?: throw IllegalStateException(
                "No readable local GGUF model is available"
            )
    }

    /** Load model (create native handle) */
    override suspend fun load(request: LoadRequest): LoadResult = withContext(Dispatchers.IO) {
        val result: LoadResult = synchronized(stateLock) {
            if (nativeHandle != 0L) {
                // Already loaded
                LoadResult(success = true, modelId = request.modelId)
            } else {
                val modelPath = getModelPath()
                val file = File(modelPath)
                if (!file.exists()) {
                    LoadResult(
                        success = false,
                        modelId = request.modelId,
                        error = "Model file not found: $modelPath"
                    )
                } else {
                    val nativeConfig = currentConfig ?: NativeConfig(modelPath = modelPath)
                    nativeHandle = nativeCreate(nativeConfig)

                    if (nativeHandle == 0L) {
                        LoadResult(
                            success = false,
                            modelId = request.modelId,
                            error = "Failed to create native engine"
                        )
                    } else {
                        LoadResult(success = true, modelId = request.modelId)
                    }
                }
            }
        }
        result
    }

    /** Unload model */
    override suspend fun unload(request: UnloadRequest): UnloadResult = withContext(Dispatchers.IO) {
        synchronized(stateLock) {
            if (nativeHandle != 0L) {
                nativeDestroy(nativeHandle)
                nativeHandle = 0L
            }
        }
        UnloadResult(success = true, modelId = request.modelId)
    }

    /** Generate (non-streaming) */
    override suspend fun generate(request: GenerateRequest): GenerateResult = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(request)
        val genConfig = NativeGenConfig(
            maxTokens = request.maxTokens,
            temperature = request.temperature,
            topP = request.topP,
            topK = 40,
            stream = false
        )

        val result = if (nativeHandle != 0L) {
            nativeGenerate(nativeHandle, prompt, genConfig, null)
        } else null
        if (result == null) {
            GenerateResult(
                content = "",
                finishReason = com.jarvis.app.model.FinishReason.ERROR,
                usage = TokenUsage(0, 0, 0)
            )
        } else {
            val health = nativeGetHealth(nativeHandle)
            GenerateResult(
                content = result,
                finishReason = com.jarvis.app.model.FinishReason.STOP,
                usage = TokenUsage(
                    health.promptTokens.toInt(),
                    health.completionTokens.toInt(),
                    health.totalTokens.toInt()
                )
            )
        }
    }

    /** Stream generation */
    override fun stream(request: GenerateRequest): Flow<StreamEvent> = flow {
        val prompt = buildPrompt(request)
        val genConfig = NativeGenConfig(
            maxTokens = request.maxTokens,
            temperature = request.temperature,
            topP = request.topP,
            topK = 40,
            stream = true
        )

        if (nativeHandle == 0L) {
            emit(StreamEvent.Error("Model not loaded"))
            emit(StreamEvent.Done(null))
            return@flow
        }

        kotlinx.coroutines.coroutineScope {
            var accumulated = ""
            var usage: TokenUsage? = null
            val channel = Channel<StreamEvent>(Channel.UNLIMITED)

            val genJob = launch {
                nativeGenerate(nativeHandle, prompt, genConfig, object : TokenCallback {
                    override fun onToken(token: String, tokenId: Int, isFinal: Boolean) {
                        if (isFinal) {
                            val health = nativeGetHealth(nativeHandle)
                            usage = TokenUsage(
                                health.promptTokens.toInt(),
                                health.completionTokens.toInt(),
                                health.totalTokens.toInt()
                            )
                            channel.trySend(StreamEvent.Done(usage))
                        } else {
                            accumulated += token
                            channel.trySend(StreamEvent.Token(token, accumulated))
                        }
                    }
                })

                // Generation finished without an explicit final callback
                if (usage == null) {
                    val health = nativeGetHealth(nativeHandle)
                    usage = TokenUsage(
                        health.promptTokens.toInt(),
                        health.completionTokens.toInt(),
                        health.totalTokens.toInt()
                    )
                    channel.trySend(StreamEvent.Done(usage))
                }
                channel.close()
            }

            // Drain the channel into the flow
            for (event in channel) {
                emit(event)
            }
            genJob.join()
        }
    }.flowOn(Dispatchers.IO)

    /** Health check */
    override suspend fun health(): ProviderHealth = withContext(Dispatchers.IO) {
        if (nativeHandle == 0L) {
            ProviderHealth(
                isHealthy = false,
                latencyMs = -1,
                modelLoaded = false,
                currentModel = null,
                error = "Not loaded"
            )
        } else {
            val nativeHealth = nativeGetHealth(nativeHandle)
            ProviderHealth(
                isHealthy = nativeHealth.state == NativeState.READY || nativeHealth.state == NativeState.IDLE,
                latencyMs = nativeHealth.firstTokenMs,
                modelLoaded = nativeHealth.modelLoaded,
                currentModel = currentConfig?.modelPath?.let { File(it).name },
                error = nativeHealth.errorMessage
            )
        }
    }

    /** Model info */
    override suspend fun modelInfo(): ModelInfo = withContext(Dispatchers.IO) {
        ModelInfo(
            id = currentConfig?.modelPath?.let { File(it).name } ?: "Qwen3-1.7B-Q4_K_M",
            name = "Local Qwen3-1.7B (GGUF)",
            providerType = providerType,
            contextWindow = currentConfig?.nCtx ?: 4096,
            maxOutputTokens = 2048,
            supportsStreaming = true,
            supportsTools = false,
            supportsVision = false
        )
    }

    /** Token usage */
    override suspend fun tokenUsage(): TokenUsage = withContext(Dispatchers.IO) {
        if (nativeHandle == 0L) {
            TokenUsage(0, 0, 0)
        } else {
            val health = nativeGetHealth(nativeHandle)
            TokenUsage(
                health.promptTokens.toInt(),
                health.completionTokens.toInt(),
                health.totalTokens.toInt()
            )
        }
    }

    /** Synchronous chat for Human Core model port */
    override fun requestChat(
        messages: List<Pair<String, String>>,
        maxTokens: Int,
        timeoutMs: Long
    ): String? {
        if (nativeHandle == 0L) return null

        val prompt = messages.map { "${it.first}: ${it.second}" }.joinToString("\n")
        val genConfig = NativeGenConfig(
            maxTokens = maxTokens,
            temperature = 0.7f,
            topP = 0.9f,
            topK = 40,
            stream = false
        )

        // Wait for ready with timeout
        val ready = nativeWaitReady(nativeHandle, timeoutMs.toInt())
        if (!ready) return null

        return nativeGenerate(nativeHandle, prompt, genConfig, null)
    }

    /** Get current state */
    fun getState(): NativeState {
        if (nativeHandle == 0L) return NativeState.DORMANT
        val ordinal = nativeGetState(nativeHandle)
        return NativeState.values().firstOrNull { it.ordinal == ordinal } ?: NativeState.FAILED
    }

    /** Get detailed health */
    fun getHealth(): NativeHealth? {
        if (nativeHandle == 0L) return null
        return nativeGetHealth(nativeHandle)
    }

    /** Prewarm the model (async) */
    fun prewarm(): Boolean {
        if (nativeHandle == 0L) return false
        return nativePrewarm(nativeHandle)
    }

    /** Wait for ready */
    fun waitReady(timeoutMs: Int = 30000): Boolean {
        if (nativeHandle == 0L) return false
        return nativeWaitReady(nativeHandle, timeoutMs)
    }

    /** Cancel generation */
    fun cancel() {
        if (nativeHandle != 0L) nativeCancel(nativeHandle)
    }

    /** Suspend model (keep weights, release context) */
    fun suspend(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeSuspend(nativeHandle)
    }

    /** Resume suspended model */
    fun resume(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeResume(nativeHandle)
    }

    /** Count tokens for context budgeting */
    fun countTokens(text: String): Int {
        if (nativeHandle == 0L) return text.length / 4 // Rough estimate
        return nativeTokenCount(nativeHandle, text)
    }

    /** Encode text to tokens */
    fun encode(text: String, output: IntArray): Int {
        if (nativeHandle == 0L) return -1
        return nativeEncode(nativeHandle, text, output)
    }

    /** Decode tokens to text */
    fun decode(tokens: IntArray): String? {
        if (nativeHandle == 0L) return null
        return nativeDecode(nativeHandle, tokens)
    }

    /** Check if model is loaded and ready */
    fun isReady(): Boolean {
        val state = getState()
        return state == NativeState.READY || state == NativeState.IDLE
    }

    /** Build prompt from GenerateRequest */
    private fun buildPrompt(request: GenerateRequest): String {
        val systemPrompt = "You are JARVIS, a helpful AI assistant. Be concise and natural."
        val messages = buildString {
            append(systemPrompt).append("\n\n")
            request.messages.forEach { msg ->
                append(msg.role.name.lowercase()).append(": ").append(msg.content).append("\n")
            }
            append("assistant: ")
        }
        return messages
    }

    /** Cleanup */
    fun shutdown() {
        synchronized(stateLock) {
            if (nativeHandle != 0L) {
                nativeDestroy(nativeHandle)
                nativeHandle = 0L
            }
        }
    }
}