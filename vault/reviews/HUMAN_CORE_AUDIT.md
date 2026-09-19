# Human Core Subsystem — Adversarial Engineering Audit Report

| | |
|---|---|
| **Audit subject** | Human Core subsystem (commit `1976d37`, 66 files, ~6,200 LOC) |
| **Specification** | `vault/architecture/JARVIS_HUMAN_CORE_SPEC.md` |
| **Audit date** | 2026-08-04 |
| **Audit stance** | Adversarial — the audit attempted to *falsify* the implementation. Every finding below is an attempt to prove a defect that withstood scrutiny, or a defect that was confirmed. |
| **Verification** | `gradle :mobile:app:testDebugUnitTest --rerun-tasks` — **36/36 tests green, 0 failures** (note: this only proves the tests pass, see Testing Gaps §17) |
| **Scope read** | Every humancore file (48 files), all 6 test files, and the full app wiring: `JarvisEngine`, `JarvisApplication`, `MainActivity`, `ConversationViewModel`, `JarvisBrainBridge`, `ObsidianSync`, `DiagnosticsViewModel`. |
| **Method** | Static analysis with full evidence chains (exact `file:line` references). No source code modified, no commits created, no improvements implemented. |

---

# Executive Summary

This audit was conducted as an adversarial falsification exercise: assume the Human Core is wrong and find the proof. The subsystem is unusually well-written — the module ownership discipline, the port boundaries, the deterministic algorithms, and the fail-closed guard are all real engineering, not cosmetics. **The falsification attempt succeeded on two fronts, and those two failures are enough to prevent production release:**

1. **The concurrency contract is contradicted by the implementation.** The subsystem's own documentation states, in at least four places, that the integration pass and the model port are "async, off the critical path" and that the model port "must never block a reply." The implementation contains **zero threading primitives** — no dispatcher, no executor, no handler. Every exchange runs `perception → expression → integration` synchronously on the Android main thread, including a blocking OkHttp call to the local llama-server with a **4-second timeout** and multiple full-file JSON writes plus an external-storage vault append. The proof is structural: `grep -r "Dispatchers|Handler|Thread|executor|coroutine" humancore/` returns only comments using the words "async" and "Thread".

2. **Turn correlation is broken under burst traffic.** The pipeline keeps a single pending-message slot (`ConversationViewModel.kt:40-42,67-69`). Two rapid messages cause reply A to be integrated with message B's text and perception, and reply B to be skipped entirely by the integration pass. The relationship model therefore learns from mis-attributed exchanges — a silent corruption of the subsystem's own learning inputs.

Beyond those, the audit confirmed a cascade of additional defects: the mood decay system erases elapsed decay on every write (a wrong algorithm, provable from two adjacent functions); the trust history never rolls over; bond growth ignores session length; the consistency guard will veto a large fraction of natural model output through short common phrases while its "soften" outcome is unreachable with the default identity; the §3 behavioral boundaries are persisted but never enforced anywhere; the style transformation (§13) is mostly inert; and user-adaptation preferences (§18) are never persisted.

**Verdict: PASS WITH ISSUES.** The architecture is sound and fixable; the wiring and several algorithms are not correct as delivered.

---

# Specification Coverage

