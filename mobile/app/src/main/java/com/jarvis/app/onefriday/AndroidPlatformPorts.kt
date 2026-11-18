package com.jarvis.app.onefriday

import android.content.Context
import com.jarvis.app.ObsidianSync
import com.jarvis.app.companioncore.presence.CompanionCoreHolder
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.cognitive.capability.CapabilityFabric
import com.jarvis.app.turn.TurnContext
import com.jarvis.app.latency.AckSpeech
import com.jarvis.app.latency.LatencyLayer
import com.jarvis.app.latency.WarmupEngine
import com.jarvis.app.model.AndroidResourceSnapshot
import com.jarvis.app.model.ResourceSnapshot
import com.jarvis.app.symbiosis.NanoSymbioticController
import kotlinx.coroutines.CoroutineScope
import java.util.Calendar

/**
 * ONE-2-COMPOSITION: the phone's adapter.
 *
 * Everything android-bound lives on this side of the seam: the process Context,
 * the sqlite graph store, the neural embedder, the real battery/CPU reading, the
 * Companion Core's turn signals, the Obsidian vault sidecar, and the platform
 * speech engines.
 *
 * Note what is NOT here: any decision about what a turn means. This class is the
 * order of *supply*, and the phone supplies exactly what it supplies.
 */
class AndroidPlatformPorts(
    private val context: Context,
    override val storageDir: java.io.File,
    private val scope: CoroutineScope,
    private val memoryStore: com.jarvis.app.body.MemoryStorePort,
    private val failureSurface: FailureSurface,
    private val modelBackend: com.jarvis.app.model.ModelBackend,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val turnTraceStore: com.jarvis.app.trace.TurnTraceStore? = null,
    private val threadTracker: com.jarvis.app.threads.ThreadTracker? = null,
    private val provenanceLedger: com.jarvis.app.memory.provenance.ProvenanceLedger? = null,
    private val adapterManifest: com.jarvis.app.memory.provenance.AdapterManifest? = null,
    private val nanoSymbiosis: NanoSymbioticController? = null,
    private val deviceControlRouterProvider: () -> com.jarvis.app.android.DeviceControlRouter? = { null },
    private val riskGateProvider: () -> com.jarvis.app.android.RiskGate? = { null }
) : PlatformPorts {

    override fun registerCapabilities(fabric: CapabilityFabric) {
        val result = fabric.register(
            com.jarvis.app.android.AndroidMediaControlCapability(
                routerProvider = deviceControlRouterProvider,
                riskGateProvider = riskGateProvider
            )
        )
        check(result is com.jarvis.app.cognitive.capability.CapabilityRegistry.RegisterResult.Ok) {
            "Android media capability registration failed: $result"
        }

        val councilResult = fabric.register(
            com.jarvis.app.model.remote.RemoteAiCouncilCapability {
                (context.applicationContext as? com.jarvis.app.JarvisApplication)?.providerCouncil
            }
        )
        check(councilResult is com.jarvis.app.cognitive.capability.CapabilityRegistry.RegisterResult.Ok) {
            "External AI council capability registration failed: $councilResult"
        }
    }

    override fun grantedCapabilityPermissions(): Set<com.jarvis.app.cognitive.capability.CapabilityPermission> {
        val app = context.applicationContext as? com.jarvis.app.JarvisApplication
        val enabled = app?.providerCouncil?.isEnabled() == true &&
            app.remoteProviderProfiles.createEnabledEntries().isNotEmpty()
        return if (enabled) setOf(
            com.jarvis.app.cognitive.capability.CapabilityPermission.EXTERNAL_AI
        ) else emptySet()
    }

    override fun modelScope(): CoroutineScope = scope

    /**
     * The phone's real reading of its own resources, taken live at admission
     * time. The old app wiring built exactly this governor; nothing about it
     * changed — it just moved behind the port.
     */
    override fun resourceSnapshot(): ResourceSnapshot =
        nanoSymbiosis?.resourceSnapshot() ?: AndroidResourceSnapshot(context)

    override fun recordWorkloadOutcome(
        organ: String,
        succeeded: Boolean,
        latencyMs: Long,
        peakMemoryMb: Long
    ) {
        nanoSymbiosis?.recordOutcome(organ, succeeded, latencyMs, peakMemoryMb)
    }

    override fun nowMs(): Long = clock()

    override fun runTurnWork(block: () -> Unit) = LatencyLayer.dispatchTurnWork(block)

    override fun runLater(ms: Long, block: () -> Unit) = LatencyLayer.scheduleTurnWork(ms, block)

    override fun speakFastAck(text: String) = AckSpeech.fastSpeak(text)

    /** The same sink the app wired: sentence-streaming TTS when the body has
     *  attached, platform TTS otherwise. */
    override fun speakFullReply(text: String) = LatencyLayer.speakReplyText(text)

    override fun logTurn(fromJarvis: Boolean, text: String) = ObsidianSync.logTurn(fromJarvis, text)

    override fun warmModel() {
        WarmupEngine.start()
        WarmupEngine.prime()
    }

    override fun hasLanguageModel(modelManager: com.jarvis.app.model.ModelManager): Boolean {
        val provider = modelManager.activeProvider.value
        val health = modelManager.providerHealth.value
        return provider.providerType != com.jarvis.app.env.ModelProviderType.HEURISTIC &&
            health.isHealthy && health.modelLoaded
    }

    /**
     * Read and update only the local last-interaction timestamp. Unknown
     * presence/away state remains null; it is never inferred from missing
     * sensor/runtime evidence or fabricated as an all-clear observation.
     */
    override fun sessionContext(): TurnContext {
        val now = clock()
        val preferences = context.getSharedPreferences("friday_turn_session", Context.MODE_PRIVATE)
        val lastInteraction = preferences.getLong("last_interaction_at_ms", 0L)
        val secondsSinceLastContact = lastInteraction.takeIf { it > 0L && now >= it }
            ?.let { (now - it) / 1_000L }
        preferences.edit().putLong("last_interaction_at_ms", now).apply()

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        return TurnContext(
            channel = TurnContext.Channel.ANDROID_APP,
            localHour = calendar.get(Calendar.HOUR_OF_DAY),
            secondsSinceLastContact = secondsSinceLastContact,
            wasAway = null,
            prosody = null
        )
    }

    override fun onUserSubmitted() {
        CompanionCoreHolder.instance()?.integration?.onUserSubmitted()
    }

    override fun onFastPathAck() {
        CompanionCoreHolder.instance()?.integration?.onFastPathAck()
    }

    override fun onFirstSegmentReady() {
        CompanionCoreHolder.instance()?.integration?.onFirstSegmentReady()
    }

    override fun onUtteranceCompleted() {
        CompanionCoreHolder.instance()?.integration?.onUtteranceCompleted()
    }

    /**
     * Reporting only: the port is where a host's failure channel is plugged in,
     * not where an event is handed back. The real [FailureSurface] aggregates and
     * deduplicates, so the `FailureEvent` it returns is deliberately dropped
     * here — a turn's outcome must not depend on what the reporter does with it.
     */
    override fun reportFailure(report: com.jarvis.app.failure.FailureReport) {
        failureSurface.report(report)
    }

    override fun stores(): PlatformStores = AndroidPlatformStores(
        context = context,
        memoryStore = memoryStore,
        backend = modelBackend,
        traceStore = turnTraceStore,
        threads = threadTracker,
        provenance = provenanceLedger,
        adapters = adapterManifest,
        clock = clock
    )

    override fun failureSurface(): FailureSurface = failureSurface
}

