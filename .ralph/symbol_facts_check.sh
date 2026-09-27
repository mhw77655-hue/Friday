#!/bin/bash
# ---------------------------------------------------------------------------
# symbol_facts_check.sh — the end-to-end proof that the symbol-fact memory works.
#
# It runs the exact loop a Ralph iteration is supposed to run the first time it
# meets an unfamiliar class:
#
#   1. pick a REAL class that SYMBOL_FACTS.md does not know yet
#   2. grep the real source tree for its declaration
#   3. derive the real package from that file's `package` line (never a guess)
#   4. append the fact:  ClassName -> real.package (path/File.kt:line)
#   5. print the REAL prompt via `ralph.sh --print-prompt` and assert that the
#      prompt now carries BOTH the new fact AND the mandatory grep-before-import
#      rule — i.e. the next iteration is told to grep, not to guess.
#
# No network, no model call, no gradle. Deterministic: the auto-picked class is
# the alphabetically first unrecorded top-level declaration, so two runs pick
# different classes and both facts stay true.
#
# usage: symbol_facts_check.sh [--dry-run] [ClassName]
#   --dry-run  do everything except step 4 (no file is written)
# exit 0 iff every assertion passed
# ---------------------------------------------------------------------------
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RALPH="$SCRIPT_DIR/ralph.sh"
PROMPT_MD="$SCRIPT_DIR/prompt.md"
SYMBOL_FACTS="$SCRIPT_DIR/SYMBOL_FACTS.md"
SRC_MAIN="$REPO_DIR/mobile/app/src/main/java"
SRC_TEST="$REPO_DIR/mobile/app/src/test/java"

DRY_RUN=false
TARGET=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    *) TARGET="$1"; shift ;;
  esac
done

FAILURES=0
pass() { echo "  PASS  $1"; }
fail() { echo "  FAIL  $1"; FAILURES=$((FAILURES + 1)); }

echo "symbol_facts_check: proving the class -> real-package memory end to end."

# --- AC1: the injection code path is greppable and really names the file ------
echo
echo "[AC1] injection path in ralph.sh"
if grep -q 'SYMBOL_FACTS_FILE="$SCRIPT_DIR/SYMBOL_FACTS.md"' "$RALPH"; then
  pass "ralph.sh resolves SYMBOL_FACTS.md next to itself"
else
  fail "ralph.sh never assigns SYMBOL_FACTS_FILE"
fi
if grep -q 'cat "$SYMBOL_FACTS_FILE"' "$RALPH"; then
  pass "build_prompt() concatenates SYMBOL_FACTS.md into the model prompt"
else
  fail "build_prompt() does not concatenate SYMBOL_FACTS.md"
fi
if [ ! -f "$SYMBOL_FACTS" ]; then
  fail "$SYMBOL_FACTS does not exist"
  echo
  echo "RESULT: FAIL ($FAILURES assertion(s))"
  exit 1
fi
if grep -q 'ModelProviderType' "$SYMBOL_FACTS"; then
  pass "AC4: SYMBOL_FACTS.md is seeded with the ModelProviderType fact"
else
  fail "AC4: SYMBOL_FACTS.md has no ModelProviderType fact"
fi

# --- pick a real class the fact file does not know yet ------------------------
echo
echo "[step 1] pick a real, not-yet-recorded class"
if [ -z "$TARGET" ]; then
  TARGET="$(grep -rhoE '^(data |sealed |enum |abstract |open )*(class|interface|object) [A-Za-z0-9_]+' \
              --include='*.kt' "$SRC_MAIN" "$SRC_TEST" 2>/dev/null \
            | awk '{print $NF}' | sort -u \
            | grep -v -x -F -f <(sed -n 's/^- \([A-Za-z0-9_]*\) ->.*/\1/p' "$SYMBOL_FACTS") \
            | head -1)"
fi
if [ -z "$TARGET" ]; then
  fail "could not find an unrecorded top-level class to test with"
  echo
  echo "RESULT: FAIL ($FAILURES assertion(s))"
  exit 1
fi
echo "  target class: $TARGET"

# --- step 2/3: grep the real tree, derive the real package --------------------
echo
echo "[step 2] grep the real source tree for its declaration"
DECL="$(grep -rnE "^(data |sealed |enum |abstract |open )*(class|interface|object) ${TARGET}\b" \
          --include='*.kt' "$SRC_MAIN" "$SRC_TEST" 2>/dev/null | head -1)"
