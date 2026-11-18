package com.jarvis.app.model.remote

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
 * Per-provider credentials encrypted with an Android Keystore AES-GCM key.
 * Only ciphertext and IVs are persisted in app-private preferences.
 */
class ProviderCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("friday_provider_secrets", Context.MODE_PRIVATE)

    @Synchronized
    fun save(providerId: String, secret: String) {
        require(providerId.matches(Regex("[a-zA-Z0-9_.-]{1,80}"))) { "Invalid provider id" }
        require(secret.isNotBlank()) { "Credential cannot be blank" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val prefix = providerId
        prefs.edit()
            .putString("$prefix.cipher", Base64.encodeToString(cipher.doFinal(secret.trim().toByteArray()), Base64.NO_WRAP))
            .putString("$prefix.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun read(providerId: String): String? = runCatching {
        require(providerId.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
        val encrypted = prefs.getString("$providerId.cipher", null) ?: return null
        val iv = prefs.getString("$providerId.iv", null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8).takeIf { it.isNotBlank() }
    }.getOrNull()

    @Synchronized
    fun clear(providerId: String) {
        prefs.edit().remove("$providerId.cipher").remove("$providerId.iv").apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            generateKey()
        }
    }

    companion object {
        private const val KEY_ALIAS = "friday.ai.providers.credentials.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
