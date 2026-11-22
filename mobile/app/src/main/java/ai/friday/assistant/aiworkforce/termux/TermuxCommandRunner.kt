package ai.friday.assistant.aiworkforce.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class TermuxCommandResult(
    val executionId: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val errorCode: Int,
    val errorMessage: String
)

internal enum class TermuxBridgeState {
    READY,
    TERMUX_NOT_INSTALLED,
    RUN_COMMAND_PERMISSION_REQUIRED
}

/**
 * Narrow, result-returning bridge to Termux RUN_COMMAND.
 *
 * Friday does not read Termux's private files or extract provider credentials.
 * The only commands exposed by the AI workforce layer are fixed, read-only
 * Codex/Claude CLI invocations. Termux and the user retain control of their
 * own OAuth sessions and credential stores.
 */
internal class TermuxCommandRunner(private val context: Context) {

    fun state(): TermuxBridgeState {
        val installed = try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    TERMUX_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (!installed) return TermuxBridgeState.TERMUX_NOT_INSTALLED
        return if (
            context.checkSelfPermission(RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED
        ) TermuxBridgeState.READY else TermuxBridgeState.RUN_COMMAND_PERMISSION_REQUIRED
    }

    suspend fun run(
        commandPath: String,
        arguments: List<String>,
        stdin: String,
        workDirectory: String = "~/",
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): TermuxCommandResult {
        check(state() == TermuxBridgeState.READY) {
            "Termux is not ready. Install Termux and grant Friday the Run commands in Termux permission."
        }
        val termuxDataDir = runCatching {
            context.packageManager.getApplicationInfo(TERMUX_PACKAGE, 0).dataDir
        }.getOrElse { throw IllegalStateException("Termux's private runtime path is unavailable.", it) }
        require(commandPath.startsWith(termuxDataDir + "/files/usr/bin/")) {
            "Friday may only launch an executable from Termux's usr/bin directory."
        }
        require(arguments.size <= 16 && arguments.all { it.length <= MAX_ARGUMENT_CHARS }) {
            "The Termux command arguments exceed Friday's bounded command policy."
        }
        require(stdin.length <= MAX_STDIN_CHARS) {
            "The AI request is too large for the Termux command bridge."
        }

        val executionId = UUID.randomUUID().toString()
        val completion = CompletableDeferred<TermuxCommandResult>()
        pending[executionId] = completion
        try {
            val callbackIntent = Intent(context, TermuxCommandResultService::class.java)
                .putExtra(EXTRA_EXECUTION_ID, executionId)
            val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback = PendingIntent.getService(
                context,
                executionId.hashCode(),
                callbackIntent,
                pendingFlags
            )

            val command = Intent(ACTION_RUN_COMMAND).apply {
                setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                putExtra(EXTRA_COMMAND_PATH, commandPath)
                putExtra(EXTRA_ARGUMENTS, arguments.toTypedArray())
                putExtra(EXTRA_STDIN, stdin)
                putExtra(EXTRA_WORKDIR, workDirectory)
                putExtra(EXTRA_BACKGROUND, true)
                putExtra(EXTRA_PENDING_INTENT, callback)
                putExtra(EXTRA_COMMAND_LABEL, "Friday AI consultation")
                putExtra(
                    EXTRA_COMMAND_DESCRIPTION,
                    "A read-only AI consultation requested by Friday. Provider login remains in Termux."
                )
            }
            context.startService(command)
            withTimeout(timeoutMs) { completion.await() }
        } finally {
            pending.remove(executionId)
        }
    }

    companion object {
        internal const val TERMUX_PACKAGE = "com.termux"
        internal const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        internal const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        fun bashExecutable(context: Context): String {
            val dataDir = context.packageManager.getApplicationInfo(TERMUX_PACKAGE, 0).dataDir
            return "$dataDir/files/usr/bin/bash"
        }
        internal const val EXTRA_EXECUTION_ID = "friday_execution_id"

        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN"
        private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        private const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
        private const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"

        internal const val RESULT_BUNDLE = "result"
        internal const val RESULT_STDOUT = "stdout"
        internal const val RESULT_STDERR = "stderr"
        internal const val RESULT_EXIT_CODE = "exitCode"
        internal const val RESULT_ERROR_CODE = "err"
        internal const val RESULT_ERROR_MESSAGE = "errmsg"

        private const val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L
        private const val MAX_ARGUMENT_CHARS = 4_096
        private const val MAX_STDIN_CHARS = 64_000
        private val pending = ConcurrentHashMap<String, CompletableDeferred<TermuxCommandResult>>()

        internal fun deliver(result: TermuxCommandResult) {
            pending[result.executionId]?.complete(result)
        }
    }
}
