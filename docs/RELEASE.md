# Releasing

The upload keystore lives only in 1Password. Nothing secret is in the repository, and `*.jks` is gitignored.

## One-time setup

1. Install the 1Password CLI (`op`) and enable **Settings → Developer → Integrate with 1Password CLI** in the desktop app.
2. `OP_VAULT=Private ./scripts/release/setup-keystore.sh` creates a 4096-bit RSA key, stores the passwords in an item
   (`DHMO Android upload key`) and the keystore as a Document in that vault, then prints the certificate fingerprints. It
   refuses to run if the item exists, so it can never overwrite a key.
3. Register the printed **SHA-1** in the Google Cloud console as the Android OAuth client for `casa.peterson.dhmo` (Drive
   backup sign-in only works for builds signed with a registered key). Debug builds need their own client entry (`.debug`
   package and debug keystore SHA-1).

## Each release

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

Back up the 1Password vault. Without this key installed copies cannot be updated, and Play App Signing (recommended when
this goes to the Play Store) is the safety net: Google holds the app signing key and this one is only the upload key.
