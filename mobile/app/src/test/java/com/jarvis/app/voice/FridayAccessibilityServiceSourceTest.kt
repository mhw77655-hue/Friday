package com.jarvis.app.voice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the duplicated trailing declaration block that broke
 * `:mobile:app:compileDebugKotlin` on main: the companion object and sealed
 * interfaces were appended a second time, leaving a dangling top-level `),`.
 *
 * A normal JVM test cannot load that broken source, so this test inspects the
 * source text directly. It fails if a top-level declaration name appears twice
 * or if a top-level closing delimiter is orphaned.
 */
class FridayAccessibilityServiceSourceTest {

    private val source = File(
        "src/main/java/com/jarvis/app/voice/FridayAccessibilityService.kt"
    )

    @Test
    fun `each top level declaration is declared exactly once`() {
        assertTrue("missing source at ${source.absolutePath}", source.isFile)

        val declarations = source.readLines()
            .filter { it.isNotEmpty() && !it.first().isWhitespace() }
            .mapNotNull { TOP_LEVEL_DECLARATION.find(it)?.groupValues?.get(1) }

        assertEquals(
            "top-level declarations must be unique: $declarations",
            declarations.distinct(),
            declarations
        )
        assertTrue(declarations.contains("FridayAccessibilityService"))
        assertTrue(declarations.contains("PhoneControlCommand"))
        assertTrue(declarations.contains("PhoneControlResult"))
    }

    @Test
    fun `no stray top level closing delimiter remains`() {
        val stray = source.readLines().filter { line ->
            line.isNotEmpty() &&
                !line.first().isWhitespace() &&
                (line.startsWith(")") || line.startsWith("]"))
        }
        assertTrue("stray top-level closing delimiter(s): $stray", stray.isEmpty())
    }

    private companion object {
        private val TOP_LEVEL_DECLARATION = Regex(
            """^(?:(?:sealed|data|enum|abstract|open|final|private|internal|public)\s+)*(?:class|interface|object|fun|val|var)\s+([A-Za-z_][A-Za-z0-9_]*)"""
        )
    }
}
