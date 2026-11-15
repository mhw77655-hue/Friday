# voiceforge/ — committed voice-prompt asset slot (AC4)

## Intended content

`venon_voice_reference.wav` — Venon's own recorded reference clip, used by the
Chatterbox voice-cloning pipeline as the `audio_prompt_path` for JARVIS's
spoken replies.

- Format expected by the cloning front-end: 16-bit PCM mono WAV.
- Duration: ~5 seconds of the voice being cloned (one or two spoken sentences).
- Location the Kotlin backend points at:
  `assets/voiceforge/venon_voice_reference.wav` (resolved against app assets).

## Current state — legacy experiment, not production

This directory and the VoiceForge/Chatterbox adapter are retained as a historical
experiment and test harness only. They are not part of Friday's Android runtime.
The adapter and backend live under `src/test`, and the standalone JVM server is
also test-only. The production app speaks through its in-app `:voice` service,
`VoiceOrganismHost`, `VoiceOutputPath`, and the Sherpa-ONNX providers
(Piper-first for English, Kokoro as an optional higher-quality voice).

No Termux installation, Python server, localhost TTS endpoint, or VoiceForge
checkpoint is required for Friday to speak. Do not use this document as evidence
that the old Chatterbox path is wired into the app.
