# Objective 1 — Host-adaptive self-repair and verified update loop

## Status and intent

This document extends the Objective 1 acceptance contract. The first-launch host probes and consent-gated evidence report are only the observation/reporting foundation. They are not, by themselves, self-repair and must not be represented as full Objective 1 completion.

The required outcome is that an installed Friday can maintain itself against the real Android host it is running on: observe the host, detect and diagnose faults, send actionable evidence to the cloud, receive a host-compatible engineered repair, safely apply it through supported Android mechanisms, verify recovery, and resume its objectives without making the owner the routine tester or log courier.

## End-to-end control loop

1. **Observe the host.** Build a versioned host capability profile from information Android exposes to an ordinary app: device/build identity at an appropriate privacy level, Android/API version, ABI, available memory and storage, battery/thermal constraints, granted and denied permissions, installed/runtime capabilities, app version and signing identity, and measured feature-level capability probes. Record provenance, timestamp, confidence, and unavailable/withheld fields. Never infer a capability merely because a device model usually has it.
2. **Detect and preserve.** Monitor crashes, ANRs where observable, worker failures, failed capability probes, resource pressure, update failures, and objective execution failures. Persist a bounded, redacted diagnostic bundle locally before attempting network delivery. Keep a durable incident ID and event timeline so interrupted uploads can resume.
3. **Diagnose.** Run deterministic local checks first. Classify each incident as app defect, host incompatibility, resource exhaustion, permission/user-action boundary, network/service failure, or unknown. Attach evidence and confidence. Unknown or ambiguous cases remain unresolved; Friday must not invent a root cause.
4. **Report to cloud.** Upload only the minimum necessary sanitized evidence through the existing consent-gated reporting path. Support resumable, idempotent submissions and explicit delivery acknowledgements. Respect revocation, retention limits, secret redaction, and user-data boundaries. If reporting is not authorized or connectivity is unavailable, queue locally and continue safe local operation.
5. **Cloud engineering.** A cloud-side repair service must ingest the incident, correlate it with the exact app commit/build, signer, Android/API/ABI profile, capability results, and prior incidents, then create a reproducible diagnosis and proposed remediation. Where code changes are needed, produce a traceable source change/PR, run static analysis, unit tests, Android instrumentation/emulator tests, dependency and security checks, and build a signed candidate using the permanent release signer. A generated APK is not a repair until its provenance and test evidence pass policy.
6. **Host-specific release selection.** Produce a compatibility decision against the reported host profile. Prefer a single universal build when it safely supports the host; otherwise select a narrowly scoped variant or feature/configuration policy. Do not create arbitrary per-device forks when a capability-based configuration is sufficient. The release manifest must bind version, package ID, signer fingerprint, artifact digest, supported API/ABI/capabilities, source commit, test results, and rollback target.
7. **Secure delivery and installation.** Friday fetches release metadata and artifact over authenticated transport, verifies a trusted signature/provenance chain, pinned package identity and permanent signing certificate, digest, version/rollback policy, and host compatibility before installation. Use Android PackageInstaller and handle success, failure, and STATUS_PENDING_USER_ACTION explicitly. Attempt user-action-free updates only when the installed app, target SDK, installer status, and declared permissions satisfy the platform's documented conditions; never promise silent installation on every unmanaged handset or bypass Android security controls.
8. **Recovery and rollback.** Keep the currently working installation and user data safe. Use a staged update state machine (downloaded → verified → install-requested → installed → health-checking → committed, or failed/rollback-required), durable across process death and reboot. If post-update health checks fail, stop rollout, preserve diagnostics, and recover through a previously verified compatible release or a supported recovery path. Do not assume Android permits downgrading an APK in place; rollback strategy must account for package-manager version rules and preserve the signing identity.
9. **Post-update proof.** After install/restart, rerun the failed probe and relevant regression checks on the actual host, verify the expected version/signer, and report the outcome. A cloud CI pass or emulator pass is not proof that the physical host recovered. Close an incident only when the original failure is demonstrably resolved or explicitly classified as requiring owner action.
10. **Resume and learn.** Resume the interrupted objective/work queue only after the health gate passes. Feed verified outcomes into compatibility knowledge and regression tests. Use bounded retry/backoff and circuit breakers; do not loop endlessly on an unfixable host limitation. Continue monitoring and repeat the cycle autonomously.

