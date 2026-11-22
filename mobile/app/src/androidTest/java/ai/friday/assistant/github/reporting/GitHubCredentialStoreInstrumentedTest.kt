package ai.friday.assistant.github.reporting

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GitHubCredentialStoreInstrumentedTest {
    @Test
    fun credentialRoundTripsThroughAndroidKeystoreAndCanBeRevokedLocally() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = GitHubCredentialStore(context)
        val token = "github_pat_" + "z".repeat(48)
        store.clear()

        store.save(token)
        assertEquals(token, store.read())
        assertEquals(true, store.isConfigured())

        store.clear()
        assertNull(store.read())
        assertEquals(false, store.isConfigured())
    }
    @Test
    fun pendingReportIsPersistedExactlyUntilSuccessfulDelivery() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = GitHubPendingReportStore(context)
        val report = """{"report_type":"FRIDAY_LIVE_TEST_REPORT","schema_version":1,"report_id":"stable-report-id-1234"}"""
        store.clear()

        store.save(report)
        assertEquals(report, store.read())

        // A retry must see the same report id and payload after a new store instance is created.
        assertEquals(report, GitHubPendingReportStore(context).read())

        store.clear()
        assertNull(store.read())
    }

}
