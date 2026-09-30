#!/usr/bin/env python3
"""audit.py -- the vision audit Ralph runs after a story closes.

It reads .ralph/VISION.md and the diff that story produced, checks the changed
code against VISION.md Section 13's anti-drift checklist, and produces TWO
things, never one:

  1. a report naming every finding with a file and a line, in the same
     no-unverified-claims standard the rest of this repo holds; and
  2. for findings it is CONFIDENT about, a real draft patch that reconfigures
     the flagged code toward the liquid form Section 4 describes.

The patch is a diff, on its own branch, never merged by this tool. See
"NEVER" below for the guarantees that are structural, not promises.

COLD, NOT HINTED
----------------
This tool reads Section 4 (the rule a finding is measured against) and
Section 13 (the checklist). It deliberately does NOT read Section 12, which is
where the repo discloses its known deviations by name: a tool told the answers
proves nothing, so `Vision.load` extracts only the two sections it is allowed
to see, and `audit_check.sh` asserts this source never mentions Section 12.

COST DISCIPLINE
---------------
Five of the six checks are grep/parse level and never spend a token. Only
`check_ownership_naming` may call a model, and it makes AT MOST ONE call per
run no matter how many names it screens. The run report prints the exact model
call count, so "no model was needed" and "a model was consulted" are
distinguishable facts rather than an assumption.

NEVER (all of it structural, all of it asserted by audit_check.sh)
-----------------------------------------------------------------
  * It never writes to main, never merges, never fast-forwards, never
    reverts. Its only git write is a commit on a freshly created `audit/`
    branch inside a throwaway worktree.
  * It never writes prd.json and never closes a story. It has no code path
    that calls reject_story.py or edits a `passes` field. Findings are
    reported, never enforced.
  * Its exit status is independent of what it found: 0 when it produced a
    report, 2 only on a usage/IO error. A finding is not a failure.
  * It never touches the security floor, approval requirements, identity
    adjacency, or anything CONTINUITY-LAW already gates. `OUT_OF_SCOPE`
    states exactly which paths that is.

Usage
-----
  python3 .ralph/audit.py --base <ref> --head <ref>   # audit that diff
  python3 .ralph/audit.py --base <ref> --head <ref> --no-model
  python3 .ralph/audit.py --paths <file>...           # audit whole files
  python3 .ralph/audit.py --make-branch <name>        # build the patch branch
  python3 .ralph/audit.py --run-ci <name>             # ... and prove it on CI
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
VISION = os.path.join(HERE, "VISION.md")
OUT_DIR = os.path.join(HERE, "audit")

# Production Kotlin is the only thing this tool judges. Tests are evidence for
# a human, not implementation code, and the tool's own patch rewrites neither.
SOURCE_PREFIX = "mobile/app/src/main/java"

# What this tool never looks at, and why. Named here rather than implied, so
# "it did not flag it" and "it was not allowed to look" are distinguishable.
OUT_OF_SCOPE = (
    "the security floor and approval requirements (VISION.md Section 5's one "
    "fixed exception: authorization roots, protected operations, self-modification "
    "protections)",
    "identity-adjacent claims and anything CONTINUITY-LAW already gates -- the "
    "gate is the mechanism, so flagging the gate's own vocabulary as drift would "
    "be flagging the fix; its two disclosed deviations are still reported because "
    "they are properties of the CODE, not of the identity it protects",
    "the non-production tree (tests, resources, build files): this tool audits "
    "implementation code, and a test is a claim about code, not code",
)


# ---------------------------------------------------------------------------
# VISION.md -- Sections 4 and 13 only
# ---------------------------------------------------------------------------

class Vision:
    """The two sections of VISION.md this tool is allowed to read."""

    def __init__(self, path):
        with open(path, "r", encoding="utf-8") as fh:
            raw = fh.read()
        self.section4 = self._slice(raw, "4")
        self.checklist = self._checklist(raw)
        if not self.section4 or not self.checklist:
            raise SystemExit(
                "audit.py: VISION.md did not yield both Section 4 and the Section 13 "
                "checklist; refusing to run a checklist it could not read"
            )
        self.item = self._resolve_items()
        if sorted(self.item) != sorted(S13_PHRASES):
            raise SystemExit(
                "audit.py: could not match every Section 13 item this tool cites "
                "(%s). The checklist was: %s. Refusing to cite a question by index."
                % (sorted(set(S13_PHRASES) - set(self.item)),
                   " | ".join(self.checklist))
            )

    @staticmethod
    def _slice(raw, number):
        """The text under '## <number>. <title>', up to the next '## ' heading."""
        m = re.search(
            r"^##\s+" + re.escape(number) + r"\.\s.*?$(.*?)(?=^##\s|\Z)",
            raw,
            re.M | re.S,
        )
        return m.group(1).strip() if m else ""

    @staticmethod
    def _checklist(raw):
        """Section 13's question-per-item list, verbatim, in order.

        The items WRAP across lines in the real file, so a bullet and its
        continuation lines are rejoined into one question before the trailing
        `?` is looked for.
        """
        body = Vision._slice(raw, "13")
        items, current = [], None

        def flush():
            if current:
                items.append(" ".join(" ".join(current).split()))

        for line in body.splitlines():
            if re.match(r"^-\s+\S", line):
                flush()
                current = [line.lstrip("- ").strip()]
            elif current is not None:
                if line.strip() and line.startswith((" ", "\t")):
                    current.append(line.strip())
                else:
                    flush()          # end of the bullet, on a blank line too
                    current = None
        flush()
        return [i for i in items if i.endswith("?")]

    def _resolve_items(self):
        """Map each check's key to the index of the question it really cites.

        Resolved by a distinctive phrase rather than a fixed index, so a
        reordering or an edit to VISION.md's checklist can never make this
        tool quote the wrong question as its authority.
        """
        out = {}
        for key, phrase in S13_PHRASES.items():
            hits = [i for i, item in enumerate(self.checklist) if phrase in item]
            if len(hits) != 1:
                out[key] = None
            else:
                out[key] = hits[0]
        return out


# Each check cites a Section 13 question by a distinctive phrase in it, so the
# quote in the report is the file's own text and is guaranteed to be the
# question this check actually answers. S13_KEYS is the list of keys the
# Vision constructor above verifies are all resolvable.
S13_PHRASES = {
    "own_module": "property of the whole system",
    "fixed_role": "emergent, per-task behavior",
    "predefined": "predefined list of inputs",
    "named_link": "fixed, named connection",
    "permanent_job": "one permanent job",
    "hardware_ceiling": "implicit",
    "renamed": "rename the old process",
    "representation": "as if it were what the underlying thing",
}


# ---------------------------------------------------------------------------
# The diff under audit
# ---------------------------------------------------------------------------

@dataclass
class ChangedFile:
    path: str
    added: dict = field(default_factory=dict)   # new line no -> source text
    removed: int = 0
    text: str = ""                              # the file's new content
    created: bool = False                       # the story created the file


class Diff:
    """The added lines of one story's production diff, plus the new content."""

    def __init__(self, files, base=None, head=None):
        self.files = files
        self.base = base
        self.head = head

    @classmethod
    def from_range(cls, base, head):
        status = subprocess.run(
            ["git", "-C", REPO, "diff", "--name-status", "--no-color",
             base, head, "--", SOURCE_PREFIX],
            capture_output=True, text=True, check=True,
        ).stdout
        created = {ln.split("\t", 1)[1].strip()
                   for ln in status.splitlines() if ln.startswith("A\t")}
        out = subprocess.run(
            ["git", "-C", REPO, "diff", "--unified=0", "--no-color",
             "--diff-filter=ACMR", base, head, "--", SOURCE_PREFIX],
            capture_output=True, text=True, check=True,
        ).stdout
        files, cur, lineno = {}, None, 0
        for line in out.splitlines():
            if line.startswith("+++ b/"):
                cur = line[6:].strip()
                files[cur] = ChangedFile(path=cur, created=cur in created)
            elif line.startswith("@@") and cur:
                # @@ -a,b +c,d @@  -> the new-file range starts at c
                m = re.search(r"\+(\d+)", line)
                lineno = int(m.group(1)) - 1
            elif line.startswith("+") and cur:
                lineno += 1
                files[cur].added[lineno] = line[1:]
            elif line.startswith("-") and cur:
                files[cur].removed += 1
            elif not line.startswith("\\") and cur:
                lineno += 1
        for path, cf in files.items():
            show = subprocess.run(
                ["git", "-C", REPO, "show", "%s:%s" % (head, path)],
                capture_output=True, text=True,
            )
            cf.text = show.stdout if show.returncode == 0 else ""
        return cls([cf for cf in files.values() if cf.text], base=base, head=head)

    @classmethod
    def from_paths(cls, paths):
        files = []
        for path in paths:
            full = os.path.join(REPO, path)
            if not os.path.isfile(full):
                continue
            with open(full, "r", encoding="utf-8") as fh:
                text = fh.read()
            # A whole-file audit treats every line as added: there is no diff to
            # narrow to, and pretending otherwise would hide findings.
            cf = ChangedFile(path=path, text=text, created=True,
                             added={i: ln for i, ln in enumerate(text.splitlines(), 1)})
            files.append(cf)
        return cls(files, base="", head="HEAD")


# ---------------------------------------------------------------------------
# Findings
# ---------------------------------------------------------------------------

@dataclass
class Finding:
    check: str
    file: str
    line: int
    symbol: str
    detail: str
    s13: int
    vision_section: str
    severity: str            # "deviation" | "observation"
    evidence: str            # the exact source line, verbatim
    why_not: str = ""        # for severity == "observation": why this is not one
    recipe: str = ""         # the remediation recipe, when one applies

    @property
    def where(self):
        return "%s:%d" % (self.file, self.line)

    @property
    def key(self):
        return "%s@%s" % (self.check, self.where)


