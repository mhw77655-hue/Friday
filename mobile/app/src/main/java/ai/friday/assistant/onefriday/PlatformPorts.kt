package ai.friday.assistant.onefriday

import ai.friday.assistant.body.MemoryStorePort
import ai.friday.assistant.capability.CapabilityRegistry
import ai.friday.assistant.failure.FailureReport
import ai.friday.assistant.failure.FailureSurface
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
 * ONE-2-COMPOSITION: the platform adapter [TurnPathAssembly] reads to build a
 * Friday.
 *
 * This file deliberately imports NO android.* type. That is the whole point of
 * the split: the turn path is platform-free, so the thing that names the
 * platform is this interface and its sole production implementation
 * (`AndroidPlatformPorts` for the phone), never the organs. JVM adapters are test-only.
 *
 * What belongs here is only what genuinely differs between the Android host
 * and a test fixture: where durable files go, what the device looks like right
 * now, what time it is, where speech and turn-state go, and which store
 * implementations the platform can offer. Everything else — the claim
 * workspace, the identity/emotion/social/continuity stack, the cognitive engine,
 * the turn-context and owner-seeded identity boundaries — is decided ONCE, in
 * [TurnPathAssembly] in the Android app; JVM fixtures are test-only.
 *
 * It is an adapter, not an owner: nothing here decides a cognitive outcome, and
 * nothing here is a cognitive role (VISION.md Section 4 / Section 13).
 */
interface PlatformPorts {

    /** App-private durable directory. Every local file this Friday writes lives under it. */
    val storageDir: File

    /** The coroutine scope the long-lived organs (model manager, stores) run on. */
    fun modelScope(): CoroutineScope

    /**
     * A LIVE reading of this device's resources at wake-admission time.
     * Injected, never defaulted to an all-clear inside the composition root: a
     * host that cannot read a value says so in its own implementation instead of
     * the root pretending the device is healthy.
     */
    fun resourceSnapshot(): ResourceSnapshot

    /** Aggregate outcome feedback for NAE; hosts without an adaptive observer may ignore it. */
    fun recordWorkloadOutcome(organ: String, succeeded: Boolean, latencyMs: Long, peakMemoryMb: Long = 0L) = Unit

    /** The turn clock. Injected so a host (or a harness) owns time. */
    fun nowMs(): Long

    /** Run one unit of slow-path turn work (perception, generation hand-off, completion). */
    fun runTurnWork(block: () -> Unit)

    /** Run [block] after [ms] on the host's own scheduler. */
    fun runLater(ms: Long, block: () -> Unit)

    /** Spoken fast-path acknowledgement sink. */
    fun speakFastAck(text: String)

    /** Spoken full-reply sink. */
    fun speakFullReply(text: String)

    /** Durable turn-log sink (the Obsidian vault sidecar on the phone). */
    fun logTurn(fromFriday: Boolean, text: String)

    /** Warm the model so the first real reply is not a cold load. */
    fun warmModel()

    /** Whether the phone currently has a real language model available. Test hosts default to true. */
    fun hasLanguageModel(modelManager: ai.friday.assistant.model.ModelManager): Boolean = true

    /** The per-turn session evidence; unavailable fields remain null. */
    fun sessionContext(): TurnContext?

    /** Turn-state signals the host's UI/companion surface consumes. */
    fun onUserSubmitted()

    fun onFastPathAck()

    fun onFirstSegmentReady()

    fun onUtteranceCompleted()

    /** Where a turn-path failure is reported. Never swallowed inside the root. */
    fun reportFailure(report: FailureReport)

    /** The store implementations this platform can actually offer. */
    fun stores(): PlatformStores

    /**
     * The nervous-system failure surface this Friday reports into. A host owns
     * it (the phone's is backed by a durable store from the moment it boots);
     * the composition root only reports into it.
     */
    fun failureSurface(): FailureSurface

    /**
     * The optional forget-propagation engine the cognitive engine's write-back
     * path consults. Null ⇒ no forget propagation, on every host, because a
     * missing optional capability must read the same everywhere.
     */
    /**
     * Register host-owned capabilities into Friday's shared fabric. Android
     * supplies real device adapters; JVM hosts may intentionally supply none.
     */
    fun registerCapabilities(fabric: ai.friday.assistant.cognitive.capability.CapabilityFabric) = Unit

    fun memoryForgetter(): ai.friday.assistant.memory.provenance.MemoryForgetter? = null
}

/**
 * The store half of the adapter: the implementations that cannot exist without
 * a platform (sqlite, neural embeddings, the process Context, a local HTTP model
 * client). A host supplies the ones it has; the composition root never branches
 * on which host it is running.
 */
interface PlatformStores {

    /** Durable fact graph. */
    fun graphStore(): MemoryGraphStore

    /** The embedder the retrievers and signal scorers share. */
    fun embeddingProvider(): EmbeddingProvider

    /** Keyword/tag memory retrieval port handed to the cognitive engine. */
    fun memoryStore(): MemoryStorePort

    /** The capability registry organs read from and route through. */
    fun capabilityRegistry(): CapabilityRegistry

    /** The single model backend the one loading authority uses. */
    fun modelBackend(): ModelBackend

    /** Optional per-turn trace store; null disables tracing honestly. */
    fun turnTraceStore(): TurnTraceStore?

    /** Optional open-thread tracker; null disables thread tracking honestly. */
    fun threadTracker(): ThreadTracker?

    /** Optional durable provenance ledger; null disables provenance honestly. */
    fun provenanceLedger(): ProvenanceLedger?

    /** Optional adapter manifest consulted by the model load path; null allows every load. */
    fun adapterManifest(): AdapterManifest?
}