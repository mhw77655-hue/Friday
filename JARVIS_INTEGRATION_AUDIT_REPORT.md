# JARVIS Integration Audit Report

**Date**: Sat Aug 22 2026  
**Type**: Read-Only Integration Audit  

---

## Executive Summary

This read-only audit inspects the model management, runtime routing, message pathways, context and memory wiring, cognitive engine status, HumanCore model consumption, capability/tool execution, and the test suite within the repository.

---

## Findings

### (1) Canonical Model Authority Right Now

**`com.jarvis.app.model.ModelManager`** (`mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt`) is the canonical model authority.

**Code Evidence**:
- `mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:25-26`:
  ```kotlin
  * ModelManager — orchestrates model providers and delegates to the active adapter.
  * Provider-agnostic: HumanCore, LatencyLayer, and UI only talk to ModelManager.
  ```
- `mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:233-236`:
  ```kotlin
  // Conversation authority surface. ModelManager is the SOLE model
  // authority for the whole organism: the latency layer, the Human Core
  // model port, the orb binding and the warmup engine all read these
  // flows instead of the retired JarvisBrainBridge.
  ```
- `mobile/app/src/main/java/com/jarvis/app/humancore/mod/ModelPort.kt:21-22`:
  ```kotlin
  // Implementations: the app wires the canonical model authority
  // ([ModelBackedModelPort] → [ModelBackend] → ModelManager) with [HeuristicModelPort] as its fallback
  ```

---

### (2) Runtime Serving LFM/Qwen Models

The model runtime is **`llama-server` (llama.cpp binary)** running on host loopback at **`http://127.0.0.1:8080`**. It is managed via external Python/bash scripts.

**Code Evidence**:
- `runtime/models/model_registry.json:4-44`: Registers `lfm2.5-1.2b-instruct` (`LFM2.5-1.2B-Instruct-Q4_K_M.gguf`), `qwen3-1.7b-q4` (`Qwen3-1.7B-Q4_K_M.gguf`), and `qwen3-1.7b-q8` (`Qwen3-1.7B-Q8_0.gguf`).
- `runtime/model_gateway/model_controller.py:10`:
  ```python
  LLAMA = "/root/llama.cpp/build/bin/llama-server"
  ```
- `runtime/model_gateway/model_controller.py:77-85`:
  ```python
  command = [
      LLAMA,
      "-m", model["path"],
      "--host", "127.0.0.1",
      "--port", str(model["port"]),
      "-c", "4096",
      "-ngl", "0",
      "-np", "1",
  ]
  ```
- `scripts/jarvis-model-manager.sh:5`:
  ```bash
  LLAMA="/root/llama.cpp/build/bin/llama-server"
  ```
- `mobile/app/src/main/java/com/jarvis/app/model/adapters/LlamaCppAdapter.kt:36`:
  ```kotlin
  private var baseUrl = "http://127.0.0.1:8080"
  ```
*Note on Native Execution*: Native Android GGUF inference (`LocalInferenceEngine.kt:46` loading `jarvis_llama_jni`) is unbuilt/unvendored in native libs; the Android app relies on HTTP calls via `LlamaCppAdapter` to the host `127.0.0.1:8080`.

---

### (3) End-to-End User Message Flow

1. **Entry Point**:
   - **UI Path**: `ConversationViewModel.send(text)` (`mobile/app/src/main/java/com/jarvis/app/ui/viewmodel/ConversationViewModel.kt:31`) calls `LatencyLayer.onUserInput(text)` (`mobile/app/src/main/java/com/jarvis/app/latency/LatencyLayer.kt:109`).
   - **Voice Path**: Mic input captured by `JarvisMic` / `JarvisVosk` / `SherpaBridge` / `PlatformRecognizerStt` -> `BodyCoordinator` (`mobile/app/src/main/java/com/jarvis/app/body/BodyCoordinator.kt:195-207`) -> `BodyEvent.UserInput(text)` -> `BodyCoordinator.generateResponse(text)` (`BodyCoordinator.kt:435`).
2. **Fast-Path & Perception Pass**:
   - `LatencyLayer` triggers fast ACK speech / orb state (`LatencyLayer.kt:111-113`).
   - `LatencyLayer` or `BodyCoordinator` invokes `HumanCore.beginExchange(text, sessionContext)` (`LatencyLayer.kt:180`, `BodyCoordinator.kt:451`).
3. **Model Dispatch**:
   - `LatencyLayer` invokes `modelManager.send(text)` (`LatencyLayer.kt:159`).
   - `BodyCoordinator` invokes `modelManager.sendWithContext(text, memoryItems)` (`BodyCoordinator.kt:454`).
