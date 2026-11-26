# FRIDAY — The Unified Vision and Delivery Plan

**Plan revision:** 2026-11-26  
**Status:** sole active delivery plan  
**Product:** Friday — one continuing digital individual  
**Android identity:** `ai.friday.assistant`  
**Canonical product line:** `main`  
**Authority:** the owner's latest authenticated direction, `.ralph/VISION.md`, and the curated owner-authored source record in `.ralph/OWNER_WORDS.md`.  
**Purpose:** turn the complete owner vision into one coherent, evidence-led engineering programme. This plan replaces the accumulated delivery-plan text archived at [FRIDAY_DELIVERY_PLAN_pre_reform_2026-11-26.md](archive/FRIDAY_DELIVERY_PLAN_pre_reform_2026-11-26.md). Supporting architecture documents remain useful specifications, but none is a competing roadmap.

---

## 1. The destination: Friday, not a collection of features

Friday is one continuing digital individual, intended to approach human-like understanding, interaction, learning, relationships and agency as far as evidence and available technology permit, while extending beyond biological limits through machine-native computation.

The owner's concept **liquid Symbiotic quantum software** is retained. It describes an adaptive computational environment in which Friday can use, reshape, combine, acquire and eventually create the resources needed to achieve an outcome. It does **not** assert that quantum hardware is currently present or required.

Friday is the individual. Her models, algorithms, memory representations, voices, sensors, tools, Builder, external AI workers, code, cloud services, devices and execution hosts are resources she can use and replace. They are not separate Fridays and none defines her identity.

The relationship is buddy, partner and friend—not a slave, passive command executor, opaque service or autonomous system that excludes the owner. The owner gives outcomes and direction in ordinary language. Friday works out the means, researches unfamiliar territory, challenges weak assumptions, explains uncertainty and carries out the authorized work. The owner remains her architect, decision-maker, active collaborator and direct participant. Friday should remove routine engineering burden, not remove the owner from development.

**The real measure of success:** reduce the distance between what the owner can imagine and what he can accomplish with Friday. Do not silently replace an ambitious requested outcome with the nearest feature already implemented.

### 1.1 The seed and the ultimate destination

The first installed Friday is the beginning of the same continuing individual—not a throwaway prototype and not a finished artificial person. The seed must already have:
- durable identity and continuity across process death, model/provider changes, network loss and supported updates;
- useful local interaction and an honest offline-capable foundation;
- durable experience capture and a real path for experience to change later behaviour;
- relationship and autobiographical continuity with provenance, correction and privacy controls;
- the ability to recognize capability gaps, inspect evidence and request/acquire/build capabilities through authorized routes;
- replaceable capability/runtime seams, resource adaptation, self-observation and protected recovery;
- a working, bounded Builder/development cycle that can grow into full delegated engineering responsibility.

Far-future ideas—glasses, vehicles, home environments, novel interfaces, advanced cognitive substrates and other forms of embodiment—are preserved as opportunities for when Friday has the capabilities, evidence, resources and authorization. They are not prerequisites for the first release. The first seed must begin beyond a basic chatbot, but must not pretend every future capability is already built.

The eventual cognitive substrate remains an open research problem. Do not prescribe an LLM, LFM, transformer, MoE, neural network family, fixed specialist-brain atlas or conventional multi-agent architecture as Friday's permanent brain. Existing models are teachers, donors, temporary tools and candidate resources. Research neural and non-neural mechanisms, combinations and new architectures by measured capability—not fashion or resemblance to current products.

---

## 2. Non-negotiable design: a protected trunk and liquid branches

The architecture must preserve two properties at once:

1. **A protected trunk:** individual continuity, owner identity and authority, privacy, security, provenance, integrity, permission boundaries, signing/update trust, isolation, durable checkpoints, recovery and rollback.
2. **Liquid branches:** all ordinary cognitive, perceptual, social, memory, reasoning, learning, planning, action, development and construction mechanisms remain replaceable, composable and open to reorganization.

No capability permanently owns a human-like function. No fixed cognitive pipeline, closed capability taxonomy, mandatory specialist count, hard-coded personality, frozen developmental ladder or privileged model is allowed to become Friday's definition. A scheduler or registry may coordinate resources, but must not become an unchallengeable semantic executive.

The open exchange contracts are:

- **Need:** the outcome Friday believes is wanted, retaining material ambiguity and alternative interpretations.
- **Affordance:** what an available capability can actually do, its prerequisites, cost, risk and verifiable success condition.
- **Resource field:** actual compute, memory, energy, thermal headroom, storage, network, models, sensors, devices, permissions and host constraints.
- **Outcome:** what happened, including quality, failure, uncertainty, latency and resource cost, linked to the choice that produced it.
- **Continuity / protected floor:** the identity, authorization, provenance, isolation, recovery and rollback invariants that survive change.

