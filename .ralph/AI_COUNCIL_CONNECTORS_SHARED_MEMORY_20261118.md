# Friday AI Council, Connector Hub and Shared Project Memory

**Status:** architecture proposal / implementation contract  
**Date:** 2026-11-18  
**Base:** main at `d1b4cc88d722fa420d5648db9284a30b15ac33e8`

## Owner outcome

Friday remains one persistent Android-resident individual. External models are optional advisers and tools she can consult; they do not become competing identities, replace her cognition, or own her memory. The owner should be able to add/remove providers and have each provider understand the project through a compact, locally maintained project context rather than repeatedly explaining it or sending the entire repository.

## What the current code actually provides

This proposal starts from the current implementation, not a greenfield fantasy:

- `ModelProvider` already defines generate, stream, health, load/unload, model metadata and token usage.
- `GenerateRequest` already carries messages, model ID, token limits, tool schemas and session ID.
- Existing production adapters include llama.cpp, Ollama and RemoteJarvis.
- `ProviderConfig` contains endpoint/model/auth fields. `ModelScreen` has a generic configuration dialog.
- `JevApiKeyStore` demonstrates AES-GCM encrypted credentials with a non-exportable Android Keystore key.
- `TurnPathAssembly` constructs `CloudModelRouter(emptyList())`. Therefore the current cloud reasoning pool is literally empty at production composition; this is not merely a hidden settings toggle.
- `CloudProvider.generate(prompt)` is synchronous and prompt-only. It cannot carry structured messages, context scopes, usage, cancellation, provider health or asynchronous network calls. It is too weak to become the permanent AI council contract.
- `ModelProviderType.CLOUD` is a generic stub, not a configured OpenAI/Anthropic/Google provider.
- Android memory is already SQLite-backed, with graph retrieval, embeddings, provenance, correction/supersession and forgetting. It must remain the local authority while alternatives are evaluated.

## Frenemy: reuse the model, not the desktop installation

Inspected `noblehacks/frenemy` v2 README, INSTALL, PRIVACY, RELEASE-STATUS, MCP adapters and relay core.

Reusable concepts:
- Every model can expose the others as callable tools; any connected model can be the driver.
- Consultation and write-capable delegated work are distinct.
- Worker jobs are isolated, observable, cancellable and return a result for review.
- Cross-model output is explicitly untrusted data, not instructions.
- Jobs have watchdogs, bounded output, process-tree cancellation, conflict-aware workspaces and explicit apply/recover operations.

Not portable as-is:
- Frenemy expects Node.js 18+, desktop CLI binaries and local authenticated sessions for Claude Code, Codex, Antigravity and Kimi. Its installer edits desktop user config files and its Snitch UI targets VS Code.
- Its privacy note says prompts/files go to local AI CLIs, and job records/output excerpts remain in local user directories. It is not an Android provider SDK or a credential-free universal API.
- Friday must not shell out to desktop tools that do not exist on Android, nor silently scrape other apps' private sessions.

Adapt the protocol ideas into Friday's native Kotlin architecture: an in-process connector registry, provider adapters, structured adviser tools, durable job ledger and review gates.

## Target architecture

```
Android Friday
  Identity + cognitive engine (one authority)
  Local SQLite memory + provenance ledger (authoritative)
  Project Context Pack (local, versioned, scoped)
  Connector Hub
    Provider Registry + capability descriptors
    OpenAI / Anthropic / Gemini / future provider adapters
    Optional MCP client transport (only explicit, trusted servers)
  Council Orchestrator
    Router -> task classifier -> budget/admission -> adviser selection
    bounded consultation -> critique/dedupe -> evidence reconciliation
    Friday decides -> memory promotion gate
  Connector Settings + usage/cost + job status
```

No provider is allowed to mutate Friday's identity, local memory, phone state or project files directly. Providers can return proposals, claims, tool calls and structured evidence. Friday's own policy gates decide what is accepted or executed.

## Provider access: honest compatibility contract

There are two distinct connection modes and the UI must label them honestly:

