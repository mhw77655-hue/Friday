# Friday Local Station and Cloud Development Architecture

**Status:** target architecture and staged implementation contract  
**Plan authority:** `.ralph/FRIDAY_DELIVERY_PLAN.md`  
**Owner outcome:** the owner can tell Friday, in ordinary language, to take responsibility for keeping the app and its GitHub project healthy. Friday plans and coordinates the work; the owner should not need to manually diagnose, edit source, run commands, hunt APKs or repeat repairs.

## 1. The operating model

Friday is one continuing individual with a local Android host and optional remote computational resources.

- **Phone = Local Station / real-world host.** It observes the actual device, runs supported local diagnostics, captures privacy-minimised evidence, checks candidate APKs on the target environment, reports real installation/runtime outcomes and preserves work while offline.
- **Cloud = Development and compute environment.** It can inspect the canonical repository, research, run compiler/build/test workloads, construct isolated source candidates, compare alternatives and prepare pull requests and signed releases.
- **GitHub = source and build provenance system.** It stores canonical source, review history, workflow definitions and immutable build/test evidence. It is not the private device telemetry database and public Issues are not a telemetry transport.
- **Friday = coordinator and decision owner.** She interprets the owner's natural-language goal, plans tasks, selects local/cloud capabilities, tracks dependencies and checkpoints, evaluates evidence, decides whether a candidate meets the requested outcome, and explains only the approval or external prerequisite that genuinely remains.

The local station and cloud workers are not separate Fridays. The phone is not merely a dashboard for a cloud agent, and the cloud is not the authority on what actually happened on the owner's handset.

## 2. What the phone contributes to the cloud

The phone contributes the evidence that a cloud build machine cannot reproduce:

- Android version/API, ABI, device class and app version/source revision/artifact digest.
- Available memory/storage, app process memory, thermal/battery state and relevant resource pressure.
- Actual capability probes: model/runtime load state, inference result, voice engine readiness, Shizuku Binder/authorization/effective UID, permission state, update/install state and supported integration availability.
- Redacted crash/exception chains, process-exit/ANR evidence, correlated operation traces, failed postconditions and recovery outcomes.
- Bounded benchmark measurements: load time, latency, memory, energy/thermal observations where reliable, and quality metrics.
- Results of in-app health tests and owner-approved target-device acceptance journeys.
- Candidate APK verification: package identity, source revision, digest, signer, compatibility, install result, post-update health and rollback result.

**Default exclusion:** no raw conversation, transcripts, microphone/camera recordings, screen contents, notification bodies, contacts, location, personal files, installed-app inventory, account identifiers, serial numbers, credentials or private keys. A task may request a narrowly scoped extra evidence item only after Friday explains the purpose and obtains the relevant owner consent. Redaction is not a substitute for data minimisation.

The station sends structured evidence, not an unfiltered dump of the phone.

## 3. What the phone must not pretend to be

The installed Android app is **not** a general-purpose GitHub Actions self-hosted runner. It must not download and execute arbitrary workflow scripts, shell commands, DEX/JAR/native binaries or untrusted test code. It must not expose ADB, Shizuku, Accessibility or Android system permissions as a remote command channel.

GitHub's self-hosted runner support targets conventional desktop/server operating systems. Android is not a supported runner host. The phone instead implements a narrow **device-station protocol**: outbound polling or streaming over authenticated HTTPS, a signed job envelope, a fixed allowlisted operation set, durable execution state, and a signed result envelope. The phone needs no inbound open port and no generic shell.

Heavy Gradle builds, compiler work, static analysis and broad test matrices remain on reproducible cloud/desktop CI workers. The phone provides target-device evidence and may run only tests/diagnostics that its installed app, an explicitly installed test harness, and Android's granted capabilities genuinely support.

Android may suspend or terminate background work. Use WorkManager for durable deferrable tasks, user-visible foreground execution for eligible long-running owner-requested work, and app-start reconciliation for recovery. Persist checkpoints and make operations idempotent. Never promise an immortal background process or evade OS quotas.

## 4. The end-to-end maintenance loop

