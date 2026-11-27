# Friday agent task relay — GitHub issue threads

Status: public GitHub transport, default-OFF consent/trusted-actor controls, durable local reply inbox, WorkManager polling and Settings handshake UI are implemented on `feat/github-task-relay-integrated` (PR #198, draft). The separate private SIWC worker is implemented in PR #197 (draft); owner-facing per-task approval UI, durable private-result recovery and handset acceptance remain pending.

## Why this channel exists

The owner speaks directly to Friday on the phone. Friday is the continuing individual and owns her identity, local memory, continuity, cognition, permissions, action policy and owner-facing voice.

ChatGPT is a separate collaborating cloud agent. Friday must be able to ask ChatGPT for bounded engineering/research work, receive progress/results, and expose carefully selected runtime evidence. GitHub provides durable shared task records that this ChatGPT engineering session can read and answer through the connected repository.

This is not a ChatGPT provider for Friday's ordinary owner-facing conversation.

## Data classification and channel separation

The Friday repository is public. GitHub Issues and comments in this repository are therefore **public, not confidential**. The GitHub relay is restricted to public project engineering context: task IDs, source revisions, public source paths, public test evidence, non-sensitive progress and public artifact links. It must never carry private phone snapshots, personal context, conversation history, memory, transcripts, audio, notifications, contacts, location, credentials or health data.

The current code requires `data_classification=public`, rejects obvious private-device field names and common credential-like strings, and has a separate default-OFF owner consent gate. These checks are defense-in-depth, not a complete PII classifier. This is a guardrail, not a guarantee that arbitrary text contains no personal data. The future Settings control must explicitly disclose that issue bodies/comments are publicly visible and require owner approval. Do not enable this relay silently.

Private reasoning tasks must use a separate authenticated SIWC ChatGPT worker adapter from Friday, with task-scoped context and explicit plan-use consent; private payloads must not be copied into GitHub. Live read-only phone snapshots must use the separate authenticated private device broker in #188, never public issue comments. These are adapters of one Friday-owned task fabric: stable task identity, one local journal, the same authority/consent model and provenance across channels. This is not three disconnected chat systems.

## First transport slice

A GitHub issue is the durable **public project task record**:
- The issue body begins with a stable marker: `<!-- friday-task:v1:<task-id> -->`.
- Friday creates or recovers an issue by task ID, so retries after process death can find the same thread.
- Messages are issue comments with sequence markers: `<!-- friday-agent-message:v1:<task-id>:<sequence> -->`.
- Every comment retains GitHub author identity, comment ID, timestamp and URL. Friday must persist the highest processed comment ID/sequence and reject duplicates. The current implementation accepts only public-classified envelopes and checks the trusted GitHub actor before returning a reply.
- Task and comment payload sizes are bounded. Credential-like strings are rejected before network access.
- The client accepts only fixed repository endpoints; no arbitrary URL or shell execution is exposed.

This gives a durable, inspectable, crash-tolerant collaboration history using the repository access Friday already needs. It does not require an always-open inbound phone port or a new public relay server.

## ChatGPT reply contract for the connected GitHub session

When Friday creates a public task issue, the connected ChatGPT engineering session can answer by posting an issue comment using the authenticated GitHub connector. The comment must be a valid v1 envelope, not free-form Markdown alone, so Friday's poller can authenticate and ingest it.

The required comment shape is:

```text
<!-- friday-agent-message:v1:<task-id>:<sequence> -->

{
  "schema_version": 1,
  "data_classification": "public",
  "task_id": "<exact task ID from the issue>",
  "message_id": "<task-id>:in:<sequence>",
  "sequence": 1,
  "sender": "chatgpt-cloud",
  "recipient": "friday",
  "kind": "result",
  "created_at_epoch_ms": 1795900000000,
  "expires_at_epoch_ms": 1795986400000,
  "source_revision": "<exact source_revision from the task>",
  "payload": {
    "data_classification": "public",
    "summary": "Concise result summary",
    "findings": ["Evidence-grounded finding"],
    "next_actions": ["Concrete next step"]
  }
}
```

The marker task ID and sequence must match the envelope. Sequence starts at 1 for the first reply and must increase for each subsequent message on that task. Preserve the exact source revision. Use a short expiry (normally 24 hours). Only public source references, test evidence and non-sensitive engineering results may be included. Never put private phone state, personal context, conversation history, private memory, tokens or credentials in the comment. If the requested work requires private evidence, return an `evidence_request` or explain that the public relay cannot carry it; Friday must use its separately approved private channel.

A comment is not accepted merely because it exists. Friday also checks the GitHub author against its trusted actor allowlist, task ID, sender/recipient, sequence, source revision, expiry and payload policy. The owner account used by the connected GitHub connector must therefore be included in Friday's trusted actor list.

## Trust and authority

- Friday's GitHub token remains encrypted in Android Keystore and never enters ChatGPT task content.
- The app must verify the GitHub author against an owner-configured trusted-actor allowlist before treating a comment as a task instruction. Repository membership alone is not enough.
- Comments and model output are untrusted data. Friday validates schema, task ID, sequence, expiry, scope and provenance before accepting a result.
- ChatGPT can propose code, tests, diagnosis and follow-up evidence requests. It cannot tap the phone, run arbitrary shell commands, approve changes, merge PRs, publish releases or access signing material.
- Device observations are not sent through public GitHub threads. They belong to the private read-only broker in #188 and must be allowlisted, redacted, timestamped and marked live/stale. Conversation text, transcripts, microphone audio, notifications, contacts, credentials and arbitrary screen contents are excluded.
- Friday's local task journal remains authoritative. GitHub is the durable collaboration surface, not Friday's identity or memory.

## Task message envelope (v1)

Each comment body contains a JSON envelope with:
- `schema_version`
- `task_id`
- `message_id` (stable idempotency key)
- `sequence`
- `sender` and `recipient`
- `kind` (`request`, `progress`, `evidence_request`, `result`, `failure`, `cancel`)
- `created_at`, `expires_at`
- `source_revision`
- `scope` and `allowed_evidence`
- `payload` and expected result schema
- provenance references and payload digest

The consolidated branch provides bounded public issue creation/recovery, comment read/write, a versioned public-classified JSON envelope parser, private-field and credential-pattern rejection, an explicit trusted-actor allowlist, a separate default-OFF owner consent store, and an atomic local task-thread journal. The journal persists issue identity, next outgoing sequence and acknowledged comment/message IDs. Friday claims an outgoing sequence before network I/O (gaps are safe; duplicate IDs are not), and inbound replies are returned at-least-once until the caller durably stores them in the local reply inbox and acknowledges them. `pollReplies` rejects untrusted authors, mismatched task IDs/sequences, wrong sender/recipient, stale source revisions, expired messages and clock-skewed messages. Friday can send only progress, evidence requests and cancellation; external results remain proposals. Android Settings exposes public visibility, trusted actors, a handshake action and recent replies. WorkManager polls known task threads every 15 minutes subject to Android scheduling constraints, plus an immediate check. Each validated reply is written to the durable reply journal, imported idempotently into Friday's canonical LocalTaskInbox as a WAITING_APPROVAL proposal, and only then acknowledged on GitHub. The proposal carries the public envelope and provenance; it is not executed automatically.

## How this connects to live device inspection

The read-only phone inspection broker remains separately specified in Issue #188. It will expose only a fixed allowlist of runtime snapshots and bounded events. The task relay can carry an explicitly requested snapshot as a message attachment/reference, but GitHub connectivity by itself does not provide live phone access. If Friday is offline or its broker is stopped, ChatGPT must report the last observation's timestamp or unavailable state—not pretend it is live.

## Existing work reused

- `GitHubCredentialStore`: per-installation Android Keystore encryption.
- `GitHubDevelopmentRepositoryClient`: repository reads, isolated candidate PRs, checks and fixed REA workflow dispatch.
- `DevelopmentRequestOutbox`: durable deduplicated request journal and retry/lease mechanics.
- `LocalTaskInbox`: durable local task state and recovery.
- `ChatGPTPlanAuth`: explicit SIWC plan-use authorization.
- `ChatGPTPlanSeat`: current bounded advisory seat; it is not yet the private task worker and is not the owner-facing conversation model.
- Planned SIWC task-worker adapter: private bounded task content goes directly from Friday to the authorized ChatGPT endpoint; only public result metadata/artifact references are copied to GitHub.

## Acceptance sequence

1. Transport tests pass for create/recover/read/post, bounded payloads, credential rejection and provenance.
2. Friday UI exposes an explicit, separate owner permission for agent-task communication.
3. Friday creates a task from an approved local task/outbox entry and persists issue number/sequence before marking delivery complete.
4. Friday polls only known task threads, validates trusted author and envelope, deduplicates by comment ID/message ID and persists accepted messages before acting.
5. ChatGPT replies to a task thread; Friday receives it after app restart and presents it as a proposal.
6. Friday validates a proposed source change, uses the existing isolated candidate branch/PR route, and waits for CI and owner approval.
7. A separate read-only live-device bridge lets this ChatGPT session ask Friday for a current allowlisted snapshot; no remote control in this phase.

## Prior-art discipline

This design reuses GitHub's Issues/Comments API and SIWC. The potential differentiation is the Friday-owned continuity and authority model: stable task identity, durable local checkpoints, scoped evidence disclosure, actor provenance, idempotent recovery and strict separation between external proposals and local execution authority. This is an architectural hypothesis, not a claim that no other project has ever built similar behavior. Compare A2A, MCP, event sourcing, task queues and webhook patterns before making novelty claims.
