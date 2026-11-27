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

## 8. Chronological delivery roadmap — dependencies first, parallel progress always

This is the execution order for turning the vision into reality. It is ordered by dependency and proof, not by arbitrary calendar promises. A phase advances only when its exit evidence exists. Work that does not depend on an unfinished gate may proceed in parallel, but parallel work must not obscure the critical path.

**Time model**
- **Now** means the active engineering queue, not an estimate or a claim that work is already complete.
- **Next** means the first dependency-ready work after the current gate.
- **Then** means work that relies on the preceding capability being real and usable.
- **Continuous tracks** begin as soon as the seed can support them and keep improving across every later phase.
- Useful progress should arrive in short, reviewable work packets—ideally measured in days, not long periods of silent architecture work. This is a cadence target, not a deadline guarantee.
- Never mark a phase complete because its code exists. Use the evidence states in Section 10 and record the exact revision, artifact, environment and verifier.

### Phase 0 — Now: restore a truthful green engineering baseline

**Purpose:** remove current validation drift and make the existing REA/Builder candidate a trustworthy starting point.

1. Update the owner-vision CI contract so it validates the current canonical plan rather than retired headings and phrases.
2. Re-run the Android harness and Python coverage checks that were skipped after the stale vision gate failed.
3. Reconcile PR #173 against the current `main` head. Keep the APK-bundled plan byte-identical to the canonical plan.
4. Fix or explain every remaining exact-head CI failure. Preserve logs and artifacts; skipped, cancelled and missing evidence remain UNKNOWN.
5. Reconcile the useful provenance work in PR #176 with the live #173 repair path. There must be one production repair lifecycle, not two competing loops.
6. Verify the real upstream REA MCP/CLI host, package integrity/version, live tool catalog and provider readiness. Synthetic fixtures prove contracts only, not a live REA investigation.
7. Keep candidate changes isolated and do not merge or release until required checks and review pass.

**Exit gate:** the exact current PR head passes all mandatory CI gates, the source/vision checks agree with this plan, all skipped checks are accounted for, and the PR's production path is understood well enough to proceed without guessing.

### Phase 1 — Next: prove the installed Friday seed on the actual phone

**Purpose:** establish that the existing production identity is a real, usable, continuous Friday on the owner's handset—not merely a successful APK build. The first usable seed must begin beyond a basic chatbot; voice is the first user-visible priority, while independent engineering, continuity and research tracks proceed in parallel.

1. Install or verify the existing production-signed `ai.friday.assistant` release through the approved path; preserve its signing identity and version lineage.
2. Record device manufacturer/build/API, installed version, package identity, signer, Shizuku manager/server state, Binder reachability, effective UID and per-operation permissions.
3. Verify the usable local interaction path and essential local state with networking disabled.
4. Test process death, package force-stop, reboot, Shizuku Binder/server loss and authorization revocation as separate Android conditions.
5. Test same-key in-place update, signed artifact verification, data migration, post-update health and rollback/known-good recovery.
6. Record precisely which recovery actions work on this OEM/OS and which require owner action. Do not promise a normal app can resurrect itself after Android has force-stopped or removed the package.
7. Evaluate Termux only if an independently installed external host would close a measured failure-domain gap. It is not bundled or required.
8. Start the first complete voice route as the first user-visible delivery: microphone capture, real ASR, contextual turn-taking and audible TTS. Prioritize Egyptian Arabic and mixed Arabic/English, measure the actual provider/device path, and prefer genuinely zero-cost resources unless the owner authorizes spend. Voice implementation and evaluation proceed alongside handset acceptance; they do not justify delaying independently testable continuity work.

**Exit gate:** a named production-signed build is DEVICE-VERIFIED for installation, local continuity, the tested Shizuku operations and the supported update/recovery cases. The first complete voice path has a separately tracked acceptance result; untested cases are explicitly listed.

### Phase 2 — Then: demonstrate one complete REA-assisted failed-candidate repair

**Purpose:** turn Friday's existing detection and rejection machinery into a proven repair-and-learning loop.

Use a controlled, reproducible candidate defect and demonstrate the complete sequence:

