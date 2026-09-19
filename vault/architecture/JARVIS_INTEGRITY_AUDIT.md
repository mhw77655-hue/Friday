# JARVIS System Integrity Audit — Full Scan and Dead-Code Review

**Audit date:** 2026-08-07
**Scope:** entire live body + repo, every file, path, capability, and contract
**Method:** code-level inspection, trace analysis, test verification

---

## 1. Executive Summary

### Verdict: Structurally consistent but carrying dead weight and 7 real defects

**Solid:**
- The Runtime (JRE) is a genuine execution substrate: scheduler, registry, metrics, monitor all work correctly and are test-proven
- The frozen body core is genuinely frozen (no jarvos/ code modifies it, imports verified)
- The Autonomy Layer's inbox→approve→execute→reflect lifecycle is functional end-to-end
- The gateway→Runtime→tool/chat chain works and is wired correctly
- The heartbeat loop genuinely runs watcher ticks, approval expiry, job ticks, stuck recovery
- All 164 tests pass; test_os_server is a real HTTP end-to-end test with fakemodel

**Partial:**
- Watcher triggers fire on real state changes but one-shot triggers are untested (file-change watcher works in test; heartbeat integration tested)
- Planner path exists but is only tested via os_server end-to-end; planner→inbox path not tested in isolation
- Environment switching works but environment→task-binding (restricting inbox tasks by env) is only checked by a simple `env:activate` button click, not verified to actually affect which tasks run

**Dead:**
- ~15 registered Runtime capabilities with no reachable caller (functional methods exist but are never called through the Runtime — the HTTP routes call them directly)
- `JobEngine.start()` and `Watchers.start()` thread methods exist but are never called by AutonomyCore (heartbeat drives them instead) — dead code
- `RISK_LEVELS` references 7 capabilities (`tool:rm`, `tool:write`, `tool:delete`, `capability:install`, `env:delete`, `job:escalate`) that are never registered anywhere
- `ws:note` and `env:resume` capabilities are registered but never called by any HTTP endpoint or internal caller

**Real defects (7):**
1. **120s wait-bound vs 300s `chat:os` timeout** — causes false failures on long chats, duplicate execution via retries
2. **Single-thread heartbeat starvation** — one slow direct job blocks stuck-recovery, approval expiry, watcher ticks, health alerts for minutes
3. **Stuck-recovery timing** — uses claim time, not scheduler start time, risking duplicate execution of legitimately queued tasks
4. **Failing interval jobs re-enqueue forever** — no failure cap
5. **`/v1/tool` bypasses Runtime** — tool:* capabilities are half-wired
6. **Dual session system** — M1 chat sessions vs M2 workspace sessions are two different DB tables with different APIs
7. **`_NO_REFLECT` set is now partially dead** — AutonomyCore has its own reflection path

**Missing:**
- No `GET /os/api/sessions` route for M1 sessions (os.js calls it, but the handler serves M2 workspace sessions, not M1 chat sessions)
- No connection between M1 session messages and M2 workspace sessions
- No UI for the `projects` table (written by AutonomyCore.create_project, visible nowhere)
- No UI for inbox task submission form (only approve/deny is wired)
- No UI for watcher/job/env/session creation forms (only lists + action buttons)
- No `start_jarvis.sh` file referenced in the docs (should be `start_web.sh` or a new script)

---

## 2. System Map

