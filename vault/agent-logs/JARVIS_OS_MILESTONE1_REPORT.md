# JARVIS OS — Milestone 1 Report

**Date:** 2026-08-07
**Status:** Built, integrated, and verified. 116 tests green (38 cognitive + 46 runtime + 32 OS). Live vertical verified end-to-end over HTTP (fake model standing in for llama-server: chat streams tokens, a goal decomposes, executes, reflects, stores).
**Location:** live body `~/jarvis/jarvos/` (a new package beside `cognitive/` and `runtime/`). Docs mirrored in this vault.

## Doctrine honored

- **The OS owns surface, not soul.** It adds the Workspace, Planner, Models, Console, Telemetry, and Notifications — execution and memory surfaces around the existing Cognitive Core. It does NOT replace the Human Core, the gateway's personality handling, or the frozen body's state bus.
- **Zero behavior change.** The Consciousness Gateway (:8140) contract is byte-identical; the frozen web body (`web_shell.py` :8130) keeps working unchanged. All 84 pre-existing cognitive + runtime tests pass untouched. No frozen module (`human_core.py`, `identity.py`, `state.py`, `state_server.py`, `tools.py`, `voice.py`, `fastpath.py`, `web_shell.py`) was rewritten.
- **Everything executes through the Runtime.** The OS never calls the LLM, a tool, or a subsystem directly — it asks the shared `RuntimeManager` to execute a capability. The gateway and the OS register on ONE runtime in ONE process.
- **Honest everywhere.** Model down → honest offline reply, honest `model_up: false`; no kv-cache/metric values fabricated; `detail`-style telemetry never guessed. The LLM is a plug-in — the OS never names a provider or model in its code.

---

## 1. One process, two HTTP surfaces

```
                 ┌────────────────── OSApp (jarvos/app.py) ──────────────────┐
                 │          ONE shared RuntimeManager (JRE)                  │
                 │                                                            │
  web body ─────►│  GatewayCore (:8140, unchanged contract)                  │
  :8130          │    registers: chat, tool:read_file, tool:list_dir,        │
                 │               tool:battery_status                         │
                 │                                                            │
  desktop ──────►│  OsCapabilities (:8150 OS Portal)                         │
  :8150          │    registers: chat:os, chat:session, model:*, planner:*,  │
                 │               reflect:*, telemetry:snapshot, world:*      │
                 │                                                            │
                 │  WorldStore (jarvos/data/world.db): plans, tasks,         │
                 │    reflections, sessions, session_messages, events        │
                 └────────────────────────────────────────────────────────────┘
```

- `python3 jarvos/app.py` boots both. `python3 cognitive/gateway.py` still runs standalone (back-out path). `start_web.sh` prefers `jarvos/app.py` and falls back to the gateway.
- Runtime task events (task.completed) feed an OS event bus (`/os/events` SSE) and auto-trigger reflection for planner-driven steps.

## 2. OS subsystems (all on the shared Runtime)

| Subsystem | File | Capabilities | Notes |
|---|---|---|---|
| World Store | `jarvos/world.py` | data (not a capability) | plans, tasks, reflections, sessions, messages, events; thread-safe WAL sqlite |
| Telemetry | `jarvos/telemetry.py` | `telemetry:snapshot` | runtime metrics + ResourceSnapshot, persisted to events |
| Model Manager | `jarvos/model_manager.py` | `model:list/health/switch/load/unload/resources` | reads `cognitive/config/models.json` (the sanctioned registry); llama_cpp launchable, vLLM/Ollama honest UnsupportedOperation |
| Planner | `jarvos/planner.py` | `planner:plan`, `planner:run` | rule-based goal → step graph → runtime execution in dep order; `now=True` reentrant steps (no worker deadlock) |
| Reflection | `jarvos/reflection.py` | `reflect:run`, `reflect:list` | rule-based lessons; auto-triggered on planner-driven tasks |
| Chat | `jarvos/chat.py` | `chat:os`, `chat:session` | streams tokens of the ACTIVE model (any provider) via StreamHub; persists every message |
| StreamHub | `jarvos/streams.py` | SSE plumbing | per-stream queues with a replay window (fast streams still deliver to late subscribers) |
| LLM client | `jarvos/llm.py` | n/a | OpenAI-compatible, streaming + non-streaming, stdlib only |
| OS Portal | `jarvos/os_server.py` | HTTP :8150 | static desktop UI + `/os/api/*` + `/os/stream/{id}` + `/os/events` |

## 3. Desktop UI (os/web/, served at :8150)

Visual Bible tokens (Void Black, JARVIS Blue, Listening/Thinking/Warning accents), status bar (active model + health, reactor state, caps, queue, CPU/MEM/THR), dock, six panels, command palette (Ctrl+K), toasts. All UI state is what the backend reports — nothing local or fake.

- **Workspace** — markdown chat, streaming tokens via SSE, session selector.
- **Planner** — goals as cards, expandable step graphs with statuses/durations/results and auto-reflections.
- **Models** — registry list with switch/load/unload/health; active model live-swappable.
- **Console** — live lifecycle events from `/os/events`.
- **Telemetry** — gauges from `telemetry:snapshot` + ResourceSnapshot.
- **Alerts** — notification history with an unread badge.

## 4. Verification

```
python3 -m unittest discover -s cognitive/tests -p "test_*.py"   # 38 OK
python3 -m unittest discover -s runtime/tests -p "test_*.py"     # 46 OK
python3 -m unittest discover -s jarvos/tests -p "test_*.py"      # 32 OK (world, models,
                                                                 #   planner, reflection,
                                                                 #   chat, full HTTP e2e)
```

Live vertical (fake model on :8080 standing in for llama-server):

1. `GET /os/api/status` → `model_up: true`, active model resolved.
2. `POST /os/api/chat` → `POST /os/stream/{sid}` → SSE token stream: `"Understood. I will handle that with care..."`, persisted to a session.
3. `POST /os/api/plan {goal:"list the jarvis directory and tell me what is here"}` → plan completed; steps `tool:list_dir` (9ms) + `chat:os` (278ms) succeeded; auto-reflection `"tool:list_dir returned instantly (9ms) - candidate for caching"` stored.
4. Gateway contract unchanged: `POST :8140/v1/intent` still returns the decision shape the frozen web body expects.

## 5. Notes / rough edges (honest)

- `model:load/unload` for `llama_cpp` verifies the launch conf and records intent; actual process launch/stop stays with the launcher scripts in this milestone (documented in the capability response).
- `chat:os` history is the session's persisted messages (bounded by the world store); long-running sessions are not yet summarized.
- The planner is rule-based (no model decomposition yet); the step graph is the growth point.
- `/os/events` is live-only (no replay); the Console shows events after the page loads.
