# REA first static Android investigation — 2026-11-26

**Status:** real REA MCP package inspection completed; class/method tracing and handset runtime tests are follow-up work.

## Provenance

- Upstream tool: `rea-agents@6.10.1`
- npm integrity: `sha512-3AWrfFEXppvC98FWhZR2Tzulnqs5xJQrSKmAub6+QME/I58Pr3h8pHqqwM30z2DvlnIx/8Hsu8woMngfi4SNyg==`
- MCP server: `rea` 6.10.1, protocol `2025-03-26`
- Live catalog: 263 tools, including Android APK inspection and Android ADB/device tool families
- Android provider: JADX headless 0.7.1, SHA-256 `6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75`
- Environment: Node 24.22.1, full JDK 21
- Run: [GitHub Actions run 45772567145](https://github.com/mhw77655-hue/Friday/actions/runs/45772567145)
- Evidence artifact: `friday-rea-android-evidence-45772567145`

The real MCP server was started over stdio, initialized, queried with `tools/list`, and called using `tools/call` for `inspect_android_package` on each target. All three operations returned structured REA Evidence. `rea doctor --provider jadx --json` reported `healthy: true` for the selected Android provider. Its broader `environment_healthy: false` reflects unrelated missing native providers and an unconfigured client skill/registration; it does not negate the healthy JADX provider.

## Target identities

| Target | Package | Version | minSdk / targetSdk | Classes | Manifest components | SHA-256 |
|---|---|---:|---:|---:|---:|---|
| Friday production APK | `ai.friday.assistant` | 1.0.547 (1547) | 26 / 36 | 13,267 | 33 | `231ee7244bd5d3b6adf5219a202a12af95536c9d78d59ac6f910acf080d53075` |
| Shizuku official APK | `moe.shizuku.privileged.api` | 13.6.0.r1086.2650830c (1086) | 24 / 36 | 1,786 | 14 | `6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f` |
| Termux official GitHub arm64 APK | `com.termux` | 0.118.3 (1002) | 24 / 28 | 3,423 | 12 | `72fdb596045116bf5ba1b5bdf5b26fddb9acc0bd074ad9f2da9eb0ae85e83a4e` |

The Termux APK is the official GitHub arm64 debug build, not a claim that it is byte-identical to the version installed on the owner's phone.

## Friday manifest findings

The shipped Friday 1.0.547 APK already includes Shizuku integration markers:

- Requests `moe.shizuku.manager.permission.API_V23`.
- Declares `rikka.shizuku.ShizukuProvider` as an exported provider, protected by `android.permission.INTERACT_ACROSS_USERS_FULL`.
- Declares a non-exported `FridayRuntimeService`.
- Declares a non-exported `FridayRuntimeBootReceiver` listening for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
- Has separate-process services for voice (`:voice`), inference (`:inference`), voice interaction (`:assistant` / `:voiceassistant`), and other platform integrations.

This confirms the Shizuku API and runtime components are already present in the release artifact. It does **not** prove their code paths work, that Friday has shell identity, or that Friday can restart after its own package is force-stopped. The next REA class/method investigation must inspect the actual Shizuku binding/permission calls and the runtime/boot service code before proposing changes.

## Shizuku manifest findings

The official manager APK declares:

- Package `moe.shizuku.privileged.api`, version 13.6.0, target SDK 36.
- `BootCompleteReceiver` is exported and listens for both `LOCKED_BOOT_COMPLETED` and `BOOT_COMPLETED`.
- The manager exposes its authorization request activity and legacy binder-request receiver.
- It includes its ADB pairing service.

These are declarations only. They do not show whether wireless-debugging Shizuku is active on the target phone, whether it survives a reboot, or which UID a particular client receives.

## Termux manifest findings

The official Termux 0.118.3 APK declares:

- Package `com.termux`, target SDK 28.
- `TermuxService` is non-exported.
- `RunCommandService` is exported but protected by the app-defined `com.termux.permission.RUN_COMMAND`.
- The APK itself does not declare a boot receiver.
- It requests `WAKE_LOCK`, `FOREGROUND_SERVICE`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, among other permissions.
- Its manifest declares `android:debuggable=true` and legacy `android:sharedUserId="com.termux"`.

The separate Termux:Boot add-on must be inspected as its own package. Do not attribute its boot receiver or startup behavior to the Termux main APK. A declared wake lock or foreground-service permission is not proof of indefinite survival: actual behavior still depends on service/process state, battery policy, user force-stop, and OEM behavior.

## Immediate technical conclusions

1. Friday already has a Shizuku API footprint; don't duplicate the SDK integration blindly. First trace and test the existing code.
2. Friday's own runtime/boot components are in Friday's package. Static presence does not make them an independent recovery anchor after Friday is force-stopped.
3. Termux's exported `RunCommandService` is a permission-gated command boundary, not unrestricted access. Its main service is private.
4. Termux:Boot is a separate package; its behavior must be studied separately.
5. Shizuku manager boot handling and Shizuku's ADB/shell execution mechanism must be traced through classes/methods and tested on-device. The manifest alone cannot establish the granted UID or actual command capabilities.
6. Friday should absorb mechanisms into her own code where platform rules permit. Keep a separate helper or external dependency only where the platform requires a distinct installed package/process or separately granted identity.

## Explicit evidence limitations

- `inspect_android_package` is static APK inspection; APK code was not executed.
- APK signature verification was not performed by this operation. REA's `inspect_apk_signature` must be called separately before signer conclusions.
- No handset or ADB device was attached to this CI run. There is no runtime evidence for background persistence, Shizuku server activation, shell UID, package force-stop, boot, or relaunch.
- JADX class counts and decompiled metadata do not prove every dynamic/reflection/JNI relationship.

## Next REA investigation

The next workflow pass searches the live APKs for Shizuku binding/authorization/UserService classes, Termux command-service and boot classes, and Friday's Shizuku/runtime/self-repair/Builder classes. It will retain the search Evidence. Follow-up calls should then inspect exact class and method bodies and trace incoming references before any native Friday code is changed.


## Follow-up REA class and method investigation

A second real REA MCP run completed with class inventory, class decompilation, method inspection and cross-reference tracing. It included the separate Termux:Boot APK. Run: [45773851184](https://github.com/mhw77655-hue/Friday/actions/runs/45773851184). Evidence artifact: `friday-rea-android-evidence-45773851184`.

### Friday's existing Shizuku daemon

REA inspected the production APK's actual DEX methods:

- `ScreenBridge.bind(Context)` creates a `UserServiceArgs` for `FridayShellService`, sets process suffix `device-capability`, `daemon(true)`, stable tag `friday-device-capability-core`, and UserService version **6**.
- `FridayShellService.identity()` reports only the remote UID and PID. It has no process-start timestamp or unique instance token, so Friday cannot distinguish a surviving daemon from a newly created daemon that happens to reuse a PID.
- `FridayShellService.launchApp(packageName)` uses a fixed Android MAIN/LAUNCHER intent via `am start`, then reads ActivityManager state to verify the target package resumed. This is already a suitable typed relaunch primitive for a future watchdog.
- `FridayShellService.destroy()` cancels outstanding APK transfers and kills its own UserService process. Explicit service removal therefore has a deliberate process-destruction path.

The official Shizuku API contract states that a UserService runs in a separate process as root UID 0 or shell UID 2000 (ADB mode). With daemon mode enabled, it persists until explicitly unbound; non-daemon mode ends with the caller process. The important boundary is that Shizuku server stop/restart kills the UserService too. See the [official Shizuku API documentation](https://github.com/RikkaApps/Shizuku-API/blob/master/api/src/main/java/rikka/shizuku/Shizuku.java#L3040-L3274).

**Engineering conclusion:** Friday already uses the key daemon mechanism. The missing work is to observe/reuse the existing daemon without accidentally creating a replacement, prove force-stop survival on the actual phone, and only then add an autonomous watchdog inside this privileged process. PR #174 implements the non-creating `peekUserService` continuity probe and unique instance identity.

### Termux and Termux:Boot mechanisms

REA inspected the exact methods in the official Termux 0.118.3 and Termux:Boot 0.8.1 APKs:

- `RunCommandService.onStartCommand` validates the requested action, the external-app execution policy, executable path and file permissions, then forwards a typed execution request to TermuxService. It is not an unrestricted command tunnel.
- `TermuxService.onStartCommand` promotes itself to a foreground service, handles execute/wake-lock/wake-unlock actions, and returns Android's `START_NOT_STICKY` value. Its `onDestroy` releases wake locks and stops/kills remaining command tasks unless it is performing an intentional stop.
- `actionAcquireWakeLock` acquires a CPU partial wake lock and a Wi-Fi lock, requests battery-optimization exemption when absent, and refreshes the foreground notification.
- The separate `com.termux.boot` package's `BootReceiver.onReceive` reads executable scripts from Termux's `.termux/boot` directory, sorts them, and schedules each through JobScheduler with a short override deadline.
- `BootJobService.onStartJob` starts Termux's foreground `TermuxService` with a background execution request.

The BootReceiver and JobService belong to **Termux:Boot**, not the main Termux APK. The inspected add-on is `com.termux.boot` 0.8.1 (versionCode 1000), 686 classes, target SDK 28. This is the key separation to preserve when adapting the design: boot scheduling is owned by a separate package identity. Friday must not add Termux as a dependency; it should implement the needed lifecycle mechanism within Friday's own architecture where Android allows it.

### Friday's remaining engineering blockers

REA inspected the production APK's current implementations and the matching repository source:

- `FridayRuntimeService.onStartCommand` promotes Friday's own service to foreground and returns `START_STICKY`. `FridayRuntimeBootReceiver` handles BOOT_COMPLETED and MY_PACKAGE_REPLACED when the runtime setting is enabled. Both components still belong to Friday's package and therefore are not an independent force-stop recovery anchor.
- `CloudVmSandboxExecutor.stressTest` unconditionally returns failure because the cloud VM sandbox is not implemented. It explicitly fails closed and refuses candidate promotion.
- `SelfRepairLoop` contains a real observe/diagnose/propose/apply/verify/rollback/gate pipeline, but its own source documentation says it is tooling/test-only and is not instantiated in the live FridayEngine. Its mandatory sandbox gate cannot pass with the current stub.
- `ObjectiveBuilderCandidateExecutor.execute` uses Friday's resident model to draft a bounded patch and publishes an isolated GitHub candidate PR. It does not invoke REA and does not itself run the independent cloud sandbox.

This corrects the delivery priority: the next major product milestone is not inventing another Shizuku bridge. It is (a) proving and extending the existing Shizuku daemon recovery anchor, and (b) connecting REA plus an isolated GitHub Actions sandbox to Friday's live engineering worker so her Builder and self-repair system can actually investigate, test and verify changes.