1. **Owner intent:** The owner says something natural, such as "Friday, take care of the app and keep the repository healthy." This is an outcome request, not a coded command language.
2. **Plan:** Friday reads the single canonical objectives plan, current task/evidence ledger, repository revision, CI state, installed-phone state, open incidents and available resources.
3. **Observe:** The phone contributes current host evidence. Cloud inspection contributes source, dependency, history, workflow and test evidence. Each fact is tied to a timestamp and source revision.
4. **Diagnose:** Friday identifies the first failing boundary, separates source defects from environment/tool/CI failures, compares hypotheses, and records uncertainty and evidence that could falsify the diagnosis.
5. **Design:** Friday creates a bounded work packet with expected behavior, affected paths, constraints, tests, resource budget, risk class and rollback target.
6. **Construct:** A cloud worker or local deterministic tool creates an isolated candidate branch/worktree. It cannot edit the installed app or protected main branch directly.
7. **Verify independently:** A separate verifier checks the diff, provenance, security boundaries, targeted tests, broad tests, build artifact and expected postconditions. The implementer is never the sole judge of its own work.
8. **Device validation:** Where relevant, Friday sends a signed, allowlisted test request to the local station. The station verifies job authenticity and compatibility, runs the permitted probe, and returns a signed, correlated result.
9. **Promote:** Passing changes become a reviewed PR. Merge/release permissions follow the owner's configured policy and repository protections. Signing identity and secrets remain outside source workers.
10. **Deploy:** CI builds the exact reviewed revision. The phone verifies manifest signature, package, signer lineage, digest, version monotonicity and compatibility, stages the APK, then uses Android's supported install path. It never assumes that a successful download means installation.
11. **Observe and recover:** The new app performs post-update health checks. Friday confirms the installed revision and real postconditions; if unhealthy, she retains or restores the last-known-good state where Android's package/update model permits it, or presents the exact required recovery step.
12. **Learn:** The task ledger records the defect, root cause, patch, reviewer, tests, device evidence, residual risks and regression prevention. Repeated failures become structural work, not another isolated patch.

Every task is resumable and has an idempotency key, deadline, cancellation state, bounded retries, evidence links and a terminal outcome. A task is not complete because a worker said it finished.

## 5. Station trust and protocol

### Device enrollment

- Generate a device-specific asymmetric key in Android Keystore where supported; private key material never leaves the device.
- Enroll through an explicit owner-authorized pairing flow against a configured private control-plane endpoint.
- Bind the public key to a device record, app package, installed signing identity, owner account and revocable station policy.
- Use TLS, request/response signatures or equivalent mutually authenticated proof, nonces, expiry, monotonic sequence/replay protection and key rotation/revocation.
- Keep GitHub authorization separate from station enrollment. A GitHub OAuth/GitHub App grant is scoped, revocable and never embedded in the APK or sent to coding workers.
- Pin the cloud proposal-signing trust root in the app's protected configuration; key rotation requires an authenticated transition and rollback-safe policy.

### Signed job envelope

A job contains schema version, job ID/idempotency key, issue/expiry time, target device/profile constraints, allowed operation enum, bounded parameters, data requested, consent requirement, resource/time ceilings, expected postconditions, result schema and cancellation policy.

Unknown required operations, expired/replayed jobs, wrong-device jobs, untrusted signers, excessive resource requests and arbitrary command/script fields are rejected. The station has no "execute arbitrary command" operation.

### Signed result envelope

A result contains job ID, device/app identity, source revision, test/diagnostic version, start/end times, observed facts, redaction summary, expected versus actual postcondition, evidence digests, failure classification and station signature. A result may be incomplete or unknown; it must never convert missing observations into success.

### Cloud proposals

Cloud output is untrusted candidate material until independently verified. The phone may accept signed declarative configuration/resource proposals only after schema, expiry, anti-replay, device compatibility, provenance, licence, resource budget, policy and rollback checks. New native code, Android components, permissions or unsupported formats require the signed APK release lane. No remote proposal can grant permissions, change signing identity, bypass owner authorization or run shell.

## 6. Autonomy and approval boundaries

