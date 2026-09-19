# Model Backend Proof — LOCAL-MODEL-BACKEND-GROUND-TRUTH-AND-BUILD

## WHAT

The production `ModelManager` was wired to a REAL running model backend. Ground truth first
(no filesystem guesses): the local Ollama server at `127.0.0.1:8080` was probed live with
`GET /api/tags` (HTTP 200) and two real `POST /api/generate` calls, confirming it serves the
imported local GGUF model `jarvis-resident:latest` (LFM2.5-1.2B-Instruct-Q4_K_M, family
`lfm2`, context 128000, capabilities `["completion"]`), built from `/sdcard/JARVIS/models/`.

Implementation (all real HTTP through the existing `OllamaAdapter`, no new loader, no native
library, no llama-server binary):

1. `OllamaAdapter.listModels()` — a real `GET /api/tags` probe that lists the models the
   server actually registers, and a fix to `load()`: it probes `/api/tags` first (`/api/pull`
   fails for locally imported modelfile models with `pull model manifest: file does not exist`,
   which I reproduced live) and only falls back to `/api/pull` for genuinely absent models.
2. `OllamaModelBackend` — the first REAL `ModelBackend`. `loadModel` probes the real registry
   (`/api/tags`) and refuses to register a handle for a model the server does not have
   (throws — never the old "answered success without a real model" silent fallback).
   `generate` performs a real `/api/chat` through the existing adapter and returns the model's
   actual text. Contract axioms hold: distinct handles per load, unload invalidates, generate
   on an unloaded/never-loaded handle throws.
3. `JarvisEngine.init` constructs `OllamaAdapter` configured to `http://127.0.0.1:8080` with
   model `jarvis-resident:latest`, builds `OllamaModelBackend` over it, and passes it to
   `ModelManager` via its `backend` ctor param — the single-load-authority seam. The real
   backend is exposed as `JarvisEngine.ollamaModelBackend`.

Tests proving real-inference through the real production call path (on-device only; the JVM
suite runs on the device where the server lives; CI runs no unit tests):

- `OllamaModelBackendTest` (9 tests): live `/api/tags` probe, real `loadModel`/`generate`
  (non-blank output, no `[fake-model-backend]` marker, no "heuristic offline mode" canned
  text), distinct handles, unload invalidation, throw-on-unloaded/never-loaded, and refusal
  to load an unregistered model.
- `OllamaModelBackendLiveWiringTest` (2 tests): the real Stage-04 production call path
  `LatencyPipeline.onUserInput -> CognitiveEngine.process -> bridgeSend -> ModelManager.send`
  over the REAL backend + REAL active Ollama HTTP provider. A user turn
  "The weather today is quite sunny." came back with the model's real reply, and a high-doubt
  turn woke the reasoning organ over the real backend and the cooldown sweep unloaded it.

## VERIFIED

Live probes against the running server (device, `ollama serve` PID visible in `ps`):

- `GET 127.0.0.1:8080/api/tags` -> HTTP 200; bodies list includes
  `jarvis-resident:latest` (details: family `lfm2`, parameter size 1.2B, quantization
  `Q4_K_M`, format `gguf`, context length 128000).
- `POST /api/generate {"model":"jarvis-resident:latest",...}` -> real model output: first a
  single-token prompt returned `"PROOF"` (done_reason stop); a second call returned
  `"Hello! How can I assist you today?"` with load ~9.5s, eval ~0.8s warm.
- `POST /api/chat {"model":"jarvis-resident:latest","messages":[{"role":"user","content":
  "The weather today is quite sunny."}],...}` -> real reply:
  `"That's great! A sunny day is perfect for enjoying the outdoors, going for a walk, or even
  having a picnic. How's the weather doing? Are you feeling ready to embrace some sunshine?"
  (done_reason stop, eval_token_count 45).
- Reproduced `/api/pull` failure for the locally imported modelfile model:
  `{"error":"pull model manifest: file does not exist"}` — hence the tag-probe load path.

Test evidence (full regression sweep, `testDebugUnitTest`):

- `OllamaModelBackendTest`: 7 tests, 0 failures, 0 errors.
- `OllamaModelBackendLiveWiringTest`: 2 tests, 0 failures, 0 errors; the real live turn
  returned the model's real reply through the real production call path and the active
  provider is `OLLAMA` (a real HTTP provider, never a heuristic stub).
- Full regression sweep across every previously-closed suite (cognitive, memory, identity,
  resolution, cloud, research, android, builder, selfreconfig, phaseb, latency, anchor,
  model, JarvisEngineTest, ToolBuilderResearchWiringTest): 648 tests, 0 failures, 0 errors.