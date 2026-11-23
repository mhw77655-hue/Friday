package ai.friday.assistant.aiworkforce.table

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ChatGPTPlanIdTokenInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun verifiesOpenAiIssuerAudienceNonceExpiryAndRsaSignature() {
        val server = MockWebServer()
        server.start()
        try {
            val pair = rsaKeyPair()
            val jwk = JSONObject()
                .put("kid", "test-key")
                .put("kty", "RSA")
                .put("use", "sig")
                .put("alg", "RS256")
                .put("n", encodeUrl(unsigned(pair.public.encodedModulus())))
                .put("e", encodeUrl(unsigned(pair.public.encodedExponent())))
            server.enqueue(MockResponse().setBody(JSONObject().put("keys", org.json.JSONArray().put(jwk)).toString()))

            val clientId = "oaiapp_friday_test"
            val nonce = "nonce-for-test"
            val now = System.currentTimeMillis() / 1000L
            val header = encodeUrl("""{"alg":"RS256","kid":"test-key","typ":"JWT"}""".toByteArray())
            val payload = encodeUrl(JSONObject()
                .put("iss", ChatGPTPlanAuth.ISSUER)
                .put("aud", clientId)
                .put("sub", "account-subject")
                .put("nonce", nonce)
                .put("iat", now)
                .put("exp", now + 600)
                .toString().toByteArray())
            val input = "$header.$payload"
            val signer = Signature.getInstance("SHA256withRSA")
            signer.initSign(pair.private)
            signer.update(input.toByteArray(Charsets.US_ASCII))
            val token = "$input.${encodeUrl(signer.sign())}"

            val auth = ChatGPTPlanAuth(
                context = context,
                http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
            )
            val claims = auth.verifyIdToken(token, clientId, nonce, server.url("/jwks").toString())
            assertEquals("account-subject", claims.getString("sub"))
            assertEquals(clientId, claims.getString("aud"))
        } finally {
            server.shutdown()
        }
    }

    private fun rsaKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }

    private fun encodeUrl(bytes: ByteArray): String = Base64.encodeToString(
        bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    private fun unsigned(bytes: ByteArray): ByteArray =
        if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes

    private fun java.security.PublicKey.encodedModulus(): ByteArray =
        (this as java.security.interfaces.RSAPublicKey).modulus.toByteArray()

    private fun java.security.PublicKey.encodedExponent(): ByteArray =
        (this as java.security.interfaces.RSAPublicKey).publicExponent.toByteArray()
}