Friday should do routine, reversible, low-risk maintenance without asking the owner to manually operate developer tools, once the owner enables the corresponding policy. Examples include collecting a host profile, running diagnostics, opening a bounded investigation, updating task state, running CI, preparing a patch, retrying a transient network operation and reporting verified outcomes.

Higher-impact operations have explicit policy gates:

- **May be automated after opt-in:** read-only repository inspection, redacted diagnostics, unit/static tests, isolated candidate branches, dependency/license reports, reproducible CI, non-executable compatible resource downloads and reversible local cache repair.
- **Owner-configurable approval:** repository writes/PR creation, new data categories sent off-device, test-harness installation, APK staging/installation, changing runtime/model resource allocations, and any operation that affects app availability.
- **Always protected unless a separately designed explicit policy says otherwise:** signing-key creation/rotation/export, secrets access, permission/privilege grants, destructive data deletion, irreversible external actions, public release publication, repository protection changes, weakening required tests, merging a change that has not passed independent gates, or transmitting private content.

Friday should explain an approval in terms of the exact operation, target, data, risk and consequence—not ask the owner to become the engineer.

## 7. Implementation status and gaps

The repository already contains useful foundations: host/resource observation, a schema-versioned `HostAdaptationEnvelope` payload builder, an objective-plan sync worker, local task inbox/ledger components, GitHub repository client/credential-store components, update staging/verification and diagnostic evidence stores.

Those components do **not** yet establish the complete local-station/cloud loop. The host-adaptation document currently records no configured authenticated cloud endpoint, no complete signed proposal/anti-replay pipeline, no complete device-station job protocol, and no physical-phone upload/adaptation acceptance. A local task inbox is not proof of a cloud job channel; a GitHub client is not proof of secure end-to-end autonomy; a payload builder is not an uploader.

The first implementation must inspect and reuse the actual production classes rather than create duplicate "agent" systems. Map each existing class to this protocol, identify disconnected or overlapping paths, and close one production slice at a time.

## 8. Delivery gates

- **LS-0 — Contract and inventory:** protocol, threat model, privacy schema, current production entry points and explicit non-capabilities documented.
- **LS-1 — Local station identity:** device key, enrollment, revocation, local durable outbox, offline queue and restart recovery.
- **LS-2 — Evidence pipeline:** minimum host/capability profile, redaction, correlated diagnostics, signed result and verified upload to a private endpoint.
- **LS-3 — Cloud task delivery:** signed, expiring, replay-resistant, device-bound allowlisted jobs delivered over outbound connections; cancellation and durable resume.
- **LS-4 — Real device verification:** actual in-app diagnostic probes and a separately authorized test-harness lane, with exact device/build provenance.
- **LS-5 — Repository operations:** least-privilege GitHub authorization, read/branch/PR workflows, durable task ledger and independent review.
- **LS-6 — Continuous repair:** detect CI regressions, isolate causes, prepare and test candidate patches, obtain independent verification, create reviewed PRs and prevent repeated defects.
- **LS-7 — Signed update loop:** exact reviewed source -> reproducible CI -> signed release manifest/APK -> phone verification -> install -> health check -> rollback evidence.
- **LS-8 — Owner experience:** natural-language "let Friday handle it" request, visible progress/evidence/approvals, pause/stop controls, and recovery after offline periods/reboot/process death.

No gate is passed by documentation or class existence alone. Each requires integration tests and retained evidence. LS-4 and LS-7 require target-device evidence; cloud-only CI cannot certify the owner's phone.

## 9. Success condition

The owner can express an outcome in ordinary language and Friday can take responsibility for the whole supported maintenance cycle: understand the objective, inspect the actual project and phone, diagnose, research, change, test, verify, coordinate GitHub, stage/deploy safely, observe the result, recover when necessary and remember what happened. The owner is not asked to repeat manual source edits, terminal commands, APK hunting or routine debugging.

The owner remains in control of authorization, risk and protected data. "Let me handle it" means Friday owns the engineering work—not that security, platform limits or evidence can be ignored.


## 10. Target-device recovery execution: Shizuku + ADB + Termux

