# Objective 1 — foundation verification record

**Recorded:** 2026-11-21  
**Scope:** repository, Android, formal and adversarial foundation  
**Canonical branch:** `main`  
**Owner declaration:** pending; this document is evidence, not sign-off  
**Current main at record update:** `cc88077658965e81c3fed8e891a742b59d5a035f`

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

## Permanent signing and self-update integration — implementation merged, validation pending

- Permanent signing workflow merged to `main` in commit `34a15591c372bb39fe124457fcbd8ff796713864` (PR #109). It restores the existing owner keystore from GitHub Secrets, checks the certificate fingerprint, builds the release APK, and verifies the resulting APK signer against the expected permanent fingerprint.
- The workflow generates `friday.apk` and `friday-release-metadata.json`, matching the existing in-app update client's asset contract. Public GitHub Release publication is an explicit manual opt-in and defaults to disabled.
- The existing updater already performs release discovery, APK staging, checksum/package/version/certificate verification, and Android PackageInstaller handoff. The application now schedules first-run and 24-hour update discovery through WorkManager on unmetered networks; this is subject to Android background scheduling and force-stop policy.
- After PackageInstaller reports success, Friday preserves the expected release identity and checks installed version code, version name, source revision and signing certificate on the next main-process launch.
- These additions have not yet passed a CI run on the merged source revision, and no permanent signed artifact or public release has been produced. No physical-device update test has been performed.
- Automatic rollback after a defective update that was successfully installed is not implemented. A rejected/failed PackageInstaller transaction should leave the prior installed package intact; this is not equivalent to post-install rollback.

## Adversarial work added; validation still pending

- [Adversarial validation plan](OBJECTIVE_1_ADVERSARIAL_VALIDATION_PLAN.md) now covers static/build integrity, source-to-test mapping, property/boundary tests, mutation, fuzzing, state-machine testing, concurrency, fault injection, Android lifecycle, native boundaries, security, soak testing and recovery-system attacks.
- CI now configures JaCoCo for JVM unit tests and Android instrumentation, requires non-empty coverage reports, and publishes the reports as artifacts. A successful report tied to the final tested commit is still required before quoting any coverage percentage.
- A deterministic mutation probe now weakens the HIGH-risk authorization gate and the independent postcondition-completion gate in a disposable CI checkout. Both corresponding test suites must independently fail on the mutants, then pass again after source restoration. This probe has not yet passed in a completed CI run.
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
- No permanent production-signed APK or GitHub Release has been published. The owner-created PKCS#12 identity and five GitHub Actions secrets are now configured; the permanent release workflow is merged but has not yet been executed. Independent offline keystore/password backup is still unverified.
- Same-key in-place upgrade, post-update continuity health checks, and last-known-good rollback are not yet proven as production guarantees.
- Full offline end-to-end cognition, persistent developmental learning, and universal phone control are not accepted by this foundation record.
- Voice-control implementation remains in Objective B's existing voice phases; it is not being pulled into Objective 1.

## Current validation head

- [Current main commit](https://github.com/mhw77655-hue/Friday/commit/cc88077658965e81c3fed8e891a742b59d5a035f)
- [Current CI run for the adversarial test infrastructure](https://github.com/mhw77655-hue/Friday/actions/runs/44932928715)

Objective 1 remains open until the adversarial campaign has completed, critical findings have been fixed or explicitly dispositioned, the final coverage/mutation evidence is published, all required CI lanes pass on the final commit, and the owner chooses to declare it complete.
