#!/usr/bin/env bash
# ci_verify.sh -- decide pass/fail for unit-test filters using the GitHub
# Actions run for the current HEAD commit of this repo.
#
# Usage:
#   ci_verify.sh [--dry-run] <filter> [<filter> ...]
#   ci_verify.sh [--dry-run] --build-status
#
# A filter uses the same package/class glob format as Gradle's --tests
# argument (e.g. 'com.jarvis.app.cognitive.*'). Each filter is matched
# against every JUnit test's class name and fully-qualified method name.
#
# Exit status (filter mode):
#   0  every test matching the filter(s) passed AND at least one matched
#   1  no matching test, or a matching test failed/cancelled/timed out
#   2  usage error
#
# Exit status (--build-status mode):
#   0  the workflow run for HEAD concluded success
#   1  any other conclusion (incl. cancelled/time out), workflow rejected
#      with zero jobs, or the run never completed
#   2  usage error
#
# The GitHub token is read ONLY from the GITHUB_TOKEN environment variable
# and is never written to a file.
set -euo pipefail

API_BASE="https://api.github.com"
WORKFLOW_FILENAME="build.yml"
ARTIFACT_NAME="junit-results"
TIMEOUT_SECONDS="${CIV_TIMEOUT_SECONDS:-1500}"
POLL_SECONDS="${CIV_POLL_SECONDS:-15}"

fail() {
  echo "ci_verify.sh: $*" >&2
  exit 1
}

# ---------------------------------------------------------------------------
# CLI parsing
# ---------------------------------------------------------------------------
DRY_RUN=0
BUILD_STATUS=0
FILTERS=()
while [ "$#" -gt 0 ]; do
  case "$1" in
    --dry-run) DRY_RUN=1 ;;
    --build-status) BUILD_STATUS=1 ;;
    -h|--help)
      cat <<'HELP'
ci_verify.sh -- decide CI pass/fail for the GitHub Actions run of HEAD

Usage:
  ci_verify.sh [--dry-run] <filter> [<filter> ...]
  ci_verify.sh [--dry-run] --build-status

  --dry-run       print what would be done (HEAD, repo, mode, whether a
                  token is set) without any network call, then exit 0.
  --build-status  instead of judging unit-test results, exit 0 only if the
                  workflow run for HEAD concluded success; otherwise print
                  the run URL, the conclusion, every job conclusion, the
                  failed step names, and the last 80 lines of the failed
                  job's log, then exit 1.

Filter mode:
A filter is a package/class glob like Gradle's --tests argument, e.g.
'com.jarvis.app.cognitive.*'. The script looks up the GitHub Actions run for
the current HEAD commit, pushes main if no run exists yet, waits (up to 25
minutes) for the run to finish, downloads its 'junit-results' artifact, and
returns exit 0 only when every test matching the filter(s) passed and at
least one matching test exists. GITHUB_TOKEN must be set (never stored).
HELP
      exit 0 ;;
    -*) echo "ci_verify.sh: unknown option: $1" >&2; exit 2 ;;
    *) FILTERS+=("$1") ;;
  esac
  shift
done
if [ "${#FILTERS[@]}" -eq 0 ] && [ "$BUILD_STATUS" -eq 0 ]; then
  echo "ci_verify.sh: at least one test filter is required (or use --build-status)" >&2
  echo "Usage: ci_verify.sh [--dry-run] <filter> [<filter> ...]" >&2
  echo "       ci_verify.sh [--dry-run] --build-status" >&2
  exit 2
fi

# ---------------------------------------------------------------------------
# Local facts (no network)
# ---------------------------------------------------------------------------
REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
HEAD_SHA="$(git rev-parse HEAD)"
ORIGIN="$(git config --get remote.origin.url || true)"
OWNER_REPO=""
if [ -n "$ORIGIN" ]; then
  OWNER_REPO="$(python3 - "$ORIGIN" <<'PY'
import re, sys
url = sys.argv[1]
m = re.search(r'(?:github\.com[:/]|git@github\.com[:/])([^/]+)/([^/.]+?)(?:\.git)?$', url.strip())
print(m.group(1) + "/" + m.group(2) if m else "")
PY
)"
fi
if [ -z "$OWNER_REPO" ]; then
  OWNER_REPO="${GITHUB_REPOSITORY:-}"
