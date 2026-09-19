# Web-First JARVIS Shell — Milestone 1 Report

**Date:** 2026-08-06
**Verdict:** First working local web body — **real and verified**, not a demo. One URL (`http://127.0.0.1:8130`), reactor home + presence cockpit + command surface, driven by the frozen Human Core.
**Plan:** `vault/architecture/WEB_FIRST_SHELL_PLAN.md`

---

## 1. What was built

| File | Kind | Role | Verified |
|---|---|---|---|
| `~/jarvis/web_shell.py` | NEW | The web body server (:8130): static UI + `/api/status` `/api/command` `/api/speak` `/api/health`. One `HumanCore` in a worker thread (sqlite thread-bound). | ✅ live |
| `~/jarvis/web/index.html` | NEW | Shell page: full-bleed reactor canvas + CORE STATUS, COMMAND, MEMORY, TOOLS panels + the reactor's required DOM. | ✅ served 200 |
| `~/jarvis/web/reactor.js` | NEW | The reactor engine from `reactor/index.html`, extracted **byte-for-byte** (verified `MATCH: True`). No redesign. | ✅ valid JS |
| `~/jarvis/web/shell.js` | NEW | Cockpit logic: polls `/api/status`, renders panels, command submit, mic → STT bridge, speak, honest link status. | ✅ valid JS |
| `~/jarvis/web/styles.css` | NEW | Visual Bible tokens: Void Black/Deep Space/Steel, state-color accents, glass panels, three-tier hierarchy. | ✅ served |
| `~/jarvis/start_web.sh` | NEW | One-command launcher (state bus + brain + web body). | ✅ tested |

**No existing file was modified.** Human Core, Companion Core, identity, state server, and the STT bridge are untouched.

## 2. Verified end-to-end (this session)

A real command through the web shell:
```
POST /api/command {"text":"hello jarvis, what are you doing right now"}
  → reactor observed: thinking (~75s, llama) → speaking (Piper audible) → idle
  → last_response: coherent LLM reply, real energy=1.00,
    honest "I don't have real-time information on the battery level" (fabrication check live)
```
Also verified: `/api/health` reports `{llama:true, state:true, stt:false}` (STT bridge app currently killed by Android — reported honestly, mic disabled, no fake audio); `/api/speak` triggers audible Piper TTS; static assets serve with correct content-types; `start_web.sh` detects running servers and drops you at the URL.

## 3. State / event contract (unchanged, authoritative)

- `state_server.py :8123` remains the single state source. `GET /state → {state, since}`, `POST /state {state}`, `GET /health`, CORS `*`.
- The reactor (browser) polls `:8123` directly. The shell publishes `listening` on mic-start; `think()` publishes `thinking`/`speaking`; the worker publishes `idle` after each turn.
- STT bridge `:8765` driven directly from the browser (CORS `*`): `/stt/start` `/stt/result` `/stt/stop` `/stt/status`.

## 4. Web body API (:8130)

```
GET  /                 shell page
GET  /reactor.js /shell.js /styles.css     static assets
GET  /api/status       cockpit read: reactor state + StateCore fields
                       (valence/arousal/confidence/uncertainty/motivation/
                       energy/attention_focus/battery) + last command/response +
                       tools + recent memory (recall) + recent events (state_log)
POST /api/command {"text"}   enqueue one HumanCore.think(); returns immediately
POST /api/speak    {"text"}  voice.speak(text) (manual TTS hook)
GET  /api/health   {ok, up:{llama,state,stt}}
```

## 5. Human Core integration (the only touchpoints)

1. `web_shell.py` imports `human_core`, `identity`, `state_publish`, `voice` — reads only; no frozen module edited.
2. One `HumanCore` instance lives in a single worker thread; commands are serialized (sqlite is thread-bound). `/api/status` uses its own per-request DB connection.
3. After `think()` the worker publishes `idle` — the web shell owns the loop now, exactly as `chat.py` did.
4. Voice path: shell publishes `listening` (mic) → `/api/command` → `think()` publishes `thinking` → `speaking` during Piper playback → `idle`.

## 6. The pathway (as built)

```
speak ──▶ STT bridge :8765 (en-GB offline) ──▶ /api/command ──▶ think()
     ──▶ llama-server :8080 ──▶ reply ──▶ speaking (Piper) ──▶ idle
type ──▶ /api/command ──▶ (same)
```
Reactor reflects every step. The reply is spoken aloud and shown as a one-line caption — **not** a chat transcript.

## 7. Stubbed vs real

**Real now (verified):** reactor rendering, state transport, presence/state display with live StateCore fields, typed command → think → audible reply → idle, manual TTS, honest health/link status, memory + tools panels (real DB/identity data), self-check honesty (battery).

**Real-when-available (wired, needs the app open):** voice input. The STT bridge app is built and its offline `en-GB` pack is confirmed working, but Android kills it when backgrounded (no foreground service). When it's open, `/api/health` flips `stt:true` and the mic activates; otherwise the mic is disabled with an honest caption. The full utterance→transcription→command path in `shell.js` is code-complete but a **real spoken capture has not been run through it** — the one remaining on-device verification.

**Deferred (foundation exists, per plan §8):** SSE/WebSocket transport, live voice-amplitude waveform on the reactor (identity §8), Waking/Critical state wiring (identity §2), browser `mediaSession`, expansion modules beyond memory/tools, terminal/log panel.

## 8. Known gaps / reported

- **Read-first files missing:** `vault/agent-logs/COMPANION_CORE_PHASE2_REPORT.md` and `COMPANION_CORE_PHASE2_FIX_REPORT.md` do not exist in the vault. Used Phase1 report + specs instead.
- **STT bridge process lifetime:** the app is killed when backgrounded. Open item — a foreground service would keep it alive (planned, not built in this milestone).
- **STT bridge (Android) is the only remaining Android dependency** — exactly as the web-first doctrine allows (hardware/mic bridge only).
- **First LLM reply latency** on this device is ~75s (3B Q4, 4 threads) — hardware-bound, not a defect; subsequent turns reuse warm state.
