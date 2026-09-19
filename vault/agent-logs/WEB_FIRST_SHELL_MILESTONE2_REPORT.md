# Web-First JARVIS Shell — Milestone 2 Report

**Date:** 2026-08-06 (updated after on-device verification)
**Verdict:** All Milestone-1 stubs are **removed and verified end-to-end on the device**. The web-first body is now fully usable: spoken input is the default path, voice survives backgrounding, state transport is live, waking/critical/waveform are wired. Remaining items are deferred enrichments, not stubs.
**Plan:** `vault/architecture/WEB_FIRST_SHELL_PLAN.md` · **Milestone 1:** `WEB_FIRST_SHELL_MILESTONE1_REPORT.md`

---

## 1. What changed since Milestone 1

| Change | Where | Status |
|---|---|---|
| **Foreground service** so the STT bridge survives backgrounding | `stt-bridge/BridgeService.kt` (NEW), `MainActivity.kt` (thin launcher), `AndroidManifest.xml` (permissions + service) | ✅ **verified on device** — bridge stayed up (`stt:true`) while backgrounded/browser in use |
| **Spoken input is the default path** (auto-arms when idle) | `web/shell.js` (voiceMode loop) | ✅ **verified on device** — spoke "thanks mate" in the browser, full loop answered |
| **Live state transport (SSE)** with polling fallback | `state_server.py` `/state/events`; `reactor.js` EventSource | ✅ verified (pushes `waking`/`thinking`/`idle` instantly) |
| **Waking + Critical states** | `state_server.py` KNOWN_STATES, `reactor.js` STATES + pulse, `state_publish.py` `waking()`, `web_shell.py` boot ignition, `styles.css` accent | ✅ verified |
| **Live waveform** (real amplitude → reactor) | `voice.py` WAV envelope + `amplitude()`; `web_shell.py` `/api/wave`; `shell.js` feeds mic RMS (listening) + WAV amp (speaking) | ✅ verified (`amp 0.44` during real playback) |
| **Cleartext HTTP to the bridge's own loopback** (targetSdk34 blocked it) | `AndroidManifest.xml` `android:usesCleartextTraffic="true"` | ✅ fixed — "bridge unreachable: Cleartext HTTP traffic not permitted" resolved |
| Typed input kept as fallback | `web/shell.js` | ✅ preserved |

**Frozen modules untouched** — `human_core.py`, `state.py`, `identity.py`, `chat.py`, `tools.py`, `memory.py`, Companion Core, `run.sh`: no edits. Only additive hooks (`state_publish.waking()`/`critical()` are new functions, not changes to existing ones).

## 1b. Audit fixes found and applied this session (code-inspected, not just visually)

