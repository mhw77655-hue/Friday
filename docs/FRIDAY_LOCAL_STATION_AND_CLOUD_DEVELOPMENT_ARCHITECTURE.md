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
