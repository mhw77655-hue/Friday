# Objective 1 — foundation verification record

**Recorded:** 2026-11-22  
**Scope:** repository, Android, formal and adversarial foundation  
**Canonical branch:** `main`  
**Owner declaration:** pending; this document is evidence, not sign-off  
**Current main at record update:** `cc88077658965e81c3fed8e891a742b59d5a035f`

## Corrected Objective 1 outcome — installed Builder seed

Objective 1 is the first operational Builder, not only a protected foundation or adversarial audit. The installed Friday must study the Android host, configure an app-owned development workspace without Termux, load the canonical plan offline, use local intelligence and available no-bill external Seats, construct a bounded candidate, run CI/build verification, preserve evidence and resume interrupted work. The owner supplies outcomes and protected approvals; Friday owns routine execution.

The canonical plan now defines this scope, and the same corrected plan is bundled in the APK asset so offline first launch does not bootstrap an obsolete Objective 1 definition.

**New implementation in PR #112:** the local resident model now has a bounded source-candidate executor; candidates are path/secret checked and published only to isolated GitHub PRs. A shared Friday Table log records Friday's local turn and an anonymous OVHcloud open-weight guest critique. The guest uses keyless HTTPS, is restricted to public/shareable repository context, and has no paid fallback. Candidate CI checks are polled and stored in the task ledger. No generated candidate is auto-merged or released.

This is still not acceptance evidence by itself. The current branch must pass latest-head CI; the physical phone must complete host study, resident-model admission, Termux-free execution and a real Builder cycle; CI/review evidence and update continuity must be verified. No physical-device pass is inferred from emulator results.

## Current status

The original foundation build and architecture gates have passed on earlier verified commits. The owner has now defined the final Objective 1 stage: an adversarial simulation campaign that deliberately attempts to break the retained foundation, tests the recovery paths, and records every failure and unresolved gap.

**Objective 1 is not complete yet.** The adversarial campaign is in progress. No green job, successful build, or heuristic audit report is being treated as proof that every source line or behaviour is safe.

## Verified evidence so far

