package ai.friday.assistant.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.friday.assistant.aiworkforce.table.TaskCapsuleCompiler
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnthropicTaskWorkerInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun encryptedKeyAndOneShotApprovedCapsuleReachClaudeWithoutUnrelatedMemory() {
        context.getSharedPreferences("friday_anthropic_secure", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("friday_anthropic_task_approvals", Context.MODE_PRIVATE).edit().clear().commit()
        val keyStore = AnthropicApiKeyStore(context)
        val apiKey = "sk-ant-test-credential-never-export"
        keyStore.saveApiKey(apiKey)
        assertEquals(apiKey, keyStore.readApiKey())
        val stored = context.getSharedPreferences("friday_anthropic_secure", Context.MODE_PRIVATE)
        assertFalse(stored.getString("api_key_ciphertext", "").orEmpty().contains(apiKey))

        val compiler = TaskCapsuleCompiler()
        val capsule = compiler.compile(
            TaskCapsuleCompiler.Request(
                taskId = "friday-capsule-001",
                objective = "Assess Friday identity continuity.",
                instructions = "Use only the selected local evidence.",
                acceptanceCriteria = listOf("Return a concise evidence-based assessment."),
                allowedTools = listOf("github.read_file"),
                outputSchema = """{"type":"object","required":["assessment"]}"""
            ),
            listOf(
                TaskCapsuleCompiler.MemoryEvidence(
                    "memory-relevant", "Friday identity remains stable across process restart.",
                    "individual", "experience-1", 100L, 0.98,
                    TaskCapsuleCompiler.Sensitivity.SANITIZED
                ),
                TaskCapsuleCompiler.MemoryEvidence(
                    "memory-unrelated", "A private unrelated conversation about dinner.",
                    "conversation", "experience-2", 101L, 0.02,
                    TaskCapsuleCompiler.Sensitivity.PUBLIC
                )
            )
        )
        assertTrue(compiler.verify(capsule))
        assertFalse(capsule.context.contains("unrelated conversation"))

        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    event: message_start
                    data: {"type":"message_start","message":{"id":"msg_task_1","model":"claude-opus-5-5","usage":{"input_tokens":50}}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Continuity evidence received."}}

                    event: message_delta
                    data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":8}}

                    event: message_stop
                    data: {"type":"message_stop"}

                    """.trimIndent()
                )
        )
        server.start()
        try {
            val provider = AnthropicMessagesProvider(
                apiKeySource = keyStore,
                modelProvider = { "claude-opus-5-5" },
                apiBaseUrl = server.url("/v1").toString().trimEnd('/')
            )
            val worker = AnthropicTaskWorker(
                provider = provider,
                compiler = compiler,
                approvals = AnthropicTaskApprovalStore(context)
            )
            worker.recordOwnerApproval(capsule)
            val first = worker.execute(capsule)
            assertTrue("Anthropic task failed: " + (first as? CloudResult.Failure)?.reason, first is CloudResult.Success)
            val request = server.takeRequest()
            assertEquals(apiKey, request.getHeader("x-api-key"))
            val sentBody = request.body.readUtf8()
            assertTrue(sentBody.contains("Friday identity remains stable"))
            assertFalse(sentBody.contains("unrelated conversation"))
            assertFalse(sentBody.contains(apiKey))
            assertFalse(sentBody.contains("whole conversation"))

            val replay = worker.execute(capsule)
            assertTrue(replay is CloudResult.Failure)
            assertEquals("one-shot approval prevents an automatic second billable request", 1, server.requestCount)
        } finally {
            server.shutdown()
            keyStore.clear()
        }
    }

    @Test
    fun tamperedCapsuleIsRejectedBeforeApprovalOrNetworkUse() {
        context.getSharedPreferences("friday_anthropic_task_approvals", Context.MODE_PRIVATE).edit().clear().commit()
        val compiler = TaskCapsuleCompiler()
        val capsule = compiler.compile(
            TaskCapsuleCompiler.Request(
                taskId = "friday-tamper-001",
                objective = "Test capsule integrity.",
                instructions = "Do not modify this capsule.",
                acceptanceCriteria = listOf("Reject changed context."),
                outputSchema = """{"type":"object"}"""
            ),
            emptyList()
        )
        val tampered = capsule.copy(context = "injected unrelated conversation")
        val server = MockWebServer()
        server.start()
        try {
            val worker = AnthropicTaskWorker(
                provider = AnthropicMessagesProvider(
                    apiKeySource = AnthropicApiKeySource { "sk-ant-test-credential-never-export" },
                    modelProvider = { "claude-opus-5-5" },
                    apiBaseUrl = server.url("/v1").toString().trimEnd('/')
                ),
                compiler = compiler,
                approvals = AnthropicTaskApprovalStore(context)
            )
            assertTrue(worker.execute(tampered) is CloudResult.Failure)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
