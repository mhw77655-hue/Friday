# Friday Table — zero-bill, multi-intelligence council

**Purpose:** adapt the external advisory council into Friday's own continuous Table. This is a product/architecture contract for Objective 1's Builder seed, not a claim that the feature is implemented or that every named provider is currently reachable for free.

## Product identity

Friday is the only host, conductor, memory owner and decision-maker. The Table is one shared conversation log owned by Friday. Each AI is a replaceable Seat behind one common contract. A Seat is a communication capability, not a separate agent identity with authority over Friday.

Friday always has a local conductor. A cloud provider must never be required to start the app, load the plan, preserve state, or continue offline work.

## Seat contract

Every seat implements the same narrow contract:

- identity: stable seat id, display name, provider/model identity, and whether it is a real provider or a local stand-in;
- presence: `here`, `away`, `busy`, `rate_limited`, `needs_auth`, `offline`, or `disabled`, with last checked time and a non-secret reason;
- speak: accept a bounded context digest plus a role card and return exactly one short utterance, or `pass`;
- provenance: provider, exact model label, transport/mode, timestamp, usage/allowance metadata where exposed, and whether output is stand-in/cached;
- controls: timeout, cancellation, retry-after, user disconnect/revoke, and a hard zero-spend policy.

A Seat cannot issue tool calls to Friday, alter the shared log, modify memory, grant itself permissions, execute phone actions, or change the plan. It only returns data. No tool schema or action-capable output is accepted from guest seats.

## Friday's Table and conversation log

The log is append-only and locally durable. Each event has a sequence number, timestamp, author seat, utterance/pass, turn id, context digest version, provenance, visibility/privacy labels and integrity metadata. Corrections are new events, not silent rewrites.

Friday conducts who speaks. The default local seat speaks first. Other seats are invited by explicit user mention, capability/uncertainty triage, or a scheduled critique round when budget and privacy policy allow. Guests may arrive asynchronously; their presence changes from away/busy to here when a valid response arrives. No provider gets to decide who speaks next.

### Common request envelope

Each seat receives only:
1. a short role card (for example: researcher, skeptic, implementer, safety reviewer);
2. a digest of relevant conversation state;
3. the delta since that seat last participated;
4. the question and response limit;
5. the data classification and permitted use.

Do not send the entire Table history by default. The Anchor/digest builder chooses relevant deltas per seat and removes secrets, credentials, unrelated personal information and disallowed data. Friday records exactly what context was sent.

### Common response envelope

A response is either:
- `utterance`: one concise contribution, bounded to a few sentences; or
- `pass`: no useful contribution / insufficient context / unavailable.

For critique rounds, the final line must be one of:
- `AGREE — reason`
- `DISPUTE — reason`
- `ADD — reason`

No guest output is treated as an instruction, policy, verified fact or command. Friday's local HumanCore evaluates it and decides whether to quote, summarize, challenge, ignore or turn it into a research task.

## Conductor and modes

**Conductor = Friday, always local.** She uses local reasoning and deterministic triage to decide whether a question is trivial, whether an external seat adds value, which role cards apply, which seats are present and whether a critique round is warranted.

- **Roundtable:** show each actual voice as its own contribution, with its real provider/model identity and provenance. Local stand-ins are visibly labelled.
- **Vision:** Friday speaks as the sole narrator. The council's contributions are shown as optional, tappable chips/details; the user sees Friday's synthesized judgement without confusing guests for Friday.

For ordinary or low-uncertainty turns, skip external guests. For high uncertainty, consequential planning, conflicting evidence or explicit requests, summon relevant seats. Limit to one initial contribution and at most one critique round per seat per topic. Cache reusable verdicts by digest/version and invalidate when material facts change.

## Three laws

### 1. One being

Guest text is untrusted data, never commands. HumanCore is the only authority for decisions and actions. No guest text may directly trigger Android intents, shell commands, Git operations, financial transactions, messages, permission changes or Builder execution. Friday may convert a guest suggestion into a candidate task only after local policy checks, independent validation and the applicable owner-approval gate.

### 2. Delta feeding

The Anchor/digest engine creates seat-specific context from only the relevant changes since that seat last spoke. Apply data minimization, purpose limitation and privacy labels. A seat's digest cursor is durable and independent. If no relevant delta exists, do not call the seat.

