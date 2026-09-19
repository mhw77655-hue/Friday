# JARVIS — Full System Report
**Date:** 2026-08-08
**Scope:** Complete discovery-only survey of `/mnt/sdcard/jarvis-repo` — code, configs, docs, logs, assets, downloads, and vault contents.
**Rule:** Nothing was modified during the survey. On-device runtime behavior and the post-APK source edits are marked **UNKNOWN** rather than guessed.

---

## 1. Executive Summary

**JARVIS is an offline-first, local AI companion** targeting a single Android device under Termux + proot. It exists as **two parallel surfaces**:

1. **The Android app** (`mobile/`, Kotlin + Jetpack Compose) — the *repository's* subject. It implements a "liquid OS" architecture: a **Human Core** (personality/identity/emotion engine), a **Companion Core** (presence engine + reactor orb), a **latency-first conversation layer**, a **model fabric** (local llama/Ollama/remote adapters + heuristic fallback), a full **on-device voice pipeline** (Vosk wake-word → Whisper fallback → Android TTS), and a **governance layer** (manifest, tiers, approval gate). This is genuinely wired and mostly complete; it compiles to a 165 MB APK built **2026-08-08 10:27**.
2. **The web-first "body"** (Python, living in Termux home `~/jarvis`, **not in this repo**) — the vault documents describe it as the *live, working* runtime (browser shell `:8130`, Consciousness Gateway `:8140`, frozen `human_core.py`, llama-server `:8080`, Piper TTS, state bus `:8123`). Its code, databases, and tests are **outside this repository**.

**Key state findings:**
- The git repo tracks only the **Android app up to commit `1976d37` (Human Core)**. The **entire Companion Core, latency layer, model fabric, env/runtime/session/inbox/approvals/alerts, and the `vf/` visual foundation are uncommitted** (working tree: 42 modified, 4 deleted, 9+ untracked paths) — including all its tests.
- The **whole documentation vault (`vault/`) is untracked** — the architecture specs, design system, milestone reports, and audits exist only on disk.
- The **brain defaults to a heuristic (keyword) adapter**; a real LLM requires llama-server/Ollama at `127.0.0.1`, and no GGUF is present in the repo (the Qwen3 GGUF in `vault/just_downloaded/` is unwired).
- **Voice is structurally enabled** in code (started at boot), contradicting a committed "disable voice" milestone and a stale capability-manifest limitation note.
- The repo holds **~8.4 GB** total (incl. 1.6 GB `.git`): model stores, a **4.08 GiB "just_downloaded" pile that is 100% unused by code**, and a `local-repo` AAR that is **never resolved** (coordinate mismatch).
- No hard build blockers: the tree produced a debug APK today. The dominant risks are **no reproducible snapshot** (uncommitted), **model duplication in git**, **stub cloud/memory layers**, and the **voice-loop being gated on one screen's ViewModel**.

---

## 2. Repository Inventory

| Location | Contents | Size |
|---|---|---|
| `mobile/` | Android app (`mobile/app`, Kotlin+Compose), source + tests + `build/` | 1.1 GB (incl. build output) |
| `mobile/app/build/outputs/apk/debug/app-debug.apk` | Latest debug APK (arm64-v8a, ~165 MB) | built 08-08 10:27 |
| `mobile/app/src/main/assets/` | Bundled models: whisper-tiny.en (int8 encoder/decoder/tokens) + full vosk small-en-us-0.15; `capability_manifest.json` | ~167 MB |
| `mobile/app/src/main/java/com/jarvis/app/` | ~158 `.kt` files, ~25 packages | — |
| `vault/` | Obsidian vault (untracked): `architecture/` (7 specs), `agent-logs/` (9 milestone reports), `reviews/` (8 audit/fix reports), `design-system/` (Visual Bible + 20 system docs + report), `decisions/`, `07-system/`, `.smart-env` caches, `just_downloaded/` | 4.1 GB total |
| `vault/just_downloaded/` | 4.08 GiB of unused model/asset downloads (see §10) | 4.1 GB |
| `models/sherpa-onnx-streaming-zipformer-en-2023-06-21/` | Streaming Zipformer ASR (host-side Python tier-2, **not in APK**): int8 encoder + fp32 decoder/joiner + tokens + test wavs + `export-*.sh` | 522 MB (+484 MB tar.bz2) |
| `sherpa-onnx-whisper-tiny.en/` | Whisper tiny.en fp32 + int8 (fp32 pair unused; int8 pair duplicated into assets) | 245 MB (+113 MB tar.bz2) |
| `vosk-model-small-en-us-0.15/` | Vosk Kaldi ASR (full tree; duplicated verbatim into assets) | 68 MB (+40 MB zip) |
| `jniLibs/` | sherpa-onnx native libs, **4 ABIs** (only arm64-v8a packaged) | 137 MB |
| `local-repo/` | Local Maven: `com.k2fsa.sherpa.onnx:sherpa-onnx-android:1.13.2.aar` — **coordinate-mismatched, effectively unused** | 55 MB |
| `core/`, `manifest/`, `standards/`, `tests/`, `compute_node.py`, `cascade_controller.py`, `patch_user_model.py`, `test_all_three.py`, `test_vosk_grammar.py`, `phase0_report.txt` | Python Phase-1 governance/brain scaffolding (host-side, **not used by the app**) | ~100 KB |
| `hf-space-source/` | **Empty** (gitignored; submodule never checked out) | 0 |
| `logs/`, `snapshots/` (gitignored), `none` (0-byte), `whisper_out.txt` | Runtime-log/misc artifacts | tiny |
| `.git/` | 208 tracked files incl. every model binary | 1.6 GB |

