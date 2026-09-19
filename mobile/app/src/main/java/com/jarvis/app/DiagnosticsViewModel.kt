package com.jarvis.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.JarvisEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val _healthText = MutableStateFlow("Reading battery state...")
    val healthText: StateFlow<String> = _healthText.asStateFlow()

    private val _batteryText = MutableStateFlow("Battery: reading...")
    val batteryText: StateFlow<String> = _batteryText.asStateFlow()

    private val _networkText = MutableStateFlow("Net: checking...")
    val networkText: StateFlow<String> = _networkText.asStateFlow()

    private val _shizukuText = MutableStateFlow("Shizuku: checking...")
    val shizukuText: StateFlow<String> = _shizukuText.asStateFlow()

    private val _micText = MutableStateFlow("Mic: checking...")
    val micText: StateFlow<String> = _micText.asStateFlow()

    private val _sttText = MutableStateFlow("STT: idle")
    val sttText: StateFlow<String> = _sttText.asStateFlow()

    private val _hotwordText = MutableStateFlow("Hotword: unknown")
    val hotwordText: StateFlow<String> = _hotwordText.asStateFlow()

    private val _ttsText = MutableStateFlow("Tts: idle")
    val ttsText: StateFlow<String> = _ttsText.asStateFlow()

    private val _voiceSubstrateText = MutableStateFlow("Voice Substrate: initializing...")
    val voiceSubstrateText: StateFlow<String> = _voiceSubstrateText.asStateFlow()

    private val _screenText = MutableStateFlow("Screen: not read yet")
    val screenText: StateFlow<String> = _screenText.asStateFlow()

    private val _humanCoreText = MutableStateFlow("Human Core: initializing...")
    val humanCoreText: StateFlow<String> = _humanCoreText.asStateFlow()

    private val _failureText = MutableStateFlow("Failures: none")
    val failureText: StateFlow<String> = _failureText.asStateFlow()

    private var lastBatteryText = ""
    private var lastNetworkText = "Net: checking..."
    private var lastNotifText = "Notif: checking..."
    private var lastShizukuText = "Shizuku: checking..."
    private var lastMicText = "Mic: checking..."
    private var lastSttText = "STT: idle"
    private var lastHotwordText = "Hotword: unknown"
    private var lastBrainText = "Brain: idle"
    private var lastTtsText = "Tts: idle"
    private var lastScreenText = "Screen: not read yet"
    private var lastSelfKnowledgeText = "Self: loading manifest..."
    private var lastHumanCoreText = "Human Core: initializing..."
    private var lastFailureText = "Failures: none"
    private var lastVoiceSubstrateText = "Voice Substrate: initializing..."

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            lastBatteryText = if (pct >= 0) {
                "Battery: $pct%${if (charging) " (charging)" else ""}"
            } else {
                "Battery level unavailable"
            }
            _batteryText.value = lastBatteryText
            publishHealthText()
        }
    }

    private val connectivityManager =
        getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkType()
        }

        override fun onLost(network: Network) {
            lastNetworkText = "Net: offline"
            publishHealthText()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateNetworkType(capabilities)
        }
    }

    private fun updateNetworkType(capabilities: NetworkCapabilities? = null) {
        val caps = capabilities ?: connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val type = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        }
        lastNetworkText = "Net: $type"
        publishHealthText()
    }

    private fun publishVoiceSubstrate(state: com.jarvis.app.voice.VoiceOrganismHost.SubstrateState) {
        lastVoiceSubstrateText = "Voice Substrate: ${state.state} " +
            "voice=${state.voicePid ?: "-"} app=${state.appPid} " +
            "ping=${state.pingLatencyMs?.let { "${it}ms" } ?: "-"} " +
            "restarts=${state.restartCount}" +
            (state.lastRestartMs?.let { " lastRestart=${it}ms" } ?: "")
        _voiceSubstrateText.value = lastVoiceSubstrateText
        publishHealthText()
    }

    /** R1 isolation demo: crash the :voice process; the host rebuilds it. */
    fun crashVoiceSubstrate() {
        viewModelScope.launch {
            val host = runCatching { JarvisEngine.getVoiceOrganismHost() }.getOrNull()
            if (host == null) {
                lastVoiceSubstrateText = "Voice Substrate: no host"
                _voiceSubstrateText.value = lastVoiceSubstrateText
                return@launch
            }
            val rebuiltMs = host.runCrashRestartCycle()
            lastVoiceSubstrateText += if (rebuiltMs >= 0) " cycle=OK (${rebuiltMs}ms)" else " cycle=FAILED"
            _voiceSubstrateText.value = lastVoiceSubstrateText
        }
    }

    private fun publishHealthText() {
        val battery = if (lastBatteryText.isNotEmpty()) lastBatteryText else "Reading battery state..."
        lastHumanCoreText = if (HumanCore.isInitialized()) HumanCore.describe() else "Human Core: offline"
        _humanCoreText.value = lastHumanCoreText
        _failureText.value = lastFailureText
        _healthText.value = "$battery. $lastNetworkText. $lastNotifText. $lastShizukuText. $lastMicText. $lastSttText. $lastHotwordText. $lastBrainText. $lastTtsText. $lastScreenText. $lastSelfKnowledgeText. $lastHumanCoreText. $lastFailureText. $lastVoiceSubstrateText. Real device signal — body coordinator handles voice. Telemetry: ${Telemetry.summarize()}"

        _networkText.value = lastNetworkText
        _shizukuText.value = lastShizukuText
        _micText.value = lastMicText
        _sttText.value = lastSttText
        _hotwordText.value = lastHotwordText
        _ttsText.value = lastTtsText
        _screenText.value = lastScreenText
    }

    init {
        getApplication<Application>().registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
        updateNetworkType()

        viewModelScope.launch {
            NotificationBridge.isConnected.collect { connected ->
                if (!connected) {
                    lastNotifText = "Notif: access not granted"
                    publishHealthText()
                }
            }
        }
        // R1 voice substrate: stream the :voice habitat state into the row.
        // The host is created asynchronously by JarvisEngine.init, so read it
        // defensively — the row stays "initializing..." until it appears.
        viewModelScope.launch {
            val host = runCatching { JarvisEngine.getVoiceOrganismHost() }.getOrNull() ?: return@launch
            host.state.collect { publishVoiceSubstrate(it) }
        }
        viewModelScope.launch {
            NotificationBridge.notificationCount.collect { count ->
                if (NotificationBridge.isConnected.value) {
                    lastNotifText = "Notif: connected, $count seen"
                    publishHealthText()
                }
            }
        }
        viewModelScope.launch {
            ShizukuBridge.isAvailable.collect { available ->
                lastShizukuText = when {
                    !available -> "Shizuku: not running"
                    ShizukuBridge.isGranted.value -> "Shizuku: running, granted"
                    else -> "Shizuku: running, not granted"
                }
                publishHealthText()
            }
        }
        viewModelScope.launch {
            ShizukuBridge.isGranted.collect { granted ->
                if (ShizukuBridge.isAvailable.value) {
                    lastShizukuText = if (granted) "Shizuku: running, granted" else "Shizuku: running, not granted"
                    publishHealthText()
                }
                if (granted) {
                    ScreenBridge.bind(getApplication())
                }
            }
        }
        viewModelScope.launch {
            ScreenBridge.lastScreenText.collect { text ->
                lastScreenText = "Screen: $text"
                publishHealthText()
            }
        }
        viewModelScope.launch {
            JarvisMic.isRecording.collect { recording ->
                lastMicText = if (recording) "Mic: capturing" else "Mic: not capturing"
                publishHealthText()
            }
        }
        viewModelScope.launch {
            JarvisMic.lastLevel.collect { level ->
                if (JarvisMic.isRecording.value) {
                    lastMicText = "Mic: capturing, level $level"
                    publishHealthText()
                }
            }
        }
        viewModelScope.launch {
            TtsBridge.status.collect { s ->
                lastTtsText = "Tts: $s"
                publishHealthText()
            }
        }
        viewModelScope.launch {
            SpeechBridge.status.collect { s ->
                lastSttText = "STT: $s"
                publishHealthText()
            }
        }
        viewModelScope.launch {
            SpeechBridge.lastResult.collect { heard ->
                if (heard.isNotBlank()) {
                    lastSttText = "STT: heard \"$heard\""
                    publishHealthText()
                }
            }
        }
        viewModelScope.launch {
            com.jarvis.app.voice.HotwordAvailabilityProbe.availability.collect { a ->
                lastHotwordText = "Hotword: $a"
                publishHealthText()
            }
        }
        viewModelScope.launch {
            JarvisEngine.modelManager?.status?.collect { s ->
                lastBrainText = s
                publishHealthText()
            }
        }

        // Nervous-system failure surface: live summary of active failures +
        // subsystem health, recomputed whenever either changes.
        viewModelScope.launch {
            val surface = try { JarvisEngine.getFailureSurface() } catch (e: IllegalStateException) { null }
            if (surface != null) {
                combine(surface.worstSeverity, surface.currentFailures, surface.subsystemHealth) {
                    worst, failures, health ->
                    summarizeFailures(worst, failures, health)
                }.collect { summary ->
                    lastFailureText = summary
                    publishHealthText()
                }
            }
        }
    }

    /** Deterministic human-readable summary of the nervous-system failure state. */
    private fun summarizeFailures(
        worst: com.jarvis.app.failure.FailureSeverity,
        failures: List<com.jarvis.app.failure.FailureAggregate>,
        health: Map<String, com.jarvis.app.failure.SubsystemHealth>
    ): String {
        if (worst == com.jarvis.app.failure.FailureSeverity.INFO) {
            val down = health.values.count { it.status == com.jarvis.app.failure.SubsystemStatus.DOWN }
            val degraded = health.values.count { it.status == com.jarvis.app.failure.SubsystemStatus.DEGRADED }
            return "Failures: none ($down down, $degraded degraded)"
        }
        val active = failures
            .filter { it.isActive }
            .take(3)
            .joinToString("; ") { "${it.subsystem}: ${it.currentSeverity.name} x${it.occurrenceCount}" }
        return "Failures: $worst — $active"
    }
}