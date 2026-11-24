package ai.friday.assistant.reverseengineering

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledApkAnalyzerInstrumentedTest {
    @Test
    fun listsSelectableLauncherAppsAndAnalyzesInstalledApkStructure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val targets = InstalledApplicationCatalog.list(context)
        assertFalse("Android emulator should expose at least one launcher app", targets.isEmpty())
        assertFalse("Friday must not list itself as an analysis target", targets.any { it.packageName == context.packageName })

        val report = InstalledApkAnalyzer(context).analyze(targets.first())
        assertTrue("APK must have a positive size", report.apkBytes > 0)
        assertEquals("SHA-256 must be 64 hex characters", 64, report.apkSha256.length)
        assertTrue("APK should contain a binary Android manifest", report.manifestBytes > 0)
        assertTrue("APK should contain at least one DEX file", report.dexFiles > 0)
        assertEquals(report.packageName, targets.first().packageName)
        assertTrue("Component inventory must not be negative",
            report.activityCount >= 0 && report.serviceCount >= 0 &&
                report.receiverCount >= 0 && report.providerCount >= 0)

        val saved = ApplicationKnowledgeStore(context).persist(report)
        try {
            assertTrue("Analysis must be persisted in Friday's private workspace", saved.isFile)
            val persisted = JSONObject(saved.readText())
            assertEquals(report.apkSha256, persisted.getString("apk_sha256"))
            assertTrue(persisted.has("requested_permissions"))
            assertTrue(persisted.has("exported_components"))
        } finally {
            saved.delete()
        }
    }
}
