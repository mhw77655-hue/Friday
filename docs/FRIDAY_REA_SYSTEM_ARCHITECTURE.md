# Friday REA — Reverse Engineer Anything Tool Integration and Adaptation System

**Status:** target architecture and implementation contract  
**Authority:** subordinate to `.ralph/FRIDAY_DELIVERY_PLAN.md`; this document expands its autonomous engineering, capability acquisition, continuity and owner-control requirements.  
**Owner outcome:** Friday can take responsibility for understanding, maintaining, repairing, extending and verifying her own system and owner-authorized applications and infrastructure.

## 1. The intended capability

**REA means the Reverse Engineer Anything tool**: the upstream REA project at https://github.com/morluto/rea, exposed to agents through its MCP server and CLI. Friday's REA capability is her ability to direct that tool, interpret its evidence, and use the findings to adapt, reconstruct, repair or extend authorized systems. Do not redefine the tool's name as “Reverse Engineering and Adaptation.” REA is a tool Friday uses—not a separate Friday, an Android feature, a one-off APK analyzer, or a fixed list of scripts.

Friday must be able to receive an engineering outcome in natural language, investigate unfamiliar software and systems, construct an evidence-backed model of how they work, choose a repair or integration strategy, implement it, validate it in an appropriate environment, and maintain the result over time. This applies to her own application and cloud infrastructure, repositories, and other applications or systems the owner owns or has authorized her to inspect and modify.

The same Friday coordinates every execution environment. Android is one host and sensor/actuator surface. Cloud workspaces, CI runners, virtual machines, containers, desktop systems, connected devices, local models, remote models and external tools are additional resources. No host, model, language, repository, framework or provider defines the boundary of Friday's intelligence.

The target interaction is: **"Friday, take care of yourself and your project."** Friday should turn that responsibility into a persistent, prioritized, observable engineering programme—not wait for the owner to translate every defect into file edits or commands.

## 1.1 Upstream REA tool and Friday's relationship to it

