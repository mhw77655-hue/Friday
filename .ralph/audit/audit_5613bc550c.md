# Vision audit — d6ef306e1d..5613bc550c

Generated 2026-10-20 22:56 UTC by `.ralph/audit.py` (a mechanism, per VISION.md Section 9; not an authority on anything).

## What was read

- Checklist: **VISION.md Section 13**, 8 questions, parsed out of the file at run time -- not a copy of it. The quoted text of each question is in every finding below.
- The rule a finding is measured against: **VISION.md Section 4**.
- The code: `mobile/app/src/main/java`, the 8 changed file(s) between `d6ef306e1d87d06ce82c9cc20a5da7ee3bcf4f65` and `5613bc550cd3cb762d3453755f7b01f10da3d217`.
- **Not** read: VISION.md Section 12, which discloses this repo's known deviations by name. A tool handed the answers proves nothing, so these findings were reached from the code and the checklist alone. If a finding below matches a name in Section 12, that is a coincidence the tool is not allowed to benefit from.

## Headline

| | |
|---|---|
| deviations (Section 13 answered Yes) | **3** |
| observations (seen, deliberately not called a deviation) | 0 |
| model calls spent | **0** |
| draft patch produced | no |
| branch built | no |
| merged | **no** — this tool has no merge path |

## Deviations

### named_wire — `failureSurface: FailureSurface`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/onefriday/AndroidPlatformPorts.kt:35`
- **Code:** `failureSurface.report(report)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `failureSurface: FailureSurface` (declared at mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/onefriday/AndroidPlatformPorts.kt:114. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `selfModel: SelfModel`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:103`
- **Code:** `val identity = selfModel.identity()`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `selfModel: SelfModel` (declared at mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveState.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:276. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

### named_wire — `modelManager: ModelManager`

- **Where:** `mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:123`
- **Code:** `modelManager.requestChat(messages, maxTokens, timeoutMs)`
- **Section 13 question this answers Yes to:** “Did this create a fixed, named connection between two things where the task requires that interaction to vary?”
- **Vision section:** 4
- **Why:** a story wired one subsystem straight to another by TYPE: it holds `modelManager: ModelManager` (declared at mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt, a concrete class in another file) and calls it directly at mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:255. The interaction between these two is therefore fixed at compile time, and the only way to change it is to change the code. Section 13 asks whether a fixed, named connection was created where the interaction has to vary; the shared claim store is the mechanism that makes it vary.

## Observations — seen, and why each was NOT called a deviation

- None. The checks matched nothing at all this run.

## Open list — flagged, not resolved by this tool

- `named_wire` `failureSurface: FailureSurface` at `mobile/app/src/main/java/com/jarvis/app/onefriday/AndroidPlatformPorts.kt:35` — no recipe this tool is confident enough to write.
- `named_wire` `selfModel: SelfModel` at `mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:103` — no recipe this tool is confident enough to write.
- `named_wire` `modelManager: ModelManager` at `mobile/app/src/main/java/com/jarvis/app/onefriday/TurnPathAssembly.kt:123` — no recipe this tool is confident enough to write.
- recipe note: No finding carried a remediation recipe this tool is confident about, so no patch was produced. Every finding is on the open list below.

## Scope this tool refuses to touch, and why

- the security floor and approval requirements (VISION.md Section 5's one fixed exception: authorization roots, protected operations, self-modification protections)
- identity-adjacent claims and anything CONTINUITY-LAW already gates -- the gate is the mechanism, so flagging the gate's own vocabulary as drift would be flagging the fix; its two disclosed deviations are still reported because they are properties of the CODE, not of the identity it protects
- the non-production tree (tests, resources, build files): this tool audits implementation code, and a test is a claim about code, not code

## What this report does not claim

- It does not claim a finding is *wrong*. Section 13 says a Yes is a finding, not a verdict: a closed enum can be the right implementation today. What the tool claims is the narrower, checkable thing — the code has that shape, at that line.
- It does not claim a check is complete. Each check's blind spot is stated where the check is described, and gaps go on the open list above rather than being papered over.
- It did not touch `main`, did not merge, and did not close a story.

