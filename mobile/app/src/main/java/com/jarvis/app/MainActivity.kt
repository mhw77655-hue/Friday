package com.jarvis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import com.jarvis.app.video.VideoIntakeRepository
import kotlinx.coroutines.launch
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.ui.navigation.JarvisNavHost
import com.jarvis.app.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            JarvisMic.start(lifecycleScope)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSharedContent(intent)
        ShizukuBridge.init()
        // End the Human Core session when the app goes to the background so
        // session-boundary work (bond growth, baseline coupling, reflection)
        // actually happens on backgrounding instead of only when the
        // ViewModel is cleared — which never happens on a plain home-button
        // exit (HUMAN_CORE_AUDIT M-4). endSession is idempotent. Guarded by
        // isChangingConfigurations so a rotation (which also stops the
        // activity) cannot end the session or flush session-boundary work.
        // Companion Core background/foreground is process-scoped now — driven
        // by ProcessLifecycleOwner in JarvisApplication (plan §4.3, audit
        // R-A3) — so this activity observer no longer suspends it.
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP && !isChangingConfigurations) {
                    HumanCore.endSession()
                }
            }
        })
        // KWS/Vosk/TTS are owned by JarvisEngine, started once in
        // JarvisApplication.onCreate() — never re-initialized or released
        // here. That per-Activity churn was itself a crash source.

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            JarvisMic.start(lifecycleScope)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedContent(intent)
    }

    private fun handleSharedContent(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND && incoming?.action != Intent.ACTION_SEND_MULTIPLE) return
        lifecycleScope.launch {
            runCatching { VideoIntakeRepository(applicationContext).importSharedIntent(incoming) }
                .onSuccess { imported ->
                    val message = if (imported.isEmpty()) "No video or TikTok link was found in the shared item."
                        else "Friday received ${imported.size} item(s). Open Video Experience to review them."
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
                .onFailure { error ->
                    Toast.makeText(this@MainActivity, error.message ?: "Friday could not import this video.", Toast.LENGTH_LONG).show()
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        JarvisMic.stop()
        // KWS/Vosk/TTS intentionally NOT released here — they belong to
        // JarvisEngine for the life of the process, not this Activity.
    }
}
