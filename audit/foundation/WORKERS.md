# WORKERS: Dispatch, Permissions, and Replacement

This audit analyzes how workers are invoked by `.ralph/ralph.sh` and cloud runner scripts, the permission flags used by each tool, and the smallest modification needed to let another AI worker replace Jules.

---

## 1. How `.ralph/ralph.sh` Dispatches Workers

### Tool Selection & Invocation
In `.ralph/ralph.sh:10`, the default tool is configured:
```bash
TOOL="opencode"
```
It can be overridden via command-line arguments (`.ralph/ralph.sh:198-199`):
```bash
--tool) TOOL="$2"; shift 2 ;;
--tool=*) TOOL="${1#*=}"; shift ;;
```

During the iteration loop (`.ralph/ralph.sh:354-362`), the script dispatches based on `$TOOL`:
```bash
if [[ "$TOOL" == "opencode" ]]; then
  OUTPUT=$(opencode run -m "$CALL_MODEL" "$COMBINED_PROMPT" 2>&1 | tee -a "$RUN_LOG" /dev/stderr) || true
elif [[ "$TOOL" == "amp" ]]; then
  OUTPUT=$(echo "$COMBINED_PROMPT" | amp --dangerously-allow-all 2>&1 | tee -a "$RUN_LOG" /dev/stderr) || true
else
  OUTPUT=$(echo "$COMBINED_PROMPT" | claude --dangerously-skip-permissions --print 2>&1 | tee -a "$RUN_LOG" /dev/stderr) || true
fi
```

### Preflight Probing
Before the main loop begins, `.ralph/ralph.sh:117-124` performs a one-token model preflight check using the same tool dispatch pattern:
```bash
preflight_model_call() {
  local model="$1"
  case "$TOOL" in
    opencode) with_timeout "$PREFLIGHT_MODEL_TIMEOUT" opencode run -m "$model" "$PREFLIGHT_PROMPT" ;;
    amp) printf '%s\n' "$PREFLIGHT_PROMPT" | with_timeout "$PREFLIGHT_MODEL_TIMEOUT" amp --dangerously-allow-all ;;
    *) printf '%s\n' "$PREFLIGHT_PROMPT" | with_timeout "$PREFLIGHT_MODEL_TIMEOUT" claude --dangerously-skip-permissions --print ;;
  esac
}
```

---

## 2. Permission Flags Used

Different worker runners use different permission flags to grant execution privileges without interactive human approval prompts:

1. **`claude`**:
   * Flag: `--dangerously-skip-permissions --print`
   * Source: `.ralph/ralph.sh:123, 361`
   * Effect: Disables all confirmation dialogues for file reads, file edits, and bash command execution when running headless Claude Code CLI.

2. **`amp`**:
   * Flag: `--dangerously-allow-all`
   * Source: `.ralph/ralph.sh:122, 359`
   * Effect: Grants Amp permission to execute commands and access local file systems without interactive prompt blocking.

3. **`opencode`**:
   * Flag: `OPENCODE_PERMISSION='{"external_directory":{"*":"allow"}}'`
   * Source: `.ralph/cloud/run.sh:53`
   * Effect: In cloud and phone runner scripts, `opencode` environment permissions allow full access across all external directories (such as `/sdcard/jarvis-repo`).

---

## 3. Smallest Change to Let Another Worker Replace Jules

Jules is an external GitHub agent operating via GitHub Issues, branches, and PRs (e.g. `audit/ls-wake-order-jules1-*` and PR #3).
To allow another CLI worker or external agent (such as `codestory`, `aider`, `cursor`, or a custom runner) to replace Jules in the automated iteration loop, the smallest change requires adding a new case branch to `.ralph/ralph.sh`.

### The Minimal Diff in `.ralph/ralph.sh`
Only **6 lines** in `.ralph/ralph.sh` need to be changed:

```diff
--- a/.ralph/ralph.sh
+++ b/.ralph/ralph.sh
@@ -121,6 +121,7 @@ preflight_model_call() {
   case "$TOOL" in
     opencode) with_timeout "$PREFLIGHT_MODEL_TIMEOUT" opencode run -m "$model" "$PREFLIGHT_PROMPT" ;;
     amp) printf '%s\n' "$PREFLIGHT_PROMPT" | with_timeout "$PREFLIGHT_MODEL_TIMEOUT" amp --dangerously-allow-all ;;
+    worker) with_timeout "$PREFLIGHT_MODEL_TIMEOUT" my-worker --dangerously-skip-permissions "$PREFLIGHT_PROMPT" ;;
     *) printf '%s\n' "$PREFLIGHT_PROMPT" | with_timeout "$PREFLIGHT_MODEL_TIMEOUT" claude --dangerously-skip-permissions --print ;;
   esac
 }
@@ -358,6 +359,8 @@ while true; do
       OUTPUT=$(opencode run -m "$CALL_MODEL" "$COMBINED_PROMPT" 2>&1 | tee -a "$RUN_LOG" /dev/stderr) || true
     elif [[ "$TOOL" == "amp" ]]; then
       OUTPUT=$(echo "$COMBINED_PROMPT" | amp --dangerously-allow-all 2>&1 | tee -a "$RUN_LOG" /dev/stderr) || true
+    elif [[ "$TOOL" == "worker" ]]; then
+      OUTPUT=$(my-worker --dangerously-skip-permissions "$COMBINED_PROMPT" 2>&1 | tee -a "$RUN_LOG" /dev/stderr) || true
     else
       OUTPUT=$(echo "$COMBINED_PROMPT" | claude --dangerously-skip-permissions --print 2>&1 | tee -a "$RUN_LOG" /dev/stderr) || true
     fi
```

### Why This Is Sufficient
* The prompt generation (`build_prompt` at `.ralph/ralph.sh:267-277`), progress monitoring, git tracking (`COMMIT_BEFORE` / `COMMIT_AFTER`), CI verification (`.ralph/ci_verify.sh`), and retry loops are completely tool-agnostic.
* Once the worker tool CLI invocation is plugged into `preflight_model_call` and the execution loop, any agent supporting headless execution with bypass permissions can run Ralph cycles seamlessly.