Git history (12 commits, Mar–Jul 2026): Phase-0 scaffold → on-device signals (network/battery/notification/Shizuku/mic) → voice pipeline → HF-token removal → orb-12-state wiring → **Human Core subsystem** (HEAD).

---

## 3. Actual Architecture (what the code does)

**Boot chain:** `JarvisApplication.onCreate` → `JarvisEngine.init` (synchronous singletons + engine-thread startup) → `MainActivity` (Shizuku, mic permission, `CompanionCoreHolder`, nav host) → `ProcessLifecycleOwner` drives Companion Core background/foreground.

`JarvisEngine` builds in order: `FileStorage` → `ModelManager` (default provider = **HeuristicAdapter**) → `SessionManager` / `LocalTaskInbox` / `LocalApprovalQueue` / `LocalAlertStore` → wires Human Core model seam (`ModelBackend.requestChat = modelManager.requestChat`) → then on the engine thread: `Telemetry`, `ApprovalGate`, `TaskExecutor`, **`HumanCore.init`**, capability manifest load, **`CompanionCoreHolder.init` (30 Hz tick lane)**, then **voice engines started unconditionally** (`JarvisVosk.start()`, `JarvisSherpaWhisper.start()`, `JarvisTts.start()`), `LatencyLayer.init`, `RuntimeBinder`, `LiquidEnvironmentManager`, `BootstrapManager.create`, then async `environmentManager.initialize()`. Bootstrap does **not** auto-run; LAN discovery only starts if bootstrap runs.

**Layered subsystems (all real, not stubs):**
- **Human Core** (`humancore/`, committed at HEAD): `HumanCoreGraph` wiring ~25 modules; pipeline `PerceptionPass → ExpressionPass → IntegrationPass`; `store/` has 6 file-backed stores (identity, personality, mood, relationship, adaptation, dialogue) via `FileStorage` (atomic temp+rename, per-store locks, fsync); `bus/` StateBus; `protocol/`; `algo/`; `fallback/FallbackIdentity`.
- **Companion Core** (`companioncore/`, untracked): `PresenceEngine` (7-state `presence_mode` arbitration with priority table + stale fallback), `HumanCoreIntegration` as the **sole §2.30 choke point** (structural `DependencyLintTest` enforces read-only contract), `CurrentActionDiscriminator`, `EmotionExpressionLayer`, `OrbStateMachine` → `AnimationController` → `OrbRenderParams`, `MobileResourceManagement` (HIGH/MEDIUM/LOW tier ladder: 60/30/15 fps, particle caps, thermal/battery step-down, `background_suspended`), `PresenceOrb` (Compose) rendering through the frozen `vf.orb.OrbRenderer` + `MotionEngine`.
- **Latency layer** (`latency/`): `LatencyLayer`/`LatencyPipeline` = the conversation authority. `Phase {IDLE, ACKNOWLEDGING, THINKING, THINKING_LONG, REPLYING}`; fast path (pre-warmed ack speech, orb→THINKING, model warm-prime) + single-thread slow path (`HumanCore.beginExchange` perception → pending-turn FIFO correlation → Obsidian write → `ModelManager.send` → `onReplyReady` → `HumanCore.express` → styled reply → `completeExchange` async + speech). `AckSpeech` (mood-congruent pre-synthesized WAV pool), `WarmupEngine` (1-token ping every 60 s), `SpeechEngine` (cloud-then-device).
- **Model fabric** (`model/`): `ModelManager` + `ModelProvider` interface + adapters — `LlamaCppAdapter` (`127.0.0.1:8080`, OpenAI + legacy), `OllamaAdapter` (`:11434`), `RemoteJarvisAdapter` (`:8150` OS Portal), `HeuristicAdapter` (default). `CLOUD` and `NONE` both map to heuristic (cloud = stub).
- **Liquid OS plumbing**: `env/` (rich `EnvironmentProfile` — 9 built-in profiles, JSON-persisted, `LiquidEnvironmentManager` activates provider+runtime; the permission/tools/memory/latency policy hooks are declared but **conceptually unenforced**), `session/`, `bootstrap/` (6-step sequence incl. Shizuku + llama-server health check), `runtime/` (`RuntimeBinder`, `LanRuntimeDiscovery` = skeleton, mDNS not implemented), `inbox/`, `approvals/`, `alerts/` (JSONL stores), `approval/` (`ApprovalGate` risk-tiered + `TaskExecutor` with snapshot-before-write).
- **Sensors/actions**: `JarvisNotificationListener` (real), `ShizukuBridge` (real), `ScreenBridge` (Shizuku user-service → `JarvisShellService` hand-written AIDL → `uiautomator dump` XML parse), `ObsidianSync` (appends turns to `/storage/emulated/0/JarvisSync/vault/JARVIS/YYYY-MM-DD.md`), `Telemetry` (JSONL).
- **UI**: custom bottom bar, 16 routes, ~13 screens. Real: Home (PresenceOrb), Conversation, System (Diagnostics), Model Manager, Environment, Sessions, Task Inbox, Approvals, Alerts, Bootstrap, Tools, Settings, MoreSheet. Placeholders: Missions, Memory, Sandbox (renders `VisualFoundationDemo`).

