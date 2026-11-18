package com.jarvis.app.model.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderEndpointPolicyTest {
    @Test fun acceptsHttpsProviderEndpoint() {
        assertEquals("https://api.example.com/v1", ProviderEndpointPolicy.validate("https://api.example.com/v1/"))
    }

    @Test fun rejectsPlainHttpByDefault() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderEndpointPolicy.validate("http://api.example.com/v1")
        }
    }

    @Test fun allowsLoopbackHttpOnlyWithExplicitOptIn() {
        assertEquals("http://127.0.0.1:8080/v1", ProviderEndpointPolicy.validate(
            "http://127.0.0.1:8080/v1", allowInsecureLocalNetwork = true
        ))
    }

    @Test fun rejectsCredentialBearingUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderEndpointPolicy.validate("https://user:secret@example.com/v1")
        }
    }

    @Test fun rejectsQueryStringsAndFragments() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderEndpointPolicy.validate("https://api.example.com/v1?key=secret")
        }
    }
}
