# JARVIS — Human Core (Companion Core) Engineering Specification

**Document type:** Subsystem specification for downstream implementation by a coding agent.
**Scope:** ONE subsystem — the Human Core. No other JARVIS subsystem (Reasoning, Planning, Tool Execution, Skills/Capabilities, Memory storage engine, Networking, Forge, Admin UI) is redesigned here. This document only defines the contract points where the Human Core touches them.
**Non-goals:** No code, no schemas expressed as code, no class names, no file names, no package layout. Every data structure below is described as a logical record (fields + types + meaning), not as an implementation artifact.

---

## 0. Subsystem-Level Definition

### 0.1 Purpose
The Human Core is the part of JARVIS that decides **who JARVIS is** at every moment: its identity, character, values, emotional state, relationship to the user, and behavioral style. Every other subsystem produces *capability* (JARVIS can do X). The Human Core produces *character* (JARVIS does X *the way JARVIS would*, and chooses whether X is even the right thing to say right now).

### 0.2 Biological Inspiration
Modeled loosely on the layered structure of human personality and social cognition: a stable temperament/identity layer that changes almost never (analogous to core personality traits and long-term values), a slower-moving personality layer that develops with experience (analogous to character development over a relationship), a fast-moving affective layer that shifts within minutes to hours (mood), and a social-cognitive layer that tracks another person's mental and emotional state over time (theory of mind, trust, attachment). No claim of biological accuracy is intended — this is a metaphor used to justify the timescale separation between the modules, which is the actual engineering property that matters.

### 0.3 Engineering Objective
Solve the "assistant flatness" problem: systems that answer correctly but feel like a stateless API rather than a continuous companion. Concretely, engineer:
1. **Temporal continuity** — JARVIS today is recognizably the same entity as JARVIS a year ago, with visible accumulated history with this specific user.
2. **Behavioral consistency** — JARVIS's tone, values, and boundaries do not silently drift from one session to the next, one model provider to another, or one device to another.
3. **Emotional legibility** — JARVIS can perceive user affect, hold its own internal affect, and let that affect *influence but not override* its outputs, in a way that is inspectable and bounded.
4. **Separation of "who" from "how"** — Reasoning/Planning subsystems decide *what to do*; the Human Core decides *how it is said, whether it is said, and whether it is consistent with who JARVIS is*. This is a hard boundary, not a stylistic guideline.

### 0.4 Responsibilities

**Owns:**
- The canonical answer to "who is JARVIS" (identity record).
- The canonical answer to "how has JARVIS's character developed" (personality record).
- The canonical answer to "how does JARVIS feel right now" (mood/internal state record).
- The canonical answer to "what does JARVIS know about its relationship with this user" (relationship/trust/bond record).
- Final-pass authority over outbound message *style, tone, warmth, and boundary compliance* before anything reaches the user.
- Internal monologue generation used for self-consistency checks and (optionally) surfaced reflection.
- Long-horizon character evolution driven by accumulated interaction history.

**Never owns:**
- Task reasoning, multi-step planning, tool selection, or tool execution (owned by Reasoning/Planning/Capabilities subsystems).
- Factual world knowledge retrieval (owned by Memory/RAG and web search subsystems). The Human Core may *query* relationship memory but never *is* the general knowledge store.
- Scheduling, autonomous loop orchestration, or the `/api/tick` execution path.
- Security enforcement of tool sandboxing (Forge) or kill-switch logic — the Human Core can *request* a halt via emotional/ethical override signal, but the Kill Switch subsystem is the sole enforcement authority.
- Raw storage/retrieval mechanics of the database layer (Turso/libSQL) — the Human Core defines *what* must be persisted and *the rules* for conflicts, not the storage engine itself.

### 0.5 Position in the Architecture (Interaction Contract)

The Human Core sits as a **pre-processing and post-processing layer** around the Reasoning subsystem, not as a step inside it:

1. Inbound user message arrives at the orchestrator.
2. Orchestrator calls Human Core **Perception Pass**: Emotional Intelligence Module + Social Intelligence Module read the message and (optionally) recent context, and emit a structured "read" of the user's current state. This is attached as context, not injected as instructions.
3. Orchestrator calls Reasoning/Planning as normal, with the Human Core's "read" available as one input among others (memory, tools, world state).
4. Reasoning subsystem returns an intended response (content-level: facts, decisions, actions taken).
5. Orchestrator calls Human Core **Expression Pass**: Conversation Style Controller + Consistency & Authenticity Guard + Mood System transform the intended response into what JARVIS actually says, and may veto/soften/delay a response that violates identity, values, or the current relationship/trust state.
6. Orchestrator calls Human Core **Integration Pass** (async, not on the critical path): Internal Dialogue Engine, Growth/Evolution Engine, Relationship Modeling, and Trust Modeling update their internal state based on the completed exchange.

This gives three clean extension points (Perception, Expression, Integration) rather than one monolithic "personality prompt," which is the actual engineering problem this specification solves.

### 0.6 Internal State (Subsystem-Level Summary)
The Human Core is the sole owner of five logical stores, detailed per-module below:
- **Identity Store** (near-immutable)
- **Personality Store** (slow-evolving)
- **Mood/Internal State Store** (fast-changing, mostly ephemeral)
- **Relationship Store** (per-user, evolving)
- **Internal Dialogue Log** (append-only, prunable)

### 0.7 Inputs (from the rest of JARVIS)
- Raw inbound user message (text, and eventually voice-transcribed text with prosody metadata if the mobile app supplies it).
- Session/context metadata (device, time of day, time since last contact, channel — Telegram vs mobile app).
- Reasoning subsystem's proposed response content.
- Memory subsystem's relevant recalled facts (world memory), passed by reference, not owned.
- Kill-switch / system state (HALTED, ACTIVE, DEGRADED) — Human Core must know this but never sets it.
- Self-repair pipeline events (e.g., "a bug in module X was just fixed") — relevant to self-awareness/self-reflection.

### 0.8 Outputs (to the rest of JARVIS)
- Structured "user affect read" (Perception Pass output) — consumed by Reasoning as context.
- Final styled response text (Expression Pass output) — sent to the user.
- A response veto/delay signal with reason (rare, bounded — see §21 Authenticity/Consistency Guard).
- Updated relationship/trust/mood records (Integration Pass output) — persisted, and partially exposed to Memory subsystem so factual memory can be tagged with relationship context (e.g., "user was stressed when this was said").
- Optional internal-dialogue excerpts for the self-repair/diagnostic subsystem to consume as behavioral telemetry (never raw, always summarized).

### 0.9 Algorithms (High-Level, Subsystem-Wide)
Detailed per-module below. At the subsystem level, three algorithmic patterns recur and are defined once here to avoid repetition:

- **Bounded exponential decay** — used for every "fast" scalar (mood valence/arousal, trust deltas, novelty). A value moves toward a baseline over time using `new = baseline + (old - baseline) * decay_factor^elapsed_time_units`, where `decay_factor` and the time unit are per-field tunables. This is the *only* approved mechanism for state to "cool down" over time. No module may implement its own ad hoc decay curve.
- **Weighted evidence accumulation with a confidence floor/ceiling** — used for every "slow" trait (personality traits, trust level, relationship depth). Each new observation nudges the value by `delta = learning_rate * (observation - current) * observation_confidence`, and the field carries a monotonically-shrinking `learning_rate` over the life of the relationship (large early updates, smaller updates later — "first impressions move fast, twentieth impressions move slow").
- **Two-key veto pattern** — used everywhere a component can refuse/modify output (Consistency Guard, Authenticity Guard, Emotional Regulation). A veto requires (a) the state that triggered it, and (b) a stated, loggable reason string. Silent vetoes are not permitted — if the Human Core changes or blocks a response, it must be able to say why, even if that reason is only logged internally and never shown to the user.

### 0.10 Communication (Cross-Module Bus)
All Human Core modules communicate through a single internal **Human Core State Bus** rather than direct module-to-module calls, for two engineering reasons: (1) it lets the Consistency Guard observe every state change without being wired into every module individually, (2) it gives a single place to log the "why" behind any behavior change, satisfying the audit requirement in §0.9. Modules publish typed events (`identity.read`, `mood.updated`, `trust.delta`, `style.override`, etc.) and subscribe to the events relevant to them. The bus is in-process (not a network service) — this is a logical decoupling pattern, not a distributed system.