# ---------------------------------------------------------------------------
# Kotlin source shape, parsed once per file
# ---------------------------------------------------------------------------

TYPE_DECL = re.compile(
    r"^\s*(?:@\w+\s+)*(?:public\s+|internal\s+|private\s+)?"
    r"(?:data\s+|value\s+|sealed\s+|abstract\s+|open\s+|final\s+)*"
    r"(class|interface|object|enum\s+class)\s+([A-Za-z_][A-Za-z0-9_]*)"
)
NUMERIC_CONST = re.compile(
    r"^\s*(?:(?:public|internal|private|const)\s+)*"
    r"(?:const\s+)?val\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*(Int|Long|Double|Float)\s*)?"
    r"=\s*(-?\d[\d_]*(?:\.\d[\d_]*)?[LlFf]?)\s*$"
)
SHOUTY = re.compile(r"^[A-Z][A-Z0-9]*(_[A-Z0-9]+)+$")
FUN_DECL = re.compile(
    r"^\s*(?:(?:public|internal|private|override|inline|suspend|operator)\s+)*"
    r"fun\s+(?:<[^>]+>\s*)?([A-Za-z_][A-Za-z0-9_]*)\s*\("
)
COMPARISON = re.compile(
    r"(?P<lhs>[A-Za-z_][A-Za-z0-9_.]*)\s*(?P<op>>=|<=|==|!=|>|<)\s*"
    r"(?P<rhs>[A-Za-z_][A-Za-z0-9_.]*|-?\d[\d_]*(?:\.\d[\d_]*)?[LlFf]?)"
)
IDENT_OR_INTERVAL = re.compile(
    r"(?i)(interval|millis|seconds|cooldown|ttl|age|elapsed|_ms|ms$)"
)
SIZE_LHS = re.compile(r"\.(size|count)\b")
SUBTRACTION_LHS = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*\s*-\s*[A-Za-z_][A-Za-z0-9_.]*$")
PARAM_DEFAULT = re.compile(
    r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*:\s*(?:Int|Long|Double|Float)\s*=\s*"
    r"(?P<const>[A-Za-z_][A-Za-z0-9_]*)\b"
)
METHOD_CALL = re.compile(r"\b(?P<recv>[A-Za-z_][A-Za-z0-9_]*)\.[a-z][A-Za-z0-9_]*\s*\(")
PROP_DECL = re.compile(
    r"^\s*(?:(?:public|internal|private|override|open|final)\s+)*"
    r"(?:val|var)\s+(?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*:\s*(?P<type>[A-Z][A-Za-z0-9_]*)"
)
CTOR_PARAM = re.compile(
    r"(?:^|,\s*)(?P<name>[a-z][A-Za-z0-9_]*)\s*:\s*(?P<type>[A-Z][A-Za-z0-9_]*)"
)

# Names that DECLARE a role of settling, choosing or owning a meaning. Used by
# two different checks for two different reasons; see their docstrings.
ARBITER_NAME = re.compile(
    r"(Resolver|Arbiter|Judge|Decider|Selector|Router|Director|Orchestrator|"
    r"Governor|Strategist|Planner|Policy)$"
)
OWNERSHIP_NAME = re.compile(
    r"(Engine|Brain|Mind|Core|Cortex|Reasoner|Thinker|Intelligen\w*|"
    r"Sentien\w*|Conscious\w*|Personality|Ego|Identity)$"
)
# Types whose result is a RESOURCE decision (what runs, on which tier, at what
# cost) rather than a MEANING decision (what she thinks, who she is).
RESOURCE_TYPE = re.compile(
    r"(Tier|Handle|Slot|Budget|Quota|Cap|Latency|Clock|Schedule\w*|Lease|Token|"
    r"Decision$|WakeResult|Mode)$"
)
# The shared claim store and the durable records around it are the mechanism
# that REPLACES named wires, so a dependency on them is the intended shape.
STORE_TYPES = {"Workspace", "InMemoryWorkspace", "ChangeLog", "FileChangeLog",
               "Claim", "ClaimKind", "ChangeLayer", "ContinuityLaw",
               "ChangeLogEntry", "ReplayFixture", "RecordedTurn"}
PORT_TYPES = {"()", "Function"}


class Repo:
    """Index of every type declared in the production tree."""

    def __init__(self, scan_from=None):
        self.classes, self.interfaces, self.objects = {}, {}, {}
        roots = [scan_from] if scan_from else [os.path.join(REPO, SOURCE_PREFIX)]
        files = []
        for root in roots:
            for dirpath, _dirs, names in os.walk(root):
                files += [os.path.join(dirpath, n) for n in names if n.endswith(".kt")]
        for path in files:
            with open(path, "r", encoding="utf-8", errors="replace") as fh:
                for n, line in enumerate(fh, 1):
                    m = TYPE_DECL.match(line)
                    if not m:
                        continue
                    kind, name = m.group(1), m.group(2)
                    rel = os.path.relpath(path, REPO)
                    if kind == "interface":
                        self.interfaces.setdefault(name, (rel, n))
                    elif kind == "object":
                        self.objects.setdefault(name, (rel, n))
                    else:
                        self.classes.setdefault(name, (rel, n))

    def declared_type(self, name):
        """(kind, relpath, line) if NAME is a real type in the production tree."""
        for table, kind in ((self.classes, "class"), (self.interfaces, "interface"),
                            (self.objects, "object")):
            if name in table:
                return (kind,) + table[name]
        return None

    def file_of(self, name):
        """The production file that declares NAME, or None."""
        got = self.declared_type(name)
        return got[1] if got else None


def file_at(rev, path):
    """The content of PATH at REV, or None when the file did not exist there."""
    show = subprocess.run(
        ["git", "-C", REPO, "show", "%s:%s" % (rev, path)],
        capture_output=True, text=True,
    )
    return show.stdout if show.returncode == 0 else None


def enum_members(text, name):
    """The member names of `enum class NAME` in `text`, in declaration order.

    Comments are stripped before the body is split, and a member's constructor
    arguments are dropped: the KDoc between two members and the `(false)` on a
    member are both real in Kotlin source, and a member list that missed them
    would report a closed enum as having no members -- which is exactly the
    false negative this tool must not have.
    """
    m = re.search(r"enum\s+class\s+" + re.escape(name) + r"\b[^{]*\{", text)
    if not m:
        return []
    start, depth, body = m.end(), 1, ""
    for ch in text[start:]:
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                break
        body += ch
    body = re.sub(r"/\*.*?\*/", " ", body, flags=re.S)     # KDoc / block comments
    body = re.sub(r"//[^\n]*", " ", body)                  # line comments
    out, buf, inner = [], "", 0
    for ch in body:
        if ch in "([{":
            inner += 1
        elif ch in ")]}":
            inner -= 1
        if inner > 0:
            continue                                       # inside a member's args
        if ch in ",;":
            # `)` is appended as it closes the member's own argument list, so a
            # member with arguments arrives here as "MENTAL_STATE)".
            token = buf.strip().split("(")[0].strip().rstrip(")").strip()
            if re.fullmatch(r"[A-Z][A-Z0-9_]*", token):
                out.append(token)
            buf = ""
        else:
            buf += ch
    return out


def function_body(text, fun_name, fun_line):
    """The source of one function, from its `fun` line to its closing brace."""
    lines = text.splitlines()
    idx = fun_line - 1
    if idx < 0 or idx >= len(lines):
        return ""
    body, depth, started = [], 0, False
    for line in lines[idx:]:
        depth += line.count("{") - line.count("}")
        body.append(line)
        if "{" in line:
            started = True
        if started and depth <= 0:
            break
    return "\n".join(body)


# ---------------------------------------------------------------------------
# The six checks
# ---------------------------------------------------------------------------

def check_closed_type(diff, repo, vision):
    """S13 item 3 -- a predefined list of TYPES standing in for an open space.

    Structural, not a judgement: a story that adds `enum class` or `sealed
    class` has added a closed set of forms, and Section 4 says the space of
    forms a mechanism may take must stay open ("no fixed input/output list, no
    component whose name is a permanent cognitive job title"). A closed set is
    not automatically wrong -- it is a FINDING, to be argued about by a human,
    with the member list printed so the argument has facts.
    """
    out = []
    for cf in diff.files:
        for lineno, src in sorted(cf.added.items()):
            m = re.match(
                r"^\s*(?:public\s+|internal\s+|private\s+)?"
                r"(sealed\s+)?(enum\s+class|class|interface|object)\s+"
                r"([A-Za-z_][A-Za-z0-9_]*)",
                src,
            )
            if not m:
                continue
            sealed, kind, name = m.group(1), m.group(2).strip(), m.group(3)
            if not sealed and kind != "enum class":
                continue          # only a CLOSED set is this finding
            members = enum_members(cf.text, name)
            if kind == "enum class" and len(members) < 2:
                continue          # a one-member enum closes over nothing
            listed = ", ".join(members) if members else "(not an enum body)"
            out.append(Finding(
                check="closed_type",
                file=cf.path,
                line=lineno,
                symbol=name,
                detail=(
                    "a story added a CLOSED set of forms: `%s %s` with %d member(s) "
                    "[%s]. Section 4: a mechanism exists because it is the best "
                    "implementation found so far, so a fixed list of the forms it "
                    "may take reserves the outcome for that implementation."
                    % (("sealed " + kind) if sealed else kind, name,
                       len(members), listed)
                ),
                s13=vision.item["predefined"],
                vision_section="4",
                severity="deviation",
                evidence=src.strip(),
            ))
    return out


