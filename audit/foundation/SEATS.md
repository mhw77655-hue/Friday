# SEATS: Build Loop Analysis for LAUNCH-WAKE-ORDER (PR #3)

This document audits the real build loop of story `LAUNCH-WAKE-ORDER` (PR #3) across all stages: gap detection, story writing, worker execution, CI judging, merge, device testing, and incident recording. Every claim cites exact `path:line` evidence.

---

## 1. Step-by-Step Lifecycle of LAUNCH-WAKE-ORDER

### Step 1: Gap Found
* **Who does it today:** Human / AI outside the repo analyzing raw Android device logs.
* **Evidence:**
  * `.ralph/prd.json:158`: `"Evidence (KNOWN from the raw log 20261024-235308-JARVIS, first launch, 1.1 s after process start; the 10-22 and 10-23 logs show the same per the handoff): FATAL IllegalStateException 'JarvisEngine.init() must run first' from JarvisEngine.getBodyCoordinator (JarvisEngine.kt:683) called by HomeScreen.kt:55."`
  * `.ralph/incidents/LAUNCH-WAKE-ORDER.md:5`: `"FATAL IllegalStateException \"JarvisEngine.init() must run first\" from JarvisEngine.getBodyCoordinator (JarvisEngine.kt:683) called by HomeScreen.kt:55 during cold launch composition (observed in raw log 20261024-235308-JARVIS, first launch, 1.1s after process start; 10-22 and 10-23 logs showed the same)."`
  * Device logs were generated on physical phone hardware (`Realme`, see `.ralph/prd.json:8`) outside git, manually or semi-manually extracted into session logs, and inspected by Venon or external reviewers.

### Step 2: Story Written
* **Who does it today:** Human (`Venon` / `mhw77655-hue`) or AI assistant outside repo authoring the PRD story entry.
* **Evidence:**
  * Commit `213a52c`: `"chore: queue CI-HONEST, LAUNCH-OLLAMA-DOWN, LAUNCH-WAKE-ORDER (must-fail gates)"` authored by `mhw77655-hue <mhw77655@gmail.com>`.
  * `.ralph/prd.json:154-178`: Defines `LAUNCH-WAKE-ORDER`, priority `8.2`, explicit acceptance criteria `AC1` through `AC6`, and exact `verifyCommand`.
  * No in-repo automated code writes user stories or acceptance criteria into `prd.json`.

### Step 3: Worker Run
* **Who does it today:** Multiple workers in progression:
  1. Phone-local Ralph using `opencode` (AI model `opencode/deepseek-v4.1-flash-free`):
     * Executed `.ralph/ralph.sh:357` (`opencode run -m "$CALL_MODEL" "$COMBINED_PROMPT"`).
     * Output recorded in `origin/reports/phone:reports/iter_2_1030_0720.md:1-85`. Produced the red test commit `ca34ae7` (`red: add LAUNCH-WAKE-ORDER UI engine access tripwire`) on branch `audit/ls-wake-order-red`, began modifying `HomeScreen.kt` and `JarvisEngine.kt`, then ran out of token limits / stalled (`origin/reports/phone:reports/wall_phone_1031_2317.md:14-45`).
  2. Cloud Ralph runner (`.ralph/cloud/run.sh` / `.github/workflows/ralph.yml`):
     * Attempted dispatch via GitHub Actions runner, hit rate limits across free model pool (`origin/reports/cloud:reports/final_r0_1104_0249.md`).
  3. External coding agent Jules:
     * Dispatched via GitHub pull request mechanism on branch `audit/ls-wake-order-jules1-15815667853899805852`.
     * Produced commits `3dcb14a` and `6be254d`:
       * Commit `3dcb14a:3`: `Author: google-labs-jules[bot] <161369871+google-labs-jules[bot]@users.noreply.github.com>`.
       * Authored `mobile/app/src/main/java/com/jarvis/app/EngineReadiness.kt:1-96`.
       * Authored `mobile/app/src/main/java/com/jarvis/app/ui/EngineAccess.kt:1-121`.
       * Updated `JarvisEngine.kt`, `JarvisNavHost.kt`, and seven screen files (`HomeScreen.kt`, `ConversationScreen.kt`, etc.).
       * Authored tests `mobile/app/src/test/java/com/jarvis/app/launchsurvival/EngineReadinessTest.kt:1-145` and `UiEngineAccessTripwireTest.kt:1-96`.
       * Authored incident note `.ralph/incidents/LAUNCH-WAKE-ORDER.md:1-37`.

### Step 4: CI Judge
* **Who does it today:** Code and script in this repo / GitHub Actions:
  * `.github/workflows/build.yml:10-95`: Compiles Kotlin, executes unit tests (`./gradlew testDebugUnitTest`), uploads test results artifact `junit-results` and APK artifact `debug-apk`.
  * `.ralph/ci_verify.sh:1-420`: Automated judge script polling GitHub Actions API (`GET /repos/$REPO/actions/runs`), verifying run conclusion (`success`), downloading JUnit XMLs, and checking individual test filters.
  * In PR #3, CI ran on commit `6be254d`, validating unit test passes for `UiEngineAccessTripwireTest` and `EngineReadinessTest`.

### Step 5: Merge
* **Who does it today:** Human (`Venon` / `mhw77655-hue`) on GitHub.
* **Evidence:**
  * Commit `3b409ce:1-25`:
    * `Merge: fa6af12 6be254d`
    * `Author: mhw77655-hue <mhw77655@gmail.com> 1794286560 +0200`
    * `committer GitHub <noreply@github.com> 1794286560 +0200`
    * `Merge pull request #3 from mhw77655-hue/audit/ls-wake-order-jules1-15815667853899805852`
  * No script in repo merges to `main`. In fact, `.ralph/audit.py:101-115` and AGENTS.md explicitly prohibit automated tools from merging or pushing directly to main.

### Step 6: Device Test
* **Who does it today:** Human (`Venon`) on physical Realme phone device.
* **Evidence:**
  * `.ralph/prd.json:173`: `"AC6: ... Also state plainly what this story cannot prove: the first-launch race on a real process stays UNPROVEN until the device test after the rename."`
  * AGENTS.md:7: `"Do not claim anything works without evidence: a test that exercised it, a CI run on the committed code, or a run on the phone."`
  * `.ralph/VISION.md:294-295`: `"A milestone is closed only when it has all three: code with passing tests; automated Life Test evidence produced on the real composition; and something Venon can run and see or hear on his phone. A green report alone never closes a milestone."`
  * Currently manual: downloading the debug APK built by CI (`.github/workflows/build.yml:50`), installing on device via ADB/Android package installer, testing cold launch behavior.

### Step 7: Incident Note
* **Who does it today:** Worker AI / coding agent authoring files committed into git.
* **Evidence:**
  * `.ralph/incidents/LAUNCH-WAKE-ORDER.md:1-37`: Incident post-mortem detailing WHAT, WHERE, WHEN, WHY, MITIGATION, REPRODUCTION, and STATUS.
  * `.ralph/incidents/INDEX.md:21`: `| [LAUNCH-WAKE-ORDER.md](LAUNCH-WAKE-ORDER.md) | 2026-10-24 | Launch crash: UI asking half-built engine getters before wiring completes | fixed |`
  * Added in commit `3dcb14a:5-6` by `google-labs-jules[bot]`.

---

## 2. The Eight Seats Table

| Seat | Role / Responsibility | Assigned To Today | Evidence (path:line) |
|---|---|---|---|
| **Gap Detection** | Identifies runtime failures, crashes, and missing capabilities from logs and device runs | **HUMAN** | `.ralph/prd.json:158`, `.ralph/incidents/LAUNCH-WAKE-ORDER.md:5` |
| **Spec Writing** | Drafts PRD stories, priorities, and testable acceptance criteria | **HUMAN** | `.ralph/prd.json:154-178`, git commit `213a52c` |
| **Worker** | Writes reproduction tests, production implementation code, and incident files | **AI OUTSIDE** | `.ralph/ralph.sh:357`, git commit `3dcb14a:3` (`google-labs-jules[bot]`), `reports/iter_2_1030_0720.md:1` |
| **Judge** | Evaluates compilation, test execution, assertions, and build results | **AUTOMATIC IN REPO** | `.ralph/ci_verify.sh:1-420`, `.github/workflows/build.yml:76-95` |
| **Device Test** | Installs debug APK on physical hardware, observes real UX, touches and speaks | **HUMAN** | `.ralph/prd.json:173`, `.ralph/VISION.md:294`, `AGENTS.md:7` |
| **Integration** | Reviews PR, signs off on safety/architecture, and merges code into `main` | **HUMAN** | Git commit `3b409ce:1-25`, `.ralph/audit.py:101-115` |
| **Investigation** | Root cause analysis of why failures occurred and post-mortem incident documentation | **AI OUTSIDE** | `.ralph/incidents/LAUNCH-WAKE-ORDER.md:1-37`, git commit `3dcb14a` |
| **Fidelity Guard** | Vision and architectural drift checks against `VISION.md` Section 13 | **AUTOMATIC IN REPO** | `.ralph/vision_audit.sh:1-85`, `.ralph/audit.py:1-450` |

### Seat Distribution Summary
* **AUTOMATIC IN REPO:** 2 (Judge, Fidelity Guard)
* **HUMAN:** 4 (Gap Detection, Spec Writing, Device Test, Integration)
* **AI OUTSIDE:** 2 (Worker, Investigation)
* **Total Seats:** 8
