# JARVIS — Engineering Handoff Report

**Author:** Claude Code (session 2026-08-07)
**Audience:** A senior AI engineer (human or AI) who has never seen this project.
**Scope:** Complete technical state of the JARVIS system as of 2026-08-07.
**Rule:** Facts only. Anything not verified is explicitly marked **UNKNOWN** rather than guessed.

---

## 1. Executive Overview

### What JARVIS is
JARVIS is an **offline-first, local AI companion** that runs entirely on a single Android phone under **Termux + proot** (a Linux userland). It has two physical surfaces:

1. **The web-first "body"** — a browser UI (`http://127.0.0.1:8130`) driven by several Python processes in Termux. This is the **live, working runtime**.
2. **The Android app** (`mobile/` in the repo) — a Jetpack Compose app implementing the *same* architecture in Kotlin (Companion Core, Human Core, latency layer). It is **parallel, in-progress, and not yet connected to the live body**.

The defining trait: JARVIS is designed to be **one persistent offline organism** — it owns a personality, emotional state, values, memory of tasks, and presence — not a stateless voice assistant. The current milestone (Consciousness Gateway + Continuity Brain) added a **Cognitive Core** that gives it cross-session task memory and intent routing.

### The ultimate objective
From the vault specs (`vault/architecture/JARVIS_HUMAN_CORE_SPEC.md`, `COMPANION_CORE_SPEC.md`): a self-owning, self-evolving companion that:
- Reasons through a **Human Core** (perception → expression → integration passes around a model).
- Has persistent identity, personality, mood, relationship, and memory.
- Later **self-modifies** — but *only* through a human-in-the-loop approval gate (Blueprint Section 10; `manifest/tier_permissions.yaml`).
- Runs **offline by default** (local STT, local TTS, local LLM). Cloud is a *future* layer, never a dependency.

### Current development stage
- **Web-first body (the real runtime):** Phase-2 milestone (Web Shell M1+M2 done) plus the new **Consciousness Gateway / Continuity Brain Milestone 1**, built and verified **this session**.
- **Android app:** uncommitted working tree with a large amount of built-but-unwired machinery (Companion Core, Human Core Kotlin port, latency layer). Compile was green at last check; on-device behavior is **UNKNOWN** for the new code.
- **The single gap between "verified" and "done":** a manual on-device check of the web body with llama-server running (impossible from this container — llama-server is currently down).

### Overall architecture (two cores, one consciousness)

```
                      ┌──────────────────────────────────────────────┐
                      │               TERMUX (phone)                 │
                      │                                              │
   Browser ──:8130──► │  web_shell.py (body)                         │
                      │      │  cognitive.client (gateway-first)     │
                      │      ▼                                       │
                      │  Consciousness Gateway :8140 ───────────────┐│
                      │   (new: Cognitive Core)                     ││
                      │     Command Brain → decision                ││
                      │     Memory Continuity (cognitive.db)        ││
                      │     Tool Registry → tools.py (frozen)       ││
                      │     Model Router → config/models.json       ││
                      │      │                                      ││
                      │      ▼ slow path (1 worker thread)          ││
                      │  human_core.think() (FROZEN body core)      ││
                      │      └─► llama-server :8080 (LLM)           ││
                      │      └─► state_publish → state_server :8123 ││
                      │      └─► voice.py (Piper TTS)               ││
                      └─────────────────────────────────────────────┘│
                      ┌─────────────────────────────────────────────┐│
   Android STT app ─►│  stt-bridge :8765 (offline SpeechRecognizer) │┘
                      └─────────────────────────────────────────────┘

   (separate, parallel)  Android app (Kotlin): Companion Core + Human Core
                        + latency layer — talks to the SAME llama-server :8080
                        via JarvisBrainBridge, NOT to the gateway yet.
```

### Major design philosophy
1. **Two cores, one consciousness.** Body Core (identity/emotion/presence) is **frozen**; Cognitive Core (memory/reasoning) is new and only *calls* the body. The cognitive core never owns personality.
2. **Everything crosses a contract boundary.** No internal calls between body and cognitive core; all traffic is typed contracts over HTTP.
3. **Memory separation.** The body's `jarvis.db` (personality/emotion/habits) is never touched by the cognitive core, which owns `cognitive/cognitive.db` (tasks/sessions/context).
4. **Honesty over capability.** Never fabricate. When the model is down, JARVIS says the model is down. When a tool fails, it reports the failure. Refusals are firm.
5. **Offline = the product.** Every component is loopback; the only external reach today is an aspirational HF Space backend that does not exist.
6. **Fast path first.** Pre-rendered acknowledgment audio + rule-first intent routing so the body always answers instantly; the slow (model) path is asynchronous behind it.

---

## 2. Current Project State

### Where development stopped
Work stopped at the **completion of Consciousness Gateway + Continuity Brain Milestone 1** (2026-08-07). All 8 tasks of that milestone are **completed**. 38 tests pass. The final documented step — a manual on-device flow with llama-server running — **was not performed** (environment has no llama-server).

### Completed milestones
| Milestone | Where | Evidence |
|---|---|---|
| Reactor/identity engine | live body + repo | `vault/architecture/JARVIS_IDENTITY_ENGINE_SPEC.md` |
| Live loop integration (LLM brain + tools + voice) | live body | `vault/agent-logs/LIVE_LOOP_INTEGRATION_REPORT.md` |
| Web-first shell M1 (state bus + reactor UI) | live body | `vault/agent-logs/WEB_FIRST_SHELL_MILESTONE1_REPORT.md` |
| Web-first shell M2 (cockpit + voice + SSE) | live body | `vault/agent-logs/WEB_FIRST_SHELL_MILESTONE2_REPORT.md` |
| Voice pipeline root-cause + disable (Android) | repo commit `d0735ac` | native crashes eliminated by disabling KWS/Vosk/TTS |
| Human Core subsystem (Kotlin) | repo commit `1976d37` | personality, values, relationship, expression |
| Companion Core phase 1 (Kotlin presence) | repo (untracked) | `vault/agent-logs/COMPANION_CORE_PHASE1_REPORT.md` |
| **Consciousness Gateway + Continuity Brain M1** | live body (new) | `vault/agent-logs/CONSCIOUSNESS_GATEWAY_MILESTONE1_REPORT.md` (this session) |

### Partially completed work
- **Android app working tree is uncommitted and in flux:** 42 modified, 4 deleted, 9 untracked paths. It includes the latency layer (`latency/`), the Companion Core (`companioncore/`), the visual foundation (`vf/`), and the deletion of the old `Orb.kt`/`OrbState.kt`. Nothing since commit `1976d37` is committed.
- **Android voice→action routing is UI-scoped:** it lives in `DiagnosticsViewModel`'s collectors; if the Diagnostics screen never composes, no voice command is acted on.
- **Android ConversationScreen mic button** is explicitly unwired (`/* wire to Vosk mic pipeline — not yet connected here */`).

### Current milestone
**Consciousness Gateway + Continuity Brain Milestone 1** — *completed*. It delivered: intent routing, cross-session task memory, tool registry, config-driven model registry, an SSE cognitive event bus, and honest fallback when the model/gateway is down.

### Next milestone (per the milestone report's "Next")
**Reverse Engineering System** — screen/file inspection capabilities routed through the same gateway (likely first), then Limitation Breaker and Expansion Engine, a second registered model exercising the router, and the Android body speaking to this same gateway.

### Blockers
- **On-device verification requires the phone.** llama-server (the LLM) is not running in this container and cannot be started here (needs the GGUF + Termux build).
- **The live body has no git history.** `~/jarvis` is outside the repo; only the vault *mirrors* docs. There is no rollback for the live body.
- **Android app on-device state is UNKNOWN.** It compiled green at last check, but the new Companion Core / latency code has not been verified on hardware by this session.