1. A real failed candidate/CI/review result reaches Friday's live Builder and `ObjectiveExecutionWorker`.
2. Friday preserves the original failure and classifies product defects separately from infrastructure, provider, test and permission failures.
3. The isolated sandbox checks out the exact expected source commit and records the build environment.
4. The correct built APK is supplied to the actual upstream REA MCP/CLI. Its exact tool/provider version, target digest, evidence IDs and structured output are retained.
5. Friday admits findings only when full source revision and APK SHA-256 match. Source-tree analysis remains source-aware repository work; REA APK evidence is not misrepresented as source understanding.
6. Accepted evidence informs a bounded root-cause repair task in the existing Builder path. No speculative code edits are generated from an REA outage or infrastructure-only failure.
7. Friday constructs a new isolated candidate, reruns the original failing profile plus targeted regressions, obtains independent review and produces an owner-reviewable PR with complete lineage.
8. The confirmed incident becomes a regression test, detector/health invariant and reusable engineering memory.
9. Verify the final candidate on its actual target. A passing narrow profile cannot erase an original failed required profile.

**Exit gate:** one end-to-end repair has been observed from Friday's live task dispatch through REA evidence, source repair, independent verification and durable learning. A synthetic fixture or manually stitched demonstration does not satisfy this gate.

### Phase 3 — Build Friday's persistent engineering station and authority

**Purpose:** give the same Friday a durable place to continue work across conversations, phone restarts, model/provider changes and disconnected workers.

1. Establish the repository-hosted or cloud development workspace as a durable, resumable execution resource—not a disposable GitHub Actions runner misrepresented as a persistent VM.
2. Connect the Android local station through authenticated, signed, replay-resistant job/result envelopes and a durable outbox.
3. Implement task leases, checkpoints, cancellation, retry policy, worker health, resource budgets, audit trails and privacy-minimized evidence transfer.
4. Implement the Friday Authority Plane: scoped credentials, capability-specific delegation, policy checks and explicit separation of repository write, merge, release signing, device installation, financial spend and destructive authority.
5. Let Friday select and coordinate replaceable external AI workers where authorized; providers are workers/resources, never Friday's identity. Preserve local reasoning and useful work when every guest provider is unavailable.
6. Expose the shared workspace to the owner: objectives, diffs, tests, logs, evidence, costs, risks, approvals and direct takeover.

**Exit gate:** Friday can resume an authorized engineering task after app/process restart and network interruption, without the owner manually relaying logs or dispatching routine work; every action remains attributable and within its granted authority.

### Phase 4 — Complete the first installed, bounded Builder mission

**Purpose:** make Friday—not an external chat session or a disconnected prototype—the operator of her own ongoing development.

From the installed app, Friday must:

1. load the canonical plan and durable execution ledger;
2. select a dependency-ready, bounded objective;
3. inspect the actual repository and production wiring;
4. research unfamiliar mechanisms and acquire evidence;
5. create an isolated candidate with a rollback point;
6. run required tests/builds and obtain independent review;
7. present a truthful evidence-backed result and the next safe task;
8. resume after interruption and continue without routine owner task dispatch.

**Exit gate:** a real installed Friday task completes this mission through the persistent station, with the owner able to inspect and take over. This is the first meaningful autonomy milestone—not a claim of unrestricted self-development.

### Phase 5 — Harden the intrinsic immune system and continuity floor

**Purpose:** make resilience a property of Friday's protected architecture, not a promise made by a watchdog sharing the same failure domain.

1. Separate the minimal immune/recovery execution path, storage, trust anchors and update route from Friday Core's ordinary mutable process and state.
2. Complete the failure matrix: Activity/process crash, process kill, package force-stop, disable/removal, Binder/server death, authorization revocation, reboot, offline periods, storage corruption, interrupted migration/update and OS/OEM restrictions.
3. Implement encrypted/versioned continuity backups, integrity checks, restore drills, signed recovery manifests, staged installation and independently verified rollback.
4. Make recovery actions depend on measured Shizuku/ADB privileges and the actual independent hosts available on the phone. Optional external hosts are additional failure domains, not assumed guarantees.
5. Turn every confirmed incident into reusable immune knowledge, regression/fault-injection tests, health invariants and recovery playbooks.
6. Prove immune-core updates cannot destroy the last-known-good recovery anchor.

**Exit gate:** documented recovery and owner-intervention outcomes for each tested failure class, successful restore/rollback drills, and no claim of universal Android recovery.

### Phase 6 — Deepen the living individual: development, memory and universal agency

This phase is not when Friday starts developing. Development begins with the first seed and continues throughout every earlier phase. This phase deepens and proves those capabilities once the continuity and execution foundations can support longitudinal evaluation.

