package com.jarvis.app.failure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * LAUNCH-OLLAMA-DOWN: the ONE way an engine-level coroutine scope is built.
 *
 * A bare `CoroutineScope(dispatcher)` has no supervisor and no exception
 * handler. On the phone that meant a single uncaught IOException from an organ
 * (the boot anchor warm) cancelled the engine scope and killed the process —
 * in main AND in :voice, since both run `JarvisEngine.init`.
 *
 * This factory supplies both halves of containment:
 *  - a [SupervisorJob], so one failed child never cancels its siblings;
 *  - a [CoroutineExceptionHandler], so an uncaught failure is recorded through
 *    the ONE [FailureSurface] instead of escaping to the platform.
 *
 * The failure is reported with the exception's type and message as the cause
 * and a plain-language line for the person, and the scope keeps accepting
 * launches afterwards. Nothing here decides anything about a turn; it only
 * keeps a failure from taking the body down.
 */
object EngineContainment {

    /**
     * Build a contained engine scope named [name]. [failureSurface] is
     * nullable because a JVM harness may construct a scope before its surface;
     * when it is null the scope is still supervised, it simply has nowhere to
     * record into yet.
     */
    fun scope(
        name: String,
        failureSurface: FailureSurface?,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, throwable ->
            if (throwable is kotlinx.coroutines.CancellationException) return@CoroutineExceptionHandler
            failureSurface?.report(
                FailureReport(
                    subsystem = "ENGINE",
                    operation = name,
                    severity = FailureSeverity.ERROR,
                    category = FailureCategory.INTERNAL,
                    message = "Engine scope '$name' contained an uncaught failure",
                    source = "EngineContainment",
                    cause = "${throwable.javaClass.simpleName}: ${throwable.message}",
                    recoverability = Recoverability.RETRYABLE,
                    recoveryAction = RecoveryAction.NONE,
                    userMessage = "One of my internal systems had a problem, but I am still running."
                )
            )
        }
        return CoroutineScope(SupervisorJob() + dispatcher + handler)
    }
}
