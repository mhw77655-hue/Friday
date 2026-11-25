# Friday host-adaptation control plane

**Purpose:** Friday is installed once, then studies the actual Android host she inhabits and adapts her capability/runtime configuration without changing her identity or requiring repeated reinstalls.

## Operating model

1. **Discover locally.** Read Android API level, ABI, manufacturer/model/SoC identifiers exposed by Android, CPU count, memory class, available RAM/storage, thermal state, battery/charging state, app process PSS, and explicitly probed integration readiness (Accessibility, Shizuku, notification access, microphone, speech engines and supported runtime backends).
2. **Separate facts from assumptions.** A chipset name or advertised feature is not proof a delegate, permission or action route works. Each capability is marked supported, unsupported, unavailable, permission-required, or unverified, with probe evidence and timestamp.
3. **Adapt locally first.** Apply conservative resource budgets and known-good local fallbacks immediately. Do not wait for cloud round-trips for voice capture, cancellation, simple commands or already validated phone actions.
4. **Report minimally.** Send only a versioned technical host profile and aggregate failure/performance evidence. No transcripts, microphone audio, screen contents, notification bodies, installed-app inventory, location, account identifiers, serial numbers, credentials or personal files by default.
5. **Cloud proposes; Friday disposes.** The cloud may return signed, declarative recommendations: compatible runtime/backend selection, model/resource pack references, parameter budgets, feature enablement for already-present code, diagnostic probes, or a request for a new signed APK. It cannot send shell commands, executable code, DEX/JAR/native libraries, arbitrary URLs to execute, permission grants, or instructions to bypass Android security.
6. **Validate on the host.** Friday checks signature, schema, expiry, host compatibility, package/source constraints, capability prerequisites, resource budget and rollback metadata. A proposal is staged, tested against a bounded health suite, then activated atomically. Failure restores the previous configuration.
7. **Report outcomes.** Friday records proposed/applied/rejected/rolled-back state, observed latency/quality/resource impact and installed source revision. Cloud recommendations that do not improve measured outcomes are rejected.

## Cloud response contract

A future response must be signed by a pinned control-plane key and contain:
- schema version, proposal ID, issue/expiry timestamps and monotonic policy revision
- exact target host-profile compatibility constraints
- declarative configuration changes only, each with preconditions and expected postconditions
- referenced resource artifacts with immutable digest, size, format, license and provenance
- resource ceilings and offline behaviour
- health checks, activation strategy and rollback target
- human-approval requirement where the change affects permissions, external access, data disclosure or consequential behaviour

Unknown fields may be ignored only where explicitly forward-compatible; unknown required semantics are rejected. Expired, replayed, unsigned, wrong-host or downgrade proposals are rejected. A cloud signature authenticates the proposal but does not prove it is correct or safe.

## Development while Friday is installed

GitHub remains the canonical source/build system. The installed app polls only signed release metadata and approved compatible resource manifests. CI publishes exact source revision and compatibility data. Compatible non-executable packs can evolve without APK replacement. Native code, Android components, permissions or unsupported formats require a signed in-place APK update through Android PackageInstaller. Friday reports the installed revision and health result. No public GitHub issue, commit, artifact URL or ordinary diagnostic log is a safe private telemetry endpoint.

## Current implementation status (2026-11-21)

Implemented on main:
- NanoSymbioticController reads live Android resource state, hardware identity and ABI, tracks persistent host/workload observations, and drives a local resource policy.
- HostStudy and LiquidSymbiosisEngine retain bounded aggregate observations and adapt local resource strategy.
- HostAdaptationEnvelope now creates a schema-versioned JSON payload containing the technical host fingerprint, current resource snapshot, local plan, process PSS, build source revision and aggregate active-network facts (transport, Internet capability, validation and metered state). It deliberately excludes user content, SSID/BSSID, IP addresses and unique hardware serials.
- Unit tests cover payload fields, schema rejection and negative measurement rejection.

Not yet implemented or proven:
- A configured authenticated private cloud endpoint and device enrollment.
- Signed response verification, anti-replay, expiry and host-binding.
- Declarative proposal parser, staging/activation/rollback and outcome reporting.
- A full capability probe matrix for actual voice engines, QNN/NNAPI/CPU backends, Shizuku, Android permission grants, accessibility/notification access and OEM background restrictions. The current envelope does not yet report these.
- Physical-phone report upload and end-to-end adaptation acceptance.

