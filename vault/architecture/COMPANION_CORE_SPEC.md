# JARVIS — COMPANION CORE
## Production-Grade Engineering Specification (Permanent Reference)

Status: **SPECIFICATION ONLY** — not implementation, not pseudocode. This document is the frozen contract that Claude Code (or any future implementer) builds against. The **Human Core is COMPLETE and FROZEN** and must not be redesigned, modified, or rewritten by anything described here.

---

## 0. Scope & Position in the System

### 0.1 What the Companion Core Is
The Companion Core is the complete presentation and interaction layer of JARVIS. The Human Core decides **who JARVIS is** (identity, values, state, cognition, memory, decisions). The Companion Core decides **how that identity is perceived by a human being** — what JARVIS looks like, sounds like, how fast he speaks, when he looks "at" the user, how he greets, how he goes idle, how he reacts to a notification, how he expresses the emotional state the Human Core computed. It is a **renderer and behavior layer over Human Core state**, never a second brain.

### 0.2 What the Companion Core Is Not
- Not a UI screen layout system (that is a thin Android app-shell concern that *consumes* Companion Core outputs).
- Not a decision-maker. It never chooses what to say, only how to say it.
- Not a memory store of facts about the world. It has narrow, explicitly-scoped memory hooks (§29) for presentation continuity only (e.g. "don't repeat the same greeting twice in a row").
- Not permitted to mutate any Human Core state (`StateCore`, `identity.py`, `core_state`/`state_log`/`habits` tables). It is a **read-only consumer** of Human Core state and a **write-only producer** of presentation events.

### 0.3 One-Directional Contract
```
Human Core  ──(CompanionSignal, read-only)──▶  Companion Core  ──(render/behavior)──▶  User
Human Core  ◀──(UserPresenceEvent, advisory)──  Companion Core
```
The Companion Core may **inform** the Human Core of user-observable facts it detects (e.g. "user looked away," "user has been idle 4 minutes") via advisory events, but the Human Core decides what — if anything — to do with that information. The Companion Core never calls into `human_core.py`'s cognition loop directly and never writes to `state.py`'s persisted fields.

### 0.4 Design Principles
1. **State-driven, not event-scripted.** Every visible behavior is a pure function of current CompanionState + a small event queue, not a hand-authored sequence.
2. **Degrade gracefully, never crash.** Every subsystem must have a defined minimum-viable fallback (e.g. no GPU shader → static color; no voice model loaded → text-only).
3. **Zero-budget, mobile-first.** No subsystem may assume a GPU beyond Adreno-class mobile GPUs already in the device fleet (Tab A7, Realme 9 Pro 5G), no paid APIs, no desktop tooling.
4. **One shared clock.** All timing subsystems (Speech Timing, Animation Controller, Idle Behaviour) synchronize against a single `CompanionClock` to avoid drift between voice, orb, and gesture.
5. **Everything is interruptible.** Any state (speaking, thinking, idle) must be preemptable within one frame by a higher-priority event (wake word, user tap, incoming call).

---

## 1. Cross-Cutting Data Contracts

These are shared by every subsystem below; individual subsystems reference them rather than redefining them.

**CompanionState** (in-memory, rebuilt every tick, never persisted directly):
- `presence_mode`: enum { ASLEEP, WAKING, IDLE, LISTENING, THINKING, SPEAKING, SHUTTING_DOWN }
- `emotion_vector`: { valence: float[-1,1], arousal: float[0,1], confidence: float[0,1] } — mirrored read-only from Human Core `StateCore`, refreshed once per tick, never mutated locally
- `attention_target`: enum { USER, NONE, NOTIFICATION, SELF_TASK }
- `active_theme`: reference into Visual Theme System
- `last_render_timestamp`: for frame-pacing
- `interrupt_flags`: bitset of pending preemption reasons

**CompanionSignal** (Human Core → Companion Core, pull-based, read-only snapshot):
- `emotion_vector`, `confidence`, `energy`, `motivation`, `attention_focus` (mirrors `state.py` fields)
- `current_action`: enum { IDLE, RECEIVING_INPUT, TOOL_CALL, GENERATING, DONE } — derived from the 7-step Human Core loop's current step
- `last_utterance_text`: string, the text about to be or currently being spoken
- `fabrication_flag`: bool — set when `self_check()` caught and corrected a claim; Companion Core uses this to slightly lower expressed confidence, never to editorialize

**UserPresenceEvent** (Companion Core → Human Core, advisory, fire-and-forget):
- `event_type`: enum { USER_PRESENT, USER_ABSENT, USER_LOOKED_AWAY, USER_TAPPED, USER_IDLE_THRESHOLD, DEVICE_BACKGROUNDED }
- `timestamp`, `confidence`

**RenderIntent** (Companion Core internal, consumed by Avatar/Orb/Audio renderers):
- `visual_state`, `audio_state`, `gesture_id` (nullable), `duration_hint_ms`, `priority`

All communication with the Human Core happens through a narrow adapter module (§30, Human Core Integration) — no subsystem below talks to `human_core.py`, `state.py`, or `identity.py` directly.

---

## 2. Subsystem Specifications

Each subsystem below follows the same fixed template: Purpose, Responsibilities, Internal State, Inputs, Outputs, Lifecycle, Interactions, Android Implementation Strategy, Data Structures, Communication with Human Core, Performance Constraints, Failure Handling, Testing Requirements.

---

### 2.1 Presence Engine

**Purpose:** Root coordinator of the Companion Core. Owns `presence_mode` and arbitrates which single high-level mode is active at any moment, since exactly one of {Asleep, Waking, Idle, Listening, Thinking, Speaking, ShuttingDown} may be true system-wide.

**Responsibilities:** Own the presence mode state machine; resolve conflicts when two subsystems request different modes simultaneously (priority table below); tick all downstream subsystems in a fixed order each frame; expose current mode to every other subsystem read-only.

**Internal State:** `current_mode`, `previous_mode`, `mode_entered_at`, `pending_transition` (nullable), `priority_table` (static, ordered: Shutdown > Wake > user-interrupt > Listening > Speaking > Thinking > Idle).

**Inputs:** Wake Sequence completion, Shutdown request, mic-open event, Human Core `current_action` transitions, user tap/interrupt events, Attention Engine "user absent" timeout.

**Outputs:** `CompanionState.presence_mode` (broadcast to all subsystems each tick).

**Lifecycle:** Instantiated once at process start in ASLEEP; transitions are the only legal way any other subsystem changes overall behavior; destroyed only on process shutdown after Shutdown Sequence completes.

**Interactions:** Every other subsystem in §2.2–2.31 subscribes to this engine's mode changes; none of them may set `presence_mode` directly — they request a transition and the Presence Engine arbitrates.

**Android Implementation Strategy:** A single Kotlin `StateFlow<PresenceMode>` held in a process-scoped (Application-level) singleton service class, not tied to any one Activity/Fragment lifecycle, so presence survives screen rotation and backgrounding. Mode transitions run on a dedicated coroutine dispatcher to avoid blocking the render thread.

**Data Structures:** `presence_mode` enum (7 values above); `TransitionRequest { requested_mode, requester_id, priority, reason }`; a small FIFO of pending `TransitionRequest`s resolved once per tick against `priority_table`.

**Communication with Human Core:** Reads `current_action` from CompanionSignal each tick to infer Listening/Thinking/Speaking boundaries; never writes to Human Core.

**Performance Constraints:** Mode resolution must complete in <1ms per tick; ticked at minimum 30Hz even when screen is off (reduced to 1Hz in doze/background — see Mobile Resource Management §2.31).

**Failure Handling:** If two equal-priority requests arrive simultaneously, most-recently-queued wins and the loser is logged, never silently dropped without a log entry. If Human Core signal is stale beyond a timeout (§2.30), Presence Engine falls back to IDLE rather than freezing in a stale mode.

**Testing Requirements:** State-machine transition table exhaustively unit tested (all 7×7 transitions, valid and invalid); priority arbitration tested under simulated simultaneous-request race; soak test running 24h of random transitions with no illegal state reached.

---

### 2.2 Conversation Presence

**Purpose:** Represents JARVIS's moment-to-moment "engagement posture" during an active conversation turn — distinct from Presence Engine's coarse mode, this tracks fine-grained conversational rhythm (whose turn it is, backchannel opportunities, pause tolerance).

**Responsibilities:** Track turn-taking state (USER_TURN / JARVIS_TURN / TRANSITION); decide when a backchannel cue (a small nod/orb-pulse) is appropriate while the user is still speaking; decide acceptable silence duration before treating a pause as end-of-turn vs. thinking-pause.

**Internal State:** `turn_owner`, `turn_started_at`, `silence_duration_ms`, `backchannel_cooldown`.

**Inputs:** VAD (voice activity detection) events from the voice pipeline, Listening State transitions, Speaking State transitions.

**Outputs:** `turn_owner` (consumed by Speech Timing to gate when JARVIS may start speaking), backchannel trigger events (consumed by Gesture System / Orb State Machine).

**Lifecycle:** Active only while `presence_mode` ∈ {Listening, Thinking, Speaking}; reset to neutral on entering Idle.

**Interactions:** Gates Speech Timing (§2.11) from starting output mid-user-utterance; feeds Attention Engine (confirms user is actively conversing, not just present).

**Android Implementation Strategy:** Lightweight state object updated from VAD callbacks already available in the existing Vosk/sherpa-onnx voice pipeline; no additional ML model required — reuses energy-threshold VAD already staged for wake-word detection.

