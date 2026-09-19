# voiceforge/ — committed voice-prompt asset slot (AC4)

## Intended content

`venon_voice_reference.wav` — Venon's own recorded reference clip, used by the
Chatterbox voice-cloning pipeline as the `audio_prompt_path` for JARVIS's
spoken replies.

- Format expected by the cloning front-end: 16-bit PCM mono WAV.
- Duration: ~5 seconds of the voice being cloned (one or two spoken sentences).
- Location the Kotlin backend points at:
  `assets/voiceforge/venon_voice_reference.wav` (resolved against app assets).

## Current state — COMMITTED 2026-09-15 (VOICE-FORGE-ACTIVATION-REAL-SPEECH)

`venon_voice_reference.wav` is LANDED (16-bit PCM mono, 24000 Hz). The Kotlin
production composition roots wire `VoiceForgeConfig.DEFAULT_ASSET_PATH`
(`assets/voiceforge/venon_voice_reference.wav`) into `VoiceForgeBackend`:

- `JarvisEngine.init` (JarvisEngine.kt:187-192)
- `TermuxJarvisServer` (TermuxJarvisServer.kt:247-258)

The audio_prompt_path sent to voiceforge_server.py is allow-listed against
this exact directory (AC8), so voice cloning activates only for the committed
reference clip. The checkpoints the story routes are the configured VoiceForge
checkpoint ids (`chatterbox-multilingual` base + `NAMAA-Egyptian-TTS` Egyptian
fine-tune); the cloned voice rides the `audio_prompt_path` parameter.