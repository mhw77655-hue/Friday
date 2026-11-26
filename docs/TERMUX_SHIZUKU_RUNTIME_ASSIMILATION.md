# Friday Termux + Shizuku runtime assimilation

## Objective

Friday's device runtime must be able to acquire and orchestrate the execution capabilities exposed by Termux and Shizuku. Termux is an execution substrate; Shizuku is a privileged Android API / shell bridge. Friday's own capability planner decides when and how to use them.

This is an implementation of an independent recovery path, not a claim that Android force-stop semantics have been bypassed.

## Implemented in this change

- `TermuxRuntimeBridge` calls Termux's documented explicit `com.termux.RUN_COMMAND` service.
- Friday declares `com.termux.permission.RUN_COMMAND` and package visibility for Termux and Termux:Boot.
- Friday Settings exposes Termux detection, permission guidance, install/start and pause/remove controls.
- The bootstrap payload is fixed in Friday source. No model-generated executable path, shell command, script body or argument vector is accepted.
- The payload writes a Termux-owned worker to `~/.termux/boot/friday-guardian`, then starts it immediately.
- The worker obtains a wake lock, checks for the official Shizuku `rish` helper and DEX in Termux HOME, verifies that `rish -c id` returns shell UID 2000 or root UID 0, inspects Friday's package stopped state, and asks Android to launch Friday when `stopped=true`.
- It records attempts and verification results in `~/.friday-guardian.log`.
- Pause creates `~/.friday-guardian.pause` and removes the boot script. The running worker observes the pause marker and exits.

## Required one-time owner setup

1. Install Termux and Termux:Boot from compatible trusted distribution builds. Termux add-ons must use the matching signing key.
2. Open Termux and configure `allow-external-apps=true` in `~/.termux/termux.properties`; restart Termux.
3. In Android App info for Friday, grant **Run commands in Termux environment** (`com.termux.permission.RUN_COMMAND`).
4. Open Termux:Boot once so Android registers its boot receiver.
5. In Shizuku, use **Use Shizuku in terminal apps** and export the official `rish` and `rish_shizuku.dex` files.
6. Copy both files into Termux HOME (not shared storage). In Termux, run `chmod 700 ~/rish && chmod 400 ~/rish_shizuku.dex`.
7. Run `RISH_APPLICATION_ID=com.termux ~/rish -c id` and grant Termux access in Shizuku. Confirm `uid=2000(shell)` for ADB mode or `uid=0(root)` for root mode.
8. In Friday Settings, open **Independent Recovery Infrastructure** and tap **Install / start guardian**.

Friday's guardian installer cannot grant Android permissions, change Termux's `allow-external-apps` property, approve Shizuku, or silently initialize Termux:Boot. Those are explicit platform trust decisions.

## Primary recovery anchor: Shizuku daemon UserService

Friday also has a stronger primary recovery path than Termux alone. Its Shizuku UserService is created with `daemon(true)`. Shizuku's API documentation states that daemon UserServices continue running when the client app process dies, until explicitly unbound. The service process runs with shell UID 2000 or root UID 0, not Friday's ordinary app UID.

The new owner-controlled Shizuku recovery supervisor runs inside that daemon process. It periodically checks Friday's package stopped state, requests an explicit launcher activity start when stopped, and verifies the resumed activity. Events are written to `/data/local/tmp/friday-shizuku-guardian.log`.

This creates two recovery layers:
- **Primary:** Friday's Shizuku daemon UserService survives Friday's ordinary process death/force-stop while Shizuku remains running.
- **Secondary:** the Termux-owned worker can re-establish recovery after Shizuku is restarted, provided Termux is still running and the owner reactivates Shizuku where required.

Shizuku explicitly kills UserServices when its own server stops or restarts. Therefore the Shizuku daemon is not a reboot-surviving or Shizuku-server-independent process. The Termux worker is the independent fallback.

## Runtime behavior

- The primary Shizuku recovery supervisor is hosted by Friday's daemon UserService under shell/root identity and is independent of Friday's ordinary application process.
- The Termux worker is not hosted by Friday's package and does not call Friday's WorkManager, services or RUN_COMMAND service to stay alive.
- The worker invokes the Shizuku helper under Termux's separately authorized Shizuku identity.
- It checks package stopped state before launching Friday, and verifies the resumed activity after a launch attempt.
- If Shizuku is unavailable, Termux remains alive and retries at a bounded interval. It does not claim recovery succeeded.
- Shizuku daemon UserServices are killed when the Shizuku server stops or restarts; Termux is intended to provide the fallback once Shizuku is available again.
- After a non-root Wireless Debugging Shizuku restart/reboot, the owner may need to start Shizuku again. The worker detects the missing shell capability and waits.
- If Termux itself is force-stopped or killed, this worker cannot run. Termux:Boot may start it on a subsequent boot if Android permits and Termux has not been force-stopped.
- Android/OEM process policies still govern Termux. Battery exemption and a wake lock improve persistence; neither is an absolute guarantee.

## Validation protocol

Run on the actual target phone; emulator tests cannot prove Shizuku or Termux lifecycle behavior.

1. Verify Termux is installed, Friday's RUN_COMMAND permission is granted, and the Termux setting allows external apps.
2. Verify Termux:Boot has been initialized and the boot script exists.
3. Verify the worker log contains `guardian-started`.
4. Verify the worker logs a successful `rish` identity (shell UID 2000 or root UID 0).
5. While Friday is running, force-stop Friday from Android Settings or with a controlled ADB test.
6. Confirm the worker logs `friday-stopped-recovery-attempt`, starts Friday, and records `friday-foreground-verified`.
7. Separately force-stop Termux, stop Shizuku, reboot the device, and test battery/OEM process management. Record each failure mode separately.
8. Confirm **Pause guardian** stops the loop and removes its Termux:Boot entry.

## Important implementation boundary

The Settings action reports that a RUN_COMMAND request was submitted; it does not equate that with a successfully installed or running worker. The log and physical-device acceptance test are the evidence. A future iteration should add a bounded PendingIntent result channel and a device acceptance test that reads a signed guardian heartbeat.

## References

- Termux RUN_COMMAND API: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
- Termux execution environment: https://github.com/termux/termux-packages/wiki/Termux-execution-environment
- Termux:Boot: https://github.com/termux/termux-boot
- Shizuku UserService: https://github.com/RikkaApps/Shizuku-API
- Shizuku rish helper: https://github.com/RikkaApps/Shizuku/blob/master/manager/src/main/assets/rish
