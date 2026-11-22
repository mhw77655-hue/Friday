package ai.friday.assistant.aiworkforce.termux

import android.app.IntentService
import android.content.Intent

/**
 * Receives Termux's RUN_COMMAND PendingIntent result and completes the
 * in-process request. Raw command output is returned to the caller only; it
 * is never written to Logcat.
 */
@Suppress("DEPRECATION")
internal class TermuxCommandResultService : IntentService("FridayTermuxCommandResult") {

    override fun onHandleIntent(intent: Intent?) {
        intent ?: return
        val executionId = intent.getStringExtra(TermuxCommandRunner.EXTRA_EXECUTION_ID) ?: return
        val result = intent.getBundleExtra(TermuxCommandRunner.RESULT_BUNDLE) ?: return
        TermuxCommandRunner.deliver(
            TermuxCommandResult(
                executionId = executionId,
                stdout = result.getString(TermuxCommandRunner.RESULT_STDOUT).orEmpty(),
                stderr = result.getString(TermuxCommandRunner.RESULT_STDERR).orEmpty(),
                exitCode = result.getInt(TermuxCommandRunner.RESULT_EXIT_CODE, -1),
                errorCode = result.getInt(TermuxCommandRunner.RESULT_ERROR_CODE, -1),
                errorMessage = result.getString(TermuxCommandRunner.RESULT_ERROR_MESSAGE).orEmpty()
            )
        )
    }
}
