package ai.friday.assistant.selfstudy

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
                  "package": "ai.friday.assistant.voice",
                  "declarations": ["PhoneActionPlanner", "PhoneActionPlan"],
                  "imports": ["ai.friday.assistant.model.ModelManager", "kotlin.String"]
                },
                {
                  "path": "model/ModelManager.kt",
                  "package": "ai.friday.assistant.model",
                  "declarations": ["ModelManager"],
                  "imports": ["ai.friday.assistant.voice.PhoneControlCommand"]
                },
                {
                  "path": "body/BodyCoordinator.kt",
                  "package": "ai.friday.assistant.body",
                  "declarations": ["BodyCoordinator"],
                  "imports": ["ai.friday.assistant.voice.PhoneActionPlanner"]
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
        assertEquals(listOf("ai.friday.assistant.model"), summary.packageDependencies["ai.friday.assistant.voice"])
        assertEquals(listOf("ai.friday.assistant.voice"), summary.packageDependencies["ai.friday.assistant.model"])
        assertEquals(listOf("voice/PhoneActionPlanner.kt", "model/ModelManager.kt", "body/BodyCoordinator.kt"), summary.files.map { it.path })
    }
}
