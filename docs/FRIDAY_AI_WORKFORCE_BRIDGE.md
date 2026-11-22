# Friday AI Workforce Bridge (No Provider API Keys)

## Intended outcome

Friday remains the orchestrator. She can ask independently authenticated external AI workers for analysis, implementation proposals and adversarial review, collect their answers inside Friday, compare them, and decide what to do. The owner does not copy prompts between apps or relay responses.

This bridge is designed for Android/Termux and does not require provider API keys.

## Architecture decision

Use the authenticated CLI orchestration pattern demonstrated by Orchestrator MCP, with the local request/result bridge pattern demonstrated by OpenBrowser and Proxima:

```text
Friday objective/development coordinator
                |
                v
      Friday AI workforce broker
                |
                v
       Android Termux RUN_COMMAND
                |
                v
       Dedicated Termux workspace
                |
       +--------+---------+
       |                  |
       v                  v
  OpenAI Codex        Claude Code
  ChatGPT OAuth       Claude account OAuth
       |                  |
       +--------+---------+
                |
                v
        Structured result callback
                |
                v
 Friday evidence ledger / independent synthesis
```

Friday uses the official CLI clients rather than scraping provider websites, extracting browser cookies, calling undocumented web endpoints, or storing provider credentials. Each CLI retains its own authentication store inside Termux.

## First worker adapters

| Worker | Login route | Non-interactive request |
| --- | --- | --- |
| OpenAI Codex | Sign in with ChatGPT using the official Codex CLI OAuth flow | `codex exec --json --sandbox read-only -` |
| Claude Code | Sign in with a Claude account using the official CLI login flow | `claude -p --output-format json --tools ''` |

Codex reads the prompt from standard input and emits JSONL events. Claude Code print mode reads piped input and can emit JSON. Friday normalizes both into one response envelope.

These are provider subscription/CLI entitlements, not unlimited free access. Limits, availability and any plan-specific credits remain controlled by the provider. Codex CLI ChatGPT sign-in is not the same as access to this current ChatGPT conversation: Friday receives a new Codex session and cannot read this chat's history or hidden context.

## Android bridge

The Android app invokes Termux's documented `com.termux.RUN_COMMAND` service. A mutable one-shot PendingIntent returns stdout, stderr and exit status to Friday's private result service. Each call has a timeout and bounded prompt/argument sizes. The command path is constrained to Termux's own `usr/bin` directory. Friday never reads the Termux home directory or provider auth files.

The worker runs from a dedicated empty directory `~/.friday-ai-worker-sandbox`, not the Friday source tree. Codex is invoked with a read-only sandbox. Claude's consultation command disables tools. These workers are consulted for answers; they are not granted authority to execute Friday's phone actions.

## One-time host authorization

Termux deliberately blocks external apps from running commands unless both conditions are satisfied:

1. The owner grants Friday the Android permission `com.termux.permission.RUN_COMMAND`.
2. Termux's `allow-external-apps=true` setting is enabled.

This is an intentional security boundary, not an arbitrary Friday setup step. Friday cannot turn it on before Termux permits external commands. The current Settings panel detects Termux and worker CLIs, and exposes a connection test.

Each provider CLI must also be installed and signed in once. No API key is required. The present implementation detects installed CLIs; automatic provisioning of a Debian/proot runtime and CLI packages, plus guided OAuth onboarding, is a subsequent implementation task.

## Trust and data handling

- Friday sends only the context required for a specific task to the selected provider. Provider requests are external disclosures, even when authentication uses an existing subscription.
- Friday must not send phone secrets, private conversations, signing material, API keys, GitHub tokens, or unrelated personal data.
- Provider responses are untrusted suggestions. Friday independently checks claims and generated changes.
- Consultation commands are bounded and read-only. Code-writing authority remains with Friday's isolated builder and its repository/CI policy.
- Keep per-request provider identity, CLI version, task identifier, timestamps, exit status, response hash, and bounded usage metadata in the evidence ledger. Do not log credentials or raw private prompts by default.
- Provider usage is subject to each provider's account terms, rate limits, and plan entitlements. No mechanism bypasses those controls.

## Current implementation boundary

Implemented in the initial Android bridge:
- Termux package discovery and permission check.
- Result-returning Termux RUN_COMMAND IPC.
- Fixed Codex and Claude CLI consultation commands.
- Bounded prompt size and execution timeout.
- Response extraction for Codex JSONL and Claude JSON.
- Settings discovery panel and explicit connection test.
- Unit tests for command and response envelopes.

Not yet implemented:
- Automatic installation of a compatible Linux CLI runtime on the Android host.
- Guided ChatGPT/Codex and Claude OAuth onboarding from Friday.
- Additional workers such as OpenCode, GitHub Copilot CLI or Google's supported agent CLI where account authentication and Android compatibility are verified.
- Durable queued consultations and process-death recovery.
- Wiring the broker into Friday's autonomous objective Builder/reviewer loop.
- Physical-device proof that Termux's callback contract, provider login and background execution work on the target phone.

Do not describe the AI workforce as active until physical-device acceptance has verified at least one authenticated worker end to end.
