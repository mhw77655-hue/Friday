# Companion Core — Implementation Plan

| | |
|---|---|
| **Status** | PLAN (rev 2) — no production code written. **Audited 2026-08-05** (`vault/reviews/COMPANION_CORE_PLAN_AUDIT.md`, PASS WITH ISSUES); all valid findings incorporated in this revision. |
| **Contract** | `vault/architecture/COMPANION_CORE_SPEC.md` (frozen, 31 subsystems, 1022 lines) |
| **Constraint** | Human Core is **COMPLETE and FROZEN** — nothing here may redesign, modify, or rewrite it. |
| **Reference devices** | Samsung Tab A7, Realme 9 Pro 5G (zero-budget, mobile-first; Adreno-class GPUs only) |
| **Plan rev 2 date** | 2026-08-05 |
| **Audit rev 1** | 303 lines → rev 2 |

---

## 0. Executive Summary

The Companion Core is the **presentation and interaction layer** over the frozen Human Core: it renders *how* JARVIS is perceived (orb, avatar, voice, gaze, greeting, idle, notification behavior) as a pure function of Human Core state. It is a **read-only consumer** of Human Core state (`CompanionSignal`) and a **write-only producer** of presentation events (`RenderIntent`), with `UserPresenceEvent` advisories flowing back toward the Human Core. It must never generate, alter, or reinterpret the words JARVIS says.

**Headline finding:** the spec is written against an imagined Python Human Core (`state.py`, `human_core.py`, `StateCore`, 7-step loop, `jarvis.db`, Turso). This repo has a **Kotlin** Human Core. Every signal the spec names has a Kotlin equivalent, but **no `CompanionSignal`/`CompanionState`/`CompanionClock` type exists yet**, and the only rendering surface (`Orb.kt`) has **no state machine** — it is label-driven from `DiagnosticsViewModel`/`HomeViewModel` `when()` chains. The plan's central job is therefore:
1. Build the `CompanionSignal` adapter (§2.30) that maps `StateSnapshot` + `StateBus` events to the spec contract.
2. Build the Presence Engine (§2.1) tick loop that owns the 7-state `presence_mode` — the single clock every other subsystem synchronizes to.
3. Rebuild the orb (§2.6) on a proper state machine, reusing the existing Canvas rendering techniques.
4. Layer the remaining 27 subsystems in dependency order, all honoring the 7 hard invariants.

**Reuse is substantial but mostly *conceptual*:** the existing repo provides the signal source (Human Core), the rendering substrate (Compose Canvas orb), the voice pipeline (Vosk wake-word + Sherpa STT + TTS), and the app wiring (JarvisEngine/MainActivity/ConversationViewModel). Almost none of the Companion Core logic itself exists yet — it is a new subsystem, spec-only today.

**Audit outcome (rev 2):** the adversarial architecture audit (2026-08-05) rated the rev-1 plan **7.0/10, PASS WITH ISSUES** and flagged that four structural gaps — (1) no threading model, (2) no background/foreground-service/doze story, (3) device-gated acceptance criteria delegated to manual QA, (4) an under-designed `current_action` discriminator — each independently block release. **All audit findings are accepted as valid** and are incorporated into this revision. The four release-blocking items are closed in rev 2 as follows:

- **Threading model** → §1.6 (four-lane dispatcher topology + hand-off rules + lane-enforcement lint).
- **Background presence** → §1.7 (foreground service + notification, doze strategy, OEM whitelist, process-death restore, measured wake-word duty cycle).
- **Device acceptance gates** → §9 (new `androidTest` tier replacing manual QA for lifecycle/thermal/sensor/audio/leak acceptance).
- **`current_action` discriminator** → §4.2.1 (explicit state machine + documented conservative fallback).

Plus: `RenderIntent` promoted to the real render pipeline (A-2), single-clock arbitration (A-3), §2.30 choke-point enforcement for §2.19/§2.20/`UserPresenceEvent`/`fabrication_flag` (A-5/A-6/A-7/V-1/V-2/V-3), audio-focus + call/headset handling (M-6/R-A8), hard-shutdown wiring (M-5/R-A4), orb recomposition isolation + tier-capped fps (R-P2), Speech Timing pre-roll (R-P3), measured CPU/battery budgets (R-P4/R-B-series), multi-surface arbitration (M-21/R-I4), `OrbClip` drift + `Sleep_Dim` restoration (M-22), observability/ANR-watchdog (M-23), init-ordering (R-I5), sole-writer `CompanionSignal` (R-I6), TTS/VAD verification ordering (R-I7), unstated framework deps + version-matrix verification (R-M1/R-M2), Rive flavor gating (R-M3), DataStore JVM seam (R-M4), remote-brain contract versioning (R-M5/R-I2). Full disposition in §10.

---

## 1. Spec at a Glance (the contract being implemented)

### 1.1 One-directional contract (§0.3)
```
Human Core ──(CompanionSignal, read-only)──▶ Companion Core ──(render/behavior)──▶ User
Human Core ◀──(UserPresenceEvent, advisory)── Companion Core
```

### 1.2 Shared data contracts (§1)
- **`CompanionState`** (in-memory, rebuilt each tick, never persisted): `presence_mode` {ASLEEP, WAKING, IDLE, LISTENING, THINKING, SPEAKING, SHUTTING_DOWN}, `emotion_vector` {valence[-1,1], arousal[0,1], confidence[0,1]}, `attention_target` {USER, NONE, NOTIFICATION, SELF_TASK}, `active_theme`, `last_render_timestamp`, `interrupt_flags`.
- **`CompanionSignal`** (HC→CC, pull): `emotion_vector`, `confidence`, `energy`, `motivation`, `attention_focus`, `current_action` {IDLE, RECEIVING_INPUT, TOOL_CALL, GENERATING, DONE}, `last_utterance_text`, `fabrication_flag`.
- **`UserPresenceEvent`** (CC→HC, advisory): `event_type` {USER_PRESENT, USER_ABSENT, USER_LOOKED_AWAY, USER_TAPPED, USER_IDLE_THRESHOLD, DEVICE_BACKGROUNDED}, `timestamp`, `confidence`.
- **`RenderIntent`** (CC internal → renderers): `visual_state`, `audio_state`, `gesture_id`?, `duration_hint_ms`, `priority`.

### 1.3 Hard invariants (§3) — these override any subsystem-local logic
1. Exactly one `presence_mode` system-wide (Presence Engine is sole owner).
2. No Companion subsystem writes Human Core state; all HC state arrives via §2.30 → §2.9.
3. Content boundary: no subsystem generates/alters/reinterprets JARVIS's words.
4. Graceful degradation: every subsystem has a defined minimum-viable fallback.
5. One clock: all timing synchronizes to the Presence Engine tick / Animation Controller frame clock.
6. Resource governance is global: Mobile Resource Management's tier is binding on all renderers.
7. Memory boundary: Companion Memory Hooks never grows into a fact store.

### 1.4 Open items (§4) — decisions the implementer must make, not the spec
- **Voice Identity**: British-male persona vs. the currently-staged `en_US-amy-low` Piper model — must be resolved before §2.10 ships.
- **Human Core Integration**: must support both a local in-process binding and a remote HTTP/WebSocket binding until the Offline Core pivot is decided.
- **Avatar Controller**: device-tier thresholds (RAM/thermal cutoffs) require calibration on the two reference devices before avatar mode is enabled anywhere.

