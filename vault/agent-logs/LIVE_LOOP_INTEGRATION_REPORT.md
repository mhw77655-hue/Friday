# JARVIS Live Loop — Integration Report

**Date:** 2026-08-06
**Scope:** One working offline session — real TTS, STT bridge, state server + reactor, one integrated loop (speak → listen → think → speak → idle).
**Environment:** Termux + Ubuntu-proot on Realme 9 Pro 5G, fully offline, no cloud. Live code lives at `~/jarvis` (`/data/data/com.termux/files/home/jarvis`), not in this repo; this vault mirrors docs/decisions/logs only.

---

## 1. What was built / verified this pass

The four stages were **staged by a prior session** and **verified live this pass** (files already existed — this session confirmed they run, fixed the runtime, and proved the loop).

| Stage | Pieces | Status this pass |
|---|---|---|
| 1. Real TTS | `voice.py`, `voice/piper/` (ARM64 binary + espeak-ng + onnxruntime), `voice/models/en_GB-alan-medium.onnx` | ✅ **Verified live.** `python3 voice.py --synthesize` produced a real 87 KB WAV; `termux-media-player play` returned "Now Playing" (rc 0); wired into `human_core.py` via `voice.speak()`. |
| 2. STT bridge | `stt-bridge/` Kotlin app (SpeechBridge, HttpServer, MainActivity), `stt.py` client, `app-debug.apk` | 🔶 **Code-complete, not yet on device.** APK built (806 KB), contract reviewed and matching. Install blocked on this production device — see §4. |
| 3. State server + reactor | `state_server.py` (:8123), `state_publish.py`, `reactor/index.html` | ✅ **Verified live.** GET/POST/health exercised over HTTP; reactor JS passes `node --check`; state lists agree. |
| 4. Integration | `human_core.py` loop, `chat.py`, `start_all.sh` (new) | ✅ **Verified live.** One typed turn through the real loop: `decide_tool` (LLM) → `generate` (LLM) → `self_check` → `remember` → `voice.speak` (audio) → `idle`. Reactor state sampler recorded the live cycle **thinking → speaking → idle** at 400 ms polling. |

### Verification evidence (this session)
- `GET /state` → `{"state":"idle","since":...}`; `POST {"state":"listening"}` → state flips and is returned; unknown state logs a warning.
- Llama-server (`Llama-3.2-3B-Instruct-Q4_K_M.gguf`, ctx 1024, 4 threads) loaded and answered `/v1/chat/completions` and the `/completion` grammar call for tool decision.
- A real conversational turn produced a coherent reply, was persisted to `recall`, logged to `habits`, and `state_log`, and the reply was spoken through the Android media player.
- `start_all.sh` (new, this session) — one command that starts llama-server + state server if not already running, then drops into `chat.py`. Tested: detects running servers and reaches the loop.

---

## 2. Voice / model substitutions

**Voice — no substitution required.** The persona (per `identity.py`: calm British male companion, openness 0.75, conscientiousness 0.85) maps to **`en_GB-alan-medium`** — espeak voice `en-gb-x-rp` (Received Pronunciation), 22050 Hz, piper v1.0.0, ARM aarch64. This resolves the discrepancy flagged in `COMPANION_CORE_SPEC.md` §2.10 (persona-intended British male vs the previously staged `en_US-amy-low`). The preferred voice was available, so no confirmation was needed.

| Component | Actual | Notes |
|---|---|---|
| LLM | `Llama-3.2-3B-Instruct-Q4_K_M.gguf` (2.0 GB) | `config/active_model.conf` → `llama3.2-3b` |
| TTS | Piper `en_GB-alan-medium`, `termux-media-player` playback | fully on-device |
| STT | `android.speech.SpeechRecognizer` + `EXTRA_PREFER_OFFLINE=true` | uses Google's offline recognition pack — availability surfaced honestly at runtime via `isOnDeviceRecognitionAvailable` (never assumed) |

---

## 3. State server contract (as built)

`state_server.py` — threadpool HTTP server on `127.0.0.1:8123` (override `JARVIS_STATE_PORT`).

```
GET  /state   -> {"state": <str>, "since": <unix epoch seconds float>}
POST /state   -> body {"state": <str>}; updates state + since; returns new object
GET  /health  -> {"ok": true, "service": "jarvis-state-server"}
```

- **CORS:** `Access-Control-Allow-Origin: *` on every response so `reactor/index.html` opens directly from `file://` in Chrome and still reaches the server.
- **All responses JSON.** Unknown state strings are accepted but logged, so a typo surfaces instead of silently breaking the reactor mapping.
- **Publish side (`state_publish.py`)** never raises; if the server is down the loop continues, with the first failure per state logged.

**State published by the loop at each transition** (wired into `human_core.py` / `chat.py`):

| Loop stage | State | Where published |
|---|---|---|
| before STT capture | `listening` | `chat.py` |
| during `call_model()` / think | `thinking` | `human_core.think()` start |
| during TTS playback | `speaking` | `human_core.think()` before `voice.speak()` (also on the habit fast-path) |
| waiting for input | `idle` | `chat.py` |
| tool failure or fabrication check | `warning` | `human_core.run_tool()` / `self_check()` |