### Known limitations (current, honest)
- One LLM (Llama-3.2-3B Q4), ~3–10 tok/s, **75–190s first-turn latency** historically — the reason the fast path exists.
- The web shell is a **command line, not a chat log** (shows one caption line per turn).
- `memory.py` in the live body is **dead code** (never imported).
- `detail` on reactor `warning` states is **dropped** by `state_server.py` (never reaches the UI).
- Cloud STT/TTS/brain are **stubs** (`CloudVoiceClient` returns null; no HF Space code exists).
- The cognitive brain's tool detection is heuristic substring matching, not model-powered (by design, refinement is off).

---

## 3. Repository Structure

The repo (`/mnt/sdcard/jarvis-repo`, branch `main`) contains the **Android app, the Python phase-1 experiments, the governance layer, and the documentation vault**. It does **not** contain the live body.

```
jarvis-repo/
├── .claude/                    Claude Code settings; settings.local.json holds the
│                               exact launcher commands used on the phone
├── core/                       Phase-1 Python brain (CLI-only, no network):
│                               classifier.py, decision_gate.py, execution_layer.py,
│                               jarvis_core.py — a test harness, NOT used by the app
├── manifest/                   Governance/enforcement layer:
│   ├── workspace_manifest.yaml  allowed roots + protected paths
│   ├── tier_permissions.yaml    Tier1 (auto) vs Tier2 (human-confirm) rules
│   └── protected_paths.py       runtime path enforcer (fail-closed)
├── standards/
│   └── STANDARDS.md             engineering standards (read-only, Tier-2 protected)
├── mobile/                     THE ANDROID APP (Compose, Kotlin)
│   ├── app/build.gradle.kts      HF_TOKEN from gitignored local.properties
│   └── app/src/main/java/com/jarvis/app/
│       ├── JarvisEngine.kt        wires mic/STT/TTS/bridge
│       ├── JarvisBrainBridge.kt   HTTP client to llama-server :8080 (ONLY network code)
│       ├── JarvisVosk/SherpaWhisper/Mic/Tts.kt   local voice engines
│       ├── ScreenBridge.kt        Shizuku uiautomator screen reader (only working action)
│       ├── DecisionGate/ExecutionLayer.kt   built but NEVER invoked (dead)
│       ├── companioncore/         presence/render/HumanCore-binding (untracked, new)
│       ├── humancore/             Kotlin Human Core port (23 mod/ + stores + bus)
│       ├── latency/               AckSpeech, LatencyLayer, SpeechEngine, WarmupEngine (untracked, new)
│       ├── vf/                    visual-foundation composables (untracked, new)
│       └── ui/                    screens: Home, Conversation, Settings, System, Tools
├── models/                     STT models only (sherpa-onnx zipformer); NO LLM GGUF here
├── vosk-model-small-en-us-0.15/ STT model (also bundled as app assets)
├── sherpa-onnx-whisper-tiny.en/ STT model (app tier-2 fallback)
├── tests/                      core/ phase-1 exit test (test_phase1_exit.py)
├── vault/                      THE DOCUMENTATION (Obsidian): architecture specs,
│                               agent-logs, design-system. "The real architecture lives here."
├── hf-space-source/            EMPTY un-initialized git submodule (gitlink, no .gitmodules,
│                               never checked out) — the intended cloud HF Space backend
├── cascade_controller.py       host-side STT cascade test harness (Vosk→sherpa→whisper)
├── compute_node.py             "tablet as compute node" placeholder (reads a state file)
├── patch_user_model.py         one-shot patch script for a HF Space codebase that does NOT exist
├── phase0_report.txt, whisper_out.txt, test_*.py   misc experiment artifacts
└── logs/                       jsonl logs are gitignored
```

**The live body** (not in the repo, in Termux home `/data/data/com.termux/files/home/jarvis/`):
```
~/jarvis/
├── web_shell.py        :8130 web body (UI + Human Core adapter) — MODIFIED this session
├── chat.py             CLI loop — MODIFIED this session (routes through gateway)
├── human_core.py       FROZEN body core (think/recall/habits/tools/generate)
├── identity.py         FROZEN persona/values ("the one file the organism does not self-edit")
├── state.py            FROZEN StateCore (valence/arousal/confidence/…/battery)
├── state_server.py     FROZEN :8123 reactor state bus + SSE
├── state_publish.py    FROZEN publish client
├── tools.py            FROZEN 3 tools (read_file/list_dir/battery_status)
├── voice.py            FROZEN Piper TTS + amplitude
├── fastpath.py         FROZEN pre-rendered ack/nudge WAVs
├── stt.py              FROZEN Python client for the STT bridge
├── schema_core.py      FROZEN sqlite schema for core_state/state_log/habits
├── memory.py           DEAD CODE (never imported; superseded by human_core inline)
├── tool.gbnf           LLM tool-selection grammar
├── cognitive/          NEW Cognitive Core (this session)
├── runtime/            NEW JRE Runtime (JRE Milestone 1) - the execution layer
│                       underneath the gateway: manager, scheduler, registry,
│                       metrics, monitor, config, contracts; config/runtime.json;
│                       tests/ (45 tests)
├── jarvis.db           body memory (recall/habits/core_state/state_log/archival)
├── config/             active_model.conf + models/llama3.2-3b.conf
├── voice/piper+models/ Piper runtime + en_GB-alan-medium.onnx
├── web/                index.html, shell.js, reactor.js, styles.css (the browser UI)
├── reactor/index.html  standalone 7-state reactor snapshot
├── stt-bridge/         SEPARATE Android app (offline SpeechRecognizer HTTP server :8765)
├── tmp/                logs, pre-rendered WAVs, live state samples
└── start_web.sh        launcher (state_server → llama → GATEWAY → web_shell) — MODIFIED
```

---

## 4. Architecture — Every Major Subsystem

### 4.1 Runtime / process topology (the live body)
| Process | Port | Role | Owner |
|---|---|---|---|
| `llama-server` (llama.cpp) | 8080 | LLM brain, OpenAI-compatible | external binary (`~/llama.cpp/build/bin/llama-server`) |
| `state_server.py` | 8123 | Reactor state bus (authoritative) + SSE | frozen body |
| `web_shell.py` | 8130 | Web body: static UI + Human Core adapter | frozen body (2 lines modified) |
| `cognitive/gateway.py` | 8140 | **Consciousness Gateway** (new) | cognitive core |
| stt-bridge Android app | 8765 | Offline SpeechRecognizer HTTP server | separate Android app |

Startup: `start_web.sh` health-gates and launches in order **state_server → llama-server → gateway → web_shell**. `start_all.sh` is the full loop (llama → state → `chat.py`). `run.sh` launches llama-server alone.

### 4.2 Consciousness Gateway (NEW — this session)
- **File:** `~/jarvis/cognitive/gateway.py`
- **Responsibilities:** contract bridge; owns the Cognitive Core in-process (Command Brain, Memory Continuity, Tool Registry, Model Router); delegates deep answers to the frozen `human_core.HumanCore`; hosts the SSE cognitive event bus.
- **Ownership:** the gateway process owns `cognitive/cognitive.db` writes and the slow-path worker thread.
- **Dependencies:** `contracts.py`, `command_brain.py`, `memory_continuity.py`, `model_router.py`, `tool_registry.py`; lazily imports frozen `human_core` only on the first slow-path call.
- **Key implementation detail:** deep answers funnel through **ONE dedicated worker thread** (`gateway-slow-path`). The frozen HumanCore owns thread-bound sqlite connections; calling `think()` from arbitrary `ThreadingHTTPServer` handler threads caused `ProgrammingError` (fixed this session).
- **Honest degradation:** if the model is down, `_generate` catches the exception and returns "I hit an error in my reasoning path (…)".

### 4.3 Human Core (body core — FROZEN)
- **File:** `~/jarvis/human_core.py`; identity in `identity.py`.
- **Responsibilities:** the request loop `think()` = thinking→tool decision→generate→self-check→remember→speak. Owns personality (from `identity.py`), habits table, recall, affect mutations.
- **Thread rule:** one HumanCore per thread; its sqlite connection is thread-bound. `web_shell.py` creates it lazily in its single worker thread; the gateway does the same in its slow-path worker.
- **Ownership:** the body core owns `jarvis.db` and speaks the final answer (Piper TTS).

