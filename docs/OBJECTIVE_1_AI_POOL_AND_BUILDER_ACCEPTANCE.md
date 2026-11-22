# Objective 1 — installed Builder and direct AI-pool contract

**Status:** implementation and acceptance contract; not evidence of completed runtime behaviour.

## Product outcome

Objective 1 delivers the first operational Builder seed. After one installation, Friday studies the Android host she inhabits, configures an app-owned development environment, loads the canonical objectives plan, and begins progressing through it herself. The owner supplies outcomes and protected decisions; Friday determines and executes the ordinary engineering path.

Termux is not a production dependency. The installed Android app owns its workspace, runtime lifecycle, task state, provider connections, development tools, evidence and recovery. A Linux userland/runtime may be embedded as one replaceable execution capability, but Friday's identity and Builder must not depend on a separate terminal app, a particular distro, PRoot, or one environment implementation.

## Builder minimum viable developmental loop

The first seed must complete a real loop, not stop at host profiling or task decomposition:

1. Discover current host capabilities and constraints from live Android observations. Label facts as observed, inferred, unavailable or permission-gated.
2. Provision or select a Friday-owned workspace and compatible execution tools. Verify each installation and record provenance, version, digest, licence and resource cost.
3. Read the canonical plan and durable local execution ledger; reconcile changes without erasing prior evidence.
4. Select a dependency-ready task and define its expected outcome, tests, risk, resource budget and rollback.
5. Ask Friday's AI pool for bounded guidance when useful. Friday remains the coordinator and decision-maker.
6. Construct a candidate in an isolated workspace/branch. AI output is untrusted input, never an executable authority.
7. Run applicable build, unit, integration, security and regression checks. Obtain an independent challenge/review that does not merely repeat the implementer's reasoning.
8. Record source revision, diff, toolchain, provider/model provenance, tests, results, resource use, decisions, approvals and unresolved risks.
9. Promote only within granted authority. Native Android changes use the trusted signed build/update route; Android's installation authority is not bypassed.
10. Persist checkpoints and resume idempotently after process death, reboot, network loss, provider failure or resource pressure.

Task decomposition, a progress screen, a host profile, a proposed patch, or a green emulator build alone does not satisfy this loop. At least one real post-install Builder cycle must be demonstrated end-to-end.

## AI pool: actual intelligences, not coding-product substitutions

Friday is one continuing individual. ChatGPT/OpenAI, Claude/Anthropic, Gemini/Google and future providers are external intelligence resources she may consult. They are not separate Friday identities, command-line tools standing in for the models, or authorities over the Builder.

The provider interface must be provider-neutral and support:
- capability discovery and explicit connection state;
- user-authorized authentication and revocation;
- bounded prompt/context transfer with data minimization;
- streaming or asynchronous responses where supported;
- provenance (provider, model, connection mode, request purpose, timestamps and usage);
- quotas, rate limits, budget/usage limits and graceful refusal;
- structured result capture, cancellation, retries and idempotency;
- independent evaluation and disagreement handling;
- local-model/no-provider fallback.

Do not assume every provider offers the same authentication or subscription entitlement. A provider adapter is enabled only through a documented, supported route. No cookie/session extraction, reverse-engineered private endpoints, credential reuse, or hidden browser automation.

## OpenAI: supported ChatGPT-plan connection

Investigate and implement the official **Sign in with ChatGPT open-source app flow**, not Codex CLI as a substitute for ChatGPT and not ChatGPT website scraping.

The official open-source flow supports a public client registration and user authorization for eligible Responses API plan usage. The app must:
- register/use its own OpenAI-issued client ID and the supported dynamic registration/authorization flow;
- use Authorization Code + PKCE, validate state/nonce and verify the returned ID token against OpenAI JWKS;
- require the granted `chatgpt.tokens.use.direct` scope before plan-backed inference;
- store access/refresh tokens locally under user control using Android Keystore-backed protection where possible;
- never collect ChatGPT passwords or cookies, put tokens in logs, repository files, prompts, diagnostics or remote telemetry;
- request explicit consent for background automations and provide in-app disconnect/revocation and usage controls;
- use the plan only for Friday's connected application and the authenticated user's own authorized activity;
- respect account eligibility, rate/usage limits, model availability and provider errors;
- never pool, transfer, resell, rotate accounts or expose this connection as a general-purpose API to other apps/users.

