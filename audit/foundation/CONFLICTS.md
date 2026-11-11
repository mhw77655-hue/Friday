# CONFLICTS: Production Assumptions vs VISION.md

This audit exposes architectural conflicts where docs, PRD stories, or code paths assume:
1. **Termux** environment on the host phone.
2. **Ollama or localhost model server** in production (specifically `127.0.0.1:8080`).
3. **Two separate composition roots** (`JarvisEngine` vs `TermuxJarvisServer`).

All assumptions are cross-checked against `.ralph/VISION.md` (Sections 1, 8, 12).
*Crucial new reality:* **Termux has been uninstalled from the phone.** A different app, `VSCodeTermux 0.1.0` (contents unknown), is installed instead. Everything that lived solely in Termux is gone from the phone.

---

## 1. Assumptions of Termux on the Host Device

### Doc & Story Assumptions
* **`AGENTS.md:5`**: `"Build and test only through CI (.ralph/ci_verify.sh). Do not run local Gradle builds."` (Rooted in the limitation of Termux/Android terminal inability to run local Gradle).
* **`.ralph/prd.json:20`**: `"cd /sdcard/jarvis-repo && bash .ralph/ci_verify.sh ..."` — assumes the git repo is checked out at `/sdcard/jarvis-repo` or `/mnt/sdcard/jarvis-repo` inside Termux.
* **`gradle.properties:10`**: `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2` — assumes Termux's custom `aapt2` binary at `/data/data/com.termux/`. This binary is now completely non-existent because Termux was uninstalled.
* **`.github/workflows/build.yml:61`**: `sed -i '/^android\\.aapt2FromMavenOverride=/d' gradle.properties` — explicitly has to neutralize the Termux override so GitHub Actions does not fail.
* **`voiceforge/voiceforge_server.py:5,11`**: Declares that Kokoro TTS runs inside Termux on `http://127.0.0.1:8880`. With Termux uninstalled, Python, Kokoro, and VoiceForge cannot execute.

### Code Path Assumptions
* **`mobile/app/src/main/java/com/jarvis/app/voice/VoiceForgeClient.kt:29`**: Hardcoded URL `private const val DEFAULT_URL = "http://127.0.0.1:8880"` for the Python server running in Termux.
* **`mobile/app/src/main/java/com/jarvis/app/ObsidianSync.kt:28`**: Hardcoded path `private const val VAULT_PATH = "/storage/emulated/0/JarvisSync/vault/JARVIS"`, assuming an external sync folder managed via Termux scripts.
* **`mobile/app/src/main/java/com/jarvis/app/termux/TermuxJarvisServer.kt:74-625`**: A complete server implementation meant to be launched from Termux CLI (`TermuxJarvisServer.main`).

### Conflict with VISION.md
* **`.ralph/VISION.md:248-251` (Section 8)**:
  > *"A device is a possible body or surface for her, not automatically a separate Friday. Venon decides whether a given device hosts the entity or merely exposes controls to it. Wherever she is hosted, she should inhabit that device rather than sit on top of it as a conventional app — shaped to the host instead of forcing the host to behave like a generic AI platform."*
* Anchoring Friday to a Termux CLI userland forces Android to pretend to be a Linux distribution. When Termux was uninstalled, all sidecar scripts, model servers, and local tools died instantly. Friday must inhabit Android directly as an Android application.

---

## 2. Assumptions of Ollama or Localhost Model Server (`127.0.0.1:8080`)

Now that Termux is uninstalled, **nothing is serving at `127.0.0.1:8080`**. Below is every place in the codebase that expects or connects to it:

### Places Needing `127.0.0.1:8080`
1. **`mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:47`**:
   ```kotlin
   const val DEFAULT_OLLAMA_ENDPOINT = "http://127.0.0.1:8080"
   ```
2. **`mobile/app/src/main/java/com/jarvis/app/model/adapters/OllamaAdapter.kt:46`**:
   ```kotlin
   private var baseUrl: String = "http://127.0.0.1:8080",
   ```
3. **`mobile/app/src/main/java/com/jarvis/app/model/OllamaModelBackend.kt:37`**:
   ```kotlin
   private val baseUrl: String = "http://127.0.0.1:8080"
   ```
