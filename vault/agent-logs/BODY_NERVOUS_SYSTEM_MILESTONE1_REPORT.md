# BODY_NERVOUS_SYSTEM_MILESTONE1_REPORT

**Date:** 2026-08-09
**Scope:** The `body/` package — a single nervous-system coordinator for the communication body (voice in → understand → think → speak), replacing the old DiagnosticsViewModel-gated voice loop.
**Status:** COMPLETE. Compiles, 294 unit tests green (20 new for communication behavior), full `assembleDebug` APK builds.

---

## What was built

### 1. Nervous-system coordinator (`body/BodyCoordinator.kt`)
Single-threaded state machine (16 `BodyState`s) owning the voice pipeline:
- Vosk (wake word + English command STT), sherpa-whisper (English fallback), TTS.
- Push-to-talk, barge-in/interruption, memory retrieval, model lifecycle, presence.
- The pure transition table was extracted to `BodyStateMachine` so the whole communication state machine is unit-testable without Android.

### 2. Voice loop wired into the coordinator (removed the SystemScreen gate)
- **Before:** the wake-word loop lived in `DiagnosticsViewModel`, so it only ran once SystemScreen was composed.
- **Now:** the coordinator starts in `JarvisEngine.init` (before any screen) and owns the loop:
  - `JarvisMic` feeds Vosk continuously (existing); completed `"jarvis <command>"` turns arrive via `VoskBridge.lastResult` → `SttResult` → `UserInput` → **`LatencyLayer.onUserInput`** (the live turn pipeline: ack speech → brain → reply).
  - Sherpa fallback results route the same way.
  - `DiagnosticsViewModel` now only reports status; the gate is gone.
- **Fixed a duplication:** `JarvisEngine` no longer creates a second set of Vosk/Sherpa/TTS engines — the coordinator is the sole owner.

### 3. Interruption / barge-in + streaming response
- Hearing "jarvis" (or pressing MIC) mid-speech cuts TTS and goes `INTERRUPTED`; the follow-up command is accepted and routes to a new turn (was being dropped).
- Reply TTS is now **sentence-streamed**: `LatencyLayer`'s reply speech is bound to `StreamingTts`, which splits the reply, picks the TTS voice **per sentence** (English ↔ Egyptian Arabic code-switching), and plays each as it is synthesized. Falls back to one-shot speech when the body subsystem isn't wired.
- `StreamingTts` engine is lazy (was eagerly spinning up a second Google TTS at boot).
- Interruption cuts both the ack path (`JarvisTts`) and the streaming path.

### 4. Visual states driven from nervous-system state
- `VisualStateBridge` now pushes each body state into the Companion Core `PresenceEngine` via `requestTransition` (WAKING/LISTENING/THINKING/SPEAKING/ASLEEP…) so the orb follows real nervous-system state; alert layer (CRITICAL/WARNING) stacked on ERROR/INTERRUPTED.
- Previously the bridge computed a reactor state nothing consumed.

### 5. Presence + model lifecycle + RAM strategy
- Presence monitor wired (sensors + interaction → `UserPresenceChanged`).
- **Idle model unload is real now** (was `Log.i("would unload")`): after 2 min idle the active provider's model is unloaded.
- **RAM governor:** when the Companion Core resource tier drops to LOW (thermal/battery step-down) or the app is background-suspended, the mic keeps capturing (cheap) but the **continuous Vosk decode feed is paused** — a real CPU/RAM saving on weak devices.
- Single shared `MemoryStore` (coordinator now injects JarvisEngine's instance instead of building a second one).

### 6. Language routing: English + Egyptian Arabic + code-switching
- Script-based detection + per-word segmentation extracted into pure `LanguageDetection` (testable).
- TTS routes each sentence to the right voice (`en-US` / `ar-EG`) via the streaming path.
- **Arabic STT** — the bundled Vosk/sherpa models are English-only, so the push-to-talk path uses the **Android platform `SpeechRecognizer`** (`PlatformRecognizerStt`) with `ar-EG` locale. Honest fallback: when unavailable, it degrades to sherpa (English).

### 7. Unit tests for communication behavior (20 new, all green)
`BodyCommunicationTest` — state-machine paths (wake→command→think→speak→idle, blank-result, streaming chunks), interruption/barge-in, push-to-talk, presence/memory/learning/error recovery, and language detection incl. code-switching.

---

## Verification

| Check | Result |
|---|---|
| `:mobile:app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `:mobile:app:testDebugUnitTest` | 294 tests, 0 failures (20 new body tests) |
| `:mobile:app:assembleDebug` | BUILD SUCCESSFUL (12m44s) |

---

## Honest caveats (no fabrication)

- **On-device verification not performed** — no device in this environment. Compiled green + JVM tests are the only verification.
- **Arabic STT depends on the device's platform recognizer** (Google app + ar-EG language pack). If absent, Arabic push-to-talk cannot transcribe; English still works via Vosk/sherpa.
- **Default model provider is heuristic** — the deep brain answers only when a real provider (llama.cpp/Ollama) is configured and running.
- **Streaming TTS pace is reply-whole, then sentence-by-sentence** — the model path is one-shot (`ModelManager.lastReply`), so the *first* sentence can't start before the full reply arrives. Sentence-level synthesis still lets long replies begin speaking sooner and cuts cleanly on interruption.
- Manual push-to-talk "STOP" button in the conversation UI isn't wired to stop the live recognizer (the recognizer self-endpoints).

---

## Files

- New: `body/BodyCoordinator.kt`, `body/BodyStateMachine.kt`, `body/BodyTypes.kt`, `body/LanguageRouter.kt`, `body/LanguageDetection.kt`, `body/PlatformRecognizerStt.kt`, `body/StreamingTts.kt`, `body/MemoryStore.kt`, `body/VocabularyStore.kt`, `body/PresenceMonitor.kt`, `body/VisualStateBridge.kt`, `body/VisualState.kt` (+ `latency/` package), test `body/BodyCommunicationTest.kt`.
- Modified: `JarvisEngine.kt` (sole-owner voice engines, streaming-speak binding, coordinator wiring), `JarvisMic.kt` (speechEnded signal, feed-pause governor), `LatencyLayer.kt` (streaming reply seam), `DiagnosticsViewModel.kt` (gate removed, status-only).
