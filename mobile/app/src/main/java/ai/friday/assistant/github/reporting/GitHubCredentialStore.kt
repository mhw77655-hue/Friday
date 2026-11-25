package ai.friday.assistant.github.reporting

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Repository-scoped GitHub credential storage.
 * The token is encrypted with a non-exportable Android Keystore AES key.
 * It is never included in diagnostic reports or log messages.
 */
class GitHubCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(token: String) = synchronized(credentialLock) {
        require(isPlausibleToken(token)) { "GitHub token is blank or malformed." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.trim().toByteArray(Charsets.UTF_8))
        check(
            prefs.edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        ) { "Could not durably save the GitHub credential." }
    }

    fun read(): String? = synchronized(credentialLock) { runCatching {
        val encrypted = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
            .takeIf(::isPlausibleToken)
    }.getOrNull() }

    fun isConfigured(): Boolean = read() != null

    fun clear() {
        synchronized(credentialLock) {
            check(
                prefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).commit()
            ) { "Could not durably clear the GitHub credential." }
            runCatching {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                keyStore.deleteEntry(KEY_ALIAS)
            }
        }
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
        // Synchronize all instances: UI, workers and report clients construct
        // independent wrappers over one SharedPreferences file and Keystore key.
        private val credentialLock = Any()
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "friday.github.device-report"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFS_NAME = "friday_github_secure"
        private const val KEY_CIPHERTEXT = "token_ciphertext"
        private const val KEY_IV = "token_iv"

        internal fun isPlausibleToken(token: String): Boolean =
            token.trim().length in 20..512 && token.none { it.isWhitespace() || it.isISOControl() }
    }
}
