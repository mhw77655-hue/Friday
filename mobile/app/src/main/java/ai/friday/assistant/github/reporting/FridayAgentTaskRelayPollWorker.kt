package ai.friday.assistant.github.reporting

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Polls only known public project task threads. Every accepted reply is first
 * persisted in Friday's local reply journal, then acknowledged on GitHub.
 * It never executes a returned proposal or reads phone/private conversation data.
 */
class FridayAgentTaskRelayPollWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val consent = FridayAgentTaskRelayConsentStore(applicationContext)
        if (!consent.isEnabled()) return@withContext Result.success()

        val token = GitHubCredentialStore(applicationContext).read()
        if (token.isNullOrBlank()) {
            consent.recordPoll(0, "GitHub token is not configured in Friday Settings.")
            return@withContext Result.success()
        }

        val trustedActors = consent.trustedActors()
        if (trustedActors.isEmpty()) {
            consent.recordPoll(0, "No trusted GitHub actors are configured.")
            return@withContext Result.failure(
                Data.Builder().putString("status", "no_trusted_actors").build()
            )
        }

        val threads = AgentTaskThreadStore(applicationContext.filesDir)
        val replies = AgentTaskReplyStore(applicationContext.filesDir)
        val relay = FridayAgentTaskRelay(
            github = GitHubDevelopmentRepositoryClient(token),
            threads = threads,
            trustedActors = trustedActors,
            consentEnabled = consent::isEnabled
        )
        var newReplies = 0
        try {
            for (thread in threads.all().takeLast(MAX_THREADS_PER_POLL)) {
                if (!consent.isEnabled()) break
                val pending = relay.pollReplies(thread.taskId)
                for (reply in pending) {
                    if (!consent.isEnabled()) break
                    val stored = StoredAgentTaskReply(
                        taskId = thread.taskId,
                        issueNumber = thread.issueNumber,
                        issueUrl = thread.issueUrl,
                        commentId = reply.commentId,
                        authorLogin = reply.authorLogin,
                        createdAt = reply.createdAt,
                        receivedAtEpochMs = System.currentTimeMillis(),
                        envelope = reply.envelope
                    )
                    val inserted = replies.appendIfAbsent(stored)
                    // A previous attempt may have persisted the reply and died
                    // before acknowledging GitHub. In that case appendIfAbsent
                    // returns false and this retry safely completes the ack.
                    relay.acknowledge(thread.taskId, reply)
                    if (inserted) newReplies++
                }
            }
            consent.recordPoll(newReplies)
            Result.success(
                Data.Builder()
                    .putString("status", "checked")
                    .putInt("new_replies", newReplies)
                    .build()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            consent.recordPoll(newReplies, "GitHub relay is temporarily unreachable.")
            Result.retry()
        } catch (failure: Exception) {
            consent.recordPoll(newReplies, failure.javaClass.simpleName)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK = "friday-public-agent-relay-periodic"
        const val UNIQUE_NOW_WORK = "friday-public-agent-relay-now"
        private const val MAX_THREADS_PER_POLL = 20

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enable(context: Context) {
            val request = PeriodicWorkRequestBuilder<FridayAgentTaskRelayPollWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            checkNow(context)
        }

        fun checkNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<FridayAgentTaskRelayPollWorker>()
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NOW_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        fun disable(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_NOW_WORK)
        }
    }
}
