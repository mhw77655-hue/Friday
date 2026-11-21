#!/usr/bin/env bash
set -euo pipefail

RISK_SOURCE="mobile/app/src/main/java/ai/friday/assistant/android/RiskGate.kt"
EXECUTOR_SOURCE="mobile/app/src/main/java/ai/friday/assistant/cognitive/capability/CapabilityExecutor.kt"
IMMUNE_SOURCE="mobile/app/src/main/java/ai/friday/assistant/cognitive/immune/ImmuneSystem.kt"
CIRCUIT_SOURCE="mobile/app/src/main/java/ai/friday/assistant/failure/CircuitBreaker.kt"
RECOVERY_SOURCE="mobile/app/src/main/java/ai/friday/assistant/failure/RecoveryController.kt"
BACKOFF_SOURCE="mobile/app/src/main/java/ai/friday/assistant/failure/BackoffPolicy.kt"
FAILURE_SURFACE_SOURCE="mobile/app/src/main/java/ai/friday/assistant/failure/FailureSurface.kt"
FAILURE_STORE_SOURCE="mobile/app/src/main/java/ai/friday/assistant/failure/FailureStore.kt"
LOG="$(mktemp)"
RISK_BACKUP="$(mktemp)"
EXECUTOR_BACKUP="$(mktemp)"
IMMUNE_BACKUP="$(mktemp)"
CIRCUIT_BACKUP="$(mktemp)"
RECOVERY_BACKUP="$(mktemp)"
BACKOFF_BACKUP="$(mktemp)"
FAILURE_SURFACE_BACKUP="$(mktemp)"
FAILURE_STORE_BACKUP="$(mktemp)"
cp -p "$RISK_SOURCE" "$RISK_BACKUP"
cp -p "$EXECUTOR_SOURCE" "$EXECUTOR_BACKUP"
cp -p "$IMMUNE_SOURCE" "$IMMUNE_BACKUP"
cp -p "$CIRCUIT_SOURCE" "$CIRCUIT_BACKUP"
cp -p "$RECOVERY_SOURCE" "$RECOVERY_BACKUP"
cp -p "$BACKOFF_SOURCE" "$BACKOFF_BACKUP"
cp -p "$FAILURE_SURFACE_SOURCE" "$FAILURE_SURFACE_BACKUP"
cp -p "$FAILURE_STORE_SOURCE" "$FAILURE_STORE_BACKUP"

restore_sources() {
  cp -p "$RISK_BACKUP" "$RISK_SOURCE"
  cp -p "$EXECUTOR_BACKUP" "$EXECUTOR_SOURCE"
  cp -p "$IMMUNE_BACKUP" "$IMMUNE_SOURCE"
  cp -p "$CIRCUIT_BACKUP" "$CIRCUIT_SOURCE"
  cp -p "$RECOVERY_BACKUP" "$RECOVERY_SOURCE"
  cp -p "$BACKOFF_BACKUP" "$BACKOFF_SOURCE"
  cp -p "$FAILURE_SURFACE_BACKUP" "$FAILURE_SURFACE_SOURCE"
  cp -p "$FAILURE_STORE_BACKUP" "$FAILURE_STORE_SOURCE"
  rm -f "$RISK_BACKUP" "$EXECUTOR_BACKUP" "$IMMUNE_BACKUP" \
    "$CIRCUIT_BACKUP" "$RECOVERY_BACKUP" "$BACKOFF_BACKUP" \
    "$FAILURE_SURFACE_BACKUP" "$FAILURE_STORE_BACKUP"
}
trap restore_sources EXIT

# Mutant 1: remove biometric + confirmation from HIGH-risk authorization.
python3 - "$RISK_SOURCE" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = "if (auth.biometricVerified && auth.confirmationProvided) GateOutcome.Allowed(RiskTier.HIGH)"
mutant = "if (true) GateOutcome.Allowed(RiskTier.HIGH)"
if text.count(needle) != 1:
    raise SystemExit("Expected exactly one HIGH-risk gate expression; refusing ambiguous mutation.")