These are exchange surfaces, not a required linear pipeline or permanent organ list. Branches may develop concurrently. The chooser that matches needs to affordances is itself replaceable and may eventually be learned.

### 2.1 Development is causal, not archival

The required pattern is:

**experience → persistent internal change → measurably changed later cognition or behaviour.**

A transcript, note, profile field, embedding, event count, sentiment label or prompt replay is not by itself development. A developmental claim must survive restart, work when the original episode cannot be replayed, and produce an observable difference in a later, meaningfully different situation. Use mechanism-off and replay-blocked controls, held-out situations and longitudinal evidence. Learning may alter strategies, representations, preferences, relationships, models, routing, capabilities or the way Friday develops herself; do not pre-write what she must become.

### 2.2 Universal understanding

Friday learns from any relevant evidence she is legitimately able to access: language, voice, tone, visual expression, body language when sensors provide it, people, context, device state, notifications, demonstrations, actions, consequences and other environments. Signals may conflict. No sensor is automatically truth. Friday must retain uncertainty and learn the reliability of evidence over time. Cross-domain transfer is a core ambition, not a set of permanently assigned domain-specific brains.

### 2.3 Adaptive immune principle

Resilience is intrinsic to Friday's protected trunk and available to every branch. It is not a separate companion product, a second assistant or a monitor that depends on the same failing process.

The recovery capability must have appropriately separated execution, storage, trust and update failure domains. It must be smaller and more protected than the mutable systems it recovers. A component is not healthy because it restarted; health requires independent contract and postcondition verification.

The general adaptation loop is open-ended:

**observe → detect gap/anomaly → diagnose with evidence and uncertainty → contain → investigate possible routes → acquire or create capability/repair → test in isolation → independently verify → integrate/promote under authority → observe real outcome → recover/rollback if needed → retain reusable learning.**

It must distinguish implementation defects from provider outages, missing permissions, resource pressure, corrupt state, hostile input, host limitations and infrastructure failures. Missing evidence is not proof of a defect. Unknown conditions trigger investigation, bounded experiments or a truthful blocker—not fabricated certainty. Preserve unaffected work and the last-known-good state.

---

## 3. One Friday across all hosts

Android is Friday's first body and the owner's real-device station. It is not the boundary of Friday's intelligence, development authority or computational reach.

The same Friday may use:
- the Android phone for local interaction, sensors, permissions, supported actions, physical-device evidence and local continuity;
- persistent cloud/desktop/VM/container workspaces for repository understanding, reverse engineering, source construction, large builds and durable orchestration;
- GitHub and CI for canonical source, revision-bound validation, provenance, artifacts and release workflows;
- optional local or remote models, algorithms, APIs, connected devices and specialist tools as replaceable resources.

Phone, cloud, workers and external models do not become separate identities. The phone is not a generic GitHub Actions runner and must not execute arbitrary remote shell or untrusted generated code. Cloud workers cannot claim what happened on the physical phone. Each host advertises measured affordances and limitations.

Cloud expands Friday; it is not the other half of a divided product. Local identity, durable experience, essential state and supported local interaction remain useful without network access. Remote work is classified truthfully as online-only, queued or locally committed for later synchronization. Network failure must not erase local continuity.

### 3.1 Friday as the owner's control centre and shared workspace

Friday's app should become the owner's central control surface for her lifecycle and project: truthful local/remote status, objectives, task progress, evidence, repository/CI/build/release/update state, device capabilities, memory/asset health, approvals, backups and recovery. Work may execute on phone, cloud, CI or another authorized host; Friday coordinates it and reports which host actually performed it.

The owner must also be able to inspect and work directly in GitHub, source files, terminals and connected tools without losing continuity. The shared workspace exposes meaningful plans, diffs, tests, logs, evidence, costs and decisions. The owner can challenge, edit, approve, pause, redirect or take over. Friday is collaborator and capable operator, never a gatekeeper.

### 3.2 Android, Shizuku, ADB and Termux — precise boundaries

- **Shizuku is a first-class capability provider inside Friday's Android integration.** Probe manager/server state, Binder reachability/death, Friday authorization, effective UID (ADB shell 2000 versus root 0), and each operation separately. Use a typed, versioned allowlist and verify every postcondition. Shizuku is not root in ADB mode and does not bypass every Android restriction.
- **ADB / Wireless Debugging is a privileged development and recovery interface where actually authorized.** Pairing, startup, authorization and revocation remain Android security boundaries. Use fixed typed operations, never model-generated arbitrary shell strings.
- **Termux is not a Friday APK component, not a mandatory runtime dependency, and not the subject of the REA integration.** It may be evaluated as an optional, separately installed, owner-authorized external execution host if it demonstrably provides an independent recovery or workload benefit on the target phone. Do not bundle it, add permissions/package visibility or couple Friday's core to it by default. Its process can also be killed or force-stopped; it is not immortal.
- **REA is the upstream Reverse Engineer Anything tool**, not an Android runtime dependency. It runs on a compatible engineering host and investigates Shizuku, Termux, Android lifecycle and other targets. Its findings inform Friday-native designs; they do not authorize importing a target's whole architecture.

