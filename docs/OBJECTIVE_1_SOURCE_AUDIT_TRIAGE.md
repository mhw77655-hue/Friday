# Objective 1 — source audit triage

**Evidence run:** [44932395624](https://github.com/mhw77655-hue/Friday/actions/runs/44932395624)  
**Audited source revision:** `e4bd8a670349980d2971eb46ab87f8953a97da62`  
**Status:** inventory produced; findings require human triage; this is not an all-clear.

## 1. What the audit actually established

The whole-repository audit completed successfully as a CI job. It:
- inventoried 785 tracked code/config files and 130,075 lines;
- passed all 475 production Kotlin files through the whole-file vision heuristic;
- syntax-compiled 15 Python files;
- syntax-checked 6 shell scripts;
- produced a source-to-test candidate map for all 475 production Kotlin files.

The job's green status means the audit program ran and emitted its reports. It does **not** mean there were zero findings or that every finding is a confirmed defect.

## 2. Findings, without pretending they are defects

| Signal | Count | Interpretation |
|---|---:|---|
| `named_wire` deviations | 215 | Named constructor/dependency connections flagged for review; ordinary dependency injection is not automatically a defect |
| `fixed_classification` deviations | 28 | Closed classifications flagged; security, lifecycle and protocol enums may correctly need closed sets |
| `flat_threshold` deviations | 19 | Fixed bounds flagged; safety/resource bounds may be justified but should be evidence- and host-aware where possible |
| `meaning_arbiter` deviations | 7 | Components that appear to own a fixed meaning-resolution role; review replaceability and scope |
| **Total heuristic deviations** | **269** | Review signals, not 269 proven bugs |
| Closed-type observations | 266 | Observations, not deviations by themselves |
| Flat-threshold observations | 136 | Observations, not deviations by themselves |
| Ownership-naming observations | 1 | Naming/ownership observation |

The audit used zero model calls. It is a deterministic heuristic, not an architectural proof or runtime test.

## 3. Source-to-test candidate map

| Mapping signal | Production Kotlin files |
|---|---:|
| Same-package test filename match | 115 |
| Lexical symbol reference from a test file | 216 |
| No candidate test file found by this heuristic | 144 |
| **Total scanned** | **475** |

This map is intentionally conservative and lexical. A file marked unmapped may still be exercised indirectly through composition/integration tests; a symbol reference does not prove the code ran. Neither category is a coverage percentage. The exact TSV is in the source-audit artifact attached to run 44932395624.

JaCoCo unit and Android instrumentation coverage collection has since been configured in CI. No coverage percentage is claimed until a successful run produces non-empty reports tied to the exact commit. Native C/C++ and Python require their own coverage evidence; Kotlin JaCoCo does not cover them.

## 4. Required triage policy

Do not mass-rewrite these findings to reduce a counter. For each signal:
1. inspect the exact source and its actual production entry path;
2. decide whether the fixed connection/type/threshold is a legitimate invariant, a replaceable implementation seam, or an architectural limitation;
3. record the rationale and evidence;
4. change only confirmed limitations, preserving security, owner authorization, bounded resource use and truthful state;
5. add a regression test and rerun the relevant adversarial case.

Examples:
- A closed risk-tier or task-state enum can be correct; authorization must not become emergent or ambiguous.
- A constructor-injected Android Context or storage port is not automatically a bad fixed wire. A concrete model/backend dependency in a path that should adapt across hosts may deserve redesign.
- A memory limit can protect the device. It should become resource-aware where the host has meaningful alternatives, not disappear.
- A specialized resolver can be a valid implementation; it becomes a limitation when Friday cannot select another strategy or implementation for a different task.

## 5. Open validation work

- Collect and inspect the newly configured JVM and Android line/branch coverage reports.
- Triage the 269 heuristic signals by subsystem and record justified invariants versus confirmed architectural constraints.
- Review the 144 unmapped candidate files against indirect integration paths and coverage; do not treat the heuristic count as a test failure.
- Add native C/C++ static/sanitizer/fuzz evidence where supported and Python line/branch coverage where useful.
- Keep the adversarial stage open until critical findings are tested, resolved or explicitly dispositioned.
