package com.jarvis.app.github.outbox

import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopmentRequestOutboxTest {

    private fun tempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "outbox-test-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun draft(
        outcome: String = "Implement a durable development-request outbox.",
        evidence: List<String> = listOf("Requests must survive process death."),
        acceptance: List<String> = listOf("A queued request is recovered after restart."),
        risks: List<String> = listOf("Duplicate delivery must not suppress distinct work."),
        rollback: String = "Close the draft PR and discard the proposal branch.",
        unresolved: List<String> = listOf("Which persistence owner is correct?"),
        sourceRevision: String = "main",
        createdAt: String = "2026-11-18T00:00:00Z",
        requestId: String? = null,
        logicalKey: String? = null,
        privacyStatus: String = "approved",
        reviewedBy: String = "test-owner",
        reviewedAt: String? = null
    ): DevelopmentRequestDraft = DevelopmentRequestDraft(
        intendedOutcome = outcome,
        evidence = evidence,
        acceptanceCriteria = acceptance,
        risks = risks,
        rollbackExpectation = rollback,
        unresolvedQuestions = unresolved,
        sourceRevision = sourceRevision,
        createdAt = createdAt,
        requestId = requestId,
        logicalKey = logicalKey,
        privacyStatus = privacyStatus,
        privacyReviewedBy = reviewedBy,
        privacyReviewedAt = reviewedAt
    )

    @Test
    fun `enqueue persists one pending request with a stable id`() {
        val dir = tempDir()
        val outbox = DevelopmentRequestOutbox(dir)

        val outcome = outbox.enqueue(draft())

        val enqueued = outcome as EnqueueOutcome.Enqueued
        assertTrue(enqueued.request.requestId.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}")))
        assertEquals(DeliveryState.PENDING, enqueued.request.state)
        val payload = JSONObject(enqueued.request.requestJson)
        assertEquals(enqueued.request.requestId, payload.getString("request_id"))
        assertEquals("main", payload.getString("source_revision"))
        assertEquals("approved", payload.getJSONObject("privacy_review").getString("status"))
    }

    @Test
    fun `maximum supported owner instruction plus system context is accepted`() {
        val outbox = DevelopmentRequestOutbox(tempDir())
        val fullInstruction = "x".repeat(4047)

        val outcome = outbox.enqueue(draft(outcome = fullInstruction))

        assertTrue("The full 3500-character user input plus system context must fit.", outcome is EnqueueOutcome.Enqueued)
    }

    @Test
    fun `repeated enqueue of the same logical request is deduplicated`() {
        val dir = tempDir()
        val outbox = DevelopmentRequestOutbox(dir)

        val first = (outbox.enqueue(draft()) as EnqueueOutcome.Enqueued).request
        val second = outbox.enqueue(draft(createdAt = "2026-11-19T00:00:00Z"))

        val duplicate = second as EnqueueOutcome.Duplicate
        assertEquals(first.requestId, duplicate.request.requestId)
        assertEquals(1, outbox.allRequests().size)
        assertEquals(1, outbox.pendingRequests().size)
    }

    @Test
    fun `explicit logical key deduplicates even when evidence changes`() {
        val dir = tempDir()
        val outbox = DevelopmentRequestOutbox(dir)

        outbox.enqueue(draft(logicalKey = "owner-build:make-the-outbox-durable"))
        val duplicate = outbox.enqueue(
            draft(
                logicalKey = "owner-build:make-the-outbox-durable",
                evidence = listOf("A different runtime snapshot on the next attempt.")
            )
        )

        assertTrue(duplicate is EnqueueOutcome.Duplicate)
        assertEquals(1, outbox.allRequests().size)
    }

    @Test
    fun `distinct logical requests are both queued`() {
        val dir = tempDir()
        val outbox = DevelopmentRequestOutbox(dir)

        outbox.enqueue(draft(outcome = "Implement the outbox."))
        outbox.enqueue(draft(outcome = "Measure outbox delivery latency."))

        assertEquals(2, outbox.allRequests().size)
        assertEquals(2, outbox.pendingRequests().size)
    }

    @Test
    fun `queued requests survive process death and are recovered by a new instance`() {
        val dir = tempDir()
        val first = (DevelopmentRequestOutbox(dir).enqueue(draft()) as EnqueueOutcome.Enqueued).request

        val recovered = DevelopmentRequestOutbox(dir).pendingRequests()

        assertEquals(1, recovered.size)
        assertEquals(first.requestId, recovered.single().requestId)
        assertEquals(DeliveryState.PENDING, recovered.single().state)
    }

    @Test
    fun `invalid requests are rejected without corrupting valid queued work`() {
        val dir = tempDir()
        val outbox = DevelopmentRequestOutbox(dir)
        val valid = (outbox.enqueue(draft()) as EnqueueOutcome.Enqueued).request

        val blank = outbox.enqueue(draft(outcome = "   "))
        val unsafe = outbox.enqueue(
            draft(evidence = listOf("token github_pat_abcdefghijklmnopqrstuvwxyz0123456789"))
        )
        val malformedTime = outbox.enqueue(draft(createdAt = "not-a-timestamp"))
        val unapproved = outbox.enqueue(draft(privacyStatus = "pending"))

        assertTrue(blank is EnqueueOutcome.Rejected)
        assertTrue(unsafe is EnqueueOutcome.Rejected)
        assertTrue(malformedTime is EnqueueOutcome.Rejected)
        assertTrue(unapproved is EnqueueOutcome.Rejected)
        val requests = outbox.allRequests()
        assertEquals(1, requests.size)
        assertEquals(valid.requestId, requests.single().requestId)
        assertEquals(DeliveryState.PENDING, requests.single().state)
    }

    @Test
    fun `oversized request is rejected before anything is persisted`() {
        val dir = tempDir()
        val outbox = DevelopmentRequestOutbox(dir)

        val outcome = outbox.enqueue(
            draft(evidence = List(6) { "x".repeat(6000) })
        )

        assertTrue(outcome is EnqueueOutcome.Rejected)
        assertTrue(outbox.allRequests().isEmpty())
        assertFalse(File(dir, DevelopmentRequestOutbox.FILE_PATH).exists())
    }

    @Test
    fun `delivery transitions pending to delivering to delivered and is idempotent`() = runBlocking {
        var now = 1_000L
        val dir = tempDir()
        val outbox = DevelopmentRequestOutbox(dir, clock = { now })
        val request = (outbox.enqueue(draft()) as EnqueueOutcome.Enqueued).request
        val fake = FakeDelivery(mutableListOf(DeliveryResult.Delivered))

        val report = outbox.deliverPending(fake, nowMs = { now })

        assertEquals(1, report.delivered)
        assertEquals(0, report.retried)
        assertEquals(0, report.failed)
        assertEquals(0, report.remainingPending)
        assertEquals(listOf(request.requestId), fake.requestIds)
        val delivered = outbox.allRequests().single()
        assertEquals(DeliveryState.DELIVERED, delivered.state)
        assertEquals(1, delivered.attempts)
        assertNotNull(delivered.deliveredAtMs)

        val duplicate = outbox.enqueue(draft())
        assertTrue(duplicate is EnqueueOutcome.Duplicate)
        assertEquals(DeliveryState.DELIVERED, (duplicate as EnqueueOutcome.Duplicate).request.state)
        assertEquals(1, outbox.allRequests().size)
    }

    @Test
    fun `retryable failure returns the request to pending and a later attempt succeeds`() = runBlocking {
        var now = 5_000L
        val dir = tempDir()
        val outbox = DevelopmentRequestOutbox(dir, clock = { now })
        val request = (outbox.enqueue(draft()) as EnqueueOutcome.Enqueued).request
        val fake = FakeDelivery(
            mutableListOf(
                DeliveryResult.Retryable("GitHub is unreachable."),
                DeliveryResult.Delivered
            )
        )

        val first = outbox.deliverPending(fake, maxRequests = 1, nowMs = { now })
        assertEquals(0, first.delivered)
        assertEquals(1, first.retried)
        assertEquals(1, first.remainingPending)
        var stored = outbox.allRequests().single()
        assertEquals(DeliveryState.PENDING, stored.state)
        assertEquals("GitHub is unreachable.", stored.lastError)

        now += 30_000
        val second = outbox.deliverPending(fake, maxRequests = 1, nowMs = { now })
        assertEquals(1, second.delivered)
        stored = outbox.allRequests().single()
        assertEquals(DeliveryState.DELIVERED, stored.state)
        assertEquals(2, stored.attempts)
        assertEquals(request.requestId, stored.requestId)
        assertNull(stored.lastError)
    }

    @Test
    fun `permanent failure stops automatic delivery and explicit retry requeues it`() = runBlocking {
        var now = 9_000L
        val dir = tempDir()
        val outbox = DevelopmentRequestOutbox(dir, clock = { now })
        val request = (outbox.enqueue(draft()) as EnqueueOutcome.Enqueued).request
        val fake = FakeDelivery(
            mutableListOf(
                DeliveryResult.Permanent("Request schema is invalid."),
                DeliveryResult.Delivered
            )
        )

        val failed = outbox.deliverPending(fake, nowMs = { now })
        assertEquals(1, failed.failed)
        assertEquals(0, failed.remainingPending)
        assertEquals(DeliveryState.FAILED, outbox.allRequests().single().state)

        assertNull(outbox.claimNext(now))
        val retried = outbox.retry(request.requestId, now)
        assertEquals(DeliveryState.PENDING, retried?.state)

        now += 1_000
        val delivered = outbox.deliverPending(fake, nowMs = { now })
        assertEquals(1, delivered.delivered)
        assertEquals(DeliveryState.DELIVERED, outbox.allRequests().single().state)
    }

    @Test
    fun `interrupted delivery is recovered after its lease without losing the request`() {
        var now = 20_000L
        val dir = tempDir()
        val outbox = DevelopmentRequestOutbox(dir, clock = { now }, claimLeaseMs = 100L)
        val request = (outbox.enqueue(draft()) as EnqueueOutcome.Enqueued).request

        val claimed = outbox.claimNext(now)
        assertEquals(DeliveryState.DELIVERING, claimed?.state)
        assertEquals(1, claimed?.attempts)

        now += 50
        assertNull(outbox.claimNext(now))

        now += 51
        val recovered = DevelopmentRequestOutbox(dir, clock = { now }, claimLeaseMs = 100L).claimNext(now)
        assertEquals(request.requestId, recovered?.requestId)
        assertEquals(DeliveryState.DELIVERING, recovered?.state)
        assertEquals(2, recovered?.attempts)
    }

    @Test
    fun `malformed on-disk outbox is preserved instead of overwritten`() {
        val dir = tempDir()
        val file = File(dir, DevelopmentRequestOutbox.FILE_PATH)
        file.parentFile!!.mkdirs()
        file.writeText("{not valid json")

        val thrown = runCatching { DevelopmentRequestOutbox(dir).enqueue(draft()) }.exceptionOrNull()

        assertTrue(thrown is OutboxStorageException)
        assertEquals("{not valid json", file.readText())
    }

    private class FakeDelivery(
        private val results: MutableList<DeliveryResult>
    ) : DevelopmentRequestDelivery {
        val requestIds = mutableListOf<String>()

        override suspend fun deliver(request: QueuedDevelopmentRequest): DeliveryResult {
            requestIds += request.requestId
            return if (results.isEmpty()) DeliveryResult.Delivered else results.removeAt(0)
        }
    }
}
