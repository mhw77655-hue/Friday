# INCIDENT: TTS file synthesis must finish before cache or playback

## WHAT

`TextToSpeech.synthesizeToFile()` is asynchronous. A synchronous `SUCCESS` return means the request was accepted, not that the destination WAV is complete. The old streaming path immediately registered that destination in the cache and called `MediaPlayer.setDataSource()`. A partial output could therefore be treated as a cache hit or be opened for playback before Android finished writing it.

The preload path also registered its output immediately after queueing synthesis. Two first requests for the same phrase could initiate duplicate synthesis.

## REQUIRED INVARIANT

A file becomes a usable cache entry only after both:
1. `synthesizeToFile()` returned `TextToSpeech.SUCCESS`; and
2. `UtteranceProgressListener.onDone(utteranceId)` confirmed that Android finished writing the file.

The file must then pass the local validity check before atomic promotion. Playback may only use a promoted file with a completion marker. `onError`, `onStop`, or a synchronous exception discards the temporary output and reports through `FailureSurface`. Concurrent first requests for the same key join one in-flight synthesis.

## IMPLEMENTATION IN THIS BRANCH

- Added `SynthesisCompletionGate` to join the synchronous request result with the asynchronous completion callback, regardless of callback ordering.
- Added `CompletedAudioCache`: synthesis uses a unique temporary file; only an `onDone`-confirmed file of sufficient size is promoted to the cache and receives a completion marker.
- Updated `StreamingTts` to route streaming and preload through the same completion path, handle `onError` and `onStop`, coalesce concurrent first requests, and serialize callback-driven promotion with cache admission.
- Added pure Kotlin tests for queued-vs-completed state, both callback orderings, preload, error, stop, duplicate first requests, completion markers and partial-file rejection.

## STATUS

Implementation committed on `audit/vision-alignment-first-deliverable`. CI and physical-device verification are pending. This note does not claim the defect is fixed on the phone until a green build is installed and its TTS log/audio behaviour is checked there.

## PHONE EXAM

After a green APK is installed:
- cold-start the app and trigger multiple short spoken replies;
- confirm no playback occurs before a complete output is available;
- confirm concurrent/preload use does not register partial files;
- inspect logs for zero `Cached playback failed` events and no stale `STREAMING_TTS` errors attributable to this race;
- force-stop and reopen, then repeat to check stale partial files are never used as cache hits.
