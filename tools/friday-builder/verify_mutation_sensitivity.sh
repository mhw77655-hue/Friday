#!/usr/bin/env bash
set -euo pipefail

RISK_SOURCE="mobile/app/src/main/java/ai/friday/assistant/android/RiskGate.kt"
EXECUTOR_SOURCE="mobile/app/src/main/java/ai/friday/assistant/cognitive/capability/CapabilityExecutor.kt"
LOG="$(mktemp)"
RISK_BACKUP="$(mktemp)"
EXECUTOR_BACKUP="$(mktemp)"
cp -p "$RISK_SOURCE" "$RISK_BACKUP"
cp -p "$EXECUTOR_SOURCE" "$EXECUTOR_BACKUP"

restore_sources() {
  cp -p "$RISK_BACKUP" "$RISK_SOURCE"
  cp -p "$EXECUTOR_BACKUP" "$EXECUTOR_SOURCE"
  rm -f "$RISK_BACKUP" "$EXECUTOR_BACKUP"
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

# Mutant 2: let an invocation's success flag bypass independent postcondition verification.
python3 - "$EXECUTOR_SOURCE" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = "if (result.success && independentlyVerified) {"
mutant = "if (result.success) {"
if text.count(needle) != 1:
    raise SystemExit("Expected exactly one independently-verified completion gate; refusing ambiguous mutation.")
path.write_text(text.replace(needle, mutant, 1), encoding="utf-8")
PY

set +e
./gradlew :mobile:app:testDebugUnitTest \
  --tests ai.friday.assistant.android.RiskGateTest \
  --tests ai.friday.assistant.cognitive.capability.CapabilityExecutorTest \
  --no-build-cache --rerun-tasks >"$LOG" 2>&1
status=$?
set -e

if [[ "$status" -eq 0 ]]; then
  cat "$LOG"
  echo "MUTATION_SURVIVED: critical authorization/verification tests did not detect the deliberate defects." >&2
  exit 1
fi

# A compiler/configuration failure is not a killed mutant. Require both
# independent test classes to contain assertion failures in their JUnit XML.
python3 - <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
root = Path("mobile/app/build/test-results/testDebugUnitTest")
patterns = {
    "RiskGateTest": "*RiskGateTest.xml",
    "CapabilityExecutorTest": "*CapabilityExecutorTest.xml",
}
for name, pattern in patterns.items():
    files = list(root.glob(pattern))
    if not files:
        raise SystemExit(f"MUTATION_INVALID: no JUnit report for {name}; this is not evidence the mutant was killed.")
    failures = 0
    tests = 0
    for file in files:
        suite = ET.parse(file).getroot()
        failures += int(suite.attrib.get("failures", "0"))
        failures += int(suite.attrib.get("errors", "0"))
        tests += int(suite.attrib.get("tests", "0"))
    if tests == 0 or failures == 0:
        raise SystemExit(f"MUTATION_SURVIVED: {name} had {tests} tests and {failures} failures.")
    print(f"MUTATION_KILLED: {name}: {tests} tests, {failures} assertion/error failures.")
PY

restore_sources
trap - EXIT
rm -f "$LOG"

# Re-run both focused suites against the restored production source. This guards
# against leaving mutated source or stale compiled classes in the CI workspace.
./gradlew :mobile:app:testDebugUnitTest \
  --tests ai.friday.assistant.android.RiskGateTest \
  --tests ai.friday.assistant.cognitive.capability.CapabilityExecutorTest \
  --no-build-cache --rerun-tasks

echo "MUTATION_RESTORED: unmodified authorization and postcondition-verification suites pass."
