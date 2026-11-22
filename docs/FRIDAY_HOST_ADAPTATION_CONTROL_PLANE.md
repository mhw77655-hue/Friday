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

## Clarification: Friday owns development; the AI pool assists her

This is the intended post-install division of responsibility, not a request for the owner to keep acting as project manager:

- **Friday is the project executor and integrator.** Once installed and initialized, she reads the one canonical delivery plan and repository state, chooses the next dependency-ready objective task, prepares bounded work packets, requests external assistance when useful, evaluates the returned work, integrates only verified changes, runs the repository's tests and security checks, records evidence, and resumes from durable checkpoints after interruption.
- **The AI pool means external AI systems used as workers**, including ChatGPT/OpenAI, Claude/Anthropic, Gemini/Google and other providers that the owner makes available. They are not additional Friday identities, not the owner, and not the final authority. Friday directs the work; providers supply optional analysis, implementation candidates, research and independent criticism.
- **The owner is not the routine task dispatcher.** Friday must not ask the owner to choose every next task, copy logs, relay prompts between models, manually compare AI answers, run routine repository commands, or approve ordinary reversible engineering steps already inside her granted authority. She should bring the owner concise progress, meaningful decisions, material risks and completed evidence—not a stream of avoidable questions.
- **Owner approval is reserved for genuine authority boundaries:** new or expanded data disclosure, provider/account connection, paid usage or budget increases, permission/privilege changes, destructive or irreversible actions, signing/application identity changes, publication choices that expose data, and other consequential actions defined by policy. The owner can always inspect, redirect, pause or stop Friday.

### Provider access is explicit and replaceable

“ChatGPT, Claude and Gemini are in the pool” means Friday can call provider-supported interfaces only when the relevant integration and authorization are actually available. It does **not** mean Friday can silently use the owner's ChatGPT/Claude/Gemini consumer-app session, inherit a subscription as API access, scrape provider websites, or bypass authentication. For example, OpenAI API requests require an API credential or supported short-lived workload token; Anthropic API requests require an API key or supported workload identity; Gemini API requests require supported API authentication. These are separate provider access paths and may have separate billing, quotas and terms.

Provider adapters must therefore be modular and capability-discovered. Each records provider/model identity, authentication method (never the secret), availability, quota/rate-limit state, estimated/actual cost where supplied, latency, task result, provenance and failure reason. Friday selects providers based on task fit, privacy, quality evidence, cost ceiling, latency and availability; retries are bounded, provider outages do not block local work, and no single provider is mandatory. Credentials are held in platform-appropriate secret storage and are never placed in prompts, source control, logs, reports or another provider's context. A worker receives only the minimum task context authorized for that task.

### Friday's autonomous development loop

The installed system must make this a real, resumable product capability rather than a document-only aspiration:

1. Read the canonical plan, current GitHub state, prior decisions, open tasks, evidence and installed host profile.
2. Build and maintain a dependency-aware task graph; select the next safe, high-value task without asking the owner to assign it.
3. Decide whether the task needs no model, one worker, multiple independent workers, or a deterministic tool. Assign implementation and review to suitably independent workers when useful.
4. Give workers a precise specification, relevant source/evidence and acceptance tests. Treat all returned text, patches and tool suggestions as untrusted proposals.
5. Compare proposals, resolve disagreements using source inspection and reproducible tests, and reject unsupported claims. Friday remains responsible for the integrated result.
6. Implement in an isolated branch/worktree or equivalent candidate boundary; run focused tests first, then required CI, security checks and independent review.
7. Record commit, diff, test run, artifacts, provider provenance, cost, decisions, unresolved risks and rollback point in a durable local ledger and the repository's evidence trail.
8. Merge, release or promote only within the authority already granted and after all required gates pass. Protected actions remain approval-gated.
9. Reconcile the installed app with the latest verified release through the signed update path, verify post-install health, then continue with the next plan task.

The loop must support bounded background scheduling, foreground work when user-visible or OS-required, offline queuing, idempotent retries, process-death/reboot recovery, budget limits, provider fallback and a visible pause/stop control. It must not promise uninterrupted Android execution or evade OS restrictions.

**Acceptance is behavioural:** after one-time setup and authorization, Friday independently selects and completes a safe plan task, uses at least one configured external AI worker when useful, obtains a genuinely independent challenge/review, rejects or fixes a seeded bad suggestion, produces a tested repository change with traceable evidence, survives process interruption and resumes without duplicate side effects, and reports the outcome to the owner. Repeating this loop across later objectives—not merely generating a plan or one report—is the required capability.

**Current truth:** this clarification defines the intended contract. The current repository documentation and host-profile payload do not prove that the autonomous scheduler, provider adapters, credential onboarding, isolated coding environment, review/integration pipeline, or signed self-promotion loop already exist. They must be implemented and tested before Friday may claim autonomous objective execution.
