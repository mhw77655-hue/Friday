# Objective 1 — foundation verification record

**Recorded:** 2026-11-22  
**Scope:** repository, Android, formal and adversarial foundation  
**Canonical branch:** `main`  
**Owner declaration:** pending; this document is evidence, not sign-off  
**Current main at record update:** `cc88077658965e81c3fed8e891a742b59d5a035f`

## Corrected Objective 1 outcome — installed Builder, not just foundation

Objective 1 is **not complete** until the installed Friday can bootstrap and operate her own development environment on the owner's Android host, read and continue the canonical plan, coordinate an authorized AI worker pool, create and validate a real bounded change, preserve durable evidence, and resume after interruption. The owner gives outcomes; Friday determines and executes the engineering route within granted authority.

**Termux is explicitly not a production dependency.** The owner intends to uninstall Termux after installing Friday. Therefore any implementation whose only working AI-worker or source-development route calls Termux RUN_COMMAND is not acceptable as Objective 1 completion. Termux may remain a research donor or optional development aid, but the accepted runtime must be provisioned and owned by Friday inside her app-managed environment.

The current source evidence shows why this correction matters:
- `ObjectiveExecutionWorker` reconciles/decomposes queued parent tasks; it does not yet implement, build, test, integrate or promote source changes.
- `TermuxAiCliWorkerPool` is the concrete subscription-worker adapter in the current PR #113 branch. It depends on Termux's RUN_COMMAND bridge and Termux home directory.
- The `APP_MANAGED` environment kind exists as an enum value, but no production app-managed execution adapter was found in the inspected source.
- The plan itself previously deferred the complete autonomous Builder to Objective F. That conflicts with the owner's clarified Objective 1 outcome and is corrected in the canonical plan.
- The installed app must not depend on this ChatGPT conversation, a manual Termux session, or the owner relaying prompts/responses. External subscription workers may guide Friday only through supported authentication and execution paths.

**Required implementation proof:** a Friday-owned bootstrap/runtime manager; app-private workspace and toolchain lifecycle; host-aware resource admission; provider authentication/onboarding through supported flows; durable objective/task/checkpoint/evidence ledger; isolated candidate construction; test/build execution; independent review and postcondition verification; trusted repository/update integration; process-death/reboot recovery; and an actual no-Termux acceptance test. If a CLI cannot run on Android, Friday must prove an alternative route rather than silently assume compatibility.

## Current status

The original foundation build and architecture gates have passed on earlier verified commits. The owner has clarified that Objective 1 is the installed Builder seed: Friday must configure her own development environment on the actual phone, continue the canonical plan, use an AI pool for guidance, and complete a real self-development cycle without Termux or routine owner project management. The adversarial campaign remains necessary, but it is not the definition of Objective 1 or a substitute for the working Builder.

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

**Objective 1 foundation requirement:** the seed must preserve identity and plan continuity; provide durable, resumable task/evidence state; expose a protected path for future AI-worker coordination and independent verification; operate safely within Android background/resource limits; and support owner inspection, pause, redirection and protected approvals. Objective 1 does require the first bounded, end-to-end Builder cycle and a working interchangeable Seat contract. It does not require unlimited invention, autonomous promotion of protected production code, or a mature multi-provider frontier-model workforce; those expand through later objectives. Objective F must not be used to defer the first working Builder loop.

**Not yet proven in the installed app:** durable autonomous objectives execution after process death/reboot, authenticated AI-pool dispatch and result ingestion, independent reviewer workflow, offline queue/resume, and owner-facing inspection/control of the development workspace. These remain implementation and acceptance work, not completed features. The app must never claim to continue 24/7 without Android scheduling guarantees; it should persist work and resume under permitted OS conditions.


## Objective 1 acceptance expansion — 2026-11-23

The canonical requirements are in [.ralph/FRIDAY_DELIVERY_PLAN.md](../.ralph/FRIDAY_DELIVERY_PLAN.md), section “Objective 1 — installed developmental seed expansion”. This record makes the six operational gates testable:

1. **Host bootstrap:** a real versioned profile separates observed/inferred/unavailable facts, includes resource and Android execution constraints, selects a viable operating mode and fallback, applies only app-owned configuration, verifies postconditions and re-evaluates after host changes.
2. **Builder cycle:** gap identification → repository/research inspection → design challenge → isolated candidate → verified local or disclosed remote build/test → independent review → baseline comparison → provenance/evidence → safe candidate publication or rejection. A task decomposition, generated diff or open PR alone fails.
3. **AI Table:** one Friday-owned persistent transcript; common Seat input/output/presence/provenance contract; local conductor; role cards; per-seat privacy-filtered delta digests; bounded utterance/PASS; asynchronous guest arrival; blind critique and preserved dissent. Guest content is data, never authority or executable instruction.
4. **App-owned workspace:** durable workspace, tool/dependency provenance, candidate isolation, task/checkpoint journal, build/test orchestration, repository synchronization, cancellation, evidence and rollback references. Termux is not a production dependency.
5. **Trust:** least privilege, secret isolation, licence/provenance checks, bounded sandbox/resources, independent reviewer/verifier, immutable evidence and known-good recovery. No worker can self-authorize, access signing keys or promote its own changes.
6. **Installed autonomy:** load the canonical plan, choose a dependency-ready task, use local reasoning and a permitted zero-charge external Seat where available, construct/test a bounded candidate, preserve exact evidence and continue with the next safe task. Resume idempotently after force-stop, reboot, network/worker/model failure and interrupted work.

### Zero-bill and provider truth

Paid APIs, paid plans, upgrades and paid fallbacks are off by default. A Seat may be resident/local, anonymous public inference where terms permit, a genuinely free tier (which may require an account/key), or a clearly labelled manual relay. A provider's free tier is not necessarily anonymous or unlimited. An open-weight model is not the proprietary provider's consumer intelligence. Friday records actual model/provider identity, quota/cost state, terms/provenance and whether a response is a stand-in. Rate limits or outages mark a Seat away; they never trigger paid fallback. No consumer-app UI automation, browser-session extraction or private-token replay.

### First-install mission and fault matrix

The installed production app must complete one reproducible cycle from inside Friday: host discovery and adaptation; canonical-plan load; dependency-ready task selection; local and (where available) zero-charge guest guidance; isolated candidate construction; build/test; independent critique and postcondition verification; exact evidence/provenance capture; and selection/queueing of the next safe task.

Run recovery tests for force-stop/process death, reboot, offline transition, external Seat outage/rate limit, local model absence, low memory/storage, thermal/battery pressure, permission denial/revocation and interrupted builds. Require idempotent resume from the last verified checkpoint and prove that no unsafe side effect is duplicated. Separate JVM, emulator, CI and physical-handset evidence. The actual phone must demonstrate its own host adaptation and first post-install cycle.

### Current implementation/evidence status

This document records requirements, not implementation success. PR #118 is an unmerged candidate and must not be described as part of the installed mainline until its exact head passes the repository's required checks and is integrated. The earlier Objective 1 PRs #115 and #116 are design/acceptance specifications, not runtime proof. No new physical-device acceptance is implied by this plan update. Update this section only with exact commit/run/artifact/device evidence as each gate is completed.

### Automatic evidence reporting

After one-time GitHub authorization and a separate explicit public-reporting consent, Friday—not the owner—must prepare and submit sanitized host-bootstrap and Builder milestone evidence to GitHub. Reporting remains disabled by default and revocable. Public issues/artifacts may contain app/build identity, host capability facts, task/candidate IDs, test/checkpoint outcomes, sanitized crash frames and action categories only. They must exclude all conversation, speech, typed content, screen text, notifications, credentials, signing material and raw personal files. Without consent or authorization, evidence stays local and queued. Uploads use stable report IDs and idempotent retries.


## Current delivery and on-device acceptance update — 2026-11-23

### Production seed release