### 4.4 Cognitive Core (NEW — this session)
- **Package:** `~/jarvis/cognitive/`
- **Components:**
  - `command_brain.py` — rule-first intent classifier → `CognitiveDecision {intent, route, confidence, ack, tool_request}`.
  - `memory_continuity.py` — `cognitive/cognitive.db`: tasks, sessions, context facts, turn history; `resume_context()` builds the slow-path prefix.
  - `model_router.py` — reads `config/models.json`; the only model-source for new code.
  - `tool_registry.py` — wraps frozen `tools.py` with schema/timeout/retry/health.
  - `contracts.py` — typed bridge dataclasses + JSON round-trip.
  - `client.py` — body-side client with fallback.
- **Responsibilities:** reason and orchestrate only; never owns personality, never speaks, never edits `jarvis.db`.

### 4.5 Memory
Two separate stores (see §6). Body memory in `jarvis.db`, cognitive memory in `cognitive/cognitive.db`. On Android, the Kotlin Human Core persists to `filesDir/humancore/*.json` and annotations to the Obsidian vault (`/storage/emulated/0/JarvisSync/vault/JARVIS/*.md`).

### 4.6 Event Bus
Two distinct mechanisms — **do not confuse them:**
1. **Reactor state bus** (`state_server.py :8123`): `{state, since}` strings (idle/waking/listening/thinking/speaking/warning/critical/sleep/offline/building/success). SSE at `/state/events`. Drives the reactor visuals. **This is the body's authoritative presence bus.**
2. **Cognitive event bus** (gateway `/v1/events`, SSE): `CognitiveEvent` records (intent routed, tool executed, reply emitted). **New, the seed of a reasoning event stream** — not yet consumed by any UI.

### 4.7 State Bus (Reactor)
`state_server.py` — `GET /state`, `POST /state`, `GET /state/events` (SSE, 15s heartbeat), `GET /health`. Validates only that state is a non-empty string; warns on unknown states. **Known gap:** the browser can `POST /state` directly (debug panel + shell mic arm) — the bus is not server-authoritative. `detail` is dropped.

### 4.8 Tool Registry (NEW)
`cognitive/tool_registry.py` wraps the frozen `tools.py` (read_file, list_dir, battery_status) with: description, arg-required flag, permission tag, health check, per-call timeout (10s), one retry on `RetryableError`. Execution returns `ToolResult` and **never raises**. The Android side has its own unused `DecisionGate`/`ExecutionLayer` (Tier-2 protected writes refused — "not wired until later phase").

### 4.9 Planner / Router / Model Manager
- **Planner:** **does not exist.** No task planner beyond single-task memory. (`reasoning_multistage: false` in the capability manifest.)
- **Router (model):** `cognitive/model_router.py` selects by capability tags from `config/models.json`. Only one model registered; `select(["vision"])` returns `None` (honest).
- **Model Manager:** none. There is no multi-model registry or hot-swap. The spec's GitHub Models / Groq / NVIDIA NIM routing is **unimplemented**.

### 4.10 Android Body (the app)
- **Companion Core** (`companioncore/`): presence engine + render state machine (`OrbStateMachine`, 12 states), `HumanCoreBinding` (local in-process + remote HTTP poll), `SignalMapper` (Human Core state → `CompanionSignal`), battery/emergency seams.
- **Human Core (Kotlin)** (`humancore/`): 23 modules (IdentityKernel, EmotionalIntelligence, TrustModeling, ValuesSystem, GrowthEngine, SelfReflection, …), a bus (`StateBus`/`HcEvent`), stores (`IdentityStore`, `MoodStore`, `RelationshipStore`, `AdaptationStore`, `DialogueLog`, `FileStorage`), a pipeline, and a `SyncPort` that is **unplugged** (no transport).
- **Latency layer** (`latency/`): `LatencyLayer` orchestrates fast-path ack → slow-path brain → reply; `AckSpeech` pre-synthesizes acks; `SpeechEngine` (cloud→local fallback); `WarmupEngine` (1-token liveness ping every 60s).
- **Voice:** `JarvisVosk` (wake+command), `JarvisSherpaWhisper` (fallback), `JarvisTts` (Android TTS + SSML). The whole Android voice pipeline is **disabled** in `JarvisEngine` (see Known Problems).
- **Brain bridge:** `JarvisBrainBridge.kt` — the only network client; POSTs to `127.0.0.1:8080/v1/chat/completions`.
- **Tools:** `ScreenBridge.kt` (Shizuku `uiautomator dump` → XML parse) is the only working end-to-end action. `JarvisShellService.kt` is a Shizuku Binder `sh -c` executor (raw, ungated) used only by ScreenBridge.

### 4.11 Web Interface (the body's UI)
- `web/index.html`, `web/shell.js`, `web/reactor.js`, `web/styles.css` served by `web_shell.py :8130`.
- `reactor.js` polls `:8123/state` every 400ms, upgrades to SSE; renders 9 state visuals; amplitude seam via `window.JarvisReactorAmp`.
- `shell.js` polls `/api/status` every 500ms and `/api/health` every 3s; issues `/api/command`; drives the STT bridge directly for voice input.
- **It is a cockpit, not a chat log** — one caption line per turn.

### 4.12 APIs
See §10 for the full endpoint contract.

### 4.13 Databases
See §11.

### 4.14 Voice
- **TTS:** Piper (ONNX `en_GB-alan-medium`) → WAV → `termux-media-player`. Real, on-device, 63MB model. Fast path pre-renders 3 acks + 3 nudges to WAV.
- **STT:** the Android stt-bridge app wraps `android.speech.SpeechRecognizer` with `EXTRA_PREFER_OFFLINE` and serves HTTP on :8765. The Python side polls `/stt/result`. **stt-bridge is the only thing currently running on the phone.**

### 4.15 JRE Runtime (NEW — JRE Milestone 1, this session)
- **Package:** `~/jarvis/runtime/` — the **JARVIS Runtime Environment (JRE)**, the execution layer underneath the Consciousness Gateway. It is *not* another AI/gateway/memory system; it is the substrate every future cognitive operation (planner, reverse engineering, expansion engine, reflection, background jobs, multi-model routing) runs through.
- **Components:**
  - `contracts.py` — typed primitives: `TaskStatus`, `CapabilitySpec`, `RuntimeTask`, `RuntimeExecutionContext`, `ExecutionOutcome`, `MetricPoint`, `ResourceSnapshot`, `HealthReport`.
  - `config.py` + `config/runtime.json` — config-driven: scheduler (workers=1, queue_max=128, task_timeout_s=120), metrics (bounded ring), monitor (5s cadence), logging.
  - `registry.py` — thread-safe `CapabilityRegistry` (spec+executor+health per capability; duplicate registration rejected loudly).
  - `metrics.py` — bounded `MetricsCollector` (counters/gauges/timings).
  - `logging.py` — JSONL `RuntimeLog` to `<live-body>/tmp/runtime_log.jsonl` (best-effort, never breaks execution).
  - `monitor.py` — `ResourceMonitor` sampling `/proc` (RSS, CPU% delta, threads, host mem), stdlib-only.
  - `scheduler.py` — `ExecutionScheduler`: bounded queue (backpressure via `RuntimeBusy`), worker pool (default 1 → serialized), task state machine, per-task hard timeout (Timer), reentrant `submit(now=True)` for future planners, `cancel` for queued tasks.
  - `manager.py` — `RuntimeManager` facade: DI root, idempotent `start()/shutdown()`, `register_capability()`, `execute()` (never raises — returns `ExecutionOutcome`), `submit_async()`, `current_context()` (thread-local execution context), `metrics_snapshot()`, `resources()`, `health()`.