### 1.5 CompanionClock (audit A-3 — single clock arbitration)
**One master clock, one drift policy.** The spec names two synchronized sources (Presence Engine tick + Animation Controller frame clock) and Speech Timing adds an audio `PlaybackTimestamp` domain. Rev 2 resolves the three-domain drift risk:

| Domain | Source | Role |
|---|---|---|
| **Master** | `CompanionClock` (monotonic `elapsedRealtimeNanos` + epoch wall-clock pair) | The only source of "now"; owns tick scheduling for the Presence Engine (30Hz fg / 1Hz doze), frame-pacing hints, and audio timestamp correlation. |
| **Frame** | `withFrameNanos` (Android choreographer) | Render timing for the Animation Controller; reads `CompanionClock` for phase/animation offsets. Never independently advances state. |
| **Audio** | `PlaybackTimestamp` from Speech Timing | Correlates to `CompanionClock.elapsedNanos` at the moment playback starts, so orb pulse syncs to *actual* audio position, not a guess. |

Drift policy: all derived clocks are converted to master-monotonic before any cross-domain comparison; no subsystem keeps a second free-running clock. A unit test asserts every `PlaybackTimestamp`/frame timestamp round-trips through `CompanionClock` without drift over a 24h simulated run.

### 1.6 Threading Model (audit A-1, R-P1 — release-blocking, now mandatory)
**Mandatory before any subsystem implementation.** The spec's "one clock" + "everything interruptible within one frame" + "no subsystem may block the conversation loop" requires an explicit dispatcher topology:

| Lane | Dispatcher | Subsystems / work | Rules |
|---|---|---|---|
| **Engine Tick Lane** | `SingleThreadContext("CompanionTick")` | Presence Engine tick loop, `CompanionClock`, `CompanionSignal` emission, `HumanCoreIntegration` polling, `EmotionExpressionLayer` fan-out, Mobile Resource Management evaluation, `UserPresenceEvent` forwarding | Never blocks; no I/O; no framework UI/audio calls; <1ms budget per tick. All timing-sensitive subsystems read their inputs *only* from this lane's outputs. |
| **UI / Render Lane** | `Main` (Android UI thread) | `AnimationController` frame loop (`withFrameNanos`), Orb/Avatar Compose rendering, gesture/earcon trigger dispatch | Reads `CompanionState` via `StateFlow.collectAsStateWithLifecycle`; no heavy computation; frame budget 16.6ms (60fps) / 33.3ms (30fps). |
| **I/O Lane** | `Dispatchers.IO` (limited parallelism = 2) | `DataStore` (§2.29), network (remote HC binding), `SoundPool` load, file I/O | No blocking calls on other lanes; all I/O suspends here. |
| **Audio Lane** | dedicated `SingleThreadContext("CompanionAudio")` | `SpeechTiming` synthesis queue, `AudioFeedbackSystem` `SoundPool` play, TTS segment pre-roll pipeline, `AudioManager.requestAudioFocus` | Real-time priority; pre-emptible by Engine Tick Lane for hard interrupts (wake word, call). |

**Hand-off rules:**
- Engine Tick → UI: `StateFlow<CompanionState>` / `StateFlow<EmotionSnapshot>` / `StateFlow<PresenceMode>` (immutable snapshots).
- UI → Engine: `UserPresenceEvent` published to `StateBus` (thread-safe `CopyOnWriteArrayList`).
- I/O → Engine: callbacks post results to Engine Tick Lane via its dispatcher.
- Audio ↔ Engine: `CompanionSignal.current_action`/`fabrication_flag`/`last_utterance_text` flow Engine→Audio; `PlaybackTimestamp` flows Audio→UI for orb sync.

**Static enforcement:** a Gradle/detekt rule forbidding `AudioManager`/`MediaPlayer`/`SoundPool`/framework-UI calls on Engine Tick Lane, and forbidding I/O or heavy computation on UI Lane (the Human Core C-1 blocking-on-Main regression must not recur). This is verified by an instrumentation-time lane-assertion in the androidTest tier (§9).

### 1.7 Background Presence Design (audit M-1/M-2, R-A1/R-A2/R-A6, R-B1/R-B4 — release-blocking, now mandatory)
The spec's "24/7 companion" premise (1Hz tick in doze, background signal polling, wake-word listening) does not survive Android background/doze/OEM policy — and Realme 9 Pro is among the most aggressive OEM kill-policy vendors. Rev 2 mandates:

| Requirement | Solution |
|---|---|
| **Continuous presence while backgrounded** | Foreground Service (`Service.startForeground`) with a low-priority silent notification ("JARVIS companion"). Holds `PARTIAL_WAKE_LOCK` + `FOREGROUND_SERVICE_MICROPHONE` (API 31+; mic indicator covered by the Android 12+ privacy indicator — no extra UI needed). |
| **Doze / App Standby exemption** | Prompt user for "Unrestricted" battery optimization via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; document OEM whitelist flows (Samsung/OnePlus/Realme). Background tick drops to 0.1Hz and renders suspend (§2.31) regardless. |
| **Process-death restore** | On cold start + `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)`, `CompanionClock` and `PresenceEngine` reconstruct from persisted `last_render_timestamp` + `presence_mode` (DataStore §2.29). Wake Sequence resumes from ASLEEP or WAKING based on timestamp delta. |
| **Wake-word duty cycle** | Vosk continuous listening at 0.1Hz tick (1 frame/10s) in background → <0.5% CPU. Foreground: full VAD (20ms frames). Mode transition driven by `ProcessLifecycleOwner` (`ON_START`/`ON_STOP`). **Duty cycle measured** on both reference devices (R-B1/R-B4). |
| **Budget guard** | `MobileResourceManagement` (§2.31) caps background tick at 0.1Hz; `CompanionClock` provides monotonic epoch + frame time. Measured idle CPU must stay ≤5% (R-P4); if exceeded, tier steps to LOW (15fps) and background tick to 0.05Hz. |

**Hard-shutdown path (audit M-5/R-A4):** in addition to the soft `ON_STOP` path (CC `suspend()`), wire `Application.onTrimMemory(TRIM_MEMORY_BACKGROUND)` and `onLowMemory()` to trigger the §2.18 Shutdown Sequence (IDLE→SHUTTING_DOWN→suspend) so background kills are orderly and `CompanionState.presence_mode` persists correctly. Add a §2.18 timing test (shutdown must complete within one frame of trigger).

---

## 2. Existing Repository Assets (what can be reused)

All line counts/state reflect the **current working tree** (Human Core audit fix-set applied, uncommitted).

### 2.1 Human Core (frozen) — the signal source
| Asset | Path | Reuse |
|---|---|---|
| `HumanCore` facade | `humancore/HumanCore.kt` | `snapshot(): StateSnapshot?`, `beginExchange/express/completeExchange/endSession`, `companionBehaviors()`, `describe()`, `isInitialized()` |
| `StateBus` (singleton) | `humancore/bus/StateBus.kt` | `subscribe()`/`publish()`/`auditTrail()` — **zero external subscribers today**; the Companion Core's `HumanCoreIntegration` is the natural first consumer |
| `HcEvent` (18 subtypes) | `humancore/bus/HcEvent.kt` | `MoodUpdated`, `EmotionalRead`, `PresenceChanged`, `GuardSoften/Veto`, `TrustDelta`, `DialogueAppended`, `SelfReflectionGenerated`, … — the event feed for expression/reactivity |
| `StateSnapshot` | `humancore/protocol/StateSnapshot.kt` | `moodValence/moodArousal` (→ `emotion_vector`), `lastUserSignals`, `trust`, `bondDepth`, `secondsSinceLastContact`, `presence`, `totalInteractions` |
| `PresenceState` | `humancore/protocol/PresenceState.kt` | HC presence (`ACTIVE/AWAY/DEGRADED`, `systemTier`) — **not** the same concept as CC's `presence_mode`; feeds Attention/Environment reasoning |
| HC `PresenceManager` advisory feeds | `humancore/mod/PresenceManager.kt` | `noteBackgroundActivity()`, `updateSystemTier()` — **never invoked anywhere** (audit m-3); used by the additive `HumanCore` advisory facade (§4.2.1) |
| `CompanionBehaviorOrchestrator` | `humancore/mod/CompanionBehaviorOrchestrator.kt` | proactive-behavior eligibility, **never invoked** (audit m-10); §2.20/§2.16 can consume `HumanCore.companionBehaviors()` |

