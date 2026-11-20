# FRIDAY automatic device evidence delivery

## Objective

After one-time owner-authorized pairing, FRIDAY collects, preserves, sanitizes and uploads device evidence without requiring the owner to run ADB, copy logcat, export files or attach logs. The assistant retrieves new evidence through the connected GitHub integration when working on the project.

## Acquisition

Combine:
- CrashJournal and FRIDAY's own crash-safe structured runtime events.
- Startup, lifecycle, recovery, model, speech, audio, permission, Shizuku and resource-governance events.
- A narrowly scoped system-log excerpt obtained through the already-authorized Shizuku UserService when genuinely available.
- A compatibility/resource snapshot captured with the event.

Expose a typed Shizuku diagnostics operation with fixed reviewed commands, never caller-supplied shell. Report API presence, binder connection, permission grant, actual UID and operation-level success separately. If Shizuku is unavailable, collect the evidence available inside FRIDAY's own process.

## Privacy boundary

Never upload unfiltered device-wide logcat. Retain only FRIDAY package/process lines and a reviewed allowlist of Android lifecycle/runtime events needed to explain FRIDAY's failure. Omit conversation, prompts, memories, transcripts, audio, camera, screen/accessibility/notification contents, location, contacts, accounts, serials, Android IDs, network addresses, credentials, arbitrary URLs and unrelated application logs. Omit exception messages by default; retain bounded exception classes and stack frames.

Apply strict time-window, line-count and byte-size limits. Include schema version, capture time, app/build identity, source provenance, redaction counts and content hashes. Preserve original local evidence in app-private storage with bounded retention.

## Private delivery

The existing observer issue belongs to the public Friday repository and must not receive raw device logs. Use a separate private diagnostics destination (dedicated private repository or equivalent private object store) and a dedicated least-privilege credential, encrypted under its own Android Keystore key and separate from development/public-observer credentials.

FRIDAY writes sanitized bounded records to a durable local outbox, uploads when connected, uses stable event IDs and content digests for idempotent retry, and persists acknowledgements. No remote shell, arbitrary file reads, installation or unrestricted control commands are exposed.

The GitHub integration used by the development assistant must have read access to the private destination. During project work, the assistant retrieves new evidence directly; the owner no longer exports or attaches files. If the connector cannot access the private destination, automatic assistant retrieval is not complete and must not be claimed.

## Automatic triggers

Queue evidence on:
- Java/native crash or ANR.
- Failed/degraded startup or recovery.
- Microphone, speech-recognition, TTS or voice-session failure.
- Model load/inference failure or repeated provider fallback.
- Shizuku binder death, permission changes or failed privileged operations.
- Severe memory, thermal, storage or battery pressure affecting FRIDAY.
- A low-frequency bounded health summary.

WorkManager may retry delivery when Android permits. The outbox survives process death and reboot; the design does not claim FRIDAY can restart after Android force-stop. Upload is automatic after one-time pairing, but immediate assistant wake-up while no ChatGPT session is active requires a separate notification/automation integration.

## Acceptance test

1. Cause a controlled startup/audio failure on the physical handset.
2. Verify relevant evidence is captured without conversation or unrelated application content.
3. Kill FRIDAY before upload and verify the outbox survives.
4. Restore connectivity and verify automatic, deduplicated upload to the private destination.
5. Verify the connected assistant can retrieve the report without ADB, manual export or user attachment.
6. Verify public repository issues contain no raw/private device logs.
7. Revoke the private credential and verify uploads stop while local evidence remains preserved.

This is complete only after the real phone passes the test. Code, green CI or a public snapshot alone is not proof.