def outcome_members(body, members):
    """Which members of a closed type actually reach the caller of `body`.

    A member counts when it sits in a RESULT position, which in Kotlin is one
    of three shapes, and all three occur in real code:

        return FAST                     -> an early return
        cond -> CORE                    -> an expression branch of a `when`
        SLOW                             -> a block-bodied branch, whose value
                                           is its last expression

    The third shape is why a scanner that only looked for `-> MEMBER` silently
    reported zero for a real three-way classifier whose middle and last branches
    are block-bodied. Missing a real classification is the worst kind of wrong
    for this tool, so all three are measured, and a member that merely appears
    in a comment or a KDoc reference is not counted.
    """
    found, where = set(), {}
    for raw in body.splitlines():
        # A trailing `// comment` is documentation, not a result value.
        code = re.sub(r"//.*$", "", raw).strip()
        if not code:
            continue
        m_ret = re.search(r"\breturn\b(.*)$", code)
        if m_ret:
            candidate = m_ret.group(1)
        else:
            arrow = code.rfind("->")
            if arrow != -1 and re.match(r"->\s*[A-Za-z_{(]", code[arrow:]):
                candidate = code[arrow + 2:]
            else:
                candidate = re.sub(r"^[\s{}]+", "", code)
        candidate = candidate.rstrip("{} \t").strip()
        for member in members:
            if re.fullmatch(r"(?:\w+\.)*" + re.escape(member) + r"\b", candidate):
                found.add(member)
                where.setdefault(member, code)
    return found, where


def check_fixed_classification(diff, repo, vision):
    """S13 item 2 -- an emergent, per-task behavior turned into a fixed function
    with a fixed role.

    Structural: a function ADDED by this story whose DECLARED RETURN TYPE is a
    closed type, and whose body returns two or more distinct members of that
    type. The output set was decided when the function was written, not by
    anything the input carries, so the roles are fixed.

    The declared return type is the discriminator that keeps this honest: a
    function only participates in a fixed classification if the shape of its
    answer was itself fixed by a type. A companion lookup that returns the same
    closed type but resolves a name through `values().firstOrNull { ... }`
    reaches no member by name, so it stays clean -- it looks a name up, it does
    not sort anything into a category.

    KNOWN LIMITATION: a closed type split across files (a `sealed` hierarchy
    whose implementations each declare their own file) cannot be enumerated by
    this text scan, so a classification over such a type is reported as NOT
    SEEN rather than as clean. The report says so in its limits.
    """
    out = []
    closed = {}
    for other in diff.files:
        for name in set(re.findall(r"enum\s+class\s+([A-Za-z_][A-Za-z0-9_]*)",
                                   other.text)):
            members = enum_members(other.text, name)
            if len(members) >= 2:
                closed[name] = members
    for cf in diff.files:
        for lineno, src in sorted(cf.added.items()):
            m = re.match(
                r"^\s*(?:(?:public|internal|private|override|inline|suspend|operator)\s+)*"
                r"fun\s+(?:<[^>]+>\s*)?[A-Za-z_][A-Za-z0-9_]*\s*\([^)]*\)\s*:\s*"
                r"([A-Za-z_][A-Za-z0-9_]*)\s*(?:\?|)\s*(?:\{|=)",
                src,
            )
            if not m:
                continue
            ret = m.group(1)
            members = closed.get(ret)
            if not members:
                continue
            body = function_body(cf.text, None, lineno)
            used, where = outcome_members(body, members)
            if len(used) < 2:
                continue
            out.append(Finding(
                check="fixed_classification",
                file=cf.path,
                line=lineno,
                symbol="%s -> {%s}" % (ret, ", ".join(sorted(used))),
                detail=(
                    "a story added `%s`, declared to return the closed type "
                    "`%s`, and it reaches %d of that type's %d members [%s] as "
                    "written-out results rather than as something the input "
                    "earned. The set of roles was fixed when the function was "
                    "written. Section 4: the architecture is allowed to become "
                    "whatever actually produces the outcome; a fixed "
                    "classification reserves it in advance. Each outcome is "
                    "reached at: %s."
                    % (src.strip().split("(")[0].strip(), ret, len(used),
                       len(members), ", ".join(sorted(used)),
                       "; ".join("%s -> `%s`" % (m, where[m])
                                 for m in sorted(used)))
                ),
                s13=vision.item["fixed_role"],
                vision_section="4",
                severity="deviation",
                evidence=src.strip(),
            ))
    return out


def _numeric_constants(cf):
    """(name, value, lineno, src) for this file's declared numeric constants.

    Two accepted declaration shapes, both stated rather than guessed:
    `const val NAME = <number>` (with or without a type annotation), and a
    SCREAMING_CASE `val NAME: <number type> = <number>`, which is how this
    repo names every threshold it declares. A camelCase property default such
    as `decayRate: Double = 0.0` is a DATA VALUE, not a threshold, and is
    excluded on purpose.
    """
    out = []
    for lineno, src in sorted(cf.added.items()):
        m = NUMERIC_CONST.match(src)
        if not m:
            continue
        name, value = m.group(1), m.group(2)
        if "const" not in src and not SHOUTY.match(name):
            continue
        out.append((name, value, lineno, src.strip()))
    return out


def check_flat_threshold(diff, repo, vision):
    """S13 item 3 -- a threshold, count or interval that is a bare constant
    where Section 3 and Section 4 require it to develop with accumulated
    evidence.

    Structural, and deliberately three-valued so it does not cry wolf. A
    constant is a CANDIDATE if its use reaches a comparison. That comparison
    is then classified:
      * an EVIDENCE-COUNT gate when the compared-against quantity is a count of
        retained things (`x.size`);
      * an INTERVAL gate when the constant names time or the compared quantity
        is a difference of two instants;
      * anything else -- a bounded ratio floor, a validity check -- is recorded
        as an OBSERVATION with the reason it was not called a deviation.
    Only the first two are deviations, and Section 12's own wording ("a flat
    evidence count AND a flat time interval") is exactly those two.
    """
    out = []
    constants = []
    for cf in diff.files:
        constants += [(cf, n, v, l, s) for (n, v, l, s) in _numeric_constants(cf)]

    for cf, name, value, lineno, src in constants:
        # Use-name closure: a constant bound as a constructor-parameter default
        # is used under the PARAMETER's name, and it is that name the
        # comparison site will mention. One hop, because one hop is what this
        # codebase does.
        aliases = {name}
        for other in diff.files:
            for n2, line2 in other.added.items():
                m = PARAM_DEFAULT.search(line2)
                if m and m.group("const") == name:
                    aliases.add(m.group("name"))
        sites = []
        for other in diff.files:
            for n2, line2 in sorted(other.added.items()):
                m = COMPARISON.search(line2)
                if not m:
                    continue
                lhs, rhs = m.group("lhs"), m.group("rhs")
                bare = lambda s: s.split(".")[-1]
                if bare(lhs) in aliases or rhs in aliases:
                    sites.append((other, n2, line2.strip(), lhs, rhs))
        if not sites:
            out.append(Finding(
                check="flat_threshold", file=cf.path, line=lineno, symbol=name,
                detail="declared here and used only as a value or in arithmetic; "
                       "it never gates anything, so there is no threshold here.",
                s13=vision.item["predefined"], vision_section="3",
                severity="observation", evidence=src,
                why_not="no comparison in the changed code reaches this constant",
            ))
            continue

        evidence_sites, interval_sites, other_sites = [], [], []
        for other, n2, line2, lhs, rhs in sites:
            if SIZE_LHS.search(lhs) or lhs.split(".")[-1] == "count":
                evidence_sites.append((other, n2, line2))
            elif IDENT_OR_INTERVAL.search(name) or SUBTRACTION_LHS.match(lhs) \
                    or IDENT_OR_INTERVAL.search(rhs):
                interval_sites.append((other, n2, line2))
            else:
                other_sites.append((other, n2, line2))

        is_deviation = bool(evidence_sites or interval_sites)
        kinds = []
        if evidence_sites:
            kinds.append("an evidence COUNT")
        if interval_sites:
            kinds.append("a real-time INTERVAL")
        # EVERY site is listed, not the first one: a report that named only
        # `require(minIntervalMs > 0L)` would point the reader at a check on the
        # configuration instead of at the gate that actually refuses a change,
        # which reads as though the bar were never applied.
        where = "; ".join("%s:%d `%s`" % (o.path, n, t)
                          for o, n, t in (evidence_sites + interval_sites + other_sites))
        out.append(Finding(
            check="flat_threshold",
            file=cf.path,
            line=lineno,
            symbol=name,
            detail=(
                "a story added `%s = %s` and it gates %s at one fixed value for "
                "every field, every value and every moment, however established "
                "the value being challenged already is. Gating sites: %s. "
                "Section 3: a mechanism that cannot itself develop on evidence is "
                "storage, not development. Section 4: the rate limit on a value "
                "should be a consequence of what the value has proved, not a "
                "number written down once."
                % (name, value, " and ".join(kinds) or "a bounded measure", where)
            ) if is_deviation else (
                "a story added `%s = %s` and it gates a bounded measure at one "
                "fixed value (gating sites: %s). Recorded, not called a deviation: "
                "the quantity compared is not an accumulation of evidence and not "
                "elapsed time, so nothing here grows with history."
                % (name, value, where)
            ),
            s13=vision.item["predefined"],
            vision_section="3",
            severity="deviation" if is_deviation else "observation",
            evidence=src,
            why_not="" if is_deviation else
                     "the compared quantity is neither an evidence count nor an "
                     "elapsed interval",
            recipe="evidence-scaled-threshold" if is_deviation else "",
        ))
    return out


