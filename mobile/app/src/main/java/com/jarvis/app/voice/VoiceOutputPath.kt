package com.jarvis.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
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
        val profile = profileStore.get(DefaultJarvisVoiceProfile.id) ?: DefaultJarvisVoiceProfile
        val lang = language ?: profile.language
        val myJob = scope.launch {
            _isSpeaking.value = true
            cancelled = false
            try {
                for (segment in segment(text)) {
                    if (cancelled) break
                    val spoken = habitatSpeak(segment, lang, voiceId ?: profile.preferredVoice, profile, vocabulary)
                    if (!spoken && fallback != null) {
                        runCatching { fallback.speakStreaming(segment, vocabulary ?: emptyVocabulary()) }
                    }
                }
            } catch (e: Throwable) {
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
        if (substrate.state != VoiceOrganismHost.State.BOUND) return false
        if (host.voiceState.value == VoiceOrganismHost.VoiceState.FAILED) return false

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
        } ?: return false

        return withContext(Dispatchers.IO) {
            playWav(audio.audioFile, audio.sampleRate, audio.channels)
        }
    }

    // ───────────────────────────────────────────────── playback

    private fun playWav(file: File, sampleRate: Int, channels: Int): Boolean {
        if (cancelled) return true
        val pcm = readWavPcm16(file, channels) ?: return false
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
        return try {
            track.write(pcm, 0, pcm.size)
            track.play()
            while (track.playState == AudioTrack.PLAYSTATE_PLAYING && !cancelled) {
                Thread.sleep(20)
            }
            runCatching { track.stop() }
            true
        } catch (e: Throwable) {
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
