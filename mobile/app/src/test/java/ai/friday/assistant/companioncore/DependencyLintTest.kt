package ai.friday.assistant.companioncore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.walkTopDown

/**
 * Build-time dependency-graph lint (spec §2.30 Testing, audit R-I6) as a JVM
 * test: scans the Companion Core main source tree and enforces the §2.30
 * boundary — no Companion Core module may import the quarantined Human Core —
 * plus the sole-writer discipline (only `FridayRuntimeIntegration` owns a
 * `MutableStateFlow<CompanionSignal>`).
 *
 * This is a source-scan, not a review checklist: it reads the actual `.kt`
 * files under `src/main/java/ai/friday/assistant/companioncore` and fails the
 * build if a subsystem bypasses the adapter.
 */
class DependencyLintTest {

    @Test
    fun `no companion core subsystem imports human core internals`() {
        val files = companionCoreSources()
        assertTrue("no Companion Core sources found — check path resolution", files.isNotEmpty())

        val violations = files.filter(::containsHcImport)

        assertTrue(
            "Companion Core subsystems bypass the §2.30 choke point: " +
                violations.joinToString { it.name },
            violations.isEmpty()
        )
    }

    @Test
    fun `only the integration owns a CompanionSignal mutable flow`() {
        val files = companionCoreSources()
        // The flow's element type is inferred (no explicit <CompanionSignal>),
        // so match the initializer pattern: a MutableStateFlow producing a
        // CompanionSignal. Only FridayRuntimeIntegration may declare one.
        val writers = files.filter { file ->
            Regex("MutableStateFlow[^\\n]*CompanionSignal").containsMatchIn(file.readText()) ||
                Regex("StateFlow<CompanionSignal>").containsMatchIn(file.readText())
        }
        assertTrue(
            "sole-writer discipline violated; only FridayRuntimeIntegration may own " +
                "the CompanionSignal flow: ${writers.joinToString { it.name }}",
            writers.map { it.name } == listOf("FridayRuntimeIntegration.kt")
        )
    }

    @Test
    fun `companion signal is immutable - no var fields`() {
        val signalFile = File(companionCoreSources().first { it.name == "CompanionSignal.kt" }.path)
        val body = signalFile.readText()
        assertFalse("CompanionSignal must not declare var fields", body.containsRegex("var "))

        val intentFile = File(companionCoreSources().first { it.name == "RenderIntent.kt" }.path)
        assertFalse("RenderIntent must not declare var fields", intentFile.readText().containsRegex("var "))
    }

    private fun containsHcImport(file: File): Boolean {
        val text = file.readText()
        // import of the quarantined legacy package is prohibited in production.
        return text.contains("import ai.friday.assistant.humancore") ||
            text.contains("ai.friday.assistant.humancore.")
    }

    private fun companionCoreSources(): List<File> {
        val root = locateCompanionCoreRoot()
            ?: error("Cannot locate the Companion Core source root. Searched user.dir=" + System.getProperty("user.dir"))
        return root.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    private fun locateCompanionCoreRoot(): File? {
        // Candidate roots, in order: the module working dir (Gradle unit tests
        // run with workingDir = project dir), then up to the repo root.
        val rel = "src/main/java/ai/friday/assistant/companioncore"
        val candidates = buildList {
            add(File(rel))
            add(File(System.getProperty("user.dir"), rel))
            add(File(System.getProperty("user.dir"), "mobile/app/$rel"))
        }
        return candidates.firstOrNull { it.isDirectory }
    }
}

private fun String.containsRegex(regex: String): Boolean = Regex(regex).containsMatchIn(this)
