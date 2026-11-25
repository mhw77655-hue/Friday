package ai.friday.assistant.failure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryControllerTest {
    @Test
    fun recoveryActionAndIndependentVerifierCanSuspend() = runBlocking {
        val surface = FailureSurface()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = RecoveryController(surface, scope)
        val actionCalls = AtomicInteger(0)
        val verifyCalls = AtomicInteger(0)

        try {
            controller.register(
                RecoveryController.RetrySpec(
                    subsystem = "MODEL",
                    operation = "send",
                    maxAttempts = 1,
                    backoffMs = 0,
                    action = {
                        delay(20)
                        actionCalls.incrementAndGet()
                        true
                    },
                    verify = {
                        delay(20)
                        verifyCalls.incrementAndGet()
                        true
                    },
                    verificationTimeoutMs = 1_000,
                    verificationPollMs = 10
                )
            )

            controller.handle(
                FailureReport(
                    subsystem = "MODEL",
                    operation = "send",
                    severity = FailureSeverity.ERROR,
                    category = FailureCategory.MODEL,
                    message = "test model send failure",
                    recoverability = Recoverability.RETRYABLE,
                    recoveryAction = RecoveryAction.RETRY_WITH_BACKOFF
                )
            )

            withTimeout(2_000) {
                while (verifyCalls.get() == 0) delay(10)
            }
            assertEquals(1, actionCalls.get())
            assertEquals(1, verifyCalls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun directFailureSurfaceReportsReachTheRegisteredRecoveryController() = runBlocking {
        val surface = FailureSurface()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = RecoveryController(surface, scope)
        val actionCalls = AtomicInteger(0)
        val verifyCalls = AtomicInteger(0)
        try {
            controller.register(
                RecoveryController.RetrySpec(
                    subsystem = "VOICE",
                    operation = "transcribe",
                    maxAttempts = 1,
                    backoffMs = 0,
                    action = { actionCalls.incrementAndGet(); true },
                    verify = { verifyCalls.incrementAndGet(); true },
                    verificationTimeoutMs = 1_000,
                    verificationPollMs = 10
                )
            )
            // Existing organs commonly report directly to FailureSurface.
            surface.report(
                FailureReport(
                    subsystem = "VOICE",
                    operation = "transcribe",
                    severity = FailureSeverity.ERROR,
                    category = FailureCategory.STT,
                    message = "voice provider unavailable",
                    recoverability = Recoverability.RETRYABLE,
                    recoveryAction = RecoveryAction.RETRY_WITH_BACKOFF
                )
            )
            withTimeout(2_000) {
                while (verifyCalls.get() == 0) delay(10)
            }
            assertEquals(1, actionCalls.get())
            assertEquals(1, verifyCalls.get())
            assertEquals(1, surface.recentFailures.value.count {
                it.subsystem == "VOICE" && it.operation == "transcribe"
            })
            assertEquals(0, surface.currentFailures.value.count {
                it.subsystem == "VOICE" && it.operation == "transcribe"
            })
        } finally {
            scope.cancel()
        }
    }
}
