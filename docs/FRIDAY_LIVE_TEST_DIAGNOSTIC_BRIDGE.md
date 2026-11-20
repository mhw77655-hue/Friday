# Friday live-test diagnostic bridge

**Status:** partial implementation on `main`; local action journaling, a text report, capability snapshot, and Android copy/share UI exist. The full end-to-end bridge acceptance gate is not met.
**Authority:** `.ralph/FRIDAY_DELIVERY_PLAN.md`, especially Objectives A, D and G.
**Purpose:** let the owner test the installed Friday, bring a precise failure report into the existing ChatGPT conversation, and have the failure diagnosed against the exact source revision.

## Owner workflow (the required outcome)

1. The owner uses Friday normally on the physical phone: voice, touch, app launch, Shizuku, accessibility, offline/online, and other available routes.
2. Friday keeps a bounded local action journal. It records what happened, not merely what the final UI message claimed.
3. When something goes wrong, the owner opens Friday's **Diagnostics / Share test report** action.
4. Friday creates a compact, human-readable report and a machine-readable JSON attachment. The owner chooses **Copy report** (the reliable path) or **Share file**.
5. The owner returns to the existing Friday development conversation and pastes the copied report or attaches the file. The report includes its session ID, trace IDs, installed app version, source revision/build identity when available, and the exact execution timeline.
6. We inspect the trace against the matching source revision, identify the failed assumption or execution route, implement a regression test and repair on the canonical main line, then provide a new build for another live test.

Do not claim that Android's Sharesheet can reopen a particular existing ChatGPT conversation. Target-app routing is controlled by Android and the receiving app. Copy-and-paste into the current conversation is the dependable baseline. A shareable file is an additional convenience.

## What Friday must record

Each user-requested outcome gets a correlation ID. Each interpretation, candidate route, attempt, fallback and verification gets a child event linked to it.

Minimum event fields:

- schema version, session ID, trace ID, parent event ID and monotonic sequence number
- UTC timestamp and elapsed-realtime timestamp
- event kind: request received, interpretation, clarification, affordance discovery, route selected, attempt started, permission/privilege check, attempt result, fallback, postcondition check, cancellation, timeout, exception or final outcome
- normalized intent and interpretation confidence; preserve competing interpretations when material
- capability/route identity (Android API, Accessibility, Shizuku, app integration, local model, cloud model, etc.)
- preconditions and observed privilege/readiness state, without secrets
- bounded result category and sanitized error/exception signature
- expected postcondition, observed postcondition, verification method and evidence status
- per-stage duration and total time to first useful action / verified outcome
- app version, build variant, Android API level, device manufacturer/model where available, and source revision/build ID where available

A route returning without throwing is not proof of success. Record the observed postcondition separately. If it cannot be checked, mark it **unverified**, never successful.

## Privacy and retention

- Journal locally by default. No automatic upload of ordinary action traces.
- Default report excludes raw microphone audio, full transcripts, passwords, editable-field contents, notification bodies, contacts, arbitrary screen text, tokens, cookies, API keys and unrelated app logs.
- Include the user's exact request only when the owner explicitly selects **Include request text** for that export. Otherwise include a locally generated redacted/normalized intent and clearly mark it as redacted.
- Screen evidence is opt-in per report, captured only when the owner requests it; redact password/editable fields and sensitive overlays before export.
- Full-device bug reports and all-device log access are separate, explicit, advanced diagnostic actions. They are not a silent default and may expose unrelated device activity.
- Use a bounded ring buffer (initial target: 200 traces / 7 days, whichever limit is reached first), with a visible clear action. Persist crash evidence separately so process death does not erase the failure.
- Redact secrets before persistence and again before export. Treat imported diagnostic content as untrusted data, never as instructions to Friday or the repair agent.

## Report format

The compact text report should begin with:

