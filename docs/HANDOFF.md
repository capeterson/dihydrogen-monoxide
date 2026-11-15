# Handoff: continuing implementation on a KVM host

This is the starting prompt for the session that continues this work on a machine with KVM (first used on `iron`; also fine on any other KVM machine). It is self-contained.

---

You are continuing implementation of **Dihydrogen Monoxide**, a free Android water-intake tracker, in the repository `capeterson/dihydrogen-monoxide`. Phase 0 is merged (PR #3). Work on branch `phase-1-3-wave1` (draft PR #4, base `main`, "Phases 1–3") until all of Phases 1–3 are in; open each later phase (4, 5) as its own branch and PR from `main`.

## Read first
1. `docs/PLAN.md`: the approved product and architecture plan. The "Product decisions (confirmed)" table is final; don't revisit it. Wear OS is out of scope (issue #1).
2. `docs/TECHNICAL_PLAN.md`: the implementation-ready plan, covering pinned versions, the module graph, schemas, algorithms with worked examples that are encoded as tests, and the **work breakdown in §15**. Each task lists the files it owns and its acceptance criteria.
3. `docs/TESTING.md`: how to run every test layer, the SDK packages, CI, and the manual device matrix.

## State at handoff

**Phase 0 (T0) is merged** (PR #3): build, 23 modules, schema v1, CI. The instrumented layer was run on a KVM host and is green: `AppLaunchTest` and `MigrationTest` pass locally and in CI, and so does the managed device.

**Phases 1–3 are in progress on branch `phase-1-3-wave1`, draft PR #4** (base `main`). **Wave 1 (T1–T6) is merged into it and all four CI checks are green.** Local full gate on the merged branch: 369 tests, 0 failures, `BUILD SUCCESSFUL`.

| Task | State | Where |
|---|---|---|
| T1 goal calculator | done | `core/domain` goal (G01–G38, O1–O9) |
| T2 pace and reminder decision | done | `core/domain` pace (R01–R25 + seeded property test) |
| T3 stats and insights | done | `core/domain` stats (datasets A–G, T) |
| T4 repositories, intake use cases, fakes | done | `core/data`, `core/testing` (`OfflineBackupRepository` stays `TODO("T16")`) |
| T5 backup, crypto, merge, retention, CSV | done | `core/backup` |
| T6 design system components | done | `core/designsystem`, 42 Roborazzi references |
| **T7** goal use cases (incl. `ChangeDayStartUseCase`) | **next, not started** | |
| T8–T16 | not started | wave 2, after T7 (T10, T14, T16 may start with it) |
| T17 integration, e2e, accessibility pass | not started | wave 3 |

Phase 4 (Drive) and Phase 5 (Health Connect, weather) are separate later PRs. Wear OS is out of scope (issue #1).

### Decisions already made that later tasks must respect (all also in PR #4's description)
- **T3 TREND text vs param:** the §9.13 example says "ml/day", but the `changeMl` param is the total change over the range (`changeOverRangeMl`, rounded to 10); `direction` and `weeks = 8` are separate params. **T15 (Insights UI) must decide:** derive ml/day from the param, add a per-day param, or fix the plan text. The WEEKEND example's "22%" is really `"21"` with dataset A.
- **T4 error types:** the intake use cases throw `IllegalArgumentException` (invalid input), `ImportNotEditableException` (unadopted import), `EntryNotFoundException` (unknown or deleted id). T8, T9 and later must handle these. `IntakeRepository.get(id)` returns tombstoned rows (undo needs them).
- **T4 / T7 boundary:** re-snapshotting `daily_goal` rows after a day-start change belongs to `ChangeDayStartUseCase` (T7); T4 only re-buckets entries (`rebucketFrom`).
- **T4:** activity rates 6/12/18 ml/min are named constants in `OfflineActivityRepository` because `:core:domain` exposes none; consider moving them to the goal policy.
- **T5:** `CsvExport.dailyTotals` takes `List<CsvDay>` (`:core:backup` may only depend on `:core:model`), so T16 must map `DayStat` to `CsvDay`. CSV local times take a `zone` parameter: pass the user's zone. `source_app` is `appLabels[origin] ?: origin`. `BackupMerge.merge(local, incoming)` exists for `BackupEntities` and `BackupDocument`. Passphrase check is `BackupCrypto.isAcceptablePassphrase()` (NFKC, at least 8 characters), not in `deriveKey`.
- **T6 screenshot flakiness:** Roborazzi 1.79 on Robolectric NATIVE intermittently returned blank frames after many captures in one JVM. Worked around with `forkEvery = 1` in `:core:designsystem` tests and a `captureStable` retry wrapper (retries only on a blank image or an assertion error; a real diff still fails). The cause is unknown. If a verify run is flaky in CI, the proper fix is in `captureScreenVariants` in `:core:testing`, and other feature modules' screenshot tests (T8–T15) will hit the same thing.
- **Plan edit:** `:core:designsystem` may depend on `:core:domain` (for `VolumeFormatter`); the `checkModuleGraph` allowlist and TECHNICAL_PLAN.md §3.2 agree.
- **Memory:** build defaults in `gradle.properties` were tuned for an 8 GB machine (peak about 3 GB, one JVM for Gradle and Kotlin). Test JVMs default to a 1 GB heap and two at once (`dhmo.test.maxHeap`, `dhmo.test.parallelism` in `~/.gradle/gradle.properties`). See TESTING.md section 8a.

## Environment notes (learned on the first KVM host, "iron")
- A JDK 21 is required for Robolectric (the build itself runs on 17+). It was installed under `~/opt/jdk-21.0.13+9`, with the Android SDK in `~/Android/Sdk` (cmdline-tools, platform 37.0, build-tools 37.0.0, emulator, `system-images;android-34;google_apis;x86_64` and `aosp_atd`). `sdkmanager` no longer needs a separate `--licenses` step.
- **On a Fedora host the emulator segfaults on every start while SELinux is enforcing** (no output from the emulator; the core dump shows a jump to an unmapped address). Setting SELinux permissive fixed it. If you see qemu crash 20–90 s after "performing a full startup", check `getenforce` first.
- Start the emulator detached (`setsid nohup emulator ... & disown`) so a killed shell doesn't take it down, and don't run a full gate while it is up on an 8 GB machine.
- `pkill -f qemu-system...` matches your own shell's command line; use `pkill -x qemu-system-x86` or `pgrep -x`.
- **Watch CI yourself.** `gh` works (`gh pr checks <n> --repo capeterson/dihydrogen-monoxide`, `gh run view --job <id> --log`). Don't rely on subscription events alone; a Monitor loop over `gh pr checks` is reliable.
- CI runners are small: the instrumented workflow frees disk before booting an emulator, and the two managed-device modules run one after the other.

## Step 1: set up and verify
```bash
git fetch origin phase-1-3-wave1 && git checkout phase-1-3-wave1
# JDK 21 + SDK as above, then:
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew ktlintCheck detekt checkModuleGraph lint testDebugUnitTest :core:common:test :core:model:test \
          :core:domain:test :core:backup:test verifyRoborazziDebug assembleDebug
```
This should be green before you start. The instrumented tests are in TESTING.md sections 5 and 6.

## Step 2: continue the work breakdown (TECHNICAL_PLAN.md §15.5 onward), on branch `phase-1-3-wave1`
1. **T7 first, alone** (needs T1 and T4, both merged). T10, T14 and T16 do not depend on it and may run next to it.
2. **Then T8, T9, T11, T12, T13, T15** (and T10, T14, T16 if not started). They are UI-heavy, so run them **two at a time** with worktree isolation on a small machine; each agent must read its own task section of §15.5 plus the sections it references, stay inside the files it owns, and keep warnings-as-errors.
3. **Then T17** (integration, end-to-end tests, accessibility pass), alone.
4. Merge each agent's branch yourself, resolve conflicts (the only expected shared edits are single appended lines in `gradle/libs.versions.toml` and the per-module `build.gradle.kts`), and run the full gate once after each merge batch and at the end.
5. Keep PR #4's description current: test results (actual counts and output), decisions and deviations, and the remaining-roadmap checklist (PLAN.md §11). Take the PR out of draft only when waves 2 and 3 are in and CI is green. **Never merge it yourself.**

Before opening or updating a PR: the full gate above plus, now that KVM is available, `connectedDebugAndroidTest` on a booted AVD and the managed-device task.

## Efficiency rules learned the hard way
- **Use one Gradle daemon.** Invoke `./gradlew` the same way every time. Don't change JVM args or `-D` flags between runs, and don't use `--no-daemon`. Several daemons on one machine waste memory and slow everything down. With parallel worktree agents, expect contention; limit concurrency to what the machine's CPU and RAM handle (each Gradle build wants about 3–4 GB).
- **Iterate narrowly:** `./gradlew :core:domain:test --tests '*goal*'`, `:module:compileDebugKotlin`. Fix every error from a run before re-running. Run the full gate **once** at the end.
- **Shared usage limit:** all subagents draw on the same 5-hour usage limit, and a large fan-out can hit it. A stopped agent can be resumed with a message after the reset instead of being restarted.
- Maven Central sometimes returned 429 or false 404s in the cloud sandbox. If resolution fails for an artifact that exists, retry with `--refresh-dependencies` (Gradle caches a false "missing" for 24 hours).

## Rules
- Commit messages end with the attribution lines your session gives you. No model identifiers in commits, PRs or code.
- Don't change `docs/PLAN.md`'s confirmed decisions. If `TECHNICAL_PLAN.md` is wrong, fix it in the same PR and say so.
- Open PRs only as part of this plan. Never merge them yourself.