4. **Adapter Routing**:
   - `ModelManager.generate()` delegates to `_activeProvider.value` (`mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:198`).
5. **HTTP Request**:
   - `LlamaCppAdapter.generate()` executes POST to `http://127.0.0.1:8080/v1/chat/completions` (`mobile/app/src/main/java/com/jarvis/app/model/adapters/LlamaCppAdapter.kt:287-293`).
6. **Reply Processing & Expression Pass**:
   - `ModelManager.lastReply` emits response -> `LatencyLayer.onReplyReady(reply)` (`LatencyLayer.kt:352`).
   - `HumanCore.express(reply, context)` verifies safety/styling (`LatencyLayer.kt:360`, `BodyCoordinator.kt:457`).
   - `HumanCore.completeExchange(...)` logs exchange and updates internal state (`LatencyLayer.kt:370`, `BodyCoordinator.kt:461`).
   - Response is appended to UI turns (`Turn`) and spoken via `SpeechEngine` / `StreamingTts` / `VoiceOutputPath`.

---

### (4) Model Selection & Determinism

- **Where Selection Happens**:
  - In Android: `LiquidEnvironmentManager.activate(profile)` (`mobile/app/src/main/java/com/jarvis/app/env/LiquidEnvironmentManager.kt:166`) calls `modelManager.switchProvider(profile.provider, profile.modelSource)` and `runtimeBinder.attachLocal(profile.modelSource)` (`LiquidEnvironmentManager.kt:174`).
  - On host: `runtime/model_gateway/model_controller.py:69-112` or `scripts/jarvis-model-manager.sh:57-81` selects the active GGUF model (`lfm`, `qwen`, or `qwen-q8`).
- **Is it Deterministic?**: **YES**. `EnvironmentRepository` persists the active profile ID (defaulting to `"local-on-device"`, `LiquidEnvironmentManager.kt:62`). Provider endpoints are hardcoded loopback addresses (e.g. `127.0.0.1:8080`).

---

### (5) Context Construction Wiring

- **Status**: **PARTIALLY WIRED**.
- **Code Evidence**:
  - In `BodyCoordinator.kt:419-454`, retrieved memory items are formatted into the prompt in `ModelManager.sendWithContext(message, memoryItems)` (`mobile/app/src/main/java/com/jarvis/app/model/ModelManager.kt:306-311`):
    ```kotlin
    val fullMessage = message + "\n\n[Context from memory]:\n" + memoryItems.map { "- ${it.content}" }.joinToString("\n")
    ```
  - In `LatencyLayer.kt:158-160`, `modelManager.send(text)` sends raw user text without memory context.
  - Subsystems such as `AdaptiveContextBuilder.kt` (`mobile/app/src/main/java/com/jarvis/app/body/AdaptiveContextBuilder.kt`) and `CognitiveContextBuilder.kt` (`mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveContextBuilder.kt`) are NOT wired into `BodyCoordinator` or `LatencyLayer`.

---

### (6) Memory Wiring

- **Status**: **PARTIALLY WIRED**.
- **Code Evidence**:
  - **Persistence**: Wired. `JarvisEngine.init` connects `HumanCore` memory annotations to `MemoryStore.storeConversation` (`mobile/app/src/main/java/com/jarvis/app/JarvisEngine.kt:149-153`). `BodyCoordinator.completeExchangeWithMemory` also stores conversations (`BodyCoordinator.kt:595-599`).
  - **Retrieval in Voice Path**: Wired in `BodyCoordinator.kt:419-428` (`triggerMemoryRetrieval` calls `memoryStore.retrieve`).
  - **Retrieval in UI Path**: NOT wired in `LatencyLayer.kt` (UI text messages bypass memory retrieval).
  - **Cognitive Memory Subsystem**: Unwired. Cognitive memory modules (`ContinuityManager`, `MemoryConsolidator`, `MemoryConflictResolver`, `MemoryUpdateEngine` in `mobile/app/src/main/java/com/jarvis/app/cognitive/memory/`) are standalone test targets and are NOT instantiated or wired in `JarvisEngine.kt`.

---

### (7) CognitiveEngine / Gateway Status