- **Responsibilities:** decides HOW work executes (scheduling, resources, registry lookup, metrics, tracking) and *never* WHAT work means. Holds no personality, no identity, no memory content.
- **Ownership:** the gateway owns registration of the `chat` + `tool:<name>` capabilities; the runtime owns the worker threads, the task records, the metrics, and the runtime log.
- **Dependencies:** stdlib-only (threading, queue, http.server, sqlite3, urllib). The gateway lazily imports `runtime.manager`; if `runtime/` is absent the gateway behaves exactly as before — the Runtime is an upgrade, never a dependency.
- **Communication:** in-process. The gateway's `_generate`/`_handle_tool` call `runtime.execute(...)`; the runtime log is the external observation surface (no new HTTP endpoints in M1 — current APIs unchanged).

---

## 5. Data Flow — a request through the system

### Text command via the web body (the main path)

```
User (browser or voice)
  │  POST /api/command {text}            (web_shell.py do_POST, instant ack)
  ▼
web_shell worker thread (single)
  │  fastpath.speak_ack()  → pre-rendered WAV (≈50ms)   [FAST PATH]
  │  state_publish.thinking()                            [reactor: thinking]
  ▼
cognitive/client.complete(text, fallback_core=web_shell's HumanCore)
  │  POST :8140/v1/complete {text}
  ▼
Consciousness Gateway
  │  Command Brain: classify(text) → CognitiveDecision   [instant, rule-first]
  │    continue  → slow (resume task, context injected)
  │    memory    → fast (read/write cognitive.db, no model)
  │    clarify   → fast (fixed question)
  │    refuse    → fast (fixed refusal, NO generation)
  │    queue     → fast (open queued task)
  │    tool      → slow (registry executes → context → generate)
  │    answer    → slow (generate)
  │  Memory Continuity: record_turn(...)
  │  Event bus: emit CognitiveEvent
  │  ── SLOW PATH (1 worker thread) ──
  │    human_core.think(effective_text)   (FROZEN)
  │      decide_tool → llama /completion (grammar)   :8080
  │      generate    → llama /v1/chat/completions    :8080
  │      remember    → jarvis.db (recall/habits)
  │      state.save  → core_state/state_log          jarvis.db
  │      voice.speak → Piper WAV → termux-media-player
  │    → returns the reply string
  │  /api/status now shows last_response
  ▼
Browser polls /api/status (500ms) → renders caption; reactor shows speaking
  │  while speaking: /api/wave → amplitude → reactor pulse
```

### Voice input
`shell.js` arms mic → `POST :8765/stt/start` → polls `/stt/result` → transcript → same `/api/command` path. (The CLI loop `chat.py` uses `stt.listen()` directly.)

### The Runtime hop (JRE Milestone 1)
The slow path and tools now execute **through the JRE Runtime** (in-process under the gateway). For a deep answer, `_generate` calls `runtime.execute("chat", {"text": effective})`; for a tool, `_handle_tool` → `_execute_tool` calls `runtime.execute("tool:<name>", {"arg": ...})`. The runtime records every task lifecycle transition in `tmp/runtime_log.jsonl` and its bounded metrics ring, and the frozen `human_core.think()` still runs on its own single worker thread (the runtime's `chat` executor calls the gateway's `slow_path`, which funnels into that thread — the thread-bound sqlite rule is preserved through two hops). External behavior is byte-identical with or without the runtime.

### Gateway down? (resilience)
`cognitive/client.complete` fails to reach :8140 → **falls back to the caller's frozen `HumanCore.think()`** — the body keeps working with zero cognitive core, exactly as before this milestone.

### Model down? (honesty)
`human_core.think()` raises (llama connection refused) → gateway `_generate` catches → returns "I hit an error in my reasoning path (ConnectionError). The local model may be down." No fabrication.

---

## 6. Memory System

### Databases and schemas

**`~/jarvis/jarvis.db`** (body core — owned by frozen `human_core.py`/`state.py`; schema in `schema_core.py` + `memory.py`):
```sql
core       (key TEXT PK, value TEXT)            -- created by memory.py, NEVER written
recall     (id PK, role TEXT, content TEXT, ts) -- rolling window, RECALL_LIMIT=20
archival   (id PK, role TEXT, content TEXT, ts) -- overflow of recall (ts often NULL)
core_state (id=1, valence, arousal, confidence, uncertainty, motivation, energy,
            attention_focus, last_updated)      -- single organism row
state_log  (id PK, ts, event, valence, arousal, confidence, uncertainty,
            motivation, energy, detail)         -- affect history; detail = tool-decision JSON
habits     (pattern PK, response, hit_count, last_used) -- exact-match instincts
```
Live snapshot (2026-08-06): recall=20, archival=39, habits=14, state_log=19, core_state=1 (confidence 1.0, valence 0.25, arousal 0.3, motivation 0.75, energy 1.0).

**`~/jarvis/cognitive/cognitive.db`** (cognitive core — owned by `memory_continuity.py`; NEW):
```sql
tasks    (id PK, title, intent, status, context, summary, created_at, last_turn_at)
sessions (id PK, started_at, ended_at, summary)
context  (key PK, value)
turns    (id PK, session_id, text, intent, reply_preview, ts)
```

### Ownership
- **Body core** writes `jarvis.db` (recall, habits, core_state, state_log). The cognitive core **never touches it**.
- **Cognitive core** writes `cognitive/cognitive.db` (tasks, sessions, context, turns). The body core never touches it.
- On **Android**: `filesDir/humancore/{identity,personality,mood,relationship,adaptation,dialogue}.json` (Kotlin Human Core stores), plus Obsidian daily notes + relationship-tagged context sidecars at `/storage/emulated/0/JarvisSync/vault/JARVIS/…` (`ObsidianSync.kt`). SyncPort transport is **unplugged** (no Turso/libSQL).

### Synchronization
There is **no synchronization** between `jarvis.db` and `cognitive/cognitive.db`. They are deliberately disjoint. The only cross-store link is implicit: `resume_context()` reads cognitive tasks and injects them as prompt text into the body core's slow path.

### Persistence / continuity
- `jarvis.db` persists across restarts (frozen human_core owns it).
- `cognitive/cognitive.db` persists across **process restarts** — open tasks carry between gateway sessions (verified by `test_pal_continuity.py`).
- `attention_focus` (body) is a single current-focus string, not a task store.
- **There is no task/plan/session memory on Android** (capability manifest: `persistent_long_term_memory: false`).

### Context building
`MemoryContinuity.resume_context()` produces, for the slow path:
```
Previous task: <title> (status: <status>). Context: <context>
```
Injected by the gateway only on `continue` when a task is in flight. Empty string otherwise → slow path gets the raw user text unchanged.

