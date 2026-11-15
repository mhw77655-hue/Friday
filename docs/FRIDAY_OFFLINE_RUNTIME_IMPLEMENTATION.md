# Friday offline runtime implementation — current engineering state

This branch starts the transition from a server-dependent model path to an app-contained offline runtime.

## Implemented in this slice

- Offline model catalog:
  - LiquidAI LFM2.5 1.2B Instruct GGUF, Q4_K_M, resident-model candidate.
  - ggml-org Qwen3 1.7B GGUF, Q4_K_M, reasoning-model candidate.
- Resumable downloads into Android app-private filesDir/models.
- GGUF magic validation, free-space preflight, partial download resume, fsync and atomic finalization.
- Model Manager UI exposes the two downloads.
- ModelPathResolver prioritizes app-private storage.
- CMake fetches a pinned llama.cpp revision rather than relying on a manually provisioned shared-storage checkout.

## Not yet proven / next work

- Compile compatibility of the existing JNI bridge against the pinned llama.cpp revision.
- Wire LocalInferenceEngine to the production ModelBackend and active provider path.
- Replace all remaining Ollama localhost assumptions in the Android runtime path.
- Validate prompt templates and streaming with both model families.
- Add model metadata/license/checksum provenance and robust cancellation.
- Run native inference on a physical arm64 Android device and profile sustained thermal behaviour.
- Complete integrated voice, visual perception, expressive presence, and NAE host adaptation.

Downloading weights is not proof of inference. The branch must not be presented as a living Friday build until these next gates pass.
