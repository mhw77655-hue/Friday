package com.jarvis.app.mutant

import com.jarvis.app.evolution.Behavior
import com.jarvis.app.evolution.BehaviorSynthesizer
import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.humancore.store.FileStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ArtifactRehydratorTest {
    @Test
    fun `promoted synth artifact rehydrates with its tests and behavior intact`() {
        val root = File.createTempFile("friday-artifact", "").apply { delete(); mkdirs() }
        try {
            val workspace = File(root, "candidate").apply { mkdirs() }
            val behavior = Behavior.ReverseWords(delimiter = " ")
            val implementation = CandidateImplementation(
                spec = BehaviorSynthesizer.specFor(behavior),
                tests = BehaviorSynthesizer.testsFor(behavior),
                language = "synth",
                sourceCode = BehaviorSynthesizer.sourceFor(behavior)
            )
            ArtifactManager(FileStorage(root)).collect(
                candidateId = "reverse_words_candidate",
                genomeId = "reverse_words_candidate",
                implementation = implementation,
                workspace = workspace
            )
            val genome = GenomeBuilder("reverse_words_candidate")
                .capability("reverse_words")
                .build()
                .copy(
                    healthState = GenomeHealth.HEALTHY,
                    artifactPath = workspace.absolutePath
                )

            val restored = ArtifactRehydrator.load(workspace, genome)
            assertNotNull(restored)
            assertEquals("world hello", (restored!!.spec.execute(mapOf("text" to "hello world")) as SpecOutput.Success)
                .output["result"])
            assertEquals(implementation.tests.size, restored.tests.size)
            assertEquals(true, restored.tests.any { it.isFailureCase || it.expectedError != null })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `modified artifact is rejected before it can be registered`() {
        val root = File.createTempFile("friday-artifact-tamper", "").apply { delete(); mkdirs() }
        try {
            val workspace = File(root, "candidate").apply { mkdirs() }
            val behavior = Behavior.Clamp(0.0, 10.0)
            val implementation = CandidateImplementation(
                spec = BehaviorSynthesizer.specFor(behavior),
                tests = BehaviorSynthesizer.testsFor(behavior),
                language = "synth",
                sourceCode = BehaviorSynthesizer.sourceFor(behavior)
            )
            ArtifactManager(FileStorage(root)).collect(
                candidateId = "clamp_candidate",
                genomeId = "clamp_candidate",
                implementation = implementation,
                workspace = workspace
            )
            File(workspace, "artifacts/source.txt").appendText("\nresult = 999")
            val genome = GenomeBuilder("clamp_candidate")
                .capability("clamp")
                .build()
                .copy(healthState = GenomeHealth.HEALTHY, artifactPath = workspace.absolutePath)

            assertNull(ArtifactRehydrator.load(workspace, genome))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `synth language round trips escaped strings in lookup behavior`() {
        val behavior = Behavior.Lookup(
            table = mapOf("a\"b" to "line\none", "slash\\" to "quote\""),
            default = "unknown"
        )
        val spec = BehaviorSynthesizer.specFor(behavior)
        val restored = SynthProgram.parse(
            "```synth\n# capability: lookup\n${spec.source}\n```"
        )
        val result = restored.execute(mapOf("key" to "a\"b")) as SpecOutput.Success
        assertEquals("line\none", result.output["result"])
    }
}
