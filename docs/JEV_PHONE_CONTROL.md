# Jev-backed voice control

Friday uses TypeSafe Jev as a bounded decision organ, not as the conversational brain and not as the Android executor.

## Runtime loop

1. Android speech recognition produces a transcript.
2. Friday's deterministic parser maps only explicit, supported commands to a typed `PhoneControlCommand`.
3. If a TypeSafe key is configured, `JevDecisionClient` sends the utterance, proposed action and risk tier to `POST https://api.typesafe.ai/v1/systemone` using `jev-latest`.
4. Jev returns a typed `choice` plus confidence/probabilities: execute, confirm or reject.
5. `JevPhoneActionPolicy` requires at least 0.90 confidence and execute probability.
6. Android-side code still checks the actual permission/capability and executes the action. Jev cannot grant permissions or bypass Android restrictions.
7. Friday reports the observed outcome; unsupported or unverified actions are not claimed as successful.

If no Jev key is configured, Friday's offline fallback allows only the deterministic low-risk commands. If a key is configured but the Jev service cannot be reached, the action is held rather than silently bypassing the decision gate.

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
- Semantic UI: tap visible text or type into the focused field (held pending a real confirmation flow)

Media control requires Android notification/media-session access. Accessibility actions require the user to enable Friday's accessibility service. Some apps expose little or no semantic accessibility information.

## Explicit platform limits

- Android restricts direct Wi-Fi toggling by ordinary third-party apps on modern versions. Friday opens the system connectivity panel rather than pretending it can toggle protected settings.
- Secure windows, permission prompts and protected app surfaces remain inaccessible.
- A typed Jev result is schema-safe, not guaranteed true. Confidence is a decision signal, not proof. High-impact actions need local checks and explicit user confirmation.
- Arbitrary multi-step tasks, sending messages, purchases, deleting data and changing security settings are not auto-executed by this command layer.

## Validation

Unit tests cover the Jev execute/confirm/reject policy, confidence threshold, and command parsing. Build and unit-test CI is required before this integration is considered verified on the development branch. Real device validation is still needed for the user's Android firmware, installed speech recognizer, accessibility semantics and media-session permissions.
