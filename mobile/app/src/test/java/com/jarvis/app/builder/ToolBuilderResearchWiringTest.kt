package com.jarvis.app.builder

import com.jarvis.app.cloud.CloudModelRouter
import com.jarvis.app.cloud.CloudProvider
import com.jarvis.app.cloud.CloudResult
import com.jarvis.app.evolution.Behavior
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.research.universal.CandidateSource
import com.jarvis.app.research.universal.InMemoryMechanismsStore
import com.jarvis.app.research.universal.UniversalResearchEngine
import com.jarvis.app.selfreconfig.JarvisOrganGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TOOLBUILDER-RESEARCH-CONSUMER-WIRING proof harness (AC1–AC3).
 *
 * The UniversalResearchEngine is constructed with its real CloudModelRouter
 * channels and a real mechanisms store — no test-only double acts as the
 * research engine or its channels — and the ToolBuilder is constructed through
 * ToolBuilderComposition, the exact stack JarvisEngine.init() hosts for
 * requestBuild(). Building for a genuine capability gap must flow the engine's
 * research output (including the cloud channel's) into the builder's build
 * record through the real call path, and persist it to the shared store.
 */
class ToolBuilderResearchWiringTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tmpDir(): File = File.createTempFile("toolbuilder-research-wiring", "").apply {
        delete(); mkdirs(); tempDirs += this
    }

    /** Same MECHANISM: fixture the phase-B harness parses through the real
     *  CloudResearchProvider. Two distinct mechanisms across two domains, so the
     *  cloud channel's output is unambiguous. */
    private val cloudMechanismsText = """
        MECHANISM: name=pheromone routing|domain=INSECTS|description=trail reinforcement decays over time|category=routing|complexity=SIMPLE|properties=decay=slow,reinforce=fast
        MECHANISM: name=health-aware replica|domain=NETWORKING|description=route to nearest healthy server and cache|category=routing|complexity=MODERATE|properties=health=checked,cache=yes
    """.trimIndent()

    private class MechanismCloudProvider(
        override val id: String,
        private val reply: String
    ) : CloudProvider {
        override fun generate(prompt: String): CloudResult = CloudResult.Success(reply, id)
    }

    /** The same production composition JarvisEngine.init() hosts for
     *  requestBuild() — ToolBuilderComposition over the real engine. */
    private fun productionBuilderStack(
        root: File,
        research: UniversalResearchEngine
    ): ToolBuilderComposition.Stack {
        val stack = ToolBuilderComposition.build(
            research = research,
            failureSurface = FailureSurface(),
            fileStorage = FileStorage(root),
            scope = CoroutineScope(Dispatchers.IO),
            workspacesDir = File(root, "workspaces")
        )
        tempDirs += root
        return stack
    }

    @Test
    fun `research output flows from the universal research engine into the tool builder through the real call path`() = runBlocking<Unit> {
        val root = tmpDir()
        val store = InMemoryMechanismsStore()
        val engine = UniversalResearchEngine(
            cloudRouter = CloudModelRouter(listOf(MechanismCloudProvider("free-model-1", cloudMechanismsText))),
            store = store
        )
        val stack = productionBuilderStack(root, engine)

        assertEquals(0, store.count())

        // The full §15 loop for a genuine capability gap; the builder's §7
        // research sweep runs the FULL persisted pipeline (local + cloud).
        val result = stack.builder.build(
            ToolSpecification(
                capability = "reverse_words",
                description = "reverse the order of words in a sentence",
                behavior = Behavior.ReverseWords(),
                researchFirst = true
            )
        )

        assertNotNull("research note must be produced", result.researchNote)
        assertTrue(
            "researchCandidates was empty — the engine's research output never reached the builder",
            result.researchCandidates.isNotEmpty()
        )
        assertTrue(
            "cloud-reasoning candidates from UniversalResearchEngine's real channels must appear in the builder's record",
            result.researchCandidates.any { it.source == CandidateSource.CLOUD_REASONING }
        )

        // Persisted: the same candidates landed in the shared mechanisms store —
        // cloud output is interchangeable input for any consumer (§7).
        assertTrue("researchPersist must persist the candidates", store.count() >= 1)
        assertTrue(
            "the store must contain a cloud-reasoning candidate",
            store.readAll().any { it.source == CandidateSource.CLOUD_REASONING }
        )

        stack.scope.cancel()
    }

    @Test
    fun `the tool builder is constructed by the same production composition JarvisEngine init hosts`() {
        val root = tmpDir()
        val stack = productionBuilderStack(root, UniversalResearchEngine())

        assertNotNull("composition must construct the ToolBuilder", stack.builder)
        assertNotNull(stack.registry)
        assertNotNull(stack.gns)
        assertNotNull(stack.rollback)
        assertNotNull(stack.archive)
        assertNotNull(stack.governor)

        stack.scope.cancel()
    }

    @Test
    fun `organ graph maps the tool builder node and the research consumer edge`() {
        val graph = JarvisOrganGraph.build()

        val node = graph.node("builder.toolBuilder")
        assertNotNull("builder.toolBuilder must be a registered organ", node)
        assertEquals("com.jarvis.app.builder.ToolBuilder", node!!.qualifiedClassName)
        assertTrue(
            "research.universalResearchEngine -> builder.toolBuilder edge must exist",
            graph.edgesFrom("research.universalResearchEngine").any { it.toId == "builder.toolBuilder" }
        )
    }
}