# Objective 1 — foundation verification record

**Recorded:** 2026-11-22  
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

## Permanent signing and self-update integration — implementation merged, validation in progress

- The repository's established `.github/workflows/release.yml` is the production release pipeline; a redundant second manual release workflow was removed. It restores the owner-configured PKCS#12 key from GitHub Secrets, builds the release APK, checks the APK signer against the pinned certificate fingerprint, and publishes the signed APK plus metadata and checksum.
- Production signing is no longer hypothetical: run [44992085464](https://github.com/mhw77655-hue/Friday/actions/runs/44992085464) completed successfully on commit `a604f38c2251fcce3685032f1976694fd7a36a08`. Its signing log reports SHA-256 `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8`, matching the owner's permanent certificate. It published release `friday-v1.0.477` with the APK, metadata and checksum assets.
- The existing updater performs release discovery, APK staging, checksum/package/version/certificate verification, and Android PackageInstaller handoff. WorkManager now schedules first-run and 24-hour discovery on unmetered networks; Android may defer constrained background work, and a force-stopped app will not run until relaunched.
- After PackageInstaller reports success, Friday preserves the expected release identity and verifies installed version code, version name, source revision and signing certificate on the next main-process launch.
- A real integration defect was found and fixed: the established release asset is named `app-release.apk` in GitHub's `name` field but carries the `friday.apk` display label. The updater now accepts the expected asset name or label, while still requiring HTTPS. An instrumentation test covers this behavior and rejects HTTP URLs.
- The newer app/update integration and asset-resolution test have been pushed to `main`. Fresh CI runs are in progress; the earlier attempt failed because ShellCheck rejected a redundant workflow's malformed line continuations. That redundant workflow has since been removed. No success is claimed until the final-head run completes.
- No physical-device update journey has been performed. A failed/rejected PackageInstaller transaction should leave the previous package intact; automatic rollback after a defective update has successfully installed is not implemented.
- Independent offline backup of the private keystore and its password remains unverified. The existing GitHub signing secret and local keystore have already produced a matching production-signed artifact; do not rotate or delete either.


## Phone-to-GitHub reporting and physical Shizuku acceptance — implementation merged, physical proof pending

- Branch `objective-1/github-device-reporting` adds a direct phone-to-repository report path. Friday Settings stores a fine-grained GitHub token encrypted with a non-exportable Android Keystore AES-GCM key. The token is never included in diagnostic payloads or logs.
- The token is restricted by design to this repository and GitHub Issues read/write. Friday submits an explicitly owner-triggered, bounded JSON diagnostic as a GitHub issue. The payload includes app/build identity, device capability states, sanitized crash frames and action-category events; it excludes conversation content, transcripts, typed text, screen text, notifications and raw exception messages.
- A GitHub Actions workflow accepts only owner-created report issues, validates schema, report ID/title match, size and credential-like content, then archives the JSON as a 14-day artifact and comments with the validation run.
- Report payloads are capped at 40 KiB and the action journal export is bounded to the most recent 50 events. Stable report IDs and issue-title lookup prevent duplicate creation when a delivery response is lost and the same payload is retried.
- The branch adds MockWebServer tests for API method/auth headers, issue creation, duplicate detection and permission failures; payload tests for schema, size and credential rejection; and an emulator instrumentation test for Android Keystore credential round-tripping.
- The branch also adds a one-tap Shizuku acceptance runner: verify remote UID, verify diagnostics UID matches, exercise Doze add/remove with read-back and restore the owner's original whitelist state, then launch Android Settings and verify its resumed activity. It records only sanitized pass/fail action events.
- **Not yet proven:** the merged implementation has not yet been installed on the owner's physical phone. No GitHub token has been configured in Friday, no device report has been uploaded, and the Shizuku runner has not been exercised on the Realme handset. GitHub issue visibility follows repository visibility; the repository is public, so device reports submitted as issues are public. The owner must review the payload and accept that visibility before uploading.
- The one-time owner action after a production-signed build containing this merge is available: create a fine-grained token for only this repository with Issues read/write, paste it directly into Friday Settings, save, verify access, run the Shizuku acceptance checks, then tap Upload device report. The token must never be sent to ChatGPT.

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
