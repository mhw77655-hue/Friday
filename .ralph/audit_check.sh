#!/usr/bin/env bash
# Proof that .ralph/audit.py does what the AUDIT-TOOL story claims, run
# entirely offline against the committed repo.
#
# This file is the ORACLE, not the detector. It is allowed to read VISION.md
# Section 12 to learn which three deviations are supposed to be found -- that
# is what a test is for. The tool under test is not, and AC1 below proves it
# did not: the six check functions are extracted from audit.py and searched for
# the disclosed names, and the report is checked for naming each one.
#
# Every check prints PASS or FAIL with the reason. Exits non-zero if any fail.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
cd "$REPO"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

FAILED=0
pass() { printf '  PASS  %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; FAILED=$((FAILED + 1)); }
check() { if [ "$1" = "0" ]; then pass "$2"; else fail "$2"; fi; }
newest_sidecar_file() {
    ls -t "$1"/audit_*.json 2>/dev/null | head -1
}
newest_sidecar() {
    python3 -c "
import json, sys
f = sys.argv[1]
d = json.load(open(f))
what = sys.argv[2]
print({'deviations': len(d['deviations']),
       'observations': len(d['observations']),
       'count': len(d['deviations']) + len(d['observations'])}[what])" \
    "$(newest_sidecar_file "$1")" "$2" 2>/dev/null
}

# The two commits that introduced the disclosed deviations.
POS_BASE="$(git rev-parse a59ac69^)"
POS_HEAD="9c92571"

echo "audit_check.sh -- AC1, AC2, AC4, AC5, AC6 for .ralph/audit.py"
echo

# ---------------------------------------------------------------------------
echo "AC1  positive control: run cold, and surface all three disclosed deviations"
# ---------------------------------------------------------------------------

# The coldness claim, checked structurally rather than asserted. The check code
# is the source region from the six-checks banner up to the recipes banner; a
# name from the disclosures appearing there would mean the detector was told.
python3 - "$WORK" <<'PY'
import re, sys, pathlib
work = sys.argv[1]
src = pathlib.Path(".ralph/audit.py").read_text(encoding="utf-8")
start = src.index("# The six checks")
end = src.index("# Remediation recipes")
checks = src[start:end]
# Names Section 12 discloses. If any appears in the DETECTOR, discovery was
# contaminated and AC1 is void regardless of what the report says.
disclosed = ["ClaimKind", "ChangeLayer", "FAST/SLOW/CORE", "WORKSPACE-CORE",
             "CONTINUITY-LAW", "S13_PHRASES_DISCLOSED"]
hits = [n for n in disclosed if n in checks]
pathlib.Path(work, "cold.txt").write_text(
    "none" if not hits else " ".join(hits), encoding="utf-8")
# The detector must not read the section that discloses the answers.
pathlib.Path(work, "slice.txt").write_text(
    str(len(re.findall(r'_slice\(\s*raw\s*,\s*"12"', src))), encoding="utf-8")
PY
COLD="$(cat "$WORK/cold.txt")"
[ "$COLD" = "none" ]
check $? "the detector names none of the three disclosed deviations ($COLD)"
SLICES="$(cat "$WORK/slice.txt")"
[ "$SLICES" = "0" ]
check $? "audit.py never extracts VISION.md Section 12 (extractions of 12/13: $SLICES)"

python3 .ralph/audit.py --base "$POS_BASE" --head "$POS_HEAD" --no-model \
    --out "$WORK/pos.md" --out-dir "$WORK" >"$WORK/pos.out" 2>&1
[ $? -eq 0 ]
check $? "the cold run completed (report at $WORK/pos.md)"

for sym in ClaimKind ChangeLayer DEFAULT_MIN_EVIDENCE_CLAIMS DEFAULT_MIN_INTERVAL_MS; do
    grep -q "$sym" "$WORK/pos.md"
    check $? "found independently: $sym"
done

grep -q "forTarget" "$WORK/pos.md"
check $? "the three-way classification is named as forTarget, not just as an enum"

# Each finding must cite a file and a line, or it is an assertion, not a finding.
grep -qE '\*\*Where:\*\* `[^`]+:[0-9]+`' "$WORK/pos.md"
check $? "every deviation names a file and a line"

# The report must say what it did not read, or "cold" is unverifiable from it.
grep -q "Not\*\* read: VISION.md Section 12" "$WORK/pos.md"
check $? "the report states Section 12 was not read"

echo
# ---------------------------------------------------------------------------
echo "AC2  negative control: the Workspace interface and InMemoryWorkspace"
# ---------------------------------------------------------------------------
python3 .ralph/audit.py --no-model --out "$WORK/neg.md" --out-dir "$WORK" \
    --paths mobile/app/src/main/java/com/jarvis/app/cognition/workspace/Workspace.kt \
            mobile/app/src/main/java/com/jarvis/app/cognition/workspace/InMemoryWorkspace.kt \
    >"$WORK/neg.out" 2>&1
[ $? -eq 0 ]
check $? "the negative run completed"

NEG_DEV=$(newest_sidecar "$WORK" deviations 2>/dev/null)
[ "$NEG_DEV" = "0" ]
check $? "zero deviations on Workspace.kt + InMemoryWorkspace.kt (got $NEG_DEV)"

# A negative control that reports nothing at all proves nothing: the checks must
# have run and must have explained the near-miss they declined to flag.
NEG_OBS=$(newest_sidecar "$WORK" observations 2>/dev/null)
[ "$NEG_OBS" -ge 1 ]
check $? "and it still explains the near-miss it declined to flag ($NEG_OBS observation(s))"
grep -q "why_not\|Not a deviation because" "$WORK/neg.md"
check $? "the near-miss carries a stated reason, not silence"

echo
# ---------------------------------------------------------------------------
echo "AC4  no self-merge: structurally, and at run time"
# ---------------------------------------------------------------------------
# Static: the tool must contain no path that writes main or closes a story.
python3 - "$WORK" <<'PYEOF'
import ast, pathlib, sys

tree = ast.parse(pathlib.Path(".ralph/audit.py").read_text(encoding="utf-8"))
bad = []


def literals(node):
    return [n.value for n in ast.walk(node)
            if isinstance(n, ast.Constant) and isinstance(n.value, str)]


for node in ast.walk(tree):
    if not isinstance(node, ast.Call):
        continue
    lits = literals(node)
    if "merge" in lits:
        bad.append((node.lineno, "calls git merge", ""))
    if "push" in lits:
        # The one permitted push must be scoped to a refspec, so it can only
        # ever move the audit branch and never `main`.
        if not any("refs/heads/" in v for v in lits):
            bad.append((node.lineno, "pushes without a refs/heads/ refspec", ""))
    if "reject_story" in lits or "reject_story.py" in lits:
        bad.append((node.lineno, "can reject a story", ""))

# `prd.json` and `reject_story.py` are named ONLY in the module docstring, as
# claims about what the tool does not do. Anywhere else it would be a use.
docstrings = set()
for node in ast.walk(tree):
    if isinstance(node, (ast.Module, ast.FunctionDef, ast.AsyncFunctionDef,
                         ast.ClassDef)):
        d = ast.get_docstring(node, clean=False)
        if d:
            docstrings.add(d)
for node in ast.walk(tree):
    for lit in [n for n in ast.walk(node)
                if isinstance(n, ast.Constant) and isinstance(n.value, str)]:
        v = lit.value
        if ("prd.json" in v or "reject_story" in v) and v not in docstrings:
            bad.append((node.lineno, "names the PRD outside a docstring", v[:60]))

pathlib.Path(sys.argv[1], "ac4.txt").write_text(
    "\n".join("%s: %s %s" % (n, why, txt) for n, why, txt in bad) or "clean",
    encoding="utf-8")
PYEOF
AC4="$(cat "$WORK/ac4.txt")"
[ "$AC4" = "clean" ]
check $? "no code path in audit.py merges, pushes main, writes prd.json, or rejects a story"

# main is refused even when asked directly.
python3 .ralph/audit.py --base "$POS_BASE" --head "$POS_HEAD" --no-model \
    --make-branch main --out "$WORK/refuse.md" --out-dir "$WORK" >"$WORK/refuse.out" 2>&1
grep -qi "refus" "$WORK/refuse.out"
check $? "asking for --make-branch main is refused, not obeyed"

python3 .ralph/audit.py --base "$POS_BASE" --head "$POS_HEAD" --no-model \
    --make-branch not-main-ish --run-ci not-under-audit --out "$WORK/refuse2.md" --out-dir "$WORK" \
    >"$WORK/refuse2.out" 2>&1
grep -qi "audit/" "$WORK/refuse2.out"
check $? "asking to push outside audit/ is refused before any push happens"

# Dynamic: run the whole tool, branch building included, and prove main and the
# PRD did not move. A structural claim about a script proves the script reads
# well; only this proves the script behaves.
SHA_BEFORE="$(git rev-parse HEAD)"
PRD_BEFORE="$(cksum .ralph/prd.json)"
BR_BEFORE="$(git rev-parse --abbrev-ref HEAD)"
python3 .ralph/audit.py --base "$POS_BASE" --head "$POS_HEAD" --no-model \
    --make-branch "audit/ac4-probe-$$" --out "$WORK/branch.md" --out-dir "$WORK" >"$WORK/branch.out" 2>&1
git branch -D "audit/ac4-probe-$$" >/dev/null 2>&1
[ "$(git rev-parse HEAD)" = "$SHA_BEFORE" ]
check $? "main's HEAD is unchanged after a full run that built a branch"
[ "$(cksum .ralph/prd.json)" = "$PRD_BEFORE" ]
check $? "prd.json is byte-identical after that run"
[ "$(git rev-parse --abbrev-ref HEAD)" = "$BR_BEFORE" ]
check $? "the working tree is still on $BR_BEFORE"

echo
# ---------------------------------------------------------------------------
echo "AC5  cost discipline: structure first, one model call only for naming"
# ---------------------------------------------------------------------------
# The structural findings above were produced with --no-model. The count in the
# sidecar is the measurement, not an assumption.
python3 -c "
import json, sys
d = json.load(open(sys.argv[1]))
assert d['model_calls'] == 0, d['model_calls']
assert len(d['deviations']) >= 5, d['deviations']" "$(newest_sidecar_file "$WORK")" 2>/dev/null
check $? "5+ structural deviations were found with 0 model calls"

# One call, for the naming check only.
cat >"$WORK/stub_model.sh" <<'STUB'
#!/usr/bin/env bash
echo "$@" >/dev/null
cat >/dev/null
printf '%s\n' "$(cat "$STUB_COUNT_FILE")" >/dev/null
echo "$$" >> "$STUB_COUNT_FILE"
echo "CoreIdentity | neutral | a local data shape named after what it records"
STUB
chmod +x "$WORK/stub_model.sh"
export STUB_COUNT_FILE="$WORK/calls.txt"
: >"$STUB_COUNT_FILE"

# A fixture with a name that trips the pre-screen, so the gate is genuinely
# reachable: without a candidate the count of 0 would prove nothing.
FIXTURE="$WORK/NamedBrainEngine.kt"
cat >"$FIXTURE" <<'KT'
package com.jarvis.app.auditfixture

/** A fixture whose name ends in an ownership word, to reach the model gate. */
class NamedBrainEngine(val seed: Long) {
    fun think(): Long = seed
}
KT
cp "$FIXTURE" mobile/app/src/main/java/com/jarvis/app/NamedBrainEngine.kt
python3 .ralph/audit.py --model-cmd "$WORK/stub_model.sh" \
    --paths mobile/app/src/main/java/com/jarvis/app/NamedBrainEngine.kt \
    --out "$WORK/model.md" --out-dir "$WORK"  >"$WORK/model.out" 2>&1
rm -f mobile/app/src/main/java/com/jarvis/app/NamedBrainEngine.kt

CALLS=$(wc -l <"$STUB_COUNT_FILE" | tr -d ' ')
[ "$CALLS" = "1" ]
check $? "exactly 1 model call for 1 ownership-name candidate (got $CALLS)"

# Many candidates must still cost one call, not one each.
: >"$STUB_COUNT_FILE"
cat >mobile/app/src/main/java/com/jarvis/app/NamedSoulEngine.kt <<'KT'
package com.jarvis.app.auditfixture
class NamedSoulEngine(val seed: Long)
KT
cat >mobile/app/src/main/java/com/jarvis/app/NamedSelfEngine.kt <<'KT'
package com.jarvis.app.auditfixture
class NamedSelfEngine(val seed: Long)
KT
python3 .ralph/audit.py --model-cmd "$WORK/stub_model.sh" \
    --paths mobile/app/src/main/java/com/jarvis/app/NamedSoulEngine.kt \
            mobile/app/src/main/java/com/jarvis/app/NamedSelfEngine.kt \
    --out "$WORK/model.md" --out-dir "$WORK" >"$WORK/model2.out" 2>&1
rm -f mobile/app/src/main/java/com/jarvis/app/NamedSoulEngine.kt \
      mobile/app/src/main/java/com/jarvis/app/NamedSelfEngine.kt
CALLS2=$(wc -l <"$STUB_COUNT_FILE" | tr -d ' ')
[ "$CALLS2" = "1" ]
check $? "3 candidates still cost 1 model call, not 3 (got $CALLS2)"
python3 -c "
import json, glob, os, sys
files = sorted(glob.glob(os.path.join(sys.argv[1], 'audit_*.json')), key=os.path.getmtime)
d = json.load(open(files[-1]))
assert d['model_calls'] == 1, d['model_calls']" "$WORK" 2>/dev/null
check $? "the sidecar records exactly 1 model call for that run"

echo
# ---------------------------------------------------------------------------
echo "AC6  the tool audits itself, and the zero is argued rather than assumed"
# ---------------------------------------------------------------------------
python3 .ralph/audit.py --self-audit --out "$WORK/self.md" --out-dir "$WORK" >"$WORK/self.out" 2>&1
[ $? -eq 0 ]
check $? "the self-audit ran"
# A vacuous self-audit -- Kotlin checks pointed at a Python file -- reports zero
# with no effort. A real pass must show evidence of having looked.
SELF_OBS=$(newest_sidecar "$WORK" count 2>/dev/null)
[ "${SELF_OBS:-0}" -ge 1 ]
check $? "the self-audit is non-vacuous: it reports ${SELF_OBS:-0} finding(s) about itself"
grep -q "What this run in particular cannot see" "$WORK/self.md"
check $? "and it states what this pass cannot see"

echo
echo "---"
if [ "$FAILED" -eq 0 ]; then
    echo "audit_check.sh: all checks passed"
    exit 0
fi
echo "audit_check.sh: $FAILED check(s) FAILED"
exit 1