fi

if [ "$DRY_RUN" -eq 1 ]; then
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    TOKEN_LINE='  GITHUB_TOKEN: set'
  else
    TOKEN_LINE='  GITHUB_TOKEN: NOT set'
  fi
  if [ "$BUILD_STATUS" -eq 1 ]; then
    printf 'ci_verify.sh dry-run: would check the build status of the CI run for HEAD\n'
    printf '  HEAD       : %s\n' "$HEAD_SHA"
    printf '  repository : %s\n' "${OWNER_REPO:-unknown}"
    printf '  mode       : --build-status\n'
    printf '%s\n' "$TOKEN_LINE"
    printf '  would      : look up the "%s" run for HEAD through %s; if none exists, push main; wait up to %ss for completion; exit 0 only if the run conclusion is success, otherwise report the run URL, jobs, failed steps and the failed job log tail, then exit 1.\n' \
      "$WORKFLOW_FILENAME" "$API_BASE" "$TIMEOUT_SECONDS"
    exit 0
  fi
  printf 'ci_verify.sh dry-run: would verify the following filter(s) against the CI run for HEAD\n'
  printf '  HEAD       : %s\n' "$HEAD_SHA"
  printf '  repository : %s\n' "${OWNER_REPO:-unknown}"
  printf '  filter(s)  : %s\n' "${FILTERS[*]}"
  printf '%s\n' "$TOKEN_LINE"
  printf '  would      : look up the "%s" run for HEAD through %s; if none exists, push main; wait up to %ss for completion; download the "%s" artifact; compare every JUnit test against the filter(s); exit 0 only if at least one test matched and none failed.\n' \
    "$WORKFLOW_FILENAME" "$API_BASE" "$TIMEOUT_SECONDS" "$ARTIFACT_NAME"
  exit 0
fi

# ---------------------------------------------------------------------------
# Prerequisites (plain fail-fast messages)
# ---------------------------------------------------------------------------
[ -n "${GITHUB_TOKEN:-}" ] || fail "GITHUB_TOKEN is not set: refusing to query the GitHub API without it"
[ -n "$OWNER_REPO" ] || fail "could not determine owner/repo (no git remote and no GITHUB_REPOSITORY)"
command -v curl >/dev/null 2>&1 || fail "curl is required but not installed"
command -v jq >/dev/null 2>&1 || fail "jq is required but not installed"
command -v python3 >/dev/null 2>&1 || fail "python3 is required but not installed"
command -v unzip >/dev/null 2>&1 || fail "unzip is required but not installed"

AUTH=(-H "Authorization: Bearer ${GITHUB_TOKEN}" -H "Accept: application/vnd.github+json")

# GET a URL with retries on transient TLS/network errors. A bare curl in a
# command substitution under set -e aborts the whole run on the first EOF.
api_get() {
  local url="$1" tries=3
  while [ "$tries" -gt 0 ]; do
    if curl -fsS "${AUTH[@]}" "$url"; then return 0; fi
    tries=$((tries - 1))
    [ "$tries" -gt 0 ] && sleep 3
  done
  return 1
}

get_runs() {
  api_get "${API_BASE}/repos/${OWNER_REPO}/actions/runs?head_sha=${HEAD_SHA}&per_page=30" || {
    echo "ci_verify.sh: GitHub API request failed (check GITHUB_TOKEN/network)" >&2
    return 1
  }
}

pick_run_id() {
  # newest run of the build.yml workflow for HEAD; empty when none exists.
  # The piped JSON is carried via the environment: a `python3 - <<'PY'`
  # heredoc would consume the pipeline's stdin, so a plain `python3 -` never
  # sees the JSON (json.load(sys.stdin) then reads the heredoc's own source).
  RUN_JSON_PAYLOAD="$(cat)"
  export RUN_JSON_PAYLOAD
  python3 - <<'PY'
import json, os
data = json.loads(os.environ["RUN_JSON_PAYLOAD"])
cand = []
for r in data.get("workflow_runs", []):
    if r.get("head_sha") == os.environ.get("HEAD_SHA") and r.get("path", "").endswith("build.yml"):
        cand.append(r)
if not cand:
    print("")
else:
    cand.sort(key=lambda r: r.get("created_at", ""))
    print(cand[-1]["id"])
PY
}