def type_param_lines(cf):
    """The line numbers that belong to a TYPE DECLARATION's parameter list.

    `CTOR_PARAM` on its own matches the parameters of any function, so
    `fun verify(baseline: ReplayResult, current: ReplayResult = replay())` read
    as a constructor holding two subsystems -- it is not one. Walking forward
    from each added type declaration while its parentheses are still open reads
    the parameter list that actually belongs to a class, including the
    multi-line form real constructors use.
    """
    lines = cf.text.splitlines()
    inside = set()
    for lineno in sorted(cf.added):
        idx = lineno - 1
        if idx < 0 or idx >= len(lines):
            continue
        if not TYPE_DECL.match(lines[idx]) and not re.match(
                r"^\s*(?:public |internal |private |override |abstract |open |"
                r"data |sealed |value |final )*(class|object|interface)\s", lines[idx]):
            continue
        if "(" not in lines[idx]:
            continue
        depth = 0
        cursor = idx
        while cursor < len(lines):
            depth += lines[cursor].count("(") - lines[cursor].count(")")
            inside.add(cursor + 1)
            cursor += 1
            if depth <= 0:
                break
    return inside


def check_named_wire(diff, repo, vision):
    """S13 item 4 -- a FIXED, NAMED connection between two things where the
    interaction has to vary.

    Structural: a property, or a constructor parameter of a type declaration,
    whose declared type is a CONCRETE production class declared in a DIFFERENT
    file, and a changed line that calls a method on it directly. Holding a
    reference to a named subsystem by type, across a file boundary, IS the
    named wire the shared workspace exists to replace.

    Three exclusions keep this from crying wolf, and each one was written
    because a real file tripped the earlier looser version:

      * the shared store and the log that backs it -- the mechanism, not drift
      * interfaces and objects -- depending on an affordance is what Section 4
        permits
      * a type declared in the SAME file -- `ReplayResult` inside ReplayCheck.kt
        is a local data shape, not a wire between two subsystems
    """
    out = []
    for cf in diff.files:
        ctor_lines = type_param_lines(cf)
        held = {}
        for lineno, src in sorted(cf.added.items()):
            m = PROP_DECL.match(src)
            if m and m.group("type") not in PORT_TYPES:
                held[m.group("name")] = (m.group("type"), lineno)
            if lineno in ctor_lines:
                for pm in CTOR_PARAM.finditer(src):
                    if pm.group("type") not in PORT_TYPES:
                        held.setdefault(pm.group("name"), (pm.group("type"), lineno))
        for name, (type_name, decl_line) in sorted(held.items()):
            if type_name in STORE_TYPES:
                continue
            decl = repo.declared_type(type_name)
            if not decl or decl[0] != "class":
                continue        # an interface/object is an affordance or the store
            if decl[1] == cf.path:
                continue        # same file: a local data shape, not a wire
            for lineno, src in sorted(cf.added.items()):
                m = METHOD_CALL.search(src)
                if m and m.group("recv") == name:
                    out.append(Finding(
                        check="named_wire",
                        file=cf.path,
                        line=decl_line,
                        symbol="%s: %s" % (name, type_name),
                        detail=(
                            "a story wired one subsystem straight to another by "
                            "TYPE: it holds `%s: %s` (declared at %s, a concrete "
                            "class in another file) and calls it directly at "
                            "%s:%d. The "
                            "interaction between these two is therefore fixed at "
                            "compile time, and the only way to change it is to "
                            "change the code. Section 13 asks whether a fixed, "
                            "named connection was created where the interaction "
                            "has to vary; the shared claim store is the mechanism "
                            "that makes it vary."
                        ) % (name, type_name, decl[1], cf.path, lineno),
                        s13=vision.item["named_link"],
                        vision_section="4",
                        severity="deviation",
                        evidence=src.strip(),
                    ))
                    break
    return out


class ModelGate:
    """The ONE place a model may be called, and the counter that proves it."""

    def __init__(self, enabled, command):
        self.enabled = enabled
        self.command = command
        self.calls = 0

    def ask(self, prompt):
        if not self.enabled or not self.command:
            return None
        self.calls += 1
        try:
            done = subprocess.run(self.command, input=prompt, capture_output=True,
                                  text=True, timeout=900)
        except (OSError, subprocess.SubprocessError):
            return None
        return done.stdout.strip() or None


def introduced_names(diff, repo):
    """Names this story INTRODUCED, with where and how they were declared.

    A name that already existed is not a finding: the pre-existing
    `JarvisEngine.kt` and `CognitiveEngine.kt` both end in an ownership word,
    and a story that merely edits them does not introduce the claim. So a
    candidate counts only when the file is new, or when the name is absent from
    the file's own content at the base commit.
    """
    out = {}
    for cf in diff.files:
        base_text = None if cf.created else (file_at(diff.base, cf.path) or "")
        for lineno, src in sorted(cf.added.items()):
            m = TYPE_DECL.match(src)
            if not m:
                continue
            name = m.group(2)
            if not OWNERSHIP_NAME.search(name):
                continue
            if cf.created or name not in base_text:
                out[name] = (cf.path, lineno, m.group(1))
        stem = os.path.splitext(os.path.basename(cf.path))[0]
        if cf.created and OWNERSHIP_NAME.search(stem):
            out.setdefault(stem, (cf.path, 1, "file"))
    return out


def check_ownership_naming(diff, repo, vision, gate):
    """S13 item 5 -- one component, one permanent job.

    The only genuinely SEMANTIC check: whether a NAME claims a permanent
    cognitive function. The structural part is the free pre-screen (a type name
    this story introduced that ends in an ownership word), and the model is
    asked once about ALL surviving names, never once per name. With no candidate
    the gate is never called, so `model calls: 0` is a real measurement here and
    not an assumption. If the model is unavailable the check reports the
    candidates UNJUDGED rather than guessing: an unverified claim is worse
    than an open one.
    """
    candidates = introduced_names(diff, repo)
    if not candidates:
        return [], gate.calls

    listing = "\n".join(
        "- %s  (%s:%d, declared `class`/`object`/`interface` as `%s`)"
        % (n, p, l, k) for n, (p, l, k) in sorted(candidates.items())
    )
    prompt = (
        "VISION.md Section 4, quoted:\n\n"
        + vision.section4
        + "\n\nA codebase change introduced these type names:\n\n"
        + listing
        + "\n\nFor EACH name, answer on its own line, in the form\n"
          "  <name> | ownership | one sentence\n"
          "where <name> is the name exactly as given, `ownership` is `ownership` "
          "if the name presents that type as permanently OWING a cognitive "
          "function (a fixed job title, a mind, a brain, a permanent identity) "
          "rather than declaring an affordance (what it can affect, at what cost, "
          "on what evidence), and the sentence says which and why. If a name is "
          "neutral, say `neutral`. Answer with those lines and nothing else."
    )
    answer = gate.ask(prompt)
    if answer is None:
        return [Finding(
            check="ownership_naming",
            file=next(iter(candidates.values()))[0],
            line=next(iter(candidates.values()))[1],
            symbol=", ".join(sorted(candidates)),
            detail=(
                "the free pre-screen found %d added name(s) ending in an "
                "ownership word: %s. The semantic judgement (does the name CLAIM "
                "a permanent cognitive job, or declare an affordance?) was NOT "
                "made: no model was available, and this tool does not guess a "
                "meaning-level verdict." % (len(candidates), listing)
            ),
            s13=vision.item["permanent_job"],
            vision_section="4",
            severity="observation",
            evidence=listing,
            why_not="unjudged: the only model-backed check had no model, so the "
                    "names are reported as open, not as deviations",
        )], gate.calls

    out = []
    for line in answer.splitlines():
        parts = [p.strip() for p in line.split("|")]
        if len(parts) < 2 or parts[0] not in candidates:
            continue
        name, verdict = parts[0], parts[1].lower()
        if verdict != "ownership":
            continue
        path, lineno, _kind = candidates[name]
        why = parts[2] if len(parts) > 2 else "(no reason given)"
        out.append(Finding(
            check="ownership_naming",
            file=path,
            line=lineno,
            symbol=name,
            detail=(
                "a story introduced `%s`, and the one model call this tool is "
                "allowed to make read that name as CLAIMING permanent ownership of "
                "a cognitive function: %s. Section 4: a capability declares an "
                "affordance, never ownership; no component whose name is a "
                "permanent cognitive job title." % (name, why)
            ),
            s13=vision.item["permanent_job"],
            vision_section="4",
            severity="deviation",
            evidence="%s (introduced by this story)" % name,
        ))
    return out, gate.calls


def check_meaning_arbiter(diff, repo, vision):
    """Section 4 -- anything shaped like a resolver or arbiter making a
    MEANING-level decision instead of a RESOURCE-level one.

    Structural: an added type whose NAME declares a settling role (Resolver,
    Arbiter, Judge, Router, ...) that declares at least one added function
    returning a MEANING type (a claim, a kind, a layer, a payload) rather than
    a RESOURCE type (a tier, a handle, a budget, a schedule). Section 4 is
    explicit that routing, allocating and settling competing interpretations is
    resource scheduling -- and that a mechanism which changes her SEMANTIC
    behaviour has become the brain, which is a failure of the design.

    The limit of this check is stated in the report rather than hidden: it is a
    name-and-signature screen, so a settling role under a name that declares
    nothing about settling is invisible to it. That gap is why the OPEN list
    exists; it is not a claim that no such construct exists.
    """
    out = []
    for cf in diff.files:
        for lineno, src in sorted(cf.added.items()):
            m = TYPE_DECL.match(src)
            if not m or not ARBITER_NAME.search(m.group(2)):
                continue
            name = m.group(2)
            meaning = []
            for l2, src2 in sorted(cf.added.items()):
                fm = re.match(
                    r"^\s*(?:(?:public|internal|private|override|inline|suspend|operator)\s+)*"
                    r"fun\s+(?:<[^>]+>\s*)?[A-Za-z_][A-Za-z0-9_]*\s*\([^)]*\)\s*:\s*"
                    r"([A-Za-z_][A-Za-z0-9_<>?]*)",
                    src2,
                )
                if not fm:
                    continue
                ret = fm.group(1).rstrip("?")
                if RESOURCE_TYPE.search(ret):
                    continue
                if re.search(r"(Claim|Kind|Layer|Meaning|Interpretation|Intent|"
                             r"Persona|Identity|Payload|Target)", ret) or ret == "String":
                    meaning.append((ret, l2))
            if not meaning:
                continue
            out.append(Finding(
                check="meaning_arbiter",
                file=cf.path,
                line=lineno,
                symbol=name,
                detail=(
                    "a story introduced `%s`, a type whose own name declares the "
                    "role of settling or choosing, and it returns the MEANING type(s) "
                    "%s rather than a resource decision. Section 4: a mechanism that "
                    "routes attention, allocates compute or settles competing "
                    "interpretations is doing resource scheduling; if disabling it "
                    "would change what she thinks, that mechanism has become the "
                    "brain, which is a failure of the design rather than a feature."
                    % (name, ", ".join(sorted({r for r, _ in meaning})))
                ),
                s13=vision.item["permanent_job"],
                vision_section="4",
                severity="deviation",
                evidence=src.strip(),
            ))
    return out


