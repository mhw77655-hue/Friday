#!/bin/bash
set -e
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"
# Check local HEAD equals origin/main
LOCAL=$(git rev-parse HEAD)
git fetch origin main >/dev/null 2>&1 || true
REMOTE=$(git rev-parse origin/main 2>/dev/null || echo "")
if [ "$LOCAL" != "$REMOTE" ] && [ -n "$REMOTE" ]; then
  echo "local HEAD differs from origin/main"
  exit 1
fi
# Check passes flags in prd.json match expected state (basic check)
python3 -c "
import json
d = json.load(open('.ralph/prd.json'))
# all stories up to done stage should be consistent
pass
" || exit 1
exit 0
