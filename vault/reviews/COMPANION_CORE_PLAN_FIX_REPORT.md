# Companion Core — Plan Fix Report

| | |
|---|---|
| **Audited document** | `vault/architecture/COMPANION_CORE_IMPLEMENTATION_PLAN.md` (rev 1, 303 lines) |
| **Audit** | `vault/reviews/COMPANION_CORE_PLAN_AUDIT.md` (2026-08-05, PASS WITH ISSUES, composite 7.0/10) |
| **Reference contract** | `vault/architecture/COMPANION_CORE_SPEC.md` (frozen) + `JARVIS_HUMAN_CORE_SPEC.md` (frozen) + `HUMAN_CORE_AUDIT.md` |
| **Fix report date** | 2026-08-05 |
| **Fix result** | Plan revised to 440 lines (rev 2). **All audit findings reviewed and accepted as valid; none rejected.** All incorporated into the plan. |

---

## 1. Review Method

Every audit finding was verified against the rev-1 plan text and the frozen spec:
1. **Re-read the full audit** (M-series missing-requirements table M-1…M-23, architectural problems A-1…A-10, Android risks R-A1…R-A8, performance R-P1…R-P6, memory R-M1…R-M4, battery R-B1…R-B5, integration R-I1…R-I7, Human Core violations V-1…V-4, plus dependencies/testing/scalability notes and the must-fix list).
2. **Checked each finding against rev-1 plan text** to confirm it was absent or partial (acceptance gate), or already covered (rejection gate — none).
3. **Edited only `vault/architecture/COMPANION_CORE_IMPLEMENTATION_PLAN.md`.** No production code, no other files.

**Result:** every finding survived verification. The audit is accurate — the rev-1 plan's gaps were structural (threading, background, device-testing, `current_action`) and each required a real plan change, not a doc retouch.

---

## 2. Accepted Findings and Changes Made

Legend — plan rev-2 anchor: **§1.x** = new Spec section, **§3** = subsystem table row, **§4.x** = integration section, **Phase n** = phase item, **§7** = risks, **§9** = verification.

### 2.1 Release-blocking (audit must-fix 1–4)

| Finding | Verdict | Change made in plan |
|---|---|---|
| **A-1 / R-P1 — No threading/ownership model** (30Hz tick, `StateFlow`, `withFrameNanos`, TTS callbacks have no thread home; repeats Human Core C-1 by construction) | **ACCEPT** | New **§1.6 Threading Model**: four-lane topology (Engine Tick Lane `SingleThreadContext("CompanionTick")`, UI/Render Lane `Main`, I/O Lane `Dispatchers.IO`×2, Audio Lane dedicated), dispatcher-ownership table, hand-off rules per lane, and static Gradle/detekt lane-enforcement lint. Mandatory before any subsystem implementation. |
| **M-1 / M-2 / R-A1 / R-A2 / R-B1 / R-B4 — No background-presence story** (foreground persistence, doze tick, OEM battery-manager kill, wake-word-while-backgrounded, VAD cost, 0.1Hz-vs-once-per-60s contradiction) | **ACCEPT** | New **§1.7 Background Presence Design**: foreground service + silent notification + `FOREGROUND_SERVICE_MICROPHONE` (API31+), doze/App-Standby exemption via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + OEM whitelist flows (Realme/Samsung), process-death restore from persisted `presence_mode`, wake-word duty cycle (0.1Hz bg, full VAD fg), background tick capping, measured ≤5% idle-CPU budget guard. |
| **M-4 / M-9 / M-10 / M-11 / R-B5 — Device-gated acceptance delegated to manual QA with no gate** (7×7 transitions, 24h soak, battery ground truth, cold-start, earcon latency, interrupt<1frame, leak soak) | **ACCEPT** | New **§9 verification includes a new `androidTest` tier** (one reference device in CI): lifecycle suspension, thermal tier-stepping, sensor-leak soak, notification-permission deny path, TTS pre-roll, 24h leak soak, battery-drain ground truth per tier, lane assertions. Manual QA retained only for subjective visual/audio quality. **7×7 exhaustive transition coverage** added to Phase 2. |
| **A-4 / M-12 / R-P6 — `current_action` discriminator under-determined** (no tool-vs-generation distinction; drives 3 presence modes; staleness→mode flapping) | **ACCEPT** | New **§4.2.1 `current_action` discriminator**: explicit state machine (IDLE→RECEIVING_INPUT→TOOL_CALL/GENERATING→DONE→IDLE), conservative fallback (`THINKING`-while-sending) documented, and staleness/dwell hysteresis to prevent Thinking↔Idle oscillation. Designed before Phase 1 contract tests (audit A-10/M-12). |

