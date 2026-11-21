#!/usr/bin/env bash
set -euo pipefail

SOURCE="mobile/app/src/main/java/ai/friday/assistant/android/RiskGate.kt"
LOG="$(mktemp)"
BACKUP="$(mktemp)"
cp -p "$SOURCE" "$BACKUP"

restore_source() {
  cp -p "$BACKUP" "$SOURCE"
  rm -f "$BACKUP"
}
trap restore_source EXIT

# Remove the biometric + confirmation requirement for HIGH-risk actions.
# The exhaustive RiskGateTest truth table must detect this exact security regression.
python3 - "$SOURCE" <<'PY'
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

set +e
./gradlew :mobile:app:testDebugUnitTest \
  --tests ai.friday.assistant.android.RiskGateTest \
  --no-build-cache --rerun-tasks >"$LOG" 2>&1
status=$?
set -e

if [[ "$status" -eq 0 ]]; then
  cat "$LOG"
  echo "MUTATION_SURVIVED: authorization tests did not detect the weakened HIGH-risk gate." >&2
  exit 1
fi

if ! grep -Eq 'RiskGateTest.*FAILED|There were failing tests' "$LOG"; then
  cat "$LOG"
  echo "MUTATION_INVALID: mutant run failed for a reason other than a RiskGateTest assertion." >&2
  exit 2
fi

echo "MUTATION_KILLED: RiskGateTest rejected the deliberately weakened HIGH-risk authorization gate."

restore_source
trap - EXIT
rm -f "$LOG"

# Re-run the focused suite against the restored production source. This guards
# against leaving mutated classes or stale build outputs in the CI workspace.
./gradlew :mobile:app:testDebugUnitTest \
  --tests ai.friday.assistant.android.RiskGateTest \
  --no-build-cache --rerun-tasks

echo "MUTATION_RESTORED: the unmodified authorization tests pass."
