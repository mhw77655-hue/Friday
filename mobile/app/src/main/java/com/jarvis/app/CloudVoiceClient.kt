package com.jarvis.app

object CloudVoiceClient {
    suspend fun transcribe(samples: ShortArray): String? = null
    suspend fun speak(text: String): ByteArray? = null
}