1. **Official provider API** (initial supported path): OpenAI API, Anthropic API, Google Gemini API, and later any provider with a documented supported API. User supplies their own provider API credential or supported OAuth credential. Credentials are encrypted at rest with Android Keystore; never in source, logs, analytics, exported backups or prompts. API usage may be separately billed and is subject to provider quotas. A ChatGPT Plus/Pro, Claude Pro or Gemini consumer subscription must NOT be represented as API credits or automatically accepted as an API key.
2. **Local companion / CLI bridge** (optional future path): only where a provider explicitly supports the platform, authentication flow and automation interface. Frenemy's desktop CLI approach is a reference; it does not establish that the same binaries/auth flows run on Android. No accessibility scraping, password extraction, private app database access or unsupported subscription bypass.

Provider adapters must be independently testable and report configured, reachable, quota-limited, unauthorized, unsupported and disabled states distinctly. A provider's refusal or exhausted quota is not success and should not be silently hidden by fallback.

## Connector Hub contract

Introduce an asynchronous provider contract instead of expanding the current synchronous prompt-only `CloudProvider`:

- `ProviderDescriptor`: stable ID, display name, auth mode, supported models, capabilities (streaming, tools, vision, structured output), region/data controls.
- `ProviderCredentialStore`: save/read/delete through a provider-scoped Android Keystore alias and encrypted preferences; never put secrets into `ProviderConfig.parameters`.
- `ProviderConnectionStatus`: unconfigured / checking / ready / unauthorized / rate-limited / unavailable / disabled.
- `CouncilRequest`: task ID, bounded task brief, allowed context references, output schema, max input/output tokens, deadline, sensitivity class, allowed tools and cancellation.
- `CouncilResponse`: provider/model, text or structured result, finish/error category, input/output/cached tokens, estimated cost when supplied by provider, latency, request ID, evidence/claims.
- `ProviderAdapter`: suspend generate/stream, health, model catalog, token counting where available, cancel, error normalization.
- All network I/O is off main thread; retries are idempotent and bounded. Honor Retry-After and provider-specific quota signals. No blind fan-out after a timeout if the prior request may still be running.

Use documented HTTPS APIs and provider-specific serializers. Keep model protocol differences inside adapters; do not assume all vendors implement OpenAI-compatible routes.

## Initial provider sequence

1. OpenAI Responses API adapter (GPT models) with usage metadata and tool-call mapping.
2. Anthropic Messages API adapter (Claude) with system/message conversion and usage metadata.
3. Google Gemini API adapter using its current supported agent/conversation API, not a stale legacy endpoint by default.
4. Provider-independent custom OpenAI-compatible endpoint only as an explicit advanced configuration, with compatibility limitations shown.

A provider may be registered without being the active conversation model. The Council can consult an adviser without switching Friday's primary model.

## Council behaviour and token/cost discipline

Default mode is **one model call**. The council is demand-driven, not a permanent panel of agents.

- **Local-first:** Friday's resident model and local tools handle ordinary conversation, memory retrieval, summaries, simple classification and routine tasks.
- **Escalate only on value:** consult another model only for an explicit owner request, high-impact/ambiguous decision, low-confidence result, capability gap, or a testable second-opinion policy.
- **Task-aware seats:** choose one specialist adviser based on task category. Use a second independent adviser only if disagreement, risk or uncertainty crosses a configured threshold. Never broadcast every prompt to every provider.
- **Sequential by default:** ask one adviser, evaluate, and only then escalate. Parallel calls are reserved for tasks where independent answers materially improve reliability and the budget permits.
- **Bounded brief:** send a task-specific context capsule, not the full transcript, whole project, entire memory database or unrelated phone data.
- **Structured answer:** request recommendation, assumptions, evidence/source pointers, uncertainty, counterargument and unresolved questions. Friday compares claims, not prose length.
- **Deduplicate:** hash normalized task + context version + model/prompt policy; reuse a recent valid result when scope and freshness match. Never reuse across different privacy scopes or stale project revisions.
- **Budgets:** per-task max providers, input/output token ceilings, estimated/actual spend ceiling, daily/monthly limits, concurrency cap, deadline and user-visible kill switch. If provider pricing/usage is unknown, report unknown rather than inventing cost.
- **Circuit breaker:** respect 401/403, 429, Retry-After, quota, billing and service outage. Do not repeatedly wake a known-unavailable provider.
- **Caching:** stable reusable prompt prefix and provider-supported prompt/context caching; keep dynamic user/project material separate. Cache only where privacy/retention settings allow it.
- **No idle council:** no scheduled polling, background agent chatter or unsolicited provider calls. Work starts from a concrete task/event.
- **No duplicate work:** one task owner, explicit job IDs, cancellation, deadlines, dedupe and state transitions. A failed/skipped/cancelled adviser must remain visibly failed/skipped/cancelled.

