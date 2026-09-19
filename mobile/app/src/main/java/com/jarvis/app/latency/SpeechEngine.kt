package com.jarvis.app.latency

import android.content.Context
import android.media.MediaPlayer
import com.jarvis.app.CloudVoiceClient
import com.jarvis.app.Telemetry
import com.jarvis.app.TtsBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Speech for the latency layer — two channels with deliberately different
 * latency budgets:
 *
 * - [fastSpeak] (ack): near-instant, pre-synthesized, on-device. Used for the
 *   first spoken reply and short local-action lines. Never waits on a network.
 * - [fullSpeak] (deep reply): cloud TTS when the client is wired, on-device
 *   fallback otherwise. This is the slow path's audio and is only ever reached
 *   once the real answer exists.
 *
 * [fullSpeak] flushes any in-flight ack first so the real answer takes over
 * the channel cleanly instead of talking over the acknowledgment.
 */
object SpeechEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun fastSpeak(phrase: String) = AckSpeech.fastSpeak(phrase)

    fun fullSpeak(text: String) {
        if (text.isBlank()) return
        scope.launch {
            TtsBridge.stopSpeaking() // flush the ack / any prior playback
            val cloudAudio = CloudVoiceClient.speak(text)
            val played = cloudAudio?.let { playCloudAudio(it) } ?: false
            Telemetry.logTts(usedCloud = played)
            if (!played) {
                TtsBridge.speak(text)
            }
        }
    }

    private fun playCloudAudio(bytes: ByteArray): Boolean {
        val ctx = appContext ?: return false
        return try {
            val tmp = File(ctx.cacheDir, "cloud_tts_${System.currentTimeMillis()}.wav")
            tmp.writeBytes(bytes)
            val player = MediaPlayer()
            player.setDataSource(tmp.absolutePath)
            player.setOnCompletionListener {
                TtsBridge.clearCloudPlayback()
                it.release()
                tmp.delete()
            }
            player.prepare()
            TtsBridge.registerCloudPlayback(player)
            player.start()
            true
        } catch (e: Exception) {
            false
        }
    }
}
