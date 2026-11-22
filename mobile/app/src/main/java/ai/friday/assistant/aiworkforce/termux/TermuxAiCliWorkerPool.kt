package ai.friday.assistant.aiworkforce.termux

import android.content.Context
import ai.friday.assistant.aiworkforce.AiWorkerConsultationPort
import ai.friday.assistant.aiworkforce.AiWorkerReply
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

enum class AiCliWorkerId(val displayName: String, val commandName: String) {
    OPENAI_CODEX("ChatGPT / OpenAI Codex", "codex"),
    ANTHROPIC_CLAUDE("Claude Code", "claude"),
    GOOGLE_GEMINI("Google Gemini CLI", "gemini")
}

enum class AiCliWorkerState {
    AVAILABLE,
    NOT_INSTALLED,
    AUTHENTICATION_NOT_CONFIRMED,
    TERMUX_UNAVAILABLE,
    EXECUTION_ENVIRONMENT_UNAVAILABLE
}

data class AiCliWorkerStatus(
    val id: AiCliWorkerId,
    val state: AiCliWorkerState,
    val detail: String,
    val environment: AiExecutionEnvironmentKind? = null
)

data class AiCliConsultation(
    val provider: AiCliWorkerId,
    val response: String,
    val exitCode: Int,
    val elapsedMs: Long,
    val rawFormat: String,
    val environment: AiExecutionEnvironmentKind
)

/**
 * Friday's subscription-authenticated AI workforce. Provider identity and
 * execution location are separate: the registry selects any healthy
 * environment capable of running a compatible official CLI. Termux is the
 * first concrete adapter; app-managed and Linux/proot adapters can be added
 * without changing provider protocols or Friday's orchestration layer.
 *
 * Provider credentials remain in the environment's own official CLI store.
 * Friday never reads, copies, or logs those credentials.
 */
internal class TermuxAiCliWorkerPool(
    context: Context,
    private val environments: AiExecutionEnvironmentRegistry =
        AiExecutionEnvironmentRegistry(listOf(TermuxAiExecutionEnvironment(context.applicationContext)))
) : AiWorkerConsultationPort {
    private val workerDirectory = "~/.friday-ai-worker-sandbox"

    suspend fun discover(): List<AiCliWorkerStatus> {
        val ready = environments.readyEnvironments()
        if (ready.isEmpty()) {
            val probes = environments.discover()
            val detail = probes.joinToString(" ") { "${it.kind}: ${it.detail}" }
                .ifBlank { "No AI execution environment has been registered." }
            val state = if (probes.any { it.state == AiExecutionEnvironmentState.PERMISSION_REQUIRED })
                AiCliWorkerState.TERMUX_UNAVAILABLE
            else AiCliWorkerState.EXECUTION_ENVIRONMENT_UNAVAILABLE
            return AiCliWorkerId.entries.map { AiCliWorkerStatus(it, state, detail) }
        }

        return AiCliWorkerId.entries.map { worker ->
            val located = ready.firstNotNullOfOrNull { (environment, _) ->
                val result = runCatching {
                    environment.executeShell(
                        command = "command -v ${worker.commandName}",
                        stdin = "",
                        workingDirectory = workerDirectory,
                        timeoutMs = 15_000L
                    )
                }.getOrNull()
                if (result != null && result.transportError == null &&
                    result.exitCode == 0 && result.stdout.isNotBlank()
                ) environment else null
            }
            if (located != null) {
                val setup = runCatching {
                    located.executeShell(
                        command = "mkdir -p ~/.friday-ai-worker-sandbox && printf READY",
                        stdin = "",
                        workingDirectory = workerDirectory,
                        timeoutMs = 15_000L
                    )
                }.getOrNull()
                if (setup != null && setup.transportError == null && setup.exitCode == 0) {
                    AiCliWorkerStatus(
                        worker,
                        AiCliWorkerState.AUTHENTICATION_NOT_CONFIRMED,
                        "CLI found in ${located.kind}. Its account login is checked on first consultation.",
                        located.kind
                    )
                } else {
                    AiCliWorkerStatus(
                        worker,
                        AiCliWorkerState.EXECUTION_ENVIRONMENT_UNAVAILABLE,
                        "The isolated workspace could not be prepared in ${located.kind}.",
                        located.kind
                    )
                }
            } else {
                AiCliWorkerStatus(
                    worker,
                    AiCliWorkerState.NOT_INSTALLED,
                    "${worker.commandName} CLI was not found in any ready environment: " +
                        ready.joinToString { it.first.kind.name } + "."
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
        val ready = environments.readyEnvironments()
        val environment = ready.firstNotNullOfOrNull { (candidate, _) ->
            val probe = runCatching {
                candidate.executeShell(
                    command = "command -v ${worker.commandName}",
                    stdin = "",
                    workingDirectory = workerDirectory,
                    timeoutMs = 15_000L
                )
            }.getOrNull()
            if (probe != null && probe.transportError == null &&
                probe.exitCode == 0 && probe.stdout.isNotBlank()
            ) candidate else null
        } ?: throw IllegalStateException(
            "${worker.displayName} is not installed in any ready AI execution environment."
        )

        val startedAt = System.currentTimeMillis()
        val result = environment.executeShell(
            command = AiCliProtocol.command(worker),
            stdin = prompt,
            workingDirectory = workerDirectory,
            timeoutMs = timeoutMs
        )
        check(result.transportError == null && result.exitCode == 0) {
            safeFailure(worker, environment.kind, result)
        }
        val response = AiCliProtocol.extractResponse(worker, result.stdout)
        check(response.isNotBlank()) { "${worker.displayName} returned no final response." }
        return AiCliConsultation(
            provider = worker,
            response = response,
            exitCode = result.exitCode,
            elapsedMs = System.currentTimeMillis() - startedAt,
            rawFormat = if (worker == AiCliWorkerId.OPENAI_CODEX) "jsonl" else "json",
            environment = environment.kind
        )
    }

    override suspend fun consultAvailable(prompt: String): List<AiWorkerReply> =
        consultInstalled(prompt).mapNotNull { (provider, result) ->
            result.getOrNull()?.let {
                AiWorkerReply(
                    providerId = provider.name,
                    response = it.response,
                    executionEnvironment = it.environment.name
                )
            }
        }

    /** Ask every discovered worker concurrently; a failure never cancels another provider. */
    suspend fun consultInstalled(
        prompt: String,
        timeoutMs: Long = DEFAULT_CONSULT_TIMEOUT_MS
    ): Map<AiCliWorkerId, Result<AiCliConsultation>> = supervisorScope {
        val installed = discover().filter {
            it.state != AiCliWorkerState.NOT_INSTALLED &&
                it.state != AiCliWorkerState.TERMUX_UNAVAILABLE &&
                it.state != AiCliWorkerState.EXECUTION_ENVIRONMENT_UNAVAILABLE
        }
        installed.map { status ->
            status.id to async { runCatching { consult(status.id, prompt, timeoutMs) } }
        }.associate { (id, deferred) -> id to deferred.await() }
    }

    private fun safeFailure(
        worker: AiCliWorkerId,
        environment: AiExecutionEnvironmentKind,
        result: AiEnvironmentCommandResult
    ): String {
        val detail = (result.transportError ?: result.stderr.ifBlank { result.stdout })
            .lineSequence().firstOrNull()?.take(240).orEmpty()
        return "${worker.displayName} did not complete in $environment (exit ${result.exitCode}). " +
            detail.ifBlank { "Check that its official CLI is installed and signed in." }
    }

    companion object {
        private const val MAX_PROMPT_CHARS = 64_000
        private const val DEFAULT_CONSULT_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