### 2.2 App shell / engine
| Asset | Path | Reuse |
|---|---|---|
| `JarvisEngine` (singleton) | `app/JarvisEngine.kt` | owns the app's `HandlerThread`; `init()` order (Telemetry → DecisionGate → ExecutionLayer → HumanCore → capability manifest → voice). Companion Core init hooks in here (§4.3 init order). |
| `JarvisApplication` | `app/JarvisApplication.kt` | calls `JarvisEngine.init(this)`. Extended for `onTrimMemory`/`onLowMemory` hard-shutdown (audit M-5). |
| `MainActivity` | `app/MainActivity.kt` | `ON_STOP → HumanCore.endSession()` (audit M-4 fix) — the same lifecycle seam drives CC background suspension (§2.31). |
| `ConversationViewModel` | `ui/viewmodel/ConversationViewModel.kt` | FIFO turn queue; calls `beginExchange/express/completeExchange`; constructs `SessionContext` from `snapshot()`. |
| `JarvisBrainBridge` | `app/JarvisBrainBridge.kt` | async model bridge; `status` StateFlow ("idle"/"sending (local)"/…) — source for mapping to `current_action`. |

### 2.3 Rendering substrate
| Asset | Path | Reuse |
|---|---|---|
| `Orb.kt` (256 lines) | `app/Orb.kt` | the **entire existing orb renderer** — Compose `Canvas`, radial aura, 7-pass glow, noisy organic ring, 260-particle grain, `rememberInfiniteTransition` pulse/noise/flicker, `animateColorAsState` crossfades. **Reused as the renderer; the state machine is rebuilt.** |
| `OrbState.kt` | `app/OrbState.kt` | current fields (`colorHex`, `pulseIntensity`, `pulseSpeedMs`, `label`, `secondaryRingHex`) — superseded by `OrbRenderParams` from §2.6, but the hex-color/pulse vocabulary is kept |
| `DiagnosticsViewModel` / `HomeViewModel` | `ui/viewmodel/` | currently fabricate orb labels from battery/network/bridge collectors; these `when()` chains are **replaced** by the orb state machine |

### 2.4 Voice pipeline (re-enabled in the working tree)
| Asset | Path | Reuse |
|---|---|---|
| `JarvisVosk` + VoskBridge | `voice/` (per survey) | wake-word detection → Wake Sequence trigger (§2.17); mic VAD reuse for §2.2/§2.23 (spec: "reused from the existing voice pipeline, not duplicated") |
| `JarvisSherpaWhisper` | `voice/` | STT tier-2 → Listening State input |
| `JarvisTts` / TTS bridge | `voice/` | spoken-word playback — §2.11 Speech Timing's playback engine. **Verified before Phase 4** (R-I7): confirm VAD amplitude frames exist and `JarvisTts` can grow segment playback or be wrapped. |

### 2.5 Build / tests
- **Pure JVM unit tests only** (no Robolectric/androidTest/emulator): `src/test/java/com/jarvis/app/humancore/` — 44 tests, `TestHarness.kt` (`ManualClock`, `newGraph`, `FixedModelPort`). Companion Core must extend this pattern; **new harness needed** (its own `ManualClock`/CompanionClock + graph builder). The androidTest tier (§9) is new.
- Gradle: `minSdk 26`, `compileSdk 36`, Kotlin 2.4.10, Compose BOM 2024.09.00, navigation-compose 2.8.4, okhttp 4.12.0, lifecycle-runtime-ktx. **No `DataStore` dependency yet** (needed for §2.29), **no `rive-android`** (needed for §2.7), **no `PowerManager`/thermal or `SensorManager` usage** (needed for §2.26/§2.23), **no `androidx.lifecycle:lifecycle-process`**, **no audio-focus or notification-permission deps**.
- Root has model archives (`vosk-model-small-en-us-0.15`, `sherpa-onnx-whisper-tiny.en`) staged at repo root — these are build inputs for the voice tier.

### 2.6 What does NOT exist yet (the actual build list)
No `CompanionCore` package, no `CompanionSignal`/`CompanionState`/`CompanionClock`/`RenderIntent` types, no Presence Engine tick loop, no orb state machine, no Avatar/Rive, no expression/gesture/animation controller, no visual theme/audio feedback/resource-governor, no DataStore presentation store, no `current_action` mapping, no `UserPresenceEvent` delivery, no foreground service.

---

## 3. Subsystem-by-Subsystem Mapping (reuse / new / integration)

Legend: **REUSE** = existing component consumed as-is or with thin adaptation; **NEW** = new module; **INT** = integration point with the frozen Human Core.

