package com.jarvis.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.jarvis.app.Telemetry
import com.jarvis.app.body.StreamingTts
import com.jarvis.app.body.VocabularyStore
import com.jarvis.app.voice.provider.VoiceSynthesisParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

private const val TAG = "VoiceOutputPath"

/**
 * R2 — the app-side voice output path.
 *
 * This is where the nervous system actually *speaks*: text is segmented per
 * sentence, each sentence is synthesized by the `:voice` habitat (the
 * isolated process that owns the native models), and the returned WAV is
 * played through [AudioTrack]. The legacy in-process [StreamingTts] remains
 * as the per-sentence fallback — the body still answers even if the habitat
 * is not yet ready — but the voice itself is Jarvis's (Kokoro in the habitat),
 * not the system engine's.
 *
 * The habitat and the app share a UID, so the WAV crosses the boundary as a
 * file read from [VoiceOrganismHost.VoiceAudio.audioFile] — no Binder size cap.
 */
class VoiceOutputPath(
    private val host: VoiceOrganismHost,
    private val context: Context,
    private val scope: CoroutineScope,
    private val fallback: StreamingTts? = null,
    private val profileStore: VoiceProfileStore
) {

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    @Volatile private var cancelled = false
    private var job: Job? = null

    /** Speak [text] through the habitat, per sentence, with legacy fallback. */
    fun speak(text: String, language: String? = null, voiceId: String? = null, vocabulary: VocabularyStore? = null) {
        if (text.isBlank()) return
        interrupt()
        val requestStartedAt = SystemClock.elapsedRealtime()
        val profile = profileStore.get(DefaultJarvisVoiceProfile.id) ?: DefaultJarvisVoiceProfile
        val lang = language ?: profile.language
        val segments = segment(text)
        Telemetry.logSignal("voice_output", "requested")
        Telemetry.logSignal("voice_output_language", lang)
        Telemetry.logSignal("voice_output_segments", segments.size.toString())
        val myJob = scope.launch {
            _isSpeaking.value = true
            cancelled = false
            var habitatSegments = 0
            var fallbackSegments = 0
            try {
                for (segment in segments) {
                    if (cancelled) break
                    val spoken = habitatSpeak(segment, lang, voiceId ?: profile.preferredVoice, profile, vocabulary)
                    if (spoken) {
                        habitatSegments++
                    } else if (fallback != null) {
                        fallbackSegments++
                        Telemetry.logSignal("voice_output_route", "legacy_fallback")
                        runCatching { fallback.speakStreaming(segment, vocabulary ?: emptyVocabulary()) }
                    } else {
                        Telemetry.logSignal("voice_output_route", "no_available_output")
                    }
                }
                Telemetry.logSignal("voice_output_outcome", if (cancelled) "interrupted" else "finished")
                Telemetry.logSignal("voice_output_duration_ms", (SystemClock.elapsedRealtime() - requestStartedAt).toString())
                Telemetry.logSignal("voice_output_habitat_segments", habitatSegments.toString())
                Telemetry.logSignal("voice_output_fallback_segments", fallbackSegments.toString())
            } catch (e: Throwable) {
                Telemetry.logSignal("voice_output_outcome", "exception_${e::class.java.simpleName}")
                Log.w(TAG, "voice output failed: ${e.message}")
            } finally {
                // Only this utterance may clear the speaking flag — an earlier
                // cancelled job must not stomp the current one's state.
                if (job === this) _isSpeaking.value = false
            }
        }
        job = myJob
    }

    /** Stop playback and ask the habitat to cancel in-flight synthesis. */
    fun interrupt() {
        cancelled = true
        runCatching { host.cancelVoice() }
        runCatching { fallback?.interrupt() }
        job?.cancel()
        job = null
    }

    private suspend fun habitatSpeak(
        segment: String,
        language: String,
        voiceId: String?,
        profile: VoiceProfile,
        vocabulary: VocabularyStore?
    ): Boolean {
        // Only route through the habitat when it is bound and not failed; else
        // the caller falls through to the legacy path without blocking.
        val substrate = host.state.value
        if (substrate.state != VoiceOrganismHost.State.BOUND) {
            Telemetry.logSignal("voice_habitat", "not_bound")
            return false
        }
        if (host.voiceState.value == VoiceOrganismHost.VoiceState.FAILED) {
            Telemetry.logSignal("voice_habitat", "failed")
            return false
        }

        val synthesisStartedAt = SystemClock.elapsedRealtime()
        val params = VoiceSynthesisParams(
            speechRate = profile.speechRate,
            pitch = profile.pitch,
            pronunciationHints = pronunciationHintsOf(segment, vocabulary),
            utteranceId = "utter_${System.currentTimeMillis()}"
        )
        val audio = withContext(Dispatchers.IO) {
            host.synthesizeText(
                text = segment,
                language = language,
                voiceId = voiceId,
                params = params
            )
        }
        if (audio == null) {
            Telemetry.logSignal("voice_habitat_synthesis", "no_audio_returned")
            Telemetry.logSignal("voice_synthesis_latency_ms", (SystemClock.elapsedRealtime() - synthesisStartedAt).toString())
            return false
        }
        Telemetry.logSignal("voice_habitat_synthesis", "audio_returned")
        Telemetry.logSignal("voice_synthesis_latency_ms", (SystemClock.elapsedRealtime() - synthesisStartedAt).toString())
        Telemetry.logSignal("voice_audio_sample_rate", audio.sampleRate.toString())
        Telemetry.logSignal("voice_audio_channels", audio.channels.toString())
        Telemetry.logSignal("voice_audio_bytes", runCatching { audio.audioFile.length().toString() }.getOrDefault("unavailable"))

        return withContext(Dispatchers.IO) {
            playWav(audio.audioFile, audio.sampleRate, audio.channels)
        }
    }

    // ───────────────────────────────────────────────── playback

    private fun playWav(file: File, sampleRate: Int, channels: Int): Boolean {
        if (cancelled) return true
        val pcm = readWavPcm16(file, channels)
        if (pcm == null) {
            Telemetry.logSignal("voice_playback_outcome", "invalid_wav")
            return false
        }
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val channelMask = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding).coerceAtLeast(pcm.size)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(encoding)
                .setChannelMask(channelMask)
                .build())
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        val playbackStartedAt = SystemClock.elapsedRealtime()
        return try {
            val writtenBytes = track.write(pcm, 0, pcm.size)
            Telemetry.logSignal("voice_playback_written_bytes", writtenBytes.toString())
            if (writtenBytes <= 0) {
                Telemetry.logSignal("voice_playback_outcome", "write_failed")
                false
            } else {
                track.play()
                while (track.playState == AudioTrack.PLAYSTATE_PLAYING && !cancelled) {
                    Thread.sleep(20)
                }
                runCatching { track.stop() }
                Telemetry.logSignal("voice_playback_outcome", if (cancelled) "interrupted" else "completed")
                Telemetry.logSignal("voice_playback_duration_ms", (SystemClock.elapsedRealtime() - playbackStartedAt).toString())
                true
            }
        } catch (e: Throwable) {
            Telemetry.logSignal("voice_playback_outcome", "exception_${e::class.java.simpleName}")
            Log.w(TAG, "playback failed: ${e.message}")
            false
        } finally {
            runCatching { track.release() }
        }
    }

    /** Extract the 16-bit PCM payload from a RIFF WAV, validating the header. */
    private fun readWavPcm16(file: File, channels: Int): ByteArray? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 44) return null
                val riff = ByteArray(4); raf.readFully(riff)
                if (String(riff) != "RIFF") return null
                raf.skipBytes(4)
                val wave = ByteArray(4); raf.readFully(wave)
                if (String(wave) != "WAVE") return null
                raf.skipBytes(4) // "fmt "
                raf.skipBytes(4) // fmt chunk size
                val fmt = ShortArray(2); raf.readShort()
                val fmtChannels = raf.readShort().toInt()
                raf.skipBytes(4 + 4 + 2) // sampleRate, byteRate, blockAlign
                val bitsPerSample = raf.readShort().toInt()
                if (fmtChannels != channels || bitsPerSample != 16) return null
                // Walk chunks to "data"
                while (raf.filePointer < raf.length() - 8) {
                    val chunkId = ByteArray(4); raf.readFully(chunkId)
                    val size = Integer.reverseBytes(raf.readInt())
                    if (String(chunkId) == "data") {
                        val data = ByteArray(size)
                        raf.readFully(data)
                        return data
                    }
                    raf.skipBytes(size)
                }
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "wav read failed: ${e.message}")
            null
        }
    }

    // ───────────────────────────────────────────────── segmentation + hints

    private fun segment(text: String): List<String> =
        text.trim()
            .split(Regex("(?<=[.!?。！？])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun pronunciationHintsOf(segment: String, vocabulary: VocabularyStore?): Map<String, String> {
        if (vocabulary == null) return emptyMap()
        val hints = mutableMapOf<String, String>()
        for (word in segment.split(Regex("\\W+")).filter { it.isNotBlank() }) {
            vocabulary.getPronunciation(word, "en")?.let { hints[word] = it }
        }
        return hints
    }

    private fun emptyVocabulary(): VocabularyStore = VocabularyStore(context)
}
