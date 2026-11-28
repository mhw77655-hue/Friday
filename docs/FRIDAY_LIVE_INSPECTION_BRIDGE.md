# Friday live inspection bridge — implementation contract

**Controlling issue:** [#188](https://github.com/mhw77655-hue/Friday/issues/188)

## Current implementation boundary

Friday already creates a local `friday-device-report-v2` report. That report is intentionally useful for offline support, but it contains more detail than should be exposed to a remote inspection peer. The new `FridayInspectionSnapshot` projection is the only payload permitted to cross the future live-inspection boundary.

It copies only:
- app version, version code and a strictly validated 40-character source revision;
- Android API level and device-capture timestamp;
- closed-vocabulary status values for network, battery, Shizuku, microphone permission, speech recognition, voice runtime and model runtime;
- available memory/storage byte counts;
- counts of bounded local diagnostic events and failures.

It never copies raw diagnostic events, failure messages, stack traces, exception text, model names, screen state, notification content, conversation/transcript/audio data, personal memory, files, credentials or tokens. Free-form status strings are normalised to a closed vocabulary before export.

## Required transport contract

The transport must remain outbound-only from the phone. A future authenticated session must use:
- owner-visible pairing and a device-bound key generated on the handset;
- short-lived, audience-bound session credentials and server nonce challenge;
- TLS plus message-level request binding, monotonic sequence and expiry;
- replay rejection, per-device rate limits, bounded payload sizes and audit records;
- immediate local revocation and server-side revocation propagation;
- a read-only method allowlist. No generic RPC, shell, file read, tap, text entry, settings mutation or Shizuku command.

The remote MCP surface is limited to `friday_status`, `friday_inspection_snapshot` and `friday_inspection_events_summary`. It must not expose an arbitrary proxy into Android APIs.

## Hosting and ChatGPT connection

The Android projection is not itself a cloud connection. A reachable, private HTTPS MCP host/relay and an authenticated device session are still required. The repository currently contains no Friday-owned deployment target or deployment credential. Do not use the unrelated Venture Autopilot Supabase project, public GitHub Issues, or a public unauthenticated port for private device telemetry.

The ChatGPT custom MCP connection is a separate one-time account/workspace action. It cannot be created by committing Android source. Do not report the phone as connected until this exact ChatGPT session retrieves a fresh timestamped snapshot and a revocation test returns denied.

## Proxima decision

Upstream Proxima is a desktop-local multi-provider gateway and its current published licence is personal/non-commercial. It is not a suitable embedded dependency for Friday's commercial product or a phone-to-cloud transport. Its orchestration concepts may inform a separately implemented, licence-clean Friday model router; it is not part of this read-only inspection bridge.