| § | Subsystem | Build kind | Reused from | Notes / integration point |
|---|---|---|---|---|
| 2.1 | Presence Engine | NEW | — | Root coordinator; owns 7-state `presence_mode`; priority arbitration (Shutdown > Wake > user-interrupt > Listening > Speaking > Thinking > Idle); the tick loop + `CompanionClock`. Process-scoped `StateFlow<PresenceMode>`, Application-level singleton, Engine Tick Lane (§1.6). **Stale-signal → IDLE fallback (audit M-15):** if no fresh `CompanionSignal` within the staleness window, fall back to IDLE (never freeze in a stale mode). |
| 2.2 | Conversation Presence | NEW | voice VAD (Vosk) | Turn-owner from VAD frames (~20ms); backchannel cooldown max1/4s; end-of-turn 1.2s silence fallback. Feeds §2.3, gates §2.11. VAD reuse **verified in Phase 0** (R-I7). |
| 2.3 | Attention Engine | NEW | §2.23, HC `PresenceManager` feeds | Fuses touch/screen/mic/notification → `attention_target`; 2Hz fg / 0.1Hz bg. Emits `UserPresenceEvent`. |
| 2.4 | Eye Contact Model | NEW | — | `gaze_vector` (StateFlow<Offset>), `gaze_style` {DIRECT, SOFT, AVERTED}; aversion during Thinking; micro-variation. Pure function, unit-testable. |
| 2.5 | Visual Identity | NEW | orb color vocabulary | Immutable brand root (colors, silhouette, typography feel) — root dependency of §2.6/2.7/2.8/2.27. |
| 2.6 | Orb State Machine | **REWRITE** | `Orb.kt` renderer, `OrbState.kt` | Replace label-driven `when()` chains with a pure `(mode, emotion, gaze) → OrbRenderParams` mapping + `OrbClip` enum. **`OrbClip` set aligned with spec** (audit M-22): {Idle_Breathe, Listening_Ripple, Thinking_Swirl, Speaking_Pulse, Wake_Bloom, **Sleep_Dim** (restored), Shutdown_Fade, Notification_Glow} — extensions flagged for spec coordination. AGSL `RuntimeShader` optional, API33+, Canvas fallback unconditional. |
| 2.7 | Avatar Controller | NEW (gated) | — | Rive (`rive-android` 8.7.0) state machine; same `VisualRenderer` contract as Orb; device-tier + user-toggle gated, default OFF; auto-demotes to Orb. §4 open item (tier calibration). **Flavor-gated AAR** (R-M3). |
| 2.8 | Facial Expression System | NEW | — | expression targets from emotion; must agree with §2.4 gaze. Only meaningful when an avatar (or expression-capable orb) renders. |
| 2.9 | Emotion Expression Layer | NEW | HC `StateSnapshot.moodValence/Arousal` | **INT**: sole reader of the emotion field of `CompanionSignal`; one immutable `EmotionSnapshot` per tick fan-out (`StateFlow<EmotionSnapshot>`); neutral-low-confidence fallback on stale HC data. |
| 2.10 | Voice Identity | NEW | TTS engine | Canonical voice params (timbre, accent, base rate). **§4 open item**: British-male persona vs `en_US-amy-low` Piper staged model. |
| 2.11 | Speech Timing | NEW | TTS, §2.2 | Segment text at punctuation; **pre-roll pipeline** (audit R-P3): segment N+1 synthesized off-lane while segment N plays to meet <50ms gap jitter / <800ms first-audio; degrade to whole-utterance on slow devices. `PlaybackTimestamp` stream via `CompanionClock` (§1.5). Gates Speaking State. |
| 2.12 | Speech Style | NEW | §2.9 | Delivery annotation only (pacing, emphasis cues) — **never content**. Feeds §2.22. |
| 2.13 | Listening State | NEW | — | mode bundle: mic active, VAD, listening earcon; gates §2.2/§2.3. **Mic-permission-revoked mid-session → text-input fallback (audit M-13):** on `PERMISSION_DENIED`/mic failure, Listening degrades to text-input and notifies the user; no stuck mic state. |
| 2.14 | Thinking State | NEW | — | triggers on `current_action` ∈ {TOOL_CALL, GENERATING}; escalation NORMAL/EXTENDED/LONG (~4s steps); never fabricates progress detail. |
| 2.15 | Speaking State | NEW | §2.11 | mode bundle: playback position, interrupt handling (tap-to-stop, wake-word preempt). |
| 2.16 | Idle Behaviour | NEW | §2.3 | idle animation after `last_user_activity_at` threshold; idle earcon budget. |
| 2.17 | Wake Sequence | NEW | Vosk wake word | ASLEEP→IDLE/LISTENING, <1.5s target/<3s hard; parallel voice-model warm-load; conditional Greeting. |
| 2.18 | Shutdown Sequence | NEW | `MainActivity` ON_STOP + `Application.onTrimMemory`/`onLowMemory` (audit M-5/R-A4) | IDLE→SHUTTING_DOWN → suspension; hard and soft paths. Timing test (complete within one frame). |
| 2.19 | Greeting System | NEW | HC `secondsSinceLastContact`, `CompanionBehaviorOrchestrator` | **INT**: reads HC state **only via §2.30/`CompanionSignal`** (audit A-5/V-1); rotation-aware greeting via §2.29. |
| 2.20 | Notification Behaviour | NEW | HC `companionBehaviors()`, app notification channel | **INT**: HC data **only via §2.30/`CompanionSignal`** (audit A-5/V-1); queues; urgency {LOW, NORMAL, URGENT}; never stacks; only interrupts Idle (unless urgency-whitelisted). **`POST_NOTIFICATIONS` (API 33+) runtime permission** (audit R-A7). **Coalescing cap + dismissed-no-nag (audit M-14):** surface at most "N updates" (no unlimited stacking); a user-dismissed notification suppresses re-notification for that item and feeds the Attention Engine. |
| 2.21 | Animation Controller | NEW | `Orb.kt` `withFrameNanos` (reuse pattern) | shared clip playback/blending; frame clock via `CompanionClock` (§1.5); watchdog → feeds §2.31. |
| 2.22 | Gesture System | NEW | — | fixed vocab {Nod, TiltCurious, PulseAcknowledge, SoftBlink}; compatibility table; rate-limit 1/3s. |
| 2.23 | User Attention Detection | NEW | Vosk VAD, PowerManager, SensorManager, touch dispatch | raw `PresenceSignal` collection; registers fg/unregisters bg; no `SENSOR_DELAY_FASTEST`. **Rotation-safe registration** (audit R-A5): listeners bound to lifecycle, unregistered on config change, leak test in androidTest tier. |
| 2.24 | Conversation Flow Behaviour | NEW | — | soft intensity modifier (never changes mode) → Orb/Idle. |
| 2.25 | Contextual Expressions | NEW | **INT**: HC `fabrication_flag`/event tags | `ContextTag` {Apologizing, SelfCorrecting, ToolFailure, ToolSuccess, GreetingMoment} → expression/gesture overrides; **no free-text parsing of `last_utterance_text`**. Source: structured HC signals via §2.30. |
| 2.26 | Environmental Awareness | NEW | PowerManager thermal, battery | battery/thermal/time-of-day/dark-mode snapshot; upstream of §2.27, data source for §2.31. |
| 2.27 | Visual Theme System | NEW | §2.5, dark mode | palette from brand + dark-mode swap; `active_theme` referenced by CompanionState. |
| 2.28 | Audio Feedback System | NEW | TTS engine (separate channel) | `SoundPool` earcons {Wake, Sleep, ListenStart, ListenEnd, NotifyLow, NotifyUrgent}; respects RINGER_MODE_SILENT; preload at Wake; <100ms latency. **Audio-focus + call/headset handling** (audit M-6/R-A8): `AudioManager.requestAudioFocus`; `ACTION_PHONE_STATE_CHANGED` preemption; headset-routing handling. |
| 2.29 | Companion Memory Hooks | NEW | — | **Android `DataStore` (new dep)** for `CompanionPresentationState {recentGreetingIds, gestureVarietyCounters, themePreference}`; deliberately out-of-band from HC memory (invariant 7). **JVM seam** (audit R-M4): `PresentationStore` interface + in-memory double for pure-JVM tests. |
| 2.30 | Human Core Integration | NEW | **INT**: `HumanCore.snapshot()`, `StateBus`, `HcEvent`, `JarvisBrainBridge.status` | THE choke point. Poll/subscribe HC at tick cadence; build `CompanionSignal`; deliver `UserPresenceEvent` outward. **No other Companion subsystem imports HC internals** (build-time dependency lint). Dual local/remote binding per §4. Sole-writer of `CompanionSignal` (audit R-I6). |
| 2.31 | Mobile Resource Management | NEW | `PowerManager.getThermalHeadroom` (API29+), frame-time fallback, app lifecycle | 3-tier ladder HIGH/MEDIUM/LOW (60/30/15fps, particle caps, Avatar eligibility); `background_suspended` flag; the one subsystem never suspended. Concrete tier triggers defined (thermal headroom thresholds, battery %, sustained frame-miss count). |

---

## 4. Dependencies & Integration Points

### 4.1 Dependency graph (build order implied)
```
§2.30 HumanCoreIntegration ──▶ §2.9 EmotionExpressionLayer
                                     │
┌────────────────────────────────────┼──────────────────────────────┐
§2.1 PresenceEngine (tick + mode) ◀──┤  (tick loop drives §2.9 snapshot)
│  │  │  │  │                        ▼
│  │  │  └── §2.31 ResourceGovernor ──▶ §2.6/§2.7/§2.21 (quality caps)
│  │  └───── §2.3 AttentionEngine ◀── §2.23 UserAttentionDetection
│  └──────── §2.2 ConversationPresence ◀── voice VAD
│              │
§2.5 VisualIdentity ──▶ §2.6 OrbStateMachine ─┐
        │                §2.7 AvatarController ─┼─▶ §2.21 AnimationController
        │                §2.8 FacialExpression ─┘
        └──▶ §2.27 VisualTheme ◀── §2.26 Environment
§2.19 Greeting ◀── §2.29 MemoryHooks
§2.28 AudioFeedback ◀── §2.17 WakeSequence
```