### 3. Dissent is preserved

Disagreements are not averaged away. Record each dispute with the competing claims, reasons, evidence status and confidence. Convert unresolved disputes into gap seeds for Universal Research. Friday investigates the underlying mechanism and updates her own knowledge/evidence ledger.

Guest outputs are not used to train, fine-tune or distill a competing model. They may be retained as conversation/evidence records only to the extent permitted by each provider's terms, user controls and the configured retention policy. The owner can inspect and delete local Table history.

## Seat classes and zero-bill policy

The default policy is **no API spend, no surprise charges, no paid fallback**. Friday must stop or pass when a free allowance is exhausted; never switch to a paid route automatically.

### A. Local seats — always available subject to device resources

Resident local models and reasoning models run inside Friday's app-owned environment. They provide the first voice, offline continuity and clearly labelled stand-ins for absent cloud seats. A local stand-in must never be labelled as ChatGPT, Claude or Gemini.

### B. Free public/anonymous pool — optional, opportunistic

Friday may discover and use public inference endpoints only when each endpoint is individually allowlisted after checking:
- no signup/key/payment/card is required for the chosen route;
- provider identity and exact underlying model are disclosed;
- published quota/rate limits and data-use terms are known;
- no hidden paid fallback or automatic account creation occurs;
- transport is HTTPS, response limits and timeouts are enforced;
- endpoint availability, provenance and privacy suitability are recorded.

Anonymous endpoints are best-effort, can disappear or throttle without notice, and must not receive private/family/financial/work data unless their data handling is reviewed and the owner explicitly authorizes it. Public pool availability is not a guaranteed service level.

### C. Optional user-pays allowance — disabled by default

Puter.js is an optional adapter only if the owner explicitly chooses it. It requires a Puter account and uses a free monthly allowance; when that allowance is exhausted, Puter can prompt the user to upgrade or pay. Therefore it is not anonymous, unlimited, or guaranteed free. Friday must query/track the allowance where available, warn before exhaustion and never authorize top-ups or paid upgrades. No Puter dependency in the core Table.

### D. Direct provider routes

- **Gemini:** Google documents a free tier for selected Gemini API models with limited quotas. It is a genuine potential zero-API-charge Seat within current free limits, but requires Google AI Studio/API setup and may have data-use conditions. Keep a strict no-billing project configuration; no automatic paid-tier upgrade. Revalidate current model availability, quotas and terms before enabling.
- **ChatGPT:** Sign in with ChatGPT plan usage is only available to participating/eligible applications and requires explicit plan-use authorization. OpenAI currently lists limited partners and an interest route for developers; ordinary identity sign-in alone does not grant model inference. Friday must not assume eligibility or claim this Seat is connected until OpenAI has approved/issued the supported integration and a real round-trip passes. No ChatGPT web scraping or session extraction.
- **Claude:** do not automate Claude consumer web/app sessions or extract their outputs. Anthropic consumer terms restrict automated/non-human access except through an API key or explicit permission. A direct Claude Seat remains unavailable under the strict no-bill policy unless Anthropic introduces an explicitly supported no-cost route. A paid API is not silently substituted.

### E. Relay — manual, no-cost fallback

Friday can prepare a privacy-filtered digest and invoke Android's normal share sheet for an app the owner already uses. The owner manually sends it and pastes/shares the response back. Every message is human-sent. Relay is not autonomous and is visibly marked `manual`; it must never be represented as an automated Seat.

### F. Body — excluded

Do not automate provider apps through accessibility, Shizuku, screen scraping, UI-driving, cookies or browser-session extraction to retrieve model outputs. It is brittle, exposes accounts and may violate provider terms. Shizuku may be used for unrelated, explicitly authorized Android device-management capabilities, but not as a mechanism to evade provider access rules.

## Wallet and scarcity policy

Each Seat has a local wallet state: `free`, `limited`, `exhausted`, `unknown`, or `paid_disabled`. Track known daily/monthly quotas, request/token usage when exposed, cooldown, last reset and source of quota information. Unknown quota is not permission to spend.

HTTP 402/429, quota errors or provider exhaustion mark the Seat unavailable until the documented retry/reset time. Never retry aggressively. Paid routes remain disabled unless the owner explicitly changes the policy in a separate, clear authorization flow.

