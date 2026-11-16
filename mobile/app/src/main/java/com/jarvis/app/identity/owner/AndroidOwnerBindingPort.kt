package com.jarvis.app.identity.owner

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android production owner-binding store.
 *
 * AES-256-GCM protects the small authority record with a non-exportable
 * Android Keystore key. A malformed/unreadable existing file is treated as
 * an integrity failure: bindOwner refuses to overwrite it, preventing a
 * corrupt record from silently turning into an unbound account.
 */
class AndroidOwnerBindingPort(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : OwnerBindingPort {
    private val file = File(context.applicationContext.filesDir, "owner/owner-binding.enc")
    private val atomicFile = AtomicFile(file)

    override fun bindOwner(binding: OwnerBinding): Boolean {
        if (file.exists()) {
            val current = ownerBinding() ?: return false
            if (current.relationshipId != binding.relationshipId || current.ownerKey != binding.ownerKey) {
                return false
            }
        }

        return runCatching {
            val encrypted = encrypt(serialize(binding))
            file.parentFile?.mkdirs()
            var stream: FileOutputStream? = null
            try {
                stream = atomicFile.startWrite()
                stream.write(encrypted)
                stream.fd.sync()
                atomicFile.finishWrite(stream)
                stream = null
            } finally {
                if (stream != null) atomicFile.failWrite(stream)
            }
            true
        }.getOrDefault(false)
    }

    override fun ownerBinding(): OwnerBinding? {
        if (!file.exists()) return null
        return runCatching {
            deserialize(decrypt(atomicFile.openRead().use { it.readBytes() }))
        }.getOrNull()
    }

    private fun serialize(binding: OwnerBinding): ByteArray =
        JSONObject().put("relationshipId", binding.relationshipId)
            .put("ownerKey", binding.ownerKey)
            .put("authenticator", binding.authenticator)
            .put("authenticatedAtEpochMs", binding.authenticatedAtEpochMs)
            .toString().toByteArray(Charsets.UTF_8)

    private fun deserialize(bytes: ByteArray): OwnerBinding {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        return OwnerBinding(
            relationshipId = json.getString("relationshipId"),
            ownerKey = json.getString("ownerKey"),
            authenticator = json.getString("authenticator"),
            authenticatedAtEpochMs = json.getLong("authenticatedAtEpochMs")
        )
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return ByteBuffer.allocate(1 + 1 + iv.size + encrypted.size)
            .put(FORMAT_VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(encrypted)
            .array()
    }

    private fun decrypt(encoded: ByteArray): ByteArray {
        require(encoded.size >= 2) { "Owner binding file is truncated" }
        val buffer = ByteBuffer.wrap(encoded)
        require(buffer.get() == FORMAT_VERSION) { "Unsupported owner binding format" }
        val ivSize = buffer.get().toInt() and 0xff
        require(ivSize in 12..16 && buffer.remaining() > ivSize) { "Invalid GCM envelope" }
        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val DEFAULT_KEY_ALIAS = "friday.owner-binding.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val FORMAT_VERSION: Byte = 1
    }
}
