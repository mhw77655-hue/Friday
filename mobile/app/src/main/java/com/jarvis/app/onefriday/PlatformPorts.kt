package com.jarvis.app.onefriday

import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.capability.CapabilityRegistry
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.humancore.mod.MemoryAnnotation
import com.jarvis.app.humancore.protocol.SessionContext
import com.jarvis.app.humancore.store.StoragePort
import com.jarvis.app.memory.EmbeddingProvider
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.provenance.AdapterManifest
import com.jarvis.app.memory.provenance.ProvenanceLedger
import com.jarvis.app.model.ModelBackend
import com.jarvis.app.model.ResourceSnapshot
import com.jarvis.app.threads.ThreadTracker
import com.jarvis.app.trace.TurnTraceStore
import com.jarvis.app.voice.VoiceForgeSynthesizer
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * ONE-2-COMPOSITION: the platform adapter [TurnPathAssembly] reads to build a
 * Friday.
 *
 * This file deliberately imports NO android.* type. That is the whole point of
 * the split: the turn path is platform-free, so the thing that names the
 * platform is this interface and its two implementations
 * ([JvmPlatformPorts] for the Termux/plain-JVM host, `AndroidPlatformPorts` for
 * the phone), never the organs.
 *
 * What belongs here is only what genuinely differs between two hosts running
 * the SAME individual: where durable files go, what the device looks like right
 * now, what time it is, where speech and turn-state go, and which store
 * implementations the platform can offer. Everything else — the claim
 * workspace, the identity/emotion/social/continuity stack, the cognitive engine,
 * the admission governor's policy, the Human Core wiring — is decided ONCE, in
 * [TurnPathAssembly], for every host.
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
    fun logTurn(fromJarvis: Boolean, text: String)

    /** Warm the model so the first real reply is not a cold load. */
    fun warmModel()

    /** The per-turn session context, or null when the host has no session to describe. */
    fun sessionContext(): SessionContext?

    /** Turn-state signals the host's UI/companion surface consumes. */
    fun onUserSubmitted()

    fun onFastPathAck()

    fun onFirstSegmentReady()

    fun onUtteranceCompleted()

    /** Where a turn-path failure is reported. Never swallowed inside the root. */
    fun reportFailure(report: FailureReport)

    /** The legacy Human Core's durable storage for this host. */
    fun humanCoreStorage(): StoragePort

    /**
     * Optional sink for the legacy Human Core's memory annotations. Null means
     * this host does not mirror them anywhere — the Human Core itself is
     * untouched either way.
     */
    fun humanCoreMemorySink(): ((MemoryAnnotation) -> Unit)? = null

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
    fun memoryForgetter(): com.jarvis.app.memory.provenance.MemoryForgetter? = null
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

    /** The speech synthesizer the spoken-reply backend drives. */
    fun voiceSynthesizer(): VoiceForgeSynthesizer

    /** Optional per-turn trace store; null disables tracing honestly. */
    fun turnTraceStore(): TurnTraceStore?

    /** Optional open-thread tracker; null disables thread tracking honestly. */
    fun threadTracker(): ThreadTracker?

    /** Optional durable provenance ledger; null disables provenance honestly. */
    fun provenanceLedger(): ProvenanceLedger?

    /** Optional adapter manifest consulted by the model load path; null allows every load. */
    fun adapterManifest(): AdapterManifest?
}