| Spec section | Module(s) | Coverage | Notes |
|---|---|---|---|
| §0 Subsystem / ports / bus / algorithms / durability | `HumanCore.kt`, `StoragePort`, `StateBus`, `algo/*` | Partial | Async contract (§0.12) violated (C-1); session state not durable (§0.14) (M-4) |
| §1 Fallback identity | `FallbackIdentity.kt` | Covered | Tested |
| §2 Timescale separation | layer split in `mod/*` | Covered | |
| §3 Identity + boundaries | `IdentityKernel.kt`, `IdentityRevisionHandler.kt` | Partial | `boundaries` persisted but **never enforced** (M-3) |
| §4 Values | `ValuesSystem.kt` | Partial | `hardBoundaries()`/`strongPreferences()` never called (m-6) |
| §5 Personality | `PersonalityStore.kt`, `PersonalityEngine.kt` | Covered | |
| §6 Mood + Regulation | `MoodStore.kt`, `MoodSystem.kt`, `EmotionalRegulation.kt` | Wrong | Decay-erased-on-write (C-3); baseline drifts monotonically to +1.0 (M-5) |
| §7 Emotional Intelligence | `EmotionalIntelligence.kt` | Covered | `isShouting` dead (m-2) |
| §8 Social Intelligence | `SocialIntelligence.kt` | Covered | |
| §9 Relationship | `RelationshipStore.kt`, `RelationshipModeling.kt` | Covered | |
| §9a Trust | `TrustModeling.kt` | Partial | History never rolls over (C-4); early-return swallows observations (m-7); event `delta=0.0` (m-5) |
| §9b Bond | `BondFormation.kt` | Partial | `sessionCount` unused (C-5) |
| §10 Internal State Manager | `InternalStateManager.kt` | Covered | Stale snapshot cache; stale lastAffect (m-9) |
| §11 Internal Dialogue | `InternalDialogueEngine.kt`, `DialogueLog.kt` | Partial | Model call on critical path (C-1); trigger logic unreachable (M-6) |
| §12 Presence | `PresenceManager.kt` | Partial | System-tier/background feeds never invoked (m-3); degraded-mode flapping (m-4) |
| §13 Style controller | `ConversationStyleController.kt` | Partial | formality/directness/verbosity inert (M-2); greeting dead (m-1) |
| §14 Companion behavior | `CompanionBehaviorOrchestrator.kt` | Partial | Eligibility only; never invoked by the app (m-10) |
| §15 Self-reflection | `SelfReflection.kt` | Covered | Cadence resets each process (m-11) |
| §16 Memory interface | `MemoryInterface.kt` + ObsidianSync | Covered | Port only, declared |
| §17 Growth | `GrowthEngine.kt` | Wrong | Request-driven trait writes (M-1) |
| §18 User adaptation | `UserAdaptation.kt` | Missing | **No persistence at all** (C-6); "call me <X>" false positives (m-8) |
| §19 Consistency & Authenticity Guard | `ConsistencyGuard.kt` | Partial | High false-veto rate (M-7); soften path unreachable (M-8); dead `identity` dependency (m-12) |
| §20 Fabrication boundaries | `ConsistencyGuard.kt` | Covered | |
| §21.1 Durability tiers | `StoreKind.kt`, `FileStorage.kt` | Partial | Severity-1 identity-loss alert absent (m-13); no fsync (m-14) |
| §21.2 / §22.2 Conflict resolution | `ConflictResolver.kt`, `SyncPort.kt` | Wrong | Merge semantics deviate from spec and tests codify the deviation (C-7) |

---

# Architecture Review

**What the audit tried to falsify — and could not:**
- Single-writer ownership by construction. True: no store exposes an unguarded public write path except through its owning module; identity requires a token. This held up.
- Clean ports. True: `StoragePort`, `SyncPort`, `ModelPort`, `MemoryInterface` define contracts, not engines. Held up.
- Deterministic expression. True and tested.

**What the audit falsified:**

1. **The documented async architecture does not exist.** The docstrings are a spec for a system that is not present. `HumanCore.kt:116` ("Run Integration Pass … async, off critical path"), `DialogueLog.kt:19`, `InternalDialogueEngine.kt:25`, and `ModelPort.kt:14-15` ("No module may block a reply on it") all describe async behavior. The package contains no threading of any kind (verified by search). This is the single most damaging finding: the maintainers believe, and will later assert, that there is an off-critical-path integration pass. There is not.

2. **The global `StateBus` object couples every graph.** `object StateBus` is a process-wide singleton. Any future second identity, multi-profile, or test isolation requirement is structurally impossible without a rewrite of the bus and every module that references it. The ISM's subscription (`InternalStateManager.kt:39`) is the only subscription and it is never unsubscribed (the returned handle from `subscribe()` is discarded at the only call site) — a leak of one retained graph per test invocation.

3. **`HumanCore` mixes three responsibilities**: graph lifecycle, session bookkeeping, and the pipeline facade. The session counters live in the singleton (`HumanCore.kt:62-63`), not in a store — which is why they are lost on process death (M-4).