This section makes the owner's existing target-device capabilities first-class parts of the Local Station. It does not introduce another Friday application or assistant.

### 10.1 Shizuku is an execution interface, not merely a health probe

The Local Station must integrate the official Shizuku API in the production Friday application and expose a typed capability broker to the immune core.

At runtime the broker must:
- Detect Shizuku binder availability and binder death.
- Request and track the owner's Shizuku authorization.
- Read the effective server UID and distinguish ADB shell (2000) from root (0).
- Maintain a versioned capability matrix for the actual Android build.
- Route only typed, allowlisted operations through Shizuku; never accept arbitrary shell strings from cloud/model output.
- Record command/API, arguments schema, caller, authorization, result, exit/error and evidence correlation ID.
- Fail closed when authorization disappears or a requested operation exceeds the observed privilege.

The first operation set to validate on the owner's device is: package enabled/stopped/version/signature inspection; process and service diagnostics; permitted log collection; app-op/permission state inspection; Friday process/component restart; package install-session initiation and result inspection; storage availability; and reboot/reconnect state. Do not infer that every operation is available simply because Shizuku is connected.

Shizuku ADB mode provides the Android shell identity, not root. Its actual permissions vary by Android version. It does not generally grant access to Friday's private app data directory. Root-only operations must be explicitly detected and reported as unavailable unless the device actually supplies root.

### 10.2 ADB and Wireless Debugging

ADB is the recovery control plane used to bootstrap/restart the Shizuku server and execute the narrow device operations that the shell identity is authorized to perform. Wireless Debugging pairing and authorization remain explicit Android security boundaries. Friday may detect and guide an expired/missing pairing, and may resume after the owner authorizes it; it must not claim it can silently turn Wireless Debugging on or bypass pairing.

The Local Station must persist the last successful Shizuku/ADB capability profile, detect drift after reboot or OS update, and request only the missing owner action. It must not block unrelated cloud-side diagnosis and source repair while the device control plane is unavailable.

### 10.3 Optional external execution hosts — Termux is not part of the Friday APK

Termux is an optional, separately installed, owner-authorized execution host that may be evaluated when it adds a genuinely independent phone-side recovery or workload path. It is **not** a Friday application component, bundled runtime, mandatory dependency, or part of the upstream REA integration. Do not add Termux packages, permissions, package-visibility declarations, scripts or runtime coupling to Friday by default.

If the owner chooses to use an existing Termux installation, a narrowly scoped external worker may:
- start through Termux:Boot when configured and permitted;
- hold a wake lock only in an explicitly enabled operating mode;
- maintain a durable incident/outbox queue independent of Friday's app data;
- communicate with the authenticated cloud control plane over outbound TLS;
- observe permitted Friday package/process health and coordinate with Shizuku/ADB;
- verify signed recovery manifests and report staged installation outcomes.

Any such worker requires a narrow authenticated protocol bound to device identity, nonce, expiry, operation enum and bounded parameters. No generic shell endpoint, unverified broadcast or filesystem drop-in is trusted. The worker receives no master credentials or signing keys.

Termux is not an immortal process. Android/OEM battery management can kill it, and force-stop prevents its boot receiver from restarting it until the user reopens it. Its value must be measured against the embedded Friday immune path and other available hosts. If Termux is absent, Friday's core remains functional and the recovery system reports the actual remaining routes.

Other independently authorized hosts may provide the same optional role. The architecture is defined by failure-domain independence and verified affordances, not by a mandatory app name.

### 10.4 Recovery behavior by failure class

