# Friday production signing and in-place updates

The production APK must always use one permanent signing certificate. CI debug APKs are test artifacts only; they are not production update packages.

## One-time key creation

On a trusted computer with JDK 17 or newer:

```sh
bash tools/friday-builder/generate_release_keystore.sh
```

The script creates `friday-release.p12`, asks for a strong password, and prints the base64 keystore, alias and SHA-256 certificate fingerprint.

Keep the keystore and password in two secure offline backups. Never commit the keystore or paste it into a public issue/chat. Losing this key means Android will reject future updates signed by a replacement key.

## One-time GitHub configuration

Open [Friday repository Actions secrets](https://github.com/mhw77655-hue/Friday/settings/secrets/actions) and add all five repository secrets:

| Secret | Value |
|---|---|
| `FRIDAY_RELEASE_KEYSTORE_BASE64` | Base64 value printed by the generator |
| `FRIDAY_RELEASE_STORE_PASSWORD` | Password entered during generation |
| `FRIDAY_RELEASE_KEY_ALIAS` | `friday-release` |
| `FRIDAY_RELEASE_KEY_PASSWORD` | Same password for this PKCS12 keystore |
| `FRIDAY_RELEASE_CERT_SHA256` | Certificate fingerprint printed by the generator |

The coding worker never receives these secrets. A separate release workflow runs only after the exact `main` commit passes the complete `build` workflow. It builds a release APK, verifies the signing certificate against the pinned fingerprint, and publishes the APK, SHA-256 checksum and metadata as GitHub Release assets.

No debug APK is ever promoted as a production release.

## First install identity

The owner confirmed the previous APK was deleted and authorized a fresh package identity. The first permanent-signed Friday APK is therefore a clean install using `ai.friday.assistant`; there is no old-package data continuity requirement. Do not install a debug APK as the seed.

After the permanent-signed seed is installed, every update must keep the same application ID and signing certificate. Android's package installer enforces signer compatibility; Shizuku does not bypass that check.

## Install-once update channel

The production signing workflow is only one half of the deployment system. The first phone install must be the permanent-key release, never the debug artifact or disposable CI smoke build. Before that install, prove a same-key in-place update and continuity preservation on the emulator.

The update-channel design and acceptance gates are documented in [FRIDAY_INSTALL_ONCE_UPDATE_CHANNEL.md](FRIDAY_INSTALL_ONCE_UPDATE_CHANNEL.md). Friday's updater should prefer eligible Android PackageInstaller self-update, then a previously owner-authorized Shizuku shell route where available, and finally the normal Android confirmation flow. Shizuku is optional, not a guaranteed silent-install mechanism. Android may still return STATUS_PENDING_USER_ACTION; the client must handle it.

The first implementation stage now being built is release discovery and integrity-checked APK staging. Installation orchestration, owner-visible update controls, post-update health verification and rollback remain separate required work; do not treat staging as a complete updater.

The GitHub repository token is separate from the release signing key.
