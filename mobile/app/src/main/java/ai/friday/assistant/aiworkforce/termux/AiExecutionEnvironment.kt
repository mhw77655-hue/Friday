package ai.friday.assistant.aiworkforce.termux

/**
 * Execution location is independent from the AI provider. Friday may host a
 * worker in an app-managed runtime, Termux, a Linux/proot environment, or
 * another explicitly registered host. Provider adapters must not assume that
 * a particular environment exists.
 */
enum class AiExecutionEnvironmentKind {
    APP_MANAGED,
    TERMUX_NATIVE,
    LINUX_PROOT,
    SHIZUKU_SHELL
}

enum class AiExecutionEnvironmentState {
    READY,
    NOT_INSTALLED,
    PERMISSION_REQUIRED,
    UNSUPPORTED,
    ERROR
}

data class AiExecutionEnvironmentProbe(
    val kind: AiExecutionEnvironmentKind,
    val state: AiExecutionEnvironmentState,
    val detail: String
)

data class AiEnvironmentCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val transportError: String? = null
)

/**
 * Minimal execution contract for subscription-authenticated CLI workers.
 * Implementations own their transport and never expose credential files.
 */
interface AiExecutionEnvironment {
    val kind: AiExecutionEnvironmentKind
    suspend fun probe(): AiExecutionEnvironmentProbe
    suspend fun executeShell(
        command: String,
        stdin: String,
        workingDirectory: String,
        timeoutMs: Long
    ): AiEnvironmentCommandResult
}

/**
 * Chooses the first healthy registered environment. Registration order is a
 * policy decision (for example app-managed first, then Termux, then proot).
 * A failed candidate never prevents discovery of the remaining environments.
 */
class AiExecutionEnvironmentRegistry(
    private val candidates: List<AiExecutionEnvironment>
) {
    suspend fun discover(): List<AiExecutionEnvironmentProbe> = candidates.map { candidate ->
        runCatching { candidate.probe() }.getOrElse {
            AiExecutionEnvironmentProbe(
                candidate.kind,
                AiExecutionEnvironmentState.ERROR,
                it.message?.take(240) ?: "Environment probe failed."
            )
        }
    }

    suspend fun selectReady(): Pair<AiExecutionEnvironment, AiExecutionEnvironmentProbe>? {
        for (candidate in candidates) {
            val result = runCatching { candidate.probe() }.getOrNull() ?: continue
            if (result.state == AiExecutionEnvironmentState.READY) return candidate to result
        }
        return null
    }
}
