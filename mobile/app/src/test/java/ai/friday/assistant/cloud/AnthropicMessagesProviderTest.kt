package ai.friday.assistant.cloud

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicMessagesProviderTest {
    @Test
    fun `streams text and exposes cache and token usage without exposing the api key`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setHeader("request-id", "req_friday_1")
                .setBody(
                    """
                    event: message_start
                    data: {"type":"message_start","message":{"id":"msg_1","model":"claude-opus-5-5","usage":{"input_tokens":25,"cache_creation_input_tokens":7,"cache_read_input_tokens":3}}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Friday "}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"continues."}}

                    event: message_delta
                    data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":12}}

                    event: message_stop
                    data: {"type":"message_stop"}

                    """.trimIndent()
                )
        )
        server.start()
        try {
            val provider = AnthropicMessagesProvider(
                apiKeySource = AnthropicApiKeySource { "sk-ant-test-secret-credential" },
                modelProvider = { "claude-opus-5-5" },
                apiBaseUrl = server.url("/v1").toString().trimEnd('/')
            )
            val result = provider.generate("A bounded task capsule")

            assertTrue(result is CloudResult.Success)
            result as CloudResult.Success
            assertEquals("Friday continues.", result.text)
            assertEquals("anthropic-claude", result.providerId)
            assertEquals("req_friday_1", result.requestId)
            assertEquals("claude-opus-5-5", result.model)
            assertEquals(25, result.usage?.inputTokens)
            assertEquals(12, result.usage?.outputTokens)
            assertEquals(7, result.usage?.cacheCreationInputTokens)
            assertEquals(3, result.usage?.cacheReadInputTokens)

            val request = server.takeRequest()
            assertEquals("sk-ant-test-secret-credential", request.getHeader("x-api-key"))
            assertEquals(AnthropicMessagesProvider.API_VERSION, request.getHeader("anthropic-version"))
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            assertTrue(body.contains("\"stream\":true"))
            assertTrue(body.contains("\"cache_control\":{\"type\":\"ephemeral\"}"))
            assertFalse(body.contains("tools"))
            assertFalse(result.text.contains("sk-ant-test-secret-credential"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `missing credentials fail locally without making a network request`() {
        val server = MockWebServer()
        server.start()
        try {
            val result = AnthropicMessagesProvider(
                apiKeySource = AnthropicApiKeySource { null },
                modelProvider = { "claude-opus-5-5" },
                apiBaseUrl = server.url("/v1").toString().trimEnd('/')
            ).generate("Task")
            assertTrue(result is CloudResult.Failure)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `rate limits and overload are surfaced for provider rotation`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429).setBody("sensitive server detail"))
        server.start()
        try {
            val result = AnthropicMessagesProvider(
                apiKeySource = AnthropicApiKeySource { "sk-ant-test-secret-credential" },
                modelProvider = { "claude-opus-5-5" },
                apiBaseUrl = server.url("/v1").toString().trimEnd('/')
            ).generate("Task")
            assertTrue(result is CloudResult.Failure)
            result as CloudResult.Failure
            assertTrue(result.rateLimited)
            assertFalse(result.reason.contains("sensitive server detail"))
            assertFalse(result.reason.contains("sk-ant-test-secret-credential"))
        } finally {
            server.shutdown()
        }
    }
}
