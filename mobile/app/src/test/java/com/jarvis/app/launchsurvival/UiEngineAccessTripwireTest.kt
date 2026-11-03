package com.jarvis.app.launchsurvival

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LAUNCH-WAKE-ORDER AC1: no screen may ask a half-built engine for anything.
 *
 * The engine's throwing accessors (derived from JarvisEngine.kt itself: every
 * getter whose body is `= checkNotNull(...)`) and the screens' own
 * "must run before" helpers are exactly the calls that crash the first
 * composition. UI code must reach the engine only through the ONE
 * readiness-gated accessor file, com.jarvis.app.ui.EngineAccess.
 *
 * The allowlist is a single file, so a new screen cannot quietly reintroduce a
 * throwing path: the scan is the whole ui tree, not a hand-kept list.
 */
class UiEngineAccessTripwireTest {

    @Test
    fun `no screen touches a throwing engine accessor before the engine is ready`() {
        val appRoot = findAppRoot()
        val engineFile = File(appRoot, "JarvisEngine.kt")
        assertTrue("JarvisEngine.kt must exist at ${engineFile.absolutePath}", engineFile.exists())

        val throwingAccessors = throwingAccessorNames(engineFile.readText())
        assertTrue(
            "the scan must derive the throwing accessors from JarvisEngine.kt itself; found none in " +
                engineFile.absolutePath,
            throwingAccessors.isNotEmpty()
        )

        val uiRoot = File(appRoot, "ui")
        assertTrue("the ui tree must exist at ${uiRoot.absolutePath}", uiRoot.isDirectory)

        // The ONE readiness-gated accessor file is the only allowed place for
        // these calls. Everything else in the ui tree is scanned.
        val allowlist = setOf(File(uiRoot, "EngineAccess.kt").canonicalPath)
        val violations = mutableListOf<String>()

        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.canonicalPath !in allowlist }
            .sortedBy { it.absolutePath }
            .forEach { file ->
                file.readLines().forEachIndexed { index, raw ->
                    val line = raw.substringBefore("//").trim()
                    if (line.isEmpty()) return@forEachIndexed

                    val direct = throwingAccessors.firstOrNull { name ->
                        Regex("""\bJarvisEngine\s*\.\s*$name\s*\(""").containsMatchIn(line)
                    }
                    if (direct != null) {
                        violations += "${relative(uiRoot, file)}:${index + 1}: " +
                            "direct call to throwing engine accessor JarvisEngine.$direct -> $line"
                        return@forEachIndexed
                    }
                    if (line.contains("must run before") ||
                        line.contains("not initialized. Call JarvisEngine.init")
                    ) {
                        violations += "${relative(uiRoot, file)}:${index + 1}: " +
                            "screen-local engine accessor throws before the engine is wired -> $line"
                    }
                }
            }

        assertTrue(
            "UI code must reach the engine only through the ONE readiness-gated accessor " +
                "(com.jarvis.app.ui.EngineAccess); ${violations.size} call site(s) still throw while " +
                "the engine is waking: " + violations.joinToString(" | ") +
                "\nAll call sites:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /** Every getter in JarvisEngine.kt whose body is `= checkNotNull(...)`. */
    private fun throwingAccessorNames(engineSource: String): Set<String> =
        Regex("""fun\s+(\w+)\s*\([^)]*\)[^=\n]*=\s*checkNotNull""")
            .findAll(engineSource)
            .map { it.groupValues[1] }
            .toSet()

    private fun relative(root: File, file: File): String =
        file.absolutePath.removePrefix(root.absolutePath).removePrefix(File.separator)

    private fun findAppRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/jarvis/app"),
            File(System.getProperty("user.dir"), "src/main/java/com/jarvis/app"),
            File(System.getProperty("user.dir"), "mobile/app/src/main/java/com/jarvis/app"),
            File("/mnt/sdcard/jarvis-repo/mobile/app/src/main/java/com/jarvis/app")
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.first()
    }
}
