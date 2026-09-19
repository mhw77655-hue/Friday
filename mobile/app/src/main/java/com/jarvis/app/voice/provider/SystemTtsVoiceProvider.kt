package com.jarvis.app.voice.provider

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SystemTtsProvider"

/**
 * R2 — the Arabic + universal-fallback provider: the Android system
 * [TextToSpeech] service, running *inside* the `:voice` habitat.
 *
 * This is the only provider that covers Egyptian Arabic (ar-EG) in this
 * milestone — none of the sherpa-onnx Kokoro models ship Arabic (documented in
 * the milestone report; Piper-Arabic is the deferred R2b candidate). It is
 * deliberately secondary to the native Kokoro provider: Jarvis's English voice
 * is Kokoro, and system TTS picks up where the native provider can't.
 *
 * Non-crash-prone by nature (it is a system service), but it still lives in
 * the habitat so every voice path shares one process and one scheduler.
 */
class SystemTtsVoiceProvider(
    private val context: Context
) : VoiceProvider {

    override val id = "system"
    override val name = "Android system TTS"

    @Volatile private var tts: TextToSpeech? = null
    private val speakingUtterances = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var cancelledUtterance: String? = null

    override fun capabilities(): VoiceCapabilities {
        val languages = systemVoices()
            .mapNotNull { it.locale?.language }
            .toSet()
            .ifEmpty { setOf("en") }
        return VoiceCapabilities(
            providerId = id,
            languages = languages,
            voices = voices(),
            streaming = false,
            cloning = false,
            offline = true,
            estimatedLatencyMs = 300,
            estimatedMemoryMb = 20,
            modelSizeMb = 0,
            startupCostMs = 800,
            quality = VoiceQuality.MEDIUM,
            interruptible = true,
            needsPhonemizer = false
        )
    }

    override fun voices(): List<VoiceInfo> = systemVoices().map { v ->
        VoiceInfo(
            id = v.name,
            name = v.name,
            language = v.locale?.toLanguageTag() ?: "en",
            quality = VoiceQuality.MEDIUM
        )
    }

    override suspend fun initialize(context: Context): VoiceInitResult {
        if (tts != null) return VoiceInitResult(ok = true)
        val completer = CompletableDeferred<Boolean>()
        // TextToSpeech must be constructed on a thread with a Looper.
        val engine = withContext(Dispatchers.Main) {
            TextToSpeech(context.applicationContext) { status ->
                // Must not reference `engine` here — it is still being constructed.
                completer.complete(status == TextToSpeech.SUCCESS)
            }
        }
        tts = engine
        return withContext(Dispatchers.IO) {
            val ok = completer.await()
            if (ok) VoiceInitResult(ok = true)
            else {
                Log.w(TAG, "TTS init failed")
                VoiceInitResult(ok = false, error = "TTS init failed")
            }
        }
    }

    override suspend fun synthesize(
        text: String,
        language: String,
        voiceId: String?,
        params: VoiceSynthesisParams,
        outFile: File
    ): VoiceSynthesisResult = withContext(Dispatchers.IO) {
        val engine = tts
        if (engine == null) {
            return@withContext VoiceSynthesisResult(success = false, error = "TTS not initialized")
        }
        val locale = localeFor(language, voiceId)
        val langResult = engine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            return@withContext VoiceSynthesisResult(
                success = false, error = "language not supported by system TTS: $language")
        }
        if (voiceId != null) {
            systemVoices().firstOrNull { it.name == voiceId }?.let { engine.voice = it }
        }
        engine.setSpeechRate(params.speechRate)
        engine.setPitch(params.pitch)

        val utteranceId = params.utteranceId
        val bundle = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val done = CompletableDeferred<VoiceSynthesisResult>()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { speakingUtterances.add(id ?: "") }
            override fun onDone(id: String?) {
                speakingUtterances.remove(id ?: "")
                done.complete(
                    VoiceSynthesisResult(
                        success = true,
                        sampleRate = 24000,
                        channels = 1,
                        durationMs = (text.length * 50L / params.speechRate.toDouble()).toLong().coerceAtLeast(300),
                        audioFile = outFile.takeIf { it.exists() && it.length() > 44 }
                    )
                )
            }
            override fun onError(id: String?) {
                speakingUtterances.remove(id ?: "")
                done.complete(VoiceSynthesisResult(success = false, error = "TTS synthesis error"))
            }
        })

        val code = engine.synthesizeToFile(text, bundle, outFile, utteranceId)
        if (code != TextToSpeech.SUCCESS) {
            return@withContext VoiceSynthesisResult(success = false, error = "synthesizeToFile code $code")
        }
        done.await()
    }

    override fun cancel() {
        tts?.stop()
        speakingUtterances.clear()
    }

    override suspend fun healthCheck(): ProviderHealth {
        val engine = tts
        val healthy = engine != null && engine.isLanguageAvailable(Locale.US) != TextToSpeech.LANG_MISSING_DATA
        return ProviderHealth(healthy = healthy, providerId = id)
    }

    override fun release() {
        runCatching { tts?.shutdown() }
        tts = null
        speakingUtterances.clear()
    }

    // ───────────────────────────────────────────────── internal

    private val voiceCache = mutableListOf<android.speech.tts.Voice>()

    private fun systemVoices(): List<android.speech.tts.Voice> {
        val engine = tts ?: return emptyList()
        return voiceCache.ifEmpty {
            engine.voices?.toList()?.also { voiceCache.addAll(it) } ?: emptyList()
        }
    }

    private fun localeFor(language: String, voiceId: String?): Locale {
        voiceId?.let { id ->
            systemVoices().firstOrNull { it.name == id }?.locale?.let { return it }
        }
        return when {
            language.startsWith("ar") -> Locale.forLanguageTag("ar-EG")
            language.startsWith("en") -> Locale.US
            else -> Locale.forLanguageTag(language)
        }
    }
}