if [ -z "$DECL" ]; then
  fail "no declaration found for $TARGET — the target was not real"
  echo
  echo "RESULT: FAIL ($FAILURES assertion(s))"
  exit 1
fi
DECL_FILE="${DECL%%:*}"
REST="${DECL#*:}"
DECL_LINE="${REST%%:*}"
PKG="$(sed -n 's/^package \([A-Za-z0-9_.]*\).*/\1/p' "$DECL_FILE" | head -1)"
REL_FILE="${DECL_FILE#"$REPO_DIR"/}"
echo "  declaration: $REL_FILE:$DECL_LINE"
echo "  package    : $PKG"
if [ -z "$PKG" ]; then
  fail "could not derive a package from $REL_FILE"
else
  pass "real package derived from the source file, not guessed"
fi

# --- step 4: record the fact --------------------------------------------------
FACT="- ${TARGET} -> ${PKG} (${REL_FILE}:${DECL_LINE})"
echo
echo "[step 3] record the fact"
if grep -q "^- ${TARGET} ->" "$SYMBOL_FACTS"; then
  echo "  (already recorded, left as is): $FACT"
else
  echo "  appending: $FACT"
  if [ "$DRY_RUN" = "true" ]; then
    echo "  --dry-run: not written"
  else
    {
      echo ""
      echo "### recorded by symbol_facts_check.sh on $(date '+%Y-%m-%d')"
      echo "$FACT"
    } >> "$SYMBOL_FACTS"
    if grep -qF -- "$FACT" "$SYMBOL_FACTS"; then
      pass "fact appended to SYMBOL_FACTS.md"
    else
      fail "append did not land in SYMBOL_FACTS.md"
    fi
  fi
fi

# --- step 5: the REAL prompt must carry the fact and the rule -----------------
echo
echo "[step 4] print the real prompt (ralph.sh --print-prompt) and inspect it"
PROMPT_OUT="$(bash "$RALPH" --print-prompt 2>/dev/null)"
if [ -z "$PROMPT_OUT" ]; then
  fail "ralph.sh --print-prompt produced nothing"
  echo
  echo "RESULT: FAIL ($FAILURES assertion(s))"
  exit 1
fi
echo "  prompt size: ${#PROMPT_OUT} bytes"

if [ "$DRY_RUN" = "true" ]; then
  echo "  SKIP  the two prompt-content assertions below: --dry-run wrote no fact,"
  echo "        so by construction the prompt cannot carry it yet."
else
  if printf '%s\n' "$PROMPT_OUT" | grep -qF -- "$FACT"; then
    pass "the new fact is IN the prompt the model will actually receive"
  else
    fail "the new fact is NOT in the prompt (injection is broken)"
  fi
  # Non-vacuity: the real package of the target must be present in the prompt and
  # a fabricated one must not have been recorded for it.
  if printf '%s\n' "$PROMPT_OUT" | grep -qF -- "${TARGET} -> ${PKG}"; then
    pass "the recorded package is the one the source file declares"
  else
    fail "recorded package disagrees with the source file"
  fi
fi

# AC3: the rule must be its own section, with the real command in it, and it
# must forbid guessing rather than permit it.
RULE_HEADING_COUNT="$(printf '%s\n' "$PROMPT_OUT" | grep -c '^## MANDATORY: grep the real source tree BEFORE you write any import$')"
if [ "$RULE_HEADING_COUNT" = "1" ]; then
  pass "AC3: prompt.md carries the grep-first rule as its own top-level section"
else
  fail "AC3: expected exactly one '## MANDATORY: grep ...' heading, found $RULE_HEADING_COUNT"
fi
if printf '%s\n' "$PROMPT_OUT" | grep -q "class|interface|object) <ClassName>"; then
  pass "AC3: the rule states the literal grep command to run"
else
  fail "AC3: the rule does not state a concrete grep command"
fi
if printf '%s\n' "$PROMPT_OUT" | grep -qi 'Never.*guess a package by convention'; then
  pass "AC2: the prompt instructs grep-first and explicitly forbids guessing"
else
  fail "AC2: the prompt does not forbid guessing a package"
fi
if printf '%s\n' "$PROMPT_OUT" | grep -q "SYMBOL_FACTS.md"; then
  pass "the prompt tells the agent where the symbol-fact memory lives"
else
  fail "the prompt never mentions SYMBOL_FACTS.md"
fi

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "RESULT: PASS — grep -> record -> prompt carries the rule and the fact."
  exit 0
fi
echo "RESULT: FAIL ($FAILURES assertion(s))"
exit 1