### 4.2 The Human Core Integration choke point (§2.30) — exact mapping
| Spec `CompanionSignal` field | Kotlin source |
|---|---|
| `emotion_vector.valence / arousal` | `StateSnapshot.moodValence / moodArousal` (mirror; null → neutral) |
| `confidence` | derived: `1.0 - (1.0 - lastUserConfidence) ...` **GAP** — HC has no single "JARVIS self-confidence"; derive from `moodValence` magnitude + trust, or define a documented heuristic |
| `energy`, `motivation`, `attention_focus` | **GAP** — Kotlin HC has no `energy/motivation/attention_focus` scalars. Options: (a) map from existing `StateSnapshot`/`HcEvent` (e.g. `totalInteractions` cadence, trust/bond); (b) treat as spec-deferred. Flag as a design decision — do not extend the frozen HC. |
| `current_action` {IDLE, RECEIVING_INPUT, TOOL_CALL, GENERATING, DONE} | **GAP** — Kotlin HC has no 7-step loop marker. Map via explicit state machine (§4.2.1) from `JarvisBrainBridge.status` + `ConversationViewModel` send/receive boundaries. Adapter-local, not HC-internal. |
| `last_utterance_text` | **Owned by `HumanCoreIntegration`** (audit R-I1): the adapter observes `ConversationViewModel.express()` output (the styled reply) and `JarvisBrainBridge.lastReply`; the ViewModel never writes `CompanionSignal` directly. Single owner, single source. |
| `fabrication_flag` | **GAP** — HC Guard `GuardSoften`/`GuardVeto` events (fabricated_activity/memory categories) map to "a claim was caught/corrected" → set flag. **Semantic guard** (audit A-6/V-3): only set when the caught-correction is confirmed; otherwise leave unset and log, never infer. |
| `UserPresenceEvent` (outbound) | Deliver via an **additive `HumanCore` advisory facade** (e.g. `HumanCore.adviseUserPresence(event: UserPresenceEvent)`), forwarding to HC `PresenceManager` feeds — **not** direct CC→`PresenceManager` calls (audit A-7/V-2). The additive facade preserves invariant #2 (read-only HC). |

**`CompanionSignal` mutation discipline (audit R-I6):** `HumanCoreIntegration` is the **sole writer** of `CompanionSignal`. It owns a `MutableStateFlow<CompanionSignal>` updated once per tick. All subsystems consume via `StateFlow<CompanionSignal>` (immutable snapshots). No subsystem may mutate the signal directly — the choke point is a *discipline*, enforced by: (a) `CompanionSignal` is a `@Stable` data class with no setters; (b) the `MutableStateFlow` is private to `HumanCoreIntegration`; (c) a unit test asserting no writes to the flow outside that class.

**Hard rule to enforce:** a build-time/dependency-graph check (unit test scanning imports, per spec §2.30 "contract test") asserting no Companion module other than `HumanCoreIntegration` references `com.jarvis.app.humancore.*` internals — only the public facade. §2.19/§2.20/§2.25 read HC state only via `CompanionSignal`/`EmotionSnapshot` (audit A-5/V-1).

### 4.2.1 `current_action` discriminator (audit A-4/M-12/R-I6 — release-blocking, designed now)
Explicit state machine (not heuristic string matching) mapping app-bridge activity to the spec enum:

```
IDLE ──user text submitted──▶ RECEIVING_INPUT
RECEIVING_INPUT ──bridge.status=="sending"/"retrying"──▶ TOOL_CALL (if tool path) else GENERATING
GENERATING ──bridge.status=="ok (local)" + first segment ready──▶ DONE (→ Speaking State)
DONE ──utterance completed / idle timeout──▶ IDLE
```
- **Conservative fallback:** if the tool-vs-generation distinction is unavailable from `JarvisBrainBridge`, default to `THINKING`-while-sending (map both tool and generation to GENERATING) and document the divergence rather than inventing a tool-execution signal. The discriminator is a pure function (state + bridge status → `current_action`), unit-tested across the full transition table including error/retry arcs.
- **Staleness hysteresis (audit R-P6):** the discriminator's output is held for a minimum dwell before `current_action` can flop (Thinking→Idle→Thinking oscillation guard); debounce on bridge-status transitions.

### 4.3 Other integration seams
- **App lifecycle** (background suspension §2.31): **drive suspension from `ProcessLifecycleOwner`** (audit R-A3) — not the view-scoped `MainActivity` observer — because the Presence Engine is Application-scoped and an off-screen orb must keep rendering in split-screen/PIP until the whole process backgrounds. `MainActivity`'s existing `LifecycleEventObserver` (audit M-4) remains as one input among several; extend it to drive CC `suspend()/resume()` in parallel with `HumanCore.endSession()` (additive). Hard-shutdown path on `JarvisApplication.onTrimMemory`/`onLowMemory` (audit M-5/R-A4).
- **Conversation loop**: CC must observe the same turn boundaries `ConversationViewModel` drives — hook `express()` output (the styled reply) as `last_utterance_text`; `completeExchange` timing feeds `current_action → DONE`.
- **Wake word**: `VoskBridge.notifyWakeDetected()` is the §2.17 trigger + §2.2/§2.13 mic-activity signal.
- **System notification channel**: §2.20 foreground cue coexists with OS notifications; `POST_NOTIFICATIONS` permission (API 33+) + a foreground-service notification channel are app-shell concerns integrated in Phase 0.
- **Init order in `JarvisEngine` (audit R-I5):** §2.30 must be initialized **after** `HumanCore.init` and **before** any Companion subsystem consumes it. Explicit order: `HumanCore.init` → `CompanionCore.init` (opens §2.30, §2.29, §2.31, PresenceEngine) → capability-manifest/voice init. Add a startup assertion/ordering test.

---

## 5. Phased Implementation Plan

Each phase ends with something **run/testable** (pure JVM tests + the androidTest tier per §9). All phases honor invariants 1–7.

### Phase 0 — Decisions & scaffolding (0.5–1 wk)
1. Resolve the §4 open items that block later phases: voice persona (British-male vs `en_US-amy-low`), `energy/motivation/attention_focus` and `confidence` mapping strategy, `UserPresenceEvent` delivery mechanism (additive facade), dual local/remote binding shape, avatar tier thresholds.
2. **Resolve audit-mandated decisions:** threading model (§1.6), background presence design (§1.7), hard shutdown path (M-5), `current_action` discriminator (§4.2.1), TTS pre-roll architecture (R-P3), rotation-safe sensor listeners (R-A5), audio focus + call handling (M-6/R-A8), multi-surface arbitration (M-21/R-I4), `POST_NOTIFICATIONS` permission (R-A7).
3. **Verify the voice-pipeline assumptions (R-I7):** confirm Vosk exposes amplitude VAD frames and `JarvisTts` can be wrapped for segment playback. If not, adjust Phase 3/4 design **before** Phase 4 depends on them.
4. Create package `com.jarvis.app.companioncore` (mirroring `humancore/` layout): `contract/` (CompanionState, CompanionSignal, UserPresenceEvent, RenderIntent, enums), `engine/` (PresenceEngine, CompanionClock), `render/`, `audio/`, `sensors/`, `store/`.
5. Add Gradle deps (audit R-M1/R-M2): **androidx datastore-preferences** (§2.29), **rive-android 8.7.0** (§2.7, **flavor-gated** — `debug`/`companion` product flavor, audit R-M3), `lifecycle-runtime-compose` (explicit), **androidx.lifecycle:lifecycle-process**, **androidx.media** (audio focus), **androidx.core:core-ktx** (notification permission), **AlarmManager/WorkManager** (background scheduling). **Verify Kotlin 2.4.10 / Compose BOM 2024.09.00 compatibility matrix** for all new deps; record pinned versions.
6. New pure-JVM harness `companioncore/TestHarness` (ManualClock/CompanionClock, signal fixtures) — same pattern as humancore's. `PresentationStore` interface + in-memory double (audit R-M4).
7. Stand up the **androidTest tier** (§9) with the first instrumentation test (lifecycle suspension).

