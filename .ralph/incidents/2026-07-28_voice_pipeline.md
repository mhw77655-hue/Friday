# INCIDENT: Voice pipeline (KWS/Vosk/TTS) disabled after repeated native crashes

## WHAT

Repeated native process crashes involving the voice pipeline. The only
recorded failure signature is the one preserved in the d9ca295 commit message
and its KDoc comment:

> "Voice pipeline (KWS/Vosk/TTS) temporarily disabled — it was the actual
> source of repeated native crashes (FORTIFY: pthread_mutex_lock on a
> destroyed mutex)."

No verbatim logcat / tombstone / stack trace (incl. native frames) is
committed anywhere in the repo that preserves the actual crash at disable
time. The FT FORTIFY check fired on `pthread_mutex_lock` against a mutex
that had already been destroyed — that is the extent of the preserved
signature.

## WHERE

Component: **Cannot be narrowed to a single native component from the
available evidence.** The d9ca295 diff disables KWS, Vosk AND TTS **all
three together** in `JarvisEngine.init()` (JarvisEngine.kt), and the commit
explicitly instructs "Re-enable one engine at a time, separately, once each
is isolated." The diff therefore does not make the specific component
determinable, and no crash log names one. The three disabled engines were:
`JarvisKws`, `JarvisVosk`, `JarvisTts`, each posted to its own dedicated
HandlerThread (`JarvisEngine-KWS` / `JarvisEngine-Vosk` / `JarvisEngine-TTS`)
in the immediate predecessor commit 9c3f09d.

Environment: restricted to arm64-v8a ABI only (per predecessor commit
9c3f09d "restrict to arm64-v8a only"). Other environment details (Android
API level, Termux version, model backend, quantization) were not preserved
at disable time and are not present in the repo.

## WHEN

- Commit hash at failure/disable time: `d9ca295` (`d9ca2957f8c5dbd67ce1d291f321d781036ac090`)
- Author: mhw77655-hue <mhw77655@gmail.com>
- Date (verified via `git log`, real not assumed): **Tue Jul 28 05:13:15 2026 +0300**
- Disable landed ~35 minutes after the dedicated-thread architecture it
  replaced: predecessor 9c3f09d "Fix: separate KWS/Vosk/TTS onto dedicated
  threads..." was 2026-07-28 04:38:48 +0300.
- Last known-good: no later commit is recorded that re-enabled the old
  JarvisEngine dedicated-thread voice pipeline; the follow-on architectural
  rework moved voice ownership into `BodyCoordinator.initializeSubsystems()`
  and removed the JarvisEngine KWS/Vosk/TTS threads entirely.

## WHY

**Root cause: unknown — no log or stack trace was preserved at disable
time.**

The commit message asserts a symptom-level signature ("FORTIFY:
pthread_mutex_lock on a destroyed mutex" — a use-after-destroy of a
`pthread_mutex_t`/`std::mutex` in native code) but preserves no log line,
stack, or tombstone that names the failing frame, thread, or engine. Nothing
in the repo (tombstone files, committed logcat, JNI diagnostics, test logs)
captures the actual crash. Per the reconstruction rule, a plausible-sounding
cause is NOT inferred to fill this section.

Ruled-out / unprovable hypotheses (no supporting evidence in repo):
- (cannot confirm) a race between engine teardown and a still-running native
  callback — plausible given the "destroyed mutex" wording, but no frames
  survive to prove a specific engine's lifecycle race.
- (cannot confirm) the dedicated-thread starvation scenario the predecessor
  commit 9c3f09d claimed to fix — that commit describes blocking TTS
  starving KWS/Vosk, which is a queueing problem, not the preserved mutex
  signature.
- Native `std::mutex` usage exists today in `local_inference_engine.cpp` and
  `silero_vad/silero_vad_engine.cpp`, but these are current files and do not
  establish which (if any) of the three disabled engines carried the
  historical bug.

To move this from "unknown" to "root-caused", a real stack trace at advisory
disable-time (or a live reproduction under the re-enabled pipeline) is
required.

## MITIGATION

- **STOPGAP (landed in d9ca295, 2026-07-28 05:13:15 +0300):** removed the
  three `kwsHandler.post` / `voskHandler.post` / `ttsHandler.post` blocks in
  `JarvisEngine.init()`, so no voice engine is started on the dedicated
  threads. "Body/diagnostics/Orb stay fully functional without it" per the
  commit. Deliberately a stopgap, not a fix — the commit itself instructs a
  separate re-enable of each engine once isolated and confirmed stable.
- The old `JarvisEngine-KWS/Vosk/TTS` dedicated-thread architecture was
  subsequently ripped out by a later architectural rework (voice ownership
  moved to `BodyCoordinator.initializeSubsystems()`, which now starts
  `JarvisVosk` + `JarvisSherpaWhisper` + TTS with a recovery/circuit-breaker
  path). This is a structural change, not evidence that the original crash
  is root-caused.

## REPRODUCTION

Not currently reproducible from repo evidence. The disabling commit d9ca295
preserves only the FORTIFY signature phrase, not the steps/device state that
triggered the crash. No reproduction was recorded at disable time.

## STATUS

**bypassed** — the crash-prone native STT stack no longer exists in the tree.
`JarvisVosk.kt` (org.vosk.*, native libvosk.so JNI) and `JarvisSherpaWhisper.kt`
(com.k2fsa.sherpa.onnx.OfflineRecognizer, native ONNX JNI) — the only plausible
sources of the `pthread_mutex_lock on a destroyed mutex` FORTIFY crash — were
deleted (with `JarvisSherpaZipformer`, `VoskBridge`, `SherpaBridge`,
`STTArbitrator`), and STT was replaced with the platform `android.speech.SpeechRecognizer`
(`JarvisSpeechRecognizer` / `PlatformSpeechRecognizerPort`, no JNI, no native
object lifecycle). TTS (`JarvisTts`, pure `android.speech.tts.TextToSpeech`,
never implicated) is re-enabled unchanged. The wake-word pass is probed at
runtime (`HotwordAvailabilityProbe`); where the device's SoundTrigger HAL does
not support custom keyphrase enrollment the body falls back to manual
tap-to-trigger STT.

This is **bypassed, NOT fixed**: the root cause remains permanently unknown
(no preserved stack trace), and it is distinguished from "fixed" accordingly.
Because the crashing native code is deleted rather than repaired, the crash
class itself cannot recur from the in-app STT path — but a live reproduction
of the original fault was never captured, so no claim of understanding the
root cause is made.
