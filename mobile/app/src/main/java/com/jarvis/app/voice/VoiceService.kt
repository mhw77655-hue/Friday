package com.jarvis.app.voice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.jarvis.app.voice.provider.SherpaTtsVoiceProvider
import com.jarvis.app.voice.provider.SystemTtsVoiceProvider
import com.jarvis.app.voice.provider.VoiceProvider
import com.jarvis.app.voice.provider.VoiceProviderRegistry
import com.jarvis.app.voice.provider.VoiceProviderSelector
import com.jarvis.app.voice.provider.VoiceSynthesisParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "VoiceService"

/**
 * The `:voice` process — the mutated environment that owns voice inference.
 *
 * R1 was a supervised placeholder (tone bytes proving the boundary). R2 hosts
 * the real providers:
 *
 *   - [VoiceProviderRegistry] (Sherpa/Kokoro + Android system TTS),
 *   - [VoiceProviderSelector] (profile → provider, deterministic),
 *   - [VoiceModelManager] (lazy download / admission / lifecycle),
 *   - [VoiceFailureController] (classified failures cross IPC to the host).
 *
 * Every native inference call is routed through [VoiceScheduler] so at most one
 * runs at a time (the same serialization discipline that prevented the old
 * in-process native engines from starving each other). A crash inside a
 * provider kills ONLY this process; the host detects Binder death and rebuilds.
 */
class VoiceService : Service() {

    private val scheduler = VoiceScheduler("voice-inference")
    private val startRealtime = SystemClock.elapsedRealtime()
    private val voicePid = Process.myPid()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var registry: VoiceProviderRegistry
    private lateinit var selector: VoiceProviderSelector
    private lateinit var modelManager: VoiceModelManager
    private lateinit var failureController: VoiceFailureController
    private lateinit var providers: List<VoiceProvider>

    override fun onCreate() {
        super.onCreate()
        failureController = VoiceFailureController { report -> Log.w(TAG, report.message) }
        modelManager = VoiceModelManager(
            context = this,
            governor = null, // the habitat admits itself; the host reports pressure
            scope = scope,
            onFailure = { report -> Log.w(TAG, report.message) }
        )
        registry = VoiceProviderRegistry().apply {
            register(SherpaTtsVoiceProvider(modelManager), priority = VoiceProviderRegistry.HIGH_PRIORITY)
            register(SystemTtsVoiceProvider(this@VoiceService), priority = VoiceProviderRegistry.DEFAULT_PRIORITY)
        }
        selector = VoiceProviderSelector(registry)
        providers = registry.all()
        providers.forEach { p ->
            scope.launch {
                try {
                    p.initialize(this@VoiceService)
                } catch (e: Throwable) {
                    Log.w(TAG, "provider ${p.id} init failed: ${e.message}")
                }
            }
        }
        Log.i(TAG, ":voice habitat ready (pid=$voicePid), providers=${providers.map { it.id }}")
    }

    override fun onBind(intent: Intent): IBinder = VoiceStub(scheduler, startRealtime, voicePid, this)

    override fun onDestroy() {
        providers.forEach { runCatching { it.release() } }
        scheduler.close()
        super.onDestroy()
    }

    private class VoiceStub(
        private val scheduler: VoiceScheduler,
        private val startRealtime: Long,
        private val voicePid: Int,
        private val service: VoiceService
    ) : IVoiceService.Stub() {

        override fun ping(): Long = SystemClock.elapsedRealtime()

        override fun synthesize(text: String, language: String, voiceId: String, optionsJson: String, outPath: String): String {
            if (text.isBlank()) {
                return json(false, error = "empty text")
            }
            val params = parseOptions(optionsJson)
            val profile = com.jarvis.app.voice.DefaultJarvisVoiceProfile.copy(
                language = language.ifBlank { "en" },
                preferredProvider = null,
                preferredVoice = voiceId.ifBlank { null },
                speechRate = params.speechRate,
                pitch = params.pitch,
                pronunciation = params.pronunciationHints
            )
            val selection = service.selector.select(profile)
            val result = CompletableDeferred<String>()
            // Serialized inference: one synthesis at a time in the habitat.
            val accepted = scheduler.submit {
                when (selection) {
                    is VoiceProviderSelector.Selection.Selected ->
                        result.complete(runSynthesis(selection.provider, selection.voice?.id, text, language, params, outFile(outPath)))
                    is VoiceProviderSelector.Selection.Fallback ->
                        result.complete(runSynthesis(selection.provider, selection.voice?.id, text, language, params, outFile(outPath)))
                    is VoiceProviderSelector.Selection.Failure ->
                        result.complete(json(false, error = selection.reason))
                }
            }
            if (!accepted) return json(false, error = "voice scheduler closed")
            // First synthesis may download the model; allow generous time, but
            // cancel() will abort the wait (the provider flags cancellation).
            val jsonResult = kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(TimeUnit.MINUTES.toMillis(6)) {
                    result.await()
                }
            }
            return jsonResult ?: json(false, error = "synthesis timeout")
        }

