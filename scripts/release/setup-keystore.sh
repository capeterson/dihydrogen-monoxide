#!/usr/bin/env bash
# One-time: create the upload keystore and store it in 1Password. Nothing is written to the repository and the
# keystore never stays on disk: it exists only inside 1Password (as a Document) after this script exits.
#
# Needs the 1Password CLI (`op`) signed in, e.g. via the desktop app integration
# (1Password → Settings → Developer → "Integrate with 1Password CLI").
#
#   OP_VAULT=Private ./scripts/release/setup-keystore.sh
set -euo pipefail

VAULT="${OP_VAULT:-Private}"
ITEM="${OP_ITEM:-DHMO Android upload key}"
ALIAS="${DHMO_KEY_ALIAS:-dhmo-upload}"

command -v op >/dev/null || { echo "Install the 1Password CLI first: https://developer.1password.com/docs/cli/get-started" >&2; exit 1; }
command -v keytool >/dev/null || { echo "keytool (a JDK) is required" >&2; exit 1; }
op whoami >/dev/null 2>&1 || { echo "op is not signed in (enable the desktop-app integration, then retry)" >&2; exit 1; }

if op item get "$ITEM" --vault "$VAULT" >/dev/null 2>&1; then
  echo "Item '$ITEM' already exists in vault '$VAULT'. Refusing to overwrite a signing key." >&2
  exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
chmod 700 "$work"

gen() { head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32; }
store_pass="$(gen)"
key_pass="$store_pass" # PKCS12 keystores use one password for both

keytool -genkeypair -v -storetype PKCS12 \
  -keystore "$work/upload.jks" -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$store_pass" -keypass "$key_pass" \
  -dname "CN=Dihydrogen Monoxide, O=Peterson, C=US" >/dev/null

# The item holds the passwords and the keystore file itself as its attached document.
op item create --vault "$VAULT" --category "Secure Note" --title "$ITEM" \
  "keystore password[password]=$store_pass" \
  "key password[password]=$key_pass" \
  "key alias[text]=$ALIAS" \
  "notes[text]=Android upload key for casa.peterson.dhmo (phone and Wear OS builds must share it). Losing this means no more updates for installed apps." \
  --format json >/dev/null
op document create "$work/upload.jks" --vault "$VAULT" --title "$ITEM (keystore file)" >/dev/null

echo "Stored in 1Password vault '$VAULT': '$ITEM' and '$ITEM (keystore file)'."
keytool -list -keystore "$work/upload.jks" -storepass "$store_pass" | grep -i 'SHA\|Certificate fingerprint' || true
echo "Register the SHA-1 above in the Google Cloud OAuth Android client for Drive (see docs/RELEASE.md)."