/**
 * ONE-2-COMPOSITION: the store half of the phone's adapter.
 *
 * The android-bound stores in one readable place: sqlite fact graph, neural
 * embedder, keyword memory port, the process-wide capability registry, the real
 * local-model backend, the real speech synthesizer, and the local-only durable
 * stores (traces, threads, provenance, adapters). Every one of them is the
 * production class — this is a list of what the phone can offer, not a second
 * implementation of any of it.
 */
class AndroidPlatformStores(
    private val context: Context,
    private val memoryStore: com.jarvis.app.body.MemoryStorePort,
    private val backend: com.jarvis.app.model.ModelBackend,
    private val traceStore: com.jarvis.app.trace.TurnTraceStore? = null,
    private val threads: com.jarvis.app.threads.ThreadTracker? = null,
    private val provenance: com.jarvis.app.memory.provenance.ProvenanceLedger? = null,
    private val adapters: com.jarvis.app.memory.provenance.AdapterManifest? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : PlatformStores {

    override fun graphStore(): com.jarvis.app.memory.MemoryGraphStore =
        com.jarvis.app.memory.AndroidMemoryGraphStore(context, clock = clock)

    override fun embeddingProvider(): com.jarvis.app.memory.EmbeddingProvider =
        com.jarvis.app.memory.NeuralEmbeddingProvider()

    override fun memoryStore(): com.jarvis.app.body.MemoryStorePort = memoryStore

    override fun capabilityRegistry(): com.jarvis.app.capability.CapabilityRegistry =
        com.jarvis.app.capability.CapabilityRegistryHolder.get()

    override fun modelBackend(): com.jarvis.app.model.ModelBackend = backend

    override fun turnTraceStore(): com.jarvis.app.trace.TurnTraceStore? = traceStore

    override fun threadTracker(): com.jarvis.app.threads.ThreadTracker? = threads

    override fun provenanceLedger(): com.jarvis.app.memory.provenance.ProvenanceLedger? = provenance

    override fun adapterManifest(): com.jarvis.app.memory.provenance.AdapterManifest? = adapters
}
