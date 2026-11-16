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

## Implemented repository and phone connection

The current development branch now contains:
- An Android Settings connection for a fine-grained GitHub token, encrypted with Android Keystore.
- A phone-initiated workflow dispatch that sends a bounded, explicitly consented technical snapshot to the proposal worker.
- A read-only OpenCode proposal job and a separate trusted publisher that creates draft PRs only.
- Automatic proposal generation when the main-branch Android CI workflow fails. It reads failure logs with Actions read permission, filters/redacts the evidence, and proposes one root-cause repair.
- A phone-initiated GitHub Issues command channel. Friday polls every 15 minutes via WorkManager (subject to Android scheduling) and accepts only owner-authored, expiring `diagnostics.snapshot` commands.
- Phone-side proposal lookup and explicit channel disconnect.

The channel uses a public GitHub issue because the repository is public. The bounded device snapshot and replies are visible to repository readers. The app warns before sending. No private conversation, location, contacts, notifications, raw audio/video, screen content, files, or unique device ID is transmitted.

## Still not deployed or verified

- The workflow must be merged to the repository default branch before Android can dispatch it.
- The owner must create a fine-grained token with Actions read/write, Pull requests read, and Issues read/write for this repository only. Contents write is not required by the phone.
- The new Android bridge and workflow still require a green current-head CI run.
- The phone must be installed and paired before the command channel can return real device telemetry.
- The channel is periodic pull, not an always-on socket. Android may defer it for Doze, battery optimization, or OEM policy.
- It provides read-only diagnostics, not unrestricted remote control. A future capability broker must remain typed, permission-bounded, visible, and revocable.
- No signed update installation, automatic merge, or autonomous release is implemented.

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

## Owner vision source — now wired into development

The repository root contains the original `New Folder chats.zip` archive. `.ralph/OWNER_WORDS.md` contains curated, provenance-labelled excerpts from the owner's own conversation turns; `.ralph/VISION.md` now includes direct owner statements and points to the excerpt file. The archive is not treated as a flat prompt dump: assistant-authored text pasted into a user turn is not silently attributed to the owner, historical requests are not allowed to override later corrections, and the latest authenticated owner instruction wins.

Every Ralph run now receives both `VISION.md` and `OWNER_WORDS.md` in its composed prompt. The phone-originated repository worker is also instructed to read both, inspect the original archive when the curated excerpts do not resolve an actual ambiguity, and avoid turning outcomes into fixed modules, taxonomies or a chatbot architecture. `.ralph/owner_vision_check.sh` is an offline regression check for this prompt injection and runs in CI.

## Android persistent runtime — implemented on the development branch

The Android app now has an owner-controlled **Keep Friday alive** setting backed by `FridayRuntimeService`:

- A visible foreground-service notification keeps the runtime user-visible.
- `START_STICKY` asks Android to recreate the service after ordinary process reclamation when the OS permits it.
- The boot/package-replacement receiver restores the non-microphone core when the owner enabled persistence and Android permits the start.
- Friday exposes Android's battery-optimization exemption settings so the owner can exempt the app from Doze restrictions.
- When persistence is enabled from the visible Settings screen and microphone permission is granted, capture is hosted by the foreground service rather than relying on the activity's lifecycle.
- A sticky restart or boot does **not** silently reopen the microphone. The owner must open Friday in the foreground before microphone capture is resumed.
- The notification has an explicit Stop Friday action; disabling the setting clears the persistent preference and stops the service.

This is an Android-supported persistence strategy, not an anti-kill exploit. It cannot defeat force-stop, OEM-specific task killers, user permission revocation, thermal shutdown, or Android policy. Android 14+ imposes while-in-use restrictions on microphone foreground services; Android 14+ also requires declared foreground-service types and permissions. Android 15+ forbids certain service types (including microphone) from being launched by a boot receiver. Friday therefore restores only its non-microphone runtime at boot and waits for a visible user launch to resume voice. See the [foreground-service type requirements](https://developer.android.com/about/versions/14/changes/fgs-types-required), [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), and [Android 15 boot restrictions](https://developer.android.com/about/versions/15/changes/foreground-service-types).

The `specialUse` type is declared for the user-enabled companion runtime with an explicit manifest subtype. If distributed through Google Play, this use case is subject to Play's foreground-service review; sideloading the APK does not remove Android's runtime restrictions.

**What this does not prove:** it does not establish subjective consciousness, continuous perception, fully autonomous initiative, or a fully developed learning loop. It provides a more durable execution host for the existing identity, memory, cognition, presence, voice and resource systems. Those systems still need integrated, on-device Life Tests showing that real experience changes later behavior.



## Shizuku Doze persistence assist

The foreground runtime settings now expose a Shizuku-backed **Protect Friday** action. The Binder contract accepts only a boolean and the shell service hardcodes the package name `com.jarvis.app`; it runs the fixed `cmd deviceidle whitelist +/-com.jarvis.app` command and reads the Device Idle whitelist back before reporting whether the change was verified. It does not expose arbitrary shell execution.

This is a Doze/battery-idle exemption assist, not a literal anti-kill switch. It can reduce idle restrictions where the Android build permits the shell command. It cannot defeat force-stop, every OEM task killer, permission revocation, thermal shutdown or Android's process lifecycle rules. The owner can remove the whitelist entry from the same Settings control.