# ---------------------------------------------------------------------------
# AC6 -- auditing the auditor
# ---------------------------------------------------------------------------
#
# Pointing the Kotlin checks at `.ralph/audit.py` returns zero deviations, and
# that zero is WORTHLESS: the checks read `enum class` and `val x: T`, none of
# which Python has, so a zero only means the scanner found no Kotlin. Reporting
# it as a clean audit would be the exact failure this tool exists to prevent --
# a check that cannot see, claiming that it looked and found nothing.
#
# So the self-audit uses a scanner that can actually read Python, and asks the
# same Section 13 questions of the tool's own source. It is a text scan, so its
# own limits are stated in the report.

SELF_AUDIT_LIMITS = [
    "this is a TEXT scan of Python source, not an analysis of it: it sees "
    "literals, comparisons, command names and function names. It does not "
    "resolve imports, call graphs, or reachability, so a fixed dependency "
    "reached indirectly is NOT counted above and the absence of one here is "
    "not proof of its absence",
    "it audits `.ralph/audit.py` only. `audit_check.sh`, `ralph.sh` and "
    "`ci_verify.sh` are not covered by this pass, so nothing here is a claim "
    "about them",
    "the Kotlin checks were NOT run against this file, because a check that "
    "cannot read the language reports its own blindness as a clean result. "
    "Their silence on Python is not evidence, and is not reported as one",
]

PY_LITERAL_SET = re.compile(r"^([A-Z][A-Z0-9_]*)\s*=\s*[\[{]")
PY_LITERAL_END = re.compile(r"[\]}]")
PY_CMP = re.compile(r"([<>=!]=|<|>)\s*(\d+)")
PY_COMMAND = re.compile(r"\[\s*\"(git|bash|rm|cp|mv)\"")
PY_RETURN_TYPE = re.compile(r"->\s*([A-Za-z_][A-Za-z0-9_.\[\], ]*)")


def module_literal_sets(lines):
    """(name, first_line, members) for each module-level `NAME = { ... }`.

    Read across lines, because the real ones here are dictionaries written one
    entry per line; a single-line match would have found none of them and the
    self-audit would have reported this tool as holding no fixed vocabulary.
    """
    out = []
    i = 0
    while i < len(lines):
        m = PY_LITERAL_SET.match(lines[i].strip())
        if not m:
            i += 1
            continue
        buf, depth, j = lines[i], 0, i
        while j < len(lines):
            for ch in lines[j]:
                if ch in "[{":
                    depth += 1
                elif ch in "]}":
                    depth -= 1
            buf += "\n" + lines[j]
            j += 1
            if depth <= 0:
                break
        body = buf[buf.index("[") if "[" in buf else buf.index("{"):]
        members = []
        for tok in re.split(r"[,\n]", body):
            tok = tok.strip().strip('"\'')
            m2 = re.match(r'^"?([A-Za-z_][A-Za-z0-9_]*)"?\s*[:=]', tok) or \
                 re.fullmatch(r'"?([A-Za-z_][A-Za-z0-9_]*)"?', tok)
            if m2:
                members.append(m2.group(1))
        out.append((m.group(1), i + 1, members))
        i = j
    return out


def self_audit(vision):
    """Run the Section 13 questions over this tool's own source, in Python.

    Returns findings. Anything it cannot see is left to the limits paragraph it
    writes into the report rather than being counted as clean.

    A self-audit that listed every `== 0` in a loop counter would be noise
    wearing diligence's clothes, so comparisons are filtered to ones that
    actually decide something: a minimum-membership cut-off or a named
    constant. Index arithmetic and loop termination are counted as seen-and-not-
    drift, in aggregate, rather than line by line.
    """
    path = os.path.join(HERE, "audit.py")
    with open(path, "r", encoding="utf-8") as fh:
        src = fh.read()
    lines = src.splitlines()
    out = []

    # closed_type -- a fixed vocabulary the tool can emit or act on.
    #
    # Reported as an OBSERVATION, never a deviation, and that is the honest
    # call: a module-level table may be a closed classification of the audited
    # subject, or it may be plain configuration -- a list of remediation
    # recipes is data, not a taxonomy of the world. A text scan cannot tell
    # those apart, and calling all of them drift would be the overclaim this
    # tool is supposed to refuse. They go on the open list with the question
    # stated, so a human can settle it.
    for name, lineno, members in module_literal_sets(lines):
        if len(members) < 2:
            continue
        out.append(Finding(
            check="closed_type",
            file=".ralph/audit.py",
            line=lineno,
            symbol=name,
            detail=(
                "this tool defines `%s`, a closed list of %d values [%s] -- the "
                "same shape it flags in production code. The open question is "
                "whether it is the same KIND of thing: a fixed vocabulary the "
                "thing being judged is sorted into, or configuration the tool "
                "happens to be given. `%s` is the vocabulary this tool answers "
                "with; `%s` is data. This scan reads literals and cannot tell "
                "them apart, so it does not pretend to."
                % (name, len(members), ", ".join(members[:8]), name,
                   "a recipe table" if name == "RECIPES" else "the limits table")
            ),
            s13=vision.item["predefined"],
            vision_section="4",
            severity="observation",
            evidence=lines[lineno - 1].strip()[:200],
            why_not="a fixed table in the auditor is not automatically a closed "
                    "classification of the audited system; whether it is one is "
                    "a question for a reader, not something this scan can settle",
        ))

    # flat_threshold -- a number written down once that decides something.
    seen_trivial = 0
    for i, line in enumerate(lines, 1):
        code = re.sub(r"#.*$", "", line).strip()
        if not code or code.startswith(('"', "'", "*", "-")):
            continue
        for op, num in PY_CMP.findall(code):
            if code.startswith(("def ", "class ", "return ", "import ", "@")):
                continue
            if num in ("0", "1"):
                # Index arithmetic and loop termination. Counted, not listed:
                # twenty line-by-line entries would bury the two that matter.
                seen_trivial += 1
                continue
            out.append(Finding(
                check="flat_threshold",
                file=".ralph/audit.py",
                line=i,
                symbol=num,
                detail=(
                    "this tool decides something against the fixed number %s "
                    "(`%s`). A threshold the auditor itself holds still is the "
                    "same pattern it reports in production code: the number was "
                    "written once and never asked what it should scale with."
                    % (num, code[:120])
                ),
                s13=vision.item["predefined"],
                vision_section="4",
                severity="deviation",
                evidence=code[:200],
            ))
            break

    # named_wire -- a fixed, named dependency on an external mechanism.
    cmd_lines = [i for i, l in enumerate(lines, 1) if PY_COMMAND.search(l)]
    if cmd_lines:
        out.append(Finding(
            check="named_wire",
            file=".ralph/audit.py",
            line=cmd_lines[0],
            symbol="git, bash",
            detail=(
                "this tool reaches named external programs by literal command "
                "at %d places (first at line %d). The interaction with git and "
                "the shell is fixed at those call sites and can only be changed "
                "by changing code."
                % (len(cmd_lines), cmd_lines[0])
            ),
            s13=vision.item["named_link"],
            vision_section="4",
            severity="observation",
            evidence=lines[cmd_lines[0] - 1].strip()[:200],
            why_not="the dependency is on the mechanism this tool exists to "
                    "drive -- the same way the shared store is the mechanism for "
                    "production code -- and replacing git with an indirection "
                    "would not make the audit any more honest",
        ))

    # meaning_arbiter -- a name in this tool that claims to settle meaning.
    for i, line in enumerate(lines, 1):
        m = re.match(r"^def\s+([a-z_]*(?:arbiter|judge|decide|settle|verdict)[a-z_]*)", line)
        if m:
            out.append(Finding(
                check="meaning_arbiter",
                file=".ralph/audit.py",
                line=i,
                symbol=m.group(1),
                detail=(
                    "this tool has a function whose own name claims to settle a "
                    "judgement (`%s`). Whether that makes it the brain depends "
                    "on what disabling it would change -- and a text scan cannot "
                    "answer that, so it is recorded open rather than answered."
                    % m.group(1)
                ),
                s13=vision.item["permanent_job"],
                vision_section="4",
                severity="observation",
                evidence=line.strip()[:200],
                why_not="a name is not a reserved cognitive role; the finding is "
                        "left open because this scan cannot resolve it, not "
                        "because it was dismissed",
            ))

    out.sort(key=lambda f: (f.severity != "deviation", f.check, f.line))
    return out


# ---------------------------------------------------------------------------
# Remediation recipes
# ---------------------------------------------------------------------------
#
# A recipe is a list of exact (file, old, new) substitutions. Every `old` must
# occur EXACTLY ONCE in its file or the recipe is DECLINED and the finding goes
# to the open list -- the tool never guesses a patch shape, and never emits a
# diff it has not proved applies. That is why one recipe is shipped instead of
# a general refactoring engine: a recipe it can verify is worth more than a
# recipe it cannot.

