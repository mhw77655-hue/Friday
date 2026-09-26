package com.jarvis.app.memory.provenance

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.body.MemoryType
import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.memory.FakeMemoryGraphStore
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.model.AdmissionDecision
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.model.ModelBackend
import com.jarvis.app.model.ModelBackendConfig
import com.jarvis.app.model.ModelHandle
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.ModelProviderType
import com.jarvis.app.model.OrganWakeRequest
import com.jarvis.app.model.ResourceGovernor
import com.jarvis.app.termux.TermuxJarvisServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections

/**
 * ADAPTER-MANIFEST — a model adapter is the one derived artifact that leaves
 * memory as raw weights, so "what was it trained from" has to be provable. A
 * future training run must never fold in a memory that cannot be traced back,
 * and forgetting a source memory must stop the adapter that carries it.
 *
 * No adapter training exists in the codebase and none is added here: the adapter
 * files are byte blobs, and what is under test is the CONTRACT around them.
 *
 * AC1/AC3 are driven through the REAL conversation composition
 * (TermuxJarvisServer → LatencyPipeline → CognitiveEngine.handleMemoryDirective
 * → MemoryForgetter → ProvenanceLedger → AdapterManifest), so the taint is caused
 * by a user saying "forget …", not by a direct call into the manifest. AC2 is the
 * file-reload proof, AC4 is the real ModelManager load seam with a real
 * ModelBackend that reads the blob it is asked to load, and AC5 lives in
 * SystemGraphTest.
 */
class AdapterManifestTest {

    private val servers = mutableListOf<TermuxJarvisServer>()
    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private companion object {
        const val ADAPTER_A = "adapter-a"
        const val ADAPTER_B = "adapter-b"

        /** Six distinct source memories, each addressable by its own id. */
        val SOURCES: List<Pair<String, String>> = listOf(
            "M1" to "Sara's locker code is 4471",
            "M2" to "Omar's bike lock code is 8823",
            "M3" to "Nadia's gym door code is 9154",
            "M4" to "Tarek's wifi phrase is quiet river",
            "M5" to "Layla's safe combination is 6308",
            "M6" to "Hana's gate keypad is 2745"
        )

        val A_SOURCES = SOURCES.take(5).map { it.first }
        val B_SOURCES = listOf("M6")
    }

