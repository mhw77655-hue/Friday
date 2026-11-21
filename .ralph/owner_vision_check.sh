#!/usr/bin/env bash
# Offline contract check: the canonical delivery plan and owner-authored sources exist.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PLAN=".ralph/FRIDAY_DELIVERY_PLAN.md"
test -s "$PLAN"
test -s .ralph/VISION.md
test -s .ralph/OWNER_WORDS.md
test -s AGENTS.md

grep -q "sole active product and delivery plan" "$PLAN"
grep -q "Friday is the individual" "$PLAN"
grep -q "liquid Symbiotic quantum software" "$PLAN"
grep -q "Cross-objective integration target" "$PLAN"
grep -q "Repository purification and product identity gate" "$PLAN"
grep -q "the sole active product and delivery authority" AGENTS.md
grep -q "canonical product line is `main`" AGENTS.md

echo "FRIDAY_DELIVERY_PLAN=PASS"
echo "OWNER_VISION_SOURCE=PASS"
echo "OWNER_WORDS_SOURCE=PASS"
echo "CANONICAL_MAINLINE=PASS"
