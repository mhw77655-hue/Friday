package com.jarvis.app.onefriday.floor

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorTripwireTest {
    @Test
    fun `AC3 floor manifest lists floor files with hashes`() {
        val manifest = FloorManifest(File.createTempFile("floor", ".manifest"))
        val files = manifest.listFloorFiles()
        assertTrue(files.isNotEmpty())
        assertTrue(files.any { it.contains("approval") })
        assertTrue(files.any { it.contains("ContinuityLaw") })
    }
}