- Main commit: `36c87d67e50cb39751bbe7415cc81fae86e973dc`.
- Main build workflow [45195342365](https://github.com/mhw77655-hue/Friday/actions/runs/45195342365) completed successfully.
- Permanent-key signed release workflow [45198206272](https://github.com/mhw77655-hue/Friday/actions/runs/45198206272) completed successfully on that exact main commit.
- Published production release: [Friday 1.0.506](https://github.com/mhw77655-hue/Friday/releases/tag/friday-v1.0.506), target commit `36c87d67e50cb39751bbe7415cc81fae86e973dc`.
- APK asset: `app-release.apk`, 130,819,706 bytes; GitHub asset SHA-256: `2a1533feb13b581eb1cb8f54f98e59bda3d78033029358735d453b1d998da66b`.
- Release workflow built the signed production APK, verified its signature against the pinned permanent certificate, generated metadata/checksum and published the release. This supersedes the stale v1.0.492 release reference above.
- This establishes a distributable, permanently signed Objective 1 seed. It does not establish that the owner has installed it or that same-key upgrade continuity has passed on the physical phone.

### Device-side acceptance automation candidate

- Branch `objective1-device-self-acceptance` adds a fail-closed `HostAcceptanceEvidence` evaluator and tests. It records seven concrete first-launch probes: app-private workspace directories, actual write/read/delete, observed host profile, bounded resource policy, honest local-inference admission, canonical plan asset presence and app identity.
- `HostBootstrapWorker` now executes those probes on the Android host and includes the results in `host-bootstrap.json`. The report scope explicitly says `FOUNDATION_PROBES_ONLY_NOT_FULL_OBJECTIVE_ACCEPTANCE`; it cannot claim the full Builder mission, lifecycle recovery, multiple frontier providers or protected production promotion.
- The canonical plan and APK-bundled plan are updated together and must remain byte-identical.
- This is a candidate change only. It is not merged or included in Friday 1.0.506 until its exact-head CI passes and it is integrated into main, followed by a new signed release.
- The intended owner workflow is install and launch Friday, then complete only the one-time GitHub authorization and separate public-report consent if automatic public evidence submission is desired. Friday performs the host probes, preserves the evidence and submits it itself. Without reporting consent, evidence remains local and queued. No Termux, ADB session, manual log copy or hand-built report is required.

### Remaining Objective 1 gates

- Exact-head CI and production signed release for the device-side acceptance candidate.
- Actual installed first-launch evidence and one complete bounded Builder cycle on the phone.
- Durable resume proof after process death/reboot, including idempotency and no duplicate external side effects.
- Complete automated Builder repair/retry and verified local/remote build/test route.
- Full multi-seat registry, per-seat delta history and truthful zero-bill provider/cost accounting.
- Same-key in-place update continuity and last-known-good rollback evidence.
- Adversarial protected-floor and relevant physical-host checks.

**Status remains OPEN.** The signed release is available, and the device-side harness is being strengthened so physical-host evidence is generated by Friday rather than delegated to the owner. No physical run is claimed before the app has actually run on the handset.


## Direct ChatGPT plan Seat implementation candidate — 2026-11-23

PR #122 adds an in-app OpenAI Sign in with ChatGPT path for the Friday Table:

- Official open-source dynamic registration and OAuth Authorization Code + PKCE using the system browser and loopback callback.
- ID-token signature/issuer/audience/nonce/expiry verification against OpenAI JWKS; token response must grant `chatgpt.tokens.use.direct`.
- Android Keystore AES-GCM encrypted account/token storage and refresh.
- Account-specific model discovery and streamed Responses API inference with `store=false`, `stream=true`, and success only on `response.completed`.
- Private/sensitive data blocked before token access or network calls; background Builder use is separately disabled by default and requires explicit owner consent.
- ChatGPT joins the Table as an advisory Seat alongside OVH; the local Builder remains the authority and retains all external critiques.

The PR includes instrumentation coverage for credential encryption, consent defaults, privacy gating, mocked Responses API streaming, rate-limit behavior and RS256 ID-token verification. Actual account eligibility, sign-in and real inference still require an in-app owner-authorized session; no account is preconnected and no API billing key is used.