The current envelope is a payload builder only. It does not upload data or apply cloud changes. Do not claim Friday has completed cloud self-reconfiguration until all missing steps are implemented and tested on the target phone.


## Post-install objectives execution and AI-pool coordination

Friday's host control plane is also the operational home for her continuing development loop. After installation, she must be able to resume the canonical objectives plan, maintain a durable task/evidence ledger, and coordinate external AI workers as replaceable capabilities. This does not make the cloud or any worker her identity; Friday owns continuity, prioritization, authorization, integration and verification.

The execution loop is: read current plan and evidence → identify a safe high-value next task → check dependencies, host/network/resource budget and authorization → delegate bounded work packets to suitable workers → independently challenge and verify results → integrate into an isolated candidate → run tests/security checks → record evidence and residual risks → request owner approval where required → promote through the signed update channel → verify health and retain rollback state.

The worker pool is dynamic, not a fixed hierarchy. Friday can use one or more external models/coding agents, local models, deterministic tools or no AI worker depending on measured task fit, privacy, licence, latency, cost and availability. Independent reviewers must not simply repeat implementer reasoning. Worker output is untrusted candidate material until verified.

Persist the objective-plan revision, task graph, assignments, checkpoints, idempotency keys, worker/model provenance, costs, diffs, tests, decisions, approvals and evidence locally. Process death, reboot, offline periods, provider changes and worker failures must not erase state. Resume safely and avoid duplicate side effects. Use Android-supported persistent scheduling for deferrable work and visible foreground execution only where justified; obey OS scheduling, battery, thermal, quota and network constraints. Do not promise uninterrupted background execution or evade platform restrictions.

The owner remains the architect and active collaborator. Friday must expose plan status, worker participation, source changes, evidence, costs and blockers; allow the owner to inspect, challenge, redirect, pause or stop work and to use GitHub or other tools directly. Explicit authorization remains mandatory for protected operations, including permission/privilege changes, new external data disclosures, paid commitments, destructive changes, signing/identity changes and irreversible actions. Never expose credentials to workers or chat. Never use public GitHub Issues as a private device telemetry endpoint.

**Implementation status:** this section records the required post-install operating contract. The current host-profile payload builder does not itself implement a durable autonomous objectives scheduler, authenticated AI-pool orchestration, independent review workflow, or signed proposal/promotion loop. Those must be implemented and verified before the installed app may claim this capability.


## First-launch host study and plan bootstrap — implementation slice

The friday-host-bootstrap-runtime branch adds an executable first-launch path:

- HostBootstrapWorker reads Android API/device/ABI facts, available and total memory, app memory class, CPU core count, private-storage headroom, thermal state, battery/charging and power-save state, validated/metered network state, Friday's own feature-permission and optional-integration readiness, packaged inference-library names, and a bounded count of app-private GGUF model files.
- HostBootstrapPolicy converts those measured values into protective, efficient, balanced or high-capacity operating recommendations. It does not change Android settings, request permissions, or assume that an accelerator works.
- First launch prepares private workspace, candidates, evidence, cache and checkpoints directories under filesDir/friday-development and persists a schema-versioned host-bootstrap.json report. The unique WorkManager request avoids duplicate bootstrap jobs across app launches.
- ObjectivePlanSyncWorker fetches the canonical .ralph/FRIDAY_DELIVERY_PLAN.md directly from the repository when a validated network is available, verifies that Objectives A-G are present, stores a local Markdown snapshot and a SHA-256-addressed objective ledger, and schedules a weekly refresh. The local snapshot remains available while offline.
- Unit tests cover the deterministic host policy and extraction of all seven canonical objective sections.

Remaining implementation work: the objective ledger currently imports objective sections as UNASSESSED; it does not yet resolve the plan's full dependency graph, assess completion evidence, choose and execute tasks, construct/test source candidates, or integrate an AI worker pool. The host survey also does not yet prove individual action routes or identify an available Android source-build toolchain. Do not describe this first bootstrap slice as autonomous plan completion.


