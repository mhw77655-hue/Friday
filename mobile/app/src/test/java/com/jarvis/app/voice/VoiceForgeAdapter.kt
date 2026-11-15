package com.jarvis.app.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * VOICE-FORGE-EGYPTIAN-KAREN-TTS — the wire contract between the Kotlin speech
 * backend and the local Chatterbox Multilingual inference server.
 *
 * [VoiceForgeSynthesizer] is the single HTTP hop JARVIS's spoken-reply path
 * talks to. Production wires the real [VoiceForgeAdapter] (okhttp against the
 * Termux server at [VoiceForgeConfig.baseUrl]); tests swap ONLY this hop with a
 * recording fake, exactly like ReasoningTierServeGroundTruthTest swaps the last
 * model hop — every other object on the drive path is the real production class.
 */
interface VoiceForgeSynthesizer {

    /** Synthesize [VoiceForgeSynthesisRequest.text] into WAV PCM audio. */
    suspend fun synthesize(request: VoiceForgeSynthesisRequest): VoiceForgeSynthesisResponse

    /** Probe the server's health + the checkpoint it has loaded. */
    suspend fun health(): VoiceForgeHealth
}

/**
 * A single synthesis request, mirroring OllamaAdapter's request shape: the
 * endpoint + checkpoint + generation parameters travel WITH the text so the
 * backend never assumes server-side defaults.
 */
data class VoiceForgeSynthesisRequest(
    val text: String,
    val language: String,
    val checkpoint: String,
    val exaggeration: Float,
    val cfgWeight: Float,
    val audioPromptPath: String?
)

/** Synthesis outcome from the final HTTP hop. */
sealed interface VoiceForgeSynthesisResponse {
    /** 16-bit PCM WAV bytes produced by the server. */
    class Audio(
        val wavBytes: ByteArray,
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Long
    ) : VoiceForgeSynthesisResponse

    /** The server answered but could not produce audio. */
    data class Failed(val reason: String) : VoiceForgeSynthesisResponse

    /** The server could not be reached / timed out / threw. */
    object Unreachable : VoiceForgeSynthesisResponse
}

/** Server health snapshot. [checkpoint] is what the server reports loaded. */
data class VoiceForgeHealth(
    val healthy: Boolean,
    val latencyMs: Long? = null,
    val checkpoint: String? = null,
    val error: String? = null
)

/**
 * Static configuration for [VoiceForgeAdapter] — the production defaults the
 * backend must send, never the server's own defaults:
 *  - [defaultCheckpoint] — Chatterbox Multilingual base weights.
 *  - [egyptianCheckpoint] — the Egyptian fine-tuned checkpoint
 *    (NAMAA-Egyptian-TTS; oddadmix/chatterbox-egyptian-v0 is the recorded
 *    alternate pending the A/B on Venon's reference clip).
 *  - [exaggeration] / [cfgWeight] — generation parameters tuned toward
 *    calm/precise/warm-formal delivery.
 *  - [audioPromptPath] — Venon's own recorded reference clip (voice cloning).
 *    The committed asset SLOT exists at
 *    mobile/app/src/main/assets/voiceforge/ and stays null here until the real
 *    recording is landed (it is human input the agent cannot manufacture).
 */
data class VoiceForgeConfig(
    val baseUrl: String = VOICE_FORGE_DEFAULT_URL,
    val defaultCheckpoint: String = "chatterbox-multilingual",
    val egyptianCheckpoint: String = "NAMAA-Egyptian-TTS",
    val exaggeration: Float = 0.65f,
    val cfgWeight: Float = 1.7f,
    val audioPromptPath: String? = null,
    /** AC7: shared-secret bearer token; null disables auth (dev mode). */
    val authToken: String? = null
) {
    companion object {
        const val VOICE_FORGE_DEFAULT_URL = "http://127.0.0.1:8765"
        const val DEFAULT_CHECKPOINT = "chatterbox-multilingual"
        const val EGYPTIAN_CHECKPOINT = "NAMAA-Egyptian-TTS"
        const val DEFAULT_EXAGGERATION = 0.65f
        const val DEFAULT_CFG_WEIGHT = 1.7f
        const val DEFAULT_ASSET_PATH = "assets/voiceforge/venon_voice_reference.wav"
        /** AC7: shared-secret bearer token; must match --token on voiceforge_server.py. */
        const val DEFAULT_AUTH_TOKEN = "jarvis-voiceforge-local"
    }
}