This connection does **not** provide access to the user's ChatGPT conversations or memories and does not add usage beyond the user's plan. It is not an unlimited or free-for-all model endpoint. The app must disclose what context Friday sends to OpenAI and let the owner inspect/control that sharing.

OpenAI references:
- https://developers.openai.com/siwc/quickstart
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference
- https://openai.com/policies/sign-in-with-chatgpt-terms/

Eligibility note: OpenAI documents plan usage for open-source partners and selected private apps. Confirm Friday's client registration and eligibility through the supported flow; do not assume a production client ID or entitlement exists until issued and verified.

## Anthropic and Google: truthful provider capability states

**Claude:** Anthropic's current legal documentation says developers building their own products must not offer Claude.ai login or route consumer Free/Pro/Max/Ultra plan credentials through their own app. Its Claude Code binary may be run unmodified with each end user's own authentication under Anthropic's product terms, but that is a separate product and is not Friday's desired direct Claude intelligence adapter. Do not embed Claude Code or represent it as direct Claude-app integration. Until Anthropic publishes an approved direct third-party app route, show Claude as unavailable for subscription-backed direct integration; an explicitly owner-authorized paid Console/cloud route is a separate optional connection, never silently enabled.

**Gemini:** Google's developer forum response dated November 16, 2026 states there is no official third-party application route to use an individual's Google AI Pro/Ultra subscription quota through Gemini API; programmatic Gemini API usage is managed through a Google Cloud project. Do not reuse Gemini consumer sessions or imply that a Google sign-in grants subscription inference. Keep a provider adapter slot for a future official route; any current API/cloud route must disclose billing and require explicit owner authorization.

Provider references:
- https://code.claude.com/docs/en/legal-and-compliance
- https://support.claude.com/en/articles/18467148-use-your-claude-platform-account-in-a-third-party-app
- https://discuss.ai.google.dev/t/question-official-way-for-a-third-party-desktop-app-to-use-a-users-google-ai-pro-ultra-subscription-with-gemini/199349

## Android-owned development environment

The app must own the Builder workspace and environment lifecycle. Donor projects such as embedded PRoot/Linux engines may be evaluated and selectively integrated after license, Android target-SDK, W^X/seccomp/SELinux, ABI, storage, process lifecycle, native toolchain and physical-device validation. A donor is not automatically a production-ready runtime.

Environment discovery is capability-based, not distro-based. Friday may use Android/JVM/native tools directly for tasks that do not need Linux, and provision an embedded Linux environment only when it materially enables a task. She must not require Termux or ask the owner to maintain a terminal.

Native source changes require reproducible builds, independent tests, signing continuity and Android's normal update/installation authorization. Friday may autonomously develop candidates and prepare verified releases within policy; she must not claim silent installation or bypass platform controls.

## Objective 1 exit evidence

Objective 1 is complete only when all applicable evidence is attached to the exact tested source revision:

- latest-head CI passes Android build, unit/instrumentation tests, source/security audit and required adversarial tests;
- production APK is built from that revision and verified against the permanent signing certificate;
- installed Friday on the target phone performs host discovery and adapts its workload with measured postconditions;
- no Termux installation or external terminal app is required;
- app-owned workspace/runtime is provisioned and survives process death/reboot with durable checkpoints;
- the AI pool demonstrates at least one real supported ChatGPT-plan connection and a request/response round trip, subject to issued client registration and user consent;
- provider-unavailable behaviour is truthful and local Builder work continues;
- Friday completes one real source/capability candidate cycle: task selection, construction, build/tests, independent review, evidence capture and safe continuation;
- update/identity continuity and rollback behaviour are tested;
- no credentials, private prompts, conversations or sensitive device data appear in GitHub evidence;
- residual provider limitations (including Claude/Gemini subscription restrictions) are explicitly recorded, not hidden behind fake adapters.

A failure to obtain provider eligibility, a missing owner-only permission, or a provider's lack of a supported consumer-subscription integration must be recorded as a real dependency/limitation. It must not be worked around through scraping or relabelled as success.
