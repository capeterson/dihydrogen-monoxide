# Releasing

Debug and production builds use independent persistent keys. The debug key is a repository Actions secret. The production
key and passwords are secrets in the `production` GitHub environment, restricted to workflows on `main`, with a verified
backup in 1Password. Nothing secret is in the repository; `*.jks`, `*.keystore`, and `*.p12` are gitignored.

## One-time setup

1. Install the 1Password CLI (`op`) and enable **Settings → Developer → Integrate with 1Password CLI** in the desktop app.
2. `OP_VAULT=Private ./scripts/release/setup-keystore.sh` creates a 4096-bit RSA key, stores the passwords in an item
   (`DHMO Android production signing key`) and the keystore as a Document in that vault, then prints the certificate fingerprints. It
   refuses to run if the item exists, so it can never overwrite a key.
3. Register the printed **SHA-1** in the Google Cloud console as the Android OAuth client for `casa.peterson.dhmo` (Drive
   backup sign-in only works for builds signed with a registered key). Debug builds need their own client entry (`.debug`
   package and debug keystore SHA-1).

## Persistent debug signing for GitHub APKs

The debug publication workflow needs the repository Actions secret `DHMO_DEBUG_KEYSTORE_BASE64`. It restores the same
keystore to `~/.android/debug.keystore` on every runner, so the phone and watch APKs share a signing identity across builds.
Gradle's cache does not persist this file. Without an explicit key, fresh runners generate different debug keys and Android
rejects updates with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, shown as a generic installation failure on the phone.

Choose one debug keystore and keep it backed up. To use this machine's existing standard Android debug key:

```bash
keytool -list -v -keystore "$HOME/.android/debug.keystore" -storepass android -alias androiddebugkey
base64 -w 0 "$HOME/.android/debug.keystore" | gh secret set DHMO_DEBUG_KEYSTORE_BASE64 --repo capeterson/dihydrogen-monoxide
```

The keystore must contain the private key `androiddebugkey` with the standard debug store/key password `android`. This is
separate from the production key above. Never regenerate or overwrite it during a build. Keep secret values out of
logs and Git; only the public certificate fingerprint is printed. The workflow fails if the secret is absent or invalid and
checks every APK's certificate before publication, including the watch APK.

The previously published debug builds used ephemeral keys. Moving an installed copy to the persistent key requires one
uninstall/reinstall with a data backup and restore; the APK contains only the old certificate, so its private signing key
cannot be recovered from the download. Android permissions may need granting again. Once this workflow and secret are
configured, subsequent debug APKs can update in place. The same key must also sign any local APK used to update those
GitHub builds. Register its SHA-1 separately for the debug package's Google OAuth client, if used.

The shared Gradle setup also restores this key in normal CI and instrumented-test jobs. Fork pull requests cannot access
repository secrets, so their test-only APKs use disposable keys; only the dedicated debug workflow publishes APKs and it
requires the persistent key.

## Promote a debug build to a full release

Open **Actions → Promote debug build → Run workflow** on `main`. Supply:

- `debug_tag`: an existing published tag such as `debug-5-791409c`.
- `version`: the release name and APK version name, such as `1.0.0` (three numeric components).
- `version_code`: a positive Android integer, strictly greater than previously promoted production releases.

The manual action resolves the selected tag to its exact commit and confirms it belongs to `main`'s history. It rebuilds
both APKs using that commit's sources as the production `release` variant, overrides version metadata with an external
Gradle init script, and uploads unsigned APKs between jobs. It can promote older debug tags without requiring their build
scripts to support new version properties. A separate job signs them using the production environment; source Gradle code
never receives the production private key. No production build is published automatically on push.

Before publishing, the signing job checks the production package `casa.peterson.dhmo`, requested version/code, disabled
debugging, and pinned certificate. The full GitHub release has tag `v<version>`, title `<version>`, `prerelease=false`, and
is marked latest. Phone and watch APKs omit `debug` in their filenames. `promotion.json` records source provenance,
version metadata, the certificate fingerprint, and APK hashes. Existing tags/releases are never overwritten. Rerunning a
failed signing/publication job is safe if it has not already created the release; otherwise choose a new version.

Re-signing an existing debug APK would retain its `.debug` identity and debug flags. Rebuilding as a production variant
intentionally creates a separate installed app; debug and production installs do not update one another.

### Production key storage

The `production` GitHub environment contains `DHMO_PRODUCTION_KEYSTORE_BASE64`, `DHMO_PRODUCTION_KEYSTORE_PASSWORD`,
`DHMO_PRODUCTION_KEY_ALIAS`, and `DHMO_PRODUCTION_KEY_PASSWORD`. Its variable `DHMO_PRODUCTION_CERT_SHA256` pins the
public certificate. The debug key is not used by the promotion job.

In 1Password's **Private** vault, `DHMO Android production signing key` stores the passwords, alias, and fingerprint;
`DHMO Android production signing key (keystore file)` stores the keystore Document. Both must be kept permanently. Restore
the same key when repairing CI, rather than generating a replacement. Never rotate the key just to repair a failed build.

## Local production builds

```bash
./scripts/release/build-release.sh          # signed phone and watch APKs
./scripts/release/build-release.sh bundle   # plus the Play bundle
```

The script copies the keystore into a private directory under `$XDG_RUNTIME_DIR` (tmpfs), exports the signing values as
environment variables for Gradle only, and deletes the keystore on exit. `AndroidApplicationConventionPlugin` reads
`DHMO_KEYSTORE_FILE`, `DHMO_KEYSTORE_PASSWORD`, `DHMO_KEY_ALIAS` and `DHMO_KEY_PASSWORD`; without them the release build
is unsigned (CI lint and R8 checks). The phone and watch apps both use this convention, so they always share one key, as the
Wear Data Layer requires.

Before sending a build to a tester: bump `versionCode` in `app/build.gradle.kts` and `wear/build.gradle.kts`, install the
signed APK on a real phone, and run the manual matrix in `docs/TESTING.md`.

## Drive configuration

Set `dhmo.googleWebClientId=<web client id>` in `~/.gradle/gradle.properties`. When it is blank the Drive section shows
"Not configured in this build".

## Losing the key

Back up the 1Password vault. Without the production key, directly distributed installed copies cannot be updated. For
Google Play distribution, configure Play App Signing and its upload key separately; it does not recover the private key
used for directly distributed GitHub APKs.
