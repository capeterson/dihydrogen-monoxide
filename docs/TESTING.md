# Testing guide

How to run every test layer of Dihydrogen Monoxide locally and in CI, and what must still be checked by hand on real devices. The design of the test layers is in [TECHNICAL_PLAN.md §14](TECHNICAL_PLAN.md#14-testing-and-ci).

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | **25** (Temurin), provisioned by Gradle | `gradle/gradle-daemon-jvm.properties` pins the Gradle daemon to Temurin 25; unit and screenshot tests run on the same JDK. If no matching JDK is installed, Gradle downloads one into `~/.gradle/jdks` (foojay resolver), so any JDK 17+ on the `PATH` is enough to start `./gradlew`. Pinning the vendor makes local and CI builds identical. detekt is `2.0.0-alpha.6`, the first detekt that runs on JDK 25 (1.23.x embeds a Kotlin 2.0 compiler that rejects it); move to 2.0.0 stable when it ships. Bytecode still targets Java 17. CI installs Temurin 25. To move to a newer JDK, run `./gradlew updateDaemonJvm --jvm-version=<n> --jvm-vendor=adoptium` and change `JDK_VERSION` in `build-logic`. |
| Gradle | via `./gradlew` (9.9.1) | Never use a system Gradle. |
| Android SDK | see below | Point `ANDROID_HOME` at it, or write `sdk.dir=/path/to/sdk` in `local.properties` (gitignored). |

### 1.1 SDK packages

Install the command-line tools from <https://developer.android.com/studio#command-line-tools-only> into `$ANDROID_HOME/cmdline-tools/latest`, then:

```bash
export ANDROID_HOME=$HOME/Android/Sdk          # or wherever you installed it
SDKM=$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager

yes | $SDKM --licenses

# Required for building and for all JVM / Robolectric / screenshot tests
$SDKM "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"

# Only for instrumented tests on a local emulator (section 6)
$SDKM "emulator" "system-images;android-34;google_apis;x86_64"

# Only for Gradle Managed Devices (section 5). Gradle downloads this itself on first run,
# but pre-installing avoids a slow first run:
$SDKM "system-images;android-34;aosp_atd;x86_64"
```

On Apple Silicon or ARM Linux, replace `x86_64` with `arm64-v8a`.

Robolectric downloads its own Android framework jars (`android-all-instrumented`) from Maven Central on first use, so the JVM tests need network access once.

## 2. Pure JVM unit tests

The pure Kotlin modules (`:core:common`, `:core:model`, `:core:domain`, `:core:backup`) hold the goal calculator, pace and reminder decisions, statistics and the backup codec. No Android SDK is involved.

```bash
./gradlew :core:common:test :core:model:test :core:domain:test :core:backup:test

# One area or one class
./gradlew :core:domain:test --tests '*goal*'
./gradlew :core:domain:test --tests '*ReminderDecisionTest'
```

Reports: `<module>/build/reports/tests/test/index.html`.

The worked-example tables in TECHNICAL_PLAN.md (§7.5 goal, §8.5 reminders, §9.14 stats datasets, §11.8 retention) are encoded verbatim. If a table changes in the plan, change the test in the same commit.

## 3. Robolectric unit tests (Android modules)

DAOs and the `daily_total` view (in-memory Room), DataStore serializers, repositories, use cases, view models, receivers, `AlarmManager` / `NotificationManager` shadows, WorkManager workers (`TestListenableWorkerBuilder`, `WorkManagerTestInitHelper`), `MockWebServer` network tests and `FakeHealthConnectClient` sync tests all run here, on the JVM.

```bash
./gradlew testDebugUnitTest                          # every Android module
./gradlew :core:database:testDebugUnitTest           # one module
./gradlew :core:reminders:testDebugUnitTest --tests '*Scheduler*'
```

Each Android module pins `sdk=37` in `src/test/resources/robolectric.properties`, so no `@Config(sdk = …)` is needed. Reports: `<module>/build/reports/tests/testDebugUnitTest/index.html`.

## 4. Screenshot tests (Roborazzi, JVM)

Each screen's main states are captured in light, dark and large-font (font scale 2.0) variants on Robolectric, with no emulator. Reference PNGs are committed under `<module>/src/test/screenshots/`.

```bash
./gradlew verifyRoborazziDebug     # compare against references (what CI runs)
./gradlew compareRoborazziDebug    # write diff images to <module>/build/outputs/roborazzi
./gradlew recordRoborazziDebug     # overwrite references after an intended UI change
```

Plain `testDebugUnitTest` does not compare or record images: without one of the Roborazzi tasks, `captureRoboImage` is a no-op.

References are recorded on Linux, because font rendering differs between operating systems. On macOS or Windows, don't commit locally recorded images. Run the `record-screenshots` workflow (Actions → *Record screenshots* → *Run workflow* on your branch), which records on Linux and commits the PNGs to the branch.

## 5. Gradle Managed Devices

A Gradle Managed Device (GMD) named `pixel6Api34` (Pixel 6, API 34, `aosp-atd` image) is declared by the Android convention plugins. Gradle creates, boots and tears down the emulator itself. This needs hardware virtualisation: KVM on Linux, Hypervisor.framework on macOS, WHPX on Windows.

```bash
./gradlew pixel6Api34DebugAndroidTest \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

`swiftshader_indirect` gives software rendering, which works on headless machines and CI; leave it off locally if you have a GPU. Reports: `<module>/build/outputs/androidTest-results/managedDevice/` and `<module>/build/reports/androidTests/managedDevice/`.

Instrumented sources exist in `:app` and `:core:database` (Room `MigrationTestHelper` tests against the exported schemas in `core/database/schemas/`).

The `:app` tests drive the real application, not `HiltTestApplication`, so start-up (WorkManager's Hilt factory, notification channels) is the same as in production. `FreshInstallRule` clears the database and both settings stores through the debug-only `TestHooksEntryPoint` (`app/src/debug`) before the activity starts, so tests do not depend on order. `PhaseOneToThreeFlowsTest` covers the flows of TECHNICAL_PLAN §14.1 in one walk: onboarding along the "set my own goal" path, first log, edit, delete through the TalkBack action with Undo, a day override, switching Insights ranges, and the export document. The Storage Access Framework picker is system UI, so the export check reads the document from `BackupRepository` instead of saving a file.

## 6. Instrumented tests on an emulator or device

With an emulator or a USB device connected (`adb devices` lists it):

```bash
# Create and start an emulator once
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n api34 \
  -k "system-images;android-34;google_apis;x86_64" -d pixel_6
$ANDROID_HOME/emulator/emulator -avd api34 -no-snapshot -no-audio &
adb wait-for-device

./gradlew connectedDebugAndroidTest
```

Use a `google_apis` image (not `aosp_atd`) when you want to try Health Connect or Google sign-in by hand. Health Connect is built into API 34+ images. On API 28–33 it has to be installed from the Play Store, so it needs a `google_apis_playstore` image.

## 7. Static analysis

```bash
./gradlew ktlintCheck detekt checkModuleGraph
./gradlew ktlintFormat        # auto-fix formatting
./gradlew lint                # Android lint (HTML: <module>/build/reports/lint-results-debug.html)
```

`checkModuleGraph` fails the build if a module depends on a module it may not (TECHNICAL_PLAN.md §3.2). Kotlin warnings are errors.

## 8. The whole local gate

This is what must pass before a pull request is opened:

```bash
./gradlew ktlintCheck detekt checkModuleGraph lint \
  testDebugUnitTest :core:common:test :core:model:test :core:domain:test :core:backup:test \
  verifyRoborazziDebug assembleDebug
```

## 8a. Memory use

The defaults in `gradle.properties` are sized for an 8 GB machine that also runs an emulator. A clean build of the unit-test and Roborazzi layer peaks at about 3 GB of Java memory in total (measured: 4.2 GB with the earlier 4 GB heap and a separate Kotlin daemon); the whole gate including lint and a release build peaks at about 3.9 GB.

- One JVM does both the Gradle work and the Kotlin compiles (`kotlin.compiler.execution.strategy=in-process`), with `-XX:+UseSerialGC` and a shrinking heap. The daemon exits after 5 idle minutes; run `./gradlew --stop` to free it sooner.
- Unit-test JVMs get a 1 GB heap and at most 2 run at once. Machine-local overrides in `~/.gradle/gradle.properties`: `dhmo.test.maxHeap` (for example `2g`) and `dhmo.test.parallelism` (for example `4`). Test workers were not the peak; the Gradle JVM was.
- Don't run an emulator and a full gate at the same time on a small machine; boot the emulator, run the instrumented tests, then `adb emu kill`.
- On CI the two managed-device tasks run one after the other, because each boots its own 2.5 GB emulator.

## 9. How CI runs the tests

| Workflow | Trigger | What it runs |
|---|---|---|
| `.github/workflows/ci.yml` | every push and pull request | JDK 25 (Temurin) and `gradle/actions/setup-gradle` caching; ktlint, detekt, the module-graph check, Android lint, JVM and Robolectric unit tests, Roborazzi verification, `assembleDebug`. Test reports, Roborazzi diffs and lint HTML are uploaded as artifacts when a step fails. |
| `.github/workflows/instrumented.yml` | pull requests to `main`, and manual dispatch | KVM is enabled on the `ubuntu-latest` runner with a udev rule. The AVD (API 34, `google_apis`, `x86_64`) is cached. `reactivecircus/android-emulator-runner` boots it and runs `connectedDebugAndroidTest`. A second job runs the GMD task `pixel6Api34DebugAndroidTest`. Android test reports are uploaded on failure. |
| `.github/workflows/record-screenshots.yml` | manual dispatch | Re-records Roborazzi references on Linux and commits them to the branch. |

To download a failing run's reports: open the run in the Actions tab and look under *Artifacts*.

## 10. Manual test matrix (real devices)

These behaviours depend on the OS, OEM firmware, real time passing or Google services, so automated tests can't cover them reliably. Run the matrix before each release, and before a phase is called done where the roadmap's exit criteria require it (Phase 2: reminders; Phase 4: Drive restore; Phase 5: Health Connect). Record results (device, Android version, build, pass/fail, notes) in the release checklist.

Recommended devices: a Pixel on the latest Android; a Samsung (One UI); one of Xiaomi, OnePlus or Huawei for aggressive battery management; one Android 9–13 device for Health Connect as a Play app.

### 10.1 Reminders (Phase 2)

Setup: reminders on, wake 07:00, sleep 23:00, interval 60 min, a goal you're behind on. Use `adb shell dumpsys alarm | grep -A3 dhmo` to see the scheduled alarm.

| # | Scenario | Steps | Expected |
|---|---|---|---|
| R-M1 | Behind pace | Log nothing; wait until past the first candidate time | One notification with + container, Snooze and Custom actions |
| R-M2 | Ahead of pace | Log well ahead of the curve | No notification until behind again |
| R-M3 | Interval floor | Log a drink right after a reminder | Next reminder no sooner than 60 min after the drink |
| R-M4 | Notification action | Tap "+ Glass" without opening the app | Entry logged, notification dismissed, Today and next alarm updated |
| R-M5 | Snooze | Tap Snooze | No reminder for 30 min |
| R-M6 | Goal met | Reach the goal | No further nudges that day; summary only if enabled |
| R-M7 | Quiet hours | Be behind at 23:30 | No notification until the next day's wake time |
| R-M8 | Doze | `adb shell dumpsys deviceidle force-idle`, then wait past the alarm; also test overnight with the screen off and unplugged | Reminder arrives within the Doze maintenance window (inexact is acceptable) |
| R-M9 | App standby | `adb shell am set-standby-bucket <pkg> rare` | Reminder still arrives, possibly late |
| R-M10 | Reboot | Reboot while an alarm is pending | Alarm rescheduled after `BOOT_COMPLETED` (check `dumpsys alarm`) |
| R-M11 | Time zone change | Change the zone in system settings by ±3 h | Next alarm recomputed for local wake/sleep; past days' totals unchanged |
| R-M12 | DST | Set the date to just before a DST transition in a zone with DST; cross it | Window length 15/17 h on the transition day; no duplicate or missing reminder |
| R-M13 | Manual time change | Move the clock forward 2 h | `TIME_SET` triggers a reschedule |
| R-M14 | App update | Install a new build over the old one | `MY_PACKAGE_REPLACED` reschedules |
| R-M15 | Permission revoked | Revoke notifications (Android 13+) | No crash; app shows the permission prompt or banner |
| R-M16 | Day start 04:00 | Log at 01:00 | Entry counts toward the previous hydration day |
| R-M17 | DND | Enable Do Not Disturb | Channel behaviour respected; no bypass |

### 10.2 OEM battery management

| # | Device | Steps | Expected |
|---|---|---|---|
| B-M1 | Samsung | Leave the app unused for 3 days ("sleeping apps" may kick in) | Reminders keep coming, or missed-alarm detection shows the "allow background activity" guide |
| B-M2 | Xiaomi (MIUI/HyperOS) | Default battery saver, autostart off; reboot | Same as above; the guide links to the autostart and battery settings |
| B-M3 | Huawei / OnePlus | App launch management on automatic | Same as above |
| B-M4 | Any | Swipe the app away from recents | Reminders continue |

Missed-alarm detection (TECHNICAL_PLAN.md §8.9) should flag a fire time much later than scheduled. Check that the guided screen appears and its links open the right OEM settings pages (see dontkillmyapp.com for current paths).

### 10.3 Health Connect (Phase 5)

| # | Scenario | Expected |
|---|---|---|
| H-M1 | Android 14+: turn on "Write hydration", log 250 ml | Record visible in Health Connect → Data → Hydration with this app as the source; 1 s duration |
| H-M2 | Edit and delete that entry in the app | Record updated / removed in Health Connect |
| H-M3 | Fitbit app logs water; imports off (default) | Nothing imported |
| H-M4 | Turn on "Count water logged in other apps" | Fitbit entries appear as read-only with the source label; the app's own records are not duplicated |
| H-M5 | Exclude Fitbit in the source-app list | Its imports disappear; turning it back on restores them |
| H-M6 | Samsung Health logs water (Samsung device) | Same as H-M3 to H-M5 |
| H-M7 | Adopt an import, edit it | Edit sticks; later changes from the source app don't overwrite it |
| H-M8 | Weight sync on; log weight in Fitbit/Samsung Health | New profile snapshot; goal recomputed (unless weight is pinned) |
| H-M9 | Exercise session logged by a watch | Activity adjustment replaces the level default for that day |
| H-M10 | Android 9–13 without Health Connect installed | "Install Health Connect" opens the Play Store; feature hidden otherwise |
| H-M11 | Revoke permissions in Health Connect settings | Sync stops cleanly; switch shows off; no crash |
| H-M12 | Background read permission granted / not granted | Periodic sync runs only when granted |
| H-M13 | Don't open the app for over 30 days (or clear the token) | Expired token falls back to a time-range reconcile with no duplicates |

### 10.4 Google Drive backup and restore (Phase 4)

| # | Scenario | Expected |
|---|---|---|
| D-M1 | Sign in; "Back up now" | File appears in the app-data folder (Drive web → Settings → Manage apps shows hidden app data size) |
| D-M2 | Clean device (factory reset or a second device), same account | Onboarding offers "Restore from your latest backup"; after restore every entry, container, profile snapshot, goal snapshot, override and setting matches: 100% of the data (Phase 4 exit criterion) |
| D-M3 | Encrypted backup, correct passphrase | Restores |
| D-M4 | Encrypted backup, wrong passphrase | Clear error; no partial restore |
| D-M5 | Merge restore with overlapping edits | Union by UUID; later `updatedAt` wins; tombstones respected |
| D-M6 | Revoke the app's Drive access at myaccount.google.com | Next backup produces the "Reconnect Google Drive" notification |
| D-M7 | Unmetered-only constraint on mobile data | Scheduled backup waits for Wi-Fi; "Back up now" still works |
| D-M8 | Retention over time (or with the clock moved forward) | At most 7 daily, 4 weekly and 6 monthly files kept |
| D-M9 | Undo last restore | Safety snapshot brings back the pre-restore data |

### 10.5 Weather (Phase 5)

| # | Scenario | Expected |
|---|---|---|
| W-M1 | Device-location mode | Only the coarse location prompt; no background-location prompt |
| W-M2 | Permission denied | Mode stays off; manual climate is used |
| W-M3 | Manual city | City search works without any permission |
| W-M4 | Airplane mode at day start | Goal falls back to the manual climate with "Forecast unavailable" shown |

### 10.6 Accessibility and display

| # | Scenario | Expected |
|---|---|---|
| A-M1 | TalkBack through onboarding, Today, History, Insights | Every control is labelled; the progress ring announces its percentage |
| A-M2 | Font scale 200%, display size largest | No clipped or overlapping text |
| A-M3 | Accessibility Scanner on each screen | No errors |
| A-M4 | Tablet or foldable, rotate and unfold | Adaptive layout (navigation rail), no state loss |
