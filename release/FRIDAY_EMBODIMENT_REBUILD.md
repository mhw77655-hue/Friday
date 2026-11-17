# Friday embodiment and field-diagnostics rebuild

## Why this work exists

The 26 screenshots supplied from the Realme RMX3472 / Android 14 test show a build that exposes internal scaffolding as if it were a finished companion. The user has not selected or installed a local language model. The model-free Device Lab is a screening tool, not an inference backend. The UI must never imply a model is linked, loaded, or answering when that is not true.

This rebuild is not a chatbot skin. Friday's Android app is the body and operating surface for a persistent agent. The conversation view is one interaction channel, not the product's entire identity.

## Implemented in this repair branch

- Home and conversation user-facing labels use Friday rather than JARVIS.
- Removed the unsupported "Synced to Obsidian vault" status claim from the conversation header. Obsidian logging may exist as an optional integration, but a successful sync must be reported only when it is actually connected and confirmed.
- Added a one-tap Android document export from System Diagnostics.
- The JSON report captures timestamp, app/build identity, Android/device/ABI, RAM and storage headroom, battery/network, microphone/STT/hotword/TTS, voice process health, screen-context bridge state, model-runtime status, memory subsystem state, failure-surface summary and telemetry summary.
- The report explicitly excludes conversation contents, speech transcripts, notification contents, screen contents, credentials and tokens.

## Required next implementation gates

### A. Honest model lifecycle
- Distinguish catalog discovery, model selected, download started, download verified, model loaded, generation active, and generation failed.
- Show "No model selected/installed" as a first-class state when the user has not supplied one.
- The Device Lab must screen the actual model catalog entries, including the 0.8B fallback, without implying that a candidate is installed or proven capable.
- Never enter a permanent THINKING state when model admission or generation has failed. Surface the exact actionable failure and export it in the report.
- Keep cloud inference an explicit separate route; do not silently substitute a cloud service for a local model.

### B. Friday's visual embodiment
- Replace the placeholder orb as the only visible identity with a persistent, expressive avatar surface.
- Avatar state must be driven by real runtime events: idle/present, listening, attending/thinking, speaking, interrupted, unavailable and sleeping.
- Mouth movement must be synchronized to actual generated audio/visemes or measured audio envelope; do not fake lip sync while silent.
- Provide a coherent idle animation, eye/gaze behaviour, expression transitions, and accessible reduced-motion mode.
- Keep the renderer replaceable: start with a low-cost 2D/2.5D Android renderer that fits the Realme's memory budget; define a renderer contract so a 3D/native avatar backend can be added later without changing cognition, memory, voice or turn ownership.
- No cloud avatar dependency by default. No camera capture or face identity inference without explicit opt-in.

### C. Voice as an identity, not a speaker toggle
- Separate voice identity/profile from the current TTS provider.
- Report actual provider readiness and audible playback success; "BOUND" is process state, not proof speech was heard.
- Build sentence-streamed playback, interruption/barge-in, voice activity state, and avatar synchronization.
- Piper Amy is only the current generic TTS output, not Friday's final voice identity. Voice cloning remains disabled until a complete Android inference/preprocessing pipeline is implemented and evaluated.

### D. Full diagnostic reporter
- Extend the exported report from a point-in-time status snapshot into a support bundle with bounded, timestamped subsystem events, turn lifecycle timings, model admission decisions, download integrity results, voice playback outcomes, process restarts, crash/failure aggregates, and capability truth states.
- Add report schema version, app commit/build identifier, redaction manifest, export timestamp and report completeness indicator.
- Keep private content out by default. Offer an explicit separate opt-in attachment for user-selected logs only.
- Add deterministic tests for redaction, required sections, bounded event retention and export validity.

### E. Product surface rebuild
- Home becomes Friday's living presence surface, not a capability-count dashboard.
- Primary actions are natural interaction, presence/voice controls, active work and clear current state.
- Move demo buttons, synthetic sensor samples and engineering-only panels behind Developer Diagnostics.
- Replace empty placeholder pages with honest, useful states and clear routes to the relevant action.
- Capability labels must reflect verified operational state, not class registration or static feature flags.

## Acceptance gates

1. No visible JARVIS or false Obsidian-sync identity/status remains in normal user-facing screens.
2. With no local model installed, the app explicitly says so and does not remain indefinitely in a thinking state.
3. With a compatible model selected and loaded, one-turn and multi-turn inference complete; failures are visible and included in export.
4. The avatar visibly changes state on real listening/thinking/speaking events; speaking animation is synchronized to actual audio.
5. TTS output can be previewed, interrupted, and its playback result is measured; voice profile persists independently of provider.
6. Exported report opens as valid JSON, contains all required technical sections, and passes privacy-redaction tests.
7. Realme RMX3472 acceptance measures memory, heat, latency, audio continuity, process recovery and battery cost.
8. CI is green and the exact candidate APK is tested on the target phone before release.

## Device constraints from supplied screenshots

The screenshots report a Realme RMX3472, Snapdragon 695 / SM6375, Android API 34, 8 CPU cores, 7.4 GB total RAM, about 1.97 GB available at capture, 384 MB app memory class and thermal status 3 while charging. These are point-in-time observations, not fixed device specifications. The currently shown model estimates are not runtime benchmarks.

## Current scope honesty

This branch implements the identity correction and first exportable device diagnostic snapshot. It does not yet claim the expressive avatar, final voice identity, full support-bundle event history, connected local model, or successful model generation. Those remain explicit engineering and device-acceptance gates rather than being mislabeled complete.
