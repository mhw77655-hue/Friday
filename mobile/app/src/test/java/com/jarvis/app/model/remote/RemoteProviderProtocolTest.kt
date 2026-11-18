package com.jarvis.app.model.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteProviderProtocolTest {
    @Test fun providerProtocolsAreExplicitAndDistinct() {
        assertEquals(4, RemoteProviderProtocol.values().size)
        assertEquals(RemoteProviderProtocol.OPENAI_RESPONSES, RemoteProviderProtocol.valueOf("OPENAI_RESPONSES"))
        assertEquals(RemoteProviderProtocol.OPENAI_COMPATIBLE, RemoteProviderProtocol.valueOf("OPENAI_COMPATIBLE"))
        assertEquals(RemoteProviderProtocol.ANTHROPIC_MESSAGES, RemoteProviderProtocol.valueOf("ANTHROPIC_MESSAGES"))
        assertEquals(RemoteProviderProtocol.GEMINI_INTERACTIONS, RemoteProviderProtocol.valueOf("GEMINI_INTERACTIONS"))
    }

    @Test fun endpointRequiresHttpsUnlessLocalOptIn() {
        val safe = ProviderEndpointPolicy.validate("https://api.example.com/v1")
        assertEquals("https://api.example.com/v1", safe)
        assertEquals("http://127.0.0.1:11434/v1", ProviderEndpointPolicy.validate(
            "http://localhost:11434/v1", allowInsecureLoopbackHttp = true
        ))
    }
}
