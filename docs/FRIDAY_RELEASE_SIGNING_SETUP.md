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

The coding worker never receives these secrets. A separate release workflow runs only after the exact `main` commit passes the complete `build` workflow. It builds a release APK, verifies the signing certificate against the pinned fingerprint, and publishes the APK, SHA-256 checksum and metadata as GitHub Release assets.

No debug APK is ever promoted as a production release.

## First install identity

The owner confirmed the previous APK was deleted and authorized a fresh package identity. The first permanent-signed Friday APK is therefore a clean install using `ai.friday.assistant`; there is no old-package data continuity requirement. Do not install a debug APK as the seed.

After the permanent-signed seed is installed, every update must keep the same application ID and signing certificate. Android's package installer enforces signer compatibility; Shizuku does not bypass that check.

## Install-once update channel

The production signing workflow is only one half of the deployment system. The first phone install must be the permanent-key release, never the debug artifact or disposable CI smoke build. Before that install, prove a same-key in-place update and continuity preservation on the emulator.

The update-channel design and acceptance gates are documented in [FRIDAY_INSTALL_ONCE_UPDATE_CHANNEL.md](FRIDAY_INSTALL_ONCE_UPDATE_CHANNEL.md). Friday's updater should prefer eligible Android PackageInstaller self-update, then a previously owner-authorized Shizuku shell route where available, and finally the normal Android confirmation flow. Shizuku is optional, not a guaranteed silent-install mechanism. Android may still return STATUS_PENDING_USER_ACTION; the client must handle it.

The updater now schedules background discovery and APK staging on unmetered connectivity, exposes review/install controls in Settings, re-verifies the staged APK immediately before creating a PackageInstaller session, and handles Android's pending-user-action callback. This path still needs an end-to-end same-key upgrade test, post-update continuity health checks and last-known-good rollback before it is accepted as production-complete.

The GitHub repository token is separate from the release signing key.