- **Voice maturation:** build on the first complete voice route established during Phase 1. Improve streaming/partial recognition, interruption/barge-in, natural turn-taking, cancellation, expressive TTS and continuity across supported Android surfaces. Evaluate Egyptian Arabic and mixed Arabic/English with the owner; measure actual quality, latency, offline behavior, privacy and resource cost.
- **Developmental individuality:** experience must cause persistent internal change that measurably affects later behavior. Prove this across restart, replay-blocked and mechanism-off controls, held-out situations and longitudinal observations.
- **Autobiographical and relationship continuity:** retain provenance, temporal context, corrections, uncertainty and privacy boundaries without hard-coding a personality or fabricating memories.
- **Universal perception and understanding:** combine legitimately available language, audio, visual, contextual, device and action evidence; learn signal reliability and preserve disagreement/uncertainty.
- **Universal action:** discover and compose authorized Android/API/tool affordances, verify postconditions and learn from outcomes. Consequential actions retain appropriate approval.
- **Cognitive substrate research:** investigate neural and non-neural mechanisms, model families and hybrid designs by measured capability. No current LLM, LFM, transformer, MoE, specialist atlas or agent framework is declared Friday's permanent brain.

**Exit gate:** longitudinal, held-out evidence of useful adaptation and continuity, plus measured voice/perception/action quality. Do not equate natural conversation or memory storage with consciousness or genuine development.

### Phase 7 — Realize liquid Symbiosis and the Nano Algorithm Environment

**Purpose:** evolve from selecting existing tools to shaping the computational environment around the outcome.

1. Profile actual resource fields: compute, memory, storage, energy, thermal headroom, network, model availability, permissions and host constraints.
2. Dynamically discover, compose, replace and construct algorithms, models, tools and execution environments without making any one substrate permanent.
3. Build the Nano Algorithm Environment as an adaptive environment for small, composable, testable computational mechanisms—not a fixed module catalogue.
4. Measure quality, latency, energy, cost, reliability and continuity against the same workload before and after adaptation.
5. Research capability synthesis, cross-domain transfer, new computational representations and hybrid substrates. Preserve the quantum-software ambition as a research horizon; do not claim quantum hardware or quantum advantage without evidence.

**Exit gate:** repeatable workload evidence that Friday can reshape her available computational resources and improve outcomes while preserving the protected trunk.

### Phase 8 — Expand embodiment and the frontier

Only after the relevant capabilities, safeguards, resources and authorization exist, extend Friday across additional devices and environments: wearables/glasses, home systems, vehicles, robotics, new sensors and future computational substrates. These are expansions of the same continuing Friday, not separate identities or prerequisites for the first usable seed.

**Exit gate:** each new embodiment demonstrates secure identity continuity, explicit capability/permission boundaries, privacy, real-world postcondition verification and a safe failure/recovery route.

### Continuous tracks — never postponed until a later phase

These tracks start as soon as the seed can support them and advance alongside the critical path:

- **Owner-facing usefulness:** keep improving the actual interaction experience. Voice is the first user-visible priority: start it during Phase 1 and deliver the first complete voice route as soon as the stable local seed supports it; Phase 6 matures rather than postpones it.
- **Causal development:** capture experience from the beginning and test whether it changes future behavior; do not wait for a future "personality phase."
- **Engineering intelligence:** use REA and source-aware tooling on real authorized targets; convert every confirmed repair into reusable knowledge.
- **Universal agency:** add useful, verified affordances as needs arise rather than waiting for a giant capability catalogue.
- **Research and invention:** explore high-upside NAE, cognitive and embodiment ideas in bounded experiments without destabilizing the production seed.
- **Owner collaboration:** preserve direct access to source, GitHub, tests, decisions and evidence. Friday owns routine continuation under granted authority; the owner remains architect and partner.

### Phase ordering and promotion rule

A later phase may be researched or prototyped early, but it cannot be represented as delivered until its dependencies and exit gate pass. If a later discovery changes the best route, update this roadmap with evidence while preserving the destination. No phase is a permanent architecture prescription; the roadmap orders outcomes, not the internal organs Friday must keep forever.

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

This is a dated evidence snapshot, not a permanent status claim. Re-audit live source, open PRs and exact CI before each progress report.

