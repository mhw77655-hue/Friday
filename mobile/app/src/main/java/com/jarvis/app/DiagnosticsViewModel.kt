package com.jarvis.app

import android.Manifest
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
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.JarvisEngine
import kotlinx.coroutines.delay
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

    private val _humanCoreText = MutableStateFlow("Human Core: initializing...")
    val humanCoreText: StateFlow<String> = _humanCoreText.asStateFlow()

    private val _failureText = MutableStateFlow("Failures: none")
    val failureText: StateFlow<String> = _failureText.asStateFlow()

    private val _selfDiagnosticFindings = MutableStateFlow<List<SelfDiagnosticFinding>>(emptyList())
    val selfDiagnosticFindings: StateFlow<List<SelfDiagnosticFinding>> = _selfDiagnosticFindings.asStateFlow()

    private val _selfDiagnosticAt = MutableStateFlow<Long?>(null)
    val selfDiagnosticAt: StateFlow<Long?> = _selfDiagnosticAt.asStateFlow()

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

    /**
     * Inspect the real running Friday composition without asking a language
     * model. This checks actual object wiring, the production organ graph,
     * current failure evidence, platform permissions and optional resources.
     */
    fun runSelfDiagnosis() {
        viewModelScope.launch(Dispatchers.Default) {
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

            when (val readiness = JarvisEngine.currentReadiness) {
                is EngineReadinessState.Ready -> add(
                    "engine", "Friday engine", DiagnosticLevel.PASS,
                    "The production composition root reached Ready."
                )
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
                    "JarvisEngine.init has not started.",
                    "Open Friday normally and inspect the application startup path."
                )
            }

            val ready = JarvisEngine.currentReadiness is EngineReadinessState.Ready
            add(
                "cognition", "Independent cognitive engine",
                if (JarvisEngine.cognitiveEngine != null) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                if (JarvisEngine.cognitiveEngine != null) "CognitiveEngine is present in the live application composition." else "No CognitiveEngine instance is exposed by the live composition.",
                if (JarvisEngine.cognitiveEngine == null) "Inspect TurnPathAssembly and JarvisEngine initialization." else null
            )
            add(
                "body", "Communication body",
                if (JarvisEngine.bodyCoordinatorSlot().isReady) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                if (JarvisEngine.bodyCoordinatorSlot().isReady) "BodyCoordinator is available through the readiness-gated production accessor." else "BodyCoordinator is not ready.",
                if (!JarvisEngine.bodyCoordinatorSlot().isReady) "Inspect BodyCoordinator construction and startup failures." else null
            )
            add(
                "presence", "Companion / presence core",
                if (com.jarvis.app.companioncore.presence.CompanionCoreHolder.instance() != null) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                if (com.jarvis.app.companioncore.presence.CompanionCoreHolder.instance() != null) "The local Companion Core instance exists independently of model availability." else "CompanionCoreHolder has no active core instance.",
                if (com.jarvis.app.companioncore.presence.CompanionCoreHolder.instance() == null) "Inspect CompanionCoreHolder initialization and its tick-lane startup." else null
            )
            add(
                "identity", "Persistent identity context",
                if (JarvisEngine.identityContext != null) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                if (JarvisEngine.identityContext != null) "IdentityContext is wired into the production turn path." else "IdentityContext is missing.",
                if (JarvisEngine.identityContext == null) "Inspect the identity construction in TurnPathAssembly." else null
            )
            add(
                "memory", "Experience / memory substrate",
                if (JarvisEngine.graphStore != null && JarvisEngine.memoryConsolidationLoop != null) DiagnosticLevel.PASS else if (ready) DiagnosticLevel.FAILURE else DiagnosticLevel.WARNING,
                "Memory graph=${JarvisEngine.graphStore != null}; consolidation loop=${JarvisEngine.memoryConsolidationLoop != null}.",
                if (JarvisEngine.graphStore == null || JarvisEngine.memoryConsolidationLoop == null) "Inspect persistent graph-store construction and the experience consolidation loop." else null
            )
            add(
                "capabilities", "Capability and tool-building fabric",
                if (JarvisEngine.capabilityFabric != null && JarvisEngine.toolBuilder != null) DiagnosticLevel.PASS else DiagnosticLevel.WARNING,
                "Capability fabric=${JarvisEngine.capabilityFabric != null}; ToolBuilder=${JarvisEngine.toolBuilder != null}.",
                if (JarvisEngine.capabilityFabric == null || JarvisEngine.toolBuilder == null) "Inspect capability registration and ToolBuilderComposition." else null
            )
            add(
                "device-control", "Phone capability router",
                if (JarvisEngine.androidDeviceControlRouter != null) DiagnosticLevel.PASS else DiagnosticLevel.WARNING,
                if (JarvisEngine.androidDeviceControlRouter != null) "The risk-gated DeviceControlRouter is present." else "No production device-control router is wired.",
                if (JarvisEngine.androidDeviceControlRouter == null) "Inspect the Android capability adapter stack and RiskGate wiring." else null
            )

            val graph = JarvisEngine.systemGraph
            if (graph == null) {
                add("organ-graph", "Live system graph", DiagnosticLevel.FAILURE, "The production SystemGraph is missing.", "Inspect JarvisOrganGraph.build() during engine initialization.")
            } else {
                val wiring = runCatching {
                    com.jarvis.app.selfreconfig.WiringDiagnostics(graph).diagnose()
                }.getOrNull()
                if (wiring == null) {
                    add("organ-graph", "Live system graph", DiagnosticLevel.FAILURE, "The graph exists, but reachability diagnostics could not run.", "Inspect the entry point and dependency edges in JarvisOrganGraph.")
                } else {
                    val actionable = wiring.unreachableNodes.filter {
                        it.organType != com.jarvis.app.selfreconfig.SystemGraph.OrganType.PLANNED
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

            val surface = runCatching { JarvisEngine.getFailureSurface() }.getOrNull()
            if (surface == null) {
                add("failure-surface", "Failure and recovery system", DiagnosticLevel.FAILURE, "The live FailureSurface is unavailable.", "Inspect FailureSurface construction before any subsystem can report trustworthy health.")
            } else {
                val active = surface.currentFailures.value.filter { it.isActive }
                val worst = surface.worstSeverity.value
                val level = when {
                    worst.rank >= com.jarvis.app.failure.FailureSeverity.ERROR.rank -> DiagnosticLevel.FAILURE
                    worst.rank >= com.jarvis.app.failure.FailureSeverity.DEGRADED.rank || active.isNotEmpty() -> DiagnosticLevel.WARNING
                    else -> DiagnosticLevel.PASS
                }
                val health = surface.systemHealth.value
                add(
                    "failure-surface", "Failure and recovery system",
                    level,
                    "Active conditions=${active.size}; total recorded occurrences=${surface.totalFailureCount}; system status=${health.status}; degraded subsystems=${health.degradedSubsystemCount}; down subsystems=${health.downSubsystemCount}.",
                    if (active.isNotEmpty()) "Inspect the individual evidence-backed diagnoses below and verify recovery rather than clearing the failure record." else null
                )
                val diagnosisEngine = runCatching { JarvisEngine.getSelfDiagnosis() }.getOrNull()
                surface.recentFailures.value.take(3).forEach { event ->
                    val diagnosis = diagnosisEngine?.let { runCatching { it.diagnose(event) }.getOrNull() }
                    val detail = diagnosis?.let {
                        "${it.summary}. Changed before: ${it.changedBefore}. Fallback: ${it.fallbackAvailable ?: "none recorded"}; fallback succeeded: ${it.fallbackSucceeded ?: "not verified"}."
                    } ?: "${event.subsystem}.${event.operation}: ${event.message}"
                    val failureLevel = when {
                        diagnosis?.subsystemHealthyAfter == true -> DiagnosticLevel.WARNING
                        event.severity.rank >= com.jarvis.app.failure.FailureSeverity.ERROR.rank -> DiagnosticLevel.FAILURE
                        else -> DiagnosticLevel.WARNING
                    }
                    add(
                        "failure-${event.id}", "Diagnosis: ${event.subsystem} / ${event.operation}",
                        failureLevel, detail,
                        if (diagnosis?.subsystemHealthyAfter == true) "The subsystem is healthy now; monitor for recurrence." else "Inspect the named dependency and recovery action; do not assume the fallback worked."
                    )
                }
            }

            val voiceHost = JarvisEngine.voiceOrganismHostSlot().value
            val voiceState = voiceHost?.state?.value
            val voiceLevel = when (voiceState?.state) {
                com.jarvis.app.voice.VoiceOrganismHost.State.BOUND -> DiagnosticLevel.PASS
                com.jarvis.app.voice.VoiceOrganismHost.State.DISABLED -> DiagnosticLevel.FAILURE
                else -> DiagnosticLevel.WARNING
            }
            add(
                "voice-habitat", "Isolated voice habitat", voiceLevel,
                voiceState?.let { "State=${it.state}; voice PID=${it.voicePid ?: "not bound"}; restarts=${it.restartCount}; last status=${it.lastStatus.ifBlank { "none" }}." }
                    ?: "VoiceOrganismHost is not available yet.",
                if (voiceLevel != DiagnosticLevel.PASS) "Inspect the :voice process binding, provider readiness and recorded voice failure." else null
            )

            val modelResolution = JarvisEngine.ollamaModelBackend?.lastResolution
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
                if (micGranted) "RECORD_AUDIO is granted; capture=${JarvisMic.isRecording.value}." else "RECORD_AUDIO is not granted. Friday's non-microphone core can still run.",
                if (!micGranted) "Grant microphone permission only if voice input is wanted." else null
            )
            val shizukuGranted = ShizukuBridge.isGranted.value
            val shizukuBound = ScreenBridge.status.value == "bound"
            add(
                "shizuku", "Shizuku device bridge",
                if (shizukuGranted && shizukuBound) DiagnosticLevel.PASS else DiagnosticLevel.WARNING,
                "Permission granted=$shizukuGranted; privileged UserService bound=$shizukuBound; status=${ScreenBridge.status.value}.",
                if (!shizukuGranted) "Start Shizuku and grant Friday permission if privileged device capabilities are wanted."
                else if (!shizukuBound) "Use Connect phone to GitHub / the device capability control to bind Friday's Shizuku UserService."
                else null
            )
            val runtimeEnabled = com.jarvis.app.runtime.FridayRuntimeController.isEnabled(context)
            val runtimeRunning = com.jarvis.app.runtime.FridayRuntimeController.isServiceRunning(context)
            add(
                "persistent-runtime", "Persistent background runtime",
                if (runtimeEnabled && runtimeRunning) DiagnosticLevel.PASS else DiagnosticLevel.WARNING,
                "Owner enabled=$runtimeEnabled; Android service running=$runtimeRunning.",
                if (!runtimeEnabled) "Enable Keep Friday alive in Settings if background presence is wanted."
                else if (!runtimeRunning) "Open Friday to resume the foreground service and inspect any recorded startup failure."
                else null
            )
            val batteryExempt = runCatching {
                com.jarvis.app.runtime.FridayRuntimeController.batteryOptimizationIgnored(context)
            }.getOrDefault(false)
            add(
                "doze", "Android Doze exemption",
                if (batteryExempt) DiagnosticLevel.PASS else DiagnosticLevel.WARNING,
                if (batteryExempt) "Android reports Friday as exempt from battery optimization." else "Friday is not reported as exempt from Doze battery optimization.",
                if (!batteryExempt) "Use Android battery settings or the Shizuku Protect Friday action. This cannot prevent force-stop or all OEM task killers." else null
            )
            add(
                "autonomous-evolution", "Autonomous development",
                DiagnosticLevel.PASS,
                if (com.jarvis.app.github.FridayEvolutionScheduler.isEnabled(context)) "Owner-enabled GitHub development cycles are scheduled." else "Scheduled development is intentionally off; local Friday continues independently."
            )

            _selfDiagnosticFindings.value = findings
            _selfDiagnosticAt.value = System.currentTimeMillis()
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