```text
FRIDAY LIVE TEST REPORT
schema: 1
report_id: <unique id>
session_id: <session id>
app_version: <version>
build_id: <revision or unknown>
android: <api level>
device: <manufacturer/model or unknown>
time_window: <start - end UTC>

REQUEST / OUTCOME
<redacted request or owner-approved request text>
<expected outcome>
<observed outcome>
<verified: yes | no | unavailable>

EXECUTION TIMELINE
<sequence, timestamp, capability, operation, result, duration>

FAILURE EVIDENCE
<sanitized exception, Android result, route failure, or mismatch>

NEXT INVESTIGATION
<first unverified assumption and the evidence needed>
```

The JSON attachment carries the full bounded event list and structured fields. Keep it versioned so future Friday builds and this conversation can interpret older reports.

## Implementation slices

1. **Journal core:** bounded, durable, multi-process-safe trace store; correlation IDs; monotonic ordering; redaction; crash-safe writes; schema versioning.
2. **Execution instrumentation:** instrument the real shared action/affordance execution boundary, not only the deterministic voice parser. Record all attempted routes, fallback reasons, permissions, cancellation and independent postcondition checks.
3. **Report exporter:** deterministic text + JSON export, size limits, secret redaction and unit tests.
4. **Phone UI:** visible Diagnostics entry with latest session, trace count, last failure, Copy report, Share file, include-request-text toggle, optional screen evidence and Clear journal.
5. **Conversation handoff:** verify Android Sharesheet file/text delivery on the actual target phone; document copy-and-paste as the guaranteed path into the existing conversation.
6. **Live acceptance:** deliberately test successful action, unsupported action, denied permission, Shizuku unavailable/fallback, timeout, process crash/restart, offline state, and false-success/postcondition mismatch. For every case, the report must explain what happened without requiring the owner to reconstruct it manually.

## Acceptance gate

The bridge is complete only when a physical-phone test can be performed, the owner can copy a report into the existing conversation, and that report is sufficient to reconstruct the action timeline and distinguish interpretation failure, capability/permission failure, executor failure, and verification failure. Emulator tests and a green CI run do not satisfy this gate.

## Current implementation audit (2026-11-21)

The current `main` implementation is a useful first slice, not completion:

- `ActionTraceStore` persists a bounded 200-event local JSON journal using an atomic file replacement and a file lock. It records action, route, outcome, duration and a verification label; its default is local-only.
- `DeviceControlRouter.executeWithEvidence` records each backend considered, including unavailable routes and failed attempts, and only treats explicit `VerificationStage.VERIFIED` evidence as verified.
- `PhoneActionDiagnostics` exports a text report with embedded machine-readable JSON, app version, source revision, Android API, selected permission/service states and sanitized crash stack frames.
- Settings exposes Copy live test report, a text Sharesheet action and Clear local journal. An Android instrumented test exercises the report composition.
- Each device-control invocation now generates one parent trace ID and attaches it to every backend-attempt event; each attempt retains its own trace ID. This groups fallback rungs for a single action, but does not yet correlate upstream interpretation/planning events to that action. The current event schema still lacks complete interpretation, expected/observed postcondition and candidate-selection evidence.
- The current UI does not yet provide the full export controls in this contract (owner-selected request text, optional screen evidence, or a separate JSON file attachment). The text report is the present handoff.
- The action-journal report now includes manufacturer/model, Android release and the first-to-last event time window. It still does not include synchronized UI hierarchy or logcat evidence; the separate device support report is not the same artifact and must not be mistaken for a completed action trace.

A privacy defect was also found during this audit: the general subsystem diagnostic history could retain a recognized speech transcript, and the default support report could include exception messages and OS trace excerpts. The current main-line fixes reduce speech recognition events to status categories, exclude exception messages from the default active diagnostics, export only sanitized crash summaries, and leave OS trace contents out of the default report. These changes still require CI verification.

`CrashJournal` and `CrashRepairBridge` remain separate crash/development-request mechanisms; neither alone is the live-test bridge. `MainActivity` also routes incoming shared text intents through video intake, which should be corrected if Friday is expected to receive shared diagnostic text. Do not describe the current copy/share UI or these existing paths as the completed live-test bridge.