- **Status**: **PRESENT IN CODE ONLY / NOT LIVE**.
- **Code Evidence**:
  - `CognitiveEngine` (`mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:50`) is NEVER instantiated or invoked in `JarvisEngine.kt`, `MainActivity`, `BodyCoordinator.kt`, or `LatencyLayer.kt`.
  - `MILESTONE_REPORT_cognitive_substrate.md:42`:
    ```markdown
    - **Runtime wiring** — `CognitiveEngine` is not yet connected into `JarvisEngine`/`MainActivity`/the nervous-system event flow. Deliberately not wired per the build directive.
    ```
  - `runtime/model_gateway/gateway.py` is a standalone Python helper script and is not running inside the Android process.
  - The "Consciousness Gateway (:8140)" documented in `vault/agent-logs/CONSCIOUSNESS_GATEWAY_MILESTONE1_REPORT.md` references a Python body (`~/jarvis` in Termux) outside this repository (`vault/agent-logs/FULL_SYSTEM_REPORT_2026-08-08.md:13`).

---

### (8) HumanCore Model Output Consumption

- **Status**: **NO (Not as primary answer generator); YES (As expression/safety filter and off-path internal reflection consumer)**.
- **Code Evidence**:
  - `HumanCore` does NOT generate user responses. Responses are generated directly by `ModelManager` (via `LlamaCppAdapter`, etc.).
  - `HumanCore.express(reasoningReply, context)` (`mobile/app/src/main/java/com/jarvis/app/humancore/HumanCore.kt:136`) receives the pre-generated string from `ModelManager` to run safety checks (`ConsistencyGuard`), styling (`ConversationStyleController`), and emotional alignment.
  - `HumanCore`'s `InternalDialogueEngine` (`HumanCore.kt:314`) uses `ModelPort` (`ModelBackedModelPort` -> `ModelBackend.requestChat` -> `ModelManager.requestChat`) off the critical path for internal reflections (`InternalDialogueEngine.kt:52`). If `requestChat` is null or fails, it falls back to `HeuristicModelPort` (`HumanCore.kt:308`).

---

### (9) Tool / Capability Calls Executability

- **Status**: **NOT EXECUTABLE END TO END**.
- **Code Evidence**:
  - `CapabilityRegistry`, `CapabilityFabric`, `CapabilityInvoker`, `CapabilityExecutor`, and `CapabilityActionPort` (`mobile/app/src/main/java/com/jarvis/app/cognitive/capability/`) are NOT initialized or referenced in `JarvisEngine.kt`, `LatencyLayer.kt`, `BodyCoordinator.kt`, or `ModelManager.kt`.
  - `LlamaCppAdapter.kt:191`:
    ```kotlin
    supportsTools = false, // llama.cpp supports tools via grammar
    ```
  - `ModelManager` has no logic to parse structured tool call requests from LLM outputs and dispatch them to execution targets.
  - `TaskExecutor.kt:45`: `var executeCapability: ... = null` remains unassigned in production.

---

### (10) Single Missing Integration Seam Blocking Full Loop

**The Seam**: **Wiring `CognitiveEngine` / `CapabilityFabric` into `JarvisEngine` and the turn dispatch loop (`LatencyLayer` / `BodyCoordinator`)**.

**Explanation**:  
Currently, user input passes directly from `LatencyLayer`/`BodyCoordinator` to `ModelManager.send()` (raw text LLM completion), completely bypassing the cognitive substrate (`CognitiveEngine`, `GoalPlanner`, `DecisionEngine`, `CapabilityInvoker`/`CapabilityFabric`, `WorkingMemory`). Because LLM outputs are directly treated as conversational text strings and handed straight to `HumanCore.express()`, there is no cognitive decision gate to intercept tool requests, execute capabilities, plan multi-step goals, or close the autonomous feedback loop.

---

## Test Suite Overview

- **Unit Tests**:
  - `mobile/app/src/test/java/com/jarvis/app/cognitive/` (Tests `CognitiveEngineTest`, `CapabilityExecutorTest`, `ExecutionEngineTest`, `GoalPlannerTest`, `MemoryUpdateEngineTest` in isolation).
  - `mobile/app/src/test/java/com/jarvis/app/humancore/` (Tests `PipelineIntegrityTest`, `ConsistencyGuardTest`, `StorePersistenceTest`).
  - `mobile/app/src/test/java/com/jarvis/app/latency/` (Tests `LatencyPipelineTest`, `WarmupEngineTest`).
  - `mobile/app/src/test/java/com/jarvis/app/model/ModelManagerTest.kt` (Tests `ModelManager` provider switching).
  - `tests/test_building_system.py`, `tests/test_phase1_exit.py` (Python infrastructure tests).
- **Instrumentation Tests**:
  - `mobile/app/src/androidTest/java/com/jarvis/app/voice/VoiceSubstrateTest.kt`.
