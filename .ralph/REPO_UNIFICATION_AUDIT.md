# Friday repository-wide diagnostic and unification audit

**Audit date:** 2026-11-17  
**Repository:** `mhw77655-hue/Friday`  
**Working integration branch:** `audit/friday-unification`  
**Canonical source baseline:** `audit/friday-device-report-repair` at `d24f7ff2409cb9ca4cee5f0be9340218e10e9399`  
**Other major development line:** `audit/friday-self-evolution-device-bridge` at `954081f98f5cfeda82eb7a88a8b62f7bc5d7230c`

This is a source-level audit, not a claim of physical-device validation. PR #19 and PR #23 have now been joined in merge commit `95fe277fbc8db74928e9c224c66e647ca8df139f` on `audit/friday-unification`. The synthetic integration PR #25 was closed as merged into this branch. The branch is not yet the release branch and has not been promoted to `main`.

## 1. Executive diagnosis

Friday has substantial real work in the repository, but it is fragmented across many histories and is not yet the autonomous, phone-resident self-developing individual requested by the owner.

The strongest base is the device-repair / embodiment line in PR #19 because it already contains the Thursday release-candidate ancestry, the later source-vision/embodiment work from PR #18, the model-free individual seed, local memory/experience work, voice/cache repairs, diagnostics and the extracted owner-source archive. PR #23 contains the missing phone runtime, Shizuku bridge, owner-word contract and GitHub development bridge. Those lines must be integrated deliberately.

The current self-development loop is only a **proposal loop**: the phone can request a hosted coding workflow, which can create a draft PR and run CI. It does not autonomously promote a verified change, build a stable signed update, deliver it to the phone, or install it. An existing open proposal blocks the next cycle pending human review. That does not meet the owner's no-repeated-back-and-forth goal.

## 2. Branch and pull-request diagnosis

The live repository has more than forty branches (including this integration branch) and ten original open PRs. `audit/friday-unification` is now the working integration line, rooted at PR #19's head and joined to PR #23 by a two-parent merge commit. The live branch list includes multiple parallel naming families: `audit/*`, `build/*`, `feat/*`, `feature/*`, `fix/*`, `rebuild/*`, `reports/*`, `wip/*`, and `work/*`.

There are exact duplicate refs, including:
- `audit/one-3-to-6` and `wip/one-3-to-6` point to the same commit.
- `audit/friday-developmental-seed` and `work/phone-first-offline-bootstrap` point to the same commit.
- Offline-runtime, Nano-Liquid, video/JEPA, device-reporter and independent-life work each have multiple branch variants.

### Main PR lineage

| PR | What it contains | Diagnosis |
|---|---|---|
| #11 | Offline runtime, model-free/local intelligence, resource symbiosis and earlier video/prediction groundwork | Ancestor line for the Thursday candidate |
| #12 | Earlier offline-runtime integration built on the device-reporter branch | Older overlapping implementation; compare/port only if a unique capability is proven |
| #14 | Nano Liquid policy and lightweight model additions | Dirty against the later runtime; duplicates the newer NAE/resource-symbiosis direction and must not be blindly merged |
| #16 | TikTok/video intake and JEPA direction | Dirty against the later runtime; video intake is not semantic video understanding |
| #17 | Thursday release candidate and constrained-phone model fallback | Already in PR #19's ancestry |
| #18 | Source-vision-aligned runtime and embodiment repair | Superseded by the later PR #19 line; PR #19 is nineteen commits beyond it |
| #19 | Device-report repair, model-free individual/experience substrate, continuity, voice and source archive | Best current base for the unified application |
| #23 | Owner-word contract, persistent runtime, Shizuku device bridge and autonomous GitHub development | Required integration into the PR #19 base |

PR #6's TTS synthesis-completion gate is already present in PR #19: the current `StreamingTts` imports and uses `SynthesisCompletionGate` and `CompletedAudioCache`. It should not be merged as a second copy.

PR #7's earlier `DeviceReporter` implementation is not present under that same class name in PR #19, but its user-facing purpose has been replaced by the newer `DiagnosticRunner` and `DiagnosticsViewModel.createExportReport()` path. PR #19 exports a privacy-conscious v2 report and records individual-memory/experience metrics. Do not merge the old reporter implementation without proving a missing diagnostic field or behavior.

