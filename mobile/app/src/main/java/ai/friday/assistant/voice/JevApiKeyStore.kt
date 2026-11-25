package ai.friday.assistant.voice

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the user's TypeSafe credential encrypted with a non-exportable Android Keystore key. */
class JevApiKeyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jev_secure", Context.MODE_PRIVATE)

    fun save(apiKey: String) = synchronized(credentialLock) {
        require(apiKey.isNotBlank()) { "Jev API key cannot be blank" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        check(
            prefs.edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        ) { "Could not durably save the Jev credential." }
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
            .takeIf { it.isNotBlank() }
    }.getOrNull() }

    fun clear() {
        synchronized(credentialLock) {
            // Avoid waking Android Keystore when no credential was ever stored.
            // This is the common first-run path and must remain a fast, reliable
            // Settings action. Once ciphertext is durably removed, it is no longer
            // decryptable even if best-effort key cleanup later fails.
            val hadCredential = prefs.contains(KEY_CIPHERTEXT) || prefs.contains(KEY_IV)
            check(prefs.edit().clear().commit()) { "Could not durably clear the Jev credential." }
            if (!hadCredential) return
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
        // @Synchronized would protect only one JevApiKeyStore instance. Settings,
        // the engine and background workers construct separate instances over
        // the same SharedPreferences file and Keystore alias, so all instances
        // must serialize the complete encrypt/persist/decrypt/delete transaction.
        private val credentialLock = Any()
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "friday.typesafe.jev.api"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_CIPHERTEXT = "api_ciphertext"
        private const val KEY_IV = "api_iv"
    }
}
