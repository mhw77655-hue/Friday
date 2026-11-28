package ai.friday.assistant.onefriday

import ai.friday.assistant.body.MemoryStorePort
import ai.friday.assistant.capability.CapabilityRegistry
import ai.friday.assistant.failure.FailureReport
import ai.friday.assistant.turn.TurnContext
import ai.friday.assistant.memory.EmbeddingProvider
import ai.friday.assistant.memory.MemoryGraphStore
import ai.friday.assistant.memory.provenance.AdapterManifest
import ai.friday.assistant.memory.provenance.ProvenanceLedger
import ai.friday.assistant.model.ModelBackend
import ai.friday.assistant.model.ResourceSnapshot
import ai.friday.assistant.threads.ThreadTracker
import ai.friday.assistant.trace.TurnTraceStore
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * ONE-2-COMPOSITION: a test-only plain-JVM adapter. It is not a production host.
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
    private val turnWork: (() -> Unit) -> Unit,
    private val later: (Long, () -> Unit) -> Unit,
    private val fastAckSink: (String) -> Unit,
    private val fullReplySink: (String) -> Unit,
    private val turnLogSink: (Boolean, String) -> Unit,
    private val warmer: () -> Unit,
    private val session: () -> TurnContext?,
    private val submittedSignal: () -> Unit,
    private val ackSignal: () -> Unit,
    private val firstSegmentSignal: () -> Unit,
    private val utteranceDoneSignal: () -> Unit,
    private val failureSink: (FailureReport) -> Unit,
    private val failureSurface: ai.friday.assistant.failure.FailureSurface,
    private val forgetter: ai.friday.assistant.memory.provenance.MemoryForgetter? = null,
    private val languageModelAvailable: () -> Boolean = { true }
) : PlatformPorts {

    override fun failureSurface(): ai.friday.assistant.failure.FailureSurface = failureSurface

    override fun memoryForgetter(): ai.friday.assistant.memory.provenance.MemoryForgetter? = forgetter

    override fun modelScope(): CoroutineScope = scope

    override fun resourceSnapshot(): ResourceSnapshot = snapshot()

    override fun nowMs(): Long = clock()

    override fun runTurnWork(block: () -> Unit) = turnWork(block)

    override fun runLater(ms: Long, block: () -> Unit) = later(ms, block)

    override fun speakFastAck(text: String) = fastAckSink(text)

    override fun speakFullReply(text: String) = fullReplySink(text)

    override fun logTurn(fromFriday: Boolean, text: String) = turnLogSink(fromFriday, text)

    override fun warmModel() = warmer()

    override fun hasLanguageModel(modelManager: ai.friday.assistant.model.ModelManager): Boolean =
        languageModelAvailable()

    override fun sessionContext(): TurnContext? = session()

    override fun onUserSubmitted() = submittedSignal()

    override fun onFastPathAck() = ackSignal()

    override fun onFirstSegmentReady() = firstSegmentSignal()

    override fun onUtteranceCompleted() = utteranceDoneSignal()

    override fun reportFailure(report: FailureReport) = failureSink(report)

    override fun stores(): PlatformStores = stores
}

/**
 * ONE-2-COMPOSITION: the JVM host's stores.
 *
 * The graph store, embedder and memory port arrive from the caller — on the
 * host-side tests these are deterministic in-memory implementations.
 * The capability registry is created here rather than read from a holder,
 * because there is no process-wide holder outside Android; it is held here so
 * every organ in one Friday reads the same registry instance.
 */
class JvmPlatformStores(
    private val graphStore: MemoryGraphStore,
    private val embeddingProvider: EmbeddingProvider,
    private val memoryStore: MemoryStorePort,
    private val backend: ModelBackend,
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

    override fun turnTraceStore(): TurnTraceStore? = turnTraceStore

    override fun threadTracker(): ThreadTracker? = threadTracker

    override fun provenanceLedger(): ProvenanceLedger? = provenanceLedger

    override fun adapterManifest(): AdapterManifest? = adapterManifest
}
