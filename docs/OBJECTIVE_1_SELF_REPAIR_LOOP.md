# Objective 1 — Installed-host diagnostics and verified update foundation

> **Scope boundary — this is not Friday's complete self-adaptation architecture.** The canonical, system-wide requirement is defined in `.ralph/FRIDAY_DELIVERY_PLAN.md` under "Cross-objective resilience principle — Friday's adaptive immune system". This document specifies only the initial installed-host diagnostics, cloud-assisted repair/update path and privileged installer acceptance slice relevant to the seed. It must not turn universal self-healing, unknown-problem solving, or all future capability acquisition into an Objective 1 completion gate. Those capabilities evolve across the entire delivery plan and all objectives.

## Status and intent

This document extends the Objective 1 acceptance contract. First-launch host probes and consent-gated evidence reporting are only the observation/reporting foundation. They are not, by themselves, self-repair and must not be represented as full acceptance.

The seed-level requirement is that an installed Friday can observe the actual Android host, detect and preserve actionable faults, report evidence through the authorized cloud path, receive a verified compatible update, apply it through an available supported installer, verify recovery, and resume its work. This is one initial operational slice of the broader system-wide adaptation architecture.

## Initial installed-host update cycle

1. **Observe the host.** Build a versioned, privacy-minimized host capability profile from information Android exposes to an ordinary app: Android/API version, ABI, available memory/storage, battery/thermal constraints, granted permissions, runtime capabilities, app version and signing identity, and measured feature probes. Record provenance, timestamp, confidence, and unavailable/withheld fields. Never infer a capability merely because a device model usually has it.
2. **Detect and preserve.** Monitor observable crashes, ANRs, worker failures, failed capability probes, resource pressure, update failures and objective execution failures. Persist a bounded, redacted diagnostic bundle locally before network delivery. Keep a durable incident ID and event timeline so interrupted uploads can resume.
3. **Diagnose.** Run deterministic local checks first. Classify incidents as app defect, host incompatibility, resource exhaustion, permission/user-action boundary, network/service failure or unknown. Attach evidence and confidence. Unknown cases remain unresolved; Friday must not invent a root cause.
4. **Report to cloud.** Upload only minimum necessary sanitized evidence through the existing consent-gated reporting path. Support resumable, idempotent submissions and explicit acknowledgements. Respect revocation, retention limits, secret redaction and user-data boundaries. If reporting is not authorized or connectivity is unavailable, queue locally and continue safe local operation.
5. **Cloud-assisted repair path.** Correlate the incident with exact app commit/build, signer, Android/API/ABI profile, capability results and prior incidents. Produce a reproducible diagnosis and proposed remediation. Where code changes are needed, use a traceable repository change/PR, static analysis, unit tests, Android instrumentation/emulator tests, dependency/security checks and a signed candidate using the permanent release signer. A generated APK is not a repair until provenance and test evidence pass policy.
6. **Host-compatible release selection.** Prefer a universal build when it safely supports the host; otherwise select a narrowly scoped variant or capability/configuration policy. The release manifest binds version, package ID, signer fingerprint, artifact digest, supported API/ABI/capabilities, source commit, test results and recovery target.
7. **Secure delivery and installer selection.** Verify authenticated transport, trusted release provenance, package identity, permanent signing certificate, digest, version/rollback policy and host compatibility before installation. Select the strongest currently available authorized backend: root when actually available; Shizuku-compatible privileged UserService when running and authorized; otherwise native Android PackageInstaller. Record the chosen backend and why alternatives were unavailable.
8. **Recovery and rollback.** Keep the currently working installation and user data safe. Use a staged update state machine (downloaded → verified → install-requested → installed → health-checking → committed, or failed/recovery-required), durable across process death and reboot. If post-update checks fail, stop rollout, preserve diagnostics and recover through a previously verified compatible release or supported recovery path. Do not assume Android permits APK downgrade in place; account for package-manager rules and preserve signing identity.
9. **Post-update proof.** After install/restart, rerun the failed probe and relevant regression checks on the actual host, verify expected version/signer, and report the outcome. Cloud CI or emulator success is not proof that the physical host recovered. Close an incident only when the original failure is demonstrably resolved or explicitly classified as requiring owner action.
10. **Resume.** Resume the interrupted work queue only after the health gate passes. Use bounded retry/backoff and circuit breakers; do not loop endlessly on an unfixable host limitation.

## Required installer architecture

Background installation is a first-class Friday capability, not merely a link that opens another installer. Implement a backend abstraction and capability-driven selection:

