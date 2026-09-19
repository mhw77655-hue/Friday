package com.jarvis.app

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object TtsBridge {
    private var tts: JarvisTts? = null

    private val _status = MutableStateFlow("not started")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Written from the ack path (UI thread) and read/released from the speech
    // engine's background thread — volatile so the fast path never races the
    // full-reply flush.
    @Volatile private var activeCloudPlayer: MediaPlayer? = null
    @Volatile private var activeAckPlayer: MediaPlayer? = null

    fun attach(instance: JarvisTts) {
        tts = instance
    }

    fun updateStatus(value: String) {
        _status.value = value
        _isSpeaking.value = value == "speaking"
    }

    fun speak(text: String) {
        JarvisEngine.run {
            tts?.speak(text)
        }
    }

    /**
     * Pre-synthesize a phrase to a cache WAV on the engine thread (latency
     * layer preload). [onResult] receives the cache file when synthesis
     * finishes, or null when the engine is unavailable or synthesis failed.
     */
    fun synthesizeToCache(text: String, onResult: (File?) -> Unit) {
        JarvisEngine.run {
            val instance = tts
            if (instance != null) instance.synthesizeToCacheFile(text, onResult) else onResult(null)
        }
    }

    /**
     * Play a pre-synthesized ack WAV (latency-first fast path) — near-instant,
     * no live synthesis. Returns true when playback actually started; false
     * when the file failed to load (caller falls back to live TTS).
     */
    fun playCached(file: File): Boolean {
        try {
            stopSpeaking()
            val player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                activeAckPlayer = null
                _isSpeaking.value = false
                it.release()
            }
            player.setOnErrorListener { _, _, _ ->
                activeAckPlayer = null
                _isSpeaking.value = false
                true
            }
            player.prepare()
            activeAckPlayer = player
            _isSpeaking.value = true
            player.start()
            return true
        } catch (e: Exception) {
            _isSpeaking.value = false
            return false
        }
    }

    fun registerCloudPlayback(player: MediaPlayer) {
        activeCloudPlayer = player
        _isSpeaking.value = true
    }

    fun clearCloudPlayback() {
        activeCloudPlayer = null
        _isSpeaking.value = false
    }

    fun stopSpeaking() {
        JarvisEngine.run {
            tts?.interrupt()
        }
        listOfNotNull(activeCloudPlayer, activeAckPlayer).forEach { player ->
            try {
                if (player.isPlaying) player.stop()
                player.release()
            } catch (e: Exception) { }
        }
        activeCloudPlayer = null
        activeAckPlayer = null
        _isSpeaking.value = false
    }
}