## Shared Project Context Pack — the answer to repeated explanations

Friday owns a local, versioned project knowledge pack. It is not a second mutable copy of the whole repo and not a provider-owned memory.

Initial pack sections:
- Owner-authored vision and approved owner words (source/provenance preserved).
- Current verified repository facts and architecture boundaries.
- Current build plan, open blockers, active PRs and CI evidence with exact commit SHA and timestamp.
- Architecture decision records and rejected approaches with reasons.
- A repository manifest: module map, key entry points, interface contracts, dependency graph and source file references.
- Selected source excerpts retrieved by task, each with commit SHA/path/line range and content hash.
- Open questions and explicitly unknown facts.

Context assembly rules:
- Always include a tiny identity/authority header and pack version/hash.
- Retrieve only relevant sections for the current task using local lexical + embedding + graph retrieval.
- Include provenance and freshness; do not present stale plan text as current truth.
- Provide provider adapters a read-only, scoped context object. They cannot fetch arbitrary local files or the whole project.
- Send source text only when required; prefer file/path/line references and concise summaries for planning.
- Persist provider outputs as untrusted candidate evidence. A model answer is not an owner instruction, repository fact or verified test result.
- Promote only reconciled, useful facts through the existing memory provenance/correction/forget pipeline. Preserve contradictions and uncertainty rather than silently overwriting.
- A user correction supersedes model suggestions; forgetting must propagate to context-pack indexes and provider caches where deletion is supported. If remote provider retention cannot be controlled, warn and do not send sensitive material.

The project should be accessible to Friday on her own phone as an app-private, offline-readable context pack. The source workshop remains the authority for actual repository checkout, CI, emulator, patch construction and promotion. A phone context pack is not falsely advertised as a full compiler/workshop or as an automatically synchronized complete repository.

## Omnigraph evaluation

Omnigraph's typed ontology, versioned graph branches, merge review and vector/BM25/graph retrieval are promising for cross-agent shared knowledge. Its current project describes an object-storage-native Rust graph engine using versioned Lance data, an MCP/agent skill, and a Cloud service in private preview; Studio is desktop-oriented.

Do not add an unverified Rust daemon to the Android APK or replace SQLite based on marketing claims. Evaluation gate:
1. Inspect deployment, storage, auth, network and license requirements.
2. Determine whether the engine/server can be built and supported on Android arm64 without an always-on paid cloud or a separate server.
3. Benchmark memory/RAM/CPU/storage and query latency on a real target phone.
4. Prove offline behaviour, encryption, backup/restore, schema migration, branch merge conflict handling and deletion/forget propagation.
5. Compare against Friday's current SQLite graph + vector retrieval using the same real corpus and relevance tests.
6. If Android-native deployment is unsuitable, retain SQLite as the local source of truth and optionally use Omnigraph as a syncable, user-controlled graph service later. No mandatory cloud dependency.

## Privacy and trust boundaries

- Provider data egress is explicit and visible. Per provider, show what data categories may leave the phone and allow a provider to be disabled.
- Default share scope: task brief + minimum project excerpts. Never include microphone/camera/location/contacts/notifications/whole screen/private conversations by default.
- Credentials are isolated by provider, encrypted with Keystore, redacted from exceptions and never passed to another provider.
- Provider output is untrusted. Prompt injection inside returned text, files, code, web citations or MCP output cannot change system policy.
- Advice-only tools are read-only. Work tools are separate, scoped, cancellable and return patch artifacts for review; no provider can merge, sign, release, install or execute privileged phone actions.
- Every connector call records provider/model, task ID, context-pack hash, scopes shared, status, token/cost metadata and retention policy — never raw secret values.
- Owner can inspect, export and delete local connector history; provider-side retention is disclosed separately.

