# Friday repository structure and incremental migration contract

**Status:** inventory snapshot and migration policy  
**Authority:** `.ralph/FRIDAY_DELIVERY_PLAN.md` and `AGENTS.md`  
**Rule:** reduce structural fragility without a risky mass rewrite, loss of validated behavior or competing product line.

## 1. Observed repository shape

Inventory of `repair/full-app-resilience-audit-v2` at the time this document was written:

- 1,313 tracked tree entries.
- 1,084 entries under `mobile/app`.
- 629 files under `mobile/app/src/main/java`.
- 366 JVM unit-test source files under `mobile/app/src/test/java`.
- 39 Android instrumentation-test source files under `mobile/app/src/androidTest/java`.
- Gradle currently includes one Android application module: `:mobile:app`.
- The production Kotlin namespace has many sibling domain packages and several important orchestration classes at the root package.
- `local-repo/` contains a pinned Sherpa ONNX Android AAR and Maven metadata.
- `jniLibs/arm64-v8a` contains packaged native libraries; `mobile/app/src/main/cpp` contains native build sources.
- `research/legacy-prototypes/` contains exploratory code; `vault/` is explicitly historical/non-authoritative material.
- `.ralph/FRIDAY_DELIVERY_PLAN.md` is canonical; `mobile/app/src/main/assets/friday/FRIDAY_DELIVERY_PLAN.md` is its offline bundled mirror and must remain byte-identical.

This is a structural observation, not a claim that every file is wrong. The app's size and single-module build are not defects by themselves. The fragility risk is that many unrelated domains, UI, orchestration, platform integration, research and recovery mechanisms currently share one Gradle compilation/dependency boundary.

## 2. Intended repository ownership

| Area | Owns | Must not become |
|---|---|---|
| `.ralph/` | Owner intent, one delivery plan, objective/evidence ledgers, incident records and audit tooling | A second source tree or multiple competing plans |
| `mobile/app/` | Android application, production resources, manifest, app composition and Android-specific adapters | A dumping ground for cloud build agents, repository scripts or historical prototypes |
| `mobile/app/src/test/` | Fast deterministic JVM tests for pure logic and contracts | A mock-only substitute for production integration |
| `mobile/app/src/androidTest/` | Real Android/Compose/platform integration and device acceptance tests | A place to hide unexecuted acceptance claims |
| `mobile/app/src/main/cpp/` and packaged native libraries | Native source, ABI-specific outputs and JNI boundary | Untracked binaries with unknown provenance |
| `docs/` | Current architecture contracts, operational procedures and evidence standards | Competing objectives or stale product authorities |
| `formal/` | Abstract formal specifications and machine-checked invariants | A claim that Android implementation conforms without refinement tests |
| `research/` | Active experiments with provenance, licence, hypotheses and reproducible results | Production dependencies by accident |
| `tools/` and `scripts/` | Reproducible repository/build/audit automation | Runtime functionality silently required by the APK |
| `local-repo/` | Only intentionally vendored artifacts unavailable or unsuitable from configured repositories, with checksums/licences | An unreviewed binary cache |
| `vault/` | Historical, read-only donor material and archived reports | Active architecture, build inputs or implementation instructions |
| `governance/` and `standards/` | Machine-readable repository policy and engineering standards | Duplicated mutable product plans |

Root-level owner evidence archives require a privacy/provenance review before relocation or removal. Do not publish raw device or personal evidence into public artifacts.

## 3. Target dependency direction

The eventual structure should separate stable contracts from replaceable implementations. Names below describe responsibility boundaries, not a demand to create every Gradle module immediately.

1. **Core contracts/domain:** identity-independent value types, operation contracts, outcomes, provenance, policy decisions and pure algorithms. No Android, Compose, GitHub SDK, network or native dependency.
2. **Core runtime/continuity:** lifecycle-independent orchestration, durable event/checkpoint contracts, task state, recovery policy and resource budgets. Platform persistence is injected through interfaces.
3. **Diagnostics and immune/recovery:** event capture, classification, containment, evidence, recovery coordination and independent verification. It observes other domains through contracts; it must not depend on UI.
4. **Capability providers:** models, voice, vision, memory, actions, cloud workers and builders behind replaceable contracts. One provider must not own Friday's identity or force unrelated features to depend on it.
5. **Android host adapters:** Activity/Compose, Android services, WorkManager, PackageInstaller, Shizuku, permissions, Keystore, SQLite and device resource probes. Android APIs terminate at this boundary.
6. **Cloud/development adapters:** GitHub authorization/API, task transport, artifact manifests, CI orchestration and remote AI workers. No GitHub credential or build tool enters the installed APK unless it is explicitly an app-facing, least-privilege integration.
7. **Feature/UI layer:** owner interaction and truthful views over operations. UI invokes application contracts; it does not own authorization or business rules.

Dependency direction is inward toward contracts. UI and platform adapters depend inward; core contracts do not import Android, Compose, GitHub, network clients or feature packages. Cross-feature calls go through explicit contracts/events rather than sibling-package reach-through. Avoid cycles. Keep privileged code and signing trust in the smallest possible boundary.

