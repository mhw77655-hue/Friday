# INCIDENT: <short-title>

<!-- Copy this template to .ralph/incidents/<YYYY-MM-DD>_<short-id>.md -->
<!-- Fill every section. Empty or missing sections cause auto-reject. -->

## WHAT

<!-- Exact failure signature: crash type, verbatim error message, full stack trace incl. native frames.
     If no stack trace exists, state that explicitly and describe the observable failure. -->

## WHERE

<!-- Specific subsystem/file/function — not a vague area name.
     Include environment: Termux version, Android API level, CPU ABI, model backend, quantization.
     If environment is unknown, say so. -->

## WHEN

<!-- Commit hash at failure time, timestamp, active Ralph run/story, last known-good commit.
     Example: "Failed at commit abc1234 during RALPH run iteration 5 (story VOICE-CRASH).
     Last known-good: commit def5678." -->

## WHY

<!-- Root cause with proving log line, OR explicit list of ruled-out hypotheses.
     If root cause is unknown, state "root cause unknown" and list what was checked.
     Restating the crash instead of explaining it FAILS this section. -->

## MITIGATION

<!-- What was done to stop the immediate bleeding.
     Label each action as PERMANENT-FIX or STOPGAP.
     Example: "- STOPGAP: disabled voice pipeline (commit d9ca295)"
     Example: "- PERMANENT-FIX: added null-check before native call (commit abc1234)" -->

## REPRODUCTION

<!-- Exact repro steps, or explicit "not currently reproducible" statement.
     Include device state, prior actions, and any timing dependencies. -->

## STATUS

<!-- One of: open | mitigated | root-caused | fixed | wontfix
     Also state what would change the status.
     Example: "mitigated — will move to root-caused when log capture is added."
     Example: "fixed — permanent fix landed in commit abc1234." -->
