#!/usr/bin/env bash
set -euo pipefail

OUT="${1:-friday-release.p12}"
ALIAS="friday-release"

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool is required. Install a JDK (Termux: pkg install openjdk-17)." >&2
  exit 2
fi
if [[ -e "$OUT" ]]; then
  echo "Refusing to overwrite existing keystore: $OUT" >&2
  exit 3
fi

read -rsp "Choose a strong keystore password: " STORE_PASSWORD
echo
read -rsp "Confirm keystore password: " STORE_PASSWORD_CONFIRM
echo
if [[ "$STORE_PASSWORD" != "$STORE_PASSWORD_CONFIRM" || "${#STORE_PASSWORD}" -lt 16 ]]; then
  echo "Passwords must match and be at least 16 characters." >&2
  exit 4
fi

keytool -genkeypair -v \
  -keystore "$OUT" \
  -storetype PKCS12 \
  -storepass "$STORE_PASSWORD" \
  -keypass "$STORE_PASSWORD" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=Friday Release, OU=Friday, O=Friday, L=Unknown, ST=Unknown, C=EG"

chmod 600 "$OUT"
FINGERPRINT="$(keytool -list -v -keystore "$OUT" -storetype PKCS12 -storepass "$STORE_PASSWORD" -alias "$ALIAS" | awk '/SHA256:/ {print $2; exit}' | tr -d ':' | tr '[:lower:]' '[:upper:]')"
KEYSTORE_B64="$(base64 "$OUT" | tr -d '\n')"

cat <<EOF

Keystore created: $OUT
Alias: $ALIAS
Certificate SHA-256 (store as FRIDAY_RELEASE_CERT_SHA256):
$FINGERPRINT

Add these GitHub repository Actions secrets:
FRIDAY_RELEASE_KEYSTORE_BASE64 = $KEYSTORE_B64
FRIDAY_RELEASE_STORE_PASSWORD = (the password you entered)
FRIDAY_RELEASE_KEY_ALIAS = $ALIAS
FRIDAY_RELEASE_KEY_PASSWORD = (the same password you entered)
FRIDAY_RELEASE_CERT_SHA256 = $FINGERPRINT

IMPORTANT:
- Keep an offline backup of $OUT and its password.
- Never commit the keystore or paste it into a public issue/chat.
- Losing this key means Android will reject future updates signed by a replacement key.
EOF
