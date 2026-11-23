package ai.friday.assistant.aiworkforce.table

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatGPTPlanSeatInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun credentialsAreEncryptedAndRoundTripThroughAndroidKeystore() {
        val store = ChatGPTPlanCredentialStore(context)
        store.clear()
        val hostId = store.hostId()
        val credentials = ChatGPTPlanCredentialStore.Credentials(
            clientId = "oaiapp_test_client",
            hostId = hostId,
            subject = "subject-test",
            email = "friday-test@example.invalid",
            idToken = "id-token-secret",
            accessToken = "access-token-secret",
            refreshToken = "refresh-token-secret",
            expiresAtEpochSeconds = 4_000_000_000L,
            scopes = setOf("openid", "chatgpt.tokens.use.direct"),
            modelSlug = "test-model"
        )
        store.save(credentials)

        assertEquals(credentials, store.read())
        assertEquals(hostId, store.hostId())

        val prefs = context.getSharedPreferences("friday_chatgpt_plan_secure", Context.MODE_PRIVATE)
        val ciphertext = prefs.getString("credentials_ciphertext", "").orEmpty()
        assertTrue(ciphertext.isNotBlank())
        assertFalse(ciphertext.contains("access-token-secret"))
        assertFalse(ciphertext.contains("refresh-token-secret"))

        store.clear()
        assertEquals(null, store.read())
    }

    @Test
    fun signInDoesNotImplicitlyEnableBackgroundUse() {
        val consent = ChatGPTPlanBackgroundConsentStore(context)
        consent.setEnabled(false)
        assertFalse(consent.isEnabled())
        consent.setEnabled(true)
        assertTrue(consent.isEnabled())
        consent.setEnabled(false)
        assertFalse(consent.isEnabled())
    }

    @Test
    fun privateDigestIsRejectedBeforeAnyNetworkCall() = kotlinx.coroutines.runBlocking {
        val store = ChatGPTPlanCredentialStore(context)
        store.clear()
        val seat = ChatGPTPlanSeat(context)
        val reply = seat.speak(
            SeatRequest(
                turnId = "privacy-test",
                digest = "private conversation text",
                delta = "private details",
                roleCard = SeatRoleCard("reviewer", "Review only."),
                dataClass = SeatDataClass.PRIVATE,
                externallyShareable = false
            )
        )
        assertTrue(reply.passed)
        assertEquals(SeatPresence.DISABLED, reply.presence)
        assertNotNull(reply.error)
    }
}