- **Original Android foundation:** build, release-variant smoke assembly with a disposable CI key, launcher packaging check, JVM tests, emulator tests and whole-source audit passed on commit `a2685bd761f346da28426fdf022465f83dfbf124` in [run 44925158880](https://github.com/mhw77655-hue/Friday/actions/runs/44925158880).
- **Production recovery regression:** the earlier failing voice crash/restart test was corrected to exercise the single production `FridayEngine` supervisor instead of creating a second competing supervisor. The revised emulator run passed all three required jobs on commit `24c8d12d040501b4215e579c241154bd7e9fae4f`: [run 44931854463](https://github.com/mhw77655-hue/Friday/actions/runs/44931854463). Its instrumented report contained 7 tests, 0 failures, 0 errors and 0 skipped; `VoiceSubstrateTest.voiceHabitatIsolatesAndSurvivesCrash` passed.
- **Formal contracts:** Lean 4 formal workflow passed at `278b714c01d6bc1d1f27500abdbc7323fa0513a6` in [run 44914858181](https://github.com/mhw77655-hue/Friday/actions/runs/44914858181). The formal workspace, workflow and relevant contracts have not been replaced by the adversarial campaign.
- **Identity:** Gradle namespace and application ID are `ai.friday.assistant`; the manifest label is `Friday`.
- **Action-completion invariant:** `CapabilityExecutor` requires an independent postcondition verification. An invocation's own success flag is insufficient; missing or failed verification routes the step to failure.
- **Immune foundation:** production composition creates an `ImmuneSystem` used by the capability fabric. It classifies, contains and degrades failed capabilities, applies circuit breakers, and records recovery observations. Current limits are documented below; this is not universal self-healing.
- **Source audit:** 475 production Kotlin files were scanned. The heuristic audit produced 269 deviation signals and 403 observations; these are not 269 confirmed defects and are not an all-clear. See the [source-audit triage](OBJECTIVE_1_SOURCE_AUDIT_TRIAGE.md) and the [source-audit artifact/run](https://github.com/mhw77655-hue/Friday/actions/runs/44932395624).
- **Source/test map:** 115 filename matches, 216 lexical symbol-reference matches and 144 files with no candidate mapping. These are heuristic candidate mappings, not runtime coverage. Exact TSV is in the source-audit artifact.

## Permanent signing and self-update integration — implementation merged, validation in progress

- The repository's established `.github/workflows/release.yml` is the production release pipeline; a redundant second manual release workflow was removed. It restores the owner-configured PKCS#12 key from GitHub Secrets, builds the release APK, checks the APK signer against the pinned certificate fingerprint, and publishes the signed APK plus metadata and checksum.
- Production signing is no longer hypothetical: run [44992085464](https://github.com/mhw77655-hue/Friday/actions/runs/44992085464) completed successfully on commit `a604f38c2251fcce3685032f1976694fd7a36a08`. Its signing log reports SHA-256 `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8`, matching the owner's permanent certificate. It published release `friday-v1.0.477` with the APK, metadata and checksum assets.
- The existing updater performs release discovery, APK staging, checksum/package/version/certificate verification, and Android PackageInstaller handoff. WorkManager now schedules first-run and 24-hour discovery on unmetered networks; Android may defer constrained background work, and a force-stopped app will not run until relaunched.
- After PackageInstaller reports success, Friday preserves the expected release identity and verifies installed version code, version name, source revision and signing certificate on the next main-process launch.
- A real integration defect was found and fixed: the established release asset is named `app-release.apk` in GitHub's `name` field but carries the `friday.apk` display label. The updater now accepts the expected asset name or label, while still requiring HTTPS. An instrumentation test covers this behavior and rejects HTTP URLs.
- The newer app/update integration and asset-resolution test have been pushed to `main`. Fresh CI runs are in progress; the earlier attempt failed because ShellCheck rejected a redundant workflow's malformed line continuations. That redundant workflow has since been removed. No success is claimed until the final-head run completes.
- No physical-device update journey has been performed. A failed/rejected PackageInstaller transaction should leave the previous package intact; automatic rollback after a defective update has successfully installed is not implemented.
- Independent offline backup of the private keystore and its password remains unverified. The existing GitHub signing secret and local keystore have already produced a matching production-signed artifact; do not rotate or delete either.


## Host adaptation, trustworthiness and phone-originated evidence — implementation partly merged, host validation pending

- Branch `objective-1/github-device-reporting` adds a direct phone-to-repository report path. Friday Settings stores a fine-grained GitHub token encrypted with a non-exportable Android Keystore AES-GCM key. The token is never included in diagnostic payloads or logs.
- The token is restricted by design to this repository and GitHub Issues read/write. Friday submits an explicitly owner-triggered, bounded JSON diagnostic as a GitHub issue. The payload includes app/build identity, device capability states, sanitized crash frames and action-category events; it excludes conversation content, transcripts, typed text, screen text, notifications and raw exception messages.
- A GitHub Actions workflow accepts only owner-created report issues, validates schema, report ID/title match, size and credential-like content, then archives the JSON as a 14-day artifact and comments with the validation run.
- Report payloads are capped at 40 KiB and the action journal export is bounded to the most recent 50 events. Stable report IDs and issue-title lookup prevent duplicate creation when a delivery response is lost and the same payload is retried.
- The branch adds MockWebServer tests for API method/auth headers, issue creation, duplicate detection and permission failures; payload tests for schema, size and credential rejection; and an emulator instrumentation test for Android Keystore credential round-tripping.
- The branch also adds a one-tap Shizuku acceptance runner: verify remote UID, verify diagnostics UID matches, exercise Doze add/remove with read-back and restore the owner's original whitelist state, then launch Android Settings and verify its resumed activity. It records only sanitized pass/fail action events.
- **Correct acceptance interpretation:** the physical phone is not merely a place where the owner must manually prove that Friday happens to run. It is Friday's actual host environment. The acceptance goal is for Friday to discover the host's real capabilities and constraints, adapt its own operating profile safely, and produce verifiable evidence of that adaptation. The owner should not be turned into a manual QA technician.
- Friday must build a privacy-preserving host profile from supported Android APIs: OS/API level, app/runtime permissions and feature availability, CPU/ABI and available execution capacity where exposed, memory class and pressure signals, storage headroom, thermal state, battery/power-saver state, network/connectivity constraints, background-execution restrictions, and optional integrations such as Shizuku. Do not infer or collect restricted hardware identifiers.
- Friday must use that profile to select a safe operating mode: bound memory and concurrency, schedule/defer non-urgent work under Android power restrictions, reduce caches or model/context budgets under pressure, choose only actually available local/remote compute paths, disable or degrade unavailable capabilities, and explain material trade-offs. Re-evaluate on lifecycle, thermal, memory, battery, network and permission changes; do not assume the first-launch profile remains valid forever.
- Adaptation must never mean silently changing host settings, bypassing Android restrictions, escalating privileges, weakening security, or claiming a capability merely because a device model is expected to support it. Ask permission only at the point a feature genuinely needs it; if denied, continue with a safe alternative where possible.
- Trustworthiness is a cross-cutting acceptance invariant for every Friday operation: authenticated and authorized actions, least privilege, explicit consent for sensitive effects, independent postcondition verification, provenance-tagged evidence, honest uncertainty, privacy-minimized logs, bounded retries/idempotency, safe failure and recovery, and signed update integrity. Friday must distinguish observed facts, inferred estimates and unavailable signals.
- **Not yet proven:** the merged reporting path has not yet been exercised by Friday on the owner's physical Realme 9 Pro 5G; no device-originated host profile/adaptation report has been validated in GitHub, and Shizuku-specific optional capabilities have not been verified on that handset. CI/emulator success cannot substitute for this host-specific evidence. GitHub issue visibility follows repository visibility; the repository is public, so device reports submitted as issues are public. Any upload flow must disclose that before submission.
- The only owner-side interaction should be the minimum necessary to install/launch the production-signed build and grant or deny explicitly requested permissions. Friday should then perform its own host discovery, adaptation, checks and report generation. Any GitHub authorization must be entered directly into Friday, never sent to ChatGPT; the current issue-upload design needs a clear public-data disclosure and least-privilege credential handling.

## Adversarial work added; validation still pending

- [Adversarial validation plan](OBJECTIVE_1_ADVERSARIAL_VALIDATION_PLAN.md) now covers static/build integrity, source-to-test mapping, property/boundary tests, mutation, fuzzing, state-machine testing, concurrency, fault injection, Android lifecycle, native boundaries, security, soak testing and recovery-system attacks.
- CI now configures JaCoCo for JVM unit tests and Android instrumentation, requires non-empty coverage reports, and publishes the reports as artifacts. A successful report tied to the final tested commit is still required before quoting any coverage percentage.
- A deterministic mutation probe now weakens the HIGH-risk authorization gate and the independent postcondition-completion gate in a disposable CI checkout. Both corresponding test suites must independently fail on the mutants, then pass again after source restoration. The probe passed in the completed CI run 44991067759 on baseline commit `a604f38c2251fcce3685032f1976694fd7a36a08`: the deliberate authorization/recovery mutants were killed and the unmodified suites passed afterward. A fresh run on the newer permanent-signing/update integration commits is still required.
- A heuristic source-to-test inventory is generated and attached to the whole-source audit artifact. It must be reviewed against actual coverage and integration paths.
- The first adversarial emulator failure exposed a test-harness race: the test created a second voice supervisor while production already owned one. The production-supervisor test now passes. The initial failure and correction remain in the evidence history; they are not erased.

## Known recovery limitations — not claimed as solved

- `ImmuneMemory` is currently process-local/in-memory, not durable across process death.
- The capability invocation failure path contains and records failures but does not currently provide an automatic recovery function. Blindly retrying arbitrary actions would risk duplicate side effects; fallback/replan and idempotency need explicit policies.
- `RecoveryController` retry wiring currently covers TTS initialization; `VoiceOrganismHost` separately supervises the isolated voice process. These do not amount to universal system-wide healing.
- Universal recovery-progress notifications and evidence-based ETA estimates are not established.
- Autonomous source-code repair/promotion is not implemented. Future repair must be isolated, regression/security tested, authorized where required, delivered through trusted signing, independently health-checked and rollback-capable.
- Native C/C++ and Python require coverage/sanitizer evidence beyond Kotlin JaCoCo. Emulator evidence is not physical-device proof.
- The vision audit is heuristic and has zero model calls; every deviation must be manually dispositioned. Do not mass-edit legitimate status enums, authorization boundaries, constructor injection or safety bounds to reduce counts.

## Explicitly not claimed

- No physical installation, permission grant, or acceptance test has been performed on the owner's Realme 9 Pro 5G.
- No local model is installed on the owner's phone.
- A permanent production-signed APK and GitHub Release have been published as `friday-v1.0.477` and its APK signer matches the owner's recorded permanent fingerprint. The newer update-integration build and physical-device update remain unverified. Independent offline keystore/password backup is still unverified.
- Same-key in-place upgrade and post-update continuity health checks are implemented but not yet proven on the owner's physical phone. Last-known-good rollback after a successfully installed defective update is not implemented.
- Full offline end-to-end cognition, persistent developmental learning, and universal phone control are not accepted by this foundation record.
- Voice-control implementation remains in Objective B's existing voice phases; it is not being pulled into Objective 1.

## Current validation head

- [Current main commit](https://github.com/mhw77655-hue/Friday/commit/cc88077658965e81c3fed8e891a742b59d5a035f)
- [Current CI run for the adversarial test infrastructure](https://github.com/mhw77655-hue/Friday/actions/runs/44932928715)

Objective 1 remains open until the adversarial campaign has completed, critical findings have been fixed or explicitly dispositioned, the final coverage/mutation evidence is published, all required CI lanes pass on the final commit, and the owner chooses to declare it complete.


## Reporting integration merge update — 2026-11-22

- PR #110 is merged into `main` at `87d8dace06d5daa2dc940df216bebb32a13aea71`.
- The exact PR head `84689627b554b37f846a6785b0d3f77382bd7505` passed CI run [45029884401](https://github.com/mhw77655-hue/Friday/actions/runs/45029884401): `debug-apk`, `Disposable Android phone twin (instrumented)`, and `Whole-repository source inventory and Kotlin vision audit` all concluded `success`; `report-failure` was skipped because no failure occurred.
- This is PR-head CI evidence. It is not physical-device evidence and does not establish that a release APK containing the merge has been published. A separate post-merge production release verification remains required.
- Android requires matching application ID and signing certificate (or valid proof-of-rotation) for updates, and may require user approval. See [Android app update requirements](https://developer.android.com/google/play/app-updates).


## Post-merge production release evidence — 2026-11-22

- Main commit: `8cc07c8feb4726e8028f169d4f6b96bcac259e19`.
- Main CI run [45031221224](https://github.com/mhw77655-hue/Friday/actions/runs/45031221224) completed successfully. Its three substantive jobs passed: `debug-apk`, `Disposable Android phone twin (instrumented)`, and `Whole-repository source inventory and Kotlin vision audit`. The failure-report job was skipped because there was no failed job.
- Production signing workflow [45032924556](https://github.com/mhw77655-hue/Friday/actions/runs/45032924556) completed successfully. It checked all five permanent signing secrets, decoded the configured PKCS#12 keystore, built the release APK, verified its signature against the pinned certificate, generated metadata/checksum, and published the release.
- Published release: [Friday 1.0.492](https://github.com/mhw77655-hue/Friday/releases/tag/friday-v1.0.492), version code 1492, target commit `8cc07c8feb4726e8028f169d4f6b96bcac259e19`.
- The release job log records APK v2 signature verification and signer SHA-256 `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8`, matching the owner's permanent signing certificate. The uploaded APK asset is `app-release.apk` (release display label `friday.apk`).
- This closes the post-merge build and permanent-signing publication gate. It does **not** prove in-place installation over the owner's current app, a physical update journey, phone-side report submission, or the full adversarial campaign.

## Execution retrospective — process corrections to apply immediately

A companion record, [Objective 1 execution retrospective](OBJECTIVE_1_EXECUTION_RETROSPECTIVE.md), captures the workflow failures and process changes observed across the project conversation. Apply it to all remaining Objective 1 work and later objectives: inspect repository state before asking the owner to run commands; use GitHub Actions for builds/reports; distinguish debug artifacts from permanent-key production releases; verify exact commit, job conclusions, APK certificate and published assets before claiming success; never ask for secrets in chat; and ask for physical-device actions only when a real device-only gate is reached.


## Clarification: physical-device acceptance means Friday adapts to its host

The owner has clarified that the Realme handset is not just a target on which a human must perform a pass/fail checklist. It is Friday's host. The acceptance question is whether Friday can understand the environment it actually inhabits and adapt its execution, resource use and available capabilities to that environment while remaining trustworthy.

This is a product capability and a trust boundary, not a ceremonial installation test.

Required acceptance behaviour:
1. **Discover:** collect only supported, relevant, non-sensitive host signals and label each as observed, inferred or unavailable.
2. **Model:** create a versioned host-capability profile with confidence and freshness; do not turn device marketing specifications into runtime facts.
3. **Adapt:** choose resource budgets, concurrency, cache sizes, background schedules, compute/model paths and feature availability from current signals. Preserve core functionality where possible through graceful degradation.
4. **Reassess:** respond to memory pressure, thermal changes, low battery, power saver, network loss, permission revocation, process recreation and OS scheduling restrictions.
5. **Explain:** tell the owner what changed, why, what remains unavailable and what evidence supports the claim.
6. **Verify:** measure the postconditions of the adaptation (for example bounded memory/concurrency, correct feature gating, deferred background work, no permission bypass, no private data leakage), rather than merely recording that an adaptation method returned success.
7. **Report:** generate a sanitized, reproducible host-adaptation report tied to app version, source revision, Android API level, profile version, test cases and outcomes. Never include conversation content, typed text, secrets, raw hardware identifiers or unrestricted device inventory.

The host-adaptation acceptance suite must include deterministic emulator profiles (low-memory, constrained storage, thermal pressure, battery saver/Doze, offline/poor network, denied/revoked permissions, unsupported optional services) plus a physical run on the owner's actual handset to validate OEM-specific behaviour. The physical run is not expected to prove every hypothetical device state; it validates that runtime discovery and adaptation work on the real host and that reported facts match observations.

**Trustworthiness bar:** Friday must not silently overreach to make itself work. It must operate within Android's permission and background-execution model, expose uncertainty, fail safely, preserve user control, verify consequential actions independently and keep an auditable record. Android's own guidance explicitly calls for lazy permission requests, clear rationales and graceful degradation when permissions are denied; Android also imposes device/app-state-dependent background resource limits. See [Android adaptive-app quality guidelines](https://developer.android.com/develop/adaptive-apps/quality-guidelines/core-app-quality?hl=en) and [Android power-management resource limits](https://developer.android.com/topic/performance/power/power-details).

This host-adaptation contract is now part of Objective 1's acceptance meaning. A generic "APK installed" result or a manually completed Shizuku checklist is insufficient.


## Post-install mission continuity — owner requirement recorded 2026-11-22

The installed Friday's continuing responsibility is to carry the canonical objectives plan forward herself, using the available AI pool and other development resources. Installation is the beginning of that operation, not a handoff of project management back to the owner. The owner remains the architect, collaborator and authority for protected decisions; Friday must make the work understandable and directly collaborative, not opaque or owner-excluding.

The active plan now defines the complete operating contract in [FRIDAY_DELIVERY_PLAN.md](../.ralph/FRIDAY_DELIVERY_PLAN.md), and the host execution/control-plane contract in [FRIDAY_HOST_ADAPTATION_CONTROL_PLANE.md](FRIDAY_HOST_ADAPTATION_CONTROL_PLANE.md).

**Objective 1 foundation requirement:** the seed must preserve identity and plan continuity; provide durable, resumable task/evidence state; expose a protected path for future AI-worker coordination and independent verification; operate safely within Android background/resource limits; and support owner inspection, pause, redirection and protected approvals. Objective 1 does not claim to deliver the full autonomous Builder or mature multi-provider AI-pool orchestration; those capabilities continue through their owning objectives, especially F and G.

**Not yet proven in the installed app:** durable autonomous objectives execution after process death/reboot, authenticated AI-pool dispatch and result ingestion, independent reviewer workflow, offline queue/resume, and owner-facing inspection/control of the development workspace. These remain implementation and acceptance work, not completed features. The app must never claim to continue 24/7 without Android scheduling guarantees; it should persist work and resume under permitted OS conditions.


### Latest Builder compile correction

The original PR workflow #2838 checked merge revision `4b33b4f8` and failed with two Context/String argument mismatches and one malformed literal-newline syntax error in `ObjectiveBuilderCandidateExecutor.kt`.

The source was corrected in commit `4da78949f8afa630a2ea8000b16c0aade5919c77`; the Android context property was then explicitly renamed to `androidContext` in commit `ddb685cfc5a024944396a52ae63c4fc59d5be78c` to prevent recurrence. The exact current PR head is `ddb685cfc5a024944396a52ae63c4fc59d5be78c`.

**Verification remains blocked:** GitHub Actions has no run for the current head. The connected GitHub actions surface can inspect and rerun existing jobs but does not expose a workflow-dispatch operation. Re-running #2838 would test the old failed merge revision, not this corrected head. No build or test pass is claimed.