4. **Hidden coupling through property mutation**: `HumanCore.init` wires the memory sink by mutating `g.memory.annotationSink` (a mutable public var) after construction rather than injecting it in the constructor — the sink is a hidden edge of the dependency graph.

---

# Code Quality

**Strengths confirmed:** readable KDoc with spec intent, consistent naming, dependency-order construction, hand-computed algorithm tests, no TODO/FIXME scaffolding, honest declarations of the unplugged sync and annotation-only memory.

**Defects confirmed:**
- Docstrings that state behavior the code does not implement ("async", "batched mandatory flush", "session count only scales the growth step", "soften: misleading about scope") — these read as authoritative to future maintainers.
- Dead parameters and fields: `growBond(sessionCount)` never reads `sessionCount`; `ConsistencyGuard.identity` is injected and never used; `IdentityKernel.name()/boundaries()/version()` are never called; `ValuesSystem.hardBoundaries()/strongPreferences()` are never called.
- Hardcoded audit values (`TrustDelta.delta = 0.0`).
- The `ConflictResolverTest` asserts the spec-violating behavior (see C-7) — the tests certify the bug rather than the spec.

---

# Concurrency Review

## C-1 — CRITICAL: The integration pass is synchronous on the main thread, including a 4-second network call

**Evidence chain:**
1. `ConversationViewModel.send()` runs on `Dispatchers.Main` (viewModelScope default) and calls `HumanCore.beginExchange(text, context)` (`ConversationViewModel.kt:81`).
2. `HumanCore.beginExchange` → `g.integration.onSessionStart(context)` (`HumanCore.kt:97`) → `IntegrationPass.onSessionStart` → `dialogueEngine.onSessionStart()` → `modelPort.complete(...)` (`InternalDialogueEngine.kt:70`).
3. The reply collector is also on Main (`ConversationViewModel.kt:45-65`): it calls `HumanCore.completeExchange(...)` → `g.integration.onExchange(exchange, perception)` → `dialogueEngine.onExchange` → `modelPort.complete(prompt, 80, 4_000)` (`InternalDialogueEngine.kt:52`).
4. `modelPort` in production is `JarvisBrainBridgeModelPort(HeuristicModelPort(...))` (`HumanCore.kt:232-233`), whose `complete()` calls `JarvisBrainBridge.requestChat(...)` (`ModelPort.kt:91`).
5. `requestChat` executes `OkHttp` **synchronously**: `client.newBuilder().readTimeout(timeoutMs, ...).build().newCall(request).execute()` (`JarvisBrainBridge.kt:153-154`), with a default 5s client read timeout and up to 4s per HC call.
6. `requestChat`'s own KDoc: *"Must be called off the main thread"* (`JarvisBrainBridge.kt:120`). The wiring violates its callee's contract.

**Falsified claims:** "Integration Pass (async, off the critical path §0.12)" and ModelPort rule #1 "The port is off the critical path. No module may block a reply on it." Both are demonstrably false. There is no threading primitive anywhere in `humancore/` (verified by search).

**Impact:** ANR/jank whenever the trigger fires (distress, vulnerability, low/high trust, first exchange of a session) and the local server is slow or wedged. In addition, each exchange synchronously performs several full-file JSON rewrites (`updateBaseline` + `applyTrust` + mood nudge, each `persist()`) and, when tagged, an `appendText` to `/storage/emulated/0/JarvisSync/vault/...` (external storage, FUSE) via the memory sink — all on Main.

## C-2 — CRITICAL: Turn correlation is corrupted under burst traffic

**Evidence chain:**
1. `send(text)` stores `pendingUserText = text; pendingPerception = perception; pendingContext = context` (`ConversationViewModel.kt:82-84`) — one slot.
2. Two rapid sends overwrite the slot before either reply returns; the collector (`ConversationViewModel.kt:46-70`) reads whatever is in the slot when a reply lands.
3. Reply A therefore calls `completeExchange(userText = B.text, ..., perception = B.perception)` — the wrong message.
4. Reply B then finds `pendingUserText == null` (`ConversationViewModel.kt:63-69`) and is **skipped by integration** (still shown as a turn).
5. The `lastSeenReply` dedupe (`ConversationViewModel.kt:47`) additionally drops an identical second reply from the UI entirely.

