#!/bin/bash
# Cloud Ralph v1.1. Runs the unchanged .ralph/ralph.sh on a GitHub runner, unattended.
# The runner is disposable: all state lives in git (main, audit/* red branches, ralph/wip).
export GIT_PAGER=cat
: "${STORIES:=LAUNCH-WAKE-ORDER}" "${RELAY:=0}" "${WINDOW:=17400}" "${CAP:=14400}" "${IDLE:=1800}" "${TICK:=30}"
: "${MINLEFT:=1800}" "${GRACE:=60}" "${GATECAP:=150}" "${GTICK:=3}" "${WAIT:=600}" "${MAXRELAY:=6}" "${HB:=1800}" "${PUMPSEC:=45}"
LOGDIR="$HOME/.local/share/opencode/log"
REPO="${GITHUB_REPOSITORY:-mhw77655-hue/Friday}"
OWNER="${REPO%%/*}"
START=$(date +%s); END=$((START + WINDOW))
L=/tmp/cloud_lines.txt; : > "$L"
IT=0; STALLS=0; NOCHG=0; BAD=""; WHY=""; DONE=""; PROGRESS=0; M=""

say() { echo "$*" | tee -a "$L"; }
redact() { sed -E "s/(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9]{16,}|AKIA[A-Z0-9]{12,})/[REDACTED]/g; /GITHUB_TOKEN=|Authorization:|Bearer /d" | head -c 60000; }