path.write_text(text.replace(needle, mutant, 1), encoding="utf-8")
PY

# Mutant 2: replace the independent verifier with the invocation's own
# success flag. This bypasses both the failure and completion guards together.
python3 - "$EXECUTOR_SOURCE" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = """val independentlyVerified = if (invocationSucceeded) {
            try {
                evidenceVerifier(request, invocation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        } else false"""
mutant = "val independentlyVerified = invocationSucceeded"
if text.count(needle) != 1:
    raise SystemExit("Expected exactly one independent evidence-verifier block; refusing ambiguous mutation.")
path.write_text(text.replace(needle, mutant, 1), encoding="utf-8")
PY

# Mutant 3: keep the exhausted attempt counter after a successful circuit probe.
python3 - "$IMMUNE_SOURCE" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = "            recoveryAttempts[operationKey]?.set(0)"
mutant = "            // MUTANT: stale attempt budget is not reset after recovery."
if text.count(needle) != 1:
    raise SystemExit("Expected exactly one probe recovery-budget reset; refusing ambiguous mutation.")
path.write_text(text.replace(needle, mutant, 1), encoding="utf-8")
PY

# Mutant 4: allow every caller through while the circuit is HALF_OPEN.
# The single-probe admission invariant must reject this.
python3 - "$CIRCUIT_SOURCE" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = """            State.HALF_OPEN -> false
        }
    }

    fun reset()"""
mutant = """            State.HALF_OPEN -> true
        }
    }

    fun reset()"""
if text.count(needle) != 1:
    raise SystemExit("Expected exactly one half-open probe guard; refusing ambiguous mutation.")
path.write_text(text.replace(needle, mutant, 1), encoding="utf-8")
PY

# Mutant 5: treat an ordinary failed retry as a failed half-open probe,
# opening the circuit prematurely and bypassing the configured retry budget.
python3 - "$RECOVERY_SOURCE" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = """                    entry.breaker.onFailure()
                    surface.recover("""
mutant = """                    entry.breaker.onProbeFailure()
                    surface.recover("""
if text.count(needle) != 1:
    raise SystemExit("Expected exactly one ordinary retry-failure branch; refusing ambiguous mutation.")
path.write_text(text.replace(needle, mutant, 1), encoding="utf-8")
PY

# Mutant 6: accept a negative delay ceiling, allowing an invalid retry schedule.
python3 - "$BACKOFF_SOURCE" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = 'require(maxDelayMs >= 0) { "maxDelayMs must not be negative" }'
mutant = 'require(true) { "MUTANT: negative maxDelayMs accepted" }'
if text.count(needle) != 1:
    raise SystemExit("Expected exactly one max-delay validation; refusing ambiguous mutation.")
path.write_text(text.replace(needle, mutant, 1), encoding="utf-8")
PY

# Mutant 7: evict the oldest aggregate even when it is still an active incident.
python3 - "$FAILURE_SURFACE_SOURCE" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = "aggregates.entries.firstOrNull { !it.value.isActive }?.key"
mutant = "aggregates.entries.firstOrNull()?.key"
if text.count(needle) != 1:
    raise SystemExit("Expected exactly one resolved-only eviction selector; refusing ambiguous mutation.")
path.write_text(text.replace(needle, mutant, 1), encoding="utf-8")
PY

# Mutant 8: mark a failure as persisted before the append actually succeeds.
# The later-snapshot retry test must detect the lost incident.
python3 - "$FAILURE_STORE_SOURCE" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = """                fileStorage.append(StoreKind.FAILURES, toJson(event).toString())
                markSeen(event.id)"""
mutant = """                    markSeen(event.id)
                    fileStorage.append(StoreKind.FAILURES, toJson(event).toString())"""
if text.count(needle) != 1:
    raise SystemExit("Expected exactly one post-append seen marker; refusing ambiguous mutation.")
path.write_text(text.replace(needle, mutant, 1), encoding="utf-8")
PY

