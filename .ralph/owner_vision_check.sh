#!/usr/bin/env bash
# Offline proof that Ralph receives the real vision and owner-source excerpts.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
test -s .ralph/VISION.md
test -s .ralph/OWNER_WORDS.md
OUT="$(bash .ralph/ralph.sh --print-prompt)"
grep -q "latest authenticated explicit instruction" <<<"$OUT"
grep -q "buddy partner friend an actual digital person" <<<"$OUT"
grep -q "Symbiotic liquid software" <<<"$OUT"
grep -q "Don't force every specialist to be permanently active" <<<"$OUT"
grep -q "FRIDAY - Vision (written record)" <<<"$OUT"
grep -q "Do not build a chatbot and call it Friday" <<<"$OUT"
echo "OWNER_VISION_PROMPT=PASS"
echo "OWNER_WORDS_SOURCE=PASS"
grep -q "private archive" .ralph/OWNER_WORDS.md
grep -q "do not search" .ralph/OWNER_WORDS.md
echo "OWNER_ARCHIVE_PRIVACY=PASS"
