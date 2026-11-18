# Friday production signing and in-place updates

The production APK must always use one permanent signing certificate. The CI debug APK is for testing only; it is not the production installation.

## Generate the permanent key once

On a trusted computer or in Termux with a JDK installed:

```sh
bash tools/friday-builder/generate_release_keystore.sh
```

The script creates `friday-release.p12`, asks for a strong password, and prints the base64 keystore, alias and SHA-256 certificate fingerprint.

Keep the keystore file and password in two secure offline backups. Never commit the keystore. Never replace it casually: Android will reject an APK signed with a different certificate as an update.

## Configure GitHub once

Open [Friday repository Actions secrets](https://github.com/mhw77655-hue/Friday/settings/secrets/actions) and add:

| Secret | Value |
|---|---|
| `FRIDAY_RELEASE_KEYSTORE_BASE64` | Base64 value printed by the generator |
| `FRIDAY_RELEASE_STORE_PASSWORD` | Password entered during generation |
| `FRIDAY_RELEASE_KEY_ALIAS` | `friday-release` |
| `FRIDAY_RELEASE_KEY_PASSWORD` | Same password for this PKCS12 keystore |
| `FRIDAY_RELEASE_CERT_SHA256` | Fingerprint printed by the generator |

The release workflow is separate from the untrusted coding worker. The worker never receives these secrets. A release is built only after the main build workflow succeeds, and the APK certificate is checked against the pinned fingerprint before publishing.

## How updates work

The release workflow publishes `friday.apk`, `friday-release-metadata.json`, and a SHA-256 checksum as GitHub Release assets. The metadata contains the package id, version code/name, source commit, signing certificate fingerprint and APK hash.

The Android updater must still be implemented: it will fetch the latest release, verify the checksum and that the downloaded APK certificate matches the installed Friday certificate, then use explicitly authorized Shizuku shell access to run `pm install -r`. That is an in-place package update; it does not uninstall Friday or intentionally erase app-private data. The updater must retain a last-known-good APK and validate startup before accepting the new version.

## Important Android signature constraint

If an older debug APK is already installed with a different debug certificate, Android will reject the first production-signed APK as an update. Shizuku does not bypass Android's package-signature check. In that case, preserve/export Friday's identity and memory before a one-time migration to the permanent release signature. After the first permanent-signed install, future same-key releases can update in place.

The GitHub repository token used by Friday to access the workshop is separate from this signing key.
