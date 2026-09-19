# JARVIS Runtime Environment (JRE) — Milestone 1 Report

**Date:** 2026-08-07
**Status:** Built, integrated, and verified (83 tests green: 45 runtime + 38 cognitive). Live full-stack run confirmed on-device-equivalent processes (state_server → gateway → web_shell → runtime).
**Location:** live body `~/jarvis/runtime/` (the Cognitive Core's sibling package). Docs mirrored in this vault.

## Doctrine honored

- **The Runtime is NOT another AI, gateway, or memory system.** It is the execution environment that manages HOW work executes. It owns scheduling, the execution pipeline, the capability registry, shared execution context, resource/performance metrics, and the execution queue. It never owns personality, never replaces the Human Core, and never reads or writes `jarvis.db` / `cognitive/cognitive.db` content.
- **Zero behavior change.** Existing behavior is byte-identical. All 38 pre-existing gateway/cognitive tests pass unchanged. No frozen module (`human_core.py`, `identity.py`, `state.py`, `state_server.py`, `tools.py`, `voice.py`, `fastpath.py`, `web_shell.py` semantics) was rewritten. No API was added or changed (the Runtime is in-process in M1).
- **The Runtime is passive by default.** It is not "on" until the gateway's first slow-path or tool call. If `runtime/` is absent or fails to import, the gateway executes exactly as before.

---

## 1. Runtime Architecture

```
                      ┌────────────────────────────────────────────────┐
                      │          Consciousness Gateway :8140           │
                      │                                                │
   fast intents ─────►│  Command Brain → decision                      │
   (memory/refuse/    │                                                │
    clarify/queue)    │   slow path / tools ──► RuntimeManager (JRE)   │
                      │                            │  register         │
                      │   register_capability ◄────┤  chat, tool:*     │
                      │                            ▼                   │
                      │   runtime.execute(name) → ExecutionScheduler   │
                      │                            │  bounded queue    │
                      │                            ▼  worker pool (=1) │
                      │                        CapabilityRegistry      │
                      │                            │  executor lookup  │
                      │                            ▼                   │
                      │   chat executor → slow_path → frozen           │
                      │       HumanCore.think() (own worker thread)    │
                      │   tool:* executor → tool_registry.execute()    │
                      │                            │                   │
                      │   events → MetricsCollector + RuntimeLog       │
                      │                            │                   │
                      │   monitor loop → ResourceMonitor (/proc)       │
                      └────────────────────────────────────────────────┘
```

The Runtime is a **passive substrate**: the gateway *delegates* execution to it; the Runtime decides how (queue, worker, timeout, metrics, tracking), never what. Future systems (Planner, Reverse Engineering, Expansion Engine, Reflection, Background Tasks, Multi-Model Routing) plug in by **registering capabilities** — no architectural change required.

## 2. Package Structure

```
~/jarvis/runtime/
├── __init__.py            package doc + test command
├── contracts.py           TaskStatus, CapabilitySpec, RuntimeTask,
│                          RuntimeExecutionContext, ExecutionOutcome,
│                          MetricPoint, ResourceSnapshot, HealthReport
├── config.py              RuntimeConfig (nested dataclasses) + load_config
├── config/runtime.json    shipped defaults (workers=1, queue_max=128,
│                          task_timeout_s=120, monitor 5s, log to tmp/)
├── registry.py            CapabilityRegistry (thread-safe, dup-rejecting)
├── metrics.py             MetricsCollector (bounded ring: counters/gauges/timings)
├── logging.py             RuntimeLog (JSONL, best-effort, never raises)
├── monitor.py             ResourceMonitor (/proc RSS/CPU/threads/mem)
├── scheduler.py           ExecutionScheduler (queue + workers + timeout + reentrancy)
├── manager.py             RuntimeManager (facade, DI root, lifecycle)
└── tests/                 45 tests: contracts, registry, metrics, monitor,
                           scheduler, manager, gateway-integration
```

Stdlib-only by design — no new dependencies.

## 3. Runtime APIs (Python surface — no HTTP in M1)

| API | Purpose |
|---|---|
| `RuntimeManager(config?, registry?, scheduler?, metrics?, monitor?, log?)` | DI root; every collaborator injectable |
| `start()` / `shutdown()` / `is_running()` | idempotent lifecycle |
| `register_capability(spec, executor, health_check?)` | capability entry point for all future systems |
| `execute(capability, params?, timeout?, now?) → ExecutionOutcome` | submit + block; **never raises** on execution failure (returns `ok=False`); the gateway relies on this for honest soft replies |
| `submit_async(capability, params?, now?) → RuntimeTask` | fire-and-forget (future background jobs) |
| `current_context()` | thread-local `RuntimeExecutionContext` visible inside an executor (the planner/reflection seam) |
| `metrics_snapshot()`, `resources()`, `tasks()`, `queue_depth()` | observability |
| `health() → HealthReport` | scheduler liveness + every registered capability's health check |
| `capabilities()` / `capability_health(name)` | registry read side |

## 4. Internal Lifecycle

A task moves `queued → running → succeeded | failed | timed_out | cancelled`:

```
submit(capability, params)
  ├─ unknown capability → ExecutionOutcome(ok=False, UnknownCapabilityError)
  ├─ queue full → ExecutionOutcome(ok=False, RuntimeBusy)   [backpressure]
  └─ enqueued (or inline if now=True on a worker thread)
worker picks task → started_at, status=running, thread-local context set
  ├─ spec timeout Timer armed (default 120s)
  ├─ executor(params) from the registry
  │    ├─ returns → succeeded, result stored
  │    └─ raises → failed, error + error_type recorded
  ├─ Timer fires first → timed_out, _done set, queue freed (zombie drains)
finally → finished_at, duration_ms, context cleared, _done set, listeners notified
```

Every transition fires a listener the RuntimeManager wires into the metrics collector (`tasks.submitted`, `tasks.completed{...}`, `task.duration_ms{...}`, `queue.depth`, `tasks.running`) and the JSONL log.

## 5. Scheduler Design

- **Bounded queue** (`queue_max=128`, config) → backpressure via `RuntimeBusy`, never unbounded growth.
- **Worker pool** (default 1) → strictly serialized execution, matching the pre-Runtime behavior exactly.
- **Per-task hard timeout** (Timer) → a hung capability never wedges the queue; the caller gets `timed_out` and the queue continues.
- **Reentrant `submit(now=True)`** → a task running on a worker may submit a subtask that runs inline on the same thread (the future-planner seam), preserving the frozen core's thread-bound sqlite rule across two hops.
- **Cancellation** of queued tasks; running tasks are left to finish (Python cannot safely kill threads) but their outcome is already `timed_out`/superseded.
- **`current_context()`** — thread-local, available only inside an executor.

## 6. Capability Registry

`name → (CapabilitySpec, executor, health_check)`. One owner per capability — **duplicates rejected loudly**. The gateway registers `chat` (executor → its `slow_path`, i.e. the frozen HumanCore funnel) and `tool:<name>` (executor → `tool_registry.execute`) plus their health checks (`model_router.health(default_model())`, `tool_registry.health(name)`). Health with no registered check is `None` (unknown), never false.

## 7. Metrics System

Bounded `MetricsCollector` (ring, `max_points=10000`): counters, gauges, timings, taggable. Snapshot via `metrics_snapshot()`. Recorded automatically per task lifecycle + per monitor tick (`process.rss_mb`, `process.cpu_percent`, `process.threads`). Sink is `"memory"` in M1 (future: file/exporter).

## 8. Health Monitoring

`RuntimeManager.health()` aggregates: `runtime` (scheduler liveness) + every capability that registered a health check (`capability:<name>`). `ResourceMonitor` samples real `/proc` data (RSS, CPU% delta, thread count, host memory) on a config cadence; unreadable fields are `None` (honest), never fabricated.

## 9. Integration with the Gateway

`cognitive/gateway.py` — **additive only** (imports untouched; one new param; two delegations):
- `GatewayCore(..., runtime=None)` — an optional injected Runtime.
- `_generate` → `runtime.execute("chat", {"text": effective})` when the runtime is up, else `self.slow_path(effective)` directly. The **soft-error reply is byte-identical** for both paths (`_soft_error` helper).
- `_handle_tool` → `_execute_tool` → `runtime.execute("tool:<name>", ...)` when registered, else `tool_registry.execute`.
- `_ensure_runtime()` — **lazy**: creates + starts the Runtime on the first slow-path/tool call; if `runtime/` is missing or fails to boot, the gateway silently runs exactly as before. Registration of `chat`/`tool:*` is idempotent.
- Existing HTTP endpoints, contracts, fast intents, and memory behavior are unchanged.

## 10. Migration Strategy

- **M1 (this milestone):** runtime exists, gateway delegates, behavior identical. The Runtime is a transparent substrate — nothing migrated, nothing rebuilt.
- **M2+:** new systems (Planner, Reverse Engineering, Background Jobs) register capabilities and use `submit_async`/`execute`. The frozen HumanCore stays behind the `chat` capability; no subsystem ever calls it directly.
- **Back-out:** deleting `~/jarvis/runtime/` restores pre-Runtime behavior with zero code change to frozen modules.

## 11. Unit Tests

`python3 -m unittest discover -s runtime/tests` → **45 tests, OK** (stdlib, hermetic, fake slow path, temp stores, ephemeral ports):
- contracts, registry (dup rejection, health), metrics (bounds, tags), monitor (honest None)
- scheduler: lifecycle, strict serialization, failure recording, unknown capability, **timeout frees the queue**, **reentrant now-inline**, current-context, cancel-queued, backpressure, observability
- manager: DI, idempotent lifecycle, execute outcomes (success/failure/unknown), async submit, health aggregation, resources, metrics, log file
- gateway-integration: **replies byte-identical with/without runtime**, **soft-error text identical**, tools routed through runtime, health registration, full HTTP contract with runtime in path

Combined with the untouched cognitive suite: **83 tests green**. Manual on-device checklist is the final step (below).

## 12. Documentation

- `vault/architecture/JARVIS_ENGINEERING_HANDOFF.md` — updated: `runtime/` in the tree, §4.15 JRE Runtime subsystem, §5 Runtime hop, §10 M1 note, §12 Working capabilities, §17 tests, §18 backlog, §19 roadmap, §20 handoff rules (never bypass the Runtime; register-before-execute; runtime/ may evolve but never fork).

## 13. Architecture Diagrams

See §1 (component) above and §5 of the Engineering Handoff (request flow). Runtime task lifecycle diagram in §4 above.

## 14. Deliverable cross-check

1. Runtime architecture ✅ 2. Package structure ✅ 3. Runtime APIs ✅ 4. Internal lifecycle ✅ 5. Scheduler design ✅ 6. Capability registry ✅ 7. Metrics system ✅ 8. Health monitoring ✅ 9. Gateway integration ✅ 10. Migration strategy ✅ 11. Unit tests ✅ 12. Documentation ✅ 13. Architecture diagrams ✅ 14. Updated Engineering Handoff ✅ 15. This report ✅

## Success criteria

- ✅ Existing functionality unchanged (38 cognitive tests green; live replies identical).
- ✅ All existing tests pass.
- ✅ Runtime fully integrated (gateway delegates chat + tools; runtime log/metrics record every execution).
- ✅ Future systems plug in via the capability registry — no architectural change needed.
- ✅ No frozen module rewritten.
- ✅ The Runtime is the permanent execution foundation.

## Manual on-device checklist (final step)

1. `cd ~/jarvis && ./start_web.sh`
2. `curl http://127.0.0.1:8140/health` → `{"ok": true, ...}`
3. Send a deep question; `tail tmp/runtime_log.jsonl` → `task.submitted/started/completed chat`
4. Send a tool command; runtime log → `task.* tool:list_dir`
5. Stop llama-server; same commands → honest soft error, runtime records `failed` chat tasks
6. `python3 -m unittest discover -s cognitive/tests` and `-s runtime/tests` → all green
