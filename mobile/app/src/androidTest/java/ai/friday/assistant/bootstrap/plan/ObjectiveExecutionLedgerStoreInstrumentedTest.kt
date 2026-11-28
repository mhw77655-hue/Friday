package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.BuildConfig
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ObjectiveExecutionLedgerStoreInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun packagedCanonicalPlanIsImportedBeforeAssessmentAndSurvivesReconciliation() {
        val directory = File(context.filesDir, "friday-development/plan")
        check(directory.deleteRecursively() || !directory.exists())

        val store = ObjectiveExecutionLedgerStore(context)
        val first = store.reconcile()
        val plan = File(directory, "canonical-delivery-plan.md")
        val metadataFile = File(directory, "objective-ledger.json")

        assertTrue("The packaged canonical plan must be imported to app-private storage.", plan.isFile)
        assertTrue("Integrity metadata must be committed with the imported plan.", metadataFile.isFile)
        assertTrue("The canonical plan must define multiple objectives.", first.optInt("objective_count") >= 5)
        assertTrue("The plan must expose Objective A phases.", first.optInt("phase_count") > 0)

        val packaged = context.assets.open("friday/FRIDAY_DELIVERY_PLAN.md")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(packaged, plan.readText(Charsets.UTF_8))
        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(packaged.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals(expectedHash, JSONObject(metadataFile.readText()).getString("content_sha256"))
        assertEquals(
            BuildConfig.SOURCE_REVISION.takeIf { it.matches(Regex("[0-9a-fA-F]{40}")) }?.lowercase() ?: "unknown",
            JSONObject(metadataFile.readText()).getString("source_revision_at_install")
        )

        val assessed = store.ensurePhaseAssessmentPackets()
        val taskCount = assessed.optJSONArray("tasks")?.length() ?: 0
        assertTrue("Canonical phases must become durable assessment work packets.", taskCount > 0)

        val recovered = store.reconcile()
        assertEquals("Reconciliation must preserve accepted task history.", taskCount, recovered.optJSONArray("tasks")?.length() ?: 0)
        assertFalse("Reconciliation must not silently discard the canonical source hash.", recovered.optString("plan_sha256").isBlank())
    }
}
