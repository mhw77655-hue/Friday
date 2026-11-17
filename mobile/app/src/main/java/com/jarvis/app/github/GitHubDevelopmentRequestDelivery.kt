package com.jarvis.app.github

import android.content.Context
import com.jarvis.app.github.outbox.DeliveryResult
import com.jarvis.app.github.outbox.DevelopmentRequestDelivery
import com.jarvis.app.github.outbox.QueuedDevelopmentRequest
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Production transport for the durable development-request outbox.
 *
 * The outbox persists delivery state; this adapter only maps the existing
 * GitHub workflow-dispatch response into a retryable or permanent outcome.
 * A missing/rejected credential is retryable because the owner can reconnect
 * GitHub later without losing the queued request.
 */
class GitHubDevelopmentRequestDelivery(
    context: Context,
    private val client: GitHubDevelopmentClient = GitHubDevelopmentClient(context)
) : DevelopmentRequestDelivery {
    override suspend fun deliver(request: QueuedDevelopmentRequest): DeliveryResult = try {
        client.dispatchStoredRequest(request.requestId, request.requestJson)
        DeliveryResult.Delivered
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (missingCredential: IllegalStateException) {
        DeliveryResult.Retryable(missingCredential.message ?: "GitHub credential is unavailable.")
    } catch (rejected: GitHubDispatchException) {
        val reason = rejected.message ?: "GitHub returned HTTP ${rejected.statusCode}."
        if (isRetryable(rejected.statusCode)) {
            DeliveryResult.Retryable(reason)
        } else {
            DeliveryResult.Permanent(reason)
        }
    } catch (network: IOException) {
        DeliveryResult.Retryable(network.message ?: "GitHub is unreachable.")
    } catch (unexpected: Exception) {
        DeliveryResult.Retryable(unexpected.message ?: unexpected.javaClass.simpleName)
    }

    private fun isRetryable(statusCode: Int): Boolean = when (statusCode) {
        401, 403, 404, 408, 409, 425, 429 -> true
        in 500..599 -> true
        in 400..499 -> false
        else -> true
    }
}
