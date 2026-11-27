package ai.friday.assistant.aiworkforce.table

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.concurrent.TimeUnit

fun interface ChatGPTPlanTokenProvider {
    suspend fun validAccessToken(): String?
}

/**
 * Implements OpenAI's documented open-source Sign in with ChatGPT flow:
 * dynamic client registration, loopback callback, PKCE, ID-token verification,
 * and ChatGPT-plan Responses API authorization. No API key or client secret.
 */
class ChatGPTPlanAuth(
    context: Context,
    private val credentials: ChatGPTPlanCredentialStore =
        ChatGPTPlanCredentialStore(context.applicationContext),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val discoveryEndpoint: String = DISCOVERY_ENDPOINT,
    private val trustedEndpointOrigin: String = "https://auth.openai.com"
) : ChatGPTPlanTokenProvider {
    @Volatile private var cachedDiscovery: JSONObject? = null
    @Volatile private var cachedDiscoveryAt = 0L

    suspend fun signIn(activity: Activity): String = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
        val oidc = openIdConfiguration()
        val existing = credentials.read()
        val registration = credentials.readRegistration()
        val pendingClientId = credentials.pendingClientId()
        val server = ServerSocket()
        try {
            server.reuseAddress = false
            server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1)
            server.soTimeout = ACCEPT_POLL_MS
            val redirectUri = "http://127.0.0.1:${server.localPort}/auth/callback"
            val state = randomUrlSafe(32)
            val nonce = randomUrlSafe(32)
            val verifier = randomUrlSafe(32)
            val challenge = base64Url(MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(Charsets.US_ASCII)))
            val firstRegistration = existing == null && registration == null && pendingClientId == null
            val clientId = existing?.clientId ?: registration?.clientId ?: pendingClientId ?: DYNAMIC_CLIENT_ID

            val auth = Uri.parse(oidc.getString("authorization_endpoint")).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .apply {
                    if (firstRegistration) appendQueryParameter("agent_name_hint", APP_NAME)
                }
                .appendQueryParameter("ext_agent_host_id", credentials.hostId())
                .apply {
                    if (!firstRegistration) {
                        existing?.idToken?.let { appendQueryParameter("id_token_hint", it) }
                        (existing?.email ?: registration?.email)?.let { appendQueryParameter("login_hint", it) }
                    }
                }
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("scope", "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct")
                .apply {
                    // If identity was previously granted without plan use, explicitly
                    // reopen the consent step so the owner can enable the missing scope.
                    if (existing != null && REQUIRED_SCOPE !in existing.scopes) {
                        appendQueryParameter("prompt", "consent")
                    }
                }
                .appendQueryParameter("resource", RESOURCE)
                .appendQueryParameter("state", state)
                .appendQueryParameter("nonce", nonce)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .build()

            withContext(Dispatchers.Main) {
                activity.startActivity(Intent(Intent.ACTION_VIEW, auth))
            }

            val callback = awaitCallback(server, state, android.os.SystemClock.elapsedRealtime() + CALLBACK_TIMEOUT_MS)
            require(constantTimeEquals(state, callback.getQueryParameter("state").orEmpty())) {
                "ChatGPT sign-in state validation failed."
            }
            val oauthError = callback.getQueryParameter("error")
            require(oauthError.isNullOrBlank()) {
                if (oauthError == "access_denied") "ChatGPT authorization was declined."
                else "ChatGPT authorization failed: ${oauthError ?: "unknown error"}."
            }
            val code = callback.getQueryParameter("code").orEmpty()
            require(code.isNotBlank()) { "ChatGPT did not return an authorization code." }

            val issuedClientId = callback.getQueryParameter("client_id")
                ?: existing?.clientId
                ?: registration?.clientId
                ?: pendingClientId
                ?: error("OpenAI did not return the newly issued client ID.")
            require(issuedClientId.startsWith("oaiapp_")) { "OpenAI returned an invalid issued client ID." }
            if (existing != null && issuedClientId != existing.clientId) {
                error("OpenAI returned a different client registration; refusing to replace the selected account.")
            }
            if (registration != null && issuedClientId != registration.clientId) {
                error("OpenAI returned a different client registration; refusing to replace the saved account mapping.")
            }
            credentials.setPendingClientId(issuedClientId)

            val token = exchangeAuthorizationCode(code, issuedClientId, verifier, redirectUri, oidc.getString("token_endpoint"))
            require(token.optString("token_type").equals("Bearer", ignoreCase = true)) { "OpenAI returned an unsupported token type." }
            val scopes = token.optString("scope").split(' ').filter { it.isNotBlank() }.toSet()
            // Identity sign-in and ChatGPT plan usage are separate grants.
            // Preserve a verified identity-only session so Settings can show
            // the actual state and let the owner explicitly re-authorize plan use.
            val idToken = token.getString("id_token")
            val claims = verifyIdToken(idToken, issuedClientId, nonce, oidc.getString("jwks_uri"))
            val subject = claims.getString("sub")
            if ((existing?.subject ?: registration?.subject)?.let { it != subject } == true) {
                error("The newly authorized ChatGPT account does not match the previously selected account.")
            }
            val expiresIn = token.optLong("expires_in", 0L)
            require(expiresIn > 0L) { "OpenAI token response has no valid expiry." }
            credentials.save(
                ChatGPTPlanCredentialStore.Credentials(
                    clientId = issuedClientId,
                    hostId = credentials.hostId(),
                    subject = subject,
                    email = claims.optString("email").takeIf { it.isNotBlank() },
                    idToken = idToken,
                    accessToken = token.getString("access_token"),
                    refreshToken = token.getString("refresh_token"),
                    expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + expiresIn,
                    scopes = scopes,
                    modelSlug = existing?.modelSlug
                )
            )
            if (REQUIRED_SCOPE in scopes) {
                "Connected to ChatGPT plan usage for ${claims.optString("email").ifBlank { "your authorized account" }}."
            } else {
                "Signed in to ChatGPT as ${claims.optString("email").ifBlank { "your account" }}, but plan usage was not authorized. Reconnect and choose Use your ChatGPT plan to enable AI requests."
            }
        } finally {
            runCatching { server.close() }
        }
        }
    }

    override suspend fun validAccessToken(): String? = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
        val current = credentials.read() ?: return@withContext null
        if (REQUIRED_SCOPE !in current.scopes) return@withContext null
        val now = System.currentTimeMillis() / 1000L
        if (current.expiresAtEpochSeconds > now + TOKEN_REFRESH_SKEW_SECONDS) {
            return@withContext current.accessToken
        }
        val oidc = openIdConfiguration()
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", current.clientId)
            .add("refresh_token", current.refreshToken)
            .add("resource", RESOURCE)
            .build()
        val refreshed = executeTokenRequest(form, oidc.getString("token_endpoint")) ?: return@withContext null
        if (!refreshed.optString("token_type").equals("Bearer", ignoreCase = true)) return@withContext null
        val newScopes = refreshed.optString("scope")
            .takeIf { it.isNotBlank() }?.split(' ')?.filter { it.isNotBlank() }?.toSet()
            ?: current.scopes
        if (REQUIRED_SCOPE !in newScopes) return@withContext null
        val access = refreshed.optString("access_token").takeIf { it.isNotBlank() }
            ?: return@withContext null
        val refresh = refreshed.optString("refresh_token").takeIf { it.isNotBlank() }
            ?: current.refreshToken
        val idToken = refreshed.optString("id_token").takeIf { it.isNotBlank() } ?: current.idToken
        if (idToken != current.idToken) {
            val claims = verifyIdToken(idToken, current.clientId, expectedNonce = null, jwksEndpoint = oidc.getString("jwks_uri"))
            if (claims.optString("sub") != current.subject) return@withContext null
        }
        val expiresIn = refreshed.optLong("expires_in", 0L)
        if (expiresIn <= 0L) return@withContext null
        credentials.save(current.copy(
            idToken = idToken,
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = now + expiresIn,
            scopes = newScopes
        ))
        access
        }
    }

    suspend fun disconnect(): String = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
        val current = credentials.read()
            ?: return@withContext "ChatGPT tokens are already cleared; the saved account registration is retained."
        var confirmed = false
        var lastFailure = "OpenAI revocation endpoint could not be reached."
        for (attempt in 0..2) {
            try {
                val endpoint = openIdConfiguration().getString("revocation_endpoint")
                val form = FormBody.Builder()
                    .add("token", current.refreshToken)
                    .add("token_type_hint", "refresh_token")
                    .add("client_id", current.clientId)
                    .build()
                val request = Request.Builder().url(endpoint).post(form).build()
                val responseCode = http.newCall(request).execute().use { it.code }
                if (responseCode == 200) { confirmed = true; break }
                lastFailure = "OpenAI token revocation returned HTTP $responseCode."
                if (responseCode !in 500..599) break
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                lastFailure = failure.javaClass.simpleName
            }
            if (attempt < 2) delay(500L shl attempt)
        }
        credentials.clearTokensPreserveRegistration()
        if (confirmed) {
            "ChatGPT renewable session revoked; local access/refresh/ID tokens cleared. Account registration retained."
        } else {
            "Disconnected locally and cleared tokens, but remote revocation was not confirmed ($lastFailure). You can also disconnect Friday in ChatGPT Settings."
        }
        }
    }

    /** True when a verified ChatGPT identity is stored, even without plan-use consent. */
    fun isSignedIn(): Boolean = credentials.read()?.subject?.isNotBlank() == true

    /** True only when OpenAI granted the optional direct ChatGPT-plan usage scope. */
    fun isConnected(): Boolean = credentials.read()?.let { REQUIRED_SCOPE in it.scopes } == true
    fun accountLabel(): String = credentials.read()?.email
        ?: credentials.readRegistration()?.email
        ?: "ChatGPT not connected"

    private fun exchangeAuthorizationCode(code: String, clientId: String, verifier: String, redirectUri: String, tokenEndpoint: String): JSONObject {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("code", code)
            .add("code_verifier", verifier)
            .add("redirect_uri", redirectUri)
            .add("resource", RESOURCE)
            .build()
        return executeTokenRequest(form, tokenEndpoint) ?: error("OpenAI token exchange failed; no credentials were saved.")
    }

    private fun executeTokenRequest(form: RequestBody, tokenEndpoint: String): JSONObject? {
        val request = Request.Builder().url(tokenEndpoint).header("Accept", "application/json").post(form).build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return null
            return JSONObject(body)
        }
    }

    private suspend fun awaitCallback(server: ServerSocket, expectedState: String, deadlineElapsedMs: Long): Uri {
        while (true) {
            currentCoroutineContext().ensureActive()
            if (android.os.SystemClock.elapsedRealtime() >= deadlineElapsedMs) {
                throw SocketTimeoutException("ChatGPT sign-in callback timed out.")
            }
            val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
            socket.soTimeout = SOCKET_TIMEOUT_MS
            try {
                val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                val requestLine = reader.readLine().orEmpty()
                while (true) {
                    val header = reader.readLine() ?: break
                    if (header.isEmpty()) break
                }
                val parts = requestLine.split(' ')
                if (parts.size < 2 || parts[0] != "GET") {
                    respond(socket, 400, "Invalid callback request.")
                    continue
                }
                val uri = Uri.parse("http://127.0.0.1:${server.localPort}${parts[1]}")
                if (uri.path != CALLBACK_PATH) {
                    respond(socket, 404, "Not found.")
                    continue
                }
                if (!constantTimeEquals(expectedState, uri.getQueryParameter("state").orEmpty())) {
                    respond(socket, 400, "Invalid sign-in state.")
                    continue
                }
                respond(socket, 200, "ChatGPT sign-in response received. You can return to Friday.")
                return uri
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    private fun respond(socket: Socket, code: Int, message: String) {
        val body = "<!doctype html><html><body><h3>Friday</h3><p>$message</p></body></html>".toByteArray(Charsets.UTF_8)
        val reason = if (code == 200) "OK" else if (code == 404) "Not Found" else "Bad Request"
        val headers = "HTTP/1.1 $code $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(headers.toByteArray(Charsets.US_ASCII))
            write(body)
            flush()
        }
    }

    internal fun verifyIdToken(idToken: String, clientId: String, expectedNonce: String?, jwksEndpoint: String? = null): JSONObject {
        val parts = idToken.split('.')
        require(parts.size == 3) { "OpenAI ID token is malformed." }
        val header = JSONObject(String(decodeUrl(parts[0]), Charsets.UTF_8))
        require(header.optString("alg") == "RS256") { "OpenAI ID token uses an unsupported signature algorithm." }
        val kid = header.optString("kid")
        require(kid.isNotBlank()) { "OpenAI ID token has no signing key identifier." }
        val trustedJwksEndpoint = jwksEndpoint ?: openIdConfiguration().getString("jwks_uri")
        val jwksRequest = Request.Builder().url(trustedJwksEndpoint).get().build()
        val jwks = http.newCall(jwksRequest).execute().use { response ->
            require(response.isSuccessful) { "OpenAI signing keys could not be retrieved." }
            JSONObject(response.body?.string().orEmpty())
        }
        val keys = jwks.optJSONArray("keys") ?: error("OpenAI signing key set is malformed.")
        val jwk = (0 until keys.length()).asSequence()
            .map { keys.getJSONObject(it) }
            .firstOrNull { it.optString("kid") == kid && it.optString("kty") == "RSA" &&
                (it.optString("use").isBlank() || it.optString("use") == "sig") &&
                (it.optString("alg").isBlank() || it.optString("alg") == "RS256") }
            ?: error("OpenAI signing key was not found.")
        val modulus = BigInteger(1, decodeUrl(jwk.getString("n")))
        val exponent = BigInteger(1, decodeUrl(jwk.getString("e")))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update((parts[0] + "." + parts[1]).toByteArray(Charsets.US_ASCII))
        require(verifier.verify(decodeUrl(parts[2]))) { "OpenAI ID token signature is invalid." }

        val claims = JSONObject(String(decodeUrl(parts[1]), Charsets.UTF_8))
        require(claims.optString("iss") == ISSUER) { "OpenAI ID token issuer is invalid." }
        val audience = claims.opt("aud")
        val audienceMatches = when (audience) {
            is String -> audience == clientId
            is JSONArray -> (0 until audience.length()).any { audience.optString(it) == clientId }
            null -> false
            else -> false
        }
        require(audienceMatches) { "OpenAI ID token audience does not match Friday's issued client ID." }
        if (audience is JSONArray && audience.length() > 1) {
            require(claims.optString("azp") == clientId) { "OpenAI ID token authorized party is invalid." }
        }
        val now = System.currentTimeMillis() / 1000L
        require(claims.has("exp") && claims.optLong("exp", 0L) > now) { "OpenAI ID token has expired." }
        require(claims.has("iat")) { "OpenAI ID token has no issued-at time." }
        require(claims.optLong("nbf", 0L) <= now + CLOCK_SKEW_SECONDS) { "OpenAI ID token is not yet valid." }
        require(claims.optLong("iat", now + 1L) <= now + CLOCK_SKEW_SECONDS) { "OpenAI ID token was issued in the future." }
        require(claims.optString("sub").isNotBlank()) { "OpenAI ID token has no account subject." }
        if (expectedNonce != null) {
            require(constantTimeEquals(expectedNonce, claims.optString("nonce"))) { "OpenAI ID token nonce validation failed." }
        }
        return claims
    }

    @Synchronized
    private fun openIdConfiguration(): JSONObject {
        val now = System.currentTimeMillis()
        cachedDiscovery?.let { cached ->
            if (now - cachedDiscoveryAt < DISCOVERY_CACHE_MS) return cached
        }
        val request = Request.Builder().url(discoveryEndpoint).header("Accept", "application/json").get().build()
        val configuration = http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "OpenAI OpenID discovery returned HTTP ${response.code}." }
            JSONObject(response.body?.string().orEmpty())
        }
        require(configuration.optString("issuer") == ISSUER) { "OpenAI discovery issuer does not match the production issuer." }
        listOf("authorization_endpoint", "token_endpoint", "jwks_uri", "revocation_endpoint").forEach { key ->
            val endpoint = Uri.parse(configuration.getString(key))
            val trustedOrigin = Uri.parse(trustedEndpointOrigin)
            require(
                endpoint.scheme == trustedOrigin.scheme &&
                    endpoint.host == trustedOrigin.host &&
                    endpoint.port == trustedOrigin.port
            ) {
                "OpenAI discovery returned an untrusted $key endpoint."
            }
        }
        cachedDiscovery = configuration
        cachedDiscoveryAt = now
        return configuration
    }

    private fun randomUrlSafe(bytes: Int): String {
        val random = ByteArray(bytes)
        SecureRandom().nextBytes(random)
        return base64Url(random)
    }

    private fun base64Url(bytes: ByteArray): String = android.util.Base64.encodeToString(
        bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
    )

    private fun decodeUrl(value: String): ByteArray = android.util.Base64.decode(
        value, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
    )

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8)
    )

    companion object {
        const val ISSUER = "https://auth.openai.com"
        const val RESOURCE = "https://api.openai.com/v1"
        const val DISCOVERY_ENDPOINT = "https://auth.openai.com/.well-known/openid-configuration"
        const val REQUIRED_SCOPE = "chatgpt.tokens.use.direct"
        private const val DYNAMIC_CLIENT_ID = "dynamic_agent_client"
        private const val APP_NAME = "Friday"
        private const val CALLBACK_PATH = "/auth/callback"
        private const val CALLBACK_TIMEOUT_MS = 5 * 60 * 1000L
        private const val ACCEPT_POLL_MS = 1_000
        private const val SOCKET_TIMEOUT_MS = 15_000
        private const val TOKEN_REFRESH_SKEW_SECONDS = 90L
        private const val CLOCK_SKEW_SECONDS = 5L
        private const val DISCOVERY_CACHE_MS = 10 * 60 * 1000L
        private val refreshMutex = Mutex()
    }
}
