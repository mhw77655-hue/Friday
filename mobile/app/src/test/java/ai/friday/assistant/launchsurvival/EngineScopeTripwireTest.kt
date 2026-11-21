package ai.friday.assistant.launchsurvival

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LAUNCH-OLLAMA-DOWN AC3: every engine-level coroutine scope must come from the
 * ONE shared containment factory, never a bare `CoroutineScope(...)`.
 *
 * A bare engine scope has no SupervisorJob and no exception handler, so one
 * uncaught organ failure kills the whole process. This test walks the
 * production entry file and requires it to contain ZERO raw constructions; the
 * factory file is the only allowed place for the construction itself.
 */
class EngineScopeTripwireTest {

    @Test
    fun `engine scopes come from the shared containment factory`() {
        val root = findSourceRoot()
        val engine = File(root, "FridayEngine.kt")
        assertTrue("FridayEngine.kt must exist at ${engine.absolutePath}", engine.exists())

        val raw = Regex("""CoroutineScope\s*\(""")
            .findAll(codeLines(engine.readText()))
            .count()

        assertEquals(
            "FridayEngine.kt must obtain every scope from the shared containment factory " +
                "(ai.friday.assistant.failure.EngineContainment), never construct a raw CoroutineScope",
            0,
            raw
        )

        val factory = File(root, "failure/EngineContainment.kt")
        assertTrue(
            "the shared containment factory must exist at ${factory.absolutePath}",
            factory.exists()
        )
    }

    /** Count only CODE lines; a KDoc mention of the forbidden shape is prose. */
    private fun codeLines(text: String): String = text.lineSequence()
        .filterNot {
            val t = it.trim()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//")
        }
        .map { it.substringBefore("//") }
        .joinToString("\n")

    private fun findSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/ai/friday/assistant"),
            File(System.getProperty("user.dir"), "src/main/java/ai/friday/assistant"),
            File(System.getProperty("user.dir"), "mobile/app/src/main/java/ai/friday/assistant"),
            File("/mnt/sdcard/friday-repo/mobile/app/src/main/java/ai/friday/assistant")
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.first()
    }
}
