#!/usr/bin/env bash
# vision_audit.sh -- run the VISION.md Section 13 audit of what a story built,
# and print the report.
#
# Usage:
#   vision_audit.sh                     # the diff the newest story produced
#   vision_audit.sh --all               # every production Kotlin file, cold
#   vision_audit.sh --base <ref> --head <ref>
#   vision_audit.sh --paths <file>...   # whole files instead of a diff
#   vision_audit.sh --self-audit        # audit this tool's own source
#   vision_audit.sh --make-branch audit/<name> [--run-ci audit/<name>]
#   vision_audit.sh --help
#
# WHAT THE DEFAULT IS, AND WHY IT IS NOT A HINT
# --------------------------------------------
# The audit is of the diff a story produced, so the default range is the newest
# RUN of consecutive commits that changed production Kotlin under
# mobile/app/src/main/java: the feature commit plus the fixes that follow it,
# up to the first parent that did not touch it. A docs-only commit does not
# move that range, and neither does a fix inside it get dropped, because a
# fix is part of the story's real diff. The range is derived from `git log` at
# run time; nothing in this repo tells it which commit or which findings to
# expect, and it never reads VISION.md Section 12, where the known deviations
# are disclosed by name (audit.py extracts Sections 4 and 13 only, and
# audit_check.sh proves it).
#
# Pass --all to sweep the whole production tree instead, which is the AC1 "run
# cold against the current repo" shape and is much noisier: every closed type
# and every constant in the module is reported, because on a whole-repo pass
# none of them was "added by a story".
#
# OUTPUT
# ------
# stdout: the full report, and nothing else, so it can be piped or grepped as
#         the product rather than as a log.
# stderr: the one-line-per-finding index audit.py prints, so a human watching
#         a terminal still sees the summary.
#
# WHAT IT IS NOT
# --------------
# It reports; it never enforces. It does not edit source, does not touch
# prd.json, and has no path that merges a branch or closes a story. The patch
# it can produce is a diff on its own `audit/**` branch, and --run-ci pushes
# that branch and reads CI's verdict. Merging remains a human action.
#
# COST
# ----
# Five of the six checks are parse-level and spend nothing. Only the
# ownership-naming check may call a model, at most once per run, and only when
# AUDIT_MODEL_CMD names a command that reads a prompt on stdin. With no such
# command the report says the names are UNJUDGED rather than guessing.
#
# On the executable bit: this repository is checked out on a filesystem that
# does not keep it (chmod +x is silently dropped and core.fileMode is false), so
# every script here is invoked as `bash .ralph/<name>.sh` and no stored check may
# require `test -x`.
set -uo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd -- "$HERE/.." && pwd)"
cd "$REPO" || exit 2

SOURCE_PREFIX="mobile/app/src/main/java"

if [ ! -f "$HERE/audit.py" ]; then
  echo "vision_audit.sh: $HERE/audit.py is missing; there is nothing to run." >&2
  exit 2
fi

# No arguments at all: choose the range. With any argument, pass them through
# untouched so audit.py's own flags (and --help) are reachable.
if [ "$#" -eq 0 ]; then
  newest="$(git log -n 1 --format=%H -- "$SOURCE_PREFIX" 2>/dev/null || true)"
  if [ -z "$newest" ]; then
    echo "vision_audit.sh: no commit under $SOURCE_PREFIX yet; there is no story" \
         "diff to audit. Pass --all to sweep the tree instead." >&2
    exit 2
  fi
  # Walk the run back while each parent also touched production Kotlin.
  first="$newest"
  while :; do
    parent="$(git rev-parse --verify --quiet "${first}^" 2>/dev/null || true)"
    [ -n "$parent" ] || break
    [ "$(git log -n 1 --format=%H "$parent" -- "$SOURCE_PREFIX" 2>/dev/null || true)" \
      = "$parent" ] || break
    first="$parent"
  done
  base="$(git rev-parse --verify --quiet "${first}^" 2>/dev/null || true)"
  if [ -z "$base" ]; then
    # The very first commit in the repo: diff against the empty tree, which is
    # what that commit's parent would have been.
    base="$(git hash-object -t tree /dev/null)"
  fi
  echo "vision_audit.sh: auditing the newest production-Kotlin diff ${base:0:10}..${newest:0:10} (${first:0:10} is where that run of story commits starts)" >&2
  set -- --base "$base" --head "$newest"
elif [ "$1" = "--all" ]; then
  # Every tracked production Kotlin file. `--` pathspec-safe, and the empty case
  # is reported by audit.py rather than turned into an empty audit here.
  shift
  echo "vision_audit.sh: auditing every tracked file under $SOURCE_PREFIX" >&2
  set -- --paths $(git ls-files -- "$SOURCE_PREFIX") "$@"
fi

summary="$(mktemp)"
trap 'rm -f "$summary"' EXIT

# audit.py writes the report, the patch when it has one, and a JSON sidecar into
# .ralph/audit/, and prints its own index. Its index goes to stderr; the report
# it names is the stdout of this script.
python3 "$HERE/audit.py" "$@" >"$summary"
status=$?
cat "$summary" >&2

report="$(sed -n 's/^  report   : //p' "$summary" | tail -1)"
if [ -z "$report" ] || [ ! -f "$report" ]; then
  echo "vision_audit.sh: audit.py produced no report (exit ${status}); its own" \
       "message above is the whole answer." >&2
  exit "${status:-2}"
fi

cat "$report"
exit "$status"