/**
 * VoiceForgeAdapter — the real HTTP client for the local Chatterbox
 * Multilingual server, mirroring OllamaAdapter's shape (nullable-Context
 * constructor, okhttp, org.json, /health probe). Never touches the platform
 * TextToSpeech: this is JARVIS's own voice.
 */
class VoiceForgeAdapter(context: android.content.Context?) : VoiceForgeSynthesizer {

    private var config: VoiceForgeConfig = VoiceForgeConfig()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    /** (Re)configure endpoint/checkpoint/generation parameters (idempotent). */
    fun configure(config: VoiceForgeConfig) {
        this.config = config
    }

    val configuredDefaults: VoiceForgeConfig get() = config

    override suspend fun synthesize(request: VoiceForgeSynthesisRequest): VoiceForgeSynthesisResponse =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("text", request.text)
                .put("language", request.language)
                .put("checkpoint", request.checkpoint)
                .put("exaggeration", request.exaggeration.toDouble())
                .put("cfg_weight", request.cfgWeight.toDouble())
            request.audioPromptPath?.let { body.put("audio_prompt_path", it) }

            val httpResponse = runCatching {
                val reqBuilder = Request.Builder()
                    .url("${config.baseUrl}/v1/synthesize")
                    .post(body.toString().toRequestBody(jsonMediaType))
                config.authToken?.let {
                    reqBuilder.addHeader("Authorization", "Bearer $it")
                }
                client.newCall(reqBuilder.build()).execute()
            }.getOrElse { return@withContext VoiceForgeSynthesisResponse.Unreachable }

            httpResponse.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext VoiceForgeSynthesisResponse.Failed("HTTP ${resp.code}")
                }
                val json = runCatching { JSONObject(resp.body?.string() ?: "{}") }.getOrNull()
                    ?: return@withContext VoiceForgeSynthesisResponse.Failed("malformed response")
                val wavBase64 = json.optString("wav_base64", "")
                if (wavBase64.isBlank()) {
                    return@withContext VoiceForgeSynthesisResponse.Failed("empty wav_base64")
                }
                val bytes = runCatching { Base64.getDecoder().decode(wavBase64) }
                    .getOrElse { return@withContext VoiceForgeSynthesisResponse.Failed("invalid wav_base64") }
                VoiceForgeSynthesisResponse.Audio(
                    wavBytes = bytes,
                    sampleRate = json.optInt("sample_rate", 24000),
                    channels = json.optInt("channels", 1),
                    durationMs = json.optLong("duration_ms", 0L)
                )
            }
        }

    override suspend fun health(): VoiceForgeHealth = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val response = runCatching {
            val reqBuilder = Request.Builder().url("${config.baseUrl}/health").get()
            config.authToken?.let {
                reqBuilder.addHeader("Authorization", "Bearer $it")
            }
            client.newCall(reqBuilder.build()).execute()
        }.getOrElse {
            return@withContext VoiceForgeHealth(
                healthy = false,
                latencyMs = System.currentTimeMillis() - start,
                error = it.message
            )
        }
        response.use { resp ->
            val json = runCatching { JSONObject(resp.body?.string() ?: "{}") }.getOrNull()
            VoiceForgeHealth(
                healthy = resp.isSuccessful,
                latencyMs = System.currentTimeMillis() - start,
                checkpoint = json?.optString("checkpoint") ?: config.defaultCheckpoint,
                error = if (!resp.isSuccessful) "HTTP ${resp.code}" else null
            )
        }
    }
}