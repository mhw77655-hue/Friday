package com.jarvis.app.onefriday

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Friday is an Android application. The production composition contract is
 * therefore tested at its Android entry point; desktop CI is only the test
 * runner and is not a second production host.
 */
class AndroidCompositionContractTest {

    @Test
    fun `Android entry point assembles exactly one cognitive turn path`() {
        val entry = source("src/main/java/com/jarvis/app/JarvisEngine.kt")
        val assembly = source("src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt")

        assertEquals(1, entry.split("TurnPathAssembly.assemble(").size - 1)
        assertTrue(entry.contains("AndroidPlatformPorts("))

        // These authorities belong to the one composition root, not a second
        // copy assembled by JarvisEngine or a screen.
        assertEquals(1, assembly.split("val modelManager = ModelManager(").size - 1)
        assertEquals(1, assembly.split("val engine = CognitiveEngine(").size - 1)
        assertEquals(1, assembly.split("val pipeline = LatencyPipeline(").size - 1)

        listOf("ModelManager(", "CognitiveEngine(", "LatencyPipeline(").forEach { constructor ->
            assertFalse(
                "JarvisEngine must not construct a competing $constructor",
                entry.contains(constructor)
            )
        }
    }

    @Test
    fun `production speech is routed through Friday's in-app voice habitat`() {
        val entry = source("src/main/java/com/jarvis/app/JarvisEngine.kt")

        assertTrue(entry.contains("VoiceOrganismHost("))
        assertTrue(entry.contains("voiceHost.start()"))
        assertTrue(entry.contains("VoiceOutputPath("))
        assertTrue(entry.contains("LatencyLayer.setStreamingSpeak"))
        assertTrue(entry.contains("output.speak("))
    }

    @Test
    fun `Android build configuration has no Termux toolchain dependency`() {
        val gradleProperties = source("gradle.properties")
        val appBuild = source("mobile/app/build.gradle.kts")

        assertFalse(gradleProperties.contains("/data/data/com.termux"))
        assertFalse(gradleProperties.contains("android.aapt2FromMavenOverride"))
        assertFalse(appBuild.contains("runTermuxJarvisServer"))
    }

    @Test
    fun `Android production entry does not invoke an external host process`() {
        val productionFiles = listOf(
            "src/main/java/com/jarvis/app/JarvisEngine.kt",
            "src/main/java/com/jarvis/app/onefriday/AndroidPlatformPorts.kt",
            "src/main/java/com/jarvis/app/onefriday/PlatformPorts.kt",
            "src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt",
            "src/main/java/com/jarvis/app/voice/VoiceService.kt",
            "src/main/java/com/jarvis/app/voice/VoiceOrganismHost.kt",
            "src/main/java/com/jarvis/app/model/InferenceService.kt"
        ).map(::source)

        productionFiles.forEach { source ->
            assertFalse(source.contains("TermuxJarvisServer"))
            assertFalse(source.contains("JvmPlatformPorts"))
            assertFalse(source.contains("VoiceForgeAdapter"))
            assertFalse(source.contains("VoiceForgeBackend"))
            assertFalse(source.contains("ProcessBuilder("))
            assertFalse(source.contains("Runtime.getRuntime().exec("))
        }
    }

    private fun source(relative: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, relative),
            File(cwd, "mobile/app/$relative"),
            File(cwd.parentFile, relative),
            File(cwd.parentFile, "mobile/app/$relative")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Could not locate production source $relative from ${cwd.absolutePath}")
        return file.readText()
    }
}
