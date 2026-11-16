package com.jarvis.app.onefriday

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wall-clock boundary for the production turn path.
 *
 * Every remaining System.currentTimeMillis read is either the single Android
 * root clock or a constructor fallback for isolated tests. Production wiring
 * injects the root clock into the fallbacks. Cognitive stage durations use
 * System.nanoTime instead, because elapsed-time measurement must be monotonic.
 */
class TurnPathClockAllowlistTest {
    private data class Allowance(
        val path: String,
        val expectedReads: Int,
        val reason: String
    )

    private val allowlist = listOf(
        Allowance("JarvisEngine.kt", 1, "The one Android root clock; the same function is passed into the thread tracker and AndroidPlatformPorts."),
        Allowance("cognitive/CognitiveEngine.kt", 1, "Constructor fallback for isolated tests; TurnPathAssembly injects PlatformPorts.nowMs in production."),
        Allowance("cognitive/ContextWindowAssembler.kt", 1, "Constructor fallback for isolated tests; CognitiveEngine passes the injected root clock."),
        Allowance("continuity/ContinuityGate.kt", 1, "Constructor fallback for isolated tests; TurnPathAssembly injects PlatformPorts.nowMs."),
        Allowance("identity/WorldModelService.kt", 1, "Constructor fallback for isolated tests; TurnPathAssembly injects PlatformPorts.nowMs."),
        Allowance("memory/BlendedMemoryRetriever.kt", 1, "Constructor fallback for isolated tests; TurnPathAssembly injects PlatformPorts.nowMs."),
        Allowance("memory/MemoryImportanceScorer.kt", 1, "Constructor fallback for isolated tests; TurnPathAssembly injects PlatformPorts.nowMs."),
        Allowance("memory/MemoryGraphStore.kt", 1, "Interface fallback for test callers; production retrieval always supplies an explicit as-of time."),
        Allowance("memory/AndroidMemoryGraphStore.kt", 1, "Constructor fallback for isolated Android tests; AndroidPlatformStores supplies the root clock."),
        Allowance("threads/ThreadObjects.kt", 1, "Constructor fallback for isolated tests; JarvisEngine supplies the root clock."),
        Allowance("prediction/ExpectationStateEngine.kt", 1, "Default test clock; TurnPathAssembly injects PlatformPorts.nowMs."),
        Allowance("model/ModelManager.kt", 1, "Constructor fallback for isolated tests; TurnPathAssembly injects PlatformPorts.nowMs."),
        Allowance("onefriday/AndroidPlatformPorts.kt", 2, "Default clocks for the adapter and its store bundle; the Android production entry point supplies one shared clock to both."),
        Allowance("latency/LatencyLayer.kt", 1, "Constructor fallback for isolated tests; production LatencyPipeline receives PlatformPorts.nowMs.")
    )

    @Test
    fun `every direct wall clock read in the turn path has an explicit reason`() {
        val appRoot = findAppRoot()
        val pattern = Regex("""System\.currentTimeMillis\(\)|System::currentTimeMillis""")
        val actualPaths = allowlist.map { it.path }.toSet()
        val counts = allowlist.associate { allowance ->
            val file = File(appRoot, allowance.path)
            assertTrue("turn-path source file missing: ${allowance.path}", file.isFile)
            val count = file.readLines().sumOf { line -> pattern.findAll(line).count() }
            allowance.path to count
        }

        allowlist.forEach { allowance ->
            assertTrue("allowlist reason must be explicit for ${allowance.path}", allowance.reason.isNotBlank())
            assertEquals(
                "unreviewed wall-clock read count in ${allowance.path}: ${allowance.reason}",
                allowance.expectedReads,
                counts.getValue(allowance.path)
            )
        }

        // Scan the turn-path source packages as well: a newly added file with
        // an unreviewed wall-clock read cannot hide outside the explicit list.
        val turnPathRoots = listOf(
            "cognitive", "continuity", "identity", "memory", "threads",
            "prediction", "model", "onefriday", "latency"
        )
        val unlisted = mutableListOf<String>()
        turnPathRoots.forEach { directory ->
            File(appRoot, directory).walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val relative = file.relativeTo(appRoot).path.replace(File.separatorChar, '/')
                    val count = file.readLines().sumOf { line -> pattern.findAll(line).count() }
                    if (count > 0 && relative !in actualPaths) {
                        unlisted += "$relative ($count direct reads)"
                    }
                }
        }
        assertTrue(
            "new turn-path wall-clock reads require an allowlist entry and reason: ${unlisted.joinToString()}",
            unlisted.isEmpty()
        )
    }

    private fun findAppRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/jarvis/app"),
            File(System.getProperty("user.dir"), "src/main/java/com/jarvis/app"),
            File(System.getProperty("user.dir"), "mobile/app/src/main/java/com/jarvis/app")
        )
        return candidates.firstOrNull { it.isDirectory } ?: candidates.last()
    }
}
