package ai.friday.assistant.aiworkforce.termux

import android.app.Activity
import android.content.Context

/** Termux is one execution adapter, not a requirement imposed on every host. */
internal class TermuxAiExecutionEnvironment(context: Context) : AiExecutionEnvironment {
    private val appContext = context.applicationContext
    private val runner = TermuxCommandRunner(appContext)

    override val kind = AiExecutionEnvironmentKind.TERMUX_NATIVE

    override suspend fun probe(): AiExecutionEnvironmentProbe {
        val state = runner.state()
        val mapped = when (state) {
            TermuxBridgeState.READY -> AiExecutionEnvironmentState.READY
            TermuxBridgeState.TERMUX_NOT_INSTALLED -> AiExecutionEnvironmentState.NOT_INSTALLED
            TermuxBridgeState.RUN_COMMAND_PERMISSION_REQUIRED -> AiExecutionEnvironmentState.PERMISSION_REQUIRED
        }
        val detail = when (state) {
            TermuxBridgeState.READY -> "Termux RUN_COMMAND is available."
            TermuxBridgeState.TERMUX_NOT_INSTALLED -> "Termux is not installed."
            TermuxBridgeState.RUN_COMMAND_PERMISSION_REQUIRED ->
                "Friday needs the Android Termux RUN_COMMAND permission."
        }
        return AiExecutionEnvironmentProbe(kind, mapped, detail)
    }

    override suspend fun executeShell(
        command: String,
        stdin: String,
        workingDirectory: String,
        timeoutMs: Long
    ): AiEnvironmentCommandResult {
        val termuxHome = appContext.packageManager
            .getApplicationInfo(TermuxCommandRunner.TERMUX_PACKAGE, 0)
            .dataDir + "/files/home"
        val resolvedWorkingDirectory = when {
            workingDirectory == "~" -> termuxHome
            workingDirectory.startsWith("~/") -> termuxHome + workingDirectory.removePrefix("~")
            else -> workingDirectory
        }
        val result = runner.run(
            commandPath = TermuxCommandRunner.bashExecutable(appContext),
            arguments = listOf("-c", command),
            stdin = stdin,
            workDirectory = resolvedWorkingDirectory,
            timeoutMs = timeoutMs
        )
        return AiEnvironmentCommandResult(
            stdout = result.stdout,
            stderr = result.stderr,
            exitCode = result.exitCode,
            transportError = if (result.errorCode == Activity.RESULT_OK) null
                else result.errorMessage.ifBlank { "Termux transport error ${result.errorCode}." }
        )
    }
}
