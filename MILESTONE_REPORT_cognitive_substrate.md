# Milestone: Cognitive State + Working Memory — Build 01A

**Scope:** Cognitive substrate only. No APK build, no voice/vision/cloud/native runtime, no full brain.
**Status:** Compiles clean, 75/75 cognitive unit tests pass, full unit-test suite green.

---

## What was reused

The repo already contained an uncommitted `com.jarvis.app.cognitive` package (from prior work). It was **not** rebuilt from scratch — it was repaired, hardened, and made testable:

- **CognitiveState** — the full structured runtime state (intent, goal, subgoals, active memories, attention, uncertainty, resource/capability, self/user/world models, plan, decision). Kept as-is; fixed a `List<String> = emptyMap()` type break, removed the undeclared `kotlinx-datetime` dependency (replaced `TimeZone` with `java.util.TimeZone`), removed dead imports.
- **WorkingMemory** — bounded activation-based memory. Kept; fixed the missing `minRelevanceThreshold` config, a nested `Stats` referencing the outer constructor `config`, a totalInsertions counter that incremented on every stats refresh, an evictOne return-type break, and a Double/Float mismatch.
- **AttentionEngine** — kept; fixed an infinite recursion (`getAllRanked` ⇄ `getRank`, StackOverflowError), a wrong `kotlinx.coroutines.MutableStateFlow` import, `.average()` Double→Float, and a nullable-chain bug.
- **IntentInference** — kept; fixed error-level deprecated `toLowerCase()` → `lowercase()`, a greeting regex that only matched full strings (`"hi there"` was missed), an empty-word crash in entity extraction, and a question-swallowing-meta-cognitive ordering bug.
- **CognitiveContextBuilder** — kept; added missing imports (`ifNotEmpty`, `MemoryType`). Fixed a real budget bug: `applyTokenBudget` sorted `sortedByDescending { priority.ordinal }`, which placed **LOW-priority sections first and CRITICAL frame last** — it could starve the frame of budget. Now sorts CRITICAL→LOW.
- **CognitiveEngine** — kept; fixed the wrong `MutableStateFlow` import, `receiveAsFlow` imported from the wrong package (`channels` vs `flow`), a missing `MemoryType` import, and `ActiveMemory.MemorySource` → the actual top-level `MemorySource` enum. Wired the previously-dead `CognitiveEvent` emissions (`GoalChanged`, `SubgoalUpdated`, `PlanUpdated`, `DecisionMade`) into the state-transition methods.
- **Reused across subsystems:** `MemoryItem`/`MemoryType`/`MemoryStore` (body), `SessionContext` (humancore protocol), `HumanCore` object as the reserved integration point, the existing `ifNotEmpty` util.

## What was added

1. **`MemoryStorePort`** (body package) — a 1-method retrieval contract. `MemoryStore` implements it; `CognitiveEngine` now depends on the port instead of the Android-coupled `MemoryStore` (which requires a `Context`). This makes the whole cognitive layer pure-JVM testable.
2. **`WorkingMemory.activate(itemId, amount)`** — an explicit *memory activation request* API (raise activation to hold an item for immediate reasoning), distinct from `access()` which implies reading.
3. **`WorkingMemory.decayOnce()`** — deterministic decay for tests/manual cycles.
4. **Event/state transition wiring** — `setGoal`/`addSubgoal`/`updateSubgoal`/`setPlan`/`recordDecision` now emit `CognitiveEvent`s.
5. **Unit tests — 6 files, 75 tests** (`IntentInferenceTest`, `WorkingMemoryTest`, `AttentionEngineTest`, `CognitiveContextBuilderTest`, `CognitiveEngineTest`, `CognitiveStateTest`). All directive-required cases covered, plus the failure cases:
   - intent inference, ambiguous intent, confidence handling
   - WM insertion, activation, decay, eviction, priority, relevance, recency, goal alignment, uncertainty
   - attention ranking
   - context construction + "don't dump all history" cap
   - memory activation request
   - self/user/world model state
   - event/state transitions
   - failure cases: empty input, contradictory input, low confidence, missing memory, overloaded working memory

## What is still missing

- **Working-memory persistence** — WM is intentionally ephemeral/in-memory; nothing yet promotes salient items back into the body `MemoryStore` for long-term storage.
- **Model population** — `SelfModel`/`UserModel`/`WorldModel` exist with defaults but nothing yet fills them from real user/device data.
- **Plan generation** — `Plan`/`DecisionRecord` are passive structures; no planner produces them.
- **Intent semantics** — inference is keyword/heuristic; no model-backed understanding (by design for this build).
- **Runtime wiring** — `CognitiveEngine` is not yet connected into `JarvisEngine`/`MainActivity`/the nervous-system event flow. Deliberately not wired per the build directive.

## What Build 01B should be

**Build 01B: the Goal / Planning subsystem** — a `GoalPlanner` + `DecisionEngine` that *consumes* `CognitiveState` (current intent, goal, subgoals, uncertainty, resource state) to:
- generate a `Plan` (steps with dependencies) from a `Goal`,
- propose/select between `DecisionOption`s and record a `DecisionRecord`,
- promote completed subgoals → update goal status.

It is the single next consumer of this substrate, stays a standalone subsystem (like this build), and can reuse `WorkingMemory` + `AttentionEngine` for plan-step relevance. No APK, no voice, no full brain — same scope discipline as Build 01A.
