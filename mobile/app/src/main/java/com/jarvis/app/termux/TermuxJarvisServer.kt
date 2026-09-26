package com.jarvis.app.termux

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.capability.CapabilityRegistry
import com.jarvis.app.capability.DeterministicCapabilityRouter
import com.jarvis.app.cloud.CloudModelRouter
import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.cognitive.capability.CapabilityFabric
import com.jarvis.app.cognitive.immune.ImmuneSystem
import com.jarvis.app.env.ModelSource
import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.identity.HumanCoreIdentitySource
import com.jarvis.app.identity.IdentityContext
import com.jarvis.app.identity.PersonaTuner
import com.jarvis.app.identity.SelfModel
import com.jarvis.app.identity.StageHistorySource
import com.jarvis.app.identity.UserMentalStateEstimator
import com.jarvis.app.identity.UserProfile
import com.jarvis.app.identity.WorldModelService
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.memory.EmbeddingProvider
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.MemoryImportanceScorer
import com.jarvis.app.memory.MemoryNode
import com.jarvis.app.memory.SignalProfile
import com.jarvis.app.memory.SignalSplitScorer
import com.jarvis.app.memory.withSignals
import com.jarvis.app.model.CognitiveAdmissionPolicy
import com.jarvis.app.model.ModelBackend
import com.jarvis.app.model.ModelHandle
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.OllamaModelBackend
import com.jarvis.app.model.OrganRole
import com.jarvis.app.model.ResourceGovernor
import com.jarvis.app.model.adapters.OllamaAdapter
import com.jarvis.app.model.config.ProviderConfig
import com.jarvis.app.resolution.CapabilityMemoryIndex
import com.jarvis.app.resolution.FuzzyCommandResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * JARVIS TERMUX LIVE INSTANCE — the real production conversation composition
 * behind a minimal Termux-native HTTP server.
 *
 * This is the same composition root the Android app uses (real
 * [CognitiveEngine] + real [LatencyPipeline] + real [ModelManager]), mirrored
 * into a standalone JVM process so a plain browser on localhost can run a real
 * conversation against the resident organ.
 *
 * JVM-safe doubles living IN this file (never test classes, never
 * Android-bound):
 *  - [InMemoryGraph] — pure-Kotlin [MemoryGraphStore] (the production store,
 *    AndroidMemoryGraphStore, is android.sqlite and cannot load in Termux)
 *  - [DeterministicEmbedding] — dense seeded [EmbeddingProvider] (production
 *    NeuralEmbeddingProvider runs the neural forward pass)
 *
 * The HTTP layer uses only [ServerSocket]/[Socket] (present in BOTH the
 * Android SDK and the JVM), so this file compiles against android.jar yet runs
 * unmodified on the Termux JVM.
 *
 * The model backend is real [OllamaModelBackend] unless [backendOverride] is
 * supplied (tests inject the in-memory FakeModelBackend).
 *
 * VOICE-FORGE-EGYPTIAN-KAREN-TTS: the spoken-reply VoiceForge backend is real
 * [com.jarvis.app.voice.VoiceForgeAdapter] unless [voiceForgeSynthesizerOverride]
 * is supplied (tests swap ONLY the final HTTP hop with a recording fake — every
 * other object on the drive path is the real production class).
 */
