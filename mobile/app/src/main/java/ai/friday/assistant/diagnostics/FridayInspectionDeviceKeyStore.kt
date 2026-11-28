package ai.friday.assistant.diagnostics

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Device identity key. The private key is generated and retained by Android Keystore. */
class FridayInspectionDeviceKeyStore {
    fun getOrCreateKeyPair(): KeyPair {
        val store = keyStore()
        val existing = store.getCertificate(KEY_ALIAS)
        if (existing != null) {
            return KeyPair(existing.publicKey, store.getKey(KEY_ALIAS, null) as PrivateKey)
        }
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKeyPair()
    }

    fun publicKeyDerBase64(): String =
        Base64.encodeToString(getOrCreateKeyPair().public.encoded, Base64.NO_WRAP)

    fun sign(message: String): String {
        val pair = getOrCreateKeyPair()
        return Base64.encodeToString(
            Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private)
                update(message.toByteArray(Charsets.UTF_8))
                sign()
            },
            Base64.NO_WRAP
        )
    }

    fun revokeLocalKey() {
        val store = keyStore()
        if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "friday_live_inspection_device_p256_v1"
    }
}