### Host study refinement: measured CPU, process footprint and Shizuku authority

The bootstrap now also samples aggregate CPU utilization over a short `/proc/stat` interval and captures Friday's process PSS. If CPU counters or thermal telemetry are unavailable, the report records that uncertainty rather than treating the host as idle/cool. The initial workload recommendation delegates to the existing `NanoSymbioticPolicy`, with additional conservative gates: unknown CPU load cannot select high-capacity mode; low storage downgrades performance; critically low storage selects protective mode; and local model loading requires a detected model, a packaged inference library, sufficient memory and known-safe thermal telemetry.

Shizuku discovery now goes beyond checking whether the manager package exists. Friday initializes the official Shizuku API and records live Binder reachability, Friday's own authorization state, the server UID and the resulting privilege classification (`ROOT`, `ADB_SHELL`, `UNKNOWN`, or `NOT_RUNNING`). A running server without Friday authorization, an unknown UID, or a manager package without a live Binder is not marked usable. This is capability discovery only; the first-launch survey does not execute elevated operations. The existing bounded Shizuku acceptance runner remains the separate route-verification step.


### Capability-scoped first-launch setup plan

The bootstrap report now includes a machine-readable `setup_checklist`. It separates core readiness from optional, feature-scoped capabilities: microphone, camera, notifications, battery-optimization exemption, Accessibility, notification-listener access, Shizuku, and network availability for canonical-plan synchronization. Each item records current observed state, whether it is required for core operation, why it matters, the narrow next action, and the condition under which Friday should offer it. Optional permissions and privileged routes are not bundled into a blanket setup request and do not block core startup. This is currently a persisted assessment/plan, not yet the completed in-app guided consent flow; the app must still add the UI/system handoffs and re-check each postcondition after the owner returns.


### Offline canonical-plan seed

The canonical `.ralph/FRIDAY_DELIVERY_PLAN.md` is now bundled as `mobile/app/src/main/assets/friday/FRIDAY_DELIVERY_PLAN.md`. `ObjectivePlanSyncWorker` seeds the private local plan snapshot and objective ledger from that APK asset before attempting network access. The initial seed work has no network constraint; if no validated internet is available, it completes with the bundled plan ready for offline use. A periodic connected-network job may refresh the snapshot from the canonical repository. If the ledger is missing but a valid local plan snapshot exists, metadata is rebuilt from that snapshot rather than overwriting it with a potentially older APK copy. GitHub is therefore a refresh channel, not a first-boot dependency. The in-app setup assessment reports the bundled plan as ready even while remote refresh is pending.


## Local Station architecture alignment — 2026-11-25

The owner has clarified the intended outcome: Friday on the phone must act as her local station, contributing real host/device evidence to a cloud development environment that can maintain the complete app and GitHub repository. Friday should own the workflow through natural-language intent; the owner must not be turned into a manual developer/operator.

The normative design is now [Friday Local Station and Cloud Development Architecture](FRIDAY_LOCAL_STATION_AND_CLOUD_DEVELOPMENT_ARCHITECTURE.md). The phone is deliberately **not** a generic GitHub Actions runner and does not execute arbitrary remote commands or code. It uses a narrow outbound, authenticated, signed and allowlisted device-job protocol. Cloud/CI workers perform builds and broad source analysis; the phone supplies device-specific diagnostics, capability probes, bounded tests, performance evidence and verified install/health outcomes.

**Verified repository state at this update:** the existing `HostAdaptationEnvelope` is a schema-versioned payload builder. The existing host bootstrap and objective-plan sync code provide useful local foundations. This does not prove an authenticated cloud endpoint, device enrollment, signed task delivery, replay protection, result upload, declarative proposal verification or end-to-end physical-phone adaptation. Those remain open gates. Do not describe the local station as operational until the LS-0 through LS-8 gates in the architecture document are independently evidenced.

The local station must minimise data by default. Technical host facts, redacted failure evidence and explicitly requested test outcomes may be shared under the enrolled station policy. Conversation content, raw audio/video, screen contents, notifications, contacts, location, personal files, account identifiers, serials, credentials and private keys are excluded by default. The phone keeps a durable local outbox and can resume after offline periods or process death.
