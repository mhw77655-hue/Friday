# English on-device model shortlist (research snapshot: 2026-11-15)

## Decision

Use a two-tier local portfolio, not one large always-resident model:

1. **Low-memory resident candidate:** `AkaumiKaede/Qwen3.5-2B-Uncensored-Zero-Refusal-GGUF`, Q4_K_M, reported 1.27 GB. The Hugging Face card identifies Apache-2.0 and English support. Its author reports 0 refusals across 408 prompts and KL divergence 0.2096 versus the base model. These are author-reported results, not independent benchmark results. Treat this as a candidate to benchmark against the base model, especially for reasoning, coding, instruction following, and hallucination rate.
2. **Higher-quality local candidate:** `TrevorJS/gemma-4-E2B-it-uncensored-GGUF`, Q4_K_M, reported 3.43 GB. The GGUF page identifies Apache-2.0. The official Gemma 4 E2B family has 2.3B effective parameters (5.1B including embeddings), 128K advertised context, and native text/image/audio support in the base family. Do not assume every community GGUF retains all modalities; verify its exact conversion and runtime support.

## Why this is the right phone-first approach

- Qwen3.5-2B Q4 is roughly 1.27 GB of weights, a much more realistic always-available option than 3-8B weights. Runtime memory is higher than file size because of KV cache, compute buffers, tokenizer/runtime, and the rest of Android.
- Gemma 4 E2B Q4 is roughly 3.43 GB of weights. It is an optional quality tier, not the default for an unknown phone.
- The phone should never keep a model resident merely because it can. Friday should measure available memory, thermal state, battery, storage, latency, and foreground use, then load/unload and choose context/thread budgets accordingly.
- The model's refusal rate is not its intelligence. Abliteration can change refusal behaviour and can also damage other capabilities. Benchmark the actual task suite before promoting a derivative.

## Runtime compatibility gate

Both candidates are GGUF and have llama.cpp usage documented on their model pages. The current project native pin must still be built and tested against both exact files; a model page saying “llama.cpp supported” does not prove the project's JNI wrapper, chat template, tokenizer, or Android ABI supports it. Never download a model into the app catalogue until the native smoke test succeeds.

## Promotion test suite

Compare each candidate with its unmodified base model using the same phone, context, sampling, and prompts:

- English conversation and instruction adherence
- Multi-step reasoning and calibrated uncertainty
- Kotlin/Android coding and tool-call JSON validity
- Memory/continuity recall when the correct evidence is supplied
- Refusal rate on benign difficult topics (lower is not automatically better)
- Harmful-request handling, factuality, hallucination and prompt-injection resistance
- Cold-load time, tokens/sec, peak RSS, thermal rise, battery per 1K generated tokens
- Quality after 10-20 turn sessions and context compaction

Do not accept the model based on its creator's “zero refusal” claim alone.

## Nano-liquid symbiosis: intended loop

1. Sense only public Android signals: available RAM, app process memory where available, storage, battery/charging, power-save, thermal status, foreground state, and network availability.
2. Choose a reversible operating envelope: model, context length, CPU threads, generation budget, voice/perception concurrency, and whether work should defer.
3. Observe outcomes: load failures, latency, tokens/sec, memory pressure, thermal events, battery use, task success, user corrections, and task abandonment.
4. Update a bounded local profile using rolling statistics / exponential moving averages. Do not rewrite model weights or silently alter user data.
5. Run background maintenance only during safe idle windows, under Android background-execution limits. It must yield immediately to calls, battery saver, thermal pressure, or memory pressure.
6. Explain why it changed operating mode and keep a user-visible reset/disable control.

The first controller implementation is in `LiquidSymbiosisController.kt`. It is a policy substrate, not yet wired into the model loader, scheduler, or persisted profile store. The next work is integration plus tests on a real phone.

## Hard truth / remaining blockers

- The exact phone RAM, chipset, Android version, and available storage are not known, so a final default model cannot be honestly chosen yet.
- The model catalog must register Qwen3.5-2B as the low-memory option and Gemma 4 E2B as the higher-memory option only after exact-file native smoke tests.
- `SymbiosisProfileStore` needs a real DataStore/Room implementation.
- `AndroidHostProbe` must be connected to lifecycle/process state and verified on target Android versions.
- The controller must be injected into inference load/unload, thread/context selection, voice/perception scheduling, and WorkManager/foreground service policy.
- Need benchmark telemetry and a visible “why Friday adapted” history, plus reset/disable controls.
- Background adaptation is operational optimization, not autonomous model retraining. Actual weight training on a phone would be expensive, battery-heavy, thermally hostile, and inappropriate as the first learning mechanism.

## Sources

- Official Gemma 4 model card: https://ai.google.dev/gemma/docs/core/model_card_4
- Gemma 4 E2B uncensored GGUF: https://huggingface.co/TrevorJS/gemma-4-E2B-it-uncensored-GGUF
- Qwen3.5-2B uncensored GGUF: https://huggingface.co/AkaumiKaede/Qwen3.5-2B-Uncensored-Zero-Refusal-GGUF
- llama.cpp Android guide: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