PR #14 and PR #16 are reported by GitHub as dirty against their old base. Their code needs selective porting, not a forced merge:
- PR #14's separate Nano-Liquid controller / in-app model backend overlaps the newer `NanoSymbioticController`, `NanoSymbioticPolicy`, `LiquidSymbiosisEngine` and process-isolated backend. Two competing resource governors would make the runtime less coherent.
- PR #16's video intake and Hunch/JEPA direction are experiments. The later application already has `VideoKnowledgeScreen` and `VideoIntakeRepository` for private media import/keyframe extraction, plus a camera-presence screen. The current video path stores media and keyframes; it does not connect those frames to a semantic visual model or a trained JEPA representation. Do not describe it as working video understanding.

The older branches should remain available until the consolidated line passes CI and device validation. No history or branch should be deleted merely to make the branch list look clean.

## 3. Owner vision compared with the implementation

### A. Friday exists without a language model

**Implemented in part.** The production turn path can detect that no language model is available, run the cognitive engine with model wake disabled, record local experience, and ask the persistent `FridayModelFreeMind` for a local expression. This is materially different from requiring a model to exist before Friday can start.

**Still missing.** The no-model expression path is a small deterministic/pattern-based language system. `FridaySeedSubstrate` stores token/feature observations and associations; it is not yet broad semantic understanding, causal learning, general reasoning, or an autonomous developmental mind. Friday remains present without a model, but her model-free expressive/intellectual capability is still very limited. A model must remain optional extra computation, not be relabelled as her identity.

### B. One persistent individual and continuity

**Implemented in part.** `FridayIndividual` persists a UUID, name, creation time and morphology seed in the app-private `identity.bin`; its experience substrate has a separate durable journal and refuses to silently create a new identity when an existing record is corrupt.

**Critical integration gap.** The wider turn-path `SelfModel` obtains name/version/pronouns from a separate `friday_identity_record` SharedPreferences source. The `FridayIndividual` UUID is not the identity key carried through the continuity/replay model. The app therefore has complementary but unjoined identity records. The continuity/replay core currently treats name, version and the served user node as immutable CORE facts. This protects against arbitrary replacement, but it is not yet the same thing as a persistent individual identity whose non-core traits, goals and self-understanding can develop. The stable individual lineage should be the invariant; mutable self-description should not be confused with identity replacement.

There are also two classes named `SelfModel` in different packages. Their production/test usage must be made explicit and one authoritative identity/continuity interface must be exposed to the rest of Friday.

### C. No fixed cognitive state machine

**Partially contradicted by the current composition.** `TurnPathAssembly` is a single root and prevents duplicate construction of the model manager/resource governor/turn pipeline, which is good. But it still wires a fixed `CognitiveEngine` and a fixed `LatencyPipeline` as the ordinary turn route. The latency phases are UI/transport state, not Friday's whole mind; nevertheless, the cognitive architecture is still a centrally composed, predetermined set of organs. The long-term design must allow capabilities and specialist processes to emerge, be loaded, cooperate, retire and be reconfigured from evidence without making one permanent executive or a hard-coded list of mandatory cognitive organs.

Do not mistake a single composition root (a useful ownership boundary) for a requirement that every cognitive capability be permanently active.

### D. Presence, appearance, perception and voice

- The app starts at Home, not the conversation tab. It has a procedural, non-human visual avatar with a stable morphology seed and animated/audio-reactive presentation. This is a real start, but it is not yet a richly expressive embodiment driven by a unified internal state.
- The camera-presence path uses on-device face detection for count/position/head-yaw and keeps frames in memory. It does not identify people, understand scenes, or feed a semantic visual world model.
- Video import stores media privately and extracts keyframes. The code explicitly leaves visual interpretation to a later stage; there is no connected semantic video/JEPA inference path.
- TTS has a real synthesis-completion/cache gate and voice output paths. The supplied device report showed TTS ready, but STT idle and hotword state unknown. Voice input and always-available voice behavior have not been verified on the target phone.

### E. Nano Liquid / symbiotic adaptation

