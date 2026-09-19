package com.jarvis.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Raw microphone capture. Also keeps a rolling buffer of raw samples so the
 * tap-to-trigger / platform-STT path can re-attempt an utterance if the first
 * recognition session yields nothing.
 *
 * utteranceSamples() replaces a blind "last N seconds" window with real
 * energy-based endpointing: it marks where speech actually began (after a
 * period of silence) and returns audio from that point forward, instead of
 * a fixed trailing window that silently truncates the front of anything
 * longer than the window -- which is what was happening to any command
 * longer than ~3 seconds.
 */
object JarvisMic {
    private const val SAMPLE_RATE = 16000
    private const val BUFFER_SECONDS = 10 // ceiling for a full utterance, not the capture window itself
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    // Energy-based endpointing thresholds. SPEECH_RMS_THRESHOLD is deliberately
    // conservative -- tune against real observed levels (diagnostics screen
    // showed idle-room levels around 4-20 and active speech around 50-80+).
    private const val SPEECH_RMS_THRESHOLD = 35
    private const val SILENCE_HANG_MS = 700L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _lastLevel = MutableStateFlow(0)
    val lastLevel: StateFlow<Int> = _lastLevel.asStateFlow()

    /**
     * Fired once per closed utterance: after a speech burst, SILENCE_HANG_MS
     * of quiet closes the utterance and this emits. Consumers use it as the
     * end-of-speech signal (e.g. BodyCoordinator SpeechEnd → platform STT). */
    private val _speechEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val speechEnded: SharedFlow<Unit> = _speechEnded.asSharedFlow()

    private var job: Job? = null
    private var audioRecord: AudioRecord? = null

    private val bufferLock = Any()
    private val ringBuffer = ShortArray(SAMPLE_RATE * BUFFER_SECONDS)
    private var writePos = 0
    private var filled = false

    private var speechStartPos: Int? = null
    private var lastSpeechAtMs = 0L

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (_isRecording.value) return

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufSize <= 0) {
            _lastLevel.value = -1
            return
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufSize * 2
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            _lastLevel.value = -1
            return
        }

        audioRecord = record
        record.startRecording()
        _isRecording.value = true

        job = scope.launch(Dispatchers.Default) {
            val buffer = ShortArray(minBufSize)
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) {
                        sum += buffer[i] * buffer[i].toDouble()
                    }
                    val rms = sqrt(sum / read)
                    _lastLevel.value = rms.toInt()

                    val now = System.currentTimeMillis()
                    synchronized(bufferLock) {
                        if (rms > SPEECH_RMS_THRESHOLD) {
                            if (speechStartPos == null) {
                                speechStartPos = writePos // mark BEFORE this chunk is written in
                            }
                            lastSpeechAtMs = now
                        } else if (speechStartPos != null && now - lastSpeechAtMs > SILENCE_HANG_MS) {
                            speechStartPos = null // utterance closed; next speech opens a new one
                            _speechEnded.tryEmit(Unit)
                        }

                        for (i in 0 until read) {
                            ringBuffer[writePos] = buffer[i]
                            writePos = (writePos + 1) % ringBuffer.size
                            if (writePos == 0) filled = true
                        }
                    }
                }
            }
        }
    }

    /** Last ~10 seconds of raw audio, in chronological order (fallback / legacy). */
    fun recentSamples(): ShortArray = synchronized(bufferLock) {
        val size = if (filled) ringBuffer.size else writePos
        val out = ShortArray(size)
        if (!filled) {
            System.arraycopy(ringBuffer, 0, out, 0, writePos)
        } else {
            val tailLen = ringBuffer.size - writePos
            System.arraycopy(ringBuffer, writePos, out, 0, tailLen)
            System.arraycopy(ringBuffer, 0, out, tailLen, writePos)
        }
        out
    }

    /**
     * Audio from where the current/most recent utterance actually started,
     * not a blind trailing window. Falls back to recentSamples() if no
     * speech-start marker is available (e.g. called well after speech ended
     * and the silence-hang already closed it out).
     */
    fun utteranceSamples(): ShortArray = synchronized(bufferLock) {
        val start = speechStartPos ?: return recentSamples()
        val len = if (writePos >= start) writePos - start else ringBuffer.size - start + writePos
        val capped = minOf(len, ringBuffer.size)
        val out = ShortArray(capped)
        for (i in 0 until capped) {
            out[i] = ringBuffer[(start + i) % ringBuffer.size]
        }
        out
    }

    fun stop() {
        job?.cancel()
        job = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _isRecording.value = false
        speechStartPos = null
    }
}
