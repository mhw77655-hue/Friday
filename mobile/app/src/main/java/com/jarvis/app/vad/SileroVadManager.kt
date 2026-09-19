package com.jarvis.app.vad

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * SileroVadManager - Android-native Voice Activity Detection using Silero VAD ONNX model
 *
 * Audio Gate pattern:
 * - DORMANT: Not initialized
 * - LISTENING: Waiting for speech (VAD running)
 * - SPEECH: Speech detected
 * - SILENCE: Silence after speech (end of utterance)
 * - FAILED: Error state
 *
 * Features:
 * - Adaptive thresholds via environmental profiles
 * - Speech start/end callbacks
 * - Metrics and health monitoring
 * - Configurable sensitivity
 */
class SileroVadManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        private var nativeLoaded = false
        private val loadLock = Any()

        fun loadNative() {
            synchronized(loadLock) {
                if (!nativeLoaded) {
                    System.loadLibrary("jarvis_silero_vad")
                    nativeLoaded = true
                }
            }
        }

        /** VAD states matching native (order must match C++ enum values) */
        enum class State {
            IDLE,
            LISTENING,
            SPEECH,
            SILENCE,
            FAILED
        }

        /** Environmental profiles (order must match native profile indices) */
        enum class Profile {
            QUIET,
            NORMAL,
            NOISY,
            VEHICLE,
            OUTDOOR,
            CUSTOM
        }

        /** VAD result */
        data class Result(
            val state: State,
            val speechProbability: Float,
            val speechDetected: Boolean,
            val timestampMs: Long,
            val audioLevel: Int
        )

        /** VAD configuration */
        data class Config(
            val modelPath: String = "",
            val sampleRate: Int = 16000,
            val threshold: Float = 0.5f,
            val minSpeechDurationMs: Int = 150,
            val minSilenceDurationMs: Int = 500,
            val speechPadMs: Int = 200,
            val windowSizeSamples: Int = 512,
            val useGpu: Boolean = false,
            val numThreads: Int = 2
        )

        /** VAD metrics */
        data class Metrics(
            val totalChunksProcessed: Long,
            val speechChunks: Long,
            val silenceChunks: Long,
            val avgSpeechProbability: Float,
            val avgInferenceMs: Float,
            val peakRamBytes: Long,
            val currentRamBytes: Long,
            val modelLoaded: Boolean,
            val errorMessage: String?
        )

        external fun nativeCreate(config: Config): Long
        external fun nativeDestroy(handle: Long)
        external fun nativeGetState(handle: Long): Int
        external fun nativeProcess(handle: Long, audio: ShortArray, numSamples: Int, timestampMs: Long): NativeResult
        external fun nativeProcessWithCallbacks(
            handle: Long,
            audio: ShortArray,
            numSamples: Int,
            timestampMs: Long,
            onSpeechStart: SpeechStartCallback?,
            onSpeechEnd: SpeechEndCallback?,
            userData: Long
        )
        external fun nativeReset(handle: Long)
        external fun nativeUpdateConfig(handle: Long, config: Config): Boolean
        external fun nativeGetConfig(handle: Long): Config
        external fun nativeApplyProfile(handle: Long, profile: Int): Boolean
        external fun nativeGetProfileConfig(profile: Int, sampleRate: Int): Config
        external fun nativeGetMetrics(handle: Long): Metrics
        external fun nativeIsHealthy(handle: Long): Boolean
        external fun nativeVersion(): String
        external fun nativeOnnxVersion(): String

        /** Native result struct */
        data class NativeResult(
            val stateOrdinal: Int,
            val speechProbability: Float,
            val speechDetected: Boolean,
            val timestampMs: Long,
            val audioLevel: Int,
            val errorMessage: String?
        )

        /** Speech start callback */
        interface SpeechStartCallback {
            fun onSpeechStart(timestampMs: Long, userData: Long)
        }

        /** Speech end callback */
        interface SpeechEndCallback {
            fun onSpeechEnd(timestampMs: Long, durationMs: Int, userData: Long)
        }
    }

    private var nativeHandle: Long = 0
    private var currentConfig: Config = Config()
    private val stateLock = Any()

    // State flows
    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _lastResult = MutableStateFlow<Result?>(null)
    val lastResult: StateFlow<Result?> = _lastResult.asStateFlow()

    private val _metrics = MutableStateFlow<Metrics?>(null)
    val metrics: StateFlow<Metrics?> = _metrics.asStateFlow()

    // Callbacks
    private var onSpeechStartListener: ((Long) -> Unit)? = null
    private var onSpeechEndListener: ((Long, Int) -> Unit)? = null

    init {
        SileroVadManager.loadNative()
    }

    /** Initialize VAD with model from assets or filesDir */
    suspend fun initialize(modelFileName: String = "silero_vad.onnx"): Boolean = withContext(Dispatchers.IO) {
        synchronized(stateLock) {
            if (nativeHandle != 0L) {
                Log.w("SileroVadManager", "Already initialized")
                return@withContext true
            }

            // Try to get model from filesDir first, then assets
            val modelFile = File(context.filesDir, "models/$modelFileName")
            if (!modelFile.exists()) {
                modelFile.parentFile?.mkdirs()
                try {
                    context.assets.open("models/$modelFileName").use { input ->
                        modelFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    Log.e("SileroVadManager", "Failed to copy model from assets", e)
                    // Try without models/ prefix
                    val altFile = File(context.filesDir, modelFileName)
                    if (!altFile.exists()) {
                        try {
                            context.assets.open(modelFileName).use { input ->
                                altFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        } catch (e2: Exception) {
                            Log.e("SileroVadManager", "Model not found in assets either", e2)
                            _state.value = State.FAILED
                            return@withContext false
                        }
                    }
                    currentConfig = currentConfig.copy(modelPath = altFile.absolutePath)
                }
            } else {
                currentConfig = currentConfig.copy(modelPath = modelFile.absolutePath)
            }

            nativeHandle = nativeCreate(currentConfig)
            if (nativeHandle == 0L) {
                _state.value = State.FAILED
                Log.e("SileroVadManager", "Failed to create native VAD engine")
                return@withContext false
            }

            _state.value = State.IDLE
            Log.i("SileroVadManager", "VAD initialized with model: ${currentConfig.modelPath}")
            return@withContext true
        }
    }

    /** Apply environmental profile */
    fun applyProfile(profile: Profile): Boolean {
        synchronized(stateLock) {
            if (nativeHandle == 0L) return false
            val success = nativeApplyProfile(nativeHandle, profile.ordinal)
            if (success) {
                currentConfig = nativeGetProfileConfig(profile.ordinal, currentConfig.sampleRate)
                Log.i("SileroVadManager", "Applied profile: $profile")
            }
            return success
        }
    }

    /** Update configuration at runtime */
    fun updateConfig(config: Config): Boolean {
        synchronized(stateLock) {
            if (nativeHandle == 0L) return false
            val success = nativeUpdateConfig(nativeHandle, config)
            if (success) currentConfig = config
            return success
        }
    }

    /** Get current config */
    fun getConfig(): Config {
        synchronized(stateLock) {
            if (nativeHandle != 0L) {
                currentConfig = nativeGetConfig(nativeHandle)
            }
            return currentConfig
        }
    }

    /** Process audio chunk (call from audio thread) */
    fun processAudio(audio: ShortArray, timestampMs: Long = System.currentTimeMillis()): Result? {
        if (nativeHandle == 0L) return null

        val nativeResult = nativeProcess(nativeHandle, audio, audio.size, timestampMs)
        val state = State.values().firstOrNull { it.ordinal == nativeResult.stateOrdinal } ?: State.FAILED

        val result = Result(
            state = state,
            speechProbability = nativeResult.speechProbability,
            speechDetected = nativeResult.speechDetected,
            timestampMs = nativeResult.timestampMs,
            audioLevel = nativeResult.audioLevel
        )

        _lastResult.value = result
        _state.value = state

        return result
    }

    /** Process audio with speech start/end callbacks */
    fun processAudioWithCallbacks(
        audio: ShortArray,
        timestampMs: Long = System.currentTimeMillis(),
        onSpeechStart: ((Long) -> Unit)? = null,
        onSpeechEnd: ((Long, Int) -> Unit)? = null
    ) {
        if (nativeHandle == 0L) return

        // Store callbacks
        this.onSpeechStartListener = onSpeechStart
        this.onSpeechEndListener = onSpeechEnd

        // Use a userData pointer to identify this manager instance
        val userData = System.identityHashCode(this).toLong()

        nativeProcessWithCallbacks(
            nativeHandle,
            audio,
            audio.size,
            timestampMs,
            object : SpeechStartCallback {
                override fun onSpeechStart(timestampMs: Long, userData: Long) {
                    if (userData == System.identityHashCode(this@SileroVadManager).toLong()) {
                        onSpeechStartListener?.invoke(timestampMs)
                    }
                }
            },
            object : SpeechEndCallback {
                override fun onSpeechEnd(timestampMs: Long, durationMs: Int, userData: Long) {
                    if (userData == System.identityHashCode(this@SileroVadManager).toLong()) {
                        onSpeechEndListener?.invoke(timestampMs, durationMs)
                    }
                }
            },
            userData
        )
    }

    /** Reset VAD state (between utterances) */
    fun reset() {
        synchronized(stateLock) {
            if (nativeHandle != 0L) {
                nativeReset(nativeHandle)
                _state.value = State.IDLE
                _lastResult.value = null
            }
        }
    }

    /** Get current metrics */
    suspend fun refreshMetrics() = withContext(Dispatchers.IO) {
        if (nativeHandle != 0L) {
            _metrics.value = nativeGetMetrics(nativeHandle)
        }
    }

    /** Check if healthy */
    fun isHealthy(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeIsHealthy(nativeHandle)
    }

    /** Shutdown and release resources */
    fun shutdown() {
        synchronized(stateLock) {
            if (nativeHandle != 0L) {
                nativeDestroy(nativeHandle)
                nativeHandle = 0L
                _state.value = State.IDLE
                _lastResult.value = null
                _metrics.value = null
            }
        }
    }

    /** Get version info */
    fun getVersion(): String = nativeVersion()
    fun getOnnxVersion(): String = nativeOnnxVersion()
}