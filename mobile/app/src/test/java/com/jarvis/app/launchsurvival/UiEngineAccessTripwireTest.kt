package com.jarvis.app.launchsurvival

import java.io.File
import org.junit.Assert.assertEquals
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

        // Navigation used to unwrap nullable readiness-gated helpers with !!.
        // That converted a typed missing-organ result back into a process crash
        // if an optional/late organ was absent after the global Ready signal.
        val navHost = File(uiRoot, "navigation/JarvisNavHost.kt")
        assertTrue("navigation host must exist", navHost.isFile)
        val navSource = navHost.readText()
        val unsafeUnwrap = Regex(
            """\bget(?:TaskInbox|ApprovalQueue|AlertStore|SessionManager|EnvironmentManager|ModelManager|BootstrapManager)\s*\(\)\s*!!"""
        )
        assertTrue(
            "navigation must not force-unwrap nullable engine accessors",
            !unsafeUnwrap.containsMatchIn(navSource)
        )
        val requiredSlots = listOf(
            "taskInbox", "approvalQueue", "alertStore", "sessionManager",
            "environmentManager", "modelManager", "bootstrapManager"
        )
        requiredSlots.forEach { accessor ->
            assertTrue(
                "navigation must use EngineAccess.$accessor()",
                navSource.contains("EngineAccess.$accessor()")
            )
        }
    }

    @Test
    fun `every published engine slot is backed by the shared readiness gate`() {
        val appRoot = findAppRoot()
        val engineSource = File(appRoot, "JarvisEngine.kt").readText()
        val slotDeclarations = engineSource.lineSequence()
            .filter { Regex("""fun\s+\w+Slot\s*\(""").containsMatchIn(it) }
            .toList()

        assertTrue("JarvisEngine must expose readiness-gated slots", slotDeclarations.isNotEmpty())
        slotDeclarations.forEach { declaration ->
            assertTrue(
                "every engine slot must delegate to readinessGate.slot: $declaration",
                declaration.contains("readinessGate.slot")
            )
        }

        val accessorSource = File(appRoot, "ui/EngineAccess.kt").readText()
        val normalizedAccessors = accessorSource.replace(Regex("""\s+"""), " ")
        val typedAccessors = Regex(
            """fun (\w+)\(\): EngineSlot<[^>]+> = JarvisEngine\.(\w+Slot)\(\)"""
        ).findAll(normalizedAccessors).toList()
        assertTrue("EngineAccess must publish typed accessors", typedAccessors.isNotEmpty())
        typedAccessors.forEach { match ->
            assertEquals(
                "UI accessor ${match.groupValues[1]} must delegate to its matching engine slot",
                "${match.groupValues[1]}Slot",
                match.groupValues[2]
            )
        }
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
