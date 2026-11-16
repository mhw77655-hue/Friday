package com.jarvis.app.identity.owner

import java.io.File
import java.util.Properties

/** JVM-only durable owner-binding store for composition and integration tests. */
internal class FileOwnerBindingPort(private val directory: File) : OwnerBindingPort {
    private val file = File(directory, "owner-binding-test.properties")

    override fun bindOwner(binding: OwnerBinding): Boolean {
        val existing = ownerBinding()
        if (file.exists() && existing == null) return false
        if (existing != null &&
            (existing.relationshipId != binding.relationshipId || existing.ownerKey != binding.ownerKey)
        ) return false
        directory.mkdirs()
        val properties = Properties().apply {
            setProperty("relationshipId", binding.relationshipId)
            setProperty("ownerKey", binding.ownerKey)
            setProperty("authenticator", binding.authenticator)
            setProperty("authenticatedAtEpochMs", binding.authenticatedAtEpochMs.toString())
        }
        file.outputStream().use { properties.store(it, "test owner binding") }
        return true
    }

    override fun ownerBinding(): OwnerBinding? {
        if (!file.isFile) return null
        return runCatching {
            val properties = Properties().apply { file.inputStream().use(::load) }
            OwnerBinding(
                relationshipId = properties.getProperty("relationshipId"),
                ownerKey = properties.getProperty("ownerKey"),
                authenticator = properties.getProperty("authenticator"),
                authenticatedAtEpochMs = properties.getProperty("authenticatedAtEpochMs").toLong()
            )
        }.getOrNull()
    }
}
