package com.jarvis.app.onefriday

import android.content.Context
import com.jarvis.app.ObsidianSync
import com.jarvis.app.companioncore.presence.CompanionCoreHolder
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.protocol.SessionContext
import com.jarvis.app.latency.AckSpeech
import com.jarvis.app.latency.LatencyLayer
import com.jarvis.app.latency.WarmupEngine
import com.jarvis.app.model.AndroidResourceSnapshot
import com.jarvis.app.model.ResourceSnapshot
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
    private val humanCoreStorage: com.jarvis.app.humancore.store.StoragePort,
    private val humanCoreMemorySink: (com.jarvis.app.humancore.mod.MemoryAnnotation) -> Unit,
    private val failureSurface: FailureSurface,
    private val modelBackend: com.jarvis.app.model.ModelBackend,
    private val voiceSynthesizer: com.jarvis.app.voice.VoiceForgeSynthesizer,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val turnTraceStore: com.jarvis.app.trace.TurnTraceStore? = null,
    private val threadTracker: com.jarvis.app.threads.ThreadTracker? = null,
    private val provenanceLedger: com.jarvis.app.memory.provenance.ProvenanceLedger? = null,
    private val adapterManifest: com.jarvis.app.memory.provenance.AdapterManifest? = null
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

    override fun logTurn(fromJarvis: Boolean, text: String) = ObsidianSync.logTurn(fromJarvis, text)

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
            wasAway = presence?.mode != com.jarvis.app.humancore.protocol.PresenceState.Mode.ACTIVE,
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

    override fun reportFailure(report: com.jarvis.app.failure.FailureReport) =
        failureSurface.report(report)

    override fun humanCoreStorage(): com.jarvis.app.humancore.store.StoragePort = humanCoreStorage

    override fun humanCoreMemorySink(): ((com.jarvis.app.humancore.mod.MemoryAnnotation) -> Unit)? =
        humanCoreMemorySink

    override fun stores(): PlatformStores = AndroidPlatformStores(
        context = context,
        memoryStore = memoryStore,
        backend = modelBackend,
        voiceSynthesizer = voiceSynthesizer,
        traceStore = turnTraceStore,
        threads = threadTracker,
        provenance = provenanceLedger,
        adapters = adapterManifest
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
    private val voiceSynthesizer: com.jarvis.app.voice.VoiceForgeSynthesizer,
    private val traceStore: com.jarvis.app.trace.TurnTraceStore? = null,
    private val threads: com.jarvis.app.threads.ThreadTracker? = null,
    private val provenance: com.jarvis.app.memory.provenance.ProvenanceLedger? = null,
    private val adapters: com.jarvis.app.memory.provenance.AdapterManifest? = null
) : PlatformStores {

    override fun graphStore(): com.jarvis.app.memory.MemoryGraphStore =
        com.jarvis.app.memory.AndroidMemoryGraphStore(context)

    override fun embeddingProvider(): com.jarvis.app.memory.EmbeddingProvider =
        com.jarvis.app.memory.NeuralEmbeddingProvider()

    override fun memoryStore(): com.jarvis.app.body.MemoryStorePort = memoryStore

    override fun capabilityRegistry(): com.jarvis.app.capability.CapabilityRegistry =
        com.jarvis.app.capability.CapabilityRegistryHolder.get()

    override fun modelBackend(): com.jarvis.app.model.ModelBackend = backend

    override fun voiceSynthesizer(): com.jarvis.app.voice.VoiceForgeSynthesizer = voiceSynthesizer

    override fun turnTraceStore(): com.jarvis.app.trace.TurnTraceStore? = traceStore

    override fun threadTracker(): com.jarvis.app.threads.ThreadTracker? = threads

    override fun provenanceLedger(): com.jarvis.app.memory.provenance.ProvenanceLedger? = provenance

    override fun adapterManifest(): com.jarvis.app.memory.provenance.AdapterManifest? = adapters
}