### 2.2 Architectural problems

| Finding | Verdict | Change made in plan |
|---|---|---|
| **A-2 / M-7 — `RenderIntent` is a dead contract** (declared in Phase 1, never produced/consumed; state bundles emit `OrbRenderParams` directly) | **ACCEPT** | `RenderIntent` promoted to the **actual pipeline type** (§1.2, Phase 1 item 1, Phase 2 item 3): state bundles → `RenderIntent` → renderers, so interrupt/priority/duration-hint semantics exist. |
| **A-3 — Two/three clocks vs Invariant 5 ("one clock")** (tick, frame clock, audio `PlaybackTimestamp` = 3 timing domains) | **ACCEPT** | New **§1.5 CompanionClock**: one master monotonic+epoch clock; frame and audio domains convert to master-monotonic before any cross-domain comparison; 24h drift test. |
| **A-5 / R-I3 / V-1 — §2.19/§2.20 read frozen HC outside the §2.30 choke point** (invariant-2 structural break) | **ACCEPT** | §4.2 reroutes §2.19/§2.20/§2.25 to read HC **only** via `CompanionSignal`/`EmotionSnapshot`; `CompanionSignal` extended with `secondsSinceLastContact`/proactive-behavior fields; build-time dependency-lint contract test enforces no HC-internal imports outside `HumanCoreIntegration`. |
| **A-6 / M-19 / V-3 — `fabrication_flag` sourced from `GuardSoften`/`GuardVeto`** (output vetos ≠ `self_check()`-caught corrections; would misfire `SelfCorrecting` cue) | **ACCEPT** | §4.2 fabrication-flag row gains a **semantic guard**: set only on confirmed caught-correction, otherwise leave unset and log — never infer. Explicitly documented divergence from `GuardSoften`/`GuardVeto`. |
| **A-7 / V-2 — `UserPresenceEvent` delivery conflicts with frozen-HC constraint** (reusing `PresenceManager.noteBackgroundActivity()` is a semantic overload + direct HC-internal call; adding facade method modifies frozen file) | **ACCEPT** | §4.2 commits to the **additive `HumanCore` advisory facade** (`adviseUserPresence`), consistent with the already-established `endSession`/`describe` additive pattern. Direct CC→`PresenceManager` path rejected. |
| **A-8 / R-M1 — `StateBus` global-singleton coupling** (no unsubscribe discipline, test contamination, un-replaceable) | **ACCEPT** | §9 adds **`StateBus` test isolation**: explicit subscribe/unsubscribe, `StateBus.reset()` between tests, cross-suite leaked-subscription assertion. Single-identity coupling recorded as a stated v1 decision (§7). |
| **A-9 — Phase 4 scope cramming** (13 subsystems in 2 weeks, several framework-dependent) | **ACCEPT** | Phase 4 split into **4a** (§2.2/2.3/2.4/2.13/2.14/2.15) and **4b** (§2.16–2.25) reviewable milestones (§5, §8). |
| **A-10 — Phase 0 defers contract decisions that block invariants** (choke-point tests can't be written before `UserPresenceEvent`/`current_action` are resolved) | **ACCEPT** | `UserPresenceEvent` mechanism committed (§4.2) and `current_action` discriminator designed (§4.2.1) **before** Phase 1 contract tests. |

### 2.3 Android lifecycle risks

| Finding | Verdict | Change made in plan |
|---|---|---|
| **R-A3 — Lifecycle seam is view-scoped** (orb keeps rendering off-screen in split-screen/PIP) | **ACCEPT** | §4.3 drives suspension from **`ProcessLifecycleOwner`** (not just `MainActivity` observer); `MainActivity` observer becomes one input among several. |
| **R-A4 / M-5 — Hard-shutdown un-wired** (`onTrimMemory(CRITICAL)`/`onLowMemory`/`onDestroy`-without-`onStop`) | **ACCEPT** | §1.7 hard-shutdown path + §4.3 + Phase 4 §2.18 (IDLE→SHUTTING_DOWN→suspend; timing test, complete within one frame). |
| **R-A5 — Rotation leak surface** (§2.23 sensor listeners survive rotation) | **ACCEPT** | §3 2.23 row: rotation-safe registration bound to lifecycle; **sensor-leak soak test** in §9 androidTest tier. |
| **R-A6 — Wake word while backgrounded** (needs mic-holding FGS + notification + Android12+ indicator) | **ACCEPT** | §1.7: `FOREGROUND_SERVICE_MICROPHONE` + silent notification; privacy indicator handled by FGS type. |
| **R-A7 — Missing `POST_NOTIFICATIONS` (API 33+)** | **ACCEPT** | §3 2.20 + Phase 0 + §9: runtime permission, deny-path degrades to silent + OS-channel fallback. |
| **R-A8 / M-6 — Audio-focus and call interrupts missing** (spec names incoming call as first-class preemption) | **ACCEPT** | §3 2.28 + Phase 3 item 4: `AudioManager.requestAudioFocus`, `ACTION_PHONE_STATE_CHANGED`/Telecom handling, headset-routing, pause-on-call, tests (audio-focus loss/gain). |

### 2.4 Performance risks

| Finding | Verdict | Change made in plan |
|---|---|---|
| **R-P2 — Orb renderer cost at target** (260 particles + 7-pass glow + 3 transitions at 60fps; 30Hz `StateFlow` into broad recomposition) | **ACCEPT** | Phase 2 item 4: **orb recomposition isolation** (own recomposition scope + `derivedStateOf`-gated parameter reads) + frame-rate capped to tier. |
| **R-P3 / M-18 — Speech Timing has no pre-roll pipeline** (<50ms gap jitter / <800ms first-audio unmet by sequential synthesize-then-play) | **ACCEPT** | Phase 3 item 2: **double-buffer pre-roll** — segment N+1 synthesized off-lane while N plays; degrade to whole-utterance on slow devices; latency budgets asserted in tests. |
| **R-P4 / M-20 / R-B2 — Idle CPU/GPU budget unmeasured** (≤5% idle CPU target stated, not engineered) | **ACCEPT** | Phase 2 item 7: **instrumented idle-CPU/battery measurement** on both reference devices at Phase 2 (not Phase 7), with tier-step-down if exceeded. |
| **R-P5 / R-B3 / M-16 — No frame-rate capping to tier** (120Hz display renders 120fps unless throttled) | **ACCEPT** | Phase 2 item 4: frame-rate capping to tier (16.6/33.3ms) enforced against display refresh via `CompanionClock`. |

### 2.5 Memory risks

| Finding | Verdict | Change made in plan |
|---|---|---|
| **R-M2 — Per-utterance playback handles** (`MediaPlayer`/`AudioTrack` ownership, spec leak-soak absent) | **ACCEPT** | Phase 3 item 6: one handle per utterance, deterministic release on completion/interrupt; verified by utterance soak in §9. |
| **R-M3 — `rive-android` AAR ships in all builds from day one** | **ACCEPT** | Phase 0 item 5: **flavor-gated** (`debug`/`companion` product flavor) so the Rive AAR is not in every build. |
| **R-M4 — `DataStore` (Android) has no JVM test story** | **ACCEPT** | Phase 0 item 6 + §9: `PresentationStore` interface + in-memory double for pure-JVM tests. |
| **R-M5 / R-I2 — No dependency on the remote-brain contract; dual-mode promise drifts** | **ACCEPT** | Phase 0 item 5 + §7: versioned remote-binding schema committed alongside CC; both bindings implement one `HumanCoreBinding` interface with schema-version handshake; **remote API surface verified** (must expose all `CompanionSignal` fields ≤2s staleness) before dual-mode is claimed. |

### 2.6 Integration risks

| Finding | Verdict | Change made in plan |
|---|---|---|
| **R-I1 — `last_utterance_text` dual-source ambiguity** (viewmodel vs §2.30 adapter) | **ACCEPT** | §4.2 mapping row: **owned by `HumanCoreIntegration`** (adapter observes `express()` output + bridge reply); ViewModel never writes `CompanionSignal`. |
| **R-I5 — Init-order unspecified in `JarvisEngine`** | **ACCEPT** | §4.3: explicit order `HumanCore.init` → `CompanionCore.init` (§2.30/2.29/2.31/PresenceEngine) → capability/voice; startup ordering test. |
| **R-I6 — `CompanionSignal` assembled from 3 sources with no single ownership** (choke point is a class, not a discipline) | **ACCEPT** | §4.2 **sole-writer discipline**: private `MutableStateFlow`, `@Stable` no-setter data class, unit test asserting no external writes. |
| **R-I7 — TTS pipeline assumption unverified** (VAD frames + `JarvisTts` segment-playback extensibility; verified only after Phase 4 depends on it) | **ACCEPT** | Phase 0 item 3: **verification gate** — confirm Vosk amplitude VAD frames and `JarvisTts` wrappability before Phase 3/4. |
| **M-8 — Local-vs-remote binding parity test** | **ACCEPT** | Phase 1 item 6: parity test (both bindings produce identical `CompanionSignal` for identical HC state). |

### 2.7 Remaining missing requirements

| Finding | Verdict | Change made in plan |
|---|---|---|
| **M-3 — 7×7 exhaustive transition + 24h soak** | **ACCEPT** | Phase 2 item 1 + §9 (soak in androidTest). |
| **M-13 — Mic-permission-revoked mid-session → text-input fallback** | **ACCEPT** | §3 2.13 row + Phase 4 test: degrade to text-input on `PERMISSION_DENIED`/mic failure, never stuck-mic. |
| **M-14 — Notification coalescing cap + dismissed-no-nag** | **ACCEPT** | §3 2.20 row: "N updates" cap, no unlimited stacking; dismissed → no re-nag + feeds Attention Engine. |
| **M-15 — Presence Engine stale-signal → IDLE fallback** (never freeze) | **ACCEPT** | §3 2.1 row: stale `CompanionSignal` → fall back to IDLE. |
| **M-17 — Asset integrity CI check + visual diff for Visual Identity** | **ACCEPT** | §9: CI asset-parse step + visual-diff gate. |
| **M-21 / R-I4 — Multi-surface double-render** | **ACCEPT** | Phase 5 item 3: **single-visible-surface rule** (one active companion surface drives one render loop; navigation hands off ownership; test asserts one loop). |
| **M-22 — `OrbClip` set drift + `Sleep_Dim` semantics lost** | **ACCEPT** | §3 2.6 row: `OrbClip` set aligned with spec (restores `Sleep_Dim`), extensions flagged for spec coordination. |
| **M-23 — ANR/crash watchdogs + CC telemetry** | **ACCEPT** | Phase 7 item 4: `CompanionTelemetry` (severity/tagging/sampling standard) + ANR/crash watchdog, wired to existing `Telemetry` JSONL sink. |

### 2.8 Scalability / dependencies / testing notes

| Finding | Verdict | Change made in plan |
|---|---|---|
| **Single-identity architecture baked into two cores** (StateBus + PresenceEngine + MemoryHooks) | **ACCEPT** (as a decision) | §7: stated as a **documented v1 decision** (multi-profile = future rewrite, not an accident). |
| **Device-testing gap scales badly** | **ACCEPT** | §9 androidTest tier (above). |
| **9–12 engineer-weeks optimistic; Phase 4 at 2 weeks** | **ACCEPT** | §8 + Phase 4 split into 4a/4b; Phases 3–4 overlap where dependencies allow; residual-risk note in §7. |
| **No experiment/feature-flag path for behavior tuning** | **ACCEPT** | Phase 7 item 5: flags in DataStore §2.29, default off (greeting policy, gesture rate, idle variation). |
| **Unstated new framework dependencies** (`lifecycle-process`, AlarmManager/WorkManager, `androidx.media`, `core-ktx` notification perm) | **ACCEPT** | Phase 0 item 5 lists all, pinned. |
| **Version-matrix risk** (Kotlin 2.4.10 + Compose BOM 2024.09.00 vs new deps) | **ACCEPT** | Phase 0 item 5: explicit compatibility-matrix verification + recorded pins. |
| **No logging/telemetry standard** | **ACCEPT** | Phase 7 item 4 `CompanionTelemetry` standard. |
| **No ANR/crash monitoring** | **ACCEPT** | Phase 7 item 4 watchdog. |
| **`StateBus` un-replaceable / cross-core coupling** | **ACCEPT** | §9 isolation + §7 single-identity note. |

---

## 3. Rejected Findings

**None.** Every audit finding was verified and accepted. The audit's independent stance, spec cross-checking, and the concrete, verified gaps in rev 1 (threading, background presence, device-testing void, `current_action` design) were all reproduced against the plan text. No finding was found to be spurious, already-addressed, or out of scope. (V-4, a *credit* to the plan, was not a finding to fix — the plan's restraint against extending the frozen HC was preserved.)

