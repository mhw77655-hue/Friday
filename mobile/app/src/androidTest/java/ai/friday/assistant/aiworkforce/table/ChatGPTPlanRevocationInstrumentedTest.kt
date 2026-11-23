package ai.friday.assistant.aiworkforce.table

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ChatGPTPlanRevocationInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun disconnectRevokesRefreshTokenAndRetainsEncryptedAccountRegistration() {
        val server = MockWebServer()
        server.start()
        try {
            val store = ChatGPTPlanCredentialStore(context)
            store.clear()
            store.save(ChatGPTPlanCredentialStore.Credentials(
                clientId = "oaiapp_revocation_test",
                hostId = store.hostId(),
                subject = "stable-account-subject",
                email = "account@example.invalid",
                idToken = "id-token-secret",
                accessToken = "access-token-secret",
                refreshToken = "refresh-token-secret",
                expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + 3600,
                scopes = setOf("openid", "offline_access", "chatgpt.tokens.use.direct"),
                modelSlug = "test-model"
            ))
            server.enqueue(MockResponse().setBody(
                JSONObject()
                    .put("issuer", ChatGPTPlanAuth.ISSUER)
                    .put("authorization_endpoint", "https://auth.openai.com/api/accounts/authorize")
                    .put("token_endpoint", "https://auth.openai.com/api/accounts/oauth/token")
                    .put("jwks_uri", "https://auth.openai.com/.well-known/jwks.json")
                    .put("revocation_endpoint", server.url("/revoke").toString())
                    .toString()
            ))
            server.enqueue(MockResponse().setResponseCode(200).setBody(""))

            val auth = ChatGPTPlanAuth(
                context = context,
                credentials = store,
                http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
                discoveryEndpoint = server.url("/.well-known/openid-configuration").toString()
            )
            val result = kotlinx.coroutines.runBlocking { auth.disconnect() }

            assertTrue(result.contains("renewable session revoked"))
            assertNull(store.read())
            assertEquals("oaiapp_revocation_test", store.readRegistration()?.clientId)
            assertEquals("stable-account-subject", store.readRegistration()?.subject)

            val discovery = server.takeRequest()
            assertEquals("GET", discovery.method)
            assertEquals("/.well-known/openid-configuration", discovery.path)
            val revoke = server.takeRequest()
            assertEquals("POST", revoke.method)
            assertEquals("/revoke", revoke.path)
            val form = revoke.body.readUtf8()
            assertTrue(form.contains("token=refresh-token-secret"))
            assertTrue(form.contains("token_type_hint=refresh_token"))
            assertTrue(form.contains("client_id=oaiapp_revocation_test"))
        } finally {
            server.shutdown()
        }
    }
}
