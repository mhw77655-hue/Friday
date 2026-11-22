# Friday Shizuku device agency

## Purpose

Shizuku is not a connection badge. It is one privileged execution substrate in Friday's device capability fabric. Friday's app process must never assume that a running Shizuku server means an operation was executed.

## Verified execution contract

1. Shizuku Binder is reachable.
2. Friday's per-app Shizuku permission is granted.
3. Friday binds its dedicated UserService.
4. The UserService reports its own Linux UID over Binder.
5. Friday accepts only UID 2000 (Android shell) or UID 0 (root).
6. Each requested operation uses a typed Binder method with bounded arguments.
7. The operation returns evidence from the device; the router reports success only when that evidence meets the operation's verification contract.

A Binder connection without the expected execution identity is not a usable privileged capability.

## Current typed Shizuku capabilities

- `identity`: prove the remote UserService UID and PID.
- `diagnosticsSnapshot`: return bounded system, storage, battery and build diagnostics.
- `captureScreenSummary`: capture the foreground UI hierarchy and extract visible text.
- `setDozeWhitelist`: add or remove only Friday's own package from Device Idle whitelist and verify by reading the whitelist back.
- `launchApp`: launch an installed package through Android's launcher intent and verify that the package owns the resumed/top-resumed activity.

The app-launch operation accepts a package name, not a command. Package names are validated before use. The service uses an argument-vector ProcessBuilder with fixed executable and argument structure; it does not expose arbitrary shell, scripts, paths or command strings.

## Device Agency Fabric — target architecture

Friday should reason over capabilities, not memorize one implementation per user request.

A capability descriptor should declare:
- semantic operation and outcome;
- backend(s) that can perform it (Android API, Intent, MediaSession, Accessibility, Shizuku, optional ADB bridge);
- required permission and Shizuku identity;
- argument schema and resource bounds;
- risk tier and confirmation policy;
- expected side effects and reversibility;
- verification probe and success evidence;
- fallback and compensation strategy.

At runtime:
1. Observe the requested outcome and relevant device state.
2. Discover currently usable capabilities, including actual Shizuku service identity.
3. Build a small action plan with preconditions and expected postconditions.
4. Prefer the narrowest ordinary Android API; use Shizuku when it adds a necessary capability.
5. Ask for confirmation when the risk policy requires it.
6. Execute one bounded operation at a time.
7. Verify the postcondition from a fresh device read, not from command exit text alone.
8. Record an action receipt: intent, chosen backend, privilege identity, operation, before/after evidence, duration, result and failure reason.
9. Learn from the receipt so future plans choose more reliable methods on this device.

## Non-negotiable boundaries

- Never expose an arbitrary shell tool to the language model.
- Never treat a successful process start or Binder bind as proof that the user's goal happened.
- Never claim an action succeeded without a fresh postcondition check.
- Keep read-only inspection separate from mutation.
- Require confirmation for disruptive or privacy-sensitive actions.
- Prefer reversible operations and implement rollback where possible.
- Make every capability individually revocable and observable.
- Do not bypass Android's permission model, force-stop semantics, foreground-service rules or OEM power policies.
- If Shizuku is unavailable, Friday continues through ordinary Android APIs and reports the missing capability honestly.

## Physical-device acceptance runner

Friday Settings now exposes a one-tap Shizuku acceptance sequence. It:
1. waits for a verified shell/root UserService identity;
2. requests a bounded diagnostics snapshot and checks that its UID matches the identity probe;
3. reads the existing Friday Doze-whitelist state;
4. tests add and remove with read-back verification, then restores the original state even if an intermediate check fails;
5. launches Android Settings and verifies the resumed activity.

Each result is recorded as a privacy-filtered action event so the owner can upload it through the GitHub device-report channel. The runner does not execute arbitrary shell commands and does not change the owner's final Doze setting.

## Current limitations

The Shizuku backend is not yet a universal device controller. The production router currently has MediaSession and Shizuku backends; the Shizuku backend supports screen capture and app launch. Diagnostics and Doze-whitelist changes are invoked through the Settings surface. Other action types still require explicit typed operations and verification contracts.

CI can verify compilation and unit tests, but only a physical handset with Shizuku started and Friday explicitly authorized can prove real shell/root execution. The device acceptance test must record the remote UID, launch a harmless known app, verify its resumed activity, read diagnostics, and test Doze whitelist add/remove with read-back verification.
