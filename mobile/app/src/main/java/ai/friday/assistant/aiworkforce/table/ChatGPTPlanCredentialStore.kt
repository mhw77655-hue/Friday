package ai.friday.assistant.aiworkforce.table

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Per-installation ChatGPT-plan credentials encrypted by a non-exportable
 * Android Keystore AES-GCM key. Tokens never enter GitHub reports or Table logs.
 */
class ChatGPTPlanCredentialStore(context: Context) {
    data class Credentials(
        val clientId: String,
        val hostId: String,
        val subject: String,
        val email: String?,
        val idToken: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresAtEpochSeconds: Long,
        val scopes: Set<String>,
        val modelSlug: String?
    )

    data class Registration(val clientId: String, val hostId: String, val subject: String, val email: String?)

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun hostId(): String {
        prefs.getString(KEY_HOST_ID, null)?.let { return it }
        val value = "urn:uuid:${UUID.randomUUID()}"
        prefs.edit().putString(KEY_HOST_ID, value).commit()
        return value
    }

    @Synchronized
    fun read(): Credentials? = runCatching {
        val ciphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        val json = JSONObject(String(
            cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
            Charsets.UTF_8
        ))
        Credentials(
            clientId = json.getString("client_id"),
            hostId = json.getString("host_id"),
            subject = json.getString("subject"),
            email = json.optString("email").takeIf { it.isNotBlank() },
            idToken = json.getString("id_token"),
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAtEpochSeconds = json.getLong("expires_at_epoch_seconds"),
            scopes = json.getString("scopes").split(' ').filter { it.isNotBlank() }.toSet(),
            modelSlug = json.optString("model_slug").takeIf { it.isNotBlank() }
        ).takeIf { it.clientId.startsWith("oaiapp_") && it.subject.isNotBlank() }
    }.getOrNull()

    @Synchronized
    fun readRegistration(): Registration? = runCatching {
        val ciphertext = prefs.getString(KEY_REG_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_REG_IV, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        val json = JSONObject(String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8))
        Registration(
            clientId = json.getString("client_id"),
            hostId = json.getString("host_id"),
            subject = json.getString("subject"),
            email = json.optString("email").takeIf { it.isNotBlank() }
        ).takeIf { it.clientId.startsWith("oaiapp_") && it.subject.isNotBlank() }
    }.getOrNull()

    @Synchronized
    fun pendingClientId(): String? =
        prefs.getString(KEY_PENDING_CLIENT_ID, null)?.takeIf { it.startsWith("oaiapp_") }

    @Synchronized
    fun setPendingClientId(clientId: String) {
        require(clientId.startsWith("oaiapp_"))
        prefs.edit().putString(KEY_PENDING_CLIENT_ID, clientId).commit()
    }

    @Synchronized
    fun clearTokensPreserveRegistration() {
        prefs.edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .remove(KEY_PENDING_CLIENT_ID)
            .commit()
    }

    @Synchronized
    fun save(credentials: Credentials) {
        require(credentials.clientId.startsWith("oaiapp_"))
        require(credentials.subject.isNotBlank())
        require(credentials.accessToken.isNotBlank() && credentials.refreshToken.isNotBlank())
        val json = JSONObject()
            .put("client_id", credentials.clientId)
            .put("host_id", credentials.hostId)
            .put("subject", credentials.subject)
            .put("email", credentials.email ?: JSONObject.NULL)
            .put("id_token", credentials.idToken)
            .put("access_token", credentials.accessToken)
            .put("refresh_token", credentials.refreshToken)
            .put("expires_at_epoch_seconds", credentials.expiresAtEpochSeconds)
            .put("scopes", credentials.scopes.sorted().joinToString(" "))
            .put("model_slug", credentials.modelSlug ?: JSONObject.NULL)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .remove(KEY_PENDING_CLIENT_ID)
            .commit()
        saveRegistration(Registration(
            clientId = credentials.clientId,
            hostId = credentials.hostId,
            subject = credentials.subject,
            email = credentials.email
        ))
    }

    @Synchronized
    fun updateModel(slug: String) {
        val current = read() ?: return
        save(current.copy(modelSlug = slug.take(120)))
    }

    @Synchronized
    fun clear() {
        prefs.edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .remove(KEY_REG_CIPHERTEXT)
            .remove(KEY_REG_IV)
            .remove(KEY_PENDING_CLIENT_ID)
            .commit()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    @Synchronized
    private fun saveRegistration(registration: Registration) {
        val json = JSONObject()
            .put("client_id", registration.clientId)
            .put("host_id", registration.hostId)
            .put("subject", registration.subject)
            .put("email", registration.email ?: JSONObject.NULL)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_REG_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_REG_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "friday.chatgpt.plan.credentials"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "friday_chatgpt_plan_secure"
        private const val KEY_CIPHERTEXT = "credentials_ciphertext"
        private const val KEY_IV = "credentials_iv"
        private const val KEY_REG_CIPHERTEXT = "registration_ciphertext"
        private const val KEY_REG_IV = "registration_iv"
        private const val KEY_PENDING_CLIENT_ID = "pending_issued_client_id"
        private const val KEY_HOST_ID = "agent_host_id"
    }
}