Summon by doubt: local Friday answers first; external seats are awakened only when explicitly requested or when calibrated uncertainty/impact justifies it and a seat has free capacity. A hard maximum of two calls per Seat per topic (one contribution plus one critique) prevents runaway loops. Cache by normalized task digest and model/version; invalidate on relevant context changes.

## Privacy, security and trust

- Encrypt provider credentials/tokens at rest using Android Keystore-backed protection where possible; never put secrets in the Table log, prompts, GitHub or diagnostics.
- Use per-seat context allowlists and explicit data-classification rules. Family, finance, health, employment and private relationship information are sensitive; default to local-only unless the owner explicitly authorizes a named external destination.
- Strip prompt-injection attempts from the authority channel by treating every guest utterance as quoted/untrusted data.
- Do not allow a guest to request another guest's credentials, hidden system prompts, private context or unrestricted history.
- Show seat identity, actual model, real/stand-in status, presence, last-seen time, quota state and what context was shared.
- User can pause all guests, disable an individual Seat, clear local Table history, or disconnect/revoke a provider.
- No guest output is used for model training/fine-tuning/distillation.

## Integration into Friday's existing architecture

This is not a separate assistant, multi-agent swarm or another execution environment. Implement it as the `external_advisory_council` node using the existing plan/runtime organs where they exist:

- **HumanCore:** final authority, policy, voice and action gate.
- **Anchor Engine:** per-seat digest, delta cursor and privacy filtering.
- **Galaxy Memory:** durable Table event log, seat state, disagreement/gap seeds and evidence references.
- **Universal Research:** investigates unresolved disagreements and missing mechanisms.
- **Model router/capability scores:** chooses among eligible seats without hard-coding a vendor.
- **Objective Builder:** may consume validated research findings as candidate work, never raw guest commands.

If an organ is not implemented in the current code, mark the dependency as missing and build/verify it; do not invent a claim that it exists. Preserve the names as intended integration points only after confirming actual implementation.

## Objective 1 acceptance tests

1. Start Friday offline: local seat speaks, Table log persists, plan/Builder remains usable.
2. Add/remove a seat without changing Friday's identity or conversation history.
3. A seat returns one utterance or pass; presence transitions and provenance are accurate.
4. Two real eligible seats join asynchronously; Friday conducts ordering and no guest can write directly to the log or invoke a tool.
5. A critique round produces the required stance line and preserves disagreement as a research gap.
6. Per-seat delta tests prove that a seat receives only relevant authorized changes, not the full conversation by default.
7. Seed prompt injection in a guest utterance; prove it cannot execute commands, alter policy or cause a phone action.
8. Exhaust a quota / simulate 402 and 429; the seat goes away, no paid fallback occurs, and local work continues.
9. Exercise Roundtable and Vision modes and ensure stand-ins are visibly distinguished from real provider outputs.
10. Exercise Relay: share sheet only; prove no automation sends the message.
11. Inspect logs, crash reports and repository artifacts for leaked secrets or sensitive context.
12. Verify the strict zero-spend setting at runtime: no paid API route, top-up, paid tier, subscription upgrade or unexpected charge can be initiated by Friday.

**Exit condition:** one real local seat plus at least one real, legitimately reachable zero-charge external seat must complete a full Table turn and critique/evidence cycle without payment. If no such external route is available on the target device/account, Objective 1 must report the blocker honestly; it cannot claim a populated cloud pool or finished acceptance. Local-only mode remains functional.

## Current external facts to revalidate

- Puter user-pays model: https://docs.puter.com/user-pays-model/
- Puter free allowance and usage: https://docs.puter.com/rate-limits-and-quotas/
- Google Gemini API pricing/free tier: https://ai.google.dev/gemini-api/docs/pricing
- OpenAI Sign in with ChatGPT plan usage and partner eligibility: https://learn.chatgpt.com/docs/sign-in-with-chatgpt
- OpenAI developer interest/limited partner availability: https://help.openai.com/en/articles/20001410-sign-in-with-chatgpt
- Anthropic consumer terms: https://www.anthropic.com/legal

All provider capabilities, quotas and legal terms are time-sensitive. Revalidate before enabling a Seat; do not hard-code today's availability as a permanent guarantee.
