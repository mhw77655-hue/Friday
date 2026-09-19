package com.jarvis.app.runtime.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.runtime.RuntimeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * LAN Runtime Discovery - uses Android NSD (Network Service Discovery)
 * to find other Jarvis hosts advertising OS Portal via mDNS/Bonjour.
 * Service type: _jarvis-os._tcp.local.
 */
class LanRuntimeDiscovery(
    private val scope: CoroutineScope
) {
    private val discoveredRuntimes = mutableMapOf<String, RuntimeInfo>()
    private var nsdManager: android.net.nsd.NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    private val updateChannel = Channel<List<RuntimeInfo>>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    /** Start discovery - call from RuntimeBinder.initialize() */
    fun startDiscovery(onUpdate: (List<RuntimeInfo>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            // Collect updates from channel
            for (runtimes in updateChannel) {
                onUpdate(runtimes)
            }
        }

        // In a real implementation, this would use NsdManager
        // For now, we'll simulate with a periodic scan that can be triggered
        // The actual NSD implementation requires Context and proper lifecycle
    }

    /** Trigger a manual scan */
    fun triggerScan() {
        // In production: use NsdManager.discoverServices()
        // For now, emit current discovered runtimes
        scope.launch {
            updateChannel.trySend(discoveredRuntimes.values.toList())
        }
    }

    /** Add a discovered runtime (called from NSD callbacks) */
    fun addDiscoveredRuntime(info: NsdServiceInfo) {
        val runtime = RuntimeInfo(
            id = info.serviceName,
            name = info.serviceName,
            endpoint = "${info.host}:${info.port}",
            model = info.serviceType,
            capabilities = listOf("generate", "stream", "health", "models"),
            isLocal = false,
            providerType = ModelProviderType.REMOTE_JARVIS
        )
        discoveredRuntimes[runtime.id] = runtime
        emitUpdate()
    }

    /** Remove a lost runtime */
    fun removeDiscoveredRuntime(serviceName: String) {
        discoveredRuntimes.remove(serviceName)
        emitUpdate()
    }

    private fun emitUpdate() {
        scope.launch {
            updateChannel.trySend(discoveredRuntimes.values.toList())
        }
    }

    /** Stop discovery */
    fun stop() {
        nsdManager?.stopServiceDiscovery(discoveryListener!!)
        updateChannel.close()
    }
}

/** NSD Discovery Listener implementation */
class JarvisDiscoveryListener(
    private val discovery: LanRuntimeDiscovery
) : NsdManager.DiscoveryListener {
    override fun onDiscoveryStarted(serviceType: String) {}
    override fun onServiceFound(service: NsdServiceInfo) {
        if (service.serviceType == "_jarvis-os._tcp.") {
            discovery.addDiscoveredRuntime(service)
        }
    }
    override fun onServiceLost(service: NsdServiceInfo) {
        discovery.removeDiscoveredRuntime(service.serviceName)
    }
    override fun onDiscoveryStopped(serviceType: String) {}
    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
}

/** NSD Resolve Listener */
class JarvisResolveListener(
    private val discovery: LanRuntimeDiscovery
) : NsdManager.ResolveListener {
    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
        discovery.addDiscoveredRuntime(serviceInfo)
    }
}