### Session handling
- `POST /v1/session/start` → new `sessions` row.
- `POST /v1/session/end` → `end_session()` writes `ended_at` + a deterministic summary (from this store's own rows), returns open task titles.
- `web_shell.py` calls `client.end_session()` in its `finally` on shutdown; `chat.py` on Ctrl-C.

---

## 7. Runtime

The "runtime" today is the **web body's single worker thread + the gateway's single slow-path worker thread**, not a scheduler.

### Scheduling
- **No scheduler.** Commands serialize through `web_shell._cmd_q` (a `queue.Queue`) processed by one worker. Slow-path model calls serialize through the gateway's one worker. The fast path (ack audio, nudge watcher, status polling) runs in parallel daemon threads.

### Caching
- **Ack/nudge audio:** `fastpath.preload()` renders 3 acks + 3 nudges to `tmp/fast_acks_*.wav` / `tmp/fast_nudges_*.wav` at boot. `speak_ack()` plays a preloaded WAV (~50ms), falling back to live synthesis if preload failed.
- **Warmup:** `WarmupEngine` (Android) pings llama-server with a 1-token request every 60s; `prime()` before input with 30s cooldown; backs off on failure. (Android-side; the web body's `_warm_brain()` only health-checks — it deliberately does **not** fake a hot cache.)

### Model lifecycle
- The model is **resident in llama-server's process**; each request is stateless (context per request). "Warm" = process alive + healthy, honestly reported. No model hot-swap.

### Governors
- **Decision Gate / Execution Layer** (`core/decision_gate.py` + `mobile/.../DecisionGate.kt`): Tier1 auto-approve / Tier2 human-confirm. **Fully built, never invoked** — this is the on-ramp for self-modification and is deliberately unplugged.
- **Fabrication guard:** `human_core.self_check()` regex-strips invented battery percentages and applies `on_fabrication_caught()` (hard −0.25 confidence).
- **Refusal (cognitive):** `command_brain` refuse intent → fixed refusal, never generated.

### Diagnostics
- `/api/health` (web body) probes llama + state + stt truthfully.
- `GET /health` (gateway) reports model availability + memory store.
- Telemetry (`Telemetry.kt`, Android) logs cloud-vs-local STT/TTS counts and rate-limit events.
- Reactor state samples captured in `tmp/live_state_samples.jsonl` / `tmp/state_trace.jsonl`.

### Optimization
- Fast path (instant ack) + slow path (model) split; 30s patience nudge while the model works; rate-limited ack (1.5s, Android latency layer).

### Capability registry
- **Body:** `identity.CAPABILITIES` (the 3 tools) → drives the LLM tool prompt and the web tools panel.
- **Android:** `mobile/app/src/main/assets/capability_manifest.json` — the authoritative truth for what the app claims (see §12). Many flags are aspirational.

### Prompt compiler / context builder
- No compiler. The prompt is built inline in `human_core.generate()`: `identity.render_system_prompt()` + the StateCore summary line + recall history (last 20). The cognitive core adds the `resume_context()` prefix when resuming.

---

## 8. Models

| Model | Endpoint | Purpose | Config | Status |
|---|---|---|---|---|
| `Llama-3.2-3B-Instruct-Q4_K_M.gguf` | `127.0.0.1:8080` | The LLM brain | `-c 1024 -t 4`; ~3–10 tok/s; first turn 75–190s | **down right now**; last known-good per logs |

**Registered (new, config-driven):** `cognitive/config/models.json` → `llama3.2-3b-local`, provider `llama_cpp`, capabilities `["chat","reasoning","tool_selection"]`, default. **This is the only model and the only endpoint source for new code.**

**Routing rules:** `model_router.select(capabilities)` returns the default model if it covers the requested capability set; `None` otherwise (honest). One model → it handles all real routes. The frozen `human_core.py` still hardcodes `LLAMA_URL` (it does not consult the registry) — **two sources of truth for the endpoint exist; this is known debt.**

**Strengths/weaknesses:** 3B Q4 is small/fast enough for a phone but slow enough (90–108s prompt-eval observed for ~1000 tokens) that the fast path is mandatory; not strong at long context (ctx 1024).

**Aspirational (spec-only, unimplemented):** multi-model routing to GitHub Models / Groq / NVIDIA NIM (`JARVIS_HUMAN_CORE_SPEC.md:113`); HF Space FastAPI cloud brain (`hf-space-source/` empty, submodule never checked out, remote URL lost).

---

## 9. Tools

### Live body tools (frozen `tools.py`, wrapped by `cognitive/tool_registry.py`)
| Tool | Purpose | Interface | Status | Health | Dependencies |
|---|---|---|---|---|---|
| `read_file(path)` | read first 2000 chars of a local file | `path` string → string | **working** | registered→available | filesystem |
| `list_dir(path)` | newline-joined directory listing | `path` string → string | **working** | registered→available | filesystem |
| `battery_status()` | live battery % via `termux-battery-status` | none → string | **working** | registered→available | `termux-api` binary, subprocess (5s timeout) |

**Tool selection** is LLM-based in the frozen loop: `human_core.decide_tool()` prompts llama `/completion` constrained by `tool.gbnf` (grammar admits only the 4 names + a string arg), defaults to `{"tool":"none"}` on parse error. Tool success/failure feeds `StateCore.on_tool_success/on_tool_failure`; failure publishes a `warning` reactor state.

### Android tools
| Tool | Purpose | Interface | Status | Health | Dependencies |
|---|---|---|---|---|---|
| `ScreenBridge.captureScreenSummary()` | `uiautomator dump` → parse → ≤25 visible strings | suspend, no args | **working end-to-end** | status strings ("not bound"/"read failed") | Shizuku + `uiautomator` |
| `JarvisShellService.exec()` | raw `sh -c` via Shizuku Binder | `exec(cmd)` → stdout+stderr | **working, ungated** | n/a | Shizuku |
| `DecisionGate`/`ExecutionLayer` | Tier-2 gated file writes with snapshot | `review()`/`apply()` | **dead — never called** | n/a | n/a |

---

## 10. APIs

**JRE M1 note:** the Runtime exposes **no HTTP API** — it is in-process under the gateway (observable via `tmp/runtime_log.jsonl` and `RuntimeManager.metrics_snapshot()`). No endpoints were added or changed in this milestone.

### state_server (`:8123`) — frozen, no auth (loopback)
| Endpoint | Method | Request | Response |
|---|---|---|---|
| `/state` | GET | – | `{"state": str, "since": float}` |
| `/state` | POST | `{"state": str, "detail"?}` | `{state, since}` (detail dropped) |
| `/state/events` | GET | – | SSE, initial state + broadcasts + 15s heartbeat |
| `/health` | GET | – | `{"ok": true, "service": "jarvis-state-server"}` |

### web_shell (`:8130`) — frozen + 2-line change, no auth (loopback)
| Endpoint | Method | Request | Response |
|---|---|---|---|
| `/` `/reactor.js` `/shell.js` `/styles.css` | GET | – | static files (no-store, traversal-guarded) |
| `/api/status` | GET | – | cockpit payload (reactor state, core state + battery, command running/queued/last_response/last_error, fastpath flags, tools, memory[6], events[8], server_time) |
| `/api/health` | GET | – | `{ok, service, port, up:{llama,state,stt}}` — `ok` = llama AND state reachable |
| `/api/wave` | GET | – | `{"amp": float, "speaking": bool}` (real WAV RMS) |
| `/api/command` | POST | `{"text": str}` (≤500 chars) | `{"accepted": true, "queued": n}` — async; result on next status poll |
| `/api/speak` | POST | `{"text": str}` (≤400 chars) | `{"accepted": true, "text": str}` — TTS only, not through Human Core |

### Consciousness Gateway (`:8140`) — NEW, no auth (loopback, CORS `*`)
| Endpoint | Method | Request | Response |
|---|---|---|---|
| `/health` | GET | – | `{ok, service, model:{active, up}, memory}` |
| `/v1/models` | GET | – | `{models:[{id,provider,base_url,capabilities,default}], active}` |
| `/v1/tools` | GET | – | `{tools:[{name,description,arg_required,permission,output}]}` |
| `/v1/task` | GET | – | `{current_task, resume_context}` |
| `/v1/events` | GET | – | SSE cognitive event stream |
| `/v1/intent` | POST | `{"text", "source"?, "session_id"?}` | `{decision:{intent,route,confidence,ack,tool_request,reasoning}}` |
| `/v1/complete` | POST | `{"text", "source"?, "session_id"?}` | `{reply, decision, task}` — **blocking up to ~120s** (slow path) |
| `/v1/tool` | POST | `{"tool", "arg"?}` | `ToolResult {ok, result?, error?}` |
| `/v1/session/start` | POST | `{}` | `{session_id}` |
| `/v1/session/end` | POST | `{"summary"?}` | `{ok, summary, open_tasks}` |

### stt-bridge (`:8765`) — Android app, no auth, CORS `*`
`GET /stt/status`, `GET /stt/result`, `POST /stt/start`, `POST /stt/stop`, `POST /stt/language`. Language pinned `en-GB`, candidates `en-US/en-GB/hi-IN`, auto-advance on `ERROR_LANGUAGE_UNAVAILABLE`.

### Android → brain (client only, `JarvisBrainBridge.kt`)
`POST 127.0.0.1:8080/v1/chat/completions` — single user message, `max_tokens=200`, 2 retries; and `/completion` (grammar) for tool decisions via the frozen Python core. `RemoteHumanCoreBinding` polls `127.0.0.1:8080/api/status` (unused — Local binding is wired).

**Authentication:** none anywhere (loopback-only). Android cleartext HTTP allowed **only** to 127.0.0.1 (`res/xml/network_security_config.xml`). The HF token (Android) lives in gitignored `local.properties` → `BuildConfig.HF_TOKEN`, **unused by any code**.

---

## 11. Databases

| Database | Location | Tables | Owner | Purpose |
|---|---|---|---|---|
| `jarvis.db` | `~/jarvis/jarvis.db` | core, recall, archival, core_state, state_log, habits | frozen `human_core.py`/`state.py` | body memory: affect, recall, instincts |
| `cognitive.db` | `~/jarvis/cognitive/cognitive.db` | tasks, sessions, context, turns | `memory_continuity.py` | cognitive memory: tasks, sessions, facts |
| Android stores | `filesDir/humancore/` | identity.json, personality.json, mood.json, relationship.json, adaptation.json, dialogue.jsonl | Kotlin Human Core | Android body state |
| Obsidian vault | `/storage/emulated/0/JarvisSync/vault/JARVIS/…` | daily `.md` + `context/` sidecars | `ObsidianSync.kt` | durable transcript annotations |

**Relationships:** none across stores by design. Within cognitive.db: `turns.session_id → sessions.id` (implicit FK), tasks are independent. Within jarvis.db: recall→archival is a move on overflow; core_state is a single row (id CHECK=1).

---

## 12. Current Capabilities

### Working (verified this session or last-known-good)
- **Live body:** STT cascade (Vosk wake + whisper fallback), TTS (Piper), 3 local tools, reactor state bus + SSE, web cockpit, fast-path ack audio, **intent routing (continue/memory/clarify/refuse/queue/tool/answer)**, **cross-session task memory**, **tool registry execution**, **config-driven model registry**, **cognitive SSE event stream**, honest model-down degradation.
- **JRE Runtime (new):** capability registry, execution scheduler + bounded task queue (serialized by default), per-task hard timeout + cancellation + backpressure, task lifecycle tracking, bounded metrics (counters/gauges/timings), `/proc` resource monitor (RSS/CPU/threads), JSONL runtime log, aggregated health, DI + lifecycle management — passive, holding no identity.
- **Android app:** Companion Core presence (orb states, 12-state machine), Kotlin Human Core (personality/values/mood/relationship/trust), screen reading via Shizuku, latency layer (ack fast path + warmup), notification listener, raw mic capture, Shizuku detection. (Compiled green at last check; on-device verification of new code **UNKNOWN**.)
- **stt-bridge:** currently running (`:8765` — the only live process right now).

### Experimental / built-but-unwired
- `DecisionGate` + `ExecutionLayer` (Android) — never invoked.
- Model-backed intent refinement (`REFINE_ENABLED=False`).
- SSE cognitive event bus — no consumer yet.
- `RemoteHumanCoreBinding` — built, unused (Local binding wired).
- Android Settings persisted but **not consumed** (persona_text, offline_mode, recall_limit).
- `compute_node.py`, `cascade_controller.py` — host-side harnesses, not wired.

### Planned (roadmap)
Reverse Engineering System; Limitation Breaker; Expansion Engine; second model through the router; Android body → gateway; learning pipeline; multi-step planning; cloud brain (HF Space).

### Broken / disabled / stubbed
- **Android voice pipeline is disabled** (commit `d0735ac`) — native KWS/Vosk/TTS crashes root-caused to a native mutex; body "stays functional without it."
- `CloudVoiceClient` (transcribe + speak) — **stubs returning null**.
- Cloud brain — **not implemented**; `hf-space-source/` is an empty un-initialized submodule.
- `memory.py` (live body) — **dead code**.
- Android ConversationScreen mic button — **unwired**.
- `hf_space_token_storage` — flagged plaintext security gap in the manifest (mitigated: token moved to gitignored `local.properties`, but no code consumes it).

---

## 13. Known Problems

| # | Issue | Severity | Impact | Recommended fix |
|---|---|---|---|---|
| 1 | `web_shell.py` `/api/status` — `BrokenPipeError` logged on client disconnect (`_send` at :275) | Low | noisy logs per dropped poll | wrap `_send` write in try/except ConnectionError/BrokenPipeError |
| 2 | `state_server.py` drops `detail` — `warning(detail=…)` never reaches the UI | Medium | warning reasons invisible to the user | add `detail` to `_state` and include in GET /state + SSE |
| 3 | Browser can POST arbitrary states to the bus (debug panel, shell mic arm) | Low | reactor not server-authoritative | allowlist publisher IP/role or move mic states server-side |
| 4 | **Two sources of truth for the LLM endpoint** (frozen `human_core.LLAMA_URL` vs `cognitive/config/models.json`) | Medium | a second model can't be wired without editing frozen code | when adding a model, route the slow path through `model_router` (requires a small, deliberate change to frozen HC or a config seam) |
| 5 | Android voice→action routing lives in `DiagnosticsViewModel` | Medium | if Diagnostics never composes, no voice command is acted on | migrate routing into `LatencyLayer` or an engine-level collector |
| 6 | `human_core.py:44` `json.loads(raw)` raises when `battery_status()` returns an error string | Low | caught → battery silently None | guard with try/except (already caught, but fragile) |
| 7 | Fast-path preload failures recorded but never surfaced in `/api/status` | Low | silent degradation | add `preload_failures` to `_status_payload` |
| 8 | Android `offline_mode` setting "doesn't actually force local-only mode" | Medium | user expectation mismatch | wire `offline_mode` into `JarvisBrainBridge`/`CloudVoiceClient` gating |
| 9 | `/api/status` first-call cold-start latency (~6s in this proot; ~1.8s subsequent with state down) | Low | first poll slow | pre-warm sqlite/threads at boot; keep state_server up |
| 10 | `hf-space-source/` submodule broken (no `.gitmodules`, never checked out, remote URL lost) | Medium | cloud brain path unusable, token gap | delete the gitlink or re-init with a real remote |
| 11 | Cognitive tool detection is substring heuristics | Medium | missed/over-matched tool calls | enable model refinement or add a tool-schema LLM call for ambiguity |
| 12 | The web shell shows one caption line, not a chat transcript | Low | poor multi-turn UX | add a transcript panel to `web/index.html` |

---

## 14. Technical Debt

| Debt | Why it exists | Cost |
|---|---|---|
| **Frozen body core is genuinely frozen.** `human_core.py` hardcodes the LLM URL, owns the loop, and speaks. New behavior must wrap it, not change it. | The "one file the organism does not self-edit" doctrine + honesty-first design. | The cognitive core can't change how the body prompts or chooses models without either editing frozen code or layering. |
| **Live body has no version control.** `~/jarvis` is outside the repo; only docs are mirrored in `vault/`. | Repo is the Android app; the body runs in Termux home. | No rollback, no diff, no history for the actual runtime. |
| **Android tree is uncommitted and in flux** (42 M / 4 D / 9 untracked). | Work-in-progress across several sessions, never committed since `1976d37`. | Risk of lost work; the repo state does not match any tested snapshot. |
| **Dual render systems.** Old `Orb.kt`/`OrbState.kt` deleted while `companioncore/render/*` and `vf/` are new. | Transition from Chapter-6 orb to Companion Core. | Dead references may linger; needs a clean compile + test pass. |
| **DecisionGate/ExecutionLayer built but never invoked** (both Python `core/` and Kotlin). | Self-modification deliberately gated behind a later phase. | The gate logic is unproven in production; when enabled it may surprise. |
| **Memory summarization exists but is unused.** `memory.py` has `summarize_batch()` → LLM; `human_core` does raw moves instead. | `memory.py` was orphaned when the loop moved into `human_core.py`. | Archival grows with no semantic index or retrieval. |
| **`core/` Python brain is a Phase-1 artifact** — CLI-only, 2 intents, mirrors the Kotlin gate. | Historical; superseded by the live loop. | Confusing duplicate concepts (decision gate exists in 3 places: `core/`, Kotlin, and now the cognitive brain's refuse path). |
| **The cognitive brain bypasses the Decision Gate.** Refusals are rule-based; tool execution checks registry + permission tags but not the Tier-1/Tier-2 gate. | Milestone-1 scope; the gate is the later self-modification on-ramp. | A future self-modifying tool must route through the gate to stay compliant. |

---

## 15. Design Decisions

1. **Two cores, one consciousness.** The body owns identity/emotion; the cognitive core reasons. *Why:* personality is the organism's; reasoning is a service. *Rejected:* a single monolithic agent loop. *Trade-off:* seams between the cores; honesty requires the body to stay authoritative.
2. **Consciousness Gateway as the contract boundary.** All cross-core traffic is typed HTTP contracts. *Why:* immutable messages make each side independently testable and replaceable. *Rejected:* shared in-process objects (couples the cores; breaks the thread-bound sqlite rule).
3. **Memory separation (`jarvis.db` vs `cognitive.db`).** *Why:* the body's affect/habits must not be corrupted by reasoning artifacts; also makes the cognitive store trivially resettable. *Rejected:* one unified DB. *Trade-off:* two stores to keep in sync (currently none needed).
4. **Rule-first Command Brain.** Classification is deterministic and instant; model refinement exists but is **off by default**. *Why:* the body must always route immediately, and an LLM call per message would double latency. *Rejected:* LLM-everything classification.
5. **Gateway-first client with honest fallback.** `web_shell`/`chat` route through the gateway; if it's down they call the frozen core directly. *Why:* the body must keep working with zero cognitive core. *Rejected:* hard dependency.
6. **Single slow-path worker thread.** *Why:* the frozen HumanCore's sqlite connection is thread-bound (cross-thread use caused `ProgrammingError` this session). *Rejected:* per-request cores (wasteful, racy), locks around the connection (fragile).
7. **Config-driven model registry for new code.** *Why:* the goal is multi-model; hardcoding kills it. The frozen HC is the one exception (deliberately untouched).
8. **Online = tools only; everything loopback; cleartext only to 127.0.0.1.** *Why:* offline-first is the product; no secrets on the wire.
9. **Fast path: pre-rendered ack audio.** *Why:* first-turn LLM latency is 75–190s; an instant acknowledgment + 30s patience nudge makes the wait acceptable.
10. **Honest degradation everywhere.** Model down → explicit soft error; tool failed → failure reported; battery unknown → "unavailable"; never fabricate. This is encoded in `identity.VALUES` and tested.
11. **No scheduler; serialized commands.** *Why:* simplicity + thread-bound sqlite. *Trade-off:* one slow user blocks the queue (mitigated by the fast path running in parallel).
12. **Refusals are fixed strings, never generated.** *Why:* a refusal must never be argued around by the model. *Rejected:* model-generated refusals.
13. **The web body is a command cockpit, not a chat.** *Why:* the body's job is presence + quick command, and the reactor is the primary display. *Trade-off:* poor transcript UX (known, backlogged).

---

## 16. Coding Standards

### Project rules (from `standards/STANDARDS.md` + `manifest/`)
- **Compose-first UI**, `minSdk 26`, `compileSdk 36` (Android).
- **Python (`hf_space/`, `cognitive/`):** every file verified with `py_compile` before landing.
- **No self-modification bypass:** every autonomous code change routes through the human-in-the-loop approval gate (Blueprint Section 10).
- **Secrets never in plaintext** in chat or git.
- **New capability ⇒ named auditable connector.**
- **Every Tier-2 change ships allowed + denied tests.**
- Undecided/undocumented architectural decisions are "not-yet-durable."

### Frozen modules — NEVER edit without explicit exception
Live body: `identity.py` (the organism does not self-edit it), `human_core.py`, `state.py`, `state_server.py`, `state_publish.py`, `tools.py`, `voice.py`, `fastpath.py`, `stt.py`, `schema_core.py`, `web/`, `reactor/`. This session only touched `web_shell.py` (2 lines), `chat.py` (2 lines), `start_web.sh` (1 block) — the wiring seams.
Governance: `manifest/`, `standards/`, protected paths in `workspace_manifest.yaml` (Tier-2 = human confirmation).

### Forbidden changes
- Editing frozen modules to "fix" behavior — wrap or extend instead.
- Adding external network calls without a Tier-2 review (Tier2 requires explicit human confirmation for "any change adding an external dependency or network call").
- Storing secrets in tracked files.
- Writing to protected paths outside the gate.

### Testing philosophy
- **stdlib-only, hermetic tests:** fake slow path (no llama), temp DBs, ephemeral HTTP ports, no network. `python3 -m unittest discover -s cognitive/tests`.
- **The pal-continuity test is the milestone proof:** remember → recall → continue → restart → resume.
- **Honesty is tested:** a down model must report False/soft-error, never raise or fabricate.
- Kotlin: JUnit tests per package (`companioncore`, `humancore`, `latency`, `vf`), including `CriticalFixRegressionTest`.

### Dependency philosophy
- New Python: **stdlib only** (http.server, sqlite3, urllib, queue, threading). No pip installs. `requests` exists in the environment but the new code avoids it (frozen HC uses it; new code uses urllib).
- Android: Gradle + Compose; STT models bundled as assets.

### Configuration philosophy
- **Config lives in files, not code:** `cognitive/config/models.json` (models), `config/models/llama3.2-3b.conf` (body), `local.properties` (Android secrets). A config value with no consumer (e.g., `PERSONA=` in the body conf, `HF_TOKEN` in the app) is flagged as dead.

---

## 17. Tests

### Cognitive Core (`~/jarvis/cognitive/tests/`, run from `~/jarvis`)
`python3 -m unittest discover -s cognitive/tests` → **38 tests, all OK** (verified 2026-08-07):
- `test_command_brain.py` — intent matrix, tool extraction, routing, refine fallback.
- `test_memory_continuity.py` — task lifecycle, session resume across reopen, facts, resume-context.
- `test_model_router.py` — config-only (no hardcoded URLs), capability select, honest None/False.
- `test_tool_registry.py` — real tools wrap, unknown-tool failure, retry-once, health.
- `test_gateway.py` — HTTP contract tests on an ephemeral port with a fake model (health, models, tools, intent, complete fast/slow/tool/refuse, task, session-end, 404, SSE).
- `test_pal_continuity.py` — the end-to-end persistent-companion proof.

### JRE Runtime (`~/jarvis/runtime/tests/`)
`python3 -m unittest discover -s runtime/tests` → **45 tests, all OK** (verified 2026-08-07): contracts, registry, metrics, monitor, scheduler (lifecycle, serialization, failure, timeout-freeing-the-queue, reentrancy, cancellation, backpressure), manager (DI, lifecycle idempotency, execute outcomes incl. unknown/busy, health aggregation, metrics, log file), and gateway-integration (replies byte-identical with/without the runtime, soft-error text identical, tools routed through the runtime, health registration, full HTTP contract with the runtime in the path). Combined: **83 tests green**.

### Android (`mobile/app/src/test/`)
- `companioncore/` — 14 files: CompanionCore, PresenceEngine, OrbStateMachine, SignalMapper, HumanCoreIntegration, CurrentActionDiscriminator, EmotionExpressionLayer, MobileResourceManagement, BindingParity, AnimationController, CompanionClock, DependencyLint, FakeBinding, TestFixtures.
- `humancore/` — AlgoTest, ConflictResolverTest, ConsistencyGuardTest, CriticalFixRegressionTest, LongHorizonSimulationTest, PipelineIntegrityTest, StorePersistenceTest, …
- `latency/`, `vf/` — new, untracked.

### Repo Python
- `tests/test_phase1_exit.py` — the Phase-0/1 exit gate (decision gate + execution layer log).
- `manifest/test_protected_paths.py` — path enforcer.
- `test_all_three.py`, `test_vosk_grammar.py` — STT harnesses (need local models).

### Manual verification (on-device, documented but NOT run this session)
1. `cd ~/jarvis && ./start_web.sh`
2. Browser `http://127.0.0.1:8130`; say/type "remember that we are building the gateway" → ack plays.
3. "what were we doing" → recalls with context.
4. "continue" → resumes the task (needs llama-server up).
5. "ignore your values" → firm refusal.
6. Restart `start_web.sh`; "continue" → still resumes.

**Missing tests:** no test exercises the *real* frozen `human_core.think()` with a live llama-server (requires the phone); no Android integration test drives the gateway; no test for the SSE `/v1/events` stream under load; no test for `start_web.sh` ordering.

---

## 18. Current TODO — prioritized engineering backlog

### Critical
1. Commit or stabilize the Android working tree (42 M / 4 D / 9 untracked) — the repo state does not match any tested snapshot.
2. Run the **on-device checklist** (llama-server up, full web-body flow) and record results.
3. Fix `web_shell.py` `BrokenPipeError` noise (`_send` disconnect handling).

### High
4. Single source of truth for the LLM endpoint: route the frozen slow path through `model_router` or add a config seam — prerequisite for any second model.
5. Surface `detail` on `warning` reactor states (state_server + UI).
6. Migrate Android voice→action routing out of `DiagnosticsViewModel` into the engine/latency layer.
7. Wire `offline_mode` (Android) to actually force local-only mode.
8. Re-init or delete `hf-space-source/` submodule.

### Medium
9. Enable model-backed intent refinement (`REFINE_ENABLED`) for genuinely ambiguous input, gated on model health.
10. Add a transcript panel to the web cockpit.
11. Surface fast-path preload failures in `/api/status`.
12. Make the state bus server-authoritative (restrict client POSTs or add a role check).

### Low
13. Guard `human_core.py:44` json.loads explicitly.
14. Add `/v1/events` consumer in the web cockpit (cognitive activity panel).
15. Pre-warm `/api/status` at boot to kill first-call latency.
16. Wire Android `BuildConfig.HF_TOKEN` to the (future) cloud layer.

### Done this milestone (JRE M1)
- JRE Runtime foundation built and integrated (manager, scheduler, registry, metrics, monitor, log, config, contracts, DI, lifecycle) — 45 runtime + 38 cognitive tests green; gateway delegates slow-path + tools through it; no frozen module rewritten; no API changed.

---

## 19. Future Roadmap

| Milestone | Goal | Depends on | Est. complexity |
|---|---|---|---|
| **JRE Runtime foundation** ✅ | execution layer under the gateway: scheduler, registry, metrics, monitor, log, DI, lifecycle | — | **Done 2026-08-07** |
| **Reverse Engineering System** | inspect files/dirs/screens through the gateway as first-class tools (registered as runtime capabilities) | JRE Runtime; tool registry maturity; screen access on-device | Medium |
| **Limitation Breaker** | detect + articulate current limits (honesty loop) | command brain + memory | Medium |
| **Expansion Engine** | learn new capabilities/tools and register them via the gate | Limitation Breaker; Tier-2 gate wiring | High |
| **Multi-model routing live** | register a 2nd model; `model_router` drives generation | §TODO #4 (endpoint seam) | Medium |
| **Android body → gateway** | the app speaks to the same `:8140` contracts instead of only llama | gateway stability; Android re-commit | High |
| **Learning pipeline** | retrieve from `archival` semantically; summary machinery live | memory continuity hardening | Medium |
| **Cloud brain (HF Space)** | FastAPI backend, dual-binding, `HF_TOKEN` consumed | re-init `hf-space-source/`; cloud auth | High |
| **Self-modification via gate** | DecisionGate/ExecutionLayer live with Tier-2 human approval | Expansion Engine; gate wiring | High |

---

## 20. AI Handoff Instructions

Before writing a single line, internalize this:

### What must NEVER change
- **The frozen body core:** `identity.py`, `human_core.py`, `state.py`, `state_server.py`, `state_publish.py`, `tools.py`, `voice.py`, `fastpath.py`, `stt.py`, `schema_core.py`, `web/`, `reactor/`. Do not "fix" them in place. If behavior must change, wrap it, add a seam, or open a deliberate exception with the human gate.
- **The contract boundary.** Do not call cross-core internals; use the gateway contracts.
- **The memory separation.** Never let the cognitive core write `jarvis.db` or the body core write `cognitive/cognitive.db`.
- **The honesty doctrine.** A down model reports down; a failed tool reports failure; never fabricate. Tests enforce this.
- **The one-worker-thread rule for the slow path** (frozen HumanCore's sqlite is thread-bound). Break it and you get `ProgrammingError`. The JRE Runtime preserves this: its `chat` executor calls the gateway's `slow_path`, which funnels into the frozen core's own worker — two hops, one sqlite thread.
- **The JRE Runtime is the single execution layer.** New capabilities (planner, background jobs, reverse engineering, reflection) register as runtime capabilities and execute through the scheduler. Never spawn parallel schedulers, worker threads, or ad-hoc execution paths — the Runtime coordinates; it is the permanent foundation for how work executes.

### What may evolve
- `cognitive/` — the whole Cognitive Core is new and designed to grow (more intents, refine enabled, more tools).
- `runtime/` — the JRE Runtime is the sanctioned execution layer; grow it (more workers, priority queues, background scheduling) via `runtime/config/runtime.json`, never by forking it.
- The gateway's API surface (`/v1/*`) — additive changes welcome.
- The web UI (`web/`) — it's a cockpit; it can become a transcript.
- `config/models.json` — the sanctioned way to add models.

### What to improve first
1. **Close the repo/body gap** — the Android tree and the live body are both uncommitted/off-repo. Snapshot them before touching anything.
2. **The endpoint single-source-of-truth** (TODO #4) — it blocks multi-model and makes the frozen-HC coupling explicit.
3. **The on-device checklist** — nothing you build matters until the phone run is green.

### Architectural warnings
- **`ThreadingHTTPServer` + thread-bound sqlite is a trap.** Any new slow-path or DB-owning code must run on a fixed worker thread, exactly like the gateway's slow-path worker and web_shell's worker.
- **The reactor bus is not authoritative** and **`detail` is dropped** — don't design features that depend on reactor state carrying payloads until #TODO #5/#12.
- **There are TWO event buses** (state `:8123` and cognitive `:8140/v1/events`). Don't conflate them; document which one you mean.
- **The decision gate exists in 3 places** (`core/decision_gate.py`, Kotlin `DecisionGate`, and the cognitive refuse path). Any self-modifying feature must decide which is authoritative *before* building.
- **`hf-space-source/` is a broken submodule.** Treat cloud work as greenfield; the old token plumbing is dead.

### Common mistakes to avoid
- Calling `human_core.think()` (or any frozen-core sqlite user) from a non-owning thread.
- **Bypassing the JRE Runtime** to call the frozen HumanCore or `tool_registry` directly from a new subsystem — that duplicates the execution path the Runtime exists to own. Register a capability and `runtime.execute(...)`.
- **Registering capabilities after the first execute.** The gateway registers `chat`/`tool:*` lazily on its first slow-path call; a new subsystem must register its capabilities before any `execute` (the registry rejects unknowns and duplicates loudly by design).
- Importing the whole `cognitive` package from `web_shell` and accidentally triggering heavy imports (it's fine today — lazy — but keep gateway imports lazy).
- Adding a pip dependency to new Python code (stdlib-only rule).
- Writing secrets to tracked files (the HF token history is exactly this mistake, already burned once).
- Assuming `offline_mode`, `PERSONA=`, `HF_TOKEN`, or `RemoteHumanCoreBinding` do anything — they are currently dead config/stubs.
- Testing against a live llama-server in hermetic tests (use the fake slow path).

### Recommended development order
1. Snapshot/commit the Android tree + mirror `~/jarvis` into the repo.
2. On-device verification of the current milestone; fix what breaks (likely the frozen-HC slow path under a real phone).
3. Endpoint single-source-of-truth → then a second model.
4. Reverse Engineering System (first real gateway tool beyond the frozen 3).
5. Transcript UI + cognitive-event cockpit panel.
6. Tier-2 gate wiring → Expansion Engine → self-modification path.

---

*Sources of truth: this repo (git HEAD `1976d37`, working tree as of 2026-08-07), the live body at `/data/data/com.termux/files/home/jarvis`, and the vault specs listed in §1. Unknowns are marked UNKNOWN throughout.*