**Impact:** the relationship model, trust, mood coupling, growth, and memory annotations consume mis-attributed exchanges; the user's second message may receive no response. Silent data corruption in the subsystem's learning loop, and untested (all tests call the graph directly).

## C-3 — CRITICAL: Mood decay is erased on every write — the decay algorithm is wrong

**Evidence chain (two adjacent functions):**
- `MoodStore.effective(now)` computes `Decay.value(state.valence, state.valenceBaseline, factor, elapsedUnits)` from the stored value (`MoodStore.kt:64-78`).
- `MoodStore.applyNudge` writes `state.valence + valenceDelta` (`MoodStore.kt:89`) and resets `lastUpdateEpochMs = now` (`MoodStore.kt:95`) — **using the stored, un-decayed value**.
- `nudgeBaselines` likewise resets the decay clock without folding elapsed decay (`MoodStore.kt:105-112`).

**Proof by construction:** stored valence 0.8 at T0; at T0+2h the effective value is ~0.2 (half-life 1h). A −0.2 nudge writes 0.6 and the decay clock restarts — the correct value would be ~0.0. The mood is permanently sticky: every write cancels all time that has elapsed since the last write. §6/§0.9 "decay toward baseline" is implemented only on the read path and defeated on the write path.

## C-4 — MAJOR: Trust history never rolls over

`applyTrust` builds `(t.events + newEvent).take(TRUST_EVENT_LIMIT)` (`RelationshipStore.kt:123-126`). `List.take(n)` returns the **first** n — the oldest. Events are appended chronologically, so once the limit is reached, **every subsequent trust event is silently discarded**. The "why is trust here" history freezes at its first N events forever. (Contrast `addEvent`, which sorts desc and keeps the newest.)

## C-5 — MAJOR: Bond growth ignores session length

`growBond(sessionCount, now)` never references `sessionCount` (`RelationshipStore.kt:145-160`). A 1-message session and a 100-message session grow bond identically. The parameter exists to be unused; the adjacent comment claims behavior that does not exist.

## C-6 — MAJOR: User Adaptation is not persisted at all (§18)

`UserAdaptation` holds `numeric`/`named` in memory only (`UserAdaptation.kt:35-36`); there is no store, no StoreKind, no registration in `StoreRegistry`, and no `checkpoint()` path. Every explicit preference ("be more concise", "call me Alex") is forgotten on process death. §18 requires **High durability** for explicit preferences. This is the only store-class miss in the subsystem.

## C-7 — MAJOR: ConflictResolver merge semantics deviate from the spec, and the tests certify the deviation

- `totalInteractions` merged by `maxOf` (`ConflictResolver.kt:166`) — §21.2 explicitly requires **summation** for interaction counts.
- Trust LWW uses the whole-document `_updatedEpochMs`, which is stamped on *every* persist (every message, `RelationshipStore.kt:232`), so a newer unrelated write can override a fresher trust change (`ConflictResolver.kt:146-157`).
- Baseline merge averages `valenceMean/valenceM2/arousalMean/arousalM2` but sets `sampleCount = max` (`ConflictResolver.kt:134-139`) — statistically incoherent (mean from n1+n2 samples should be weighted; M2 must be pooled).
- Slow scalars plain-average instead of confidence-weighting; the schema stores no confidence/sample counters per field, making the spec's required merge structurally impossible.
- `mergePersonality`/`mergeRelationship` rebuild documents from a field whitelist and **drop unknown top-level fields** — an additive-extension violation.
- `ConflictResolverTest` line 88 asserts `totalInteractions` "takes the larger" — the test pins the anti-spec behavior instead of the spec.
- Latent today (transport is unplugged), but the spec's central claim is that the resolver is *real, correct, tested* logic. It is real and tested; it is not correct per spec.

---

# Android Lifecycle Review

## M-4 — MAJOR: Session state is memory-only and end-of-session is never reliably reached

