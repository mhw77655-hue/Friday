# Milestone: Self / User / World Model Subsystem — Build 01H

**Scope:** Structured, provenance-tracked models of Self, User, and World — with deterministic update rules (observe → confirm → supersede → contradict), explicit conflict handling that never silently erases history, staleness/volatility awareness, bounded persistence/load, a bounded `ModelContextPort` seam into cognition, and ConflictResolver LWW coverage for the new `StoreKind`s (`SELF_MODEL`, `USER_MODEL`, `WORLD_MODEL`). No capability implementations, no tools, no voice/vision/cloud/APK/native runtime changes.
**Status:** Main compiles clean; 7 new model-subsystem smoke tests pass (0 failures). Full module suite: only pre-existing `CapabilityExecutorTest` failures remain (7, in the uncommitted 01F execution subsystem — outside this build's scope).

---

## Principle

> **No silent rewriting of the past.**

Every model change is explicit and traceable:

- A true fact → **superseded** (never deleted) with a link to what replaced it.
- Contradictory observations → a **structured conflict** (never a silent winner).
- Every fact carries **provenance** — source, evidence strength, confidence with explicit contradiction count.
- Models know when they are **stale** (expected-change-rate + world-volatility driven).
- Cognition asks for a **bounded projection** ("what is relevant now") — never a model-store dump.

---

## New mechanisms (`com.jarvis.app.cognitive.model`)

### Core types

| Type | Role |
|------|------|
| `ModelDomain` | `SELF` \| `USER` \| `WORLD` |
| `ModelFact` | Evidence-backed fact with status (CONFIRMED/OBSERVED/INFERRED/OUTDATED/CONTRADICTED), confidence (`ModelConfidence`), evidence list, sensitivity, staleness metadata, and history links (`supersedes`, `supersededBy`, `conflictingFactIds`). |
| `ModelConfidence` | `score` + `sourceReliability` + `confirmationState` + `evidenceCount` + `contradictionCount` + `lastValidation`. |
| `ModelUpdate` | Single observation/event carrying `domain`, `factKey`, `value`, `source` (`ModelEvidenceSource`), evidence strength, source reliability, expected change rate, world volatility, sensitivity, observedAt. |
| `UpdateOutcome` | Deterministic classification: `ADDS`, `CONFIRMS`, `SUPERSEDES`, `CONTRADICTS`, `TOO_WEAK`, `OBSOLETE`. |

### Three models

#### SelfModel
- `identity`: canonicalName, capabilities, constraints, meta
- `nature`: personality vector, values hierarchy, expression style, communication preferences, learning profile, limitations
- `activeState`: cognitiveLoad, attentionFocus, currentIntent, activeSubgoals, resourcePressure, capabilityDegraded, sessionContext
- `facts` + `factHistory` (by stable factKey)
- `epistemic`: openQuestions, knownUnknowns, assumptionLedger, validationQueue

#### UserModel
- `profile`: canonicalName, displayName, pronouns, locale, timezone, onboardingCompletedAt
- `relationshipContext`: bondDepth, trustBaseline, interactionStyle, sharedHistoryAnchorIds, sensitiveTopics, interactionCounters, trustEvents
- `preferences` (keyed by stable factKey), `facts` + `factHistory`

#### WorldModel
- `entities`: `WorldEntity` (id, type {PERSON, DEVICE, LOCATION, CONCEPT, SYSTEM}, canonicalName, attributes {typed: Text/Number/Bool/Strings}, confidence, expectedChangeRate, source, sensitivity, staleness, historicalEntityIds)
- `relationships`: `WorldRelationship` (id, sourceEntity, targetEntity, type {OWNS, USES, LOCATED_AT, CONNECTED_TO, PART_OF, DERIVED_FROM, RELATED_TO, UNKNOWN}, confidence, evidence)
- `facts` + `factHistory` (generic non-entity facts)
- `historicalEntityIds`: observed but no longer current

---

## ModelUpdateEngine — deterministic update rules

```
observe(ModelUpdate) → UpdateOutcome
```

Decision tree (policy is pure, inspectable, no hidden weights):

1. **Too weak** — `evidenceStrength < minEvidenceStrength` → `TOO_WEAK`.
2. **Obsolete** — `observedAt < existing.updatedAt` → `OBSOLETE`.
3. **No existing fact** → `ADDS` (new fact, status from source).
4. **Same value** → `CONFIRMS` (boost confidence, increment evidenceCount, maybe promote to CONFIRMED).
5. **Different value** — compute `newScore = 0.6*evidenceStrength + 0.4*sourceReliability`.
   - `newScore >= existing.confidence.score + supersedeDelta` → **SUPERSEDES**  
     Old fact → OUTDATED, moved to history with `supersededBy` link; new fact becomes current candidate.
   - `existingCredible && newScore >= conflictThreshold` → **CONTRADICTS**  
     Current fact stays CONFIRMED/CONTRADICTED (visible `contradictionCount++`); challenger preserved in history with `conflictingFactIds`. No silent overwrite.
   - Otherwise → challenger recorded in history as conflicting alternative, current truth unchanged → `CONTRADICTS` (deferred).

`entityFact` / `relationshipFact` helpers promote world observations into the same fact lattice so supersession/contradiction/staleness logic is uniform.

---

## Staleness sweep (build 01H addition)

```
sweepStale(ModelStore, now) → Int // count marked stale
```

- Per-fact: `isStale(now) = now - lastVerifiedAt > horizon(expectedChangeRate, worldVolatility)`
- Rapid + high volatility = short horizon (minutes); Slow + low volatility = long (days).
- Stale facts get `status=OUTDATED`, `isStale=true`. They remain queryable and in history.

---

## Persistence & restart continuity

`ModelPersistence(StoragePort, onFailure)`:

- `save(self, user, world)`: atomic per-`StoreKind` JSON (atomic-replace contract of `StoragePort`).
- `load()`: independent per-kind; one corrupt kind degrades to `null` with structured `FailureReport`; others load normally.
- `LoadResult` carries per-kind models + failures list.

**Round-trip guarantee** (smoke test verified): facts, history, entities, relationships, staleness metadata, confidence (including contradictionCount) survive process death and reload.

---

## Query & bounded projection (`ModelQuery`, `ModelContextProvider`)

```kotlin
// Per-domain direct access
query.getSelfFact("canonicalName")
query.getUserFact("pref.theme")
query.getWorldFact("network.status")

// Relevance-scored (keyword overlap + confidence + recency)
query.getRelevantUserContext("pizza", limit=5)  // bounded list
query.getRelevantWorldContext("phone", limit=5)

// Context projection seam for cognition
val provider = ModelContextProvider(query, Config(maxUserFacts=5, maxSelfFacts=3, maxWorldFacts=3))
val snapshot = provider.requestModelContext("food", currentGoal="plan lunch", activeEntities=listOf("user"))
// → snapshot.userFacts, snapshot.selfFacts, snapshot.worldFacts (all bounded, relevance-ranked)
```

Cognition never sees the full model store — only the bounded, task-relevant projection.

---

## ModelSynchronization — external input → model updates

```kotlin
syncFromContinuity(store, engine, continuity, queries=...)  // pulls active truths into model
syncFromHumanCore(store, engine, userState, relationshipContext)
syncFromExecution(store, engine, executionResult)
```

Each source maps to an appropriate `ModelEvidenceSource` and calls `engine.observe`. The same deterministic rules apply regardless of origin.

---

## ConflictResolver LWW for new StoreKinds

`ConflictResolver.resolve(kind, local, remote)` now covers:

| StoreKind | Resolution |
|-----------|------------|
| `SELF_MODEL` | LWW on document `updatedAt` (newest snapshot wins) |
| `USER_MODEL` | LWW on `updatedAt` |
| `WORLD_MODEL` | LWW on `updatedAt` |

(Existing kinds unchanged: IDENTITY→version, PERSONALITY→average, MOOD→LWW, RELATIONSHIP→union, ADAPTATION→merge, DIALOGUE/FAILURES→union JSONL.)

---

## Files created / modified (summary)

### New core types & models
- `model/ModelFact.kt` — fact + confidence + status + staleness
- `model/ModelUpdate.kt` — single observation input
- `model/SelfModel.kt` — identity, nature, activeState, facts, history, epistemic
- `model/UserModel.kt` — profile, relationshipContext, preferences, facts, history
- `model/WorldModel.kt` — entities, relationships, facts, history, typed AttributeValue

### Engine & query
- `model/ModelUpdateEngine.kt` — deterministic observe/supersede/contradict/stale-sweep
- `model/ModelStore.kt` — in-memory store with history, current-facts, obsolescence check
- `model/ModelQuery.kt` — relevance-scored lookup, contradiction surfacing
- `model/ModelContextPort.kt` + `ModelContextProvider.kt` — bounded projection seam

### Persistence & sync
- `model/ModelPersistence.kt` — atomic per-kind save/load with failure reporting
- `model/ModelSynchronization.kt` — Continuity/HumanCore/Execution → engine.observe

### Smoke test
- `model/ModelSubsystemSmokeTest.kt` — 7 tests (supersede+history, contradiction+history, staleness+sweep+persist+load, corrupt-degradation, query+projection, entities+relationships, ConflictResolver LWW)

---

## Verification

```bash
./gradlew :mobile:app:compileDebugKotlin   # BUILD SUCCESSFUL
./gradlew :mobile:app:testDebugUnitTest --tests "com.jarvis.app.cognitive.model.ModelSubsystemSmokeTest"
# 7 tests completed, 0 failures
```

---

## What's next (Build 01I+)

- **ModelConflictResolver** — structured conflict resolution policies (merge, user-instruction-wins, highest-reliability, deferred-for-cognition) beyond the engine's `CONTRADICTS` flag.
- **Background consolidation** — periodic sweep that promotes high-confidence OBSERVED/INFERRED → CONFIRMED, decays stale facts, merges duplicates.
- **Capability-driven model updates** — capabilities emit `ModelUpdate`s via `CapabilityExecutionContext`.
- **Richer WorldEntity types** — `SERVICE`, `AGENT`, `DOCUMENT`, `SKILL` with capability bindings.
- **Self-model learning** — reflect on own capability outcomes to update `SelfModel.nature.learningProfile`.

---

## Appendix: key config knobs (all pure, inspectable)

```kotlin
ModelUpdateEngine.Config(
    minEvidenceStrength = 0.15f,
    conflictThreshold = 0.55f,
    supersedeDelta = 0.15f,
    confirmBoost = 0.05f,
    maxConfidence = 0.99f,
    stalenessHorizonMs = 24 * 60 * 60 * 1000
)

ModelContextProvider.Config(
    maxUserFacts = 10,
    maxSelfFacts = 5,
    maxWorldFacts = 5,
    minRelevance = 0.1f
)
```

All thresholds are testable in isolation — no hidden ML, no heuristics that change at runtime.