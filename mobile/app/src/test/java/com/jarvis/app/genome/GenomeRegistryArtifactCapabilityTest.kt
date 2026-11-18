package com.jarvis.app.genome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenomeRegistryArtifactCapabilityTest {
    @Test
    fun `one artifact never advertises inherited capabilities it does not implement`() {
        val genome = GenomeBuilder("multi-capability-genome")
            .capability("native.inherited")
            .capability("generated.new")
            .build()
            .copy(
                healthState = GenomeHealth.HEALTHY,
                artifactPath = "/private/friday-builder/workspaces/candidate",
                artifactCapability = "generated.new"
            )
        val registry = GenomeRegistry()

        registry.register(genome)

        assertEquals(genome.id, registry.bestFor("generated.new")?.id)
        assertNull(registry.bestFor("native.inherited"))
    }

    @Test
    fun `legacy genome without executable artifact remains discoverable as a rebuild candidate`() {
        val genome = GenomeBuilder("legacy-genome")
            .capability("legacy.capability")
            .build()
            .copy(healthState = GenomeHealth.HEALTHY)
        val registry = GenomeRegistry()

        registry.register(genome)

        assertEquals(genome.id, registry.bestFor("legacy.capability")?.id)
    }
}
