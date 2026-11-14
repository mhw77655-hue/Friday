package com.friday.app.onefriday

import android.content.Context
import com.friday.app.ObsidianSync
import com.friday.app.companioncore.presence.CompanionCoreHolder
import com.friday.app.failure.FailureSurface
import com.friday.app.humancore.HumanCore
import com.friday.app.humancore.protocol.SessionContext
import com.friday.app.latency.AckSpeech
import com.friday.app.latency.LatencyLayer
import com.friday.app.latency.WarmupEngine
import com.friday.app.model.AndroidResourceSnapshot
import com.friday.app.model.ResourceSnapshot
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
    private val memoryStore: com.friday.app.body.MemoryStorePort,
    private val humanCoreStorage: com.friday.app.humancore.store.StoragePort,
    private val humanCoreMemorySink: (com.friday.app.humancore.mod.MemoryAnnotation) -> Unit,
    private val failureSurface: FailureSurface,
    private val modelBackend: com.friday.app.model.ModelBackend,
    private val voiceSynthesizer: com.friday.app.voice.VoiceForgeSynthesizer,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val turnTraceStore: com.friday.app.trace.TurnTraceStore? = null,
    private val threadTracker: com.friday.app.threads.ThreadTracker? = null,
    private val provenanceLedger: com.friday.app.memory.provenance.ProvenanceLedger? = null,
    private val adapterManifest: com.friday.app.memory.provenance.AdapterManifest? = null,
    private val modelProviders: Map<com.friday.app.env.ModelProviderType, com.friday.app.model.ModelProvider> = emptyMap()
) : PlatformPorts {

    override fun modelScope(): CoroutineScope = scope

    /**
     * The phone's real reading of its own resources, taken live at admission
     * time. The old app wiring built exactly this governor; nothing about it
     * changed — it just moved behind the port.
     */
    override fun resourceSnapshot(): ResourceSnapshot = AndroidResourceSnapshot(context)

    override fun nowMs(): Long = clock()

    override fun runTurnWork(block: () -> Unit) = LatencyLayer.dispatchTurnWork(block)

    override fun runLater(ms: Long, block: () -> Unit) = LatencyLayer.scheduleTurnWork(ms, block)

    override fun speakFastAck(text: String) = AckSpeech.fastSpeak(text)

    /** The same sink the app wired: sentence-streaming TTS when the body has
     *  attached, platform TTS otherwise. */
    override fun speakFullReply(text: String) = LatencyLayer.speakReplyText(text)

    override fun logTurn(fromFriday: Boolean, text: String) = ObsidianSync.logTurn(fromFriday, text)

    override fun warmModel() {
        WarmupEngine.start()
        WarmupEngine.prime()
    }

    /**
     * The same per-turn session context the app built, now derived from the
     * injected clock so one Friday has exactly one notion of "now".
     */
    override fun sessionContext(): SessionContext {
        val snapshot = HumanCore.snapshot()
        val presence = snapshot?.presence
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = clock()
        return SessionContext(
            channel = SessionContext.Channel.MOBILE_APP,
            localHour = calendar.get(Calendar.HOUR_OF_DAY),
            secondsSinceLastContact = snapshot?.secondsSinceLastContact,
            wasAway = presence?.mode != com.friday.app.humancore.protocol.PresenceState.Mode.ACTIVE,
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
    override fun reportFailure(report: com.friday.app.failure.FailureReport) {
        failureSurface.report(report)
    }

    override fun humanCoreStorage(): com.friday.app.humancore.store.StoragePort = humanCoreStorage

    override fun humanCoreMemorySink(): ((com.friday.app.humancore.mod.MemoryAnnotation) -> Unit)? =
        humanCoreMemorySink

    override fun stores(): PlatformStores = AndroidPlatformStores(
        context = context,
        memoryStore = memoryStore,
        backend = modelBackend,
        voiceSynthesizer = voiceSynthesizer,
        traceStore = turnTraceStore,
        threads = threadTracker,
        provenance = provenanceLedger,
        adapters = adapterManifest,
        modelProviders = modelProviders
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
    private val memoryStore: com.friday.app.body.MemoryStorePort,
    private val backend: com.friday.app.model.ModelBackend,
    private val voiceSynthesizer: com.friday.app.voice.VoiceForgeSynthesizer,
    private val traceStore: com.friday.app.trace.TurnTraceStore? = null,
    private val threads: com.friday.app.threads.ThreadTracker? = null,
    private val provenance: com.friday.app.memory.provenance.ProvenanceLedger? = null,
    private val adapters: com.friday.app.memory.provenance.AdapterManifest? = null,
    private val modelProviders: Map<com.friday.app.env.ModelProviderType, com.friday.app.model.ModelProvider> = emptyMap()
) : PlatformStores {

    override fun graphStore(): com.friday.app.memory.MemoryGraphStore =
        com.friday.app.memory.AndroidMemoryGraphStore(context)

    override fun embeddingProvider(): com.friday.app.memory.EmbeddingProvider =
        com.friday.app.memory.NeuralEmbeddingProvider()

    override fun memoryStore(): com.friday.app.body.MemoryStorePort = memoryStore

    override fun capabilityRegistry(): com.friday.app.capability.CapabilityRegistry =
        com.friday.app.capability.CapabilityRegistryHolder.get()

    override fun modelBackend(): com.friday.app.model.ModelBackend = backend

    override fun modelProviders(): Map<com.friday.app.env.ModelProviderType, com.friday.app.model.ModelProvider> = modelProviders

    override fun voiceSynthesizer(): com.friday.app.voice.VoiceForgeSynthesizer = voiceSynthesizer

    override fun turnTraceStore(): com.friday.app.trace.TurnTraceStore? = traceStore

    override fun threadTracker(): com.friday.app.threads.ThreadTracker? = threads

    override fun provenanceLedger(): com.friday.app.memory.provenance.ProvenanceLedger? = provenance

    override fun adapterManifest(): com.friday.app.memory.provenance.AdapterManifest? = adapters
}
