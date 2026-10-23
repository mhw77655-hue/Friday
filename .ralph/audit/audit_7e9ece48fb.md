# Vision audit — HEAD~1..HEAD

Generated 2026-10-21 00:08 UTC by `.ralph/audit.py` (a mechanism, per VISION.md Section 9; not an authority on anything).

## What was read

- Checklist: **VISION.md Section 13**, 8 questions, parsed out of the file at run time -- not a copy of it. The quoted text of each question is in every finding below.
- The rule a finding is measured against: **VISION.md Section 4**.
- The code: `mobile/app/src/main/java`, the 4 changed file(s) between `HEAD~1` and `HEAD`.
- **Not** read: VISION.md Section 12, which discloses this repo's known deviations by name. A tool handed the answers proves nothing, so these findings were reached from the code and the checklist alone. If a finding below matches a name in Section 12, that is a coincidence the tool is not allowed to benefit from.

## Headline

| | |
|---|---|
| deviations (Section 13 answered Yes) | **1** |
| observations (seen, deliberately not called a deviation) | 0 |
| model calls spent | **0** |
| draft patch produced | no |
| branch built | no |
| merged | **no** — this tool has no merge path |

## Deviations

### closed_type — `Match`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/model/OllamaModelBackend.kt:84`
- **Code:** `enum class Match { EXACT, LATEST_FORM, DEGRADED_TO_REGISTERED, EMPTY_REGISTRY }`
- **Section 13 question this answers Yes to:** “Did this replace "whatever is actually needed right now" with a predefined list of inputs, outputs, types, thresholds, stages, or roles?”
- **Vision section:** 4
- **Why:** a story added a CLOSED set of forms: `enum class Match` with 3 member(s) [EXACT, LATEST_FORM, DEGRADED_TO_REGISTERED]. Section 4: a mechanism exists because it is the best implementation found so far, so a fixed list of the forms it may take reserves the outcome for that implementation.

## Observations — seen, and why each was NOT called a deviation

- None. The checks matched nothing at all this run.

## Open list — flagged, not resolved by this tool

- `closed_type` `Match` at `mobile/app/src/main/java/com/jarvis/app/model/OllamaModelBackend.kt:84` — no recipe this tool is confident enough to write.
- recipe note: No finding carried a remediation recipe this tool is confident about, so no patch was produced. Every finding is on the open list below.

## Scope this tool refuses to touch, and why

- the security floor and approval requirements (VISION.md Section 5's one fixed exception: authorization roots, protected operations, self-modification protections)
- identity-adjacent claims and anything CONTINUITY-LAW already gates -- the gate is the mechanism, so flagging the gate's own vocabulary as drift would be flagging the fix; its two disclosed deviations are still reported because they are properties of the CODE, not of the identity it protects
- the non-production tree (tests, resources, build files): this tool audits implementation code, and a test is a claim about code, not code

## What this report does not claim

- It does not claim a finding is *wrong*. Section 13 says a Yes is a finding, not a verdict: a closed enum can be the right implementation today. What the tool claims is the narrower, checkable thing — the code has that shape, at that line.
- It does not claim a check is complete. Each check's blind spot is stated where the check is described, and gaps go on the open list above rather than being papered over.
- It did not touch `main`, did not merge, and did not close a story.

