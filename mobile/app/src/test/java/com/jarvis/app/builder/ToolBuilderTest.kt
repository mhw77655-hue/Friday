package com.jarvis.app.builder

import com.jarvis.app.cognitive.capability.CapabilityFabric
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.immune.ImmuneSystem
import com.jarvis.app.evolution.Behavior
import com.jarvis.app.evolution.RollbackController
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.model.GenerateRequest
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.mutation.LlmSynthesisProvider
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.nervous.GlobalNervousSystem
import com.jarvis.app.research.universal.UniversalResearchEngine
import com.jarvis.app.resource.ResourceGovernor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §22 — THE ARCHITECTURE PROOF, END TO END.
 *
 * The system is told: «JARVIS needs capability X but no registered
 * implementation exists.» It must demonstrate the complete loop:
 *
 *   CAPABILITY GAP → genome → candidate → mutant environment → build →
 *   tests → benchmark → score → archive → promote → nervous system discovers
 *   → callable → failure path tested → rollback tested.
 *
 * Uses a harmless local toy capability (reverse_words). No production code is
 * modified; the proof is the architecture executing for real. Plain JVM.
 */
class ToolBuilderTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tmpDir(): File = File.createTempFile("toolbuilder-test", "").apply {
        delete(); mkdirs(); tempDirs += this
    }

    private class Harness(
        val registry: MicroSystemRegistry,
        val genomeRegistry: GenomeRegistry,
        val archive: GenomeArchive,
        val failureSurface: FailureSurface,
        val governor: ResourceGovernor,
        val builder: ToolBuilder,
        val gns: GlobalNervousSystem,
        val rollback: RollbackController,
        val scope: CoroutineScope
    )

    private fun harness(): Harness {
        val root = tmpDir()
        val fileStorage = FileStorage(root)
        val scope = CoroutineScope(Dispatchers.IO)
        val failureSurface = FailureSurface()
        // Constructed through the same production composition point as
        // JarvisEngine.init() (TOOLBUILDER-RESEARCH-CONSUMER-WIRING) — no second
        // stack construction site.
        val stack = ToolBuilderComposition.build(
            research = UniversalResearchEngine(),
            failureSurface = failureSurface,
            fileStorage = fileStorage,
            scope = scope,
            workspacesDir = File(root, "workspaces")
        )
        return Harness(
            registry = stack.registry,
            genomeRegistry = stack.genomeRegistry,
            archive = stack.archive,
            failureSurface = stack.failureSurface,
            governor = stack.governor,
            builder = stack.builder,
            gns = stack.gns,
            rollback = stack.rollback,
            scope = stack.scope
        )
    }

    @Test
    fun `full evolutionary loop creates a callable capability from a gap`() = runBlocking {
        val h = harness()
        val spec = ToolSpecification(
            capability = "reverse_words",
            description = "reverse the order of words in a sentence",
            behavior = Behavior.ReverseWords(),
            researchFirst = true
        )

        // 1. The gap is real — nothing provides it yet.
        assertFalse(h.gns.hasCapability("reverse_words"))

        // 2. Build: gap → design → synthesize → environment → build → test →
        //    benchmark → score → archive → promote → register.
        val result = h.builder.build(spec)
        assertTrue("build message: ${result.message}", result.built)
        assertEquals("PROMOTED", result.status)
        assertNotNull(result.genomeId)
        assertNotNull(result.providerId)
        assertTrue(result.fitness!! > 0)
        assertTrue(result.candidates.isNotEmpty())

        // 3. The nervous system now discovers the capability (§22).
        assertTrue(h.gns.hasCapability("reverse_words"))
        assertTrue("reverse_words" in h.gns.capabilities)

        // 4. The archived genome is HEALTHY (promoted).
        val genomeId = result.genomeId!!
        assertEquals(GenomeHealth.HEALTHY, h.archive.get(genomeId)?.healthState)
        assertEquals(genomeId, h.genomeRegistry.bestFor("reverse_words")?.id)

        // 5. The promoted organism is callable through the router (§22).
        val routed = h.gns.route("reverse_words", mapOf("text" to "hello jarvis"))
        assertTrue("route: ${routed.lastError}", routed.success)
        assertEquals("jarvis hello", (routed.result?.data as Map<*, *>)["result"])

        // 6. Failure path: a bad input fails loudly on the canonical surface.
        val bad = h.gns.route("reverse_words", mapOf("wrong_key" to "x"))
        assertFalse(bad.success)
        assertTrue(
            "failure surfaced",
            h.failureSurface.currentFailures.value.any { it.category == FailureCategory.EVOLUTION }
        )

        // 7. Rollback: the capability is removed and the previous state restored.
        val rollbackResult = h.rollback.rollback(
            com.jarvis.app.evolution.PromotionReceipt(
                genomeId = genomeId,
                rollbackPoint = null,
                systemId = result.providerId!!,
                promotedAtMs = 0
            ),
            "end-to-end rollback test"
        )
        assertTrue(rollbackResult.success)
        assertEquals(GenomeHealth.REJECTED, h.archive.get(genomeId)?.healthState)
        assertFalse(h.gns.hasCapability("reverse_words"))
        assertFalse(h.registry.has(result.providerId!!))
        h.scope.cancel()
    }

    @Test
    fun `production composition uses Friday model to synthesize a custom capability`() = runBlocking {
        val root = tmpDir()
        val scope = CoroutineScope(Dispatchers.IO)
        val response = """
            ```synth
            # capability: word_count
            words = split(trim(text), " ")
            result = size(words)
            TEST two_words | input text="hello world" | expect result=2
            TEST missing_input | input | error undefined variable 'text'
            ```
        """.trimIndent()
        var modelCalls = 0
        val provider = LlmSynthesisProvider(complete = { _: GenerateRequest ->
            modelCalls++
            GenerateResult(content = response)
        })
        val cognitiveFabric = CapabilityFabric(ImmuneSystem(surface = FailureSurface()))
        val stack = ToolBuilderComposition.build(
            research = UniversalResearchEngine(),
            failureSurface = FailureSurface(),
            fileStorage = FileStorage(root),
            scope = scope,
            workspacesDir = File(root, "workspaces"),
            llmSynthesisProvider = provider,
            cognitiveCapabilityFabric = cognitiveFabric
        )

        val priorGenome = GenomeBuilder("prior-existing-tool")
            .capability("existing_tool")
            .build()
            .copy(healthState = com.jarvis.app.genome.GenomeHealth.HEALTHY)
        stack.archive.put(priorGenome)
        stack.genomeRegistry.register(priorGenome)

        val spec = ToolSpecification(
            capability = "word_count",
            description = "count whitespace-separated words in text",
            behavior = Behavior.Custom(capability = "word_count", port = "text", type = "String"),
            constraints = listOf("pure function", "no side effects")
        )
        val result = stack.builder.build(spec)

        assertTrue("build status=${result.status}; ${result.message}", result.built)
        assertTrue("the live model-backed synthesizer must be called", modelCalls > 0)
        val routed = stack.gns.route("word_count", mapOf("text" to "one two three"))
        assertTrue("legacy nervous-system route failed: ${routed.lastError}", routed.success)
        assertEquals(3.0, (routed.result?.data as? Map<*, *>)?.get("result"))

        // The same promoted implementation is also exposed to the primary
        // CognitiveEngine fabric; Friday does not build tools into a parallel,
        // unreachable registry.
        val cognitiveCall = cognitiveFabric.invoker.invoke(
            ActionRequest(
                planId = "self-build-integration",
                stepId = "word-count",
                action = "word_count",
                description = "Count the words in the supplied text",
                context = mapOf("text" to "one two three")
            )
        )
        assertTrue("cognitive fabric could not invoke the promoted tool: ${cognitiveCall.resolution}", cognitiveCall.success)
        assertEquals("3.0", cognitiveCall.result?.output?.get("result"))

        val rolledBack = stack.rollback.rollback(
            result.promotionReceipt
                ?: error("a promoted build must retain its complete rollback receipt"),
            "verify cognitive-fabric rollback"
        )
        assertTrue(rolledBack.success)
        assertTrue(cognitiveFabric.registry.lookupByOperation("word_count").isEmpty())
        assertTrue(
            "rollback must preserve unrelated existing capability mappings",
            "existing_tool" in stack.genomeRegistry.allBest()
        )
        scope.cancel()
    }

    @Test
    fun `startup restores archived genome metadata but does not mistake it for an executable tool`() = runBlocking {
        val root = tmpDir()
        val storage = FileStorage(root)
        val scope = CoroutineScope(Dispatchers.IO)
        val archived = GenomeBuilder("archived-tool-genome")
            .capability("archived_tool")
            .build()
            .copy(healthState = com.jarvis.app.genome.GenomeHealth.HEALTHY)
        try {
            GenomeArchive(storage, scope).put(archived)
            Thread.sleep(250)
            val stack = ToolBuilderComposition.build(
                research = UniversalResearchEngine(),
                failureSurface = FailureSurface(),
                fileStorage = storage,
                scope = scope,
                workspacesDir = File(root, "workspaces")
            )
            assertEquals("archived-tool-genome", stack.genomeRegistry.bestFor("archived_tool")?.id)
            val gap = CapabilityGapDetector(stack.registry, stack.genomeRegistry).detect("archived_tool")
            assertTrue(gap.gap)
            assertEquals("KNOWN_GOOD_ARTIFACT_MISSING", gap.status)
            stack.scope.cancel()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `phone-resident Builder creates a capability and restores it after process restart`() = runBlocking {
        val root = tmpDir()
        val storage = FileStorage(root)
        val response = """
            ```synth
            # capability: word_count
            words = split(trim(text), " ")
            result = size(words)
            TEST two_words | input text="hello world" | expect result=2
            TEST missing_input | input | error undefined variable 'text'
            ```
        """.trimIndent()
        var modelCalls = 0
        val provider = LlmSynthesisProvider(complete = { _: GenerateRequest ->
            modelCalls++
            GenerateResult(content = response)
        })
        val firstScope = CoroutineScope(Dispatchers.IO)
        val firstFabric = CapabilityFabric(ImmuneSystem(surface = FailureSurface()))
        val first = ToolBuilderComposition.build(
            research = UniversalResearchEngine(),
            failureSurface = FailureSurface(),
            fileStorage = storage,
            scope = firstScope,
            workspacesDir = File(root, "workspaces"),
            llmSynthesisProvider = provider,
            cognitiveCapabilityFabric = firstFabric
        )

        val specJson = """{"capability":"word_count","description":"Count whitespace-separated words in text","behavior":{"type":"custom","port":"text","value_type":"String"},"constraints":["pure function","no side effects"]}"""
        val built = firstFabric.invoker.invoke(
            ActionRequest(
                planId = "builder-runtime",
                stepId = "create-word-count",
                action = "builder.create_tool",
                description = "Create a reusable word counting capability",
                context = mapOf("spec_json" to specJson)
            )
        )
        assertTrue("Builder invocation failed: ${built.failure}", built.success)
        assertTrue("the live model should synthesize the custom capability", modelCalls > 0)
        val firstCall = firstFabric.invoker.invoke(
            ActionRequest(
                planId = "builder-runtime",
                stepId = "call-word-count",
                action = "word_count",
                context = mapOf("text" to "one two three")
            )
        )
        assertTrue("generated capability failed: ${firstCall.failure}", firstCall.success)
        assertEquals("3.0", firstCall.output["result"])

        // A new composition simulates process death/restart. It receives no
        // model provider; the promoted artifact itself must be enough.
        firstScope.cancel()
        val secondScope = CoroutineScope(Dispatchers.IO)
        val secondFabric = CapabilityFabric(ImmuneSystem(surface = FailureSurface()))
        val second = ToolBuilderComposition.build(
            research = UniversalResearchEngine(),
            failureSurface = FailureSurface(),
            fileStorage = FileStorage(root),
            scope = secondScope,
            workspacesDir = File(root, "workspaces"),
            cognitiveCapabilityFabric = secondFabric
        )
        assertTrue("promoted capability was not restored", "word_count" in second.recoveredCapabilities)
        val restoredCall = secondFabric.invoker.invoke(
            ActionRequest(
                planId = "builder-restart",
                stepId = "call-restored-word-count",
                action = "word_count",
                context = mapOf("text" to "four five six")
            )
        )
        assertTrue("restored capability failed: ${restoredCall.failure}", restoredCall.success)
        assertEquals("3.0", restoredCall.output["result"])
        secondScope.cancel()
    }

    @Test
    fun `building an already-satisfied capability is a no-op`() = runBlocking {
        val h = harness()
        val spec = ToolSpecification(
            capability = "clamp",
            description = "clamp a value into a range",
            behavior = Behavior.Clamp(min = 0.0, max = 100.0)
        )
        // Pre-install a provider directly.
        val genome = com.jarvis.app.evolution.GenomeFactory.genomeFor(spec.toRequirement())
        val organism = com.jarvis.app.microsystem.SpecMicroSystem(
            id = "preinstalled_clamp", genome = genome,
            spec = com.jarvis.app.evolution.BehaviorSynthesizer.specFor(Behavior.Clamp(0.0, 100.0))
        )
        organism.initialize()
        h.registry.register(organism)

        val result = h.builder.build(spec)
        assertFalse(result.built)
        assertEquals("SATISFIED", result.status)
        assertEquals("preinstalled_clamp", result.providerId)
        assertTrue(result.message.contains("already provided"))
        h.scope.cancel()
    }

    @Test
    fun `invalid tool spec is rejected before evolution runs`() = runBlocking {
        val h = harness()
        val result = h.builder.build(
            ToolSpecification(capability = "", description = "", behavior = Behavior.Clamp(0.0, 1.0))
        )
        assertFalse(result.built)
        assertEquals("INVALID_SPEC", result.status)
        h.scope.cancel()
    }
}