class TermuxJarvisServer(
    private val port: Int = 8081,
    private val ollamaHost: String = "127.0.0.1:8080",
    private val modelId: String = "jarvis-resident:latest",
    private val backendOverride: ModelBackend? = null,
    private val voiceForgeSynthesizerOverride: com.jarvis.app.voice.VoiceForgeSynthesizer? = null,
    private val dataDir: java.io.File = java.io.File(System.getProperty("java.io.tmpdir"), "jarvis-termux"),

    // TURN-TRACE (Gate 3a): optional append-only local trace store. Null ⇒ no
    // tracing (negative control: the trace file provably does not grow). Tests
    // inject a temp-file JsonlTurnTraceStore; Termux/normal runs leave it null.
    private val turnTraceStore: com.jarvis.app.trace.TurnTraceStore? = null,

    // THREAD-OBJECTS (Gate 3a, priority 2): optional per-conversation open-thread
    // registry, exposed so a harness can read openThreads()/toggle enabled. Null
    // ⇒ no thread tracking (AC7 negative control: with the tracker disabled, the
    // three_thoughts_test assertions fail). Tests inject a real ThreadTracker;
    // Termux/normal runs leave it null.
    val threadTracker: com.jarvis.app.threads.ThreadTracker? = null,

    // PROVENANCE-LEDGER: optional durable local provenance ledger. Null ⇒ no
    // provenance recording (negative control: derivedFrom stays empty). Tests
    // inject a temp-file JsonlProvenanceLedger; Termux/normal runs leave it null.
    val provenanceLedger: com.jarvis.app.memory.provenance.ProvenanceLedger? = null,

    // PROVENANCE-LEDGER (AC2): optional memory-store override so a REAL turn can
    // actually retrieve a fixture memory (the production default is the empty
    // store). Null ⇒ the existing EmptyMemoryStore behavior byte-for-byte.
    val memoryStoreOverride: MemoryStorePort? = null,

    // FORGET-PROPAGATION: optional JVM reference memory graph store for the
    // engine's write-back path, so a forget test can share ONE graph store with
    // the daemon and the forgetter. Null ⇒ the existing InMemoryGraph behavior
    // byte-for-byte.
    val graphStoreOverride: MemoryGraphStore? = null,

    // FORGET-PROPAGATION: optional forget propagation engine. Null ⇒ the
    // pre-forget conversation path byte-for-byte. Tests inject a real
    // MemoryForgetter; Termux/normal runs leave it null.
    val memoryForgetter: com.jarvis.app.memory.provenance.MemoryForgetter? = null
) {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workers: ExecutorService? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Human Core: the identity source behind HumanCoreIdentitySource/SelfModel.
    // Initialized the same way JarvisEngine.init does — over the real singleton
    // with real storage in this server's data directory — so the identity the
    // DIRECT_REPLY payload carries is the REAL HumanCore identity (cold-start
    // "JARVIS" baseline, or any durable revision) and never the software
    // "unknown v0" fallback SelfModel reports while HumanCore is uninitialized.
    init {
        if (!HumanCore.isInitialized()) {
            HumanCore.init(storage = FileStorage(dataDir))
        }
    }

    // ── JVM-safe doubles (disclosed, main-source, never Android-bound) ──

    class InMemoryGraph : MemoryGraphStore {
        private val nodes = mutableListOf<MemoryNode>()
        private var idCounter = 0
        override fun addFact(subject: String, predicate: String, `object`: String, source: String): String {
            val now = System.currentTimeMillis()
            val id = "termux-${++idCounter}"
            for (i in nodes.indices) {
                if (nodes[i].subject == subject && nodes[i].predicate == predicate && nodes[i].validUntil == null) {
                    nodes[i] = nodes[i].copy(validUntil = now, supersededBy = id)
                }
            }
            nodes.add(
                MemoryNode(
                    id = id, subject = subject, predicate = predicate,
                    `object` = `object`, source = source, validFrom = now
                )
            )
            return id
        }
        override fun query(subject: String?, predicate: String?, asOfTime: Long): List<MemoryNode> =
            nodes.filter { n ->
                (subject == null || n.subject == subject) &&
                    (predicate == null || n.predicate == predicate) &&
                    n.validFrom <= asOfTime && (n.validUntil == null || n.validUntil > asOfTime)
            }
        override fun getHistory(subject: String, predicate: String): List<MemoryNode> =
            nodes.filter { it.subject == subject && it.predicate == predicate }.sortedBy { it.validFrom }
        // CORRECTION-CHAIN: same field-by-field signal persistence as the
        // Android store — the six signals are stored separately, never blended.
        override fun recordSignals(id: String, profile: SignalProfile): Boolean {
            val idx = nodes.indexOfFirst { it.id == id }
            if (idx < 0) return false
            nodes[idx] = nodes[idx].withSignals(profile)
            return true
        }
        // Accessibility is its own seam: a consolidation pass moves this axis and
        // physically cannot reach a stored signal through it.
        override fun setAccessibility(id: String, accessibility: Float): Boolean {
            val idx = nodes.indexOfFirst { it.id == id }
            if (idx < 0) return false
            nodes[idx] = nodes[idx].copy(accessibility = accessibility)
            return true
        }
        // FORGET-PROPAGATION: expire (supersede, never delete) every currently-
        // valid node whose subject/object carries the forgotten content — matching
        // the store's decay-not-delete principle and the row-count-never-decreases
        // assertion the Android store keeps.
        override fun removeContaining(text: String): Int {
            val now = System.currentTimeMillis()
            val needle = text.lowercase()
            var count = 0
            for (i in nodes.indices) {
                val n = nodes[i]
                if (n.validUntil == null &&
                    (n.subject.lowercase().contains(needle) || n.`object`.lowercase().contains(needle))
                ) {
                    nodes[i] = n.copy(validUntil = now)
                    count++
                }
            }
            return count
        }
        override fun nodeCount(): Long = nodes.size.toLong()
        override fun close() {}
    }

    class DeterministicEmbedding(
        override val dimension: Int = 256,
        private val seed: Int = 0x9E3779B9.toInt()
    ) : EmbeddingProvider {
        override val modelId: String = "jarvis-termux-embed-v1:d=$dimension"
        override fun embed(text: String): FloatArray {
            val vec = FloatArray(dimension)
            val seen = HashSet<String>()
            for (t in text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }) {
                if (t in seen) continue
                val rng = kotlin.random.Random(seed xor t.hashCode())
                for (d in 0 until dimension) {
                    vec[d] += if (rng.nextBoolean()) 1f else -1f
                }
            }
            var norm = 0f
            for (x in vec) norm += x * x
            norm = kotlin.math.sqrt(norm)
            if (norm > 0f) for (i in vec.indices) vec[i] /= norm
            return vec
        }
    }

    private class EmptyMemoryStore : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }

    // ── Identity, memory, capability (real subsystems, JVM-safe stores) ──

    val graphStore: MemoryGraphStore = graphStoreOverride ?: InMemoryGraph()
    private val embeddingProvider = DeterministicEmbedding(dimension = 256)
    private val scorer = MemoryImportanceScorer(embeddingProvider = embeddingProvider)
    val retriever = BlendedMemoryRetriever(graphStore, embeddingProvider, scorer)
    // CORRECTION-CHAIN: the six split signals are computed ONCE, where a fact is
    // stored (the engine write-back), over this same real embedding provider.
    private val signalScorer = SignalSplitScorer(embeddingProvider)

    val worldModel = WorldModelService(graphStore, retriever)
    val userProfile = UserProfile(worldModel)
    val mentalStateEstimator = UserMentalStateEstimator()
    val registry = CapabilityRegistry()
    val selfModel = SelfModel(
        identitySource = HumanCoreIdentitySource(),
        capabilityRegistry = registry,
        stageHistory = StageHistorySource { emptyList() }
    )
    val personaTuner = PersonaTuner(worldModel)
    // PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL: the JVM mirror
    // of the JarvisEngine.init construction point — the real social stack over
    // the same graph store + world-model seam, wired into IdentityContext.
    val personRelationshipModel = com.jarvis.app.social.PersonRelationshipModel(graphStore, worldModel)
    val confidentialityFirewall = com.jarvis.app.social.ConfidentialityFirewall(graphStore, worldModel)
    val identityContext = IdentityContext(
        worldModel = worldModel,
        userProfile = userProfile,
        mentalStateEstimator = mentalStateEstimator,
        selfModel = selfModel,
        personaTuner = personaTuner,
        personRelationshipModel = personRelationshipModel,
        confidentialityFirewall = confidentialityFirewall
    )
    val dialectDetector = com.jarvis.app.language.EgyptianArabicDialectDetector()

    // CONTINUITY-GATE-ENFORCED-SEAM (AC2): the JVM mirror of the JarvisEngine.init
    // construction point — the same typed ContinuityGate registry built at the
    // SAME composition point as the identity/emotion/social stack it guards, with
    // every organ contributing its real per-turn signal. The generation payload
    // is assembled ONLY from ContinuityGate.Snapshot.
    val continuityGate = com.jarvis.app.continuity.ContinuityGate(
        dialectDetector = dialectDetector,
        personRelationshipModel = personRelationshipModel,
        confidentialityFirewall = confidentialityFirewall,
        blendedRetriever = retriever,
        mentalStateEstimator = mentalStateEstimator
    )

    private val failureSurface = FailureSurface()
    private val immune = ImmuneSystem(surface = failureSurface)
    val capabilityFabric = CapabilityFabric(immune)
    private val capabilityRouter = DeterministicCapabilityRouter(registry)
    val fuzzyCommandResolver = FuzzyCommandResolver(retriever, capabilityRouter, registry)
    val capabilityMemoryIndex = CapabilityMemoryIndex(graphStore)

    val cloudModelRouter = CloudModelRouter(emptyList())

    // ── Model: real Ollama backend + the single ModelManager loading authority ──

    private val ollamaAdapter = OllamaAdapter(null).apply {
        configure(
            ModelSource.Local(ollamaHost),
            ProviderConfig(modelId = modelId)
        )
    }
    val resolvedBackend: ModelBackend = backendOverride ?: OllamaModelBackend(ollamaAdapter)
    private val governor = ResourceGovernor()
    val modelManager = ModelManager(
        context = null,
        scope = scope,
        backend = resolvedBackend,
        resourceGovernor = governor
    )
    private val policy = CognitiveAdmissionPolicy()

    // VOICE-FORGE-EGYPTIAN-KAREN-TTS (AC2) + VOICE-FORGE-ACTIVATION-REAL-SPEECH
    // (AC3/AC7): the spoken-reply VoiceForge backend constructed on the JVM
    // composition root exactly like the engine builds it (real adapter by default;
    // the recording fake only at the final HTTP hop). audioPromptPath wires the
    // voice reference clip; authToken carries the shared-secret for AC7 auth gate.
    private val voiceForgeAdapter: com.jarvis.app.voice.VoiceForgeSynthesizer =
        voiceForgeSynthesizerOverride ?: com.jarvis.app.voice.VoiceForgeAdapter(null).also {
            it.configure(
                com.jarvis.app.voice.VoiceForgeConfig(
                    authToken = com.jarvis.app.voice.VoiceForgeConfig.DEFAULT_AUTH_TOKEN
                )
            )
        }
    val voiceForgeBackend = com.jarvis.app.voice.VoiceForgeBackend(
        synthesizer = voiceForgeAdapter,
        audioPromptPath = com.jarvis.app.voice.VoiceForgeConfig.DEFAULT_ASSET_PATH
    )

    // BUILD-TWIN-ARM64-VERIFICATION: the real ARM64 verification twin gate,
    // constructed on the JVM composition root exactly like the other real model
    // subsystems. The voiceforge health probe is a real HTTP GET to /health on
    // the configured host (the device itself when voiceforge_server.py runs
    // locally); any GGUF step command runs via LocalTwinCommandRunner. The
    // verifier decides whether a hardware-sensitive build artifact is
    // authorized to be copied to the phone (healthy:true + exit-0 only).
    val buildTwinVerifier = com.jarvis.app.buildtwin.BuildTwinVerifier(
        spec = com.jarvis.app.buildtwin.BuildTwinSpec(
            host = "127.0.0.1",
            healthPort = 8765
        ),
        probe = com.jarvis.app.buildtwin.HttpTwinHealthProbe(),
        runner = com.jarvis.app.buildtwin.LocalTwinCommandRunner()
    )

    val engine = CognitiveEngine(
        scope = scope,
        memoryStore = memoryStoreOverride ?: EmptyMemoryStore(),
        humanCore = HumanCore,
        modelManager = modelManager,
        cognitiveAdmissionPolicy = policy,
        blendedRetriever = retriever,
        graphStore = graphStore,
        identityContext = identityContext,
        continuityGate = continuityGate,
        capabilityFabric = capabilityFabric,
        dialectDetector = dialectDetector,
        turnTraceStore = turnTraceStore,
        threadTracker = threadTracker,
        provenanceLedger = provenanceLedger,
        memoryForgetter = memoryForgetter,
        signalScorer = signalScorer
    )

    val pipeline = LatencyPipeline(
        dispatch = { it() },
        scheduleDelayed = { _, _ -> },
        bridgeSend = { text -> modelManager.send(text) },
        bridgeStatus = { modelManager.status.value },
        beginExchange = { _, _ -> null },
        express = { reply, _ -> com.jarvis.app.humancore.protocol.StyledResponse.Approved(reply) },
        completeExchange = { _, _, _, _, _ -> },
        sessionContext = { null },
        cognitiveEngine = engine
    )

    fun bootstrap(): ModelHandle = runBlocking {
        modelManager.request(OrganRole.RESIDENT, task = "termux-bootstrap")
    }

    fun useRealProvider() {
        if (backendOverride != null) return
        runBlocking {
            modelManager.switchProvider(
                ModelProviderType.OLLAMA,
                ModelSource.Local(ollamaHost),
                ProviderConfig(modelId = modelId)
            )
        }
    }

    // ── Minimal HTTP/1.1 server (ServerSocket only — Android + JVM safe) ──

    fun start() {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress("0.0.0.0", port))
        serverSocket = socket
        workers = Executors.newCachedThreadPool()
        acceptThread = Thread {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    workers?.execute { handle(client) }
                } catch (e: IOException) {
                    if (!socket.isClosed) break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        acceptThread?.let { t ->
            serverSocket?.close()
            t.interrupt()
        }
        workers?.shutdownNow()
        acceptThread = null
        workers = null
        serverSocket = null
    }

    fun isRunning(): Boolean = serverSocket?.isClosed?.not() == true

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 30_000
            val input = client.getInputStream()
            val requestLine = readLine(input)
            if (requestLine == null || requestLine.isBlank()) {
                client.close()
                return
            }
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                respond(client, 400, "text/plain", "Bad request")
                return
            }
            val method = parts[0]
            val path = parts[1]

            // Consume headers, discover Content-Length.
            var contentLength = 0
            while (true) {
                val line = readLine(input) ?: break
                if (line.isBlank()) break
                if (line.startsWith("Content-Length:", true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val body = if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(buf, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read, StandardCharsets.UTF_8)
            } else {
                ""
            }

            when {
                method == "GET" && path == "/" -> respond(client, 200, "text/html; charset=utf-8", CHAT_HTML)
                method == "GET" && path == "/api/health" ->
                    respond(client, 200, "application/json", """{"status":"ok","model":"$modelId","server":"termux"}""")
                method == "POST" && path == "/api/chat" -> handleChat(client, body)
                else -> respond(client, 404, "text/plain", "Not found")
            }
        } catch (_: IOException) {
            // client disconnected or timed out — nothing to surface
        } finally {
            try {
                client.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun handleChat(client: Socket, body: String) {
        val message = extractMessage(body)
        if (message.isNullOrBlank()) {
            respond(client, 400, "application/json", """{"error":"Missing 'message' field"}""")
            return
        }
        try {
            val reply = processMessage(message)
            val escaped = reply.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            respond(client, 200, "application/json", """{"reply":"$escaped"}""")
        } catch (e: Exception) {
            val err = e.message?.replace("\\", "\\\\")?.replace("\"", "\\\"") ?: "unknown error"
            respond(client, 500, "application/json", """{"error":"$err"}""")
        }
    }

    private fun processMessage(message: String): String {
        pipeline.onUserInput(message)
        return runBlocking {
            withTimeout(120_000) {
                while (modelManager.lastReply.value.isBlank()) {
                    delay(200)
                }
                modelManager.lastReply.value
            }
        }
    }

    /** Read one CRLF/LF line as bytes without over-reading the stream. */
    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
        }
        return out.toString("UTF-8")
    }

    private fun respond(client: Socket, code: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
            500 -> "Internal Server Error"; else -> "Error"
        }
        val header = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n" +
            "\r\n"
        val out: OutputStream = client.getOutputStream()
        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun extractMessage(json: String): String? {
        val key = "\"message\""
        val idx = json.indexOf(key)
        if (idx < 0) return null
        val after = json.substring(idx + key.length).trimStart()
        if (!after.startsWith(':')) return null
        val valueStart = after.indexOf('"', 1)
        if (valueStart < 0) return null
        val valueEnd = after.indexOf('"', valueStart + 1)
        if (valueEnd < 0) return null
        return after.substring(valueStart + 1, valueEnd)
    }

    private companion object {
        const val CHAT_HTML = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>JARVIS</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:monospace;background:#0a0a0a;color:#0f0;display:flex;flex-direction:column;height:100vh}
#messages{flex:1;overflow-y:auto;padding:16px}
.msg{margin:8px 0;padding:8px 12px;border-radius:4px;max-width:80%}
.user{background:#1a1a2e;color:#4fc3f7;align-self:flex-end;margin-left:auto}
.jarvis{background:#0d1117;color:#0f0;border:1px solid #1a3a1a}
#input{display:flex;padding:8px;background:#111}
#input input{flex:1;background:#1a1a1a;color:#0f0;border:1px solid #333;padding:8px 12px;font-family:monospace;font-size:14px}
#input button{background:#1a3a1a;color:#0f0;border:1px solid #0f0;padding:8px 16px;cursor:pointer;font-family:monospace}
#input button:hover{background:#0f0;color:#000}
#status{padding:4px 16px;background:#111;color:#666;font-size:12px;border-top:1px solid #222}
</style>
</head>
<body>
<div id="messages"></div>
<div id="input">
<input type="text" id="msg" placeholder="Type a message..." autofocus>
<button onclick="send()">Send</button>
</div>
<div id="status">Connected to JARVIS Termux</div>
<script>
const msgs=document.getElementById('messages');
const input=document.getElementById('msg');
const status=document.getElementById('status');
input.addEventListener('keydown',e=>{if(e.key==='Enter')send()});
function addMsg(text,cls){const d=document.createElement('div');d.className='msg '+cls;d.textContent=text;msgs.appendChild(d);msgs.scrollTop=msgs.scrollHeight}
async function send(){
const text=input.value.trim();if(!text)return;
input.value='';addMsg(text,'user');
status.textContent='Thinking...';
try{const r=await fetch('/api/chat',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({message:text})});
const d=await r.json();if(d.reply){addMsg(d.reply,'jarvis')}else if(d.error){addMsg('Error: '+d.error,'jarvis')}}
catch(e){addMsg('Connection error','jarvis')}
status.textContent='Connected to JARVIS Termux'}
</script>
</body>
</html>"""

        @JvmStatic
        fun main(args: Array<String>) {
            val port = args.firstOrNull { it.startsWith("--port=") }
                ?.substringAfter("=")?.toIntOrNull() ?: 8081
            val host = args.firstOrNull { it.startsWith("--ollama=") }
                ?.substringAfter("=") ?: "127.0.0.1:8080"
            val model = args.firstOrNull { it.startsWith("--model=") }
                ?.substringAfter("=") ?: "jarvis-resident:latest"

            println("JARVIS Termux Server starting on port $port...")
            println("Ollama backend: $host (model: $model)")

            val server = TermuxJarvisServer(port = port, ollamaHost = host, modelId = model)
            server.useRealProvider()
            val handle = server.bootstrap()
            println("Resident organ loaded: ${handle.modelId}")
            server.start()
            println("Server running at http://localhost:$port")
            println("Press Ctrl+C to stop.")

            Runtime.getRuntime().addShutdownHook(Thread {
                println("\nShutting down...")
                server.stop()
            })

            Thread.currentThread().join()
        }
    }
}