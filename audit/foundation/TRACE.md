# TRACE: System Screen Failure to Capability Gap Disconnect

This document traces the real failure observed in the application:
The **System Screen** shows `STREAMING_TTS ERROR x4`, and logcat records `TextToSpeech "synthesizeToFile failed: TTS engine connection not fully set up"`.
We follow this failure through `StreamingTts`, `FailureSurface`, and onward to `CapabilityGapDetector`, `ToolBuilder`, and `DecisionEngine.missingCapabilities`.

---

## 1. Trace from Logcat to System Screen

### Point 1: Trigger in StreamingTts
In `mobile/app/src/main/java/com/jarvis/app/body/StreamingTts.kt:338`, speech synthesis is performed:
```kotlin
val result = engine?.synthesizeToFile(ssml, null, cacheFile, id)
```
When the Android system TTS engine connection is not ready or fails, Android's `TextToSpeech` logs `synthesizeToFile failed: TTS engine connection not fully set up` and returns `TextToSpeech.ERROR` (`-1`).

Because `result != TextToSpeech.SUCCESS`, `StreamingTts.kt:346-364` falls back to `engine?.speak(...)`:
```kotlin
val speakResult = engine?.speak(ssml, TextToSpeech.QUEUE_ADD, null, id)
if (result == null || speakResult != TextToSpeech.SUCCESS) {
    onFailure(
        com.jarvis.app.failure.FailureReport(
            subsystem = "STREAMING_TTS",
            operation = "synthesize",
            severity = com.jarvis.app.failure.FailureSeverity.ERROR,
            category = com.jarvis.app.failure.FailureCategory.TTS,
            message = "Streaming TTS synthesis + speak fallback failed",
            source = "StreamingTts",
            cause = "synthesize=$result speak=$speakResult",
            dependency = language.name,
            recoverability = com.jarvis.app.failure.Recoverability.RECOVERABLE,
            recoveryAction = com.jarvis.app.failure.RecoveryAction.REINITIALIZE
        )
    )
}
```

### Point 2: Routing to FailureSurface
In `mobile/app/src/main/java/com/jarvis/app/JarvisEngine.kt:300`, `StreamingTts` is constructed with an `onFailure` lambda wired directly to `FailureSurface`:
```kotlin
val streamingTts = StreamingTts(appContext, languageRouter, scope, onFailure = { r -> failureSurface.report(r) })
```
In `mobile/app/src/main/java/com/jarvis/app/failure/FailureSurface.kt:151-197`:
* `report(report: FailureReport)` receives the report.
* It deduplicates by `report.dedupeKey` into a `FailureAggregate`.
* The 4 consecutive segment failures increment `occurrenceCount` to `4`, and record `currentSeverity = ERROR`.
* `_currentFailures.value` is updated to include the active aggregate.

### Point 3: Propagation to DiagnosticsViewModel & SystemScreen
In `mobile/app/src/main/java/com/jarvis/app/DiagnosticsViewModel.kt:276-286`:
* A coroutine observes `surface.worstSeverity`, `surface.currentFailures`, and `surface.subsystemHealth`.
* It calls `summarizeFailures`:
  ```kotlin
  val active = failures
      .filter { it.isActive }
      .take(3)
      .joinToString("; ") { "${it.subsystem}: ${it.currentSeverity.name} x${it.occurrenceCount}" }
  return "Failures: $worst — $active"
  ```
* For 4 errors on `STREAMING_TTS`, this produces:
  `Failures: ERROR — STREAMING_TTS: ERROR x4`
* In `mobile/app/src/main/java/com/jarvis/app/ui/screens/SystemScreen.kt:73-78`:
  ```kotlin
  SystemRow(
      "Failures",
      failureText.removePrefix("Failures: "),
      failureDotColor(failureText),
      failureText
  )
  ```
* The user sees `STREAMING_TTS: ERROR x4` rendered directly on the System Screen.

---

## 2. Existence of the Autonomous Construction Organisms

We confirm that all three components named in the architectural path exist in the codebase:

1. **`CapabilityGapDetector`**:
   * Declared at `mobile/app/src/main/java/com/jarvis/app/builder/CapabilityGapDetector.kt:19`.
   * Accepts `MicroSystemRegistry`, `GenomeRegistry`, and `CapabilityRouter`.
   * Evaluates capabilities via `detect(capability: String): GapAnalysis`.
2. **`ToolBuilder`**:
   * Declared at `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilder.kt:33`.
   * Composed in `mobile/app/src/main/java/com/jarvis/app/builder/ToolBuilderComposition.kt:110-123`.
   * Holds `gapDetector`, `EvolutionEngine`, and references to `FailureSurface`.
