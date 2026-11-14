package com.friday.app.onefriday

import com.friday.app.body.MemoryStorePort
import com.friday.app.capability.CapabilityRegistry
import com.friday.app.failure.FailureReport
import com.friday.app.humancore.mod.MemoryAnnotation
import com.friday.app.humancore.protocol.SessionContext
import com.friday.app.humancore.store.StoragePort
import com.friday.app.memory.EmbeddingProvider
import com.friday.app.memory.MemoryGraphStore
import com.friday.app.memory.provenance.AdapterManifest
import com.friday.app.memory.provenance.ProvenanceLedger
import com.friday.app.model.ModelBackend
import com.friday.app.model.ResourceSnapshot
import com.friday.app.threads.ThreadTracker
import com.friday.app.trace.TurnTraceStore
import com.friday.app.voice.VoiceForgeSynthesizer
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * ONE-2-COMPOSITION: the plain-JVM / Termux host's adapter.
 *
 * This is the host with the least to offer — no sqlite, no process Context, no
 * battery — and that is exactly why it is worth doing carefully: when the
 * *weakest* host gets the same turn path as the phone, the turn path cannot have
 * been secretly Android-shaped all along.
 *
 * Every dependency is explicit and injectable. Nothing here is defaulted to
 * "healthy" or "present": a capability a JVM host does not have is passed in as
 * null and stays null in the organs, so absence is visible in the composition
 * instead of being papered over.
 */
class JvmPlatformPorts(
    override val storageDir: File,
    private val scope: CoroutineScope,
    private val stores: PlatformStores,
    private val snapshot: () -> ResourceSnapshot,
    private val clock: () -> Long,
    private val humanCoreStorage: StoragePort,
    private val turnWork: (() -> Unit) -> Unit,
    private val later: (Long, () -> Unit) -> Unit,
    private val fastAckSink: (String) -> Unit,
    private val fullReplySink: (String) -> Unit,
    private val turnLogSink: (Boolean, String) -> Unit,
    private val warmer: () -> Unit,
    private val session: () -> SessionContext?,
    private val submittedSignal: () -> Unit,
    private val ackSignal: () -> Unit,
    private val firstSegmentSignal: () -> Unit,
    private val utteranceDoneSignal: () -> Unit,
    private val failureSink: (FailureReport) -> Unit,
    private val failureSurface: com.friday.app.failure.FailureSurface,
    private val forgetter: com.friday.app.memory.provenance.MemoryForgetter? = null,
    private val memorySink: ((MemoryAnnotation) -> Unit)? = null
) : PlatformPorts {

    override fun failureSurface(): com.friday.app.failure.FailureSurface = failureSurface

    override fun memoryForgetter(): com.friday.app.memory.provenance.MemoryForgetter? = forgetter

    override fun modelScope(): CoroutineScope = scope

    override fun resourceSnapshot(): ResourceSnapshot = snapshot()

    override fun nowMs(): Long = clock()

    override fun runTurnWork(block: () -> Unit) = turnWork(block)

    override fun runLater(ms: Long, block: () -> Unit) = later(ms, block)

    override fun speakFastAck(text: String) = fastAckSink(text)

    override fun speakFullReply(text: String) = fullReplySink(text)

    override fun logTurn(fromFriday: Boolean, text: String) = turnLogSink(fromFriday, text)

    override fun warmModel() = warmer()

    override fun sessionContext(): SessionContext? = session()

    override fun onUserSubmitted() = submittedSignal()

    override fun onFastPathAck() = ackSignal()

    override fun onFirstSegmentReady() = firstSegmentSignal()

    override fun onUtteranceCompleted() = utteranceDoneSignal()

    override fun reportFailure(report: FailureReport) = failureSink(report)

    override fun humanCoreStorage(): StoragePort = humanCoreStorage

    override fun humanCoreMemorySink(): ((MemoryAnnotation) -> Unit)? = memorySink

    override fun stores(): PlatformStores = stores
}

/**
 * ONE-2-COMPOSITION: the JVM host's stores.
 *
 * The graph store, embedder and memory port arrive from the caller — on the
 * Termux host those are in-memory implementations, in tests they are fixtures.
 * The capability registry is created here rather than read from a holder,
 * because there is no process-wide holder outside Android; it is held here so
 * every organ in one Friday reads the same registry instance.
 */
class JvmPlatformStores(
    private val graphStore: MemoryGraphStore,
    private val embeddingProvider: EmbeddingProvider,
    private val memoryStore: MemoryStorePort,
    private val backend: ModelBackend,
    private val voiceSynthesizer: VoiceForgeSynthesizer,
    private val capabilityRegistry: CapabilityRegistry = CapabilityRegistry(),
    private val turnTraceStore: TurnTraceStore? = null,
    private val threadTracker: ThreadTracker? = null,
    private val provenanceLedger: ProvenanceLedger? = null,
    private val adapterManifest: AdapterManifest? = null
) : PlatformStores {

    override fun graphStore(): MemoryGraphStore = graphStore

    override fun embeddingProvider(): EmbeddingProvider = embeddingProvider

    override fun memoryStore(): MemoryStorePort = memoryStore

    override fun capabilityRegistry(): CapabilityRegistry = capabilityRegistry

    override fun modelBackend(): ModelBackend = backend

    override fun voiceSynthesizer(): VoiceForgeSynthesizer = voiceSynthesizer

    override fun turnTraceStore(): TurnTraceStore? = turnTraceStore

    override fun threadTracker(): ThreadTracker? = threadTracker

    override fun provenanceLedger(): ProvenanceLedger? = provenanceLedger

    override fun adapterManifest(): AdapterManifest? = adapterManifest
}