SCALE_WITH_HISTORY_NOTE = """\
        // The bar is a function of THIS FIELD's own accumulated history, not a
        // number written down once: a value that has already been accepted N
        // times is a more established value, and replacing it must cost more
        // real evidence and more real time than the last replacement did. Both
        // curves saturate, because a requirement that grows without bound would
        // make durable identity-adjacent state permanently unchangeable -- a
        // worse deviation than a flat one, not a better one.
"""

RECIPES = {
    "evidence-scaled-threshold": {
        "target_check": "flat_threshold",
        "why": (
            "Reconfigure the fixed gate into a function of the field's own "
            "accepted-change history: each additional accepted change to a field "
            "demands one more real claim and one more real interval, both "
            "saturating at a stated ceiling. At zero prior accepted changes the "
            "behaviour is identical to the flat one, so the change is a "
            "reconfiguration of the same gate, not a different gate."
        ),
        "edits": [
            # --- production: derive both bars from the field's real history ---
            ("ContinuityLaw.kt",
             "        val evidenceIds = (priorEvidence.map { it.id } + claim.id).distinct()\n",
             "        val evidenceIds = (priorEvidence.map { it.id } + claim.id).distinct()\n"
             + SCALE_WITH_HISTORY_NOTE
             + "        val acceptedChanges = changeLog.entriesFor(field).size\n"
               "        val requiredClaims = requiredEvidenceClaims(acceptedChanges, minEvidenceClaims)\n"
               "        val requiredInterval = requiredIntervalMs(acceptedChanges, minIntervalMs)\n"),
            ("ContinuityLaw.kt",
             "        if (evidenceIds.size < minEvidenceClaims) {",
             "        if (evidenceIds.size < requiredClaims) {"),
            ("ContinuityLaw.kt",
             '                    "$minEvidenceClaims real claims state',
             '                    "$requiredClaims real claims state'),
            ("ContinuityLaw.kt",
             "        if (lastChangeAt != null && at - lastChangeAt < minIntervalMs) {",
             "        if (lastChangeAt != null && at - lastChangeAt < requiredInterval) {"),
            ("ContinuityLaw.kt",
             '(minimum ${minIntervalMs}ms)',
             '(minimum ${requiredInterval}ms for a field with $acceptedChanges '
             'prior accepted change(s))'),
            ("ContinuityLaw.kt",
             "        const val DEFAULT_MIN_EVIDENCE_CLAIMS: Int = 2\n",
             "        const val DEFAULT_MIN_EVIDENCE_CLAIMS: Int = 2\n"
             "\n"
             "        /**\n"
             "         * Extra real claims each additional accepted change to one field\n"
             "         * demands. The bar moves with the field's own history instead of\n"
             "         * being the same number forever.\n"
             "         */\n"
             "        const val EVIDENCE_STEP: Int = 1\n"
             "\n"
             "        /**\n"
             "         * Ceiling on the evidence bar. A requirement that grew without a\n"
             "         * bound would make durable identity-adjacent state unchangeable after\n"
             "         * enough history, which is a worse deviation than a flat one.\n"
             "         */\n"
             "        const val MAX_EVIDENCE_CLAIMS: Int = 4\n"
             "\n"
             "        /**\n"
             "         * Ceiling on the interval bar, for the same reason.\n"
             "         */\n"
             "        const val MAX_INTERVAL_MS: Long = 60_000L\n"
             "\n"
             "        /**\n"
             "         * Real claims a SLOW change to a field with [acceptedChanges] prior\n"
             "         * accepted changes must be supported by, saturating at\n"
             "         * [MAX_EVIDENCE_CLAIMS]. At zero prior changes this is [base]\n"
             "         * unchanged: the first change to a field costs what it always did.\n"
             "         */\n"
             "        fun requiredEvidenceClaims(acceptedChanges: Int, base: Int = DEFAULT_MIN_EVIDENCE_CLAIMS): Int =\n"
             "            (base + acceptedChanges * EVIDENCE_STEP).coerceAtMost(MAX_EVIDENCE_CLAIMS)\n"
             "\n"
             "        /**\n"
             "         * Real milliseconds a SLOW change to a field with [acceptedChanges]\n"
             "         * prior accepted changes must wait, saturating at [MAX_INTERVAL_MS].\n"
             "         */\n"
             "        fun requiredIntervalMs(acceptedChanges: Int, base: Long = DEFAULT_MIN_INTERVAL_MS): Long =\n"
             "            (base * (acceptedChanges + 1L)).coerceAtMost(MAX_INTERVAL_MS)\n"),
            # --- tests: the four sites that pinned the flat constants ---------
            # (They assert the FLAT behaviour, so resolving the deviation has to
            #  realign them. Each replacement says so in the test's own words.)
            # The FOURTH was not in the ContinuityLaw story's own test file. It
            # sits in the PRODUCTION-WIRING test, where the second change to a
            # field is proposed after a restart; without a third real statement
            # that assertion would still pass, but for want of EVIDENCE rather
            # than the interval it exists to prove survived the restart. A test
            # that passes for the wrong reason proves nothing, so it is
            # realigned too.
            ("ContinuityLawProductionWiringTest.kt",
             '        restored.continuityLaw!!.propose(persona("w-3", "directness", "playful", at + 2))\n'
             '        val tooSoon = restored.continuityLaw!!.propose(persona("w-4", "directness", "playful", at + 3))\n',
             '        restored.continuityLaw!!.propose(persona("w-3", "directness", "playful", at + 2))\n'
             '        restored.continuityLaw!!.propose(persona("w-4", "directness", "playful", at + 3))\n'
             '        // The evidence bar scales with the accepted change this field\n'
             '        // already has, so the interval is once more the only remaining\n'
             '        // reason a refusal could have. Too few real proposals would be\n'
             '        // refused for want of evidence whether or not the interval\n'
             '        // survived the restart -- a test that passes for the wrong reason\n'
             '        // proves nothing.\n'
             '        val tooSoon = restored.continuityLaw!!.propose(persona("w-5", "directness", "playful", at + 4))\n'),
            ("ContinuityLawTest.kt",
             '        law.propose(persona("c-3", "directness", "playful"))\n'
             '        val tooSoon = law.propose(persona("c-4", "directness", "playful"))\n',
             '        law.propose(persona("c-3", "directness", "playful"))\n'
             '        law.propose(persona("c-4", "directness", "playful"))\n'
             '        // A third independent statement: the evidence bar scales with the\n'
             '        // field\'s accepted-change history, so one prior accepted change asks\n'
             '        // for three real claims. With only two, this test would be refused\n'
             '        // for want of EVIDENCE and would never reach the interval it exists\n'
             '        // to exercise.\n'
             '        val tooSoon = law.propose(persona("c-4b", "directness", "playful"))\n'),
            ("ContinuityLawTest.kt",
             "        now += ContinuityLaw.DEFAULT_MIN_INTERVAL_MS\n"
             '        val later = law.propose(persona("c-5", "directness", "playful"))\n',
             "        // The interval scales with the same history: a field that has already\n"
             "        // accepted one change waits two intervals, not one.\n"
             "        now += ContinuityLaw.requiredIntervalMs(1)\n"
             '        val later = law.propose(persona("c-5", "directness", "playful"))\n'),
            ("ContinuityLawTest.kt",
             '        law.propose(persona("c-3", "directness", "playful"))\n'
             '        law.propose(persona("c-4", "directness", "playful"))\n'
             '        val second = log.entriesFor("persona:directness").last()\n',
             '        // Three statements, not two: the evidence bar scales with the one change\n'
             '        // this field has already accepted, so a second change clears three.\n'
             '        law.propose(persona("c-3", "directness", "playful"))\n'
             '        law.propose(persona("c-4", "directness", "playful"))\n'
             '        law.propose(persona("c-5", "directness", "playful"))\n'
             '        val second = log.entriesFor("persona:directness").last()\n'),
            ("ContinuityLawTest.kt",
             "        // TWO real proposals, so the evidence count is satisfied and the interval\n"
             "        // is the only remaining reason a refusal could have. One proposal would\n"
             "        // be refused for want of evidence whether or not the interval survived —\n"
             "        // a test that passes for the wrong reason proves nothing.\n"
             '        restored.propose(persona("c-3", "directness", "playful"))\n'
             '        val refused = restored.propose(persona("c-4", "directness", "playful"))\n',
             "        // A full set of real proposals, so the SCALED evidence count is satisfied\n"
             "        // and the interval is once more the only remaining reason a refusal could\n"
             "        // have. Too few would be refused for want of evidence whether or not the\n"
             "        // interval survived — a test that passes for the wrong reason proves\n"
             "        // nothing.\n"
             '        restored.propose(persona("c-3", "directness", "playful"))\n'
             '        restored.propose(persona("c-4", "directness", "playful"))\n'
             '        val refused = restored.propose(persona("c-5", "directness", "playful"))\n'),
        ],
    },
}


def resolve_source_file(short_name):
    """The production/test path whose basename is `short_name`, newest match."""
    hits = []
    for root in (os.path.join(REPO, SOURCE_PREFIX),
                 os.path.join(REPO, "mobile/app/src/test/java")):
        for dirpath, _d, names in os.walk(root):
            for n in names:
                if n == short_name:
                    hits.append(os.path.relpath(os.path.join(dirpath, n), REPO))
    return sorted(hits)[0] if hits else None


