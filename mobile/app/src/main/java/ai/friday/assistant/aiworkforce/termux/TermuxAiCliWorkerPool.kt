package ai.friday.assistant.aiworkforce.termux

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

enum class AiCliWorkerId(val displayName: String, val commandName: String) {
    OPENAI_CODEX("ChatGPT / OpenAI Codex", "codex"),
    ANTHROPIC_CLAUDE("Claude Code", "claude")
}

enum class AiCliWorkerState {
    AVAILABLE,
    NOT_INSTALLED,
    AUTHENTICATION_NOT_CONFIRMED,
    TERMUX_UNAVAILABLE
}

data class AiCliWorkerStatus(
    val id: AiCliWorkerId,
    val state: AiCliWorkerState,
    val detail: String
)

data class AiCliConsultation(
    val provider: AiCliWorkerId,
    val response: String,
    val exitCode: Int,
    val elapsedMs: Long,
    val rawFormat: String
)

/**
 * Subscription-authenticated AI workers hosted by the user's existing Termux
 * environment. This is the Android adaptation of the CLI orchestration pattern:
 * Codex signs in with ChatGPT OAuth; Claude Code signs in with a Claude account.
 *
 * No API key is requested, copied, extracted or stored by Friday. Provider
 * credentials remain in each official CLI's own Termux home directory.
 */
internal class TermuxAiCliWorkerPool(context: Context) {
    private val appContext = context.applicationContext
    private val runner = TermuxCommandRunner(appContext)
    private val bashPath by lazy { TermuxCommandRunner.bashExecutable(appContext) }
    private val workerDirectory = "~/.friday-ai-worker-sandbox"

    suspend fun discover(): List<AiCliWorkerStatus> {
        val bridgeState = runner.state()
        if (bridgeState != TermuxBridgeState.READY) {
            val detail = when (bridgeState) {
                TermuxBridgeState.TERMUX_NOT_INSTALLED -> "Termux is not installed."
                TermuxBridgeState.RUN_COMMAND_PERMISSION_REQUIRED ->
                    "Friday needs the Android 'Run commands in Termux environment' permission."
                TermuxBridgeState.READY -> "Ready."
            }
            return AiCliWorkerId.entries.map {
                AiCliWorkerStatus(it, AiCliWorkerState.TERMUX_UNAVAILABLE, detail)
            }
        }

        val sandbox = runCatching {
            runner.run(
                commandPath = bashPath,
                arguments = listOf("-c", "mkdir -p ~/.friday-ai-worker-sandbox && printf READY"),
                stdin = "",
                timeoutMs = 15_000L
            )
        }.getOrNull()
        if (sandbox == null || sandbox.errorCode != android.app.Activity.RESULT_OK || sandbox.exitCode != 0) {
            return AiCliWorkerId.entries.map {
                AiCliWorkerStatus(
                    it,
                    AiCliWorkerState.TERMUX_UNAVAILABLE,
                    "Friday could not prepare its isolated Termux consultation directory. Check Termux external-command settings."
                )
            }
        }

        return AiCliWorkerId.entries.map { worker ->
            val probe = runCatching {
                runner.run(
                    commandPath = bashPath,
                    arguments = listOf("-c", "command -v ${worker.commandName}"),
                    stdin = "",
                    timeoutMs = 15_000L
                )
            }.getOrNull()
            if (probe == null || probe.errorCode != android.app.Activity.RESULT_OK) {
                AiCliWorkerStatus(
                    worker,
                    AiCliWorkerState.TERMUX_UNAVAILABLE,
                    "Termux refused the bridge request. Check allow-external-apps=true in Termux properties."
                )
            } else if (probe.exitCode == 0 && probe.stdout.isNotBlank()) {
                AiCliWorkerStatus(
                    worker,
                    AiCliWorkerState.AUTHENTICATION_NOT_CONFIRMED,
                    "CLI found. Its existing account login will be verified on first consultation."
                )
            } else {
                AiCliWorkerStatus(
                    worker,
                    AiCliWorkerState.NOT_INSTALLED,
                    "${worker.commandName} CLI was not found in Termux."
                )
            }
        }
    }

    suspend fun consult(
        worker: AiCliWorkerId,
        prompt: String,
        timeoutMs: Long = DEFAULT_CONSULT_TIMEOUT_MS
    ): AiCliConsultation {
        require(prompt.isNotBlank()) { "An AI consultation prompt cannot be blank." }
        require(prompt.length <= MAX_PROMPT_CHARS) { "The AI consultation prompt exceeds Friday's limit." }
        check(runner.state() == TermuxBridgeState.READY) {
            "Friday's Termux bridge is not ready."
        }

        val command = AiCliProtocol.command(worker)
        val startedAt = System.currentTimeMillis()
        val result = runner.run(
            commandPath = bashPath,
            arguments = listOf("-c", command),
            stdin = prompt,
            workDirectory = workerDirectory,
            timeoutMs = timeoutMs
        )
        check(result.errorCode == android.app.Activity.RESULT_OK && result.exitCode == 0) {
            safeFailure(worker, result)
        }
        val response = AiCliProtocol.extractResponse(worker, result.stdout)
        check(response.isNotBlank()) {
            "${worker.displayName} returned no final response."
        }
        return AiCliConsultation(
            provider = worker,
            response = response,
            exitCode = result.exitCode,
            elapsedMs = System.currentTimeMillis() - startedAt,
            rawFormat = if (worker == AiCliWorkerId.OPENAI_CODEX) "jsonl" else "json"
        )
    }

    /** Ask all installed workers concurrently; one provider failing does not cancel another. */
    suspend fun consultInstalled(
        prompt: String,
        timeoutMs: Long = DEFAULT_CONSULT_TIMEOUT_MS
    ): Map<AiCliWorkerId, Result<AiCliConsultation>> = supervisorScope {
        val installed = discover()
            .filter { it.state != AiCliWorkerState.NOT_INSTALLED && it.state != AiCliWorkerState.TERMUX_UNAVAILABLE }
        installed.map { status ->
            status.id to async {
                runCatching { consult(status.id, prompt, timeoutMs) }
            }
        }.associate { (id, deferred) -> id to deferred.await() }
    }

    private fun safeFailure(worker: AiCliWorkerId, result: TermuxCommandResult): String {
        val detail = (result.errorMessage.ifBlank { result.stderr.ifBlank { result.stdout } })
            .lineSequence()
            .firstOrNull()
            ?.take(240)
            .orEmpty()
        return "${worker.displayName} did not complete (exit ${result.exitCode}, Termux error ${result.errorCode}). " +
            detail.ifBlank { "Check that its official CLI is installed and signed in." }
    }

    companion object {
        private const val MAX_PROMPT_CHARS = 64_000
        private const val DEFAULT_CONSULT_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
