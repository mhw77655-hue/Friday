# Friday agent task relay — GitHub issue threads

Status: GitHub transport and core local task orchestration are implemented in branch `feat/friday-github-task-relay`; Settings integration, WorkManager polling and handset acceptance remain pending.

## Why this channel exists

The owner speaks directly to Friday on the phone. Friday is the continuing individual and owns her identity, local memory, continuity, cognition, permissions, action policy and owner-facing voice.

ChatGPT is a separate collaborating cloud agent. Friday must be able to ask ChatGPT for bounded engineering/research work, receive progress/results, and expose carefully selected runtime evidence. GitHub provides durable shared task records that this ChatGPT engineering session can read and answer through the connected repository.

This is not a ChatGPT provider for Friday's ordinary owner-facing conversation.

## First transport slice

A GitHub issue is the durable task thread:
- The issue body begins with a stable marker: `<!-- friday-task:v1:<task-id> -->`.
- Friday creates or recovers an issue by task ID, so retries after process death can find the same thread.
- Messages are issue comments with sequence markers: `<!-- friday-agent-message:v1:<task-id>:<sequence> -->`.
- Every comment retains GitHub author identity, comment ID, timestamp and URL. Friday must persist the highest processed comment ID/sequence and reject duplicates.
- Task and comment payload sizes are bounded. Credential-like strings are rejected before network access.
- The client accepts only fixed repository endpoints; no arbitrary URL or shell execution is exposed.

This gives a durable, inspectable, crash-tolerant collaboration history using the repository access Friday already needs. It does not require an always-open inbound phone port or a new public relay server.

## Trust and authority

- Friday's GitHub token remains encrypted in Android Keystore and never enters ChatGPT task content.
- The app must verify the GitHub author against an owner-configured trusted-actor allowlist before treating a comment as a task instruction. Repository membership alone is not enough.
- Comments and model output are untrusted data. Friday validates schema, task ID, sequence, expiry, scope and provenance before accepting a result.
- ChatGPT can propose code, tests, diagnosis and follow-up evidence requests. It cannot tap the phone, run arbitrary shell commands, approve changes, merge PRs, publish releases or access signing material.
- Device observations are allowlisted, redacted, timestamped and marked as live/stale. Conversation text, transcripts, microphone audio, notifications, contacts, credentials and arbitrary screen contents are excluded by default.
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

The current branch now provides bounded issue creation/recovery, comment read/write, a versioned JSON envelope parser, an explicit trusted-actor allowlist, a separate default-OFF owner consent store, and an atomic local task-thread journal. The journal persists issue identity, next outgoing sequence and acknowledged comment/message IDs. Friday claims an outgoing sequence before network I/O (gaps are safe; duplicate IDs are not), and inbound replies are returned at-least-once until the caller durably stores them in Friday's inbox and acknowledges them. `pollReplies` rejects untrusted authors, mismatched task IDs/sequences, wrong sender/recipient, stale source revisions, expired messages and clock-skewed messages. Friday can send only progress, evidence requests and cancellation; external results remain proposals. WorkManager polling, Settings UI, wiring into `LocalTaskInbox`, and phone acceptance are still pending.

## How this connects to live device inspection

The read-only phone inspection broker remains separately specified in Issue #188. It will expose only a fixed allowlist of runtime snapshots and bounded events. The task relay can carry an explicitly requested snapshot as a message attachment/reference, but GitHub connectivity by itself does not provide live phone access. If Friday is offline or its broker is stopped, ChatGPT must report the last observation's timestamp or unavailable state—not pretend it is live.

## Existing work reused

- `GitHubCredentialStore`: per-installation Android Keystore encryption.
- `GitHubDevelopmentRepositoryClient`: repository reads, isolated candidate PRs, checks and fixed REA workflow dispatch.
- `DevelopmentRequestOutbox`: durable deduplicated request journal and retry/lease mechanics.
- `LocalTaskInbox`: durable local task state and recovery.
- `ChatGPTPlanAuth`: explicit SIWC plan-use authorization.
- `ChatGPTPlanSeat`: current bounded advisory seat; it is not yet the task worker and is not the owner-facing conversation model.

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
