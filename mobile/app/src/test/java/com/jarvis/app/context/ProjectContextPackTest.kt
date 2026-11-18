package com.jarvis.app.context

import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectContextPackTest {
    private fun pack() = ProjectContextPack(
        projectId = "friday",
        ownerVision = "Friday preserves her identity and develops capabilities on the phone.",
        identityRules = "Owner-authored identity rules are immutable without explicit owner approval.",
        architecture = "Android Kotlin runtime with local-first memory and optional remote advisers.",
        verifiedFacts = "SQLite graph memory is authoritative.",
        activeObjectives = "Implement provider connectors and context continuity.",
        knownFailures = "Cloud provider list is currently empty.",
        sourceRevision = "abc123",
        updatedAtMs = 123L
    )

    @Test fun rendersProjectIdentityAndRevision() {
        val rendered = pack().renderFor("provider connector")
        assertTrue(rendered.contains("PROJECT CONTEXT PACK v1"))
        assertTrue(rendered.contains("Friday preserves her identity"))
        assertTrue(rendered.contains("revision=abc123"))
    }

    @Test fun boundsContextSize() {
        val huge = pack().copy(ownerVision = "x".repeat(20_000))
        assertTrue(huge.renderFor("identity", 512).length <= 512)
    }
}