# shellcheck disable=SC2034
export HEAD_SHA

# ---------------------------------------------------------------------------
# Find (or trigger) the run for HEAD
# ---------------------------------------------------------------------------
RUN_ID="$(get_runs | pick_run_id)"

if [ -z "$RUN_ID" ]; then
  echo "ci_verify.sh: no run for ${HEAD_SHA}; pushing main so CI builds this commit"
  git push origin HEAD:main || fail "could not push main (check auth/network)"
  RUN_ID=""
  deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
  while [ -z "$RUN_ID" ]; do
    RUN_ID="$(get_runs | pick_run_id)"
    if [ -n "$RUN_ID" ]; then
      echo "ci_verify.sh: run ${RUN_ID} appeared for ${HEAD_SHA}"
      break
    fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
      fail "timed out after ${TIMEOUT_SECONDS}s waiting for a run to appear after push"
    fi
    echo "ci_verify.sh: waiting for a run to appear for ${HEAD_SHA} ..."
    sleep "$POLL_SECONDS"
  done
fi

# ---------------------------------------------------------------------------
# Wait for completion (reused immediately when already finished)
# ---------------------------------------------------------------------------
start=$SECONDS
while :; do
  RUN_JSON="$(api_get "${API_BASE}/repos/${OWNER_REPO}/actions/runs/${RUN_ID}")"
  STATUS="$(printf '%s' "$RUN_JSON" | jq -r '.status')"
  if [ "$STATUS" = "completed" ]; then
    CONCL="$(printf '%s' "$RUN_JSON" | jq -r '.conclusion')"
    echo "ci_verify.sh: run ${RUN_ID} finished with conclusion: ${CONCL}"
    case "$CONCL" in
      cancelled)
        if [ "$BUILD_STATUS" -eq 1 ]; then
          break # --build-status reports the conclusion itself
        fi
        fail "the CI run ${RUN_ID} was cancelled" ;;
      success|failure|neutral|aborted|timed_out|action_required|stale|skipped)
        # continue; the verdict is decided below
        ;;
      *) fail "unexpected run conclusion '${CONCL}'" ;;
    esac
    break
  fi
  if [ $((SECONDS - start)) -ge "$TIMEOUT_SECONDS" ]; then
    fail "timed out after ${TIMEOUT_SECONDS}s waiting for run ${RUN_ID} (status: ${STATUS})"
  fi
  echo "ci_verify.sh: waiting for run ${RUN_ID} ... status=${STATUS}, elapsed=$((SECONDS - start))s"
  sleep "$POLL_SECONDS"