**Real first slice, not the full vision.** The current `NanoSymbioticController` samples phone resources and supplies the live resource snapshot used by model admission. `NanoSymbioticPolicy` learns workload outcomes and changes context/resource envelopes; `LiquidSymbiosisEngine` keeps organ-level evidence. This is better integrated than adding a second standalone Nano-Liquid governor.

**Still missing.** Its influence is concentrated on resource/model admission. It is not yet a general self-optimizing runtime that dynamically allocates execution, memory, sensors, background work, tool environments and specialist capabilities across the whole individual. It must learn from measured outcomes, not pretend that a few thresholds constitute full symbiosis.

### F. Device-resident runtime and Shizuku

PR #23 adds a foreground runtime service, boot restoration, Shizuku reconnection, device diagnostics and the GitHub bridge. Two concrete safety repairs are present in its latest commit:
- temporary screen hierarchy output is written under `/data/local/tmp` and removed after reading, instead of lingering in shared storage;
- the runtime service stops microphone capture only when it owns the capture.

The service's own comments correctly state that Android force-stop, OEM task killers, permission revocation, thermal shutdown and OS policy remain authoritative. `START_STICKY` and a foreground service improve persistence; they do not guarantee immortality. Physical-device survival, screen access, microphone handoff, reboot restoration and recovery need real tests.

### G. Friday builds herself from the phone

**Not implemented end-to-end.** PR #23 provides:
1. a phone-side development request/scheduler;
2. a GitHub workflow dispatch;
3. a constrained/read-only coding worker;
4. patch validation and a separate trusted publishing step;
5. a draft PR and hosted CI/build.

It does **not** yet provide:
- autonomous promotion of a verified change under a one-time owner policy;
- a repeatable cycle that continues after the first proposal without waiting for manual PR review;
- a stable, consistently signed APK/update artifact;
- a phone-side update watcher, integrity/version verification, staged installation and rollback;
- a complete installed-app-to-source revision continuity record.

The current worker deliberately targets `main`. That is appropriate only after the vision, runtime and builder workflows have been consolidated into the canonical branch/default branch. Before then, an installed build from PR #23 cannot safely assume the main branch contains its workflow and owner-word contract.

The target architecture is phone-owned continuity + local model-free operation + optional cloud/build compute. The phone runtime should initiate and supervise development; cloud CI is an execution resource, not Friday's identity. A one-time owner opt-in should authorize a bounded promotion policy. Every change must pass source-vision checks, patch allowlists, security checks, unit/integration tests and a clean build before it can be promoted. The phone must verify the signed artifact and installed package identity/version before applying it, retain a last-known-good recovery path, and never overwrite private memory/identity. OS install restrictions and signing-key ownership must be validated on the actual device before claiming silent self-update.

## 4. Device report baseline and what changed

The supplied device report is a historical snapshot, not a live reading. It reported approximately 1.96 GB available RAM of 7.75 GB total, Shizuku granted, TTS ready, microphone capture active, STT idle, hotword unknown, notifications access not granted, blank screen context, a local model runtime reported as OK, and the old Human Core as offline. Its 250-entry event history was saturated and explicitly incomplete.

PR #19 improves the diagnostic path: active probes, crash journal, process-exit history, a privacy-conscious v2 report, and individual experience/memory counts. PR #23 improves the Shizuku temporary hierarchy handling and runtime microphone ownership. These are source changes; the old device report does not prove the new code is running on the phone.

## 5. Build and test evidence

