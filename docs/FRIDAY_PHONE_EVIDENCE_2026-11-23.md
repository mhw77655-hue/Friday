# Physical-phone evidence review — 2026-11-23

**Evidence source:** repository archive `New folder (1).zip`, retrieved from the public repository and unpacked without modifying the original.  
**Archive SHA-256:** `396917ab1b9665c4336c88b9b7a99cc3e8e9017007a319a550695b19b8ab51bb` (the GitHub Actions transport artifact; the inner ZIP bytes were preserved).  
**Evidence inventory:** 39 entries: two text records, 37 screenshots and one `friday-device-report-v2` JSON report.  
**Installed app:** Friday 1.0.510, source revision `24e653589fa623ce5819387a9179dfe878a0a720`.  
**Host:** Android 14 / API 34. Device-specific identifiers are intentionally omitted here.

## 1. Observed phone state

The report was generated at `2026-11-23T18:46:11Z`.

- 2.3 GB available RAM of approximately 7.4 GB; 31.8 GB free storage.
- The active diagnostic run produced 22 findings: 8 PASS, 9 INFO, 5 WARNING, 0 ERROR.
- Friday architecture/self-diagnosis produced 20 findings: 14 PASS, 4 WARNING, 2 FAILURE.
- The two FAILURE findings were the FailureSurface reporting one active condition / system DOWN, and an active `ENGINE / friday-engine` uncaught failure.
- The engine composition root nevertheless reported Ready. This is a health/readiness contradiction; readiness must not mask an active runtime incident.
- The engine failure report contained only the generic containment message. The exported report did not include the underlying exception type/cause, so this archive alone cannot prove the exact root cause.
- Shizuku was running and permission was granted, but the privileged UserService was not bound. Exact status: `bind failed: process name suffix must not be null`.
- Keep Friday alive was disabled and the foreground service was not running (`Owner enabled=false; Android service running=false`). The report therefore does not prove that the runtime switch had successfully been enabled.
- No local model was confirmed loaded. This is an optional compute capability, not evidence that the app identity or core runtime was absent.
- Friday's individual identity existed but had zero persisted experiences and zero associations at capture.
- The live system graph had 59 nodes and 83 edges; 32 reachable and 27 unreachable, with no missing invariants. Planned nodes must be separated from unreachable production nodes before treating this as a defect count.
- No previous uncaught crash record or Android process-exit history was available.

The report explicitly states that chat messages, speech transcripts, notification contents, screen contents and credentials/tokens were excluded.

## 2. Confirmed defects and repository status

### Shizuku UserService construction

The installed build's failure string exactly identifies the missing Shizuku process-name suffix. The fix was subsequently merged to main in PR #129: `processNameSuffix("device-capability")` is now applied when constructing `Shizuku.UserServiceArgs`.

### AMOLED/button-label contrast

The screenshots show blue primary buttons with muted gray labels, making actions difficult to read. The installed build predates the theme fix. PR #129 also merged the AMOLED surface and Material button typography correction; current theme uses `Color.Unspecified` for label styles so Material's semantic foreground can be inherited rather than forcing muted gray.

### Shizuku health-check regression

The current self-diagnosis code compared `ScreenBridge.status.value == "bound"`. The verified binding implementation now publishes `bound · verified Shizuku uid=2000` or UID 0. The old equality check would therefore report a healthy verified binding as disconnected. This branch adds `ShizukuBindingHealthGate`, checks both the verified status prefix and the actual shell/root UID, and includes unit tests for valid and invalid states.

### Silent diagnostic-button feedback

The System screen's two diagnosis buttons had callbacks, but the UI gave inadequate progress/outcome feedback and an exception in the active diagnostic coroutine could leave the owner without a visible result. This branch adds explicit running/completed/failed status, catches non-cancellation probe exceptions into a visible ERROR finding, and adds instrumented tests for both action semantics and active-diagnosis output.

The settings screen already had click handlers for its main controls, but the previous instrumented test covered only a small subset. This branch expands the Settings interaction checks beyond the export path. Instrumented tests are still not physical-phone acceptance evidence.

## 3. Important limits of this evidence

- The archive does not include a Settings-screen screenshot showing a failed tap, a touch-event trace, a UI hierarchy dump or an Android bug report. Therefore it cannot identify a specific failed Settings button from direct physical interaction evidence.
- The active diagnostics and self-diagnosis data are present in the export; at least one active diagnostic run completed before the report was exported. It would be inaccurate to conclude that all diagnosis buttons were dead.
- The report is from the pre-PR-#129 APK. The Shizuku suffix and AMOLED fixes are already on main, but they were not present in the installed 1.0.510 build evidenced here.
- The active ENGINE failure remains a real unresolved runtime incident in this evidence. The generic record does not expose enough information to assert its root cause or claim it is repaired. The updated diagnostic export now includes the retained exception type when available so the next on-device report can distinguish the actual cause.

## 4. Acceptance required after the next APK

1. Install an APK built from a source revision containing the merged PR #129 fixes and this branch's diagnostic fixes.
2. Confirm the Settings screen actions expose click semantics and produce visible progress/result feedback.
3. Confirm Shizuku binds and reports verified UID 2000 or 0; confirm self-diagnosis reports PASS for the bridge.
4. Toggle Keep Friday alive on and verify both persisted owner setting and live foreground-service state; then verify stop/off behaviour.
5. Run both diagnosis actions and confirm the UI reports completion or a visible failure, never silent no-op.
6. Export a new report and inspect the ENGINE failure cause. Only mark it recovered after the underlying failure is resolved and the FailureSurface records verified recovery.
7. Re-check the actual phone after installation; emulator/instrumentation results are not a substitute for this host-specific acceptance.

**Truth rule:** this document records evidence and code changes. It does not claim the new APK has been installed on the phone or that physical-device acceptance has passed.


## 5. Runtime-toggle failure visibility added after evidence review

The report recorded `Owner enabled=false; Android service running=false`, but did not reveal whether the owner had left the setting off or whether a previous foreground-service start attempt had been rejected. The runtime start path previously allowed a synchronous Android/OEM `RuntimeException` from `startForegroundService` to escape the Settings click without a durable explanation. The updated controller catches that platform rejection, records the exception type/message in the existing runtime start-failure field, preserves the owner's enabled preference for a later retry, and returns a success/failure result. Settings now refreshes that failure field during its service-state poll so a rejected start is visible immediately. This does not bypass Android policy or claim the service is always startable; it makes the failure observable and recoverable.

The new Settings instrumentation coverage also checks actionable controls beyond report export. These tests prove UI semantics on the emulator, not the actual owner's touch panel or OEM-specific foreground-service acceptance.
