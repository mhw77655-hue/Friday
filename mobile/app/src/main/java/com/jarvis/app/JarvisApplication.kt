package com.jarvis.app

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jarvis.app.diagnostics.DeviceReporter
import com.jarvis.app.life.IndependentLifeCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class JarvisApplication : Application() {
    private val reporterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Initialize telemetry before engine boot so early lifecycle/resource
        // events are not lost; JarvisEngine.init also initializes the same file.
        Telemetry.init(this)
        // Friday's identity/continuity substrate is initialized before the
        // optional model and language-engine stack. Friday remains present
        // and stateful when every model provider is absent or unavailable.
        val lifeCore = IndependentLifeCore.initialize(this)
        reporterScope.launch { lifeCore.observe("system.application_created") }
        JarvisEngine.init(this)

        // Process-scoped background/foreground for the Companion Core (plan
        // §4.3, audit R-A3). ProcessLifecycleOwner fires ON_STOP only when the
        // LAST activity stops — i.e. the whole process backgrounds — so a
        // rotation or a config change cannot spuriously suspend the presence
        // engine. The resource reporter follows the same process lifecycle.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                val core = com.jarvis.app.companioncore.presence.CompanionCoreHolder.instance()
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        reporterScope.launch { lifeCore.setForeground(false) }
                        core?.onBackground()
                        DeviceReporter.stopSampling()
                    }
                    Lifecycle.Event.ON_START -> {
                        reporterScope.launch { lifeCore.setForeground(true) }
                        core?.onForeground()
                        DeviceReporter.startSampling(this@JarvisApplication, reporterScope)
                    }
                    else -> Unit
                }
            }
        })
    }
}