- **Re-arm raced the reply tail:** after a turn, `renderStatus` ran both `scheduleArm(1500)` *and* `maybeArm()` in the same tick, so the mic re-armed instantly at idle and could swallow Piper's final syllable. A finished turn now arms only through the 1.5s settle window; `maybeArm()` is guarded by `pendingArm` (also blocks the 500ms status poll / 3s health poll from arming early).
- **`done` without `final`:** the recognizer can end with no match; shell.js previously spun polling until the 18s deadline. Now it stops and re-arms after 1.2s.
- **Bridge OPTIONS short-circuit:** the bridge advertised `OPTIONS` in CORS headers but routed it to a GET/POST handler — a preflight could have triggered `startListening`. Added an explicit `OPTIONS` short-circuit. (Browsers don't preflight these simple POSTs today; this is defense.)
- **Mic language pinned:** the persisted language was a stale `hi-IN` (no pack) from the pack scan; re-pinned `en-GB` on the live bridge. Candidates order now `en-GB,en-US,hi-IN`.

## 2. Verified this session (all real, no placeholders)

- **SSE transport:** subscribing to `/state/events` receives the initial state + every POST instantly (observed `idle → waking → idle` pushes in <50ms). Polling `GET /state` still works (contract intact). Reactor pauses its 400ms poll while SSE is connected and falls back to polling on stream drop.
- **Waking boot:** `web_shell.py` runs the ignition sequence (`waking` → 1.4s → `idle`) on launch; the bus and reactor show the transient waking state (Identity §3.2) instead of jumping straight to active.
- **Critical:** a fully mapped state (`#FF1744`, near-stopped ring, slow heavy pulse, down-still arrow) — reachable via the debug control or any publisher.
- **Waveform:** during a real turn the loop sampled `thinking(75s) → speaking → idle`, and `/api/wave` reported the actual WAV envelope (`amp 0.4359, speaking:true`) mid-playback. The reactor's speaking state is now driven by real outgoing audio; listening is driven by the bridge's real mic RMS.
- **Full loop (again):** typed command → think → Piper speech (audible) → idle. Reply honest about internal state and battery (`Battery: Unknown`, no fabrication).

## 3. Real vs stubbed now

**Real (verified on device):** reactor rendering; SSE + poll-fallback transport; waking/critical/waveform; presence + StateCore readout; **spoken-default voice loop** (auto-listen → recognize → think → audible reply → re-listen); **bridge survives backgrounding** via foreground service; typed fallback; manual TTS; honest health; memory/tools panels; fabrication check live.

**Still stubbed / deferred (none block use):**
- Nothing in the priority-1..4 scope remains stubbed — the full spoken loop was verified in the browser on the device.
- **SSE is HTTP/1.1 + one thread per subscriber** (stdlib, offline-first) — fine for localhost/2 clients; WebSocket upgrade is a future transport step.
- **First-reply latency ~75–90s** on this 3B/4-thread device — hardware-bound; the reactor shows thinking throughout, and the waveform path adds no latency.
- **Battery sensor stays "Unknown"** until the Human Core's worker refreshes `device_battery` during an active session (honest `unavailable`, no fabrication).
- Deferred enrichments (foundation exists): live voice waveform rendering, browser `mediaSession`, tools/memory expansion modules, terminal/log panel.

## 4. Remaining blockers (honest)

- **No blocker to daily use.** The full loop is verified. If the user declines the battery-optimization exemption, bridge survival is best-effort (Realme/ColorOS can still freeze it) — foreground service keeps it alive in practice, as observed.
- **Battery-optimization exemption is a one-time user decision** the service requests on first start; declining weakens background survival.
- **Spec reconciliation (documented, not blocking):** Visual Bible §5.4 gives Warning `#FF3B30`/Critical `#FF1744` and §5.7 suggests Critical pulses *faster* (2Hz); the Identity Engine §3.7 mandates Critical pulse *slower and heavier*. I honored the Identity Engine (it sits above the bible) — Critical is `#FF1744` with a slow heavy pulse and near-stopped ring. Worth a future spec amendment to remove the conflict.

## 5. Files touched this milestone

- NEW `stt-bridge/.../BridgeService.kt` · EDIT `stt-bridge/.../MainActivity.kt` · EDIT `stt-bridge/.../AndroidManifest.xml` (foreground service + permissions + `usesCleartextTraffic`)
- EDIT `stt-bridge/.../HttpServer.kt` (OPTIONS short-circuit)
- EDIT `~/jarvis/state_server.py` (SSE + `waking` in KNOWN_STATES)
- EDIT `~/jarvis/state_publish.py` (`waking()`)
- EDIT `~/jarvis/voice.py` (`_envelope`, `amplitude()`, serialized `speak()`)
- EDIT `~/jarvis/web_shell.py` (`/api/wave`, waking boot)
- EDIT `~/jarvis/web/reactor.js` (waking/critical STATES, SSE, amplitude seam, critical pulse)
- EDIT `~/jarvis/web/shell.js` (voice-default loop, amplitude feed, re-arm settle guard, done-without-final)
- EDIT `~/jarvis/web/styles.css` (waking accent)
- Rebuilt APK: `/sdcard/Download/jarvis-stt-bridge.apk` (cleartext + OPTIONS guard)