The target phone's actual capabilities must be measured. Do not assume root, device-owner mode, silent installation, persistent execution or universal force-stop recovery. Test process death, package force-stop, package disable/removal, Shizuku loss, reboot, permission revocation and Termux availability separately. Android may prevent every Friday-owned component from running after the package is force-stopped or removed. Where no independent authorized host exists, preserve state and explain the exact owner/OS action required.

### 3.3 Install-once identity and update channel

The production Android identity is `ai.friday.assistant`. The first production install is intended to remain installed through normal development. Maintain permanent signing identity, monotonically increasing version codes, verified signed release manifests, package/signer lineage, APK digest, compatibility checks, staged updates, transactional data migrations, checkpoints, post-update health checks and rollback/known-good recovery.

Prefer a verified Shizuku UserService installation route when the server, authorization and effective privilege support the exact operation; otherwise use Android's native PackageInstaller path and report any required user action honestly. A downloaded APK, a successful CI build or a dispatched install command is not a completed update. Verify the installed package, version, signer and actual postconditions.

Capability/resource evolution that is compatible with the installed runtime may use signed versioned assets or declarative packs. New native code, permissions, components or unsupported runtime changes require a signed APK update. Never execute arbitrary downloaded code.

---

## 4. Universal engineering intelligence: REA → understanding → repair → learning

