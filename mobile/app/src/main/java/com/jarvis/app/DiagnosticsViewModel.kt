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
import android.os.Build
import android.app.ActivityManager
import android.os.StatFs
import android.os.Environment
import org.json.JSONObject
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.app.diagnostics.DiagnosticEventLog
import com.jarvis.app.diagnostics.CrashJournal
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

    private val _individualMemoryText = MutableStateFlow("Friday individual: initializing...")
    val individualMemoryText: StateFlow<String> = _individualMemoryText.asStateFlow()

    private val _modelRuntimeText = MutableStateFlow("model runtime: checking")
    val modelRuntimeText: StateFlow<String> = _modelRuntimeText.asStateFlow()

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
    private var lastIndividualText = "Friday individual: initializing..."
    private var lastFailureText = "Failures: none"
    private var lastVoiceSubstrateText = "Voice Substrate: initializing..."

    // Only meaningful subsystem transitions are retained. Volatile microphone
    // levels and screen contents are filtered by the pure diagnostic log.
    private val diagnosticEvents = DiagnosticEventLog(capacity = 250)

    private fun modelRuntimeDiagnosticText(): String {
        val manager = JarvisEngine.modelManager ?: return "model manager unavailable"
        val provider = manager.activeProvider.value
        val health = manager.providerHealth.value
        return when {
            provider.providerType == com.jarvis.app.env.ModelProviderType.HEURISTIC ->
                "model-free fallback; no language model loaded"
            health.modelLoaded && health.isHealthy ->
                "loaded: ${health.currentModel ?: provider.config?.modelId ?: provider.providerType.name}"
            else ->
                "provider selected (${provider.providerType.name}); no model confirmed loaded"
        }
    }

    private fun individualDiagnosticText(): String {
        val individual = JarvisEngine.fridayIndividual
            ?: return "Friday individual: unavailable"
        return "Friday individual: present; experiences=${individual.experience.experienceCount()}; " +
            "associations=${individual.experience.associationCount()}"
    }

    private fun safeLastModelTurnSummary(): String = when {
        lastBrainText.startsWith("ok (local)") -> "last turn succeeded"
        lastBrainText.startsWith("sending") -> "turn in progress"
        lastBrainText.startsWith("retrying") -> "turn retrying"
        lastBrainText.startsWith("local brain unreachable") -> "last turn failed"
        else -> "no completed model turn recorded"
    }

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
        diagnosticEvents.record("battery", lastBatteryText)
        diagnosticEvents.record("network", lastNetworkText)
        diagnosticEvents.record("notifications", lastNotifText)
        diagnosticEvents.record("shizuku", lastShizukuText)
        diagnosticEvents.record("microphone", lastMicText)
        diagnosticEvents.record("speech_recognition", lastSttText)
        diagnosticEvents.record("hotword", lastHotwordText)
        val runtimeText = modelRuntimeDiagnosticText()
        _modelRuntimeText.value = runtimeText
        diagnosticEvents.record("model_runtime", runtimeText)
        diagnosticEvents.record("text_to_speech", lastTtsText)
        diagnosticEvents.record("voice_process", lastVoiceSubstrateText)
        diagnosticEvents.record("screen_context", lastScreenText)
        diagnosticEvents.record("failure_surface", lastFailureText)
        val battery = if (lastBatteryText.isNotEmpty()) lastBatteryText else "Reading battery state..."
        lastIndividualText = individualDiagnosticText()
        _individualMemoryText.value = lastIndividualText
        _failureText.value = lastFailureText
        _healthText.value = "$battery. $lastNetworkText. $lastNotifText. $lastShizukuText. $lastMicText. $lastSttText. $lastHotwordText. $runtimeText. $lastTtsText. Screen context content is excluded. $lastSelfKnowledgeText. $lastIndividualText. $lastFailureText. $lastVoiceSubstrateText. Telemetry: ${Telemetry.summarize()}"

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
                lastMicText = if (recording) "Mic: energy monitor active; speech recognition starts on request" else "Mic: off"
                publishHealthText()
            }
        }
        viewModelScope.launch {
            JarvisMic.lastLevel.collect { level ->
                if (JarvisMic.isRecording.value) {
                    lastMicText = "Mic: energy monitor active, level $level"
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

    /**
     * Creates a shareable, privacy-conscious support report.
     * It captures runtime/device/subsystem state, never chat text, transcripts,
     * notification contents, screen contents, credentials, or personal files.
     */
    fun createExportReport(): String {
        val app = getApplication<Application>()
        val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val storage = StatFs(app.filesDir.absolutePath)
        val report = JSONObject()
        report.put("report_schema", "friday-device-report-v2")
        report.put("generated_at_utc", java.time.Instant.now().toString())
        report.put("app", JSONObject()
            .put("application_id", app.packageName)
            .put("version_name", runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName
            }.getOrNull() ?: "unknown")
            .put("version_code", runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
            }.getOrDefault(-1L)))
        report.put("device", JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device_code", Build.DEVICE)
            .put("android_release", Build.VERSION.RELEASE)
            .put("sdk_int", Build.VERSION.SDK_INT)
            .put("supported_abis", org.json.JSONArray(Build.SUPPORTED_ABIS.toList())))
        report.put("resources", JSONObject()
            .put("low_memory", memory.lowMemory)
            .put("available_memory_bytes", memory.availMem)
            .put("total_memory_bytes", memory.totalMem)
            .put("threshold_bytes", memory.threshold)
            .put("app_memory_class_mb", activityManager.memoryClass)
            .put("storage_available_bytes", storage.availableBytes)
            .put("storage_total_bytes", storage.totalBytes))
        report.put("subsystems", JSONObject()
            .put("battery", batteryText.value)
            .put("network", networkText.value)
            .put("notifications", lastNotifText)
            .put("shizuku", shizukuText.value)
            .put("microphone", micText.value)
            .put("speech_recognition", sttText.value)
            .put("hotword", hotwordText.value)
            .put("text_to_speech", ttsText.value)
            .put("voice_process", voiceSubstrateText.value)
            .put("screen_context", "content intentionally omitted by privacy policy")
            .put("model_runtime", modelRuntimeDiagnosticText())
            .put("last_model_turn_status", safeLastModelTurnSummary())
            .put("individual_memory", lastIndividualText)
            .put("failure_surface", failureText.value))
        val eventSnapshot = diagnosticEvents.snapshot()
        report.put("event_history_complete", false)
        report.put("event_history_capacity", 250)
        report.put("event_history_count", eventSnapshot.size)
        report.put("events", org.json.JSONArray().apply {
            eventSnapshot.forEach { event ->
                put(JSONObject()
                    .put("at_utc", event.atUtc)
                    .put("subsystem", event.subsystem)
                    .put("state", event.state))
            }
        })
        val individual = JarvisEngine.fridayIndividual
        report.put("individual", individual?.let {
            JSONObject()
                .put("identity_name", it.identity.name)
                .put("experience_count", it.experience.experienceCount())
                .put("association_count", it.experience.associationCount())
        } ?: JSONObject.NULL)
        report.put("last_crash", CrashJournal.read(app) ?: JSONObject.NULL)
        report.put("telemetry_summary", Telemetry.summarize())
        report.put("privacy", JSONObject()
            .put("chat_messages_included", false)
            .put("speech_transcripts_included", false)
            .put("notification_contents_included", false)
            .put("screen_contents_included", false)
            .put("credentials_or_tokens_included", false))
        return report.toString(2)
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