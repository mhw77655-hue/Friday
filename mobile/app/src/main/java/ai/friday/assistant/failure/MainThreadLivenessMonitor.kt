package ai.friday.assistant.failure

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Detects a blocked Android main looper without depending on Friday's engine
 * coroutine scope. The monitor thread remains able to publish evidence while
 * the UI thread is stalled; once the looper responds again, it closes the
 * incident. It does not kill/relaunch the process or claim to survive process
 * death — Android and an external recovery channel own those cases.
 */
class MainThreadLivenessMonitor(
    private val surface: FailureSurface,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val uptimeMillis: () -> Long = { SystemClock.uptimeMillis() },
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Friday-MainThread-Watchdog").apply { isDaemon = true }
    }
) : AutoCloseable {

    private val running = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    @Volatile private var scheduledTask: ScheduledFuture<*>? = null
    private val pingPending = AtomicBoolean(false)
    private val recoveryObserved = AtomicBoolean(false)
    private val lastAcknowledgedAt = AtomicLong(uptimeMillis())
    private val incidentKey = AtomicReference<String?>(null)

    private val heartbeat = Runnable {
        lastAcknowledgedAt.set(elapsedRealtime())
        pingPending.set(false)
        if (incidentKey.get() != null) recoveryObserved.set(true)
    }

    init {
        require(intervalMs > 0L) { "intervalMs must be positive" }
        require(timeoutMs > intervalMs) { "timeoutMs must be greater than intervalMs" }
    }

    @Synchronized
    fun start() {
        if (closed.get() || !running.compareAndSet(false, true)) return
        lastAcknowledgedAt.set(elapsedRealtime())
        scheduledTask = scheduler.scheduleAtFixedRate(
            ::tick,
            intervalMs,
            intervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    /** Pause while Friday is backgrounded; restart on the next visible resume. */
    @Synchronized
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        scheduledTask?.cancel(false)
        scheduledTask = null
        mainHandler.removeCallbacks(heartbeat)
        pingPending.set(false)
        recoveryObserved.set(false)
    }

    private fun tick() {
        if (closed.get() || !running.get()) return
        val now = elapsedRealtime()
        val lastAck = lastAcknowledgedAt.get()

        if (pingPending.compareAndSet(false, true)) {
            if (!mainHandler.post(heartbeat)) pingPending.set(false)
        }

        if (pingPending.get() && now - lastAck >= timeoutMs && incidentKey.get() == null) {
            val message = "Android main looper did not acknowledge a watchdog heartbeat within ${timeoutMs}ms."
            val report = FailureReport(
                subsystem = SUBSYSTEM,
                operation = OPERATION,
                severity = FailureSeverity.CRITICAL,
                category = FailureCategory.LIFECYCLE,
                message = message,
                recoverability = Recoverability.RECOVERABLE,
                recoveryAction = RecoveryAction.WAIT_FOR_RECOVERY,
                metadata = mapOf(
                    "timeoutMs" to timeoutMs.toString(),
                    "elapsedSinceAckMs" to (now - lastAck).coerceAtLeast(0L).toString()
                ),
                userMessage = "Friday's interface is temporarily unresponsive. Recovery evidence is being preserved."
            )
            if (runCatching { surface.report(report) }.isSuccess) {
                incidentKey.compareAndSet(null, report.dedupeKey)
            }
        }

        if (recoveryObserved.compareAndSet(true, false)) {
            val key = incidentKey.getAndSet(null)
            if (key != null) {
                runCatching {
                    surface.recover(
                        key,
                        RecoveryResult.SUCCESS,
                        "Android main looper acknowledged a later heartbeat; UI responsiveness recovered.",
                        RecoveryAction.WAIT_FOR_RECOVERY
                    )
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stop()
        mainHandler.removeCallbacks(heartbeat)
        scheduler.shutdownNow()
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 1_000L
        const val DEFAULT_TIMEOUT_MS = 5_000L
        const val SUBSYSTEM = "ANDROID_UI"
        const val OPERATION = "main-thread-liveness"
    }
}
