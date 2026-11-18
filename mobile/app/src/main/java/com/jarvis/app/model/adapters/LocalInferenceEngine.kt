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
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
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

        @JvmStatic
        external fun nativeCreate(config: NativeConfig): Long
        @JvmStatic
        external fun nativeGetState(handle: Long): Int
        @JvmStatic
        external fun nativeGetHealth(handle: Long): NativeHealth
        @JvmStatic
        external fun nativePrewarm(handle: Long): Boolean
        @JvmStatic
        external fun nativeWaitReady(handle: Long, timeoutMs: Int): Boolean
        @JvmStatic
        external fun nativeGenerate(
            handle: Long,
            prompt: String,
            config: NativeGenConfig,
            callback: TokenCallback?
        ): String?
        @JvmStatic
        external fun nativeCancel(handle: Long)
        @JvmStatic
        external fun nativeSuspend(handle: Long): Boolean
        @JvmStatic
        external fun nativeResume(handle: Long): Boolean
        @JvmStatic
        external fun nativeDestroy(handle: Long)

        @JvmStatic
        external fun nativeTokenCount(handle: Long, text: String): Int
        @JvmStatic
        external fun nativeEncode(handle: Long, text: String, output: IntArray): Int
        @JvmStatic
        external fun nativeDecode(handle: Long, tokens: IntArray): String?

        @JvmStatic
        external fun nativeVersion(): String
        @JvmStatic
        external fun nativeSystemInfo(): String
        @JvmStatic
        external fun nativeBenchmark(handle: Long, nThreads: Int, nPrompt: Int, nGen: Int): String

        /** Token streaming callback */
        interface TokenCallback {
            fun onToken(token: String, tokenId: Int, isFinal: Boolean)
        }
    }

    private var nativeHandle: Long = 0
    @Volatile private var currentConfig: NativeConfig? = null
    @Volatile private var providerConfig: ProviderConfig? = null
    private val modelPathResolver = ModelPathResolver(context)
    // Read leases allow generation and cancellation to share the live handle;
    // unload/shutdown wait for all native calls before destroying it.
    private val stateLock = ReentrantReadWriteLock()

    init {
        LocalInferenceEngine.loadNative()
    }

    override val config: ProviderConfig? get() = providerConfig

    /** Configure the engine with model source and provider config */
    fun configure(
        modelSource: com.jarvis.app.env.ModelSource,
        config: ProviderConfig
    ) = stateLock.writeLock().withLock {
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
        val result: LoadResult = stateLock.writeLock().withLock {
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
        stateLock.writeLock().withLock {
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
            stopSequences = request.stopSequences.toTypedArray(),
            stream = false
        )

        val generated = stateLock.readLock().withLock {
            val handle = nativeHandle
            if (handle == 0L) null else {
                val text = nativeGenerate(handle, prompt, genConfig, null)
                text?.let { it to nativeGetHealth(handle) }
            }
        }
        if (generated == null) {
            GenerateResult(
                content = "",
                finishReason = com.jarvis.app.model.FinishReason.ERROR,
                usage = TokenUsage(0, 0, 0)
            )
        } else {
            val (text, health) = generated
            GenerateResult(
                content = text,
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
            stopSequences = request.stopSequences.toTypedArray(),
            stream = true
        )

        kotlinx.coroutines.coroutineScope {
            var accumulated = ""
            val channel = Channel<StreamEvent>(Channel.UNLIMITED)
            val genJob = launch {
                val generated = stateLock.readLock().withLock {
                    val handle = nativeHandle
                    if (handle == 0L) null else {
                        // Never query native health from inside a token callback:
                        // generation owns the native model mutex.
                        val text = nativeGenerate(handle, prompt, genConfig, object : TokenCallback {
                            override fun onToken(token: String, tokenId: Int, isFinal: Boolean) {
                                if (!isFinal) {
                                    accumulated += token
                                    channel.trySend(StreamEvent.Token(token, accumulated))
                                }
                            }
                        })
                        text?.let { it to nativeGetHealth(handle) }
                    }
                }
                if (generated == null) {
                    channel.trySend(StreamEvent.Error("Model not loaded or native generation failed"))
                    channel.trySend(StreamEvent.Done(null))
                } else {
                    val (text, health) = generated
                    // JNI currently returns a complete result but does not yet
                    // forward callbacks. Preserve output until native streaming
                    // is implemented, without duplicating incremental tokens.
                    if (accumulated.isEmpty() && text.isNotEmpty()) {
                        accumulated = text
                        channel.trySend(StreamEvent.Token(text, accumulated))
                    }
                    channel.trySend(StreamEvent.Done(TokenUsage(
                        health.promptTokens.toInt(),
                        health.completionTokens.toInt(),
                        health.totalTokens.toInt()
                    )))
                }
                channel.close()
            }
            for (event in channel) emit(event)
            genJob.join()
        }
    }.flowOn(Dispatchers.IO)

    /** Health check */
    override suspend fun health(): ProviderHealth = withContext(Dispatchers.IO) {
        stateLock.readLock().withLock {
            val handle = nativeHandle
            if (handle == 0L) {
                ProviderHealth(isHealthy = false, latencyMs = -1L, modelLoaded = false, currentModel = null, error = "Not loaded")
            } else {
                val nativeHealth = nativeGetHealth(handle)
                ProviderHealth(
                    isHealthy = nativeHealth.state == NativeState.READY || nativeHealth.state == NativeState.IDLE,
                    latencyMs = nativeHealth.firstTokenMs,
                    modelLoaded = nativeHealth.modelLoaded,
                    currentModel = currentConfig?.modelPath?.let { File(it).name },
                    error = nativeHealth.errorMessage
                )
            }
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
        stateLock.readLock().withLock {
            val handle = nativeHandle
            if (handle == 0L) TokenUsage(0, 0, 0) else {
                val health = nativeGetHealth(handle)
                TokenUsage(health.promptTokens.toInt(), health.completionTokens.toInt(), health.totalTokens.toInt())
            }
        }
    }

    /** Synchronous chat for Human Core model port */
    override fun requestChat(
        messages: List<Pair<String, String>>,
        maxTokens: Int,
        timeoutMs: Long
    ): String? {
        val prompt = messages.map { "${it.first}: ${it.second}" }.joinToString("\n")
        val genConfig = NativeGenConfig(maxTokens = maxTokens, temperature = 0.7f, topP = 0.9f, topK = 40, stream = false)
        return stateLock.readLock().withLock {
            val handle = nativeHandle
            if (handle == 0L || !nativeWaitReady(handle, timeoutMs.toInt())) null
            else nativeGenerate(handle, prompt, genConfig, null)
        }
    }

    /** Get current state */
    fun getState(): NativeState = stateLock.readLock().withLock {
        val handle = nativeHandle
        if (handle == 0L) NativeState.DORMANT else {
            val ordinal = nativeGetState(handle)
            NativeState.values().firstOrNull { it.ordinal == ordinal } ?: NativeState.FAILED
        }
    }

    /** Get detailed health */
    fun getHealth(): NativeHealth? = stateLock.readLock().withLock {
        nativeHandle.takeIf { it != 0L }?.let { nativeGetHealth(it) }
    }

    /** Prewarm the model (async) */
    fun prewarm(): Boolean = stateLock.readLock().withLock {
        nativeHandle.takeIf { it != 0L }?.let { nativePrewarm(it) } ?: false
    }

    /** Wait for ready */
    fun waitReady(timeoutMs: Int = 30000): Boolean = stateLock.readLock().withLock {
        nativeHandle.takeIf { it != 0L }?.let { nativeWaitReady(it, timeoutMs) } ?: false
    }

    /** Cancel generation can share a read lease with the in-flight generate call. */
    fun cancel() = stateLock.readLock().withLock {
        nativeHandle.takeIf { it != 0L }?.let { nativeCancel(it) }
    }

    /** Suspend model (keep weights, release context) */
    fun suspend(): Boolean = stateLock.readLock().withLock {
        nativeHandle.takeIf { it != 0L }?.let { nativeSuspend(it) } ?: false
    }

    /** Resume suspended model */
    fun resume(): Boolean = stateLock.readLock().withLock {
        nativeHandle.takeIf { it != 0L }?.let { nativeResume(it) } ?: false
    }

    /** Count tokens for context budgeting */
    fun countTokens(text: String): Int = stateLock.readLock().withLock {
        nativeHandle.takeIf { it != 0L }?.let { nativeTokenCount(it, text) } ?: text.length / 4
    }

    /** Encode text to tokens */
    fun encode(text: String, output: IntArray): Int = stateLock.readLock().withLock {
        nativeHandle.takeIf { it != 0L }?.let { nativeEncode(it, text, output) } ?: -1
    }

    /** Decode tokens to text */
    fun decode(tokens: IntArray): String? = stateLock.readLock().withLock {
        nativeHandle.takeIf { it != 0L }?.let { nativeDecode(it, tokens) }
    }

    /** Check if model is loaded and ready */
    fun isReady(): Boolean = stateLock.readLock().withLock {
        val handle = nativeHandle
        if (handle == 0L) false else {
            val ordinal = nativeGetState(handle)
            val state = NativeState.values().firstOrNull { it.ordinal == ordinal } ?: NativeState.FAILED
            state == NativeState.READY || state == NativeState.IDLE
        }
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
        stateLock.writeLock().withLock {
            if (nativeHandle != 0L) {
                nativeDestroy(nativeHandle)
                nativeHandle = 0L
            }
        }
    }
}