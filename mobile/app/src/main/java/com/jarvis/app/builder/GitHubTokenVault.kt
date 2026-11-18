package com.jarvis.app.builder

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
 * Narrow phone-side credential boundary for dispatching the repository workflow.
 *
 * The only accepted credential is a fine-grained GitHub token with Actions:write
 * on the single Friday repository. It is encrypted with an Android Keystore
 * AES-GCM key; it is never embedded in source, sent in a request body, or logged.
 * This token cannot push commits or write repository contents.
 */
interface RepositoryBuildSettings {
    fun hasToken(): Boolean
    fun token(): String?
    fun automaticRequestsEnabled(): Boolean
}

class GitHubTokenVault(context: Context) : RepositoryBuildSettings {
    private val preferences = context.applicationContext.getSharedPreferences(
        "friday_repository_builder",
        Context.MODE_PRIVATE
    )

    override fun hasToken(): Boolean = runCatching { !token().isNullOrBlank() }.getOrDefault(false)

    override fun token(): String? {
        val ivText = preferences.getString(KEY_IV, null) ?: return null
        val cipherText = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = Base64.decode(ivText, Base64.NO_WRAP)
        val encrypted = Base64.decode(cipherText, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun saveToken(rawToken: String) {
        val value = rawToken.trim()
        require(value.length <= 255 && FINE_GRAINED_TOKEN.matches(value)) {
            "Use a fine-grained GitHub token with the github_pat_ prefix."
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val saved = preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
        check(saved) { "Could not persist the encrypted repository token." }
    }

    override fun automaticRequestsEnabled(): Boolean =
        preferences.getBoolean(KEY_AUTOMATIC, false)

    fun setAutomaticRequestsEnabled(enabled: Boolean) {
        require(!enabled || hasToken()) { "Save the fine-grained Actions token before enabling automatic requests." }
        check(preferences.edit().putBoolean(KEY_AUTOMATIC, enabled).commit()) {
            "Could not persist the repository-builder consent setting."
        }
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .putBoolean(KEY_AUTOMATIC, false)
            .commit()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
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

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "friday.github.actions.dispatch.v1"
        const val KEY_IV = "actions_token_iv"
        const val KEY_CIPHERTEXT = "actions_token_ciphertext"
        const val KEY_AUTOMATIC = "automatic_repository_requests"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val FINE_GRAINED_TOKEN = Regex("^github_pat_[A-Za-z0-9_]{20,}$")
    }
}
