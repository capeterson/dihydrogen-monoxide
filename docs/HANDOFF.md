# Handoff: continuing implementation on a KVM host

This is the starting prompt for the session that continues this work on a machine with KVM (the `iron` bridge environment). It is self-contained.

---

You are continuing implementation of **Dihydrogen Monoxide**, a free Android water-intake tracker, in the repository `capeterson/dihydrogen-monoxide`. Work on branch `ccr-a06b5cae-xrdq04` (PR #3, "Phase 0 foundation", targets `main`) until that PR merges. Then start new branches from `main` for each follow-up PR.

## Read first
1. `docs/PLAN.md`: the approved product and architecture plan. The "Product decisions (confirmed)" table is final; don't revisit it. Wear OS is out of scope (issue #1).
2. `docs/TECHNICAL_PLAN.md`: the implementation-ready plan, covering pinned versions, the module graph, schemas, algorithms with worked examples that are encoded as tests, and the **work breakdown in §15**. Each task lists the files it owns and its acceptance criteria.
3. `docs/TESTING.md`: how to run every test layer, the SDK packages, CI, and the manual device matrix.

## State at handoff
- **Done (T0, Phase 0):** the Gradle build (AGP 9.5.1, Gradle 9.9.1, Kotlin 2.4.21, KSP 2.3.14, compileSdk/targetSdk 37, minSdk 28) with `build-logic` convention plugins; all 23 modules compile; Room schema v1 with exported `core/database/schemas/.../1.json` and DAO tests; DataStore; every `:core:data` interface (`Offline*Repository` bodies are `TODO("T4")`); goal, pace and stats signatures with `TODO("T1"/"T2"/"T3")` bodies; the app shell with navigation and placeholder screens; Hilt; detekt, ktlint and lint with no baselines and warnings as errors; Roborazzi with committed references; the Gradle Managed Device `pixel6Api34`; CI workflows `ci.yml`, `instrumented.yml` and `record-screenshots.yml`.
- **Application id:** `casa.peterson.dhmo`, confirmed by the owner.
- **Verified locally (no KVM):** `ktlintCheck detekt checkModuleGraph`, `testDebugUnitTest` plus the JVM module tests (80 tests, 0 failures), `verifyRoborazziDebug`, `lint`, `:app:assembleDebug :app:assembleRelease`.
- **Never run:** any instrumented test. `AppLaunchTest` in `:app` and `MigrationTest` in `:core:database` compile only. The managed-device task exists but has not been executed.
- **Known gaps:** onboarding has no navigation buttons yet (T8), so a fresh install stays on the welcome placeholder. `./gradlew :app:tasks --all` fails with a Compose Preview "unit tests disabled for this variant" error. It doesn't affect any gate command; look at it when convenient.

## Step 1: verify the KVM host and run the instrumented layer
```bash
ls -l /dev/kvm && nproc && free -g          # /dev/kvm must exist and be accessible to your user
java -version                               # build runs on 17+; a JDK 21 must also be installed for Robolectric tests
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
SDKM=$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager
yes | $SDKM --licenses
$SDKM "platform-tools" "platforms;android-37.0" "build-tools;37.0.0" \
      "emulator" "system-images;android-34;google_apis;x86_64" "system-images;android-34;aosp_atd;x86_64"
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew pixel6Api34DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
# and/or on a booted AVD:
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n api34 -k "system-images;android-34;google_apis;x86_64" -d pixel_6
$ANDROID_HOME/emulator/emulator -avd api34 -no-window -no-audio -no-snapshot -gpu swiftshader_indirect &
adb wait-for-device && ./gradlew connectedDebugAndroidTest
```
If `/dev/kvm` is missing, stop and tell the user. Don't fall back to software emulation. Report the actual output of the instrumented runs, fix what fails (`AppLaunchTest`, `MigrationTest`), and push the fixes to PR #3 if it is still open, or to a new PR otherwise.

## Step 2: continue the work breakdown (TECHNICAL_PLAN.md §15)
Scope for the next PR, "Phases 1–3": **wave 1** (T1 goal calculator, T2 pace and reminder decision, T3 stats, T4 repositories and fakes, T5 backup format and CSV, T6 design system components, all parallel), then **wave 2** (T7 first; T10, T14 and T16 may start alongside it; then T8, T9, T11, T12, T13, T15), then **T17** (integration, end-to-end tests, accessibility pass). Phase 4 (Drive, T-DR1..3) and Phase 5 (Health Connect and weather, T-HC1..3, T-WX1..2, T-COR) are **separate later PRs**.

How to run it:
- Use parallel implementation subagents with **git worktree isolation**, one per task. Give each its task section from §15, the §1–§3 context, and the sections its task references. Each agent must stay inside the files it owns (§15.1), keep warnings-as-errors, and never skip or disable tests to get green.
- Merge results yourself and resolve conflicts. The only expected shared edit is single appended lines in `gradle/libs.versions.toml`.
- Before opening a PR, the full gate must pass locally: `./gradlew ktlintCheck detekt checkModuleGraph lint testDebugUnitTest :core:common:test :core:model:test :core:domain:test :core:backup:test verifyRoborazziDebug assembleDebug`, plus the instrumented tests now that KVM is available.
- PR descriptions include actual test output (including failures and skips) and a checklist of what remains from the roadmap (PLAN.md §11). After opening a PR, watch it and fix CI failures.

## Efficiency rules learned the hard way
- **Use one Gradle daemon.** Invoke `./gradlew` the same way every time. Don't change JVM args or `-D` flags between runs, and don't use `--no-daemon`. Several daemons on one machine waste memory and slow everything down. With parallel worktree agents, expect contention; limit concurrency to what the machine's CPU and RAM handle (each Gradle build wants about 3–4 GB).
- **Iterate narrowly:** `./gradlew :core:domain:test --tests '*goal*'`, `:module:compileDebugKotlin`. Fix every error from a run before re-running. Run the full gate **once** at the end.
- **Shared usage limit:** all subagents draw on the same 5-hour usage limit, and a large fan-out can hit it. A stopped agent can be resumed with a message after the reset instead of being restarted.
- Maven Central sometimes returned 429 or false 404s in the cloud sandbox. If resolution fails for an artifact that exists, retry with `--refresh-dependencies` (Gradle caches a false "missing" for 24 hours).

## Rules
- Commit messages end with the attribution lines your session gives you. No model identifiers in commits, PRs or code.
- Don't change `docs/PLAN.md`'s confirmed decisions. If `TECHNICAL_PLAN.md` is wrong, fix it in the same PR and say so.
- Open PRs only as part of this plan. Never merge them yourself.