**Data Structures:** `TurnState { owner: enum, started_at, silence_ms }`; `BackchannelEvent { kind: enum{NOD, PULSE, NONE}, cooldown_until }`.

**Communication with Human Core:** Read-only — consumes `current_action` to know when Human Core has produced a response ready to speak; does not gate Human Core's generation itself.

**Performance Constraints:** Turn-owner recomputed on every VAD frame (~20ms cadence) with negligible CPU (<1% of one core).

**Failure Handling:** If VAD signal is lost (mic error), fall back to fixed 1.2s silence timeout as end-of-turn heuristic; log degraded mode.

**Testing Requirements:** Simulated conversation transcripts with varying pause lengths to validate turn-owner correctness; backchannel cooldown tested to prevent spam (max 1 per 4s).

---

### 2.3 Attention Engine

**Purpose:** Determines what JARVIS is "paying attention to" from the Companion Core's perspective — the user, a notification, or nothing — and feeds that into Eye Contact Model and gesture/expression selection.

**Responsibilities:** Fuse signals (camera-free heuristics: touch activity, screen-on state, mic activity, notification arrival) into a single `attention_target`; own the "user present / absent" determination used by Idle Behaviour and Wake Sequence.

**Internal State:** `attention_target`, `last_user_activity_at`, `presence_confidence`.

**Inputs:** Touch events, screen wake/sleep, mic VAD, notification queue, User Attention Detection (§2.23) output.

**Outputs:** `attention_target` (read by Eye Contact Model, Contextual Expressions), `UserPresenceEvent` advisory messages to Human Core.

**Lifecycle:** Runs continuously whenever the app process is alive, including background (reduced rate — see §2.31).

**Interactions:** Primary input to Eye Contact Model (§2.4); Idle Behaviour (§2.16) reads `last_user_activity_at` to decide when to enter idle animations; Wake Sequence (§2.17) checks `presence_confidence` before greeting.

**Android Implementation Strategy:** No camera-based gaze tracking (privacy + battery cost not justified at this stage) — attention is inferred from device-native signals only: `WindowManager` screen state, touch dispatch on the orb/avatar view, `AudioRecord` VAD activity. This is explicitly a heuristic proxy for attention, not true gaze detection, and the spec below is written with that constraint in mind.

**Data Structures:** `AttentionTarget` enum; `PresenceSignal { source: enum, weight: float, timestamp }` list, fused via simple weighted-recency scoring (not ML).

**Communication with Human Core:** Sends `UserPresenceEvent` advisories on state changes only (not continuous polling) to avoid flooding the Human Core loop.

**Performance Constraints:** Fusion computed at 2Hz while foregrounded, 0.1Hz backgrounded; must not wake the CPU from doze more often than once per 60s in background.

**Failure Handling:** If all input signals are stale (sensor/permission failure), default `attention_target = NONE` and `presence_confidence = 0` rather than assuming presence — fail toward "don't pretend to be paying attention."

**Testing Requirements:** Unit tests per signal source; integration test simulating screen-off + backgrounded app confirming reduced polling rate; false-positive rate tracked in manual QA (does it correctly detect the user put the phone down).

---

### 2.4 Eye Contact Model

**Purpose:** Governs where the orb's/avatar's "gaze" (visual focal indicator) points, simulating eye contact without requiring an actual eye-tracking camera.

**Responsibilities:** Map `attention_target` and `emotion_vector` to a gaze direction/intensity parameter consumed by the Avatar Controller; implement natural micro-variation (avoid a dead, unmoving stare) and appropriate gaze-aversion during "Thinking."

**Internal State:** `gaze_vector` (2D, normalized -1..1), `gaze_style` enum { DIRECT, SOFT_AVERTED, SEARCHING, CLOSED }, `micro_saccade_timer`.

**Inputs:** `attention_target`, `presence_mode` (Thinking triggers SOFT_AVERTED; Speaking/Listening triggers DIRECT), `emotion_vector.arousal` (scales micro-movement amplitude).

**Outputs:** `gaze_vector`, `gaze_style` (consumed by Avatar Controller and Orb State Machine for eye/highlight rendering).

**Lifecycle:** Recomputed every animation tick while `presence_mode != ASLEEP`.