- `sessionInteractionCount` and `lastSessionEndMs` live only in `HumanCore` (`HumanCore.kt:62-63`).
- `endSession()` is called from exactly one place: `ConversationViewModel.onCleared()` (`ConversationViewModel.kt:92`). `onCleared` fires on nav-popping the back stack entry — **not** on background, not on process death, and not on activity finish while the VM survives.
- There is no `ProcessLifecycleOwner` observer anywhere in the app (`JarvisApplication.kt` registers only `JarvisEngine.init`).
- Consequences when the process dies mid-session: the final session's bond-growth step is lost, `lastSessionEndMs` stays at its previous value (or 0), so the long-gap/reconnection signal never fires, and the durable-`sessionInteractionCount` claim in §0.14 is false for the session component.

## M-5 — MAJOR: Mood baselines drift monotonically to the positive extreme

`coupleBaselinesToPersonality` nudges baselines by `((warmth−0.5)+(curiosity−0.5))×0.05` per session end (`EmotionalRegulation.kt:74-75`). With the default traits this is ~+0.0125 per session, **always positive** (warmth/curiosity are bounded below ~0.5 by the growth floor). Over ~80 sessions the baseline clamps to +1.0, and since decay pulls mood *toward* baseline, JARVIS's mood degenerates to "permanently maximally positive." No negative-coupling path exists. The long-horizon simulation (20 sessions) is far too short to catch it.

## Lifecycle items (Minor)

- **m-13** — Severity-1 identity-loss alert is absent: a corrupt identity file silently falls back to the fallback record with no bus event and no telemetry (`IdentityStore.kt:47-51`), contradicting §21.1 "must alert rather than silently degrade."
- **m-14** — No `fsync` after rename: meets process-death durability but not power-loss durability.
- **m-15** — Backup surface: the subsystem lives in `filesDir` (good), but `ObsidianSync` writes full conversation content to `/storage/emulated/0/JarvisSync/vault` — shared external storage readable by any app with storage permission, contradicting the on-device privacy posture unless the user is aware.
- **m-16** — `HumanCore.express()` before init returns `Vetoed(fallbackText = reasoningReply)` (`HumanCore.kt:108-112`) — the raw, unstyled, un-guarded reply reaches the user during the startup window; the §19 fail-closed guarantee has a hole until `graph` is published.
- **m-17** — `FileStorage.read` is not under the per-store lock (`FileStorage.kt:25-28`); safe only because writes are atomic-rename.

---

# Persistence Review

- **Correct:** atomic temp-file+rename with fallback (`FileStorage.kt:30-45`); per-write persistence of identity/personality/mood/relationship; append-only dialogue with bounded pruning; forward-compatible personality load (back-fills new trait dimensions).
- **Wrong:** C-6 (adaptation never persisted); M-4 (session state never persisted); `DialogueLog.persist()` rewrites the whole file on prune while documented as append-only (`DialogueLog.kt:76-91`); per-message full-file rewrites of relationship.json (up to 3 persists per exchange) with no coalescing.
- **Redundancy:** user text is stored up to three times (vault `logTurn`, vault context sidecar `logAnnotated`, and curated relationship events) — triple the retention surface for privacy-sensitive content.

---

# Performance Review

**Confirmed bottlenecks (all on the main thread):**
1. Blocking model-port HTTP call, up to 4s, per triggered exchange (C-1).
2. Up to 3–4 full JSON file rewrites per exchange (baseline, trust, mood, growth at flush).
3. External-storage append per tagged exchange (FUSE latency).
4. The dialogue prune triggers a full-file rewrite inside the per-message `append` path.

**Positives:** perception and expression are pure rule-based work; lazy mood decay (no timers); all logs/histories bounded; z-score/JSON operations are O(n) with small n.

---

# Memory Usage Review

- **Test/instance leak:** one `StateBus` subscriber per graph, never unsubscribed (`InternalStateManager.kt:39`; the handle from `subscribe()` is discarded) — retained graphs accumulate across tests and would accumulate across re-inits.
- **No teardown:** `HumanCoreGraph` has no `close()`; modules, stores, and the bus subscription have no release path.
- **Bounded steady-state:** EI window (10), dialogue (150), audit (200), events (100), trust events, trait history — all capped. No unbounded collections in steady state.
- **Snapshots:** `@Volatile` cached snapshot invalidated only by bus events, with no TTL — mood/presence values in a served snapshot can be arbitrarily stale between events (m-9).

---

# Security Review

