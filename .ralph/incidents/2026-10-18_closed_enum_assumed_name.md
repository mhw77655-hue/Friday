# INCIDENT: Closed enum / assumed name fed by outside input, and uncaught organ exceptions killing the process

## WHAT

Two fatal crashes within 5 seconds of launch on the Realme phone (debug APK,
2026-10-16 23:20, Ollama at 127.0.0.1:8080 registering `jarvis-resident:latest`
and `jarvis-reasoning:latest`):

**CRASH A — main process, 1.7 s after launch.** An uncaught exception on a
`Dispatchers.IO` worker killed the whole app:

```
java.lang.IllegalArgumentException: No enum constant com.jarvis.app.body.LanguageRouter.Language.EN
    at java.lang.Enum.valueOf(Enum.java:238)
    at com.jarvis.app.body.LanguageRouter$Language.valueOf(LanguageRouter.kt)
    at com.jarvis.app.body.StreamingTts.synthesizeSegment(StreamingTts.kt:285)
```

**CRASH B — `:voice` process, 5 s after launch.**

```
java.lang.IllegalArgumentException: Ollama server does not register model 'resident';
registered models: jarvis-reasoning:latest,jarvis-resident:latest
    at com.jarvis.app.model.OllamaModelBackend.loadModel(OllamaModelBackend.kt:58)
```

Non-fatal but observed: `BodyCoordinator`, `PresenceMonitor` and a "Model warmed"
log ran in BOTH processes (main pid 4039 and `:voice` pid 4175). `ObsidianSync
logTurn` failed ENOENT for `/storage/emulated/0/JarvisSync/vault/JARVIS/2026-10-16.md`.

## WHERE

Two subsystems, one class of defect — an outside value pushed through a closed
assumption:

- **Speech path.** `com.jarvis.app.body.StreamingTts.synthesizeSegment` (line 285
  before the fix). The `TtsSegment.language` field carries the raw 2-letter code
  `"en"` (set in `speak()` from `LanguageSegment.language.code`), but the code
  called `LanguageRouter.Language.valueOf(segment.language.uppercase())`, and the
  enum constants are `ENGLISH` / `EGYPTIAN_ARABIC`. `valueOf("EN")` always throws.
- **Model load path.** `com.jarvis.app.model.OllamaModelBackend.loadModel`. The
  requested id was the literal `"resident"`, produced by
  `ModelManager.defaultModelId(ModelTier.RESIDENT)`'s fallback when the active
  provider's `ProviderConfig` carries no `modelId`. The old
  `require(modelId in registered)` threw when the server registered
  `jarvis-resident:latest` instead.

Environment: arm64-v8a debug APK on a Realme phone; Android API 26+; local Ollama
server at 127.0.0.1:8080. Backend `OllamaModelBackend` over `OllamaAdapter`.

## WHEN

- Failed at the installed debug build of 2026-10-16 ~23:20, main and `:voice`
  processes, within 5 s of launch.
- Last known-good: none recorded for this build; the crashes were present at
  every launch of it.
- Reproducing tests added under `com.jarvis.app.launchsurvival` on 2026-10-18.

## WHY

**Root cause: a closed enum and an assumed name, both fed by values that
originate outside the code that consumes them.**

- CRASH A: the speech path treated an *external language tag* as if it were an
  *enum constant name*. `valueOf` is a closed lookup that throws
  `IllegalArgumentException` for any string it does not recognise. The synthesis
  loop ran on a `Dispatchers.IO` worker with no `try/catch`, so the throw was
  uncaught on a background thread and terminated the process — an organ failure
  escaped into a process kill.
- CRASH B: the tier's default id `"resident"` was *assumed* rather than
  *resolved against what the server actually registers*
  (`jarvis-resident:latest`). `require(modelId in registered)` is a hard throw,
  and it fired inside the model-load path that the `:voice` process reaches at
  boot.

Both are the same failure shape VISION.md warns about: a closed set fed by
outside input, and an organ exception with no containment.

Ruled out: the crashes were NOT an Ollama outage (the server answered
`/api/tags` 200), NOT an STT/native fault, and NOT the legacy voice-pipeline
crash of 2026-07-28 (that stack no longer exists in the tree).

## MITIGATION

- **PERMANENT-FIX (CRASH A):** `LanguageTagResolver` (new, pure Kotlin) resolves
  an external tag by normalisation + BCP-47 prefix matching; an unknown, empty or
  differently-cased tag degrades to the default voice and is reported as unknown.
  `LanguageRouter.resolveLanguageTag` is the ONE seam for outside tags, and it
  never calls `valueOf`. `StreamingTts.synthesizeSegment` now uses it.
- **PERMANENT-FIX (process containment):** `StreamingTts.startSynthesisLoop`
  wraps each segment in `try/catch`, reports the failure through the existing
  `FailureSurface` (with the stack type + message as `cause`, and a
  plain-language `userMessage`), and keeps serving the next turn.
- **PERMANENT-FIX (CRASH B):** `OllamaModelBackend.loadModel` resolves the
  requested id against the server's registered names — exact match, then the
  `:latest` form, then a degrade to a registered model (preferring the same
  family), then an explicit `EMPTY_REGISTRY` gap. It never throws out of the call
  for a missing tier.

## REPRODUCTION

Automated, in `mobile/app/src/test/java/com/jarvis/app/launchsurvival/`:

- `LanguageTagResolutionTest` — resolves `EN`, `en`, `en-US`, `ar-EG`, `""`,
  `xx-YY` and hostile tags; all must end with a voice choice; unknown tags must be
  recorded and route to the default.
- `ResidentTierLoadTest` — drives `ModelManager.request(RESIDENT)` over a
  `FakeOllama` answering `/api/tags` exactly as the phone's server did; the
  resident tier must resolve to `jarvis-resident:latest`.
- `TypedTurnGenerationTest` — books the resident handle, sends one typed turn,
  and asserts the fake server's `/api/chat` answered with generated text.
- `FailingOrganContainmentTest` — a throwing synthesis loop is contained and
  recorded while the loop keeps serving; an empty registry yields a gap, not a
  throw.

Not reproducible against a live phone from repo evidence alone; the repro
requires the installed APK and the local Ollama registration.

## STATUS

**fixed** — permanent fixes landed (LanguageTagResolver + synthesis-loop
containment + registry-aware model name resolution), with reproducing tests in
`com.jarvis.app.launchsurvival`. The status would move to "regression watch" if a
future build reintroduces `valueOf` on an outside tag or a hard `require` on an
assumed model name.