        override fun cancel(): Boolean {
            // Flags every provider to stop; the scheduler's in-flight generation
            // sees the flag via the generation callback and returns early.
            var any = false
            service.providers.forEach { runCatching { it.cancel(); any = true } }
            return any
        }

        override fun getCapabilities(): String {
            val providersArr = JSONArray()
            service.registry.all().forEach { p ->
                val cap = p.capabilities()
                providersArr.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("languages", JSONArray(cap.languages))
                        .put("voices", JSONArray(cap.voices.map { it.id }))
                        .put("quality", cap.quality.name)
                        .put("streaming", cap.streaming)
                        .put("cloning", cap.cloning)
                        .put("offline", cap.offline)
                        .put("memoryMb", cap.estimatedMemoryMb)
                        .put("modelSizeMb", cap.modelSizeMb)
                        .put("latencyMs", cap.estimatedLatencyMs)
                )
            }
            val modelsArr = JSONArray()
            VoiceModelManifest.ALL.forEach { entry ->
                val st = service.modelManager.statusFlow(entry.id).value
                modelsArr.put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("state", st.state.name)
                        .put("present", st.present)
                        .put("memoryMb", st.memoryMb)
                )
            }
            return JSONObject()
                .put("providers", providersArr)
                .put("models", modelsArr)
                .put("schedulerCompleted", service.scheduler.completed)
                .toString()
        }

        override fun getStatus(): String = buildString {
            append("pid=").append(voicePid)
            append(" uptimeMs=").append(SystemClock.elapsedRealtime() - startRealtime)
            append(" schedulerCompleted=").append(service.scheduler.completed)
            append(" schedulerMaxConcurrent=").append(service.scheduler.maxConcurrencyObserved)
            append(" providers=").append(service.registry.all().joinToString(",") { it.id })
            append(" models=").append(
                VoiceModelManifest.ALL.joinToString(",") { e ->
                    "${e.id}:${service.modelManager.statusFlow(e.id).value.state}"
                }
            )
        }

        override fun crashNow() {
            // The isolation proof. An uncaught exception on a thread kills ONLY
            // this process; the app process is untouched. Binder death detection
            // is cause-agnostic — a native SIGSEGV walks the same death path.
            Thread {
                Thread.currentThread().name = "voice-crash"
                throw RuntimeException("VOICE_SUBSTRATE_DEMO_CRASH pid=$voicePid")
            }.start()
        }

        // ─────────────────────────────────────────── helpers

        private fun runSynthesis(
            provider: VoiceProvider,
            voiceId: String?,
            text: String,
            language: String,
            params: VoiceSynthesisParams,
            outFile: File
        ): String {
            val t0 = SystemClock.elapsedRealtime()
            return try {
                // Initialize is idempotent and cheap when already done. Both the
                // init and the synthesis run under the scheduler thread.
                val result = kotlinx.coroutines.runBlocking {
                    runCatching { provider.initialize(service) }
                        .getOrElse { throw it }
                    provider.synthesize(text, language, voiceId, params, outFile)
                }
                val wallMs = SystemClock.elapsedRealtime() - t0
                if (result.success) {
                    VoiceMetrics.recordSynthesis(provider.id, language, wallMs, result.durationMs, result.sampleRate)
                    json(true, sampleRate = result.sampleRate, channels = result.channels, durationMs = result.durationMs)
                } else {
                    service.failureController.onSynthesisFailure(provider.id, result.error)
                    json(false, error = result.error ?: "synthesis failed")
                }
            } catch (e: Throwable) {
                service.failureController.onSynthesisFailure(provider.id, e.message)
                json(false, error = e.message ?: "synthesis threw")
            }
        }

        private fun parseOptions(optionsJson: String): VoiceSynthesisParams {
            if (optionsJson.isBlank()) return VoiceSynthesisParams()
            return runCatching {
                val o = JSONObject(optionsJson)
                VoiceSynthesisParams(
                    speechRate = o.optDouble("speechRate", 1.0).toFloat().takeIf { it > 0 } ?: 1.0f,
                    pitch = o.optDouble("pitch", 1.0).toFloat().takeIf { it > 0 } ?: 1.0f,
                    pronunciationHints = emptyMap(),
                    utteranceId = o.optString("utteranceId", "voice_${System.currentTimeMillis()}")
                )
            }.getOrDefault(VoiceSynthesisParams())
        }

        private fun outFile(outPath: String): File {
            if (outPath.isNotBlank()) {
                val f = File(outPath)
                f.parentFile?.mkdirs()
                return f
            }
            val f = File(service.cacheDir, "voice_synth_${System.currentTimeMillis()}.wav")
            f.parentFile?.mkdirs()
            return f
        }

        private fun json(
            success: Boolean,
            sampleRate: Int = 0,
            channels: Int = 1,
            durationMs: Long = 0,
            error: String? = null
        ): String = JSONObject()
            .put("success", success)
            .put("sampleRate", sampleRate)
            .put("channels", channels)
            .put("durationMs", durationMs)
            .put("error", error ?: JSONObject.NULL)
            .toString()
    }
}
