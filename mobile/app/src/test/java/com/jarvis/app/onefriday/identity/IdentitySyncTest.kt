package com.jarvis.app.onefriday.identity

import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.humancore.store.ValueRecord
import com.jarvis.app.humancore.store.ValueSeverity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentitySyncTest {
    @Test
    fun `AC1 identity becomes Friday she via admin revision`() {
        val dir = File.createTempFile("hc", "dir")
        dir.delete()
        dir.mkdirs()
        try {
            HumanCore.init(FileStorage(dir))
            val current = HumanCore.stateSnapshot()
            val values = current.values.map { v ->
                ValueRecord(v.key, v.statement, ValueSeverity.valueOf(v.severity.name), v.justification)
            }
            // Keep four hard boundary values and Human Core personality defaults unchanged structure
            val ok = HumanCore.adminIdentityRevision(
                newName = "Friday",
                newSelfDescription = "Friday is a developing individual.",
                newValues = values,
                newBoundaries = current.boundaries,
                trigger = "ONE-5-IDENTITY-SYNC-FLOOR",
                authorizer = "admin"
            )
            assertTrue(ok)
            val after = HumanCore.stateSnapshot()
            assertEquals("Friday", after.identityName)
            // pronoun she should be reflected
        } finally {
            dir.deleteRecursively()
        }
    }
}
