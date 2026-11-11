# INCIDENT: TTS Engine Cold-Start Readiness & Process Isolation

## WHAT

During cold start:
1. The app process logs a dozen TextToSpeech "synthesizeToFile/speak failed: TTS engine connection not fully set up".
2. The `:voice` process binds TextToSpeech three times.
3. `StreamingTts.playCached` (StreamingTts.kt:372) subsequently fails `setDataSource` four times because the pre-synthesis files are missing or empty due to premature synthesis attempts.

## WHERE

- `mobile/app/src/main/java/com/jarvis/app/JarvisTts.kt`
- `mobile/app/src/main/java/com/jarvis/app/body/StreamingTts.kt`
- `mobile/app/src/main/java/com/jarvis/app/latency/AckSpeech.kt`
- `mobile/app/src/main/java/com/jarvis/app/voice/provider/SystemTtsVoiceProvider.kt`

## WHEN

- Cold start boot sequence when `JarvisApplication.onCreate` runs.

## WHY

1. **Premature TTS operations:** `JarvisTts` and `StreamingTts` accepted calls (`speak`, `speakImmediate`, `synthesizeToCacheFile`) before Android's asynchronous `TextToSpeech.OnInitListener` returned `TextToSpeech.SUCCESS`. In Android TTS, any call to `speak` or `synthesizeToFile` before connection initialization completes fails with "TTS engine connection not fully set up".
2. **Missing/empty cache playback crash:** When `synthesizeToFile` failed, no valid WAV was written to cache. `StreamingTts.playCached` attempted `player.setDataSource(file.absolutePath)` unconditionally, throwing an exception in `MediaPlayer.setDataSource`.
3. **Multiple TTS owners per process & `:voice` duplicate bindings:**
   `JarvisApplication.onCreate` unconditionally calls `JarvisEngine.init(this)`. In Android, the `:voice` process is declared on `VoiceService` under the same application, so `JarvisApplication.onCreate` runs in both `main` and `:voice`.
   In `:voice`, `VoiceService` initializes its own `SystemTtsVoiceProvider`, while `JarvisEngine.init` simultaneously instantiated `JarvisTts` (via `BodyCoordinator`) and `StreamingTts`, binding `TextToSpeech` 3 times in `:voice`. In `main`, both `JarvisTts` and `StreamingTts` independently created `TextToSpeech` instances.

## FINDING: DUPLICATE WIRING IN :VOICE

As noted in `REPO_FACTS.md` and the prompt constraints, `JarvisEngine.init` running in `:voice` is known duplicate wiring tracked for a separate story (`ONE-2-COMPOSITION`). We do not change whether `JarvisEngine.init` runs in `:voice` here. Instead, process-level TTS deduplication is enforced via `ProcessTtsHolder` so subsequent TTS creation attempts reuse the existing instance or remain guarded.

## MITIGATION / FIX

1. **Readiness Slot Pattern:** Introduced `TtsReadinessState` (`NotStarted`, `Initializing`, `Ready`, `Failed`) and typed `TtsSlot<T>` gated by `TtsReadinessGate` (mirroring `EngineReadinessGate` / `EngineSlot`).
2. **Process-Level Single Owner:** Introduced `ProcessTtsHolder` to enforce exactly one TTS owner per process. `JarvisTts` and `StreamingTts` in the main process share the underlying TTS instance. In `:voice`, `VoiceService`'s `SystemTtsVoiceProvider` acts as the primary owner.
3. **Gating Call Sites:** Gated all `speak`, `speakImmediate`, and `synthesizeToFile` operations behind the readiness slot so nothing attempts to speak or synthesize before the engine's init callback reports `SUCCESS`.
4. **Failure Containment:** Engine initialization failures are captured and forwarded to `FailureSurface` without throwing uncaught exceptions.
5. **Cold-Start File Validation:** Added file existence and size check (`file.exists() && file.length() >= 44`) in `StreamingTts.playCached` before `player.setDataSource(...)` to prevent `MediaPlayer` crashes on missing or zero-byte audio files.

## STATUS

Fixed and verified via unit tests in `com.jarvis.app.launchsurvival.TtsReadinessTest`.
