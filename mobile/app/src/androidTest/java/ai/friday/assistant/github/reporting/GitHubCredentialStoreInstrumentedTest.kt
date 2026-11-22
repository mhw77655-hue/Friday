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
}
