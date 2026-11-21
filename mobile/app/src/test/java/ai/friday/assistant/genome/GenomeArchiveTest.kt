package ai.friday.assistant.genome

import ai.friday.assistant.humancore.store.FileStorage
import ai.friday.assistant.humancore.store.StoreKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Genome archive: persistent lineage storage.
 * Plain JVM (JUnit 4).
 */
class GenomeArchiveTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tmpDir(): File = File.createTempFile("genome-archive-test", "").apply {
        delete()
        mkdirs()
        tempDirs += this
    }

    private fun genome(id: String): Genome = GenomeBuilder(id)
        .capability("speech_to_text")
        .mutationOperator(MutationOperator.PARAMETER_MUTATION)
        .build()

    @Test
    fun `archive retains lineage through forks`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val archive = GenomeArchive(FileStorage(tmpDir()), scope)
            val root = genome("root")
            archive.put(root)

            val c1 = root.fork(MutationOperator.PARAMETER_MUTATION, "m1", "c1")
            archive.put(c1)
            val c2 = c1.fork(MutationOperator.ALGORITHM_MUTATION, "m2", "c2")
            archive.put(c2)

            assertEquals(3, archive.count())
            val lineage = archive.lineage("c2")
            // c2 → c1 → root
            assertEquals(3, lineage.size)
            assertEquals("c2", lineage[0].id)
            assertEquals("c1", lineage[1].id)
            assertEquals("root", lineage[2].id)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `archive persists to disk and reloads`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val dir = tmpDir()
        try {
            val archive = GenomeArchive(FileStorage(dir), scope)
            archive.put(genome("persisted"))
            // Give the async persistence a moment to write.
            Thread.sleep(200)
        } finally {
            scope.cancel()
        }

        val scope2 = CoroutineScope(Dispatchers.Default)
        try {
            val reloaded = GenomeArchive(FileStorage(dir), scope2)
            val loaded = reloaded.loadFromDisk()
            assertTrue(loaded >= 1)
            assertNotNull(reloaded.get("persisted"))
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun `genome archive never writes into the Human Core dialogue store`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        val storage = FileStorage(tmpDir())
        val dialogueRecord = """{"role":"user","content":"keep this conversation"}"""
        storage.append(StoreKind.DIALOGUE, dialogueRecord)
        val before = storage.read(StoreKind.DIALOGUE)

        try {
            val archive = GenomeArchive(storage, scope)
            archive.put(genome("isolated-genome"))
            Thread.sleep(200)

            assertEquals(before, storage.read(StoreKind.DIALOGUE))
            assertNotNull(storage.read(StoreKind.GENOME_ARCHIVE))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `archive round trips the complete genome contract and provenance`() = runBlocking {
        val dir = tmpDir()
        val scope = CoroutineScope(Dispatchers.Default)
        val original = GenomeBuilder("full-genome")
            .version(4)
            .parentLineage(listOf(Genome.ROOT_LINEAGE, "parent-genome"))
            .capability("custom_tool")
            .input("text", "String", nullable = false, desc = "source text")
            .output("result", "Number", desc = "computed value")
            .dependency("text-normalizer")
            .runtime(minSdk = 26, isolation = IsolationLevel.SEPARATE_PROCESS)
            .model("resident", "LLM", required = true, maxMemoryMb = 700)
            .tool("text-normalizer", required = true)
            .environment(network = true, permissions = setOf("INTERNET"))
            .commContract(inputTopics = setOf("tool.input"), outputTopics = setOf("tool.output"))
            .resourceBudget(maxMemoryMb = 320, maxCpuPercent = 24.0, priority = 3)
            .latencyBudget(maxFirstTokenMs = 1200, maxFullResponseMs = 8000)
            .safety(SafetyConstraint.USER_ACTION_REQUIRED)
            .test("known input", TestType.UNIT, "returns a stable value")
            .benchmark("latency", "ms", 100.0)
            .fitness("quality", 0.97)
            .mutationOperator(MutationOperator.ALGORITHM_MUTATION)
            .author("Friday")
            .sourceContext("generated from a verified capability gap")
            .build()
            .copy(
                runtimeRequirements = RuntimeRequirements(
                    minSdk = 26,
                    maxConcurrency = 2,
                    requiresMainThread = false,
                    requiresBackground = true,
                    isolation = IsolationLevel.SEPARATE_PROCESS
                ),
                modelRequirements = ModelRequirements(listOf(ModelSlot("resident", "LLM", "Q4", 700, true))),
                toolRequirements = listOf(ToolRequirement("text-normalizer", true)),
                environmentRequirements = EnvironmentRequirements(
                    networkAccess = true,
                    fileAccess = setOf("app-private"),
                    sensors = setOf("microphone"),
                    permissions = setOf("INTERNET"),
                    batteryMinPercent = 25,
                    thermalOk = false
                ),
                communicationContract = CommunicationContract(
                    inputTopics = setOf("tool.input"),
                    outputTopics = setOf("tool.output"),
                    requestTopics = setOf("tool.request"),
                    resultTopics = setOf("tool.result"),
                    confidenceThreshold = 0.8f,
                    priority = 2,
                    maxInflight = 3
                ),
                resourceBudget = ResourceBudget(320, 24.0, 55.0, 3, true, 700),
                latencyBudget = LatencyBudget(1200, 8000, 900),
                compatibilityRequirements = CompatibilityRequirements(
                    requiresGenomeIds = setOf("parent-genome"),
                    incompatibleGenomeIds = setOf("unsafe-genome"),
                    requiredCapabilities = setOf("text-normalizer")
                ),
                healthState = GenomeHealth.HEALTHY,
                provenance = Provenance(
                    author = "Friday",
                    createdMs = 123456789L,
                    lastMutation = "bounded optimization",
                    mutationOperator = MutationOperator.ALGORITHM_MUTATION,
                    sourceContext = "generated from a verified capability gap"
                )
            )
        try {
            GenomeArchive(FileStorage(dir), scope).put(original)
            Thread.sleep(250)
        } finally {
            scope.cancel()
        }

        val scope2 = CoroutineScope(Dispatchers.Default)
        try {
            val restored = GenomeArchive(FileStorage(dir), scope2)
            assertTrue(restored.loadFromDisk() >= 1)
            assertEquals(original, restored.get("full-genome"))
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun `children queries find direct lineage`() {
        val archive = GenomeArchive(FileStorage(tmpDir()), CoroutineScope(Dispatchers.Default))
        val root = genome("root")
        archive.put(root)
        archive.put(root.fork(MutationOperator.PARAMETER_MUTATION, "m1", "kid1"))
        archive.put(root.fork(MutationOperator.STRATEGY_REPLACEMENT, "m2", "kid2"))

        val children = archive.children("root")
        assertEquals(2, children.size)
        assertTrue(children.any { it.id == "kid1" })
        assertTrue(children.any { it.id == "kid2" })
    }

    @Test
    fun `archive returns null for unknown genome`() {
        val archive = GenomeArchive(FileStorage(tmpDir()), CoroutineScope(Dispatchers.Default))
        assertNull(archive.get("nonexistent"))
    }

    @Test
    fun `accepted and rejected genomes tracked separately`() {
        val archive = GenomeArchive(FileStorage(tmpDir()), CoroutineScope(Dispatchers.Default))
        archive.put(genome("good").copy(healthState = GenomeHealth.HEALTHY))
        archive.put(genome("bad").copy(healthState = GenomeHealth.REJECTED))
        archive.put(genome("testing").copy(healthState = GenomeHealth.TESTING))

        assertEquals(1, archive.allAccepted().size)
        assertEquals(1, archive.allRejected().size)
        assertEquals(1, archive.allTesting().size)
    }
}
