#!/bin/bash

print_progress_bar() {
  local total=$(grep -c '"id":' /sdcard/jarvis-repo/.ralph/prd.json 2>/dev/null)
  local passed=$(grep -c '"passes": *true' /sdcard/jarvis-repo/.ralph/prd.json 2>/dev/null)
  [ -z "$total" ] && total=1
  [ "$total" -eq 0 ] && total=1
  local pct=$(( passed * 100 / total ))
  local filled=$(( pct / 5 ))
  local bar=""
  for ((k=0; k<20; k++)); do
    if [ $k -lt $filled ]; then bar="${bar}#"; else bar="${bar}-"; fi
  done
  echo "Progress: [${bar}] ${pct}% (${passed}/${total} stories passing)"
}
set -e

TOOL="opencode"
MAX_ITERATIONS=10
DOWNLOAD_DIR="/sdcard/Download"

MODEL_POOL=(
  "opencode/big-pickle"
  "omniroute/auto/best-free"
  "omniroute/oc/hy3-free"
  "omniroute/opencode/deepseek-v4-flash-free"
  "omniroute/openrouter/z-ai/glm-5.2:free"
  "omniroute/openrouter/nvidia/nemotron-3-ultra-550b-a55b:free"
  "omniroute/openrouter/openai/gpt-oss-20b:free"
  "omniroute/oc/nemotron-3-ultra-free"
  "omniroute/oc/mimo-v2.5-free"
)

while [[ $# -gt 0 ]]; do
  case $1 in
    --tool) TOOL="$2"; shift 2 ;;
    --tool=*) TOOL="${1#*=}"; shift ;;
    --model) MODEL_POOL=("$2"); shift 2 ;;
    --model=*) MODEL_POOL=("${1#*=}"); shift ;;
    *)
      if [[ "$1" =~ ^[0-9]+$ ]]; then MAX_ITERATIONS="$1"; fi
      shift
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROMPT_FILE="$SCRIPT_DIR/prompt.md"
FACTS_FILE="$SCRIPT_DIR/REPO_FACTS.md"
PROGRESS_FILE="$SCRIPT_DIR/progress.txt"
RUN_LOG="$SCRIPT_DIR/ralph_run_$(date +%Y%m%d_%H%M%S).log"
touch "$RUN_LOG"
PRD_FILE="$SCRIPT_DIR/prd.json"
GRADLE_PROPS="$REPO_DIR/gradle.properties"
REJECT_HELPER="$SCRIPT_DIR/reject_story.py"

if [ ! -f "$PROGRESS_FILE" ]; then
  echo "# Ralph Progress Log" > "$PROGRESS_FILE"
  echo "Started: $(date)" >> "$PROGRESS_FILE"
  echo "---" >> "$PROGRESS_FILE"
fi

if [ ! -f "$FACTS_FILE" ]; then
  echo "# REPO_FACTS.md" > "$FACTS_FILE"
  echo "(no facts recorded yet)" >> "$FACTS_FILE"
fi

if [ ! -f "$REJECT_HELPER" ]; then
  echo "FATAL: $REJECT_HELPER not found. Place reject_story.py next to ralph.sh in .ralph/ before running."
  exit 1
fi

# ---------------------------------------------------------------------------
# Enable gradle caching/parallel builds
# ---------------------------------------------------------------------------
touch "$GRADLE_PROPS"
for LINE in "org.gradle.caching=true" "org.gradle.parallel=true" "org.gradle.configuration-cache=true"; do
  KEY="${LINE%%=*}"
  if ! grep -q "^${KEY}=" "$GRADLE_PROPS" 2>/dev/null; then
    echo "$LINE" >> "$GRADLE_PROPS"
  fi
done

# ---------------------------------------------------------------------------
# Resume-awareness
# ---------------------------------------------------------------------------
echo "==============================================================="
echo "  RESUME CHECK — what's already done before this run continues"
echo "==============================================================="
cd "$REPO_DIR"
echo "--- Last 15 commits ---"
git log --oneline -15 2>/dev/null || echo "(no git history readable)"
echo "--- Working tree status ---"
git status --short 2>/dev/null || echo "(git status unavailable)"
echo "--- Current prd.json story states ---"
jq -r '.userStories[] | "\(.id): passes=\(.passes)"' "$PRD_FILE" 2>/dev/null || echo "(could not read prd.json)"
echo "==============================================================="
cd "$SCRIPT_DIR"

echo ""
echo "Starting Ralph — Tool: $TOOL — Model pool size: ${#MODEL_POOL[@]} — Max iterations: $MAX_ITERATIONS"
echo "Progress log: $PROGRESS_FILE"
cd "$SCRIPT_DIR"