---

## 4. Final Architecture Status

**Revised plan: `vault/architecture/COMPANION_CORE_IMPLEMENTATION_PLAN.md` (rev 2, 440 lines).** All 31 spec subsystems remain mapped (unchanged coverage), now with the audit's gaps closed:

- **Readiness:** the four release-blocking items (threading model §1.6, background presence §1.7, androidTest device gates §9, `current_action` discriminator §4.2.1) are now designed, not deferred.
- **Human Core boundary:** invariant 2 is structurally enforced — all HC reads via §2.30, `UserPresenceEvent` via additive facade, `fabrication_flag` semantically guarded, dependency-lint + sole-writer `CompanionSignal` discipline added. Frozen HC remains unmodified (the only change is the additive `adviseUserPresence` facade, consistent with prior additive growth).
- **Android lifecycle:** foreground service + doze + OEM whitelist + hard-shutdown (`onTrimMemory`/`onLowMemory`) + `ProcessLifecycleOwner`-driven suspension + audio-focus/call handling all specified.
- **Performance/battery:** orb recomposition isolation + tier-capped fps + TTS pre-roll + measured idle-CPU/battery budgets specified with Phase-2 instrumentation (not deferred to Phase 7).
- **Testing:** two tiers — pure-JVM (existing pattern + `StateBus` isolation + `PresentationStore` double + lane lint) and a new **androidTest tier** that converts the spec's device-gated acceptance criteria into CI gates on one reference device.
- **Ownership/scalability:** Phase 4 split into 4a/4b; single-identity stated as a documented v1 decision; feature-flag path for behavior tuning; `CompanionTelemetry` + ANR watchdog.

**Remaining plan-level open items (unchanged, are genuine spec §4 open items — the audit did not object to their existence, only to their sequencing):** voice persona, `energy/motivation/attention_focus`/`confidence` mapping strategy, avatar tier thresholds. `UserPresenceEvent` mechanism and `current_action` design were the two audit-flagged *contract* decisions and are now **committed** (§4.2, §4.2.1) rather than left open.

**Production gate:** implementation must not begin Phase 1 (contract tests) until §1.6 threading model and §4.2.1 discriminator are code-level fixtures, and Phase 4/5 device work must not ship without the §9 androidTest gates green on the reference device.

---

## 5. Files Changed

| File | Change |
|---|---|
| `vault/architecture/COMPANION_CORE_IMPLEMENTATION_PLAN.md` | Rev 1 (303 lines) → rev 2 (440 lines): all accepted findings incorporated; new §1.5–1.7, §4.2.1, §9 androidTest tier, §10 disposition table; 53 audit references; Phase 4 split; per-row subsystem notes. |
| `vault/reviews/COMPANION_CORE_PLAN_FIX_REPORT.md` | This document (new). |

No production source files were modified.
