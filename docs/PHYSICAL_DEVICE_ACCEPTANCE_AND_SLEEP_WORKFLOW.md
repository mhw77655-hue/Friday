# Physical-device acceptance and first complete user outcome

## Two different test environments

Friday uses two complementary validation loops:

1. **Disposable virtual phone twin (GitHub Actions):** fast, repeatable build, unit, instrumentation, launch, lifecycle, crash, permission and upgrade testing. It is deliberately synthetic and cannot prove OEM-specific behavior or a real Shizuku server.
2. **Owner's physical handset (phone-initiated acceptance):** verifies the installed candidate against the real Android/OEM environment. It is not a replacement for the emulator and must not be required for every code iteration.

The emulator must never receive a live control channel into the owner's handset or private app data.

## Physical handset proof without a permanently attached development PC

The existing GitHub Device Channel is a phone-initiated, outbound-only relay. Friday polls a public repository issue, accepts only owner-authored commands with an expiry of at most 30 minutes, and posts a bounded result. It does not expose a remote shell.

The new `shizuku.acceptance` command performs a read-only physical acceptance probe:

1. Friday checks that Shizuku is reachable and its own Shizuku permission is granted.
2. Friday binds its actual Shizuku UserService.
3. The UserService reports its own Linux UID through Binder.
4. Friday accepts only UID 2000 (shell) or UID 0 (root).
5. Friday makes a fresh typed diagnostics call to that same service.
6. The public result contains only the verified UID and Android API level. It excludes device model, fingerprint, PID, storage, battery, screen contents and personal data.

The GitHub issue is public. Commands and results are therefore public too; do not put secrets or private diagnostics in it. The probe is read-only and cannot install APKs, launch arbitrary apps, change settings, or execute caller-supplied shell commands.

### One-time owner setup

The physical handset must be explicitly enrolled once; Android intentionally does not allow a remote CI runner to silently grant Shizuku/ADB authority.

1. Install the candidate Friday build on the handset.
2. Start Shizuku using its supported device-local procedure.
3. Open Friday and grant Friday's Shizuku permission.
4. In Friday Settings, connect the GitHub account and connect the phone channel. This creates a public issue used as the command queue.
5. Use **Check now** once so the phone begins polling.

After that, an owner-authored, short-lived `shizuku.acceptance` command can request a fresh physical Shizuku proof without a cable, manual log export, or a PC permanently connected to the handset. The app must be online and Android must permit its scheduled worker to run; the manual **Check now** control is the deterministic fallback.

Example owner command comment:

```text
FRIDAY_DEVICE_COMMAND v1
id: shizuku-check-20261120-001
expires: 2026-11-20T19:00:00Z
action: shizuku.acceptance
```

The phone must be running a build that includes this command handler. The first install/update and Shizuku authorization remain explicit owner actions. The command does not bypass Android's permission model.

## Evidence levels

- **CI build passed:** candidate compiles and packages.
- **Emulator passed:** candidate behaves on the declared virtual Android profile.
- **Physical Shizuku accepted:** the actual handset's Friday process called a real Shizuku UserService and verified its shell/root UID.
- **Physical feature accepted:** a specific real-phone operation was executed and its postcondition independently read back.
- **Owner outcome accepted:** the complete user-requested workflow worked end-to-end on the handset.

Never promote one evidence level into a stronger claim.

## First user outcome: “Friday, prepare my phone for sleep”

This should be delivered as the first complete cross-layer workflow, not as a hardcoded phrase-to-toggle shortcut.

### Outcome contract

Friday should understand the desired outcome, inspect relevant current state, use the owner's saved sleep preferences, select available capabilities, execute the plan, and verify each postcondition. On the first use, or whenever a preference is ambiguous, she should ask before saving a default.

### Initial safe capability set

- Read current time, battery state, charging state and the next alarm exposed by Android's AlarmManager.
- Apply a user-approved Do Not Disturb mode using NotificationManager only after the owner grants Notification Policy Access.
- Adjust brightness only if the owner has granted the relevant system-setting access, otherwise offer the system settings handoff.
- Preserve alarms; never cancel or silently reschedule them.
- Do not silently enable airplane mode, disable mobile data, turn off connectivity, mute emergency/priority contacts, or change alarm volume.
- Record the before-state and the exact changes Friday made.
- Verify each change using a fresh Android API read-back.
- Provide a clear undo/wake action and restore only settings Friday herself changed, without overwriting a later manual change by the owner.

Android API behavior varies by OS version. In particular, Android 15+ applies app-owned Automatic Zen Rule behavior to many apps targeting the newer SDK; Friday must verify the resulting rule state rather than assume a global DND toggle occurred.

### Completion test

A real handset acceptance scenario must demonstrate:
1. Natural voice request is recognized.
2. Friday resolves the intended sleep outcome and uses the saved profile.
3. Friday checks the current device state and available permissions.
4. Friday applies only authorized, non-destructive changes.
5. Each changed setting is independently read back.
6. Existing alarms remain intact.
7. Friday reports any unavailable capability honestly.
8. The wake/undo path restores Friday's own changes without undoing later owner changes.

The phrase is ready for ordinary use only after this scenario passes on the owner's handset. Emulator success alone is not sufficient.

## Current known boundary

The GitHub Device Channel currently uses a public issue relay. The physical Shizuku probe reports minimal evidence only, but issue metadata and comments remain public. A future private relay may replace this transport; do not expand the public channel to screen contents, notifications, messages, contacts, location, arbitrary commands or file transfer.
