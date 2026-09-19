# Milestone: Cognitive Continuity & Memory Lifecycle — Build 01G

**Scope:** Memory continuity subsystem only — promotion/supersession/conflict/provenance/temporal reconstruction + restart persistence + a bounded seam into cognition. No capability implementations, no tools, no voice/vision/cloud/APK/native runtime changes.
**Status:** Main compiles clean; 42 new cognitive-memory tests pass (0 failures). Full module suite: only pre-existing `CapabilityExecutorTest` failures remain (7, in the uncommitted 01F execution subsystem — the executor omits `CapabilityInvocationStarted/Completed` that its own 01E tests require; an additive fix restores those events and resolves 1, the rest are pre-existing 01F regressions, outside this build's scope).

---

## Principle

No silent rewriting of the past. Every memory change is explicit and traceable:

- A true memory → **superseded** (never deleted) with a link to what replaced it.
- Contradictory memories → a **structured conflict** (never a silent winner).
- Every memory carries **provenance** — where it came from, what derived it, current vs. original confidence.
- Cognition asks for a **bounded reconstruction** ("what is true now, relevant to this task") — never a memory-store dump.

## New mechanisms (`com.jarvis.app.cognitive.memory`)

- **`ExperienceRecord`** — structured capture of an experience (source, context, entities, action, result, failures, evidence, confidence, importance, candidate memory type).
- **`MemoryLifecycleState` + `MemoryCandidate` + `PromotionFactors`** — explicit, deterministic lifecycle (CANDIDATE → ACTIVE → CONSOLIDATED → SUPERSEDED/CONFLICTED/ARCHIVED/REJECTED) with a fixed-weight promotion score. No arbitrary tunables.
- **`MemoryConsolidator`** — pipeline `ExperienceRecord → candidate → evaluate → promote/reject/defer → ConsolidatedMemory`. Scores recurrence/importance/relevance/novelty/reliability; detects content conflicts on promotion.
- **`MemoryUpdateEngine`** — supersession without history loss: old memory → SUPERSEDED with `supersededBy`; new memory → CONSOLIDATED with `supersedes`; both keep full provenance; temporal relationship added. Also `updateMemory` (corrections) and `reinforceMemory` (recurrence).
- **`MemoryConflictResolver`** — deterministic resolution policies (highest confidence / last-writer-wins / highest reliability / user instruction / supersession), never fabricates certainty; deferred/unknown are explicit outcomes.
- **`MemoryProvenance` / `DerivationStep` / `TemporalRelationship`** — derivation chain, verification status, source reliability, and explicit temporal edges (BEFORE, DURING, CHANGED_TO, SUPERSEDED_BY, DERIVED_FROM, CONTRADICTS, CAUSED, …).
- **`ContinuityManager`** — bounded reconstruction of "what is true now": active truths, superseded history relevant to the query, unresolved conflicts, unknowns, temporal narratives, overall confidence. Does NOT dump the store.

## Restart continuity (§10) — minimal reuse, no redesign

- New `StoreKind.CONSOLIDATED_MEMORY` (single JSON snapshot document) written through the existing **`StoragePort`** (atomic file writes — the contract survives process death at any moment).
- **`ContinuityPersistence`** — serialize/deserialize the consolidated store (provenance, lifecycle, temporal relationships, metadata). Corrupt/malformed snapshots → structured `FailureReport`, load degrades to empty (never crashes, never poisons unrelated organs).
- `MemoryConsolidator.restoreAll` rebuilds the in-memory type index on restart.
- Human-Core `ConflictResolver` gains one branch for the new kind (LWW on the snapshot clock) — the only cross-subsystem change, and it is additive.
- Verified by `ContinuityRestartTest`: session 1 records preference A, supersedes to B, persists → session 2 loads → reconstruction still returns B as current truth, A as historical, with the supersession relationship and provenance intact.

## Seam into cognition (no runtime wiring)

- **`ContinuityPort`** — the only seam. `CognitiveContextBuilder` (and optionally `CognitiveEngine`) can request a bounded reconstruction for the current task. No ContinuityManager/MemoryConsolidator reference leaks into the engine; a missing port is a no-op (`null`).
- `CognitiveContextBuilder` folds a `continuity` section (active truths / superseded / conflicts / unknowns) into the reasoning context when a port is wired.
- **Explicitly not done:** no wiring into `MainActivity`/`JarvisEngine`, no runtime behavior behind the seam in this build.

## Tests (42, JUnit4 — matching the module's existing style)

| File | Tests | Covers |
|---|---|---|
| ExperienceRecordTest | 2 | defaults, summary |
| MemoryCandidateTest | 7 | promotion scoring, thresholds, decisions |
| MemoryProvenanceTest | 6 | derivation history, verification, temporal relationships, provenance string |
| MemoryConsolidatorTest | 5 | promote/reject/defer, stats, queue limits |
| MemoryUpdateEngineTest | 7 | supersession, not-found, non-current-truth, update, reinforce |
| MemoryConflictResolverTest | 5 | confidence / recency / user-instruction resolution, preference deferral, already-resolved |
| ContinuityManagerTest | 7 | reconstruction, historical context, conflicts, unknowns, summaries, temporal chain, explain |
| ContinuityRestartTest | 3 | survive restart, empty load, malformed snapshot degradation |

## Existing mechanisms reused

- `StoragePort` / `FileStorage` / `StoreKind` — persistence boundary, not redesigned.
- `FailureReport` / `FailureCategory.PERSISTENCE` / `Recoverability.RETRYABLE` — structured failure reporting for corrupt snapshots.
- `MemoryItem` / `MemoryType` / `MemoryStorePort` — existing body-memory types reused as-is.
- `WorkingMemory` — adapter can populate working memory from continuity reconstruction.
- Human-Core `ConflictResolver` — extended additively with one branch.

## Deliberate limitations

- `MemoryConflictResolver` records conflict membership on the involved memories; `getUnresolvedConflicts()` reconstructs descriptors from memory pairs (no separate conflict registry yet).
- Merge resolution is declared but not implemented (explicitly documented in code).
- Persistence snapshot is a full-document rewrite per save — appropriate for the consolidated store's size, not a redesign.