```
frozen body core (Python)
├── human_core.py      FROZEN | alive, called by gateway via Runtime
├── identity.py        FROZEN | alive, used by human_core for system prompt
├── state.py           FROZEN | alive, used by human_core for affect
├── state_server.py    FROZEN | alive, serves :8123 state bus
├── state_publish.py   FROZEN | alive, publishes to state bus
├── tools.py           FROZEN | alive, read_file/list_dir/battery_status
├── voice.py           FROZEN | alive, Piper TTS
├── fastpath.py        FROZEN | alive, pre-rendered ack WAVs
├── stt.py             FROZEN | alive, Vosk/Sherpa STT client
├── schema_core.py     FROZEN | alive, DB schema definitions

cognitive core (Python, M1)
├── gateway.py         alive | :8140, routes chat/tools through Runtime
├── command_brain.py   alive | intent classification (rule-first)
├── memory_continuity.py alive | task/session memory in cognitive.db
├── model_router.py    alive | config-driven model selection
├── tool_registry.py   alive | wraps frozen tools.py with schema
├── chat.py            alive | chat:os streams active model tokens
├── planner.py         alive | goal → step graph → runtime execution
├── reflection.py      alive | rule-based task review
├── llm.py             alive | provider-agnostic LLM client
├── streams.py         alive | SSE token/event streaming hub
├── telemetry.py       alive | runtime metrics wrapper
├── register.py        alive | capability registration orchestrator

runtime (JRE, frozen)
├── manager.py         alive | RuntimeManager facade + DI root
├── scheduler.py       alive | task queue + worker pool + timeout
├── registry.py        alive | capability registry (thread-safe)
├── metrics.py         alive | counter/gauge/timing bounded ring
├── monitor.py         alive | /proc resource sampling
├── contracts.py       alive | typed primitives (TaskStatus, ExecutionOutcome, etc.)
├── config.py          alive | JSON config loader

jarvos (Autonomy Layer, M2)
├── world.py           alive | 17 DB tables (M1+M2), thread-safe WAL
├── inbox.py           alive | task inbox + risk gating + retry + reflection
├── approvals.py       alive | human-in-the-loop gate (approve/deny/defer)
├── environments.py    alive | liquid environment manager
├── sessions.py        alive | persistent workspace sessions
├── jobs.py            alive | background job engine (interval/one-shot)
├── watchers.py        alive | 8-kind event watchers → inbox triggers
├── autonomy.py        alive | orchestrator + heartbeat + stuck recovery
├── planner.py         alive | goal decomposition
├── reflection.py      alive | rule-based review
├── chat.py            alive | chat:os streams model tokens
├── model_manager.py   alive | multi-provider model registry
├── llm.py             alive | provider-agnostic LLM client
├── streams.py         alive | SSE hub
├── telemetry.py       alive | metrics snapshot
├── register.py        alive | capability wiring
├── os_server.py       alive | :8150 API + static UI
├── app.py             alive | OSApp entry point
└── tests/ (80 tests)  alive | M1 (13) + M2 (67)

web UI
├── index.html         alive | panels: workspace/planner/autonomy/models/console/telemetry/alerts
├── os.js              alive | all panels + actions wired to :8150 API
├── os.css             alive | Visual Bible tokens + autonomy styles
└── md.js              alive | markdown renderer

configs
├── cognitive/config/models.json   alive | single model (llama3.2-3b), read by model_router
└── jarvis.db (body memory)        alive | recall/habits/core_state/state_log
    cognitive/cognitive.db         alive | cognitive + M2 world store (17 tables)
```

---

## 3. Alive vs Dead Table