The named tool is **REA — Reverse Engineer Anything**, maintained at [morluto/rea](https://github.com/morluto/rea) and distributed as the `rea-agents` npm package. It exposes investigation workflows through MCP and a CLI. Friday must integrate with the real upstream tool and its documented contracts; she must not merely recreate a generic reverse-engineering architecture and call that “REA.”

REA can inspect supported software artifacts and runtime surfaces, return structured findings and evidence, and expose the limitations of each analysis. The agent then uses those findings to investigate further and construct/test an adapted implementation. Its supported targets and prerequisites are version-dependent, so Friday must query the installed tool's actual catalog/readiness rather than hard-code a stale list.

**The intended relationship is:**
1. Friday identifies a capability gap or a system she needs to understand (for example Shizuku, Termux, Android package lifecycle, an app, protocol, binary or service).
2. Friday selects the relevant artifact and runtime evidence that she is authorized to inspect.
3. Friday invokes upstream REA through its MCP interface or CLI on a compatible execution host.
4. REA performs the supported inspection and returns evidence, structured findings and explicit limitations.
5. Friday correlates those findings with documentation, live experiments and her own system model.
6. Friday derives the underlying mechanism and capability—not a blind copy of the target product's architecture.
7. Friday designs an implementation adapted to her own identity, security model, host constraints and capability contracts.
8. Friday builds, tests and independently verifies that implementation; only then may she promote it into her own runtime.

REA does not itself magically grant Android privileges or make a desktop analysis provider run inside the Android APK. The REA host, MCP connection, target device and any ADB/provider prerequisites are explicit resources. Friday should place REA on a suitable persistent/cloud/desktop/VM development host when the target analysis requires Node.js, JADX/JDK, ADB or native-analysis providers. Android remains a target and evidence source; it is not assumed to be the only execution host.

Termux, Shizuku and similar tools are **targets and reference implementations for REA investigations**, not automatic dependencies of Friday. Friday may use an existing authorized installation as a benchmark or execution environment when useful, but the goal is to understand and assimilate the relevant mechanisms into Friday's own architecture wherever possible. A dependency is retained only when there is a deliberate, measured reason—not because the tool was studied.

## 2. System architecture

REA is composed of cooperating, replaceable capabilities connected through durable contracts. These are implementation boundaries, not a rigid cognitive pipeline.

### 2.1 Intent and responsibility interpreter
- Convert owner goals into outcome contracts, constraints, quality bars and acceptance evidence.
- Preserve ambiguity and competing interpretations; ask only when the ambiguity changes a consequential decision.
- Maintain a living map of owned systems, delegated authority, dependencies, current health, objectives and unresolved risks.
- Distinguish "make it work", "understand it", "maintain it", "replace it" and "learn how it works" as different outcome types.

### 2.2 Universal artifact intake
Accept authorized inputs from repositories, source archives, APKs/AABs, desktop/mobile packages, binaries, libraries, configuration, databases, logs, crash dumps, screenshots, videos, network traces, API descriptions, documentation, running applications and connected devices.
- Identify formats, versions, architectures, languages, frameworks, build systems, signatures and provenance.
- Hash and preserve originals; work on immutable copies.
- Build an artifact inventory and relationships graph.
- Track missing inputs and confidence rather than silently treating partial evidence as complete.

### 2.3 Static understanding and reconstruction
Use language- and format-aware tools where available, with model-assisted interpretation as a replaceable layer:
- Parse source into syntax/semantic indexes, symbols, call graphs, data/control flow, module boundaries and dependency graphs.
- Inspect manifests, resources, bytecode, DEX, ELF/native libraries, package metadata, signing, permissions, build scripts, schemas and embedded assets.
- Recover API contracts, storage models, state machines, component relationships and external service dependencies.
- Identify dead, duplicated, unreachable, generated, vendored, legacy and security-sensitive code.
- For binaries without source, produce a confidence-labelled reconstruction: observable interfaces, probable internal structure, resources, dependencies and behaviour. Never claim source-level certainty from decompilation when names/types/logic were lost.

### 2.4 Dynamic observation laboratory
Run software in isolated, disposable environments selected for the target:
- Android emulators and physical devices; desktop OS virtual machines; Linux containers/VMs; browser automation; service sandboxes; hardware-in-the-loop where available.
- Observe UI state, accessibility trees, navigation, inputs/outputs, process lifecycle, logs, exceptions, resource use, file/database changes, API calls and network behaviour within authorized scope.
- Capture reproducible scenarios, checkpoints and traces.
- Compare multiple versions and environments to locate regressions and host-specific behaviour.
- Treat screenshots and a successful launch as evidence fragments, never as proof of full correctness.

### 2.5 Behavioural model and hypothesis engine
Combine static and dynamic evidence into a continuously revisable system model:
- Component and dependency graph.
- User journeys and state-transition graphs.
- Execution traces and data lineage.
- Contracts, invariants, failure modes and security boundaries.
- Hypotheses with supporting/contradicting evidence, confidence and next discriminating experiment.
- Explicit unknowns and untested regions.

The model must distinguish observed facts, source-derived facts, inferred behaviour and speculation. Every conclusion links to evidence and the exact artifact revision/environment that produced it.

### 2.6 Repair and construction planner
Given an outcome and the reconstructed model:
- Find root causes, not just failing assertions or visible symptoms.
- Compare repair, refactor, replacement, adapter, configuration, dependency and architecture alternatives.
- Select the smallest coherent change that meets the full outcome, while allowing larger redesign when the existing structure prevents durable correctness.
- Split work by independent boundaries, avoid duplicate/conflicting workers and maintain a dependency-aware task graph.
- Create missing tools, parsers, adapters, tests and capability packs when required.
- Keep changes in isolated workspaces/branches with known-good checkpoints and reproducible build inputs.

### 2.7 Independent verification and evidence
Verification is an independent capability, not the implementer's self-report:
- Compile/build and package using pinned toolchains.
- Run unit, integration, UI, instrumentation, contract, property-based, fuzz, mutation, security, compatibility, performance and resource tests as appropriate.
- Reproduce the original failure, verify the intended postcondition, and test adjacent behaviours for regressions.
- Compare before/after behaviour and resource cost.
- Verify signatures, artifact hashes, provenance, version, permissions and install/update/rollback behaviour.
- Test on actual target hosts when a claim depends on hardware, OEM behaviour, external credentials or real integrations.
- Report exact evidence, coverage, skipped checks, uncertainty and residual risks. A green workflow is not equivalent to a working product.

### 2.8 Durable engineering memory and project ledger
Persist an append-only event/evidence ledger containing:
- Goal, acceptance criteria, plan revision and task graph.
- Artifact hashes, source revisions, toolchain/environment versions and provenance.
- Findings, hypotheses, decisions and rejected alternatives.
- Workspaces, patches, commits, test runs, logs and independent reviews.
- Credential/authorization decisions without storing raw secrets in ordinary task records.
- Checkpoints, idempotency keys, approvals, deployments, rollback points and observed outcomes.

Friday must resume after app process death, host reboot, cloud worker replacement, provider outage or network loss without losing project state or repeating non-idempotent side effects.

### 2.9 Capability acquisition and self-extension
When Friday lacks a capability, she should be able to discover, install, configure, compose or build one:
- Search trusted sources and documentation; evaluate compatibility, licence, security, maintenance and provenance.
- Generate adapters, parsers, test harnesses, build recipes and restricted executable capabilities.
- Test candidates in isolated workspaces with explicit inputs, outputs, permissions and resource budgets.
- Promote only versioned, integrity-checked capabilities with evidence and rollback.
- Rehydrate approved capabilities after restart and verify them before use.
- Retire or replace capabilities when they become unsafe, obsolete or ineffective.

A class existing or a tool being listed is not proof it is wired into production or works.

### 2.10 Multi-host execution fabric
Friday chooses execution placement based on the work:
- Phone: real-device observation, Android-specific tests, permissions, local data and user-facing operation.
- Cloud: large repositories, scalable builds, parallel analysis, persistent orchestration and high-compute tasks.
- Desktop/VM/container: OS-specific reverse engineering, GUI automation, build/test toolchains and isolated experiments.
- CI: reproducible, revision-bound validation and release provenance.
- External AI/model/tool providers: replaceable reasoning or specialist capabilities, never trusted authorities.

Hosts advertise measured affordances, not assumed capability. Jobs are resumable and portable where practical. Android lifecycle constraints influence scheduling on Android; they do not constrain Friday's overall runtime or make her identity dependent on one process remaining alive.

## 3. Authority, permissions and authorization

Friday is intended to manage the complete owner-delegated project—not to be artificially restricted to read-only repository access. Build a **Friday Authority Plane** that can exercise broad standing authority within scopes explicitly delegated by the owner.

### 3.1 Unified authority registry
Represent each authority as a machine-readable grant:
- Principal: Friday identity and the specific execution worker acting for her.
- Resource: repository, cloud account/project, app, device, service, data set or environment.
- Actions: read, create, edit, execute, build, test, branch, commit, push, open PR, merge, deploy, release, administer, delete, spend or disclose.
- Scope: named repositories/projects/resources, environments and paths.
- Conditions: risk class, budget, time window, network/data constraints and required verification.
- Credential reference: brokered secret handle, never the secret itself.
- Expiry/revocation, provenance, audit trail and policy revision.

### 3.2 Credential and identity broker
- Use OAuth/GitHub Apps, cloud IAM service identities, short-lived scoped tokens, device-bound keys and provider-native delegated credentials.
- Obtain the broadest authority the owner has explicitly granted and the provider actually supports; do not silently assume access.
- Prefer short-lived, resource-scoped credentials; rotate and revoke them; keep secrets out of prompts, logs, source, artifacts and worker contexts.
- Workers receive task-scoped capabilities, not Friday's master credentials.
- Maintain independent recovery access so a broken automation path cannot lock the owner out.

### 3.3 Standing delegation, not repeated permission theatre
The owner may establish durable policies for routine project maintenance, code changes, test execution, branch/PR creation, dependency updates, cloud resource management and deployment to designated environments. Friday should act under those standing grants without repeatedly asking for the same authorization.

Require a fresh owner decision only where policy says the action exceeds delegated scope or crosses a protected boundary: new account access, privilege escalation, new sensitive-data disclosure, irreversible/destructive operations, material spending outside budget, production-impacting deployment without a pre-authorized policy, signing/identity changes, or actions affecting third parties. Explain the exact missing authority and offer the narrowest grant that resolves it.

### 3.4 Secure execution boundaries
- Treat repository files, issues, build output, web pages, binaries and model/worker output as untrusted data, not policy instructions.
- Execute untrusted code in disposable isolated environments with explicit filesystem, network, process, device and secret boundaries.
- Separate candidate generation from privileged promotion. The agent can prepare a change; a narrow promotion service enforces policy and evidence.
- Log every privileged action and its initiating objective, principal, scope, credential grant, result and rollback handle.
- Keep a break-glass owner path and emergency stop/revoke mechanism.

This is not a restriction on Friday's intended capability. It is the mechanism that makes broad delegated authority reliable, attributable and recoverable.

## 4. Project self-management loop

Friday's persistent operating loop:

1. Reconcile the canonical objectives with actual repository, cloud and installed-host state.
2. Discover drift, failures, outdated dependencies, security issues, missing capabilities and improvement opportunities.
3. Rank work by owner outcome, impact, confidence, risk, dependency, cost and urgency.
4. Select work she is authorized and equipped to perform; acquire missing capabilities where needed.
5. Investigate until the root cause or a clearly bounded uncertainty is established.
6. Create isolated candidate changes and durable checkpoints.
7. Run independent verification appropriate to the claim.
8. Review the exact diff, security impact, compatibility, costs and release provenance.
9. Promote within standing authority; request only the exceptional authorization actually needed.
10. Observe real outcomes, roll back when postconditions fail, and record lessons.
11. Update the objectives/evidence ledger so the same problem is not rediscovered and manually repaired.

The loop must detect no-progress cycles, repeated failed strategies, stale branches, conflicting agents, flaky infrastructure and false-green tests. It should change strategy when evidence says the current route is not working.

## 5. Universal reverse-engineering workflow

For any authorized app/system:
1. Acquire and hash the artifact/source; establish ownership/authorization and scope.
2. Inventory file formats, languages, frameworks, build chain, package identity, dependencies and platform requirements.
3. Build static indexes and dependency/execution graphs.
4. Establish a baseline by building or running the original where possible.
5. Exercise important workflows in a controlled lab and collect traces.
6. Correlate runtime observations with source/binary structures.
7. Form a behavioural specification with known facts, hypotheses and unknowns.
8. Identify the smallest experiment that can distinguish competing root-cause hypotheses.
9. Design a repair/reconstruction with explicit acceptance tests.
10. Implement in a clean isolated workspace; preserve original and baseline.
11. Verify build, behaviour, security, compatibility, resource use and recovery.
12. Produce a provenance-linked engineering report and maintain the resulting system.

For Android packages, this includes manifest/component analysis, DEX/resource/native-library inspection, permissions, exported components, intents, deep links, storage, lifecycle, network endpoints and UI flows. For source repositories, it includes build graph, production composition, runtime wiring, dependency health, tests, workflows, release/signing path and generated artifacts. Extend through plugins/adapters for other package formats and platforms rather than hard-coding Android assumptions into REA.

## 6. Repository and application structural integrity

REA must continuously improve the structure it works in:
- Maintain a machine-generated inventory and ownership map for every important directory/module.
- Derive dependency and runtime-wiring graphs; detect cycles, hidden coupling, dead code, duplicated implementations and boundary violations.
- Establish explicit module contracts and stable interfaces before extracting modules.
- Prefer incremental strangler migrations with compatibility tests over mass file moves.
- Keep generated, vendored, research, historical, production and test assets clearly separated.
- Pin and record toolchains, dependency locks, build inputs and artifact provenance.
- Add architecture tests and source-to-runtime wiring tests.
- Measure build time, test time, module coupling, change failure rate, flaky tests and rollback success.
- Make cleanup evidence-driven: remove code only after proving it is unused, superseded or intentionally retired, with history retained.

## 7. Delivery stages and proof gates

These are capability gates, not a claim that they already exist.

- **REA-0 — Truth inventory:** authoritative plan, repository map, production composition, workflows, credentials inventory by reference, release path and known defects.
- **REA-1 — Durable engineering workspace:** isolated worktrees/cloud workspaces, immutable artifact intake, task/evidence ledger, checkpoints and resume.
- **REA-2 — Universal understanding:** source/binary intake, language/build detection, static indexes, dependency graph and provenance-linked findings.
- **REA-3 — Dynamic laboratory:** Android/device and general VM/container/browser execution, trace capture, reproducible scenarios and baseline comparison.
- **REA-4 — Root-cause and repair:** hypothesis tracking, repair planning, candidate patch generation and regression test construction.
- **REA-5 — Independent verification:** separate verifier, exact-diff review, security/resource checks, target-host acceptance and signed evidence.
- **REA-6 — Authority Plane:** delegated identity/credential broker, policy engine, standing grants, audit, revocation and break-glass recovery.
- **REA-7 — Project operations:** persistent objective scheduler, GitHub/cloud administration, dependency maintenance, CI triage, release and rollback within grants.
- **REA-8 — Self-extension:** Friday can build and safely promote new REA adapters, tools and workflows, then rehydrate and use them after restart.
- **REA-9 — Self-maintaining project:** Friday continuously maintains her own app, cloud, repositories and authorized connected projects, reports only meaningful decisions/blockers and can prove each completed outcome.

Gates can be developed in parallel where dependencies permit. A gate passes only with production wiring and reproducible evidence; a document, class, unit test or green unrelated workflow alone does not pass it.

## 8. First implementation sequence for this repository

1. Reconcile the canonical plan and bundled offline copy with this REA target.
2. Inventory the existing builder, source index, objective scheduler, execution ledger, capability registry, sandbox, GitHub integration, cloud hooks, device station and release/update mechanisms. Identify actual production wiring and missing links.
3. Establish one persistent engineering task/evidence model shared across Android and cloud workers.
4. Implement the cloud engineering workspace and isolated repository operations with resumable tasks.
5. Connect the phone as a first-class device/host station to submit observations, receive authorized test/update tasks and verify results.
6. Build universal static intake and repository understanding, then Android APK/DEX/native analysis as the first concrete adapter.
7. Add dynamic test orchestration and trace-to-source correlation.
8. Wire repair planning, patch creation, independent verification and evidence-based promotion.
9. Implement the Authority Plane and expand delegated administration to the owner's GitHub/cloud resources.
10. Migrate existing subsystems into stable boundaries incrementally, keeping Friday installable and preserving the known-good release path.

Do not spend another cycle writing an architecture document without implementing the next executable slice. Each slice must name the production entry point, durable state, authority required, tests, observed result and next dependency.

## 9. Acceptance: the owner should not have to manually engineer Friday

The system is not complete when it can explain how it would fix itself. It is complete when, under a standing owner delegation, Friday can:
- Notice a real defect in her app or repository without the owner locating the file.
- Reconstruct enough of the system to identify the actual production failure.
- Decide on and implement a coherent repair.
- Build and test the exact candidate in isolated environments.
- Validate on the actual phone when the claim requires physical-device evidence.
- Repair her cloud/repository/CI infrastructure when that is the blocker.
- Preserve evidence and known-good rollback state.
- Deploy or release within the owner's standing policy.
- Confirm the installed/runtime result and update her own project ledger.
- Resume the work after process/host/worker interruption without restarting the whole investigation.

The owner must be able to inspect, pause, redirect, revoke or take over. Friday should not make the owner manually perform routine engineering steps that she has already been granted authority and capability to perform.

**Truth rule:** this document specifies the target and implementation contract. It does not certify any gate as implemented. Every capability must be marked planned, implemented, production-wired, tested, host-verified or released with exact evidence.