4. **`mobile/app/src/main/java/com/jarvis/app/termux/TermuxJarvisServer.kt:77`**:
   ```kotlin
   private val ollamaHost: String = "http://127.0.0.1:8080",
   ```
5. **`scripts/jarvis-model.sh:17` & `scripts/jarvis-start.sh:15`**:
   Assumes Ollama daemon running on `127.0.0.1:8080` inside Termux environment.
6. **`mobile/app/src/main/java/com/jarvis/app/JarvisEngine.kt:491`**:
   Launches `anchorEngine.anchor()` at startup, which requests `ModelTier.RESIDENT` from Ollama at `127.0.0.1:8080`.
7. **`.ralph/prd.json:8` (LAUNCH-SURVIVAL)**:
   Explicitly predicated on: `"debug APK installed on the Realme with Ollama serving at 127.0.0.1:8080"`.
8. **`.ralph/prd.json:133` (LAUNCH-OLLAMA-DOWN)**:
   Explicitly addresses the crash when `127.0.0.1:8080` is unreachable: `"Ollama server unreachable at 127.0.0.1:8080 ... cannot load resident"`.

### Conflict with VISION.md
* **`.ralph/VISION.md:241-244` (Section 8)**:
  > *"Network availability changes available capability, not whether she exists. Offline is a real operating condition, not a fallback mode. Her local identity, continuity, memory, relationship state, and whatever cognition, perception, and action her local resources can sustain remain part of the same Friday when the network is absent..."*
* Assuming a background daemon running on `127.0.0.1:8080` violates the principle that Friday's presence does not depend on external daemon processes running in an uninstalled terminal emulator. When Ollama is absent, Friday's brain must either degrade gracefully to an on-device embedded engine or cloud tier, never crash or enter an infinite loop.

---

## 3. Two Separate Composition Roots

### The Two Composition Roots
1. **`JarvisEngine.kt`** (Android App Entry Point):
   * Initialized in `mobile/app/src/main/java/com/jarvis/app/JarvisApplication.kt:11`.
   * Sets up Android AndroidContext, registers Android broadcast receivers, initializes `BodyCoordinator`, `PresenceMonitor`, and Android `ResourceGovernor`.
   * Wires the Human Core (`HumanCore.init(...)`, `JarvisEngine.kt:389-408`).
2. **`TermuxJarvisServer.kt`** (JVM / Termux CLI Entry Point):
   * Initialized in `mobile/app/src/main/java/com/jarvis/app/termux/TermuxJarvisServer.kt:74`.
   * Stubs `HumanCore` exchanges to no-ops (`TermuxJarvisServer.kt:150-165`).
   * Uses an all-clear, non-Android `ResourceGovernor` and `InMemoryGraph`.
   * Builds its own separate copy of `CognitiveEngine`, `LatencyPipeline`, `MemoryStore`, and `Workspace`.

### Active Work & Unmerged Status
Story `ONE-2-COMPOSITION` (`.ralph/prd.json:187-200`) was intended to unify these roots into `TurnPathAssembly.kt`:
* Commits `dc8a68f`, `5613bc5`, and `a3ea8bf` introduced `TurnPathAssembly.kt`, `AndroidPlatformPorts.kt`, and `JvmPlatformPorts.kt`.
* However, in `.ralph/prd.json:200`, `ONE-2-COMPOSITION` still has `"passes": false`.
* In `JarvisEngine.kt:252-649`, the legacy wiring remains active alongside `TurnPathAssembly`, creating duplicate assembly graphs.

### Conflict with VISION.md
* **`.ralph/VISION.md:324` (Section 12 Disclosed Deviations)**:
  > *"- Two compositions exist: the Android app wires the Human Core and the real resource snapshot; the JVM/Termux server stubs the Human Core and reports all-clear resources. Evidence from the second does not speak for the first."*
* Testing on the JVM/Termux composition does not prove behavior on the Android phone. Because Termux is uninstalled, `TermuxJarvisServer` cannot run on the phone at all; only the Android composition exists in reality on the target device.