    private fun contentOf(id: String): String =
        SOURCES.first { it.first == id }.second

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "jarvis-adapters-${System.nanoTime()}")
            .apply { mkdirs() }
            .also { tempDirs.add(it) }

    /** An "adapter file": bytes, so a load is a real read of a real file. */
    private fun adapterBlob(dir: File, adapterId: String, seed: Int): File =
        File(dir, "$adapterId.blob").apply {
            writeBytes(ByteArray(64) { ((it + seed) % 251).toByte() })
        }

    /**
     * The real conversation path's memory store: six memories with ids the test
     * controls, so a forget turn resolves a concrete id the way the engine's own
     * lookup does (first memory whose content contains the forget fragment).
     */
    private fun memoryStoreWithAllSources(): MemoryStorePort = object : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = SOURCES.map { (id, content) ->
            MemoryItem(
                id = id,
                type = MemoryType.FACT,
                content = content,
                timestamp = 1000L,
                tags = listOf("fixture"),
                relevance = 0.9f
            )
        }
    }

    private fun recordingPipeline(server: TermuxJarvisServer): LatencyPipeline = LatencyPipeline(
        dispatch = { it() },
        scheduleDelayed = { _, _ -> },
        bridgeSend = { },
        bridgeStatus = { server.modelManager.status.value },
        beginExchange = { _, _ -> null },
        express = { reply, _ -> StyledResponse.Approved(reply) },
        completeExchange = { _, _, _, _, _ -> },
        sessionContext = { null },
        cognitiveEngine = server.engine
    )

    private class Fixture(
        val dir: File,
        val ledger: JsonlProvenanceLedger,
        val tombstones: JsonlTombstoneStore,
        val manifest: JsonlAdapterManifest,
        val graph: MemoryGraphStore,
        val forgetter: MemoryForgetter,
        val server: TermuxJarvisServer
    )

    private fun fixture(registerAdapters: Boolean = true): Fixture {
        val dir = tempDir()
        val ledger = JsonlProvenanceLedger(File(dir, "provenance.jsonl"))
        val tombstones = JsonlTombstoneStore(File(dir, "tombstones.jsonl"))
        val graph = FakeMemoryGraphStore()
        val manifest = JsonlAdapterManifest(
            file = File(dir, "adapters.jsonl"),
            provenanceLedger = ledger,
            tombstoneStore = tombstones
        )
        if (registerAdapters) {
            manifest.register(ADAPTER_A, A_SOURCES)
            manifest.register(ADAPTER_B, B_SOURCES)
        }
        val forgetter = MemoryForgetter(
            tombstoneStore = tombstones,
            ledger = ledger,
            graphStore = graph,
            adapterManifest = manifest
        )
        val server = TermuxJarvisServer(
            port = 0,
            backendOverride = FakeModelBackend(),
            memoryStoreOverride = memoryStoreWithAllSources(),
            graphStoreOverride = graph,
            memoryForgetter = forgetter
        ).also { servers.add(it) }
        return Fixture(dir, ledger, tombstones, manifest, graph, forgetter, server)
    }

    // ── AC1 ───────────────────────────────────────────────────────────────────

    /**
     * AC1: adapter A (from M1..M5) and adapter B (from M6) are registered, M3 is
     * forgotten through the real conversation path, and ONLY A is affected: it is
     * TAINTED, mayLoad is false, and its retrain plan is exactly the four sources
     * that survive — while B stays ACTIVE and loadable.
     */
    @Test
    fun `AC1 forgetting a source memory taints only the adapter trained from it`() = runBlocking {
        val f = fixture()

        // The registration itself is a provenance record, so the forget organ can
        // find the adapter by asking the ledger which artifacts derive from M3.
        assertEquals(
            "A's registration is an ADAPTER_BATCH in the ledger",
            setOf("M1", "M2", "M3", "M4", "M5"),
            f.ledger.sourcesOf(ADAPTER_A)
        )
        assertEquals(
            ProvenanceKind.ADAPTER_BATCH,
            f.ledger.derivedFrom("M3").single().kind
        )
        assertEquals(AdapterState.ACTIVE, f.manifest.state(ADAPTER_A))
        assertTrue("a fresh adapter may load", f.manifest.mayLoad(ADAPTER_A))

        val pipeline = recordingPipeline(f.server)
        val forgotten = contentOf("M3")
        pipeline.onUserInput("forget $forgotten")

        // The turn really forgot: a tombstone exists for the resolved id.
        assertEquals("the real forget turn tombstoned M3", 1L, f.tombstones.count())
        assertTrue("M3 is tombstoned by id", f.tombstones.isTombstoned("M3"))

        assertEquals("A is TAINTED by M3", AdapterState.TAINTED, f.manifest.state(ADAPTER_A))
        assertFalse("a tainted adapter may not load", f.manifest.mayLoad(ADAPTER_A))
        assertEquals(
            "the retrain plan is exactly the sources that survive",
            listOf("M1", "M2", "M4", "M5"),
            f.manifest.retrainPlan(ADAPTER_A)
        )
        assertEquals(
            "the taint names the forgotten source",
            listOf("M3"),
            f.manifest.registrations().single { it.adapterId == ADAPTER_A }.taintedBy
        )

        assertEquals("B is untouched", AdapterState.ACTIVE, f.manifest.state(ADAPTER_B))
        assertTrue("B may still load", f.manifest.mayLoad(ADAPTER_B))
        assertEquals("B's plan is unchanged", listOf("M6"), f.manifest.retrainPlan(ADAPTER_B))
    }

    // ── AC2 ───────────────────────────────────────────────────────────────────

    /**
     * AC2: the state is a durable file fact, not an in-memory field. A manifest
     * rebuilt from the same file — a fresh object, as after a process restart —
     * reports the same taint, the same load refusal and the same retrain plan.
     */
    @Test
    fun `AC2 manifest state survives a reload from its file`() = runBlocking {
        val f = fixture()
        recordingPipeline(f.server).onUserInput("forget ${contentOf("M3")}")
        assertEquals(AdapterState.TAINTED, f.manifest.state(ADAPTER_A))

        val reloaded = JsonlAdapterManifest(
            file = f.manifest.manifestFile!!,
            provenanceLedger = JsonlProvenanceLedger(File(f.dir, "provenance.jsonl")),
            tombstoneStore = JsonlTombstoneStore(File(f.dir, "tombstones.jsonl"))
        )

        assertEquals(AdapterState.TAINTED, reloaded.state(ADAPTER_A))
        assertFalse("a reloaded manifest still refuses a tainted adapter", reloaded.mayLoad(ADAPTER_A))
        assertEquals(
            "the reloaded plan is the same surviving set",
            listOf("M1", "M2", "M4", "M5"),
            reloaded.retrainPlan(ADAPTER_A)
        )
        assertEquals(AdapterState.ACTIVE, reloaded.state(ADAPTER_B))
        assertTrue(reloaded.mayLoad(ADAPTER_B))
        assertEquals(
            "both registrations survive the reload",
            listOf(ADAPTER_A, ADAPTER_B),
            reloaded.registrations().map { it.adapterId }
        )

        // The file is a byte-scan surface that must not carry memory content.
        val persisted = reloaded.manifestFile!!.readText()
        assertTrue("ids only, never content", persisted.contains("M3"))
        assertFalse("no memory plaintext in the manifest", persisted.contains(contentOf("M3")))
    }

    // ── AC3 ───────────────────────────────────────────────────────────────────

    /**
     * AC3 (negative control): with propagation disabled the same real forget turn
     * is accepted but changes nothing, so A stays ACTIVE and still loadable. The
     * fixture is byte-for-byte the AC1 fixture with one flag flipped, so a
     * manifest that tainted unconditionally would fail here.
     */
    @Test
    fun `AC3 with propagation disabled the adapter stays ACTIVE`() = runBlocking {
        val f = fixture()
        f.forgetter.setPropagationEnabled(false)

        recordingPipeline(f.server).onUserInput("forget ${contentOf("M3")}")

        assertEquals("nothing was forgotten", 0L, f.tombstones.count())
        assertEquals(AdapterState.ACTIVE, f.manifest.state(ADAPTER_A))
        assertTrue("an untainted adapter may load", f.manifest.mayLoad(ADAPTER_A))
        assertEquals(
            "the plan still names every source, including the un-forgotten M3",
            A_SOURCES,
            f.manifest.retrainPlan(ADAPTER_A)
        )
    }

    // ── AC4 ───────────────────────────────────────────────────────────────────

    /**
     * A backend that actually reads the adapter file it is asked to load, so a
     * refused load is provable by the absence of those bytes ever being read.
     */
    private class BlobBackend(private val files: Map<String, File>) : ModelBackend {
        val loadedBytes = Collections.synchronizedList(mutableListOf<ByteArray>())
        private var counter = 0L

        override suspend fun loadModel(config: ModelBackendConfig): ModelHandle {
            val file = files[config.modelId] ?: error("no adapter file for ${config.modelId}")
            loadedBytes.add(file.readBytes())
            return ModelHandle(id = ++counter, modelId = config.modelId)
        }

        override suspend fun generate(handle: ModelHandle, prompt: String): String = ""

        override suspend fun unloadModel(handle: ModelHandle) = Unit

        override fun isLoaded(handle: ModelHandle): Boolean = handle.id <= counter
    }

    /** Always admits, so the load reaches the gate deterministically. */
    private class AllowGovernor : ResourceGovernor() {
        override fun admit(request: OrganWakeRequest): AdmissionDecision = AdmissionDecision.ALLOW
    }

    /**
     * A manager per scenario. ModelManager caches ONE handle per TIER, so a
     * second adapter load through the same manager is served from that cache and
     * would never reach the load path at all — a fresh manager is what makes
     * every assertion below an assertion about the load seam.
     */
    private fun managerFor(gate: AdapterManifest, backend: ModelBackend): ModelManager = ModelManager(
        context = null,
        scope = CoroutineScope(Dispatchers.Default),
        backend = backend,
        resourceGovernor = AllowGovernor(),
        adapterLoadGate = gate,
        clock = { 0L },
        autoSweep = false
    )

    /**
     * AC4: the refusal happens at the REAL seam — ModelManager, the one place
     * every model load funnels — not inside the manifest. A TAINTED adapter
     * never reaches the backend (its blob is never read) and the manager reports
     * a failure; an ACTIVE adapter loads normally and its bytes are read.
     */
    @Test
    fun `AC4 the real load seam refuses a tainted adapter and loads an active one`() = runBlocking {
        val f = fixture()
        val dir = tempDir()
        val blobA = adapterBlob(dir, ADAPTER_A, seed = 1)
        val blobB = adapterBlob(dir, ADAPTER_B, seed = 2)
        assertNotEquals(
            "two adapters must not share bytes, or the refusal is unprovable",
            blobA.readBytes().toList(),
            blobB.readBytes().toList()
        )
        val backend = BlobBackend(mapOf(ADAPTER_A to blobA, ADAPTER_B to blobB))

        // Before the forget, the ACTIVE adapter loads and its bytes are read: the
        // gate is a provenance check, not a blanket block on adapters.
        assertTrue(
            "an active adapter loads through the real seam",
            managerFor(f.manifest, backend).loadModel(ModelProviderType.LLAMA_CPP, ADAPTER_B).isSuccess
        )
        assertTrue(
            "the active adapter's bytes were really read",
            backend.loadedBytes.any { it.contentEquals(blobB.readBytes()) }
        )

        recordingPipeline(f.server).onUserInput("forget ${contentOf("M3")}")
        backend.loadedBytes.clear()

        val refusedManager = managerFor(f.manifest, backend)
        val refused = refusedManager.loadModel(ModelProviderType.LLAMA_CPP, ADAPTER_A)
        assertTrue("the load seam refuses a tainted adapter", refused.isFailure)
        assertTrue(
            "the refusal names the taint",
            refused.exceptionOrNull()?.message?.contains("TAINTED") == true
        )
        assertTrue(
            "the tainted adapter's bytes were never read by the backend",
            backend.loadedBytes.none { it.contentEquals(blobA.readBytes()) }
        )
        assertTrue(
            "a refused load leaves nothing cached to serve later",
            refusedManager.loadModel(ModelProviderType.LLAMA_CPP, ADAPTER_A).isFailure
        )
        assertTrue(
            "the adapter trained without the forgotten memory still loads",
            managerFor(f.manifest, backend).loadModel(ModelProviderType.LLAMA_CPP, ADAPTER_B).isSuccess
        )

        // A retrain that re-registers only the surviving sources is the one path
        // back to loadable, and the re-registered bytes are the ones loaded.
        f.manifest.register(ADAPTER_A, f.manifest.retrainPlan(ADAPTER_A))
        assertEquals(AdapterState.ACTIVE, f.manifest.state(ADAPTER_A))
        assertTrue(
            "a re-registered adapter loads once its plan excludes the forgotten memory",
            managerFor(f.manifest, backend).loadModel(ModelProviderType.LLAMA_CPP, ADAPTER_A).isSuccess
        )
        assertTrue(
            "the re-registered adapter's bytes were read",
            backend.loadedBytes.any { it.contentEquals(blobA.readBytes()) }
        )
    }
}