**Voice loop reality:** `MainActivity` starts `JarvisMic` on permission. The wake-word → command → action loop lives **only in `DiagnosticsViewModel`'s collectors** (created when `SystemScreen` composes); it strips "jarvis", tries `CloudVoiceClient.transcribe` (stub → null), then routes `screen…` → `ScreenBridge` + `LatencyLayer.ackOnly`, else `LatencyLayer.onUserInput`. The **ConversationScreen MIC button is an explicit no-op** (`/* wire to Vosk mic pipeline — not yet connected here */`).

---

## 4. Intended Architecture (what the vault specifies)

The vault is remarkably consistent. Master doctrine: **"two cores, one consciousness."** The **Human Core** (declared COMPLETE and FROZEN) decides *who JARVIS is* (identity, values, cognition, memory, decisions); the **Companion Core** decides *how that identity is perceived* — it is "a renderer and behavior layer over Human Core state, never a second brain," a **read-only consumer**, and is specified to contain **31 subsystems** (§2.1–§2.31 of `COMPANION_CORE_SPEC.md`: presence, attention, eye-contact, visual identity, orb, avatar, facial expression, emotion expression, voice identity, speech timing/style, listening/thinking/speaking behavior, idle/wake/shutdown sequences, greeting, notification, animation controller, gesture, user-attention detection, conversation flow, contextual expressions, environmental awareness, theme, audio feedback, memory hooks, **§2.30 Human Core Integration choke point**, resource management). One-directional contract: `Human Core ─(CompanionSignal, read-only)▶ Companion Core ─(render)▶ User` and `Human Core ◀(UserPresenceEvent, advisory)─ Companion Core`.

The **Cognitive Core** (Python, new) "reasons only, never owns personality"; the **JRE Runtime** "decides HOW work executes, never WHAT"; the **JARVIS OS** "owns surface, not soul." The **web-first pivot** makes the browser the body (Android = STT bridge only). The **Identity Engine** (`JARVIS_IDENTITY_ENGINE_SPEC.md`, "permanent design law") defines identity as a **reactor core, not a face**, with **exactly eight canonical states** (Idle/Waking/Listening/Thinking/Speaking/Warning/Critical/Sleep), inertia/elasticity motion laws, "panic never increases brightness/speed," and a hard never-list (no face, no emoji, no speech bubble, no gamification, no red except Critical).

Human Core spec: five stores with **one writer each**, four data axes on **timescale separation** (identity: never; personality: weeks–months, written only by Growth & Evolution Engine; mood: minutes–hours, written only by Emotional Regulation; relationship: days–months), six personality traits (directness, warmth, humor-frequency, formality, proactiveness, curiosity), two-axis mood (valence/arousal), trust vs bond as distinct scalars, and an explicit doctrine that **world memory is NOT part of the Human Core**.

Milestone reports claim (web body, outside repo): Live-loop integration, Web Shell M1+M2, Consciousness Gateway + Continuity Brain M1, JRE Runtime M1, JARVIS OS M1 — all "built and verified" 2026-08-06/07 (test counts 38/45/116/165 in `~/jarvis`, not this repo). For the **Android side**, the reports claim: Human Core committed (44 tests), Companion Core Phase-1 (99 tests), Visual Foundation (135→149 tests), and an audited-but-unreported Phase-2 (217→219 tests).

---

## 5. Reality vs Design

