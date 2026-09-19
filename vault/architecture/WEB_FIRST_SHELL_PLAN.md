# Web-First JARVIS Shell — Implementation Plan (Milestone 1)

**Date:** 2026-08-06
**Status:** First working web-first body — foundation, not the final luxury shell
**Divergence note:** `COMPANION_CORE_IMPLEMENTATION_PLAN.md` is an Android-first (Kotlin/StateFlow) plan. This plan pivots the *rendering + presence + adapter* layers into a local browser body while honoring the same contracts: the frozen Human Core stays the single source of truth, and the web server implements the Human Core Integration choke point (§2.30) natively instead of via Chaquopy.

---

## 0. Doctrine for this milestone

- **The browser is the body.** The local web app is JARVIS's main interface. Android is only the STT bridge for the microphone.
- **Frozen = untouched.** No edits to `human_core.py`, `state.py`, `identity.py`, `chat.py`, `tools.py`, `memory.py`, or `run.sh`. The web shell only *calls* them.
- **No redesign of the visual identity.** The reactor rendering engine from `reactor/index.html` is reused byte-for-byte (extracted to a module), and the shell chrome follows the Visual Bible tokens.
- **No chatbot screen, no dashboard.** The shell is a reactor home + presence cockpit + command surface. Responses are spoken aloud and shown as a status caption — not a transcript.
- **No cloud, no fake data.** Every panel value is real (StateCore row, recall rows, `state_log`, `identity.CAPABILITIES`, live battery when readable). Missing data renders as "unavailable", never a guess.

## 1. Architecture (as built)

```
Browser (Chrome, 127.0.0.1:8130)          Local processes (Python, unchanged cores)
┌─────────────────────────────────────┐
│  web/index.html  (shell)            │
│   ├─ reactor.js  (verbatim reactor) │────poll 400ms──▶ state_server :8123  (state bus)
│   ├─ shell.js    (cockpit UI)       │────poll 500ms──▶ /api/status (same-origin)
│   ├─ styles.css  (Visual Bible)     │
│   └─ panels: CORE STATUS ·          │
│            COMMAND · MEMORY · TOOLS │
└──────────────┬──────────────────────┘
               │ same-origin fetch
               ▼
      web_shell.py  :8130   (NEW — the body server)
         ├─ static UI serving
         ├─ Human Core adapter (single HumanCore in one worker thread)
         └─ /api/status /api/command /api/speak /api/health
               │
               ▼  calls frozen cores
      human_core.think() ──▶ llama-server :8080, state_publish → :8123, voice.speak (Piper → speaker)
               ▲
               │ voice (offline en-GB)
      STT bridge app :8765  ◀── shell.js drives /stt/start · /stt/result directly
```

### Processes (unchanged except `web_shell.py`)

| Port | Process | Role | Change |
|---|---|---|---|
| 8080 | `llama-server` | LLM brain | none |
| 8123 | `state_server.py` | reactor state bus (authoritative) | none |
| 8130 | `web_shell.py` | web body: UI + adapter + command loop | **NEW** |
| 8765 | stt-bridge app | offline STT (`en-GB`) | none |

## 2. Files

| File | Kind | Purpose |
|---|---|---|
| `~/jarvis/web_shell.py` | NEW | Local HTTP server (:8130). Serves UI + API. One HumanCore in a worker thread. |
| `~/jarvis/web/index.html` | NEW | Shell page: full-bleed reactor canvas + glass panels + reactor's required DOM. |
| `~/jarvis/web/reactor.js` | NEW | The reactor engine extracted **byte-for-byte** from `reactor/index.html` (no redesign). |
| `~/jarvis/web/shell.js` | NEW | Cockpit logic: polls `/api/status`, renders panels, command submit, mic → STT bridge, speak. |
| `~/jarvis/web/styles.css` | NEW | Visual Bible tokens: Void Black, Deep Space, Steel, state colors, panel/typography rules. |
| `~/jarvis/start_web.sh` | NEW | Launcher: ensures state_server (+llama) up, starts `web_shell.py`, prints the URL. |
| `vault/architecture/WEB_FIRST_SHELL_PLAN.md` | NEW | This plan. |
| `vault/agent-logs/WEB_FIRST_SHELL_MILESTONE1_REPORT.md` | NEW | Build report + stubbed-vs-real. |