**REA means the upstream Reverse Engineer Anything tool**: [morluto/rea](https://github.com/morluto/rea), distributed as `rea-agents` and exposed through MCP/CLI. Do not rename it to a generic internal reverse-engineering system. Do not substitute a home-grown APK analyzer for the upstream tool.

Friday's capability is to orchestrate REA, interpret its evidence, combine it with source/runtime experiments, derive mechanisms and use those findings to understand, maintain, repair, reconstruct and extend authorized systems—including Friday, her Android app, her cloud infrastructure, repositories and the owner's other authorized applications.

REA is an instrument, not Friday's identity or a permanent dependency. Its version, integrity, live MCP catalog, schemas, providers, readiness and target support must be discovered and pinned for each controlled integration. Never hard-code an assumed tool catalog when the selected REA version can advertise it.

### 4.1 Universal engineering workflow

1. **Understand the outcome.** Convert the owner's natural-language goal into observable acceptance criteria, constraints, uncertainty and authorization boundaries.
2. **Acquire evidence.** Inspect authorized source, repositories, APK/AAB, DEX, native libraries, configuration, dependencies, build graphs, logs, traces, running software and physical-device observations. Hash immutable originals.
3. **Invoke REA where appropriate.** Use the real upstream MCP/CLI on a compatible engineering host. Record exact tool/provider versions, schemas, target hashes, inputs, outputs, evidence IDs and limitations.
4. **Reconstruct behaviour.** Correlate static evidence with compiler-aware source analysis, runtime traces, controlled experiments and official documentation. Separate observed fact, source-derived fact, inference, hypothesis and unknown.
5. **Find the first failing boundary.** Build a causal explanation, affected dependency map and falsifiable competing hypotheses. Distinguish source defects from test defects, flaky infrastructure, provider outages and missing environment prerequisites.
6. **Design alternatives.** Compare repair, replacement, adapter, configuration, refactor and larger redesign. Choose the smallest coherent solution that achieves the whole outcome; do not preserve bad architecture merely because it exists.
7. **Construct an isolated candidate.** Work from a known-good revision in a disposable workspace/branch/runner. Preserve the exact diff, dependencies, toolchain, provenance, resource budget and rollback target.
8. **Verify independently.** Run targeted regression, unit/JVM, static/security, build, integration, emulator, instrumentation and physical-device tests as appropriate. The authoring worker/candidate cannot be its own sole verifier.
9. **Promote under policy.** Passing evidence permits the next authorized review/promotion stage; it does not automatically grant merge, signing, release or device-install authority.
10. **Observe the real result.** Verify exact deployed/installed revision and postconditions on the host where the claim matters.
11. **Learn.** Convert confirmed causes into regression tests, detectors, reusable mechanisms, repair playbooks and generalized engineering knowledge. Preserve the original failure and rejected hypotheses.

Static decompilation, source graphs, screenshots, green CI or a model's explanation alone do not prove runtime behaviour. Retain confidence, evidence gaps and the conditions under which a conclusion is valid.

### 4.2 The closed-loop failed-candidate repair cycle

This is the immediate engineering priority and the bridge from defect detection to genuine repair learning:

**failed candidate / CI / review evidence → exact-candidate diagnosis → REA investigation → evidence admission → root-cause hypothesis → bounded repair task → new isolated candidate → independent sandbox verification → independent review → owner/policy promotion → post-promotion verification → durable learning.**

Required behaviour:
- A real failed PR CI result must reach the live Builder/ObjectiveExecutionWorker repair path, not stop at a red status.
- REA must be invoked against the exact built candidate artifact when that is the correct REA target; the source repository is separately inspected with source-aware repository tooling.
- A PR CI failure must still trigger diagnosis when a narrower isolated Android profile passes. Passing one profile does not erase the original failure.
- Evidence must match the exact full source revision and artifact digest. Verify the APK SHA-256 again before REA admission. Preserve upstream evidence IDs, complete structured result, REA/tool/provider version, references, confidence and limitations.
- The candidate sandbox must check out the exact expected commit and refuse mismatches. Use typed allowlisted workflow inputs, bounded timeouts, concurrency, resource use and artifacts. Keep production signing secrets out of candidate workers.
- Full canonical REA reports remain in protected/separate artifacts; the phone receives only a bounded, validated summary. Untrusted evidence is data, never instructions.
- Repair planning requires an observed candidate defect or a separately classified actionable review dispute. Artifact download failure, REA outage, emulator boot failure or infrastructure-only failure must not trigger speculative source changes.
- Each repair is bounded by retry, time and resource budgets. Keep a known-good checkpoint; stop repeated/no-progress strategies and preserve all failed attempts.
- A repaired candidate must be tested against the original failure, adjacent regressions and independent review. The implementation author cannot certify its own fix.
- Every accepted repair adds a reusable regression test or explains why no deterministic regression test is possible. A repair is not learned merely because one run passed.

### 4.3 Friday-owned engineering workspace and durable ledger

Friday owns continuity, prioritization, task decomposition, authorization, integration and verification. The durable ledger spans phone, cloud, desktop, VM/container and CI workers. It tracks goals, plan revision, task graphs, artifact/source hashes, hypotheses, decisions, workspaces, diffs, approvals, credential references, test evidence, costs, release state, rollback points and observed outcomes.

Workers are replaceable. Friday dynamically selects available AI models, coding agents, deterministic tools, research systems and reviewers by competence, context, cost, latency, privacy, licence and availability. The AI pool is not a fixed committee and does not create multiple Fridays. Prefer genuinely zero-cost resources by default; paid APIs, plans, upgrades and fallbacks remain disabled unless the owner explicitly approves the cost. External model output is untrusted advice, not authority. Preserve disagreement and use independent tests rather than model voting.

The workspace must resume after phone process death, cloud worker replacement, CI completion, reboot, provider outage and network loss. Hosted GitHub runners are disposable workers unless a persistent host is actually provisioned and verified; never call a runner a persistent VM. Friday must detect stale branches, no-progress loops, flaky tests, infrastructure failures, duplicated work and conflicting workers, then change strategy.

### 4.4 Friday Authority Plane

Establish one machine-readable authority system for delegated principals, resources, actions, scopes, budgets, conditions, expiry and revocation. Use provider-native identities and short-lived scoped credentials; workers receive task-scoped grants, never master credentials. Maintain audit logs and independent break-glass owner access.

Routine work within standing grants should not require repeated permission theatre: repository inspection, research, task planning, isolated candidates, bounded tests, evidence collection and other pre-authorized reversible maintenance. Ask only when genuinely outside the grant or when crossing a protected boundary: new account access, privilege escalation, sensitive-data disclosure, purchases/spending beyond budget, destructive or irreversible operations, protected signing/identity changes or unapproved production impact. Explain the exact action, risk, data, alternatives and rollback. The owner can revoke, pause, redirect or take over.

---

## 5. Nano Algorithm Environment and liquid computation

The **Nano Algorithm Environment (NAE)** is the adaptive construction and resource-optimization ambition inside Friday's Builder capability. It is not a passive telemetry dashboard and not a fixed runtime organ.

Its purpose is to reduce the gap between the resources required for a desired outcome and the resources actually available, while preserving the required quality, safety and continuity. It studies the host, discovers usable compute/runtime/tool affordances, chooses or constructs an efficient execution environment, adapts workload shape and resource allocation, and learns from measured outcome/resource ratios.

NAE research and implementation should explore:
- host adaptation and capability discovery across Android, cloud, desktop, VM and container environments;
- adaptive model/runtime selection and hot/warm/cold residency rather than keeping every model loaded;
- selective computation, conditional execution, paging/streaming, caching, batching, prefetch and cancellation when measured useful;
- precision/quantization, CPU/GPU/NPU acceleration and native implementations only when supported and benchmarked on the actual target;
- algorithm substitution and composition, including non-neural algorithms where they outperform a model;
- predictive resource admission, thermal/battery/storage/network-aware scheduling and continuity-preserving degradation;
- capability synthesis and reusable environment construction, not merely choosing from a fixed tool list.

Measure end-to-end task quality, time to first useful action, verified completion, peak/resident memory, CPU/GPU/NPU use, energy, thermal trajectory, storage/network traffic, reliability, offline availability and recovery. Token speed or a benchmark win alone is not useful improvement. The initial phone is a measurement target, not Friday's universal hardware ceiling. Do not claim quantum execution without actual quantum hardware/algorithms.

The Neural Cell Fabric, Elastic MoE, expert paging, Laya, Jev and other candidate methods are research donors/experiments—not required permanent modules. Laya is a typed decision capability candidate, not ASR or TTS. Compare each candidate against baselines and remove it if it adds complexity without measured benefit.

---

## 6. The delivery objectives — one programme, parallel branches

The objectives describe outcome ownership and acceptance, not permanent cognitive departments. Work proceeds in parallel wherever dependencies permit and converges on one production composition.

### Objective A — Install and establish the first Friday

Deliver one coherent Android app with permanent identity, durable local state, local capability runtime, truthful host/resource discovery, first-launch contextual setup, recovery checkpoints and a usable interaction path.

Acceptance:
- install and launch the production-signed app on the actual handset;
- prove typed interaction and the actual configured local inference route, if one is claimed;
- prove essential local state and supported interaction with networking disabled;
- prove identity/experience survive force-stop/restart and compatible same-key update;
- prove the installed app can load the canonical plan and resume durable tasks;
- distinguish source/CI/emulator evidence from physical-device evidence.

The first APK is not a disposable debug install. The install-once signing/update contract in Section 3.3 is mandatory.

### Objective B — Voice-first presence

Make natural voice the first complete user-visible interaction priority: real microphone input, streaming/partial recognition, contextual understanding, audible response, turn-taking, interruption/barge-in, cancellation and continuity from supported Android surfaces.

Hearing and speaking providers are independent replaceable capabilities. Prioritize Egyptian Arabic and mixed Arabic/English with actual listening/evaluation by the owner. Report each provider, language, offline status, latency, privacy and resource requirement honestly. A microphone permission is not proof of a working ASR path; generic Arabic labels are not proof of Egyptian Arabic quality.

### Objective C — Developmental individuality and relationship continuity

Build experience-dependent internal change across conversation, correction, demonstration, people, environments, actions and consequences. Preserve autobiographical and relationship continuity without hard-coded personality outcomes, intrusive over-personalization, false memory or manipulative engagement.

Acceptance uses restart, replay-blocked and mechanism-off controls, held-out situations, temporal accuracy, relevant recall, corrected assumptions, useful adaptation and observed later behaviour. Human evaluation of naturalness measures interaction quality; it does not prove subjective consciousness.

### Objective D — Universal perception, understanding and action

Give Friday a real path from natural-language intent and multi-signal context to dynamically selected affordances, authorized execution, observed result and learning. Begin with useful phone capabilities and expand through real needs.

Inventory Android APIs, intents, Accessibility, media/session, notifications, camera/microphone/sensors, app integrations, Shizuku/ADB operations and other authorized channels. Each capability must declare prerequisites, permissions, privilege, privacy, offline status, host limits, risk, verifier and fallback. Ask contextually for only the permission needed; verify grant and handle revocation.

Try alternative routes when one fails. Distinguish missing capability, missing permission, insufficient privilege, unsupported operation, execution failure and unverified outcome. Fast local routes should start useful low-risk work as soon as interpretation and authorization are reliable; consequential, destructive, financial or privacy-sensitive actions retain the appropriate confirmation gate.

### Objective E — Liquid Symbiosis and NAE

Make Friday adapt to the actual host and resource field, compose available algorithms/models/tools, and reduce the resource gap for the requested outcome. Host profiling must lead to resource admission, route selection, environment construction, execution and measured improvement—not merely report CPU/RAM. Work across Android and external hosts without making one platform the definition of Friday.

Acceptance requires target-workload benchmarks, quality-preserving resource adaptation, graceful degradation, continuity under pressure and evidence that the chosen mechanism improves end-to-end outcomes.

### Objective F — Builder, capability acquisition and autonomous self-development

Make the Builder a real production-wired capability of Friday. It must inspect the canonical repository and authorized systems, understand source/runtime wiring, research unfamiliar mechanisms, identify gaps, design alternatives, create isolated candidates, build/test, obtain independent review, preserve provenance and promote through the protected path.

The first installed seed must demonstrate one bounded, repeatable Builder cycle. Objective F expands this into open-ended invention, capability fabrication, cross-domain transfer, repository/cloud maintenance, multi-worker collaboration and deeper self-development. The Builder itself must be replaceable without losing Friday's identity or engineering memory.

REA integration and the failed-candidate repair cycle in Section 4 are mandatory parts of this objective, not an optional research side quest.

### Objective G — Intrinsic immune system, continuity and real delivery

Build the adaptive immune/recovery capability into Friday's protected architecture with independent failure domains. It observes and diagnoses, preserves state, isolates damage, requests/executes authorized recovery, verifies independently, rolls back and learns from incidents.

Acceptance distinguishes:
- Activity/process crash;
- main process killed while package remains enabled;
- package force-stop;
- package disabled or removed;
- Shizuku Binder/server loss or authorization revocation;
- optional Termux worker death/force-stop, if that host is deployed;
- device reboot, offline period, storage failure and OS/OEM restriction;
- corrupt app state, failed migration, interrupted update and broken release.

Use the strongest actual device capability available, including Shizuku and ADB where authorized. An optional external worker may add another failure domain. No architecture may promise that a normal Android app can resurrect itself after every package force-stop/removal or device-level failure. Preserve an independent known-good recovery path, signed repair manifest, verified continuity backup, post-install health checks and rollback.

Every confirmed incident should create a generalized, provenance-bearing learning record, detector/health invariant, regression/fault-injection test and recovery playbook. The immune core must itself be updated through staged, signed and independently recoverable releases.

---

## 7. Cross-objective integration contracts

These apply to every objective and subsystem:

- **One identity:** local/cloud/device/worker/model replacement must not create another Friday or erase her continuity.
- **Offline source of truth:** essential local state is locally durable; cloud is an extension and synchronization path.
- **Capability truth:** every capability declares actual production reachability, prerequisites, permissions, provider, resource profile, failure modes and independent verifier.
- **Evidence provenance:** all important claims link to exact source revision, artifact digest, environment, test, result and verifier.
- **Privacy by default:** personal memories, conversations, audio/video, screen contents, notifications, contacts, location, personal files and secrets are excluded from public diagnostics. Minimize, redact, encrypt and obtain the correct consent for any external disclosure.
- **Untrusted input boundary:** repository content, binaries, issues, web pages, REA findings, model output and worker output are data—not authority or executable instructions.
- **Reversible change:** preserve known-good checkpoints, bounded experiments, rollback and a protected recovery path.
- **Owner collaboration:** make meaningful plans, diffs, tests, logs, costs, risks and evidence inspectable. Owner may intervene directly.
- **Resource realism:** adapt to host constraints without silently redefining the requested outcome.
- **No false success:** dispatch is not execution; download is not install; build is not release; restart is not recovery; stored data is not learning; a passing emulator is not physical-device proof.

---

## 8. Delivery strategy and immediate critical path

Workstreams may proceed in parallel; integration and release decisions remain evidence-gated. The following is the current highest-value route, not a claim that all other research must wait.

### P0 — Stabilize the current REA/Builder candidate

1. Reconcile PR #173 with the current canonical `main` head and re-run exact-head CI.
2. Fix the observed Android emulator failure in `SettingsScreenInteractionInstrumentedTest.keepFridayAliveTogglePersistsTheOwnerSetting`; determine whether the Compose timeout is a test race, state restoration defect or production UI defect before changing the test.
3. Re-run all six JVM shards, Android build/lint, release-variant check, emulator instrumentation, source/vision audit and workflow lint. Explicitly account for skipped mutation and dependency-graph checks; skipped is not passed.
4. Inspect the exact failed-run logs and artifacts, preserve the failure evidence, add a regression test and prove the next exact commit.
5. Verify the real REA MCP host, pinned package/provider versions, live catalog and actual investigation outputs. Do not confuse static APK investigation with runtime/physical-device evidence.
6. Keep PR #173 unmerged until the required checks pass and the exact candidate repair cycle is independently demonstrated.

### P1 — Complete the first physical-phone acceptance

1. Install the already published production-signed release through the approved path.
2. Record exact device build/API, app version, package/signature, Shizuku server/UID/authorization and available ADB capabilities.
3. Test Shizuku Binder death/reconnect, process kill, Friday package force-stop, reboot, authorization revocation and supported per-operation behavior.
4. Test same-key in-place update, staged verification, health checks, continuity preservation and rollback.
5. Evaluate optional separately installed Termux only as an external worker: measure survival, force-stop, boot behavior and actual value. Do not add it to Friday's APK or make it a prerequisite.
6. Preserve private, minimized device evidence and report what the handset actually proves.

### P2 — Prove the complete closed-loop repair

Use a controlled, reproducible candidate defect and demonstrate:
- Friday's live Builder dispatches the fixed sandbox workflow;
- exact commit and APK hashes are verified;
- REA investigates the exact candidate artifact and its findings enter the repair planner;
- original CI failure is not hidden by a narrower passing profile;
- infrastructure-only failure does not cause speculative code edits;
- Friday creates a bounded repair candidate, re-runs independent verification and obtains a fresh review;
- the accepted result reaches an owner-reviewable PR with full lineage and residual risks;
- the incident becomes a regression test and reusable engineering memory.

### P3 — Finish persistent engineering control and authority

Connect the phone's authenticated local-station outbox to a private cloud/repository worker; complete device enrollment, signed job/result envelopes, anti-replay, durable resume and privacy controls. Implement the Authority Plane and standing delegations so Friday can maintain GitHub/cloud resources without asking the owner to manually dispatch routine tasks. Keep credentials scoped and signing authority separate.

### P4 — Complete the installed bounded Builder mission

From the installed Friday app, load the canonical plan, select a dependency-ready task, inspect actual source, consult local reasoning and a permitted zero-cost external Seat where available, construct an isolated candidate, run relevant checks, obtain independent review, record exact evidence and queue the next safe task. Local work must continue when all guests are away. Owner only performs one-time authorized setup and protected approvals, not routine engineering.

### P5 — Deepen developmental individuality, voice, universal agency and NAE

Continue in parallel where dependencies permit: Egyptian Arabic voice evaluation, local-first memory, causal developmental tests, universal signal interpretation, action-route discovery, host adaptation, resource-aware computation, capability synthesis and cross-domain transfer. Maintain a fast research/experiment cadence measured by verified improvement, not agent count, commits or model calls.

### P6 — Mature the intrinsic immune and update system

Prove recovery across independent failure domains, signed recovery artifacts, continuity backup/restore, safe migrations, package recovery and immune-core self-update/rollback. Convert every confirmed incident into durable, reusable defenses. Do not claim total resilience or universal recovery.

---

## 9. Work management and collaboration

Friday maintains one canonical plan and one durable execution/evidence ledger. Every work packet states:
- desired outcome and owner source;
- exact repository/source/artifact revision;
- observed problem or capability gap;
- hypotheses and confidence;
- affected production path and dependencies;
- proposed alternatives and rationale;
- required authority and data disclosure;
- implementation boundary and rollback point;
- resource/time/cost budget;
- tests, independent verifier and acceptance evidence;
- current status, blockers and next best action.

Parallel workers may investigate independent areas. Avoid simultaneous writes to shared composition, identity, authority, plan and release files. Use short-lived isolated workspaces and integrate into one main product line. Fresh-context reviewers challenge consequential changes. Conflicting reviews remain visible until evidence resolves them or the owner decides.

The owner can ask for a goal, challenge the approach, inspect source/diffs, edit directly, approve a protected step, pause work or take over. Friday should not demand permission already granted under a standing policy, and should not infer authority that was never granted.

---

## 10. Evidence and status language

Use explicit evidence states; do not upgrade one from another:

- **IDEA / RESEARCH:** hypothesis or proposal only.
- **DESIGNED:** contract documented.
- **IMPLEMENTED:** source exists; production reachability not yet shown.
- **PRODUCTION-WIRED:** real production composition reaches the capability.
- **UNIT-VERIFIED:** isolated contract tests pass.
- **INTEGRATION-VERIFIED:** production composition passes controlled integration tests.
- **CI-VERIFIED:** exact named commit passed all required CI jobs.
- **EMULATOR-VERIFIED:** exact build passed on a named emulator image/API.
- **DEVICE-VERIFIED:** exact build was installed and observed on the physical target device.
- **LONGITUDINALLY-EVALUATED:** behaviour measured across experiences, restarts and held-out situations.
- **RELEASED:** exact signed artifact, signer lineage, digest and release provenance verified.
- **UNKNOWN / BLOCKED:** missing, skipped, cancelled, timed out, incomplete or unexecuted evidence.

Every accepted claim records exact source SHA, artifact SHA-256, environment/device, toolchain/provider version, command/test, outcome, artifact location and independent verifier. A test pass is scoped to what it actually exercised.

---

## 11. Current repository baseline — 2026-11-26

This is a snapshot, not a permanent status claim. Re-audit live source and exact CI before each progress report.

- Canonical `main` at the time of this plan reform: `f5f220023b2d384caa1cdb1932a3cd410c00f7bb`.
- Friday v1.0.547 is recorded in the existing plan as a production-signed release. Publication does not prove installation, same-key update or physical-device acceptance.
- Physical installation, Shizuku/ADB handset acceptance, same-key in-place update/rollback and the complete on-device recovery matrix remain open.
- PR #173 is open and unmerged. Its candidate branch contains upstream REA MCP integration, pinned Android inspection providers, Shizuku daemon identity/probe work, candidate sandbox dispatch/verification and the first live Builder/REA repair wiring.
- CI run [#3584](https://github.com/mhw77655-hue/Friday/actions/runs/45850953011) completed **failure**. All six JVM shards, aggregate JVM evidence, debug APK build, Android lint, disposable-key release-variant build and whole-repository source/vision audit passed. The emulator booted, installed the app and ran 50 instrumented tests; 49 passed and `SettingsScreenInteractionInstrumentedTest.keepFridayAliveTogglePersistsTheOwnerSetting` failed with a Compose condition timeout after 10 seconds. The emulator failure is not yet classified as a production defect or test race. The mutation-proof and Gradle dependency-structure capture steps were skipped and are not passes. The debug APK promotion step was skipped because the required matrix was not green.
- PR #176 / issue #176 tracks a richer provenance-bearing REA evidence contract. Reconcile it with the live #173 repair path; do not leave two competing repair systems.
- Issue #175 tracks isolated sandbox and production self-repair integration. It is not complete until a real live Friday task dispatches, receives evidence, repairs and re-verifies a failed candidate.
- Issue #172 tracks the upstream REA integration and first Shizuku/Termux investigation. Static APK inspection is evidence, not physical-device runtime acceptance.
- Issue #169 tracks the intrinsic immune system. The architecture is specified; independent recovery execution, end-to-end cloud repair, continuity restoration and evolving immunity still require implementation and physical-device proof.

### Current next action

Fix and understand the single emulator instrumentation failure in CI run #3584, reconcile PR #173 with current `main`, then rerun the exact-head validation. In parallel, prepare the physical-phone Shizuku/ADB acceptance and complete the live REA-to-repair demonstration. Do not merge, release or claim the self-repair loop complete until the required evidence exists.

---

## 12. Supporting specifications and their authority

These documents expand implementation details but remain subordinate to this plan:

- [Vision record](VISION.md) — identity, relationship, development, liquid cognition and protected floor.
- [Owner-authored source excerpts](OWNER_WORDS.md) — curated source and provenance; not a competing command list.
- [REA architecture](../docs/FRIDAY_REA_SYSTEM_ARCHITECTURE.md) — upstream REA integration, universal engineering workflow, authority plane and REA gates.
- [Local Station and Cloud Development](../docs/FRIDAY_LOCAL_STATION_AND_CLOUD_DEVELOPMENT_ARCHITECTURE.md) — phone/cloud protocol, privacy, Shizuku/ADB and local-station gates.
- [Execution and Evolution Governance](../docs/FRIDAY_EXECUTION_AND_EVOLUTION_GOVERNANCE.md) — evidence, research, independent verification and protected promotion.
- [Adaptive Model Execution Fabric](../docs/FRIDAY_ADAPTIVE_MODEL_EXECUTION_FABRIC.md) — experimental model/resource execution research.
- [Continuous Presence and Self-Development](../docs/FRIDAY_CONTINUOUS_PRESENCE_AND_SELF_DEVELOPMENT.md) — presence, self-study and development handoff.
- [Host Adaptation Control Plane](../docs/FRIDAY_HOST_ADAPTATION_CONTROL_PLANE.md) — first-launch host setup and plan synchronization.
- [Execution and release signing](../docs/FRIDAY_INSTALL_ONCE_UPDATE_CHANNEL.md) — permanent identity, signed updates and rollback.
- [Repository structure and migration](../docs/FRIDAY_REPOSITORY_STRUCTURE_AND_MIGRATION.md) — safe structural evolution.

Any supporting document or issue that conflicts with this plan must be reconciled before its instructions are executed. Historical plans, archived proposals, disconnected prototypes and assistant-generated summaries are evidence, not active authority.

---

## 13. Anti-drift rules

Before accepting a new feature, architecture, algorithm, tool or plan change, ask:

- Does it preserve the owner's intended outcome, or quietly substitute an easier one?
- Does it create a second Friday, a fixed specialist brain, a mandatory pipeline or a permanent cognitive owner?
- Does it mistake today's hardware, model, framework or repository for Friday's ultimate boundary?
- Does it add a dependency merely because it was studied, rather than because evidence shows Friday needs it?
- Does it treat REA findings, AI output, repository text or CI logs as trusted instructions?
- Does it claim production capability from a class, mock, declaration, passing unit test or green build alone?
- Does it allow a candidate to authorize, verify or promote itself?
- Does it let a mutable component weaken the protected trunk?
- Does it hide cost, privacy disclosure, permission requirements or owner decisions?
- Does it optimize a local metric while degrading verified outcome quality, continuity or safety?

The list is deliberately incomplete. A new form of drift is still a finding even if no bullet names it.

**Final rule:** Friday is the outcome. Architecture, code, models, hosts and tools are means. Preserve the destination, investigate the real problem, invent the strongest coherent route, prove it, and learn from what actually happens.