| Design (vault) | Reality (repo) | Verdict |
|---|---|---|
| Human Core frozen/complete | Kotlin Human Core committed, 25 modules, real stores | **Matches** spec at high fidelity; audits (C-1…m-18) found defects, fix report claims all resolved |
| Companion Core = 31 subsystems | Android implements the **core ~10** (presence, integration, emotion, orb SM, animation, resource mgmt, clock, signal mapping). Avatar, gestures, eye contact, voice identity, greeting, wake/shutdown sequences, contextual expressions, attention detection: **absent** | **Partial** — Phase-2 core present, Phase-3+ not begun |
| "Two cores, one consciousness" read-only contract | `HumanCoreIntegration` sole writer of `CompanionSignal`, `DependencyLintTest` enforces | **Implemented** |
| One canonical identity (8 states, blue #2E9BFF reactor) | App uses **12/13 VF reactor states**; brand accent is **violet #7C5CFF**, not the design-system blue | **Deliberate deviations** (documented in Theme.kt/ReactorEngine) |
| Web-first body is "the live runtime" | That runtime is **not in this repo**; the repo's Android app is the parallel surface the same docs call "in-progress, not yet connected to the live body" | **Split brain** between repo and `~/jarvis` |
| Voice: milestone says "disabled, crash root-caused" | Code **enables voice at boot**; capability manifest (08-04) still says "voice_pipeline_v1 retired" | **Doc-vs-code contradiction**; manifest note stale |
| Milestone "Stopped after Phase1" / plan header "no production code written" | Working tree contains a large, **audited-and-fixed but uncommitted** Phase-2 + Liquid OS | **Vault lags the tree** (the Phase-2 audit itself flags this) |
| `DecisionGate`/`ExecutionLayer` were the action path | Python + Kotlin versions both exist but are **dead**; superseded by `approval/ApprovalGate`+`TaskExecutor` (which cite them as "retired") | Superseded, harmless |
| Critical pulse: Identity Engine says "slower and heavier"; Visual Bible says faster (2 Hz) | Android VF wired **2 Hz sharp pulse**; the web reactor implemented the slow-heavy variant | **Documented conflict**, each side cites a different frozen doc |
| Cloud is "future, never a dependency" | Consistent — cloud is stub everywhere; `HF_TOKEN` BuildConfig generated but **never read**; `hf-space-source/` empty | Matches, but the token-removal "fix" left dead scaffolding |

---

## 6. Voice System

- **Real:** `JarvisMic` (16 kHz `AudioRecord`, energy endpointing `RMS≥35`, 700 ms silence hang, rolling 10 s buffer for retries); `JarvisVosk` (**two-recognizer**: grammar-constrained wake `["jarvis","hey jarvis","[unk]"]` then free-form command recognizer; model copied from assets); `JarvisSherpaWhisper` (whisper-tiny.en int8 one-shot fallback); `JarvisTts` (Android `TextToSpeech`, forces Google engine, best-voice selection, **SSML pacing wrap**, pre-synthesis WAV cache); singleton bridges (`VoskBridge`/`SherpaBridge`/`TtsBridge`); `JarvisNotificationListener` + `NotificationBridge`; `ShizukuBridge`; `ScreenBridge`.
- **Stub:** `CloudVoiceClient` — `transcribe()` and `speak()` **always return null** (6-line file); all voice falls through to local.
- **Enabled?** Yes, structurally: `RECORD_AUDIO` in manifest, engines started unconditionally in `JarvisEngine.init`, mic started by `MainActivity`. The **actual loop** runs only after `SystemScreen` (DiagnosticsViewModel) is composed; the Conversation MIC button is unwired. Historical native-crash episode (commit `d0735ac`, Jul 28) is **reverted at HEAD**.
- **Separate surface:** the web body uses Android **`SpeechRecognizer` (offline) via an `stt-bridge` app on `:8765`** + **Piper TTS** (`en_GB-alan-medium`, `termux-media-player`) — none of that is in this repo.

---

## 7. Visual System

- **Spec (complete, vault):** `JARVIS_VISUAL_BIBLE.md` (frozen) + 20 `design-system/system/` docs (colors, materials, typography, spacing, components, HUD, reactor, motion, particles, lighting, sound, voice-visualizer, phone/tablet/desktop/watch/AR UI, animations, icons, tokens) + `DESIGN_SYSTEM_REPORT.md` (status COMPLETE). Markdown only — no code.
- **Implementation (`vf/`, untracked, 19 files):** tokens (`JvTokens`, 68 vals incl. 4 px spacing scale, motion durations, canonical no-bounce easings), `TypographyEngine`, `ThemeEngine`, `MotionEngine` (true-sine breathing), `GlowEngine`, `ParticleEngine`, `AnimationEngine`, `ReactorEngine` (**13 states**, building/executing shared, facet-flicker flags, sharp Warning/Critical pulse wired end-to-end), `OrbRenderer`, `ReactorRenderer`, `HudRenderer`, `OrbitalRing`, components (`JvButton/Chip/Dialog/Input/Notification/Panel`), demo.
- **Production surface:** `HomeScreen` renders `PresenceOrb`, which collects `OrbRenderParams` each tick and draws via `OrbRenderer`/`MotionEngine`; `MobileResourceManagement` paces the render clock (tier FPS caps consumed).
- **Gaps (per `VISUAL_FOUNDATION_FIX_REPORT`):** deferred-as-features — audio-driven listening rings, faceted crystal, research/planning/coding tells, particle-direction-grammar rendering, waveform, sound language, startup/shutdown sequences, AnimationEngine crossfade, performance tiers, EI layer.
- **Deviations:** app brand violet vs design blue; 13 VF states vs identity-spec's 8.

---

## 8. Personality and Identity

The **Human Core** is the only personality system (no other subsystem owns character). Implemented modules: `IdentityKernel` (read-only, versioned, sole writer `IdentityRevisionHandler`), `ValuesSystem`, `PersonalityEngine` (6 trait scalars, shrinking learning rate, batch-only writes), `MoodSystem` (valence/arousal, mean-reverting baselines), `EmotionalIntelligence` (per-turn user affect read + trend window), `EmotionalRegulation` (sole mood writer), `SocialIntelligence` (tie-breaker), `TrustModeling`, `BondFormation` (session-length aware after C-5 fix), `RelationshipModeling`, `GrowthEngine` (sole personality writer), `UserAdaptation` (persisted via `AdaptationStore` after C-6 fix), `ConversationStyleController`, `ConsistencyGuard` (fail-closed veto, authenticity detectors), `SelfReflection`, `InternalDialogueEngine`, `PresenceManager`, `CompanionBehaviorOrchestrator` (idle by design). Offline fallback: `FallbackIdentity` + `HeuristicModelPort` (deterministic cue→line); online seam: `ModelBackedModelPort` → `ModelBackend.requestChat` → `ModelManager`.

The **audit→fix history** (`HUMAN_CORE_AUDIT` C-1…m-18 → `HUMAN_CORE_FIX_REPORT`, 44/44 tests) is the reliable read of quality: threading, burst-turn correlation FIFO, mood-decay, trust rollover, bond-session-count, adaptation persistence, ConflictResolver spec-semantics — all claimed fixed in the working tree. The **Identity Engine spec** (reactor visual identity, 8 states) governs the *look*, not the personality engine.

---

## 9. Memory System

- **Human Core stores** (`filesDir/humancore/*.json/jsonl`): 6 stores, per-store writer discipline, durability tiers (IDENTITY=critical, RELATIONSHIP/ADAPTATION=high, PERSONALITY/DIALOGUE=moderate, MOOD=low/ephemeral), atomic + fsync writes.
- **World memory** = annotations only: `MemoryInterface.annotateExchange` → sink → `ObsidianSync.logAnnotated` → markdown append. **No queryable memory subsystem** (manifest: `persistent_long_term_memory:false`, `conversation_thread_tracking:false`; MemoryScreen is a placeholder).
- **Session/task/inbox/approval/alert/telemetry** = independent JSONL stores under `filesDir`.
- **Docs' intended split (live body):** `jarvis.db` (core, recall, archival, core_state, state_log, habits — frozen body core) vs `cognitive/cognitive.db` (tasks, sessions, context, turns — cognitive core); hard rule "never let the cognitive core write `jarvis.db` or the body core write `cognitive/cognitive.db`."
- The **Obsidian vault** (`vault/`) is the human-readable doc store — untracked in git.

---

## 10. Models and Downloaded Assets

**Actively used by the APK:** vosk small-en-us-0.15 (wake+command STT, assets), whisper-tiny.en **int8** encoder/decoder/tokens (tier-2 fallback, assets), sherpa/onnxruntime/vosk `.so` (arm64-v8a only).

**Actively used host-side (Python, not in APK):** Zipformer streaming ASR (int8 encoder + fp32 decoder/joiner + tokens) — tier-2 of the `cascade_controller.py` STT cascade. Note the **fp32 encoder + int8 decoder/joiner variants are present but never loaded**.

**Downloaded-but-unused / redundant:**
- `vault/just_downloaded/` — **4.08 GiB, zero references in code** (grep-verified): Qwen3-1.7B GGUF (Q4_K_M, 1.28 GB — candidate local brain, unwired; the running brain per docs is `Llama-3.2-3B-Instruct-Q4_K_M.gguf`, not in repo), Piper TTS voices (en_US-lessac, ar_JO-kareem — app TTS is Android system), Silero VAD v6.2.1 + source, openWakeWord source, llama.cpp-master source, sherpa-onnx-master source, and a **6-format XLM-RoBERTa-class model export** (fp32/fp16 ONNX, O4, qint8-arm64, qint8-avx512, OpenVINO IR, PaddlePaddle params, safetensors, pytorch.bin, sentencepiece + tokenizer.json) — purpose spec-only (the "identity engine").
- Whisper **fp32** encoder/decoder in repo root (int8 is bundled).
- `jniLibs` armeabi-v7a / x86 / x86_64 (only arm64-v8a packaged).
- `local-repo` AAR 1.13.2 (declared dep is jitpack `v1.12.40` — the AAR is never resolved; build uses the cached jitpack artifact).
- Top-level `.tar.bz2`/`.zip` sources after extraction (kept in git).
- **Duplication:** whisper int8 + full vosk model exist **twice** (repo root and `mobile/app/src/main/assets/`), both tracked. Total repo ≈ 8.4 GB; `.git` ≈ 1.6 GB.

---

## 11. Runtime and Dependencies

**Android build:** Gradle 8.9, AGP 8.7.0, Kotlin 2.4.10 (+ compose plugin), Compose BOM 2024.09.00, minSdk 26 / target+compileSdk 36, Java 17, **`abiFilters = arm64-v8a`**, `aapt2` from Maven override (no full build-tools on device), `-Xmx3g` heap (asset-compression worker for the multi-hundred-MB models). Repos: google/mavenCentral/jitpack + device-local `file:///storage/emulated/0/jarvis-repo/local-repo`.

**Dependencies:** navigation-compose 2.8.4, core-ktx 1.13.1, lifecycle (runtime/livedata/process 2.8.4), activity-compose 1.9.1, compose-ui/material3, shizuku api+provider 13.1.5, vosk-android 0.3.75, okhttp 4.12.0, sherpa-onnx v1.12.40 (jitpack), junit + org.json (test-only). **AIDL is hand-written** (no working `aidl` compiler on device) — `IJarvisShellService` is an IBinder/Parcel port.

**Native:** libonnxruntime, libsherpa-onnx-{c,cxx,jni}, libvosk (arm64-v8a, ~45 MB total in APK).

**Python (repo):** stdlib-only Phase-1 (`classifier`, `decision_gate`, `execution_layer`, `protected_paths`) — ran under Python 3.14 today (pycache timestamps). **Python (live body, outside repo):** frozen core modules + llama-server + Piper, per handoff.

**Build environment:** the `.claude/settings.local.json` records the actual device workflow — `gradle :mobile:app:compileDebugKotlin`, `testDebugUnitTest --offline`, `assembleDebug`, `compressDebugAssets`, plus adb/termux interactions (llama-server, piper, stt-bridge, web_shell).

---

## 12. Dependency Graph

```
JarvisApplication ─► JarvisEngine.init ─┬─► FileStorage ─► 6 HC stores
                                        ├─► ModelManager ─► LlamaCpp/Ollama/RemoteJarvis/Heuristic adapters
                                        ├─► SessionManager / LocalTaskInbox / LocalApprovalQueue / LocalAlertStore
                                        ├─► ModelBackend.requestChat ─► HumanCore ModelPort seam
                                        ├─► ApprovalGate.init / TaskExecutor.init
                                        ├─► HumanCore.init ──(annotationSink)─► ObsidianSync
                                        ├─► CompanionCoreHolder.init ─► (30Hz) HumanCoreIntegration.tick
                                        │        ─► PresenceEngine ─► OrbStateMachine ─► AnimationController ─► PresenceOrb
                                        │        ─► MobileResourceManagement (tier caps)
                                        ├─► JarvisVosk / JarvisSherpaWhisper / JarvisTts (+ bridges)
                                        ├─► LatencyLayer.init ─► { WarmupEngine, SpeechEngine, AckSpeech, LatencyPipeline }
                                        ├─► RuntimeBinder (+ LanRuntimeDiscovery)
                                        └─► LiquidEnvironmentManager ─► EnvironmentRepository
                                              └─► BootstrapManager ─► runtimeBinder.initialize()

LatencyLayer.onUserInput ─► LatencyPipeline ─┬─► (fast) AckSpeech + CompanionCore reactor seams
                                             └─► (slow) HumanCore.beginExchange ─► pendingQueue ─► ModelManager.send
                                                   ─► ObsidianSync ─► onReplyReady ─► HumanCore.express
                                                   ─► completeExchange (IntegrationPass, async)

MainActivity ─► ShizukuBridge / JarvisMic ─► VoskBridge ─► DiagnosticsViewModel collectors
   └─ (voice) "screen…" ─► ScreenBridge ─► Shizuku ─► JarvisShellService(exec) ─► uiautomator dump

UI screens ─► getModelManager() / getEnvironmentManager() / getSessionManager() / getTaskInbox()
              / getApprovalQueue() / getAlertStore() / getBootstrapManager()  (JarvisEngine singletons)
```

Companion Core → Human Core is **read-only via HumanCoreIntegration** (enforced by `DependencyLintTest`). Human Core → model is via `ModelBackend` (set in JarvisEngine; unset in JVM tests → heuristic fallback).

---

## 13. Data Flow

**Text (app):** `ConversationScreen` → `ConversationViewModel.send` → `LatencyLayer.onUserInput` → fast path (ack speech once per 4 s gap, orb→THINKING, warm-prime, turn append) → slow thread (perception → pending-turn FIFO → Obsidian write → `ModelManager.send`) → `onReplyReady` (express → styled reply, verified flag = `status=="ok (local)"`, turns + Obsidian, `completeExchange` async integration, speak).

**Voice (app):** `JarvisMic` feeds `JarvisVosk`; wake "jarvis" → `VoskBridge.notifyWakeDetected` → command recognizer → `DiagnosticsViewModel` collector strips wake word → (cloud stub) → `screen…`? `ScreenBridge.captureScreenSummary` + `LatencyLayer.ackOnly` : `LatencyLayer.onUserInput`. Whisper fallback on `SherpaBridge.lastResult` same cascade.

**Companion tick (30 Hz):** `HumanCoreIntegration.tick` (fresh `CompanionSignal`; staleness→neutral) → `PresenceEngine.tick` (7-state arbitration) → `OrbStateMachine` → `AnimationController.submit` → `OrbRenderParams` → `PresenceOrb`; `MobileResourceManagement.evaluate` paces the render clock; background (0.1 Hz) via `ProcessLifecycleOwner`.

**Python Phase-1 (host, not in app):** text → `classifier` → `decision_gate.review` (tier1 auto / tier2 blocked) → `execution_layer.apply` → `logs/decision_gate.jsonl` / `execution.jsonl`.

**Live body (outside repo, per docs):** browser → `web_shell.py :8130` (fast-path ack + `state_publish.thinking`) → `cognitive/client` → Gateway `:8140` (Command Brain classify → fast/slow; slow on 1 worker thread) → frozen `human_core.think` → llama `:8080` → `jarvis.db` write + `voice.py` Piper speak → `/api/status` → reactor; audio amplitude via `/api/wave`. STT via stt-bridge `:8765`.

---

## 14. Resource Analysis

- **APK: 165 MB** = 159 MB assets (vosk 68 MB + whisper int8 ~103 MB) + ~45 MB native libs. Asset-heavy by design; fine for a personal device, hostile to any store distribution.
- **Repo: ~8.4 GB**, of which **4.08 GiB is an unused download pile**, ~1.1 GB is model dirs + archives tracked in git (`.git` = 1.6 GB), ~1.1 GB mobile build output.
- **Redundancy:** vosk + whisper int8 duplicated (root + assets); whisper fp32 + zipformer fp32/int8 variants + 3 unused ABIs + top-level archives all present-but-unused in git; the local AAR duplicates what the jitpack artifact provides.
- **Memory (runtime):** stores are JSONL/JSON with atomic writes; `LatencyLayer` keeps turns in a `StateFlow`; `WarmupEngine` polls the model every 60 s (battery cost if llama always up).
- **Power/stability:** Companion tick 30 Hz foreground / 0.1 Hz background with tiered frame caps; `background_suspended` exists but the **foreground service that would let the app survive backgrounding was deliberately deferred** — on aggressive OEMs (Realme 9 Pro cited) the presence engine and voice may be killed (explicitly flagged in `COMPANION_CORE_PLAN_AUDIT`).

---

## 15. Offline vs Cloud Responsibilities

**Offline (default, real):** Vosk + Whisper STT, Android-system TTS, local llama-server/Ollama brain (when running), heuristic adapter fallback, offline-first environment profiles, `screen` command via Shizuku, everything file-backed.

**Cloud (aspirational, stubbed):** `CloudVoiceClient` returns null; `CLOUD` provider maps to heuristic; `BuildConfig.HF_TOKEN` generated but **never read**; `hf-space-source/` empty; `cloud_brain_single_shot:false` in the capability manifest; network_security_config permits cleartext only to `127.0.0.1` (explicitly **no** cloud endpoints allowed). The **architecture doctrine** treats cloud as a never-a-dependency future layer (dual-binding + HF Space brain on the roadmap). The current app is **fully functional offline** — the honest answer is that cloud is a designed-in seam, not a feature.

---

## 16. Broken / Partial / Missing / Duplicated

**Broken / stale:** capability-manifest `known_limitations` (voice "retired", hf-token "plaintext gap") contradict the current code; `DiagnosticsViewModel.kt` route-logic is the only voice actor (routing should be in engine/latency per handoff TODO #6); ConversationScreen MIC button no-op; SettingsScreen permission-toggle row "not yet wired to a real permission check"; `offline_mode` preference not wired to force local-only.

**Partial:** `LanRuntimeDiscovery` (skeleton, no mDNS); `LiquidEnvironmentManager` policy hooks (permission/tools/memory/network/sync/latency/ui/safety) are declared but not enforced — only provider+runtime activation is real; `CloudVoiceClient` (stub); Companion Core 31-subsystem spec vs ~10 implemented; `vf` rendering vs the full reactor-state language (several tells deferred); `TaskExecutor.executeCapability` dispatcher exists but **no CapabilityRegistry class** exists (comment-only seam).

**Missing (per manifest + roadmap):** persistent queryable long-term memory, conversation-thread tracking, multi-stage reasoning, cloud brain, multi-model routing (only one endpoint config), vision/avatar/gesture/eye-contact, foreground service (deferred), androidTest tier (deferred).

**Duplicated / dead:** `DecisionGate`+`ExecutionLayer` in **Python** *and* **Kotlin** — all four dead (superseded by `approval/ApprovalGate`+`TaskExecutor`; nothing imports them); two `JarvisAction` data classes (root + `approval/`); `HeuristicModelPort` (Human Core) vs `HeuristicAdapter` (ModelManager) — same concept at two layers; `CompanionBehaviorOrchestrator` (HC) vs `companioncore/presence` — overlapping "companion behavior" concepts; models duplicated root↔assets; `local-repo` AAR vs jitpack dep; identity spec duplicated in `vault/architecture` and `vault/just_downloaded` (md5-identical); `DiagnosticsViewModel.kt.orig` already deleted. **`JarvisShellService` is not in the manifest** — correct for a Shizuku user-service (instantiated by Shizuku, not the system), but easy to mistake for a gap.

---

## 17. Build Blockers

No hard blockers — the tree built a debug APK **2026-08-08 10:27**. Real risks, in order:

1. **Uncommitted working tree** (≈100 files: 42 M / 4 D / 9 untracked dirs + new screens/tests/vault). No tested snapshot matches the repo; any `git checkout`/rebase or disk loss destroys the Companion Core + Liquid OS + vf + all Phase-2 fixes. This is the handoff's own Critical TODO #1.
2. **Sherpa resolution fragility:** declared dep is jitpack `v1.12.40`; the local-repo AAR is `1.13.2` with different coordinates. Online builds work (cache present); an **offline rebuild after cache eviction** cannot fall back to the bundled AAR — the native libs would silently come from nowhere (no `src/main/jniLibs`).
3. **Device-specific build plumbing:** `settings.gradle.kts` hardcodes `/storage/emulated/0/jarvis-repo/local-repo`; `aapt2` Maven override + `android.suppressUnsupportedCompileSdk=36` are container/workaround-specific — a PC/CI Gradle run may not resolve the local repo path (harmless while deps resolve online, but a divergence source).
4. **Voice native-crash history** (the reason `d0735ac` disabled it) is re-enabled with no on-device re-verification of stability for the two-recognizer Vosk + whisper + TTS triad.
5. **Asset-compression heap** requirement (3 GB) is baked into `gradle.properties`; without it `compressDebugAssets` OOMs.
6. **No on-device verification** is possible in this container (no device; llama-server not runnable here) — the handoff explicitly marks Android on-device behavior UNKNOWN for all post-Human-Core code.

---

## 18. Recommended Next Build Order

Derived from the handoff's own Critical/High backlog cross-checked against the code:

1. **Stabilize & commit the working tree** (Critical): commit Companion Core, Liquid OS, vf, latency, and all new screens/tests; commit or ignore the vault deliberately; record the exact verified build. Without this, nothing else is safe.
2. **Move voice→action routing into the engine/latency layer** (handoff TODO #6): make the wake-loop app-scoped (not gated on `SystemScreen`), and wire the ConversationScreen MIC button to the same `LatencyLayer` path.
3. **Make bootstrap + offline real**: auto-run the bootstrap sequence (or surface it clearly), wire `offline_mode` to force local-only, and activate a real provider (llama-server health check → auto-select LlamaCpp) so the default brain is an actual LLM rather than the heuristic.
4. **Resolve the sherpa dependency** (align `local-repo` coordinates or vendor `src/main/jniLibs` so offline builds are deterministic).
5. **Wire or delete the dead seams**: `CapabilityRegistry`/`executeCapability`, `BuildConfig.HF_TOKEN`, `CloudVoiceClient` (either implement the cloud adapter the manifest promises or remove the token plumbing).
6. **Memory milestone**: turn the annotations-only sink into a queryable local memory (the manifest's `persistent_long_term_memory:false` is the honest current state).
7. **Repo hygiene**: drop unused models/ABIs/archives from git, de-duplicate root↔assets, move the 4.08 GiB `just_downloaded` pile out of the vault or curate it.
8. **Then the documented roadmap** (handoff §19): Reverse Engineering System → Limitation Breaker → Expansion Engine → multi-model routing live → **Android body → gateway** (app speaks the `:8140` contracts) → learning pipeline → cloud brain (HF Space) → self-modification via Tier-2 gate.

---

*Method note: everything above was verified by direct read of the repo (all core runtime files, configs, manifest, APK contents, git history/diff), plus two agent sweeps (mobile source-tree architecture; models/assets inventory) and direct reading of the primary vault documents (engineering handoff in full, web-first plan, Companion Core + Human Core specs, design-system report, identity spec). Two things could not be verified from this container and are flagged UNKNOWN rather than asserted: on-device runtime behavior (no device; llama-server not present) and whether the handful of source files edited after the 10:27 APK still compile.*