**No existing file is modified.**

## 3. State / event transport contract (unchanged, authoritative)

`state_server.py :8123` remains the single state source. `GET /state → {"state", "since"}`; `POST /state {state}`; `GET /health`; CORS `*`. All state publishing continues through `state_publish.py` (`listening/thinking/speaking/idle/warning`). The browser reads :8123 directly (CORS already in place) and drives STT on :8765 directly (CORS already in place). The web server adds no second state store.

## 4. Web body API (web_shell.py :8130)

| Endpoint | Method | Purpose |
|---|---|---|
| `/` | GET | serve `web/index.html` |
| `/reactor.js` `/shell.js` `/styles.css` | GET | static assets |
| `/api/status` | GET | cockpit read — see below |
| `/api/command` | POST `{"text":…}` | enqueue one `HumanCore.think(text)`; returns immediately |
| `/api/speak` | POST `{"text":…}` | `voice.speak(text)` (manual TTS hook) |
| `/api/health` | GET | `{ok, up:{llama, state, stt}}` — honest link status |

**`/api/status` payload** (all real): reactor state + since (from :8123) · StateCore fields (valence, arousal, confidence, uncertainty, motivation, energy, attention_focus, battery) · last command + response caption · tools (`identity.CAPABILITIES`) · recent memory (last 6 `recall` rows) · recent events (last 8 `state_log` rows).

## 5. Human Core integration points (the only touchpoints)

1. `web_shell.py` imports `human_core.HumanCore`, `state_publish`, `voice`, `identity`, `tools` — reads only.
2. Commands are serialized through one **worker thread** that owns a single `HumanCore` (sqlite is thread-bound; `/api/status` uses its own per-request connection).
3. After `think()` returns, the worker publishes `idle` — the web shell owns the loop now, exactly as `chat.py` does. (No frozen logic touched.)
4. Voice path: `shell.js` posts `listening` to :8123 on mic start (real capture state), then `/api/command` → `think()` publishes `thinking` → `speaking` during Piper playback.

## 6. The pathway (input → brain → response → state)

```
speak ──▶ STT bridge (en-GB, offline) ──▶ /api/command ──▶ think() ──▶ llama ──▶ reply
  ▶ speaking (Piper → speaker) ──▶ idle      · reactor reflects every step
type  ──▶ /api/command ──▶ think() ──▶ (same)
```

## 7. Build order

1. `web_shell.py` + `/api/health` + `/api/status` + static serving — **test with curl**
2. `reactor.js` extraction (byte-faithful) — **test it serves and is valid JS**
3. `index.html` + `styles.css` + `shell.js` — shell chrome over the reactor
4. `/api/command` + `/api/speak` worker — **test with a real turn**
5. `start_web.sh` — one-command launch
6. Milestone report (stubbed vs real)

## 8. Stubbed vs real (Milestone 1 target)

- **Real now:** reactor rendering, state transport, presence/status readout, typed command → think → speak → idle, manual TTS, honest health.
- **Real-when-available:** voice input (works the moment the STT bridge app is open; honest "unavailable" otherwise — the bridge is killed by Android when backgrounded, a known open item).
- **Deferred (foundation exists):** SSE/WebSocket transport upgrade, live audio-amplitude waveform on the reactor (identity §8), Waking/Critical state wiring per identity §2, browser `mediaSession`/background control, tools/memory expansion modules, terminal/log panel expansion.

## 9. Missing read-first items (reported)

- `vault/agent-logs/COMPANION_CORE_PHASE2_REPORT.md` — **does not exist**
- `vault/agent-logs/COMPANION_CORE_PHASE2_FIX_REPORT.md` — **does not exist**
- Used instead: `COMPANION_CORE_PHASE1_REPORT.md`, the Identity Engine spec (mirrored to the vault), the Visual Bible, and the Companion Core spec.