1. **Shizuku-compatible backend (primary non-root privileged path).** Integrate the official Shizuku API/provider and supported UserService mechanism. Shizuku-Next advertises API/package compatibility with Shizuku, so interoperate through the documented API rather than hard-coding fork-specific internals. At runtime detect binder/server liveness, API level, authorization and actual server UID/privilege. Request per-app authorization through the normal consent flow; never silently grant Friday its own privilege.
2. **Privileged install operation.** Execute installation through a Shizuku UserService running with the server's actual shell (UID 2000) or root (UID 0) identity. Support single APK and split APK sessions/operations where available. Capture exit status and bounded output; command dispatch is not proof of success. Verify PackageManager reports the expected package version and signing certificate afterwards.
3. **Root backend.** Detect and use explicitly available, authorized root as a separate backend. Do not infer root from Shizuku availability: Shizuku may run as shell or root, and the UID must be checked.
4. **Native Android backend.** Retain PackageInstaller as the unprivileged fallback, including REQUEST_INSTALL_PACKAGES eligibility and STATUS_PENDING_USER_ACTION handling. It is not the primary strategy when a valid privileged backend is available.
5. **Backend orchestration.** Prefer the strongest available authorized backend (root, then Shizuku shell, then native PackageInstaller), with policy configurable and auditable. Probe immediately before use because Shizuku can stop, lose authorization or restart with different privileges. On binder death/disconnection, persist state and retry within bounded limits, then fall back safely.
6. **Service lifecycle and setup.** Integrate with Shizuku-Next's documented startup/reliability features where available, including optional start-on-boot, watchdog restart, automatic recovery and supported wireless-debugging pairing assistance. Detect whether those options are enabled and whether the server actually returned; never assume enabled settings guarantee startup on every OEM/device or after every reboot. Guide one-time pairing/authorization and request documented permission, then monitor binder liveness. If Android/OEM policy or revoked authorization prevents unattended startup, report the exact prerequisite and queue the verified update. Distinguish one-time privilege setup and exceptional platform-required startup from ordinary background update operation.
7. **Background execution.** Run downloads, verification, installation and post-install checks through a durable foreground/WorkManager-coordinated state machine with notifications and process-death recovery. Work must be resumable and idempotent.
8. **Trust gates remain mandatory.** Before invoking any backend, verify cloud-signed release metadata, artifact digest, package name, permanent signing certificate, version policy, compatibility profile and provenance. Privilege must never weaken artifact verification. Never accept a remote arbitrary shell command; cloud may request only a typed install of a fully verified release artifact.
9. **Observable outcomes.** Record selected backend, server UID/privilege class, authorization state (not secrets), operation result, package-manager result, before/after version and signer, health-check result and fallback reason in the sanitized update report.

## Security, privacy and trust invariants

- Never upload credentials, private keys, signing material, access tokens, raw personal files or unrestricted log dumps.
- Redact identifiers/content not required for diagnosis; respect consent, revocation and retention policy.
- Reject mismatched package IDs, signer fingerprints, digests, unsupported host profiles, expired/revoked manifests and unauthorized downgrades.
- Preserve user data and local objective state across repair.
- A failed/interrupted update must not destroy the only working diagnostics/reporting path.
- Cloud-generated code must pass repository review and CI policy; no direct production mutation from unreviewed model output.
- Keep an attributable audit trail for incident → diagnosis → change → tests → artifact → install → health result.
- The cloud is an engineering/release authority, not an unrestricted remote shell. Remote actions must be narrowly typed, authenticated, policy-checked and auditable.

## Objective 1 acceptance gates

These gates cover the initial installed-host/update slice only:

- [ ] Host profile is collected on the installed app and distinguishes observed, inferred, unavailable and permission-blocked values.
- [ ] A controlled fault is detected and a durable, sanitized incident bundle is preserved.
- [ ] Consent-gated report reaches the cloud and receives a verifiable acknowledgement; offline retry is tested.
- [ ] Cloud processing correlates the report with exact build and host profile and produces a traceable diagnosis.
- [ ] A real repair change or host-specific compatibility adjustment is generated through the repository workflow and passes required CI/security gates.
- [ ] A signed artifact and machine-verifiable release manifest match the permanent release signer.
- [ ] Friday validates and downloads the artifact, then exercises the supported installation path.
- [ ] Shizuku API integration and privileged UserService installer backend are implemented.
- [ ] Friday detects Shizuku/Shizuku-Next through the compatible API, checks binder liveness, permission, API level and actual server UID, and distinguishes shell from root.
- [ ] A controlled test installs/updates a test package in the background through Shizuku shell on a supported host without installer confirmation UI.
- [ ] A separate test validates the root backend when root is actually available.
- [ ] A separate test validates native PackageInstaller fallback and correctly surfaces STATUS_PENDING_USER_ACTION.
- [ ] Tests cover Shizuku absent/stopped, permission denied/revoked, binder death, service restart, shell-vs-root privilege, unsupported command/API, install failure, split APK, process death and post-install signer/version verification.
- [ ] A Friday self-update candidate is installed through Shizuku on the actual target phone, then health-checked and reported.
- [ ] A deliberately bad candidate is rejected or safely recovered from without losing user data or the reporting path.
- [ ] Friday resumes a checkpointed task after successful recovery and records the evidence chain.
- [ ] The owner is not required to collect logs, operate ADB/Termux, edit source or coordinate routine repairs. One-time privilege setup and exceptional Android-required confirmation are reported honestly.

## Test strategy

Use unit tests for classification, redaction, manifest validation, state transitions, retry/idempotency, signer pinning and backend selection. Use integration tests for cloud intake → repository workflow → signed artifact → release metadata. Use emulator tests for PackageInstaller result handling and process-death recovery. Friday then executes physical-host acceptance after installation and uploads evidence through the consent-gated path.

Test successful repair, incompatible artifact rejection, interrupted download, install requiring user action, install failure, post-update regression, recovery, offline reporting, Shizuku binder loss, permission revocation, root-vs-shell identity and resumed objective execution. A mock, emulator result or documentation checklist cannot satisfy the physical-host gate.

## Explicit non-claims

This document does not claim PR #119 already implements the update cycle. PR #119's described scope is on-device foundation probes and reporting. This document defines the additional seed-level installed-host/update implementation slice. It does not claim every Shizuku-Next feature works on every device, or that Android permits universal silent installation. Shizuku-Next's compatibility claim is a basis for API interoperability, not proof of device/release compatibility; physical-host tests remain required.

Universal self-adaptation, solving unanticipated problems, and Friday's complete self-development architecture are system-wide requirements in the canonical delivery plan, not Objective 1 completion gates.
