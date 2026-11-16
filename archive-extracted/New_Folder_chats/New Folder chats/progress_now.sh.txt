#!/bin/bash
# ---------------------------------------------------------------------------
# progress.sh — the honest progress readout.
#
# It answers one question: how far along is this stage, RIGHT NOW? It answers it
# by reading the only source of truth there is, the live .ralph/prd.json, and
# the live runner's own log. There is no second counter anywhere: a hand-kept
# tally is exactly the thing that drifts from reality and lies to whoever reads
# it, which is the whole reason this script exists.
#
# Deliberately pure: local file reads and printing. No network client, no model
# call, no gradle, no side effects. Safe to run at any moment, from anywhere.
#
# usage:
#   bash .ralph/progress.sh                 # the real .ralph/prd.json
#   bash .ralph/progress.sh <prd.json>      # any prd (fixture / alt stage)
#   bash .ralph/progress.sh --brief         # bar + one summary line only
#   bash .ralph/progress.sh --no-run        # story state only, no run line
#   bash .ralph/progress.sh --note "..."    # caller states the run's outcome
#
# Environment:
#   PROGRESS_BAR_WIDTH      cells in the bar            (default 20)
#   PROGRESS_STALE_SECONDS  a run marker older than this is called stale
#                                                            (default 3600)
# ---------------------------------------------------------------------------
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRD_FILE="$SCRIPT_DIR/prd.json"
ACTIVE_MARKER="$SCRIPT_DIR/.ralph_active"
BAR_WIDTH="${PROGRESS_BAR_WIDTH:-20}"
STALE_SECONDS="${PROGRESS_STALE_SECONDS:-3600}"

BRIEF=false
SHOW_RUN=true
RUN_NOTE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --brief) BRIEF=true; shift ;;
    --no-run) SHOW_RUN=false; shift ;;
    --note) RUN_NOTE="$2"; shift 2 ;;
    --prd) PRD_FILE="$2"; shift 2 ;;
    -*) echo "progress.sh: unknown option '$1'" >&2; exit 2 ;;
    *) PRD_FILE="$1"; shift ;;
  esac
done

if ! command -v jq >/dev/null 2>&1; then
  echo "progress.sh: jq is required to read $PRD_FILE and is not on PATH." >&2
  exit 1
fi
if [ ! -f "$PRD_FILE" ]; then
  echo "progress.sh: no prd.json at $PRD_FILE" >&2
  exit 1
fi
if ! jq empty "$PRD_FILE" 2>/dev/null; then
  echo "progress.sh: $PRD_FILE is not valid JSON — story state is UNKNOWN (not guessed)." >&2
  exit 1
fi

# --- the real numbers, read live from the real file --------------------------
TOTAL="$(jq '[.userStories[]?] | length' "$PRD_FILE")"
CLOSED="$(jq '[.userStories[]? | select(.passes == true)] | length' "$PRD_FILE")"
PROJECT="$(jq -r '.project // "unknown project"' "$PRD_FILE")"

if [ "$BAR_WIDTH" -lt 4 ]; then BAR_WIDTH=4; fi
if [ "$TOTAL" -gt 0 ]; then
  FILLED=$(( CLOSED * BAR_WIDTH / TOTAL ))
  # A finished stage must show a full bar even when rounding would leave a gap.
  if [ "$CLOSED" -eq "$TOTAL" ]; then FILLED="$BAR_WIDTH"; fi
else
  FILLED=0
fi
BAR=""
for ((i = 0; i < BAR_WIDTH; i++)); do
  if [ "$i" -lt "$FILLED" ]; then BAR="${BAR}#"; else BAR="${BAR}-"; fi
done

STATE="in progress"
[ "$CLOSED" -eq "$TOTAL" ] && STATE="complete"
[ "$TOTAL" -eq 0 ] && STATE="no stories"

# PIDs of live ralph.sh runners, read straight out of /proc. Pure read, no
# signals, so it can never hurt the runner it is describing. Used only when no
# marker file exists (a run that started before the marker was introduced).
live_runner_pids() {
  local p pid0 cmd
  for p in /proc/[0-9]*; do
    [ -r "$p/cmdline" ] || continue
    pid0="${p#/proc/}"
    [ "$pid0" = "$$" ] && continue
    cmd="$(tr '\0' '\n' < "$p/cmdline" 2>/dev/null | sed -n '2p')"
    case "$cmd" in
      *ralph.sh) printf '%s ' "$pid0" ;;
    esac
  done
}

