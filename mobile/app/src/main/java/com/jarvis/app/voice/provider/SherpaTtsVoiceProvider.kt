package com.jarvis.app.voice.provider

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.jarvis.app.voice.VoiceModelEntry
import com.jarvis.app.voice.VoiceModelManager
import com.jarvis.app.voice.VoiceModelManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SherpaTtsProvider"

/**
 * R2 — the first production native provider: sherpa-onnx **Kokoro** running
 * entirely inside the `:voice` habitat.
 *
 * The app process never loads this ONNX runtime or the model — [OfflineTts] is
 * constructed here, serialized through the habitat's [com.jarvis.app.voice.VoiceScheduler],
 * and its native inference can at worst kill the `:voice` process (which the
 * host then rebuilds) — never the app.
 *
 * Model lifecycle (download / admission / unload) is owned by
 * [VoiceModelManager]; this provider only owns the loaded [OfflineTts] instance.
 * Voice selection maps `speaker_<i>` voice ids to the model's speaker index.
 */
class SherpaTtsVoiceProvider(
    private val modelManager: VoiceModelManager,
    private val modelEntry: VoiceModelEntry = VoiceModelManifest.kokoroEnInt8
) : VoiceProvider {

    override val id = "sherpa"
    override val name = "sherpa-onnx Kokoro"

    private val ttsCache = ConcurrentHashMap<String, OfflineTts>()
    private val cancelled = AtomicBoolean(false)

    override fun capabilities(): VoiceCapabilities {
        val entry = modelEntry
        return VoiceCapabilities(
            providerId = id,
            languages = entry.languages,
            voices = voices(),
            streaming = false, // single-shot per sentence; the habitat segments
            cloning = false,
            offline = true,    // no network after the model is resident
            estimatedLatencyMs = entry.latencyMs,
            estimatedMemoryMb = entry.expectedRamMb,
            modelSizeMb = entry.sizeMb,
            startupCostMs = entry.startupCostMs,
            quality = VoiceQuality.HIGH,
            interruptible = true,
            needsPhonemizer = false
        )
    }

    override fun voices(): List<VoiceInfo> {
        val entry = modelEntry
        return (0 until entry.speakerCount).map { i ->
            VoiceInfo(
                id = "speaker_$i",
                name = "Kokoro speaker ${i + 1}",
                language = entry.languages.firstOrNull() ?: "en",
                quality = VoiceQuality.HIGH
            )
        }
    }

    override suspend fun initialize(context: Context): VoiceInitResult {
        // Models load lazily from the filesystem (assetManager=null paths); the
        // provider is ready immediately. Idempotent by design.
        return VoiceInitResult(ok = true)
    }

    override suspend fun synthesize(
        text: String,
        language: String,
        voiceId: String?,
        params: VoiceSynthesisParams,
        outFile: File
    ): VoiceSynthesisResult = withContext(Dispatchers.IO) {
        cancelled.set(false)
        val entry = modelEntry
        // The model supports the requested language (else the selector wouldn't route here).
        val acquire = modelManager.acquire(entry)
        if (acquire.isFailure) {
            return@withContext VoiceSynthesisResult(
                success = false,
                error = "model unavailable: ${acquire.exceptionOrNull()?.message}"
            )
        }
        try {
            val tts = ttsFor(entry)
            val sid = speakerIdFor(voiceId)
            val generated = tts.generateWithCallback(
                text = text,
                sid = sid,
                speed = params.speechRate,
                callback = { _ ->
                    if (cancelled.get()) 0 else 1
                }
            )
            if (cancelled.get() || generated.samples.isEmpty()) {
                return@withContext VoiceSynthesisResult(
                    success = false,
                    error = if (cancelled.get()) "cancelled" else "empty synthesis output"
                )
            }
            writeWav16(outFile, generated.sampleRate, generated.samples)
            VoiceSynthesisResult(
                success = true,
                sampleRate = generated.sampleRate,
                channels = 1,
                durationMs = (generated.samples.size * 1000L / generated.sampleRate).coerceAtLeast(1),
                audioFile = outFile
            )
        } catch (e: Throwable) {
            Log.w(TAG, "synthesis failed: ${e.message}")
            VoiceSynthesisResult(success = false, error = e.message)
        } finally {
            modelManager.release(entry)
        }
    }

    override fun cancel() {
        cancelled.set(true)
    }

    override suspend fun healthCheck(): ProviderHealth {
        val loaded = ttsCache.containsKey(modelEntry.id)
        return ProviderHealth(
            healthy = loaded || modelManager.isPresent(modelEntry.id),
            providerId = id,
            error = if (loaded) null else "model not loaded yet (lazy)"
        )
    }

    override fun release() {
        ttsCache.forEach { (_, tts) -> runCatching { tts.release() } }
        ttsCache.clear()
    }

    // ───────────────────────────────────────────────── internal

    private fun ttsFor(entry: VoiceModelEntry): OfflineTts {
        ttsCache[entry.id]?.let { return it }
        val files = modelManager.modelFiles(entry)
            ?: throw IllegalStateException("model ${entry.id} files not present")
        val modelDir = files["model"]!!.parentFile
        val espeakDir = File(modelDir, "espeak-ng-data")
        // v1.12.40 builder: voices.isNotEmpty() selects the Kokoro model config;
        // assetManager=null makes every path a filesystem path (no AssetManager).
        val config = getOfflineTtsConfig(
            modelDir = modelDir.absolutePath,
            modelName = entry.modelFile,
            acousticModelName = "",
            vocoder = "",
            voices = entry.voicesFile,
            lexicon = "",
            dataDir = if (espeakDir.exists()) espeakDir.absolutePath else "",
            dictDir = "",
            ruleFsts = "",
            ruleFars = "",
            numThreads = 2,
            isKitten = false
        )
        Log.i(TAG, "loading ${entry.id} into :voice habitat")
        val tts = OfflineTts(assetManager = null, config = config)
        ttsCache[entry.id] = tts
        return tts
    }

    private fun speakerIdFor(voiceId: String?): Int {
        if (voiceId.isNullOrBlank()) return 0
        val i = voiceId.removePrefix("speaker_").toIntOrNull() ?: 0
        return i.coerceAtLeast(0)
    }

    private fun writeWav16(file: File, sampleRate: Int, samples: FloatArray) {
        val bytes = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF header
        bytes.put("RIFF".toByteArray(Charsets.US_ASCII))
        bytes.putInt(36 + samples.size * 2)
        bytes.put("WAVE".toByteArray(Charsets.US_ASCII))
        bytes.put("fmt ".toByteArray(Charsets.US_ASCII))
        bytes.putInt(16)
        bytes.putShort(1)                 // PCM
        bytes.putShort(1)                 // mono
        bytes.putInt(sampleRate)
        bytes.putInt(sampleRate * 2)      // byte rate
        bytes.putShort(2)                 // block align
        bytes.putShort(16)                // bits per sample
        bytes.put("data".toByteArray(Charsets.US_ASCII))
        bytes.putInt(samples.size * 2)
        for (s in samples) {
            val clamped = s.coerceIn(-1f, 1f)
            bytes.putShort((clamped * 32767f).toInt().toShort())
        }
        FileOutputStream(file).use { it.write(bytes.array()) }
    }
}
