package ai.friday.assistant.model.adapters

import android.content.Context
import ai.friday.assistant.env.ModelProviderType
import ai.friday.assistant.model.GenerateRequest
import ai.friday.assistant.model.GenerateResult
import ai.friday.assistant.model.LoadRequest
import ai.friday.assistant.model.LoadResult
import ai.friday.assistant.model.ModelInfo
import ai.friday.assistant.model.ModelProvider
import ai.friday.assistant.model.ProviderHealth
import ai.friday.assistant.model.StreamEvent
import ai.friday.assistant.model.TokenUsage
import ai.friday.assistant.model.UnloadRequest
import ai.friday.assistant.model.UnloadResult
import ai.friday.assistant.model.config.ProviderConfig
import ai.friday.assistant.model.storage.ModelPathResolver
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
import java.util.concurrent.locks.ReentrantReadWriteLock

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
                    System.loadLibrary("friday_llama_jni")
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

    @Volatile private var nativeHandle: Long = 0
    private var currentConfig: NativeConfig? = null
    private var providerConfig: ProviderConfig? = null
    private val modelPathResolver = ModelPathResolver(context)
    // Read calls may run together (including cancel during generation), while
    // unload waits for every in-flight native call before freeing its handle.
    private val nativeLock = ReentrantReadWriteLock()

    private inline fun <T> withNativeRead(block: (Long) -> T): T? {
        val lock = nativeLock.readLock()
        lock.lock()
        try {
            val handle = nativeHandle
            return if (handle == 0L) null else block(handle)
        } finally {
            lock.unlock()
        }
    }

    init {
        LocalInferenceEngine.loadNative()
    }

    override val config: ProviderConfig? get() = providerConfig

    /** Configure the engine with model source and provider config */
    fun configure(
        modelSource: ai.friday.assistant.env.ModelSource,
        config: ProviderConfig
    ) {
        providerConfig = config

        val params = config.parameters

        val configuredPath = when (modelSource) {
            is ai.friday.assistant.env.ModelSource.Local -> modelSource.path
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
        val lock = nativeLock.writeLock()
        lock.lock()
        try {
            if (nativeHandle != 0L) {
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
        } finally {
            lock.unlock()
        }
    }

    /** Unload model */
    override suspend fun unload(request: UnloadRequest): UnloadResult = withContext(Dispatchers.IO) {
        val lock = nativeLock.writeLock()
        lock.lock()
        try {
            if (nativeHandle != 0L) {
                nativeDestroy(nativeHandle)
                nativeHandle = 0L
            }
        } finally {
            lock.unlock()
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

        val generated = withNativeRead { handle ->
            val content = nativeGenerate(handle, prompt, genConfig, null)
            if (content == null) null else content to nativeGetHealth(handle)
        }
        if (generated == null) {
            GenerateResult(
                content = "",
                finishReason = ai.friday.assistant.model.FinishReason.ERROR,
                usage = TokenUsage(0, 0, 0)
            )
        } else {
            val (content, health) = generated
            GenerateResult(
                content = content,
                finishReason = ai.friday.assistant.model.FinishReason.STOP,
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

        kotlinx.coroutines.coroutineScope {
            var accumulated = ""
            var usage: TokenUsage? = null
            var finalCallbackSeen = false
            val channel = Channel<StreamEvent>(Channel.UNLIMITED)

            val genJob = launch {
                val lock = nativeLock.readLock()
                lock.lock()
                try {
                    val handle = nativeHandle
                    if (handle == 0L) {
                        channel.trySend(StreamEvent.Error("Model not loaded"))
                        channel.trySend(StreamEvent.Done(null))
                        return@launch
                    }

                    val generated = nativeGenerate(handle, prompt, genConfig, object : TokenCallback {
                        override fun onToken(token: String, tokenId: Int, isFinal: Boolean) {
                            if (isFinal) {
                                // Do not call nativeGetHealth from inside the JNI
                                // callback: native generation owns the model mutex.
                                finalCallbackSeen = true
                            } else {
                                accumulated += token
                                channel.trySend(StreamEvent.Token(token, accumulated))
                            }
                        }
                    })

                    if (generated == null) {
                        channel.trySend(StreamEvent.Error("Native generation failed"))
                        channel.trySend(StreamEvent.Done(null))
                        return@launch
                    }
                    // Read health only after nativeGenerate has released its
                    // model lock. The final callback is emitted before return.
                    val health = nativeGetHealth(handle)
                    usage = TokenUsage(
                        health.promptTokens.toInt(),
                        health.completionTokens.toInt(),
                        health.totalTokens.toInt()
                    )
                    if (!finalCallbackSeen) {
                        channel.trySend(StreamEvent.Error("Native stream ended without its final callback"))
                    }
                    channel.trySend(StreamEvent.Done(usage))
                } finally {
                    lock.unlock()
                    channel.close()
                }
            }

            try {
                for (event in channel) emit(event)
                genJob.join()
            } finally {
                if (genJob.isActive) {
                    // Cancellation is allowed alongside the generator's read
                    // lock and tells native code to leave its token loop.
                    withNativeRead { nativeCancel(it) }
                    genJob.cancel()
                    genJob.join()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Health check */
    override suspend fun health(): ProviderHealth = withContext(Dispatchers.IO) {
        val nativeHealth = withNativeRead { nativeGetHealth(it) }
        if (nativeHealth == null) {
            ProviderHealth(
                isHealthy = false,
                latencyMs = -1,
                modelLoaded = false,
                currentModel = null,
                error = "Not loaded"
            )
        } else {
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
        val health = withNativeRead { nativeGetHealth(it) }
        if (health == null) {
            TokenUsage(0, 0, 0)
        } else {
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
        val prompt = messages.map { "${it.first}: ${it.second}" }.joinToString("\n")
        val genConfig = NativeGenConfig(
            maxTokens = maxTokens,
            temperature = 0.7f,
            topP = 0.9f,
            topK = 40,
            stream = false
        )

        return withNativeRead { handle ->
            if (!nativeWaitReady(handle, timeoutMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())) {
                null
            } else {
                nativeGenerate(handle, prompt, genConfig, null)
            }
        }
    }

    /** Get current state */
    fun getState(): NativeState {
        val ordinal = withNativeRead { nativeGetState(it) } ?: return NativeState.DORMANT
        return NativeState.values().firstOrNull { it.ordinal == ordinal } ?: NativeState.FAILED
    }

    /** Get detailed health */
    fun getHealth(): NativeHealth? = withNativeRead { nativeGetHealth(it) }

    /** Prewarm the model (async) */
    fun prewarm(): Boolean = withNativeRead { nativePrewarm(it) } ?: false

    /** Wait for ready */
    fun waitReady(timeoutMs: Int = 30000): Boolean =
        withNativeRead { nativeWaitReady(it, timeoutMs.coerceAtLeast(0)) } ?: false

    /** Cancel generation; read lock permits this alongside an active generation. */
    fun cancel() {
        withNativeRead { nativeCancel(it) }
    }

    /** Suspend model (keep weights, release context) */
    fun suspend(): Boolean = withNativeRead { nativeSuspend(it) } ?: false

    /** Resume suspended model */
    fun resume(): Boolean = withNativeRead { nativeResume(it) } ?: false

    /** Count tokens for context budgeting */
    fun countTokens(text: String): Int =
        withNativeRead { nativeTokenCount(it, text) } ?: (text.length / 4)

    /** Encode text to tokens */
    fun encode(text: String, output: IntArray): Int =
        withNativeRead { nativeEncode(it, text, output) } ?: -1

    /** Decode tokens to text */
    fun decode(tokens: IntArray): String? = withNativeRead { nativeDecode(it, tokens) }

    /** Check if model is loaded and ready */
    fun isReady(): Boolean {
        val state = getState()
        return state == NativeState.READY || state == NativeState.IDLE
    }

    /** Build prompt from GenerateRequest */
    private fun buildPrompt(request: GenerateRequest): String {
        val systemPrompt = "You are FRIDAY, a helpful AI assistant. Be concise and natural."
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
        val lock = nativeLock.writeLock()
        lock.lock()
        try {
            if (nativeHandle != 0L) {
                nativeDestroy(nativeHandle)
                nativeHandle = 0L
            }
        } finally {
            lock.unlock()
        }
    }
}