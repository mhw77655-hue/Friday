# ChatGPT main-turn provider integration

Status: implementation branch opened; provider wiring is not yet delivered.

Branch: `feat/chatgpt-main-turn-provider`
Base: PR #185 head `364cea7266426b118ec14bd93691eeadf1128013`
Tracking issue: #186
Parent UI/auth work: PR #185

## Objective

Use the owner's separately authorized ChatGPT plan as an optional reasoning provider for Friday's ordinary typed and spoken turns, through the existing ModelManager -> LatencyLayer -> Friday reply/TTS path. This must not create a second conversation authority or require a local LLM to be installed.

## Non-negotiable boundaries

- ChatGPT identity sign-in, ChatGPT-plan inference permission, Builder/background-use consent, and normal-conversation consent are four distinct states.
- Normal-conversation consent defaults OFF. Before enabling it, Settings must disclose that the turn prompt and any context assembled into that prompt are sent to OpenAI. The user can disable it at any time.
- The provider must check the normal-chat consent gate before reading a token or making a network request.
- Use only the current SIWC plan-use access token and the Responses API. Never use an API key, another account, or a hidden paid fallback.
- Send `store:false`, `stream:true`, and developer/user/assistant messages (map any local system instruction to developer). Do not send unsupported fields.
- Discard partial output unless a `response.completed` event is received.
- Friday retains ownership of identity, memory, continuity, cognition, tool execution, approvals and action policy. The cloud provider returns reasoning text only.
- When cloud use is disabled, unauthorized, offline or limited, restore the previously selected local provider without deleting local state. Do not force-install or assume a local model; if none exists, Friday's model-free seed remains the honest fallback.
- Every turn continues through the single existing LatencyPipeline and existing speech output.

## Planned implementation sequence

1. Add a dedicated normal-conversation consent store; do not reuse Builder/background consent.
2. Implement a ChatGPT SIWC ModelProvider with bounded request/response handling, model discovery, SSE completion validation, explicit errors and no API-key fallback.
3. Add the provider through PlatformPorts into the one ModelManager; keep CLOUD absent when no authorized provider is available.
4. Add explicit provider activation and prior-local-provider restoration, including revocation/consent-off handling.
5. Add a compact Settings control that shows identity, plan-use permission, normal-chat consent, active provider and health/test result.
6. Add unit/instrumented tests for consent default-off, no token/network access before consent, payload contract, completed-only streaming, HTTP errors/rate limits, fallback and provider-switch races.
7. Run CI and emulator instrumentation; then request the minimum physical-phone acceptance needed to prove an actual typed answer and spoken reply.

## Current evidence

- PR #185 has a passing latest validation run: https://github.com/mhw77655-hue/Friday/actions/runs/46057753690
- That run passed debug/release builds, Android lint, emulator instrumentation and JVM test shards.
- PR #185 remains a draft and has not been physically accepted on the owner's phone.
- The production composition currently creates `CloudModelRouter(emptyList())`; ModelManager explicitly has no CLOUD provider. This explains why the existing ChatGPT Seat login did not affect normal Conversation.
- The current ChatGPT plan Seat is an advisory Builder/Table participant. It is not reused as the ordinary-turn provider.
- No local LLM is installed on the phone; this implementation must work without one.

## External protocol references

- https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt
- https://developers.openai.com/siwc/quickstart
- https://developers.openai.com/api/reference/overview/

Do not mark this document complete until code, tests and runtime evidence exist.