| Failure | Expected recovery path |
|---|---|
| Friday Activity/process crashes while package remains enabled | An independently running immune process or optional external worker detects the failure, preserves evidence and attempts only a verified, authorized recovery |
| Friday enters a crash loop | Isolated recovery path requests cloud diagnosis, verifies a signed candidate and stages the supported update route |
| Friday package is force-stopped | Normal components in the stopped package cannot be assumed to run. An already-running independent host may attempt only operations proven available under the actual shell/device-owner/OEM privileges; otherwise owner/OS action is required |
| Friday package code is corrupted | Independent recovery host or owner-mediated path fetches a signed known-good/repaired APK, verifies identity/signer/digest and invokes a supported install path |
| Friday private data is corrupted | Restore from an independent encrypted/versioned continuity backup; Shizuku ADB mode alone does not grant access to Friday's private app data |
| Shizuku server dies or authorization is revoked | Friday detects and reports the loss; an independent host may preserve the incident and guide authorized Shizuku/ADB reauthorization; no privilege escalation |
| Optional Termux worker is killed | Embedded immune path or another already-running authorized host may continue; otherwise recovery waits for an available host |
| Optional Termux worker is force-stopped | Termux cannot self-start; another independent host or owner action is required |
| Friday is uninstalled/disabled | Recovery requires a genuinely independent host or owner-mediated route; install/enable authority is measured and may require confirmation |
| Device reboots | Each configured host re-establishes only the services Android permits; Shizuku ADB-mode startup may require Wireless Debugging/ADB authorization |
| Device/OS or all recovery hosts fail | Phone-side recovery is unavailable; cloud preserves source repair and waits for a restored device/control plane |

The system must not collapse process death, package force-stop, package disablement, uninstall, Shizuku loss, optional-worker force-stop and device reboot into one generic "app died" condition. They have different Android semantics and require separate tests.

### 10.5 Cloud-to-device repair package

The cloud returns a signed recovery manifest containing: target package ID, version/versionCode, signing certificate lineage, source commit, artifact SHA-256, supported SDK/ABI, minimum immune protocol version, required migration ID, continuity backup compatibility, post-install health contract, expiry/anti-replay nonce, and rollback/known-good reference.

The device worker verifies the manifest signature and APK digest before invoking installation. The installer path must be selected from measured capabilities: Shizuku shell APIs/commands, Android PackageInstaller session, user-mediated package installer, or a device-owner/OEM/root route if actually provisioned. Never infer silent installation authority from Shizuku connectivity alone.

The cloud-generated patch/build is a candidate, not an authorized release. Source repair, independent verification, signing authorization and device installation remain distinct trust decisions.

### 10.6 Required physical-device acceptance matrix

On the owner's actual phone, capture reproducible evidence for:
1. Shizuku connected in ADB mode; effective UID and per-operation capability probes.
2. Shizuku binder death and reconnection.
3. Friday Core process kill while an independent immune/external host remains alive; detection, cloud request, restart.
4. Friday package force-stop while an independent host remains alive; determine whether any shell/device-owner/OEM-authorized recovery action works on this exact OS/OEM, otherwise record the required owner action.
5. Friday crash-loop; repair artifact verification and installation outcome.
6. Higher-version same-signature update; package data, identity, memory, settings and databases remain intact.
7. Corrupted test data restore from the independent continuity backup.
8. If Termux is separately deployed, test its process kill versus force-stop and accurately distinguish recoverable from owner-intervention cases.
9. Device reboot; test each actually configured host's startup and Shizuku server availability/restart requirements.
10. Network unavailable during diagnosis/download; durable queue and safe resume.
11. Shizuku permission revocation and reauthorization.
12. Package disabled/uninstalled cases; record exactly which actions require user or device-owner authority.

Each result must include device build/API, app version/commit, Shizuku UID, optional external-host state, operation, observed result and evidence digest. Emulator CI is not a substitute for this matrix.

### 10.7 Implementation order

1. Inspect the current production Shizuku integration and existing host/capability probes; do not create duplicate clients.
2. Implement the typed Shizuku capability broker and binder lifecycle.
3. Evaluate optional external workers (including an existing Termux installation) only if a measured independent recovery need remains; do not add them to the Friday APK or make them mandatory.
4. Implement package/process health probes and force-stop recovery experiments.
5. Connect the device worker to the authenticated cloud recovery job protocol.
6. Implement signed repair manifest verification and the measured package installation path.
7. Prove continuity backup/restore and post-install health invariants.
8. Run the full physical-device failure matrix and convert every failure into an immune regression test.

No feature is marked implemented or device-verified until its exact code path and test evidence exist.
