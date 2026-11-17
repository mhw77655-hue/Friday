package com.jarvis.app.onefriday

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The root-clock wiring regression test.
 *
 * The exhaustive list of direct wall-clock reads is owned by WallClockAllowlistTest.
 * This test protects the architectural boundary: Android owns one clock and the
 * production composition passes that clock through PlatformPorts to every
 * time-dependent cognitive organ. Constructor defaults remain for isolated tests.
 */
class TurnPathClockAllowlistTest {
    @Test
    fun `production composition injects one Android root clock through the turn path`() {
        val appRoot = findAppRoot()
        val engine = File(appRoot, "JarvisEngine.kt")
        val ports = File(appRoot, "onefriday/AndroidPlatformPorts.kt")
        val assembly = File(appRoot, "onefriday/TurnPathAssembly.kt")
        val cognitive = File(appRoot, "cognitive/CognitiveEngine.kt")

        listOf(engine, ports, assembly, cognitive).forEach {
            assertTrue("required production clock source is missing: ${it.path}", it.isFile)
        }

        val engineText = engine.readText()
        val portsText = ports.readText()
        val assemblyText = assembly.readText()
        val cognitiveText = cognitive.readText()

        assertTrue(
            "JarvisEngine must create the single host clock and pass it into AndroidPlatformPorts",
            engineText.contains("val clock: () -> Long = { System.currentTimeMillis() }") &&
                engineText.contains("clock = clock")
        )
        assertTrue(
            "AndroidPlatformPorts must expose the injected host clock, not construct another",
            portsText.contains("override fun nowMs(): Long = clock()")
        )
        assertTrue(
            "the Android graph store must receive the same host clock",
            portsText.contains("AndroidMemoryGraphStore(context, clock = clock)")
        )
        assertTrue(
            "TurnPathAssembly must pass PlatformPorts.nowMs into the cognitive engine",
            assemblyText.contains("nowMs = { ports.nowMs() }")
        )
        assertTrue(
            "successor-state learning must share the turn's injected clock",
            assemblyText.contains("clock = { ports.nowMs() }")
        )
        assertTrue(
            "CognitiveEngine keeps a fallback only for isolated callers",
            cognitiveText.contains("private val nowMs: () -> Long = { System.currentTimeMillis() }")
        )
        assertFalse(
            "the production composition root itself must not read the wall clock directly",
            Regex("""System\.currentTimeMillis\(\)""").containsMatchIn(assemblyText)
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
