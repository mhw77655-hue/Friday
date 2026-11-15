package com.jarvis.app.selfstudy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ApplicationSourceIndexTest {
    @Test fun `parses source paths declarations and internal package dependency graph`() {
        val raw = """
            {
              "schemaVersion": 2,
              "sourceRevision": "abc123",
              "files": [
                {
                  "path": "voice/PhoneActionPlanner.kt",
                  "package": "com.jarvis.app.voice",
                  "declarations": ["PhoneActionPlanner", "PhoneActionPlan"],
                  "imports": ["com.jarvis.app.model.ModelManager", "kotlin.String"]
                },
                {
                  "path": "model/ModelManager.kt",
                  "package": "com.jarvis.app.model",
                  "declarations": ["ModelManager"],
                  "imports": ["com.jarvis.app.voice.PhoneControlCommand"]
                },
                {
                  "path": "body/BodyCoordinator.kt",
                  "package": "com.jarvis.app.body",
                  "declarations": ["BodyCoordinator"],
                  "imports": ["com.jarvis.app.voice.PhoneActionPlanner"]
                }
              ]
            }
        """.trimIndent()

        val summary = ApplicationSourceIndex.parse(raw)

        assertNotNull(summary)
        assertEquals(2, summary!!.schemaVersion)
        assertEquals("abc123", summary.sourceRevision)
        assertEquals(3, summary.sourceFileCount)
        assertEquals(4, summary.declaredSymbolCount)
        assertEquals(3, summary.packageCount)
        assertEquals(3, summary.dependencyEdgeCount)
        assertEquals(listOf("com.jarvis.app.model"), summary.packageDependencies["com.jarvis.app.voice"])
        assertEquals(listOf("com.jarvis.app.voice"), summary.packageDependencies["com.jarvis.app.model"])
        assertEquals(listOf("voice/PhoneActionPlanner.kt", "model/ModelManager.kt", "body/BodyCoordinator.kt"), summary.files.map { it.path })
    }
}
