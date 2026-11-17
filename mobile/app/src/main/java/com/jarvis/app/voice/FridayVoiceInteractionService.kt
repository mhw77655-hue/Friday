package com.jarvis.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.jarvis.app.MainActivity

/**
 * System-selected assistant entry point. Android keeps the selected
 * VoiceInteractionService available for assistant invocations; the heavy
 * interaction work belongs to the session service.
 */
class FridayVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
    }
}

class FridayVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle): VoiceInteractionSession =
        FridayVoiceInteractionSession(this)
}

/**
 * Hands the system assistant invocation to Friday's own foreground experience.
 * MainActivity owns the existing mic permission and JarvisMic lifecycle.
 */
class FridayVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_FRIDAY_VOICE_INVOCATION, true)
        }
        startVoiceActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_FRIDAY_VOICE_INVOCATION = "com.jarvis.app.voice.INVOCATION"
    }
}