## 4. Incremental migration sequence

Every wave is a separate, reviewable change set with a measured baseline, a behaviour corpus, an explicit file owner, migration script if needed, tests, dependency report and rollback plan. Do not rename/move thousands of files at once.

### Wave 0 — Freeze the truth and inventory

- Reconcile tracked files, generated files, binaries, duplicate plan copies, active Gradle inputs and actual production entry points.
- Produce compiler-aware symbol/dependency graphs for Kotlin/Java and explicit JNI/native/manifest edges.
- Classify each source file as production, test, research, generated, vendored, historical or unknown.
- Find duplicate types, dependency cycles, broad package access, reflection/service-loader edges, unowned files and dead code candidates.
- Establish a canonical package/module owner map and CODEOWNERS or equivalent review ownership.
- No source relocation in this wave.

### Wave 1 — Remove build and repository ambiguity

- Pin and checksum every vendored AAR/native binary; record source URL, version, licence, ABI and reason it is vendored.
- Separate generated outputs and local developer state from tracked source.
- Ensure root Gradle settings, convention plugins and version catalog are the single build configuration authority.
- Make source revision, APK digest, signing certificate and dependency/SBOM provenance available in release evidence.
- Keep the canonical delivery plan and bundled offline mirror byte-identical through a required CI gate.

### Wave 2 — Stabilize contracts inside the existing module

- Extract pure interfaces/value types before moving implementation files.
- Establish dependency rules for identity/continuity, operation execution, approvals, diagnostics, host adaptation, cloud transport and update channels.
- Add architecture tests that fail on forbidden imports and dependency cycles.
- Introduce explicit composition roots; eliminate accidental service-locator/global access where it obscures ownership.
- Keep public APIs small and versioned; add contract tests and migration compatibility tests.

### Wave 3 — Split modules only where evidence supports it

Candidate first boundaries: pure `:core:contracts`, pure `:core:runtime`, Android `:platform:android`, and selected feature/provider modules. Confirm with actual dependency graphs before choosing the split. A module split is accepted only if it reduces coupling/build time or enables independent verification without increasing circular dependencies, duplicated state or developer friction.

Do not create one Gradle module per "organ", screen, class or research idea. Module count is not architecture quality.

### Wave 4 — Isolate the development station and cloud control plane

- Keep repository/build orchestration and source-editing agents outside the installed APK's privileged runtime.
- Keep the phone-side station protocol narrow, signed, allowlisted and durable.
- Keep GitHub credentials scoped and separate from device enrollment/signing keys.
- Put candidate generation in isolated branches/workspaces and require independent verification before PR promotion.
- Add contract tests between phone station, cloud task broker, GitHub adapter and release/update channel.

### Wave 5 — Retire duplication and historical leakage

- Trace old/new implementations and preserve useful algorithms with provenance and tests.
- Move only confirmed historical/non-production artifacts to the archive or remove them after owner/privacy/licence review.
- Remove dead code only after reference, reflection, manifest, JNI, generated-code and runtime traces show it is unreachable.
- Do not promote donor prototypes from `vault/` or `research/legacy-prototypes/` into production without an explicit adaptation and validation record.

### Wave 6 — Enforce structure continuously

- CI checks dependency direction, package/module ownership, public API changes, duplicate resources, vendored checksums, secrets, plan mirror consistency, generated-file drift and test placement.
- Changes touching protected contracts require owners/reviewers independent of the implementer.
- Architecture changes include migration/rollback notes and evidence of reduced coupling or improved verification.
- A scheduled audit reports new cycles, oversized packages, stale dependencies, unowned files and test gaps; it opens bounded tasks rather than rewriting the repository automatically.

## 5. Non-negotiable migration rules

- Preserve one canonical plan, one product identity, one release signing lineage and one production composition.
- Never move code based only on folder names or lexical references; include compiler, manifest, reflection, coroutine, JNI and runtime paths.
- Never merge formatting-only churn with semantic architecture changes.
- Keep each migration bisectable and revertible.
- Do not weaken tests, remove failing tests or relabel skipped work as success to make a migration green.
- No module split without a verified dependency graph and baseline build/test measurements.
- No source agent can approve its own migration.
- Keep owner data and private device evidence out of public logs and artifacts.
- When an old and new path coexist, specify which is authoritative, how traffic is switched, how outcomes are compared and when the old path can be retired.

## 6. First structural work packet

The first packet is **not** a mass move. It is to build a trustworthy compiler-aware dependency and ownership map for the current 1,084-entry Android app subtree, reconcile it with manifest/Gradle/JNI/runtime entry points, identify cycles and overlapping implementations, then select the smallest boundary with the highest reduction in coupling. The first actual source migration should target a pure contract or deterministic algorithm with no Android dependency and have JVM contract tests plus a build gate.

