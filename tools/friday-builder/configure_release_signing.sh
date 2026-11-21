#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

REPOSITORY="${FRIDAY_REPOSITORY:-mhw77655-hue/Friday}"
OUT="${1:-$HOME/.local/share/friday/friday-release.p12}"
ALIAS="friday-release"

for tool in keytool gh base64; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "Missing required tool: $tool (JDK 17+ and GitHub CLI are required)." >&2
    exit 2
  }
done

gh auth status >/dev/null
permission="$(gh repo view "$REPOSITORY" --json viewerPermission --jq '.viewerPermission')"
if [[ "$permission" != "ADMIN" ]]; then
  echo "GitHub CLI needs ADMIN access to $REPOSITORY to configure repository secrets." >&2
  exit 3
fi

mkdir -p "$(dirname "$OUT")"
chmod 700 "$(dirname "$OUT")"

if [[ -e "$OUT" ]]; then
  read -rp "Keystore already exists at $OUT. Reuse this exact identity? [y/N] " reuse
  [[ "$reuse" == "y" || "$reuse" == "Y" ]] || exit 4
else
  read -rsp "Choose a strong keystore password (at least 20 characters): " FRIDAY_STORE_PASSWORD
  echo
  read -rsp "Confirm keystore password: " confirmation
  echo
  if [[ "$FRIDAY_STORE_PASSWORD" != "$confirmation" || "${#FRIDAY_STORE_PASSWORD}" -lt 20 ]]; then
    echo "Passwords must match and contain at least 20 characters." >&2
    exit 5
  fi
  export FRIDAY_STORE_PASSWORD
  keytool -genkeypair -v \
    -keystore "$OUT" \
    -storetype PKCS12 \
    -storepass:env FRIDAY_STORE_PASSWORD \
    -keypass:env FRIDAY_STORE_PASSWORD \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=Friday Release, OU=Friday, O=Friday, L=Unknown, ST=Unknown, C=EG"
  chmod 600 "$OUT"
  unset confirmation
fi

read -rsp "Enter the keystore password to verify and configure GitHub: " FRIDAY_STORE_PASSWORD
echo
export FRIDAY_STORE_PASSWORD
keytool -list -keystore "$OUT" -storetype PKCS12 \
  -storepass:env FRIDAY_STORE_PASSWORD -alias "$ALIAS" >/dev/null
FINGERPRINT="$(keytool -list -v -keystore "$OUT" -storetype PKCS12 \
  -storepass:env FRIDAY_STORE_PASSWORD -alias "$ALIAS" |
  awk '/SHA256:/ {print $2; exit}' | tr -d ':' | tr '[:lower:]' '[:upper:]')"
if [[ ! "$FINGERPRINT" =~ ^[A-F0-9]{64}$ ]]; then
  echo "Could not derive a valid SHA-256 certificate fingerprint." >&2
  exit 6
fi

KEYSTORE_B64="$(base64 "$OUT" | tr -d '\n')"
printf '%s' "$KEYSTORE_B64" | gh secret set FRIDAY_RELEASE_KEYSTORE_BASE64 --repo "$REPOSITORY"
printf '%s' "$FRIDAY_STORE_PASSWORD" | gh secret set FRIDAY_RELEASE_STORE_PASSWORD --repo "$REPOSITORY"
printf '%s' "$ALIAS" | gh secret set FRIDAY_RELEASE_KEY_ALIAS --repo "$REPOSITORY"
printf '%s' "$FRIDAY_STORE_PASSWORD" | gh secret set FRIDAY_RELEASE_KEY_PASSWORD --repo "$REPOSITORY"
printf '%s' "$FINGERPRINT" | gh secret set FRIDAY_RELEASE_CERT_SHA256 --repo "$REPOSITORY"

unset KEYSTORE_B64 FRIDAY_STORE_PASSWORD
echo
echo "All five permanent signing secrets were submitted to $REPOSITORY."
echo "Certificate SHA-256: $FINGERPRINT"
echo "Keystore retained locally at: $OUT"
echo "CRITICAL: make two encrypted offline backups of that exact keystore and password."
echo "Do not delete it, regenerate it, commit it, or send it through chat."
echo "Trigger the Friday signed-release workflow only after verifying both backups."