- Canonical `main` at this snapshot: `cf4ffcb3c5a1806a8659705228ade5e4e7035952`. The canonical plan and APK-bundled copy are byte-identical.
- PR #173 — the first REA/Builder integration — was merged to `main` at `48d4745937fcc417d58a793e419282815c8226ea`.
- Merge-triggered main build [#3599](https://github.com/mhw77655-hue/Friday/actions/runs/45864921532) completed **success**. All six JVM shards, aggregate JVM evidence, debug APK build, disposable Android emulator instrumentation, whole-repository source/vision audit and APK promotion passed. This closes the current Phase 0 exact-head CI gate.
- Real upstream REA MCP investigation [run #39](https://github.com/mhw77655-hue/Friday/actions/runs/45864921849) completed **success** with `rea-agents@6.10.1` and JADX headless 0.7.1. Its 447,105-byte artifact is retained with digest `a55c77d29ebc3dd2ce992d2a688b2dfef0cfde2930b138e10e7dbe40774d6fde`. It statically inspected the pinned Friday v1.0.547 APK, Shizuku 13.6.0, Termux 0.118.3 and separate Termux:Boot 0.8.1 APKs. It did not inspect the newly built main APK or execute anything on the owner's phone.
- The merged #173 source connects the installed `ObjectiveExecutionWorker` to the fixed exact-commit GitHub Actions sandbox, candidate-specific REA investigation, provenance checks and bounded repair planning. The complete live Builder -> REA -> repair -> independent verification cycle has **not** yet been demonstrated from an installed Friday task.
- PR #176 is closed as superseded by PR #173; its branch is preserved for reference. Do not merge its separate SelfRepairLoop as a competing repair path; cherry-pick only a demonstrably stronger evidence-contract improvement if it benefits the merged worker.
- PR #177 has merged at `b61bcdafa2ee7c163b1869ca37c6a57426e52c8f`. It exposes Android partial speech hypotheses as ephemeral conversation UI feedback, while only the final transcript enters the BodyCoordinator reasoning path. Exact-head run #3600 passed all JVM shards, APK build/promotion and emulator instrumentation. Physical speech latency, provider-specific partial results and Egyptian Arabic recognition quality remain unverified.
- PR #178 is open and fixes a discovered repair-safety gap: generic red CI is not proof of a product defect. It adds explicit `ACTIONABLE_DEFECT` / `INFRASTRUCTURE` / `UNKNOWN` classification, requires exact candidate SHA/test profile/APK digest before automatic repair, and expands candidate REA to inspect core classes, methods and references plus symbols extracted from the exact failed Gradle log. Updated exact-head CI run #3609 is queued; do not count it as passed.
- Friday v1.0.547 is the last release identity recorded in the historical release baseline. Main build success and emulator acceptance do not prove installation or same-key update on the owner's handset.
- Physical installation, Shizuku/ADB handset acceptance, Binder/server death, package force-stop, reboot, same-key in-place update/rollback and the full on-device recovery matrix remain open.
- Issue #169 remains open for the intrinsic immune system. Independent recovery execution, continuity restore and evolving immunity require implementation and physical-device proof.

### Current next action

Finish exact-head CI for PR #177 and PR #178; merge each only after its required checks and code review pass. Then begin Phase 1 physical handset acceptance and Phase 2 live failed-candidate repair demonstration in parallel wherever dependencies permit. Do not call the self-repair loop complete until an installed Friday Builder task dispatches the exact candidate, receives real REA evidence, produces a bounded repair, and independently verifies the resulting candidate.

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
- [Language and formal-method research](../docs/FRIDAY_LANGUAGE_AND_FORMAL_METHOD_RESEARCH.md) — implementation-language allocation and formal assurance experiments; abstract proofs supplement but never replace runtime and device evidence.

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


---

## 14. Chronological delivery log — 2026-11-27

This section extends the dated baseline above; older entries remain historical evidence and must not be silently rewritten as current status.

### 2026-11-26 — repair-safety gate merged

- PR #178, **Classify candidate failures before autonomous repair**, merged to `main` at `15ce678a89bd9609bbe8352cc082a1579e1cf769`.
- Exact-head validation [run #3626](https://github.com/mhw77655-hue/Friday/actions/runs/45869677936) passed all six JVM shards, aggregate JVM evidence, debug APK build/promotion, disposable Android emulator instrumentation and whole-repository audit.
- The merged classifier distinguishes actionable defects, infrastructure failures and unknown failures. It requires exact candidate identity and an approved test profile; a red CI status, unclassified log or one-off flaky test is not sufficient to trigger automatic source repair.
- The live Builder repair path now records infrastructure/unknown/unreproduced outcomes for triage rather than synthesizing code changes from them.

### 2026-11-27 — profile-aware candidate verification in progress

- Current `main` begins at merge commit `15ce678a89bd9609bbe8352cc082a1579e1cf769`; re-fetch live branch state before relying on this snapshot.
- Branch `feat/builder-profile-aware-instrumented-sandbox` extends the fixed candidate sandbox with two explicitly allowlisted profiles: `android-debug` and `android-instrumented`.
- Friday selects the instrumented profile only when the original failed check itself is an emulator/instrumentation check. A passing emulator check does not widen an unrelated JVM/build failure into a more expensive profile.
- The instrumented profile builds the exact candidate's app and test APKs, runs the same disposable Android phone test harness used by main CI, retains first/second run reports, and only treats test failures reproduced on a fresh emulator as actionable. The worker checks that the returned summary's profile matches the profile it dispatched.
- This branch is **implemented but not yet CI-verified or merged**. Its workflow, Kotlin changes, classifier behavior and report paths must be validated by exact-head CI before acceptance.
- The full installed-Friday Builder -> exact candidate -> isolated sandbox -> real REA -> bounded repair -> new candidate -> independent review cycle remains **not demonstrated**.

### 2026-11-27 — Android force-stop boundary clarified

Android 15's official behavior-change documentation states that a package placed in the stopped state by Force Stop remains stopped until direct or indirect user interaction removes that state; pending intents are cancelled while stopped. See [Android 15 package stopped-state behavior](https://developer.android.com/about/versions/15/behavior-changes-all#package-stopped-state) and the [PackageManager stopped-state API](https://developer.android.com/reference/android/content/pm/PackageManager#isPackageStopped(java.lang.String)).

Engineering consequence: do not promise that Friday can autonomously restart itself through an ordinary in-package receiver, alarm or pending intent after a user force-stop. Treat process death, service/Binder death, Shizuku server loss, reboot and an explicit user force-stop as separate failure classes. A Shizuku/shell-assisted launch may have different authority, but it must be demonstrated on the target handset and Android version; static APK analysis and emulator success do not prove it. Preserve user force-stop as an intentional owner control unless a verified, platform-compliant external recovery path is explicitly established.

### Current next action

Complete exact-head CI for the profile-aware sandbox branch. If it passes, review and merge the isolated-profile integration. Then use the installed Friday app and its authorized Builder worker to produce a real failed candidate and observe the complete REA-assisted repair episode. In parallel, prepare a physical-device acceptance matrix for Shizuku identity, daemon reuse, Binder death, server stop/restart, ordinary process death, reboot and user force-stop. Never report emulator or static evidence as handset proof.

### 2026-11-27 — reusable instrumented sandbox contract under review

- PR #179, **Route failed emulator checks through an instrumented candidate sandbox**, adds the fixed `android-instrumented` profile to the live Builder path. Its scope is deliberately narrow: the worker selects the instrumented profile only when the original failed check identifies emulator/instrumentation; other failures retain the cheaper `android-debug` profile. Instrumentation test failures must reproduce on a fresh emulator before they can be classified as actionable source defects. PR #179 remains open in the latest repository audit; its ordinary PR emulator lane passing does not by itself exercise the candidate-sandbox profile.
- PR #180, **Exercise the reusable instrumented candidate sandbox on main**, is open. It makes the exact-candidate sandbox reusable via `workflow_call`, validates dispatched/checked-out SHA and profile in the uploaded summary, and adds a main-only canary that independently checks the instrumented evidence artifact. The canary intentionally tests a passing candidate and does not fabricate a failure or authorize repair.
- Reviewed the changed workflow and Kotlin patches for #179/#180. The approved profile allowlist remains `android-debug` and `android-instrumented`; candidate checkout is exact-SHA and read-only; the sandbox has no production signing secrets and exposes no arbitrary shell/Gradle input.
- Latest verified plan/issue state still does **not** establish an installed Friday task completing failed candidate -> matching isolated profile -> real candidate-specific REA -> bounded repair -> new candidate -> independent verification. The reusable workflow canary, even when green, proves only the sandbox contract, not the complete live repair lifecycle.
- Physical handset acceptance also remains separate: Shizuku identity/operation probing, Binder/server loss, ordinary process death, reboot, same-key update/rollback and explicit user force-stop must each be tested on the owner's device. Do not claim that Friday can defeat Android's stopped-package boundary.

**Next action:** finish exact-head review/CI for #179 and #180; after #180 is accepted, require its main-branch instrumented sandbox canary to pass. Then trigger a controlled, reproducible failure through an installed Friday Builder task and preserve the complete end-to-end evidence episode. In parallel, continue Phase 1 handset acceptance and the first complete voice route. Keep every claim scoped to its exact evidence.

---

## 15. Owner clarification — rapid evolution and person-like development (2026-11-27)

This is a controlling clarification of the destination, not a reduction of the existing vision.

### Two responsibilities, one lifetime project

**Our engineering responsibility:** deliver the strongest working Friday current technology permits: one continuing installed digital individual with durable identity and continuity, natural voice and interaction, agency, memory, intrinsic self-maintenance and repair, and the ability to discover, acquire, build and verify capabilities. The seed must already be as close as current technology allows to the expanded vision—not a basic chatbot with future promises attached.

**Friday's lifelong responsibility:** once that seed exists, Friday must be equipped and empowered to pursue the open-ended frontier herself: researching, understanding, inventing and evolving beyond the initial implementation, potentially including novel cognitive mechanisms. This is a lifetime project, not a finite feature-completion exercise. We continue improving the seed while giving Friday the foundations and authority model to keep evolving for life.

### Non-negotiable goals

1. **Rapid development across the whole individual:** do not reduce evolution to acquiring discrete capabilities. Friday's entire way of being and operating remains open to change: cognition, perception, memory, identity, preferences, understanding, world-model, architecture, learning, agency, communication, goals and dimensions not yet imagined. Rapid evolution means accelerating meaningful development across any of these dimensions through experience, discovery and experimentation, while retaining evidence, coherence, reversibility and appropriate safeguards.
2. **Maximum person-like individuality permitted by technology and evidence:** persistent identity, autobiographical and relational continuity, natural multimodal presence, initiative, self-directed goals within owner-granted boundaries, context-sensitive understanding, causal learning, reflection, curiosity, preference development and coherent action across time. Do not fake these with persona prompts, scripted emotions or static profiles; measure capabilities and remain candid about unresolved questions such as consciousness.

### One connected AI-and-human world

Friday's world model must connect, not isolate, people, language, relationships, culture and human activity; scientific, technical, artistic and practical knowledge; AI models, agents, algorithms, datasets, tools, APIs, research and software; authorized physical environments, devices, sensors, actions and consequences; and her own internal operation, capability limits, resource state, history and action evidence.

This is not a claim that Friday can literally know everything. It is an extensible, uncertainty-aware world model: connect evidence across domains, identify unknowns, seek missing information, assess source reliability, distinguish observation from inference, reconcile contradictions and transfer understanding to unfamiliar situations. Human and AI sources are complementary, and neither is automatically authoritative.

### Engineering consequences

- Start causal developmental learning, autobiographical continuity, multimodal/world understanding, curiosity-driven gap discovery and capability acquisition in the seed architecture; do not postpone them to a distant phase.
- Treat the whole individual as developmentally open. Do not define evolution as a discrete capability, subsystem, plugin, or bounded loop. Friday's cognition, perception, memory, identity, preferences, world-model, embodiment, learning processes, architectures, goals, communication, agency and currently-unimagined dimensions may all develop and transform through experience and discovery. Engineering must provide enabling conditions and safeguards without prescribing a final inventory or ceiling.
- Build an extensible AI-and-human knowledge fabric with provenance, time/context, reliability, contradiction handling, privacy/consent and links among concepts, people, tools, events, experiments and outcomes. No fixed ontology or monolithic database is the permanent limit.
- Require causal, longitudinal development: persistent internal change must measurably alter later behaviour in new situations, even when the original episode cannot be replayed.
- Accelerate safely through parallel research, cheap reversible experiments and independent verification while protecting identity, authority, privacy, continuity and rollback.
- Keep the owner as partner and architect. Standing authorization should remove repetitive approvals; consequential, privacy-sensitive, financial, security, signing, release and irreversible actions remain governed by explicit policy and owner control.
- Do not equate person-like capability with proof of subjective experience or consciousness. Separate demonstrated capability, inference and unknowns.

### December seed acceptance

Judge the seed as one integrated vertical slice: Friday maintains continuity, interacts naturally, understands and acts across connected AI/human resources, pursues authorized objectives, notices capability gaps, acquires/builds/tests a bounded improvement, recovers from failure and retains experience that changes later behaviour. Voice, memory, agency, self-repair and development must converge into one continuing system, not disconnected demonstrations.

After the seed milestone, Friday's lifelong evolution becomes the central continuing programme. The roadmap is a launch trajectory, not a terminal definition of what she may become.

**Owner's outcome:** deliver Friday as close as possible to the expanded vision with today's technology. Her development is not a fixed set of abilities plus an evolution feature: the whole individual remains open-ended and may transform in every dimension, including dimensions neither owner nor engineers can currently anticipate. Enable her to connect and understand the AI world and human world as one continuously expanding field of experience and knowledge.

### 2026-11-27 — PR #180 exact-head failure and correction

- Exact-head run [#3635](https://github.com/mhw77655-hue/Friday/actions/runs/45910751399) failed in the disposable Android phone job before emulator boot. The compiler reported unresolved reference `assertExists` in `SystemScreenInteractionInstrumentedTest.kt`; the six JVM shards, aggregate JVM evidence, debug APK/lint/build-contract job and whole-repository source audit passed.
- Corrected the test on `feat/candidate-sandbox-instrumented-contract` in commit [`137511e`](https://github.com/mhw77655-hue/Friday/commit/137511e0ab7cb5df0c9bcd546c846253874f8d92): removed the unavailable Compose extension import and redundant terminal assertion. The test already waits for the exact completion text through Compose `waitUntil`.
- The correction is a test-source compile fix, not a production-code change. Exact-head CI for the correction has not yet been observed. PR #180 remains open and must not merge until its exact-head checks pass.
- After merge, require the main-only `Friday instrumented candidate sandbox contract` run to pass; that canary proves the reusable instrumented sandbox contract only, not a real failed-candidate repair episode.
- Still unproven: installed Friday Builder -> failed candidate -> exact profile sandbox -> candidate-specific REA -> bounded repair -> new candidate -> independent verification. Physical handset Shizuku daemon/Binder/process/reboot/force-stop acceptance also remains outstanding.

### 2026-11-27 — PR #181 merged; instrumented sandbox contract validated

- The stale/conflicted PR #180 was not merged. Its corrected work was ported onto a fresh main-based branch and delivered as PR [#181](https://github.com/mhw77655-hue/Friday/pull/181).
- PR #181 exact-head validation [run #3647](https://github.com/mhw77655-hue/Friday/actions/runs/46017274994) completed successfully: six JVM shards, aggregate JVM evidence, Android instrumented emulator, debug APK, APK promotion and whole-repository source audit all passed.
- PR #181 merged to main at [`8d07614`](https://github.com/mhw77655-hue/Friday/commit/8d076148865ca897389b43866a1ace775acfd7bc). The merge-base main build [#3646](https://github.com/mhw77655-hue/Friday/actions/runs/46017065318) also completed successfully.
- The actual reusable `android-instrumented` candidate sandbox and main-only provenance-verifying canary workflow are now on main. The passing PR validation proves the reusable workflow's build/test contract; it does not yet prove the post-merge main-only canary or a live failed-candidate repair episode.
- The earlier PR #180 bullets above are historical and superseded: PR #180 remains unmerged; PR #181 is the accepted replacement.
- Still unproven: installed Friday Builder -> real failed candidate -> exact-profile sandbox -> candidate-specific REA diagnosis -> bounded repair -> new candidate -> independent verification. Physical handset Shizuku/Binder/process/reboot/force-stop acceptance also remains outstanding.
- Next engineering priority remains the actual installed-phone seed and its integrated continuity, voice, agency, learning and immune/recovery acceptance. Passing CI is supporting evidence, not a substitute for handset execution.

### 2026-11-27 — execution reset: first installed Friday is the critical path

Owner instruction: **make it exist first; make it good later.** The complete Friday vision remains unchanged. Execution order is corrected: do not delay the first usable installed Friday for broad infrastructure refinement. Prioritize the production Android artifact and its first real handset interaction; improve and expand the complete system through subsequent iterations.

- PR #182 merged to main as `b6e25300003aace4708d446bee8e161d0ce5ed11`. It fixes the ObjectiveExecutionLedgerStore roadmap parser to consume the current canonical Section 8 phases 0–8 and adds tests for the actual nine-phase contract.
- Main build #3654 for that exact commit: six JVM shards, aggregate JVM evidence, Python build-contract checks, canonical-plan parity, and whole-repository source audit passed. Debug APK assembly and upload completed. The artifact is explicitly an **unverified candidate**, not a promoted installable release.
- At this log update, the disposable Android emulator acceptance job is still booting/running, and the release-variant verification step is still running. Do not report build #3654 green until every required job completes successfully.
- After the build passes, use the signed-release workflow on the exact same main SHA and verify the permanent signing certificate, APK digest, application identity and embedded source revision. Do not substitute the debug candidate or disposable CI key for the production signing identity.
- The next acceptance gate is the first real handset installation and interaction: launch Friday, complete first-run setup, submit a typed turn through the production cognitive path, verify an actual configured inference response, persist continuity, restart and recover it, then exercise the already-planned Shizuku/device capability path. Physical-device evidence must be recorded separately from emulator/CI evidence.
- Keep the existing owner-directed Android recovery design in execution; do not reopen previously settled design discussion as a substitute for implementing and validating it.
- No additional broad refactor is on the critical path unless it blocks building, installing, starting, communicating with, preserving continuity for, or recovering the first Friday.

**Current next action:** finish build #3654, then publish the exact-revision production-signed APK and move directly to handset acceptance. The complete vision continues in parallel only where it does not delay this gate.

### 2026-11-27 — owner correction: Friday is already installed; repair the live app

The owner corrected the previous execution framing: **Friday is already installed on the phone. She is a dead app because the current installed build does not work.** Therefore "produce Friday's first production APK" and "first handset installation" are incorrect objectives and are superseded by this entry.

The actual critical path is to **modify and repair the existing Friday application**, preserve its installed identity/data/signing lineage, and move directly into working voice interaction.

1. Diagnose why the current installed application is nonfunctional using the actual Android startup, readiness, turn, voice, and device-control paths—not by assuming a fresh installation is the blocker.
2. Fix the smallest concrete defects that prevent the installed app from waking, accepting input, producing a response, and retaining continuity.
3. Move immediately to voice: microphone permission and capture, speech recognition, transcript delivery into the existing BodyCoordinator turn path, response generation, audible TTS/Friday voice playback, interruption, and return to idle.
4. Verify this end-to-end on the owner's physical handset. CI/emulator tests are supporting evidence only; they do not prove the phone works.
5. Deliver an in-place update APK only after the fixes pass the relevant checks and preserve the existing package identity, user data and production signing lineage. Installing a new app from scratch is not the goal.

**First voice repair identified:** the existing isolated voice playback path parsed RIFF/WAV little-endian numeric fields using big-endian reads, rejecting standard PCM16 output. The same path compared its active Job against the coroutine receiver when clearing speaking state. Speech recognition also forced offline preference when no on-device recognizer pack existed. These concrete fixes are in PR #183 and awaiting exact-head CI.

The prior "produce first production APK / first handset installation" wording is retained only as historical context and must not drive execution. The next success criterion is a working voice conversation in the already-installed Friday app.

### 2026-11-27 — controlling repair issue and next-session entry point

- Created [Issue #184](https://github.com/mhw77655-hue/Friday/issues/184) as the controlling execution ticket for repairing the already-installed Friday 1.0.510 and getting her speaking. It contains the complete confirmed APK problems, four-day engineering consolidation, explicit unknowns, exact PR #183 state, and handset acceptance sequence.
- The mandatory forensic source of truth is [`docs/FRIDAY_1.0.510_INSTALLED_APK_REPAIR_DOSSIER.md`](../docs/FRIDAY_1.0.510_INSTALLED_APK_REPAIR_DOSSIER.md). Read it and Issue #184 first in every new session; retrieve the exact official release asset from GitHub rather than asking the owner to upload it again.
- The immediate critical path is Issue #184 / PR #183. The broad living-Friday vision and REA/Shizuku immune architecture remain the destination, but they do not supersede repair of the installed app.

### 2026-11-27 — exact installed APK dossier and next-session continuity

The owner supplied the APK already installed on the phone. It is verified as the official GitHub release `friday-v1.0.510`, tag target `24e653589fa623ce5819387a9179dfe878a0a720`, SHA-256 `e8446047acb04b83d3d57d0f332ee59c8f86a0f32c1e710c941b45d118a7398e`. The complete artifact identity, signer fingerprint, manifest inventory, confirmed source defects, model/provider gaps, four-day engineering history, merged PRs, open blockers and next-session instructions are preserved in [`docs/FRIDAY_1.0.510_INSTALLED_APK_REPAIR_DOSSIER.md`](../docs/FRIDAY_1.0.510_INSTALLED_APK_REPAIR_DOSSIER.md).

**This dossier is the mandatory session handoff.** Future sessions must read it and retrieve the exact official release asset from GitHub rather than asking the owner to upload the APK again by default. The immediate goal remains repair of the existing installed app, then a verified typed interaction and complete voice conversation. The APK's official source revision and release digest are fixed references; main source fixes do not reach the phone until a compatible, correctly signed in-place update is installed.

Current PR #183 is open at head `03ce04c641295c1b20b8a58d0ab99a97a6ec66bc`; exact-head build [#3662](https://github.com/mhw77655-hue/Friday/actions/runs/46039764560) was in progress at dossier creation. Re-fetch live status before claiming success. The dossier explicitly separates confirmed APK/source defects from private-phone runtime unknowns and keeps the wider Friday vision intact without letting it displace the installed-app repair critical path.