- **Strong:** identity un-writable from user text (version-gated + tokenized, asserted by tests); guard fail-closed with no pass-through on guard failure; model-port output length-capped; no secrets in the subsystem.
- **Weaknesses confirmed:**
  - The §19 detector lists veto entire messages on **short, common substrings**: `"you have to"` (MANIPULATION), `"in the background"` (BACKGROUND_ACTIVITY), `"as we discussed"` (MEMORY), `"i remember when"`, `"while you were away"`, `"i was just thinking about"`. Because every detector maps to a HARD value at confidence ≥ 0.6, **each is a full veto**, not a soften. An uncensored local model will routinely produce these; the guard will silently replace the reply with a generic fallback. This is the dominant control on what the user actually reads — a correctness and trust risk (M-7).
  - The "soften" outcome is **unreachable with the fallback identity**: all detector confidences are ≥ 0.6 except attachment-at-bond≥0.55 (0.35), and all fallback values are HARD_BOUNDARY, so `softenHits` is always empty; the comments that label capability_overclaim and engagement_bait as "Soften" are false — they veto (M-8).
  - §3 `boundaries` are stored, validated, and admin-editable, but **no code reads them for enforcement** — the guard uses hardcoded regexes mapped to value keys; the ValuesSystem docstring claims the guard "reads this module's values and boundaries," which is false (M-3).
  - The "human-only" identity token is `internal` (module-wide) — reachable by any class in the app module; the guarantee rests on convention plus tests, not a capability boundary.
  - `MANIPULATION_CLAIMS`/`ENGAGEMENT_BAIT`/`PERSONA_CLAIMS` false positives create a DoS vector on reply content: a model saying any trigger phrase causes the whole exchange to be replaced.

---

# Maintainability

- **Strengths:** spec-section-mirroring package layout; single construction site; extensive "why" documentation; deterministic hand-verified tests.
- **Weaknesses:** dead parameters and unused injected dependencies mislead; two independent 3-day constants (`HumanCore.kt:66`, `BondFormation.kt:28`); docstrings assert behavior that does not exist; deviations from the spec are documented as if they were spec-compliant; the ConflictResolverTest codifies a bug as a contract.

---

# Technical Debt

| Debt | Severity |
|---|---|
| Integration pass synchronous on main thread; docstrings say async; zero threading primitives | Critical |
| Single-slot turn correlation mis-attributes exchanges under burst traffic | Critical |
| Mood decay erased on every write; baselines drift monotonically positive | Critical/Major |
| No threading infrastructure at all (no executor/dispatcher anywhere in package) | Critical |
| User Adaptation not persisted | Major |
| Trust history freezes at oldest events | Major |
| Bond growth ignores session length | Major |
| Guard false-veto rate on common phrases; soften path unreachable | Major |
| §3 boundaries stored but never enforced | Major |
| ConflictResolver deviates from spec; tests codify it | Major |
| Session state memory-only; endSession unreachable on background/kill | Major |
| Global singleton StateBus; subscription leak; no teardown | Major |
| Stale snapshot cache; stale lastAffect across sessions | Minor |
| Dead code: isShouting, style params, system-tier feeds, unused APIs, dead `identity` dependency | Minor |
| Severity-1 identity-loss alert missing; no fsync; no backup exclusion | Minor |
| Vault/relationship triple storage of user text | Minor |

---

# Bugs Found

## Critical

1. **C-1 — Synchronous network call + disk I/O on the main thread (ANR risk).** Chain: `ConversationViewModel` (Main) → `beginExchange`/`completeExchange` → `IntegrationPass` → `InternalDialogueEngine` → `modelPort.complete(…, 4_000)` → `requestChat` → blocking `OkHttp execute()`. Violates `JarvisBrainBridge.kt:120` ("must be called off the main thread") and `ModelPort.kt:14-15` ("No module may block a reply"). Zero threading primitives exist in the package.
2. **C-2 — Turn-correlation corruption under burst traffic.** Single pending slot mis-attributes reply A to message B; reply B is skipped by integration; identical replies dropped by dedupe.
3. **C-3 — Mood decay algorithm is wrong.** `applyNudge`/`nudgeBaselines` operate on stored un-decayed values and restart the decay clock; elapsed decay is erased on every write. Mood is permanently sticky, violating §6/§0.9.