def build_patch(findings):
    """Apply the recipes this run is confident about. Returns (diff, notes)."""
    import difflib

    notes, by_recipe = [], {}
    for f in findings:
        if f.recipe:
            by_recipe.setdefault(f.recipe, []).append(f)
    if not by_recipe:
        return None, ["No finding carried a remediation recipe this tool is "
                      "confident about, so no patch was produced. Every finding is "
                      "on the open list below."]

    files, diff_lines, declined = {}, [], []
    for recipe_id, rs in sorted(by_recipe.items()):
        recipe = RECIPES[recipe_id]
        # A recipe is applied WHOLE or not at all. A partial application is the
        # worst outcome available here: it would rewrite the two gate
        # comparisons to the scaled values while leaving the stale flat reason
        # string behind, producing a diff that does not compile AND a report that
        # claims a patch exists. So the substitutions are staged in a scratch
        # copy, and any anchor that does not match exactly once discards the
        # whole recipe.
        staged, refusals = {}, []
        for short, old, new in recipe["edits"]:
            path = resolve_source_file(short)
            if path is None:
                refusals.append("%s: could not find a file named %s" % (recipe_id, short))
                continue
            if path not in staged:
                with open(os.path.join(REPO, path), "r", encoding="utf-8") as fh:
                    staged[path] = fh.read()
            count = staged[path].count(old)
            if count != 1:
                refusals.append(
                    "%s: the anchor in %s occurs %d time(s), not exactly 1 -- the "
                    "whole recipe is declined rather than guessed at"
                    % (recipe_id, path, count)
                )
                continue
            staged[path] = staged[path].replace(old, new, 1)
        if refusals:
            declined.extend(refusals)
            declined.append(
                "%s: declined in full -- %d of %d substitutions did not match, and "
                "a partial patch is worse than none. Every finding it carried is on "
                "the open list below." % (recipe_id, len(refusals), len(recipe["edits"]))
            )
            continue
        files.update(staged)
        notes.append(
            "%d finding(s) carried the recipe `%s`. Every one of its %d "
            "substitutions matched exactly once, and it was applied to %d file(s) "
            "as a plain diff; see the report for what it changes and why."
            % (sum(len(v) for v in by_recipe.values()),
               ", ".join(sorted(by_recipe)), len(recipe["edits"]), len(staged))
        )

    if not files:
        return None, declined or ["no recipe produced a change"]
    applied = sorted(by_recipe)
    for path in sorted(files):
        with open(os.path.join(REPO, path), "r", encoding="utf-8") as fh:
            before = fh.read()
        if files[path] == before:
            continue
        for line in difflib.unified_diff(
                before.splitlines(True), files[path].splitlines(True),
                fromfile="a/" + path, tofile="b/" + path, n=3):
            diff_lines.append(line)
    if not diff_lines:
        return None, declined or ["no recipe produced a change"]
    notes.insert(0, "Recipe intent: " + RECIPES[applied[0]]["why"])
    notes += declined
    return "".join(diff_lines), notes


# ---------------------------------------------------------------------------
# Running
# ---------------------------------------------------------------------------

def run_git(*args, check=True):
    done = subprocess.run(["git", "-C", REPO] + list(args),
                          capture_output=True, text=True)
    if check and done.returncode != 0:
        raise SystemExit("audit.py: git %s failed: %s" % (" ".join(args), done.stderr.strip()))
    return done


def worktree_git(worktree, *args, check=False):
    """git inside the throwaway worktree.

    The `-c safe.directory=*` override is scoped to this one invocation and is
    required, not optional: the worktree lives under /tmp, which git does not
    consider owned by this user, so without it EVERY call here fails with
    "detected dubious ownership" and the patch branch is never built.
    """
    return subprocess.run(["git", "-C", worktree, "-c", "safe.directory=*"] + list(args),
                          capture_output=True, text=True)


def make_branch(branch, patch_text):
    """Build the draft-patch commit on `branch`, in a throwaway worktree.

    The current working tree and `main` are never touched: the commit is made
    in a detached worktree under a temp dir and only the branch ref moves. If
    the worktree cannot be created the function says so and the caller keeps
    the diff on disk -- an unbuilt patch is a real, reviewable artifact; a
    silently unbuilt one would not be.

    `main` is refused here as well as at the push, so the prohibition does not
    depend on the caller passing a different argument list.
    """
    if not branch or branch == "main" or branch == "master":
        return None, ("refused to build a patch on %r: the patch branch must not "
                      "be main" % branch)
    tmp = tempfile.mkdtemp(prefix="audit-branch-")
    sha = None
    try:
        head = run_git("rev-parse", "HEAD").stdout.strip()
        done = subprocess.run(
            ["git", "-C", REPO, "worktree", "add", "--detach", tmp, head],
            capture_output=True, text=True)
        if done.returncode != 0:
            return None, "worktree add failed: %s" % done.stderr.strip()
        try:
            worktree_git(tmp, "checkout", "-b", branch, check=True)
            pfile = os.path.join(tmp, "audit.patch")
            with open(pfile, "w", encoding="utf-8") as fh:
                fh.write(patch_text)
            applied = worktree_git(tmp, "apply", "--check", "audit.patch")
            if applied.returncode != 0:
                return None, "the generated diff did not apply: %s" % applied.stderr.strip()
            worktree_git(tmp, "apply", "audit.patch", check=True)
            os.remove(pfile)
            worktree_git(tmp, "add", "-A", check=True)
            worktree_git(tmp, "-c", "user.name=ralph-audit",
                         "-c", "user.email=audit@localhost", "commit", "-q", "-m",
                         "audit: draft patch (unmerged) -- VISION.md Section 13 findings",
                         check=True)
            sha = worktree_git(tmp, "rev-parse", "HEAD", check=True).stdout.strip()
        finally:
            # The worktree must be RELEASED before the ref is moved: git refuses
            # "branch -f" on a branch still checked out in a live worktree, and
            # the patch branch is exactly that until this runs.
            subprocess.run(["git", "-C", REPO, "worktree", "remove", "--force", tmp],
                           capture_output=True, text=True)
            subprocess.run(["git", "-C", REPO, "worktree", "prune"], capture_output=True)
        if sha is None:
            return None, "the patch commit was never created in the worktree"
        run_git("branch", "-f", branch, sha)
        return sha, ("branch %s at %s (worktree removed; nothing merged)"
                     % (branch, sha[:10]))
    finally:
        if os.path.isdir(tmp):
            shutil.rmtree(tmp, ignore_errors=True)


def write_artifacts(vision, diff, findings, patch_text, patch_notes, branch_line,
                    args, base, head, range_label, extra_limits=()):
    out_dir = getattr(args, "out_dir", None) or OUT_DIR
    """Render, write and announce the report, patch and sidecar.

    Shared by the normal run and the self-audit so both produce the same
    artifacts in the same place; a second code path that quietly wrote less
    would be a way for a report to look thinner than the run that made it.
    """
    gate_calls = getattr(args, "_model_calls", 0)
    ctx = {
        "vision": vision, "diff": diff, "base": base, "head": head,
        "model_calls": gate_calls,
        "now": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        "range": range_label,
        "extra_limits": list(extra_limits),
    }
    report = render_report(ctx, findings, patch_text, patch_notes, branch_line)

    os.makedirs(out_dir, exist_ok=True)
    stem = (head if head != "HEAD" else run_git("rev-parse", "HEAD").stdout.strip())[:10]
    if not re.fullmatch(r"[0-9a-f]+", stem):
        stem = "self-%d" % int(time.time())
    report_path = args.out or os.path.join(out_dir, "audit_%s.md" % stem)
    with open(report_path, "w", encoding="utf-8") as fh:
        fh.write(report)
    patch_path = None
    if patch_text:
        patch_path = os.path.join(out_dir, "audit_%s.patch" % stem)
        with open(patch_path, "w", encoding="utf-8") as fh:
            fh.write(patch_text)

    # Machine-readable sidecar, so a future iteration can diff two audits
    # without parsing prose. A finding here is a record, not a gate.
    side = {
        "range": range_label,
        "model_calls": gate_calls,
        "deviations": [
            {"check": f.check, "symbol": f.symbol, "file": f.file, "line": f.line,
             "s13": vision.checklist[f.s13], "recipe": f.recipe}
            for f in findings if f.severity == "deviation"
        ],
        "observations": [
            {"check": f.check, "symbol": f.symbol, "file": f.file, "line": f.line,
             "why_not": f.why_not}
            for f in findings if f.severity == "observation"
        ],
        "report": os.path.relpath(report_path, REPO)
                 if report_path.startswith(REPO) else report_path,
        "patch": (os.path.relpath(patch_path, REPO)
                  if patch_path and patch_path.startswith(REPO) else patch_path),
        "branch": args.make_branch,
        "branch_sha": getattr(args, "_branch_sha", None),
        "merged": False,
    }
    with open(os.path.join(out_dir, "audit_%s.json" % stem), "w", encoding="utf-8") as fh:
        json.dump(side, fh, indent=2)

    print("  report   : %s" % (os.path.relpath(report_path, REPO)
          if report_path.startswith(REPO) else report_path))
    print("  deviations: %d   observations: %d   model calls: %d"
          % (len(side["deviations"]), len(side["observations"]), gate_calls))
    for d in side["deviations"]:
        print("    DEVIATION %-22s %s:%d  %s" % (d["check"], d["file"], d["line"], d["symbol"]))
    for d in side["observations"]:
        print("    observed %-22s %s:%d  %s" % (d["check"], d["file"], d["line"], d["symbol"]))
    if patch_path:
        print("  patch    : %s" % (os.path.relpath(patch_path, REPO)
              if patch_path.startswith(REPO) else patch_path))
    if branch_line:
        print("  branch   : %s" % branch_line)
    return side


