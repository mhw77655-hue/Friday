package ai.friday.assistant

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
import ai.friday.assistant.video.VideoIntakeRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import ai.friday.assistant.ui.navigation.FridayNavHost
import ai.friday.assistant.ui.theme.FridayTheme

class MainActivity : ComponentActivity() {
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (ai.friday.assistant.runtime.FridayRuntimeController.isEnabled(this)) {
                ai.friday.assistant.runtime.FridayRuntimeController.startWithMicrophone(this)
            } else {
                FridayMic.start(lifecycleScope)
            }
            if (intent.getBooleanExtra(
                    ai.friday.assistant.voice.FridayVoiceInteractionSession.EXTRA_FRIDAY_VOICE_INVOCATION,
                    false
                )
            ) beginAssistantListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSharedContentIntent(intent)
        ShizukuBridge.init()
        // KWS/Vosk/TTS are owned by FridayEngine, started once in
        // FridayApplication.onCreate() — never re-initialized or released
        // here. That per-Activity churn was itself a crash source.

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            if (ai.friday.assistant.runtime.FridayRuntimeController.isEnabled(this)) {
                ai.friday.assistant.runtime.FridayRuntimeController.startWithMicrophone(this)
            } else {
                FridayMic.start(lifecycleScope)
            }
        }
        // Objective A is typed-first. Do not request microphone permission just
        // because Friday was installed or opened; only an explicit voice entry
        // point or the owner's MIC action may request it.
        if (intent.getBooleanExtra(
                ai.friday.assistant.voice.FridayVoiceInteractionSession.EXTRA_FRIDAY_VOICE_INVOCATION,
                false
            )
        ) beginAssistantListening()

        // Ensure the Companion Core is built before the first composition
        // renders the presence orb. FridayEngine.init runs async on its engine
        // thread; this is idempotent (no-op if already built) and cheap (pure
        // object-graph construction + a lane thread start, no I/O).
        ai.friday.assistant.companioncore.presence.CompanionCoreHolder.init(applicationContext)

        val setupPreferences = getSharedPreferences("friday_first_launch", MODE_PRIVATE)
        val showSetup = !setupPreferences.getBoolean("completed", false)
        setContent {
            FridayTheme {
                FridayNavHost(showSetup = showSetup) {
                    setupPreferences.edit().putBoolean("completed", true).apply()
                }
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
        if (ai.friday.assistant.runtime.FridayRuntimeController.isEnabled(this)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // This visible-activity handoff is the permitted place to
                // restore microphone capture after boot or service restart.
                ai.friday.assistant.runtime.FridayRuntimeController.startWithMicrophone(this)
            } else {
                ai.friday.assistant.runtime.FridayRuntimeController.startCore(this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedContentIntent(intent)
        if (intent.getBooleanExtra(
                ai.friday.assistant.voice.FridayVoiceInteractionSession.EXTRA_FRIDAY_VOICE_INVOCATION,
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
            val readiness = FridayEngine.readinessState.first {
                it is EngineReadinessState.Ready || it is EngineReadinessState.FailedToWake
            }
            if (readiness is EngineReadinessState.Ready) {
                FridayEngine.getBodyCoordinator().apply {
                    emitEvent(ai.friday.assistant.body.BodyEvent.WakeDetected(1.0f))
                    emitEvent(ai.friday.assistant.body.BodyEvent.SpeechStart)
                }
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Friday's voice system is not ready yet.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Android shares are an input surface, not all video. Preserve the existing
     * video/TikTok intake path, but route ordinary shared text into the same
     * BodyCoordinator path as typed conversation input. This also gives the
     * owner a user-controlled way to hand a reply/report back into Friday.
     */
    private fun handleSharedContentIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) return
        lifecycleScope.launch {
            val sharedText = if (intent.action == Intent.ACTION_SEND) {
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            } else {
                ""
            }
            val imported = runCatching {
                VideoIntakeRepository(applicationContext).importSharedIntent(intent)
            }
            imported.onFailure {
                Toast.makeText(
                    this@MainActivity,
                    it.message ?: "Shared content could not be imported.",
                    Toast.LENGTH_LONG
                ).show()
            }
            val items = imported.getOrNull() ?: return@launch
            if (items.isNotEmpty()) {
                val mediaCount = items.count { it.localPath != null }
                val linkCount = items.count { it.sharedUrl != null }
                val message = when {
                    mediaCount > 0 -> "Imported $mediaCount video(s). Open More → Video Experience for their status."
                    else -> "Saved $linkCount video link(s). Share the actual video file when you want Friday to analyze it."
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                return@launch
            }

            if (intent.type?.startsWith("text/") == true && sharedText.isNotBlank()) {
                if (sharedText.length > MAX_SHARED_TEXT_CHARS) {
                    Toast.makeText(
                        this@MainActivity,
                        "That shared text is too large. Copy a shorter excerpt into Friday.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                val readiness = FridayEngine.readinessState.first {
                    it is EngineReadinessState.Ready || it is EngineReadinessState.FailedToWake
                }
                if (readiness is EngineReadinessState.Ready) {
                    FridayEngine.getBodyCoordinator().submitText(sharedText)
                    Toast.makeText(this@MainActivity, "Shared text sent to Friday.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Friday is not ready to receive shared text yet.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Friday could not find supported video, TikTok link or text in that share.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private companion object {
        const val MAX_SHARED_TEXT_CHARS = 64_000
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!ai.friday.assistant.runtime.FridayRuntimeController.isEnabled(this) ||
            !ai.friday.assistant.runtime.FridayRuntimeController.isMicrophoneServiceActive(this)
        ) {
            FridayMic.stop()
        }
        // KWS/Vosk/TTS intentionally NOT released here — they belong to
        // FridayEngine for the life of the process, not this Activity.
    }
}
