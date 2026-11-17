# Handoff: after Phases 1–3 and the Wear OS companion

Read `docs/PLAN.md`, `docs/TECHNICAL_PLAN.md` §15 and `docs/TESTING.md` first. The confirmed product decisions in PLAN.md are final.

## Current state

Phases 0–3 are implemented (T0–T17): onboarding, Today, profile and goal, settings, reminders, History, Insights, CSV/JSON export and restore from a file, with end-to-end tests. `grep -R 'TODO("T' --include=*.kt .` finds nothing. A first Wear OS companion (issue #1) adds a tile and a complication in `:wear`, the shared protocol in `:core:wear`, and the phone side in `:sync:wear`.

Next: Phase 4 (Drive backup, §15.7) and Phase 5 (Health Connect and weather, §15.8), each on its own branch and pull request from `main`. Drive can reuse `BackupRepository.exportDocument()`, `restore()` and the safety snapshots from T16; the Backup screen keeps the "Restore from a file" section and gains the Drive slot of `SettingsIntegrations`.

## Things worth knowing

- **Restore.** `OfflineBackupRepository.restore` writes a gzip safety snapshot to `files/safety/` (newest three kept), then applies the document in one transaction. `undoLastRestore()` replaces with the newest snapshot without settings sanitising and deletes it, so repeated undos walk back through the three. Merge queues Health Connect `UPSERT`/`DELETE` ops only for rows that changed and only when writing is on.
- **Screenshots of busy states.** An indeterminate `CircularProgressIndicator` never lets Compose go idle, so a Roborazzi capture of it hangs the test JVM for hours instead of failing. Capture only idle states.
- **Instrumented tests** use the real application. `FreshInstallRule` resets data through the debug-only `TestHooksEntryPoint` (`app/src/debug`). The emulator is en-US, so onboarding defaults to fluid ounces; `PhaseOneToThreeFlowsTest` picks millilitres. Lazy lists only compose visible rows: scroll before asserting on a drink.
- **Wear OS.** The watch app has the same `applicationId` (and `.debug` suffix) as the phone, which the Data Layer requires. Drinks carry a watch-made UUID and time; `IntakeRepository.addFromWatch` ignores a repeated id, so a message and its queued data item are stored once. `WearStateSync` starts in `DhmoApplication` and logs a debug line when the Wearable API is missing (emulators without Play services). The tile logs on `LoadAction` clicks and refreshes only the complications from that path, so it cannot re-log on its own reload. No real-watch run has been done yet; see TESTING.md §10.6.
- **Gradle.** A long-lived daemon can run out of Metaspace after many builds (`OutOfMemoryError: Metaspace` in KSP or Kotlin); `./gradlew --stop` and rerun.

## Verification and environment

The build and Robolectric tests run on Temurin 25, which Gradle downloads into `~/.gradle/jdks` (`gradle/gradle-daemon-jvm.properties`). The SDK is at `/home/cpeterson/Android/Sdk`, and the AVD `dhmo-api34` boots with SELinux enforcing (see the emulator policy note in memory if QEMU segfaults after an SDK update). Run instrumented tests with the emulator up and no full gate running at the same time; see TESTING.md §§5–6 and §8a.
