package com.jarvis.app.latency

import android.util.Log
import com.jarvis.app.JarvisEngine

/**
 * Compatibility entry point for the latency layer.
 *
 * Both acknowledgements and full replies use Friday's selected neural voice
 * habitat. This class deliberately has no Android/Google TTS fallback: losing
 * the voice organ is a visible degraded capability, not a change of identity.
 */
object SpeechEngine {
    fun fastSpeak(phrase: String) = AckSpeech.fastSpeak(phrase)

    fun fullSpeak(text: String) {
        if (text.isBlank()) return
        val output = JarvisEngine.getVoiceOutputPath()
        if (output == null) {
            Log.w("SpeechEngine", "Friday neural voice path unavailable; generic TTS suppressed")
            return
        }
        output.speak(text)
    }
}