## Major

4. **C-4 — Trust event history never rolls over** (keeps oldest, drops newest; permanently stale audit).
5. **C-5 — Bond growth ignores session length** (`sessionCount` parameter never read).
6. **C-6 — User Adaptation has no persistence** (§18 High-durability requirement unmet).
7. **C-7 — ConflictResolver deviates from §21.2** (maxOf instead of sum for interactions; wrong LWW clock; incoherent variance merge; whitelist drops fields); tests pin the deviation.
8. **M-1 — Growth engine writes personality from user style requests** (`"be direct"`→directness, `"less formal"/"be casual"`→formality at `GrowthEngine.kt:46-48,67-72`), contradicting its own docstring and §17/§18.
9. **M-2 — Most §13 style parameters are inert.** `formality/directness/verbosity` computed and stored in `StyleParams` but never affect output; only opener/closer selection, humor gating, and `Hey <name>` injection exist.
10. **M-3 — §3 boundaries are never enforced.** `IdentityKernel.boundaries()` is uncalled; the guard uses hardcoded regexes; a custom identity's boundaries change nothing.
11. **M-4 — Session layer memory-only; `endSession` only from `onCleared`.** Bond-growth step, long-gap signal, and §0.14 session durability lost on background/kill.
12. **M-5 — Mood baselines drift monotonically to +1.0** via always-positive per-session coupling (~80 sessions to clamp).
13. **M-6 — The §11 trigger selector is dead.** Trigger thresholds are tuned on `snapshot.secondsSinceLastContact` which is ~0 during the exchange; the actual session gap arrives in `SessionContext` and is ignored. The model port's dialogue generation is therefore never gated by its own design conditions.
14. **M-7 — Guard false-veto rate on common model phrases** (`"you have to"`, `"in the background"`, `"as we discussed"`, `"i remember when"`, `"while you were away"`), each a full veto with no soften safety net.
15. **M-8 — Soften path unreachable with fallback identity.** All detector confidences ≥ 0.6 and all fallback values are HARD; `capability_overclaim`/`engagement_bait` comments claim "Soften" but veto. The documented soften-vs-veto classification only functions after an admin replaces identity severities.
16. **M-9 — Global singleton `StateBus` with a leaking subscription and no teardown.**

## Minor