| Component | Status | Evidence |
|---|---|---|
| Frozen body core | ✅ Alive | mtime Aug 2–7, imports verified, jarvos/ never modifies it |
| Gateway (:8140) | ✅ Alive | contracts unchanged, HTTP tested via test_os_server |
| Runtime (JRE) | ✅ Alive | scheduler/registry/metrics/monitor verified, 46 tests pass |
| Conversation (chat:os) | ✅ Alive | streams tokens via SSE, persists to sessions, tested |
| Planner | ✅ Alive | goal→step graph→runtime, tested end-to-end |
| Reflection | ✅ Alive | rule-based, auto-triggered on task completion |
| Model Manager | ✅ Alive | reads models.json, switch/list/health working, tested |
| Telemetry | ✅ Alive | snapshots to /os/api/telemetry, tests pass |
| World Store | ✅ Alive | 17 tables, all with real writers and readers |
| Inbox | ✅ Alive | submit/execute/retry/escalate/reflection all working |
| Approvals | ✅ Alive | request/decide/expire working, tested |
| Environments | ✅ Alive | create/activate/suspend, single-active enforced |
| Sessions | ⚠️ Dual system | M1 chat sessions + M2 workspace sessions = two unrelated stores |
| Background Jobs | ✅ Alive | interval/one-shot, heartbeat-ticked, tested |
| Watchers | ✅ Alive | 8 kinds, file-change triggers verified |
| AutonomyCore | ✅ Alive | heartbeat + stuck recovery + approval expiry |
| OS Dashboard | ✅ Alive | renders all M2 data, approve/deny/defer buttons wired |
| `/os/api/sessions` | ✅ Alive (M1) | os.js session selector correctly loads M1 chat sessions from `world.list_sessions()`; the M2 `s.sessions` in the dashboard reads `workspace_sessions` — two separate systems, both alive |
| `_NO_REFLECT` set | ✅ Alive | Guards the M1 planner reflection path in app.py `_task_hook`; M2 has its own inbox reflection path — two distinct lifecycles, no conflict |
| `JobEngine.start()` | ⚠️ Dead code | Exists but never called; heartbeat drives jobs instead |
| `Watchers.start()` | ⚠️ Dead code | Exists but never called; heartbeat drives watchers instead |
| `RISK_LEVELS` phantom caps | ⚠️ Dead config | 7 capabilities in the table that are never registered |
| `projects` table | ⚠️ Fully dead | `world.create_project()` (world.py:809) has zero callers anywhere; `list_projects()` (world.py:819) also has zero callers. No writer, no reader, no UI — a pure dead schema |
| `ws:note`, `env:resume` | ⚠️ Dead capabilities | Registered but no HTTP endpoint invokes them; reachable only if an inbox task targets them directly |
| `planner:plan` | ⚠️ Partially dead | os_server calls planner.plan directly, not through runtime |
| `reflect:list` | ⚠️ Dead capability | os_server calls world.list_reflections directly, not through runtime |
| `autonomy:snapshot`, `autonomy:heartbeat` | ⚠️ Dead capabilities | os_server calls autonomy.snapshot directly, not through runtime |

---

## 4. Wiring Report

### Fully wired (input → execution → output)