## Dependency-ordered implementation plan

### Phase 0 — repair the current CI foundation
- Merge/validate the canonical build workflow restoration in PR #93 only after the exact head SHA passes APK build, JVM tests, full source audit and disposable emulator instrumentation.
- Do not treat the repeated failed main workflow runs as green. Preserve failed/cancelled/skipped states in evidence.
- Re-run the exact failed PR workflows after the canonical pipeline is on main.

### Phase 1 — connector domain model and secure credentials
- Replace the empty production cloud pool with an injectable provider registry while keeping tests and no-provider behaviour honest.
- Design async adapter interfaces and normalized status/error/usage types.
- Add provider-scoped Keystore credential storage, migration-safe configuration and redaction tests.
- Add fake adapters and contract tests before any live API key is needed.

### Phase 2 — direct provider adapters
- Implement OpenAI, Anthropic and Gemini adapters one at a time against official docs.
- Add HTTP mock tests for serialization, streaming, tool calls, usage accounting, 401/403/429/5xx, malformed payloads, cancellation and timeout.
- Add opt-in live smoke tests that do not run in public CI and never require secrets in repository settings.

### Phase 3 — local context pack
- Build versioned ProjectContextPack from curated owner vision, current repo facts, plans, architecture decisions and source manifest.
- Add local retrieval, freshness and provenance; test pack rebuild and offline retrieval.
- Add a read-only context capability to connector calls; enforce sensitivity filters before serialization.

### Phase 4 — council orchestrator
- Add demand-driven task classification, adviser selection, sequential escalation, structured critique and claim reconciliation.
- Add budgets, dedupe, circuit breakers, concurrency limits, cancellation and job lifecycle UI.
- Persist candidate responses as untrusted evidence; only Friday's promotion gate may update shared memory.
- Tests must prove ordinary turns make zero adviser calls, one-adviser tasks do not wake all providers, duplicate requests reuse safe cached results, and quota exhaustion stops retries.

### Phase 5 — Connector UI and project continuity
- Add provider cards for setup, test connection, selected model, privacy scope, status, usage/cost limits and disconnect.
- Add an AI Council screen showing queued/running/completed/failed/skipped jobs and each provider's contribution.
- Add a Project Knowledge screen showing pack version, sources, stale facts, sync status and what would be shared before a call.
- Make the same project context accessible to every connected provider without requiring the owner to re-explain Friday.

### Phase 6 — Omnigraph proof-of-fit
- Complete the local Android feasibility and benchmark gate above.
- Prototype only behind the existing memory-store interface and run parity/relevance/forget tests.
- Migrate no production memory until a reversible migration and device-level evidence pass.

## Acceptance tests — non-negotiable

- With no provider credentials, Friday still boots, converses locally, and clearly reports the council as unconfigured; no crash and no fake success.
- A single provider can be configured, verified, called, disconnected and have its credential erased.
- Two providers can review the same bounded task; Friday reconciles disagreement with evidence and records provenance.
- No provider receives another provider's credentials or arbitrary private phone data.
- Ordinary turns do not call the council; an explicit second opinion calls only the selected adviser.
- Token/cost limits stop calls before exceeding the configured ceiling; unknown costs are labelled unknown.
- Cancelled/failed/skipped jobs remain distinct and do not get reported as completed.
- Restarting Friday restores the same project pack and task state without re-explaining the project.
- Offline use still retrieves local project context and memory.
- Memory corrections, supersession and forget operations propagate to pack indexes and prevent forgotten facts from being reintroduced.
- Provider output cannot directly execute Android actions, modify the source tree or merge repository changes.
- The full APK, JVM and emulator test gates pass on the exact candidate SHA before integration.

## Explicit non-goals

- No attempt to make consumer subscriptions behave as API keys.
- No scraping other AI apps, extracting session cookies, or bypassing provider auth/quotas.
- No permanent multi-agent background loop.
- No fan-out to every provider on every user turn.
- No immediate replacement of SQLite memory with Omnigraph.
- No claim that a context pack equals the full repository checkout or that an Android phone is already a CI workshop.
