# Jev-backed voice control

Friday uses TypeSafe Jev as a bounded decision organ, not as the conversational brain and not as the Android executor.

## Runtime loop

1. Android speech recognition produces a transcript.
2. Friday's deterministic parser handles simple, exact commands without an extra model call.
3. For natural device requests outside that small vocabulary, Friday's existing `ModelManager` (the same brain used for conversation) receives the utterance plus a short-lived accessibility snapshot and emits one bounded typed `PhoneControlCommand`. This is not a second model. Password and editable-field contents are excluded from the screen snapshot.
4. Friday's phone-action organ sends the proposed action to TypeSafe Jev's official `POST https://api.typesafe.ai/v1/systemone` endpoint using `jev-latest`, when the user has configured a key. Jev evaluates the same action through Choice, Score and Noul questions.
5. Friday interprets those judgments through local thresholds and policy. Jev cannot execute Android actions, grant permissions, or override local confirmation.
6. Android's accessibility service re-resolves the target against the current active screen and attempts the action only if the service and target are available.
7. Friday reports the observed result; unsupported or unverified actions are not claimed as successful.

If no Jev key is configured, Friday's local policy allows only recognized low-risk actions without confirmation; screen taps and text entry still require a short-lived spoken confirmation. If Jev is unavailable, the same local policy remains authoritative: low-risk allowlisted actions can proceed, while consequential actions require confirmation.

## Credential handling

The user enters their own TypeSafe API key in Settings. It is encrypted using AES-GCM with a non-exportable Android Keystore key. It is not compiled into BuildConfig, committed to Git, or written as plaintext SharedPreferences. Requests go directly from the phone to TypeSafe over HTTPS. The state sent for a decision is limited to the recognized utterance, proposed action description and risk tier.

The Jev service is hosted. It requires internet and a TypeSafe account/API key; this is not an offline Jev model and no model weights are bundled in Friday.

## Current supported voice commands

- Navigation: back, home, recents, notifications, quick settings
- Apps: open/launch an installed app by its exact visible app label
- Settings: open Android Settings; open the Internet/connectivity panel
- Media: play, pause, next track, previous track through an accessible media session
- Volume: increase/decrease media volume
- Screen: scroll up/down
- Semantic UI: Friday's existing model can select a visible text label, content description or Android view ID from the current screen snapshot. Jev-approved low-risk navigation can execute directly; consequential targets such as Send, Delete, Pay, Transfer, Install or Reset still require spoken confirmation.
- Text entry: type the exact user-requested text into a verified focused editable field, with a 1000-character per-action limit. Ordinary text is medium risk and may execute when Jev approves; password/PIN/card/recovery-key-like content is high risk and always requires local confirmation. The literal typed payload is redacted before Jev evaluation.
- Natural device requests: one bounded action is planned at a time using the existing Friday model; if the target is missing or ambiguous, Friday asks a clarification rather than inventing a control

Media control requires Android notification/media-session access. Accessibility actions require the user to enable Friday's accessibility service. Some apps expose little or no semantic accessibility information.

## Explicit platform limits

- Android restricts direct Wi-Fi toggling by ordinary third-party apps on modern versions. Friday opens the system connectivity panel rather than pretending it can toggle protected settings.
- Secure windows, permission prompts and protected app surfaces remain inaccessible.
- A typed Jev result is schema-safe, not guaranteed true. Confidence is a decision signal, not proof. High-impact actions need local checks and explicit user confirmation.
- The planner deliberately emits one action at a time. Multi-step workflows, message sending, purchases, deletion, and security changes are not silently chained or auto-executed; each consequential interaction remains visible and confirmation-gated.

## Validation

Unit tests cover the Jev execute/confirm/reject policy, Friday-model screen-action planning, refusal to invent targets, focused-field checks, confirmation, and command parsing. Build and unit-test CI is required before this integration is considered verified on the development branch. Real device validation is still needed for the user's Android firmware, installed speech recognizer, accessibility semantics and media-session permissions.
