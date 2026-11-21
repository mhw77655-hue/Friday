#!/usr/bin/env bash
set -euo pipefail

RISK_SOURCE="mobile/app/src/main/java/ai/friday/assistant/android/RiskGate.kt"
EXECUTOR_SOURCE="mobile/app/src/main/java/ai/friday/assistant/cognitive/capability/CapabilityExecutor.kt"
IMMUNE_SOURCE="mobile/app/src/main/java/ai/friday/assistant/cognitive/immune/ImmuneSystem.kt"
LOG="$(mktemp)"
RISK_BACKUP="$(mktemp)"
EXECUTOR_BACKUP="$(mktemp)"
IMMUNE_BACKUP="$(mktemp)"
cp -p "$RISK_SOURCE" "$RISK_BACKUP"
cp -p "$EXECUTOR_SOURCE" "$EXECUTOR_BACKUP"
cp -p "$IMMUNE_SOURCE" "$IMMUNE_BACKUP"

restore_sources() {
  cp -p "$RISK_BACKUP" "$RISK_SOURCE"
  cp -p "$EXECUTOR_BACKUP" "$EXECUTOR_SOURCE"
  cp -p "$IMMUNE_BACKUP" "$IMMUNE_SOURCE"
  rm -f "$RISK_BACKUP" "$EXECUTOR_BACKUP" "$IMMUNE_BACKUP"
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

set +e
./gradlew :mobile:app:testDebugUnitTest \
  --tests ai.friday.assistant.android.RiskGateTest \
  --tests ai.friday.assistant.cognitive.capability.CapabilityExecutorTest \
  --tests ai.friday.assistant.cognitive.immune.ImmuneSystemTest \
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
    "RiskGateTest": ("*RiskGateTest.xml", "high risk gate rejects every authorization combination"),
    "CapabilityExecutorTest": ("*CapabilityExecutorTest.xml", "invocation success without independent postcondition verification"),
    "ImmuneSystemTest": ("*ImmuneSystemTest.xml", "successful recovery probe resets the budget"),
}
for suite_name, (pattern, expected_test) in targets.items():
    files = list(root.glob(pattern))
    if not files:
        raise SystemExit(f"MUTATION_INVALID: no JUnit report for {suite_name}; this is not evidence the mutant was killed.")
    matched = []
    for file in files:
        suite = ET.parse(file).getroot()
        for case in suite.findall(".//testcase"):
            if expected_test.lower() in case.attrib.get("name", "").lower():
                failed = case.find("failure") is not None or case.find("error") is not None
                matched.append((case.attrib.get("name", ""), failed))
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
  --no-build-cache --rerun-tasks

echo "MUTATION_RESTORED: unmodified authorization, postcondition-verification and immune-recovery suites pass."
