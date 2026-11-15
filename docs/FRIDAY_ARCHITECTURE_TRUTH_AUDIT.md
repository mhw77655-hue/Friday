# Friday architecture truth audit

**Date:** 2026-11-15  
**Scope:** Production Android branch `audit/friday-offline-runtime`

This document separates executable behavior from architecture labels and isolated tests. A class existing in the repository is not proof that the production composition uses it. A successful JVM test is not proof of device behavior.

## The intended organism model

Friday remains the primary intelligence and persistent individual system. Her Android process is the body; her local model authority and cognitive pipeline interpret and plan; native Android adapters are the actuators and sensors; durable experience and self-study update her strategies over time.

TypeSafe Jev is an optional cloud decision faculty, not a replacement brain, local model, voice engine, Android controller, or permanent session. A Jev call sends a bounded state and typed Choice / Score / Noul questions; Friday interprets the answer, applies her own policy, and executes through local Android capabilities. Network loss must not disable basic local control. Consequential actions remain subject to Friday's own confirmation and Android permissions.

## Confirmed mismatches and corrections

### 1. Production capability synthesis was narrower than its tests

**Found:** `LlmSpecSynthesizer` and `LlmSynthesisProvider` existed, with isolated tests demonstrating a generated capability outside the five deterministic examples. But `ToolBuilderComposition` constructed `CandidateGenerator(DEFAULT_SYNTHESIZERS)`, which only contained deterministic, cached, and heuristic synthesizers. Production did not pass Friday's live `ModelManager` into the LLM synthesizer.

**Changed:** Production composition now accepts the LLM synthesis provider; `JarvisEngine` supplies `LlmSynthesisProvider(modelManager)` using Friday's existing model authority. The same candidate-generator instance is shared by the evolution engine and builder. A new explicit `Behavior.Custom` contract carries a capability name, typed input port, and type so custom capabilities are not disguised as one of the five toy operations. Tool validation accepts that contract only for supported input types. A production-composition integration test builds and routes a model-synthesized `word_count` capability.

**Still unproven:** The model's output is not guaranteed to be correct because it was generated. It must pass the existing sandbox and promotion gate. The new integration test uses a deterministic fake completion; it proves production wiring and execution contract, not the quality of the resident model on the phone.

### 2. Candidate synthesis must not swallow cancellation

**Found:** `CandidateGenerator.generate` caught `Throwable`, which could convert coroutine cancellation and fatal VM errors into an ordinary missing candidate.

**Changed:** Cancellation is rethrown; ordinary synthesis exceptions remain candidate failures.

### 3. Research-first behavior was misleading

**Found:** `ToolBuilder.build` ran research whenever a research engine existed, even if `researchFirst` was false. `buildWithResearch` also made an initial research call whose result was discarded before `build` performed another research call.

**Changed:** Research runs only when explicitly requested. `buildWithResearch` now enters the same builder path with the research-first flag instead of making a discarded duplicate request.

### 4. Jev should advise the control system, not become the actuator

**Found:** Free-form tap and text actions returned for local confirmation before the optional Jev provider was called. Jev could not assess those consequential proposals at all.

**Changed:** When configured, Jev now evaluates consequential proposed actions. Even if Jev recommends execution, Friday's local confirmation invariant remains in force for free-form screen taps and text entry. If Jev is unavailable, those actions still require local confirmation. Basic allowlisted actions remain available under Friday's local policy.

**Still limited:** Jev is a one-request typed decision service, not a continuous motor-control channel. The current phone command parser recognizes a bounded vocabulary; it is not yet open-ended voice control of every Android app. Android Accessibility must be explicitly enabled by the owner, and some actions require additional Android permissions or cannot be automated.

## Existing mechanisms that must not be oversold

### Application self-study

The packaged source index contains file paths, package names, declaration names, and a source revision digest. It does not contain the Kotlin source text. This lets Friday map architecture metadata; it does not let her read arbitrary source files or rewrite her own APK.

The next coherent direction is to give Friday a first-class, versioned capability workspace: readable tool specifications, DSL source, tests, dependency declarations, permissions, resource budgets, provenance, and measured outcomes. Friday can then study, generate, execute, compare, promote, and roll back those dynamic capabilities without pretending to be able to hot-patch compiled Android code.

### Capability durability

The active `MicroSystemRegistry` is process-local. The genome archive persists genome metadata, while candidate source and tests are collected in the mutant workspace. The current archive format does not itself persist a complete executable capability package and there is no verified startup rehydration path for promoted tools. Therefore a successful promotion must not yet be described as a capability proven to survive process death.

This is a high-priority continuity gap: persist the executable DSL source, test suite, capability contract, dependency/permission manifest, quality evidence, and provenance as one atomic artifact; validate it on startup; rehydrate only after compatibility and integrity checks; retain the prior version for rollback.

### Mutant habitat

The production builder currently uses a process-local backend. Resource/time guards and candidate isolation are useful, but this must not be described as an OS-level security sandbox without proof of process, filesystem, permission, and network isolation. Generated capabilities must remain pure and restricted by the DSL; they must not receive arbitrary Android context or unrestricted reflection.

### Nano Liquid Symbiosis

Host observations and per-organ strategies are useful evidence, not proof of autonomous hardware reconfiguration. Any adaptation must be keyed to the actual device/runtime/model/task combination, preserve a protected memory reserve, and be accepted only when quality and stability remain within validated limits. No claim of a Snapdragon 695 NPU delegate should be made without a real supported runtime and device probe.

## Required architecture direction

1. **Persistent identity and experience:** one durable identity/experience store; model or tool replacement must not reset the individual.
2. **Local cognition and control:** local intent understanding, planning, tool selection, execution, observation, and interruption. Keep basic motor actions local and low-latency.
3. **Jev decision faculty:** a small, explicit API adapter for bounded typed decisions. Use it when it materially improves ambiguous, multi-step, or consequential decisions; do not send every simple motor command to the cloud.
4. **Native actuator fabric:** Android Accessibility, intents, media sessions, notifications, and supported platform APIs behind explicit capability and permission contracts.
5. **Capability workspace:** versioned DSL tools with source, tests, permissions, provenance, resource envelope, telemetry, and rollback.
6. **Self-study loop:** observe the actual installed host and Friday's own capability workspace; identify gaps from failures and goals; generate alternatives; test against fixed and learned regression suites; promote only evidence-backed improvements.
7. **Continuity:** rehydrate verified promoted capabilities after process death and preserve prior versions.
8. **Life tests:** prove that a learned change alters a later action, survives restart, can be disabled to restore baseline behavior, and does not depend on replaying the original prompt.

## Acceptance language

Use these distinctions in progress reports:

- **Implemented:** code is present and wired into the production Android composition.
- **Unit-tested:** isolated contract tests pass.
- **Integration-tested:** the production composition is exercised with controlled dependencies.
- **Device-verified:** behavior was observed on the target Realme 9 Pro 5G.
- **Released:** the final signed package passed the full release gate.

Do not upgrade one status to another without the corresponding evidence.