### Phase 1 — Core contracts + Integration (1–1.5 wks)  [§2.30, §2.9]
1. Implement `CompanionSignal`, `CompanionState`, `RenderIntent` (**as the real pipeline type**, audit A-2), all enums (§1) as immutable Kotlin data classes.
2. Implement `CompanionClock` (§1.5): monotonic + epoch sources, frame-pacing, drift arbitration.
3. Implement `HumanCoreIntegration`: subscribe `StateBus`, poll `HumanCore.snapshot()` at tick cadence, map to `CompanionSignal` per §4.2 using the §4.2.1 discriminator; expose `StateFlow<CompanionSignal>`; implement the **dependency-lint contract test** (no HC-internal imports outside this class). Enforce **sole-writer discipline** (audit R-I6).
4. Implement the additive `HumanCore` advisory facade (`adviseUserPresence`) + `UserPresenceEvent` delivery path (audit A-7/V-2).
5. Implement `EmotionExpressionLayer`: one `EmotionSnapshot` per tick via `StateFlow`, stale→neutral fallback, consistency test (all consumers see identical snapshot per tick).
6. Tests: mapping unit tests (every `CompanionSignal` field from fixture snapshots/events), `current_action` transition table, staleness fallback, dependency-graph lint, sole-writer discipline, **local-vs-remote binding parity test (audit M-8)** — both bindings produce identical `CompanionSignal` for identical HC state (using a mock remote endpoint).

