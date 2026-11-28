# Friday private ChatGPT task worker

**Status:** implementation draft in PR #197. Not yet merged or wired into Friday's task UI.

## Role in the collaboration system

There are two deliberately different paths:

1. **Current ChatGPT session ↔ Friday:** public GitHub task-thread relay (PR #198). Friday creates a public project task issue; this connected ChatGPT session can reply through the GitHub connector; Friday polls and stores the reply locally. No private user/device context is allowed on this path.
2. **Friday ↔ authorized ChatGPT-plan model endpoint:** this private task worker. Friday submits a bounded task projection directly to the Responses API using the user's separately granted SIWC plan-use permission. This is an outbound model request, not a connection to this exact ChatGPT UI conversation, its history or memory.

The GitHub credential and OpenAI token are separate. Neither token is passed to the other system or included in task prompts.

## Request contract

A task projection contains:
- stable task ID;
- goal and bounded instructions;
- a minimal, explicitly compiled context projection;
- source revision;
- acceptance criteria;
- classification (PUBLIC, SANITIZED or PRIVATE; SENSITIVE is rejected);
- expiry no more than 24 hours after validation.

The worker validates all limits and computes a canonical, length-prefixed SHA-256 digest over every projection field. Owner approval is stored as the task ID, digest and expiry only; no task text is retained in the approval store. The approval is consumed durably before network I/O. A changed projection cannot reuse the old approval. An interrupted/failed attempt requires a fresh owner approval rather than an automatic retry that might duplicate a billable request.

## API and privacy contract

- Uses the existing ChatGPTPlanTokenProvider, which returns a token only when the SIWC ChatGPT-plan-use scope is granted.
- Uses the public Responses API endpoint, not private ChatGPT backend endpoints.
- Sends store=false and stream=true.
- Sends only the explicitly approved projection; no conversation transcript or full Friday memory is attached automatically.
- Bounds input, expiry and output; returns task ID, response ID, client request ID, model, context digest and completion timestamp.
- No paid API-key fallback, no hidden billing route and no GitHub credential.
- Treats all supplied task/source content as untrusted data. The model returns proposals; Friday retains local acceptance and action authority.

## Current limitations

- The worker class and projection/approval tests exist, but no Friday UI currently invokes the per-task approval flow.
- The encrypted, crash-safe task-result journal now persists streaming checkpoints and commits a completed result before `execute()` returns. Completed results can be recovered after process recreation without another model request.
- Interrupted streams retain encrypted partial output as `INCOMPLETE`; it is never represented as a completed result and is never retried automatically. A fresh owner approval is required before a new request.
- The worker cannot send a message into or wake this exact ChatGPT UI conversation. The public GitHub thread relay is the available bridge to this session.
- No live handset acceptance has been performed.

## Next acceptance gates

1. Add a visible approval sheet showing the exact goal, classification, context manifest, source revision, expiry and destination before the owner approves.
2. Persist request state and streamed result chunks in a private, encrypted, crash-safe journal; reconcile completion/result IDs after process restart before any retry.
3. Bind the worker to Friday's task lifecycle and local task inbox; ensure only a locally authorized task can reach this adapter.
4. Add instrumentation for approval mismatch/replay, expiry, consent revocation, offline failure, partial stream recovery and no credential leakage.
5. Prove a real task from Friday reaches the authorized model endpoint and the complete result is recoverable after process death.


## Additional API instrumentation

An Android instrumentation test now exercises the worker against a local mock Responses endpoint. It verifies the approved projection is sent with the SIWC bearer token, the selected account model, `store=false`, `stream=true`, and a client request ID; it validates the completed response ID/output and proves the consumed approval cannot trigger a second network request. This is transport-contract testing, not a live OpenAI account or handset acceptance test.


### Android network-security test correction

The first instrumented run exposed an Android cleartext-policy failure because MockWebServer used HTTP on localhost. The worker was not changed to weaken the app's network policy. The test now uses a generated localhost certificate, a test-scoped trust anchor, and HTTPS for the mock endpoint. The next CI run must prove this correction.


### Retry and stream-failure hardening

The worker's OkHttp client now disables automatic connection retries. A consumed one-shot approval therefore cannot silently lead to a hidden duplicate request after a transport failure. HTTP failures retain the response request ID and bounded body detail; stream failures preserve the provider error code/message, and incomplete responses retain their reason. Only `response.completed` is accepted as success, consistent with the official Sign in with ChatGPT inference contract.


### Encrypted result journal

`ChatGPTTaskResultStore` writes one AES-GCM encrypted checkpoint per stable task ID under Friday's private app storage. `FileStorage` provides fsync plus atomic replacement. The journal records request ID, model, context digest, streamed output, response ID, state and timestamp. Stream output is checkpointed in bounded chunks; the `response.completed` event commits the completed state before the worker returns. A recreated worker first checks for a matching completed result and returns it without consuming a new approval or making another request. Corrupt or undecryptable journals fail closed and are not overwritten.

The Android instrumentation suite now verifies completed-result recovery across worker recreation, encrypted-at-rest output, and partial-output retention after an interrupted stream. These remain mock-endpoint tests; they do not prove a live SIWC request or phone acceptance.
