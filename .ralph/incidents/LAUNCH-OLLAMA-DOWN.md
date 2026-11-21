# INCIDENT: A model server that is down killed the process

## WHAT

On the second launch of the debug APK the main process died about 2 s in, and
the `:voice` process died too. The throw was:

```
java.io.IOException: Ollama server unreachable at 127.0.0.1:8080 ...
cannot load resident
    at ai.friday.assistant.model.OllamaModelBackend.loadModel(OllamaModelBackend.kt:53)
```

The same two crashes reproduced in the 2026-10-22 and 2026-10-23 launch logs.
No user turn was needed: the failure happened during boot warming.

## WHERE

- `ai.friday.assistant.model.OllamaModelBackend.loadModel` threw an `IOException`
  when `GET /api/tags` could not connect.
- `ai.friday.assistant.anchor.AnchorEngine.anchor` propagated it out of
  `ModelManager.request(RESIDENT)`.
- `ai.friday.assistant.FridayEngine.init` launched that call in
  `CoroutineScope(Dispatchers.Main)` (FridayEngine.kt:489), a scope with no
  `SupervisorJob` and no `CoroutineExceptionHandler`. The uncaught exception
  cancelled the engine scope and killed the process.

`:voice` runs the same `FridayApplication.onCreate -> FridayEngine.init`, so it
died on the same path. The two processes are a known duplication recorded for
ONE-2-COMPOSITION; it is out of scope here.

## WHEN

- Launch logs 2026-10-24 23:53 (and the two preceding days), ~2 s after process
  start, on the Realme phone with Ollama stopped.
- The existing `launchsurvival` tests were green throughout, because they never
  ran the production object graph against a port where nothing listens.

## WHY

**The previous pass tested a rebuilt copy of the thing, not the thing.**

`LAUNCH-SURVIVAL` (passes:true) answered "does a failing organ stay contained?"
with `FailingOrganContainmentTest`, which built its OWN
`CoroutineScope(SupervisorJob() + Dispatchers.IO)` and its OWN catch block. That
proved a *pattern* was sound; it never touched the production engine scope, so
the one scope that actually ran the anchor warm — and the one model-load call
that actually threw — were never exercised. A test that constructs the
containment it is testing cannot fail when production lacks it.

The deeper cause is a design assumption: "the model server is present" was
treated as a precondition rather than as a dependency that can be absent. An
absent dependency threw out of the load path instead of becoming data.

## FIX / PREVENTION

- An unreachable server is a typed gap at its source: `loadModel` returns a gap
  handle (`ModelResolution.unreachable`), `ModelBackend.isAvailable` is false,
  the manager does not cache it, and generation answers in plain words.
- Engine scopes come from one containment factory (`EngineContainment`) with a
  `SupervisorJob` and a handler that records into the `FailureSurface`. A
  tripwire test fails if a raw `CoroutineScope(` appears in `FridayEngine.kt`.
- The red tests drive the REAL `OllamaModelBackend`/`ModelManager`/`AnchorEngine`
  against a port where nothing listens, and the same objects recover when the
  server appears — no rebuilt copy, no private scope.
- One down-episode produces ONE failure-surface entry, and the recovery is
  recorded when the server answers again.
