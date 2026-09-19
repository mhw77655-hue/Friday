package com.jarvis.app.buildtwin

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * BUILD-TWIN-ARM64-VERIFICATION (AC4) — persists a verification run's FULL
 * output (pass or fail) into `.ralph/incidents/` using the repo's existing
 * incident-report format (see `.ralph/incidents/TEMPLATE.md` and
 * `2026-07-28_voice_pipeline.md`). Never just a pass/fail flag: the verdict's
 * complete run report (health payload, GGUF step stdout/stderr, reasons)
 * lands in the file so a future reader can re-diagnose without the run.
 *
 * [incidentsDir] is injectable (production: the repo's `.ralph/incidents`;
 * tests: a temp dir). The file is named `<yyyy-MM-dd>_buildtwin_verify.md`.
 */
class IncidentReportWriter(private val incidentsDir: File) {

    data class RunMeta(
        val runLabel: String,
        val commit: String?,
        val storyId: String,
        val subsystem: String,
        val component: String
    )

    /** Write one run's TEMPLATE-format incident; returns the written file. */
    fun write(meta: RunMeta, verdict: BuildTwinVerifier.BuildTwinVerdict): File {
        if (!incidentsDir.exists() && !incidentsDir.mkdirs()) {
            throw IllegalStateException("cannot create incidents dir: $incidentsDir")
        }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date())
        val file = File(incidentsDir, "${date}_buildtwin_verify.md")
        val body = render(meta, verdict)
        file.writeText(body)
        return file
    }

    private fun render(meta: RunMeta, verdict: BuildTwinVerifier.BuildTwinVerdict): String {
        val outcome = if (verdict.passed) "passed" else "failed"
        val status = if (verdict.passed) "root-caused" else "mitigated"
        val whatResult = if (verdict.passed) {
            "none — health.healthy=true and GGUF step exit=0. (${verdict.summary})"
        } else {
            "${verdict.summary}. health.healthy=${verdict.health.healthy}; " +
                "health.error=${verdict.health.error ?: "n/a"}; " +
                "gguf.exit=${verdict.step?.exitCode ?: "n/a"}."
        }
        return buildString {
            appendLine("# INCIDENT: BuildTwin verification run — ${meta.runLabel} ($outcome)")
            appendLine()
            appendLine("## WHAT")
            appendLine()
            appendLine("A full BuildTwin verification run over the ARM64/CPU-only " +
                "verification twin for story ${meta.storyId}. Verdict: " +
                "${if (verdict.passed) "PASSED — artifact promotion AUTHORIZED" else "FAILED — artifact promotion REFUSED"}. " +
                "Failure signature (if any): $whatResult")
            appendLine()
            appendLine("## WHERE")
            appendLine()
            appendLine("Subsystem: BuildTwinVerifier (${meta.component}); " +
                "story ${meta.storyId}; subsystem '${meta.subsystem}'. " +
                "VoiceForge health probe is the real GET to /health; the GGUF step " +
                "runs on the twin. Environment: " +
                "arm64-v8a target (Realme 9 Pro 5G mirror, CPU-only, 6-8GB ceiling).")
            appendLine()
            appendLine("Run report (full, verbatim), captured by the verifier:")
            appendLine()
            verdict.reportText.lines().forEach { appendLine("    $it") }
            appendLine()
            appendLine("## WHEN")
            appendLine()
            appendLine("Run label: ${meta.runLabel}. Commit at run time: " +
                "${meta.commit ?: "(not recorded)"}. Date: the run file's date prefix.")
            appendLine()
            appendLine("## WHY")
            appendLine()
            if (verdict.passed) {
                appendLine("No failure. The twin satisfied both gate stages: " +
                    "health.healthy=true and GGUF step exit=0, so the artifact is " +
                    "authorized to be copied to the phone's models directory.")
            } else {
                appendLine("Gate refused promotion because a real gate condition was " +
                    "not met (proving log lines above). Candidate causes, in order:")
                if (!verdict.health.healthy) {
                    appendLine("- (confirmed) twin health is not healthy:true — " +
                        "${verdict.health.error ?: "no error detail"}. The gate " +
                        "correctly refuses promotion on a non-honest-healthy twin.")
                }
                verdict.step?.let { st ->
                    if (st.exitCode != 0) {
                        appendLine("- (confirmed) GGUF step exited ${st.exitCode}, not 0 — " +
                            "the conversion/quantization attempt on the twin itself failed.")
                    }
                }
                if (verdict.health.error.isNullOrEmpty() && verdict.step?.exitCode == 0) {
                    appendLine("- (unexplained) both stages nominally passed yet verdict is " +
                        "failed — investigate verifier accounting before promotion.")
                }
            }
            appendLine()
            appendLine("## MITIGATION")
            appendLine()
            if (verdict.passed) {
                appendLine("- ARTIFACT PROMOTION AUTHORIZED by this run (PERMANENT-FIX, " +
                    "verifier-blessed).")
            } else {
                appendLine("- PROMOTION HELD / artifact NOT copied to the phone " +
                    "(deliberate gate behavior, PERMANENT-FIX for the twin-gate flow). " +
                    "The failing stage must be re-run on the twin until healthy:true " +
                    "+ exit-0, then re-verified before any copy.")
            }
            appendLine()
            appendLine("## REPRODUCTION")
            appendLine()
            appendLine("Provision a twin (bash .ralph/buildtwin/provision.sh), then run " +
                "BuildTwinVerifier with the same BuildTwinSpec (host, /health, optional " +
                "GGUF step). Deterministic: the gate's verdict reproduces with the same " +
                "twin state. Local equivalent: run 'voiceforge/voiceforge_server.py' and " +
                "probe http://127.0.0.1:8765/health at the configured port.")
            appendLine()
            appendLine("## STATUS")
            appendLine()
            appendLine("$status — verdict=${if (verdict.passed) "passed" else "failed"}, " +
                "authorizedForPromotion=${verdict.authorizedForPromotion}. " +
                "See REPO_FACTS.md 'BUILD-TWIN ARM64' standing rule: any future " +
                "native/ARM64-sensitive story must reference a twin-verified incident " +
                "log entry like this one before passes can flip to true.")
        }
    }
}