**Reactor (`reactor/index.html`)** — single file, Canvas 2D, no frameworks. Polls `GET /state` every 400 ms; procedural particle sphere with per-state color/motion (idle blue/ambient, listening teal/inward ripple, thinking amber/high churn, speaking white/outward speak-pulse, warning red/stutter-alarm, sleep violet/still, offline steel/dim). Hidden debug control: click the faint dot bottom-right, press `d`, or `?debug=1` — panel buttons POST to the real server (exercises the contract) and fall back to a local preview if the server is down.

---

## 4. What is real vs stubbed vs pending

**Real and verified live:**
- Piper TTS (synthesis + audible playback), LLM brain, Human Core decision loop, memory/state persistence, tool-calling grammar, fabrication check, state server + reactor rendering, typed-input conversation.

**Code-complete but blocked on physical device (STT voice input):**
- The `stt-bridge` APK is built and its contract matches `stt.py`, but **it is not installed**. This is a production ROM (`ro.debuggable=0`, `ro.secure=1`); this environment is a userland proot running as the Termux app UID, so `pm install` is denied (`INTERACT_ACROSS_USERS_FULL`), there is no adbd and no wireless-debugging listener, and the phone is not kernel-rooted. Installing needs a physical-device action. **Decision (user): manual install** — see §5.
- **Offline recognition language pack:** cannot be confirmed from here. It is surfaced honestly at runtime by the bridge's `GET /stt/status → on_device_available`. If false, STT reports it rather than faking speech.

**Until the bridge is installed, the loop is fully usable via the designed fallback:** `chat.py` defaults to voice, detects the unreachable bridge, and drops to typed input for that turn (never dead-ends). Run with `--text` to skip the probe.

---

## 5. Manual STT bridge install (user action, in progress)

The APK was copied to `/sdcard/Download/jarvis-stt-bridge.apk` for convenience.

1. Open the file in a file manager and tap-install (grant "install unknown apps" for the installer).
2. Open the **JARVIS STT Bridge** app — it requests `RECORD_AUDIO` and shows its loopback status. The HTTP server binds `127.0.0.1:8765` automatically.
3. Verify from Termux: `curl http://127.0.0.1:8765/stt/status` → check `on_device_available` is `true` (offline recognition pack installed) and `mic_permission_granted` is `true`.
4. Manual test without the loop: `python3 ~/jarvis/stt.py` — speak, confirm a transcription prints.
5. Full loop: `~/jarvis/start_all.sh` (omit `--text`).

---

## 6. Design-law reconciliation note (identity spec vs implementation)

The identity engine spec (delivered at `vault/architecture/JARVIS_IDENTITY_ENGINE_SPEC.md`, currently also at `/sdcard/Download/JARVIS_IDENTITY_ENGINE_SPEC.md`) defines **exactly eight canonical states**: idle, waking, listening, thinking, speaking, warning, critical, sleep.

Current implementation:
- `state_server.py` accepts 10: idle, listening, thinking, speaking, warning, sleep, **offline, building, success**, critical.
- `reactor/index.html` maps 7: idle, listening, thinking, speaking, warning, sleep, offline.

Deviations and rationale (documented, not changed this pass — the required loop states all work):
- `offline` is used only as the reactor's **link-status fallback** when the server is unreachable (per spec §12, network/capability loss should live in Warning; the reactor's own "offline" display is its UI link state, not a JARVIS state).
- `building`, `success` are accepted but never published by the loop.
- `waking` (ignition transition) and a mapped `critical` are spec-defined but not wired into the loop; the loop publishes its transitions directly (listening/thinking/speaking/idle), and the reactor eases all color/motion, so there are no hard visual cuts. Future work: add a `waking` ignition state and map `critical` per the spec.

---

## 7. Runtime notes (proot environment)

- Python modules resolve `~/jarvis/*` against `$HOME`. In Termux that's already the Termux home; inside proot `$HOME=/root`. `start_all.sh` pins `HOME=/data/data/com.termux/files/home` so the same command works in both.
- Generation is **slow on this device** (~3–10 tok/s; one full reply+prompt-eval took ≈190 s on the first turn). This is expected hardware-bound behavior of a 3B Q4 model on a mid-range phone at 4 threads — not a defect.
- `chat.py` raises `EOFError` if stdin closes (piped input ending) — expected; interactive use is unaffected.

---

## 8. How to run the one loop

```bash
~/jarvis/start_all.sh            # full loop, voice input (once bridge installed)
~/jarvis/start_all.sh --text     # full loop, typed input (works today)
# reactor: open ~/jarvis/reactor/index.html in Chrome
```

Files this pass touched/created: `start_all.sh` (new), `/sdcard/Download/jarvis-stt-bridge.apk` (copy). All stage code was already present from the prior session and was verified, not rewritten.
