package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.app.video.VideoIntakeRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.jarvis.app.ui.navigation.JarvisNavHost
import com.jarvis.app.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    private var pendingConversationStart = false

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val shouldStartConversation = pendingConversationStart || intent.getBooleanExtra(
                com.jarvis.app.voice.FridayVoiceInteractionSession.EXTRA_FRIDAY_VOICE_INVOCATION,
                false
            )
            pendingConversationStart = false
            if (com.jarvis.app.runtime.FridayRuntimeController.isEnabled(this)) {
                if (shouldStartConversation) {
                    com.jarvis.app.runtime.FridayRuntimeController.startCore(this)
                } else {
                    com.jarvis.app.runtime.FridayRuntimeController.startWithMicrophone(this)
                }
            }
            if (shouldStartConversation) beginAssistantListening()
        } else {
            pendingConversationStart = false
            Toast.makeText(this, "Microphone permission is needed for voice conversations.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSharedVideoIntent(intent)
        ShizukuBridge.init(this)
        // Voice input/output are owned by JarvisEngine and initialized once
        // by JarvisApplication; this Activity only requests runtime permission.

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            if (com.jarvis.app.runtime.FridayRuntimeController.isEnabled(this)) {
                com.jarvis.app.runtime.FridayRuntimeController.startWithMicrophone(this)
            }
        }
        if (intent.getBooleanExtra(
                com.jarvis.app.voice.FridayVoiceInteractionSession.EXTRA_FRIDAY_VOICE_INVOCATION,
                false
            )
        ) beginAssistantListening()

        // Ensure the Companion Core is built before the first composition
        // renders the presence orb. JarvisEngine.init runs async on its engine
        // thread; this is idempotent (no-op if already built) and cheap (pure
        // object-graph construction + a lane thread start, no I/O).
        com.jarvis.app.companioncore.presence.CompanionCoreHolder.init(applicationContext)

        setContent {
            JarvisTheme {
                JarvisNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuBridge.refresh()
        // Reattach the already-authorized capability service after Friday is reopened.
        if (ShizukuBridge.isGranted.value) {
            ScreenBridge.bind(applicationContext)
        }
        if (com.jarvis.app.runtime.FridayRuntimeController.isEnabled(this)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // This visible-activity handoff is the permitted place to
                // restore microphone capture after boot or service restart.
                com.jarvis.app.runtime.FridayRuntimeController.startWithMicrophone(this)
            } else {
                com.jarvis.app.runtime.FridayRuntimeController.startCore(this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedVideoIntent(intent)
        if (intent.getBooleanExtra(
                com.jarvis.app.voice.FridayVoiceInteractionSession.EXTRA_FRIDAY_VOICE_INVOCATION,
                false
            )
        ) beginAssistantListening()
    }


    /**
     * The conversation screen must pass through the Activity permission gate.
     * Starting SpeechRecognizer directly from Compose can create AudioRecord
     * before RECORD_AUDIO is granted, producing a first-turn failure.
     */
    fun toggleVoiceConversationFromUi() {
        val body = runCatching { JarvisEngine.getBodyCoordinator() }.getOrNull()
        if (body?.voiceConversationActive?.value == true) {
            body.stopVoiceConversation()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingConversationStart = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        beginAssistantListening()
    }

    private fun beginAssistantListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        lifecycleScope.launch {
            val readiness = JarvisEngine.readinessState.first {
                it is EngineReadinessState.Ready || it is EngineReadinessState.FailedToWake
            }
            if (readiness is EngineReadinessState.Ready) {
                JarvisEngine.getBodyCoordinator().startVoiceConversation()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Friday's voice system is not ready yet.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun handleSharedVideoIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) return
        lifecycleScope.launch {
            runCatching { VideoIntakeRepository(applicationContext).importSharedIntent(intent) }
                .onSuccess { items ->
                    val mediaCount = items.count { it.localPath != null }
                    val linkCount = items.count { it.sharedUrl != null }
                    val message = when {
                        items.isEmpty() -> "Friday could not find a video or TikTok link in that share."
                        mediaCount > 0 -> "Imported $mediaCount video(s). Open More → Video Experience for their status."
                        else -> "Saved $linkCount video link(s). Share the actual video file when you want Friday to analyze it."
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
                .onFailure {
                    Toast.makeText(this@MainActivity, it.message ?: "Video import failed.", Toast.LENGTH_LONG).show()
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Friday's voice habitat intentionally remains process-scoped; this
        // Activity must not tear it down during navigation or configuration changes.
    }
}