done

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# ---------------------------------------------------------------------------
# --build-status mode: the verdict is the run conclusion itself
# ---------------------------------------------------------------------------
if [ "$BUILD_STATUS" -eq 1 ]; then
  if [ "$CONCL" = "success" ]; then
    echo "ci_verify.sh: build status for ${HEAD_SHA} is success (run ${RUN_ID})"
    exit 0
  fi

  RUN_URL="$(printf '%s' "$RUN_JSON" | jq -r '.html_url')"
  echo "ci_verify.sh: build status for ${HEAD_SHA} is NOT success: conclusion=${CONCL}"
  echo "  run URL    : ${RUN_URL}"

  JOBS_JSON="$(curl -fsS "${AUTH[@]}" "${API_BASE}/repos/${OWNER_REPO}/actions/runs/${RUN_ID}/jobs?per_page=100")" || {
    echo "ci_verify.sh: could not fetch the run's jobs; see ${RUN_URL} for the run detail" >&2
    exit 1
  }
  JOB_COUNT="$(printf '%s' "$JOBS_JSON" | jq '[.jobs[]] | length')"
  if [ "$JOB_COUNT" -eq 0 ]; then
    echo "ci_verify.sh: GitHub started NO jobs for run ${RUN_ID}: the workflow file itself was rejected, so GitHub never launched the build. See ${RUN_URL} for the rejection reason." >&2
    exit 1
  fi

  echo "  conclusion : ${CONCL}"
  printf '%s' "$JOBS_JSON" | jq -r '.jobs[] | "  job        : \(.name) -> conclusion \(.conclusion // "n/a")"' || true

  # Detail every non-successful job: which steps failed, plus the log tail.
  FAILED_JIDS="$(printf '%s' "$JOBS_JSON" | jq -r '.jobs[] | select((.conclusion != "success") and (.conclusion != "neutral") and (.conclusion != "skipped")) | .id')"
  for jid in $FAILED_JIDS; do
    jname="$(printf '%s' "$JOBS_JSON" | jq -r --arg id "$jid" '.jobs[] | select(.id == ($id | tonumber)) | .name')"
    printf '%s' "$JOBS_JSON" | jq -r --arg jn "$jname" '.jobs[] | select(.name == $jn) | .steps[]? | select((.conclusion == "failure") or (.conclusion == "cancelled")) | "  failed step: \($jn) / \(.name)"' || true
    echo "  last 80 lines of the log for job '${jname}' (id ${jid}):"
    curl -fsSL "${AUTH[@]}" "${API_BASE}/repos/${OWNER_REPO}/actions/jobs/${jid}/logs" -o "$TMP/job-${jid}.log" || {
      echo "    (could not fetch the log for job ${jid})" >&2
      continue
    }
    tail -n 80 "$TMP/job-${jid}.log" || true
  done

  exit 1
fi

# ---------------------------------------------------------------------------
# Download the JUnit results artifact
# ---------------------------------------------------------------------------
ART_JSON="$(api_get "${API_BASE}/repos/${OWNER_REPO}/actions/runs/${RUN_ID}/artifacts?per_page=100")"
DL_URL="$(printf '%s' "$ART_JSON" | jq -r --arg n "$ARTIFACT_NAME" '.artifacts[] | select(.name == $n) | .archive_download_url' | head -n 1)"
[ -n "$DL_URL" ] || fail "no '${ARTIFACT_NAME}' artifact on run ${RUN_ID}"
echo "ci_verify.sh: downloading '${ARTIFACT_NAME}' artifact from run ${RUN_ID}"
curl -fsSL "${AUTH[@]}" "$DL_URL" -o "$TMP/junit.zip" || fail "failed to download the junit-results artifact"
unzip -q "$TMP/junit.zip" -d "$TMP/xml" || fail "failed to unzip the junit-results archive"

# ---------------------------------------------------------------------------
# Judge every test matching the filter(s)
# ---------------------------------------------------------------------------
python3 - "$TMP/xml" "${FILTERS[@]}" <<'PY'
import fnmatch, glob, os, sys, xml.etree.ElementTree as ET

xdir, *filters = sys.argv[1:]
matched, failed = 0, []
for path in glob.glob(os.path.join(xdir, "**", "TEST-*.xml"), recursive=True):
    try:
        root = ET.parse(path).getroot()
    except Exception as e:
        print(f"ci_verify.sh: warning: cannot parse {path}: {e}")
        continue
    for tc in root.iter("testcase"):
        cn = (tc.get("classname") or "").strip()
        nm = (tc.get("name") or "").strip()
        fq = f"{cn}.{nm}" if cn else nm
        if not any(fnmatch.fnmatch(cn, p) or fnmatch.fnmatch(fq, p) for p in filters):
            continue
        matched += 1
        bad = tc.find("failure")
        if bad is None:
            bad = tc.find("error")
        if bad is not None:
            msg = (bad.get("message") or "").strip() or (bad.text or "").strip()
            failed.append((fq, msg[:2000]))

if matched == 0:
    print(f"ci_verify.sh: no tests matched the filter(s): {' '.join(filters)}")
    sys.exit(1)
if failed:
    print(f"ci_verify.sh: {len(failed)} of {matched} matched tests FAILED:")
    for fq, msg in failed:
        print(f"  FAILED  {fq}")
        first = msg.splitlines()[0] if msg else "(no failure message)"
        print(f"          {first}")
    sys.exit(1)
print(f"ci_verify.sh: {matched} matched test(s), all passed (filters: {' '.join(filters)})")
sys.exit(0)
PY