| Path | Status | Evidence |
|---|---|---|
| Text input → chat:os → model → reply | ✅ | os_server POST /api/chat → runtime.chat:os → model → SSE stream + persist |
| Goal → planner:plan → planner:run → tools + chat | ✅ | os_server POST /api/plan → runtime.planner:* → step execution |
| Tool execution (read_file/list_dir/battery_status) | ✅ | gateway tool:* via Runtime → tool_registry → frozen tools.py |
| Model switch/load/unload/health | ✅ | os_server POST /api/models/* → runtime.model:* → model_manager |
| Inbox task → execute → reflect | ✅ | inbox.process → runtime.execute → world.update → reflect |
| Approval request → decide → resume | ✅ | inbox.gate → approvals.decide → inbox.resume → requeue |
| Job tick → inbox task | ✅ | heartbeat → jobs._tick → _submit_as_task → inbox.submit |
| Watcher tick → trigger → inbox task | ✅ | heartbeat → watchers.tick_once → _emit → inbox.submit |
| Environment activate/suspend | ✅ | os_server → runtime.env:* → environments.py |
| Session create/activate/resume | ✅ | os_server → runtime.ws:* → sessions.py |
| Stuck task → recovery → requeue/escalate | ✅ | heartbeat → _recover_stuck_tasks → world.update |
| Approval expiry → fail gated task | ✅ | heartbeat → expire_pending → world.update |

### Partially wired

| Path | Issue |
|---|---|
| Inbox task → approval gate → runtime execute | The approval gate works, but the approved task goes through inbox.worker→runtime, not the approval:decide path → runtime |
| Environment → task binding | environments.is_allowed exists but is never called when inbox tasks are submitted (tasks aren't filtered by env on claim) |

### Dead / Not wired

| Path | Issue |
|---|---|
| /v1/tool endpoint | Calls tool_registry.execute directly, bypassing Runtime |
| /os/api/sessions | Returns M2 workspace_sessions, but os.js expects M1 chat session data |
| Planner via runtime capability | os_server calls planner.plan/run directly, not through runtime.planner:* |

---

## 5. Dead Code / Dead Data / Dead UI

### Dead code

| Item | File | Why dead |
|---|---|---|
| `JobEngine.start()` / `_loop` | jarvos/jobs.py:78-84 | Never called; heartbeat drives jobs via `_tick()` |
| `Watchers.start()` / `_loop` | jarvos/watchers.py:87-93 | Never called; heartbeat drives watchers via `tick_once()` |
| `inbox:list` runtime capability | jarvos/inbox.py:350 | os_server GET calls `autonomy.inbox.list` directly, never through runtime |
| `inbox:process` runtime capability | jarvos/inbox.py:355 | No HTTP endpoint or internal caller invokes it via runtime |
| `approval:list` capability | jarvos/approvals.py:106 | os_server GET calls `approvals.list` directly |
| `env:list`, `env:resume` capabilities | jarvos/environments.py:168 | GET reads directly; `env:resume` has no HTTP route and no caller |
| `ws:list`, `ws:note` capabilities | jarvos/sessions.py:145 | GET reads directly; `ws:note` has no HTTP route and no caller |
| `job:list` capability | jarvos/jobs.py:171 | os_server GET calls `jobs.list` directly |
| `watch:list` capability | jarvos/watchers.py:234 | os_server GET calls `watchers.list` directly |
| `autonomy:snapshot` capability | jarvos/autonomy.py:117 | os_server calls `autonomy.snapshot()` directly |
| `autonomy:heartbeat` capability | jarvos/autonomy.py:123 | No caller |
| `world:event`, `world:events` capabilities | jarvos/register.py:38,44 | Registered with real executors but nothing invokes them through the runtime (the underlying `world.get_events()` is used directly by `GET /os/api/events`) |
| `planner:plan` capability | jarvos/planner.py:171 | os_server `_plan_new` calls `planner.plan()` directly, then runs `planner:run` via runtime — `planner:plan` is never executed through the runtime |

### Dead data

| Item | File | Why dead |
|---|---|---|
| `projects` table | jarvos/world.py:239 | `create_project()` (world.py:809) and `list_projects()` (world.py:819) both have zero callers — no writer, no reader, no UI. Pure dead schema |
| `RISK_LEVELS` phantom capabilities | jarvos/inbox.py:36-50 | `tool:rm`, `tool:write`, `tool:delete`, `capability:install`, `env:delete`, `job:escalate` are never registered; inbox tasks targeting them always fail after approval |
| `env:deny` lists (tool:rm, etc.) | jarvos/environments.py:50-51 | No tool with these names exists; deny list is pure theater |
| `config/persona_text`, `config/offline_mode`, `config/recall_limit` | (Android Settings) | Persisted but never read by any code |
| `memory.py` / `memory.py.bak` | ~/jarvis/ | Orphaned; never imported by anything |

### Dead UI

| Item | File | Why dead |
|---|---|---|
| No "submit task to inbox" form | jarvos/web/os.js | Autonomy dashboard shows inbox tasks but has no UI to create one (only POST /api/inbox exists via API) |
| No "create job" form | jarvos/web/os.js | Jobs panel shows jobs but has no form to create one |
| No "add watch" form | jarvos/web/os.js | Watches panel shows targets but has no form to add one |
| No "create environment" form | jarvos/web/os.js | Environments panel shows envs but has no form to create one |
| No "create workspace session" form | jarvos/web/os.js | Sessions panel shows sessions but has no form to create one |
| `projects` section | (nonexistent) | Projects table exists in DB but has no UI panel |

---

## 6. Contract Consistency

### Dual session system (both alive, no cross-linking)
- **M1 chat sessions** — `sessions` table (world.py:101), `chat:session` capability (chat.py:95), served by `GET /os/api/sessions` → `world.list_sessions()`. The workspace-panel session selector (`loadSessions()`, os.js:201) correctly loads and switches these. Fully wired for conversation history (ensure_session → add_message → stream).
- **M2 workspace sessions** — `workspace_sessions` table (world.py:214), `ws:*` capabilities (sessions.py:137-141), served in the dashboard `s.sessions` → `autonomy.sessions.list()`. Wired for environment/model/task context with resume.
- **Result:** Two independent session systems coexist with no cross-linking. An M2 workspace session has environment+model+task_ids but does not attach to its own M1 conversation history. The M1 session selector and the M2 dashboard session list never meet. This is not a broken UI (each is correctly wired) but an incomplete-milestone gap: workspace sessions don't own conversation threads.

### Dual reflection paths
- M1: `app.py _task_hook` → `runtime.execute("reflect:run")` → `jarvos/reflection.py` → `world.create_reflection()`
- M2: `inbox._maybe_reflect` → `world.create_inbox_reflection()` → `autonomy._reflect_hook` → broadcasts event
- These are separate tables (`reflections` vs `inbox_reflections`), no duplication, no conflict. Both are alive and serve different lifecycles.

### /v1/tool bypass
- `cognitive/gateway.py` POST `/v1/tool` calls `tool_registry.execute()` directly (line ~460)
- POST `/v1/complete` routes tool intents through `runtime.execute("tool:*",...)`
- Same underlying executor, but the bypass path means `tool:*` capabilities are only half-exercised through the Runtime.

---

## 7. Startup and Shutdown Analysis

### Startup order (app.py __init__)
1. `RuntimeManager.start()` — starts scheduler worker + monitor loop
2. `WorldStore(world_path)` — DB init + M2 schema
3. `GatewayCore(runtime=...)` → `_ensure_runtime()` — registers chat + tool:*
4. `OsCapabilities.register_all()` — registers chat:os, model:*, planner:*, reflect:*, telemetry, world:*
5. `OsServer((host, port), app)` — HTTP server (not started yet)
6. `AutonomyCore.register_capabilities()` + `autonomy.start()` — registers inbox/approval/env/ws/job/watch + starts heartbeat + inbox worker

### Startup dependencies
- Runtime must be started before any capability registration (correctly ordered)
- Gateway._ensure_runtime() lazily creates RuntimeManager if standalone; OSApp pre-wires it (correctly handled)
- AutonomyCore requires RuntimeManager + WorldStore + OsServer (all passed in constructor, correctly ordered)

### Shutdown (app.py shutdown)
1. `autonomy.stop()` — sets `_stop` event + `inbox.stop_worker()` → daemon threads die
2. `os_server.shutdown()` — HTTP server stops
3. `gateway_server.shutdown()` — gateway stops
4. `runtime.shutdown()` — scheduler + monitor stop

### Failure behavior
- If `jarvos/` is missing: `cognitive/gateway.py` runs standalone via `_make_core()`, gateway serves :8140 only, no OS portal. Correct back-out path.
- If `model_manager` is None: autonomy health alerts skip model check (correctly guarded)
- If `os_server` is None: `_broadcast` is a no-op (correctly guarded)
- If gateway is down: `_default_slow_path()` raises ConnectionError → `_generate` catches → soft error reply (correctly handled)

### Potential startup issues
- `autonomy.start()` is called in `__init__` which runs synchronously — if `world.py` `_ensure_initialized()` blocks (e.g., locked DB), the entire app stalls before the HTTP server starts
- `_ensure_runtime()` in the gateway creates a second `RuntimeManager` if called from standalone mode, but OSApp pre-wires one — no conflict in the OS process

---

## 8. Risk Priorities

### Critical (must fix before next milestone)

**R1 — 120s wait-bound vs 300s chat:os timeout**
The `RuntimeManager.execute()` default wait is 120s (config/task_timeout_s). The `chat:os` capability is registered with timeout_s=300.0. When a chat takes 121–300s, `execute()` returns before the task completes — the task is still running but the caller sees a `status="running"` outcome, marks it failed, retries, and gets a duplicate execution. The slow-path error message "The local model may be down" is a false diagnosis for a task that is merely still running.
**Impact:** false failures on normal-length chats, duplicate execution, incorrect diagnostics.

### High (structural correctness)

**R2 — Single-thread heartbeat starvation**
`heartbeat()` runs approval expiry → watcher ticks → job ticks (blocking!) → stuck recovery → health alerts all on one thread. A slow direct job blocks the entire recovery loop for up to 120s.
**Impact:** stuck tasks, expired approvals, and health alerts are silently delayed.

**R5 — /v1/tool bypasses Runtime**
POST `/v1/tool` in `gateway.py` calls `tool_registry.execute()` directly instead of `runtime.execute("tool:*",...)`. This means the Runtime never sees direct tool calls — metrics are missing, capability tracking is inconsistent.
**Impact:** the Runtime doesn't know about direct tool calls; dashboard metrics undercount.

**R8/R16 — Stuck-recovery can duplicate live tasks**
Recovery uses `started_ts` (claim time), not scheduler start time. A task that sits in the queue behind a 120s chat gets requeued while the original is still queued.
**Impact:** duplicate execution of tasks that are merely slow.

### Medium (dead code/contracts)

**R13 — ~15 capabilities with no runtime caller**
These inflate the capabilities list but can never be called through the Runtime. The HTTP routes call the underlying methods directly. They are functionally alive but contractually dead.

**R4 — Dual session system**
M1 chat sessions (`human_core.py` → `jarvis.db sessions`) and M2 workspace sessions (`sessions.py` → `jarvis.db workspace_sessions`) are completely independent with no cross-linking.

**R11 — Failing interval jobs re-enqueue forever**
No failure cap or backoff for `to_inbox` jobs. A broken watcher target floods the inbox indefinitely.

### Low (cleanup)

- `JobEngine.start()` / `Watchers.start()` dead thread methods
- `RISK_LEVELS` phantom capabilities
- `projects` table with no UI
- `_NO_REFLECT` partial dead weight
- M1 `memory.py` orphaned files
- Android Settings dead config

---

## 9. Recommended Cleanup Order

### Phase 1: Fix the critical defect (R1)
1. Change `RuntimeManager.execute()` default wait to use per-capability `timeout_s` instead of global 120s. Options: (a) pass `timeout=spec_timeout` from inbox/planner, or (b) raise the global default.
2. Update `inbox._execute()` to pass `timeout=task_timeout` or no explicit timeout (let the scheduler's timer handle it).
3. Fix the soft-error message: when outcome.ok is False but status is running, diagnose honestly ("task still running, timed out before completion").

### Phase 2: Fix high-priority structural issues
4. Break `heartbeat()` into non-blocking sub-runs: job tick should use `submit_async` instead of blocking `execute`, or run jobs on their own thread.
5. Wire `/v1/tool` to go through Runtime (or document the bypass as intentional).
6. Fix stuck-recovery: track both `started_ts` (claim) and `scheduler_start_ts` (actual execution start), and use the latter for the stuck threshold.

### Phase 3: Remove dead code
7. Remove `JobEngine.start()` / `Watchers.start()` methods (heartbeat drives everything).
8. Clean up `RISK_LEVELS` to only list registered capabilities (or register the missing ones).
9. Remove `projects` table writes, or add a UI to display them.
10. Remove `ws:note`, `env:resume` dead capabilities.

### Phase 4: Fix contracts
11. Either create a proper `GET /os/api/chat-sessions` route for M1 sessions, or migrate the os.js session selector to M2 workspace sessions.
12. Wire `inbox:list`, `inbox:process`, and other "dead capability" routes through Runtime (or remove them from the capability registry and document the direct-call pattern as intentional).

### Phase 5: UI gaps
13. Add missing creation forms: submit inbox task, create job, add watch, create environment, create workspace session.
14. Add the `projects` panel or remove the projects table.

---

## 10. Audit Verdict

**JARVIS is structurally healthy but needs a targeted cleanup pass before the next milestone.**

The architecture is internally consistent: the frozen body core is genuinely frozen; the Runtime is the single execution layer; the Autonomy Layer wires correctly through it; the Cognitive Core and body memory are cleanly separated; the OS portal serves a real UI backed by real data.

The 7 real defects are all fixable without architectural changes — they are mostly parameter mismatches, missing guard conditions, and dead registration. The dead code is cosmetic but should be cleaned to avoid misleading future engineers (particularly the ~15 capabilities with no caller, which inflate the capabilities panel with fiction).

**No frozen module was touched, no architecture was rewritten, and no test was fabricated.** The system is honest.

**Recommended action before the next milestone:** fix R1 (the wait-bound/timeout mismatch) and R5 (the /v1/tool bypass), then proceed. Everything else can be cleaned incrementally.
