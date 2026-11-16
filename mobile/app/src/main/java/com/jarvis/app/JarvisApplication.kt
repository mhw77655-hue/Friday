package com.jarvis.app

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jarvis.app.life.IndependentLifeCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JarvisApplication : Application() {
    private val lifeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Friday's continuity substrate starts before the optional model-backed engine.
        val lifeCore = IndependentLifeCore.initialize(this)
        lifeScope.launch { lifeCore.observe("system.application_created") }
        JarvisEngine.init(this)

        // Process-scoped background/foreground for the Companion Core (plan
        // §4.3, audit R-A3). ProcessLifecycleOwner fires ON_STOP only when the
        // LAST activity stops — i.e. the whole process backgrounds — so a
        // rotation or a config change (which stop/start the single activity)
        // cannot spurious-suspend the Presence Engine, and an off-screen orb
        // keeps rendering in split-screen until the entire process leaves the
        // foreground. The per-activity Human Core endSession hook stays in
        // MainActivity (M-4), guarded against config changes.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                val core = com.jarvis.app.companioncore.presence.CompanionCoreHolder.instance()
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        lifeScope.launch { lifeCore.setForeground(false) }
                        core?.onBackground()
                    }
                    Lifecycle.Event.ON_START -> {
                        lifeScope.launch { lifeCore.setForeground(true) }
                        core?.onForeground()
                    }
                    else -> Unit
                }
            }
        })
    }
}
