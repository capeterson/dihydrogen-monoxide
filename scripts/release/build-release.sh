#!/usr/bin/env bash
# Build signed release artifacts. The keystore is pulled from 1Password into a private tmpfs directory for the
# duration of the build and removed afterwards.
#
#   ./scripts/release/build-release.sh            # signed APKs for the phone (and watch) in build outputs
#   ./scripts/release/build-release.sh bundle     # also the Play bundle (.aab)
set -euo pipefail
cd "$(dirname "$0")/../.."

VAULT="${OP_VAULT:-Private}"
ITEM="${OP_ITEM:-DHMO Android upload key}"

command -v op >/dev/null || { echo "Install the 1Password CLI first" >&2; exit 1; }
op whoami >/dev/null 2>&1 || { echo "op is not signed in" >&2; exit 1; }

base="${XDG_RUNTIME_DIR:-/dev/shm}"
work="$(mktemp -d -p "$base" dhmo-release.XXXXXX)"
trap 'rm -rf "$work"' EXIT
chmod 700 "$work"

op document get "$ITEM (keystore file)" --vault "$VAULT" --out-file "$work/upload.jks" >/dev/null
chmod 600 "$work/upload.jks"

export DHMO_KEYSTORE_FILE="$work/upload.jks"
DHMO_KEYSTORE_PASSWORD="$(op read "op://$VAULT/$ITEM/keystore password")"
DHMO_KEY_PASSWORD="$(op read "op://$VAULT/$ITEM/key password")"
DHMO_KEY_ALIAS="$(op read "op://$VAULT/$ITEM/key alias")"
export DHMO_KEYSTORE_PASSWORD DHMO_KEY_PASSWORD DHMO_KEY_ALIAS

tasks=(:app:assembleRelease :wear:assembleRelease)
[[ "${1:-}" == "bundle" ]] && tasks+=(:app:bundleRelease)

# --no-configuration-cache: the signing inputs are environment variables that must not be cached.
./gradlew --no-configuration-cache "${tasks[@]}"

"$ANDROID_HOME"/build-tools/37.0.0/apksigner verify --print-certs app/build/outputs/apk/release/*.apk 2>/dev/null \
  || echo "(set ANDROID_HOME to verify the signature automatically)"
