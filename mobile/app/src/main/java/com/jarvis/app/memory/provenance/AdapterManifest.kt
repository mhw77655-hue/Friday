package com.jarvis.app.memory.provenance

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * ADAPTER-MANIFEST: the lifecycle state of one model adapter's trained bytes.
 *
 * An adapter is the only derived artifact that leaves the memory system's own
 * storage as raw model weights, so it is the artifact whose provenance has to be
 * provable rather than assumed: an adapter is [AdapterState.ACTIVE] only while
 * every memory it was trained from still exists. Forgetting one of those source
 * memories moves the adapter to [AdapterState.TAINTED], which is what
 * [AdapterLoadGate.mayLoad] refuses and what [AdapterManifest.retrainPlan]
 * computes a fresh training set for.
 */
enum class AdapterState {
    /** Every source memory this adapter was built from still exists. */
    ACTIVE,

    /**
     * At least one source memory was forgotten. The bytes may still be on disk,
     * but they encode a memory the user asked to be gone, so they must not load
     * and must not be presented as current.
     */
    TAINTED
}

/** One adapter's registered state, as [AdapterManifest] answers about it. */
data class AdapterRegistration(
    val adapterId: String,
    val sourceIds: List<String>,
    val state: AdapterState,
    val registeredAt: Long,
    /** The forgotten source ids that tainted this adapter, in the order found. */
    val taintedBy: List<String>
)

/**
 * ADAPTER-MANIFEST: the load-time authority a model adapter must pass.
 *
 * This is deliberately the smallest possible contract — one predicate — because
 * the only thing the load path needs to know is whether these bytes may enter
 * memory at all. [ModelManager][com.jarvis.app.model.ModelManager] holds an
 * optional gate and consults it at the single point where every model load
 * funnels, so no load path can bypass it.
 */
interface AdapterLoadGate {
    /** Whether the adapter's bytes named [adapterId] may be loaded. */
    fun mayLoad(adapterId: String): Boolean
}

/**
 * ADAPTER-MANIFEST: the registry that makes "what was this adapter trained
 * from" answerable, so a future training run can never fold in a memory that
 * cannot be traced back.
 *
 * No adapter training exists in this codebase yet and none is added here. What
 * this contract guarantees is that the moment one does exist, it cannot skip
 * the traceable-source rule:
 *
 *  - [register] names every source memory an adapter was built from, and mirrors
 *    that registration into the [ProvenanceLedger] as
 *    [ProvenanceKind.ADAPTER_BATCH], so the adapter joins the same provenance
 *    family as summaries, index rows and trace records;
 *  - FORGET-PROPAGATION calls [taint] for every adapter the ledger reports as
 *    deriving from a forgotten memory, moving it to [AdapterState.TAINTED];
 *  - [mayLoad] then refuses a tainted adapter at the real load seam, and
 *    [retrainPlan] returns exactly the sources that survive, so retraining is
 *    the only way an adapter becomes loadable again.
 *
 * An adapter that was never registered is not this contract's business:
 * [mayLoad] answers true for it, so ordinary model loads are unaffected.
 */
interface AdapterManifest : AdapterLoadGate {
    /** Whether [register]/[taint] currently append. Reads are always available. */
    val enabled: Boolean

    /** Enable/disable recording. Disabled recording is a no-op (negative control). */
    fun setEnabled(enabled: Boolean)

    /**
     * Record that [adapterId] was built from [sourceIds], and mirror the batch
     * into the [ProvenanceLedger] as [ProvenanceKind.ADAPTER_BATCH]. Re-registering
     * an adapter replaces its source set and clears any taint — that is the
     * retrain-and-reload path, and it is only reachable with a plan that
     * [retrainPlan] produced.
     */
    fun register(adapterId: String, sourceIds: List<String>): AdapterRegistration

    /** The state of [adapterId]; an unregistered adapter reads [AdapterState.ACTIVE]. */
    fun state(adapterId: String): AdapterState

    /** The source memories [adapterId] was registered from, in registration order. */
    fun sources(adapterId: String): List<String>

    /**
     * FORGET-PROPAGATION: mark [adapterId] [AdapterState.TAINTED] because
     * [forgottenSourceId] — one of its registered sources — was forgotten.
     * Returns false when the adapter is unknown, was not registered from that
     * source, or was already tainted by it.
     */
    fun taint(adapterId: String, forgottenSourceId: String): Boolean

    /**
     * The surviving training set for [adapterId]: its registered sources minus
     * every forgotten one. A source the [TombstoneStore] reports as tombstoned
     * counts as forgotten even when the adapter was registered after the forget,
     * so a retrain plan can never re-introduce a memory that is already gone.
     */
    fun retrainPlan(adapterId: String): List<String>

    /** Every registration, in registration order. */
    fun registrations(): List<AdapterRegistration>

    /** The local file backing this manifest (byte-scan surface). */
    val manifestFile: File?
}

/** ADAPTER-MANIFEST: the load refusal a tainted adapter produces. */
class TaintedAdapterRefused(adapterId: String) :
    IllegalStateException("adapter $adapterId is TAINTED: a source memory it was trained from was forgotten")

/**
 * ADAPTER-MANIFEST: local-file JSON-Lines [AdapterManifest].
 *
 * One line per mutation — a `register` line naming the source set, a `taint`
 * line naming the forgotten source — folded in order, so the state an adapter
 * is in after a restart is exactly the state it was in before it. The file is
 * grow-only, like the provenance ledger and the tombstone registry: no automatic
 * run ever rewrites history, and re-registering an adapter simply appends the
 * newer line that supersedes it.
 *
 * Only ids are stored, never adapter bytes and never memory content. The file
 * can therefore join a forget byte-scan corpus without carrying plaintext. All
 * work is local: the only APIs used are the JDK's file I/O and a JSON parser,
 * so this package keeps its no-network invariant in code and comments alike.
 */