set +e
./gradlew :mobile:app:testDebugUnitTest \
  --tests ai.friday.assistant.android.RiskGateTest \
  --tests ai.friday.assistant.cognitive.capability.CapabilityExecutorTest \
  --tests ai.friday.assistant.cognitive.immune.ImmuneSystemTest \
  --tests ai.friday.assistant.failure.CircuitBreakerTest \
  --tests ai.friday.assistant.failure.NervousSystemFailureTest \
  --tests ai.friday.assistant.failure.BackoffPolicyTest \
  --tests ai.friday.assistant.failure.FailureStoreTest \
  --no-build-cache --rerun-tasks >"$LOG" 2>&1
status=$?
set -e

if [[ "$status" -eq 0 ]]; then
  cat "$LOG"
  echo "MUTATION_SURVIVED: critical authorization/verification tests did not detect the deliberate defects." >&2
  exit 1
fi

# A compiler/configuration failure is not a killed mutant. Require the exact
# regression test for each deliberately introduced defect to fail in JUnit XML.
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path("mobile/app/build/test-results/testDebugUnitTest")
targets = {
    "RiskGateTest": ("*RiskGateTest.xml", ["high risk gate rejects every authorization combination"]),
    "CapabilityExecutorTest": ("*CapabilityExecutorTest.xml", ["invocation success without independent postcondition verification"]),
    "ImmuneSystemTest": ("*ImmuneSystemTest.xml", ["successful recovery probe resets the budget"]),
    "CircuitBreakerTest": ("*CircuitBreakerTest.xml", ["only one concurrent half open probe is admitted"]),
    "NervousSystemFailureTest": ("*NervousSystemFailureTest.xml", [
        "retry failures trip circuit at threshold and stop further attempts",
        "retry controller honors configured attempt budget before breaker threshold",
        "aggregate cap never discards active failures when all entries are active",
    ]),
    "BackoffPolicyTest": ("*BackoffPolicyTest.xml", ["invalid bounds and non one based attempts are rejected"]),
    "FailureStoreTest": ("*FailureStoreTest.xml", [
        "exhausted append remains eligible after a later persistence snapshot",
    ]),
}
for suite_name, (pattern, expected_tests) in targets.items():
    files = list(root.glob(pattern))
    if not files:
        raise SystemExit(f"MUTATION_INVALID: no JUnit report for {suite_name}; this is not evidence the mutant was killed.")
    cases = []
    for file in files:
        suite = ET.parse(file).getroot()
        for case in suite.findall(".//testcase"):
            failed = case.find("failure") is not None or case.find("error") is not None
            cases.append((case.attrib.get("name", ""), failed))
    for expected_test in expected_tests:
        matched = [(name, failed) for name, failed in cases if expected_test.lower() in name.lower()]
        if not matched:
            raise SystemExit(f"MUTATION_INVALID: expected regression test '{expected_test}' was not present in {suite_name} report.")
        if not any(failed for _, failed in matched):
            raise SystemExit(f"MUTATION_SURVIVED: {suite_name} did not fail its targeted regression test: {matched}")
        print(f"MUTATION_KILLED: {suite_name}: {matched}")
PY

restore_sources
trap - EXIT
rm -f "$LOG"

# Re-run both focused suites against the restored production source. This guards
# against leaving mutated source or stale compiled classes in the CI workspace.
./gradlew :mobile:app:testDebugUnitTest \
  --tests ai.friday.assistant.android.RiskGateTest \
  --tests ai.friday.assistant.cognitive.capability.CapabilityExecutorTest \
  --tests ai.friday.assistant.cognitive.immune.ImmuneSystemTest \
  --tests ai.friday.assistant.failure.CircuitBreakerTest \
  --tests ai.friday.assistant.failure.NervousSystemFailureTest \
  --tests ai.friday.assistant.failure.BackoffPolicyTest \
  --tests ai.friday.assistant.failure.FailureStoreTest \
  --no-build-cache --rerun-tasks

echo "MUTATION_RESTORED: unmodified authorization, postcondition-verification and immune-recovery suites pass."
