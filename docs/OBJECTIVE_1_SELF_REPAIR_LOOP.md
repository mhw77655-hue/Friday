

## Required privileged background-install capability (mandatory)

Background installation is a first-class Friday capability, not an optional manual workaround and not merely a link that opens another installer. The Update/Recovery Manager must implement a backend abstraction and capability-driven selection:

1. **Shizuku-compatible backend (primary non-root privileged path).** Integrate the official Shizuku API/provider and its supported UserService mechanism. Shizuku-Next advertises API/package compatibility with Shizuku, so Friday should interoperate through the documented API rather than hard-code fork-specific internals. At runtime detect the binder/server, API level, server UID/privilege, permission state, and service liveness. Request the per-app Shizuku authorization through its normal consent flow; never attempt to silently grant Friday its own privilege.
2. **Install through the privileged service.** Execute the install operation from a Shizuku UserService running with the server's actual shell (UID 2000) or root (UID 0) identity. Use a well-defined installer adapter for single APK and split APK sessions (for example, package-manager install/install-multiple operations where supported). Capture exit status and complete bounded stdout/stderr; do not treat command dispatch as install success. Verify PackageManager reports the expected package version and signing certificate after the operation.
3. **Root backend.** Detect and use an explicitly available, authorized root service as a separate backend. Do not infer root from Shizuku availability: Shizuku may be running as shell or root, and its UID must be checked.
4. **Native Android backend.** Retain PackageInstaller as the supported unprivileged fallback, including REQUEST_INSTALL_PACKAGES eligibility and STATUS_PENDING_USER_ACTION handling. This fallback is not the primary strategy when a valid privileged backend is available.
5. **Backend orchestration.** Prefer the strongest currently available authorized backend (root, then Shizuku shell, then native PackageInstaller), but make the policy configurable and auditable. Probe immediately before use because Shizuku can stop, lose authorization, or be restarted with different privileges. On binder death or service disconnection, persist state and retry only within bounded limits; then fall back safely.
6. **No setup fiction.** Shizuku-Next's wireless-debugging start method has lifecycle constraints, including restart after reboot on non-root devices. Friday must report whether the service is running and authorized; it must not claim that it can autonomously restart a stopped ADB-backed Shizuku server when Android requires the user to start/pair it. The user should not be asked to perform routine update taps, but a one-time privilege grant and platform-required Shizuku startup/authorization are distinct setup prerequisites.
7. **Background execution.** Run downloads, verification, installation, and post-install checks through a durable foreground/WorkManager-coordinated state machine with notifications and process-death recovery. Installation work must be resumable and idempotent; a process restart must not duplicate or corrupt an in-flight install.
8. **Trust gates remain mandatory.** Before invoking any backend, verify the cloud-signed release manifest, artifact digest, package name, permanent signing certificate, version policy, compatibility profile, and provenance. Privilege must never weaken artifact verification. Never accept a remote arbitrary shell command; the cloud may request only a typed install of a fully verified release artifact.
9. **Observable outcomes.** Record selected backend, server UID/privilege class, authorization state (not secrets), operation result, package-manager result, before/after version and signer, health-check result, and any fallback reason. Include these facts in the sanitized incident/update report.

### Revised installation acceptance gates

- [ ] Friday includes a Shizuku API integration and a dedicated privileged UserService installer backend.
- [ ] Friday detects Shizuku and Shizuku-Next through the compatible API, checks binder liveness, permission, API level, and actual server UID, and distinguishes shell from root.
- [ ] A controlled test installs/updates a test package silently in the background through Shizuku shell on a supported host, with no installer confirmation UI.
- [ ] A separate test validates the root backend when root is actually available.
- [ ] A separate test validates native PackageInstaller fallback and correctly surfaces STATUS_PENDING_USER_ACTION.
- [ ] Tests cover Shizuku absent, stopped, permission denied/revoked, binder death, service restart, shell-vs-root privilege, unsupported command/API, install failure, split APK, process death, and successful post-install signer/version verification.
- [ ] A Friday self-update candidate is installed through the Shizuku backend on the actual target phone, then health-checked and reported before the incident is closed.
- [ ] If Shizuku is not running or authorized, Friday queues the verified update and reports the precise missing prerequisite; it does not silently downgrade to an unsafe path or claim background installation succeeded.

The Shizuku backend must use the supported API/UserService architecture. The Shizuku API documentation describes UserService processes as running with the server's root or shell identity; shell UID is 2000 and root UID is 0. Friday must check which identity it actually received. Shizuku-Next's compatibility claim is a basis for API-level interoperability, not proof that every Shizuku-Next release/device combination has been tested. Physical-host tests remain required.

## Clarification to the original installation clause

The earlier statement that Friday should "use Android PackageInstaller" describes the native fallback, not the complete installer strategy. The mandatory strategy is privileged background installation through Shizuku-compatible UserService when available, root when available, and native PackageInstaller otherwise. The native fallback's confirmation behavior must not be used as an excuse to omit the Shizuku integration.