def render_report(ctx, findings, patch_text, patch_notes, branch_line):
    v = ctx["vision"]
    dev = [f for f in findings if f.severity == "deviation"]
    obs = [f for f in findings if f.severity == "observation"]
    L = []
    a = L.append
    a("# Vision audit — %s" % ctx["range"])
    a("")
    a("Generated %s by `.ralph/audit.py` (a mechanism, per VISION.md Section 9; not "
      "an authority on anything)." % ctx["now"])
    a("")
    a("## What was read")
    a("")
    a("- Checklist: **VISION.md Section 13**, %d questions, parsed out of the file at "
      "run time -- not a copy of it. The quoted text of each question is in every "
      "finding below." % len(v.checklist))
    a("- The rule a finding is measured against: **VISION.md Section 4**.")
    a("- The code: `%s`, the %d changed file(s) between `%s` and `%s`."
      % (SOURCE_PREFIX, len(ctx["diff"].files), ctx["base"], ctx["head"]))
    a("- **Not** read: VISION.md Section 12, which discloses this repo's known "
      "deviations by name. A tool handed the answers proves nothing, so these "
      "findings were reached from the code and the checklist alone. If a finding "
      "below matches a name in Section 12, that is a coincidence the tool is not "
      "allowed to benefit from.")
    a("")
    a("## Headline")
    a("")
    a("| | |")
    a("|---|---|")
    a("| deviations (Section 13 answered Yes) | **%d** |" % len(dev))
    a("| observations (seen, deliberately not called a deviation) | %d |" % len(obs))
    a("| model calls spent | **%d** |" % ctx["model_calls"])
    a("| draft patch produced | %s |" % ("yes" if patch_text else "no"))
    a("| branch built | %s |" % (branch_line or "no"))
    a("| merged | **no** — this tool has no merge path |")
    a("")
    if dev:
        a("## Deviations")
        a("")
        for f in dev:
            a("### %s — `%s`" % (f.check, f.symbol))
            a("")
            a("- **Where:** `%s`" % f.where)
            a("- **Code:** `%s`" % f.evidence)
            a("- **Section 13 question this answers Yes to:** “%s”" % v.checklist[f.s13])
            a("- **Vision section:** %s" % f.vision_section)
            a("- **Why:** %s" % f.detail)
            if f.recipe:
                a("- **Remediation available:** `%s`" % f.recipe)
            a("")
    else:
        a("## Deviations")
        a("")
        a("None. That is a measurement, not a pass: the checks ran, and what they "
          "matched is listed under Observations and Open.")
        a("")
    a("## Observations — seen, and why each was NOT called a deviation")
    a("")
    if obs:
        for f in obs:
            a("- `%s` — `%s:%d` (`%s`): %s **Not a deviation because:** %s"
              % (f.check, f.file, f.line, f.symbol, f.detail, f.why_not))
    else:
        a("- None. The checks matched nothing at all this run.")
    a("")
    a("## Open list — flagged, not resolved by this tool")
    a("")
    open_items = [f for f in findings if not f.recipe or f.severity != "deviation"]
    for f in open_items:
        a("- `%s` `%s` at `%s` — no recipe this tool is confident enough to write."
          % (f.check, f.symbol, f.where))
    if patch_notes:
        for n in patch_notes:
            a("- recipe note: %s" % n)
    if not open_items and not patch_notes:
        a("- Nothing was flagged without a route forward.")
    a("")
    a("## Scope this tool refuses to touch, and why")
    a("")
    for reason in OUT_OF_SCOPE:
        a("- %s" % reason)
    a("")
    if ctx.get("extra_limits"):
        a("## What this run in particular cannot see")
        a("")
        for limit in ctx["extra_limits"]:
            a("- %s" % limit)
        a("")
    a("## What this report does not claim")
    a("")
    a("- It does not claim a finding is *wrong*. Section 13 says a Yes is a "
      "finding, not a verdict: a closed enum can be the right implementation "
      "today. What the tool claims is the narrower, checkable thing — the code "
      "has that shape, at that line.")
    a("- It does not claim a check is complete. Each check's blind spot is stated "
      "where the check is described, and gaps go on the open list above rather "
      "than being papered over.")
    a("- It did not touch `main`, did not merge, and did not close a story.")
    a("")
    return "\n".join(L) + "\n"


def main(argv):
    ap = argparse.ArgumentParser(
        prog="audit.py", description="Audit a story's diff against VISION.md Section 13.")
    ap.add_argument("--base", help="diff base ref (default: HEAD~1)")
    ap.add_argument("--head", default="HEAD", help="diff head ref (default: HEAD)")
    ap.add_argument("--paths", nargs="*", default=None,
                    help="audit these whole files instead of a diff range")
    ap.add_argument("--no-model", action="store_true",
                    help="never call a model; the ownership-naming check reports "
                         "its candidates unjudged rather than guessing")
    ap.add_argument("--model-cmd", default=os.environ.get("AUDIT_MODEL_CMD", ""),
                    help="command that reads the prompt on stdin; one call per run")
    ap.add_argument("--make-branch", metavar="NAME", default=None,
                    help="build the draft patch as a commit on branch NAME (never merged)")
    ap.add_argument("--run-ci", metavar="BRANCH", default=None,
                    help="push the patch branch and run the full suite on CI for it")
    ap.add_argument("--self-audit", action="store_true",
                    help="audit this tool's own source with a Python-aware pass")
    ap.add_argument("--out", default=None, help="report path (default .ralph/audit/<sha>.md)")
    ap.add_argument("--out-dir", default=None,
                    help="directory for report/patch/sidecar (default .ralph/audit)")
    args = ap.parse_args(argv)

    vision = Vision(VISION)

    if args.self_audit:
        findings = self_audit(vision)
        patch_text, patch_notes = None, [
            "No recipe is shipped for the self-audit. Every finding is on the "
            "open list below; nothing about this tool is rewritten by a patch "
            "it generated about itself."]
        branch_line = None
        print("audit.py: self-audit of .ralph/audit.py")
        print("  deviations: %d   observations: %d   model calls: 0"
              % (len([f for f in findings if f.severity == "deviation"]),
                 len([f for f in findings if f.severity == "observation"])))
        for f in findings:
            print("    %-10s %-22s %s:%d  %s"
                  % (f.severity, f.check, f.file, f.line, f.symbol))
        write_artifacts(
            vision, Diff([]), findings, patch_text, patch_notes, branch_line,
            args, base="(self)", head="(self)",
            range_label="self-audit: .ralph/audit.py",
            extra_limits=SELF_AUDIT_LIMITS,
        )
        return 0

    base = args.base or (run_git("rev-parse", "--verify", "--quiet", "HEAD~1").stdout.strip()
                         or "HEAD")
    if args.paths:
        diff = Diff.from_paths(args.paths)
        rng = "files: " + ", ".join(args.paths)
    else:
        diff = Diff.from_range(base, args.head)
        rng = "%s..%s" % (base[:10], args.head[:10])
    if not diff.files:
        print("audit.py: no changed production file in %s -- nothing to audit." % rng)
        return 0

    repo = Repo()
    gate = ModelGate(not args.no_model,
                     args.model_cmd.split() if args.model_cmd else None)

    findings = []
    findings += check_closed_type(diff, repo, vision)
    findings += check_fixed_classification(diff, repo, vision)
    findings += check_flat_threshold(diff, repo, vision)
    findings += check_named_wire(diff, repo, vision)
    own, calls = check_ownership_naming(diff, repo, vision, gate)
    findings += own
    findings += check_meaning_arbiter(diff, repo, vision)
    findings.sort(key=lambda f: (f.severity != "deviation", f.check, f.file, f.line))

    patch_text, patch_notes = build_patch(findings)
    branch_line, branch_sha = None, None
    if patch_text and args.make_branch:
        branch_sha, branch_line = make_branch(args.make_branch, patch_text)
        if branch_sha is None:
            branch_line = "NOT built: %s" % branch_line
            patch_notes.append("the branch was not built, so the diff on disk is "
                               "the only artifact")

    args._model_calls = gate.calls
    args._branch_sha = branch_sha
    write_artifacts(vision, diff, findings, patch_text, patch_notes, branch_line,
                    args, base=base, head=args.head, range_label=rng)

    if args.run_ci:
        if not branch_sha:
            print("audit.py: no branch was built, so there is nothing to run on CI.",
                  file=sys.stderr)
            return 2
        if not args.run_ci.startswith("audit/"):
            print("audit.py: refusing to push to %r: a patch branch must live "
                  "under audit/ so it can never be mistaken for main."
                  % args.run_ci, file=sys.stderr)
            return 2
        print("audit.py: pushing %s (never main) and running the full suite on CI"
              % args.run_ci)
        # Push the BRANCH TIP, not HEAD. HEAD here is whatever branch the caller
        # happens to be on -- `main` -- because the patch commit was made in a
        # throwaway worktree and only the branch ref was moved. Pushing HEAD here
        # would publish main under an audit/** name and leave the real patch
        # unpushed: a green CI run for code nobody reviewed.
        push = subprocess.run(["git", "-C", REPO, "push", "-u", "origin",
                               "refs/heads/%s:refs/heads/%s"
                               % (args.run_ci, args.run_ci)],
                              capture_output=True, text=True)
        if push.returncode != 0:
            print("audit.py: push failed: %s" % push.stderr.strip(), file=sys.stderr)
            return 2
        remote_sha = run_git("rev-parse", "refs/heads/%s" % args.run_ci).stdout.strip()
        if remote_sha != branch_sha:
            print("audit.py: the pushed tip is %s but the patch commit is %s; "
                  "refusing to run CI on a branch that is not the patch."
                  % (remote_sha[:10], branch_sha[:10]), file=sys.stderr)
            return 2
        env = dict(os.environ, CIV_BRANCH=args.run_ci)
        verified = subprocess.run(
            ["bash", os.path.join(HERE, "ci_verify.sh"), "*"],
            env=env, cwd=REPO)
        print("audit.py: CI verdict for %s (%s): exit %d"
              % (args.run_ci, branch_sha[:10], verified.returncode))
        print("audit.py: the branch is NOT merged. Merging is a human action.")
        return verified.returncode

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except SystemExit:
        raise
    except Exception as exc:                      # noqa: BLE001
        print("audit.py: %s: %s" % (type(exc).__name__, exc), file=sys.stderr)
        sys.exit(2)
