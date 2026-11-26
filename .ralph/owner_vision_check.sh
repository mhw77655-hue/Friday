#!/usr/bin/env bash
# Offline contract check: canonical plan, owner-authored sources and resolved product identity.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PLAN=".ralph/FRIDAY_DELIVERY_PLAN.md"
test -s "$PLAN"
test -s .ralph/VISION.md
test -s .ralph/OWNER_WORDS.md
test -s AGENTS.md

grep -q "sole active delivery plan" "$PLAN"
grep -q "Friday is the individual" "$PLAN"
grep -q "liquid Symbiotic quantum software" "$PLAN"
grep -q "Cross-objective integration contracts" "$PLAN"
grep -q "Install-once identity and update channel" "$PLAN"
grep -qi "sole active product and delivery authority" AGENTS.md
grep -Fq 'canonical product line is `main`' AGENTS.md
grep -q "ai.friday.assistant" "$PLAN"
grep -q "Termux is not a Friday APK component" "$PLAN"
grep -q "REA means the upstream Reverse Engineer Anything tool" "$PLAN"
grep -q "Chronological delivery roadmap" "$PLAN"

echo "FRIDAY_DELIVERY_PLAN=PASS"
echo "OWNER_VISION_SOURCE=PASS"
echo "OWNER_WORDS_SOURCE=PASS"
echo "CANONICAL_MAINLINE=PASS"
echo "FRIDAY_PACKAGE_IDENTITY_DECISION=PASS"
