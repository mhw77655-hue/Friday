package com.jarvis.app.onefriday

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedContentEntryRegressionTest {

    @Test
    fun `cold start and reused activity both route shares to one declared handler`() {
        val source = source("src/main/java/com/jarvis/app/MainActivity.kt")

        val onCreate = lifecycleFunction(source, "onCreate")
        val onNewIntent = lifecycleFunction(source, "onNewIntent")

        assertTrue(
            "onCreate must hand an initial share to the shared-content handler",
            onCreate.contains("handleSharedContentIntent(intent)")
        )
        assertTrue(
            "onNewIntent must hand a share to the same handler when Android reuses Friday's activity",
            onNewIntent.contains("handleSharedContentIntent(intent)")
        )
        assertTrue(
            "the shared-content handler called by both entry points must be declared",
            Regex("""private\s+fun\s+handleSharedContentIntent\s*\(\s*intent\s*:\s*Intent\?\s*\)""")
                .containsMatchIn(source)
        )
        assertFalse(
            "a stale handler name must not survive a rename; this is the exact breakage " +
                "that failed :mobile:app:compileDebugKotlin at e60432b3",
            source.contains("handleSharedVideoIntent")
        )
    }

    private fun lifecycleFunction(source: String, name: String): String {
        val start = source.indexOf("override fun $name(")
        assertTrue("$name must be declared in MainActivity.kt", start >= 0)
        val next = listOf("override fun ", "private fun ", "private companion object")
            .map { source.indexOf(it, start + 1) }
            .filter { it > start }
            .minOrNull() ?: source.length
        return source.substring(start, next)
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
