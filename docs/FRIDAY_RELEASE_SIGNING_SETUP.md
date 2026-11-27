# Friday production signing and in-place updates

The production APK must always use one permanent signing certificate. CI debug APKs are test artifacts only; they are not production update packages.

## One-time key creation and secret configuration

The quickest safe setup is the repository helper:

```sh
bash tools/friday-builder/configure_release_signing.sh
```

Run it on a trusted computer with JDK 17+ and GitHub CLI authenticated as a repository administrator. It creates (or explicitly reuses) one PKCS12 key, verifies its fingerprint, and uploads the five required Actions secrets directly through GitHub CLI stdin. It does not print the private key or password into the terminal output. The keystore stays on that computer.

The helper refuses to overwrite an existing key. Keep two encrypted offline backups of the generated `friday-release.p12` and its password before treating setup as complete.

## Release workflow

The coding worker never receives the signing secrets. A separate release workflow runs after a successful main `build` workflow. It builds a release APK, verifies the signing certificate against the pinned fingerprint, calculates the APK SHA-256, and creates release metadata.

The workflow first preserves the verified signed APK, metadata and checksum as a 30-day Actions artifact, then attempts to publish them as GitHub Release assets. This preserves a recoverable APK if GitHub denies release publication. A green workflow conclusion alone is not proof of publication: inspect the job steps and confirm the release tag/assets exist.

If release publication returns `403 Resource not accessible by integration`, check repository Settings → Actions → General → Workflow permissions and allow read/write access to repository contents for `GITHUB_TOKEN`. If an organization policy or ruleset still prevents that, configure an owner-controlled fine-grained token with Contents: read/write as the optional `FRIDAY_RELEASE_PUBLISH_TOKEN` repository secret. The workflow uses it only for release publication. This token is separate from the permanent signing key.

No debug APK is ever promoted as a production release.

## Existing installed identity and in-place repair

The active repair task targets the owner's **already-installed Friday v1.0.510**, package `ai.friday.assistant`, versionCode `1510`, with the pinned permanent signing certificate recorded in [the installed-APK forensic dossier](FRIDAY_1.0.510_INSTALLED_APK_REPAIR_DOSSIER.md). Earlier notes in this document claiming that the previous APK had been deleted and that a clean install was authorized are stale and do not apply to this repair task.

For this task, never uninstall Friday, clear its app data, or replace it with a debug APK. The update must preserve the application ID, use the same signing identity, have a strictly higher version code, and be verified as an in-place update before the owner installs it. Android's package installer enforces signer compatibility; Shizuku does not bypass that check.

## Install-once update channel

The production signing workflow is only one half of the deployment system. The first phone install must be the permanent-key release, never the debug artifact or disposable CI smoke build. Before that install, prove a same-key in-place update and continuity preservation on the emulator.

The update-channel design and acceptance gates are documented in [FRIDAY_INSTALL_ONCE_UPDATE_CHANNEL.md](FRIDAY_INSTALL_ONCE_UPDATE_CHANNEL.md). Friday's updater should prefer eligible Android PackageInstaller self-update, then a previously owner-authorized Shizuku shell route where available, and finally the normal Android confirmation flow. Shizuku is optional, not a guaranteed silent-install mechanism. Android may still return STATUS_PENDING_USER_ACTION; the client must handle it.

The updater now schedules background discovery and APK staging on unmetered connectivity, exposes review/install controls in Settings, re-verifies the staged APK immediately before creating a PackageInstaller session, and handles Android's pending-user-action callback. This path still needs an end-to-end same-key upgrade test, post-update continuity health checks and last-known-good rollback before it is accepted as production-complete.

The GitHub repository token is separate from the release signing key.