echo "[${BAR}] ${CLOSED}/${TOTAL} stories closed — $PROJECT ($STATE)"

if [ "$BRIEF" = "true" ]; then
  exit 0
fi

# --- every story, straight from its own passes field --------------------------
echo ""
jq -r '.userStories[]? | "  [\(if .passes == true then "x" else " " end)] \(.id)  (priority \(.priority // "?"), passes=\(.passes))"' \
  "$PRD_FILE"

# --- the live run, if there is one -------------------------------------------
# The marker is created by ralph.sh at the start of a run and removed when the
# run's shell exits. Its age separates "running right now" from "the process was
# killed and left this behind".
if [ "$SHOW_RUN" = "true" ]; then
  echo ""
  NEWEST_LOG=""
  for f in "$SCRIPT_DIR"/ralph_run_*.log; do
    [ -f "$f" ] || continue
    if [ -z "$NEWEST_LOG" ] || [ "$f" -nt "$NEWEST_LOG" ]; then NEWEST_LOG="$f"; fi
  done

  ITER_LINE=""
  ITER=""
  ITER_MAX=""
  if [ -n "$NEWEST_LOG" ]; then
    ITER_LINE="$(grep -E '^===== Iteration [0-9]+ of [0-9]+' "$NEWEST_LOG" 2>/dev/null | tail -1)"
    if [ -n "$ITER_LINE" ]; then
      ITER="$(printf '%s\n' "$ITER_LINE" | sed -n 's/^===== Iteration \([0-9][0-9]*\) of \([0-9][0-9]*\).*/\1/p')"
      ITER_MAX="$(printf '%s\n' "$ITER_LINE" | sed -n 's/^===== Iteration \([0-9][0-9]*\) of \([0-9][0-9]*\).*/\2/p')"
    fi
  fi

  if [ -n "$RUN_NOTE" ]; then
    # The caller (ralph.sh) knows the run's own outcome; it says so instead of
    # guessing, and the liveness probe below is skipped because the shell that
    # would answer it is the one printing.
    echo "run: $RUN_NOTE"
  elif [ -f "$ACTIVE_MARKER" ]; then
    NOW="$(date +%s)"
    MARK_AGE=$(( NOW - $(date -r "$ACTIVE_MARKER" +%s 2>/dev/null || echo "$NOW") ))
    RUN_PID="$(head -1 "$ACTIVE_MARKER" 2>/dev/null | tr -cd '0-9')"
    # The pid is the truth, not the marker's age: a legitimate run can last
    # hours, and its marker is written once. Age only explains a dead pid.
    if [ -n "$RUN_PID" ] && kill -0 "$RUN_PID" 2>/dev/null; then
      if [ -n "$ITER" ]; then
        echo "run: IN PROGRESS — iteration ${ITER} of ${ITER_MAX} (runner pid ${RUN_PID}, up ${MARK_AGE}s)."
      else
        echo "run: IN PROGRESS — iteration not recorded yet (runner pid ${RUN_PID}, up ${MARK_AGE}s)."
      fi
    else
      echo "run: NOT running — marker for pid ${RUN_PID:-unknown} is ${MARK_AGE}s old and that process is gone (killed). Last recorded iteration: ${ITER:-none} of ${ITER_MAX:-?}."
    fi
  else
    LIVE_PIDS="$(live_runner_pids)"
    if [ -n "$LIVE_PIDS" ]; then
      if [ -n "$ITER" ]; then
        echo "run: IN PROGRESS — iteration ${ITER} of ${ITER_MAX} (live runner pid(s) $(echo "$LIVE_PIDS" | tr '\n' ' '); no marker file, so this run started before the marker existed)."
      else
        echo "run: IN PROGRESS — live runner pid(s) $(echo "$LIVE_PIDS" | tr '\n' ' '); no marker file, so this run started before the marker existed."
      fi
    elif [ -n "$ITER" ]; then
      echo "run: not in progress — the last run reached iteration ${ITER} of ${ITER_MAX}."
    elif [ -n "$NEWEST_LOG" ]; then
      echo "run: not in progress — $(basename "$NEWEST_LOG") holds no completed iteration."
    else
      echo "run: not in progress — no ralph_run_*.log exists yet."
    fi
  fi
fi
