package com.jarvis.app.evolution

import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.model.GenerateRequest
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.MessageRole
import com.jarvis.app.model.ModelManager
import com.jarvis.app.mutant.ArtifactManager
import com.jarvis.app.mutant.MutantEnvironment
import com.jarvis.app.mutant.ProcessLocalBackend
import com.jarvis.app.mutant.ResourceSandbox
import com.jarvis.app.mutation.LlmSynthesisProvider
import com.jarvis.app.mutation.SynthesisRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * REAL synthesis proof (REAL-SYNTHESIS acceptance):
 *
 * The LLM-backed synthesizer must call the model authority with a genuine
 * behavioral prompt and turn the generated TEXT into executable code — for
 * capabilities OUTSIDE the five hard-coded foundation operations. The canned
 * model below stands in for a live LLM in plain JVM tests; what is proven is
 * the real pipeline: prompt → generated text → parsed program → executed in
 * the unchanged [ResourceSandbox] → correct answers verified against
 * independently computed ground truth.
 */
class LlmSpecSynthesizerTest {

    /** Records every request and replays canned completions, oldest first. */
    private class FakeModel(private val responses: List<String>) {
        val requests = mutableListOf<GenerateRequest>()
        private var calls = 0
        val callCount: Int get() = calls

        suspend fun complete(request: GenerateRequest): GenerateResult {
            requests += request
            val content = responses.getOrElse(calls) { responses.last() }
            calls++
            return GenerateResult(content = content)
        }
    }

    /**
     * A requirement for a capability OUTSIDE the foundation behaviors. The
     * `behavior` field carries only loop-compatibility metadata here; the
     * LLM synthesizer generates purely from capability/description/constraints.
     */
    private fun requirement(capability: String, description: String) =
        CapabilityRequirement(
            capability = capability,
            description = description,
            behavior = Behavior.Clamp(min = 0.0, max = 1.0),
            constraints = listOf("pure function", "no side effects")
        )

    private fun synthesisRequest(capability: String, description: String) =
        SynthesisRequest(
            genome = GenomeBuilder("parent").capability(capability).build(),
            operator = com.jarvis.app.genome.MutationOperator.ALGORITHM_MUTATION,
            description = description,
            targetLanguage = "synth",
            constraints = listOf("pure function", "no side effects"),
            capability = capability
        )

    private val wordCountResponse = """
        Here is the synthesized candidate.

        ```synth
        # capability: word_count
        words = split(trim(text), " ")
        result = size(words)

        TEST two_words | input text="hello world" | expect result=2
        TEST padded_three | input text="  a b c  " | expect result=3
        TEST single_word | input text="jarvis" | expect result=1
        TEST missing_input | input | error undefined variable 'text'
        ```
    """.trimIndent()

    @Test
    fun `generates a correct word_count candidate outside the hard-coded operation list`() = runBlocking {
        val model = FakeModel(listOf(wordCountResponse))
        val synthesizer = LlmSpecSynthesizer(LlmSynthesisProvider(model::complete))
        val genome = GenomeBuilder("parent").capability("word_count").build()

        // word_count is NOT one of the five hard-coded foundation behaviors.
        assertFalse(BehaviorSynthesizer.SUPPORTED.contains("word_count"))

        val candidate = synthesizer.synthesize(
            genome,
            requirement("word_count", "count whitespace-separated words in text")
        )
        assertEquals("llm", synthesizer.name)
        assertEquals("word_count", candidate.spec.capability)
        assertTrue("own generated suite present", candidate.tests.size >= 4)
        assertTrue("failure-path test present", candidate.tests.any { it.isFailureCase })

        // Independent ground truth — computed HERE, never taken from the model.
        val sandbox = ResourceSandbox()
        val run = sandbox.run(candidate.spec, mapOf("text" to "one two three four"))
        assertTrue("run failed: ${run.error}", run.success)
        assertEquals(4.0, (run.data as? Map<*, *>)?.get("result"))
        val foxRun = sandbox.run(candidate.spec, mapOf("text" to "the quick brown fox jumps"))
        assertEquals(5.0, (foxRun.data as? Map<*, *>)?.get("result"))

        // The candidate's own generated suite passes too (success + failure paths).
        for (test in candidate.tests) {
            val tr = sandbox.run(candidate.spec, test.input)
            assertTrue(
                "${test.name}: ${if (tr.success) tr.data else tr.error}",
                sandbox.matches(tr, test)
            )
        }
        sandbox.shutdown()
    }

    @Test
    fun `prompt genuinely describes the required behavior`() = runBlocking {
        val model = FakeModel(listOf(wordCountResponse))
        val provider = LlmSynthesisProvider(model::complete)
        val synthesizer = LlmSpecSynthesizer(provider)

        synthesizer.synthesize(
            GenomeBuilder("parent").capability("word_count").build(),
            requirement("word_count", "count whitespace-separated words in text")
        )

        assertEquals(1, model.callCount)
        val request = model.requests.single()
        val system = request.messages.single { it.role == MessageRole.SYSTEM }.content
        val user = request.messages.single { it.role == MessageRole.USER }.content

        assertTrue(system.contains("synthesis"))
        // The prompt carries the real need — there is no operation switch anywhere.
        assertTrue(user.contains("CAPABILITY: word_count"))
        assertTrue(user.contains("DESCRIPTION: count whitespace-separated words in text"))
        assertTrue(user.contains("CONSTRAINTS:"))
        assertTrue("- pure function" in user)
        // It teaches a language rather than enumerating supported operations.
        assertTrue(user.contains("split(s,sep)") && user.contains("size(x)"))
        assertTrue(user.contains("result"))
        assertEquals(user, provider.lastPrompt)
    }