ensure_branch() {
  gh api "repos/$REPO/git/ref/heads/$1" > /dev/null 2>&1 && return 0
  SH=$(gh api "repos/$REPO/git/ref/heads/main" --jq .object.sha 2>/dev/null)
  gh api -X POST "repos/$REPO/git/refs" -f ref="refs/heads/$1" -f sha="$SH" > /dev/null 2>&1
}
put_file() { # $1 local file, $2 path on reports/cloud
  ensure_branch reports/cloud || return 1
  OLD=$(gh api "repos/$REPO/contents/$2?ref=reports/cloud" --jq .sha 2>/dev/null)
  if [ -n "$OLD" ]; then
    gh api -X PUT "repos/$REPO/contents/$2" -f message="cloud report $2" -f branch=reports/cloud -f sha="$OLD" -f content="$(base64 -w0 "$1")" > /dev/null 2>&1
  else
    gh api -X PUT "repos/$REPO/contents/$2" -f message="cloud report $2" -f branch=reports/cloud -f content="$(base64 -w0 "$1")" > /dev/null 2>&1
  fi
}
status_report() { # $1 title, $2 file name
  { echo "# cloud report: $1 ($(date -u +%FT%TZ)), relay $RELAY, stories: $STORIES"
    echo "## lanes: $MODELS"
    echo "## repo"; git rev-parse --short HEAD; git status -sb | head -30; git log --oneline -8; git diff --stat | tail -15
    echo "## queue"; bash .ralph/progress.sh 2>&1 | head -24
    echo "## opencode log errors (newest 15)"; ls -t "$LOGDIR"/*.log 2>/dev/null | head -4 | xargs -r grep -h "level=ERROR" 2>/dev/null | tail -15 | cut -c1-420
    echo "## night so far"; cat "$L" 2>/dev/null
    echo "## last ralph log (tail)"; tail -80 "$(ls -t .ralph/ralph_run_*.log 2>/dev/null | head -1)" 2>/dev/null
  } 2>&1 | redact > "/tmp/rep_$2"
  put_file "/tmp/rep_$2" "reports/$2" && echo "report pushed: reports/$2 on branch reports/cloud"
}

prep() {
  sudo mkdir -p /sdcard/Download && sudo chown -R "$(id -u):$(id -g)" /sdcard
  ln -sfn "$GITHUB_WORKSPACE" /sdcard/jarvis-repo
  cd /sdcard/jarvis-repo || exit 1
  git config user.name "ralph-cloud"; git config user.email "ralph-cloud@users.noreply.github.com"
  mkdir -p "$HOME/.config/opencode"
  echo '{"$schema":"https://opencode.ai/config.json","provider":{"opencode":{"options":{"timeout":900000,"chunkTimeout":300000}}}}' > "$HOME/.config/opencode/opencode.json"
  export OPENCODE_DISABLE_AUTOUPDATE=true OPENCODE_PERMISSION='{"external_directory":{"*":"allow"}}'
  timeout 240 npm i -g opencode-ai@1.19.5 > /tmp/npm.log 2>&1; NR=$?
  say "opencode $(opencode --version 2>&1 | head -1) (npm rc $NR)"
}

selfcheck() {
  OKS=1
  for t in jq python3 gh git opencode; do command -v "$t" > /dev/null || { say "CHECK FAIL: $t is missing"; OKS=0; }; done
  [ -f .ralph/ralph.sh ] && [ -f .ralph/prd.json ] || { say "CHECK FAIL: .ralph/ralph.sh or prd.json missing"; OKS=0; }
  git ls-remote --exit-code origin refs/heads/main > /dev/null 2>&1 || { say "CHECK FAIL: git cannot reach origin"; OKS=0; }
  if gh api -X POST "repos/$REPO/git/refs" -f ref=refs/heads/ralph/ping -f sha="$(git rev-parse origin/main)" > /dev/null 2>&1; then
    gh api -X DELETE "repos/$REPO/git/refs/heads/ralph/ping" > /dev/null 2>&1; say "check: this job can create and delete branches"
  else say "CHECK FAIL: this job cannot create branches (token permissions)"; OKS=0; fi
  [ "$OKS" = 1 ]
}

hydrate_base() {
  git fetch -q origin '+refs/heads/audit/*:refs/heads/audit/*' 2>/dev/null
  git checkout -q -B main origin/main
}
hydrate_work() {
  if git fetch -q origin ralph/wip 2>/dev/null; then
    if git merge-base --is-ancestor origin/main FETCH_HEAD; then
      git checkout -q -B main FETCH_HEAD && git reset -q --soft HEAD~1 && git reset -q && say "restored unfinished work from branch ralph/wip"
      return 0
    fi
    say "WARN: ralph/wip does not contain the current main; ignoring it"
  fi
  for B in $(jq -r --arg ids "$IDS" '.userStories[] | select(.passes != true) | select(.id as $i | ($ids | split(" ") | index($i)) != null) | .verifyCommand // empty' .ralph/prd.json | grep -o 'refs/heads/audit/[A-Za-z0-9_./-]*' | sort -u | sed 's#refs/heads/##'); do
    if git rev-parse -q --verify "refs/heads/$B" > /dev/null && ! git merge-base --is-ancestor "$B" HEAD; then
      git merge -q --no-ff --no-edit "$B" && say "merged red branch $B into main (the red tests now sit under the green work)"
    fi
  done
}

snapshot_wip() {
  TR=$(tree_hash)
  [ -n "$TR" ] || { say "WARN: could not snapshot the working tree"; return 1; }
  CM=$(git commit-tree "$TR" -p HEAD -m "ralph wip snapshot $(date -u +%FT%TZ)" 2>/dev/null)
  git push -q -f origin "$CM:refs/heads/ralph/wip" > /dev/null 2>&1 && echo "wip snapshot pushed to ralph/wip" || say "WARN: wip snapshot push failed"
}

tree_hash() { TI=$(mktemp -u); GIT_INDEX_FILE="$TI" git add -A > /dev/null 2>&1 && GIT_INDEX_FILE="$TI" git write-tree 2>/dev/null; rm -f "$TI"; }
snap() { echo "$(git rev-parse HEAD) $(jq "[.userStories[]|select(.passes==true)]|length" .ralph/prd.json) $(tree_hash)"; }
remaining() { R=0; for I in $IDS; do case " $DONE " in *" $I "*) ;; *) R=$((R+1));; esac; done; echo $R; }
verify_story() { # $1 id, $2 log, $3 timeout seconds
  CMD=$(jq -r --arg id "$1" '.userStories[] | select(.id==$id) | .verifyCommand' .ralph/prd.json)
  timeout "${3:-1500}" bash -c "$CMD" < /dev/null > "$2" 2>&1
}
check_done() {
  for I in $IDS; do
    case " $DONE " in *" $I "*) continue;; esac
    jq -e --arg id "$I" '.userStories[] | select(.id==$id) | .passes == true' .ralph/prd.json > /dev/null 2>&1 || continue
    if verify_story "$I" "/tmp/verify_$I.txt"; then DONE="$DONE $I"; say "verified: $I"; else say "claimed closed but NOT verified: $I"; fi
  done
}
baseline() {
  BASEOK=1
  for I in $(jq -r '.userStories[] | select(.passes==true) | .id' .ralph/prd.json); do
    if verify_story "$I" "/tmp/base_$I.txt" 900; then echo "baseline ok: $I"; else say "BASELINE FAIL: $I: $(tail -3 "/tmp/base_$I.txt" | tr "\n" " " | cut -c1-240)"; BASEOK=0; fi
  done
  [ "$BASEOK" = 1 ] && say "baseline: every closed story still verifies in the cloud"
  [ "$BASEOK" = 1 ]
}

active() {
  [ $(( $(date +%s) - T0 )) -lt "$IDLE" ] && return 0
  [ -n "$(find .ralph -maxdepth 1 -name "ralph_run_*.log" -newermt "@$T0" -newermt "$IDLE seconds ago" -print -quit 2>/dev/null)" ] && return 0
  [ -n "$(find . -path ./.git -prune -o -path ./.ralph -prune -o -type f -newermt "@$T0" -newermt "$IDLE seconds ago" -print -quit 2>/dev/null)" ] && return 0
  return 1
}
stop_run() { kill -TERM "$TP" 2>/dev/null; i=0; while kill -0 "$TP" 2>/dev/null && [ "$i" -lt "$GRACE" ]; do sleep 1; i=$((i+1)); done; kill -KILL -- "-$TP" 2>/dev/null; sleep 2; }
clear_lock() {
  [ -f .git/index.lock ] || return 0
  [ "$(stat -c %Y .git/index.lock)" -ge "$T0" ] || { say "WARN: index.lock predates this iteration; left in place"; return 0; }
  sleep 5; pgrep -x git > /dev/null || { rm -f .git/index.lock; say "removed an index.lock left by the killed run"; }
}
sweep() { pkill -KILL -f "[o]pencode" 2>/dev/null; sleep 1; }


nvidia_lanes() {
  [ -n "$NVIDIA_API_KEY" ] || { say "no NVIDIA_API_KEY secret: only the shared free lanes are available"; return; }
  NID=$(grep '^nvidia/' /tmp/models.txt | sed 's#^nvidia/##'); NCUSTOM=0
  if [ -z "$NID" ]; then
    NID=$(curl -sS -m 30 -H "Authorization: Bearer $NVIDIA_API_KEY" https://integrate.api.nvidia.com/v1/models 2>/dev/null | jq -r '.data[].id' 2>/dev/null); NCUSTOM=1
  fi
  [ -n "$NID" ] || { say "WARN: the NVIDIA key is set but no model list could be read"; return; }
  NP=""
  for p in qwen3-coder kimi-k2 deepseek-v3 gpt-oss-120b glm minimax; do
    x=$(echo "$NID" | grep -i -- "$p" | grep -viE "vl|vision|embed|reward|guard|safety|retriev|parse" | head -1)
    [ -n "$x" ] && NP="$NP $x"
  done
  NP=$(echo $NP | tr ' ' '\n' | awk '!s[$0]++' | head -3 | tr '\n' ' ')
  [ -n "$NP" ] || { say "WARN: no suitable NVIDIA coding model in the list"; return; }
  if [ "$NCUSTOM" = 1 ]; then
    MJ=$(for x in $NP; do jq -n --arg id "$x" '{($id): {name: $id, tool_call: true, limit: {context: 131072, output: 16384}}}'; done | jq -s 'add')
    jq --argjson m "$MJ" '.provider.nvidia = {npm: "@ai-sdk/openai-compatible", name: "NVIDIA NIM", options: {baseURL: "https://integrate.api.nvidia.com/v1", apiKey: "{env:NVIDIA_API_KEY}", timeout: 900000, chunkTimeout: 300000}, models: $m}' "$HOME/.config/opencode/opencode.json" > /tmp/oc.json && mv /tmp/oc.json "$HOME/.config/opencode/opencode.json"
  fi
  for x in $NP; do KEYED="$KEYED nvidia/$x"; done
  say "nvidia lanes:$KEYED ($([ "$NCUSTOM" = 1 ] && echo "custom provider" || echo "built-in"))"
}
discover_lanes() {
  timeout 90 opencode models < /dev/null > /tmp/models.txt 2>&1
  KEYED=""; FREE=""
  nvidia_lanes
  for m in opencode/deepseek-v4.1-flash-free opencode/big-pickle opencode/glm-5.3-flash-free opencode/flint-40b-free opencode/beam-free; do grep -qx "$m" /tmp/models.txt && FREE="$FREE $m"; done
  MODELS=$(echo $KEYED $FREE); [ -n "$MODELS" ] || MODELS="opencode/deepseek-v4.1-flash-free opencode/big-pickle"
}

# Lane pool: each lane must answer a one-word probe before the agent gets it. A lane that logs a stream error is cut at once.
gate() {
  GO=$(mktemp); MK=$(mktemp); GD=$(mktemp -d); S0=$(date +%s); ERR=""
  ( cd "$GD" && exec timeout -k 5 "$GATECAP" opencode run -m "$1" "Create a file named gate_tool.txt in the current directory containing exactly the word ok, then reply with the single word done." ) > "$GO" 2>&1 < /dev/null &
  GP=$!
  while kill -0 "$GP" 2>/dev/null; do
    sleep "$GTICK"
    ERR=$(find "$LOGDIR" -name "*.log" -newer "$MK" 2>/dev/null | xargs -r grep -h 'message="stream error"' 2>/dev/null | grep -m1 -o 'error.error=.*' | cut -c1-200)
    if [ -n "$ERR" ]; then kill -TERM "$GP" 2>/dev/null; sleep 1; kill -KILL "$GP" 2>/dev/null; break; fi
  done
  wait "$GP" 2>/dev/null; RC=$?
  SS=$(( $(date +%s) - S0 ))
  if [ -z "$ERR" ] && [ "$RC" = 0 ] && [ -f "$GD/gate_tool.txt" ] && [ "$(tr -d "[:space:]" < "$GD/gate_tool.txt")" = ok ] && ! grep -qiE "free usage|exceeded|429|quota" "$GO"; then say "lane ok: $1 (${SS}s, used a tool)"; rm -rf "$GO" "$MK" "$GD"; return 0; fi
  [ -n "$ERR" ] || ERR="$(tail -3 "$GO" | tr "\n" " " | cut -c1-200)"
  [ -f "$GD/gate_tool.txt" ] || ERR="no tool call happened. $ERR"
  say "lane failed: $1 rc=$RC after ${SS}s: $ERR"; rm -rf "$GO" "$MK" "$GD"; return 1
}
pick() { M=""; for X in $MODELS; do case " $BAD " in *" $X "*) continue;; esac; if gate "$X"; then M="$X"; return 0; fi; BAD="$BAD $X"; sweep; done; return 1; }
repick() {
  RP=0; MAXR=30; [ -z "$KEYED" ] && MAXR=3
  while [ "$RP" -lt "$MAXR" ]; do
    LEFT=$((END - $(date +%s))); [ "$LEFT" -lt "$MINLEFT" ] && return 1
    RP=$((RP+1)); say "no lane answered; waiting $((WAIT/60)) min, then retry $RP"; sleep "$WAIT"; sweep; BAD=""
    pick && return 0
  done
  return 1
}
demote() { R=""; for x in $MODELS; do [ "$x" = "$M" ] || R="$R $x"; done; MODELS="${R# } $M"; }

# CI pump: pushes made with the job token do not trigger CI, so start a build for any branch tip that has none.
pump() {
  SEEN=""
  while :; do
    for ref in main $(git ls-remote --heads origin 'audit/*' 2>/dev/null | awk '{print $2}' | sed 's#refs/heads/##'); do
      sha=$(git ls-remote origin "refs/heads/$ref" 2>/dev/null | cut -f1); [ -n "$sha" ] || continue
      case " $SEEN " in *" $sha "*) continue;; esac
      n=$(gh api "repos/$REPO/actions/workflows/build.yml/runs?head_sha=$sha&per_page=1" 2>/dev/null | jq -r .total_count 2>/dev/null)
      if [ "$n" = 0 ]; then gh workflow run build.yml --repo "$REPO" --ref "$ref" > /dev/null 2>&1 && echo "pump: started a build for $ref@${sha:0:7}" >> "$L"; SEEN="$SEEN $sha"; elif [ -n "$n" ]; then SEEN="$SEEN $sha"; fi
    done
    sleep "$PUMPSEC"
  done
}

wait_build() {
  git fetch -q origin main; SHA=$(git rev-parse origin/main); W0=$(date +%s)
  while [ $(( $(date +%s) - W0 )) -lt 1500 ]; do
    J=$(gh api "repos/$REPO/actions/workflows/build.yml/runs?head_sha=$SHA&per_page=20" 2>/dev/null)
    OKN=$(echo "$J" | jq '[.workflow_runs[]? | select(.conclusion=="success")] | length' 2>/dev/null)
    [ "${OKN:-0}" -gt 0 ] && return 0
    TOT=$(echo "$J" | jq -r '.total_count // 0' 2>/dev/null); PEND=$(echo "$J" | jq '[.workflow_runs[]? | select(.status!="completed")] | length' 2>/dev/null)
    if [ "${TOT:-0}" -gt 0 ] && [ "${PEND:-0}" = 0 ]; then say "the build of the final main@${SHA:0:7} did not succeed"; return 1; fi
    sleep "$PUMPSEC"
  done
  say "the build of the final main@${SHA:0:7} did not finish in time"; return 1
}
deliver() {
  git fetch -q origin main; SHA=$(git rev-parse origin/main)
  RID=$(gh api "repos/$REPO/actions/workflows/build.yml/runs?head_sha=$SHA&per_page=20" 2>/dev/null | jq -r '[.workflow_runs[]? | select(.conclusion=="success")][0].id // empty' 2>/dev/null)
  [ -n "$RID" ] || { say "deliver: no successful build run for main@${SHA:0:7}"; return 1; }
  rm -rf /tmp/apk; gh run download "$RID" --repo "$REPO" -n debug-apk -D /tmp/apk > /dev/null 2>&1 || { say "deliver: could not download the debug-apk artifact"; return 1; }
  APK=$(find /tmp/apk -name "*.apk" | head -1); [ -n "$APK" ] || { say "deliver: no apk in the artifact"; return 1; }
  TAG="friday-${SHA:0:7}"
  gh release create "$TAG" "$APK" --repo "$REPO" --target main --prerelease --title "Friday build ${SHA:0:7}" --notes "Built in the cloud. Stories verified:$DONE" > /dev/null 2>&1 || { say "deliver: could not create release $TAG; friday-latest was left untouched"; return 1; }
  if gh release view friday-latest --repo "$REPO" > /dev/null 2>&1; then
    gh release upload friday-latest "$APK" --repo "$REPO" --clobber > /dev/null 2>&1 && gh release edit friday-latest --repo "$REPO" --title "Friday latest: build ${SHA:0:7}" --notes "Same file as release $TAG. Stories verified:$DONE" > /dev/null 2>&1 || say "WARN: could not refresh friday-latest; the new build is in release $TAG"
  else
    gh release create friday-latest "$APK" --repo "$REPO" --target main --prerelease --title "Friday latest: build ${SHA:0:7}" --notes "Same file as release $TAG. Stories verified:$DONE" > /dev/null 2>&1 || say "WARN: could not create friday-latest; the new build is in release $TAG"
  fi
  say "release ready: https://github.com/$REPO/releases/download/friday-latest/$(basename "$APK") (also release $TAG)"
}
notify() {
  if gh issue create --repo "$REPO" --title "$1" --body "@$OWNER $2" > /dev/null 2>&1; then say "notified with an issue"
  else git fetch -q origin main 2>/dev/null; gh api -X POST "repos/$REPO/commits/$(git rev-parse origin/main)/comments" -f body="@$OWNER $1. $2" > /dev/null 2>&1 && say "notified with a commit comment" || say "WARN: could not notify (are issues disabled?)"; fi
}

finish() {
  sweep
  snapshot_wip
  say "RESULT: $WHY (relay $RELAY, iterations $IT, started $(date -d @"$START" +%H:%M), finished $(date +%H:%M), verified:$DONE)"
  REMAIN=$(remaining)
  if [ "$REMAIN" = 0 ] && [ -z "$BLOCKED" ]; then
    git fetch -q origin main
    if [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ] && git merge-base --is-ancestor origin/main HEAD; then git push -q origin HEAD:refs/heads/main > /dev/null 2>&1 || say "WARN: could not push the final main"; fi
    if wait_build && deliver; then notify "Friday build finished: $STORIES" "Verified:$DONE. Download: https://github.com/$REPO/releases/download/friday-latest/app-debug.apk"; else notify "Friday stories verified but delivery failed" "Verified:$DONE. See the reports/cloud branch."; fi
    git push -q origin :refs/heads/ralph/wip > /dev/null 2>&1
  elif [ "$WHY" = "window ended with stories still open" ] && [ "$PROGRESS" = 1 ] && [ "$RELAY" -lt "$MAXRELAY" ]; then
    say "relay: starting run $((RELAY+1)) from the saved work"
    gh workflow run ralph.yml --repo "$REPO" --ref main -f stories="$STORIES" -f relay="$((RELAY+1))" > /dev/null 2>&1 || { say "WARN: relay dispatch failed"; notify "Cloud builder stopped: relay failed" "$WHY. See reports/cloud."; }
  else
    HINT=""; [ -z "$KEYED" ] && HINT=" Add a repository secret named NVIDIA_API_KEY (a free key from build.nvidia.com), then run the ralph workflow again."
    notify "Cloud builder stopped: $WHY" "Stories still open: $STORIES. Work is saved on branch ralph/wip.$HINT See reports/cloud."
  fi
  kill "$PUMPPID" 2>/dev/null
  status_report "final: $WHY" "final_r${RELAY}_$(date +%m%d_%H%M).md"
  { echo "### Cloud Ralph: $WHY"; echo "iterations: $IT, verified:$DONE"; } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
}

# ---------------------------------------------------------------- main
prep
if [ "$STORIES" = ALL ]; then IDS=$(jq -r '[.userStories[] | select(.passes != true) | .id] | join(" ")' .ralph/prd.json); else IDS="$STORIES"; fi
say "cloud ralph v1.1 relay $RELAY: stories $IDS"
if ! selfcheck; then WHY="the cloud environment failed its self-check"; BLOCKED=1; say "RESULT: $WHY"; status_report "stopped: $WHY" "check_fail_$(date +%m%d_%H%M).md"; notify "Cloud builder stopped: self-check failed" "See reports/cloud on GitHub."; exit 1; fi
hydrate_base || { WHY="could not prepare the repository"; BLOCKED=1; say "RESULT: $WHY"; status_report "stopped: $WHY" "hydrate_fail_$(date +%m%d_%H%M).md"; exit 1; }
if [ "$RELAY" = 0 ] && ! baseline; then WHY="a closed story no longer verifies in the cloud"; BLOCKED=1; say "RESULT: $WHY"; status_report "stopped: $WHY" "baseline_fail_$(date +%m%d_%H%M).md"; notify "Cloud builder stopped: a closed story fails in the cloud" "See reports/cloud on GitHub."; exit 1; fi
hydrate_work
discover_lanes
say "lanes: $MODELS"
pump & PUMPPID=$!
status_report "start" "start_r${RELAY}_$(date +%m%d_%H%M).md"
T0=$(date +%s); check_done
if [ "$(remaining)" -eq 0 ]; then WHY="all queued stories verified"
elif pick || repick; then
  LASTHB=$(date +%s)
  while [ "$(remaining)" -gt 0 ]; do
    LEFT=$((END - $(date +%s)))
    if [ "$LEFT" -lt "$MINLEFT" ]; then WHY="window ended with stories still open"; break; fi
    IT=$((IT+1)); CP=$LEFT; [ "$CP" -gt "$CAP" ] && CP=$CAP
    NV0=$(echo $DONE | wc -w); B=$(snap); T0=$(date +%s); STALLED=0
    timeout -k "$GRACE" "$CP" bash .ralph/ralph.sh --tool opencode --model "$M" --skip-preflight 1 < /dev/null > /dev/null 2>&1 &
    TP=$!
    while kill -0 "$TP" 2>/dev/null; do
      sleep "$TICK"
      if ! active; then STALLED=1; stop_run; break; fi
      if [ $(( $(date +%s) - LASTHB )) -ge "$HB" ]; then LASTHB=$(date +%s); snapshot_wip; status_report "heartbeat" "live.md"; fi
    done
    wait "$TP"; C=$?; DUR=$(( ($(date +%s) - T0) / 60 ))
    KILLED=0; if [ "$STALLED" = 1 ] || [ "$C" = 124 ] || [ "$C" = 137 ]; then KILLED=1; kill -KILL -- "-$TP" 2>/dev/null; sleep 1; clear_lock; fi
    sweep
    A=$(snap); check_done; NV=$(echo $DONE | wc -w)
    snapshot_wip
    status_report "after iteration $IT" "iter_r${RELAY}_${IT}_$(date +%m%d_%H%M).md"
    if [ "$KILLED" = 1 ]; then
      STALLS=$((STALLS+1))
      if [ "$STALLED" = 1 ]; then RS="no log or file activity for $((IDLE/60)) min"; else RS="hit the $((CP/60)) min cap"; fi
      say "iteration $IT: killed after $DUR min on $M ($RS), $STALLS in a row"
      [ "$A" != "$B" ] && PROGRESS=1
      [ "$(remaining)" -eq 0 ] && break
      LEFT=$((END - $(date +%s))); if [ "$LEFT" -lt "$MINLEFT" ]; then WHY="window ended with stories still open"; break; fi
      if [ "$STALLS" -ge 2 ]; then WHY="two stalls in a row"; break; fi
      demote; pick || repick || { WHY="no lane answered after a stall, even after waiting"; break; }
      continue
    fi
    STALLS=0
    if [ "$NV" -gt "$NV0" ]; then PROGRESS=1; NOCHG=0; say "iteration $IT VERIFIED $((NV-NV0)) more story on $M ($DUR min)"
    elif [ "$A" != "$B" ]; then PROGRESS=1; NOCHG=0; say "iteration $IT partial progress on $M ($DUR min; changes, nothing newly verified)"
    else
      NOCHG=$((NOCHG+1)); say "iteration $IT: changed nothing on $M ($DUR min), $NOCHG in a row"
      if [ "$NOCHG" -ge 3 ]; then WHY="three iterations in a row changed nothing"; break; fi
      demote; pick || repick || { WHY="no lane answered after an iteration that changed nothing"; break; }
    fi
  done
else if [ -z "$KEYED" ]; then WHY="the shared free lanes are used up and there is no NVIDIA_API_KEY secret"; else WHY="no lane answered, even after waiting"; fi; fi
[ -z "$WHY" ] && WHY="all queued stories verified"
finish