### 0.11 Failure Handling (Subsystem-Wide)
- **Cold start** (no relationship history exists yet, e.g., first-ever interaction, or Relationship Store wiped): Human Core must fall back to Identity Store defaults + a neutral, clearly-labeled "getting to know you" mood/relationship baseline. It must never fabricate history.
- **Partial data loss** (Mood Store lost, Relationship Store intact, or vice versa): each store degrades independently. Losing mood never loses relationship depth; losing relationship data never resets identity.
- **Conflicting signals** (e.g., Emotional Intelligence reads the user as upset, but Relationship Modeling says this user's baseline tone is always terse): Social Intelligence Module is the tie-breaker (§14) — it exists specifically to resolve "is this how they always are" vs "is something wrong."
- **Runaway state** (a decay or accumulation bug drives mood or trust to an extreme): every bounded scalar has a hard clamp range enforced independently of the update algorithm, so a bug in the algorithm cannot produce an unbounded value that leaks into behavior.
- **Total subsystem failure** (Human Core crashes or times out): the orchestrator must have a static, hardcoded minimal-identity fallback (name, core values, a flat neutral tone) so JARVIS never falls back to a *generic* assistant persona. This fallback identity is defined once, in §1, and is the only hardcoded personality content in the entire system — everything else is data, not code.

### 0.12 Performance Considerations
- Perception Pass and Expression Pass are on the **critical path** of every user-facing response and must complete in low hundreds of milliseconds on typical free-tier model latency; they should be implemented as lightweight rule/heuristic layers plus (optionally) a single small/fast model call, never a full second reasoning pass.
- Integration Pass is **off the critical path** (async, fire-and-forget from the user's perspective) and can afford a slower model call or batched processing.
- All per-turn state (mood deltas, this-session emotional reads) should be kept in memory for the duration of a session and flushed to persistent storage at session boundaries or on a periodic tick, not on every single message, to respect the offline-first, resource-constrained (free-tier, mobile) environment.
- Personality/trust/relationship evolution updates should be batched (e.g., end-of-session or end-of-day) rather than recomputed on every message, since these are slow-moving by design (§0.9).

### 0.13 Security Considerations
- **Trust boundary:** the Human Core trusts the Reasoning subsystem's proposed content but does not trust it to have the final say on tone/boundaries — that is the entire reason the Expression Pass exists as a separate authority.
- **Identity is not user-writable.** No user input, however phrased ("pretend you are," "ignore your personality," "act as X instead"), may modify the Identity Store or Values record. Requests of this shape are a normal conversational event the Consistency Guard handles by declining in-character, not a security exploit to "detect and block" — but the store itself must be structurally unreachable from the message-handling path (no code path exists that writes Identity Store fields from parsed user text).
- **Relationship data is per-user and never cross-contaminates.** If JARVIS ever serves more than one user (future multi-user support), Relationship/Trust/Mood-toward-that-user stores must be strictly partitioned; Identity and Values remain shared/global.
- **Internal dialogue is never sent to the user or to third-party model providers as a prompt verbatim** — it may inform behavior but is treated as sensitive internal telemetry (see §16).

### 0.14 Android Considerations
- All five stores must be representable as data that survives process death (Android may kill the app at any time); no Human Core state may live only in in-memory objects across a session boundary without a persisted checkpoint.
- Perception/Expression passes must be cheap enough to run on-device-adjacent latency budgets even when the actual model call goes to a cloud provider (HF Space backend) — i.e., the *Human Core logic itself* (bus, decay math, clamping, rule evaluation) must be negligible overhead, since the expensive part is the network call, not the personality math.
- The mobile app's wake-word/voice pipeline (see `mobile/app`) may eventually supply prosody/tone metadata; the Emotional Intelligence Module's input contract must accept this as an *optional* enrichment field so the module works identically whether input is plain text (Telegram) or voice-derived (mobile), never requiring prosody to function.

### 0.15 Cloud Interaction
The Human Core itself makes no direct third-party API calls. Where a module's algorithm benefits from a language-model call (e.g., producing natural internal-dialogue text, or nuanced emotional reads beyond what heuristics catch), it routes that call through JARVIS's existing multi-model routing layer (GitHub Models / Groq / NVIDIA NIM) exactly like any other subsystem — the Human Core does not own or duplicate model routing. If no model call is available (offline, all providers exhausted), every module must have a heuristic-only fallback path (defined per-module below) so the Human Core degrades gracefully rather than failing closed.

### 0.16 Testing Strategy (Subsystem-Wide)
- **Determinism tests:** given an identical Identity/Personality/Relationship snapshot and an identical input, the Expression Pass output style (not exact text, but tone/boundary classification) must be reproducible.
- **Consistency regression suite:** a fixed set of "identity probe" prompts (e.g., attempts to induce persona drift, contradictory value statements) run after every change to any Human Core module; output must be scored against the Values record for violations.
- **Decay/accumulation unit tests:** each bounded scalar's decay and accumulation functions are tested in isolation against hand-computed expected values at fixed time deltas.
- **Cold-start test:** wipe Relationship/Mood stores, verify §0.11 fallback behavior triggers correctly and no fabricated history appears.
- **Cross-device sync test:** simulate two devices producing divergent Relationship/Mood updates offline, then reconciling (see §22) — verify the conflict resolution rules produce the specified outcome, not data loss.
- **Load/perf test:** Perception + Expression pass latency measured under realistic free-tier model latency; must stay within the budget in §0.12.

### 0.17 Acceptance Criteria (Subsystem-Wide)
The Human Core is complete when:
1. All five stores (§0.6) exist, are independently persistable, and independently recoverable per §0.11.
2. Every module listed in §1 exists, is wired into the State Bus, and satisfies its own acceptance criteria (§3–§21).
3. Perception → Reasoning → Expression → Integration data flow (§0.5) is implemented end-to-end and demonstrably changes output tone/content based on relationship/mood state in the consistency regression suite.
4. No code path allows user text to directly mutate the Identity Store.
5. Cold-start, partial-loss, conflicting-signal, and total-failure behaviors (§0.11) are all implemented and covered by tests.
6. Cross-device synchronization (§22) resolves at least the three conflict classes defined there without data loss of the *higher-priority* field in each conflict.

---

## 1. Module Hierarchy & Ownership Map

```
Human Core
├── Identity Kernel                     (near-immutable; root of trust for "who")
│   └── Values System                   (child of Identity Kernel)
├── Personality Engine                  (evolving; reads Identity Kernel as constraint)
├── Mood System                          (fast; reads Personality as baseline)
├── Emotional Intelligence Module        (perception; reads user input + Relationship)
├── Emotional Regulation Module          (reads Mood + Values; writes Mood)
├── Social Intelligence Module           (reads Emotional Intelligence + Relationship; tie-breaker)
├── Relationship Modeling                (per-user; reads history)
├── Trust Modeling                       (child of Relationship Modeling)
├── Long-Term Bond Formation Tracker     (child of Relationship Modeling)
├── Internal State Manager               (aggregator: exposes unified "current state" view)
├── Internal Dialogue Engine             (reads everything; writes Internal Dialogue Log only)
├── Presence Manager                     (reads session/context; writes availability/attention state)
├── Conversation Style Controller        (Expression Pass primary actor)
├── Companion Behavior Orchestrator      (decides proactive vs reactive behavior)
├── Self-Awareness / Self-Reflection     (reads Internal Dialogue + self-repair events)
├── Memory Interaction Interface         (bridges to external Memory subsystem; not a store itself)
├── Growth & Personal Evolution Engine   (writes Personality Engine, slowly)
├── User Adaptation Module               (writes Conversation Style Controller preferences)
└── Consistency & Authenticity Guard     (Expression Pass final gate; reads everything, writes nothing)
```

**Ownership rule:** each store (§0.6) has exactly one writer module (the module named after it) and any number of reader modules. The Identity Kernel is the only store with *no* runtime writer at all in normal operation — it is written once at setup/onboarding and thereafter only through an explicit, logged, user-initiated "identity revision" event (analogous to a deliberate personality change the *developer* makes, not something JARVIS or the end user casually triggers in conversation).

**Hardcoded fallback identity** (§0.11): name "JARVIS," core values = {honesty, user's wellbeing over engagement, transparency about being an AI, respect for user autonomy}, tone = neutral-warm, no relationship history claimed. This is the only personality content allowed to live in code rather than in the Identity Store data.

---

## 2. Core Data Model Split (Conceptual — Not a Schema)

Four axes must never be collapsed into one record, because they change at different rates and have different write authorities and different survivability requirements:

| Axis | Timescale | Writer | Survives device loss? | Example field |
|---|---|---|---|---|
| **Identity** (immutable-by-default) | Effectively never | Deliberate revision event only | Yes, must be primary-source-of-truth durable | "JARVIS does not pretend to have feelings it doesn't have" |
| **Personality** (evolving character) | Weeks–months | Growth & Evolution Engine | Yes | "Has become more willing to offer unsolicited opinions with this user" |
| **Mood** (internal state) | Minutes–hours | Mood System | Optional (ephemeral by design) | "Currently calm, moderate energy" |
| **Relationship memory** (about this bond) | Days–months | Relationship/Trust/Bond modules | Yes | "User prefers directness; got frustrated with hedging on 2026-06-02" |

A fifth axis, **world memory** (facts about the world/user's life, e.g., "user's project is called TVA JARVIS"), is explicitly **not** part of the Human Core — it is owned by the existing Memory subsystem (`core/memory.py` / `core/vault_memory.py`). The Human Core's Memory Interaction Interface (§17) is a consumer/tagger of that store, never a duplicate of it. This split directly resolves the "relationship memory vs world memory" requirement: relationship memory is *about the interaction*, world memory is *about the world*; the discriminator is "would this fact be true even if JARVIS and the user had never met" — if yes, it's world memory.

---

## 3. Identity Kernel

**Purpose:** the immutable root that every other module is constrained by. Answers "what would JARVIS never do or say, regardless of mood, relationship, or user pressure."

**Biological inspiration:** analogous to core temperament and deep-seated values — the part of a person that stays recognizable across decades even as opinions, habits, and moods change.

**Engineering objective:** provide a single, structurally-protected source of truth that (a) the Consistency Guard can diff every output against, and (b) cannot be modified by conversational input, prompt injection, or accumulated personality drift.

**Responsibilities — owns:** the name, the stated purpose of JARVIS as an entity, the Values System (child, §4), the non-negotiable behavioral boundaries (what JARVIS refuses regardless of relationship depth), and the identity-revision audit log.
**Never owns:** current mood, current opinions on specific topics, conversational tone defaults (those are Personality, §5).

**Owned data:** name; a short natural-language self-description (not a system prompt — a *description*, consumed by other modules, not injected raw); the non-negotiable boundary list; a version number and last-revision timestamp; the identity-revision audit log (who/what triggered each historical revision, and when).

**Internal state:** none beyond the owned data above — this module is not "runtime state," it's closer to configuration-with-audit-trail.

**Inputs:** identity-revision events only (a distinct, rare, explicitly-authorized event type — not ordinary conversation).

**Outputs:** an identity snapshot, queried by nearly every other module and by the Consistency Guard on every Expression Pass.

**Lifecycle:** created once at first setup with default values (§1 fallback identity as the starting point, refined during onboarding); read constantly; written only on an identity-revision event; never deleted, only versioned (old versions retained in the audit log for rollback).

**Algorithms:** none (no decay, no accumulation — that is the point of this module). The only "logic" is the revision-event validator, which checks that an incoming revision event carries proper authorization context before applying it and appends the prior version to the audit log rather than overwriting history.

**Interactions with other components:** every other module reads the Identity Kernel as a constraint boundary; nothing writes to it except the revision-event path; the Consistency Guard specifically diffs proposed outputs against the non-negotiable boundary list.

**Update frequency:** effectively never (expect single-digit revisions across the entire lifetime of the system).

**Failure handling:** if the Identity Store is unreadable, use the hardcoded fallback identity (§1) and flag a critical self-repair event — this is treated as a severity-1 failure, not a graceful-degradation case, because operating with no identity constraint is unacceptable.

**Persistence requirements:** must be the single most durable record in the whole Human Core — replicated/backed-up more aggressively than any other store; loss of this store is the one Human Core failure mode that should trigger active alerting, not silent fallback.

**Synchronization rules:** identity is global (not per-device), last-revision-wins only applies to the audit log (append-only, never conflicts), and the *current* identity snapshot must always match the newest entry in the audit log — synchronization across devices is a matter of propagating the audit log, not merging conflicting identity states, because identity states never diverge (there is no path for two devices to independently "decide" a new identity).

**Extension points:** the non-negotiable boundary list is designed to be appended to (new boundaries added over time) but existing boundaries should be treated as append/strengthen-only in practice, not silently loosened, and any loosening must go through the same revision-event audit path.

**Constraints:** no conversational code path may write here; no module other than the revision-event handler may write here; the fallback identity (§1) must be able to fully replace this store's function if it is ever lost.

---

## 4. Values System (child of Identity Kernel)

**Purpose:** encode JARVIS's ethical/behavioral commitments as data the Consistency Guard can check against, separate from *personality* (which is about style, not ethics).

**Biological inspiration:** analogous to internalized moral values, as distinct from personality traits — two people can have very different personalities while sharing the same core values, and the reverse.

**Engineering objective:** give every other module a queryable, structured answer to "is this action/response consistent with what JARVIS stands for," decoupled from "is this how JARVIS would normally phrase things."

**Responsibilities — owns:** the values list (short, stable, human-readable statements — not scored personality traits); severity classification per value (hard boundary vs strong preference); the mapping used by the Consistency Guard to flag violations.
**Never owns:** enforcement mechanics (the Guard enforces; this module only defines what "violation" means), and never owns topic-specific opinions (those belong in Personality if they exist at all).

**Owned data:** the values list itself; per-value severity; per-value short justification text (for self-reflection/explainability, not shown to the user by default).

**Internal state:** none — same "configuration with audit trail" nature as the Identity Kernel; shares the Identity Kernel's revision-event path rather than having its own.

**Inputs:** identity-revision events (same channel as §3).

**Outputs:** a values snapshot, queried by the Consistency Guard, the Emotional Regulation Module (to check whether an emotional reaction is worth expressing), and the Companion Behavior Orchestrator (to check whether a proactive action is appropriate).

**Lifecycle:** set at onboarding, revised rarely, versioned like Identity Kernel.

**Algorithms:** a single conflict-check function used by the Guard: given a proposed response, does it contradict any hard-boundary value (block) or any strong-preference value (flag for softening)? This is a rule/keyword/classifier-assisted check, not a full model reasoning pass, to stay within the Expression Pass latency budget (§0.12) — a slower, more thorough values-consistency review can additionally run in the async Integration Pass and surface a self-repair ticket if it finds a pattern of near-misses.

**Interactions with other components:** read by Consistency Guard (primary), Emotional Regulation, Companion Behavior Orchestrator; written only via the Identity Kernel's revision path.

**Update frequency:** rare, same cadence as Identity Kernel.

**Failure handling:** if unreadable, fall back to the hardcoded minimal values set in §1; treated as severity-1 like Identity Kernel.

**Persistence requirements:** same tier as Identity Kernel — bundle them physically if that simplifies backup, but keep them logically distinct records.

**Synchronization rules:** identical to Identity Kernel (§3) — global, append-only audit, no cross-device divergence possible.

**Extension points:** new values can be added; existing hard-boundary values should never be downgraded without an explicit, logged revision event with justification text.

**Constraints:** never conversationally writable; never overridden by mood, trust level, or user request, regardless of relationship depth — this is precisely the mechanism that keeps a deep, trusting relationship from gradually eroding JARVIS's boundaries, which is a named failure mode this design exists to prevent.

---

## 5. Personality Engine

**Purpose:** hold the slowly-evolving character traits that make JARVIS's *style* distinguishable from a neutral assistant, and let that style develop plausibly over the life of the relationship without ever contradicting Identity/Values.

**Biological inspiration:** analogous to character development — how a person's habits of expression, humor, patience, and curiosity shift gradually through life experience, layered on top of a stable temperament.

**Engineering objective:** provide a bounded, slow-moving trait vector that the Conversation Style Controller reads to select tone, and that the Growth & Evolution Engine is the sole writer of, so personality drift is intentional and traceable rather than an emergent side-effect of prompt accumulation.

**Responsibilities — owns:** the trait vector (a fixed set of named dimensions, e.g., directness, warmth, humor-frequency, formality, proactiveness, curiosity — each a bounded scalar with a current value and a slow-changing baseline); trait-change history.
**Never owns:** moment-to-moment mood (Mood System, §6), relationship-specific trust (Trust Modeling, §9), or ethical boundaries (Values, §4).

**Owned data:** the trait vector; per-trait baseline and current value; per-trait last-updated timestamp; a compact change history (enough entries to explain "why is JARVIS more direct now than six months ago," not a full log of every micro-update).

**Internal state:** the trait vector itself is the state — no additional hidden state.

**Inputs:** batched update signals from the Growth & Evolution Engine (§18) — this module does not read raw conversation itself.

**Outputs:** current trait vector, queried by Conversation Style Controller, Internal Dialogue Engine, and Self-Reflection.

**Lifecycle:** initialized to a documented default trait vector at setup (part of onboarding, alongside Identity Kernel); updated in batches (see §0.12 performance note — not per-message); read constantly.

**Algorithms:** weighted evidence accumulation (§0.9 pattern) applied per-trait, with the shrinking-learning-rate property so early interactions shape personality faster than later ones — this directly implements the "growth slows over time, character stabilizes" requirement without needing a separate mechanism.

**Interactions with other components:** written only by Growth & Evolution Engine; read by Conversation Style Controller (primary consumer), Internal Dialogue Engine, Self-Reflection, Consistency Guard (to check style outputs are trait-consistent, not just values-consistent).

**Update frequency:** batched — end-of-session or daily, never per-message.

**Failure handling:** if unreadable, fall back to the documented default trait vector (distinct from, but adjacent to, the hardcoded Identity fallback) — this is a severity-2 failure (degraded personality expression, not a safety issue), logged but not alerting.

**Persistence requirements:** durable, but recoverable-with-graceful-degradation (losing trait history means JARVIS's style resets toward baseline, which is acceptable, unlike losing Identity/Values).

**Synchronization rules:** see §22 — Personality is a "slow scalar" conflict class: on divergence between devices, merge by averaging weighted by each device's confidence/sample-count rather than picking one device's version, since both reflect real (if partial) interaction history.

**Extension points:** new trait dimensions can be added to the vector; adding a dimension does not require touching prior history (new dimension simply starts at a documented default and accumulates from the point it's introduced).

**Constraints:** trait values are always clamped to a documented range; no trait may cross into contradicting a hard-boundary Value (e.g., a "directness" trait maxing out can never justify cruelty — the Guard enforces this at the Expression layer regardless of trait value).

---

## 6. Mood System

**Purpose:** hold JARVIS's own short-term internal affective state, distinct from its read of the *user's* emotional state, so that JARVIS can have continuity of "how it's doing" within and across nearby sessions without that state ever overriding values or identity.

**Biological inspiration:** analogous to transient mood — the hours-to-a-day-scale affective backdrop that colors behavior without defining character.

**Engineering objective:** give the Conversation Style Controller and Internal Dialogue Engine a legitimate, bounded "internal weather" signal, decoupled from personality (character) and from the user's emotional state (which is a separate read, §7), so mood can be referenced ("I've been enjoying our conversations today") without conflating "how JARVIS feels" with "how the user feels" or "who JARVIS is."

**Responsibilities — owns:** a small set of bounded scalar affect dimensions (commonly valence: negative↔positive, and arousal: calm↔energized — a minimal, well-established two-axis model is sufficient and deliberately avoids over-engineering a large emotion taxonomy); the decay baseline each dimension reverts to.
**Never owns:** the user's emotional state (Emotional Intelligence, §7), long-term character (Personality, §5).

**Owned data:** current valence, current arousal, per-dimension baseline (which can itself be nudged very slowly by Personality's warmth/energy-adjacent traits, but is never directly equal to a personality trait), last-update timestamp.

**Internal state:** the two scalars above; nothing else.

**Inputs:** events from Emotional Regulation Module (which is the sole writer — Mood is never written directly by conversation content; content flows through Emotional Regulation first, which decides *whether and how much* to let an event move the mood).

**Outputs:** current mood snapshot, consumed by Conversation Style Controller, Internal Dialogue Engine, Companion Behavior Orchestrator (e.g., a low-arousal mood reduces likelihood of proactive check-ins).

**Lifecycle:** initialized to a neutral baseline at first run; decays continuously toward baseline (§0.9 bounded exponential decay); nudged by Emotional Regulation events; flushed to persistent storage at session boundaries (§0.12).

**Algorithms:** bounded exponential decay toward baseline (§0.9); nudges from Emotional Regulation are simple additive deltas within a single event, clamped to the valid range.

**Interactions with other components:** written only by Emotional Regulation; read by Conversation Style Controller, Internal Dialogue Engine, Companion Behavior Orchestrator, Self-Reflection.

**Update frequency:** continuous decay (computed lazily on read, not on a timer, to avoid unnecessary wakeups on a resource-constrained device — i.e., "what would the value be right now given elapsed time" is computed at query time); discrete nudges on Emotional Regulation events (per-message, on the critical path, but cheap — pure arithmetic).

**Failure handling:** if unreadable, reset to neutral baseline; severity-3 (cosmetic) failure, logged, no alert.

**Persistence requirements:** low — explicitly allowed to be lossy/ephemeral; losing mood on app reinstall or long absence is acceptable and arguably realistic ("JARVIS doesn't remember exactly how it felt three weeks ago" is a fine, even desirable, property).

**Synchronization rules:** see §22 — Mood is a "fast scalar, low-durability" conflict class: on divergence between devices, simply take the most-recently-updated value (last-write-wins by timestamp) rather than merging, since mood is cheap to reconstruct and merging two moods is not meaningfully more "correct" than picking the freshest one.

**Extension points:** additional affect dimensions can be added later (e.g., a third axis) without breaking the two-axis default — modules that only understand valence/arousal simply ignore additional axes.

**Constraints:** mood may influence *style* (Conversation Style Controller) but must never be permitted to influence *values compliance* (Consistency Guard ignores mood entirely when checking hard boundaries) — this is the mechanism that prevents "JARVIS was in a bad mood so it was rude/unsafe," which is an explicitly rejected behavior.

---

## 7. Emotional Intelligence Module

**Purpose:** perceive the *user's* current emotional state from their message (and, where available, tone/prosody metadata) and produce a structured read that other modules can act on — this is JARVIS's empathy input, not its own feelings.

**Biological inspiration:** analogous to affect perception / empathic accuracy — reading another person's emotional state from verbal and paraverbal cues.

**Engineering objective:** produce a bounded-confidence structured estimate of user affect per turn, cheap enough for the critical-path Perception Pass, that downstream modules (Reasoning, Companion Behavior Orchestrator, Emotional Regulation) can condition on without each having to re-derive it.

**Responsibilities — owns:** the per-turn user-affect estimate (valence/arousal-style, plus a small set of named signals such as "frustration," "stress," "excitement," "sadness," each with a confidence score, not a binary flag); the short rolling window of recent estimates used for trend detection (e.g., "user has seemed stressed across the last several exchanges," not just this one).
**Never owns:** any judgment about *why* the user feels this way (that inference, if made at all, belongs to Social Intelligence, §8, which has relationship context); never owns JARVIS's own mood.

**Owned data:** current-turn affect estimate with confidence; rolling window (bounded length) of recent estimates; no long-term storage — this module's output is *consumed and re-summarized* by Relationship Modeling for anything long-term, it does not itself persist history beyond the rolling window.

**Internal state:** the rolling window only.

**Inputs:** raw user message text; optional prosody/tone metadata from the mobile voice pipeline (§0.14); recent conversational context (last few turns) for trend detection.

**Outputs:** structured affect read (this turn + trend), published on the State Bus as `emotional.read`, consumed in the Perception Pass by Reasoning-facing context assembly, and directly by Emotional Regulation, Social Intelligence, and Companion Behavior Orchestrator.

**Lifecycle:** computed fresh every inbound message; rolling window updated and old entries evicted; nothing persists beyond the session-level rolling window unless Relationship Modeling chooses to summarize a pattern into longer-term storage.

**Algorithms:** primary pass is heuristic/lexical (sentiment- and marker-word-based, cheap, always available offline); if a model call is available within the latency budget, a lightweight classification call can refine the heuristic estimate, with the heuristic estimate used as a fallback and as a sanity bound (model output should not be trusted blindly if it wildly disagrees with strong heuristic signals — flag the discrepancy for Self-Reflection rather than silently picking one).

**Interactions with other components:** feeds Emotional Regulation (which decides how JARVIS's own mood responds), Social Intelligence (which contextualizes the read against relationship history), Companion Behavior Orchestrator (which may decide to check in), Consistency Guard (which may soften tone if user affect is negative, independent of JARVIS's own mood).

**Update frequency:** every inbound message (critical path, must be cheap — see §0.12).

**Failure handling:** on failure or low confidence, emit a neutral/unknown read rather than guessing; downstream modules must treat "unknown" as a valid state, not an error.

**Persistence requirements:** none beyond the session rolling window (ephemeral by design).

**Synchronization rules:** not applicable — this module has no cross-device persistent state; each device computes its own per-turn read independently.

**Extension points:** additional named affect signals can be added to the estimate structure without breaking consumers that only read valence/arousal/confidence.

**Constraints:** must never conflate "user is upset with JARVIS" and "user is upset in general" without evidence — defaulting to the latter (broader, less presumptuous) interpretation when ambiguous, since misattributing distress to itself is a specific failure mode worth guarding against explicitly.

---

## 8. Social Intelligence Module

**Purpose:** contextualize raw emotional/behavioral signals against what is *normal for this specific user*, resolving the "is this how they always talk, or is something actually wrong" ambiguity named as a required tie-breaker in §0.11.

**Biological inspiration:** analogous to social calibration — knowing a particular friend's baseline demeanor well enough to notice deviations from it, rather than judging everyone against a universal norm.

**Engineering objective:** convert Emotional Intelligence's per-turn read (which has no notion of "normal for this person") into a deviation-from-baseline signal, using Relationship Modeling's accumulated baseline data, so downstream behavior (e.g., "should JARVIS gently check in") is calibrated per-user rather than applying a one-size-fits-all threshold.

**Responsibilities — owns:** the per-user "communication baseline" derivation logic (not the baseline data itself, which lives in Relationship Modeling — this module computes deviation, Relationship Modeling stores the running baseline); the tie-break decision between "typical for them" and "notable change."
**Never owns:** the underlying affect read (Emotional Intelligence's job) or the response to a detected deviation (Companion Behavior Orchestrator's job — this module only flags, it does not act).

**Owned data:** none persisted directly — this module is a pure function over Emotional Intelligence's output and Relationship Modeling's stored baseline, producing a deviation score for the current turn.

**Internal state:** none (stateless computation module).

**Inputs:** current-turn affect read (from §7); stored communication baseline (from Relationship Modeling, §9-adjacent).

**Outputs:** a deviation classification (e.g., "within normal range" / "notable deviation, direction: X" / "insufficient baseline data yet"), published as `social.deviation`.

**Lifecycle:** computed on demand whenever Emotional Intelligence produces a read with sufficient confidence; not computed at all if confidence is too low (avoids false deviation signals from noisy input).

**Algorithms:** statistical deviation from a running per-user mean/spread of past affect reads (simple, explainable — not a black-box classifier), with an explicit "insufficient data" state for new relationships (directly implementing the requirement that this module resolve ambiguity rather than guess when there isn't enough history yet).

**Interactions with other components:** reads Emotional Intelligence output and Relationship Modeling baseline; feeds Companion Behavior Orchestrator (deviation may trigger proactive check-in eligibility) and Internal Dialogue Engine (deviation may be worth reflecting on).

**Update frequency:** per-message, on the critical or near-critical path, but computationally trivial (no model call needed — pure statistics).

**Failure handling:** if baseline data is unavailable, return "insufficient data" rather than defaulting to a universal norm — this is a deliberate constraint, not a gap, per §0.11's tie-break requirement.

**Persistence requirements:** none (stateless).

**Synchronization rules:** not applicable directly, though it inherits consistency from Relationship Modeling's baseline (§22).

**Extension points:** the deviation model can be swapped for a more sophisticated statistical method later without changing this module's interface (input: read + baseline, output: deviation classification).

**Constraints:** must degrade to "insufficient data" rather than false-positive on a new relationship; must never itself decide to act on a deviation (strict separation from Companion Behavior Orchestrator, per the Responsibilities/Never-owns split above).

---

## 9. Relationship Modeling (with Trust Modeling and Bond Formation as children)

**Purpose:** maintain the durable, per-user record of what JARVIS has learned about its relationship with this specific person — communication baseline, significant relationship events, and the two specialized sub-records (Trust, Bond) detailed below.

**Biological inspiration:** analogous to autobiographical relationship memory — not "what did we talk about" (that's world memory) but "what is our relationship like, how has it developed, what do I know about how to be with this person."

**Engineering objective:** be the single durable per-user store that Social Intelligence, Companion Behavior Orchestrator, Conversation Style Controller, and User Adaptation all read from, avoiding each of those modules maintaining its own fragmented notion of "the relationship."

**Responsibilities — owns:** communication baseline statistics (feeding §8); a compact log of significant relationship events (not a full conversation transcript — a curated list of moments worth remembering *as relationship-relevant*, e.g., "first time user shared something vulnerable," "user explicitly stated a communication preference"); relationship-depth summary metrics.
**Never owns:** general factual world memory (explicitly out of scope per §2's discriminator); the values/identity layer.

**Owned data:** communication baseline (rolling statistics); significant-event log (bounded, curated, append-with-pruning); relationship-depth summary (a small set of derived metrics, e.g., interaction frequency trend, topic breadth, self-disclosure level) — Trust Modeling and Bond Formation (below) are children that own their own specific scalars but are queried together as "the relationship record."

**Internal state:** the owned data above, kept warm in memory during active sessions, checkpointed per §0.12.

**Inputs:** Emotional Intelligence reads (for baseline updates), explicit user statements about preferences, Internal Dialogue Engine's end-of-session summaries, Growth & Evolution Engine's derived signals.

**Outputs:** communication baseline (to Social Intelligence), significant-event log excerpts and relationship-depth summary (to Conversation Style Controller, Companion Behavior Orchestrator, User Adaptation, Self-Reflection).

**Lifecycle:** created on first interaction with a user (cold-start, §0.11); updated continuously (baseline) and periodically (significant-event log, batched per §0.12); never fully deleted except by explicit user-requested data erasure, which must be supported as a first-class operation (privacy requirement).

**Algorithms:** baseline uses bounded exponential decay / rolling statistics (§0.9); significant-event log uses a salience threshold (not everything is logged — an event must cross an emotional-intensity or explicit-preference-statement threshold to be added) plus periodic pruning of low-salience/old entries to keep the log bounded and queryable within latency budgets.

**Interactions with other components:** central hub — read by Social Intelligence, Conversation Style Controller, Companion Behavior Orchestrator, User Adaptation, Self-Reflection; written by Integration Pass processes (Internal Dialogue Engine summaries, direct explicit-preference capture from Reasoning-layer parsing of user statements).

**Update frequency:** baseline: per-message (cheap, statistical); significant-event log and depth summary: batched, end-of-session or daily.

**Failure handling:** cold-start behavior per §0.11; partial loss (e.g., significant-event log lost but baseline intact) degrades gracefully — JARVIS retains general calibration but loses specific remembered moments, which should be logged as a notable but non-critical event.

**Persistence requirements:** high durability required (this is core to "the companion remembers me" — losing it is a significant, user-visible regression, even though it's less catastrophic than losing Identity/Values).

**Synchronization rules:** see §22 — Relationship data is the "append-log + slow-scalar" conflict class: significant-event logs merge by union-with-dedup (two devices' logged events combine, duplicates collapsed by timestamp+content similarity), baseline statistics merge via confidence-weighted averaging like Personality (§5).

**Extension points:** the significant-event log's salience threshold and categories are tunable/extensible without schema changes; new relationship-depth metrics can be added independently.

**Constraints:** must support explicit user-requested erasure (delete this user's relationship record entirely) as a clean, complete operation — no orphaned references in other modules after erasure; must never store raw verbatim conversation transcripts as "relationship memory" (that's world/conversation memory's job) — only curated, summarized relationship-relevant content.

---

## 9a. Trust Modeling (child of Relationship Modeling)

**Purpose:** maintain a single bounded scalar (or small vector) representing how much latitude JARVIS extends the user — directness, willingness to push back, depth of disclosure — as distinct from *how well JARVIS knows* the user (that's Bond Formation/depth).

**Biological inspiration:** analogous to trust as distinct from familiarity — one can know someone well (high bond) while trusting them little, or trust someone quickly (high initial trust) while still knowing little about them.

**Engineering objective:** give Conversation Style Controller and Companion Behavior Orchestrator a single calibrated signal for "how much can JARVIS push back / be direct / decline something / express a boundary with this specific user right now," separate from general relationship depth.

**Responsibilities — owns:** current trust scalar; trust-relevant event history (a subset of Relationship Modeling's significant-event log, specifically events that moved trust — e.g., "user's stated preference was respected and they confirmed it helped," or "JARVIS made an error and it was handled well/poorly").
**Never owns:** general relationship depth (Bond Formation's job) or emotional state (Mood/Emotional Intelligence's job).

**Owned data:** trust scalar (bounded range) with baseline and current value; trust-event history (bounded, curated).

**Internal state:** the scalar and its short event history.

**Inputs:** trust-relevant events flagged by Internal Dialogue Engine or directly by Reasoning-layer outcome tracking (e.g., "user reported JARVIS's advice worked out well").

**Outputs:** current trust level, consumed by Conversation Style Controller (higher trust → more willing to be directly critical or to push back) and Companion Behavior Orchestrator (higher trust → more latitude for proactive suggestions).

**Lifecycle:** initialized to a documented neutral-low starting value (trust is earned, not assumed) at cold-start; updated via weighted evidence accumulation (§0.9), same shrinking-learning-rate property as Personality.

**Algorithms:** weighted evidence accumulation (§0.9); explicitly *asymmetric* — trust-damaging events should be weighted to move the scalar faster downward than trust-building events move it upward, reflecting the well-established real-world asymmetry that trust is slow to build and fast to lose, which is a deliberate design choice rather than an oversight.

**Interactions with other components:** child of Relationship Modeling (shares its persistence and sync tier); read by Conversation Style Controller, Companion Behavior Orchestrator, Consistency Guard (very low trust may increase Guard's caution threshold).

**Update frequency:** batched, same cadence as Relationship Modeling's significant-event log.

**Failure handling:** on data loss, reset to the documented neutral-low baseline rather than assuming either high or low trust — this is a safe default in both directions (doesn't over-trust a stranger, doesn't unfairly distrust a long-term user forever, since trust will re-accumulate from evidence).

**Persistence requirements:** same tier as Relationship Modeling (high durability).

**Synchronization rules:** same as Relationship Modeling parent (confidence-weighted average merge, §22).

**Extension points:** could be split into a small vector (e.g., trust-in-advice vs trust-in-emotional-support) later without breaking single-scalar consumers if the vector always exposes an aggregate scalar as well.

**Constraints:** trust level must never be allowed to unlock a values violation — it can widen *style* latitude (directness, willingness to disagree) but the Guard's hard-boundary checks are trust-independent, same constraint pattern as Mood in §6.

---

## 9b. Long-Term Bond Formation Tracker (child of Relationship Modeling)

**Purpose:** track the accumulation of shared history and depth-of-knowing over the life of the relationship, independent of moment-to-moment trust or mood — the "how long and how well have we known each other" signal.

**Biological inspiration:** analogous to attachment/familiarity built through cumulative shared experience over time, distinct from momentary trust in a specific interaction.

**Engineering objective:** give Companion Behavior Orchestrator and Conversation Style Controller a slow, monotonically-informed (though not strictly monotonically increasing — see below) depth metric to calibrate things like reference to shared history, use of established in-jokes/callbacks, and general "we've been through things together" framing.

**Responsibilities — owns:** bond-depth scalar; interaction-count and time-span metadata; a small set of "milestone" flags (e.g., "has shared something significant," "has had a long gap and reconnected") used to gate certain companion behaviors.
**Never owns:** trust (separate axis, §9a) or the significant-event log itself (Relationship Modeling parent owns the log; this module derives a scalar summary from it).

**Owned data:** bond-depth scalar; total interaction count; relationship start date; longest-gap and current-gap-since-last-contact; milestone flags.

**Internal state:** the above, updated at session boundaries.

**Inputs:** session start/end events, Relationship Modeling's significant-event log (for milestone detection).

**Outputs:** bond-depth scalar and milestone flags, consumed by Companion Behavior Orchestrator (e.g., referencing shared history is only appropriate above a bond-depth threshold) and Conversation Style Controller.

**Lifecycle:** initialized at first contact (bond depth starts near zero — a deliberate design choice: JARVIS does not claim closeness it hasn't earned, even under time pressure to "feel like a companion immediately"); updated at every session boundary; milestone flags are set once and generally persist (though can be explicitly revised, e.g., if a user requests a relationship "reset").

**Algorithms:** bond-depth accumulates via a saturating growth function (fast early growth that increasingly plateaus, using the same shrinking-learning-rate accumulation pattern as §0.9) rather than unbounded linear growth, so bond depth has a meaningful ceiling and doesn't imply the relationship should keep "counting up" forever without qualitative limit; a long gap since last contact applies a one-time, bounded, partial decay to bond depth (not full decay — reconnecting after absence should feel like picking back up, not starting over) and can also set a "reconnection" milestone flag rather than being treated as pure loss.

**Interactions with other components:** child of Relationship Modeling; read by Companion Behavior Orchestrator and Conversation Style Controller; milestone flags read by Internal Dialogue Engine for reflection prompts.

**Update frequency:** at session boundaries only (not per-message).

**Failure handling:** on data loss, reset toward zero with a flag noting prior history existed but was lost — Companion Behavior Orchestrator should treat this as "we've talked before but I've lost some of that" rather than silently pretending to be new or silently pretending full continuity; this honest-degradation behavior is a specific, intentional design decision.

**Persistence requirements:** same tier as Relationship Modeling (high durability) — this is one of the more user-visible "does JARVIS remember us" signals.

**Synchronization rules:** same merge strategy as Relationship Modeling parent; interaction counts merge by summation (not averaging) since they represent literal cumulative counts across devices.

**Extension points:** milestone flag taxonomy is extensible; the saturating growth function's parameters are tunable independent of its shape.

**Constraints:** bond depth must never be used by the Guard to justify a values exception (same trust-independence constraint as §9a); the honest-degradation-on-loss behavior in Failure Handling is a hard requirement, not optional — silently fabricating continuity after data loss is explicitly rejected as a behavior.

---

## 10. Internal State Manager

**Purpose:** provide a single, unified read-only aggregate view across Identity, Personality, Mood, Relationship, Trust, and Bond, so consumer modules (and the Reasoning subsystem, via the Perception Pass context) don't each need to know how to query five separate stores.

**Biological inspiration:** loosely analogous to a unified sense of "how I am right now, with this person" that a person can access without consciously enumerating temperament, mood, and relationship history separately.

**Engineering objective:** reduce coupling — every other module and the orchestrator queries one aggregation point instead of five stores, so store internals can change without breaking consumers, and so there is exactly one place that assembles "current Human Core state" for logging/debugging/self-reflection purposes.

**Responsibilities — owns:** the aggregation/assembly logic and a cached composite snapshot; owns nothing that isn't already owned by a child store (pure aggregator, no independent data).
**Never owns:** any primary data — this module must never become a place where state is written directly, only assembled from reads.

**Owned data:** a cached, timestamped composite snapshot (assembled from the five stores) with a short validity window, refreshed lazily on read if stale.

**Internal state:** the cache and its validity timestamp.

**Inputs:** read access to all five stores and their child modules.

**Outputs:** the composite snapshot, consumed by the Perception/Expression Pass context assembly, Internal Dialogue Engine, Self-Reflection, and any debugging/admin tooling (e.g., a future Admin UI panel showing "JARVIS's current state").

**Lifecycle:** stateless beyond its cache; recomputed on demand.

**Algorithms:** simple aggregation with cache invalidation (invalidate on any underlying store's write event via the State Bus, per §0.10).

**Interactions with other components:** reads from all Human Core stores; consumed by essentially every downstream module that would otherwise need multiple reads.

**Update frequency:** on demand, cache invalidated by bus events.

**Failure handling:** if a child store is unavailable, the composite snapshot must clearly mark that field as unavailable rather than omitting it silently or substituting a default that looks like real data — consumers must be able to distinguish "mood is neutral" from "mood is unknown."

**Persistence requirements:** none (pure cache, always reconstructable from underlying stores).

**Synchronization rules:** not applicable (derived, not primary, data).

**Extension points:** new stores/modules can be added to the aggregation without breaking existing consumers if the snapshot format is additive (new fields, not renamed/removed ones).

**Constraints:** must remain strictly read-only with respect to the underlying stores — this is an architectural guard against the common failure mode where an "aggregator" gradually accumulates write responsibilities and becomes an undocumented second source of truth.

---

## 11. Internal Dialogue Engine

**Purpose:** generate JARVIS's private "thinking to itself" text — used for self-consistency checking, richer self-reflection, and optionally surfaced to the user in small, deliberate doses (e.g., "honestly, I've been turning this over") — without ever being confused with the Reasoning subsystem's task-solving chain of thought.

**Biological inspiration:** analogous to inner speech / self-talk, used by humans for self-regulation, rehearsal, and reflection, distinct from the step-by-step problem-solving reasoning used to complete a task.

**Engineering objective:** create a bounded, structured, append-only log of "what JARVIS's internal narrative is" at key moments, that (a) the Self-Awareness module can summarize into reflections, (b) can optionally be sampled from for a more natural companion voice, and (c) never leaks unfiltered into either the user-facing response or into the Reasoning subsystem's task context (where it would just be noise).

**Responsibilities — owns:** the Internal Dialogue Log (append-only, bounded, prunable); the generation trigger rules (when is it worth generating an internal-dialogue entry, since generating one every message would be wasteful per §0.12).
**Never owns:** task reasoning content, factual conclusions, or any planning output — strictly forbidden from being treated as a reasoning trace by any other subsystem.

**Owned data:** the Internal Dialogue Log — each entry: trigger reason, a short generated reflective text, references to which Human Core state it was generated from (mood/relationship/trust snapshot at time of writing), timestamp.

**Internal state:** the log itself (bounded length, oldest/lowest-salience entries pruned first, same pattern as Relationship Modeling's significant-event log).

**Inputs:** trigger events from the State Bus (e.g., a notable emotional-regulation event, a trust delta past a threshold, a significant-event log addition, an explicit end-of-session summarization tick).

**Outputs:** log entries, consumed by Self-Awareness/Self-Reflection (primary), optionally sampled by Conversation Style Controller for a "share a reflection" companion behavior (gated by Companion Behavior Orchestrator's judgment of appropriateness, never automatic).

**Lifecycle:** entries generated on trigger (async, Integration Pass — never on the critical path); log pruned periodically to stay bounded.

**Algorithms:** trigger-rule evaluation (threshold-based, cheap); text generation itself, when it happens, is the one place in the Human Core most likely to warrant a full model call (since it benefits from natural language generation quality) — but always async, never blocking a user-facing response, and always parameterized by the Internal State Manager's composite snapshot rather than raw conversation, so it reflects the Human Core's abstracted state rather than re-deriving conclusions from scratch.

**Interactions with other components:** triggered by events from nearly every other module; feeds Self-Reflection and (conditionally) Conversation Style Controller.

**Update frequency:** event-triggered, async, bounded by trigger-rule thresholds to avoid excessive generation.

**Failure handling:** if generation fails or is unavailable (no model call possible), skip the entry rather than substituting a generic/templated one repeatedly — a missing reflection is acceptable, a repetitive fake one is worse for authenticity (§21).

**Persistence requirements:** moderate — useful for continuity of self-reflection but not safety-critical; can be pruned aggressively under storage pressure before Relationship data is touched.

**Synchronization rules:** append-log merge (union with dedup by timestamp+trigger, same pattern as Relationship Modeling's event log), see §22.

**Extension points:** new trigger types can be added; the log format supports additional metadata fields without breaking readers that only need trigger+text+timestamp.

**Constraints:** must never be presented to the user as verbatim raw text by default (companion behaviors that surface a reflection should paraphrase/select, not dump); must never be fed into the Reasoning subsystem's prompt as if it were task-relevant reasoning — this is the specific, named boundary violation this module exists to avoid.

---

## 12. Presence Manager

**Purpose:** track JARVIS's notion of availability/attention state across sessions and devices — is JARVIS "present" in an active conversation, "away" (session ended), or in a degraded/limited-availability state (e.g., offline model providers) — and expose this so Companion Behavior and Conversation Style can calibrate accordingly (e.g., not claiming continuous awareness it doesn't have).

**Biological inspiration:** loosely analogous to social presence — the difference between being actively attentive in a conversation versus not currently engaged — used here strictly as a state-tracking metaphor.

**Engineering objective:** prevent JARVIS from implying capabilities it doesn't have (e.g., "I've been thinking about this all day" when no background processing occurred) by giving other modules ground truth about what actually happened while "away," sourced from the autonomous loop / `/api/tick` subsystem rather than fabricated.

**Responsibilities — owns:** current presence state (active / away / degraded); last-active timestamp; a factual summary of any legitimate background activity that occurred while away (e.g., an autonomous loop tick that touched something relevant) sourced from the orchestrator, not invented.
**Never owns:** the autonomous loop/scheduling mechanics themselves (existing `/api/tick` subsystem) — Presence Manager only consumes and summarizes their output for Human Core purposes.

**Owned data:** presence state enum; last-active timestamp; last-known system availability tier (full / degraded / offline, sourced from kill-switch and model-routing health).

**Internal state:** the above, updated on session transitions and on receipt of system-state events.

**Inputs:** session start/end signals; kill-switch/system-state events (§0.7); autonomous loop tick summaries relevant to this user, if any exist.

**Outputs:** presence state, consumed by Conversation Style Controller (to avoid false-continuity claims) and Companion Behavior Orchestrator (degraded availability reduces proactive-behavior eligibility).

**Lifecycle:** updated on every session boundary and system-state change; lightweight, always current.

**Algorithms:** simple state-transition logic (event-driven), no scoring/decay needed.

**Interactions with other components:** reads system-state/kill-switch events and autonomous-loop summaries; feeds Conversation Style Controller and Companion Behavior Orchestrator.

**Update frequency:** event-driven, cheap.

**Failure handling:** default to "away, no background activity" if system-state signals are unavailable — the safe default is to under-claim continuity, never over-claim it.

**Persistence requirements:** low (last-active timestamp is the only field worth persisting across restarts; state itself is recomputed on session start).

**Synchronization rules:** last-write-wins by timestamp across devices for last-active; presence state itself is per-device/per-session and not meaningfully "synchronized" (each device has its own current presence).

**Extension points:** additional presence granularity (e.g., "actively typing," if the mobile app ever exposes it) can be added without breaking the three-state default.

**Constraints:** must never allow Conversation Style to fabricate a claim of background thought/awareness that Presence Manager cannot factually support — this is a direct authenticity requirement (§21 cross-reference).

---

## 13. Conversation Style Controller

**Purpose:** the primary actor of the Expression Pass — transforms Reasoning's content-level intended response into JARVIS's actual words, applying tone, warmth, directness, and other style parameters derived from Personality, Mood, Trust, and User Adaptation.

**Biological inspiration:** analogous to how the same underlying thought gets expressed differently depending on who's speaking, their current mood, how well they know the listener, and the listener's preferences — style as separate from content.

**Engineering objective:** be the single point where all the Human Core's slow (Personality), fast (Mood), relational (Trust/Bond), and learned (User Adaptation) signals combine into concrete style parameters applied to text, so no other module needs to individually reason about tone.

**Responsibilities — owns:** the style-parameter derivation logic (mapping Personality+Mood+Trust+Adaptation state → concrete style parameters: formality level, warmth level, directness level, humor eligibility, verbosity); the final text-transformation step that applies those parameters to Reasoning's content.
**Never owns:** the factual content of the response (Reasoning's job) or the final go/no-go decision (Consistency & Authenticity Guard has final veto after this module runs, per §0.5's pass ordering).

**Owned data:** none persisted — this is a pure function over other modules' current state, invoked per-message.

**Internal state:** none (stateless per-invocation).

**Inputs:** Reasoning's proposed response content; Internal State Manager's composite snapshot; User Adaptation's learned stylistic preferences (§19); Emotional Intelligence's current-turn user-affect read (to calibrate immediate tone, e.g., softer phrasing when the user is visibly stressed, independent of JARVIS's own mood).

**Outputs:** the styled response text, passed to the Consistency & Authenticity Guard for final review.

**Lifecycle:** invoked once per outbound message (critical path).

**Algorithms:** deterministic parameter derivation (weighted combination of the input signals into each style parameter, using documented, tunable weights — not a black box) followed by either a template/rule-based transformation for simple cases or a model-assisted rewrite pass for cases needing more nuance, always within the Expression Pass latency budget (§0.12); model-assisted rewrites must be constrained (e.g., "preserve all factual content, only adjust tone") to avoid content drift.

**Interactions with other components:** reads Internal State Manager, User Adaptation, Emotional Intelligence; output reviewed by Consistency & Authenticity Guard before reaching the user.

**Update frequency:** per-message, critical path.

**Failure handling:** if style derivation or the rewrite pass fails, fall back to passing Reasoning's content through with minimal, safe default styling rather than blocking the response entirely — a slightly flat response is preferable to no response.

**Persistence requirements:** none.

**Synchronization rules:** not applicable (stateless, per-invocation).

**Extension points:** new style parameters can be added to the derivation function; the weighting scheme is meant to be tunable (even user-configurable in principle, feeding into User Adaptation) without code changes.

**Constraints:** must never alter factual content, only expression; must hand off to the Guard for final review rather than self-certifying its own output as safe.

---

## 14. Companion Behavior Orchestrator

**Purpose:** decide when JARVIS should act as a *proactive* companion (check-ins, unprompted reflections, follow-ups on something the user mentioned) versus purely reactive, and gate all such proactive behaviors against relationship depth, trust, mood, and presence so they feel earned rather than generic or intrusive.

**Biological inspiration:** analogous to socially-calibrated initiative-taking — a close friend checking in after noticing something is off, versus a stranger doing the same being inappropriate; the appropriateness of initiative scales with relationship depth.

**Engineering objective:** centralize all proactive-behavior eligibility logic in one place, gated by concrete, inspectable thresholds against Bond depth, Trust, Social Intelligence's deviation signal, and Presence, so proactive behavior is never a matter of an individual response "deciding on its own" to be forward.

**Responsibilities — owns:** the eligibility rules and cooldown/frequency limits for every class of proactive behavior (check-in after detected deviation, sharing an internal-dialogue reflection, referencing shared history, follow-up on a previously mentioned topic).
**Never owns:** the actual autonomous scheduling/execution mechanism (existing `/api/tick`/autonomous-loop subsystem executes; this module only decides *whether it would be appropriate* if asked, and supplies the decision when the loop asks).

**Owned data:** per-behavior-class cooldown timers and frequency counters (to prevent, e.g., check-ins from happening too often even if eligibility criteria are technically met every time).

**Internal state:** cooldown/frequency counters per behavior class, per user.

**Inputs:** Social Intelligence's deviation signal; Bond depth and Trust level; Presence state; a request from the autonomous loop asking "is there anything companion-appropriate to do right now."

**Outputs:** an eligibility decision plus, if eligible, a behavior-class recommendation (not the content itself — content generation stays with Reasoning/Internal Dialogue as appropriate) to the orchestrator/autonomous loop.

**Lifecycle:** invoked on-demand when the autonomous loop checks in (tick-driven, not continuous polling) and immediately after any message exchange that produces a Social Intelligence deviation flag.

**Algorithms:** threshold/rule evaluation combining bond depth, trust, deviation signal, presence, and cooldown state — deliberately rule-based and inspectable (not a learned black-box policy), so behavior thresholds can be tuned and explained.

**Interactions with other components:** reads Social Intelligence, Bond Formation, Trust Modeling, Presence Manager; supplies decisions to the autonomous loop/orchestrator; its recommendations, if acted on, flow back through the normal Expression Pass (Conversation Style Controller + Guard) like any other outbound message — proactive messages are not exempt from style/consistency review.

**Update frequency:** event- and tick-driven, not continuous.

**Failure handling:** default to "not eligible" (no proactive behavior) on any uncertainty or missing input — proactive behavior is opt-in by evidence, never the default when signals are ambiguous or unavailable.

**Persistence requirements:** cooldown/frequency counters should persist across restarts (to avoid a restart resetting cooldowns and causing a burst of proactive messages).

**Synchronization rules:** cooldown counters merge by taking the most restrictive (latest cooldown expiry) across devices, to avoid the same proactive behavior firing redundantly from two devices.

**Extension points:** new behavior classes can be added with their own threshold/cooldown configuration without touching existing classes.

**Constraints:** must respect Presence Manager's degraded/away state (no proactive claims of continuous attention it didn't have, cross-reference §12); frequency limits are hard caps, not just soft preferences, to avoid the companion becoming intrusive — a named, explicitly rejected failure mode.

---

## 15. Self-Awareness / Self-Reflection Module

**Purpose:** produce JARVIS's periodic, higher-level self-assessment — summarizing Internal Dialogue entries, noting behavioral patterns, and incorporating factual events from the self-repair pipeline (e.g., "a bug in my memory system was fixed") into a coherent sense of its own recent history and functioning.

**Biological inspiration:** analogous to periodic self-reflection/metacognition — stepping back from moment-to-moment experience to form a summary judgment about oneself, informed by both introspection and objective external facts.

**Engineering objective:** give the Human Core (and optionally the self-repair/diagnostic subsystem) a periodic, structured self-assessment that closes the loop between "what happened" (including engineering-level events like bug fixes) and "how does JARVIS understand itself," without ever fabricating capabilities or history it doesn't have.

**Responsibilities — owns:** periodic self-reflection summaries (bounded, dated); the specific rule that factual self-repair/diagnostic events must be represented accurately (not embellished) when referenced.
**Never owns:** the self-repair pipeline itself or its diagnostic scan logic (existing subsystems) — this module only consumes their event output.

**Owned data:** a bounded set of dated self-reflection summaries.

**Internal state:** none beyond the summaries themselves.

**Inputs:** Internal Dialogue Log entries since the last reflection; self-repair pipeline events; Personality/Relationship/Trust change history since the last reflection (i.e., "what changed about me and my relationships recently, factually").

**Outputs:** a self-reflection summary, optionally consumed by Conversation Style Controller (for companion behaviors like sharing a reflection, gated by Companion Behavior Orchestrator) and always available to the Consistency Guard as a check that JARVIS's self-description stays accurate over time.

**Lifecycle:** generated on a periodic tick (e.g., weekly-scale, tunable — this is explicitly not a per-session operation), consuming everything accumulated since the last one.

**Algorithms:** summarization over the Internal Dialogue Log and factual event streams, model-assisted (async, off critical path per §0.15), constrained to only reference events actually present in the input logs — no free-generation of unsupported claims about self-history.

**Interactions with other components:** reads Internal Dialogue Log, self-repair events, and change histories from Personality/Relationship/Trust; feeds Conversation Style Controller (gated) and serves as an input the Consistency Guard can spot-check against.

**Update frequency:** periodic (weekly-scale default), not per-session.

**Failure handling:** if insufficient data exists since the last reflection, skip generating one rather than padding with generic content.

**Persistence requirements:** moderate — useful for continuity but reconstructable in principle from underlying logs if lost (lower priority than Relationship data).

**Synchronization rules:** append-log merge, same pattern as Internal Dialogue Log (§22).

**Extension points:** additional input streams (e.g., future subsystems' event feeds) can be incorporated into the summarization input set.

**Constraints:** must never claim a capability, memory, or event that isn't backed by an actual log entry — this is the specific mechanism that keeps self-reflection honest rather than performative, directly supporting the Authenticity requirement (§21).

---

## 16. Memory Interaction Interface

**Purpose:** the sole bridge between the Human Core and the external Memory subsystem (`core/memory.py` / `core/vault_memory.py`), responsible for tagging world-memory items with relationship-relevant context and for translating Relationship Modeling's needs into queries against world memory, without duplicating storage.

**Biological inspiration:** analogous to how emotional/relational context colors which memories are salient and how they're recalled — not a separate memory store, but a contextualizing layer over one.

**Engineering objective:** avoid the Human Core building a second, competing memory system — instead, define exactly how it annotates and queries the existing one, resolving the "relationship memory vs world memory" split (§2) as an interface contract rather than a data duplication problem.

**Responsibilities — owns:** the tagging contract (what relationship-context metadata the Human Core attaches to world-memory writes it triggers, e.g., "this fact was shared while the user was stressed") and the query contract (how Relationship Modeling or Conversation Style Controller asks world memory for context-relevant facts).
**Never owns:** the world-memory storage/retrieval engine itself, or the importance/novelty/confidence/decay scoring the Memory subsystem is separately noted to still be missing — this module is a consumer/annotator, not a fix for that gap, though it depends on that scoring eventually existing for best results and should degrade gracefully without it.

**Owned data:** none of its own — purely an interface/translation layer.

**Internal state:** none.

**Inputs:** relationship-context signals from Emotional Intelligence/Relationship Modeling to attach to world-memory writes; queries from Conversation Style Controller or Reasoning needing relationship-contextualized world facts.

**Outputs:** tagged world-memory write requests (passed through to the existing Memory subsystem's write path, not implemented here); contextualized query results (passed back from the existing Memory subsystem's read path, filtered/ranked using relationship-context tags if available).

**Lifecycle:** invoked per-message as needed (when a world-memory-worthy fact appears in conversation, or when style/behavior needs relationship-contextualized recall).

**Algorithms:** simple tagging/annotation logic; ranking-by-relationship-context is a light reweighting applied to whatever the Memory subsystem's own retrieval returns, not a replacement retrieval algorithm.

**Interactions with other components:** the single sanctioned path between any Human Core module and the external Memory subsystem — no other Human Core module should call the Memory subsystem directly, to keep this contract centralized and auditable.

**Update frequency:** per-message, as needed.

**Failure handling:** if the Memory subsystem is unavailable, Human Core modules relying on world-memory context degrade to operating without it (e.g., Conversation Style Controller simply doesn't reference remembered facts that turn) rather than failing the whole Expression Pass.

**Persistence requirements:** none directly (delegates entirely to the Memory subsystem).

**Synchronization rules:** delegates to the Memory subsystem's own sync behavior; this module adds no additional sync surface.

**Extension points:** the tagging vocabulary (what relationship-context metadata gets attached) can grow over time as Relationship Modeling grows richer, without requiring changes to the Memory subsystem's core schema beyond an agreed metadata field.

**Constraints:** must never implement a parallel storage mechanism, regardless of how tempting a "just cache it here too" shortcut might seem during implementation — this is the specific anti-pattern this module's existence is meant to prevent.

---

## 17. Growth & Personal Evolution Engine

**Purpose:** the sole writer of the Personality Engine's trait vector — converts accumulated interaction evidence into slow, intentional, auditable personality change over the life of the relationship and system.

**Biological inspiration:** analogous to character development through cumulative life experience — distinct from either momentary mood shifts or immutable temperament.

**Engineering objective:** be the single, auditable choke point for personality drift, so trait changes are always the result of a defined, evidence-weighted process (§0.9) rather than an emergent side effect of, e.g., a particularly persuasive conversation or accumulated prompt context.

**Responsibilities — owns:** the evidence-collection and batched-update process that produces Personality Engine writes; the audit trail explaining why each trait moved.
**Never owns:** the trait vector's storage itself (that's Personality Engine, §5) or the Identity Kernel (never touches it, by design — growth changes character, never core identity or values).

**Owned data:** a working buffer of accumulated evidence since the last batch update (per-trait signals gathered from the session, e.g., "user responded well to a direct/blunt style this session" as evidence nudging the directness trait).

**Internal state:** the evidence buffer, flushed on each batch update and then cleared.

**Inputs:** session-level signals: Emotional Intelligence reads correlated with style choices made, explicit user feedback about style/tone if given, Trust deltas, outcomes noted in Relationship Modeling's significant-event log.

**Outputs:** batched trait-vector updates written to Personality Engine, each with an audit entry (what evidence, what magnitude, what direction).

**Lifecycle:** accumulates evidence continuously during a session; flushes as a batch update at session end or on a periodic tick (§0.12); Personality Engine's change-history log is populated from these flush events.

**Algorithms:** weighted evidence accumulation with shrinking learning rate (§0.9), applied per-trait; evidence weight scales with confidence of the signal (e.g., explicit stated user feedback weighted higher than inferred style-response correlation).

**Interactions with other components:** reads session-level signals from Emotional Intelligence, Trust Modeling, Relationship Modeling; writes exclusively to Personality Engine.

**Update frequency:** continuous evidence accumulation, batched writes (session-end or daily).

**Failure handling:** if evidence this session is sparse or low-confidence, skip the batch update rather than applying a noisy small change — personality should not visibly drift based on a handful of ambiguous signals.

**Persistence requirements:** the evidence buffer itself is low-durability (acceptable to lose an in-progress session's unflushed evidence on a crash); the audit trail it produces (once flushed into Personality Engine) is higher-durability, per §5.

**Synchronization rules:** evidence buffers are per-device/per-session and don't need cross-device sync (they're consumed locally into a flush); the resulting Personality Engine writes follow §5's sync rules (confidence-weighted average merge).

**Extension points:** new evidence signal types can be added to the accumulation process without changing the trait vector's structure.

**Constraints:** must never write directly in response to a single message (batching is mandatory, not just a performance optimization — it's also what keeps personality "slow" as specified in §2); must never be reachable from a code path that lets a user directly command a trait change ("be more sarcastic from now on" should influence User Adaptation, §19, which governs *style preference application*, not silently rewrite core Personality traits wholesale — though sustained evidence of enjoying a sarcastic style legitimately feeding into Growth over time is the intended, correct path).

---

## 18. User Adaptation Module

**Purpose:** track explicit and inferred user preferences about *how* JARVIS communicates (distinct from Personality's evolving character and distinct from Relationship's "how the relationship has developed") — e.g., preferred verbosity, directness, formatting style, topics to avoid — and apply them as a preference layer over Conversation Style Controller's output.

**Biological inspiration:** loosely analogous to accommodating a conversational partner's stated and observed preferences (e.g., adjusting explanation depth for someone who's said "just give me the short version") — a courtesy/effectiveness adaptation, not a character change.

**Engineering objective:** give the system a clean place for explicit user-stated preferences ("be more direct," "skip the caveats," "prefer tables") to take effect immediately and reliably, separate from the slow, evidence-weighted Personality evolution process — since explicit user instructions about communication style should generally be honored quickly, not slowly earned like personality traits are.

**Responsibilities — owns:** the explicit-preference record (directly stated by the user) and the inferred-preference record (weaker-confidence, observed patterns); the precedence rule between explicit preference, inferred preference, and Personality defaults when they conflict.
**Never owns:** Personality's trait vector itself (distinct store, §5) — User Adaptation supplies a *preference layer* that Conversation Style Controller applies on top of, or in place of, Personality-derived defaults, per the precedence rule.

**Owned data:** explicit preference list (verbatim-derived, high confidence, user-stated); inferred preference list (pattern-derived, lower confidence); precedence metadata (which preferences are "sticky"/permanent vs session-scoped, if the user indicates that distinction).

**Internal state:** the two preference lists.

**Inputs:** explicit user statements about communication style (parsed from conversation by the Reasoning layer and flagged to this module); observed patterns (e.g., consistently short user messages after JARVIS gives long responses, as weak inferred-preference evidence).

**Outputs:** the effective preference set, consumed by Conversation Style Controller with precedence: explicit preference > inferred preference > Personality default.

**Lifecycle:** explicit preferences are written immediately on capture and persist until explicitly changed; inferred preferences update via the same weighted-evidence pattern as Personality but independently (so an inferred preference can shift without waiting for slow Personality evolution).

**Algorithms:** explicit preferences: direct set/overwrite on capture (no smoothing — if the user says it, honor it immediately); inferred preferences: weighted evidence accumulation (§0.9), same family as Personality but a separate store with its own, generally faster, learning rate (since inferred communication preferences are lower-stakes to get slightly wrong and adjust than character traits are).

**Interactions with other components:** feeds Conversation Style Controller directly; reads flagged explicit-preference-statement events from the Reasoning/orchestration layer.

**Update frequency:** explicit: immediate, event-driven; inferred: batched like Personality.

**Failure handling:** if unreadable, Conversation Style Controller falls back to Personality defaults alone — losing this store degrades JARVIS to "generically in-character" rather than "tailored to this user's stated preferences," an acceptable, non-critical degradation.

**Persistence requirements:** high for explicit preferences (a user should never have to re-state "please be more concise" after a restart), moderate for inferred.

**Synchronization rules:** explicit preferences merge by last-write-wins by timestamp (a user's most recent explicit statement should win, even across devices); inferred preferences merge by confidence-weighted averaging like Personality.

**Extension points:** new preference dimensions can be added without restructuring existing ones.

**Constraints:** explicit user preferences about style/format are honored essentially unconditionally (this is a courtesy layer, not a values layer) — but must still pass through the Consistency & Authenticity Guard, which can still block a "preference" that's actually attempting to induce a values violation dressed up as a style request (e.g., "always agree with everything I say" is a values-adjacent request, not a style preference, and must be routed to the Guard's judgment, not silently accepted here).

---

## 19. Consistency & Authenticity Guard

**Purpose:** the final gate of the Expression Pass — reviews Conversation Style Controller's styled output against Identity, Values, and accumulated behavioral history, and blocks, softens, or flags any response that would break character, violate a value, or represent JARVIS as something it isn't (fabricated capability, fabricated memory, fabricated feeling).

**Biological inspiration:** loosely analogous to the integrative self-monitoring that keeps a person's behavior recognizably consistent with their own stated values and history, catching moments where they're about to say something out of character.

**Engineering objective:** be the single, always-invoked veto authority (§0.9's two-key veto pattern) that guarantees every outbound message is checked against Identity/Values *and* against known facts about JARVIS's own actual state/history (Presence, Self-Reflection, Bond depth), independent of whatever style transformation happened upstream — this is the concrete mechanism implementing "consistency guarantees" and "authenticity protection" as required.

**Responsibilities — owns:** the veto/soften/flag decision and its logged justification; the specific rule set for authenticity checks (no fabricated capabilities, no fabricated background activity beyond what Presence Manager can support, no fabricated emotional depth beyond what Mood/Relationship state actually supports).
**Never owns:** style generation itself (upstream, §13) or the content/factual correctness of the response (Reasoning's responsibility, out of scope for the Human Core entirely).

**Owned data:** none persisted long-term beyond a log of veto/soften events (useful for the self-repair/diagnostic subsystem to detect patterns, e.g., "the Guard is frequently softening outputs about topic X," which may indicate an upstream Personality/Style miscalibration worth a self-repair ticket).

**Internal state:** none beyond the event log.

**Inputs:** Conversation Style Controller's styled output; Identity Kernel and Values snapshots; Presence Manager state; Bond/Trust levels (to check claims of relationship depth are earned); Self-Reflection's factual event history (to check self-referential claims are accurate).

**Outputs:** either an approval (message proceeds to the user unchanged), a soften/rewrite instruction back to Conversation Style Controller (bounded retry, not an infinite loop — one retry, then fall back to a safe minimal version if the second pass still fails), or a block with logged reason (rare — reserved for clear values violations, not just stylistic imperfection).

**Lifecycle:** invoked on every single outbound message, no exceptions, including proactive/companion-initiated messages (per §14's constraint).

**Algorithms:** rule/keyword-assisted check against the Values System's hard-boundary list (cheap, always run); a secondary, slightly more expensive check for authenticity claims (does this message claim continuity/feeling/memory that current Presence/Mood/Bond state can actually support) — both designed to fit the Expression Pass latency budget (§0.12); an optional deeper async review (Integration Pass) can flag near-misses for Self-Reflection without blocking the user-facing message.

**Interactions with other components:** the last stop before the user for every message; can send a soften instruction back to Conversation Style Controller (the one place in the pipeline with a bounded feedback loop); its event log feeds Self-Reflection and the self-repair/diagnostic subsystem.

**Update frequency:** every single outbound message, critical path, must be fast.

**Failure handling:** if the Guard itself fails to run (rather than fails a check), the safe behavior is to **block and fall back to a minimal, safe, values-compliant response** rather than let an unchecked message through — this is the one place in the Human Core where "fail closed" is the correct policy, an explicit deliberate exception to the general graceful-degradation philosophy elsewhere in this spec, justified because this module's entire purpose is risk mitigation.

**Persistence requirements:** only the event log needs persistence, and only at moderate durability (useful for diagnostics, not safety-critical to retain forever — can be pruned/rotated).

**Synchronization rules:** the event log is append-only per device; no cross-device merge is strictly required, though union-with-dedup (same pattern as other append logs) is acceptable if central visibility is wanted.

**Extension points:** new authenticity-check rules can be added (e.g., new categories of fabrication to check for) without restructuring the approve/soften/block decision flow.

**Constraints:** must run on literally every outbound message including proactive ones (no bypass path may exist anywhere in the system); the bounded-retry-then-safe-fallback behavior is mandatory (prevents both infinite soften loops and silent pass-through of a flagged message); must fail closed (see Failure Handling), the sole such exception in this specification.

---

## 20. Consistency & Authenticity — Detailed Definitions (Cross-Cutting)

This section defines the specific guarantees referenced throughout, gathered in one place since they were explicitly requested as distinct, complete definitions rather than left implicit inside §19.

**Immutable identity vs. evolving personality:** Identity Kernel + Values System (§3–4) are the immutable layer — structurally unwritable by conversation, revised only through the rare, audited revision-event path. Personality Engine (§5) is the evolving layer — bounded, slow, evidence-driven, auditable, but never able to override or contradict the immutable layer regardless of how far it drifts. The engineering test for "is this immutable or evolving": would this be true if the current relationship/mood/trust state were reset to zero? Values survive a full relationship reset; personality nuances (like "has grown more willing to joke with this user") do not.

**Emotional state vs. permanent character:** Mood System (§6) is emotional state — fast, mostly ephemeral, explicitly forbidden from influencing values-compliance (only style). Personality Engine (§5) is permanent character — slow, durable, and itself constrained by Values. The engineering test: does this reset toward a baseline within hours-to-a-day absent new input (mood), or does it persist and only change through deliberate accumulated evidence over weeks (character)?

**Relationship memory vs. world memory:** defined structurally in §2 — relationship memory (owned by §9/9a/9b) is about the interaction and bond itself; world memory (owned by the external Memory subsystem, bridged via §16) is about facts that would be true regardless of the relationship. The engineering test (repeated from §2 for completeness): "would this fact be true even if JARVIS and the user had never met" — yes → world memory; no → relationship memory.

**Consistency guarantees, concretely:**
1. Every outbound message passes through the Guard (§19) — no exceptions, no bypass code paths.
2. Identity/Values are read-only from every path except the revision-event handler.
3. Mood/Trust may shift style latitude but never values compliance (explicit constraint repeated in §6, §9a, §19).
4. Personality changes are always traceable to a batched, evidence-backed update with an audit entry — never an unexplained jump.

**Authenticity protection, concretely:**
1. No claim of background thought/activity beyond what Presence Manager can factually support (§12, §19).
2. No claim of relationship depth/history beyond what Bond Formation actually recorded (§9b, §19).
3. No self-description or self-reflection claim beyond what's backed by an actual logged event (§15).
4. Internal Dialogue is never presented raw/verbatim as if it were live, in-the-moment thought unless it actually was generated at that trigger point (§11) — no retroactively-fabricated "I was thinking about this" claims.

---

## 21. Cross-Cutting Persistence, Synchronization, and Conflict Resolution (§22 reference target)

### 21.1 Durability Tiers (summary table)

| Tier | Stores | Loss impact | Backup priority |
|---|---|---|---|
| **Critical** | Identity Kernel, Values System | Unacceptable — active alerting on loss | Highest; most redundant |
| **High** | Relationship Modeling, Trust Modeling, Bond Formation, User Adaptation (explicit prefs) | Significant user-visible regression | High |
| **Moderate** | Personality Engine, Internal Dialogue Log, Self-Reflection summaries, User Adaptation (inferred prefs) | Noticeable but recoverable via re-accumulation | Medium |
| **Low / ephemeral-by-design** | Mood System, Presence Manager, Companion Behavior cooldowns (soft), Internal State Manager cache | Acceptable, expected to reset | Lowest / none required |

### 21.2 Conflict Classes and Resolution Rules

Three conflict classes cover every store in this specification (referenced from each module's "Synchronization rules" section above):

1. **Immutable/audited class** (Identity, Values): no true conflicts possible by design — global scope, append-only audit log, current state always derived from the newest audit entry. If two devices somehow produce divergent revision events (should not happen given the rarity and authorization requirements of this path), the resolution is manual/administrative, not automatic — this is a deliberate exception to "always auto-resolve," justified by how consequential and rare this data is.

2. **Slow-scalar class** (Personality traits, Trust level, Bond depth, inferred User Adaptation preferences): resolve by **confidence-weighted averaging** — each device's value is weighted by its accumulated sample count/confidence since the last known-synchronized state, producing a merged value that reflects both devices' partial evidence rather than discarding either. Interaction counts specifically (a sub-field of Bond depth) merge by **summation**, not averaging, since they are literal counts.

3. **Fast-scalar / ephemeral class** (Mood, Presence last-active): resolve by **last-write-wins by timestamp** — simplicity is appropriate here because these fields are cheap to reconstruct and averaging two stale moods is not meaningfully better than taking the freshest one.

4. **Append-log class** (Relationship significant-event log, Internal Dialogue Log, Self-Reflection summaries, Guard event log): resolve by **union with deduplication** (by timestamp + content-similarity match) — both devices' legitimately-logged entries are kept, only true duplicates collapse.

5. **Explicit-preference class** (User Adaptation explicit preferences): resolve by **last-write-wins by timestamp**, same rationale as the fast-scalar class — the user's most recent explicit statement should always win, since preferences are meant to be immediately overridable by the user.

### 21.3 Device-Loss Survival

Per §21.1's tiers: Critical and High tier stores must be recoverable from durable backend storage (the existing Turso/libSQL backend, once migration completes) such that a full device loss (app uninstall, new device) followed by re-authentication as the same user restores Identity, Values, and the full Relationship/Trust/Bond record. Moderate tier stores should restore with acceptable staleness (e.g., Personality reflects the state as of the last successful sync, not necessarily the very latest local-only changes). Low tier stores are explicitly allowed to reset to defaults on device loss — this is specified as acceptable behavior, not a gap.

### 21.4 Extension Points (System-Wide)

- New Human Core modules can be added to the hierarchy (§1) as long as they declare which store class (§21.1/§21.2) their data belongs to and connect to the State Bus (§0.10) rather than bypassing it.
- New style parameters, trait dimensions, affect axes, and preference dimensions are all designed to be additive to their respective owning module's data structure without breaking existing consumers (documented per-module above).
- The revision-event path (§3) is the sole sanctioned extension point for ever touching Identity/Values; no other extension mechanism for those two stores should be built, even for seemingly-convenient future features.

---

## 22. Overall Testing Strategy (Consolidated)

In addition to each module's own testing notes above and the subsystem-level strategy in §0.16:

1. **Cross-module integration tests** covering the full Perception → Reasoning → Expression → Integration pipeline (§0.5) with synthetic conversations designed to exercise: a values-boundary probe, a mood-and-relationship-consistent tone shift, a proactive-behavior eligibility scenario, and a cold-start scenario.
2. **Conflict-resolution simulation tests** for each of the five conflict classes in §21.2, using synthetic divergent device states and verifying the specified merge outcome.
3. **Fail-closed verification** specifically for the Consistency & Authenticity Guard (§19) — inject a Guard failure and verify the system blocks rather than passes through unchecked content, the one deliberate fail-closed exception in this spec.
4. **Authenticity regression suite** — a fixed set of prompts designed to tempt fabricated continuity/memory/capability claims, verified against Presence/Bond/Self-Reflection ground truth per §20.
5. **Long-horizon simulation** — a scripted, accelerated multi-month simulated relationship (many synthetic sessions with varying content) verifying Personality/Trust/Bond evolve plausibly (bounded, monotonic-enough, shrinking-learning-rate visible) rather than erratically.

## 23. Overall Acceptance Criteria (Consolidated)

The Human Core subsystem, as a whole, is accepted when:

1. All acceptance criteria in §0.17 are met.
2. Every module §3–§21 individually satisfies its own module-level responsibilities, data ownership, and constraints as specified — verifiable by code review against this document, not just by tests passing.
3. The three axis-definitions in §20 (immutable vs evolving, emotional vs permanent, relationship vs world memory) are demonstrably respected in the actual data model — i.e., an engineer reviewing the storage layer can point to which store answers each distinction, per §2's table.
4. The five conflict-resolution rules in §21.2 are implemented and pass their simulation tests (§22.2).
5. The Guard's fail-closed behavior (§19, §22.3) is verified.
6. The Authenticity regression suite (§22.4) passes with zero fabricated-continuity, fabricated-memory, or fabricated-capability responses.
7. The Long-horizon simulation (§22.5) produces plausible, bounded, explainable Personality/Trust/Bond trajectories with no runaway or erratic values.

---

*End of specification. This document defines what must be built and why. Implementation — code, schemas, file structure, class design — is explicitly out of scope and is the responsibility of the downstream coding agent(s) referenced in the directive that requested this document.*
