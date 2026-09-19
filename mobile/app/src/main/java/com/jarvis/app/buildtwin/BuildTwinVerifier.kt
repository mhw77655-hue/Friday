package com.jarvis.app.buildtwin

/**
 * BUILD-TWIN-ARM64-VERIFICATION (AC3) — the gate that decides whether a
 * hardware-sensitive build artifact is authorized to be copied to the phone.
 *
 * The gate runs ON THE TWIN FIRST:
 *  1. any pending GGUF conversion/quantization step is executed on the twin
 *     (via an injected [TwinCommandRunner] — production is
 *     [LocalTwinCommandRunner], which runs the command locally; when the twin
 *     is remote that command is an `ssh ubuntu@<twin> ...` wrapper);
 *  2. then the voiceforge server health check is probed on the twin
 *     (via an injected [TwinHealthProbe] — production is
 *     [HttpTwinHealthProbe], a real GET over the spec's health URL).
 *
 * ONLY a healthy:true health response AND an exit-0 step authorize copying
 * the artifact back to the phone's models directory. Any failure is recorded
 * honestly (full run output in the verdict) so the run can be written into
 * `.ralph/incidents/` via [IncidentReportWriter] — not just a pass/fail flag.
 *
 * The gate NEVER runs the native/ARM64-sensitive build itself on a looser
 * host first; this component is the only sanctioned authorizing authority.
 */
class BuildTwinVerifier(
    private val spec: BuildTwinSpec,
    private val probe: TwinHealthProbe,
    private val runner: TwinCommandRunner
) {

    /**
     * Full verdict of one verification run: the twin health read, the GGUF
     * step outcome (if any), and whether artifact promotion is authorized.
     * [reportText] is a complete incident-format run report (TEMPLATE-form)
     * produced by this run — pass or fail — ready for
     * [IncidentReportWriter] to persist.
     */
    data class BuildTwinVerdict(
        val passed: Boolean,
        val authorizedForPromotion: Boolean,
        val health: TwinHealth,
        val step: TwinCommandResult?,
        val reasons: List<String>,
        val reportText: String
    ) {
        /** Short one-line status for dashboards/console. */
        val summary: String
            get() = "build-twin verify ${if (passed) "PASSED" else "FAILED"} " +
                "(health.healthy=${health.healthy}, step.exit=${step?.exitCode ?: "n/a"})"
    }

    /** Run the full twin gate. Never throws: every failure is recorded. */
    fun verify(): BuildTwinVerdict {
        // 1) GGUF conversion/quantization attempt ON THE TWIN FIRST.
        val step: TwinCommandResult? = spec.ggufStepCommand?.let { cmd ->
            runner.run(cmd)
        }

        // 2) voiceforge health check on the twin.
        val health: TwinHealth = runCatching { probe.probe(spec) }
            .getOrElse { e -> TwinHealth(healthy = false, error = "probe failed: ${e.message}") }

        val stepOk = step == null || step.exitCode == 0
        val healthOk = health.healthy
        val passed = healthOk && stepOk

        val reasons = buildList {
            if (healthOk) add("health.healthy=true on $spec")
            else add("health.healthy=false (${health.error ?: "no error detail"}) on ${spec.host}")
            if (step == null) add("no GGUF step configured")
            else if (stepOk) add("GGUF step exit=0")
            else add("GGUF step exit=${step.exitCode}: ${step.stderr.take(160)}")
        }

        return BuildTwinVerdict(
            passed = passed,
            authorizedForPromotion = passed,
            health = health,
            step = step,
            reasons = reasons,
            reportText = render(health, step, passed, reasons)
        )
    }

    private fun render(
        health: TwinHealth,
        step: TwinCommandResult?,
        passed: Boolean,
        reasons: List<String>
    ): String {
        val b = StringBuilder()
        b.appendLine("BUILD-TWIN VERIFICATION RUN")
        b.appendLine("=".repeat(40))
        b.appendLine("twin:    ${spec.host} (arch=${spec.arch} cpuOnly=${spec.cpuOnly} ramCapGb=${spec.ramCapGb}GiB)")
        b.appendLine("health:  ${spec.healthUrl}")
        b.appendLine("gguf:    ${spec.ggufStepCommand ?: "none"}")
        b.appendLine("verdict: ${if (passed) "PASSED" else "FAILED"}")
        reasons.forEach { b.appendLine("  - $it") }
        b.appendLine("--- twin health payload ---")
        b.appendLine(health.rawResponse ?: "health.rawResponse=null error=${health.error ?: "n/a"}")
        if (step != null) {
            b.appendLine("--- GGUF step stdout ---")
            b.appendLine(step.stdout.take(4000).ifEmpty { "(empty)" })
            b.appendLine("--- GGUF step stderr ---")
            b.appendLine(step.stderr.take(4000).ifEmpty { "(empty)" })
        }
        b.appendLine("--- authorizedForPromotion=${if (passed) "true" else "false"} ---")
        b.appendLine("Only a PASSED verdict authorizes copying this artifact back to the phone's models directory.")
        return b.toString()
    }
}

/**
 * A single twin health read. [healthy] mirrors the voiceforge /health
 * `healthy` boolean; [checkpoint]/[error] mirror the same payload fields.
 */
data class TwinHealth(
    val healthy: Boolean,
    val checkpoint: String? = null,
    val error: String? = null,
    val rawResponse: String? = null
)

/** Outcome of running the GGUF conversion/quantization step on the twin. */
data class TwinCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

/** Injectable twin health probe (tests fake it; production is HTTP). */
fun interface TwinHealthProbe {
    fun probe(spec: BuildTwinSpec): TwinHealth
}

/**
 * Injectable step runner (tests fake it; production is a local process).
 * [run] with an explicit timeout for the full signal; [run] with a single
 * argument uses the [DEFAULT_TIMEOUT_SECONDS] band.
 */
interface TwinCommandRunner {
    fun run(command: String, timeoutSeconds: Int): TwinCommandResult

    fun run(command: String): TwinCommandResult = run(command, DEFAULT_TIMEOUT_SECONDS)

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS: Int = 600
    }
}