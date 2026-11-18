package com.jarvis.app.model.remote

import java.net.URI

/**
 * Validates owner-supplied remote endpoints before credentials are attached.
 * HTTPS is mandatory; only the packaged 127.0.0.1 loopback exception may use HTTP.
 */
object ProviderEndpointPolicy {
    fun validate(raw: String, allowInsecureLoopbackHttp: Boolean = false): String {
        val uri = runCatching { URI(raw.trim()) }.getOrElse {
            throw IllegalArgumentException("Provider endpoint is not a valid URL")
        }
        require(uri.scheme == "https" || uri.scheme == "http") { "Provider endpoint must use HTTP or HTTPS" }
        require(uri.userInfo == null) { "Credentials must not be embedded in endpoint URLs" }
        require(uri.query == null && uri.fragment == null) { "Provider endpoint cannot contain query strings or fragments" }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Provider endpoint must include a host")
        // Match Android's packaged network_security_config.xml exactly:
        // cleartext is allowed only for the explicit IPv4 loopback endpoint.
        val local = host == "127.0.0.1"
        require(uri.scheme == "https" || (allowInsecureLoopbackHttp && local)) {
            "HTTPS is required; HTTP is allowed only for explicitly approved 127.0.0.1 loopback"
        }
        return uri.toString().trimEnd('/')
    }
}