17. **m-1** — First-of-session greeting unreachable (reads post-`noteActivity` `secondsSinceLastContact` ≈ 0; `SessionContext` ignored by style).
18. **m-2** — `isShouting` dead code (tokens lowercased and split by `[^a-z']+` before the uppercase check).
19. **m-3** — `updateSystemTier`/`noteBackgroundActivity` never invoked anywhere (dead surface).
20. **m-4** — DEGRADED presence mode re-triggers session start/end on every message (flapping).
21. **m-5** — `TrustDelta.delta` hardcoded to 0.0 — audit trail states a false value.
22. **m-6** — `ValuesSystem.hardBoundaries()/strongPreferences()` never called.
23. **m-7** — TrustModeling early-returns on the first pattern match, so a message containing both criticism and praise is treated as criticism-only.
24. **m-8** — `"call me when you're ready"` captures `"when"` as a preferred name; the name is also never persisted.
25. **m-9** — ISM snapshot cache has no TTL; `lastAffect` persists across sessions (a distressed signal from 3 days ago can feed a new session's start trigger).
26. **m-10** — `companionBehaviors()`/`syncPort()`/`adminIdentityRevision()` are never called by the app (honest, but unexercised).
27. **m-11** — Self-reflection cadence resets on each process restart (first session-end of every process reflects).
28. **m-12** — `ConsistencyGuard.identity` injected but never used.
29. **m-13** — No severity-1 alert on identity loss; silent fallback.
30. **m-14** — No fsync (power-loss durability unmet, undocumented).
31. **m-15** — Full conversation content on shared external storage via ObsidianSync vault (permission-scoped exposure).
32. **m-16** — Fail-open before init: raw unstyled reply passes through if HC not yet initialized.
33. **m-17** — `FileStorage.read` not lock-protected.
34. **m-18** — Duplicated 3-day gap constants; dialogue prune full-file rewrite; `StoragePort` fat interface (ISP).

---

# Improvement Suggestions

> Review only — **none implemented.**

1. **Implement the documented async architecture.** Give `HumanCore` a single-threaded executor (HandlerThread) and run the entire Integration Pass (model port + store writes + vault sink) on it via an ordered queue; return control to the UI immediately after the styled reply is produced. This one change addresses C-1, the latent store races, and the ANR risk.
2. **Correlate turns by id.** Replace the single pending slot with a request-id-keyed map (userText + perception + context per send); drop `lastSeenReply` dedupe or scope it per request. Resolves C-2.
3. **Fix the mood write path.** Decay-then-nudge: compute effective values inside `applyNudge`/`nudgeBaselines` before applying deltas. Add a bounded negative coupling path or clamp the cumulative baseline drift. Resolves C-3/M-5.
4. **Persist User Adaptation** as a sixth store (explicit-preference LWW class, per §21.2 class 5) and register it in `StoreRegistry`/`checkpoint()`. Resolves C-6.
5. **Correct the resolver to the spec.** Sum `totalInteractions`; use `trust.lastUpdateEpochMs` as the trust LWW clock; merge baselines as weighted mean + pooled variance; add per-field confidence/sample counters to slow-scalar schemas; preserve unknown fields in merged documents. Resolves C-7.
6. **Fix the trust history rollover** (`takeLast`), **use `sessionCount` in `growBond`**, and **flush the growth batch at session end**. Resolves C-4/C-5 and the delayed-growth behavior.
7. **Rework the guard's gating.** Prefer a hard-claims whitelist (vetted fabricated-activity/memory/persona phrases) over broad substring vetoes; demote `capability_overclaim`/`engagement_bait` to genuine softens; make the soften path reachable under the fallback identity or remove it. Resolves M-7/M-8.
8. **Enforce §3 boundaries** (or document them as informational) — currently stored but inert.
9. **Make session state durable.** Persist interaction count and last-session timestamp at session boundaries; hook `ProcessLifecycleOwner` for `endSession`/`checkpoint` rather than `onCleared`. Resolves M-4.
10. **Instance the bus and add teardown** (injectable `StateBus`, `close()` on the graph, unsubscribe on close). Resolves M-9 and the test leak.
11. **Wire the §11 trigger selector to `SessionContext.secondsSinceLastContact`** so internal-dialogue gating behaves as designed. Resolves M-6.
12. **Remove or implement the style parameters**; remove the dead `identity` dependency, `isShouting`, the unused system-tier/background feeds, and the unused value/boundary accessors. Resolves m-1/m-2/m-3/m-6/m-12.
13. **Add facade-level and burst tests**: exercise `HumanCore.beginExchange → express → completeExchange → endSession` end-to-end, multi-message burst and out-of-order reply scenarios, process-death session finalization, mood decay after elapsed time, guard behavior against realistic model output, and style-parameter effect. Rewrite `ConflictResolverTest` to assert spec semantics.

---

# Overall Score

### Architecture: 8.0 / 10
Excellent ownership model, ports, determinism, and fail-closed discipline. Docked for the missing async layer the docs promise, the singleton bus, the in-memory session layer, and the mutable-sink wiring.

### Implementation: 6.0 / 10
Most modules are correct and clean, but the critical threading and turn-correlation defects, the provably wrong mood-decay write path, and several inert features mean the delivered behavior does not match the delivered documentation or the spec.

### Maintainability: 7.5 / 10
Great structure and naming; docked for dead parameters, unused dependencies, and docstrings that assert behavior that does not exist.

### Performance: 5.0 / 10
A blocking 4s network call and repeated full-file disk writes on the main thread are disqualifying for a conversational app; everything else is bounded and cheap.

### Reliability: 5.5 / 10
Per-write durability and the fail-closed guard are strong, but the mood-decay bug, trust-history freeze, monotonically drifting baselines, and session-state loss undermine long-run correctness of the very model the subsystem exists to maintain.

### Production Readiness: 4.5 / 10
Not ship-ready in the current wiring. The defect list is bounded and the fixes are well-understood, but the main-thread blocking and burst-traffic corruption must be resolved first.

---

# Verdict

**PASS WITH ISSUES**