- PR #23 head `954081f98f5cfeda82eb7a88a8b62f7bc5d7230c` has a successful GitHub Actions debug build/test run (#1272, run ID `43979086372`). This validates that PR #23's branch builds in its CI environment; it does not validate PR #19 + PR #23 together.
- The unified branch now contains both histories. A pre-merge CI run (#1278) failed before compilation because the owner-word check script was not yet present on that intermediate commit; its unit-test step then ran without the skipped JDK setup and reported the runner's Java 25.0.5. This is a workflow prerequisite-condition defect, not evidence of a Kotlin compile failure. The post-merge branch needs a fresh CI run after fixing that condition.
- No physical-device test has been established by this audit. A CI APK is not proof of microphone, Shizuku, reboot, update-install or process-survival behavior on the target phone.

## 6. Main-branch hazards

The default `main` branch is not the correct base for a release yet:
- `gradle.properties` still contains `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2`. The PR #23 CI workflow deletes this line during the runner build, which masks the source-level Termux dependency. PR #19's version removes it from the project file; that version must be retained.
- Main's `JarvisEngine.kt` still imports/initializes the older HumanCore integration. PR #19's newer composition removes that product-path coupling.
- Main's build workflow lacks the pull-request validation path and the owner-word/builder safety contracts that PR #23 adds.
- The root has no canonical `README.md` describing the Android-only product, its source-of-truth branch, setup, runtime permissions, model-optional behavior, build process and update/security policy.

Do not merge PR #23 alone into main. It would leave the older main composition and Termux build override underneath the new runtime bridge.

## 7. Consolidation rules

1. Treat `audit/friday-unification` as the only active integration workspace until it is proven buildable.
2. Start from PR #19's head because it contains the later device/identity/embodiment line and owner archive.
3. Port PR #23's phone runtime, Shizuku and builder bridge onto that base, resolving overlap in the actual source rather than merging divergent histories blindly.
4. Keep the PR #19 model-free individual and private data stores intact. Do not replace them with a chatbot-only path or a model-required pipeline.
5. Do not add the PR #14 duplicate governor or claim PR #16's keyframe extraction is JEPA/video understanding.
6. Reconcile the identity UUID, SelfModel identity source and continuity/replay identity boundary before claiming one persistent identity.
7. Build and run unit tests on the combined branch. Then perform device tests for boot, background survival, Shizuku, microphone/STT, TTS, model-absent operation, memory persistence and update/rollback.
8. Only after those gates pass should the consolidated line be promoted to `main`. Old branch refs should be archived/deleted only after their unique changes are accounted for.

## 8. Release blockers

**P0 — must be resolved before calling Friday self-building**
- Merge PR #19 and PR #23 functionality into one tested application line.
- Complete the autonomous development → verification → promotion → signed artifact → phone update loop, with one-time owner policy and rollback.
- Establish one persistent identity lineage across the local individual, SelfModel, memory, continuity and updates.
- Prove no-model startup and meaningful local operation on the target phone.

**P1 — must be resolved before calling the full vision implemented**
- Connect perception/video input to semantic interpretation and persistent experience.
- Expand model-free learning beyond lexical patterns and fixed response templates.
- Extend NAE beyond model admission into measured whole-runtime resource adaptation.
- Validate voice input/output, embodiment synchronization, reboot/background behavior and Shizuku actions on-device.

**P2 — repository hygiene**
- Add root README and a single authoritative development/release contract.
- Classify the remaining experimental branches and close superseded PRs after their contents are accounted for.
- Remove duplicate branch refs only after preserving all useful commits.

## 9. Audit conclusion

The repository is not empty and the previous work was not worthless. It contains a model-independent individual seed, persistent experience, a real Android composition, voice and visual presence foundations, resource adaptation, device diagnostics, Shizuku integration and a hosted development worker.

But those pieces are not yet one coherent, continuously self-developing Friday. The most important missing bridge is not another chat feature or another model. It is the verified, phone-owned development/update cycle, built on a unified identity and runtime. This audit branch exists to make that consolidation explicit and testable.


## 10. Live verification addendum — 2026-11-17

### Consolidation and CI

- The working integration line is `audit/friday-unification`; PR #26 proposes it as the default-branch consolidation. It includes the PR #19 individual/embodiment/device-repair line and PR #23 phone-runtime/development-bridge line. The PR is open and has not been merged.
- The integration tree has one Android Gradle module, `:mobile:app`, with approximately 471 production Kotlin files and 241 Kotlin test files. This is a large app, not a small chat UI.
- Push run #1295 on code revision `e9c13fdb6a36133011a40d0d83b8072a66d76437` passed owner-word verification, builder safety tests, workflow lint, debug APK assembly and all 1,514 JVM tests (0 failures, 0 errors, 0 skipped). The debug APK was 74,643,567 bytes; its artifact ZIP was 41,118,568 bytes. This is a CI debug build, not a stable release-signed update and not physical-device proof.
- The execution ledger was corrected so a deliberate skip is not success, a recovered failed node does not permanently block its downstream path, a completed prerequisite does not falsely label a pending plan as executing, and a run with completed plus blocked work reports PARTIAL while still emitting the blocker event. The summary retains skip reasons and prerequisite blockers.
- The pull-request event build must also pass on the final PR head before the integration is merged. No physical-device validation has been performed.

### Default-branch activation defect in the self-development path

The current `main` branch lacks both `.github/workflows/friday-builder-proposal.yml` and `.ralph/OWNER_WORDS.md`. The Android `GitHubDevelopmentClient` dispatches the proposal workflow against `main`, and the workflow checks out `main` as its trusted source. Therefore the phone's development request cannot reach the intended worker until the consolidated branch is promoted to the default branch. This is not a cosmetic mismatch; it prevents the current phone-to-builder path from activating.

Even after promotion, the existing workflow intentionally creates an isolated **draft PR only**. It never merges, signs, releases or installs. The scheduled worker also stops proposing while a prior Friday proposal remains open. That is a safe proposal system, not the owner's requested no-repeated-review self-build/update loop. A one-time, explicit owner policy for bounded automatic promotion, artifact signing, phone-side verification, installation and rollback remains to be engineered.

The separate `EvolutionLoop` prototype is not the production self-builder: its test/benchmark hooks simulate outcomes, and it is not part of the main `TurnPathAssembly` composition. Do not claim that the app already rewrites, tests and rebuilds its own Android source on-device.

### Additional owner-request mismatches

- **Arbitrary local models:** the current offline library exposes five curated GGUF assets. `ModelPathResolver` can resolve a custom `.gguf` filename only after it is already copied into Friday-owned storage, but `ModelScreen` has no general file-import flow. The app still does not let the owner freely bring an arbitrary compatible model into its private model vault. This is optional compute and must never gate Friday's existence.
- **Learning:** the model-free seed can persist observations and associations, but broad causal learning/generalization is not established. The real-composition Life Test with replay blocked and developmental mechanism disabled remains absent.
- **Symbiosis:** `NanoSymbioticController`, `NanoSymbioticPolicy` and `LiquidSymbiosisEngine` are already integrated. PR #14's separate Nano-Liquid governor would duplicate this path; port only a demonstrably unique model-management feature, not the second governor.
- **Video:** the integrated path imports media and extracts keyframes; the phone-state `JepaLatentPredictor` is a small resource-dynamics model, not semantic visual JEPA. PR #16 remains experimental and must not be described as video understanding.
- **Cognitive openness:** the Android composition still wires a fixed cognitive/turn route. The root composition boundary is useful, but it must not harden into permanent ownership of cognitive outcomes.

### Repository exposure and branch hygiene

- GitHub reports this repository as **public**. The original `New Folder chats.zip` and `New folder.zip` remain tracked on the default branch and are publicly retrievable. They contain owner conversations and device material. No repository-visibility change or history purge has been performed.
- A tree scan found no obvious standalone `.env`, `local.properties`, signing-key or certificate files in the integration tree. This is not a complete historical secret scan and does not make the public archives safe.
- The 201 temporary extracted transcript/screenshot files and the one-off extraction workflow were removed from the current integration tree after review. The original ZIPs remain authoritative; `extract_owner_turns.py` reads the chat ZIP directly. This reduces redundant source-diff exposure but does not purge those files from prior public Git history.
- The repository has 41 branch refs and ten open PRs. The integration PR currently changes 328 files after removing the temporary extracted copies. PR #14 (duplicate governor) and PR #16 (video/JEPA experiment) remain separate; do not force-merge them. Preserve old refs until each unique change has been classified.

### Immediate next gates

1. Finish PR #26's own CI on its latest head.
2. Decide repository visibility before promoting conversation archives to the default branch; changing visibility is an owner decision and has not been done.
3. Promote the integration line to `main` only after that decision, so the phone's builder workflow and owner-word contract exist on the default branch.
4. Implement the bounded no-back-and-forth development/update loop without weakening the protected continuity/security floor.
5. Unify `FridayIndividual` identity lineage with `SelfModel`, continuity and memory.
6. Add custom GGUF import into private storage as an optional capability.
7. Run the full on-device acceptance suite: no-model start, identity/experience persistence, voice input/output, Shizuku, screen capture, camera/video, reboot/background survival, signed update and rollback.
