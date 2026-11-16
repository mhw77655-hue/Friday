# Friday self-evolution and live-device bridge

## Product contract

Friday is the persistent on-device agent. GitHub is a remote workshop and audit trail, not Friday's brain and not a prerequisite for her ordinary operation. A language model is optional compute. The phone is an explicitly permissioned capability host.

## Implemented CI foundation

The repository build workflow now:
- runs on pushes to main and audit branches;
- runs for pull requests targeting main;
- can be started manually;
- runs on a daily schedule;
- builds the Android debug APK and runs the existing unit-test task;
- uploads the APK and JUnit XML;
- opens or comments on one deduplicated maintenance incident when a build/test run fails.

The incident step only reports failures. It does not modify source, approve a PR, install an APK, or publish a release.

## Required self-evolution loop

1. Observe: app health checks and device-reported failures become structured, redacted events.
2. Reproduce: CI checks out the exact commit and runs deterministic tests/builds.
3. Diagnose: produce a report with failing test, stack trace, environment, suspected owner and evidence.
4. Propose: a code agent may create a patch on an isolated branch and open a PR. It must cite the failing test and add a regression test.
5. Verify: clean GitHub-hosted runner builds and tests the exact patch. Run static analysis, dependency/security checks and regression tests.
6. Review: human approval remains required for changes to permissions, data access, networking, update/install, security boundaries, or model execution.
7. Deliver: publish a signed APK to a private/test release channel only after all gates pass.
8. Deploy: Friday downloads a signed update manifest, verifies signature, version, package ID and rollback constraints, then asks the user to approve installation through Android's supported package installer flow.
9. Learn: compare pre/post diagnostics and record whether the repair actually resolved the original failure. A green build alone is not proof of device-level repair.

No agent may push directly to main, disable tests, weaken permissions, alter signing keys, silently install an APK, or self-approve a release.

## Live phone access: correct network topology

A GitHub-hosted Actions runner cannot directly inspect a phone behind a carrier/Wi-Fi NAT, and GitHub is not a phone-control channel. The phone must initiate an authenticated outbound connection to a relay. Do not expose an ADB port, debug bridge, SSH daemon, or unauthenticated HTTP server to the public internet.

Recommended topology:

Phone app (device agent)
  -> outbound TLS/WebSocket connection
  -> authenticated relay (Cloudflare Worker + Durable Object or equivalent)
  -> signed, short-lived capability task
  -> Friday's local capability broker
  -> Android APIs guarded by runtime permissions and user consent
  -> redacted result
  -> relay
  -> GitHub issue/check/run attachment

The phone remains the authority for its sensors and actions. GitHub can request a task only through the relay's authenticated command protocol; it cannot bypass the phone's local permission checks.

## Device capability model

Expose narrow, typed operations rather than a remote shell:
- diagnostics.snapshot: app version, process health, memory/storage summaries, recent crash/ANR metadata where Android exposes it;
- diagnostics.export: a user-approved, redacted report;
- app.restart_component: restart Friday-owned components only;
- update.check: query signed release metadata;
- update.prepare: download and verify a candidate package;
- action.execute: only named app capabilities with an explicit per-action confirmation policy.

Never expose arbitrary shell, unrestricted filesystem access, raw microphone/camera streams, notification contents, SMS, contacts, credentials, or silent installation. Live sensor access must be initiated and visible on the phone, governed by Android permissions, foreground-service rules, and revocable user consent.

## Relay authentication and safety

- Pair the device using a one-time code shown on the phone and confirmed in the user's GitHub-connected account.
- Use short-lived, device-scoped credentials stored with Android Keystore; rotate and revoke them.
- Every command carries a nonce, expiry, unique task ID, allowed capability, bounded arguments and a signature/MAC.
- Reject replayed, expired, oversized, unknown or unauthorized tasks.
- Require explicit confirmation for consequential actions; read-only diagnostics can use a user-configured policy.
- Return the minimum data needed. Redact paths, identifiers, secrets, conversation content and personal data.
- Keep an append-only audit trail visible to the user.
- Relay outage must not stop Friday's local functions.

## What is not yet deployed

This repository does not yet contain a deployed relay, a registered GitHub App, a device-pairing credential issuer, an Android persistent device-agent service, or signed update infrastructure. Those require service configuration, secrets, Android permission review and on-device testing. The scheduled CI workflow is a first maintenance foundation, not a complete self-writing agent or live phone connection.

## Release acceptance criteria

- CI scheduled run produces APK + JUnit artifacts.
- A deliberate failing test creates/updates one maintenance incident.
- A proposed patch is isolated, reviewed, regression-tested and cannot merge itself.
- Phone pairing is explicit and revocable.
- Device agent survives normal process recreation using Android-supported lifecycle mechanisms.
- Remote tasks cannot exceed the allowlisted capability schema.
- No sensitive raw device data leaves the phone without explicit consent.
- A signed update is verified and user-approved before installation.
- Friday's identity, memory and local operation continue when GitHub, relay and all models are unavailable.