## Required architecture boundaries

- **Host Intelligence:** versioned, privacy-minimized capability profile and real probes.
- **Local Reliability Agent:** incident detection, diagnosis, durable evidence, queueing, and safe-mode behavior.
- **Cloud Repair Orchestrator:** authenticated incident intake, diagnosis, code/PR/build/test workflow, compatibility decision, release manifest, and audit trail.
- **Release Trust:** permanent signer pin, artifact digest, provenance, anti-tamper checks, monotonic release policy, and revocation handling.
- **Update/Recovery Manager:** PackageInstaller integration, explicit pending-user-action handling, staged activation, post-update health gate, and recovery.
- **Objective Continuity:** durable objective checkpointing so a repaired Friday resumes rather than restarting blindly.

The cloud is an engineering and release authority, not an unrestricted remote shell. Reports must not grant arbitrary cloud code execution on the handset. Any remotely delivered action must be narrowly typed, authenticated, policy-checked, auditable, and least-privilege.

## Security, privacy, and trust invariants

- Never upload credentials, private keys, signing material, access tokens, raw personal files, or unrestricted log dumps.
- Redact identifiers and content not required for diagnosis; provide preview/consent controls consistent with the existing reporting model.
- Verify every artifact before installation. Reject mismatched package IDs, signer fingerprints, digests, unsupported host profiles, expired/revoked manifests, and unauthorized downgrades.
- Preserve user data and local objective state across repair.
- A failed or interrupted update must not destroy the only working diagnostics/reporting path.
- Cloud-generated code must pass the same repository review and CI policy as ordinary code; no direct production mutation from an unreviewed model output.
- Keep an append-only, tamper-evident local and cloud audit trail for incident → diagnosis → change → tests → artifact → install → health result.

## Objective 1 acceptance gates

Objective 1 is **not complete** until all gates below have implementation and evidence:

- [ ] Host profile is collected on the installed app and distinguishes observed, inferred, unavailable, and permission-blocked values.
- [ ] A controlled fault is detected and a durable, sanitized incident bundle is preserved.
- [ ] The consent-gated report reaches the cloud and receives a verifiable acknowledgement; offline retry is tested.
- [ ] Cloud processing correlates the report with the exact build and host profile and produces a traceable diagnosis.
- [ ] A real repair change or host-specific compatibility adjustment is generated through the repository workflow and passes required CI/security gates.
- [ ] A signed artifact and machine-verifiable release manifest are produced and match the permanent release signer.
- [ ] Friday validates and downloads the artifact, then exercises the Android-supported installation path, including the pending-user-action branch.
- [ ] The installed app verifies its new version/signer and reruns the original failing test on the actual host.
- [ ] A deliberately bad candidate is rejected or safely recovered from without losing user data or the repair/reporting channel.
- [ ] Friday resumes a checkpointed objective after successful recovery and records the complete evidence chain.
- [ ] The process works without the owner manually collecting logs, operating ADB/Termux, editing source, or coordinating routine repair steps. Any Android-mandated confirmation is surfaced honestly and only when the platform requires it.

## Test strategy

Use unit tests for classification, redaction, manifest validation, state transitions, retry/idempotency, signer pinning, and rollback decisions. Use integration tests for cloud intake → repository workflow → signed artifact → release metadata. Use emulator tests for PackageInstaller result handling and process-death recovery. Physical-host acceptance is then executed by Friday itself after installation, using the same versioned test suite and uploading the resulting evidence through the consent-gated path.

A simulated cloud response, mocked installer, emulator result, or documentation-only checklist cannot satisfy the end-to-end physical-host gate. Tests must include successful repair, incompatible artifact rejection, interrupted download, install requiring user action, install failure, post-update regression, rollback/recovery, offline reporting, and resumed objective execution.

## Explicit non-claims

This contract does not claim that PR #119 already implements this loop. PR #119's described scope is the on-device foundation probes and reporting. This document defines the additional implementation and evidence required to reach the user's stated outcome. It also does not claim Android permits universal silent self-installation on ordinary unmanaged devices; the app must use the supported PackageInstaller path and handle platform-required confirmation.