Success is measured by fewer cross-domain dependencies, clearer production reachability, faster and more reliable targeted builds, stronger test isolation, fewer duplicate authorities and simpler safe replacement—not by fewer directories or a prettier tree.


## Wave 0 execution status — 2026-11-25

The current candidate CI workflow now captures the Gradle project graph plus `debugCompileClasspath`, `debugRuntimeClasspath` and `releaseRuntimeClasspath` dependency reports as the `friday-gradle-structure` artifact. The existing whole-app audit also retains a source inventory, heuristic source-to-test map and explicitly lexical cross-file trace artifact. This creates the first reproducible baseline for the build graph and source navigation.

**Limit:** Gradle dependency reports describe configured module/external dependency resolution; the lexical trace does not resolve Kotlin overloads, DI, reflection, generated code, coroutine callbacks, JNI or runtime reachability. Neither is yet a compiler-proven internal class/method dependency graph. Wave 0 remains open until those edges are mapped and reconciled with manifest, composition roots and runtime traces. No source relocation or module split is authorized merely by these reports.


## Repository hygiene baseline — 2026-11-28

This is a maintenance pass, not a structural migration. The objective is to remove accidental public artifacts and reduce operational noise without deleting unique engineering work or rewriting history.

### Completed on main

- Replaced the duplicated, instruction-heavy root README with a concise project entry point and navigation map. Detailed agent instructions remain in AGENTS.md; canonical delivery authority remains .ralph/FRIDAY_DELIVERY_PLAN.md.
- Normalized .gitignore, removed duplicate local.properties entry, and added ignore protection for re-uploaded owner/diagnostic archive filenames.
- Removed the unreferenced root log.zip diagnostic bundle.
- Removed the root New folder (1).zip owner-provided evidence archive from the current source tree after verifying the build workflow treats it as optional: the artifact upload runs only when hashFiles('New folder (1).zip') is non-empty. CI continues without the archive; owner-device evidence upload is skipped.
- Changed the build workflow's failure reporter to update the existing rolling incident issue body rather than post a new comment for every failed run.
- Updated issue #24 to identify it as a rolling CI incident record. Its 768 historical bot comments are retained; the reporter no longer creates one per failure.
- Recorded the additional archive cleanup in issue #76. Historical Git-object removal remains a separate task and is not claimed complete.

### Inventory and preservation decisions

- Current main tree contains 1,096 tracked files across 1,385 tree entries after the archive removals.
- The repository has 213 remote branches. Many are named audit/fix/feat/rebuild/work/friday-proposals and require ancestry and unique-work review before deletion.
- There are 18 open pull requests. All 18 heads were compared with main; each has unique commits and/or unmerged file changes. None was blindly closed or discarded. Some overlap in purpose, but their diffs are not empty.
- Eleven GitHub Actions workflows were present. They cover build/release, device diagnostics, candidate sandbox, formal checks, REA investigation, identity migration and live inspection. No workflow was deleted based on naming alone.
- The 55 MB Sherpa ONNX AAR and 24.6 MB ONNX runtime shared library are retained as vendored build/runtime dependencies, not treated as junk.
- .ralph/archive and vault/ remain documented historical/provenance archives. They are not active product instructions.
- A small root friday-fixes.zip remains pending content inspection; it is not referenced by source search, but was not deleted without verifying its contents.
- A personal PDF that was previously removed and later re-uploaded by the owner was left untouched.
- The root README now links to the repository structure policy and active engineering records.

### Remaining cleanup

1. Branch pruning: the current GitHub connector exposes branch listing and ref updates but no branch-delete action. Do not simulate deletion by force-moving refs. Use an authorized GitHub UI/API delete-ref capability, after preserving branches with unique unmerged work.
2. Review old closed PR branches and compare each to main before pruning; merged PRs are candidates, unmerged closed PRs may still contain unique work.
3. Inspect friday-fixes.zip before deciding whether to archive or delete it.
4. Decide whether a full historical Git-object purge is warranted for the owner archives previously removed from the current tree. Git history rewriting has broad side effects and requires coordinated clone/fork/PR handling; it is not part of routine cleanup.
5. Review the 43 docs as a navigation/duplication exercise only. Do not mass-delete or relocate architecture, acceptance, repair or provenance records based on age or file size.

### Safety rule

Do not delete branches, releases, tags, signing records, vendored runtime libraries, historical provenance or unique open-PR work merely to reduce counts. Every deletion must be tied to evidence that the item is unused, superseded or intentionally private.


### Workflow cleanup correction

- Removing `log.zip` initially triggered the log-analysis workflow because its push path filter matched deletion events. The first guard attempt placed `hashFiles()` at job level; GitHub rejected that workflow run (run `46101178939`). This was corrected by moving the `hashFiles('log.zip')` guard to the analysis step, where the expression is supported. Commit: `e07e78887453423db82c9e5e77ceaed809e5ce2d`. The workflow now checks out normally and skips the archive-analysis step when no uploaded archive exists. Verify the post-fix run before treating this cleanup item as fully validated.