class JsonlAdapterManifest(
    private val file: File,
    private val provenanceLedger: ProvenanceLedger? = null,
    private val tombstoneStore: TombstoneStore? = null,
    initiallyEnabled: Boolean = true
) : AdapterManifest {

    @Volatile
    private var _enabled: Boolean = initiallyEnabled

    override val enabled: Boolean get() = _enabled

    override fun setEnabled(enabled: Boolean) {
        _enabled = enabled
    }

    override val manifestFile: File get() = file

    private data class Entry(
        val adapterId: String,
        val sources: List<String>,
        val state: AdapterState,
        val registeredAt: Long,
        val taintedBy: List<String>
    ) {
        fun registration(): AdapterRegistration =
            AdapterRegistration(adapterId, sources, state, registeredAt, taintedBy)
    }

    override fun register(adapterId: String, sourceIds: List<String>): AdapterRegistration {
        require(adapterId.isNotBlank()) { "adapterId must not be blank" }
        val sources = sourceIds.distinct()
        val now = System.currentTimeMillis()
        val recording = enabled
        val result = synchronized(this) {
            val folded = fold()
            val existing = folded[adapterId]
            val next = Entry(
                adapterId = adapterId,
                sources = sources,
                state = AdapterState.ACTIVE,
                registeredAt = existing?.registeredAt ?: now,
                taintedBy = emptyList()
            )
            if (recording) {
                append(
                    JSONObject()
                        .put("type", "register")
                        .put("adapterId", adapterId)
                        .put("sourceIds", JSONArray(sources))
                        .put("at", now)
                )
                folded[adapterId] = next
            }
            next
        }
        // The registration is also a provenance record, so FORGET-PROPAGATION
        // finds this adapter by asking the ledger which artifacts derive from a
        // forgotten source — no separate adapter-to-source index to keep in sync.
        if (recording) {
            provenanceLedger?.record(adapterId, ProvenanceKind.ADAPTER_BATCH, sources)
        }
        return result.registration()
    }

    override fun state(adapterId: String): AdapterState = entryFor(adapterId)?.state ?: AdapterState.ACTIVE

    /**
     * The load decision. An adapter this manifest has never heard of is not its
     * business, so ordinary model loads are unaffected; a TAINTED one is refused
     * even when recording is disabled, because refusing is the safe direction.
     */
    override fun mayLoad(adapterId: String): Boolean = state(adapterId) != AdapterState.TAINTED

    override fun sources(adapterId: String): List<String> = entryFor(adapterId)?.sources ?: emptyList()

    override fun taint(adapterId: String, forgottenSourceId: String): Boolean = synchronized(this) {
        if (!_enabled) return false
        val folded = fold()
        val existing = folded[adapterId] ?: return false
        if (forgottenSourceId !in existing.sources) return false
        if (forgottenSourceId in existing.taintedBy) return false
        val next = existing.copy(
            state = AdapterState.TAINTED,
            taintedBy = existing.taintedBy + forgottenSourceId
        )
        append(
            JSONObject()
                .put("type", "taint")
                .put("adapterId", adapterId)
                .put("sourceId", forgottenSourceId)
                .put("at", System.currentTimeMillis())
        )
        folded[adapterId] = next
        true
    }

    override fun retrainPlan(adapterId: String): List<String> {
        val entry = entryFor(adapterId) ?: return emptyList()
        return entry.sources.filter { source ->
            source !in entry.taintedBy && tombstoneStore?.isTombstoned(source) != true
        }
    }

    override fun registrations(): List<AdapterRegistration> =
        synchronized(this) { fold().values.map { it.registration() } }

    private fun entryFor(adapterId: String): Entry? = synchronized(this) { fold()[adapterId] }

    /** Fold the append-only log in order; the last line for an adapter wins. */
    private fun fold(): MutableMap<String, Entry> {
        val folded = linkedMapOf<String, Entry>()
        if (!file.exists()) return folded
        file.useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                val adapterId = json.optString("adapterId").takeIf { it.isNotBlank() } ?: continue
                when (json.optString("type")) {
                    "register" -> {
                        val array = json.optJSONArray("sourceIds")
                        val sources = if (array == null) {
                            emptyList()
                        } else {
                            (0 until array.length()).map { array.getString(it) }
                        }
                        val previous = folded[adapterId]
                        folded[adapterId] = Entry(
                            adapterId = adapterId,
                            sources = sources.distinct(),
                            state = AdapterState.ACTIVE,
                            registeredAt = json.optLong("at", previous?.registeredAt ?: 0L),
                            taintedBy = emptyList()
                        )
                    }
                    "taint" -> {
                        val previous = folded[adapterId] ?: continue
                        val sourceId = json.optString("sourceId")
                        if (sourceId.isBlank() || sourceId in previous.taintedBy) continue
                        folded[adapterId] = previous.copy(
                            state = AdapterState.TAINTED,
                            taintedBy = previous.taintedBy + sourceId
                        )
                    }
                }
            }
        }
        return folded
    }

    private fun append(json: JSONObject) {
        file.parentFile?.mkdirs()
        file.appendText("$json\n", Charsets.UTF_8)
    }
}
