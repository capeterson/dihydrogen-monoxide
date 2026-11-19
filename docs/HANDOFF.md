# Handoff: all planned features implemented

Read `docs/PLAN.md`, `docs/TECHNICAL_PLAN.md` §15, `docs/RELEASE.md` and `docs/TESTING.md` first. The confirmed product decisions in PLAN.md are final.

## Current state

Everything in the product plan up to Phase 5 is implemented, plus the home-screen widget and correlations:
Today, profile and goal, reminders, History, Insights, export and restore, the Wear OS companion, weather
(`:core:weather`), Health Connect (`:sync:healthconnect`), Google Drive backup (`:sync:drive`) and the Glance widget
(`:widget`). `grep -R 'NoOp' --include=*.kt .` finds only test fakes. Not built: PDF report, Quick Settings tile and app
shortcuts (Phase 6) and the Play declarations (Phase 7).

Needs you, not code: a Google Cloud OAuth setup for Drive (`dhmo.googleWebClientId`, see `docs/RELEASE.md`), the release
keystore in 1Password (`scripts/release/setup-keystore.sh`), a hosted privacy policy, and real-device runs of
`docs/testing/health-connect-manual.md` and `docs/TESTING.md` §10.

## Things worth knowing

- **Drive without credentials.** With `dhmo.googleWebClientId` blank the Drive section reads "Not configured in this build" and
  the onboarding restore offer is not shown. Sign-in and consent cannot be exercised on an emulator without a Google
  account and a client registered for the signing key; the REST client, runner, controller and key wrapping are covered by
  JVM tests (`sync/drive/src/test`).
- **Health Connect tests** run the real engine against an in-memory Room database and `FakeHealthConnectClient`
  (`EngineTestEnvironment`). `FakeHealthConnectClient.updateRecords` throws a `NullPointerException`; to simulate a source
  app editing a record, insert it again with the same `clientRecordId` and a higher version.
- **mockk and Play Services types.** `GoogleAuthManager` cannot be mocked on this JDK (`class redefinition failed`), so the
  controller depends on the `DriveAuth`, `DriveScheduler` and `DriveRunState` seams and tests use plain fakes.

- **Restore.** `OfflineBackupRepository.restore` writes a gzip safety snapshot to `files/safety/` (newest three kept), then applies the document in one transaction. `undoLastRestore()` replaces with the newest snapshot without settings sanitising and deletes it, so repeated undos walk back through the three. Merge queues Health Connect `UPSERT`/`DELETE` ops only for rows that changed and only when writing is on.
- **Rendering busy states.** An indeterminate `CircularProgressIndicator` (any infinite animation) never lets Compose go idle, so `renderScreenVariants` on it hangs the test JVM instead of failing. Render only idle states.
- **Instrumented tests** use the real application. `FreshInstallRule` resets data through the debug-only `TestHooksEntryPoint` (`app/src/debug`). The emulator is en-US, so onboarding defaults to fluid ounces; `PhaseOneToThreeFlowsTest` picks millilitres. Lazy lists only compose visible rows: scroll before asserting on a drink.
- **Wear OS.** The watch app has the same `applicationId` (and `.debug` suffix) as the phone, which the Data Layer requires. Drinks carry a watch-made UUID and time; `IntakeRepository.addFromWatch` ignores a repeated id, so a message and its queued data item are stored once. `WearStateSync` starts in `DhmoApplication` and logs a debug line when the Wearable API is missing (emulators without Play services). The tile logs on `LoadAction` clicks and refreshes only the complications from that path, so it cannot re-log on its own reload. No real-watch run has been done yet; see TESTING.md §10.6.
- **Gradle.** A long-lived daemon can run out of Metaspace after many builds (`OutOfMemoryError: Metaspace` in KSP or Kotlin); `./gradlew --stop` and rerun.

## Verification and environment

The build and Robolectric tests run on Temurin 25, which Gradle downloads into `~/.gradle/jdks` (`gradle/gradle-daemon-jvm.properties`). The SDK is at `/home/cpeterson/Android/Sdk`, and the AVD `dhmo-api34` boots with SELinux enforcing (see the emulator policy note in memory if QEMU segfaults after an SDK update). Run instrumented tests with the emulator up and no full gate running at the same time; see TESTING.md §§5–6 and §8a.
