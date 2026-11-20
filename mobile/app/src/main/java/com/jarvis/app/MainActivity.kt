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
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (com.jarvis.app.runtime.FridayRuntimeController.isEnabled(this)) {
                com.jarvis.app.runtime.FridayRuntimeController.startWithMicrophone(this)
            } else {
                JarvisMic.start(lifecycleScope)
            }
            if (intent.getBooleanExtra(
                    com.jarvis.app.voice.FridayVoiceInteractionSession.EXTRA_FRIDAY_VOICE_INVOCATION,
                    false
                )
            ) beginAssistantListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSharedVideoIntent(intent)
        ShizukuBridge.init(this)
        // KWS/Vosk/TTS are owned by JarvisEngine, started once in
        // JarvisApplication.onCreate() — never re-initialized or released
        // here. That per-Activity churn was itself a crash source.

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            if (com.jarvis.app.runtime.FridayRuntimeController.isEnabled(this)) {
                com.jarvis.app.runtime.FridayRuntimeController.startWithMicrophone(this)
            } else {
                JarvisMic.start(lifecycleScope)
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (hasMicPermission && intent.getBooleanExtra(
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
        if (!com.jarvis.app.runtime.FridayRuntimeController.isEnabled(this) ||
            !com.jarvis.app.runtime.FridayRuntimeController.isMicrophoneServiceActive(this)
        ) {
            JarvisMic.stop()
        }
        // KWS/Vosk/TTS intentionally NOT released here — they belong to
        // JarvisEngine for the life of the process, not this Activity.
    }
}