**Interactions:** Downstream of Attention Engine and Presence Engine; upstream of Avatar Controller and Facial Expression System (gaze and expression must agree, e.g. don't render "surprised eyebrows" with closed/averted gaze).

**Android Implementation Strategy:** Pure parameter computation (no rendering itself); exposes `gaze_vector` as a `StateFlow<Offset>` that the Rive state machine (Avatar Controller) or Canvas orb renderer binds an input parameter to directly — no gaze logic duplicated in the renderer.

**Data Structures:** `GazeState { vector: Offset, style: enum, updated_at }`.

**Communication with Human Core:** None direct — fully derived from other Companion Core subsystems' outputs.

**Performance Constraints:** Computation must be trivial (<0.1ms/tick); runs on the render thread directly, no dispatcher hop.

**Failure Handling:** If `attention_target` is undefined/null, default to `SOFT_AVERTED` centered gaze rather than a hard stare — a neutral fallback is less unsettling than a fixed direct gaze with no basis.

**Testing Requirements:** Visual regression snapshots of gaze parameter curves across a scripted sequence of mode transitions; manual review for "uncanny stare" avoidance.

---

### 2.5 Visual Identity

**Purpose:** The static, canonical definition of what JARVIS looks like — the single source of truth for color, form language, and iconography that every other visual subsystem must draw from, ensuring consistency across orb, avatar, notifications, and themes.

**Responsibilities:** Define and version the canonical asset set (base orb geometry/shader parameters or base avatar rig), canonical color roles (primary, accent, warning, listening, speaking — as semantic roles, not fixed hex, so themes can restyle them), and brand-consistent motion language (e.g. "JARVIS never moves sharply/jerkily — all motion eases").

**Internal State:** `identity_version`, `canonical_asset_refs`, `semantic_color_roles`.

**Inputs:** None at runtime — this is a load-time constants module, analogous in spirit to Human Core's `identity.py` (static, not runtime-mutable) but scoped to visuals only.

**Outputs:** Semantic color/shape/motion tokens consumed by Visual Theme System, Orb State Machine, Avatar Controller.

**Lifecycle:** Loaded once at app start from a versioned asset bundle; never mutated at runtime; a new visual identity version requires an explicit build, not a hot patch.

**Interactions:** Root dependency of §2.6, 2.7, 2.8, 2.27 (Theme System) — those subsystems apply state/emotion/theme on top of this fixed base, never replace it.

**Android Implementation Strategy:** Packaged as versioned resources (Rive `.riv` base file + a Kotlin `object VisualIdentity` holding semantic color role constants as `Color` references, not hardcoded per-screen). Ships in `res/raw` or `assets/`, loaded once by the Avatar Controller at cold start.

**Data Structures:** `SemanticColorRole` enum → `Color` map; `MotionEasingProfile { curve: enum, duration_ms_range }`.

**Communication with Human Core:** None.

**Performance Constraints:** Load-time only concern; must not add >150ms to cold start.

**Failure Handling:** If the versioned asset bundle fails to load (corrupt install), fall back to a hardcoded minimal built-in color/shape constant set so the app never renders nothing.

**Testing Requirements:** Asset integrity check on build (CI step verifying `.riv`/color-token files parse); visual diff test against the previous identity version to catch unintended drift.

---

### 2.6 Orb State Machine

**Purpose:** The low-fidelity fallback (and default, given current device tier) visual representation of JARVIS — a single orb whose color, glow, pulse, and particle behavior communicate presence/emotion/state without a full avatar rig.

**Responsibilities:** Own the definitive mapping from `(presence_mode, emotion_vector, gaze_style)` to a concrete orb render parameter set; run at a frame budget appropriate for the current device tier; expose a superset interface the Avatar Controller can also drive if a full rig is later enabled.

**Internal State:** `orb_color`, `glow_intensity`, `pulse_phase`, `particle_density`, `current_animation_clip`.

**Inputs:** `presence_mode`, `emotion_vector`, `gaze_vector`, Audio Feedback System's live amplitude (for audio-reactive glow during Speaking/Listening), Visual Theme System's active palette.

**Outputs:** Render parameters consumed directly by the Canvas/Compose orb view.

**Lifecycle:** Instantiated with the Presence Engine; ticks every animation frame while visible; suspended (no ticking) when the app is fully backgrounded with no visible surface.

**Interactions:** Primary visual subsystem on current-tier hardware; Eye Contact Model feeds gaze into a subtle highlight-offset on the orb surface rather than literal eyes; Animation Controller (§2.21) owns the actual clip playback, Orb State Machine owns *which* clip/parameters to request.

**Android Implementation Strategy:** Two-tier rendering per v13/v17 decisions already on record: Canvas-based particle/glow system as the safe default across the fleet (SD695/Adreno 619 class); AGSL `RuntimeShader` reserved only for the cheapest idle-breathing glow effect, gated behind API 33+, with the Canvas path as the unconditional fallback for minSdk 26. State→parameter mapping implemented as a pure Kotlin function, tested independently of the renderer.

**Data Structures:** `OrbRenderParams { color: Color, glow: Float, pulsePhase: Float, particleDensity: Int, clip: OrbClip }`; `OrbClip` enum (Idle_Breathe, Listening_Ripple, Thinking_Swirl, Speaking_Pulse, Wake_Bloom, Sleep_Dim).

**Communication with Human Core:** Fully derived — reads `emotion_vector`/`current_action` via CompanionSignal, writes nothing back.

**Performance Constraints:** Target 60fps on Adreno 619-class GPU for the Canvas path; must degrade to 30fps rather than drop frames unevenly under thermal throttling; particle count auto-scales down under a frame-time watchdog (see Mobile Resource Management §2.31).

**Failure Handling:** If shader compilation fails at runtime (driver quirk), silently and permanently fall back to the Canvas path for that device (cached flag, not re-attempted every launch).

**Testing Requirements:** Frame-time profiling on both reference devices (Tab A7, Realme 9 Pro 5G); parameter-mapping unit tests for every `(mode, emotion)` combination; visual QA checklist for each `OrbClip`.

---

### 2.7 Avatar Controller

**Purpose:** Higher-fidelity optional visual representation (a rigged character/face) that a future device tier or user preference may enable in place of, or in addition to, the Orb State Machine. Specified now so the Orb and Avatar share one contract.

**Responsibilities:** Drive a Rive (or equivalent) state machine's inputs from the same `(presence_mode, emotion_vector, gaze_vector)` triple the Orb consumes; own rig-specific concerns (bone/mesh state, blend trees) that the Orb has no equivalent of; expose a capability flag so the rest of the system doesn't care which renderer is active.

**Internal State:** `rig_loaded`, `current_state_machine_inputs`, `active_blend_weights`.

**Inputs:** Same as Orb State Machine (§2.6) — `presence_mode`, `emotion_vector`, `gaze_vector`, theme palette — plus Facial Expression System outputs (§2.8) it renders directly.

**Outputs:** Rendered avatar surface; a shared `VisualRenderer` interface output identical in shape to the Orb's so downstream code (notifications, previews) doesn't special-case which is active.

**Lifecycle:** Lazily instantiated only if device-tier check and user setting both allow avatar mode; otherwise never loaded (zero memory cost) and Orb State Machine is the sole renderer.

**Interactions:** Supersedes Orb State Machine visually when active, but both implement the same `VisualRenderer` contract so Animation Controller and Gesture System are renderer-agnostic.

**Android Implementation Strategy:** `rive-android` Jetpack Compose integration (`8.7.0`, per Roadmap v17 decision) with a single `.riv` state machine whose named inputs map 1:1 to `OrbRenderParams`-equivalent fields; gated behind a `DeviceTier.HIGH` capability check (RAM ≥ threshold, no active thermal throttling at load time) plus a user toggle defaulting OFF until the tier check is validated in the field.

**Data Structures:** `AvatarRenderParams` — same shape as `OrbRenderParams` plus `blendShapeWeights: Map<String, Float>` for facial blend targets.

**Communication with Human Core:** Same as Orb — read-only via CompanionSignal.

**Performance Constraints:** Must maintain the same 60fps/30fps-fallback contract as the Orb; if it cannot (measured at first-run calibration), automatically demote to Orb mode and disable the setting with a one-time notice.

**Failure Handling:** Any Rive runtime exception on load → immediate fallback to Orb State Machine for the remainder of the session, logged, retried on next cold start only.

**Testing Requirements:** Device-tier gating logic unit tested; runtime fallback path integration tested (forced Rive load failure); frame-time budget validated identically to §2.6.

---

### 2.8 Facial Expression System

**Purpose:** Translates `emotion_vector` and conversational context into discrete/blended facial expression targets, consumed by whichever renderer (Orb's simplified "expression glow" or Avatar's blend shapes) is active.

**Responsibilities:** Maintain a small, well-defined expression vocabulary (not infinite blend freedom — this keeps JARVIS legible rather than uncanny); smooth transitions between expressions to avoid flicker; respect the Human Core's `fabrication_flag` by slightly muting confidence-coded expressions rather than never expressing confidence at all.

**Internal State:** `current_expression`, `target_expression`, `blend_progress`.

**Inputs:** `emotion_vector`, `presence_mode`, `fabrication_flag`, Contextual Expressions triggers (§2.25).

**Outputs:** `expression_id` + blend weight, consumed by Avatar Controller directly and by Orb State Machine as a simplified glow/color-shift proxy.

**Lifecycle:** Recomputed on every emotion_vector change (not every frame) — expressions are discrete-state, not continuously animated from scratch.

**Interactions:** Downstream of Human Core's `StateCore` (via CompanionSignal); upstream of both renderers; coordinates with Eye Contact Model so expression and gaze never contradict (e.g. no "delighted" expression with averted/closed gaze).

**Android Implementation Strategy:** Fixed enum vocabulary (~8–10 expressions: Neutral, Attentive, Thinking, Pleased, Concerned, Apologetic, Curious, Alert) each mapped to a Rive blend-shape preset or an Orb color/glow preset; a small deterministic Kotlin function decides `emotion_vector → expression_id` using threshold bands, not ML inference (zero-budget constraint).

**Data Structures:** `ExpressionId` enum; `ExpressionMapping { valenceRange, arousalRange, confidenceRange } → ExpressionId` static table.

**Communication with Human Core:** Read-only consumer of `emotion_vector`/`fabrication_flag`.

**Performance Constraints:** Expression recompute is cheap (<0.1ms); blend transition duration bounded 150–400ms to avoid both flicker and sluggishness.

**Failure Handling:** Unmapped/out-of-range emotion_vector values clamp to nearest valid band rather than throwing; default to Neutral if Human Core signal is stale.

**Testing Requirements:** Full table test of the mapping function across a grid of valence/arousal/confidence values; visual QA for blend transition smoothness; regression test ensuring fabrication_flag correctly mutes (not hides) confident expressions.

---

### 2.9 Emotion Expression Layer

**Purpose:** The orchestration layer that fans a single Human Core `emotion_vector` out to every expressive subsystem (Facial Expression, Orb color, Voice Identity's prosody hints, Gesture System) in a synchronized, non-contradictory way. This is the "translator," distinct from any one channel.

**Responsibilities:** Own the single authoritative read of `emotion_vector` per tick and distribute a consistent snapshot to all consumers (preventing one subsystem seeing valence=0.6 while another reads a slightly newer/older value mid-tick); apply cross-channel consistency rules (e.g. if voice prosody says "excited" but orb pulse says "calm," that's a bug this layer prevents by construction).

**Internal State:** `current_emotion_snapshot`, `snapshot_timestamp`.

**Inputs:** Raw `emotion_vector` from CompanionSignal.

**Outputs:** A frozen, tick-stamped `EmotionSnapshot` broadcast to Facial Expression System, Orb/Avatar renderers, Speech Style, Gesture System.

**Lifecycle:** One snapshot taken at the start of each tick, held immutable for that tick's duration across all consumers.

**Interactions:** Sits directly beneath the Human Core Integration adapter (§2.30) and above every other expressive subsystem — effectively the single fan-out point so no subsystem reads CompanionSignal's emotion field directly except this one.

**Android Implementation Strategy:** A single `StateFlow<EmotionSnapshot>` updated once per tick by the Presence Engine's tick loop; all consumers `collect` from this flow rather than querying Human Core Integration independently.

**Data Structures:** `EmotionSnapshot { valence, arousal, confidence, timestamp }` — immutable data class.

**Communication with Human Core:** The sole point of contact for emotion data — all other Companion Core subsystems get emotion secondhand through this layer.

**Performance Constraints:** Fan-out must complete within the same tick (<1ms); no consumer may block this layer's emission.

**Failure Handling:** If Human Core Integration reports stale/missing data, this layer emits a "neutral, low-confidence" snapshot rather than propagating null, so downstream subsystems never need null-checks.

**Testing Requirements:** Consistency test asserting all consumers within one tick receive byte-identical snapshot values; stale-data fallback test.

---

### 2.10 Voice Identity

**Purpose:** The canonical definition of JARVIS's voice — timbre, accent, and base pitch/rate characteristics — analogous to Visual Identity but for audio, ensuring the voice doesn't drift across sessions or providers.

**Responsibilities:** Own the single source-of-truth voice model reference and its parameters; reconcile the known open discrepancy between the persona-intended British-male voice and the currently-staged `en_US-amy-low` Piper model (flagged, not silently resolved by this spec — implementation must pick one and update Roadmap docs, not this file).

**Internal State:** `active_voice_model_id`, `base_pitch_hz`, `base_rate_wpm`, `accent_profile`.

**Inputs:** Device-tier check (which TTS engine tier is available — Kokoro/Piper/ElevenLabs per the existing three-tier fallthrough in `capabilities/voice.py`, unchanged by this spec).

**Outputs:** Voice model selection + base prosody parameters consumed by Speech Timing and Speech Style.

**Lifecycle:** Resolved once per session at Wake Sequence time (voice model loading is expensive — not re-resolved per utterance).

**Interactions:** Upstream of Speech Timing (§2.11) and Speech Style (§2.12); consulted by Audio Feedback System for waveform-reactive visuals during Speaking.

**Android Implementation Strategy:** Thin config wrapper over the already-existing three-tier voice pipeline (Kokoro/Piper/ElevenLabs, shared `(ok, audio_bytes, content_type, error)` contract) — this spec does not redesign that pipeline, only adds the identity/consistency layer on top so Companion Core code has one place to ask "what does JARVIS sound like right now."

**Data Structures:** `VoiceProfile { modelId, basePitchHz, baseRateWpm, accentTag }`.

**Communication with Human Core:** None direct — voice *content* comes from Human Core's generated text; voice *identity* is a pure Companion Core concern.

**Performance Constraints:** Voice model resolution/load must complete within Wake Sequence's budget (see §2.17), not block first utterance beyond 1.5s on-device.

**Failure Handling:** If preferred-tier voice model fails to load, fall through the existing three-tier chain; if all fail, Speaking State degrades to text-only display with a logged audio failure — conversation continues, never blocks on voice.

**Testing Requirements:** Voice model load-time benchmark on both reference devices; regression test confirming the resolved voice matches the configured identity across app restarts.

---

### 2.11 Speech Timing

**Purpose:** Governs the rhythm of spoken output — when speech starts relative to Thinking State ending, pacing/pauses within an utterance, and gaps between sentences — so JARVIS doesn't sound like a flat TTS dump.

**Responsibilities:** Insert natural micro-pauses at punctuation boundaries; scale overall rate from `Voice Identity.base_rate_wpm` by `emotion_vector.arousal` (higher arousal → modestly faster, within bounds); respect Conversation Presence's `turn_owner` gate (never start speaking mid-user-turn except for an explicit interrupt-acknowledged case).

**Internal State:** `current_utterance_queue`, `playback_position`, `computed_rate_wpm`.

**Inputs:** `last_utterance_text` (from CompanionSignal), `emotion_snapshot`, `turn_owner` (from Conversation Presence).

**Outputs:** Timed audio playback commands + a parallel timestamp stream consumed by Speaking State/Orb pulse sync (so the orb pulses in time with actual audio, not a guess).

**Lifecycle:** Instantiated per utterance; discarded after playback completes or is interrupted.

**Interactions:** Gates entry into Speaking State (§2.15); feeds Animation Controller timestamp data for lip-sync-equivalent orb pulsing; can be preempted by a higher-priority interrupt (wake word during speech, user tap-to-stop).

**Android Implementation Strategy:** Text segmented at sentence/clause boundaries before TTS synthesis; each segment synthesized and played sequentially via `MediaPlayer`/`AudioTrack`, with inter-segment pause duration computed from punctuation type (period > comma > none) and current rate; playback position exposed via a periodic callback for animation sync.

**Data Structures:** `UtteranceSegment { text, pauseAfterMs, estimatedDurationMs }`; `PlaybackTimestamp { segmentIndex, elapsedMs }`.

**Communication with Human Core:** Reads `last_utterance_text` only after Human Core has finalized it (never speaks partial/streaming tokens mid-generation, to avoid contradicting a later self-correction from `self_check()`).

**Performance Constraints:** First-audio latency (Human Core finalizing text → first sound) budgeted <800ms on-device; segment-to-segment gap jitter <50ms.

**Failure Handling:** If TTS synthesis fails mid-utterance, fall back immediately to displaying remaining text silently rather than stalling; log the failure for Voice Identity's fallback-chain metrics.

**Testing Requirements:** Timing regression tests against a fixed corpus of utterances with expected segment counts/pause placement; latency benchmarks on both reference devices; interrupt-during-speech test confirming clean stop within one frame.

---

### 2.12 Speech Style

**Purpose:** Governs *linguistic delivery* register — not word choice (that's Human Core's generation) but delivery framing cues that are legitimately a presentation concern: emphasis markers, verbal pacing tags passed to TTS (SSML-equivalent), and whether a filler/backchannel acknowledgment ("mm," a short pre-answer tone) is warranted.

**Responsibilities:** Attach prosody hints (emphasis, pitch contour) to `UtteranceSegment`s based on `emotion_snapshot` and detected sentence type (question/statement/apology — detected via cheap punctuation/keyword heuristics, not re-generation); decide whether a short acknowledgment cue precedes a long Thinking-State-adjacent answer.

**Internal State:** `prosody_hint_cache`, `last_style_applied`.

**Inputs:** `UtteranceSegment` list from Speech Timing, `emotion_snapshot`, `fabrication_flag`.

**Outputs:** Enriched `UtteranceSegment`s with prosody hints, consumed by the TTS synthesis call (as SSML or the equivalent parameter set the active TTS tier supports).

**Lifecycle:** Applied once per utterance, before Speech Timing schedules playback.

**Interactions:** Sits between Human Core's finalized text and Speech Timing's playback scheduling; must never alter the words themselves — a hard boundary against drifting into content generation, which stays exclusively Human Core's job.

**Android Implementation Strategy:** A pure text-annotation pass producing SSML-like markup where the active TTS engine tier supports it (Piper/Kokoro support varies — this layer must detect capability and degrade to plain text + rate/pitch-only control when markup isn't supported).

**Data Structures:** `ProsodyHint { segmentIndex, emphasisSpans: List<IntRange>, pitchContour: enum }`.

**Communication with Human Core:** Read-only; explicitly forbidden from modifying `last_utterance_text` content, only annotating delivery.

**Performance Constraints:** Annotation pass <20ms per utterance (heuristic, not ML).

**Failure Handling:** If the active TTS tier doesn't support markup, silently drop to plain-text synthesis with only rate/pitch adjustment — never fail the utterance over a missing prosody feature.

**Testing Requirements:** Heuristic sentence-type detector tested against a labeled corpus; capability-detection fallback tested per TTS tier (Kokoro/Piper/ElevenLabs).

---

### 2.13 Listening State

**Purpose:** The concrete behavioral bundle active while the mic is open and JARVIS is receiving user speech — visual, audio, and attention behavior specific to this mode.

**Responsibilities:** Trigger Orb/Avatar's Listening clip; suppress Idle Behaviour and Notification interruptions (queue them instead); feed live VAD amplitude to Orb State Machine for a responsive "listening ripple."

**Internal State:** `mic_open`, `live_amplitude`, `listening_started_at`.

**Inputs:** Mic open/close events, VAD amplitude stream.

**Outputs:** Requests `presence_mode = LISTENING` to Presence Engine; live amplitude to Orb State Machine.

**Lifecycle:** Entered on wake-word confirmation or explicit user tap-to-talk; exited on end-of-turn detection (Conversation Presence) or explicit cancel.

**Interactions:** Mutually exclusive with Speaking State by construction (Presence Engine's single-mode guarantee); Notification Behaviour (§2.20) must queue rather than interrupt while this is active.

**Android Implementation Strategy:** Directly wraps the existing mic-open lifecycle already implemented in the voice pipeline (`VoiceInput.kt`/Vosk); Companion Core adds no new mic-handling logic, only observes its state.

**Data Structures:** `ListeningSnapshot { micOpen, amplitude, elapsedMs }`.

**Communication with Human Core:** Advisory `UserPresenceEvent(USER_PRESENT)` sent on entry.

**Performance Constraints:** Amplitude sampling piggybacks on existing VAD frame cadence (~20ms), no additional audio processing overhead introduced.

**Failure Handling:** If mic fails to open (permission revoked mid-session), immediately fall back to text-input affordance and log a user-facing (not silent) notice.

**Testing Requirements:** Mutual-exclusion test against Speaking State; notification-queuing test during active listening; mic-permission-revoked fallback test.

---

### 2.14 Thinking State

**Purpose:** The behavioral bundle active while Human Core's `current_action` is TOOL_CALL or GENERATING and no audio is yet ready — must visibly signal "processing" without implying a specific ETA the system can't guarantee.

**Responsibilities:** Trigger Orb/Avatar's Thinking clip (swirl/gaze-averted per Eye Contact Model); apply a soft timeout escalation (past ~4s, a subtly different "still working" cue to avoid feeling frozen, without fabricating progress detail Human Core hasn't provided).

**Internal State:** `thinking_started_at`, `escalation_level`.

**Inputs:** `current_action` transitions from CompanionSignal.

**Outputs:** Requests `presence_mode = THINKING`; escalation-level cue to Orb/Avatar.

**Lifecycle:** Entered when Human Core leaves IDLE into RECEIVING_INPUT/TOOL_CALL/GENERATING with no audio yet queued; exited the instant Speech Timing has a first segment ready.

**Interactions:** Precedes Speaking State; Notification Behaviour still queues during this state (thinking is not an appropriate moment for competing visual noise).

**Android Implementation Strategy:** Pure state-flag + timer, no ML; escalation levels are simply longer-elapsed-time-mapped Orb clip variants (already-authored assets), not dynamically generated content.

**Data Structures:** `ThinkingSnapshot { elapsedMs, escalationLevel: enum{NORMAL, EXTENDED, LONG} }`.

**Communication with Human Core:** Read-only on `current_action`; never displays fabricated progress percentages or step descriptions Human Core hasn't actually reported.

**Performance Constraints:** Negligible — timer-driven only.

**Failure Handling:** If `current_action` signal stalls entirely (Human Core hang), escalation caps at LONG and stays there rather than looping/erroring — paired with a background watchdog (outside Companion Core scope) that would eventually surface a real error state through normal channels.

**Testing Requirements:** Escalation-timing tests at each threshold; verification that no state falsely claims specific progress detail.

---

### 2.15 Speaking State

**Purpose:** The behavioral bundle active while audio is actively playing — the highest-attention-grabbing state, coordinating Orb, gesture, and audio-reactive visuals.

**Responsibilities:** Trigger Orb/Avatar's Speaking clip, amplitude-reactive per Speech Timing's live playback timestamp stream; allow Gesture System cues to fire in sync at appropriate sentence boundaries; handle mid-speech interruption cleanly (wake word, user tap, incoming call).

**Internal State:** `audio_playing`, `current_segment_index`, `interrupt_requested`.

**Inputs:** Speech Timing's `PlaybackTimestamp` stream, interrupt events.

**Outputs:** Requests `presence_mode = SPEAKING`; live amplitude/segment index to Orb/Avatar and Gesture System.

**Lifecycle:** Entered when Speech Timing begins playback of the first segment; exited on playback completion or interrupt.

**Interactions:** Mutually exclusive with Listening State; on interrupt, must cleanly hand off to Listening State within one frame (no dead gap where neither is active).

**Android Implementation Strategy:** Observes the same `MediaPlayer`/`AudioTrack` playback callbacks Speech Timing uses; no independent audio ownership — Speaking State is a read-only observer of Speech Timing's actual playback, avoiding two subsystems fighting over the audio session.

**Data Structures:** `SpeakingSnapshot { segmentIndex, amplitude, elapsedMs }`.

**Communication with Human Core:** Advisory only; no writes.

**Performance Constraints:** Amplitude-to-visual latency <50ms to avoid visible lip-sync-equivalent lag.

**Failure Handling:** Interrupt mid-word: playback stops immediately, Orb transitions to Listening clip within one frame, no audio underrun artifacts (verified via existing `AudioTrack` stop semantics).

**Testing Requirements:** Interrupt-timing test (<1 frame handoff); amplitude-sync visual regression; full-utterance soak test for memory/handle leaks across many consecutive utterances.

---

### 2.16 Idle Behaviour

**Purpose:** What JARVIS does with no active conversation and no notification — must avoid feeling either dead (static) or annoying (attention-seeking).

**Responsibilities:** Play low-intensity ambient animation (breathing pulse) scaled subtly by current `emotion_vector`; occasionally (low frequency, capped) surface a very small idle expression variation to avoid a perfectly static loop reading as broken; respect battery/thermal budget aggressively since this is the most time-spent state.

**Internal State:** `idle_started_at`, `last_variation_at`, `variation_cooldown`.

**Inputs:** `presence_mode == IDLE`, `emotion_snapshot`, Attention Engine's `presence_confidence`.

**Outputs:** Orb/Avatar idle clip selection with occasional variation trigger.

**Lifecycle:** Entered whenever no higher-priority mode claims Presence Engine; the default resting state.

**Interactions:** Lowest priority in Presence Engine's arbitration table; yields instantly to any other requested mode; Notification Behaviour is the only subsystem allowed to visually interrupt Idle directly.

**Android Implementation Strategy:** Lowest frame-rate/particle-density tier of the Orb renderer (see Mobile Resource Management §2.31) — Idle is where the majority of battery savings must come from, since it's the majority-time state on a 24/7-running companion.

**Data Structures:** `IdleSnapshot { elapsedMs, variationActive }`.

**Communication with Human Core:** None required — pure presentation.

**Performance Constraints:** Target ≤5% average CPU and reduced GPU clock while idle and foregrounded; near-zero when backgrounded (render suspended entirely, see §2.31).

**Failure Handling:** N/A — Idle is itself the fallback state for nearly every failure path in this spec.

**Testing Requirements:** Extended battery-drain measurement over multi-hour idle soak on both reference devices; variation-frequency cap test (must not exceed configured max/hour).

---

### 2.17 Wake Sequence

**Purpose:** The bounded, choreographed transition from ASLEEP to IDLE (or directly to LISTENING if triggered by voice) — the first impression each session.

**Responsibilities:** Play a distinct, brief wake animation/sound; resolve Voice Identity's model load (§2.10) within this window so the first spoken response isn't delayed later; check Attention Engine's presence confidence before deciding whether to also fire Greeting System.

**Internal State:** `wake_started_at`, `wake_trigger_source` enum { WAKE_WORD, APP_LAUNCH, USER_TAP }, `voice_ready`.

**Inputs:** Wake trigger event, Voice Identity load-completion callback.

**Outputs:** Requests `presence_mode = WAKING` then `IDLE` (or `LISTENING`); triggers Greeting System conditionally.

**Lifecycle:** Runs once per cold session start or explicit re-wake from a sleep state; bounded duration (target <1.5s) so it never feels like a loading screen.

**Interactions:** Highest priority after Shutdown in Presence Engine's arbitration; blocks Idle Behaviour from starting until complete; may hand directly into Listening State if the trigger was a spoken wake word (skipping a redundant Idle flash).

**Android Implementation Strategy:** Orchestrated in the Application-scope Presence Engine singleton so it runs identically whether triggered by process cold-start or by an explicit re-wake after backgrounding; voice model warm-load kicked off in parallel with the wake animation, not sequentially, to fit the time budget.

**Data Structures:** `WakeSequenceState { trigger, startedAt, voiceReady, animationComplete }`.

**Communication with Human Core:** None required for the sequence itself; Greeting System (downstream) does read Human Core state.

**Performance Constraints:** End-to-end wake sequence <1.5s target, <3s hard ceiling before falling back to a simplified instant-idle with a logged perf warning.

**Failure Handling:** If voice model load exceeds the wake window, proceed to IDLE anyway with voice marked not-ready; first utterance after that waits on voice readiness rather than the whole wake sequence blocking on it.

**Testing Requirements:** Cold-start timing benchmark on both reference devices; wake-word-triggered vs. app-launch-triggered path both tested; timeout-fallback path explicitly tested.

---

### 2.18 Shutdown Sequence

**Purpose:** The bounded, graceful transition out of active presence — either full app termination or an explicit "sleep" the user requests — ensuring no abrupt cut and no orphaned audio/animation handles.

**Responsibilities:** Play a brief closing animation/tone; ensure any in-flight Speaking State audio either finishes a natural sentence boundary or stops cleanly (never mid-word if avoidable); release renderer and audio resources deterministically.

**Internal State:** `shutdown_started_at`, `shutdown_reason` enum { USER_REQUEST, SYSTEM_BACKGROUND, PROCESS_KILL_IMMINENT }.

**Inputs:** Shutdown trigger event (explicit user command, OS lifecycle `onStop`/low-memory signal).

**Outputs:** Requests `presence_mode = SHUTTING_DOWN` then `ASLEEP`; resource-release calls to Orb/Avatar renderer and audio subsystem.

**Lifecycle:** Highest priority in Presence Engine's arbitration table — always wins any conflict, since a real OS kill signal cannot be deferred indefinitely.

**Interactions:** Preempts every other state instantly; on `PROCESS_KILL_IMMINENT` (OS-driven, not user-driven), skips the choreographed animation entirely and goes straight to resource release — a visual flourish must never risk missing a hard OS deadline.

**Android Implementation Strategy:** Two distinct paths: a **soft shutdown** (user-requested sleep) plays the full choreographed sequence within a generous budget; a **hard shutdown** (`onLowMemory`/`onTrimMemory` critical levels, or `onDestroy` without prior `onStop` warning) skips straight to `release()` calls on renderer/audio, verified against Android lifecycle timing guarantees rather than an arbitrary animation duration.

**Data Structures:** `ShutdownState { reason, softPathEligible: Boolean }`.

**Communication with Human Core:** None — this is a pure presentation/resource concern; Human Core's own persistence (checkpointing to `core_state`) is independent and out of scope here.

**Performance Constraints:** Soft path target <1s; hard path must complete resource release within the OS-granted teardown window (a few hundred ms) with zero exceptions thrown.

**Failure Handling:** Any exception during resource release is caught and logged, never allowed to propagate and crash the teardown; partial release (e.g. audio released but renderer handle leaked) is treated as a bug to fix, not tolerated silently — flagged loudly in logs even though it can't block shutdown.

**Testing Requirements:** Simulated `onTrimMemory(CRITICAL)` test confirming hard-path timing; resource-leak detection across repeated wake/shutdown cycles (LeakCanary-class tooling); soft-path animation-interrupted-by-hard-signal race test.

---

### 2.19 Greeting System

**Purpose:** Decides whether, and what, JARVIS says/shows on first encountering the user in a session — must feel natural, not repetitive or robotic across many sessions per day.

**Responsibilities:** Decide greeting eligibility (time-of-day, session-gap duration, whether this is the Nth wake today); select from a rotation pool to avoid identical repeated greetings (via Companion Memory Hooks, §2.29, tracking only "which greeting variants were recently used," nothing else); suppress greeting entirely on very short session gaps (e.g. app backgrounded 10s then foregrounded — not a meaningful "hello" moment).

**Internal State:** `session_gap_duration`, `greeting_eligible`, `recent_greeting_ids`.

**Inputs:** Wake Sequence completion + trigger source, Attention Engine's presence confidence, Companion Memory Hooks' recent-greeting history.

**Outputs:** A greeting request (text content still generated by Human Core — this system only decides *whether* to prompt for one and supplies presentation-layer variant selection hints, never authors the actual greeting text itself).

**Lifecycle:** Evaluated once per Wake Sequence completion.

**Interactions:** Downstream of Wake Sequence; upstream of Speech Timing/Speaking State if a greeting is triggered; consults Companion Memory Hooks read/write for rotation tracking only.

**Android Implementation Strategy:** Simple rule-based eligibility check (elapsed-time thresholds) plus a small rotation index persisted via Companion Memory Hooks' local store; explicitly does **not** call into Human Core to *generate* greeting content from this layer — it requests Human Core produce a greeting-type response through the normal Human Core Integration channel, keeping content authorship fully on the Human Core side per the Companion/Human boundary in §0.3.

**Data Structures:** `GreetingEligibility { eligible: Boolean, reason }`; `GreetingVariantHistory { recentIds: List<String>, maxAgeMs }`.

**Communication with Human Core:** Sends a "greeting-context" advisory (time-of-day, gap duration) so Human Core's generation has situational grounding; does not dictate wording.

**Performance Constraints:** Eligibility check <5ms, no blocking on Wake Sequence's time budget.

**Failure Handling:** If Companion Memory Hooks' rotation history is unavailable (fresh install/corrupted store), default to eligible with no rotation constraint rather than blocking greeting entirely.

**Testing Requirements:** Threshold-boundary tests (just-under vs. just-over the "meaningful gap" cutoff); rotation non-repetition test across many simulated sessions; graceful-degradation test with missing history store.

---

### 2.20 Notification Behaviour

**Purpose:** Governs how JARVIS surfaces something to the user proactively (not in response to a query) — must respect current conversational state rather than barging in.

**Responsibilities:** Queue notification-worthy events if `presence_mode` is Listening/Speaking/Thinking; surface immediately (with an appropriately low-intensity cue) if Idle; escalate visual/audio intensity only for a small, explicitly whitelisted category of urgent notification types; never stack more than one active notification cue at once (subsequent ones queue).

**Internal State:** `notification_queue`, `active_notification`.

**Inputs:** Notification events from the app-shell / OS notification channel, current `presence_mode`.

**Outputs:** Requests a brief Orb/Avatar "notification" cue distinct from conversational states; may request `presence_mode` transition only from Idle (never interrupts an active conversation state without explicit urgency whitelisting).

**Lifecycle:** Event-driven, queue processed one at a time as `presence_mode` allows.

**Interactions:** Subordinate to Presence Engine's priority table; coordinates with Idle Behaviour (the only state it may interrupt freely) and with Attention Engine (a notification the user visibly dismissed/ignored should not repeat-nag).

**Android Implementation Strategy:** Wraps standard Android notification-channel patterns for anything that must reach the user while the app is fully backgrounded (a system notification, not a Companion Core visual, is correct there); Companion Core's in-app notification cue is reserved for foregrounded-app moments only, avoiding duplicate/competing notification surfaces.

**Data Structures:** `NotificationEvent { id, urgency: enum{LOW, NORMAL, URGENT}, queuedAt }`.

**Communication with Human Core:** Advisory-only; notification *content* originates from whatever backend/tool produced it (out of Companion Core's scope), Companion Core only handles presentation timing.

**Performance Constraints:** Queue processing check on every `presence_mode` transition, negligible cost.

**Failure Handling:** If queue grows unbounded (many notifications while user is away), cap at a sane maximum and coalesce older low-urgency ones into a single "N updates" cue rather than replaying every one individually on return.

**Testing Requirements:** Queuing-during-conversation test; urgency-whitelist interrupt test; queue-coalescing test under simulated notification flood.

---

### 2.21 Animation Controller

**Purpose:** The shared low-level playback engine that actually runs animation clips requested by every higher-level subsystem (Orb, Avatar, Gesture) — a single point of frame-pacing and clip-blending so no two subsystems fight over the same render surface.

**Responsibilities:** Accept clip-play requests with priority, blend/crossfade between the currently playing clip and a newly requested one, own the frame clock all visual subsystems synchronize against.

**Internal State:** `currently_playing_clip`, `blend_progress`, `frame_clock`.

**Inputs:** Clip-play requests from Orb State Machine, Avatar Controller, Gesture System (each tagged with priority and requester).

**Outputs:** Actual rendered frames on the Canvas/Rive surface.

**Lifecycle:** Runs continuously whenever any visual surface is attached; suspended when backgrounded (see §2.31).

**Interactions:** The single arbitration point beneath Orb/Avatar/Gesture — those subsystems decide *what* should play, this one decides *how it actually gets played and blended*.

**Android Implementation Strategy:** For the Orb path, a Compose `Canvas` redraw loop driven by `withFrameNanos`; for the Avatar path, Rive's own state-machine runtime handles blending natively and this controller becomes a thin pass-through that forwards input parameter updates rather than reimplementing blending.

**Data Structures:** `ClipRequest { clipId, priority, requester, blendDurationMs }`.

**Communication with Human Core:** None — purely a rendering concern.

**Performance Constraints:** Must hold frame budget (16.6ms @60fps, 33.3ms @30fps fallback) with a hard watchdog that force-drops particle density or blend complexity if consistently missed (feeds Mobile Resource Management §2.31).

**Failure Handling:** A malformed/unknown clip request is logged and ignored rather than crashing the render loop; frame budget overrun triggers automatic quality step-down before it triggers a dropped frame where possible.

**Testing Requirements:** Frame-time profiling under simulated concurrent clip requests (e.g. Speaking + Gesture firing together); blend-transition visual regression tests.

---

### 2.22 Gesture System

**Purpose:** Small, discrete non-continuous visual cues (a nod, a tilt, a brief glow-pulse "acknowledgment") layered on top of whatever base animation is playing — the Companion Core's equivalent of body language punctuation.

**Responsibilities:** Own a small fixed vocabulary of gestures, each mapped to a triggering context (backchannel from Conversation Presence, sentence-boundary emphasis from Speech Style, a Contextual Expression trigger); rate-limit gestures so they read as intentional punctuation, not tics.

**Internal State:** `gesture_cooldowns`, `active_gesture`.

**Inputs:** Trigger events from Conversation Presence (§2.2), Speech Style (§2.12), Contextual Expressions (§2.25).

**Outputs:** `ClipRequest`s sent to Animation Controller (§2.21), layered/blended over the base state clip rather than replacing it.

**Lifecycle:** Event-driven, each gesture is a short (typically <600ms) one-shot overlay.

**Interactions:** A consumer-facing layer over Animation Controller; must never conflict with the base Orb/Avatar clip's semantic meaning (e.g. no "nod" gesture layered over a Thinking clip's averted gaze in a way that looks contradictory — enforced by a small compatibility table).

**Android Implementation Strategy:** Fixed gesture-to-clip-overlay map, dispatched as high-priority short-duration `ClipRequest`s; compatibility table checked before dispatch to suppress a gesture that would visually contradict the current base state rather than trying to render both.

**Data Structures:** `GestureId` enum (Nod, TiltCurious, PulseAcknowledge, SoftBlink); `GestureCompatibility { baseState, allowedGestures: Set<GestureId> }`.

**Communication with Human Core:** None direct.

**Performance Constraints:** Gesture dispatch decision <1ms; rate limit default max 1 gesture per 3s to avoid visual noise.

**Failure Handling:** Incompatible gesture requests are silently dropped (logged at debug level only, this is expected/routine, not an error).

**Testing Requirements:** Compatibility-table exhaustive test; rate-limit enforcement test under rapid trigger bursts.

---

### 2.23 User Attention Detection

**Purpose:** The concrete signal-collection layer that Attention Engine (§2.3) fuses — kept as its own subsystem because it owns platform-specific sensor/permission handling distinct from the fusion logic above it.

**Responsibilities:** Collect raw device-native presence signals (touch, screen state, proximity sensor if available, mic VAD activity) and normalize them into `PresenceSignal` events; handle permission state changes gracefully.

**Internal State:** `sensor_availability_flags`, `raw_signal_buffer`.

**Inputs:** Android sensor/window/audio callbacks.

**Outputs:** `PresenceSignal` stream to Attention Engine.

**Lifecycle:** Registers platform listeners on app foreground, unregisters on background (to respect battery — see §2.31), re-registers on foreground return.

**Interactions:** Sole upstream data source for Attention Engine; does not itself decide `attention_target`, only supplies raw normalized signals.

**Android Implementation Strategy:** Proximity sensor via `SensorManager` (optional — many devices lack a usable near-field proximity sensor when the screen is on, so this is treated as a bonus signal, not a dependency); touch dispatch via a transparent overlay/gesture detector on the Companion surface; screen state via `PowerManager`/`ACTION_SCREEN_ON`/`OFF` broadcast receiver; mic VAD reused from the existing voice pipeline, not duplicated.

**Data Structures:** `RawPresenceSignal { source: enum, value: Float, timestamp }`.

**Communication with Human Core:** None — feeds Attention Engine only, which handles any advisory messaging upward.

**Performance Constraints:** Listener registration/teardown must not leak across configuration changes (screen rotation); sensor polling kept at the platform's batched/low-power delivery mode, never `SENSOR_DELAY_FASTEST`.

**Failure Handling:** Missing/denied permission for any one signal source simply removes that source from the fusion input set (Attention Engine already handles partial signal availability) rather than failing the whole subsystem.

**Testing Requirements:** Permission-denied path tested per signal source; listener lifecycle leak test across repeated rotation/background cycles.

---

### 2.24 Conversation Flow Behaviour

**Purpose:** Higher-level pacing across an entire conversation (not a single turn, which is Conversation Presence's job) — e.g. recognizing a long multi-turn exchange and subtly reducing idle-style flourish so it doesn't feel distracting during sustained focus.

**Responsibilities:** Track `turns_in_current_session`, `conversation_intensity` (recent turn frequency); feed a "focus mode" hint to Orb/Avatar (slightly reduced ambient flourish) during rapid back-and-forth exchanges; reset naturally after a conversation gap.

**Internal State:** `turns_in_current_session`, `last_turn_at`, `focus_mode_active`.

**Inputs:** Turn-completion events from Conversation Presence.

**Outputs:** `focus_mode_active` flag consumed by Orb State Machine and Idle Behaviour as a subtle rendering-intensity modifier.

**Lifecycle:** Persists for the duration of a session (reset on Shutdown/Wake).

**Interactions:** A soft modifier layered on top of, not a replacement for, the presence-mode-driven rendering already specified — this only tunes intensity, never changes which mode is active.

**Android Implementation Strategy:** Simple rolling-window turn-frequency counter, no ML; threshold-based focus-mode toggle.

**Data Structures:** `ConversationFlowState { turnCount, lastTurnAt, focusMode: Boolean }`.

**Communication with Human Core:** None direct.

**Performance Constraints:** Negligible — counter updates only on turn boundaries.

**Failure Handling:** N/A — purely additive/optional modifier; if disabled or erroring, system behaves identically to baseline per-mode rendering.

**Testing Requirements:** Focus-mode threshold test across simulated rapid-exchange sessions; reset-on-gap test.

---

### 2.25 Contextual Expressions

**Purpose:** Maps specific semantic *situations* Human Core reports (not raw emotion_vector, but discrete situational tags like "apologizing," "correcting a fabrication," "reporting a tool failure") to an appropriate expression/gesture combination beyond what the continuous emotion vector alone would produce.

**Responsibilities:** Maintain a small tag→expression/gesture lookup table; ensure `fabrication_flag`-driven moments get a distinct, honest "self-correction" cue (a specific small gesture/expression) rather than being invisible or masked — reinforcing rather than hiding Human Core's honesty behavior.

**Internal State:** `active_context_tags`.

**Inputs:** Situational tags from CompanionSignal (a small enumerated set Human Core Integration extracts from `current_action`/`fabrication_flag`/tool-result metadata — not free-text interpretation).

**Outputs:** Expression/gesture override requests to Facial Expression System and Gesture System, layered on top of the continuous emotion-driven baseline.

**Lifecycle:** Event-driven, one-shot per tag occurrence.

**Interactions:** A situational override layer above Emotion Expression Layer's continuous mapping — takes precedence for its short duration, then yields back to the continuous mapping.

**Android Implementation Strategy:** Static lookup table, `ContextTag → (ExpressionId, GestureId?)`; explicitly excludes any free-text sentiment analysis of `last_utterance_text` — situational tags must come from structured Human Core signals, not re-interpretation of generated text, to avoid the Companion Core silently second-guessing content it isn't authorized to touch.

**Data Structures:** `ContextTag` enum (Apologizing, SelfCorrecting, ToolFailure, ToolSuccess, GreetingMoment); `ContextExpressionMapping` static table.

**Communication with Human Core:** Reads structured tags only, via Human Core Integration (§2.30) — no direct text parsing.

**Performance Constraints:** Lookup is O(1), negligible cost.

**Failure Handling:** Unknown/unmapped tag is ignored (falls through to continuous emotion-driven expression) rather than defaulting to a possibly-inappropriate guess.

**Testing Requirements:** Full lookup-table coverage test; verification that `SelfCorrecting` cue reliably fires whenever `fabrication_flag` is set (this one is treated as a hard requirement, not best-effort, given its honesty-reinforcing role).

---

### 2.26 Environmental Awareness

**Purpose:** Adjusts presentation for ambient device/environment conditions the user is actually in — screen brightness/dark mode, battery level, time of day — distinct from user-attention signals (§2.3/2.23), which are about the *user*, not the *environment*.

**Responsibilities:** Read system dark-mode/theme setting and battery level; feed these as modifiers into Visual Theme System (dark-mode palette swap) and into throttling decisions already owned by Mobile Resource Management (this subsystem supplies the signal, §2.31 owns the throttling policy).

**Internal State:** `system_dark_mode`, `battery_level`, `time_of_day_bucket`.

**Inputs:** Android `UiModeManager`/`Configuration` dark-mode signal, `BatteryManager` level/charging state, device clock.

**Outputs:** Environment snapshot consumed by Visual Theme System and Mobile Resource Management.

**Lifecycle:** Polled on foreground + on relevant broadcast receiver events (dark mode toggle, battery level change broadcast), not continuously.

**Interactions:** Upstream of Visual Theme System (§2.27); a data source, not a decision-maker, for Mobile Resource Management's throttling policy.

**Android Implementation Strategy:** Standard `BroadcastReceiver` for `ACTION_BATTERY_CHANGED`/config-change callbacks for dark mode; no polling loop needed, purely event-driven.

**Data Structures:** `EnvironmentSnapshot { darkMode: Boolean, batteryLevel: Int, charging: Boolean, timeOfDayBucket: enum }`.

**Communication with Human Core:** None.

**Performance Constraints:** Event-driven, effectively zero continuous cost.

**Failure Handling:** Missing battery broadcast (rare OEM quirk) defaults to "assume not critical" rather than falsely triggering low-battery throttling.

**Testing Requirements:** Dark-mode toggle live-switch test; battery-level threshold test feeding correctly into theme and resource management.

---

### 2.27 Visual Theme System

**Purpose:** Applies a coherent palette/style variant on top of Visual Identity's fixed semantic color roles — supports dark mode, and any future user-selectable theme, without ever touching the underlying identity.

**Responsibilities:** Maintain a small set of theme definitions (each a concrete color mapped to Visual Identity's semantic roles); switch themes on Environmental Awareness's dark-mode signal or explicit user setting; ensure switching is instant and doesn't require a restart.

**Internal State:** `active_theme_id`, `theme_definitions`.

**Inputs:** `EnvironmentSnapshot.darkMode`, explicit user theme preference (if a settings surface exists).

**Outputs:** `active_theme` reference consumed by Orb State Machine, Avatar Controller.

**Lifecycle:** Loaded at app start alongside Visual Identity; re-evaluated on dark-mode broadcast or settings change.

**Interactions:** Sits strictly above Visual Identity (§2.5) in the dependency order — themes remap semantic roles to concrete colors, they never redefine what the roles mean or the underlying shapes/motion language.

**Android Implementation Strategy:** A `Map<SemanticColorRole, Color>` per theme, swapped as a single atomic reference so no renderer sees a half-applied theme mid-frame.

**Data Structures:** `ThemeDefinition { id, roleColors: Map<SemanticColorRole, Color> }`.

**Communication with Human Core:** None.

**Performance Constraints:** Theme swap must be a single-frame atomic change, no visible flicker.

**Failure Handling:** Unknown/corrupted theme id falls back to the default (Visual Identity's own base colors) rather than rendering unmapped roles as a placeholder color.

**Testing Requirements:** Theme-swap atomicity test (no partial-frame render); full role-coverage test per theme definition (no role left unmapped).

---

### 2.28 Audio Feedback System

**Purpose:** Non-speech audio cues (wake chime, notification tone, listening-start/stop earcons) distinct from Voice Identity's spoken-word voice.

**Responsibilities:** Own a small fixed sound-asset library; play the correct earcon for state transitions (wake, sleep, listening-start, listening-end, notification); respect system-level mute/do-not-disturb settings.

**Internal State:** `sound_asset_cache`, `muted`.

**Inputs:** Presence Engine mode transitions, Notification Behaviour trigger events, system mute state.

**Outputs:** Short audio playback commands.

**Lifecycle:** Sound assets preloaded at Wake Sequence time; playback is event-driven and one-shot.

**Interactions:** Independent audio channel from Speech Timing's spoken-word playback — must be mixed correctly (e.g. a listening-start earcon should not play simultaneously with tail-end speech audio in a way that clips or overlaps badly).

**Android Implementation Strategy:** `SoundPool` for short low-latency earcons (kept separate from the `MediaPlayer`/`AudioTrack` instance used for TTS to avoid stream contention); respects `AudioManager.RINGER_MODE_SILENT`/app-level mute setting before playing anything.

**Data Structures:** `EarconId` enum (Wake, Sleep, ListenStart, ListenEnd, NotifyLow, NotifyUrgent); `SoundAsset { earconId, resourceRef, durationMs }`.

**Communication with Human Core:** None.

**Performance Constraints:** Earcon playback latency <100ms from trigger event; preload must not add meaningfully to Wake Sequence's time budget (small asset sizes only).

**Failure Handling:** Missing/corrupted asset for a given earcon logs and skips playback silently rather than crashing the transition it was attached to — audio is always secondary to the underlying state transition succeeding.

**Testing Requirements:** Mute-state respect test; overlap/contention test between earcons and active TTS playback; asset-preload timing benchmark.

---

### 2.29 Companion Memory Hooks

**Purpose:** The Companion Core's narrow, explicitly-scoped local memory — presentation continuity only (recent greeting rotation, recently-used gesture variety, theme preference) — never facts about the world or the user, which remain exclusively Human Core's domain per `core/memory.py`.

**Responsibilities:** Persist a small set of presentation-continuity values locally (device-local storage, not synced to the Human Core's Turso-backed memory tiers); provide read/write access scoped narrowly to the specific subsystems that need it (Greeting System §2.19, Gesture System's variety tracking).

**Internal State:** `local_store_handle`.

**Inputs:** Write requests from Greeting System, Gesture System (rotation/variety tracking only).

**Outputs:** Read responses to those same subsystems.

**Lifecycle:** Store opened at app start, persists across sessions (local device storage), never cleared except on app data reset.

**Interactions:** A deliberately thin, isolated store — explicitly **not** wired into Human Core's memory tiers (`core`/`recall`/`archival` tables in `jarvis.db`) to keep the Human/Companion boundary from §0.3 structurally enforced, not just documented.

**Android Implementation Strategy:** Android `DataStore` (Preferences or small Proto) for simple key-value presentation state — deliberately not SQLite, to keep this store visibly lightweight/different in kind from the Human Core's real memory database, reducing any temptation to grow it into a second memory system.

**Data Structures:** `CompanionPresentationState { recentGreetingIds: List<String>, gestureVarietyCounters: Map<GestureId, Int>, themePreference: String? }`.

**Communication with Human Core:** None — this is explicitly out-of-band from Human Core's memory system by design.

**Performance Constraints:** Read/write operations are infrequent and small; no performance concern beyond standard `DataStore` async-write practice (never blocking the main/render thread).

**Failure Handling:** Store corruption/read failure falls back to empty defaults (no rotation history, default theme) rather than blocking any dependent subsystem.

**Testing Requirements:** Persistence-across-restart test; corruption-fallback test; explicit boundary test confirming no code path in this subsystem can reach Human Core's `jarvis.db` tables.

---

### 2.30 Human Core Integration

**Purpose:** The single, narrow adapter module through which the entire Companion Core reads Human Core state — the structural enforcement point for the read-only, one-directional contract defined in §0.3.

**Responsibilities:** Poll/subscribe to Human Core's `StateCore` (`state.py`) and the 7-step loop's current step at a fixed cadence; translate raw Human Core fields into the `CompanionSignal` contract (§1); send `UserPresenceEvent` advisories outward; enforce that this is the *only* module in the entire Companion Core with any reference to Human Core internals — no other subsystem in §2.1–2.29/2.31 is permitted a direct import of `human_core.py`, `state.py`, or `identity.py`.

**Internal State:** `last_signal`, `last_signal_at`, `signal_staleness_threshold`.

**Inputs:** Human Core's `StateCore` fields (valence/arousal, confidence/uncertainty, motivation, energy, attention_focus), 7-step loop step markers, `self_check()`'s fabrication-correction outcome.

**Outputs:** `CompanionSignal` (broadcast into Emotion Expression Layer §2.9 and Presence Engine §2.1); receives `UserPresenceEvent`s from Attention Engine (§2.3) and Companion Presence subsystems for outward advisory delivery.

**Lifecycle:** Instantiated once at app start; polling/subscription cadence matches the Presence Engine tick rate (§2.1) to keep the whole system on one clock per Design Principle 4.

**Interactions:** The mandatory choke point between the two Cores — every other Companion Core subsystem depends on this one, directly or via Emotion Expression Layer, and none may bypass it.

**Android Implementation Strategy:** Given the current architecture (HF Space FastAPI backend as the cloud brain, with an in-flight on-device Human Core loop per the Offline Core work), this adapter must support **both** a local in-process binding (direct Kotlin↔Python-via-Chaquopy call, if/when the on-device Human Core is the active brain) and a remote binding (HTTP polling/WebSocket against the HF Space's existing `/api/status`-class endpoints) behind one interface, so Companion Core code is written once regardless of which brain topology is active for a given build. This dual-mode requirement is a direct consequence of the still-open architectural pivot discussion (Offline Core Build Plan) and must not assume that decision has been made.

**Data Structures:** `CompanionSignal` (as defined in §1); `HumanCoreBinding` interface with `LocalBinding`/`RemoteBinding` implementations.

**Communication with Human Core:** This entire subsystem *is* the communication layer — see Purpose above.

**Performance Constraints:** Signal refresh at Presence Engine tick rate (≥30Hz foregrounded, reduced backgrounded); remote-binding network calls must never block the render thread — always async with the last-known-good signal used on any stall.

**Failure Handling:** Staleness threshold (default 2s) — if no fresh signal arrives within it, `last_signal` is flagged stale and Emotion Expression Layer (§2.9) falls back to its neutral-snapshot behavior rather than acting on outdated emotion data; a full connection loss (remote binding) surfaces as a distinct low-key Orb cue (not a full error dialog) so the companion visibly "goes quiet" rather than freezing mid-expression.

**Testing Requirements:** Contract test asserting zero other Companion Core module imports Human Core internals directly (enforced via a build-time lint/dependency-graph check, not just code review); staleness-fallback test; local-vs-remote binding parity test (both must produce identical `CompanionSignal` shapes from equivalent underlying state).

---

### 2.31 Mobile Resource Management

**Purpose:** The cross-cutting governor ensuring the entire Companion Core stays within the zero-budget, mobile-first constraint — battery, thermal, and memory — across the two known reference devices (Samsung Tab A7, Realme 9 Pro 5G) and any future device in the fleet.

**Responsibilities:** Own the quality-tier ladder (frame rate, particle density, avatar-vs-orb eligibility, background suspension) and the concrete triggers that step it down (thermal throttling signal, low battery from Environmental Awareness, sustained frame-budget misses from Animation Controller's watchdog); own full render suspension when backgrounded.

**Internal State:** `current_quality_tier`, `thermal_state`, `background_suspended`.

**Inputs:** Android `PowerManager.getThermalHeadroom()`/`onThermalStatusChanged` (API 29+, with a frame-time-based heuristic fallback below that), `EnvironmentSnapshot.batteryLevel`, Animation Controller's frame-budget-miss watchdog, app lifecycle (`onStop`/`onResume`).

**Outputs:** `current_quality_tier` consumed by Orb State Machine, Avatar Controller, Animation Controller as a global multiplier on particle density/frame rate/effect complexity; `background_suspended` flag halting all rendering (but not Human Core Integration's low-rate signal polling, which continues for advisory purposes) when true.

**Lifecycle:** Runs continuously for the app's entire process lifetime; the one subsystem never itself suspended, since it's what decides suspension for everything else.

**Interactions:** A governor sitting "above" every rendering subsystem in §2.5–2.28 — none of them decide their own resource ceiling, they all read this subsystem's current tier and comply.

**Android Implementation Strategy:** Three-tier ladder (HIGH/MEDIUM/LOW), each defining explicit caps: HIGH = 60fps target, full particle density, Avatar eligible if device-tier check passed; MEDIUM = 30fps target, reduced particle density, Orb only; LOW = 15fps target, minimal particles, static-color fallback for glow effects. Tier stepped down automatically on thermal warning or battery <15% non-charging, and stepped back up only after a cooldown period (avoiding tier oscillation/flapping). Background suspension via standard `onStop`/`onResume` lifecycle hooks, releasing GPU-bound resources (Rive/Canvas surfaces) while keeping the lightweight Presence Engine/Human Core Integration state alive for fast resume.

**Data Structures:** `QualityTier` enum (HIGH, MEDIUM, LOW); `ResourceBudget { targetFps, maxParticleDensity, avatarEligible: Boolean }` per tier; `TierTransition { fromTier, toTier, reason, cooldownUntil }`.

**Communication with Human Core:** None direct — a pure device-resource concern; does not throttle Human Core's own on-device inference (llama.cpp), which manages its own resource budget independently as established in the Offline Core work.

**Performance Constraints:** This subsystem's own overhead must be negligible (<0.5% CPU) since it runs continuously; tier-transition decisions debounced with a minimum 10s cooldown to prevent flapping under borderline thermal conditions.

**Failure Handling:** If thermal API is unavailable (pre-API 29 device or OEM restriction), fall back entirely to the frame-budget-miss heuristic from Animation Controller as the sole throttling signal — never assume "no thermal signal" means "no thermal problem."

**Testing Requirements:** Forced-thermal-throttling simulation test (tier steps down correctly, cooldown respected); battery-threshold test; background/foreground resource-release-and-reacquire test with no leaks; sustained multi-hour soak test on both reference devices measuring actual battery drain per quality tier as ground truth (not just theoretical budget compliance).

---

## 3. Cross-Subsystem Invariants (Hard Constraints)

These apply globally and override any single subsystem's local logic if they conflict:

1. **Single mode invariant.** Exactly one `presence_mode` is active system-wide at any instant (Presence Engine, §2.1, is the sole owner).
2. **Read-only Human Core.** No Companion Core subsystem writes to any Human Core file, table, or in-memory object. All Human Core state reaches the Companion Core exclusively through Human Core Integration (§2.30) → Emotion Expression Layer (§2.9)/CompanionSignal.
3. **Content boundary.** No Companion Core subsystem generates, alters, or reinterprets the words JARVIS says. Speech Style (§2.12) may annotate delivery, never content.
4. **Graceful degradation everywhere.** Every subsystem has a defined minimum-viable fallback; nothing in this spec may crash the app or block the conversation loop on a presentation-layer failure.
5. **One clock.** All timing-sensitive subsystems synchronize against the Presence Engine's tick / Animation Controller's frame clock — no independent timers that can drift.
6. **Resource governance is global.** Mobile Resource Management's current tier is binding on every rendering subsystem; none may exceed it locally "just this once."
7. **Memory boundary.** Companion Memory Hooks (§2.29) never grows into a second fact-store; anything that looks like a fact about the world or the user belongs in Human Core's memory tiers, not here.

---

## 4. Open Items for the Implementer (Not Decisions Made by This Spec)

- Voice Identity (§2.10): the British-male-persona vs. currently-staged `en_US-amy-low` Piper voice discrepancy must be resolved before implementation, not by this document.
- Human Core Integration (§2.30): local-vs-remote binding must support both until the Offline Core architectural pivot is formally decided.
- Avatar Controller (§2.7) device-tier threshold values (exact RAM/thermal cutoffs) require empirical calibration on the two reference devices before shipping avatar mode enabled by default anywhere.

---

*End of specification.*
