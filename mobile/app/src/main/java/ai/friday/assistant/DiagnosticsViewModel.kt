package ai.friday.assistant

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import ai.friday.assistant.diagnostics.DiagnosticEventLog
import ai.friday.assistant.diagnostics.CrashJournal
import ai.friday.assistant.diagnostics.EngineFailureHealthGate
import ai.friday.assistant.FridayEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class DiagnosticLevel { PASS, WARNING, FAILURE }

data class SelfDiagnosticFinding(
    val id: String,
    val title: String,
    val level: DiagnosticLevel,
    val detail: String,
    val recommendation: String? = null
)

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

    private val _diagnosticFindings = MutableStateFlow<List<ai.friday.assistant.diagnostics.DiagnosticFinding>>(emptyList())
    val diagnosticFindings: StateFlow<List<ai.friday.assistant.diagnostics.DiagnosticFinding>> = _diagnosticFindings.asStateFlow()
    private val _diagnosticRunning = MutableStateFlow(false)
    val diagnosticRunning: StateFlow<Boolean> = _diagnosticRunning.asStateFlow()
    private val _diagnosticAt = MutableStateFlow<Long?>(null)
    val diagnosticAt: StateFlow<Long?> = _diagnosticAt.asStateFlow()
    private val _diagnosticRunCount = MutableStateFlow(0)
    val diagnosticRunCount: StateFlow<Int> = _diagnosticRunCount.asStateFlow()
    private val diagnosticRunner = ai.friday.assistant.diagnostics.DiagnosticRunner(application)
    private val _selfDiagnosticFindings = MutableStateFlow<List<SelfDiagnosticFinding>>(emptyList())
    val selfDiagnosticFindings: StateFlow<List<SelfDiagnosticFinding>> = _selfDiagnosticFindings.asStateFlow()
    private val _selfDiagnosticAt = MutableStateFlow<Long?>(null)
    val selfDiagnosticAt: StateFlow<Long?> = _selfDiagnosticAt.asStateFlow()
    private val _selfDiagnosticRunning = MutableStateFlow(false)
    val selfDiagnosticRunning: StateFlow<Boolean> = _selfDiagnosticRunning.asStateFlow()
    private val _selfDiagnosticRunCount = MutableStateFlow(0)
    val selfDiagnosticRunCount: StateFlow<Int> = _selfDiagnosticRunCount.asStateFlow()

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
        val manager = FridayEngine.modelManager ?: return "model manager unavailable"
        val provider = manager.activeProvider.value
        val health = manager.providerHealth.value
        return when {
            provider.providerType == ai.friday.assistant.env.ModelProviderType.HEURISTIC ->
                "model-free fallback; no language model loaded"
            health.modelLoaded && health.isHealthy ->
                "loaded: ${health.currentModel ?: provider.config?.modelId ?: provider.providerType.name}"
            else ->
                "provider selected (${provider.providerType.name}); no model confirmed loaded"
        }
    }

    private fun individualDiagnosticText(): String {
        val individual = FridayEngine.fridayIndividual
            ?: return "Friday individual: unavailable"
        return "Friday individual: present; experiences=${individual.experience.experienceCount()}; " +
            "associations=${individual.experience.associationCount()}"
    }

    private fun safeSpeechRecognitionSummary(raw: String): String = when {
        raw.contains("heard", ignoreCase = true) -> "recognition result received; transcript omitted"
        raw.contains("error", ignoreCase = true) || raw.contains("fail", ignoreCase = true) -> "recognition error"
        raw.contains("listen", ignoreCase = true) || raw.contains("record", ignoreCase = true) -> "recognition active"
        raw.contains("idle", ignoreCase = true) -> "idle"
        else -> "status changed; transcript omitted"
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

    private fun publishVoiceSubstrate(state: ai.friday.assistant.voice.VoiceOrganismHost.SubstrateState) {
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
            val host = runCatching { FridayEngine.getVoiceOrganismHost() }.getOrNull()
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
        // The host is created asynchronously by FridayEngine.init, so read it
        // defensively — the row stays "initializing..." until it appears.
        viewModelScope.launch {
            val host = runCatching { FridayEngine.getVoiceOrganismHost() }.getOrNull() ?: return@launch
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
                    withContext(Dispatchers.IO) { ScreenBridge.bind(getApplication()) }
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
            FridayMic.isRecording.collect { recording ->
                lastMicText = if (recording) "Mic: energy monitor active; speech recognition starts on request" else "Mic: off"
                publishHealthText()
            }
        }
        viewModelScope.launch {
            FridayMic.lastLevel.collect { level ->
                if (FridayMic.isRecording.value) {
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
            ai.friday.assistant.voice.HotwordAvailabilityProbe.availability.collect { a ->
                lastHotwordText = when (a) {
                    ai.friday.assistant.voice.HotwordAvailability.SUPPORTED -> "Hotword: supported"
                    ai.friday.assistant.voice.HotwordAvailability.UNSUPPORTED -> "Hotword: unsupported; manual tap-to-listen"
                    ai.friday.assistant.voice.HotwordAvailability.UNKNOWN -> "Hotword: unknown; manual tap-to-listen"
                }
                publishHealthText()
            }
        }
        viewModelScope.launch {
            FridayEngine.modelManager?.status?.collect { s ->
                lastBrainText = s
                publishHealthText()
            }
        }

        // Nervous-system failure surface: live summary of active failures +
        // subsystem health, recomputed whenever either changes.
        viewModelScope.launch {
            delay(1500)
            runFullDiagnostic()
        }
        viewModelScope.launch {
            val surface = try { FridayEngine.getFailureSurface() } catch (e: IllegalStateException) { null }
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
     * Runs active device and subsystem probes off the UI thread. The UI heartbeat
     * is a bounded Handler round-trip; storage is actually written/read/deleted;
     * Android exit history is queried instead of assuming a clean state.
     */
    fun runFullDiagnostic() {
        if (_diagnosticRunning.value) return
        _diagnosticRunning.value = true
        viewModelScope.launch {
            var completed = false
            try {
                val signals = mapOf(
                    "battery" to batteryText.value,
                    "network" to networkText.value,
                    "notifications" to lastNotifText,
                    "shizuku" to shizukuText.value,
                    "microphone" to micText.value,
                    "speech_recognition" to safeSpeechRecognitionSummary(sttText.value),
                    "hotword" to hotwordText.value,
                    "text_to_speech" to ttsText.value,
                    "voice_process" to voiceSubstrateText.value,
                    "model_runtime" to modelRuntimeDiagnosticText(),
                    "individual_memory" to individualMemoryText.value,
                    "failure_surface" to failureText.value
                )
                val findings = withContext(Dispatchers.IO) {
                    diagnosticRunner.run(signals, CrashJournal.read(getApplication()))
                }
                _diagnosticFindings.value = findings
                completed = true
                diagnosticEvents.record("active_diagnostic",
                    "errors=${findings.count { it.severity == ai.friday.assistant.diagnostics.DiagnosticSeverity.ERROR }}; " +
                    "warnings=${findings.count { it.severity == ai.friday.assistant.diagnostics.DiagnosticSeverity.WARNING }}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _diagnosticFindings.value = listOf(
                    ai.friday.assistant.diagnostics.DiagnosticFinding(
                        severity = ai.friday.assistant.diagnostics.DiagnosticSeverity.ERROR,
                        title = "Diagnostic run failed",
                        observed = "The diagnostic runner failed (${failure.javaClass.simpleName}); exception details were omitted.",
                        likelyCause = "A platform probe or diagnostics dependency failed before evidence could be returned.",
                        recommendedAction = "Retry the diagnostic. If it fails again, preserve the crash journal and inspect the first Friday-owned stack frame."
                    )
                )
                diagnosticEvents.record("active_diagnostic", "failed; type=${failure.javaClass.simpleName}")
                completed = true
            } finally {
                if (completed) {
                    _diagnosticAt.value = System.currentTimeMillis()
                    _diagnosticRunCount.value += 1
                }
                _diagnosticRunning.value = false
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
        report.put("host_adaptation_snapshot", runCatching {
            ai.friday.assistant.env.AndroidHostSnapshot.capture(app, BuildConfig.SOURCE_REVISION).toJson()
        }.getOrElse { error ->
            JSONObject()
                .put("capture_status", "failed")
                .put("error_type", error.javaClass.simpleName)
                .put("schema_version", ai.friday.assistant.env.AndroidHostSnapshot.SCHEMA_VERSION)
        })
        // Model bootstrap state is part of acceptance evidence: a selected
        // provider is not proof that weights downloaded or loaded.
        val developmentDir = java.io.File(app.filesDir, "friday-development")
        report.put("model_bootstrap", runCatching {
            val stateFile = java.io.File(developmentDir, "model-bootstrap.json")
            if (stateFile.isFile) JSONObject(stateFile.readText(Charsets.UTF_8))
            else JSONObject().put("state", "NOT_RECORDED")
        }.getOrElse { JSONObject().put("state", "UNREADABLE").put("error_type", it.javaClass.simpleName) })
        report.put("host_bootstrap", runCatching {
            val hostFile = java.io.File(developmentDir, "host-bootstrap.json")
            val host = if (hostFile.isFile) JSONObject(hostFile.readText(Charsets.UTF_8)) else JSONObject()
            val capacity = host.optJSONObject("capacity") ?: JSONObject()
            val config = host.optJSONObject("configuration") ?: JSONObject()
            JSONObject()
                .put("source_revision", host.optString("source_revision", "unknown"))
                .put("native_inference_library_present", capacity.optBoolean("native_inference_library_present"))
                .put("recommended_model_max_bytes", config.optLong("recommended_model_max_bytes", 0L))
                .put("thermal_status_at_boot", capacity.optInt("thermal_status", -1))
        }.getOrElse { JSONObject().put("state", "UNREADABLE").put("error_type", it.javaClass.simpleName) })
        report.put("app", JSONObject()
            .put("application_id", app.packageName)
            .put("version_name", runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName
            }.getOrNull() ?: "unknown")
            .put("version_code", runCatching {
                androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(
                    app.packageManager.getPackageInfo(app.packageName, 0)
                )
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
            .put("speech_recognition", safeSpeechRecognitionSummary(sttText.value))
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
        // Export the actual bounded FailureSurface event evidence, not only
        // the human-readable summary. This preserves the first exception,
        // stack, structured classification and recovery context for remote
        // diagnosis after the app has been closed or reinstalled.
        fun safeFailureText(value: String?): Any = value
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("(?i)Bearer\\s+[^\\s,;]+"), "Bearer [REDACTED]")
            ?.replace(
                Regex("(?i)(authorization|access[_-]?token|refresh[_-]?token|api[_-]?key|password)(\\s*[:=]\\s*)[^\\s,;]+")
            ) { match -> match.groupValues[1] + match.groupValues[2] + "[REDACTED]" }
            ?.take(4_000)
            ?: JSONObject.NULL
        val failureEvents = runCatching {
            FridayEngine.getFailureSurface().recentFailures.value
        }.getOrDefault(emptyList())
        report.put("failure_evidence_complete", false)
        report.put("failure_evidence_limit", 50)
        report.put("failure_evidence", org.json.JSONArray().apply {
            failureEvents.take(50).forEach { event ->
                put(JSONObject()
                    .put("id", safeFailureText(event.id))
                    .put("timestamp_ms", event.timestamp)
                    .put("source", safeFailureText(event.source))
                    .put("subsystem", safeFailureText(event.subsystem))
                    .put("operation", safeFailureText(event.operation))
                    .put("severity", event.severity.name)
                    .put("category", event.category.name)
                    .put("message", safeFailureText(event.message))
                    .put("cause", safeFailureText(event.cause))
                    .put("state", safeFailureText(event.state))
                    .put("dependency", safeFailureText(event.dependency))
                    .put("recoverability", event.recoverability.name)
                    .put("correlation_id", safeFailureText(event.correlationId))
                    .put("attempt", event.attempt)
                    .put("recovery_action", event.recoveryAction.name)
                    .put("user_message", safeFailureText(event.userMessage))
                    .put("failure_cause", event.failureCause?.name ?: JSONObject.NULL)
                    .put("related_step_id", safeFailureText(event.relatedStepId))
                    .put("related_capability", safeFailureText(event.relatedCapability))
                    .put("environment", safeFailureText(event.environment))
                    .put("root_cause_hypothesis", safeFailureText(event.rootCauseHypothesis))
                    .put("resource_state", safeFailureText(event.resourceState))
                    .put("exception_chain", safeFailureText(event.metadata["exception_chain"]))
                    .put("stack_frames", safeFailureText(event.metadata["stack_frames"])))
            }
        })
        val individual = FridayEngine.fridayIndividual
        report.put("individual", individual?.let {
            JSONObject()
                .put("identity_name", it.identity.name)
                .put("experience_count", it.experience.experienceCount())
                .put("association_count", it.experience.associationCount())
        } ?: JSONObject.NULL)
        report.put("last_crash", ai.friday.assistant.diagnostics.sanitizedCrashSummary(CrashJournal.read(app)))
        report.put("active_diagnostics", diagnosticRunner.toJson(_diagnosticFindings.value))
        report.put("android_process_exit_history", diagnosticRunner.exitHistoryJson())
        report.put("telemetry_summary", Telemetry.summarize())
        report.put("self_diagnosis_at_ms", _selfDiagnosticAt.value ?: JSONObject.NULL)
        report.put("self_diagnosis", org.json.JSONArray().apply {
            _selfDiagnosticFindings.value.forEach { finding ->
                put(JSONObject()
                    .put("id", finding.id)
                    .put("title", finding.title)
                    .put("level", finding.level.name)
                    .put("detail", finding.detail)
                    .put("recommendation", finding.recommendation ?: JSONObject.NULL))
            }
        })
        report.put("privacy", JSONObject()
            .put("chat_messages_included", false)
            .put("speech_transcripts_included", false)
            .put("notification_contents_included", false)
            .put("screen_contents_included", false)
            .put("credentials_or_tokens_included", false))
        return report.toString(2)
    }

    fun runSelfDiagnosis() {
        if (_selfDiagnosticRunning.value) return
        _selfDiagnosticRunning.value = true
        viewModelScope.launch(Dispatchers.Default) {
            var completed = false
            try {
            val findings = mutableListOf<SelfDiagnosticFinding>()
            fun add(
                id: String,
                title: String,
                level: DiagnosticLevel,
                detail: String,
                recommendation: String? = null
            ) {
                findings += SelfDiagnosticFinding(id, title, level, detail, recommendation)
            }

            val activeEngineIncident: Boolean? = runCatching {
                val failureSurface = FridayEngine.getFailureSurface()
                EngineFailureHealthGate.hasActiveEngineFailure(
                    failureSurface.currentFailures.value.filter { it.isActive }.map { it.subsystem }
                )
            }.getOrNull()

            when (val readiness = FridayEngine.currentReadiness) {
                is EngineReadinessState.Ready -> when (activeEngineIncident) {
                    true -> add(
                        "engine", "Friday engine", DiagnosticLevel.FAILURE,
                        "The composition root reached Ready, but the authoritative FailureSurface still records an active engine incident.",
                        "Inspect the active engine diagnosis and verify recovery. Construction readiness must not override an unrecovered runtime failure."
                    )
                    false -> add(
                        "engine", "Friday engine", DiagnosticLevel.PASS,
                        "The production composition root reached Ready and the FailureSurface confirms no active engine incident."
                    )
                    null -> add(
                        "engine", "Friday engine", DiagnosticLevel.WARNING,
                        "The composition root reached Ready, but the FailureSurface could not be read; current engine health is unverified.",
                        "Restore FailureSurface access before treating Friday as healthy."
                    )
                }
                is EngineReadinessState.FailedToWake -> add(
                    "engine", "Friday engine", DiagnosticLevel.FAILURE,
                    "The production composition root failed: ${readiness.error.message ?: readiness.error.javaClass.simpleName}",
                    "Open the failure detail and repair the first initialization failure before testing dependent systems."
                )
                EngineReadinessState.Waking -> add(
                    "engine", "Friday engine", DiagnosticLevel.WARNING,
                    "The production composition root is still wiring."
                )
                EngineReadinessState.NotStarted -> add(
                    "engine", "Friday engine", DiagnosticLevel.FAILURE,
                    "FridayEngine.init has not started.",
                    "Open Friday normally and inspect the application startup path."
                )
            }

            val ready = FridayEngine.currentReadiness is EngineReadinessState.Ready
            add(
                "cognition", "Independent cognitive engine",
                if (FridayEngine.cognitiveEngine != null) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                if (FridayEngine.cognitiveEngine != null) "CognitiveEngine is present in the live application composition." else "No CognitiveEngine instance is exposed by the live composition.",
                if (FridayEngine.cognitiveEngine == null) "Inspect TurnPathAssembly and FridayEngine initialization." else null
            )
            add(
                "body", "Communication body",
                if (FridayEngine.bodyCoordinatorSlot().isReady) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                if (FridayEngine.bodyCoordinatorSlot().isReady) "BodyCoordinator is available through the readiness-gated production accessor." else "BodyCoordinator is not ready.",
                if (!FridayEngine.bodyCoordinatorSlot().isReady) "Inspect BodyCoordinator construction and startup failures." else null
            )
            add(
                "presence", "Companion / presence core",
                if (ai.friday.assistant.companioncore.presence.CompanionCoreHolder.instance() != null) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                if (ai.friday.assistant.companioncore.presence.CompanionCoreHolder.instance() != null) "The local Companion Core instance exists independently of model availability." else "CompanionCoreHolder has no active core instance.",
                if (ai.friday.assistant.companioncore.presence.CompanionCoreHolder.instance() == null) "Inspect CompanionCoreHolder initialization and its tick-lane startup." else null
            )
            add(
                "identity", "Persistent identity context",
                if (FridayEngine.identityContext != null) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                if (FridayEngine.identityContext != null) "IdentityContext is wired into the production turn path." else "IdentityContext is missing.",
                if (FridayEngine.identityContext == null) "Inspect the identity construction in TurnPathAssembly." else null
            )
            val individual = FridayEngine.fridayIndividual
            val individualId = individual?.identity?.id
            add(
                "individual-identity", "Persistent individual seed",
                if (!individualId.isNullOrBlank()) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                if (!individualId.isNullOrBlank()) "FridayIndividual has a persistent local identity UUID and ${individual.experience.experienceCount()} recorded experiences." else "The local FridayIndividual identity record is not available.",
                if (individualId.isNullOrBlank()) "Inspect FridayIndividual identity.bin creation and recovery." else null
            )
            add(
                "identity-binding", "Identity continuity binding",
                DiagnosticLevel.WARNING,
                "The local individual UUID exists, but the production SelfModel still reads its name/version/pronouns from a separate SharedPreferences identity source. The same immutable lineage key is not yet proven across SelfModel, continuity/replay and experience storage.",
                "Unify the persistent UUID as the continuity lineage key; keep mutable self-description and personality evolvable."
            )
            add(
                "model-free-presence", "Model-independent individual substrate",
                if (individual != null) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                if (individual != null) "The local individual and experience substrate are present without requiring a language model. Its current no-model expression remains limited and must not be reported as general reasoning." else "The local individual substrate is unavailable.",
                if (individual == null) "Inspect FridayIndividual construction in TurnPathAssembly." else "Expand model-free learning and expression beyond lexical associations and fixed patterns."
            )
            add(
                "memory", "Experience / memory substrate",
                if (FridayEngine.graphStore != null && FridayEngine.memoryConsolidationLoop != null) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                "Memory graph=${FridayEngine.graphStore != null}; consolidation loop=${FridayEngine.memoryConsolidationLoop != null}.",
                if (FridayEngine.graphStore == null || FridayEngine.memoryConsolidationLoop == null) "Inspect persistent graph-store construction and the experience consolidation loop." else null
            )
            add(
                "capabilities", "Capability and tool-building fabric",
                if (FridayEngine.capabilityFabric != null && FridayEngine.systemGraph != null) DiagnosticLevel.PASS else DiagnosticLevel.WARNING,
                "Capability fabric=${FridayEngine.capabilityFabric != null}; live system graph=${FridayEngine.systemGraph != null}. Tool construction is exposed through the capability graph, not a separate root-owned ToolBuilder.",
                if (FridayEngine.capabilityFabric == null || FridayEngine.systemGraph == null) "Inspect capability registration and live graph reachability." else null
            )
            add(
                "device-control", "Phone capability router",
                if (FridayEngine.androidDeviceControlRouter != null) DiagnosticLevel.PASS else DiagnosticLevel.WARNING,
                if (FridayEngine.androidDeviceControlRouter != null) "The risk-gated DeviceControlRouter is present." else "No production device-control router is wired.",
                if (FridayEngine.androidDeviceControlRouter == null) "Inspect the Android capability adapter stack and RiskGate wiring." else null
            )

            val graph = FridayEngine.systemGraph
            if (graph == null) {
                add("organ-graph", "Live system graph", DiagnosticLevel.FAILURE, "The production SystemGraph is missing.", "Inspect FridayOrganGraph.build() during engine initialization.")
            } else {
                val wiring = runCatching {
                    ai.friday.assistant.selfreconfig.WiringDiagnostics(graph).diagnose()
                }.getOrNull()
                if (wiring == null) {
                    add("organ-graph", "Live system graph", DiagnosticLevel.FAILURE, "The graph exists, but reachability diagnostics could not run.", "Inspect the entry point and dependency edges in FridayOrganGraph.")
                } else {
                    val actionable = wiring.unreachableNodes.filter {
                        it.organType != ai.friday.assistant.selfreconfig.SystemGraph.OrganType.PLANNED
                    }
                    val level = when {
                        !wiring.allInvariantNodesPresent -> DiagnosticLevel.FAILURE
                        actionable.isNotEmpty() -> DiagnosticLevel.WARNING
                        else -> DiagnosticLevel.PASS
                    }
                    val detail = "Nodes=${graph.nodeCount()}, edges=${graph.edgeCount()}, reachable=${wiring.reachableCount}, unreachable=${wiring.unreachableCount}, missing invariants=${wiring.missingInvariants.joinToString().ifBlank { "none" }}."
                    add(
                        "organ-graph", "Live system wiring",
                        level, detail,
                        if (level != DiagnosticLevel.PASS) "Review unreachable non-planned organs and missing invariants; do not treat planned nodes as implemented." else null
                    )
                }
            }

            val surface = runCatching { FridayEngine.getFailureSurface() }.getOrNull()
            if (surface == null) {
                add("failure-surface", "Failure and recovery system", DiagnosticLevel.FAILURE, "The live FailureSurface is unavailable.", "Inspect FailureSurface construction before any subsystem can report trustworthy health.")
            } else {
                val active = surface.currentFailures.value.filter { it.isActive }
                val worst = surface.worstSeverity.value
                val level = when {
                    worst.rank >= ai.friday.assistant.failure.FailureSeverity.ERROR.rank -> DiagnosticLevel.FAILURE
                    worst.rank >= ai.friday.assistant.failure.FailureSeverity.DEGRADED.rank || active.isNotEmpty() -> DiagnosticLevel.WARNING
                    else -> DiagnosticLevel.PASS
                }
                val health = surface.systemHealth.value
                add(
                    "failure-surface", "Failure and recovery system",
                    level,
                    "Active conditions=${active.size}; total recorded occurrences=${surface.totalFailureCount}; system status=${health.status}; degraded subsystems=${health.degradedSubsystemCount}; down subsystems=${health.downSubsystemCount}.",
                    if (active.isNotEmpty()) "Inspect the individual evidence-backed diagnoses below and verify recovery rather than clearing the failure record." else null
                )
                val diagnosisEngine = runCatching { FridayEngine.getSelfDiagnosis() }.getOrNull()
                surface.recentFailures.value.take(3).forEach { event ->
                    val diagnosis = diagnosisEngine?.let { runCatching { it.diagnose(event) }.getOrNull() }
                    val detail = buildString {
                        append(diagnosis?.let {
                            "${it.summary}. Changed before: ${it.changedBefore}. Fallback: ${it.fallbackAvailable ?: "none recorded"}; fallback succeeded: ${it.fallbackSucceeded ?: "not verified"}."
                        } ?: "${event.subsystem}.${event.operation}: ${event.message}")
                        event.metadata["exception_chain"]?.takeIf { it.isNotBlank() }?.let { append(" Exception chain: $it.") }
                        event.metadata["stack_frames"]?.takeIf { it.isNotBlank() }?.let { append(" Stack: $it.") }
                        event.dependency?.let { append(" Dependency: $it.") }
                        event.state?.let { append(" State: $it.") }
                        event.resourceState?.let { append(" Resources: $it.") }
                        event.rootCauseHypothesis?.let { append(" Root-cause hypothesis: $it.") }
                        event.recoveryAction.takeIf { it != ai.friday.assistant.failure.RecoveryAction.NONE }
                            ?.let { append(" Recovery action: ${it.name}.") }
                    }
                    val failureLevel = when {
                        diagnosis?.subsystemHealthyAfter == true -> DiagnosticLevel.WARNING
                        event.severity.rank >= ai.friday.assistant.failure.FailureSeverity.ERROR.rank -> DiagnosticLevel.FAILURE
                        else -> DiagnosticLevel.WARNING
                    }
                    add(
                        "failure-${event.id}", "Diagnosis: ${event.subsystem} / ${event.operation}",
                        failureLevel, detail,
                        if (diagnosis?.subsystemHealthyAfter == true) "The subsystem is healthy now; monitor for recurrence." else "Inspect the named dependency and recovery action; do not assume the fallback worked."
                    )
                }
            }

            val voiceHost = FridayEngine.voiceOrganismHostSlot().value
            val voiceState = voiceHost?.state?.value
            val voiceLevel = when (voiceState?.state) {
                ai.friday.assistant.voice.VoiceOrganismHost.State.BOUND -> DiagnosticLevel.PASS
                ai.friday.assistant.voice.VoiceOrganismHost.State.DISABLED -> DiagnosticLevel.FAILURE
                else -> DiagnosticLevel.WARNING
            }
            add(
                "voice-habitat", "Isolated voice habitat", voiceLevel,
                voiceState?.let { "State=${it.state}; voice PID=${it.voicePid ?: "not bound"}; restarts=${it.restartCount}; last status=${it.lastStatus.ifBlank { "none" }}." }
                    ?: "VoiceOrganismHost is not available yet.",
                if (voiceLevel != DiagnosticLevel.PASS) "Inspect the :voice process binding, provider readiness and recorded voice failure." else null
            )

            val modelResolution = FridayEngine.ollamaModelBackend?.lastResolution
            val modelDetail = when {
                modelResolution == null -> "No local model resolution has been attempted. This is an optional compute resource, not a Friday identity or core-presence dependency."
                modelResolution.unreachable -> "Local model server is unreachable: ${modelResolution.basis}. Friday's local identity/presence checks remain independent."
                modelResolution.emptyRegistry -> "The model server answered but registered no model: ${modelResolution.basis}."
                modelResolution.degraded -> "Model request was resolved by fallback: ${modelResolution.basis}; selected=${modelResolution.resolvedId}."
                else -> "Local model resolved: ${modelResolution.resolvedId}."
            }
            add(
                "optional-model", "Optional model resource",
                if (modelResolution == null) DiagnosticLevel.PASS
                else if (modelResolution.unreachable || modelResolution.emptyRegistry || modelResolution.degraded) DiagnosticLevel.WARNING
                else DiagnosticLevel.PASS,
                modelDetail,
                if (modelResolution?.unreachable == true || modelResolution?.emptyRegistry == true) "If expanded model reasoning is wanted, configure a reachable resource. Do not treat this as Friday being offline." else null
            )

            val context = getApplication<Application>()
            val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            add(
                "microphone", "Microphone permission",
                if (micGranted) DiagnosticLevel.PASS else DiagnosticLevel.WARNING,
                if (micGranted) "RECORD_AUDIO is granted; capture=${FridayMic.isRecording.value}." else "RECORD_AUDIO is not granted. Friday's non-microphone core can still run.",
                if (!micGranted) "Grant microphone permission only if voice input is wanted." else null
            )
            val shizukuGranted = ShizukuBridge.isGranted.value
            val shizukuBound = ScreenBridge.isVerifiedBound
            add(
                "shizuku", "Shizuku device bridge",
                if (shizukuGranted && shizukuBound) DiagnosticLevel.PASS else DiagnosticLevel.WARNING,
                "Permission granted=$shizukuGranted; privileged UserService bound=$shizukuBound; status=${ScreenBridge.status.value}.",
                if (!shizukuGranted) "Start Shizuku and grant Friday permission if privileged device capabilities are wanted."
                else if (!shizukuBound) "Open Device capabilities in Settings to bind Friday's Shizuku UserService."
                else null
            )
            val runtimeEnabled = ai.friday.assistant.runtime.FridayRuntimeController.isEnabled(context)
            val runtimeRunning = ai.friday.assistant.runtime.FridayRuntimeController.isServiceRunning(context)
            add(
                "persistent-runtime", "Persistent background runtime",
                if (runtimeEnabled && runtimeRunning) DiagnosticLevel.PASS else DiagnosticLevel.WARNING,
                "Owner enabled=$runtimeEnabled; Android service running=$runtimeRunning.",
                if (!runtimeEnabled) "Enable Keep Friday alive in Settings if background presence is wanted."
                else if (!runtimeRunning) "Open Friday to resume the foreground service and inspect any recorded startup failure."
                else null
            )
            val batteryExempt = runCatching {
                ai.friday.assistant.runtime.FridayRuntimeController.batteryOptimizationIgnored(context)
            }.getOrDefault(false)
            add(
                "doze", "Android Doze exemption",
                if (batteryExempt) DiagnosticLevel.PASS else DiagnosticLevel.WARNING,
                if (batteryExempt) "Android reports Friday as exempt from battery optimization." else "Friday is not reported as exempt from Doze battery optimization.",
                if (!batteryExempt) "Use Android battery settings or the Shizuku Protect Friday action. This cannot prevent force-stop or all OEM task killers." else null
            )
            _selfDiagnosticFindings.value = findings
            _selfDiagnosticAt.value = System.currentTimeMillis()
            completed = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _selfDiagnosticFindings.value = listOf(
                    SelfDiagnosticFinding(
                        id = "self-diagnosis-failed",
                        title = "Self-diagnosis could not complete",
                        level = DiagnosticLevel.FAILURE,
                        detail = "A diagnostic probe failed (${failure.javaClass.simpleName}); exception details were omitted.",
                        recommendation = "Retry self-diagnosis. If it fails again, inspect the corresponding diagnostic and crash-journal evidence."
                    )
                )
                _selfDiagnosticAt.value = System.currentTimeMillis()
                completed = true
            } finally {
                if (completed) {
                    _selfDiagnosticAt.value = System.currentTimeMillis()
                    _selfDiagnosticRunCount.value += 1
                }
                _selfDiagnosticRunning.value = false
            }
        }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(batteryReceiver) }
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }


    /** Deterministic human-readable summary of the nervous-system failure state. */
    private fun summarizeFailures(
        worst: ai.friday.assistant.failure.FailureSeverity,
        failures: List<ai.friday.assistant.failure.FailureAggregate>,
        health: Map<String, ai.friday.assistant.failure.SubsystemHealth>
    ): String {
        if (worst == ai.friday.assistant.failure.FailureSeverity.INFO) {
            val down = health.values.count { it.status == ai.friday.assistant.failure.SubsystemStatus.DOWN }
            val degraded = health.values.count { it.status == ai.friday.assistant.failure.SubsystemStatus.DEGRADED }
            return "Failures: none ($down down, $degraded degraded)"
        }
        val active = failures
            .filter { it.isActive }
            .take(3)
            .joinToString("; ") { "${it.subsystem}: ${it.currentSeverity.name} x${it.occurrenceCount}" }
        return "Failures: $worst — $active"
    }
}