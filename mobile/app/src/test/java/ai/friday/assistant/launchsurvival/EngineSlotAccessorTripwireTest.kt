package ai.friday.assistant.launchsurvival

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structural companion to the immutable red-first UI tripwire. */
class EngineSlotAccessorTripwireTest {

    @Test
    fun `all engine slot accessors delegate through the shared readiness gate`() {
        val appRoot = findAppRoot()
        val engineSource = File(appRoot, "FridayEngine.kt").readText()
        val declarations = engineSource.lineSequence()
            .filter { it.trimStart().startsWith("fun ") && it.contains("Slot(): EngineSlot<") }
            .toList()

        assertTrue("FridayEngine must expose typed organ slots", declarations.isNotEmpty())
        declarations.forEach { declaration ->
            assertTrue(
                "engine slot bypasses readinessGate: $declaration",
                declaration.contains("readinessGate.slot")
            )
        }
    }

    @Test
    fun `all UI typed accessors delegate to their matching engine slots`() {
        val appRoot = findAppRoot()
        val accessorSource = File(appRoot, "ui/EngineAccess.kt").readText()
        val normalized = accessorSource.replace(Regex("""\s+"""), " ")
        val declarations = accessorSource.lineSequence()
            .filter { it.trimStart().startsWith("fun ") && it.contains("EngineSlot<") }
            .toList()

        assertTrue("EngineAccess must expose typed accessors", declarations.isNotEmpty())
        declarations.forEach { declaration ->
            val name = declaration.trim().removePrefix("fun ").substringBefore("(")
            assertTrue(
                "UI accessor $name must call FridayEngine.${name}Slot()",
                normalized.contains("fun $name(): EngineSlot<") &&
                    normalized.contains("FridayEngine.${name}Slot()")
            )
        }
    }

    @Test
    fun `navigation never force unwraps nullable engine dependencies`() {
        val appRoot = findAppRoot()
        val navHost = File(appRoot, "ui/navigation/FridayNavHost.kt")
        assertTrue("navigation host must exist", navHost.isFile)
        val source = navHost.readText()
        val forbidden = Regex(
            """\bget(?:TaskInbox|ApprovalQueue|AlertStore|SessionManager|EnvironmentManager|ModelManager|BootstrapManager)\s*\(\)\s*!!"""
        )
        assertTrue("navigation must render a typed waking state instead of force-unwrapping", !forbidden.containsMatchIn(source))
        val requiredSlots = listOf(
            "taskInbox", "approvalQueue", "alertStore", "sessionManager",
            "environmentManager", "modelManager", "bootstrapManager"
        )
        requiredSlots.forEach { accessor ->
            assertTrue(
                "navigation route must use EngineAccess.$accessor()",
                source.contains("EngineAccess.$accessor()")
            )
        }
        assertTrue(
            "navigation must have a typed waking fallback for an unavailable organ",
            source.contains("WakingSurface(slot.readiness)")
        )
    }

    private fun findAppRoot(): File {
        val candidates = listOf(
            File("src/main/java/ai/friday/assistant"),
            File(System.getProperty("user.dir"), "src/main/java/ai/friday/assistant"),
            File(System.getProperty("user.dir"), "mobile/app/src/main/java/ai/friday/assistant"),
            File("/mnt/sdcard/friday-repo/mobile/app/src/main/java/ai/friday/assistant")
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.first()
    }
}