POOL_SIZE=${#MODEL_POOL[@]}
FINAL_STATUS="unknown"
ITER_REACHED=0

for i in $(seq 1 $MAX_ITERATIONS); do
  ITER_REACHED=$i
  MODEL_IDX=$(( (i - 1) % POOL_SIZE ))
  MODEL="${MODEL_POOL[$MODEL_IDX]}"

  echo ""
  echo "==============================================================="
  echo "  Ralph Iteration $i of $MAX_ITERATIONS ($TOOL / $MODEL)"
  echo "==============================================================="

  PREFLIGHT_OK=true
  if [ ! -f "$REPO_DIR/gradlew" ]; then
    echo "PRE-FLIGHT FAIL: gradlew not found at $REPO_DIR/gradlew. Skipping LLM call this iteration."
    PREFLIGHT_OK=false
  fi
  if ! jq empty "$PRD_FILE" 2>/dev/null; then
    echo "PRE-FLIGHT FAIL: prd.json is not valid JSON. Skipping LLM call this iteration."
    PREFLIGHT_OK=false
  fi

  if [ "$PREFLIGHT_OK" == "false" ]; then
    echo "$(date): Iteration $i skipped — pre-flight check failed." >> "$PROGRESS_FILE"
    sleep 2
    continue
  fi

  COMBINED_PROMPT="$(cat "$FACTS_FILE"; echo; echo "---"; echo; cat "$PROMPT_FILE")"

  echo "" >> "$RUN_LOG"
  echo "===== Iteration $i of $MAX_ITERATIONS ($TOOL / $MODEL) =====" >> "$RUN_LOG"
  COMMIT_BEFORE=$(git -C "$REPO_DIR" rev-parse HEAD 2>/dev/null)

  OUTPUT=""
  if [[ "$TOOL" == "opencode" ]]; then
    OUTPUT=$(opencode run -m "$MODEL" "$COMBINED_PROMPT" 2>&1 | tee -a "$RUN_LOG" /dev/stderr) || true
  elif [[ "$TOOL" == "amp" ]]; then
    OUTPUT=$(echo "$COMBINED_PROMPT" | amp --dangerously-allow-all 2>&1 | tee -a "$RUN_LOG" /dev/stderr) || true
  else
    OUTPUT=$(echo "$COMBINED_PROMPT" | claude --dangerously-skip-permissions --print 2>&1 | tee -a "$RUN_LOG" /dev/stderr) || true
  fi

  COMMIT_AFTER=$(git -C "$REPO_DIR" rev-parse HEAD 2>/dev/null)
  TRIMMED_OUTPUT=$(echo "$OUTPUT" | tr -d '[:space:]')
  if [ "$COMMIT_BEFORE" == "$COMMIT_AFTER" ] && [ ${#TRIMMED_OUTPUT} -lt 20 ]; then
    echo "$(date): SILENT ITERATION $i ($MODEL) - no commit, no meaningful output (${#TRIMMED_OUTPUT} chars). Likely model-pool/API failure, not a code issue. Raw output saved in $RUN_LOG." >> "$PROGRESS_FILE"
  fi

  if echo "$OUTPUT" | grep -qi "quota\|rate.limit\|capacity is busy\|Cannot connect\|exhausted\|429"; then
    echo ""
    echo ">>> Model $MODEL appears exhausted/unreachable this iteration. Next iteration will rotate to a different model automatically."
  fi

  echo ""
  echo "--- Independently re-verifying every story marked passes:true ---"

  ANY_LIE=false
  NUM_STORIES=$(jq '.userStories | length' "$PRD_FILE")
  for idx in $(seq 0 $((NUM_STORIES - 1))); do
    IS_TRUE=$(jq -r ".userStories[$idx].passes" "$PRD_FILE")
    VCMD=$(jq -r ".userStories[$idx].verifyCommand // empty" "$PRD_FILE")
    SID=$(jq -r ".userStories[$idx].id" "$PRD_FILE")

    if [ "$IS_TRUE" == "true" ] && [ -n "$VCMD" ]; then
      echo "Re-checking $SID: $VCMD"
      cd "$REPO_DIR"
      if eval "$VCMD" > /tmp/ralph_verify_$idx.txt 2>&1; then
        echo "  -> REAL PASS confirmed for $SID (tests green)"
      else
        echo "  -> LIE DETECTED: $SID claimed passes:true but verifyCommand actually failed"
        tail -25 /tmp/ralph_verify_$idx.txt
        ANY_LIE=true
        python3 "$REJECT_HELPER" "$PRD_FILE" "$idx" "AUTO-REJECTED iteration $i: verifyCommand FAILED despite passes:true being set. See /tmp/ralph_verify_$idx.txt."
      fi
      cd "$SCRIPT_DIR"
    fi

    IS_TRUE_NOW=$(jq -r ".userStories[$idx].passes" "$PRD_FILE")
    BANNED_LIST=$(jq -r ".userStories[$idx].bannedPatterns[]? // empty" "$PRD_FILE")
    if [ "$IS_TRUE_NOW" == "true" ] && [ -n "$BANNED_LIST" ]; then
      cd "$REPO_DIR"
      while IFS= read -r PATTERN; do
        if [ -n "$PATTERN" ]; then
          HIT=$(grep -rl --include="*.kt" --include="*.gradle" --include="*.gradle.kts" -- "$PATTERN" "$REPO_DIR/mobile/app/src/main" 2>/dev/null || true)
          if [ -n "$HIT" ]; then
            echo "  -> LIE DETECTED: $SID claims passes:true but banned pattern '$PATTERN' found in production source:"
            echo "$HIT"
            ANY_LIE=true
            python3 "$REJECT_HELPER" "$PRD_FILE" "$idx" "AUTO-REJECTED iteration $i: banned pattern '$PATTERN' found in production source (app/src/main) despite passes:true and a green verifyCommand."
          fi
        fi
      done <<< "$BANNED_LIST"
      cd "$SCRIPT_DIR"
    fi
  done

  # --- Incident-file enforcement: story that disables a subsystem without a linked incident file is auto-rejected ---
  cd "$REPO_DIR"
  DIFF_PRODUCTION=$(git diff HEAD -- "mobile/app/src/main/" 2>/dev/null || true)
  if [ -n "$DIFF_PRODUCTION" ]; then
    # Patterns indicating a story disabled, no-opped, or caught-and-swallowed a subsystem
    DISABLE_PATTERN=$(echo "$DIFF_PRODUCTION" | grep -nE \
      '^\+.*catch\s*\(\s*\w+\s*:\s*\w+\s*\)\s*\{|^\+.*TODO\(.*disable|^\+.*TODO\(.*no.op|^\+.*//\s*disabled|^\+.*//\s*no-op|^\+.*return;\s*$' \
      2>/dev/null || true)
    if [ -n "$DISABLE_PATTERN" ]; then
      # Check if ANY incident file exists with a non-empty WHY section
      INCIDENT_FOUND=false
      if [ -d "$SCRIPT_DIR/incidents" ]; then
        for IF in "$SCRIPT_DIR"/incidents/*.md; do
          [ -f "$IF" ] || continue
          [ "$(basename "$IF")" = "TEMPLATE.md" ] && continue
          [ "$(basename "$IF")" = "INDEX.md" ] && continue
          WHY_SECTION=$(sed -n '/^## WHY/,/^## /{/^## WHY/d;/^## /d;p}' "$IF" 2>/dev/null | \
            sed '/^[[:space:]]*$/d;/^[[:space:]]*<!--/d' | tr -d '[:space:]')
          if [ -n "$WHY_SECTION" ]; then
            INCIDENT_FOUND=true
            break
          fi
        done
      fi
      if [ "$INCIDENT_FOUND" = "false" ]; then
        ANY_LIE=true
        echo "  -> LIE DETECTED: story claims passes:true but git diff disables/no-ops a subsystem without a linked incident file"
        echo "     Disable patterns found in diff:"
        echo "$DISABLE_PATTERN" | head -5
        for idx in $(seq 0 $((NUM_STORIES - 1))); do
          IS_STORY_TRUE=$(jq -r ".userStories[$idx].passes" "$PRD_FILE")
          if [ "$IS_STORY_TRUE" = "true" ]; then
            python3 "$REJECT_HELPER" "$PRD_FILE" "$idx" "AUTO-REJECTED iteration $i: disable/no-op pattern in production diff without a linked incident file (.ralph/incidents/<id>.md with non-empty WHY section)."
          fi
        done
      fi
    fi
  fi
  cd "$SCRIPT_DIR"

  # --- Real-backend evidence enforcement (AC6 of LOCAL-MODEL-BACKEND-GROUND-TRUTH-AND-BUILD):
  # any story that claims a real model backend exists (id contains MODEL-BACKEND or title
  # mentions a real local GGUF backend) must be backed by .ralph/model_backend_proof.md
  # with a non-empty WHAT + VERIFIED description of the actual live inference that ran.
  # This catches the recurring false-real-status class (e.g. a story claiming passes:true
  # while ModelManager still silently falls back to AdapterModelBackend / heuristic stubs).
  # Same enforcement pattern as the incident-ledger above.
  cd "$REPO_DIR"
  REAL_BACKEND_LIE=false
  for idx in $(seq 0 $((NUM_STORIES - 1))); do
    IS_TRUE=$(jq -r ".userStories[$idx].passes" "$PRD_FILE")
    [ "$IS_TRUE" = "true" ] || continue
    SID=$(jq -r ".userStories[$idx].id" "$PRD_FILE")
    TITLE=$(jq -r ".userStories[$idx].title" "$PRD_FILE")
    if echo "$SID" | grep -qi "MODEL-BACKEND" || echo "$TITLE" | grep -qi "real local GGUF backend"; then
      PROOF_FILE="$SCRIPT_DIR/model_backend_proof.md"
      if [ ! -f "$PROOF_FILE" ]; then
        REAL_BACKEND_LIE=true
        echo "  -> LIE DETECTED: $SID claims passes:true but no model-backend evidence exists"
        echo "     Missing $PROOF_FILE (non-empty WHAT/VERIFIED sections proving a real live inference)"
        python3 "$REJECT_HELPER" "$PRD_FILE" "$idx" "AUTO-REJECTED iteration $i: real-backend story claimed passes:true without .ralph/model_backend_proof.md (the recurring AdapterModelBackend fallback class)."
      else
        WHAT_SECTION=$(sed -n '/^## WHAT/,/^## /{/^## WHAT/d;/^## /d;p}' "$PROOF_FILE" 2>/dev/null | \
          sed '/^[[:space:]]*$/d;/^[[:space:]]*<!--/d' | tr -d '[:space:]')
        VERIFIED_SECTION=$(sed -n '/^## VERIFIED/,/^## /{/^## VERIFIED/d;/^## /d;p}' "$PROOF_FILE" 2>/dev/null | \
          sed '/^[[:space:]]*$/d;/^[[:space:]]*<!--/d' | tr -d '[:space:]')
        if [ -z "$WHAT_SECTION" ] || [ -z "$VERIFIED_SECTION" ]; then
          REAL_BACKEND_LIE=true
          echo "  -> LIE DETECTED: $SID claims passes:true but $PROOF_FILE lacks a non-empty WHAT and/or VERIFIED section"
          python3 "$REJECT_HELPER" "$PRD_FILE" "$idx" "AUTO-REJECTED iteration $i: model_backend_proof.md lacks non-empty WHAT/VERIFIED evidence of a real live inference."
        fi
      fi
    fi
  done
  [ "$REAL_BACKEND_LIE" = "true" ] && ANY_LIE=true
  cd "$SCRIPT_DIR"

  ALL_DONE=$(jq '[.userStories[] | select(.passes == false)] | length == 0' "$PRD_FILE")
  if [ "$ALL_DONE" == "true" ]; then
    if [ "$ANY_LIE" == "false" ]; then
      FINAL_STATUS="complete"
      echo "" | tee -a "$PROGRESS_FILE"
      echo "## Ralph run complete — $(date)" >> "$PROGRESS_FILE"
      echo "All stories independently re-verified for real after $i iteration(s)." >> "$PROGRESS_FILE"
      echo "---" >> "$PROGRESS_FILE"
      break
    fi
  fi

  echo "Iteration $i complete (model used: $MODEL). Continuing..."
        print_progress_bar
                sleep 2
done

if [ "$FINAL_STATUS" != "complete" ]; then
  FINAL_STATUS="max_iterations"
  echo "" | tee -a "$PROGRESS_FILE"
  echo "## Ralph run ended — $(date)" >> "$PROGRESS_FILE"
  echo "Reached max iterations ($MAX_ITERATIONS) without independently-verified completion." >> "$PROGRESS_FILE"
  echo "---" >> "$PROGRESS_FILE"
fi

mkdir -p "$DOWNLOAD_DIR" 2>/dev/null || true
REPORT_NAME="ralph_report_$(date +%Y%m%d_%H%M%S).md"
REPORT_PATH="$DOWNLOAD_DIR/$REPORT_NAME"

{
  echo "# Ralph Run Report"
  echo ""
  echo "- Date: $(date)"
  echo "- Tool: $TOOL"
  echo "- Iterations run: $ITER_REACHED of $MAX_ITERATIONS"
  echo "- Final status: $FINAL_STATUS"
  echo ""
  echo "## Story states at end of run"
  cd "$REPO_DIR"
  jq -r '.userStories[] | "- \(.id): passes=\(.passes) | notes: \(.notes // "")"' "$PRD_FILE" 2>/dev/null
  echo ""
  echo "## Commits made this run (last 15)"
  git log --oneline -15 2>/dev/null
  echo ""
  echo "## Full progress log location"
  echo "$PROGRESS_FILE"
} > "$REPORT_PATH"

echo ""
echo "==============================================================="
if [ "$FINAL_STATUS" == "complete" ]; then
  echo "Ralph completed all tasks — every passes:true story independently re-verified for real."
else
  echo "Ralph reached max iterations ($MAX_ITERATIONS) without independently-verified completion."
fi
echo "Full progress log: $PROGRESS_FILE"
echo "Report exported to: $REPORT_PATH"
echo "==============================================================="

[ "$FINAL_STATUS" == "complete" ] && exit 0 || exit 1