### Phase 2 — Presence Engine + renderer foundation (1.5–2 wks)  [§2.1, §2.5, §2.6, §2.21, §2.31 core]
1. `PresenceEngine`: Application-scoped singleton on the Engine Tick Lane; 7-state mode machine; `TransitionRequest` FIFO + priority table; tick loop (drives §2.9 snapshot, §2.21 frame clock); `StateFlow<PresenceMode>`; transition tests + single-mode invariant test + **7×7 exhaustive mode-transition coverage** (spec requirement).
2. `VisualIdentity`: immutable brand values (reuse orb's violet `0xFF7C5CFF` + palette from `OrbState.kt`).
3. `OrbStateMachine`: pure `(mode, emotion_vector, gaze) → OrbRenderParams` + `OrbClip` enum aligned with spec (M-22, incl. `Sleep_Dim`); unit-test the mapping (state→params) independent of rendering.
4. `AnimationController`: `withFrameNanos` redraw loop; clip request/blend + frame-budget watchdog; **orb recomposition isolation** (audit R-P2): the orb composable is isolated in its own recomposition scope with `derivedStateOf`-gated parameter reads so `StateFlow` emissions at 30Hz do not recompose the whole screen; frame-rate capped to tier (16.6/33.3ms).
5. **Rebuild `Orb.kt`** to consume `OrbRenderParams`/`OrbClip` instead of `OrbState` labels; delete the `DiagnosticsViewModel`/`HomeViewModel` label chains that drove it.
6. `MobileResourceManagement` **core**: tier ladder HIGH/MEDIUM/LOW; **concrete triggers** (thermal headroom thresholds, battery %, sustained frame-miss count); `background_suspended`; global caps consumed by Orb/Animation.
7. **Measure the idle CPU budget (R-P4/R-B1):** instrument 30Hz tick + Vosk always-on + ≥15fps idle render on both reference devices; verify ≤5% idle CPU or step tiers down (0.05Hz bg tick, LOW render).
8. Manual QA on reference devices: fps/thermal at each tier (supplemented by androidTest tier §9).
9. **Migration:** `OrbState.kt` fields folded into `OrbRenderParams`; orb call sites (`HomeViewModel`, `DiagnosticsViewModel`, screens) rewired to the new `StateFlow`.

### Phase 3 — Voice & speech layer (1.5–2 wks)  [§2.10, §2.11, §2.12, §2.28]
1. `VoiceIdentity`: params object + persona resolution (§4 decision); warm-load in Wake Sequence.
2. `SpeechTiming`: punctuation segmentation; **pre-roll pipeline** (audit R-P3): double-buffer synthesis — segment N+1 synthesized off-lane while segment N plays; inter-segment pauses (period > comma > none); arousal-scaled rate; `PlaybackTimestamp` via `CompanionClock` (§1.5). Degrade to whole-utterance playback on slow devices.
3. `SpeechStyle`: delivery annotation (pacing/emphasis) — content untouched; contract test that output text is byte-identical apart from delivery metadata.
4. `AudioFeedbackSystem`: `SoundPool` earcons; mute/DND respect; preload at Wake; overlap test with TTS; **audio-focus + call/headset handling** (audit M-6/R-A8).
5. **Migration:** `JarvisTts` grows segment-playback capability (or a wrapper around it); verified VAD reuse (R-I7).
6. **Playback-handle lifecycle (audit R-M2):** `SpeechTiming`/`JarvisTts` own one `MediaPlayer`/`AudioTrack` per utterance, released deterministically on completion/interrupt (never leaked); verified by the utterance soak in §9.
7. Tests: segmentation correctness, rate bounds, mute-state, earcon/TTS overlap, audio-focus loss/gain, pre-roll gap jitter.

### Phase 4 — Interaction & behavior layer (2 wks)  [§2.2, §2.3, §2.4, §2.13, §2.14, §2.15, §2.16, §2.17, §2.18, §2.19, §2.20, §2.22, §2.24, §2.25]
1. `ConversationPresence` (VAD turn-owner + backchannel) + `UserAttentionDetection` (touch/screen/proximity/mic, rotation-safe) + `AttentionEngine` (fusion → `attention_target`, `UserPresenceEvent`).
2. `EyeContactModel` (gaze StateFlow, aversion in Thinking).
3. State bundles: `Listening`, `Thinking` (escalation), `Speaking`, `Idle` (inactivity), `WakeSequence`, `ShutdownSequence` (hard + soft paths, audit M-5/R-A4).
4. `GreetingSystem` (rotation via §2.29), `NotificationBehaviour` (queue/urgency + POST_NOTIFICATIONS), `GestureSystem` (vocab + compatibility + rate-limit), `ConversationFlowBehaviour` (intensity), `ContextualExpressions` (tag→override, via §2.30 structured tags only).
5. **Phase-size governance (audit A-9):** this is a 13-subsystem phase — split into two reviewable milestones (4a: 2.2/2.3/2.4/2.13/2.14/2.15; 4b: 2.16–2.25) to avoid an ownership/review bottleneck.
6. Tests: turn-owner transcripts, backchannel spam, escalation timing, greeting rotation, gesture compatibility, notification queueing, attention fusion, user-presence-event emission, rotation-leak, shutdown timing, **interrupt <1-frame handoff + amplitude-to-visual latency <50ms (audit M-11)**, mic-permission-revoked → text-input fallback (audit M-13).

### Phase 5 — Environment, theme, memory, governor completion (1 wk)  [§2.26, §2.27, §2.29, §2.31 full]
1. `EnvironmentalAwareness` (thermal/battery/time/dark-mode), `VisualThemeSystem` (palette swap), `CompanionMemoryHooks` (DataStore: greeting rotation, gesture variety, theme pref; invariant-7 tests; JVM seam R-M4).
2. Complete `MobileResourceManagement` (full tier triggers, background suspension wiring via the MainActivity lifecycle seam + hard-shutdown path).
3. **Multi-surface arbitration (audit M-21/R-I4):** a single-visible-surface rule — the active companion surface (the one screen hosting the orb) drives the one render loop; navigation changes hand off ownership; a test asserts only one render loop is ever active.
4. Tests: dark-mode theme swap, DataStore persistence, governor tier-stepping, background-suspension integration, multi-surface arbitration.

### Phase 6 — Avatar (gated) + integration hardening (1–2 wks)  [§2.7, §2.8]
1. `AvatarController` (Rive, flavor-gated) behind device-tier + user toggle (default OFF); `FacialExpressionSystem`; `VisualRenderer` shared contract; auto-demote to Orb on failure/perf-miss. §4 calibration required before enabling anywhere.
2. Full integration: wire `ConversationViewModel` ↔ CC (`last_utterance_text`, `current_action`); navigation embeds the companion surface under the §5 multi-surface rule.
3. End-to-end smoke on reference devices; perf/thermal pass; battery profiling vs §2.31 budgets.

### Phase 7 — Hardening, lint, documentation (1 wk)
1. Enforce all 7 invariants as contract tests (single-mode, read-only-HC lint, content-boundary, one-clock, resource-compliance, memory-boundary).
2. Performance/battery budget tests per spec constraints (2Hz fg / 0.1Hz bg attention; <1ms emotion fan-out; <100ms earcon; 16.6/33.3ms frame budgets; ≤5% idle CPU).
3. Degradation-path tests for every subsystem's minimum-viable fallback.
4. **Observability (audit M-23):** add a `CompanionTelemetry` logger (severity/tagging/sampling standard) + an ANR/crash watchdog that surfaces CC timer/frame issues instead of degrading silently. Wire to the existing `Telemetry` JSONL sink.
5. **Feature-flag path for behavior tuning** (greeting policy, gesture rate, idle variation) — flags in DataStore §2.29, default off.
6. Update `vault/architecture/` docs; write `COMPANION_CORE_IMPLEMENTATION_REPORT.md` mirroring the Human Core audit-report pattern.

---

## 6. Migrations Required

1. **Orb → Orb State Machine (breaking).** Replace `OrbState`/label wiring (`DiagnosticsViewModel`, `HomeViewModel`, `HomeScreen`) with `OrbRenderParams` driven by the Presence Engine. Existing Canvas renderer code is preserved, only its input contract changes.
2. **`JarvisTts` → segment playback.** Speech Timing needs per-segment synthesis/playback + timestamp stream; either extend `JarvisTts` or wrap it. Non-breaking if a wrapper is used.
3. **`MainActivity` lifecycle observer → CC suspension.** The existing `ON_STOP` observer gains a CC `suspend()/resume()` call alongside `HumanCore.endSession()` (additive); `JarvisApplication` gains `onTrimMemory`/`onLowMemory` hard-shutdown.
4. **`DiagnosticsViewModel` orb-label logic** retired in favor of CC state; diagnostics still readable from `StateFlow<PresenceMode>`/`CompanionState`.
5. **New deps:** `datastore-preferences`, `rive-android` (flavor-gated), `lifecycle-process`, `androidx.media`, `core-ktx` (notification perm), WorkManager. No removal of existing deps.
6. **`HcEvent` / `StateSnapshot` are unchanged** (HC frozen) — CC is purely additive on top (plus the additive `adviseUserPresence` facade on the `HumanCore` object, consistent with how it already grew `endSession`/`describe`).

---

## 7. Risks, Open Decisions, and Dependencies

### §4 open items (must be decided before Phase 3/6)
- **Voice persona** (blocks §2.10/§2.11): British-male vs staged `en_US-amy-low` Piper.
- **`energy`/`motivation`/`attention_focus`/`confidence` mapping** (blocks §2.30): the Kotlin HC has no such scalars; derive via documented heuristics or defer to neutral defaults — do not extend the frozen HC.
- **`current_action` mapping** (blocks §2.14/§2.15/§2.24): designed in §4.2.1; conservative fallback documented.
- **`UserPresenceEvent` delivery** (blocks §2.3/§2.30): additive `HumanCore` advisory facade (committed, audit V-2).
- **Avatar tier thresholds** (blocks §2.7 default-enable): empirical calibration on Tab A7 + Realme 9 Pro 5G.

### Audit-driven risks (accepted, now mitigated in plan)
- **Threading / ANR** (A-1/R-P1) → §1.6 four-lane model + lane lint + androidTest lane assertions.
- **Background presence / OEM kill** (M-1/M-2/R-A1/R-A2/R-A6/R-B1/R-B4) → §1.7 foreground service + doze + OEM whitelist + process-death restore + measured duty cycle.
- **Device acceptance gates** (M-4/M-9/M-10/M-11/R-B5) → §9 androidTest tier.
- **`current_action` under-design** (A-4/M-12/R-I6) → §4.2.1 state machine + fallback.
- **§2.30 choke-point violations** (A-5/A-6/A-7/V-1/V-2/V-3) → §4.2 rerouting + additive facade + fabrication-flag semantic guard.
- **Audio focus / calls** (M-6/R-A8) → Phase 3 §2.28.
- **Hard shutdown** (M-5/R-A4) → §1.7 + Phase 4 §2.18.
- **RenderIntent dead code** (A-2/M-7) → promoted to real pipeline in Phase 1.
- **Two/three clocks** (A-3) → §1.5 CompanionClock arbitration.
- **Orb recomposition + fps** (R-P2) → Phase 2 recomposition isolation + tier capping.
- **TTS pre-roll** (R-P3) → Phase 3 double-buffer pipeline.
- **Multi-surface double-render** (M-21/R-I4) → Phase 5 single-visible-surface rule.
- **`OrbClip` drift / `Sleep_Dim`** (M-22) → §3 2.6 aligned clip set.
- **Observability** (M-23) → Phase 7 CompanionTelemetry + ANR watchdog.
- **Init ordering** (R-I5) → §4.3 explicit init order + startup test.
- **Sole-writer `CompanionSignal`** (R-I6) → §4.2 discipline + test.
- **TTS/VAD assumption** (R-I7) → Phase 0 verification gate.
- **Framework deps + version matrix** (R-M1/R-M2) → Phase 0 pinned, verified.
- **Rive AAR size** (R-M3) → flavor gating.
- **DataStore JVM seam** (R-M4) → `PresentationStore` interface + in-memory double.
- **Remote-brain contract drift** (R-M5/R-I2) → Phase 0: versioned remote-binding schema committed alongside CC; both bindings implement one `HumanCoreBinding` interface with a schema-version handshake.
- **Multi-identity coupling** (Scalability) → stated v1 decision: single-identity; multi-profile is a documented future rewrite, not an accident.

### Key dependencies (external)
- **Human Core (frozen)**: `HumanCore.snapshot()`, `StateBus`, `HcEvent`, `PresenceManager` feeds (via additive facade), `companionBehaviors()`, `JarvisBrainBridge.status`.
- **Android framework**: `PowerManager` thermal (API29+), `SensorManager` proximity, `AudioRecord` VAD, `SoundPool`, `DataStore`, lifecycle, `Rive` (new), foreground service + notification.
- **Voice**: Vosk wake word, Sherpa STT, TTS segment playback.
- **Build**: new Gradle deps (pinned + version-verified); pure-JVM harness + new androidTest tier.

### Residual risks (accepted, documented)
- **AGSL/`RuntimeShader`** is API33+ only; minSdk 26 requires the Canvas fallback to be the unconditional path (spec already mandates this).
- **Rive binary size / GPU load** on the low-end reference device (Tab A7 SD695/Adreno 619) — hence device-tier gating + default OFF + flavor gating.
- **Frozen HC** means some spec fields have no native Kotlin source; the adapter must not invent HC state (invariant 2 / "never fabricates").
- **TTS segment timing** is the hardest fidelity risk (speaker latency on low-end devices) — Speech Timing must degrade to whole-utterance playback on slow devices.
- **9–12 engineer-weeks is optimistic** for 31 subsystems; Phase 4 is split (A-9) and Phases 3–4 overlap where dependencies allow.

---

## 8. Estimated Implementation Order (total ≈ 9–12 engineer-weeks sequential)

| Order | Phase | Output | Depends on |
|---|---|---|---|
| 1 | Phase 0 | decisions + scaffolding + deps + androidTest tier + voice-assumption verification | spec §4 resolutions |
| 2 | Phase 1 | contracts + §2.30 + §2.9 + `current_action` discriminator | Phase 0 |
| 3 | Phase 2 | §2.1/2.5/2.6/2.21/2.31-core + orb rebuild + recomposition isolation | Phase 1 |
| 4 | Phase 3 | §2.10–2.12/2.28 voice layer + pre-roll + audio-focus | Phase 1 |
| 5 | Phase 4a | §2.2–2.4/2.13–2.15 (VAD, attention, gaze, speaking/listening/thinking) | Phase 2, Phase 3 |
| 6 | Phase 4b | §2.16–2.25 (idle, wake, shutdown, greeting, notification, gesture, flow, context) | Phase 4a |
| 7 | Phase 5 | §2.26/2.27/2.29/2.31-full + multi-surface arbitration | Phase 2, Phase 4 |
| 8 | Phase 6 | §2.7/2.8 avatar (gated) | Phase 2 (VisualRenderer), calibration |
| 9 | Phase 7 | hardening + contract tests + telemetry + report | all |

Phases 1–3 are the critical path (integration → modes → voice). Phase 4 is the largest feature surface but splits into two reviewable milestones; Phases 3–4 overlap where dependencies allow. Phases 6–7 are independently scheduleable.

---

## 9. Verification Strategy (pure-JVM + new androidTest tier)

**Pure-JVM (existing pattern, extended):**
- Contract tests for all 7 invariants (§3), runnable in CI with zero device.
- Unit tests per subsystem following the humancore pattern (`ManualClock`/`CompanionClock`, fixture signals, deterministic assertion) — state→params maps (Orb, Expression, Theme, Gesture compatibility, ContextTag table), `current_action` discriminator, greeting rotation, attention fusion are pure functions and fully unit-testable.
- Dependency lint (build-time import scan) enforcing the §2.30 choke point + lane rules.
- DataStore via `PresentationStore` in-memory double (R-M4).
- **`StateBus` test isolation (audit R-M1):** every CC test subscribes/unsubscribes explicitly; `StateBus.reset()` + audit-trail clear between tests; a cross-suite test asserts zero leaked subscriptions after the full Companion Core suite (mirrors the Human Core retained-graph finding).
- **Visual Identity asset integrity (audit M-17):** a CI step parses every §2.5 asset and a visual-diff gate compares rendered brand output across commits.

**androidTest tier (NEW — audit M-4/M-9/M-10/M-11/R-B5):** one reference device in CI (start with the Tab A7; Realme 9 Pro 5G as the aggressive-OEM case). Covers the framework-dependent acceptance criteria the spec demands but rev-1 delegated to manual QA:
- **Lifecycle suspension**: foreground/background transitions drive CC `suspend()/resume()`; verify `background_suspended` + foreground-service lifecycle.
- **Thermal tier-stepping**: simulate thermal headroom change → tier steps and renderer caps respond.
- **Sensor leak soak**: rotation across 100 config changes → zero leaked sensor listeners (R-A5).
- **Notification-permission**: `POST_NOTIFICATIONS` deny path degrades to silent + OS-channel fallback (R-A7).
- **TTS pre-roll**: gap jitter <50ms / first-audio <800ms on device (R-P3).
- **Leak soak**: 24h presence soak + battery-drain ground-truth per quality tier on both reference devices (spec acceptance).
- **Lane assertion**: instrumentation asserts no framework/audio call on the Engine Tick Lane and no I/O on the UI Lane (A-1/R-P1).
- **Wake-word duty cycle**: measured Vosk CPU/current in background (R-B1/R-B4).
- **Manual QA** retained only as supplementary (subjective visual/audio quality), not for acceptance criteria.

---

## 10. Audit Finding Disposition (all findings accepted in rev 2)

The audit (`COMPANION_CORE_PLAN_AUDIT.md`, 2026-08-05) is an adversarial review of rev 1. **Every finding is valid** — each was verified against the rev-1 plan text and the frozen spec. No finding is rejected. The fix-report `vault/reviews/COMPANION_CORE_PLAN_FIX_REPORT.md` records the full per-finding disposition (finding → verdict → change). Summary:

| Category | Key findings | Disposition |
|---|---|---|
| Release-blocking | A-1 threading; M-1/M-2/R-A1/R-A2/R-A6 background presence; M-4/M-9/M-10/M-11/R-B5 device gates; A-4/M-12 `current_action` | Incorporated as §1.6, §1.7, §9, §4.2.1 (rev 2) |
| Architectural | A-2 RenderIntent dead; A-3 clocks; A-5/A-6/A-7 choke-point; A-9 phase size | §1.2/Phase 1, §1.5, §4.2, Phase 4 split |
| Human Core boundary | V-1/V-2/V-3 invariant-2 risks; V-4 credit | §4.2 rerouting + additive facade + semantic guard |
| Android lifecycle | R-A4 hard shutdown; R-A5 rotation leak; R-A7 notification perm; R-A8 audio focus | §1.7, Phase 2/4/3, Phase 0 |
| Performance | R-P1 threading; R-P2 orb recomposition; R-P3 pre-roll; R-P4 CPU budget | §1.6, Phase 2, Phase 3, Phase 2 |
| Battery | R-B1/R-B4 doze + duty cycle | §1.7, Phase 2 measurement |
| Memory | R-M1 bus coupling; R-M3 Rive gating; R-M4 DataStore seam | §7, Phase 0, §3 2.29 |
| Dependencies | R-M2 version matrix; R-M5 remote contract | Phase 0 |
| Testing | M-4/M-9/M-10/M-11/R-B5 device surface | §9 androidTest tier |
| Scalability | multi-identity coupling; phase optimism; feature flags | §7 stated v1 decision; Phase 4 split; Phase 7 flags |
| Spec coverage | M-21 multi-surface; M-22 OrbClip drift; M-23 observability | Phase 5, §3 2.6, Phase 7 |