3. **`DecisionEngine.missingCapabilities`**:
   * Declared at `mobile/app/src/main/java/com/jarvis/app/cognitive/planning/DecisionEngine.kt:221-224`:
     ```kotlin
     private fun missingCapabilities(option: DecisionOption, capabilityState: CapabilityState): List<String> {
         val available = capabilitiesAvailable(capabilityState)
         return option.capabilityRequirements.filterNot { it in available }
     }
     ```

---

## 3. Disconnect: The First Point Where the Chain Stops

Although `FailureSurface` records the failure and computes `_degradedCapabilities` in `FailureSurface.kt:325`:
```kotlin
val degraded = active
    .filter { it.currentSeverity.rank >= FailureSeverity.DEGRADED.rank }
    .map { subsystemToCapability[it.subsystem] ?: it.subsystem }
    .toSet()
_degradedCapabilities.value = degraded
```
and `CognitiveEngine` provides a method to receive updated capability state in `mobile/app/src/main/java/com/jarvis/app/cognitive/CognitiveEngine.kt:1513-1516`:
```kotlin
fun updateCapabilityState(capabilityState: CapabilityState) {
    val state = _cognitiveState.value
    _cognitiveState.value = state.copyWith(capabilityState = capabilityState)
}
```

### The Exact Stopping Point
The exact point where the chain stops is at **`mobile/app/src/main/java/com/jarvis/app/JarvisEngine.kt:446-455`**:

> **Exact Quote:**
> ```kotlin
>             // The ToolBuilder is the UniversalResearchEngine's only designed
>             // consumer. It is constructed through ToolBuilderComposition — the
>             // single production construction point for the gap→design→build→test
>             // →promote→register→rollback stack — with the real engine handed to
>             // its research seam. Dormant until a capability gap invokes
>             // requestBuild(); never writes production code, everything inside the
>             // mutant habitat (§15, §22).
>             val builderStack = com.jarvis.app.builder.ToolBuilderComposition.build(
>                 research = universalResearchEngine,
>                 failureSurface = failureSurface,
>                 fileStorage = fileStorage,
>                 scope = scope,
>                 workspacesDir = java.io.File(appContext.filesDir, "builder-workspaces")
>             )
> ```
and **`mobile/app/src/main/java/com/jarvis/app/JarvisEngine.kt:846-853`**:
> **Exact Quote:**
> ```kotlin
>     suspend fun requestBuild(
>         spec: com.jarvis.app.builder.ToolSpecification
>     ): com.jarvis.app.builder.BuildResult {
>         val builder = checkNotNull(toolBuilder) {
>             "JarvisEngine.init() must run first"
>         }
>         return builder.build(spec)
>     }
> ```

### Why It Stops Here
1. **Zero Callers to `requestBuild`:** Across the entire codebase, only unit test `ToolBuilderResearchWiringTest.kt:64` invokes `JarvisEngine.requestBuild`. No runtime engine, recovery controller, or cognitive loop ever invokes it.
2. **Zero Forwarding from `FailureSurface` to `CognitiveEngine`:** No observer connects `failureSurface.degradedCapabilities` to `CognitiveEngine.updateCapabilityState`. Across the repository, `updateCapabilityState` has zero callers (`grep -rn "updateCapabilityState"` matches only its declaration in `CognitiveEngine.kt:1513`).
3. **`DecisionEngine.missingCapabilities` is Disconnected from Real Execution:** `DecisionEngine.decide` is only called by `CognitiveEngine.evaluateDecision` (`CognitiveEngine.kt:1431`), which in turn is only called from test classes (`CognitivePlanningIntegrationTest.kt` and `ExecutionIntegrationTest.kt`). The live turn path (`CognitiveEngine.process`) does not call `evaluateDecision` or check `DecisionEngine.missingCapabilities`.
4. **`ToolBuilder` and `CapabilityGapDetector` Operate in a Private Mutant Habitat:** `ToolBuilder` only inspects `MicroSystemRegistry` and `GenomeRegistry` (`CapabilityGapDetector.kt:25-45`). It has no knowledge of `FailureSurface` active incidents or Android hardware speech synthesis state.

### What Input Would Let It Continue
To let the chain continue from failure detection through to autonomous capability repair:
1. **Failure-to-Cognition Bridge:** A coroutine must collect `failureSurface.degradedCapabilities` and invoke:
   ```kotlin
   cognitiveEngine.updateCapabilityState(
       cognitiveState.capabilityState.copy(
           degradedCapabilities = failureSurface.degradedCapabilities.value.toList()
       )
   )
   ```
2. **Cognitive Turn Integration:** The live conversation loop (`CognitiveEngine.process`) must invoke `decisionEngine.decide(...)` with candidate fallback/repair options requiring `"tts"`. When `"tts"` or `"STREAMING_TTS"` is found in `degradedCapabilities`, `missingCapabilities` flags it as a gap.
3. **Trigger Input to `requestBuild`:** An autonomous recovery or planner listener must receive the gap event and submit a `ToolSpecification(capability = "tts", ...)` to `JarvisEngine.requestBuild(spec)`.
