# Handoff: Phases 1–3 continuation

Continue on `phase-1-3-wave1`, draft PR #4 against `main`. Do not merge it. Phase 4 (Drive) and Phase 5 (Health Connect and weather) each get a later branch and PR from `main`; Wear OS is out of scope. Read `docs/PLAN.md`, `docs/TECHNICAL_PLAN.md` §15, and `docs/TESTING.md` first. The confirmed product decisions in PLAN.md are final.

## Current implementation

Phase 0 and wave 1 (T0–T6) were already complete. This continuation adds Java 25, T7 (goal use cases and Today stream), T8 (onboarding), T10 (History), and T14 (reminder settings) to the PR branch. T9 is **not** integrated. T11, T12, T13, T15, T16 and T17 remain. T14 provides settings UI but still relies on the no-op scheduler until T13. Phase 4 and Phase 5 are not started.

The Java 25 changes keep the Android bytecode target at 17, update CI to JDK 25, and migrate Detekt from 1.23.8 to 2.0.0-alpha.6 because the old parser fails on Java 25. Roborazzi NATIVE can still return fully transparent frames. Screenshot cases were isolated into separate test classes and captures retried, but two full runs still failed with blank frames on this host; later full runs passed. A real image difference still fails. Investigate further if CI flakes; do not re-record a blank reference.

History's paging query now sorts by hydration day and then instant, so travel and day-start transitions cannot split one day into multiple groups. The fake repository follows that ordering. Health Connect activity rows are ignored by the goal input builder when the exercise permission is unavailable.

## Today work in progress

The unfinished branch `task-t9-today`, commit `9069de2`, and its worktree `/tmp/dhmo-t9` preserve T9 work. The branch is pushed to `origin` so it can be fetched on another machine. It is based on `ae3fc55` (before History, reminder settings and onboarding were integrated). It is not part of PR #4. Rebase or cherry-pick it onto the current PR branch only after reviewing it; it is explicitly WIP, not a completed task. Fetch it with `git fetch origin task-t9-today` on another machine.

T9 has initial Today, edit-entry, container, battery-help and QuickLog code and preliminary tests. The latest focused run compiled but failed 2 of 5 tests: `TodayViewModelTest.quickAddDeleteAndUndo` timed out waiting for an emission, and `TodayScreenTest.quickAddAndSwipeInvokeTheirActions` could not find the expected `250 ml` node. No Today screenshot references have been recorded. Review functionality and accessibility, fix those failures, implement and test any missing acceptance criteria in §15.5 (including `QuickLogActivityTest`), record the five states in three variants, and run the module checks and app assembly before integration. The test logs from this host were `/tmp/dhmo-t9-tests.log`; do not assume `/tmp` survives.

The stopped T8 agent's work was completed and integrated. Its temporary worktree `/tmp/dhmo-t8` is not needed for continuation. The stopped T9 agent's earlier work was incorporated into the WIP commit above.

## Verification and environment

JDK 25 with `javac` is required for the build and Robolectric; the SDK is at `/home/cpeterson/Android/Sdk`. The full gate below passed after T8 was integrated (`BUILD SUCCESSFUL in 2m 13s`):

```bash
./gradlew ktlintCheck detekt checkModuleGraph lint testDebugUnitTest :core:common:test :core:model:test :core:domain:test :core:backup:test verifyRoborazziDebug assembleDebug
```

Earlier in this continuation, `connectedDebugAndroidTest`, `:app:pixel6Api34DebugAndroidTest`, and `:core:database:pixel6Api34DebugAndroidTest` passed on API 34. Those device results preceded the T8 and T10/T14 integrations and must be repeated on the final branch before the PR is updated, as must the full gate. T8's own module tests, all 30 screenshot references, lint and app assembly passed on Java 25. T10 and T14 passed their module tests and screenshots on Java 21 before integration; the integrated full Java 25 gate passed afterward.

This host has KVM and an AVD named `dhmo-api34`. Previously QEMU segfaulted when SELinux was enforcing. The user installed a new policy; the API 34 emulator booted and the scoped app and database connected tests passed with SELinux enforcing. An aggregate connected run lost the emulator during a host-wide out-of-memory event; the kernel log showed OOM kills, with no new QEMU core or SELinux denial in that window. Do not change host-wide SELinux settings without the user's help. Run the app and database managed-device tasks sequentially; see TESTING.md §§5–6.

The original checkout has pre-existing user changes in `gradle.properties` and untracked `gradle/gradle-daemon-jvm.properties`. Preserve them; they were not included in our commits. Keep one Gradle daemon where possible, iterate with narrow tasks, and do not run a full gate while a resource-heavy emulator is up on a small host.

## Remaining decisions and checks

- T15 must resolve the TREND text/parameter mismatch: `changeMl` is total change across the range, while the plan example says ml/day. The WEEKEND example's 22% rounds to `21` for dataset A.
- Intake use cases throw `IllegalArgumentException`, `ImportNotEditableException`, and `EntryNotFoundException`; `IntakeRepository.get(id)` includes tombstones.
- T16 maps `DayStat` to `CsvDay`, passes the user's zone to CSV export, and uses `BackupCrypto.isAcceptablePassphrase()` for NFKC-normalized passphrase validation.
- Re-run the full gate and connected plus managed device tests before opening or updating PR #4, keep its description current, and only mark it ready when T8–T17 are integrated and CI is green. Never merge it yourself.
