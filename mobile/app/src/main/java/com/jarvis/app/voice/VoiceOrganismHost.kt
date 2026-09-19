package com.jarvis.app.voice

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import java.io.File
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.Recoverability
import com.jarvis.app.failure.RecoveryAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "VoiceOrganismHost"

/**
 * R1 substrate — the app-process supervisor of the `:voice` capability habitat.
 *
 * The microkernel/Chromium pattern applied to voice: the crash-prone native
 * work lives in a separate process; this host watches that process and
 * rebuilds it from its [VoiceEnvironment] recipe on death. The app process is
 * never at risk — process death is opaque to Binder, so a native SIGSEGV in the
 * voice process walks the exact same death path as the deliberate
 * [IVoiceService.crashNow] demo.
 *
 * State: OFFLINE → BINDING → BOUND ⇄ REBINDING → DISABLED (after the recipe's
 * maxConsecutiveRestarts, no infinite loop).
 */
class VoiceOrganismHost(
    private val context: Context,
    private val environment: VoiceEnvironment = VoiceEnvironment(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    internal val onFailure: (FailureReport) -> Unit = {}
) {

    enum class State { OFFLINE, BINDING, BOUND, REBINDING, DISABLED }

    /** Capability-level voice state surfaced to the nervous system. */
    enum class VoiceState { CREATED, STARTING, READY, BUSY, DEGRADED, FAILED, RESTARTING, STOPPED }

    data class SubstrateState(
        val state: State = State.OFFLINE,
        val appPid: Int = Process.myPid(),
        val voicePid: Int? = null,
        val pingLatencyMs: Long? = null,
        val restartCount: Int = 0,
        val lastRestartMs: Long? = null,
        val lastStatus: String = ""
    )

    private val _state = MutableStateFlow(SubstrateState())
    val state: StateFlow<SubstrateState> = _state.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceState.CREATED)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    @Volatile private var service: IVoiceService? = null
    @Volatile private var binder: IBinder? = null
    @Volatile private var bound = false
    private var consecutiveDeaths = 0
    @Volatile private var restartStartRealtime: Long? = null
    private val restarting = java.util.concurrent.atomic.AtomicBoolean(false)

    private val deathRecipient = IBinder.DeathRecipient { restartFromDeath("binder died") }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IVoiceService.Stub.asInterface(binder)
            if (svc == null) {
                failBind("asInterface returned null for $name")
                return
            }
            this@VoiceOrganismHost.binder = binder
            this@VoiceOrganismHost.service = svc
            try {
                binder?.linkToDeath(deathRecipient, 0)
            } catch (e: RemoteException) {
                restartFromDeath("linkToDeath failed: ${e.message}")
                return
            }
            val status = runCatching { svc.getStatus() }.getOrDefault("")
            val voicePid = Regex("pid=(\\d+)").find(status)?.groupValues?.get(1)?.toIntOrNull()
            val rebuiltMs = restartStartRealtime?.let { SystemClock.elapsedRealtime() - it }
            restartStartRealtime = null
            restarting.set(false)
            consecutiveDeaths = 0
            _state.update {
                it.copy(
                    state = State.BOUND,
                    voicePid = voicePid,
                    appPid = Process.myPid(),
                    lastStatus = status,
                    lastRestartMs = rebuiltMs ?: it.lastRestartMs
                )
            }
            _voiceState.value = VoiceState.READY
            measurePing()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            restartFromDeath("service disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            restartFromDeath("binding died")
        }
    }

    /** Bind the voice habitat (idempotent). */
    fun start() {
        if (bound) return
        _voiceState.value = VoiceState.STARTING
        _state.update { it.copy(state = State.BINDING) }
        bound = context.bindService(environment.bindIntent(context), connection, environment.bindFlags)
        if (!bound) failBind("bindService returned false")
    }

    /** Report current round-trip latency for the health surface. */
    fun pingAsync() = measurePing()

    /**
     * R2 — synthesize [text] through the habitat. Returns the produced audio
     * (WAV at [audioFile]) or null when the habitat is unreachable / failed.
     * [params] are serialized across the contract; [voiceId] is provider-local.
     */
    suspend fun synthesizeText(
        text: String,
        language: String = "en",
        voiceId: String? = null,
        params: com.jarvis.app.voice.provider.VoiceSynthesisParams = com.jarvis.app.voice.provider.VoiceSynthesisParams(),
        outPath: String? = null
    ): VoiceAudio? = withContext(Dispatchers.IO) {
        val svc = service ?: run { _voiceState.value = VoiceState.DEGRADED; return@withContext null }
        val path = outPath ?: File(context.cacheDir, "voice_${System.currentTimeMillis()}.wav").absolutePath
        _voiceState.value = VoiceState.BUSY
        val resultJson = try {
            svc.synthesize(text, language, voiceId ?: "", encodeOptions(params), path)
        } catch (e: RemoteException) {
            _voiceState.value = VoiceState.FAILED
            null
        }
        _voiceState.value = VoiceState.READY
        decodeAudioResult(resultJson, path)
    }

    /** Ask the habitat to stop the in-flight synthesis. */
    fun cancelVoice() {
        runCatching { service?.cancel() }
    }

    /** Capability snapshot as JSON from the habitat (or null when unreachable). */
    fun capabilitiesJson(): String? = runCatching { service?.getCapabilities() }.getOrNull()

    /** Synthesized audio delivered across the contract. */
    data class VoiceAudio(
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Long,
        val audioFile: java.io.File
    )

    private fun encodeOptions(p: com.jarvis.app.voice.provider.VoiceSynthesisParams): String = runCatching {
        org.json.JSONObject()
            .put("speechRate", p.speechRate.toDouble())
            .put("pitch", p.pitch.toDouble())
            .put("utteranceId", p.utteranceId)
            .toString()
    }.getOrDefault("{}")

    private fun decodeAudioResult(json: String?, path: String): VoiceAudio? {
        if (json == null) return null
        val o = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return null
        if (!o.optBoolean("success", false)) return null
        val file = File(path)
        if (!file.exists() || file.length() <= 44) return null
        return VoiceAudio(
            sampleRate = o.optInt("sampleRate", 0),
            channels = o.optInt("channels", 1),
            durationMs = o.optLong("durationMs", 0),
            audioFile = file
        )
    }

    /**
     * The on-device isolation proof: crash the voice process, wait for the
     * supervised rebuild, return the measured rebuild time (ms) or -1 on timeout.
     */
    suspend fun runCrashRestartCycle(timeoutMs: Long = 5000): Long {
        val current = _state.value
        val currentPid = current.voicePid
        if (current.state != State.BOUND || currentPid == null) return -1L
        val svc = service ?: return -1L
        val t0 = SystemClock.elapsedRealtime()
        svc.crashNow()
        val rebuilt = withTimeoutOrNull(timeoutMs) {
            state.first { it.state == State.BOUND && it.voicePid != null && it.voicePid != currentPid }
        }
        return if (rebuilt != null) SystemClock.elapsedRealtime() - t0 else -1L
    }

    /** Tear down the habitat (app shutdown / capability suspend). */
    fun shutdown() {
        _voiceState.value = VoiceState.STOPPED
        cleanupBinding()
        scope.cancel()
    }

    // ───────────────────────────────────────────────────────────── internal

    private fun measurePing() {
        val svc = service ?: return
        val t0 = SystemClock.elapsedRealtime()
        scope.launch {
            val latency = withContext(Dispatchers.IO) {
                val ok = try {
                    svc.ping()
                    true
                } catch (e: RemoteException) {
                    false
                }
                if (ok) SystemClock.elapsedRealtime() - t0 else null
            }
            if (latency != null) {
                _state.update { it.copy(pingLatencyMs = latency) }
                if (latency > environment.pingTimeoutMs) reportDegraded(latency)
            } else {
                restartFromDeath("ping failed (voice process unreachable)")
            }
        }
    }

    private fun restartFromDeath(reason: String) {
        // One process death can surface as binderDied + onBindingDied +
        // onServiceDisconnected (plus a stale ping race). Atomic guard:
        // only the first caller runs the restart path.
        if (!restarting.compareAndSet(false, true)) return
        val deadPid = _state.value.voicePid
        consecutiveDeaths++
        restartStartRealtime = SystemClock.elapsedRealtime()
        val report = FailureReport(
            subsystem = "VOICE_SUBSTRATE",
            operation = "bind_voice_process",
            severity = if (consecutiveDeaths >= environment.maxConsecutiveRestarts)
                FailureSeverity.ERROR else FailureSeverity.RECOVERABLE,
            category = FailureCategory.AUDIO,
            message = "voice process died ($reason, pid=$deadPid), restarting",
            recoverability = Recoverability.RECOVERABLE,
            recoveryAction = RecoveryAction.RETRY_WITH_BACKOFF,
            attempt = consecutiveDeaths,
            metadata = mapOf("pid" to "$deadPid", "appPid" to "${Process.myPid()}")
        )
        report(report)
        _voiceState.value = VoiceState.RESTARTING
        _state.update { it.copy(state = State.REBINDING, restartCount = it.restartCount + 1) }

        if (consecutiveDeaths > environment.maxConsecutiveRestarts) {
            Log.e(TAG, "Voice substrate DISABLED after ${consecutiveDeaths} consecutive deaths")
            _voiceState.value = VoiceState.FAILED
            _state.update { it.copy(state = State.DISABLED) }
            return
        }
        scope.launch {
            delay(environment.restartBackoffMs)
            if (_state.value.state == State.DISABLED) return@launch
            // Unbind the dead connection here (not inside a binder callback —
            // unbinding from within onServiceDisconnected is an Android gotcha).
            // Releasing the registration also cancels the system's own
            // auto-reconnect so bindAgain() starts from a clean slate.
            cleanupBinding()
            bindAgain()
        }
    }

    private fun bindAgain() {
        _state.update { it.copy(state = State.BINDING) }
        bound = context.bindService(environment.bindIntent(context), connection, environment.bindFlags)
        if (!bound) failBind("rebind returned false")
    }

    private fun failBind(reason: String) {
        report(
            FailureReport(
                subsystem = "VOICE_SUBSTRATE",
                operation = "bind_voice_process",
                severity = FailureSeverity.DEGRADED,
                category = FailureCategory.AUDIO,
                message = "voice substrate bind failed: $reason",
                recoverability = Recoverability.RETRYABLE,
                recoveryAction = RecoveryAction.RETRY_WITH_BACKOFF
            )
        )
        _voiceState.value = VoiceState.DEGRADED
        _state.update { it.copy(state = State.OFFLINE) }
    }

    private fun reportDegraded(latencyMs: Long) {
        report(
            FailureReport(
                subsystem = "VOICE_SUBSTRATE",
                operation = "ping_voice_process",
                severity = FailureSeverity.WARNING,
                category = FailureCategory.AUDIO,
                message = "voice substrate ping over budget: ${latencyMs}ms",
                recoverability = Recoverability.RETRYABLE,
                recoveryAction = RecoveryAction.NONE
            )
        )
    }

    private fun report(r: FailureReport) {
        Log.w(TAG, r.message)
        onFailure(r)
    }

    private fun cleanupBinding() {
        try {
            binder?.unlinkToDeath(deathRecipient, 0)
        } catch (_: Exception) {
        }
        try {
            if (bound) context.unbindService(connection)
        } catch (_: Exception) {
        }
        service = null
        binder = null
        bound = false
    }
}
