#!/usr/bin/env bash
# BUILD-TWIN-ARM64-VERIFICATION (AC1) — teardown.sh
#
# Terminates the throwaway ARM64 twin instance started by provision.sh and
# marks the record terminated. There is NO always-on instance: after this
# script the twin no longer costs anything nor accepts any connection.
#
# Usage (from the repo root):
#   bash .ralph/buildtwin/teardown.sh [-p <profile>]
#
# If state.json is missing or already terminated, this script is a no-op
# (idempotent) — it never invents an instance ID to terminate.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_FILE="$SCRIPT_DIR/state.json"

main () {
  local profile="${AWS_PROFILE:-}"
  while getopts "p:h" opt; do
    case "$opt" in
      p) profile="$OPTARG" ;;
      h) sed -n '1,30p' "$0"; exit 0 ;;
      *) echo "unknown option" >&2; exit 2 ;;
    esac
  done

  if [ ! -f "$STATE_FILE" ]; then
    echo "No $STATE_FILE — nothing to tear down."
    exit 0
  fi

  local status instance_id region
  status="$(grep -o '"status": *"[^"]*"' "$STATE_FILE" | head -1 | cut -d'"' -f4)"
  instance_id="$(grep -o '"instanceId": *"[^"]*"' "$STATE_FILE" | head -1 | cut -d'"' -f4)"
  region="$(grep -o '"region": *"[^"]*"' "$STATE_FILE" | head -1 | cut -d'"' -f4)"

  if [ "$status" = "terminated" ]; then
    echo "Twin already terminated (per $STATE_FILE)."
    exit 0
  fi
  if [ -z "$instance_id" ]; then
    echo "No instanceId recorded in $STATE_FILE — nothing to terminate."
    exit 1
  fi

  echo "Terminating twin instance $instance_id (region $region)..."

  local cmd=(aws --region "$region")
  if [ -n "$profile" ]; then cmd+=(--profile "$profile"); fi
  cmd+=(ec2 terminate-instances --instance-ids "$instance_id")

  if ! "${cmd[@]}" >/dev/null; then
    echo "ERROR: terminate-instances failed. Leaving state.json untouched." >&2
    exit 1
  fi

  python3 - "$STATE_FILE" <<'PYEOF'
import json, sys
path = sys.argv[1]
with open(path) as f:
    state = json.load(f)
state["status"] = "terminated"
with open(path, "w") as f:
    json.dump(state, f, indent=2)
PYEOF

  echo "Twin terminated. state.json marked 'terminated' (idempotent teardown)."
}

main "$@"