package com.jarvis.app.companioncore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.walkTopDown

/**
 * Build-time dependency-graph lint (spec §2.30 Testing, audit R-I6) as a JVM
 * test: scans the Companion Core main source tree and enforces the §2.30
 * choke point — NO Companion Core module outside the integration set may
 * import Human Core internals — plus the sole-writer discipline (only
 * `HumanCoreIntegration` owns a `MutableStateFlow<CompanionSignal>`).
 *
 * This is a source-scan, not a review checklist: it reads the actual `.kt`
 * files under `src/main/java/com/jarvis/app/companioncore` and fails the
 * build if a subsystem bypasses the adapter.
 */
class DependencyLintTest {

    /** The §2.30 integration set — the only files permitted to import HC. */
    private val allowedHcFiles = setOf(
        "HumanCoreIntegration.kt",
        "LocalHumanCoreBinding.kt"
    )

    @Test
    fun `no companion core subsystem imports human core internals`() {
        val files = companionCoreSources()
        assertTrue("no Companion Core sources found — check path resolution", files.isNotEmpty())

        val violations = files.filter { file ->
            file.name !in allowedHcFiles && containsHcImport(file)
        }

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
        // CompanionSignal. Only HumanCoreIntegration may declare one.
        val writers = files.filter { file ->
            Regex("MutableStateFlow[^\\n]*CompanionSignal").containsMatchIn(file.readText()) ||
                Regex("StateFlow<CompanionSignal>").containsMatchIn(file.readText())
        }
        assertTrue(
            "sole-writer discipline violated; only HumanCoreIntegration may own " +
                "the CompanionSignal flow: ${writers.joinToString { it.name }}",
            writers.map { it.name } == listOf("HumanCoreIntegration.kt")
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
        // import com.jarvis.app.humancore or a fully-qualified humancore usage.
        return text.contains("import com.jarvis.app.humancore") ||
            text.contains("com.jarvis.app.humancore.")
    }

    private fun companionCoreSources(): List<File> {
        val root = locateCompanionCoreRoot()
            ?: error("Cannot locate the Companion Core source root. Searched user.dir=" + System.getProperty("user.dir"))
        return root.walkTopDown().filter { it.extension == "kt" }.toList()
    }

    private fun locateCompanionCoreRoot(): File? {
        // Candidate roots, in order: the module working dir (Gradle unit tests
        // run with workingDir = project dir), then up to the repo root.
        val rel = "src/main/java/com/jarvis/app/companioncore"
        val candidates = buildList {
            add(File(rel))
            add(File(System.getProperty("user.dir"), rel))
            add(File(System.getProperty("user.dir"), "mobile/app/$rel"))
        }
        return candidates.firstOrNull { it.isDirectory }
    }
}

private fun String.containsRegex(regex: String): Boolean = Regex(regex).containsMatchIn(this)