    @Test
    fun `candidate runs through the unchanged MutantEnvironment like any other`() = runBlocking {
        val model = FakeModel(listOf(wordCountResponse))
        val synthesizer = LlmSpecSynthesizer(LlmSynthesisProvider(model::complete))
        val genome = GenomeBuilder("parent").capability("word_count").build()
        val candidate = synthesizer.synthesize(
            genome,
            requirement("word_count", "count whitespace-separated words in text")
        )

        val root = File.createTempFile("llm-synth-test", "").apply { delete(); mkdirs() }
        try {
            val failureSurface = FailureSurface()
            val sandbox = ResourceSandbox()
            val factory = com.jarvis.app.mutation.EnvironmentFactory(
                failureSurface, File(root, "workspaces")
            )
            val backend = ProcessLocalBackend(sandbox)
            factory.registerBackend("process", backend)
            val mutantEnv = MutantEnvironment(
                factory, backend, sandbox, ArtifactManager(FileStorage(root))
            )

            val instance = mutantEnv.create(genome, "${genome.id}_llm", candidate).getOrThrow()
            for (test in candidate.tests) {
                val result = mutantEnv.runTestCase(instance, test)
                assertTrue("${test.name} must pass: ${result.error}", result.passed)
            }
            mutantEnv.destroy(instance)
            sandbox.shutdown()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `numeric capability synthesizes arithmetic outside the foundation set`() = runBlocking {
        val fahrenheitResponse = """
            ```synth
            # capability: fahrenheit_to_celsius
            c = (f - 32) / 1.8
            result = round(c * 100) / 100

            TEST freezing | input f=32 | expect result=0
            TEST boiling | input f=212 | expect result=100
            TEST body_temp | input f=98.6 | expect result=37
            TEST negative_overlap | input f=-40 | expect result=-40
            TEST missing_input | input | error undefined variable 'f'
            ```
        """.trimIndent()
        val model = FakeModel(listOf(fahrenheitResponse))
        val synthesizer = LlmSpecSynthesizer(LlmSynthesisProvider(model::complete))

        val candidate = synthesizer.synthesize(
            GenomeBuilder("parent").capability("fahrenheit_to_celsius").build(),
            requirement(
                "fahrenheit_to_celsius",
                "convert a temperature from degrees Fahrenheit to degrees Celsius"
            )
        )

        val sandbox = ResourceSandbox()
        for ((fahrenheit, celsius) in listOf(
            32.0 to 0.0, 212.0 to 100.0, 98.6 to 37.0, -40.0 to -40.0
        )) {
            val run = sandbox.run(candidate.spec, mapOf("f" to fahrenheit))
            assertTrue("$fahrenheit: ${run.error}", run.success)
            assertEquals(celsius, (run.data as? Map<*, *>)?.get("result"))
        }
        sandbox.shutdown()
    }

    @Test
    fun `a model response without a program fails loudly`() = runBlocking {
        val model = FakeModel(listOf("I'm afraid I cannot write programs today."))
        val provider = LlmSynthesisProvider(model::complete)
        val synthesizer = LlmSpecSynthesizer(provider)

        val loud = runCatching {
            synthesizer.synthesize(
                GenomeBuilder("parent").capability("word_count").build(),
                requirement("word_count", "count whitespace-separated words in text")
            )
        }
        assertTrue(loud.isFailure)
        assertTrue(loud.exceptionOrNull()!!.message!!.contains("no ```synth"))

        // Through the SynthesisProvider surface the miss is recorded, not silent.
        val soft = provider.synthesize(
            synthesisRequest("word_count", "count whitespace-separated words in text")
        )
        assertFalse(soft.success)
        assertNotNull(soft.error)
        assertNotNull(provider.health().error)
    }

    @Test
    fun `wires to a real ModelManager and fails loudly when no capable model serves synthesis`() = runBlocking {
        // Production wiring shape: the live model authority behind the same
        // generate() seam JarvisEngine uses. On JVM no code-capable model is
        // active, so synthesis must fail loudly instead of inventing output.
        val modelManager = ModelManager(context = null, scope = CoroutineScope(Dispatchers.Default))
        val provider = LlmSynthesisProvider(modelManager)
        assertTrue(provider.supportedCapabilities.contains("kotlin"))

        val result = provider.synthesize(
            synthesisRequest("word_count", "count whitespace-separated words in text")
        )
        assertFalse(
            "synthesis must never fake success when the model cannot serve it",
            result.success
        )
        assertNotNull(result.error)
    }
}
