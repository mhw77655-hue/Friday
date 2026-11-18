package com.jarvis.app.model.remote

import java.net.URI

/**
 * Validates owner-supplied remote endpoints before credentials are attached.
 * HTTPS is mandatory except loopback/LAN HTTP endpoints explicitly opted in.
 */
object ProviderEndpointPolicy {
    fun validate(raw: String, allowInsecureLocalNetwork: Boolean = false): String {
        val uri = runCatching { URI(raw.trim()) }.getOrElse {
            throw IllegalArgumentException("Provider endpoint is not a valid URL")
        }
        require(uri.scheme == "https" || uri.scheme == "http") { "Provider endpoint must use HTTP or HTTPS" }
        require(uri.userInfo == null) { "Credentials must not be embedded in endpoint URLs" }
        require(uri.query == null && uri.fragment == null) { "Provider endpoint cannot contain query strings or fragments" }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Provider endpoint must include a host")
        val local = host == "localhost" || host == "127.0.0.1" || host == "::1" ||
            host.endsWith(".local") || host.startsWith("192.168.") ||
            host.startsWith("10.") || host.matches(Regex("172\\.(1[6-9]|2[0-9]|3[01])\\..*"))
        require(uri.scheme == "https" || (allowInsecureLocalNetwork && local)) {
            "HTTPS is required; HTTP is allowed only for explicitly approved local endpoints"
        }
        return uri.toString().trimEnd('/')
    }
}
