# Dihydrogen Monoxide: Technical Plan

This document turns the approved product and architecture plan, [PLAN.md](PLAN.md), into implementation-ready detail: pinned versions, build layout, schemas, algorithms with worked examples, and a task breakdown for implementation agents. PLAN.md remains the authority on product scope. Its "Product decisions (confirmed)" table is final, and Wear OS is out of scope (issue #1).

**How to use it.** An implementation agent reads §1–§3, the sections its task names, and its task in §15. Worked-example tables (§7.5, §8.5, §9.14, §11.8) are meant to be encoded as unit tests verbatim.

| § | Contents |
|---|---|
| 1 | Decisions made in this document |
| 2 | Pinned versions and compatibility |
| 3 | Build system: modules, convention plugins, version catalog, static analysis |
| 4 | Room schema v1 and DAOs |
| 5 | Settings (DataStore) schema |
| 6 | Data layer, use cases, dependency injection |
| 7 | GoalPolicy v1 |
| 8 | Pace curve and reminders |
| 9 | Statistics and insights |
| 10 | Health Connect |
| 11 | Backup, restore, export |
| 12 | Weather (Open-Meteo) |
| 13 | Navigation and screens |
| 14 | Testing and CI |
| 15 | Work breakdown |

---

## 1. Decisions made in this document

These settle points PLAN.md left open or stated two ways. None contradicts a confirmed product decision.

| # | Topic | Decision | Where |
|---|---|---|---|
| D1 | DataStore flavour | Typed `DataStore<T>` with a kotlinx.serialization JSON serializer, not protobuf. Two stores: user settings (backed up) and device state (never backed up). | §5.1 |
| D2 | Hydration-day bucketing | `hydration_day` is a stored column on each entry, computed at write time. A day-start change re-buckets only the current day onward. | §4.2 |
| D3 | `DailyTotal` | A Room `@DatabaseView` grouping by the stored column, not a materialised table. `effectiveMl` is dropped (it would always equal `totalMl`). | §4.2 |
| D4 | Health Connect token | Stored in a Room `sync_state` table, in the same transaction as imported rows, not in DataStore. | §4.3 |
| D5 | Extra tables | `hc_pending_op`, `sync_state`, `health_daily` are in schema v1 so Phase 5 needs no migration. `intake_entry.hc_adopted`, `profile_snapshot.high_altitude`, `daily_goal.policy_version` and tombstone / `updated_at` columns on mergeable tables are added to PLAN §3's field lists. | §4.3 |
| D6 | Modules added | `:core:data`, `:core:backup`, `:core:common`, `:core:network`, `:core:reminders`, `:core:weather`, `:core:testing`, `:feature:onboarding`. | §3.2 |
| D7 | Backup format home | `:core:backup` (pure Kotlin), available from Phase 3 for JSON and CSV export, reused by Drive in Phase 4. | §11.1 |
| D8 | Kotlin plugin setup | AGP 9 built-in Kotlin: Android modules do not apply `org.jetbrains.kotlin.android`; KSP only, no kapt. | §3.3 |
| D9 | detekt | `2.0.0-alpha.6` with the plain `detekt` task (no type resolution). 1.23.8 was used until the move to JDK 25, which it cannot run on; take 2.0.0 stable when it ships. | §3.8 |
| D10 | Health Connect artifacts | Stable `connect-client` 1.1.0 in the app; `connect-testing` 1.0.0-alpha03 for tests only, with its strict client dependency excluded. | §2.3 |
| D11 | Navigation | Navigation Compose 2.10 with `@Serializable` routes; four top-level destinations (Today, History, Insights, Settings). | §13 |
| D12 | Goal constants | Child table from EFSA × 0.8; "unspecified" sex uses the mean of the female and male values; activity minutes at 6 / 12 / 18 ml per minute by intensity, capped at 2,000 ml; child clamp 600–3,000 ml; halves round up in `roundTo50`; `calculatedMl` is nullable for manual-only users. | §7.2 |
| D13 | Activity rule | Measured or planned minutes replace the level-based default for that day; they are never added together. | §7.2 |
| D14 | Pace curve | Default: 15% by the first 10% of the wake window, linear to 100% at 90%, then flat. Tolerance is a percentage of the goal (default 10%). | §8.2 |
| D15 | Reminder rules | The interval floor is anchored to the last drink as PLAN §5.1 says (a drink resets it). Refinement: an ignored reminder also waits one interval before the next, so the effective floor is the later of the last drink and the last reminder; without this a behind user who doesn't drink would be re-notified at every alarm; end-of-day summary at sleep − 30 min; nudges stop at the summary time when it is enabled; escalation is a nudge variant sent at most once a day. | §8.4 |
| D16 | Stats choices | Spearman for correlations; sample SD; ISO weeks for the streak freeze; MAD robust SD floored at 10% of the median; the A&S 7.1.26 `erf` approximation. | §9 |
| D17 | Encryption | PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte salt, AES-256-GCM with a 12-byte nonce; derived key wrapped by Android Keystore for scheduled backups. | §11.4 |
| D18 | Hydration record interval | Health Connect writes use `endTime = startTime + 1 s`, because the client rejects equal times. | §10.5 |
| D19 | Adopted imports | Editable locally, never overwritten by the source app, never written back to Health Connect. | §10.6 |
| D20 | Application id | `casa.peterson.dhmo` (confirmed by the owner). | §3.1 |

---

## 2. Pinned versions and compatibility

### 2.1 How the versions were verified

Verified on **2026-11-15** against the live repositories. "Latest stable" means the highest version with no alpha, beta, RC, milestone, dev or `-next` qualifier.

| Source | URL pattern |
|---|---|
| Google Maven | `https://dl.google.com/dl/android/maven2/<group path>/<artifact>/maven-metadata.xml` |
| Maven Central | `https://repo1.maven.org/maven2/<group path>/<artifact>/maven-metadata.xml` (and the `.pom` of a version for its dependencies) |
| Gradle Plugin Portal | `https://plugins.gradle.org/m2/<group path>/<artifact>/maven-metadata.xml` |
| Gradle distributions | `https://services.gradle.org/versions/current` and `/versions/all` |
| AGP compatibility | `https://developer.android.com/build/releases/gradle-plugin` (AGP 9.5 notes) and `https://developer.android.com/build/migrate-to-built-in-kotlin` |

In addition, a throwaway **smoke project** was built in the planning environment with exactly the versions marked ✔ in the last column: one Android application module (Compose, Hilt, Room, KSP, serialization, Navigation, WorkManager, DataStore, Glance, Health Connect, Credentials, Play services auth and location, Vico, OkHttp as dependencies; a Hilt view model, a Room database with exported schema, a type-safe `NavHost`) and one Kotlin/JVM module. `assembleDebug`, `testDebugUnitTest` (Robolectric + a Roborazzi capture call + `FakeHealthConnectClient`), JVM `test`, `detekt` and `ktlintCheck` all executed on Gradle 9.9.1 with JDK 21. "Built" below means the artifact was resolved and compiled or run in that project, not merely looked up.

### 2.2 Pinned versions

| Component | Artifact / plugin | Version | Verified |
|---|---|---|---|
| Gradle | wrapper distribution | **9.9.1** | lookup + built ✔ |
| Android Gradle plugin | `com.android.application` / `com.android.library` | **9.5.1** | lookup + built ✔ |
| Kotlin | `org.jetbrains.kotlin:kotlin-gradle-plugin`, `org.jetbrains.kotlin.jvm`, `.plugin.compose`, `.plugin.serialization` | **2.4.21** | lookup + built ✔ |
| KSP | `com.google.devtools.ksp` | **2.3.14** | lookup + built ✔ |
| JDK | Temurin 25, CI and local (Gradle daemon JVM criteria) | 17 minimum | built on 25 ✔ |
| compileSdk / targetSdk / minSdk | platform `android-37.0` | **37 / 37 / 28** | built ✔ |
| Build tools | | **37.0.0** | built ✔ |
| Compose BOM | `androidx.compose:compose-bom` | **2026.09.00** (Compose UI 1.12.1, Material 3 1.4.0) | lookup + built ✔ |
| Room | `androidx.room:*`, plugin `androidx.room` | **2.8.5** | lookup + built ✔ |
| Hilt | `com.google.dagger:hilt-android`, `hilt-compiler`, plugin | **2.60.1** | lookup + built ✔ |
| AndroidX Hilt | `androidx.hilt:hilt-navigation-compose`, `hilt-work`, `hilt-compiler` | **1.4.0** | lookup + built ✔ |
| WorkManager | `androidx.work:work-runtime-ktx`, `work-testing` | **2.12.0** | lookup + built ✔ |
| Glance | `androidx.glance:glance-appwidget`, `glance-material3` | **1.2.0** | lookup + built ✔ |
| Health Connect client | `androidx.health.connect:connect-client` | **1.1.0** | lookup + built ✔ |
| Health Connect testing | `androidx.health.connect:connect-testing` | **1.0.0-alpha03** (no stable exists; test-only) | lookup + built ✔ |
| Credential Manager | `androidx.credentials:credentials`, `credentials-play-services-auth` | **1.6.0** | lookup + built ✔ |
| Google ID | `com.google.android.libraries.identity.googleid:googleid` | **1.2.1** | lookup + built ✔ |
| Identity `AuthorizationClient` | `com.google.android.gms:play-services-auth` | **22.0.0** | lookup + built ✔ |
| Location | `com.google.android.gms:play-services-location` | **21.4.0** | lookup + built ✔ |
| Vico | `com.patrykandpatrick.vico:compose-m3` | **3.3.1** | lookup + built ✔ (dependency only) |
| kotlinx.serialization | `kotlinx-serialization-json` | **1.13.0** | lookup + built ✔ |
| Coroutines | `kotlinx-coroutines-core`, `-android`, `-test`, `-play-services` | **1.11.0** | lookup + built ✔ |
| DataStore | `androidx.datastore:datastore` | **1.3.0** | lookup + built ✔ (dependency only) |
| Navigation | `androidx.navigation:navigation-compose` | **2.10.2** | lookup + built ✔ |
| Lifecycle | `androidx.lifecycle:*` | **2.11.0** | lookup + built ✔ |
| Activity | `androidx.activity:activity-compose` | **1.13.0** | lookup + built ✔ |
| Core | `androidx.core:core-ktx` | **1.19.1** | lookup + built ✔ |
| Splash screen | `androidx.core:core-splashscreen` | **1.2.0** | lookup |
| Paging | `androidx.paging:paging-compose`, `paging-runtime` | **3.5.3** | lookup |
| OkHttp | `com.squareup.okhttp3:okhttp`, `mockwebserver3` | **5.5.1** | lookup + built ✔ |
| detekt | `dev.detekt` | **2.0.0-alpha.6** | built on JDK 25 ✔ |
| ktlint Gradle plugin | `org.jlleitschuh.gradle.ktlint` | **14.2.0** | lookup + built ✔ |
| ktlint engine | `com.pinterest.ktlint:ktlint-cli` (set through the plugin) | **1.8.0** | lookup + built ✔ |
| Roborazzi | `io.github.takahirom.roborazzi:*`, plugin | **1.79.0** | lookup + built ✔ |
| Robolectric | `org.robolectric:robolectric` | **4.18** | lookup + built ✔ |
| JUnit | `junit:junit` | **4.13.2** | lookup + built ✔ |
| Truth | `com.google.truth:truth` | **1.4.5** | lookup + built ✔ |
| Turbine | `app.cash.turbine:turbine` | **1.2.1** | lookup + built ✔ |
| MockK (sparingly; fakes preferred) | `io.mockk:mockk` | **1.14.12** | lookup |
| AndroidX Test | `core` 1.7.0, `runner` 1.7.0, `ext:junit` 1.3.0, `espresso-core` 3.7.0 | as listed | lookup (`ext:junit` built ✔) |

Not used, and why: protobuf (D1); kotlinx-datetime (`java.time` suffices at `minSdk` 28); JUnit 5 (Robolectric and the AndroidX test runners are JUnit 4; one engine everywhere); Navigation 3 (D11); `material-icons-extended` (frozen at 1.7.8).

### 2.3 Compatibility findings

| Area | Finding |
|---|---|
| AGP ↔ Gradle ↔ JDK | AGP 9.5 requires Gradle ≥ 9.6.0 and JDK ≥ 17. Gradle 9.9.1 with AGP 9.5.1 built on JDK 21. Gradle prints two deprecation notices originating in plugins (`Configuration.setVisible`, `ReportingExtension.file`), scheduled for removal in Gradle 10 and 11: harmless now, a reason not to jump to Gradle 10 without re-checking. |
| AGP ↔ compileSdk | AGP 9.5 supports up to API 37. `compileSdk = 37` resolves the installed platform `android-37.0`. Platform 37.1 is a minor release that the AGP 9.5 notes do not list, so it is not used. AGP's default build tools are 36.0.0; the project sets 37.0.0 explicitly. |
| AGP built-in Kotlin | See §3.3. AGP 9.5.1 ships a dependency on Kotlin Gradle plugin 2.2.10; declaring 2.4.21 on the build classpath upgraded it without problems. `org.jetbrains.kotlin.android` and `kotlin-kapt` must not be applied. |
| KSP ↔ Kotlin | KSP is versioned independently of Kotlin since KSP 2.3 (no `kotlinVersion-kspVersion` pairing). KSP 2.3.14 (built against Kotlin 2.4.0) ran the Room and Hilt processors under Kotlin 2.4.21. |
| Compose compiler | The `org.jetbrains.kotlin.plugin.compose` plugin is versioned with Kotlin (2.4.21) and worked with BOM 2026.09.00. |
| Hilt ↔ AGP 9 | Hilt Gradle plugin 2.60.1 applied and aggregated correctly with AGP 9.5.1 and KSP. `hiltViewModel()` is imported from `androidx.hilt.lifecycle.viewmodel.compose` (available through `hilt-navigation-compose` 1.4.0); the old `androidx.hilt.navigation.compose` import is deprecated. |
| Room | Room 2.8.5 with the `androidx.room` Gradle plugin exported the schema under KSP 2.3.14. |
| detekt ↔ Kotlin | detekt 2.0.0-alpha.6 embeds Kotlin 2.4.10 (1.23.8 embedded 2.0.21, whose JDK version check rejects JDK 25). The plain `detekt` task analysed Kotlin 2.4.21 sources in both module types and found the default source directories under AGP built-in Kotlin. Type-resolution tasks are not used. |
| ktlint | Plugin 14.2.0 with engine 1.8.0 ran on Gradle 9.9.1 for both module types and for `.gradle.kts` files. |
| Robolectric ↔ SDK | Robolectric 4.18 ran tests at SDK 37 (it downloaded its Android 17 image). No `@Config(sdk = …)` cap is needed; modules pin `sdk=37` in `robolectric.properties` for reproducible screenshots (§14.1). |
| Roborazzi ↔ Robolectric | Roborazzi 1.79.0 with Robolectric 4.18 and `@GraphicsMode(NATIVE)` executed a Compose capture call. Only the call path was exercised: image recording and comparison (`recordRoborazziDebug` / `verifyRoborazziDebug`) were not run. |
| Health Connect client ↔ testing | Every `connect-testing` release pins an exact client with a strict version: alpha04 → `connect-client` `[1.2.0-alpha05]`, alpha03 → `[1.1.0-beta02]`. Using alpha04 would force an alpha client into the app. The plan uses stable client 1.1.0 and `connect-testing` alpha03 with `exclude(group = "androidx.health.connect", module = "connect-client")`; `FakeHealthConnectClient.insertRecords` ran against client 1.1.0 this way. Risk: the fake was built against 1.1.0-beta02; if a later test hits a binary mismatch, the fallback is to align both on the 1.2.0 line once it is stable. |
| `HydrationRecord` | Client 1.1.0 throws `IllegalArgumentException("startTime must be before endTime.")` for equal times, hence D18. |
| OkHttp 5 | The test server artifact is `mockwebserver3` (package `mockwebserver3`), not the old `mockwebserver`. |

### 2.4 Not verified

- GitHub Action versions (§14.2): the GitHub API and release pages were not reachable from the planning environment.
- JDK 17 was not available locally; the JDK 17 claim rests on the AGP release notes.
- Roborazzi image recording and verification, the Gradle Managed Device, `assembleRelease` with R8, Android lint, and a multi-module build with `build-logic` were not run. T0 is the first real exercise of those.
- Vico, DataStore, Glance, Credentials, Play services and Paging were resolved (and, except Paging and splash screen, compiled onto the classpath) but none of their APIs was called. API details quoted for them (for example `AuthorizationClient.clearToken`) come from documentation knowledge and must be confirmed by the owning task.
- Open-Meteo request and response shapes were verified live (§12). The Drive REST and Health Connect API shapes were not exercised against real services.
- EFSA and other source figures (§7.1) are quoted from memory of the published values and should be checked against the source documents before the in-app citations are written.

---
## 3. Build system

### 3.1 Identity

| Item | Value |
|---|---|
| Root project name | `dihydrogen-monoxide` |
| `applicationId` | `casa.peterson.dhmo` (debug builds add the suffix `.debug`). Confirmed by the owner; permanent once published. |
| Base package / namespace | `casa.peterson.dhmo`; each module's namespace is `casa.peterson.dhmo.<path with dots>`, for example `casa.peterson.dhmo.core.database`, `casa.peterson.dhmo.feature.today`, `casa.peterson.dhmo.sync.drive` |
| `minSdk` / `compileSdk` / `targetSdk` | 28 / 37 / 37 |
| `buildToolsVersion` | `37.0.0` (set explicitly; AGP 9.5's default is 36.0.0) |
| Java / Kotlin bytecode target | 17 (`compileOptions` source and target 17; Kotlin `jvmTarget` 17). The Gradle daemon is pinned to Temurin 25 by `gradle/gradle-daemon-jvm.properties` (downloaded through the foojay resolver when missing), and unit tests run on a JDK 25 toolchain (`JDK_VERSION` in `build-logic`). Bytecode stays at 17 because Android (minSdk 28) lacks Java 21+ library APIs. |
| Version | `versionCode = 1`, `versionName = "0.1.0"` in `:app` |

### 3.2 Module graph

Three modules are added to PLAN §2's layout, plus four small support modules. Additions are marked **new**.

| Module | Type | Purpose | Depends on (project dependencies) |
|---|---|---|---|
| `:app` | Android application | entry point, `DhmoNavHost`, Hilt graph, WorkManager init, manifest glue | every module below except `:core:testing` |
| `:core:common` **new** | Kotlin/JVM | qualifiers, `ZoneProvider`, tiny helpers | – |
| `:core:model` | Kotlin/JVM | domain types, `AppSettings` / `DeviceState`, ISO serializers | – |
| `:core:domain` | Kotlin/JVM | goal calculator, pace and reminder decision, stats, volume formatting, `hydrationDay` | `:core:model` |
| `:core:backup` **new** | Kotlin/JVM | backup document, codec, crypto, merge, retention, CSV (§11) | `:core:model` |
| `:core:database` | Android library | Room entities, DAOs, schema | `:core:model` |
| `:core:datastore` | Android library | settings and device-state stores | `:core:model`, `:core:common` |
| `:core:data` **new** | Android library | repositories, controller interfaces, use cases, Hilt bindings (§6) | `:core:model`, `:core:common`, `:core:domain`, `:core:backup`, `:core:database`, `:core:datastore` |
| `:core:designsystem` | Android library (Compose) | theme, colours, typography, shared components (`ProgressRing`, `VolumeText`, `DhmoScaffold`, icons) | `:core:model`, `:core:domain` (for `VolumeFormatter`) |
| `:core:network` **new** | Android library | shared `OkHttpClient` and `Json` | `:core:common` |
| `:core:reminders` **new** | Android library | scheduler, receivers, notifications (§8.6) | `:core:data`, `:core:domain`, `:core:model`, `:core:common` |
| `:core:weather` **new** | Android library | Open-Meteo provider, location refresher, weather worker (§12) | `:core:data`, `:core:network`, `:core:domain`, `:core:model`, `:core:common` |
| `:core:testing` **new** | Android library (used only as `testImplementation` / `androidTestImplementation`) | fakes for every `:core:data` interface, `MainDispatcherRule`, fixed clock, screenshot helpers, golden datasets | `:core:data`, `:core:model`, `:core:domain`, `:core:common`, `:core:designsystem` |
| `:feature:onboarding` **new** | Android library (feature) | onboarding flow | feature set (below) |
| `:feature:today` | Android library (feature) | Today, edit sheet, containers, `QuickLogActivity` | feature set |
| `:feature:history` | Android library (feature) | history | feature set |
| `:feature:insights` | Android library (feature) | insights, charts (Vico) | feature set |
| `:feature:profile` | Android library (feature) | profile and goal | feature set |
| `:feature:reminders` | Android library (feature) | reminder settings UI | feature set |
| `:feature:settings` | Android library (feature) | settings, export | feature set + `:core:backup` |
| `:sync:healthconnect` | Android library (Compose) | Health Connect engine, workers, settings content, rationale activity | `:core:data`, `:core:database`, `:core:domain`, `:core:model`, `:core:common`, `:core:designsystem` |
| `:sync:drive` | Android library (Compose) | Drive auth, REST client, worker, backup and restore content | `:core:data`, `:core:backup`, `:core:network`, `:core:model`, `:core:common`, `:core:designsystem` |
| `:widget` | Android library | Glance widgets, Quick Settings tile, shortcuts | `:core:data`, `:core:domain`, `:core:model`, `:core:common`, `:core:designsystem` |

**Feature set** = `:core:data`, `:core:domain`, `:core:model`, `:core:common`, `:core:designsystem`, and `:core:testing` for tests. This is wired by the `dhmo.android.feature` convention plugin.

Allowed edges, as a picture (an arrow means "depends on"):

```
:app ──► feature:* ──► core:data ──► core:database ──► core:model
  │            │           │   ├──► core:datastore ──► core:model, core:common
  │            │           │   ├──► core:domain ─────► core:model
  │            │           │   └──► core:backup ─────► core:model
  │            └──► core:designsystem ──► core:model, core:domain
  ├──► core:reminders ──► core:data
  ├──► core:weather ────► core:data, core:network
  ├──► sync:healthconnect ──► core:data, core:database
  ├──► sync:drive ──► core:data, core:backup, core:network
  └──► widget ──► core:data
```

Forbidden: feature → feature; feature → `:core:database`, `:core:datastore`, `:sync:*`, `:core:reminders`, `:core:weather`, `:widget`; anything → `:app`; a pure JVM module → an Android module. `:sync:healthconnect` is the only non-core module that may use `:core:database` directly (it needs `HcSyncDao` and transactions spanning imports and the token). T0 adds a `checkModuleGraph` task in the root build (a few lines that walk `configurations` of each project and fail on a forbidden edge) and runs it in CI.

### 3.3 `build-logic` convention plugins

`build-logic/` is an included build with one project, `convention`. Its `build.gradle.kts` uses `kotlin-dsl` and has `compileOnly` dependencies on the AGP, Kotlin, KSP, Compose compiler, Room, Hilt, detekt, ktlint and Roborazzi Gradle plugin artifacts from the catalog. Plugins are precompiled classes registered in `gradlePlugin { plugins { … } }`.

| Plugin id | Class | What it configures |
|---|---|---|
| `dhmo.android.application` | `AndroidApplicationConventionPlugin` | applies `com.android.application`, `dhmo.quality`; `compileSdk 37`, `minSdk 28`, `targetSdk 37`, `buildToolsVersion "37.0.0"`; Java 17; Kotlin compiler options; `buildFeatures.buildConfig = true`; release build type with R8 (`isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt` + `proguard-rules.pro`); debug `applicationIdSuffix ".debug"`; lint config (below); packaging excludes `META-INF/{AL2.0,LGPL2.1}`; the Gradle Managed Device `pixel6Api34` |
| `dhmo.android.library` | `AndroidLibraryConventionPlugin` | applies `com.android.library`, `dhmo.quality`; the same SDK, Java and Kotlin settings; `defaultConfig.consumerProguardFiles("consumer-rules.pro")`; `resourcePrefix` derived from the module path (for example `feature_today_`); `testOptions.unitTests.isIncludeAndroidResources = true`; lint config; the Gradle Managed Device; standard unit-test dependencies (`dhmo.android.test` logic) |
| `dhmo.android.compose` | `AndroidComposeConventionPlugin` | applies `org.jetbrains.kotlin.plugin.compose`; `buildFeatures.compose = true`; adds the Compose BOM as `implementation(platform(...))` and `androidTestImplementation(platform(...))`; adds `compose-ui`, `compose-ui-tooling-preview`, `compose-material3`, `debugImplementation` of `ui-tooling` and `ui-test-manifest`; enables Compose compiler reports with `-Pdhmo.composeReports=true` |
| `dhmo.android.hilt` | `AndroidHiltConventionPlugin` | applies `com.google.devtools.ksp` and `com.google.dagger.hilt.android`; adds `hilt-android`, `ksp(hilt-compiler)`; for tests `hilt-android-testing` + `kspTest(hilt-compiler)` |
| `dhmo.android.room` | `AndroidRoomConventionPlugin` | applies `com.google.devtools.ksp` and `androidx.room`; `room { schemaDirectory("$projectDir/schemas") }`; `ksp { arg("room.generateKotlin", "true") }`; adds `room-runtime`, `room-ktx`, `room-paging`, `ksp(room-compiler)`, `testImplementation(room-testing)`, `androidTestImplementation(room-testing)`; adds the schema directory to `androidTest` assets |
| `dhmo.android.feature` | `AndroidFeatureConventionPlugin` | applies `dhmo.android.library`, `dhmo.android.compose`, `dhmo.android.hilt`, `dhmo.android.roborazzi`, `org.jetbrains.kotlin.plugin.serialization`; adds the feature set of project dependencies; adds `navigation-compose`, `hilt-navigation-compose`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `kotlinx-serialization-json`, `kotlinx-coroutines-android` |
| `dhmo.android.roborazzi` | `AndroidRoborazziConventionPlugin` | applies `io.github.takahirom.roborazzi`; adds `roborazzi`, `roborazzi-compose`, `roborazzi-junit-rule`, `robolectric`, `compose-ui-test-junit4` as `testImplementation`; `roborazzi { outputDir.set(file("src/test/screenshots")) }`; sets the system property `robolectric.pixelCopyRenderMode=hardware` for tests |
| `dhmo.android.test` (applied by the library and application plugins, not directly) | `AndroidTestConventionPlugin` | `testImplementation`: JUnit 4, Truth, Turbine, `kotlinx-coroutines-test`, Robolectric, `androidx-test-core`, `androidx-test-ext-junit`; `androidTestImplementation`: `androidx-test-runner`, `androidx-test-ext-junit`, Truth; `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`; unit tests get `maxHeapSize = "2g"` |
| `dhmo.jvm.library` | `JvmLibraryConventionPlugin` | applies `org.jetbrains.kotlin.jvm`, `dhmo.quality`; Java source and target 17, Kotlin `jvmTarget` 17; `explicitApi()` is **not** enabled; `testImplementation`: JUnit 4, Truth, Turbine, `kotlinx-coroutines-test`; `tasks.test { useJUnit() }` |
| `dhmo.quality` | `QualityConventionPlugin` | applies `dev.detekt` and `org.jlleitschuh.gradle.ktlint` and configures both (§3.8) |

Kotlin compiler options applied everywhere: `allWarningsAsErrors = true` unless `-Pdhmo.warningsAsErrors=false`; `freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"`; for Compose modules also `-opt-in=androidx.compose.material3.ExperimentalMaterial3Api`.

**Kotlin and AGP 9 ("built-in Kotlin").** AGP 9 compiles Kotlin itself. Android modules therefore **do not apply `org.jetbrains.kotlin.android`**; applying it fails the build. `org.jetbrains.kotlin.kapt` is also incompatible, and everything here uses KSP. AGP 9.5.1 has a runtime dependency on Kotlin Gradle plugin 2.2.10; the build raises that to 2.4.21 simply by having the Kotlin Gradle plugin 2.4.21 on the build classpath (declared in the root `plugins {}` block with `apply false`, and as a `compileOnly`/`implementation` dependency of `build-logic`). Kotlin options for Android modules go in the `kotlin { compilerOptions { } }` extension that AGP registers, never in `android.kotlinOptions`. Extra source directories go through `android.sourceSets`, not `kotlin.sourceSets`. Pure JVM modules still apply `org.jetbrains.kotlin.jvm`. The Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization` are applied by id and share the Kotlin version.

### 3.4 `gradle/libs.versions.toml`

```toml
[versions]
agp = "9.5.1"
kotlin = "2.4.21"
ksp = "2.3.14"
androidx-activity = "1.13.0"
androidx-compose-bom = "2026.09.00"
androidx-core = "1.19.1"
androidx-core-splashscreen = "1.2.0"
androidx-credentials = "1.6.0"
androidx-datastore = "1.3.0"
androidx-glance = "1.2.0"
androidx-health-connect = "1.1.0"
androidx-health-connect-testing = "1.0.0-alpha03"
androidx-hilt = "1.4.0"
androidx-lifecycle = "2.11.0"
androidx-navigation = "2.10.2"
androidx-paging = "3.5.3"
androidx-room = "2.8.5"
androidx-test-core = "1.7.0"
androidx-test-espresso = "3.7.0"
androidx-test-ext-junit = "1.3.0"
androidx-test-runner = "1.7.0"
androidx-work = "2.12.0"
detekt = "2.0.0-alpha.6"
googleid = "1.2.1"
hilt = "2.60.1"
javax-inject = "1"
junit4 = "4.13.2"
kotlinx-coroutines = "1.11.0"
kotlinx-serialization = "1.13.0"
ktlint = "1.8.0"
ktlint-gradle = "14.2.0"
mockk = "1.14.12"
okhttp = "5.5.1"
play-services-auth = "22.0.0"
play-services-location = "21.4.0"
robolectric = "4.18"
roborazzi = "1.79.0"
room = "2.8.5"
truth = "1.4.5"
turbine = "1.2.1"
vico = "3.3.1"

[libraries]
# AndroidX core
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-core-splashscreen = { module = "androidx.core:core-splashscreen", version.ref = "androidx-core-splashscreen" }
androidx-datastore = { module = "androidx.datastore:datastore", version.ref = "androidx-datastore" }
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "androidx-lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidx-lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "androidx-navigation" }
androidx-navigation-testing = { module = "androidx.navigation:navigation-testing", version.ref = "androidx-navigation" }
androidx-paging-compose = { module = "androidx.paging:paging-compose", version.ref = "androidx-paging" }
androidx-paging-runtime = { module = "androidx.paging:paging-runtime", version.ref = "androidx-paging" }
androidx-paging-testing = { module = "androidx.paging:paging-testing", version.ref = "androidx-paging" }

# Compose (versions from the BOM)
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "androidx-compose-bom" }
androidx-compose-foundation = { module = "androidx.compose.foundation:foundation" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-material3-adaptive-navigation-suite = { module = "androidx.compose.material3:material3-adaptive-navigation-suite" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }

# Room
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "androidx-room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "androidx-room" }
androidx-room-paging = { module = "androidx.room:room-paging", version.ref = "androidx-room" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "androidx-room" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "androidx-room" }

# Hilt
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-android-testing = { module = "com.google.dagger:hilt-android-testing", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "androidx-hilt" }
androidx-hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "androidx-hilt" }
androidx-hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "androidx-hilt" }
javax-inject = { module = "javax.inject:javax.inject", version.ref = "javax-inject" }

# WorkManager, Glance
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "androidx-work" }
androidx-work-testing = { module = "androidx.work:work-testing", version.ref = "androidx-work" }
androidx-glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "androidx-glance" }
androidx-glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "androidx-glance" }
androidx-glance-appwidget-testing = { module = "androidx.glance:glance-appwidget-testing", version.ref = "androidx-glance" }

# Health Connect
androidx-health-connect-client = { module = "androidx.health.connect:connect-client", version.ref = "androidx-health-connect" }
androidx-health-connect-testing = { module = "androidx.health.connect:connect-testing", version.ref = "androidx-health-connect-testing" }

# Google identity, Drive auth, location
androidx-credentials = { module = "androidx.credentials:credentials", version.ref = "androidx-credentials" }
androidx-credentials-play-services-auth = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "androidx-credentials" }
googleid = { module = "com.google.android.libraries.identity.googleid:googleid", version.ref = "googleid" }
play-services-auth = { module = "com.google.android.gms:play-services-auth", version.ref = "play-services-auth" }
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "play-services-location" }

# Kotlin libraries
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-play-services = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-play-services", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# Third party
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver3", version.ref = "okhttp" }
vico-compose-m3 = { module = "com.patrykandpatrick.vico:compose-m3", version.ref = "vico" }

# Test
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test-core" }
androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "androidx-test-espresso" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-ext-junit" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidx-test-runner" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-junit-rule = { module = "io.github.takahirom.roborazzi:roborazzi-junit-rule", version.ref = "roborazzi" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

# Gradle plugins as libraries, for build-logic (compileOnly)
android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
compose-gradlePlugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { module = "com.google.devtools.ksp:symbol-processing-gradle-plugin", version.ref = "ksp" }
room-gradlePlugin = { module = "androidx.room:room-gradle-plugin", version.ref = "androidx-room" }
hilt-gradlePlugin = { module = "com.google.dagger:hilt-android-gradle-plugin", version.ref = "hilt" }
detekt-gradlePlugin = { module = "dev.detekt:detekt-gradle-plugin", version.ref = "detekt" }
ktlint-gradlePlugin = { module = "org.jlleitschuh.gradle:ktlint-gradle", version.ref = "ktlint-gradle" }
roborazzi-gradlePlugin = { module = "io.github.takahirom.roborazzi:roborazzi-gradle-plugin", version.ref = "roborazzi" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "androidx-room" }
detekt = { id = "dev.detekt", version.ref = "detekt" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint-gradle" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
# Convention plugins (no version: they come from the included build)
dhmo-android-application = { id = "dhmo.android.application" }
dhmo-android-library = { id = "dhmo.android.library" }
dhmo-android-compose = { id = "dhmo.android.compose" }
dhmo-android-feature = { id = "dhmo.android.feature" }
dhmo-android-hilt = { id = "dhmo.android.hilt" }
dhmo-android-room = { id = "dhmo.android.room" }
dhmo-android-roborazzi = { id = "dhmo.android.roborazzi" }
dhmo-jvm-library = { id = "dhmo.jvm.library" }
```

Notes on the catalog:

- There is deliberately **no `kotlin-android` plugin entry** (§3.3).
- `room` and `androidx-room` hold the same value; keep only `androidx-room` if preferred. T0 may tidy this as long as the versions in §2 do not change.
- `mockk` is in the catalog for the few places where a platform class has no fake (for example `FusedLocationProviderClient`). Repositories and controllers are tested with the hand-written fakes in `:core:testing`, not with mocks.
- No protobuf, no kotlinx-datetime (`java.time` is available from API 26 and `minSdk` is 28, so no desugaring either), no Ktor, no Google API Java client.
- Icons: `material-icons-extended` is frozen at 1.7.8 and is not used. `:core:designsystem` ships the dozen icons it needs as vector drawables (Material Symbols, Apache 2.0).

### 3.5 `settings.gradle.kts`

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "dihydrogen-monoxide"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common", ":core:model", ":core:domain", ":core:backup")
include(":core:database", ":core:datastore", ":core:data")
include(":core:designsystem", ":core:network", ":core:reminders", ":core:weather", ":core:testing")
include(":feature:onboarding", ":feature:today", ":feature:history", ":feature:insights")
include(":feature:profile", ":feature:reminders", ":feature:settings")
include(":sync:healthconnect", ":sync:drive")
include(":widget")
```

`build-logic/settings.gradle.kts` declares the same repositories and reads the catalog with `versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }`.

### 3.6 Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false          // also pins the Kotlin Gradle plugin version used by AGP's built-in Kotlin
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
}

// checkModuleGraph task (§3.2) is registered here.
```

A module build file then looks like this:

```kotlin
// feature/today/build.gradle.kts
plugins { alias(libs.plugins.dhmo.android.feature) }
android { namespace = "casa.peterson.dhmo.feature.today" }
dependencies { implementation(libs.androidx.paging.compose) }

// core/domain/build.gradle.kts
plugins { alias(libs.plugins.dhmo.jvm.library) }
dependencies { api(projects.core.model) }
```

### 3.7 `gradle.properties` and the wrapper

```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
kotlin.code.style=official
android.useAndroidX=true
android.nonTransitiveRClass=true
ksp.incremental=true
```

- `android.builtInKotlin` and `android.newDsl` are left at their AGP 9 defaults (both on). Do not set them to `false`.
- `gradle/wrapper/gradle-wrapper.properties`: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.9.1-bin.zip`, with `distributionSha256Sum=d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228` (the checksum published at `https://services.gradle.org/versions/current` on 2026-11-15).
- `local.properties` (`sdk.dir=…`) is not committed; CI sets `ANDROID_HOME`.

### 3.8 Static analysis

**detekt 2.0.0-alpha.6** (config `config/detekt/detekt.yml`, `buildUponDefaultConfig = true`, `parallel = true`, `autoCorrect = false`):

- detekt 2.0.0-alpha.6 embeds the Kotlin 2.4.10 compiler and runs on JDK 25 (1.23.8, used before, does not). The convention plugin uses **only the plain `detekt` task** (no `detektMain` / `detektDebug` type-resolution tasks) and sets the sources explicitly, because with AGP's built-in Kotlin the detekt plugin does not discover Android source sets on its own:

  ```kotlin
  extensions.configure<DetektExtension> {
      buildUponDefaultConfig.set(true)
      parallel.set(true)
      config.setFrom(rootProject.files("config/detekt/detekt.yml"))
      source.setFrom(files("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin", "src/debug/kotlin"))
  }
  tasks.withType<Detekt>().configureEach { jvmTarget.set("17"); reports { sarif.required.set(true); html.required.set(true) } }
  // Keep detekt's own compiler version: the Kotlin Gradle plugin must not upgrade it.
  val detektKotlinVersion = getSupportedKotlinVersion() // dev.detekt.gradle.plugin
  configurations.matching { it.name == "detekt" }.configureEach {
      resolutionStrategy.eachDependency { if (requested.group == "org.jetbrains.kotlin") useVersion(detektKotlinVersion) }
  }
  ```

- All Kotlin sources live in `src/<set>/kotlin` (never `src/<set>/java`), so the list above is complete.
- **Language-feature rule:** detekt's embedded compiler (2.4.10) must be able to parse the sources, so don't use syntax newer than Kotlin 2.4. The earlier limit to Kotlin 2.0 syntax ended with the move off detekt 1.23.8.
- Rule set: defaults, plus `style.MaxLineLength.maxLineLength = 140`, `complexity.LongParameterList` `allowedFunctionParameters = 7`, `allowedConstructorParameters = 9` (detekt 1's thresholds 8 and 10) with `ignoreDefaultParameters = true`, `naming.FunctionNaming.ignoreAnnotated = ["Composable"]`, `style.MagicNumber` off for tests and for `GoalPolicyV1.kt`, `style.UnusedPrivateFunction.ignoreAnnotated = ["Preview"]`, `complexity.LongMethod.ignoreAnnotated = ["Composable"]`, `complexity.TooManyFunctions` off for DAOs and `*Repository`. **No detekt baseline file**; a suppression needs `@Suppress` with a comment.

**ktlint** through `org.jlleitschuh.gradle.ktlint` 14.2.0, with `ktlint { version.set("1.8.0"); android.set(true); outputToConsole.set(true) }`. Rules come from `.editorconfig` at the repository root:

```ini
root = true
[*.{kt,kts}]
indent_size = 4
max_line_length = 140
ktlint_code_style = android_studio
ktlint_function_naming_ignore_when_annotated_with = Composable
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
```

`ktlintCheck` also checks the `*.gradle.kts` files. `./gradlew ktlintFormat` fixes most findings.

**Android lint** (configured in the application and library convention plugins):

```kotlin
lint {
    warningsAsErrors = true
    abortOnError = true
    checkDependencies = false          // true only in :app, so each issue is reported once
    sarifReport = true
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    baseline = null                    // policy below
}
```

**Lint baseline policy:** the project starts with no baseline and keeps it that way. Fix the finding, or suppress the single occurrence with `@SuppressLint("Id")` / `tools:ignore` and a one-line reason. A `lint-baseline.xml` may only be added for a new lint check that arrives with a tool upgrade and produces many findings, in the same PR as the upgrade, with a tracking issue to remove it.

Commands: `./gradlew detekt ktlintCheck lint` (aggregate; `lint` runs per module, and `:app:lintRelease` is the CI gate).

---

## 4. Room schema v1 (`:core:database`)

Database class `DhmoDatabase`, file name `dhmo.db`, `version = 1`, `exportSchema = true`. The Room Gradle plugin writes the schema to `core/database/schemas/casa.peterson.dhmo.core.database.DhmoDatabase/1.json`, and that file is committed.

### 4.1 Storage conventions and type converters

| Kotlin type | SQL type | Encoding | Converter |
|---|---|---|---|
| `java.time.Instant` | `INTEGER` | epoch milliseconds (UTC) | `InstantConverter` |
| `java.time.ZoneOffset` | `INTEGER` | `totalSeconds` | `ZoneOffsetConverter` |
| `java.time.LocalDate` | `INTEGER` | `toEpochDay()` | `LocalDateConverter` |
| `java.util.UUID` | `TEXT` | canonical lower-case string | `UuidConverter` |
| Enums | `TEXT` | `name` | Room's built-in enum support |
| `Boolean` | `INTEGER` | 0 / 1 | built in |
| Day start / times of day | `INTEGER` | minutes after 00:00 (0–1439) | none (stored as `Int`) |
| Volumes | `INTEGER` | millilitres | none |

All four converters live in one `Converters` class registered with `@TypeConverters(Converters::class)` on the database. Entity classes are named `*Entity`; the domain types in `:core:model` have the plain names, and mappers (`toModel()` / `toEntity()`) live in `:core:data`.

Dates are stored as epoch days because range queries and `GROUP BY` on integers are cheap. The JSON backup (§11) uses ISO-8601 strings instead.

### 4.2 Hydration-day bucketing (decision)

A SQL view cannot know which day start applied to each entry, so **the hydration day is stored on each entry** in `intake_entry.hydration_day`, computed in Kotlin when the entry is written:

```kotlin
// :core:domain  time/HydrationDay.kt
fun hydrationDay(instant: Instant, offset: ZoneOffset, dayStart: LocalTime): LocalDate =
    instant.atOffset(offset).toLocalDateTime()
        .minusSeconds(dayStart.toSecondOfDay().toLong())
        .toLocalDate()
```

Rules:

1. On insert and on every edit that changes `instant` or `zoneOffset`, the repository recomputes `hydration_day` with the **current** `dayStartMinutes` setting.
2. When the user changes the day start, only entries of the *current* hydration day (as defined by the old setting) and later are re-bucketed with the new setting, in one transaction (`IntakeDao.entriesFromDay(oldToday)` then `updateHydrationDay`). Earlier days keep their buckets, so history never shifts. `DailyGoal.dayStartMinutes` records the boundary each past day used. The `DailyGoal` rows for the affected dates are re-snapshotted afterwards.
3. The transition day is therefore shorter or longer than 24 hours. That is accepted and covered by a golden test (§9.14, dataset H).
4. A restore keeps the `hydrationDay` values from the backup file.

`DailyTotal` is a **`@DatabaseView`** that groups by the stored column. It is always consistent with the entries, needs no incremental maintenance, and is fast: ten years at eight entries a day is about 30,000 indexed rows.

```kotlin
@DatabaseView(
    viewName = "daily_total",
    value = """
        SELECT hydration_day AS date,
               SUM(volume_ml) AS total_ml,
               COUNT(*)       AS entry_count,
               MIN(instant)   AS first_at,
               MAX(instant)   AS last_at
        FROM intake_entry
        WHERE deleted_at IS NULL
        GROUP BY hydration_day
    """,
)
data class DailyTotalView(
    @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "total_ml") val totalMl: Int,
    @ColumnInfo(name = "entry_count") val entryCount: Int,
    @ColumnInfo(name = "first_at") val firstAt: Instant,
    @ColumnInfo(name = "last_at") val lastAt: Instant,
)
```

PLAN §3 lists an `effectiveMl` column. It is dropped: the app tracks water only, and entries from excluded Health Connect source apps are never stored as live rows (§10.5), so `effectiveMl` would always equal `totalMl`.

### 4.3 Tables

`NN` = `NOT NULL`. No table declares a foreign key: entities reference each other by date or by soft reference only, and tombstones plus backup merges make cascading deletes unwanted.

**`intake_entry`** (`IntakeEntryEntity`)

| Column | SQL type | Notes |
|---|---|---|
| `id` | `TEXT` NN, **PK** | UUID v4; also the Health Connect `clientRecordId` |
| `instant` | `INTEGER` NN | epoch ms |
| `zone_offset_seconds` | `INTEGER` NN | offset in force when logged |
| `hydration_day` | `INTEGER` NN | epoch day (§4.2) |
| `volume_ml` | `INTEGER` NN | 1–10,000, validated in the repository |
| `source` | `TEXT` NN | `MANUAL`, `WIDGET`, `NOTIFICATION`, `TILE`, `HEALTH_CONNECT_IMPORT`, `WEAR` |
| `hc_record_id` | `TEXT` NULL | Health Connect record id, once written or imported |
| `hc_data_origin` | `TEXT` NULL | source package name for imports |
| `hc_adopted` | `INTEGER` NN default 0 | 1 = an import the user adopted and may edit (§10.6) |
| `note` | `TEXT` NULL | max 200 characters |
| `created_at` | `INTEGER` NN | epoch ms |
| `updated_at` | `INTEGER` NN | epoch ms; also the Health Connect `clientRecordVersion` |
| `deleted_at` | `INTEGER` NULL | tombstone |

Indices: `index_intake_entry_hydration_day (hydration_day)`, `index_intake_entry_instant (instant)`, `index_intake_entry_hc_record_id (hc_record_id) UNIQUE` (SQLite allows many NULLs in a unique index).

**`container`** (`ContainerEntity`)

| Column | SQL type | Notes |
|---|---|---|
| `id` | `TEXT` NN, **PK** | UUID |
| `name` | `TEXT` NN | |
| `volume_ml` | `INTEGER` NN | |
| `icon` | `TEXT` NN | `ContainerIcon` enum: `GLASS`, `CUP`, `MUG`, `BOTTLE`, `LARGE_BOTTLE`, `FLASK` |
| `sort_order` | `INTEGER` NN | 0 = default container (widget 2×1, tile); 0 and 1 are the notification actions |
| `updated_at` | `INTEGER` NN | for backup merge |
| `deleted_at` | `INTEGER` NULL | tombstone |

Index: `(sort_order)`. Seeded on first run by a `RoomDatabase.Callback` (through `ContainerRepository.seedDefaultsIfEmpty()`), with fixed UUIDs so that two devices merge cleanly: Glass 250 ml (`00000000-0000-4000-8000-000000000001`, sort 0), Bottle 500 ml (`…0002`, sort 1), Large bottle 750 ml (`…0003`, sort 2). When the units setting is US or imperial ounces the seed uses 8 / 16 / 24 fl oz converted to ml (237 / 473 / 710).

**`profile_snapshot`** (`ProfileSnapshotEntity`)

| Column | SQL type | Notes |
|---|---|---|
| `id` | `TEXT` NN, **PK** | UUID |
| `effective_from` | `INTEGER` NN | epoch day |
| `birth_date` | `INTEGER` NN | epoch day |
| `sex` | `TEXT` NN | `FEMALE`, `MALE`, `UNSPECIFIED` |
| `weight_kg` | `REAL` NN | 2.0–400.0 |
| `height_cm` | `REAL` NULL | not used by policy v1 |
| `activity_level` | `TEXT` NN | `SEDENTARY`, `LIGHT`, `MODERATE`, `VERY_ACTIVE` |
| `climate` | `TEXT` NN | `TEMPERATE`, `WARM`, `HOT_HUMID` |
| `pregnancy_status` | `TEXT` NN | `NONE`, `PREGNANT` |
| `lactating` | `INTEGER` NN | boolean |
| `high_altitude` | `INTEGER` NN | boolean; added to PLAN's field list so the altitude adjustment is recomputable |
| `source` | `TEXT` NN | `MANUAL`, `HEALTH_CONNECT` |
| `created_at` | `INTEGER` NN | epoch ms |

Index: `(effective_from, created_at)`. Rows are append-only, with one exception: a second `MANUAL` save on the same `effective_from` replaces the earlier `MANUAL` row for that day (the repository deletes it in the same transaction), so editing the profile five times in one evening leaves one row. A Health Connect weight import copies the latest snapshot, changes `weight_kg`, and sets `source = HEALTH_CONNECT`.

Resolution for a date `d`: the row with the greatest `(effective_from, created_at)` such that `effective_from <= d`. When the `pinWeight` setting is on, `HEALTH_CONNECT` rows are skipped. If no row has `effective_from <= d` (dates before the first profile) the earliest row is used.

**`daily_goal`** (`DailyGoalEntity`)

| Column | SQL type | Notes |
|---|---|---|
| `date` | `INTEGER` NN, **PK** | epoch day (hydration day) |
| `target_ml` | `INTEGER` NN | the effective goal |
| `calculated_ml` | `INTEGER` NULL | calculator result; NULL when no profile exists (manual-only users) |
| `mode` | `TEXT` NN | `CALCULATED`, `GLOBAL_OVERRIDE`, `DAY_OVERRIDE` |
| `breakdown_json` | `TEXT` NN | serialized `GoalBreakdown` (§7.3); always a full object, with an empty `components` list when there is no profile |
| `day_start_minutes` | `INTEGER` NN | boundary used for this day |
| `policy_version` | `INTEGER` NN | `GoalPolicy.VERSION` used for `calculated_ml` |
| `updated_at` | `INTEGER` NN | epoch ms |

**`day_override`** (`DayOverrideEntity`)

| Column | SQL type | Notes |
|---|---|---|
| `date` | `INTEGER` NN, **PK** | epoch day |
| `target_ml` | `INTEGER` NN | 250–10,000 |
| `reason` | `TEXT` NULL | |
| `updated_at` | `INTEGER` NN | |
| `deleted_at` | `INTEGER` NULL | tombstone (removing an override must survive a merge) |

**`activity_adjustment`** (`ActivityAdjustmentEntity`)

| Column | SQL type | Notes |
|---|---|---|
| `source` | `TEXT` NN, **PK part 1** | `HEALTH_CONNECT`, `MANUAL_PLANNED` |
| `source_record_id` | `TEXT` NN, **PK part 2** | Health Connect record id, or `manual-<uuid>` |
| `date` | `INTEGER` NN | hydration day of the session start |
| `minutes` | `INTEGER` NN | |
| `intensity` | `TEXT` NN | `LIGHT`, `MODERATE`, `VIGOROUS` |
| `extra_ml` | `INTEGER` NN | `minutes × mlPerMinute(intensity)`, before the daily cap |
| `exercise_type` | `INTEGER` NULL | Health Connect `exerciseType` |
| `updated_at` | `INTEGER` NN | |

Index: `(date)`.

**`weather_day`** (`WeatherDayEntity`)

| Column | SQL type | Notes |
|---|---|---|
| `date` | `INTEGER` NN, **PK** | hydration day |
| `lat_e1` | `INTEGER` NN | latitude × 10, rounded (0.1° precision; PLAN's `latRounded`) |
| `lon_e1` | `INTEGER` NN | longitude × 10, rounded |
| `max_temp_c` | `REAL` NN | |
| `max_apparent_temp_c` | `REAL` NN | |
| `mean_rel_humidity` | `REAL` NULL | percent |
| `extra_ml` | `INTEGER` NN | from the §7.2 table at fetch time |
| `fetched_at` | `INTEGER` NN | |

**`health_daily`** (`HealthDailyEntity`) — optional Health Connect signals for correlations (§9.11). Added now so Phase 5 needs no migration.

| Column | SQL type | Notes |
|---|---|---|
| `date` | `INTEGER` NN, **PK** | |
| `steps` | `INTEGER` NULL | |
| `active_kcal` | `REAL` NULL | |
| `updated_at` | `INTEGER` NN | |

**`hc_pending_op`** (`HcPendingOpEntity`) — the Health Connect write queue.

| Column | SQL type | Notes |
|---|---|---|
| `id` | `INTEGER` NN, **PK AUTOINCREMENT** | FIFO order |
| `entry_id` | `TEXT` NN | `intake_entry.id` |
| `op` | `TEXT` NN | `UPSERT`, `DELETE` |
| `enqueued_at` | `INTEGER` NN | |
| `attempts` | `INTEGER` NN default 0 | |
| `last_error` | `TEXT` NULL | |

Index: `(entry_id)`. Enqueueing first deletes older ops for the same `entry_id` (the latest state wins).

**`sync_state`** (`SyncStateEntity`) — device-local key/value state that must change in the same transaction as imported rows. **The Health Connect changes token lives here, not in DataStore** (decision).

| Column | SQL type | Notes |
|---|---|---|
| `key` | `TEXT` NN, **PK** | see below |
| `value` | `TEXT` NN | |
| `updated_at` | `INTEGER` NN | |

Keys: `hc.changes_token`, `hc.token_record_types` (sorted, comma-separated record type names the token covers), `hc.last_sync_at`, `hc.last_full_reconcile_at`. `hc_pending_op` and `sync_state` are excluded from the JSON backup.

### 4.4 DAOs

```kotlin
@Dao
interface IntakeDao {
    @Upsert suspend fun upsert(entry: IntakeEntryEntity)
    @Upsert suspend fun upsertAll(entries: List<IntakeEntryEntity>)

    @Query("SELECT * FROM intake_entry WHERE id = :id")
    suspend fun getById(id: UUID): IntakeEntryEntity?

    @Query("SELECT * FROM intake_entry WHERE hc_record_id = :hcRecordId")
    suspend fun getByHcRecordId(hcRecordId: String): IntakeEntryEntity?

    @Query("SELECT * FROM intake_entry WHERE hydration_day = :day AND deleted_at IS NULL ORDER BY instant ASC")
    fun observeDay(day: LocalDate): Flow<List<IntakeEntryEntity>>

    @Query("SELECT * FROM intake_entry WHERE hydration_day BETWEEN :from AND :to AND deleted_at IS NULL ORDER BY instant ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<IntakeEntryEntity>>

    @Query("SELECT * FROM intake_entry WHERE hydration_day BETWEEN :from AND :to AND deleted_at IS NULL ORDER BY instant ASC")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<IntakeEntryEntity>

    @Query("SELECT * FROM intake_entry WHERE deleted_at IS NULL ORDER BY instant DESC")
    fun pagingSource(): PagingSource<Int, IntakeEntryEntity>   // History list

    @Query("SELECT COALESCE(SUM(volume_ml), 0) FROM intake_entry WHERE hydration_day = :day AND deleted_at IS NULL")
    suspend fun totalForDay(day: LocalDate): Int

    @Query("SELECT MAX(instant) FROM intake_entry WHERE hydration_day = :day AND deleted_at IS NULL")
    suspend fun lastDrinkInstant(day: LocalDate): Instant?

    @Query("UPDATE intake_entry SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: UUID, now: Instant)

    @Query("UPDATE intake_entry SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: UUID, now: Instant)           // Undo

    @Query("UPDATE intake_entry SET hc_record_id = :hcRecordId WHERE id = :id")
    suspend fun setHcRecordId(id: UUID, hcRecordId: String?)   // does not bump updated_at

    @Query("SELECT * FROM intake_entry WHERE hydration_day >= :fromDay")
    suspend fun entriesFromDay(fromDay: LocalDate): List<IntakeEntryEntity>   // includes tombstones

    @Query("UPDATE intake_entry SET hydration_day = :day WHERE id = :id")
    suspend fun updateHydrationDay(id: UUID, day: LocalDate)

    @Query("SELECT * FROM intake_entry WHERE source = 'HEALTH_CONNECT_IMPORT' AND instant BETWEEN :from AND :to")
    suspend fun importsBetween(from: Instant, to: Instant): List<IntakeEntryEntity>

    @Query("SELECT * FROM intake_entry WHERE source = 'HEALTH_CONNECT_IMPORT' AND hc_data_origin = :packageName AND deleted_at IS NULL AND hc_adopted = 0")
    suspend fun liveImportsFrom(packageName: String): List<IntakeEntryEntity>

    @Query("SELECT * FROM intake_entry")                     // backup: everything incl. tombstones
    suspend fun getAllIncludingDeleted(): List<IntakeEntryEntity>

    @Query("SELECT COUNT(*) FROM intake_entry WHERE deleted_at IS NULL")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM intake_entry")
    suspend fun deleteAll()                                  // restore (replace) only
}

@Dao
interface DailyTotalDao {
    @Query("SELECT * FROM daily_total WHERE date = :day")
    fun observeDay(day: LocalDate): Flow<DailyTotalView?>

    @Query("SELECT * FROM daily_total WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DailyTotalView>>

    @Query("SELECT * FROM daily_total WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<DailyTotalView>

    @Query("SELECT MIN(date) FROM daily_total")
    suspend fun firstDay(): LocalDate?
}

@Dao
interface ContainerDao {
    @Query("SELECT * FROM container WHERE deleted_at IS NULL ORDER BY sort_order ASC, name ASC")
    fun observeAll(): Flow<List<ContainerEntity>>

    @Query("SELECT * FROM container WHERE deleted_at IS NULL ORDER BY sort_order ASC, name ASC")
    suspend fun getAll(): List<ContainerEntity>

    @Query("SELECT * FROM container WHERE id = :id")
    suspend fun getById(id: UUID): ContainerEntity?

    @Query("SELECT COUNT(*) FROM container")
    suspend fun countIncludingDeleted(): Int

    @Upsert suspend fun upsert(container: ContainerEntity)
    @Upsert suspend fun upsertAll(containers: List<ContainerEntity>)

    @Query("UPDATE container SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: UUID, now: Instant)

    @Query("SELECT * FROM container")
    suspend fun getAllIncludingDeleted(): List<ContainerEntity>

    @Query("DELETE FROM container") suspend fun deleteAll()
}

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(snapshot: ProfileSnapshotEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(snapshots: List<ProfileSnapshotEntity>)

    @Query("DELETE FROM profile_snapshot WHERE effective_from = :day AND source = 'MANUAL'")
    suspend fun deleteManualOn(day: LocalDate)

    @Query("SELECT * FROM profile_snapshot ORDER BY effective_from DESC, created_at DESC LIMIT 1")
    fun observeLatest(): Flow<ProfileSnapshotEntity?>

    @Query("SELECT * FROM profile_snapshot ORDER BY effective_from ASC, created_at ASC")
    fun observeAll(): Flow<List<ProfileSnapshotEntity>>

    @Query("SELECT * FROM profile_snapshot ORDER BY effective_from ASC, created_at ASC")
    suspend fun getAll(): List<ProfileSnapshotEntity>        // resolution for a date is done in Kotlin (ProfileResolver)

    @Query("DELETE FROM profile_snapshot") suspend fun deleteAll()
}

@Dao
interface GoalDao {
    @Upsert suspend fun upsertGoal(goal: DailyGoalEntity)
    @Upsert suspend fun upsertGoals(goals: List<DailyGoalEntity>)

    @Query("SELECT * FROM daily_goal WHERE date = :day")
    fun observeGoal(day: LocalDate): Flow<DailyGoalEntity?>

    @Query("SELECT * FROM daily_goal WHERE date = :day")
    suspend fun getGoal(day: LocalDate): DailyGoalEntity?

    @Query("SELECT * FROM daily_goal WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeGoals(from: LocalDate, to: LocalDate): Flow<List<DailyGoalEntity>>

    @Query("SELECT * FROM daily_goal WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getGoals(from: LocalDate, to: LocalDate): List<DailyGoalEntity>

    @Query("SELECT * FROM daily_goal") suspend fun getAllGoals(): List<DailyGoalEntity>
    @Query("DELETE FROM daily_goal") suspend fun deleteAllGoals()

    @Upsert suspend fun upsertOverride(override: DayOverrideEntity)
    @Upsert suspend fun upsertOverrides(overrides: List<DayOverrideEntity>)

    @Query("SELECT * FROM day_override WHERE date = :day AND deleted_at IS NULL")
    fun observeOverride(day: LocalDate): Flow<DayOverrideEntity?>

    @Query("SELECT * FROM day_override WHERE date = :day AND deleted_at IS NULL")
    suspend fun getOverride(day: LocalDate): DayOverrideEntity?

    @Query("UPDATE day_override SET deleted_at = :now, updated_at = :now WHERE date = :day")
    suspend fun softDeleteOverride(day: LocalDate, now: Instant)

    @Query("SELECT * FROM day_override") suspend fun getAllOverridesIncludingDeleted(): List<DayOverrideEntity>
    @Query("DELETE FROM day_override") suspend fun deleteAllOverrides()
}

@Dao
interface ActivityDao {
    @Upsert suspend fun upsert(adjustment: ActivityAdjustmentEntity)
    @Upsert suspend fun upsertAll(adjustments: List<ActivityAdjustmentEntity>)

    @Query("DELETE FROM activity_adjustment WHERE source = :source AND source_record_id = :sourceRecordId")
    suspend fun delete(source: String, sourceRecordId: String)

    @Query("SELECT * FROM activity_adjustment WHERE date = :day ORDER BY source_record_id ASC")
    fun observeDay(day: LocalDate): Flow<List<ActivityAdjustmentEntity>>

    @Query("SELECT * FROM activity_adjustment WHERE date = :day ORDER BY source_record_id ASC")
    suspend fun getDay(day: LocalDate): List<ActivityAdjustmentEntity>

    @Query("SELECT date, SUM(minutes) AS minutes FROM activity_adjustment WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date ASC")
    suspend fun minutesPerDay(from: LocalDate, to: LocalDate): List<DateMinutes>   // data class DateMinutes(date: LocalDate, minutes: Int)

    @Query("SELECT * FROM activity_adjustment") suspend fun getAll(): List<ActivityAdjustmentEntity>
    @Query("DELETE FROM activity_adjustment") suspend fun deleteAll()
}

@Dao
interface WeatherDao {
    @Upsert suspend fun upsert(day: WeatherDayEntity)
    @Upsert suspend fun upsertAll(days: List<WeatherDayEntity>)

    @Query("SELECT * FROM weather_day WHERE date = :day") fun observeDay(day: LocalDate): Flow<WeatherDayEntity?>
    @Query("SELECT * FROM weather_day WHERE date = :day") suspend fun getDay(day: LocalDate): WeatherDayEntity?

    @Query("SELECT * FROM weather_day WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<WeatherDayEntity>

    @Query("SELECT * FROM weather_day") suspend fun getAll(): List<WeatherDayEntity>
    @Query("DELETE FROM weather_day") suspend fun deleteAll()
}

@Dao
interface HealthDailyDao {
    @Upsert suspend fun upsertAll(days: List<HealthDailyEntity>)

    @Query("SELECT * FROM health_daily WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<HealthDailyEntity>

    @Query("SELECT * FROM health_daily") suspend fun getAll(): List<HealthDailyEntity>
    @Query("DELETE FROM health_daily") suspend fun deleteAll()
}

@Dao
interface HcSyncDao {
    @Insert suspend fun insertOp(op: HcPendingOpEntity): Long

    @Query("DELETE FROM hc_pending_op WHERE entry_id = :entryId")
    suspend fun deleteOpsFor(entryId: UUID)

    @Transaction
    suspend fun enqueue(op: HcPendingOpEntity) { deleteOpsFor(op.entryId); insertOp(op) }

    @Query("SELECT * FROM hc_pending_op ORDER BY id ASC LIMIT :limit")
    suspend fun nextOps(limit: Int): List<HcPendingOpEntity>

    @Query("DELETE FROM hc_pending_op WHERE id IN (:ids)")
    suspend fun deleteOps(ids: List<Long>)

    @Query("UPDATE hc_pending_op SET attempts = attempts + 1, last_error = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("SELECT COUNT(*) FROM hc_pending_op") fun observePendingCount(): Flow<Int>
    @Query("DELETE FROM hc_pending_op") suspend fun clearOps()

    @Query("SELECT value FROM sync_state WHERE `key` = :key") suspend fun getState(key: String): String?
    @Upsert suspend fun putState(state: SyncStateEntity)
    @Query("DELETE FROM sync_state WHERE `key` = :key") suspend fun deleteState(key: String)
}
```

`PagingSource` needs `androidx.room:room-paging` and `androidx.paging:paging-compose` (both in the catalog).

Multi-table writes go through `DhmoDatabase.withTransaction { }` (`androidx.room.withTransaction`) in the repositories; `:core:database` exposes a small `TransactionRunner` interface so `:core:data` can be unit-tested with fakes:

```kotlin
interface TransactionRunner { suspend fun <T> run(block: suspend () -> T): T }
```

### 4.5 Schema export and migration tests

- `build-logic` applies the `androidx.room` Gradle plugin and sets `room { schemaDirectory("$projectDir/schemas") }`. KSP option `room.generateKotlin=true`.
- `core/database/schemas/**` is committed. CI fails when a build changes a schema file without a version bump, through `git diff --exit-code core/database/schemas` after `:core:database:kspDebugKotlin`.
- **Instrumented migration test** (`core/database/src/androidTest/.../MigrationTest.kt`): the scaffold uses `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DhmoDatabase::class.java)`. `androidTest` assets include the schema directory (`android.sourceSets["androidTest"].assets.directories += "$projectDir/schemas"`). With only v1 the test creates the v1 database, inserts one row per table with raw SQL, then opens it through Room so the identity hash is validated. Each later version adds `migrate1To2()` and so on, plus a `migrateAll()` test that walks from 1 to the current version.
- **JVM schema sanity test** (`core/database/src/test/.../SchemaFileTest.kt`, plain JUnit, no Robolectric): parses `schemas/<fqcn>/*.json` with kotlinx.serialization and asserts that (a) the highest file version equals `DhmoDatabase.VERSION`, (b) the files are consecutive from 1, and (c) v1 contains exactly the tables `intake_entry, container, profile_snapshot, daily_goal, day_override, activity_adjustment, weather_day, health_daily, hc_pending_op, sync_state` and the view `daily_total`. The schema directory is passed with `tasks.withType<Test> { systemProperty("dhmo.schemaDir", "$projectDir/schemas") }`.
- **DAO tests** (`core/database/src/test`, Robolectric with an in-memory database): one test class per DAO, plus `DailyTotalViewTest`, which checks grouping, tombstone exclusion and the first/last timestamps.

---

## 5. Settings: DataStore schema (`:core:datastore`, types in `:core:model`)

### 5.1 Decision: typed `DataStore<T>` with kotlinx.serialization JSON, not protobuf

PLAN §2 says "Proto DataStore" in one row and "kotlinx.serialization: backup format and DataStore" in another. This document resolves it as **`DataStore<AppSettings>` with a kotlinx.serialization `Serializer`, stored as JSON**. Reasons:

- The settings classes are the same classes embedded in the backup file (§11), so there is one schema and one serializer, with no proto-to-DTO mapping layer.
- No protobuf Gradle plugin or `protoc` binary in the build. That plugin is one more thing that must keep pace with AGP 9 and Gradle 9.
- Evolution is handled with default values plus `ignoreUnknownKeys = true`, and an explicit `schemaVersion` for anything that needs a real migration.
- The file is small (under 4 KB) and is read once into a `StateFlow`, so the binary size advantage of protobuf is irrelevant.

It is still a typed, transactional, single-file DataStore (the property people mean by "Proto DataStore"); only the wire format differs.

There are **two stores**, so that the backup can include one wholesale and never see the other:

| Store | File | Type | In backup | Contents |
|---|---|---|---|---|
| User settings | `files/datastore/settings.json` | `AppSettings` | yes | everything the user chose |
| Device state | `files/datastore/device_state.json` | `DeviceState` | **no** | onboarding progress, cached location, reminder runtime state, backup account and status, wrapped encryption key |

Both use `Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }`, and `ReplaceFileCorruptionHandler { default }`. `device_state.json` is excluded from Android Auto Backup in `data_extraction_rules.xml`; `settings.json` and `dhmo.db` are included.

### 5.2 `AppSettings` (version 1)

All classes are in `:core:model`, package `casa.peterson.dhmo.core.model.settings`, and are `@Serializable`. Times of day are minutes after midnight. Field names are the JSON keys; never rename one, only add.

```kotlin
@Serializable
data class AppSettings(
    val schemaVersion: Int = 1,
    val units: VolumeUnit = VolumeUnit.ML,              // first run: US_FL_OZ when Locale is en-US
    val dayStartMinutes: Int = 0,                       // 0..1439
    val theme: ThemeSettings = ThemeSettings(),
    val goal: GoalSettings = GoalSettings(),
    val reminders: ReminderSettings = ReminderSettings(),
    val backup: BackupSettings = BackupSettings(),
    val healthConnect: HealthConnectSettings = HealthConnectSettings(),
    val weather: WeatherSettings = WeatherSettings(),
    val stats: StatsSettings = StatsSettings(),
)

@Serializable enum class VolumeUnit { ML, L, US_FL_OZ, IMP_FL_OZ }

@Serializable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,             // SYSTEM, LIGHT, DARK
    val dynamicColor: Boolean = true,                   // Android 12+; ignored below
)
@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class GoalSettings(
    val globalOverride: GlobalOverride = GlobalOverride(),
    val adjustments: AdjustmentToggles = AdjustmentToggles(),
    val pinned: PinnedInputs = PinnedInputs(),
)

@Serializable
data class GlobalOverride(
    val enabled: Boolean = false,
    val targetMl: Int = 2000,                           // 250..10000
    /** Keys are java.time.DayOfWeek names ("MONDAY".."SUNDAY"). A missing key falls back to targetMl. */
    val perWeekdayMl: Map<String, Int> = emptyMap(),
)

@Serializable
data class AdjustmentToggles(
    val activity: Boolean = true,
    val climate: Boolean = true,                        // covers both the weather table and the manual climate setting
    val pregnancyLactation: Boolean = true,
    val altitude: Boolean = true,
)

@Serializable
data class PinnedInputs(
    val pinWeight: Boolean = false,                     // true: ignore Health Connect weight snapshots
    val pinActivityLevel: Boolean = false,              // true: always use the profile's activity level, never measured minutes
)

@Serializable
data class ReminderSettings(
    val enabled: Boolean = false,                       // turned on by onboarding once POST_NOTIFICATIONS is granted
    val wakeMinutes: Int = 7 * 60,                      // 07:00
    val sleepMinutes: Int = 23 * 60,                    // 23:00; may be <= wakeMinutes (sleep after midnight)
    val intervalFloorMinutes: Int = 60,                 // 15..240
    val tolerancePercent: Int = 10,                     // 0..50, percent of the day's goal
    val curve: PaceCurveSettings = PaceCurveSettings(),
    val escalationEnabled: Boolean = false,
    val calendarBusyEnabled: Boolean = false,           // needs READ_CALENDAR
    val endOfDaySummaryEnabled: Boolean = false,
    val snoozeMinutes: Int = 30,
)

@Serializable
data class PaceCurveSettings(
    val type: PaceCurveType = PaceCurveType.DEFAULT,    // DEFAULT, LINEAR, CUSTOM
    /** Used only when type == CUSTOM. Validated by PaceCurve.validate (§8.2). */
    val customPoints: List<CurvePoint> = emptyList(),
)
@Serializable enum class PaceCurveType { DEFAULT, LINEAR, CUSTOM }
@Serializable data class CurvePoint(val xPermille: Int, val yPermille: Int)

@Serializable
data class BackupSettings(
    val driveEnabled: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.DAILY,   // DAILY, WEEKLY
    val unmeteredOnly: Boolean = true,
    val encryptionEnabled: Boolean = false,             // the key itself is in DeviceState
    val keepDaily: Int = 7,
    val keepWeekly: Int = 4,
    val keepMonthly: Int = 6,
)
@Serializable enum class BackupFrequency { DAILY, WEEKLY }

@Serializable
data class HealthConnectSettings(
    val writeHydration: Boolean = false,
    val importHydration: Boolean = false,               // "Count water logged in other apps"; off by default
    /** Package name -> included. Apps are added (as true) the first time a record from them is seen. */
    val importSources: Map<String, Boolean> = emptyMap(),
    val syncWeight: Boolean = false,
    val useExercise: Boolean = false,
    val readStepsAndCalories: Boolean = false,          // correlations only
    val backgroundSync: Boolean = false,
    val importHistory: Boolean = false,                 // READ_HEALTH_DATA_HISTORY
)

@Serializable
data class WeatherSettings(
    val mode: WeatherMode = WeatherMode.OFF,            // OFF, DEVICE_LOCATION, MANUAL_CITY
    val manualCity: ManualCity? = null,
)
@Serializable enum class WeatherMode { OFF, DEVICE_LOCATION, MANUAL_CITY }

@Serializable
data class ManualCity(
    val name: String,                                   // e.g. "Berlin"
    val admin1: String? = null,                         // e.g. "State of Berlin"
    val countryCode: String? = null,                    // ISO 3166-1 alpha-2
    val latE1: Int,                                     // latitude × 10, already rounded to 0.1°
    val lonE1: Int,
)

@Serializable
data class StatsSettings(
    val includeNoLogDays: Boolean = false,              // PLAN §7.2: excluded from averages by default
    val weeklyStreakFreeze: Boolean = false,
)
```

### 5.3 `DeviceState` (version 1, never backed up)

```kotlin
@Serializable
data class DeviceState(
    val schemaVersion: Int = 1,
    val onboarding: OnboardingState = OnboardingState(),
    val cachedLocation: CachedLocation? = null,         // DEVICE_LOCATION mode
    val reminderRuntime: ReminderRuntime = ReminderRuntime(),
    val backupRuntime: BackupRuntime = BackupRuntime(),
    val weatherRuntime: WeatherRuntime = WeatherRuntime(),
)

@Serializable
data class OnboardingState(
    val completed: Boolean = false,
    val disclaimerAcceptedAtMs: Long? = null,
    val manualModeRecommended: Boolean = false,         // user ticked "I have a condition / fluid restriction"
    val notificationRationaleShown: Boolean = false,
    val lastStep: String? = null,                       // route name, to resume after process death
)

@Serializable
data class CachedLocation(val latE1: Int, val lonE1: Int, val obtainedAtMs: Long)   // already rounded to 0.1°

@Serializable
data class ReminderRuntime(
    val scheduledAtMs: Long? = null,                    // what the pending alarm was set for
    val scheduledKind: String? = null,                  // NUDGE, SUMMARY, RECHECK
    val lastReminderAtMs: Long? = null,
    val snoozedUntilMs: Long? = null,
    val escalationSentDay: Long? = null,                // epoch day
    val summarySentDay: Long? = null,                   // epoch day
    val missedAlarmTimesMs: List<Long> = emptyList(),   // last 10 late deliveries (§8.9)
    val batteryHintDismissed: Boolean = false,
)

@Serializable
data class BackupRuntime(
    val accountEmail: String? = null,
    val lastSuccessAtMs: Long? = null,
    val lastErrorAtMs: Long? = null,
    val lastError: String? = null,                      // BackupError enum name
    val needsReconnect: Boolean = false,
    val wrappedKeyB64: String? = null,                  // backup key wrapped by an Android Keystore key (§11.4)
    val kdfSaltB64: String? = null,
)

@Serializable
data class WeatherRuntime(val lastFetchAtMs: Long? = null, val lastFetchFailed: Boolean = false)
```

### 5.4 Access API (`:core:datastore`)

```kotlin
interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings
}
interface DeviceStateStore {
    val state: Flow<DeviceState>
    suspend fun update(transform: (DeviceState) -> DeviceState): DeviceState
}
```

Implementations wrap `DataStoreFactory.create(serializer, corruptionHandler, migrations, scope, produceFile)`. `AppSettingsSerializer.readFrom` decodes, then runs `SettingsMigrations.migrate(decoded)` (a `when` on `schemaVersion`; identity for v1), then `SettingsValidator.sanitize` (clamps every numeric field to the range in the comments above). Tests: `AppSettingsSerializerTest` (round trip, defaults from `{}`, unknown keys ignored, corrupt file falls back to defaults), `SettingsValidatorTest`.

---

## 6. Data layer, use cases and dependency injection

### 6.1 Layering

```
feature / widget / reminders / sync        (Android, UI or platform glue)
        │  depend on interfaces only
        ▼
:core:data   repositories + controller interfaces + Android use cases     (Android library)
        │
        ├── :core:database   Room
        ├── :core:datastore  DataStore
        ├── :core:backup     backup document, merge, CSV     (pure Kotlin)
        ├── :core:domain     calculators, pace, stats        (pure Kotlin)
        └── :core:model      domain types, settings types    (pure Kotlin)
```

Rules:

- Feature modules never import `:core:database`, `:core:datastore` or any `:sync:*` module. They see domain models and the interfaces in `:core:data`.
- `:core:domain` contains only pure functions and classes. It has no `Flow`-returning repositories, no `javax.inject` annotations and no Android types. Use cases that orchestrate I/O live in `:core:data` (package `…core.data.usecase`) because they need repositories.
- Everything that reads the clock takes a `java.time.Clock` (or `InstantSource`) from Hilt; tests pass a fixed clock. `ZoneId` is always read through `ZoneProvider { fun current(): ZoneId }`.

### 6.2 Repository and controller interfaces (`:core:data`, package `…core.data.repository`)

T0 declares all of these with fully working implementations for the Room- and DataStore-backed repositories, and no-op implementations for the controllers that later phases fill in.

```kotlin
interface IntakeRepository {
    fun observeDay(day: LocalDate): Flow<List<IntakeEntry>>
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<IntakeEntry>>
    fun pagedHistory(): Flow<PagingData<IntakeEntry>>
    fun observeDailyTotal(day: LocalDate): Flow<DailyTotal?>
    fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DailyTotal>>
    suspend fun dailyTotals(from: LocalDate, to: LocalDate): List<DailyTotal>
    suspend fun entries(from: LocalDate, to: LocalDate): List<IntakeEntry>
    suspend fun firstDay(): LocalDate?
    suspend fun get(id: UUID): IntakeEntry?
    suspend fun add(volumeMl: Int, source: Source, at: Instant? = null, note: String? = null): IntakeEntry
    suspend fun update(id: UUID, volumeMl: Int, at: Instant, note: String?): IntakeEntry
    suspend fun delete(id: UUID)
    suspend fun undoDelete(id: UUID)
    suspend fun adopt(id: UUID)                               // Health Connect import → editable (§10.6)
    suspend fun rebucketFrom(oldToday: LocalDate, newDayStart: LocalTime)   // §4.2 rule 2
    suspend fun notifyExternalChange(days: Set<LocalDate>, origin: ChangeOrigin)   // called by the Health Connect engine and by restore after they write through DAOs
}

interface ContainerRepository {
    fun observeAll(): Flow<List<Container>>
    suspend fun getAll(): List<Container>
    suspend fun upsert(container: Container)
    suspend fun delete(id: UUID)
    suspend fun reorder(idsInOrder: List<UUID>)
    suspend fun seedDefaultsIfEmpty(units: VolumeUnit)
}

interface ProfileRepository {
    fun observeCurrent(): Flow<Profile?>
    fun observeSnapshots(): Flow<List<ProfileSnapshot>>
    suspend fun profileFor(day: LocalDate): ProfileSnapshot?   // applies the resolution and pinWeight rules of §4.3
    suspend fun save(profile: Profile, effectiveFrom: LocalDate)        // source = MANUAL
    suspend fun addHealthConnectWeight(weightKg: Double, effectiveFrom: LocalDate)
}

interface GoalRepository {
    fun observeGoal(day: LocalDate): Flow<DailyGoal?>
    fun observeGoals(from: LocalDate, to: LocalDate): Flow<List<DailyGoal>>
    suspend fun goals(from: LocalDate, to: LocalDate): List<DailyGoal>
    suspend fun goal(day: LocalDate): DailyGoal?
    suspend fun saveSnapshot(goal: DailyGoal)
    fun observeDayOverride(day: LocalDate): Flow<DayOverride?>
    suspend fun setDayOverride(day: LocalDate, targetMl: Int, reason: String?)
    suspend fun clearDayOverride(day: LocalDate)
}

interface ActivityRepository {
    fun observeDay(day: LocalDate): Flow<List<ActivityAdjustment>>
    suspend fun forDay(day: LocalDate): List<ActivityAdjustment>
    suspend fun minutesPerDay(from: LocalDate, to: LocalDate): Map<LocalDate, Int>
    suspend fun addPlanned(day: LocalDate, minutes: Int, intensity: Intensity)
    suspend fun removePlanned(sourceRecordId: String)
}

interface WeatherRepository {
    fun observeDay(day: LocalDate): Flow<WeatherDay?>
    suspend fun forDay(day: LocalDate): WeatherDay?
    suspend fun range(from: LocalDate, to: LocalDate): List<WeatherDay>
    suspend fun save(day: WeatherDay)
}

interface SettingsRepository {                                 // thin facade over SettingsStore
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
interface DeviceStateRepository {
    val state: Flow<DeviceState>
    suspend fun current(): DeviceState
    suspend fun update(transform: (DeviceState) -> DeviceState)
}

interface BackupRepository {                                   // Phase 3
    suspend fun exportDocument(): BackupDocument
    suspend fun restore(doc: BackupDocument, mode: RestoreMode)
    suspend fun writeSafetySnapshot(): File
    suspend fun latestSafetySnapshot(): File?
}

// Controllers: implemented outside :core:data, bound with Hilt. T0 ships no-op implementations in the owning module.
interface ReminderScheduler {                                  // impl: :core:reminders (Phase 2)
    suspend fun reschedule(reason: RescheduleReason)
    suspend fun cancel()
    suspend fun snooze()
}
interface WidgetUpdater { suspend fun updateAll() }             // impl: :widget (Phase 6)
interface HealthConnectController {                             // impl: :sync:healthconnect (Phase 5)
    val state: Flow<HcSyncState>
    fun availability(): HcAvailability
    fun permissionsFor(setting: HcSetting): Set<String>
    suspend fun requestSync(immediate: Boolean)
    suspend fun enqueueBackfill()
    suspend fun onSettingsChanged()
}
interface DriveBackupController {                               // impl: :sync:drive (Phase 4)
    val state: Flow<DriveBackupState>                           // NotConfigured, SignedOut, Ready(lastSuccessAt), Running, NeedsReconnect, Error(BackupError)
    suspend fun backUpNow()
    suspend fun listBackups(): Result<List<RemoteBackup>>
    suspend fun restore(backup: RemoteBackup, mode: RestoreMode, passphrase: CharArray?): Result<Unit>
    suspend fun onSettingsChanged()                             // (re)schedule or cancel the periodic work
    suspend fun disconnect()
}
interface WeatherController {                                   // impl: :core:weather (Phase 5)
    suspend fun refreshIfStale()
    suspend fun refreshNow(): WeatherResult<WeatherDay>
    suspend fun onSettingsChanged()
}
interface WeatherProvider { /* §12.1 */ }
```

Activity-bound flows (Google sign-in, Health Connect permission requests, the location permission) cannot go through these interfaces because they need an `ActivityResultLauncher`. The owning module exposes a composable instead, and `:app` passes it into the settings graph as a slot (§13.4).

### 6.3 Use cases (`:core:data`, package `…core.data.usecase`)

Each is a class with `suspend operator fun invoke(...)` (or returning a `Flow`), constructor-injected.

| Use case | What it does | Phase |
|---|---|---|
| `LogIntakeUseCase(volumeMl, source, at?, note?)` | validate (1–10,000 ml), `IntakeRepository.add` | 1 |
| `EditIntakeUseCase`, `DeleteIntakeUseCase`, `UndoDeleteIntakeUseCase` | as named; imports are rejected unless adopted | 1 |
| `ObserveTodayUseCase` | combines the current hydration day (re-emitted at each day boundary by `DayTicker`), entries, total, goal, pace → `TodaySnapshot` | 1 (pace added in 2) |
| `BuildGoalInputUseCase(day)` | resolves the profile, activity input and climate input for a day, applying the pinned inputs (§7.4) | 1 |
| `SnapshotDailyGoalUseCase(day)` | calculator + resolver → `GoalRepository.saveSnapshot`; no write when nothing changed; then `ReminderScheduler.reschedule(GOAL_CHANGED)` if the target changed | 1 |
| `BackfillDailyGoalsUseCase` | writes missing snapshots between the last snapshot and today (§7.4) | 1 |
| `SaveProfileUseCase`, `SetGlobalOverrideUseCase`, `SetDayOverrideUseCase`, `ClearDayOverrideUseCase` | write, then snapshot today | 1 |
| `ChangeDayStartUseCase(newMinutes)` | update the setting, `rebucketFrom`, re-snapshot the affected days, reschedule | 1 |
| `CompleteOnboardingUseCase` | seed containers, save the profile or the manual goal, mark completed | 1 |
| `BuildReminderStateUseCase` | §8.6 | 2 |
| `LoadStatsRangeUseCase(from, to)` | `buildDayStats` from totals and goal snapshots | 3 |
| `ObserveInsightsUseCase` | assembles `InsightInput`, calls `buildInsights` | 3 |
| `ExportCsvUseCase(kind, outputStream)`, `ExportJsonUseCase(outputStream)` | `:core:backup` writers | 3 |
| `ImportBackupFileUseCase(inputStream, mode, passphrase?)` | decode + `BackupRepository.restore` | 3 |

`DayTicker` (`:core:data`): `fun today(): Flow<LocalDate>` emits the current hydration day and re-emits when the day boundary passes, when the day start setting changes, and on `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` (a runtime-registered receiver while collected).

### 6.4 Entry change fan-out

Every successful write in `IntakeRepository` (add, update, delete, undo, adopt, import, restore) does, after the transaction commits:

```kotlin
interface EntryChangeListener { suspend fun onEntriesChanged(change: EntryChange) }
data class EntryChange(val affectedDays: Set<LocalDate>, val origin: ChangeOrigin)   // LOCAL, HEALTH_CONNECT, RESTORE
```

`OfflineIntakeRepository` gets `Set<@JvmSuppressWildcards EntryChangeListener>` from Hilt multibindings and calls each listener inside `supervisorScope`, so one failing listener cannot break logging. `:core:data` declares `@Multibinds abstract fun listeners(): Set<EntryChangeListener>` so the empty set works in T0. Later modules contribute with `@Binds @IntoSet`:

| Listener | Module | Action |
|---|---|---|
| `GoalSnapshotListener` | `:core:data` | nothing for plain entries; present for symmetry with activity and weather changes |
| `ReminderEntryListener` | `:core:reminders` | `reschedule(ENTRY_CHANGED)` |
| `WidgetEntryListener` | `:widget` | `updateAll()` |
| `HcEntryListener` | `:sync:healthconnect` | enqueue `hc-sync` (skipped when `origin == HEALTH_CONNECT`) |

Inside the same transaction as the entry write, the repository also inserts the `hc_pending_op` row when `settings.healthConnect.writeHydration` is on and the entry is not an import. The queue table is in `:core:database`, so this needs no dependency on the sync module.

### 6.5 Hilt modules

| Module (file) | Gradle module | Provides |
|---|---|---|
| `DatabaseModule` | `:core:database` | `DhmoDatabase` (singleton), each DAO, `TransactionRunner` |
| `DataStoreModule` | `:core:datastore` | `SettingsStore`, `DeviceStateStore` (singletons) |
| `CommonModule` | `:core:data` | `Clock` (`Clock.systemUTC()`), `ZoneProvider`, `@Dispatcher(IO)` / `@Dispatcher(Default)` `CoroutineDispatcher`, `@ApplicationScope CoroutineScope` (`SupervisorJob() + Default`) |
| `RepositoryModule` | `:core:data` | `@Binds` every repository interface to its `Offline*` implementation; `@Multibinds` for `EntryChangeListener` |
| `DomainModule` | `:core:data` | `GoalPolicy` (`GoalPolicyV1`), `GoalCalculator` |
| `RemindersModule` | `:core:reminders` | `@Binds ReminderScheduler`; `@IntoSet ReminderEntryListener` |
| `WidgetModule` | `:widget` | `@Binds WidgetUpdater`; `@IntoSet WidgetEntryListener` |
| `HealthConnectModule` | `:sync:healthconnect` | `@Binds HealthConnectController`; `HealthConnectClient?` provider; `@IntoSet HcEntryListener` |
| `DriveModule` | `:sync:drive` | `@Binds DriveBackupController`; `DriveApi` |
| `WeatherModule` | `:core:weather` | `@Binds WeatherController`, `@Binds WeatherProvider` |
| `NetworkModule` | `:core:network` | `OkHttpClient` (singleton; 15 s connect, 30 s read; no logging interceptor in release), shared `Json` |
| `WorkManagerInitializer` | `:app` | `Configuration.Provider` with `HiltWorkerFactory` (the default initializer is removed from the manifest) |

`:core:common` is an Android-free JVM module that only declares types: the `@Dispatcher` and `@ApplicationScope` qualifier annotations (with `javax.inject:javax.inject:1`), `DhmoDispatcher`, `ZoneProvider`, and small `Result` helpers. No annotation processing runs in the pure JVM modules.

All modules install in `SingletonComponent`. View models are `@HiltViewModel`. Tests replace modules with `@TestInstallIn` fakes from `:core:testing`.

---

## 7. GoalPolicy v1 (`:core:domain`, package `…core.domain.goal`)

### 7.1 Sources

- **EFSA (2010)**, *Scientific Opinion on Dietary Reference Values for water*, EFSA Journal 8(3):1459. Adequate intakes (AI) of **total** water: 6–12 months 800–1,000 ml; 1–2 years 1,100–1,200 ml; 2–3 years 1,300 ml; 4–8 years 1,600 ml; 9–13 years 2,100 ml (boys) and 1,900 ml (girls); 14 years and over 2,500 ml (male) and 2,000 ml (female); pregnancy +300 ml; lactation +700 ml.
- **US National Academies (2004)**, *Dietary Reference Intakes for Water, Potassium, Sodium, Chloride, and Sulfate*: about 20% of total water comes from food. This is the basis for the ×0.8 "drinks only" factor.
- **ACSM (2007)**, *Exercise and Fluid Replacement* position stand: basis for the per-minute exercise allowance.

The per-kilogram factors and the activity, climate and altitude constants are the product values fixed in PLAN §4.2. They are guidance, not clinical values, and the app says so.

### 7.2 Constants

```kotlin
interface GoalPolicy {
    val version: Int
    // …one property per row below
}
object GoalPolicyV1 : GoalPolicy { override val version = 1 /* … */ }
```

**Children (age < 14): age-band table, drinks only = EFSA total AI × 0.8.** Weight is not used.

| Age (completed years) | EFSA total AI used | Female | Male | Unspecified |
|---|---|---|---|---|
| 0 | 900 ml (midpoint of 800–1,000) | 720 | 720 | 720 |
| 1 | 1,150 ml (midpoint of 1,100–1,200) | 920 | 920 | 920 |
| 2–3 | 1,300 ml | 1,040 | 1,040 | 1,040 |
| 4–8 | 1,600 ml | 1,280 | 1,280 | 1,280 |
| 9–13 | 1,900 ml (girls) / 2,100 ml (boys) | 1,520 | 1,680 | 1,600 |

**Age 14 and over: `baseline = max(weightBaseMl, sexFloorMl)`.**

| Constant | Value |
|---|---|
| `perKgFactor(age)` | 14–30 → **35** ml/kg; 31–55 → **33**; 56–65 → **30**; 66 and over → **28** |
| `sexFloorMl` | female **1,600**; male **2,000**; unspecified **1,800** (mean of the two) |
| `weightBaseMl` | `hg` = weight in units of 0.1 kg, rounded half up with `BigDecimal.valueOf(weightKg).movePointRight(1).setScale(0, RoundingMode.HALF_UP).intValueExact()`, then `(hg × factor + 5) / 10` in integer arithmetic. This is "round half up to a whole ml" with no floating-point edge cases. |

**Adjustments** (each is one breakdown component):

| Component | Value |
|---|---|
| Pregnancy | **+300** ml when `pregnancyStatus == PREGNANT` |
| Lactation | **+700** ml when `lactating` (independent of pregnancy; both may apply) |
| Activity, by level | sedentary **0**, light **+250**, moderate **+500**, very active **+750** |
| Activity, by minutes | Σ over sessions of `minutes × mlPerMinute(intensity)`: light **6**, moderate **12**, vigorous **18** ml/min (12 ml/min ≈ 350 ml per 30 min; ×0.5 and ×1.5 for the other intensities). The day's sum is capped at **2,000** ml (`activityMinutesCapMl`). |
| Climate, manual | temperate **0**, warm **+250**, hot or humid **+500** |
| Climate, forecast | by maximum apparent temperature `t` in °C (a `Double`, compared exactly): `t < 25.0` → **0**; `25.0 ≤ t < 30.0` → **+250**; `30.0 ≤ t < 35.0` → **+500**; `t ≥ 35.0` → **+750** |
| Altitude | **+250** ml when `highAltitude` (the profile switch reads "I live above about 2,500 m") |

**Which activity rule applies.** `ActivityInput.Sessions` is used when the day has a measured or planned source: the "use exercise from Health Connect" setting is on with permission granted, or the user entered planned exercise for that day (`activity_adjustment` rows with source `MANUAL_PLANNED`). It is used even when the list is empty, so a rest day gets +0. Otherwise, or when `pinActivityLevel` is on, `ActivityInput.Level` is used. The two are never added together.

**Which climate rule applies.** `ClimateInput.Forecast` when weather mode is not `OFF` and a `weather_day` row exists for the date; otherwise `ClimateInput.Manual` with the profile's climate. When the forecast was wanted but is missing, the component carries `detail["fallback"] = "true"` so the goal screen can say so (PLAN §4.4).

**Clamp and rounding.**

| Constant | Value |
|---|---|
| Adult (≥ 14) clamp | min **1,000**, max **6,000** ml |
| Child (< 14) clamp | min **600**, max **3,000** ml |
| `roundTo50(x)` | nearest multiple of 50, **halves round up**: `((x + 25) / 50) * 50` in integer arithmetic for `x ≥ 0`. So 2,024 → 2,000; 2,025 → 2,050; 2,074 → 2,050; 2,075 → 2,100. |
| Order | `calculatedMl = roundTo50(clamp(rawMl, min, max))` where `rawMl = baseline + Σ applied adjustments` |
| `ClampFlag` | `LOW` when `rawMl < min`, `HIGH` when `rawMl > max`, else `NONE`. Either flag shows the "consult a clinician" note. |
| Manual targets (day and global overrides) | accepted range **250–10,000** ml; used exactly as entered, with no clamp and no rounding |
| `fallbackTargetMl` | **2,000**: used only when there is neither a profile nor an override (should be unreachable after onboarding) |

With v1 constants the low clamp cannot trigger through the calculator (the smallest baseline is 720 for children and 1,600 for adults). It is still implemented and unit-tested directly through `GoalMath.clampAndRound`.

### 7.3 Types and signatures

```kotlin
// :core:model
enum class Sex { FEMALE, MALE, UNSPECIFIED }
enum class ActivityLevel { SEDENTARY, LIGHT, MODERATE, VERY_ACTIVE }
enum class Climate { TEMPERATE, WARM, HOT_HUMID }
enum class PregnancyStatus { NONE, PREGNANT }
enum class Intensity { LIGHT, MODERATE, VIGOROUS }
enum class GoalMode { CALCULATED, GLOBAL_OVERRIDE, DAY_OVERRIDE }
enum class ClampFlag { NONE, LOW, HIGH }

data class Profile(
    val birthDate: LocalDate, val sex: Sex, val weightKg: Double, val heightCm: Double?,
    val activityLevel: ActivityLevel, val climate: Climate,
    val pregnancyStatus: PregnancyStatus, val lactating: Boolean, val highAltitude: Boolean,
)
data class ExerciseSession(val minutes: Int, val intensity: Intensity)

sealed interface ActivityInput {
    data class Level(val level: ActivityLevel) : ActivityInput
    data class Sessions(val sessions: List<ExerciseSession>) : ActivityInput
}
sealed interface ClimateInput {
    data class Manual(val climate: Climate, val forecastUnavailable: Boolean = false) : ClimateInput
    data class Forecast(val maxApparentTempC: Double) : ClimateInput
}

data class GoalInput(
    val date: LocalDate,                 // the hydration day; age is computed on this date
    val profile: Profile,
    val activity: ActivityInput,
    val climate: ClimateInput,
    val toggles: AdjustmentToggles,      // from AppSettings
)

@Serializable enum class ComponentKind {
    BASE_CHILD_TABLE, BASE_WEIGHT, BASE_SEX_FLOOR,
    PREGNANCY, LACTATION, ACTIVITY_LEVEL, ACTIVITY_MINUTES,
    CLIMATE_MANUAL, CLIMATE_FORECAST, ALTITUDE,
}

@Serializable
data class GoalComponent(
    val kind: ComponentKind,
    val ml: Int,                         // the value this component would add
    val applied: Boolean,                // false when its toggle is off; such components are listed but not summed
    val detail: Map<String, String> = emptyMap(),   // see the table below
)

@Serializable
data class GoalBreakdown(
    val policyVersion: Int,
    val ageYears: Int,
    val components: List<GoalComponent>, // fixed order: base, pregnancy, lactation, activity, climate, altitude
    val rawMl: Int,                      // sum of applied components
    val clampedMl: Int,
    val calculatedMl: Int,               // roundTo50(clampedMl)
    val clamp: ClampFlag,
)

// :core:domain
class GoalCalculator(private val policy: GoalPolicy = GoalPolicyV1) {
    fun calculate(input: GoalInput): GoalBreakdown
}

object GoalMath {
    fun ageYears(birthDate: LocalDate, on: LocalDate): Int   // ChronoUnit.YEARS.between, floored at 0
    fun weightBaseMl(weightKg: Double, factorMlPerKg: Int): Int
    fun roundTo50(ml: Int): Int
    fun clampAndRound(rawMl: Int, minMl: Int, maxMl: Int): Triple<Int, Int, ClampFlag>  // (clamped, rounded, flag)
}

data class ResolvedGoal(val targetMl: Int, val calculatedMl: Int?, val mode: GoalMode)

object GoalResolver {
    fun resolve(
        date: LocalDate,
        breakdown: GoalBreakdown?,       // null when there is no profile
        dayOverrideMl: Int?,             // day_override row for this date, tombstones excluded
        global: GlobalOverride,
        policy: GoalPolicy = GoalPolicyV1,
    ): ResolvedGoal
}
```

Component rules:

- A component is **listed** whenever it is relevant to the profile (pregnancy only when pregnant, lactation only when lactating, altitude only when `highAltitude`; base, activity and climate always). It is **applied** when its toggle is on. `AdjustmentToggles.pregnancyLactation` covers both `PREGNANCY` and `LACTATION`; `climate` covers both climate kinds. The base component has no toggle.
- When the weight-based value is below the sex floor, the base component is `BASE_SEX_FLOOR` with `ml = floor`; otherwise `BASE_WEIGHT`.

`detail` keys (all values are strings; numbers use `Locale.ROOT`):

| Kind | Keys | UI label (string resource in `:feature:profile`, formatted with the user's units) |
|---|---|---|
| `BASE_CHILD_TABLE` | `ageBand` (`"0"`, `"1"`, `"2-3"`, `"4-8"`, `"9-13"`) | "Base 1,520 ml (age 9–13)" |
| `BASE_WEIGHT` | `weightKg`, `factor` | "Base 2,310 ml (33 ml × 70 kg)" |
| `BASE_SEX_FLOOR` | `weightKg`, `factor`, `weightBaseMl`, `floorMl` | "Base 1,600 ml (minimum recommended intake; weight-based value was 1,584 ml)" |
| `PREGNANCY`, `LACTATION` | none | "+300 ml pregnancy", "+700 ml breastfeeding" |
| `ACTIVITY_LEVEL` | `level` | "+500 ml moderately active" |
| `ACTIVITY_MINUTES` | `minutes` (total), `capped` (`"true"`/`"false"`) | "+360 ml exercise (30 min)" |
| `CLIMATE_MANUAL` | `climate`, `fallback` (`"true"` when a forecast was wanted but missing) | "+250 ml warm climate", plus "Forecast unavailable, using your climate setting" |
| `CLIMATE_FORECAST` | `maxApparentTempC` | "+500 ml hot day (feels like 32 °C)" |
| `ALTITUDE` | none | "+250 ml high altitude" |

### 7.4 Override resolution

`GoalResolver.resolve`, highest priority first:

1. `dayOverrideMl != null` → `ResolvedGoal(dayOverrideMl, breakdown?.calculatedMl, DAY_OVERRIDE)`.
2. `global.enabled` → target is `global.perWeekdayMl[date.dayOfWeek.name] ?: global.targetMl`; mode `GLOBAL_OVERRIDE`.
3. `breakdown != null` → `ResolvedGoal(breakdown.calculatedMl, breakdown.calculatedMl, CALCULATED)`.
4. Otherwise → `ResolvedGoal(policy.fallbackTargetMl, null, CALCULATED)`.

`calculatedMl` is always carried so the UI can show the calculated and the effective goal side by side (PLAN §4.3). Adjustment toggles and pinned inputs act *before* the calculator, when `GoalInput` is assembled (§6.3 `BuildGoalInputUseCase`): `pinWeight` changes profile resolution (§4.3), `pinActivityLevel` forces `ActivityInput.Level`.

**Snapshot rule (`SnapshotDailyGoalUseCase`).** `daily_goal` for today is rewritten whenever any input changes: profile, settings (`goal.*`, `dayStartMinutes`, weather mode), `day_override`, `activity_adjustment` or `weather_day` for today, and when a new hydration day begins (a `DayRolloverWorker` plus a check whenever the app comes to the foreground). Rows for past days are written once and then only changed by an explicit day override on that date. If the app was not opened for several days, missing past rows are back-filled on next start using the profile and settings *as they are now* (the best available estimate), with `policy_version` set to the current version.

### 7.5 Worked examples

Unless stated, toggles are all on, activity is `Level(SEDENTARY)`, climate is `Manual(TEMPERATE)`, and there is no pregnancy, lactation or altitude. "Age" is the completed age on the goal date; tests construct `birthDate` from it (for example age 13 on 2026-06-10 → born 2012-06-11, age 14 → born 2012-06-10). Every row was computed with a reference script and checked by hand.

`GoalCalculatorTest` must encode every row verbatim.

| # | Age | Sex | kg | Other inputs | Base | Adjustments | Raw | Clamped | **Goal** |
|---|---|---|---|---|---|---|---|---|---|
| G01 | 13 | F | 45 | | child table 1,520 | | 1,520 | 1,520 | **1,500** |
| G02 | 13 | M | 45 | | child table 1,680 | | 1,680 | 1,680 | **1,700** |
| G03 | 14 | F | 45 | | floor 1,600 (45×35 = 1,575) | | 1,600 | 1,600 | **1,600** |
| G04 | 14 | M | 50 | | floor 2,000 (50×35 = 1,750) | | 2,000 | 2,000 | **2,000** |
| G05 | 30 | M | 70 | | weight 2,450 (×35) | | 2,450 | 2,450 | **2,450** |
| G06 | 31 | M | 70 | | weight 2,310 (×33) | | 2,310 | 2,310 | **2,300** |
| G07 | 55 | F | 60 | | weight 1,980 (×33) | | 1,980 | 1,980 | **2,000** |
| G08 | 56 | F | 60 | | weight 1,800 (×30) | | 1,800 | 1,800 | **1,800** |
| G09 | 65 | M | 80 | | weight 2,400 (×30) | | 2,400 | 2,400 | **2,400** |
| G10 | 66 | M | 80 | | weight 2,240 (×28) | | 2,240 | 2,240 | **2,250** |
| G11 | 40 | F | 48 | | floor 1,600 (48×33 = 1,584) | | 1,600 | 1,600 | **1,600** |
| G12 | 40 | F | 48.5 | | weight 1,601 (48.5×33 = 1,600.5, half up) | | 1,601 | 1,601 | **1,600** |
| G13 | 25 | Unspecified | 50 | | floor 1,800 (50×35 = 1,750) | | 1,800 | 1,800 | **1,800** |
| G14 | 28 | F | 65 | pregnant | weight 2,275 | +300 | 2,575 | 2,575 | **2,600** |
| G15 | 32 | F | 65 | lactating; level LIGHT | weight 2,145 | +700, +250 | 3,095 | 3,095 | **3,100** |
| G16 | 35 | M | 82.4 | level MODERATE; climate WARM | weight 2,719 (2,719.2) | +500, +250 | 3,469 | 3,469 | **3,450** |
| G17 | 35 | M | 82.4 | profile level VERY_ACTIVE but `Sessions([30 min MODERATE])`; climate WARM | weight 2,719 | +360 (minutes replace the level), +250 | 3,329 | 3,329 | **3,350** |
| G18 | 35 | M | 82.4 | `Sessions([45 min VIGOROUS, 20 min LIGHT])` | weight 2,719 | +930 (810 + 120) | 3,649 | 3,649 | **3,650** |
| G19 | 35 | M | 82.4 | profile level VERY_ACTIVE; `Sessions([])` (rest day, measured) | weight 2,719 | +0 | 2,719 | 2,719 | **2,700** |
| G20 | 45 | F | 70 | `Forecast(24.9)`; profile climate HOT_HUMID is ignored | weight 2,310 | +0 | 2,310 | 2,310 | **2,300** |
| G21 | 45 | F | 70 | `Forecast(25.0)` | weight 2,310 | +250 | 2,560 | 2,560 | **2,550** |
| G22 | 45 | F | 70 | `Forecast(29.9)` | weight 2,310 | +250 | 2,560 | 2,560 | **2,550** |
| G23 | 45 | F | 70 | `Forecast(30.0)` | weight 2,310 | +500 | 2,810 | 2,810 | **2,800** |
| G24 | 45 | F | 70 | `Forecast(34.9)` | weight 2,310 | +500 | 2,810 | 2,810 | **2,800** |
| G25 | 45 | F | 70 | `Forecast(35.0)` | weight 2,310 | +750 | 3,060 | 3,060 | **3,050** |
| G26 | 22 | M | 150 | `Sessions([200 min VIGOROUS])`; `Forecast(36.0)`; high altitude | weight 5,250 | +2,000 (3,600 capped), +750, +250 | 8,250 | 6,000 (HIGH) | **6,000** |
| G27 | 22 | M | 160 | level VERY_ACTIVE; `Forecast(36.0)`; high altitude | weight 5,600 | +750, +750, +250 | 7,350 | 6,000 (HIGH) | **6,000** |
| G28 | 1 | F | 10 | | child table 920 | | 920 | 920 | **900** |
| G29 | 0 | M | 8 | | child table 720 | | 720 | 720 | **700** |
| G30 | 8 | M | 30 | level VERY_ACTIVE; `Forecast(36.0)`; high altitude | child table 1,280 | +750, +750, +250 | 3,030 | 3,000 (HIGH) | **3,000** |
| G31 | 12 | M | 40 | `Sessions([120 min VIGOROUS])`; `Forecast(36.0)`; high altitude | child table 1,680 | +2,000 (2,160 capped), +750, +250 | 4,680 | 3,000 (HIGH) | **3,000** |
| G32 | 70 | F | 58.3 | level LIGHT; high altitude | weight 1,632 (58.3×28 = 1,632.4) | +250, +250 | 2,132 | 2,132 | **2,150** |
| G33 | 29 | F | 62 | pregnant and lactating; level MODERATE; climate HOT_HUMID; toggles `pregnancyLactation = false`, `activity = false` | weight 2,170 | +500 climate only (300, 700 and 500 activity are listed with `applied = false`) | 2,670 | 2,670 | **2,650** |
| G34 | 50 | M | 75.5 | level LIGHT; `Forecast(31.2)`; toggle `climate = false` | weight 2,492 (75.5×33 = 2,491.5, half up) | +250 activity (forecast 500 listed, not applied) | 2,742 | 2,742 | **2,750** |
| G35 | 50 | M | 75.5 | level LIGHT; climate WARM | weight 2,492 | +250, +250 | 2,992 | 2,992 | **3,000** |
| G36 | 31 | F | 70.7 | | weight 2,333 (70.7×33 = 2,333.1) | | 2,333 | 2,333 | **2,350** |
| G37 | 20 | F | 61.5 | | weight 2,153 (61.5×35 = 2,152.5, half up) | | 2,153 | 2,153 | **2,150** |
| G38 | 60 | M | 67.5 | | weight 2,025 (×30) | | 2,025 | 2,025 | **2,050** (rounding edge: exactly half rounds up) |

`GoalMathTest` (direct helper tests):

| Call | Expected |
|---|---|
| `roundTo50(2024)`, `(2025)`, `(2049)`, `(2050)`, `(2074)`, `(2075)`, `(0)`, `(24)`, `(25)` | 2000, 2050, 2050, 2050, 2050, 2100, 0, 0, 50 |
| `clampAndRound(900, 1000, 6000)` | `(1000, 1000, LOW)` |
| `clampAndRound(1000, 1000, 6000)` | `(1000, 1000, NONE)` |
| `clampAndRound(6000, 1000, 6000)` | `(6000, 6000, NONE)` |
| `clampAndRound(6001, 1000, 6000)` | `(6000, 6000, HIGH)` |
| `clampAndRound(590, 600, 3000)` | `(600, 600, LOW)` |
| `weightBaseMl(70.04, 33)` | 2310 (70.04 → 700 hg → 2,310) |
| `weightBaseMl(70.05, 33)` | 2313 (70.05 → 701 hg → 23,133 → 2,313). `hg` is computed with `BigDecimal` as in §7.2 so the result never depends on binary floating-point representation. |
| `ageYears(2012-06-11, 2026-06-10)` / `(2012-06-10, 2026-06-10)` | 13 / 14 |
| `ageYears(2000-02-29, 2026-02-28)` / `(2000-02-29, 2026-03-01)` | 25 / 26 |

`GoalResolverTest`, with a breakdown whose `calculatedMl = 2300` (G06) unless stated:

| # | Date | Day override | Global override | Result (target, calculated, mode) |
|---|---|---|---|---|
| O1 | Wed 2026-06-10 | none | disabled | 2300, 2300, `CALCULATED` |
| O2 | Wed 2026-06-10 | none | enabled, 2,500 | 2500, 2300, `GLOBAL_OVERRIDE` |
| O3 | Sat 2026-06-13 | none | enabled, 2,500, `{"SATURDAY": 3000}` | 3000, 2300, `GLOBAL_OVERRIDE` |
| O4 | Sun 2026-06-14 | none | enabled, 2,500, `{"SATURDAY": 3000}` | 2500, 2300, `GLOBAL_OVERRIDE` |
| O5 | Sat 2026-06-13 | 4,000 | enabled, 2,500, `{"SATURDAY": 3000}` | 4000, 2300, `DAY_OVERRIDE` |
| O6 | Wed 2026-06-10 | 4,025 | disabled | 4025, 2300, `DAY_OVERRIDE` (manual targets are not rounded) |
| O7 | Wed 2026-06-10 | none | disabled, 2,500 (value present but not enabled) | 2300, 2300, `CALCULATED` |
| O8 | Wed 2026-06-10 | none | enabled, 1,800; breakdown `null` | 1800, `null`, `GLOBAL_OVERRIDE` |
| O9 | Wed 2026-06-10 | none | disabled; breakdown `null` | 2000, `null`, `CALCULATED` |

---

## 8. Pace curve and reminders

Pure logic is in `:core:domain` (package `…core.domain.pace`); the Android side is in `:core:reminders`.

### 8.1 The wake window of a hydration day

```kotlin
data class WakeWindow(val wake: Instant, val sleep: Instant)   // wake < sleep

fun wakeWindow(day: LocalDate, wake: LocalTime, sleep: LocalTime, dayStart: LocalTime, zone: ZoneId): WakeWindow
```

1. `wakeLdt = day.atTime(wake)` if `wake >= dayStart`, else `day.plusDays(1).atTime(wake)` (the first occurrence of the wake time inside the hydration day).
2. `sleepLdt = wakeLdt.toLocalDate().atTime(sleep)`; if `!sleepLdt.isAfter(wakeLdt)` add one day (sleep after midnight).
3. `dayEndLdt = day.plusDays(1).atTime(dayStart)`; if `sleepLdt.isAfter(dayEndLdt)` then `sleepLdt = dayEndLdt` (the window never extends past the hydration day; the settings screen warns when this truncation happens).
4. Convert both with `ZonedDateTime.of(ldt, zone).toInstant()`. `java.time` resolves a time inside a spring-forward gap by shifting it forward by the gap length, and a time inside an autumn overlap to the earlier offset. No special code is needed.

The window length is measured in real elapsed time, so on a DST day it is 23 or 25 hours' worth of wall clock compressed or stretched, and the curve still runs from 0 to 1.

"Today" for any instant is `hydrationDay(now, zone.rules.getOffset(now), dayStart)` (§4.2).

### 8.2 Expected-by-now curve

A curve is a piecewise-linear function `f: [0,1] → [0,1]` through points stored in permille:

```kotlin
data class PaceCurve(val points: List<CurvePoint>) {       // CurvePoint(xPermille, yPermille)
    fun fraction(x: Double): Double                         // f(x); x is clamped to [0, 1]
    fun lastXAtOrBelow(q: Double): Double?                  // max { x in [0,1] : f(x) <= q }; null when q >= 1.0; 0.0 when q < 0
    companion object {
        val DEFAULT = PaceCurve(listOf(CurvePoint(0, 0), CurvePoint(100, 150), CurvePoint(900, 1000), CurvePoint(1000, 1000)))
        val LINEAR = PaceCurve(listOf(CurvePoint(0, 0), CurvePoint(1000, 1000)))
        fun validate(points: List<CurvePoint>): Boolean
        fun from(settings: PaceCurveSettings): PaceCurve    // invalid CUSTOM falls back to DEFAULT
    }
}
```

- **Default curve:** 15% of the goal within the first 10% of the window (a glass soon after waking: the "small front-load"), then a straight line to 100% at 90% of the window, then flat. With 07:00–23:00 that is 300 ml of 2,000 ml by 08:36 and the full goal by 21:24.
- **Validation of a custom curve:** 2 to 12 points; first point `(0, 0)`; last point `(1000, 1000)`; `xPermille` strictly increasing; `yPermille` non-decreasing.
- `fraction(x)`: find the segment `[(x0,y0),(x1,y1)]` with `x0 <= 1000x <= x1` (the first such segment) and interpolate: `(y0 + (y1 − y0) × (1000x − x0) / (x1 − x0)) / 1000`.
- `lastXAtOrBelow(q)`: walk the segments in order; for each segment with `y0/1000 <= q`: if `y1/1000 <= q` the candidate is `x1/1000`, otherwise it is `(x0 + (1000q − y0) × (x1 − x0) / (y1 − y0)) / 1000`. Return the last candidate. This treats flat segments correctly (it returns their right-hand end).

```kotlin
fun expectedFraction(now: Instant, window: WakeWindow, curve: PaceCurve): Double =
    curve.fraction(Duration.between(window.wake, now).toMillis().toDouble() / Duration.between(window.wake, window.sleep).toMillis())

fun expectedMl(goalMl: Int, now: Instant, window: WakeWindow, curve: PaceCurve): Int =
    floor(goalMl * expectedFraction(now, window, curve) + 0.5).toInt()      // display value

enum class PaceStatus { AHEAD, ON_TRACK, BEHIND }
data class Pace(val expectedMl: Int, val deficitMl: Int, val status: PaceStatus)   // deficit = expected − consumed (may be negative)
fun pace(goalMl: Int, consumedMl: Int, tolerancePercent: Int, now: Instant, window: WakeWindow, curve: PaceCurve): Pace
```

`toleranceMl = (goalMl × tolerancePercent + 50) / 100` (integer, half up). Status: `BEHIND` when `deficitMl > toleranceMl`; `AHEAD` when `deficitMl < −toleranceMl`; otherwise `ON_TRACK`. The Today screen shows "250 ml behind" from `deficitMl`.

Default-curve reference values (goal 2,000 / 2,450):

| x | f(x) | expected of 2,000 | expected of 2,450 |
|---|---|---|---|
| 0 | 0 | 0 | 0 |
| 0.05 | 0.075 | 150 | 184 |
| 0.10 | 0.15 | 300 | 368 |
| 0.25 | 0.309375 | 619 | 758 |
| 0.50 | 0.575 | 1,150 | 1,409 |
| 0.75 | 0.840625 | 1,681 | 2,060 |
| 0.875 | 0.9734375 | 1,947 | 2,385 |
| 0.90 – 1.0 | 1.0 | 2,000 | 2,450 |

### 8.3 Reminder decision: types

```kotlin
enum class ReminderKind { NUDGE, ESCALATION, SUMMARY }
enum class AlarmKind { NUDGE, SUMMARY, RECHECK }

data class ReminderConfig(                 // mapped 1:1 from ReminderSettings
    val enabled: Boolean,
    val wake: LocalTime, val sleep: LocalTime,
    val intervalFloor: Duration,
    val tolerancePercent: Int,
    val curve: PaceCurve,
    val escalationEnabled: Boolean,
    val endOfDaySummaryEnabled: Boolean,
)

data class ReminderState(
    val config: ReminderConfig,
    val zone: ZoneId,
    val dayStart: LocalTime,
    val day: LocalDate,                    // must equal the hydration day of `now`; the function throws otherwise
    val goalMl: Int,                       // effective goal of `day` (> 0)
    val consumedMl: Int,                   // total of `day`
    val lastDrinkAt: Instant?,             // latest entry of `day`
    val lastReminderAt: Instant?,          // last NUDGE or ESCALATION actually posted (any day)
    val snoozedUntil: Instant?,
    val busy: List<BusyInterval>,          // calendar events marked busy; empty unless the opt-in is on
    val escalationSentToday: Boolean,
    val summarySentToday: Boolean,
)
data class BusyInterval(val start: Instant, val endExclusive: Instant)

data class ReminderDecision(
    val fireNow: ReminderKind?,            // post this notification now, or null
    val nextAlarmAt: Instant?,             // null only when reminders are disabled
    val nextAlarmKind: AlarmKind?,
    val expectedMl: Int,
    val deficitMl: Int,
)

fun nextReminder(state: ReminderState, now: Instant): ReminderDecision
```

### 8.4 Reminder decision: algorithm

Definitions, all for `state.day`:

- `W`, `S` = `wakeWindow(...)`; `D = S − W`.
- `summaryAt = max(W, S − 30 min)`.
- `nudgeEnd = summaryAt` when `endOfDaySummaryEnabled`, else `S`. Nudges are only allowed at instants `t` with `W <= t < nudgeEnd`.
- `tol = (goalMl × tolerancePercent + 50) / 100`.
- "Behind" at instant `t` means `goalMl × f(x(t)) − consumedMl > tol` in real numbers (strict).

**`candidate(lastReminderAt)`: the earliest instant at which a nudge may fire, given no further drinks.** Returns `null` for "no nudge today".

1. If `consumedMl >= goalMl` → `null` (goal met).
2. `q = (consumedMl + tol) / goalMl` as a `Double`. `xStar = curve.lastXAtOrBelow(q)`. If `xStar == null` (that is, `q >= 1`: the user is within tolerance of the full goal) → `null`.
3. `c = truncateToMinute(W + xStar × D) + 1 minute`. Because `f` is non-decreasing, the user is behind at every instant from `c` on. (Truncation is to the whole minute of the instant; compute `xStar × D` in milliseconds with `Math.round`.)
4. Interval floor: `c = max(c, lastDrinkAt + intervalFloor, lastReminderAt + intervalFloor)` (skip nulls). A drink resets the timer; a reminder with no drink still waits one interval before the next.
5. Snooze: `c = max(c, snoozedUntil)`.
6. Calendar: while some `BusyInterval` has `start <= c < endExclusive`, set `c = endExclusive`.
7. If `c >= nudgeEnd` → `null`. Otherwise return `c`. (`c >= W` always holds because `xStar >= 0`.)

**`nextReminder(state, now)`:**

1. If `!config.enabled` → `ReminderDecision(null, null, null, 0, 0)`.
2. Compute `expectedMl` and `deficitMl` for `now` (§8.2).
3. Decide `fireNow`:
   - **Summary:** if `endOfDaySummaryEnabled && !summarySentToday && summaryAt <= now < S` → `SUMMARY`. Treat `summarySentToday` as true from here on.
   - **Otherwise, nudge:** if `now < nudgeEnd` and `c = candidate(lastReminderAt)` is not null and `now >= c` → fire. The kind is `ESCALATION` when `escalationEnabled && !escalationSentToday && now >= S − 120 min && (goalMl × f(x(now)) − consumedMl) > 0.40 × goalMl`, else `NUDGE`. Treat `lastReminderAt` as `now` from here on.
   - Otherwise nothing fires.
4. Decide the next alarm (always strictly after `now`):
   - If `now >= nudgeEnd` and the summary is not pending (`!endOfDaySummaryEnabled`, or it has been sent, or `now >= S`) → `(wake instant of day + 1, RECHECK)`.
   - Otherwise take the earlier of: `(candidate(updated lastReminderAt), NUDGE)` when not null, and `(summaryAt, SUMMARY)` when the summary is enabled and not yet sent. If neither exists → `(wake instant of day + 1, RECHECK)`.
5. `RECHECK` only means "wake up and call `nextReminder` again"; it never posts anything by itself.

Properties the tests assert: the function is deterministic; `nextAlarmAt > now` whenever it is not null; `fireNow` is never a nudge outside `[W, nudgeEnd)`; firing and scheduling come from the same `candidate` function, so an alarm delivered at or after its scheduled time always fires (unless the state changed in between).

**Drinks, edits and deletes** do not go through a special path: every entry change calls `ReminderScheduler.reschedule()`, which rebuilds the state and calls `nextReminder` with `fireNow` ignored (§8.7).

**Day-start boundary.** The caller always derives `day` from `now`. After `S` the next alarm is the next day's wake, by which time the new hydration day's goal snapshot exists (`SnapshotDailyGoalUseCase` runs inside `reschedule()` first). Drinks logged between the day start and the wake time count toward the new day and are simply part of `consumedMl` at the first check.

**DST and time-zone changes.** `W` and `S` are recomputed from local times on every call, in the current system zone. `TIMEZONE_CHANGED` and `TIME_SET` broadcasts trigger `reschedule()`. Past entries keep their stored offset, so the day totals do not move; only "today" is re-derived, with the new zone's offset.

### 8.5 Worked examples

`ReminderDecisionTest` must encode every row. Zone `Europe/Berlin`; hydration day 2026-06-10 (CEST, UTC+2) unless stated; config defaults unless stated: wake 07:00, sleep 23:00, day start 00:00, floor 60 min, tolerance 10%, default curve, escalation off, summary off, no snooze, no busy intervals, flags false. Times are local wall-clock times on 2026-06-10 unless a date is given.

| # | now | goal / consumed | last drink | last reminder | Other | fireNow | nextAlarmAt (kind) | expected / deficit |
|---|---|---|---|---|---|---|---|---|
| R01 | 06:00 | 2000 / 0 | – | – | before wake | – | 08:05 (NUDGE) | 0 / 0 |
| R02 | 09:00 | 2000 / 500 | 08:30 | – | on pace | – | 11:37 (NUDGE) | 353 / −147 |
| R03 | 12:00 | 2000 / 500 | 08:30 | – | behind, floor passed | NUDGE | 13:00 (NUDGE) | 752 / 252 |
| R04 | 15:00 | 2000 / 500 | 14:40 | – | drank 20 min ago | – | 15:40 (NUDGE) | 1150 / 650 |
| R05 | 15:00 | 2000 / 500 | 08:30 | 14:50 | reminded 10 min ago | – | 15:50 (NUDGE) | 1150 / 650 |
| R06 | 15:00 | 2000 / 500 | 08:30 | 13:00 | snoozed until 15:20 | – | 15:20 (NUDGE) | 1150 / 650 |
| R07 | 18:00 | 2000 / 2000 | 17:50 | – | goal met | – | 2026-06-11 07:00 (RECHECK) | 1548 / −452 |
| R08 | 18:00 | 2000 / 2100 | 17:50 | – | goal met, summary on | – | 22:30 (SUMMARY) | 1548 / −552 |
| R09 | 21:30 | 2000 / 1850 | 19:00 | – | within tolerance of the full goal | – | 2026-06-11 07:00 (RECHECK) | 2000 / 150 |
| R10 | 21:10 | 2000 / 900 | 16:00 | 20:05 | escalation on | ESCALATION | 22:10 (NUDGE) | 1969 / 1069 |
| R11 | 21:10 | 2000 / 1200 | 16:00 | 20:05 | escalation on; 38% behind | NUDGE | 22:10 (NUDGE) | 1969 / 769 |
| R12 | 22:15 | 2000 / 900 | 16:00 | 21:10 | escalation on, already sent today | NUDGE | 2026-06-11 07:00 (RECHECK) | 2000 / 1100 |
| R13 | 22:30 | 2000 / 1500 | 20:00 | 21:00 | summary on | SUMMARY | 2026-06-11 07:00 (RECHECK) | 2000 / 500 |
| R14 | 23:30 | 2000 / 1500 | 20:00 | – | after sleep | – | 2026-06-11 07:00 (RECHECK) | 2000 / 500 |
| R15 | 12:00 | 2000 / 500 | 08:30 | – | busy 11:30–12:45 | – | 12:45 (NUDGE) | 752 / 252 |
| R16 | 22:20 | 2000 / 1000 | 22:10 | – | candidate 23:10 is after sleep | – | 2026-06-11 07:00 (RECHECK) | 2000 / 1000 |
| R17 | 07:00 | 2000 / 0 | – | – | tolerance 0% | – | 07:01 (NUDGE) | 0 / 0 |
| R18 | 12:00 | 2000 / 0 | – | – | reminders disabled | – | null | 0 / 0 |
| R19 | 13:24 | 2450 / 750 | 11:00 | – | tolerance = 245 ml | NUDGE | 14:24 (NUDGE) | 1148 / 398 |
| R20 | 2026-06-11 00:10 | 2000 / 1200 | 2026-06-10 22:00 | – | wake 09:00, sleep 01:00, day start 04:00 (day is still 2026-06-10; window 09:00–01:00) | NUDGE | 2026-06-11 09:00 (RECHECK) | 2000 / 800 |
| R21 | 2026-06-11 02:00 | 2000 / 1200 | 2026-06-10 22:00 | – | same config; after sleep, before the day start | – | 2026-06-11 09:00 (RECHECK) | 2000 / 800 |
| R22 | 2026-03-29 08:30 | 2000 / 0 | – | – | spring-forward day; wake 01:00, sleep 17:00 → window is 15 h real time | NUDGE | 2026-03-29 09:30 (NUDGE) | 1008 / 1008 |
| R23 | 2026-10-25 08:30 | 2000 / 0 | – | – | fall-back day; wake 01:00, sleep 17:00 → window is 17 h real time; 08:30 is CET (UTC+1) | NUDGE | 2026-10-25 09:30 (NUDGE) | 1150 / 1150 |
| R24 | 11:00 | 3000 / 600 | 10:30 | – | LINEAR curve, tolerance 5%, floor 45 min; at 11:00 the deficit equals the tolerance exactly, which is not "behind" | – | 11:15 (NUDGE) | 750 / 150 |
| R25 | 15:00 | 3000 / 600 | 10:30 | – | same config | NUDGE | 15:45 (NUDGE) | 1500 / 900 |

Derivations for the less obvious rows: R01 `q = 200/2000 = 0.10`, `xStar = 0.0667`, `W + 64 min = 08:04:00`, plus one minute. R02 `q = 0.35`, `xStar = 0.1 + (0.35 − 0.15)/0.85 × 0.8 = 0.28824`, × 960 min = 276.7 min → 11:36 → 11:37. R22 elapsed 6.5 h of 15 h, `f(0.4333) = 0.50417`. R09 `q = 2050/2000 ≥ 1`.

`WakeWindowTest`:

| Day | wake / sleep / day start | Zone | Expected window |
|---|---|---|---|
| 2026-06-10 | 07:00 / 23:00 / 00:00 | Europe/Berlin | 2026-06-10T07:00+02:00 → 2026-06-10T23:00+02:00 |
| 2026-06-10 | 09:00 / 01:00 / 04:00 | Europe/Berlin | 2026-06-10T09:00+02:00 → 2026-06-11T01:00+02:00 |
| 2026-06-10 | 09:00 / 01:00 / 00:00 | Europe/Berlin | 2026-06-10T09:00+02:00 → 2026-06-11T00:00+02:00 (truncated at the day end) |
| 2026-03-29 | 01:00 / 17:00 / 00:00 | Europe/Berlin | 2026-03-29T01:00+01:00 → 2026-03-29T17:00+02:00 (15 h) |
| 2026-10-25 | 01:00 / 17:00 / 00:00 | Europe/Berlin | 2026-10-25T01:00+02:00 → 2026-10-25T17:00+01:00 (17 h) |
| 2026-03-29 | 02:30 / 18:30 / 00:00 | Europe/Berlin | 2026-03-29T03:30+02:00 → 2026-03-29T18:30+02:00 (wake falls in the gap and shifts forward) |
| 2026-06-10 | 03:00 / 20:00 / 04:00 | Europe/Berlin | 2026-06-11T03:00+02:00 → 2026-06-11T04:00+02:00 (wake before the day start belongs to the end of the hydration day; truncated to one hour: the settings screen must warn about this configuration) |

### 8.6 Android side: components (`:core:reminders`)

| Class | Role |
|---|---|
| `ReminderScheduler` (interface in `:core:data`, implementation `AlarmReminderScheduler` here) | `suspend fun reschedule(reason: RescheduleReason)`, `suspend fun cancel()`, `suspend fun snooze()`. The only class that touches `AlarmManager`. |
| `BuildReminderStateUseCase` | Reads settings, device state, today's `DailyGoal` and `DailyTotal`, last drink and (if opted in) calendar busy intervals, and returns `ReminderState`. |
| `ReminderAlarmReceiver` | Receives the alarm. Calls `nextReminder`, posts `fireNow` if any, stores the runtime state, schedules `nextAlarmAt`. |
| `ReminderActionReceiver` | Notification actions: log container 1, log container 2, snooze. |
| `SystemEventReceiver` | `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` is not used (no direct-boot support), `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED` → `reschedule()`. It is exported (system broadcasts need that), so any app can send it an explicit intent: `onReceive` checks `intent.action` against these four and ignores anything else. |
| `ReminderNotifier` | Builds and posts notifications; owns the channels. |
| `CalendarBusyReader` | `CalendarContract.Instances` query for events with `AVAILABILITY_BUSY`, not all-day, overlapping today's window. Returns an empty list without the `READ_CALENDAR` permission. |
| `ReminderEntryListener` | An `EntryChangeListener` (§6.4) that calls `reschedule(ENTRY_CHANGED)`. |
| `DayRolloverWorker` | Unique periodic work (24 h, flex 1 h) as a safety net: snapshot the goal, reschedule. |

Receivers use `goAsync()` with a coroutine on an application-scoped `CoroutineScope` and finish within 10 seconds; the work is a handful of indexed queries.

### 8.7 Scheduling

```kotlin
alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmAt.toEpochMilli(), pendingIntent)
```

- Inexact and Doze-safe. **No `SCHEDULE_EXACT_ALARM`, no `USE_EXACT_ALARM`**, and neither appears in the manifest.
- Exactly one alarm exists at any time: the same `PendingIntent` (request code 1000, `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`) is reused, so scheduling replaces the previous alarm. The intent carries the extras `scheduledAtMs` and `kind`.
- `reschedule()`: (1) `SnapshotDailyGoalUseCase(today)`; (2) build the state; (3) `decision = nextReminder(state, now)` and **ignore `fireNow`** (only the alarm receiver posts reminders, so logging a drink can never produce a notification); (4) if `nextAlarmAt == null` cancel the alarm, otherwise set it; (5) store `scheduledAtMs` and `scheduledKind` in `DeviceState.reminderRuntime`. If step 3 says a nudge is due right now, schedule the alarm for `now + 1 minute` rather than dropping it.
- The alarm receiver: (1) detect a missed alarm (§8.9); (2) build the state; (3) call `nextReminder`; (4) post `fireNow`, updating `lastReminderAtMs`, `escalationSentDay` or `summarySentDay`, and clearing an expired `snoozedUntilMs`; (5) schedule `nextAlarmAt`.
- `snooze()`: `snoozedUntilMs = now + snoozeMinutes`, cancel the notification, `reschedule()`.
- If notifications are not permitted (`NotificationManagerCompat.areNotificationsEnabled() == false` or the `POST_NOTIFICATIONS` permission is missing on API 33+), `reschedule()` cancels the alarm and the settings screen shows a "Notifications are off" banner.

`RescheduleReason`: `ENTRY_CHANGED`, `SETTINGS_CHANGED`, `GOAL_CHANGED`, `BOOT`, `TIME_CHANGED`, `TIMEZONE_CHANGED`, `APP_UPDATED`, `APP_FOREGROUND`, `SNOOZE`.

### 8.8 Notifications

Channels (created in `Application.onCreate` through `ReminderNotifier.ensureChannels()`; PLAN §5.3):

| Channel id | Importance | Used for |
|---|---|---|
| `reminders` | `IMPORTANCE_DEFAULT` | nudges and escalations |
| `summaries` | `IMPORTANCE_LOW` | end-of-day summary |
| `backup` | `IMPORTANCE_MIN` | backup and sync errors only ("Reconnect Google Drive") |

Notification ids and `PendingIntent` request codes (one registry, `NotificationIds.kt` in `:core:reminders`; other modules use their own ranges and must not collide):

| Constant | Value | Meaning |
|---|---|---|
| `NOTIF_REMINDER` | 1001 | the single reminder notification (nudge or escalation; a new one replaces the old) |
| `NOTIF_SUMMARY` | 1002 | end-of-day summary |
| `NOTIF_BACKUP_ERROR` | 2001 | owned by `:sync:drive` |
| `NOTIF_HC_ERROR` | 3001 | owned by `:sync:healthconnect` |
| `RC_ALARM` | 1000 | the reminder alarm |
| `RC_ACTION_LOG_1` | 1010 | log top container |
| `RC_ACTION_LOG_2` | 1011 | log second container |
| `RC_ACTION_SNOOZE` | 1012 | snooze |
| `RC_ACTION_CUSTOM` | 1013 | open `QuickLogActivity` |
| `RC_CONTENT` | 1014 | tap → `MainActivity` on Today |
| Widget | 4000–4099 | owned by `:widget` |

Reminder notification: title "Time for some water", text "You're 250 ml behind pace" (escalation: "You're well behind today: 1,100 ml to go"), category `CATEGORY_REMINDER`, `setOnlyAlertOnce(false)`, auto-cancel. Actions: `+ {container0.name} ({volume})`, `+ {container1.name} ({volume})`, `Snooze 30 min`, `Custom…`. The log actions go to `ReminderActionReceiver` with the container id; the receiver calls `LogIntakeUseCase(volume, source = NOTIFICATION)`, cancels the notification, and the entry listeners then update the widget, Health Connect queue and the next reminder. `Custom…` starts `QuickLogActivity` (a dialog-themed activity in `:feature:today`). Do Not Disturb is respected by the channel; the app never bypasses it.

### 8.9 Missed-alarm detection

In `ReminderAlarmReceiver`: `lateBy = now − scheduledAtMs`. If `lateBy > 30 min`, append `now` to `reminderRuntime.missedAlarmTimesMs` (keep the last 10). When 3 or more of them fall within the last 7 days and `batteryHintDismissed` is false, the Today screen shows a "Reminders are arriving late" card that opens a guidance screen (battery optimisation settings intent `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, which opens the list and needs no permission, plus a link to dontkillmyapp.com). The app does not request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Late delivery of up to about 15 minutes is normal in Doze and is not counted.

### 8.10 Manifest entries (`:core:reminders`)

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.READ_CALENDAR" />   <!-- runtime, opt-in; added by T13 together with the opt-in, not before -->

<receiver android:name=".ReminderAlarmReceiver" android:exported="false" />
<receiver android:name=".ReminderActionReceiver" android:exported="false" />
<receiver android:name=".SystemEventReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
    </intent-filter>
</receiver>
```

Tests: `ReminderDecisionTest`, `PaceCurveTest`, `WakeWindowTest`, `PaceTest` (JVM, `:core:domain`); `AlarmReminderSchedulerTest` (Robolectric `ShadowAlarmManager`: one alarm, correct trigger time, cancel when disabled), `ReminderAlarmReceiverTest`, `ReminderActionReceiverTest`, `SystemEventReceiverTest`, `ReminderNotifierTest` (Robolectric `ShadowNotificationManager`: channels, actions, ids) in `:core:reminders`.

---

## 9. Statistics (`:core:domain`, package `…core.domain.stats`)

Every function here is pure, takes plain values, and has no Android or coroutine dependency. View models call them on `Dispatchers.Default`. No external maths library is used.

### 9.1 Input type and conventions

```kotlin
data class DayStat(
    val date: LocalDate,
    val totalMl: Int,
    val goalMl: Int?,          // DailyGoal.targetMl snapshot; null when no snapshot exists for that day
    val entryCount: Int,
) { val logged: Boolean get() = entryCount > 0 }

/** Builds a contiguous list, one element per day in [from, to], filling days without entries with totalMl = 0, entryCount = 0. */
fun buildDayStats(from: LocalDate, to: LocalDate, totals: List<DailyTotal>, goals: Map<LocalDate, Int>): List<DayStat>
```

- **Included days.** Most functions take `includeNoLogDays: Boolean` (from `StatsSettings`, default `false`). The *included* days are all days when it is true, and only `logged` days when it is false. Days excluded this way are treated as missing data, not as zeros.
- **Today.** Ranges passed to the trend, consistency, anomaly, day-of-week and insight functions end at **yesterday** (the last complete hydration day). Views that show today (Day, Week, Month) pass it explicitly, and streaks take a `todayInProgress` flag.
- `median` of an even-sized list is the mean of the two middle values. `sd` is the **sample** standard deviation (divisor `n − 1`); it is `0.0` when `n < 2`.
- Doubles in the golden tables are given to at least 4 decimal places; tests compare with tolerance `1e-3` unless stated.

```kotlin
object Descriptive {
    fun mean(values: List<Double>): Double          // requires non-empty
    fun median(values: List<Double>): Double        // requires non-empty
    fun sd(values: List<Double>): Double
}
```

### 9.2 Summary

```kotlin
data class Summary(
    val dayCount: Int,            // included days
    val totalMl: Long,            // sum over included days (equal to the sum over all days)
    val meanMl: Double, val medianMl: Double, val sdMl: Double,
    val minMl: Int, val maxMl: Int,
)
fun summary(days: List<DayStat>, includeNoLogDays: Boolean): Summary?    // null when no day is included
```

### 9.3 Goal attainment

```kotlin
data class Attainment(
    val dayCount: Int,            // included days that have a goal (goalMl != null && goalMl > 0)
    val daysMet: Int,             // totalMl >= goalMl
    val rate: Double,             // daysMet / dayCount; 0.0 when dayCount == 0
    val below50: Int,             // totalMl * 100 <  goalMl * 50
    val from50To80: Int,          // goalMl * 50 <= totalMl * 100 < goalMl * 80
    val from80To100: Int,         // goalMl * 80 <= totalMl * 100 and totalMl < goalMl      ("partly met")
    val atLeast100: Int,          // totalMl >= goalMl
)
fun attainment(days: List<DayStat>, includeNoLogDays: Boolean): Attainment

enum class DayResult { MET, PARTLY_MET, MISSED, NO_DATA }      // chart colour (PLAN §7.1)
fun dayResult(day: DayStat): DayResult   // NO_DATA: not logged or no goal; MET: >= 100%; PARTLY_MET: >= 80%; else MISSED
```

Comparisons use integer arithmetic (use `Long` for the products) so bucket edges are exact: 1,600 of 2,000 is "80–100", 1,599 is "50–80", 1,000 is "50–80", 999 is "below 50".

### 9.4 Streaks

```kotlin
data class Streaks(val current: Int, val longest: Int)
fun goalStreak(days: List<DayStat>, weeklyFreeze: Boolean, todayInProgress: Boolean): Streaks
fun loggingStreak(days: List<DayStat>, weeklyFreeze: Boolean, todayInProgress: Boolean): Streaks
```

Both are the same scan with a different predicate: goal streak `goalMl != null && totalMl >= goalMl`; logging streak `entryCount > 0`. `days` is contiguous and ascending and should start at the first day with any entry.

Scan with `run = 0`, `longest = 0`, and an empty set `usedWeeks`:

1. If the day satisfies the predicate: `run += 1`.
2. Else if it is the last element and `todayInProgress`: leave `run` unchanged (an unfinished today never breaks a streak).
3. Else if `weeklyFreeze && run > 0` and the day's ISO week (`IsoFields.WEEK_BASED_YEAR`, `WEEK_OF_WEEK_BASED_YEAR`; weeks run Monday to Sunday regardless of locale) is not in `usedWeeks`: add the week to `usedWeeks` and leave `run` unchanged. The frozen day keeps the streak alive but does not add to its length.
4. Else `run = 0`.
5. `longest = max(longest, run)`.

`current` is `run` after the last element. Freezes are derived, not stored, so the result is reproducible from the data alone.

### 9.5 Rolling averages

```kotlin
/** Trailing mean over the window ending at each index. Element i is null when i < window - 1, or when the window holds no included day. */
fun rollingMean(days: List<DayStat>, window: Int, includeNoLogDays: Boolean): List<Double?>
```

The mean is taken over the *included* days inside `[i − window + 1, i]`. The charts use `window = 7` and `window = 30`. To draw the overlay from the first visible day, the view model loads `window − 1` extra days before the visible range.

### 9.6 Trend: Theil–Sen slope

```kotlin
data class TheilSen(val slopePerDay: Double, val intercept: Double) { val slopePerWeek get() = slopePerDay * 7 }
fun theilSen(xs: List<Double>, ys: List<Double>): TheilSen?
```

- Points are the included days: `x` = days since the first day of the range (`ChronoUnit.DAYS`), `y` = `totalMl`.
- `slopePerDay` = median of `(y[j] − y[i]) / (x[j] − x[i])` over all pairs `i < j` with `x[j] != x[i]`. Returns `null` when there are fewer than two points or no valid pair. Tied `y` values simply contribute slopes of 0.
- `intercept` = median of `y[i] − slopePerDay × x[i]`.
- Complexity is O(n²) pairs; the longest range offered is one year (66,795 pairs), which is fine.

### 9.7 Trend significance: Mann–Kendall

```kotlin
data class MannKendall(val s: Int, val variance: Double, val z: Double, val pValue: Double)
fun mannKendall(ys: List<Double>): MannKendall?        // ys in time order; null when n < 3

object Normal {
    fun erf(x: Double): Double
    fun cdf(z: Double): Double = 0.5 * (1.0 + erf(z / sqrt(2.0)))
}
```

- `S = Σ_{i<j} sign(y[j] − y[i])`.
- `Var(S) = [ n(n−1)(2n+5) − Σ_g t_g (t_g − 1)(2 t_g + 5) ] / 18`, where `t_g` is the size of each group of equal `y` values (tie correction).
- `Z = (S − 1)/√Var` if `S > 0`; `(S + 1)/√Var` if `S < 0`; `0` if `S == 0` (continuity correction). If `Var == 0` (all values equal) then `Z = 0`.
- Two-sided `p = 2 × (1 − Normal.cdf(|Z|))`; when `Z == 0` return `p = 1.0` exactly.
- **`erf` approximation** (Abramowitz & Stegun 7.1.26, maximum absolute error 1.5 × 10⁻⁷): for `x ≥ 0`, `t = 1 / (1 + 0.3275911 x)` and `erf(x) = 1 − (((((1.061405429 t − 1.453152027) t) + 1.421413741) t − 0.284496736) t + 0.254829592) t · e^(−x²)`; for `x < 0`, `erf(x) = −erf(−x)`.

```kotlin
enum class TrendDirection { UP, DOWN, STABLE, INSUFFICIENT_DATA }
data class Trend(val direction: TrendDirection, val slopePerWeekMl: Double, val changeOverRangeMl: Double, val pValue: Double?, val n: Int)
fun trend(days: List<DayStat>, includeNoLogDays: Boolean): Trend
```

`n` = number of included days. `n < 8` → `INSUFFICIENT_DATA`. Otherwise `UP` or `DOWN` (the sign of the Theil–Sen slope) only when `pValue < 0.05` and the slope is not 0; else `STABLE`. `changeOverRangeMl = slopePerDay × (last x − first x)`; the UI rounds it to the nearest 10 ml: "Up 120 ml/day over the last 8 weeks".

### 9.8 Consistency score

```kotlin
/** Uses the last 30 elements of `days`. Null when fewer than 7 days are included or the mean is 0. */
fun consistencyScore(days: List<DayStat>, includeNoLogDays: Boolean): Int?
```

`cv = sd / mean` over the included days' totals; `score = round(100 × clamp(1 − cv, 0, 1))` with `Math.round`.

### 9.9 Time-of-day profile

```kotlin
data class TimedIntake(val day: LocalDate, val minuteOfDay: Int, val volumeMl: Int)
// minuteOfDay = local clock minute (0..1439) of instant.atOffset(entry.zoneOffset)

data class TimeOfDayProfile(
    val hourlyMeanMl: List<Double>,          // 24 slots; slot k covers minutes [60k, 60k+60) since the day start
    val cumulativeFraction: List<Double>,    // 24 values: mean over days of (volume by the end of slot k / that day's total)
    val medianMinutesToHalfGoal: Double?,    // minutes since day start; null when no day reached 50%
    val medianMinutesToFullGoal: Double?,
    val daysReachingHalf: Int, val daysReachingFull: Int,
    val eveningShare: Double?,               // volume at or after 18:00 local, as a share of all volume; null when there is no volume
)
fun timeOfDayProfile(entries: List<TimedIntake>, goals: Map<LocalDate, Int>, dayStartMinutes: Int): TimeOfDayProfile
```

- `sinceStart = (minuteOfDay − dayStartMinutes) mod 1440` (non-negative). Entries of a day are ordered by `sinceStart`.
- Days are the distinct `day` values present in `entries` (days without entries do not take part). `hourlyMeanMl[k]` = total volume in slot `k` over all days ÷ number of days.
- Time to 50% / 100%: for each day with a goal, the `sinceStart` of the first entry at which the running total reaches `goal × 0.5` (use `2 × running >= goal`) or `goal`. The result is the median over the days that reached it.
- Evening: an entry is "after 6 pm" when `sinceStart >= (1080 − dayStartMinutes) mod 1440`, that is, from 18:00 until the end of the hydration day. `eveningShare` is volume-weighted over the whole range, not a mean of daily ratios.
- To display a `sinceStart` value as a clock time, add `dayStartMinutes` and take it mod 1440.
- On a DST day the wall clock repeats or skips an hour; entries are placed by their own wall-clock time and offset, which is what the user saw on the clock.

### 9.10 Day-of-week pattern

```kotlin
data class WeekdayMean(val day: DayOfWeek, val meanMl: Double?, val sampleCount: Int)
data class DayOfWeekPattern(val byDay: List<WeekdayMean> /* MONDAY..SUNDAY */, val weakest: DayOfWeek?, val overallMeanMl: Double?)
fun dayOfWeekPattern(days: List<DayStat>, includeNoLogDays: Boolean): DayOfWeekPattern
```

`weakest` is the weekday with the lowest mean among weekdays with `sampleCount >= 2`; ties go to the earlier day in Monday-to-Sunday order; `null` when no weekday has two samples.

### 9.11 Correlations

**Spearman rank correlation** is the reported statistic. It is robust to outliers (one 5 L typo does not dominate) and captures any monotonic relation, which suits "more on hotter days". Pearson is exposed as a helper and is what Spearman is computed with.

```kotlin
object Correlation {
    fun ranks(values: List<Double>): List<Double>                       // 1-based; ties get the average rank
    fun pearson(xs: List<Double>, ys: List<Double>): Double?            // null when n < 2 or either variance is 0
    fun spearman(xs: List<Double>, ys: List<Double>): Double? = pearson(ranks(xs), ranks(ys))
}
enum class CorrelationStrength { NONE, WEAK, MODERATE, STRONG }
data class CorrelationResult(val rho: Double, val n: Int, val strength: CorrelationStrength, val positive: Boolean)
fun correlate(pairs: List<Pair<Double, Double>>): CorrelationResult?    // null when n < 14 or rho is undefined
```

Strength by `|rho|`: `< 0.2` none; `< 0.4` weak; `< 0.6` moderate; otherwise strong. Pairs are (driver, `totalMl`) for days that are logged and have the driver value:

| Driver | Source |
|---|---|
| Active minutes | `ActivityDao.minutesPerDay` |
| Steps | `health_daily.steps` |
| Weight | latest `profile_snapshot.weight_kg` effective on that day (only days where a `HEALTH_CONNECT` snapshot starts, so the series is real measurements) |
| Temperature | `weather_day.max_apparent_temp_c` |

Wording never implies causation: "On hotter days you tended to drink more."

### 9.12 Anomalies (MAD)

```kotlin
enum class AnomalyDirection { HIGH, LOW }
data class Anomaly(val date: LocalDate, val totalMl: Int, val medianMl: Double, val robustSdMl: Double, val direction: AnomalyDirection)
fun anomalies(days: List<DayStat>, windowDays: Int = 30, minSamples: Int = 7, threshold: Double = 2.5): List<Anomaly>
```

For each **logged** day `i`:

1. The reference window is the logged days among the `windowDays` calendar days *before* day `i` (indices `i − 30 … i − 1`; day `i` itself is excluded, so an outlier cannot mask itself).
2. If the window holds fewer than `minSamples` logged days, day `i` is not evaluated.
3. `med` = median of the window totals; `MAD` = median of `|v − med|`.
4. `robustSd = max(1.4826 × MAD, 0.10 × med)`. The second term is a floor for very regular users, including the `MAD == 0` case: without it, someone who logs exactly 2,000 ml every day would have every 2,050 ml day flagged.
5. Day `i` is an anomaly when `|total − med| > threshold × robustSd` (strict).

No-log days are never anomalies. Anomalies are flagged in History with a "Looks unusual, check for a typo" hint.

### 9.13 Insights feed

```kotlin
enum class InsightKind {
    GOAL_HITS_LAST_7, STREAK, TREND, WEEKEND_VS_WEEKDAY, WEAKEST_DAY, BIGGEST_GAP_BLOCK,
    EVENING_SHARE, CONSISTENCY, ANOMALY, TEMPERATURE_CORRELATION, ACTIVITY_CORRELATION,
}
enum class InsightChart { WEEK, MONTH, YEAR, TIME_OF_DAY, DAY_OF_WEEK, TREND, CORRELATION_TEMPERATURE, CORRELATION_ACTIVITY, HISTORY_DAY }
data class Insight(val kind: InsightKind, val priority: Int, val params: Map<String, String>, val chart: InsightChart)

data class InsightInput(
    val today: LocalDate,
    val days: List<DayStat>,                 // contiguous, ending yesterday, up to 90 days
    val entries: List<TimedIntake>,          // last 14 days
    val temperature: List<Pair<Double, Double>>, val activeMinutes: List<Pair<Double, Double>>,   // last 60 days
    val dayStartMinutes: Int, val includeNoLogDays: Boolean, val weeklyFreeze: Boolean,
)
fun buildInsights(input: InsightInput): List<Insight>    // sorted by priority descending, then by InsightKind ordinal; the UI shows the first 5
```

Rules. "Last N days" always means the N complete days ending yesterday.

| Kind | Window | Fires when | Priority | Params → example text |
|---|---|---|---|---|
| `ANOMALY` | last 7 days | `anomalies()` returns a day in the window (the most recent one) | 90 | `date`, `totalMl` → "Tuesday's 5,000 ml looks unusual. Was it a typo?" |
| `STREAK` | all of `days` | current goal streak ≥ 3 | 80 (85 when it equals the longest and is ≥ 7) | `days`, `isLongest` → "You're on a 5-day streak." |
| `GOAL_HITS_LAST_7` | last 7 days | at least 5 of the 7 days are logged | 70 | `met`, `of` = 7 → "You've hit your goal 6 of the last 7 days." |
| `TREND` | last 56 days | `trend()` is `UP` or `DOWN` and `abs(slopePerWeekMl) >= 20` | 65 | `direction`, `changeMl` (rounded to 10), `weeks` = 8 → "Up 120 ml/day over the last 8 weeks." |
| `WEEKEND_VS_WEEKDAY` | last 28 days | ≥ 4 included weekend days and ≥ 8 included weekdays, and `abs(weekendMean − weekdayMean) / weekdayMean >= 0.15` | 60 | `percent`, `lower` → "Your weekend average is 22% lower than weekdays." |
| `BIGGEST_GAP_BLOCK` | last 14 days | ≥ 7 days with entries; clock-time blocks 08–11, 11–14, 14–17, 17–20; the block with the smallest total volume has less than 50% of the mean of the other three | 55 | `block` → "Afternoons (2–5 pm) are your biggest gap." |
| `EVENING_SHARE` | last 14 days | `eveningShare >= 0.40` | 50 | `percent` → "You drink 45% of your water after 6 pm." |
| `WEAKEST_DAY` | last 56 days | every weekday has ≥ 3 samples and the weakest mean ≤ 85% of `overallMeanMl` | 45 | `day`, `percentBelow` → "Sundays are your lowest day, 18% below average." |
| `TEMPERATURE_CORRELATION` | last 60 days | `correlate()` not null and `abs(rho) >= 0.4` | 40 | `positive`, `strength` |
| `ACTIVITY_CORRELATION` | last 60 days | same rule | 40 | `positive`, `strength` |
| `CONSISTENCY` | last 30 days | score ≥ 80 or score ≤ 40 | 30 | `score`, `high` → "Very consistent: your daily intake barely varies." |

### 9.14 Golden datasets

Expected values were computed with a reference script (plain Python, sample SD, the A&S `erf` above) and spot-checked by hand. `StatsGoldenTest` and the per-function test classes must encode them.

**Dataset A** — 14 days, Monday 2026-03-02 to Sunday 2026-03-15, goal 2,000 ml every day. `entryCount` is 0 on the no-log day and 3 otherwise.

| Mon 02 | Tue 03 | Wed 04 | Thu 05 | Fri 06 | Sat 07 | Sun 08 | Mon 09 | Tue 10 | Wed 11 | Thu 12 | Fri 13 | Sat 14 | Sun 15 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2100 | 1800 | 0 (no log) | 2500 | 1950 | 2000 | 1200 | 2200 | 2300 | 1600 | 2000 | 2050 | 900 | 2400 |

| Function | Expected |
|---|---|
| `summary(includeNoLogDays = false)` | dayCount 13, total 25,000, mean 1923.0769, median 2000.0, sd 458.9914, min 900, max 2500 |
| `summary(includeNoLogDays = true)` | dayCount 14, total 25,000, mean 1785.7143, median 2000.0, sd 677.2196, min 0, max 2500 |
| `attainment(false)` | dayCount 13, daysMet 8, rate 0.615385, buckets (below50, 50–80, 80–100, ≥100) = (1, 1, 3, 8) |
| `attainment(true)` | dayCount 14, daysMet 8, rate 0.571429, buckets (2, 1, 3, 8) |
| `goalStreak(freeze = false, todayInProgress = false)` | current 1, longest 2 |
| `goalStreak(freeze = true, todayInProgress = false)` | current 1, longest 4 (Wed 11 is frozen: Mon 09, Tue 10, Thu 12, Fri 13) |
| `goalStreak(false, false)` with Sun 15 changed to 500 | current 0, longest 2 |
| `goalStreak(false, true)` with Sun 15 changed to 500 | current 0, longest 2 (Sat 14 already broke it) |
| `goalStreak(false, true)` on the first 12 days plus a 13th day (Sat 14) of 500 | current 2, longest 2 (today does not break Thu 12 – Fri 13) |
| `loggingStreak(false, false)` | current 11, longest 11 |
| `loggingStreak(true, false)` | current 13, longest 13 |
| `rollingMean(window = 7, false)` | 6 × null, then 1925.0, 1941.6667, 2025.0, 1964.2857, 1892.8571, 1907.1429, 1750.0, 1921.4286 |
| `rollingMean(window = 7, true)` | 6 × null, then 1650.0, 1664.2857, 1735.7143, 1964.2857, 1892.8571, 1907.1429, 1750.0, 1921.4286 |
| `rollingMean(window = 3, false)` | null, null, 1950.0, 2150.0, 2225.0, 2150.0, 1716.6667, 1800.0, 1900.0, 2033.3333, 1966.6667, 1883.3333, 1650.0, 1783.3333 |
| `theilSen` (13 logged days, x = 0, 1, 3, 4, …, 13) | slopePerDay −2.272727, intercept 2022.7273, slopePerWeek −15.9091 |
| `mannKendall` (13 logged values) | S = −1, variance 267.6667, Z = 0.0, p = 1.0 |
| `trend(false)` | `STABLE`, n = 13 |
| `consistencyScore(false)` | 76 (cv = 0.238676) |
| `dayOfWeekPattern(false)` | Mon 2150 (2), Tue 2050 (2), Wed 1600 (1), Thu 2250 (2), Fri 2000 (2), Sat 1450 (2), Sun 1800 (2); weakest SATURDAY (Wednesday has one sample and is not eligible); overall mean 1923.0769 |
| weekend vs weekday (insight rule, included days) | weekend mean 1625.0, weekday mean 2055.5556, difference −20.95% |
| `anomalies()` | exactly one: 2026-03-14 (900 ml), `LOW`, median 2000.0, MAD 200, robustSd 296.52 (1,100 > 2.5 × 296.52 = 741.3). Days before index 8 have fewer than 7 earlier logged days. |

**Dataset B** — clear upward trend, 10 consecutive days: `1500, 1600, 1550, 1700, 1800, 1750, 1900, 2000, 1950, 2100`.

| Function | Expected |
|---|---|
| `summary` | mean 1785.0, median 1775.0, sd 201.4531 |
| `theilSen` | slopePerDay 66.666667, intercept 1500.0, slopePerWeek 466.6667; `changeOverRangeMl` 600.0 |
| `mannKendall` | S = 39, variance 125.0, Z = 3.398823, p = 0.000677 (tolerance 1e-5) |
| `trend` | `UP` |

**Dataset C** — ties, no trend: `2000, 2000, 1500, 2500, 2000, 1500, 2500, 2000, 2000, 2000`. `theilSen` slope 0.0, intercept 2000.0. `mannKendall` S = 2, variance 94.6667 (tie groups of 6, 2 and 2), Z = 0.102778, p = 0.918139. `trend` = `STABLE`.

**Dataset D** — downward trend, 12 days: `2400, 2300, 2350, 2200, 2100, 2150, 2000, 1900, 1950, 1800, 1700, 1750`. `theilSen` slope −66.666667, intercept 2400.0. `mannKendall` S = −58, variance 212.6667, Z = −3.908635, p = 0.0000929 (tolerance 1e-6). `trend` = `DOWN`.

**Dataset E** — dataset B with one outlier (index 4 is 5000): `1500, 1600, 1550, 1700, 5000, 1750, 1900, 2000, 1950, 2100`. `theilSen` slope is still 66.666667 with intercept 1500.0 (robustness check). `mannKendall` S = 31, variance 125.0, Z = 2.683282, p = 0.007290. `trend` = `UP`.

**Constant series** — eight values of 2000: `theilSen` slope 0.0; `mannKendall` S = 0, variance 0.0, Z = 0.0, p = 1.0.

**`Normal.cdf`** (tolerance 1e-6): `cdf(0.5)` = 0.691462, `cdf(1.0)` = 0.841345, `cdf(1.96)` = 0.975002, `cdf(2.5)` = 0.993790, `cdf(3.0)` = 0.998650, `cdf(−1.96)` = 0.024998.

**Dataset F** — anomalies, 12 logged days: `2000, 2100, 1900, 2050, 1950, 2000, 2200, 1800, 5000, 2000, 500, 2050`.

| Index | Total | Window median | MAD | robustSd | Result |
|---|---|---|---|---|---|
| 0–6 | | | | | not evaluated (fewer than 7 earlier logged days) |
| 7 | 1800 | 2000 | 50 | 200.0 (the 10% floor; 1.4826 × 50 = 74.13) | not an anomaly (200 ≤ 500) |
| 8 | 5000 | 2000 | 75 | 200.0 (floor; 1.4826 × 75 = 111.2) | **HIGH** |
| 9 | 2000 | 2000 | 100 | 200.0 | not an anomaly |
| 10 | 500 | 2000 | 75 | 200.0 | **LOW** |
| 11 | 2050 | 2000 | 100 | 200.0 | not an anomaly |

`MAD == 0` cases: eight days of 2000 followed by 2600 → `HIGH` (600 > 2.5 × 200); followed by 2500 → not an anomaly (500 is not strictly greater than 500); followed by 2150 → not an anomaly.

**Dataset G** — Spearman, 14 pairs (max apparent temperature °C, total ml):

`(18,1800) (22,2000) (25,2100) (30,2600) (28,2300) (15,1700) (33,2900) (21,2000) (27,2500) (24,2100) (19,1900) (31,2700) (35,2600) (26,2200)`

Ranks of the totals (ties averaged): `2, 4.5, 6.5, 11.5, 9, 1, 14, 4.5, 10, 6.5, 3, 13, 11.5, 8`. `spearman` = 0.974647; `pearson` = 0.947238; `correlate` → strength `STRONG`, positive. Small cases: `spearman([1,2,3,4,5], [2,1,4,3,5])` = 0.8; `ranks([10,20,20,30])` = `[1.0, 2.5, 2.5, 4.0]`; `pearson([1,2,3], [1,1,1])` = null.

**Dataset T** — time of day; goal 2,000 ml each day; day start 00:00.

| Day | Entries (clock time → ml) | Total | Reaches 50% at | Reaches 100% at | After 18:00 |
|---|---|---|---|---|---|
| 1 | 08:00 → 500, 12:30 → 500, 19:00 → 600, 21:15 → 400 | 2000 | 12:30 (750 min) | 21:15 (1275 min) | 1000 |
| 2 | 07:30 → 250, 10:00 → 750, 15:00 → 500, 18:00 → 500 | 2000 | 10:00 (600 min) | 18:00 (1080 min) | 500 |
| 3 | 09:00 → 300, 13:00 → 300, 20:00 → 900 | 1500 | 20:00 (1200 min) | never | 900 |

Expected: `medianMinutesToHalfGoal` = 750.0 (3 days); `medianMinutesToFullGoal` = 1177.5 (2 days); `eveningShare` = 2400 / 5500 = 0.436364; `hourlyMeanMl` is 0 except slot 7 → 83.3333, 8 → 166.6667, 9 → 100.0, 10 → 250.0, 12 → 166.6667, 13 → 100.0, 15 → 166.6667, 18 → 166.6667, 19 → 200.0, 20 → 300.0, 21 → 133.3333; `cumulativeFraction` for slots 0–23 = `0, 0, 0, 0, 0, 0, 0, 0.0417, 0.125, 0.1917, 0.3167, 0.3167, 0.4, 0.4667, 0.4667, 0.55, 0.55, 0.55, 0.6333, 0.7333, 0.9333, 1.0, 1.0, 1.0`. The `EVENING_SHARE` insight fires (0.436 ≥ 0.40).

**Dataset H** — hydration-day bucketing across DST, travel and a day-start change (`HydrationDayTest` in `:core:domain`, and `DayStartChangeTest` in `:core:data`).

| # | Instant (UTC) | Offset | Day start | Local wall time | Expected hydration day |
|---|---|---|---|---|---|
| H1 | 2026-10-24T21:30:00Z | +02:00 | 00:00 | 24 Oct 23:30 | 2026-10-24 |
| H2 | 2026-10-24T22:30:00Z | +02:00 | 00:00 | 25 Oct 00:30 | 2026-10-25 |
| H3 | 2026-10-25T00:30:00Z | +02:00 | 00:00 | 25 Oct 02:30 (CEST, first pass) | 2026-10-25 |
| H4 | 2026-10-25T01:30:00Z | +01:00 | 00:00 | 25 Oct 02:30 (CET, second pass) | 2026-10-25 |
| H5 | 2026-10-25T22:59:00Z | +01:00 | 00:00 | 25 Oct 23:59 | 2026-10-25 |
| H6 | 2026-10-25T23:00:00Z | +01:00 | 00:00 | 26 Oct 00:00 | 2026-10-26 |
| H7 | 2026-03-29T00:59:00Z | +01:00 | 00:00 | 29 Mar 01:59 | 2026-03-29 |
| H8 | 2026-03-29T01:00:00Z | +02:00 | 00:00 | 29 Mar 03:00 (after the gap) | 2026-03-29 |
| H9 | 2026-06-11T01:30:00Z | +02:00 | 04:00 | 11 Jun 03:30 | 2026-06-10 |
| H10 | 2026-06-11T02:00:00Z | +02:00 | 04:00 | 11 Jun 04:00 | 2026-06-11 |
| H11 | 2026-06-10T03:30:00Z | −04:00 | 00:00 | 9 Jun 23:30 (logged in New York) | 2026-06-09 |
| H12 | 2026-06-10T03:30:00Z | +02:00 | 00:00 | 10 Jun 05:30 (same instant, logged in Berlin) | 2026-06-10 |

With H1–H6 at 250 ml each, the `daily_total` view gives 2026-10-24 → 250 (1 entry), 2026-10-25 → 1,000 (4 entries; this hydration day is 25 hours long), 2026-10-26 → 250 (1 entry).

Day-start change (rule 2 of §4.2). All entries have offset +02:00 and 250 ml. The setting is changed from 00:00 to 04:00 at 2026-06-11T00:00:00Z (02:00 local), when the old "today" is 2026-06-11.

| Entry | Instant (UTC) | Local | Day before the change | Day after the change |
|---|---|---|---|---|
| a | 2026-06-09T23:00:00Z | 10 Jun 01:00 | 2026-06-10 | 2026-06-10 (not re-bucketed: it is before the old today, although the new rule alone would say 9 Jun) |
| b | 2026-06-10T20:00:00Z | 10 Jun 22:00 | 2026-06-10 | 2026-06-10 |
| c | 2026-06-10T22:30:00Z | 11 Jun 00:30 | 2026-06-11 | **2026-06-10** (re-bucketed) |
| d | 2026-06-11T03:00:00Z | 11 Jun 05:00 | logged after the change | 2026-06-11 |

Totals afterwards: 2026-06-10 → 750, 2026-06-11 → 250. `daily_goal` for 2026-06-09 and earlier keep `day_start_minutes = 0`; 2026-06-10 and 2026-06-11 are re-snapshotted with 240.

---

## 10. Health Connect (`:sync:healthconnect`, Phase 5)

### 10.1 Data types, permissions and settings

| Setting (`HealthConnectSettings`) | Health permission requested when it is switched on | Record / feature |
|---|---|---|
| `writeHydration` | `android.permission.health.WRITE_HYDRATION` | `HydrationRecord` write |
| `importHydration` | `android.permission.health.READ_HYDRATION` | `HydrationRecord` read |
| `syncWeight` | `android.permission.health.READ_WEIGHT` | `WeightRecord` read |
| `useExercise` | `android.permission.health.READ_EXERCISE` | `ExerciseSessionRecord` read |
| `readStepsAndCalories` | `android.permission.health.READ_STEPS`, `android.permission.health.READ_ACTIVE_CALORIES_BURNED` | daily aggregates for correlations |
| `backgroundSync` | `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` | periodic sync; offered only when `features.getFeatureStatus(FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == FEATURE_STATUS_AVAILABLE` |
| `importHistory` | `android.permission.health.READ_HEALTH_DATA_HISTORY` | reads older than 30 days before the first grant; offered only when `FEATURE_READ_HEALTH_DATA_HISTORY` is available |

Each switch requests only its own permission, in context, through `PermissionController.createRequestPermissionResultContract()`. If the user denies, the switch goes back to off. A setting is *effective* only when it is on **and** its permission is currently granted (`permissionController.getGrantedPermissions()` is checked at the start of every sync, because the user can revoke in system settings at any time).

### 10.2 Availability

```kotlin
enum class HcAvailability { AVAILABLE, PROVIDER_UPDATE_REQUIRED, UNAVAILABLE }

when (HealthConnectClient.getSdkStatus(context, "com.google.android.apps.healthdata")) {
    HealthConnectClient.SDK_AVAILABLE -> AVAILABLE
    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> PROVIDER_UPDATE_REQUIRED
    else -> UNAVAILABLE
}
```

- `AVAILABLE`: show the Health Connect settings screen.
- `PROVIDER_UPDATE_REQUIRED` (Android 9–13 without the app, or with an old version): show "Install or update Health Connect", which opens `market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding` with the package set to `com.android.vending`.
- `UNAVAILABLE`: hide the section.

### 10.3 Manifest (`sync/healthconnect/src/main/AndroidManifest.xml`)

```xml
<uses-permission android:name="android.permission.health.READ_HYDRATION" />
<uses-permission android:name="android.permission.health.WRITE_HYDRATION" />
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.READ_EXERCISE" />
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.READ_ACTIVE_CALORIES_BURNED" />
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" />
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_HISTORY" />

<queries>
    <package android:name="com.google.android.apps.healthdata" />
</queries>

<application>
    <!-- Android 9–13: Health Connect app shows this as the privacy policy / rationale -->
    <activity android:name=".rationale.PermissionsRationaleActivity" android:exported="true">
        <intent-filter>
            <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
        </intent-filter>
    </activity>

    <!-- Android 14+: the system permission screen links here -->
    <activity-alias
        android:name="ViewPermissionUsageActivity"
        android:exported="true"
        android:targetActivity=".rationale.PermissionsRationaleActivity"
        android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
        <intent-filter>
            <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
            <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
        </intent-filter>
    </activity-alias>
</application>
```

`PermissionsRationaleActivity` is a small Compose activity that explains each data type in plain language and links to the hosted privacy policy. T0 creates it as a stub with the manifest entries commented out until Phase 5, so the health permissions do not appear in the Phase 1–3 builds.

### 10.4 Sync state machine

```kotlin
sealed interface HcSyncState {
    data object Unavailable : HcSyncState
    data object ProviderUpdateRequired : HcSyncState
    data object Disabled : HcSyncState                       // available, but every setting is off
    data class NeedsPermission(val missing: Set<String>) : HcSyncState   // a setting is on but its permission was revoked
    data class Idle(val lastSyncAt: Instant?, val pendingOps: Int) : HcSyncState
    data object Syncing : HcSyncState
    data class Error(val reason: HcError, val lastSyncAt: Instant?) : HcSyncState
}
enum class HcError { RATE_LIMITED, IO, SECURITY, UNKNOWN }
```

| From | Event | To | Action |
|---|---|---|---|
| any | `getSdkStatus` not `SDK_AVAILABLE` | `Unavailable` / `ProviderUpdateRequired` | cancel workers |
| `Disabled` | a setting is switched on and its permission granted | `Idle` | enqueue a one-time sync |
| `Idle` | trigger (app foreground, entry change, periodic, manual "Sync now", settings change) | `Syncing` | run §10.5 |
| `Syncing` | run finished | `Idle` | store `hc.last_sync_at` |
| `Syncing` | `SecurityException` | `NeedsPermission` | switch the affected settings off in the UI state (not in `AppSettings`), show a banner; no retry |
| `Syncing` | `IOException`, `RemoteException`, `IllegalStateException`, rate limit | `Error` | `Result.retry()` with exponential backoff (30 s initial, WorkManager default cap) |
| `Error` / `NeedsPermission` | next trigger, or permission granted again | `Syncing` | |
| any | all settings off | `Disabled` | cancel workers; keep `hc_record_id` values; clear `hc_pending_op` and the token |

The state is exposed as `HealthConnectController.state: Flow<HcSyncState>` (interface in `:core:data`, §6.2).

### 10.5 One sync run (`HealthConnectSyncEngine.sync()`)

A `Mutex` guarantees one run at a time. Steps, in order:

**1. Preconditions.** Availability, then `granted = getGrantedPermissions()`. Compute the effective settings. If nothing is effective, stop.

**2. Push (when `writeHydration` is effective).** Loop: `ops = HcSyncDao.nextOps(limit = 100)` until empty.

- `UPSERT`: load the entry. If it is missing, tombstoned or has `source == HEALTH_CONNECT_IMPORT`, drop the op (imports are never written back, adopted or not). Otherwise build

  ```kotlin
  HydrationRecord(
      startTime = entry.instant, startZoneOffset = entry.zoneOffset,
      endTime = entry.instant.plusSeconds(1), endZoneOffset = entry.zoneOffset,   // see the note below
      volume = Volume.milliliters(entry.volumeMl.toDouble()),
      metadata = Metadata.manualEntry(
          clientRecordId = entry.id.toString(),
          clientRecordVersion = entry.updatedAt.toEpochMilli(),
      ),
  )
  ```

  and call `insertRecords(records)`. Health Connect upserts by `clientRecordId` when the version is higher, so retries are idempotent. Store the returned id with `IntakeDao.setHcRecordId`.
- `DELETE`: `deleteRecords(HydrationRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(entry.id.toString()))`.
- On success delete the ops; on failure `markFailed` and rethrow so the worker retries. An op with `attempts >= 10` is dropped and logged.

  *Note on `endTime`.* PLAN §9.2 says `startTime = endTime = instant`. The pinned client rejects that: constructing a `HydrationRecord` with equal times throws `IllegalArgumentException: startTime must be before endTime` (confirmed in the smoke build, §2.3). The app therefore writes a one-second interval, and the import side treats `startTime` as the entry instant.

**3. Pull.** `types` = the record types whose read setting is effective, out of `HydrationRecord`, `WeightRecord`, `ExerciseSessionRecord`. If `types` is empty, skip to step 4.

- `token = sync_state["hc.changes_token"]`. It is discarded when `sync_state["hc.token_record_types"]` differs from the current `types`.
- **No token → initial load:** call `reconcile(types)` (below), then `getChangesToken(ChangesTokenRequest(types))` and store the token and the type list.
- **Token present → changes loop:**

  ```kotlin
  var t = token
  do {
      val response = client.getChanges(t)
      if (response.changesTokenExpired) { reconcile(types); t = client.getChangesToken(ChangesTokenRequest(types)); break }
      db.withTransaction { response.changes.forEach(::apply); putState("hc.changes_token", response.nextChangesToken) }
      t = response.nextChangesToken
  } while (response.hasMore)
  ```

  The token is stored in the same transaction as the rows it produced, so a crash never loses or double-applies a page.

**Applying a change.**

| Change | Rule |
|---|---|
| `UpsertionChange` with `record.metadata.dataOrigin.packageName == context.packageName` | ignore (own-origin filter) |
| `UpsertionChange(HydrationRecord)` from another app | First add the package to `HealthConnectSettings.importSources` as `true` if it is not present. If `importSources[package] == false`, ignore. Otherwise find the local row by `hc_record_id`. If it exists and `hc_adopted == 1`, ignore (the user's edit wins). If it exists, update `instant`, `zone_offset_seconds`, `volume_ml`, `hydration_day`, `updated_at`, and clear `deleted_at`. If it does not exist, run the dedupe check, then insert with `source = HEALTH_CONNECT_IMPORT`, a new UUID, `hc_record_id`, `hc_data_origin`. Volume is `record.volume.inMilliliters` rounded half up to `Int`; records under 1 ml or over 10,000 ml are skipped. The offset is `record.startZoneOffset`, or the system zone's offset at that instant when it is null. |
| Dedupe check | If a live row exists with the same `hc_data_origin`, the same `instant` and the same `volume_ml` but a different `hc_record_id` (typical after a restore onto a new phone), adopt the new `hc_record_id` on that row and do not insert. |
| `UpsertionChange(WeightRecord)` | When `syncWeight` is effective and `pinWeight` is off: insert a `profile_snapshot` copied from the latest snapshot with the new `weight_kg` (`record.weight.inKilograms`, rounded to 0.1), `source = HEALTH_CONNECT`, `effective_from` = the local date of `record.time`. Skipped when no profile exists yet, or when the weight is unchanged to 0.1 kg. |
| `UpsertionChange(ExerciseSessionRecord)` | When `useExercise` is effective: upsert `activity_adjustment(source = HEALTH_CONNECT, source_record_id = record.metadata.id, date = hydrationDay(startTime, startZoneOffset ?: system, dayStart), minutes = Duration.between(startTime, endTime).toMinutes(), intensity = intensityOf(record.exerciseType), extra_ml = minutes × mlPerMinute)`. Sessions shorter than 5 minutes are ignored. Sessions from every app are used (they do not double-count water). |
| `DeletionChange(recordId)` | Hydration: the row with that `hc_record_id`; if `hc_adopted == 1` just clear `hc_record_id`, otherwise tombstone it. Exercise: `ActivityDao.delete(HEALTH_CONNECT, recordId)`. Weight: nothing (snapshots are history). |

`intensityOf(exerciseType)`: `LIGHT` for walking, yoga, pilates, stretching, golf; `VIGOROUS` for running (including treadmill), high-intensity interval training, boot camp, rowing, swimming (pool and open water), football, basketball, squash, boxing, martial arts, stair climbing; `MODERATE` for everything else. The mapping is a `when` over the `ExerciseSessionRecord.EXERCISE_TYPE_*` constants in one file (`ExerciseIntensity.kt`).

After any change that touches today (entries, activity, weight) the engine calls `SnapshotDailyGoalUseCase` and the entry listeners, so the goal, reminders and widget update.

**`reconcile(types)` — initial load and expired-token fallback.** Window: from `now − 30 days` (or from `now − 5 years` when `importHistory` is effective) to `now`.

- Hydration: `readRecords(ReadRecordsRequest(HydrationRecord::class, TimeRangeFilter.between(from, to), pageSize = 1000, pageToken))` until the page token is null. Apply every record from another origin as an upsert (same rules as above). Then, for local rows from `IntakeDao.importsBetween(from, to)` that are live, not adopted, and whose `hc_record_id` was **not** seen in the read, tombstone them (they were deleted in Health Connect while the token was invalid).
- Weight: read the window, apply only the most recent record.
- Exercise: read the window, upsert all; delete local `HEALTH_CONNECT` adjustments in the window that were not seen.
- Store `hc.last_full_reconcile_at`.

**4. Daily aggregates (when `readStepsAndCalories` is effective).** `aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), TimeRangeFilter.between(LocalDateTime 14 days ago, now), Period.ofDays(1)))` → `HealthDailyDao.upsertAll`. Calendar days are used here (aggregates cannot follow a custom day start); that is acceptable for correlations.

### 10.6 Source apps, exclusion and adoption

- The settings screen lists `importSources` with the app label and icon from `PackageManager` (falling back to the package name when the app is not installed) and a switch per app.
- Switching an app **off**: tombstone its live, non-adopted imports (`IntakeDao.liveImportsFrom`). Switching it **on** again: clear the token so the next sync reconciles and brings them back.
- Switching `importHydration` off: tombstone all live, non-adopted imports; keep `importSources`.
- **Adopt** (History → entry → "Make editable"): set `hc_adopted = 1` and bump `updated_at`. The entry keeps `source = HEALTH_CONNECT_IMPORT` and its origin label, becomes editable and deletable, is never overwritten by later changes from the source app, and is never written to Health Connect (the source app's record still exists there, so writing it would double-count for other readers).

### 10.7 Workers and triggers

| Worker | Unique name | Kind | Constraints | Trigger |
|---|---|---|---|---|
| `HealthConnectSyncWorker` | `hc-sync` | one-time, `ExistingWorkPolicy.REPLACE`, initial delay 5 s (debounce) | none | `HcEntryListener` (an `EntryChangeListener`) after each local change; `ProcessLifecycleOwner` `onStart` (no delay); "Sync now"; settings changes |
| `HealthConnectPeriodicWorker` | `hc-periodic` | periodic, 15 min (the WorkManager minimum), `KEEP` | battery not low | scheduled only while `backgroundSync` is effective; cancelled otherwise |

Both are `@HiltWorker` `CoroutineWorker`s that call `HealthConnectSyncEngine.sync()` and map exceptions as in §10.4. Without the background permission, reads only work in the foreground, so `hc-sync` runs triggered by a notification-action log may fail the pull step with a `SecurityException`; the engine catches that for the pull step only when the app is not in the foreground and still completes the push.

The repository writes `hc_pending_op` rows in the same transaction as the entry change whenever `writeHydration` is on (§6.2). When `writeHydration` is switched on for the first time, `HealthConnectController.enqueueBackfill()` enqueues an `UPSERT` for every live, non-imported entry.

### 10.8 Test plan (`FakeHealthConnectClient`)

`androidx.health.connect:connect-testing` provides `FakeHealthConnectClient` and `FakePermissionController`. The engine takes a `HealthConnectClient` through its constructor. Tests are Robolectric JVM tests in `sync/healthconnect/src/test`, with an in-memory Room database.

| Test class | Cases |
|---|---|
| `HcPushTest` | insert writes a record with `clientRecordId` and version; an edit upserts (one record, new volume); a delete removes it; retry after a simulated failure does not duplicate; imported and tombstoned entries are never pushed; ops are coalesced per entry |
| `HcPullTest` | initial load imports other-origin records and skips own-origin; token loop with `hasMore` pages; token stored per page; an update changes the local row; a `DeletionChange` tombstones; imports off → nothing imported; excluded app → ignored; new app is added to `importSources` |
| `HcTokenExpiryTest` | `FakeHealthConnectClient.expireToken()` → reconcile; rows deleted in Health Connect meanwhile are tombstoned; new token stored |
| `HcAdoptionTest` | an adopted row ignores upserts; a deletion clears `hc_record_id` and keeps the row; adopted rows are not pushed |
| `HcDedupeTest` | same origin, instant and volume with a new record id adopts the id and inserts nothing |
| `HcWeightExerciseTest` | weight → new snapshot (skipped when `pinWeight`); exercise → `activity_adjustment` with the right day, minutes and intensity; deletion removes it |
| `HcPermissionTest` | a revoked permission (`FakePermissionController`) produces `NeedsPermission`, with no crash and no retry loop |
| `HealthConnectSyncWorkerTest` | `TestListenableWorkerBuilder`: success, retry on `IOException`, failure mapping |

Real-device verification against Fitbit and Samsung Health (PLAN §11, phase 5) is a manual checklist in `docs/testing/health-connect-manual.md`, written by T-HC3.

---

## 11. Backup, restore and export

### 11.1 Where the code lives

| Module | Contents | Phase |
|---|---|---|
| `:core:backup` (pure Kotlin/JVM) | `BackupDocument` DTOs and serializers, `BackupCodec` (JSON + gzip), `BackupCrypto` (PBKDF2 + AES-GCM, `javax.crypto` only), `BackupMigrations`, `BackupMerge`, `Retention`, `CsvExport` | 3 (the JSON and CSV export need it) |
| `:core:data` | `BackupRepository`: builds a `BackupDocument` from Room and DataStore, applies one (replace or merge), safety snapshots | 3 |
| `:feature:settings` | Export screen (CSV, JSON through the Storage Access Framework), Backup screen UI | 3 (export), 4 (Drive UI) |
| `:sync:drive` | Google auth, `DriveApi` REST client, `DriveBackupWorker`, `BackupKeyStore`, retention pruning, restore list | 4 |

The same document is the **JSON export** (PLAN §7.4) and the **Drive backup** (PLAN §8.1).

### 11.2 JSON schema v1

Encoding rules: UTF-8; instants are ISO-8601 UTC strings as produced by `Instant.toString()` (for example `"2026-11-15T09:30:00.123Z"`); dates are ISO `yyyy-MM-dd`; UUIDs are lower-case strings; enums are their `name`; absent optional values are omitted (`explicitNulls = false`); the reader uses `ignoreUnknownKeys = true`. Field names are exactly the Kotlin property names below.

```kotlin
@Serializable
data class BackupDocument(
    val format: String = "dhmo-backup",
    val schemaVersion: Int = 1,
    val appVersion: String,                  // versionName, e.g. "1.0.0"
    val appVersionCode: Int,
    val dbVersion: Int,                      // Room version that produced it
    val policyVersion: Int,                  // GoalPolicy version in use
    val createdAt: Instant,
    val deviceName: String,                  // Settings.Global.DEVICE_NAME, falling back to Build.MODEL
    val counts: BackupCounts,
    val entities: BackupEntities,
    val settings: AppSettings,               // the whole user-settings object (§5.2); DeviceState is never included
)

@Serializable
data class BackupCounts(
    val intakeEntries: Int,                  // live entries (deletedAt == null): the number shown in the restore list
    val intakeTombstones: Int,
    val containers: Int, val profileSnapshots: Int, val dailyGoals: Int, val dayOverrides: Int,
    val activityAdjustments: Int, val weatherDays: Int, val healthDaily: Int,
)

@Serializable
data class BackupEntities(
    val intakeEntries: List<IntakeEntryDto> = emptyList(),
    val containers: List<ContainerDto> = emptyList(),
    val profileSnapshots: List<ProfileSnapshotDto> = emptyList(),
    val dailyGoals: List<DailyGoalDto> = emptyList(),
    val dayOverrides: List<DayOverrideDto> = emptyList(),
    val activityAdjustments: List<ActivityAdjustmentDto> = emptyList(),
    val weatherDays: List<WeatherDayDto> = emptyList(),
    val healthDaily: List<HealthDailyDto> = emptyList(),
)

@Serializable data class IntakeEntryDto(
    val id: String, val instant: Instant, val zoneOffsetSeconds: Int, val hydrationDay: LocalDate,
    val volumeMl: Int, val source: String, val hcDataOrigin: String? = null, val hcAdopted: Boolean = false,
    val note: String? = null, val createdAt: Instant, val updatedAt: Instant, val deletedAt: Instant? = null,
)   // hcRecordId is device-local and is not exported
@Serializable data class ContainerDto(
    val id: String, val name: String, val volumeMl: Int, val icon: String, val sortOrder: Int,
    val updatedAt: Instant, val deletedAt: Instant? = null,
)
@Serializable data class ProfileSnapshotDto(
    val id: String, val effectiveFrom: LocalDate, val birthDate: LocalDate, val sex: String, val weightKg: Double,
    val heightCm: Double? = null, val activityLevel: String, val climate: String, val pregnancyStatus: String,
    val lactating: Boolean, val highAltitude: Boolean, val source: String, val createdAt: Instant,
)
@Serializable data class DailyGoalDto(
    val date: LocalDate, val targetMl: Int, val calculatedMl: Int? = null, val mode: String,
    val breakdown: GoalBreakdown? = null,    // embedded as an object, not as a string
    val dayStartMinutes: Int, val policyVersion: Int, val updatedAt: Instant,
)
@Serializable data class DayOverrideDto(
    val date: LocalDate, val targetMl: Int, val reason: String? = null, val updatedAt: Instant, val deletedAt: Instant? = null,
)
@Serializable data class ActivityAdjustmentDto(
    val source: String, val sourceRecordId: String, val date: LocalDate, val minutes: Int, val intensity: String,
    val extraMl: Int, val exerciseType: Int? = null, val updatedAt: Instant,
)
@Serializable data class WeatherDayDto(
    val date: LocalDate, val latE1: Int, val lonE1: Int, val maxTempC: Double, val maxApparentTempC: Double,
    val meanRelHumidity: Double? = null, val extraMl: Int, val fetchedAt: Instant,
)
@Serializable data class HealthDailyDto(val date: LocalDate, val steps: Long? = null, val activeKcal: Double? = null, val updatedAt: Instant)
```

`Instant` and `LocalDate` use `InstantIsoSerializer` and `LocalDateIsoSerializer` (in `:core:model`, applied with `@file:UseSerializers`). Enum-typed columns are strings in the DTOs so that a backup written by a newer app with an unknown enum value can be detected and reported per row rather than failing the whole parse.

Example (abridged):

```json
{
  "format": "dhmo-backup", "schemaVersion": 1, "appVersion": "1.0.0", "appVersionCode": 1,
  "dbVersion": 1, "policyVersion": 1, "createdAt": "2026-11-15T09:30:00Z", "deviceName": "Pixel 8",
  "counts": {"intakeEntries": 2, "intakeTombstones": 1, "containers": 3, "profileSnapshots": 1, "dailyGoals": 1,
             "dayOverrides": 0, "activityAdjustments": 0, "weatherDays": 0, "healthDaily": 0},
  "entities": {
    "intakeEntries": [
      {"id": "5f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a11", "instant": "2026-11-15T07:05:00Z", "zoneOffsetSeconds": 3600,
       "hydrationDay": "2026-11-15", "volumeMl": 250, "source": "MANUAL",
       "createdAt": "2026-11-15T07:05:02Z", "updatedAt": "2026-11-15T07:05:02Z"}
    ],
    "containers": [
      {"id": "00000000-0000-4000-8000-000000000001", "name": "Glass", "volumeMl": 250, "icon": "GLASS", "sortOrder": 0,
       "updatedAt": "2026-11-01T10:00:00Z"}
    ]
  },
  "settings": {"schemaVersion": 1, "units": "ML", "dayStartMinutes": 0}
}
```

Versioning: `BackupMigrations.migrate(root: JsonObject): JsonObject` upgrades older documents step by step before decoding. A document with `schemaVersion` greater than the app supports is rejected with `BackupError.NEWER_VERSION` ("Update the app to restore this backup"). `format` must be `dhmo-backup`.

### 11.3 Container formats and file names

| Form | Bytes | Name |
|---|---|---|
| JSON export (SAF) | plain JSON, pretty-printed | `dihydrogen-monoxide-export-20261115-093000.json` (local time) |
| Drive backup | gzip of the compact JSON | `hydration-backup-v1-20261115T093000Z.json.gz` (`v{schemaVersion}`, UTC, `yyyyMMdd'T'HHmmss'Z'`) |
| Drive backup, encrypted | envelope below around the gzip bytes | `hydration-backup-v1-20261115T093000Z.json.gz.enc` |
| Safety snapshot (local) | gzip | `files/safety/pre-restore-20261115T093000Z.json.gz` |

`BackupCodec.decode(bytes)` detects the form from the first bytes: `44 48 4D 4F 45 4E 43 31` ("DHMOENC1") → encrypted; `1F 8B` → gzip; `{` (after optional whitespace or a UTF-8 BOM) → plain JSON. So "restore from file" accepts all three.

```kotlin
object BackupCodec {
    val json: Json                                                    // ignoreUnknownKeys, encodeDefaults, explicitNulls = false
    fun encodeJson(doc: BackupDocument, pretty: Boolean = false): ByteArray
    fun encodeGzip(doc: BackupDocument): ByteArray
    fun detect(bytes: ByteArray): BackupContainer                     // PLAIN_JSON, GZIP, ENCRYPTED, UNKNOWN
    fun decode(bytes: ByteArray, passphrase: CharArray? = null): BackupDocument   // throws BackupException(BackupError)
}
enum class BackupError { NOT_A_BACKUP, CORRUPT, NEWER_VERSION, PASSPHRASE_REQUIRED, WRONG_PASSPHRASE, IO, AUTH, QUOTA, NETWORK }
```

### 11.4 Encryption (optional)

| Parameter | Value |
|---|---|
| KDF | **PBKDF2-HMAC-SHA256** (`SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`, available from API 26), **600,000 iterations**, 16-byte random salt, 256-bit output. Chosen over Argon2 because it is in the platform and needs no native library. |
| Cipher | **AES-256-GCM** (`AES/GCM/NoPadding`), 12-byte random nonce per file, 128-bit tag |
| Passphrase rules | at least 8 characters; NFKC-normalised, then UTF-8 |

Envelope (big-endian):

| Offset | Size | Field |
|---|---|---|
| 0 | 8 | magic `DHMOENC1` (ASCII) |
| 8 | 1 | envelope version = 1 |
| 9 | 1 | KDF id = 1 (PBKDF2-HMAC-SHA256) |
| 10 | 4 | iterations (unsigned 32-bit) |
| 14 | 16 | salt |
| 30 | 12 | nonce |
| 42 | n + 16 | ciphertext followed by the GCM tag |

Bytes 0–41 (the header) are passed as GCM additional authenticated data, so tampering with the iteration count or salt fails authentication. Plaintext is the gzip form.

```kotlin
object BackupCrypto {
    const val ITERATIONS = 600_000
    fun newSalt(): ByteArray                                           // 16 bytes, SecureRandom
    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray   // 32 bytes
    fun encrypt(plain: ByteArray, key: ByteArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray
    fun decrypt(envelope: ByteArray, passphrase: CharArray): ByteArray // AEADBadTagException → WRONG_PASSPHRASE
    fun decryptWithKey(envelope: ByteArray, key: ByteArray): ByteArray
}
```

Scheduled backups cannot ask for the passphrase, so when encryption is switched on the app derives the key once with a fresh salt and stores **the derived key wrapped by an Android Keystore key** (`AES/GCM`, alias `dhmo_backup_wrap`, not exportable) in `DeviceState.backupRuntime.wrappedKeyB64`, with the salt in `kdfSaltB64`. Every backup from this device reuses that salt (it is in each file's header) with a fresh nonce. The passphrase itself is never stored. Restoring on another device asks for the passphrase and derives the key from the header. The UI warns, with a confirmation checkbox, that a forgotten passphrase makes the backups unrecoverable.

### 11.5 Google auth (Phase 4)

1. **Sign-in:** Credential Manager with `GetSignInWithGoogleOption.Builder(serverClientId)` (the explicit "Sign in with Google" button flow) → `GoogleIdTokenCredential`. The app keeps only the e-mail address for display (`backupRuntime.accountEmail`). The ID token is not sent anywhere, because the app has no server.
2. **Authorization:**

   ```kotlin
   val request = AuthorizationRequest.builder()
       .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata")))
       .build()
   val result = Identity.getAuthorizationClient(context).authorize(request).await()
   if (result.hasResolution()) launch(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())
   else useToken(result.accessToken!!)
   // after the launcher returns: Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
   ```

   Only `drive.appdata` is ever requested.
3. **In the worker:** call `authorize()` with the application context. If `hasResolution()` is true (consent revoked, account removed), the worker cannot show UI: it sets `backupRuntime.needsReconnect = true`, posts the "Reconnect Google Drive" notification on the `backup` channel (tap opens Settings → Backup), and returns `Result.failure()`.
4. **Token expiry:** on HTTP 401 the client calls `AuthorizationClient.clearToken(ClearTokenRequest.builder().setToken(token).build())`, authorizes again and retries the request once.
5. **Disconnect:** `AuthorizationClient.revokeAccess(...)` for the scope, then `CredentialManager.clearCredentialState(ClearCredentialStateRequest())`, then clear `backupRuntime`.

Configuration: a Google Cloud project with the Drive API enabled, an **Android** OAuth client (package name + signing SHA-1) and a **Web** OAuth client whose id is the `serverClientId`. The id is supplied as the Gradle property `dhmo.googleWebClientId` (from `~/.gradle/gradle.properties` or a CI secret) and exposed as `BuildConfig.GOOGLE_WEB_CLIENT_ID` in `:sync:drive`; when it is blank the Drive section shows "Not configured in this build" and tests still run. Exact `clearToken` / `revokeAccess` signatures must be checked against `play-services-auth` 22.0.0 by T-DR1.

### 11.6 Drive REST endpoints

A thin OkHttp client (`DriveApi`), base URL injectable for `MockWebServer`. Every request carries `Authorization: Bearer <accessToken>`.

| Operation | Request |
|---|---|
| **List** | `GET https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name%20contains%20'hydration-backup-'%20and%20trashed%20%3D%20false&orderBy=createdTime%20desc&pageSize=100&fields=nextPageToken,files(id,name,size,createdTime,modifiedTime,md5Checksum,appProperties)` plus `&pageToken=…` while `nextPageToken` is present |
| **Upload** (multipart, files up to 5 MB) | `POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size,createdTime,appProperties` with `Content-Type: multipart/related; boundary=…`. Part 1 `application/json; charset=UTF-8`: `{"name": "<file name>", "parents": ["appDataFolder"], "mimeType": "application/gzip", "appProperties": {…}}`. Part 2 `application/gzip` (or `application/octet-stream` when encrypted): the bytes. |
| **Download** | `GET https://www.googleapis.com/drive/v3/files/{fileId}?alt=media` |
| **Delete** | `DELETE https://www.googleapis.com/drive/v3/files/{fileId}` (expects 204) |
| **About** | `GET https://www.googleapis.com/drive/v3/about?fields=storageQuota(limit,usage),user(emailAddress,displayName)` |

`appProperties` on each file (so the restore list needs no downloads; each key plus value must stay under 124 bytes): `schemaVersion`, `createdAt` (ISO), `deviceName` (truncated to 60 characters), `entryCount`, `encrypted` (`"true"`/`"false"`), `appVersion`.

Error mapping: 401 → re-authorize once, then `AUTH`; 403 with reason `storageQuotaExceeded` → `QUOTA`; 404 on delete → treated as success; 429 and 5xx → `NETWORK` (worker retries with backoff); `IOException` → `NETWORK`. A year of data is about 3,000 entries, roughly 100 KB gzipped, so multipart upload is enough; if a document ever exceeds 5 MB the client switches to `uploadType=resumable` (not needed for v1, noted for T-DR2).

### 11.7 Backup worker and schedule

`DriveBackupWorker` (`@HiltWorker`), unique periodic work `drive-backup`: period 24 h (`DAILY`) or 7 days (`WEEKLY`); constraints `NetworkType.UNMETERED` (or `CONNECTED` when `unmeteredOnly` is off) and `requiresBatteryNotLow`; backoff exponential from 10 min. "Back up now" is unique one-time work `drive-backup-now` with `NetworkType.CONNECTED`, expedited with `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`.

Run: authorize → `BackupRepository.exportDocument()` → gzip → encrypt if enabled → upload → list → `Retention.select` → delete the surplus → store `lastSuccessAtMs`. A failed prune does not fail the backup.

### 11.8 Retention

```kotlin
data class BackupFileInfo(val id: String, val name: String, val createdAt: Instant, val schemaVersion: Int)
data class RetentionPlan(val keep: List<BackupFileInfo>, val delete: List<BackupFileInfo>)
object Retention {
    fun select(files: List<BackupFileInfo>, keepDaily: Int = 7, keepWeekly: Int = 4, keepMonthly: Int = 6, supportedSchemaVersion: Int = 1): RetentionPlan
}
```

1. Sort by `createdAt` descending. All period keys use **UTC**.
2. **Daily:** walk the list and keep the first (newest) file of each distinct UTC date until `keepDaily` distinct dates have been seen.
3. **Weekly:** the same with the key (ISO week-based year, ISO week number) and `keepWeekly`.
4. **Monthly:** the same with the key (year, month) and `keepMonthly`.
5. `keep` is the union; everything else is `delete`.
6. Periods are counted over the periods **that have backups**, not over the calendar, so a phone left in a drawer for three months does not lose its history on the next run.
7. Files with `schemaVersion > supportedSchemaVersion` (written by a newer app on another device) and files whose name does not match the backup pattern are always kept and never counted.
8. The newest file is always kept (it is the first of every rule).

Worked example for `RetentionTest`: one backup at 03:00 UTC every day from 2026-08-01 to 2026-11-15, except none from 2026-10-20 to 2026-11-05, plus a manual backup at 2026-11-15T18:00Z (91 files). With 7 / 4 / 6 the plan keeps exactly these 12:

| Kept file (`createdAt`) | Rules |
|---|---|
| 2026-11-15T18:00Z | daily, weekly (2026-W46), monthly (November) |
| 2026-11-14T03:00Z, 11-13, 11-12, 11-11, 11-10, 11-09 (all 03:00Z) | daily |
| 2026-11-08T03:00Z | weekly (W45) |
| 2026-10-19T03:00Z | weekly (W43), monthly (October) |
| 2026-10-18T03:00Z | weekly (W42) |
| 2026-09-30T03:00Z | monthly (September) |
| 2026-08-31T03:00Z | monthly (August) |

The 2026-11-15T03:00Z file is deleted (the 18:00 file is that day's newest). Only four months have backups, so four monthly files are kept.

### 11.9 Restore

`BackupRepository.restore(doc: BackupDocument, mode: RestoreMode)`, where `RestoreMode` is `REPLACE` or `MERGE`. Steps: (1) write a **safety snapshot** of the current data to `files/safety/` (keep the newest 3); (2) apply in **one Room transaction**; (3) apply settings; (4) re-derive: `SnapshotDailyGoalUseCase(today)`, reminder reschedule, widget update, and, when Health Connect write is on, enqueue `UPSERT` ops for restored non-import entries (idempotent through `clientRecordId`). Settings → Backup offers "Undo last restore", which is a `REPLACE` restore of the newest safety snapshot.

**Replace:** delete all rows of the eight backed-up tables, insert the document's rows (tombstones included), clear `hc_pending_op` and `sync_state`, and replace `AppSettings` with the document's settings after sanitising.

**Merge** (`BackupMerge.merge(local, incoming)` is a pure function in `:core:backup`, so it is unit-tested without a database):

| Entity | Key | Rule |
|---|---|---|
| Intake entry | `id` | Union. When both sides have the id, the row with the later `updatedAt` wins **as a whole**, including its `deletedAt` (so a deletion newer than an edit wins and an edit newer than a deletion resurrects). Equal `updatedAt` → keep local. |
| Container | `id` | same rule |
| Day override | `date` | same rule |
| Profile snapshot | `id` | Union. Rows are immutable; on an id clash keep local. |
| Daily goal | `date` | Later `updatedAt` wins; equal → local. |
| Activity adjustment | (`source`, `sourceRecordId`) | Later `updatedAt` wins; equal → local. |
| Weather day | `date` | Later `fetchedAt` wins; equal → local. |
| Health daily | `date` | Later `updatedAt` wins; equal → local. |
| Settings | – | **Local settings are kept.** |

`hydrationDay` values come from whichever row wins. Local `hc_record_id` values are preserved for rows that stay local or are overwritten in place.

**Settings sanitising on replace:** every `HealthConnectSettings` switch is set to `false` (permissions are per device; `importSources` is kept); `backup` is kept from the *local* device (the user just configured Drive here); `reminders.enabled` is kept from the backup, and a reminder reschedule follows, which itself checks the notification permission; a weather mode of `DEVICE_LOCATION` is kept and simply produces "forecast unavailable" until the permission is granted on this device.

**Restore list UI:** one row per Drive file, from `appProperties`: date and time, device name, entry count, lock icon when encrypted, and "Needs a newer app version" when `schemaVersion` is too high. **New-device flow:** after a successful Drive sign-in during onboarding, if the list is not empty the app offers "Restore from your latest backup" (`REPLACE`, since the device is empty).

### 11.10 CSV export

RFC 4180: comma separator, CRLF line ends, fields quoted when they contain a comma, quote, CR or LF, quotes doubled, UTF-8 without BOM, a header row, `.` as the decimal separator regardless of locale. Volumes are always millilitres. Tombstoned entries are not exported.

`CsvExport.entries(entries: List<IntakeEntry>, appLabels: Map<String, String>, out: Appendable)` → `dihydrogen-monoxide-entries-20261115.csv`

| Column | Example | Notes |
|---|---|---|
| `id` | `5f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a11` | |
| `timestamp_utc` | `2026-11-15T07:05:00Z` | `Instant.toString()` |
| `local_datetime` | `2026-11-15T08:05:00` | instant at the entry's offset |
| `utc_offset` | `+01:00` | `ZoneOffset.id`; UTC is written as `+00:00` |
| `hydration_day` | `2026-11-15` | |
| `volume_ml` | `250` | |
| `source` | `MANUAL` | |
| `source_app` | `com.fitbit.FitbitMobile` | `hcDataOrigin`, empty otherwise |
| `note` | `after run` | |
| `created_at_utc`, `updated_at_utc` | `2026-11-15T07:05:02Z` | |

`CsvExport.dailyTotals(days: List<DayStat>, goals: Map<LocalDate, DailyGoal>, totals: Map<LocalDate, DailyTotal>, out: Appendable)` → `dihydrogen-monoxide-daily-20261115.csv`. One row per day from the first logged day to yesterday, including no-log days.

| Column | Example | Notes |
|---|---|---|
| `date` | `2026-11-14` | hydration day |
| `total_ml` | `2150` | 0 on no-log days |
| `goal_ml` | `2300` | effective goal; empty when no snapshot |
| `calculated_goal_ml` | `2300` | empty when none |
| `goal_mode` | `CALCULATED` | |
| `percent_of_goal` | `93.5` | one decimal, half up; empty when no goal |
| `goal_met` | `false` | `true` / `false`; empty when no goal |
| `entry_count` | `7` | |
| `first_entry_local`, `last_entry_local` | `2026-11-14T07:12:00` | empty on no-log days |

Tests (`core/backup/src/test`): `BackupCodecTest` (round trip of a document with every entity type and every optional field both set and unset; gzip and plain detection; unknown keys ignored; `NEWER_VERSION`; `NOT_A_BACKUP`), `BackupGoldenFileTest` (decodes the committed fixture `src/test/resources/backup-v1.json` and compares with an expected object: this fixture is never edited, and each later schema version adds its own fixture), `BackupCryptoTest` (round trip, wrong passphrase, tampered header, tampered ciphertext, header layout at the documented offsets; to keep the test fast, the round-trip cases pass `iterations = 1_000` and one case checks the 600,000 default), `BackupMergeTest` (each rule in the table, including edit-versus-delete in both orders and the tie), `RetentionTest` (the example above, empty list, single file, newer-schema files kept), `CsvExportTest` (quoting, CRLF, UTC offset formatting, no-log days).

### 11.11 Android Auto Backup

`app/src/main/res/xml/data_extraction_rules.xml` (and the legacy `full_backup_content.xml`) include `database/dhmo.db` (with `-wal` and `-shm` handled by the system) and `files/datastore/settings.json`; they exclude `files/datastore/device_state.json`, `files/safety/`, caches and WorkManager's database. After an Auto Backup restore the changes token is stale (handled by the expired-token path, §10.5) and `DeviceState` is fresh, so onboarding shows a short "Welcome back" variant: permissions only, with the profile step skipped when a profile exists.

---

## 12. Weather: Open-Meteo (`:core:weather`, Phase 5)

### 12.1 Provider interface (declared in `:core:data`)

```kotlin
data class DailyForecast(
    val date: LocalDate,                 // the location's local date returned by the provider
    val maxTempC: Double,
    val maxApparentTempC: Double,
    val meanRelHumidityPercent: Double?,
)
data class GeoPlace(
    val name: String, val admin1: String?, val country: String?, val countryCode: String?,
    val latitude: Double, val longitude: Double,
)
sealed interface WeatherResult<out T> {
    data class Success<T>(val value: T) : WeatherResult<T>
    data class Failure(val reason: WeatherFailure) : WeatherResult<Nothing>
}
enum class WeatherFailure { NETWORK, HTTP, PARSE, NO_DATA }

interface WeatherProvider {
    /** latE1 / lonE1 are degrees × 10, already rounded. Nothing else about the user is sent. */
    suspend fun todayForecast(latE1: Int, lonE1: Int): WeatherResult<DailyForecast>
    suspend fun searchCity(query: String, languageTag: String): WeatherResult<List<GeoPlace>>
}
```

### 12.2 Forecast request

Verified live on 2026-11-15:

```
GET https://api.open-meteo.com/v1/forecast
      ?latitude=52.5
      &longitude=13.4
      &daily=temperature_2m_max,apparent_temperature_max,relative_humidity_2m_mean
      &timezone=auto
      &forecast_days=1
```

- Coordinates: `latE1 = Math.round(latitude × 10).toInt()`, formatted as `String.format(Locale.ROOT, "%.1f", latE1 / 10.0)`; when the value is 0 send `0.0`, never `-0.0`. The same for longitude.
- No API key, no other headers besides `User-Agent: DihydrogenMonoxide/<versionName> (Android)`.
- Temperatures are in °C by default; no unit parameters are sent.

Response (real response for the request above):

```json
{"latitude":52.506966,"longitude":13.39165,"generationtime_ms":0.14,"utc_offset_seconds":3600,
 "timezone":"Europe/Berlin","timezone_abbreviation":"GMT+1","elevation":43.0,
 "daily_units":{"time":"iso8601","temperature_2m_max":"°C","apparent_temperature_max":"°C","relative_humidity_2m_mean":"%"},
 "daily":{"time":["2026-11-15"],"temperature_2m_max":[7.8],"apparent_temperature_max":[3.4],"relative_humidity_2m_mean":[76]}}
```

Errors come back as HTTP 400 with `{"reason":"…","error":true}`.

DTO and mapping:

```kotlin
@Serializable data class ForecastResponse(val daily: Daily? = null, val error: Boolean = false, val reason: String? = null)
@Serializable data class Daily(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("apparent_temperature_max") val apparentTemperatureMax: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m_mean") val relativeHumidityMean: List<Double?> = emptyList(),
)
```

| `WeatherDay` field | Source |
|---|---|
| `date` | the **hydration day** being fetched for (today), not `daily.time[0]` |
| `latE1`, `lonE1` | the request values |
| `maxTempC` | `daily.temperature_2m_max[0]` |
| `maxApparentTempC` | `daily.apparent_temperature_max[0]` |
| `meanRelHumidity` | `daily.relative_humidity_2m_mean[0]` (nullable) |
| `extraMl` | the §7.2 forecast table applied to `maxApparentTempC` |
| `fetchedAt` | now |

`NO_DATA` when `daily` is missing, a list is empty, or either temperature is null. `PARSE` on a serialization error. `HTTP` on a non-2xx status. `NETWORK` on `IOException`. If `daily.time[0]` differs from the hydration day by more than one day the result is `NO_DATA` (clock or zone problem).

### 12.3 City search (manual city)

```
GET https://geocoding-api.open-meteo.com/v1/search?name=Berlin&count=10&language=en&format=json
```

Response: `{"results":[{"id":2950159,"name":"Berlin","latitude":52.52437,"longitude":13.41053,"country_code":"DE","country":"Germany","admin1":"State of Berlin","timezone":"Europe/Berlin", …}], "generationtime_ms":0.18}`. When nothing matches, the `results` key is absent, so the DTO field defaults to an empty list. The search runs for queries of at least 2 characters, debounced 400 ms. `language` is the first subtag of the app locale. Choosing a place stores `ManualCity(name, admin1, countryCode, latE1, lonE1)`; the unrounded coordinates are never stored.

### 12.4 Location (device mode)

- Manifest: `ACCESS_COARSE_LOCATION` only. No `ACCESS_FINE_LOCATION`, no `ACCESS_BACKGROUND_LOCATION`.
- `LocationRefresher.refreshIfForeground()`: called from `MainActivity.onStart` when the mode is `DEVICE_LOCATION`, the permission is granted and the cached location is older than 6 hours. It calls `FusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationToken)`, rounds to 0.1°, and stores `DeviceState.cachedLocation`. If the result is null it falls back to `lastLocation`.
- Workers never touch the location API; they read the cache.
- If Google Play services is missing (`GoogleApiAvailability`), device mode is disabled in the UI with a note, and the manual city remains available.

### 12.5 Fetch schedule and fallback

| Trigger | Mechanism |
|---|---|
| Shortly after the day starts | `WeatherWorker`, unique periodic work `weather-daily`, 24 h period, initial delay to day start + 15 min, `NetworkType.CONNECTED`, exponential backoff from 15 min |
| App open | `WeatherController.refreshIfStale()` from `MainActivity.onStart`: fetch when the mode is not `OFF` and there is no `weather_day` row for today or its `fetchedAt` is more than 6 hours old |
| Mode or city changed | immediate fetch |

After a successful fetch: upsert `weather_day`, set `weatherRuntime.lastFetchFailed = false`, run `SnapshotDailyGoalUseCase(today)`.

On failure, or when there are no coordinates (device mode without a cached location, or permission denied): no row is written, `lastFetchFailed = true`, and the goal uses `ClimateInput.Manual(profile.climate, forecastUnavailable = true)`, which makes the goal screen show "Forecast unavailable, using your climate setting". When the mode is `OFF` the worker is cancelled and no request is ever made.

**Attribution.** Open-Meteo data is licensed CC BY 4.0. Settings → Weather and the goal breakdown show "Weather data by Open-Meteo.com" with a link.

Tests (`core/weather/src/test`): `OpenMeteoProviderTest` with `MockWebServer` (exact request path and query, the sample response above, HTTP 400, malformed JSON, nulls, empty geocoding result, coordinate formatting including negative and zero values), `WeatherWorkerTest`, `WeatherFallbackTest` (failure leads to manual climate with the flag set), `LocationRounderTest`.

---

## 13. Navigation and screens

### 13.1 Library and pattern

Navigation Compose 2.10 with **type-safe routes** (`@Serializable` objects and data classes, `composable<Route>`, `navigation<Graph>(startDestination = …)`, `toRoute<T>()`). Navigation 3 exists as a stable library, but this plan uses Navigation Compose because every feature module can then expose a plain `NavGraphBuilder` extension and stay unaware of the others.

Each feature module has a `navigation/` package containing its routes and one public entry function. `:app` owns `DhmoNavHost`, which calls all of them. Cross-feature navigation is passed in as lambdas, so features do not depend on each other.

### 13.2 Routes

```kotlin
// :feature:onboarding
@Serializable data object OnboardingGraph
@Serializable data object WelcomeRoute
@Serializable data object DisclaimerRoute              // medical disclaimer + "I have a condition or fluid restriction" switch
@Serializable data object UnitsRoute                   // units and day start
@Serializable data object GoalModeRoute                // "Calculate for me" or "I'll set my own goal" (pre-selected when the condition switch is on)
@Serializable data object ProfileSetupRoute            // calculated path
@Serializable data object ManualGoalRoute              // manual path
@Serializable data object GoalPreviewRoute             // breakdown + disclaimer line
@Serializable data object NotificationRationaleRoute   // explains reminders, then requests POST_NOTIFICATIONS (API 33+)
@Serializable data object ReminderSetupRoute           // wake and sleep times, enable switch
@Serializable data object OnboardingDoneRoute
// Added in later phases, inserted before OnboardingDoneRoute:
@Serializable data object WeatherRationaleRoute        // Phase 5: explains rounded location → weather service, offers device location / city / skip
@Serializable data object RestoreOfferRoute            // Phase 4: optional Drive sign-in and restore

// :feature:today
@Serializable data object TodayRoute
@Serializable data class EditEntryRoute(val entryId: String)   // bottom sheet destination; also opened from History
@Serializable data object ContainersRoute              // manage quick-add presets
@Serializable data object BatteryHelpRoute             // §8.9

// :feature:history
@Serializable data object HistoryRoute
@Serializable data class HistoryDayRoute(val epochDay: Long)

// :feature:insights
@Serializable data class InsightsRoute(val range: String = "WEEK", val anchorEpochDay: Long? = null)   // DAY, WEEK, MONTH, YEAR, CUSTOM
@Serializable data class CustomRangeRoute(val fromEpochDay: Long, val toEpochDay: Long, val compare: Boolean = false)
@Serializable data class CorrelationRoute(val driver: String)   // TEMPERATURE, ACTIVE_MINUTES, STEPS, WEIGHT

// :feature:profile
@Serializable data object ProfileGoalRoute             // goal summary, breakdown, overrides
@Serializable data object EditProfileRoute
@Serializable data object GlobalOverrideRoute          // fixed goal, optional per weekday
@Serializable data class DayOverrideRoute(val epochDay: Long)
@Serializable data object AdjustmentsRoute             // toggles and pinned inputs
@Serializable data object SourcesRoute                 // citations and disclaimer

// :feature:reminders
@Serializable data object RemindersRoute
@Serializable data object PaceCurveRoute               // default / linear / custom editor

// :feature:settings
@Serializable data object SettingsRoute
@Serializable data object UnitsSettingsRoute           // units, day start
@Serializable data object AppearanceRoute              // theme, dynamic colour
@Serializable data object BackupSettingsRoute          // Phase 4: Drive; Phase 3 shows "restore from file" only
@Serializable data object RestoreRoute                 // Phase 4: list of Drive backups
@Serializable data object HealthConnectSettingsRoute   // Phase 5
@Serializable data object WeatherSettingsRoute         // Phase 5
@Serializable data object CitySearchRoute              // Phase 5
@Serializable data object ExportRoute                  // CSV, JSON (Phase 3); PDF (Phase 6)
@Serializable data object StatsSettingsRoute           // include no-log days, weekly freeze
@Serializable data object AboutRoute                   // version, licences, privacy policy, disclaimer
```

### 13.3 Entry functions (created by T0 as placeholders, filled in by the feature tasks)

```kotlin
// :feature:onboarding
fun NavGraphBuilder.onboardingGraph(navController: NavController, onFinished: () -> Unit)

// :feature:today
fun NavGraphBuilder.todayScreens(
    navController: NavController,
    onOpenGoal: () -> Unit, onOpenReminders: () -> Unit, onOpenHistoryDay: (LocalDate) -> Unit,
)

// :feature:history
fun NavGraphBuilder.historyScreens(navController: NavController, onEditEntry: (UUID) -> Unit)

// :feature:insights
fun NavGraphBuilder.insightsScreens(navController: NavController, onOpenDay: (LocalDate) -> Unit)

// :feature:profile
fun NavGraphBuilder.profileScreens(navController: NavController)

// :feature:reminders
fun NavGraphBuilder.remindersScreens(navController: NavController)

// :feature:settings
fun NavGraphBuilder.settingsScreens(
    navController: NavController,
    onOpenProfile: () -> Unit, onOpenReminders: () -> Unit,
    integrations: SettingsIntegrations,
)
```

Deep links: `dhmo://today`, `dhmo://settings/backup` (the "Reconnect Google Drive" notification), `dhmo://history/day/{epochDay}`. App shortcuts and the tile's long press open `dhmo://today`. The intent filter is not `BROWSABLE`: only the app's own notifications, shortcuts and tile open these links. Route arguments that arrive through a deep link are untrusted and validated before use (`HistoryDayRoute.date` is null for an `epochDay` outside `LocalDate`'s range, and the screen then closes).

### 13.4 Integration slots

Screens whose content needs classes from a sync module (activity-result launchers, Google and Health Connect APIs) are supplied by `:app`, so `:feature:settings` never depends on `:sync:*`:

```kotlin
// :feature:settings
class SettingsIntegrations(
    val driveBackupContent: @Composable (onBack: () -> Unit, onOpenRestore: () -> Unit) -> Unit = { _, _ -> ComingSoon() },
    val driveRestoreContent: @Composable (onBack: () -> Unit) -> Unit = { ComingSoon() },
    val healthConnectContent: @Composable (onBack: () -> Unit) -> Unit = { ComingSoon() },
    val weatherLocationPermission: @Composable (onResult: (Boolean) -> Unit) -> Unit = { },
)
```

T0 builds `SettingsIntegrations()` with the defaults in `DhmoNavHost`. Phase 4 changes one line in `:app` to pass `DriveBackupScreen` and `DriveRestoreScreen` from `:sync:drive`; Phase 5 does the same for `HealthConnectSettingsScreen` from `:sync:healthconnect`. The onboarding graph takes an equivalent `OnboardingIntegrations` (restore offer, weather rationale), also with defaults.

### 13.5 App shell (`:app`)

- `MainActivity` (`@AndroidEntryPoint`, edge to edge, splash screen through `core-splashscreen`) → `DhmoApp()`.
- `MainViewModel` exposes `StartState { Loading, Onboarding, Main }` from `DeviceState.onboarding.completed` plus the theme settings. The splash screen stays up while `Loading`.
- `DhmoNavHost(startDestination = if (onboarded) TodayRoute else OnboardingGraph)`.
- **Top-level destinations** (bottom bar on compact width, navigation rail on medium and expanded, through `NavigationSuiteScaffold` from `material3-adaptive-navigation-suite`): **Today** (`TodayRoute`), **History** (`HistoryRoute`), **Insights** (`InsightsRoute()`), **Settings** (`SettingsRoute`). Profile/Goal and Reminders are reached from the goal chip and the pace line on Today, and from the first two rows of Settings.
- The bar is hidden inside `OnboardingGraph` and on full-screen editors. Top-level navigation uses `popUpTo(graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true`.
- **Deep links.** `MainActivity` is `singleTop`, so links arrive either with the launch intent or through `onNewIntent`. `MainActivity` removes the link from the launch intent (otherwise `NavHost` follows it before onboarding state is known) and `DhmoApp` follows it once the start state is `Main`; later links reach `DhmoApp` through `addOnNewIntentListener` and call `navController.handleDeepLink`. Links are ignored while inside `OnboardingGraph`.
- **Insets.** The app is edge-to-edge (`enableEdgeToEdge()`, `isNavigationBarContrastEnforced = false`, `adjustResize`). `NavigationSuiteScaffold` consumes the insets of its bar or rail but gives none to its content, so every screen's root is `DhmoScreen` (`:core:designsystem`, a `Scaffold` with `contentWindowInsets = WindowInsets.safeDrawing`, IME included). Its inner padding is applied once: as `contentPadding` on lazy lists, otherwise as `padding(innerPadding).consumeWindowInsets(innerPadding)`; no extra `imePadding()`. Any other activity (`PermissionsRationaleActivity`, `QuickLogActivity`) also calls `enableEdgeToEdge()`.
- **Runtime permissions** (`POST_NOTIFICATIONS`, `READ_CALENDAR`, `ACCESS_COARSE_LOCATION`) use one flow: if granted, continue; else if `shouldShowRequestPermissionRationale` is true, show the rationale and request only from its Continue button; else request. When the result is a denial and `shouldShowRequestPermissionRationale` is now false (permanently denied), don't ask again: show a button to the system settings (`Settings.ACTION_APP_NOTIFICATION_SETTINGS` for notifications, `ACTION_APPLICATION_DETAILS_SETTINGS` otherwise) and keep the feature off. Grant state is always read with `checkSelfPermission` at the point of use, never cached, and permission-protected calls catch `SecurityException`.

### 13.6 Screen list

| Screen | Module | Phase | Notes |
|---|---|---|---|
| Welcome, Disclaimer, Units, Goal mode, Profile setup, Manual goal, Goal preview, Notification rationale, Reminder setup, Done | `:feature:onboarding` | 1 (reminder setup wired in 2) | linear flow with back; progress saved in `OnboardingState.lastStep` |
| Today | `:feature:today` | 1 | progress ring, pace status (2), quick-add row, custom-amount sheet, today's timeline with swipe to delete and Undo |
| Edit entry sheet, Containers | `:feature:today` | 1 | |
| `QuickLogActivity` | `:feature:today` | 2 | dialog-themed activity for the notification's "Custom…" action |
| History list, History day | `:feature:history` | 1 | paged list grouped by hydration day; anomaly hints (3); source-app labels and "Make editable" (5) |
| Insights: Day, Week, Month, Year, Custom | `:feature:insights` | 3 | range tabs in one screen; insights feed on top of Week |
| Correlation detail | `:feature:insights` | 5 | scatter plot |
| Profile and goal, Edit profile, Global override, Day override, Adjustments, Sources | `:feature:profile` | 1 | always shows calculated and effective goal |
| Reminders, Pace curve | `:feature:reminders` | 2 | |
| Settings root, Units and day start, Appearance, Stats settings, About | `:feature:settings` | 1 | |
| Export | `:feature:settings` | 3 | |
| Backup, Restore | `:feature:settings` shell, content from `:sync:drive` | 4 | |
| Health Connect | `:feature:settings` shell, content from `:sync:healthconnect` | 5 | |
| Weather, City search | `:feature:settings` | 5 | uses `WeatherController` and `WeatherProvider` interfaces only |
| Permissions rationale activity | `:sync:healthconnect` | 5 | |

### 13.7 View model and UI state sketches

All view models expose one `StateFlow<UiState>` (`stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)`) and take user actions as functions. One-off effects (snackbars) go through a `Channel<Effect>` exposed as a `Flow`.

```kotlin
// :feature:today
sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Ready(
        val day: LocalDate,
        val consumedMl: Int, val goalMl: Int, val calculatedMl: Int?, val goalMode: GoalMode,
        val progress: Float,                       // consumed / goal, not capped (the ring caps at 1, the label does not)
        val pace: Pace?,                           // null outside the wake window or before Phase 2
        val containers: List<Container>,
        val entries: List<IntakeEntry>,            // today's timeline, newest first
        val units: VolumeUnit,
        val showBatteryHint: Boolean, val showDisclaimerNote: Boolean,
    ) : TodayUiState
}
sealed interface TodayEffect { data class EntryDeleted(val id: UUID) : TodayEffect; data class Error(val messageRes: Int) : TodayEffect }
@HiltViewModel class TodayViewModel @Inject constructor(
    observeToday: ObserveTodayUseCase, private val logIntake: LogIntakeUseCase,
    private val deleteIntake: DeleteIntakeUseCase, private val undoDelete: UndoDeleteIntakeUseCase,
) : ViewModel() {
    val uiState: StateFlow<TodayUiState>
    val effects: Flow<TodayEffect>
    fun onQuickAdd(container: Container); fun onCustomAdd(volumeMl: Int, at: Instant?)
    fun onDelete(id: UUID); fun onUndoDelete(id: UUID)
}

// :feature:history
data class HistoryUiState(val units: VolumeUnit, val anomalies: Set<LocalDate>, val appLabels: Map<String, String>)
@HiltViewModel class HistoryViewModel : ViewModel() {
    val entries: Flow<PagingData<HistoryItem>>      // HistoryItem.DayHeader(date, totalMl, goalMl, isAnomaly) | HistoryItem.Entry(entry, editable)
    val uiState: StateFlow<HistoryUiState>
    fun onDelete(id: UUID); fun onUndoDelete(id: UUID); fun onAdopt(id: UUID)
}

// :feature:insights
enum class InsightsRange { DAY, WEEK, MONTH, YEAR, CUSTOM }
sealed interface InsightsUiState {
    data object Loading : InsightsUiState
    data class Ready(
        val range: InsightsRange, val from: LocalDate, val to: LocalDate,
        val days: List<DayStat>, val results: Map<LocalDate, DayResult>,
        val summary: Summary?, val attainment: Attainment, val goalStreak: Streaks, val loggingStreak: Streaks,
        val rolling7: List<Double?>, val rolling30: List<Double?>,
        val trend: Trend, val consistency: Int?,
        val dayOfWeek: DayOfWeekPattern, val timeOfDay: TimeOfDayProfile?,
        val hourly: List<HourBar>?, val expectedLine: List<Float>?,      // DAY only
        val comparison: Summary?,                                          // CUSTOM with "compare" on
        val insights: List<Insight>, val units: VolumeUnit, val firstDataDay: LocalDate?,
    ) : InsightsUiState
    data object Empty : InsightsUiState                                    // no entries yet
}
@HiltViewModel class InsightsViewModel : ViewModel() {
    val uiState: StateFlow<InsightsUiState>
    fun onRangeSelected(range: InsightsRange); fun onPrevious(); fun onNext(); fun onCustomRange(from: LocalDate, to: LocalDate, compare: Boolean)
}   // results are cached per (range, from, to) in a small LRU map; computation runs on Dispatchers.Default

// :feature:profile
sealed interface ProfileGoalUiState {
    data object Loading : ProfileGoalUiState
    data class Ready(
        val profile: Profile?, val breakdown: GoalBreakdown?, val effective: ResolvedGoal,
        val globalOverride: GlobalOverride, val todayOverride: DayOverride?,
        val toggles: AdjustmentToggles, val pinned: PinnedInputs,
        val weather: WeatherDay?, val weatherMode: WeatherMode, val forecastUnavailable: Boolean,
        val units: VolumeUnit,
    ) : ProfileGoalUiState
}

// :feature:reminders
data class RemindersUiState(
    val settings: ReminderSettings, val notificationsAllowed: Boolean, val calendarPermissionGranted: Boolean,
    val windowWarning: WindowWarning?,              // TRUNCATED_BY_DAY_START, TOO_SHORT (< 4 h)
    val nextReminderAt: Instant?, val previewCurve: List<Pair<Float, Float>>, val loading: Boolean = true,
)

// :feature:settings
data class SettingsUiState(val settings: AppSettings?, val hcAvailability: HcAvailability, val driveState: DriveBackupState, val versionName: String)
data class ExportUiState(val busy: Boolean = false, val lastResult: ExportResult? = null, val entryCount: Int = 0)

// :feature:onboarding
data class OnboardingUiState(
    val units: VolumeUnit, val dayStartMinutes: Int, val hasCondition: Boolean, val goalMode: OnboardingGoalMode,
    val draftProfile: ProfileDraft, val manualGoalMl: Int, val preview: GoalBreakdown?, val notificationsGranted: Boolean?,
)   // one OnboardingViewModel scoped to OnboardingGraph (hiltViewModel(navController.getBackStackEntry(OnboardingGraph)))
```

---

## 14. Testing and CI

### 14.1 Test layers

| Layer | Where | Runner | What |
|---|---|---|---|
| Pure JVM unit | `:core:domain`, `:core:backup`, `:core:model`, `:core:common` (`src/test/kotlin`) | JUnit 4, Truth | goal calculator tables (§7.5), pace and reminder decisions (§8.5), every statistic against the golden datasets (§9.14), backup codec, crypto, merge, retention, CSV (§11) |
| Robolectric unit | Android modules, `src/test/kotlin` | `AndroidJUnit4` on Robolectric, Truth, Turbine, `kotlinx-coroutines-test` | DAOs and the view (in-memory Room), DataStore serializers, repositories, use cases, view models (with fakes from `:core:testing`), receivers and `AlarmManager` / `NotificationManager` shadows |
| Screenshot | feature modules and `:core:designsystem`, `src/test/kotlin/**/screenshot` | Roborazzi on Robolectric (`@GraphicsMode(NATIVE)`) | each screen's main states in **light, dark and large font (font scale 2.0)**; reference images in `src/test/screenshots/` |
| Compose UI (JVM) | feature modules | `createComposeRule()` on Robolectric | interaction logic that does not need a device: quick-add, edit sheet validation, override flows, the insights range picker |
| Room migration | `:core:database` `androidTest` + JVM `SchemaFileTest` | `MigrationTestHelper` | §4.5 |
| WorkManager | `:sync:*`, `:core:weather`, `:core:reminders` | `TestListenableWorkerBuilder`, `WorkManagerTestInitHelper` (Robolectric) | backup, sync, weather and rollover workers: success, retry, failure, constraints |
| Network | `:sync:drive`, `:core:weather` | OkHttp `MockWebServer` (`mockwebserver3`) | exact request URLs, headers, multipart bodies, error mapping |
| Health Connect | `:sync:healthconnect` | `FakeHealthConnectClient` | §10.8 |
| Instrumented end to end | `:app` `androidTest` | Compose UI test + Hilt test runner on the Gradle Managed Device | onboarding to first log; log, edit, delete with Undo; set a day override; change range in Insights; export produces a document |

**Robolectric configuration.** Robolectric 4.18 supports API 37 (it ran the smoke tests against its Android 17 image with `targetSdk` 37 and no `@Config`). Tests therefore need **no `@Config(sdk = …)`**; they run at the target SDK. T0 gives each Android module `src/test/resources/robolectric.properties` from T0 with `sdk=37` and `application=android.app.Application` (so unit tests do not start the Hilt application; tests that need Hilt use `@HiltAndroidTest` with `HiltTestApplication` and override `application` in `@Config`). Pinning the SDK keeps screenshots stable when Robolectric adds a newer image.

**Screenshot conventions.** One helper in `:core:testing`:

```kotlin
/** Captures `<name>_light.png`, `<name>_dark.png`, `<name>_largefont.png` at 411 × 891 dp, 2.625 density. */
fun captureScreenVariants(name: String, content: @Composable () -> Unit)
```

It wraps the content in `DhmoTheme(darkTheme = …, dynamicColor = false)` and, for the large-font variant, in `CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f))`, and calls Roborazzi's `captureRoboImage`. Screens are captured through their stateless `…Content(uiState, callbacks)` composables with fixed fake state, never through view models, and with a fixed clock. Commands: `./gradlew recordRoborazziDebug` writes references; `./gradlew verifyRoborazziDebug` compares (CI); `./gradlew compareRoborazziDebug` writes diff images to `build/outputs/roborazzi`. Without the record or verify task, `captureRoboImage` does nothing, so plain `testDebugUnitTest` stays fast. References are recorded on Linux (the CI image); a developer on another OS re-records through the `record-screenshots` workflow dispatch rather than committing local images.

**Gradle Managed Device.** Declared by the Android convention plugins:

```kotlin
testOptions {
    managedDevices {
        localDevices {
            create("pixel6Api34") { device = "Pixel 6"; apiLevel = 34; systemImageSource = "aosp-atd" }
        }
    }
}
```

Run with `./gradlew pixel6Api34DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect`. Only `:app` and `:core:database` have `androidTest` sources, and the convention plugin declares the device only in modules that have a `src/androidTest` directory.

**Fakes (`:core:testing`).** `FakeIntakeRepository`, `FakeContainerRepository`, `FakeProfileRepository`, `FakeGoalRepository`, `FakeActivityRepository`, `FakeWeatherRepository`, `FakeSettingsRepository`, `FakeDeviceStateRepository`, `FakeBackupRepository`, `FakeReminderScheduler` (records calls), `FakeWidgetUpdater`, `FakeHealthConnectController`, `FakeDriveBackupController`, `FakeWeatherController`, `FakeWeatherProvider`, `MutableClock`, `FixedZoneProvider`, `MainDispatcherRule`, `TestData` (sample profiles, entries and the §9.14 datasets as Kotlin constants).

### 14.2 CI workflows

`.github/workflows/ci.yml` — on `pull_request` and on `push` to `main`; `concurrency` cancels superseded runs; Ubuntu runner; JDK 25 Temurin; Gradle set up with the official Gradle action (build cache, wrapper validation).

| Job | Command | Notes |
|---|---|---|
| `static` | `./gradlew ktlintCheck detekt checkModuleGraph` | uploads SARIF |
| `unit` | `./gradlew testDebugUnitTest :core:common:test :core:model:test :core:domain:test :core:backup:test` | uploads test reports on failure |
| `screenshots` | `./gradlew verifyRoborazziDebug` | uploads `**/build/outputs/roborazzi` on failure |
| `lint` | `./gradlew :app:lintRelease` | `checkDependencies = true` in `:app` covers all modules |
| `build` | `./gradlew :app:assembleDebug :app:assembleRelease` | release is unsigned; proves R8 and the release manifest |
| `schema` | `./gradlew :core:database:kspDebugKotlin && git diff --exit-code core/database/schemas` | |

`.github/workflows/instrumented.yml` — on `workflow_dispatch`, nightly `schedule`, and pull requests labelled `run-instrumented`. Enables KVM on the runner (the udev rule for `/dev/kvm`), accepts SDK licences, then `./gradlew pixel6Api34DebugAndroidTest -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect`, and uploads `**/build/reports/androidTests`. Timeout 45 minutes.

`.github/workflows/record-screenshots.yml` — `workflow_dispatch` only: `./gradlew recordRoborazziDebug`, then commits the changed PNGs to the branch it was dispatched on.

Action versions (`actions/checkout`, `actions/setup-java`, `gradle/actions/setup-gradle`, `actions/upload-artifact`) could not be looked up from the planning environment. T0 pins each to its current major version and records them here.

---

## 15. Work breakdown

### 15.1 Rules for every task

- One task is sized for one implementation agent in one session.
- A task edits only the paths it **owns**. Anything under "may touch" is a shared file, and the expected edit is listed exactly. Any other shared-file edit must be called out in the hand-back.
- Adding a library: append one line to `gradle/libs.versions.toml` (never reorder or change existing lines) and say so. Module `build.gradle.kts` files are owned by the task that owns the module; for modules shared by several tasks (`:core:domain`, `:core:data`, `:feature:settings`) T0 declares every dependency in advance.
- Strings go in the owning module's resource file named in the task. No hard-coded UI text.
- Every task must leave these green: `./gradlew ktlintCheck detekt`, the module test commands listed, and `./gradlew :app:assembleDebug`.
- Paths below abbreviate `src/main/kotlin/casa/peterson/dhmo/<module path>/` as `main/…` and the matching test directory as `test/…`.

### 15.2 Waves at a glance

| Wave | Tasks | Runs in parallel? |
|---|---|---|
| 0 | **T0** | alone, first |
| 1 (Phases 1–3) | **T1, T2, T3, T4, T5, T6** | all six in parallel |
| 2 (Phases 1–3) | **T7** first (short), then **T8, T9, T10, T11, T12, T13, T14, T15, T16** | T7 needs T1 + T4. T10, T14 and T16 do not need T7 and may start with it. The other six start when T7 lands and run in parallel. |
| 3 (first PR) | **T17** | alone, after wave 2 |
| 4 (Phase 4, own PR) | **T-DR1 ∥ T-DR3**, then **T-DR2** | DR1 and DR3 in parallel; DR2 after DR1 |
| 5 (Phase 5, own PR) | **T-HC1 ∥ T-HC3 ∥ T-WX1 ∥ T-WX2**, then **T-HC2**, then **T-COR** | four in parallel; HC2 after HC1; COR after HC2 and WX1 |

Pure-domain tasks T1, T2, T3 and T5 touch no Android code and depend only on T0.

### 15.3 Wave 0

#### T0 — Foundation scaffold (Phase 0)

**Depends on:** nothing. **Owns:** the whole repository except `docs/PLAN.md` and this file.

Deliverables:

1. **Build:** Gradle wrapper 9.9.1 with checksum; `settings.gradle.kts`, root build, `gradle.properties`, `gradle/libs.versions.toml` exactly as §3; `build-logic` with every convention plugin of §3.3; `.editorconfig`; `config/detekt/detekt.yml`; `checkModuleGraph`.
2. **All 23 modules of §3.2** created and compiling, each with its namespace, manifest, `consumer-rules.pro`, `src/test/resources/robolectric.properties` (Android modules), and all project and library dependencies it will ever need according to this document.
3. **`:core:model` complete:** every type in §5.2, §5.3, §6.2 (state and result types), §7.3 (model part), the domain entities (`IntakeEntry`, `Container`, `ProfileSnapshot`, `DailyGoal`, `DayOverride`, `ActivityAdjustment`, `WeatherDay`, `DailyTotal`, `Source`, `ContainerIcon`), ISO serializers.
4. **`:core:domain`:** `time/HydrationDay.kt` and `format/VolumeFormatter.kt` (ml, L, US fl oz = 29.5735 ml, imperial fl oz = 28.4131 ml; conversion and locale-aware formatting) **implemented and tested** (`HydrationDayTest` with dataset H rows H1–H12, `VolumeFormatterTest`). For goal, pace and stats: one file per public API with the exact signatures of §7.3, §8.2–8.3 and §9, bodies `TODO("T1")` / `TODO("T2")` / `TODO("T3")`.
5. **`:core:database` complete:** §4 in full, exported schema `1.json`, `DatabaseModule`, DAO tests, `DailyTotalViewTest`, `SchemaFileTest`, `MigrationTest` scaffold.
6. **`:core:datastore` complete:** §5.4 with tests.
7. **`:core:data`:** every interface of §6.2 and §6.4, `RepositoryModule`, `CommonModule`, `DomainModule`; one `Offline*Repository` class per interface, in its own file, with `TODO("T4")` bodies (`OfflineBackupRepository`: `TODO("T16")`); `SettingsRepository` and `DeviceStateRepository` implemented (thin wrappers).
8. **Controllers:** no-op implementations and Hilt modules in `:core:reminders`, `:widget`, `:sync:healthconnect`, `:sync:drive`, `:core:weather`; `:core:network` module with `OkHttpClient` and `Json`.
9. **`:core:designsystem`:** `DhmoTheme` (Material 3, dynamic colour on API 31+, light and dark fallback schemes, typography), `PlaceholderScreen`, `ComingSoon`, `DhmoScreen` (§13.5 Insets), icons.
10. **`:core:testing`:** `MainDispatcherRule`, `MutableClock`, `FixedZoneProvider`, `captureScreenVariants`.
11. **Feature modules:** the routes and entry functions of §13.2–13.3, fully wired; **one file per screen** (`XxxScreen.kt`) whose composable shows `PlaceholderScreen`; `SettingsIntegrations` and `OnboardingIntegrations`; resource files pre-split: `strings_onboarding.xml`, `strings_today.xml`, `strings_history.xml`, `strings_insights.xml`, `strings_profile.xml`, `strings_reminders.xml`, and in `:feature:settings` `strings_settings.xml`, `strings_export.xml`, `strings_backup.xml`, `strings_healthconnect.xml`, `strings_weather.xml`.
12. **`:app`:** `DhmoApplication` (`@HiltAndroidApp`, WorkManager `Configuration.Provider`, notification channels call), `MainActivity`, `MainViewModel`, `DhmoApp`, `DhmoNavHost` calling every feature entry function, bottom bar / rail, deep links, manifest, `data_extraction_rules.xml`, R8 rules, launcher icon.
13. **Quality and CI:** the three workflows of §14.2; one sample Roborazzi test (`PlaceholderScreenScreenshotTest` in `:core:designsystem`) with committed references; Gradle Managed Device configured; one instrumented smoke test in `:app` (`AppLaunchTest`: the app starts and shows the onboarding welcome placeholder).

**Acceptance:** `./gradlew ktlintCheck detekt checkModuleGraph` ✔; `./gradlew testDebugUnitTest :core:common:test :core:model:test :core:domain:test :core:backup:test` ✔; `./gradlew verifyRoborazziDebug` ✔; `./gradlew :app:lintRelease :app:assembleDebug :app:assembleRelease` ✔; `core/database/schemas/…/1.json` committed; the debug app launches and every bottom-bar destination shows its placeholder.

**Notes:** follow §3.3 on built-in Kotlin. In this planning environment Maven Central requests through the proxy intermittently returned 429 or false 404s under parallel load; if dependency resolution fails with "Could not find" for an artifact that exists, retry with `--max-workers=2` (Gradle caches a false "missing" result for 24 hours; `--refresh-dependencies` clears it). Stubs that ignore their parameters carry `@Suppress("UNUSED_PARAMETER")` at file level because warnings are errors.

### 15.4 Wave 1 (all parallel, after T0)

#### T1 — Goal calculator (Phase 1, pure domain)

**Depends on:** T0. **Owns:** `core/domain` `main/goal/**`, `test/goal/**`.
**Deliver:** `GoalPolicyV1`, `GoalCalculator`, `GoalMath`, `GoalResolver` per §7.
**Acceptance:** `GoalCalculatorTest` (rows G01–G38 as a parameterised table, asserting base component kind and value, each adjustment, `rawMl`, `clampedMl`, `clamp`, `calculatedMl`, plus `applied = false` components in G33 and G34 and the `detail` keys of §7.3), `GoalMathTest`, `GoalResolverTest` (O1–O9). `./gradlew :core:domain:test --tests '*goal*'` ✔.

#### T2 — Pace curve and reminder decision (Phase 2, pure domain)

**Depends on:** T0. **Owns:** `core/domain` `main/pace/**`, `test/pace/**`.
**Deliver:** `wakeWindow`, `PaceCurve`, `expectedMl`, `pace`, `nextReminder` per §8.1–8.4.
**Acceptance:** `WakeWindowTest` (7 rows), `PaceCurveTest` (reference table, validation, `lastXAtOrBelow` including flat segments, `q >= 1`, `q < 0`), `PaceTest`, `ReminderDecisionTest` (R01–R25), plus a property-style test over random states asserting `nextAlarmAt > now` and no nudge outside the window. `./gradlew :core:domain:test --tests '*pace*'` ✔.

#### T3 — Statistics and insights rules (Phase 3, pure domain)

**Depends on:** T0. **Owns:** `core/domain` `main/stats/**`, `test/stats/**`.
**Deliver:** everything in §9.1–9.13.
**Acceptance:** one test class per function file (`SummaryTest`, `AttainmentTest`, `StreaksTest`, `RollingMeanTest`, `TheilSenTest`, `MannKendallTest`, `NormalTest`, `TrendTest`, `ConsistencyTest`, `TimeOfDayProfileTest`, `DayOfWeekPatternTest`, `CorrelationTest`, `AnomaliesTest`, `InsightsTest`) encoding datasets A–G and T of §9.14 with the stated tolerances; `InsightsTest` has one positive and one negative case per rule. `./gradlew :core:domain:test --tests '*stats*'` ✔.

#### T4 — Repositories, entry use cases, fakes (Phase 1)

**Depends on:** T0. **Owns:** `core/data` `main/repository/**` (the `Offline*` implementations except `OfflineBackupRepository`), `main/usecase/intake/**` (`LogIntakeUseCase`, `EditIntakeUseCase`, `DeleteIntakeUseCase`, `UndoDeleteIntakeUseCase`), `main/time/DayTicker.kt`, `main/mapper/**`, matching `test/**`; all of `core/testing` fakes and `TestData`.
**Deliver:** §6.2 repositories over Room and DataStore, the entry fan-out of §6.4 including the `hc_pending_op` write, container seeding, profile resolution with `pinWeight`, `rebucketFrom`.
**Acceptance:** `OfflineIntakeRepositoryTest` (add / update / delete / undo set `hydrationDay`, `updatedAt`, tombstone; listeners called once per write with the affected days; a throwing listener does not fail the write; pending op written only when `writeHydration` is on and never for imports), `DayStartChangeTest` (the §9.14 day-start table), `OfflineContainerRepositoryTest` (seed for ML and for US_FL_OZ, reorder), `OfflineProfileRepositoryTest` (resolution, same-day manual replace, pinned weight), `OfflineGoalRepositoryTest`, `DayTickerTest`, `IntakeUseCasesTest` (validation limits, imports rejected unless adopted). Fakes compile and are used by at least one test each. `./gradlew :core:data:testDebugUnitTest :core:testing:testDebugUnitTest` ✔.

#### T5 — Backup document, crypto, merge, retention, CSV (Phase 3, pure)

**Depends on:** T0. **Owns:** all of `core/backup`.
**Deliver:** §11.2–11.4, §11.8, the merge function of §11.9, §11.10.
**Acceptance:** the test classes listed at the end of §11.10, including the committed fixture `backup-v1.json`. `./gradlew :core:backup:test` ✔.

#### T6 — Design system components (Phase 1)

**Depends on:** T0. **Owns:** `core/designsystem` except `theme/**` (T0's, read-only now).
**Deliver:** `ProgressRing` (announces "N percent of goal" to TalkBack; caps the arc at 100%), `VolumeText`, `GoalChip`, `QuickAddButton` (48 dp minimum target), `VolumeInputSheet` (slider + numeric field, unit aware), `DhmoTopBar`, `SectionCard`, `EmptyState`, `TimePickerRow`, `SettingsRow` / `SwitchRow`, `DayResultColors` (met / partly met / missed, colour-blind-safe and paired with icons), `HeatmapGrid` (Canvas; the PLAN's fallback for the year view).
**Acceptance:** `ComponentsScreenshotTest` (each component in light, dark, large font; references committed), `ProgressRingSemanticsTest`, `VolumeInputSheetTest`. `./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:verifyRoborazziDebug` ✔.

### 15.5 Wave 2

#### T7 — Goal use cases and Today stream (Phase 1)

**Depends on:** T1, T4. **Owns:** `core/data` `main/usecase/goal/**`, `main/usecase/today/**`, `main/usecase/onboarding/**`, matching tests; `core/data` `main/work/DayRolloverCoordinator.kt`.
**Deliver:** `BuildGoalInputUseCase`, `SnapshotDailyGoalUseCase`, `BackfillDailyGoalsUseCase`, `SaveProfileUseCase`, `SetGlobalOverrideUseCase`, `SetDayOverrideUseCase`, `ClearDayOverrideUseCase`, `ChangeDayStartUseCase`, `CompleteOnboardingUseCase`, `ObserveTodayUseCase` (pace is filled from `:core:domain` pace functions when reminders settings exist; it tolerates T2 not being merged by catching `NotImplementedError` → `pace = null`).
**Acceptance:** `SnapshotDailyGoalUseCaseTest` (writes `policyVersion`, `dayStartMinutes`, breakdown JSON; no write when unchanged; day override and global override modes; forecast present, absent with fallback flag; pinned activity level; reschedule called only when the target changes), `BackfillDailyGoalsUseCaseTest`, `ChangeDayStartUseCaseTest`, `ObserveTodayUseCaseTest` (Turbine: emits on entry, on goal change, on day rollover). `./gradlew :core:data:testDebugUnitTest` ✔.

#### T8 — Onboarding (Phase 1)

**Depends on:** T6, T7. **Owns:** all of `feature/onboarding` (keep the T0 signatures of `onboardingGraph` and `OnboardingIntegrations`).
**Deliver:** the flow of §13.2 including the disclaimer, the manual-mode recommendation, the notification rationale and the `POST_NOTIFICATIONS` request, resume after process death, and the "Welcome back" short variant (§11.11).
**Acceptance:** `OnboardingViewModelTest` (both paths; condition switch pre-selects manual; preview equals the calculator; completion writes profile or override, seeds containers, sets `completed`), `OnboardingFlowTest` (Compose, Robolectric: forward and back navigation), `OnboardingScreenshotTest` (every step × 3 variants). `./gradlew :feature:onboarding:testDebugUnitTest :feature:onboarding:verifyRoborazziDebug` ✔.

#### T9 — Today, edit sheet, containers, quick-log activity (Phases 1–2)

**Depends on:** T6, T7. **Owns:** all of `feature/today`, including its manifest (`QuickLogActivity`, intent action `casa.peterson.dhmo.action.QUICK_LOG`, not exported).
**Acceptance:** `TodayViewModelTest` (quick add, custom add, delete + undo effect, pace states, goal modes), `EditEntryViewModelTest`, `ContainersViewModelTest`, `TodayScreenTest` (Compose: tapping a container logs; swipe shows Undo), `TodayScreenshotTest` (empty, mid-day behind, goal met, override active, battery hint; × 3 variants), `QuickLogActivityTest`. `./gradlew :feature:today:testDebugUnitTest :feature:today:verifyRoborazziDebug` ✔.

#### T10 — History (Phases 1 and 3)

**Depends on:** T3, T4, T6. **Owns:** all of `feature/history`.
**Deliver:** paged list with day headers (total, goal, result colour), day detail, edit through `onEditEntry`, delete with Undo, anomaly hint from `anomalies()`, read-only rendering of imports with source label and a "Make editable" action calling `IntakeRepository.adopt` (visible only for imports, so it is inert until Phase 5).
**Acceptance:** `HistoryViewModelTest` (grouping by hydration day, anomaly flags, adopt), `HistoryScreenshotTest`, `HistoryScreenTest`. `./gradlew :feature:history:testDebugUnitTest :feature:history:verifyRoborazziDebug` ✔.

#### T11 — Profile and goal (Phase 1)

**Depends on:** T6, T7. **Owns:** all of `feature/profile`.
**Deliver:** the screens of §13.6, the breakdown list with the labels of §7.3, calculated versus effective goal, clamp note, disclaimer and sources, global override with optional per-weekday values, day override, adjustment toggles and pinned inputs, planned exercise for today (`ActivityRepository.addPlanned`).
**Acceptance:** `ProfileGoalViewModelTest`, `GlobalOverrideViewModelTest`, `BreakdownLabelTest` (every `ComponentKind` renders, in ml and in fl oz), `ProfileScreenshotTest`. `./gradlew :feature:profile:testDebugUnitTest :feature:profile:verifyRoborazziDebug` ✔.

#### T12 — Settings basics (Phase 1)

**Depends on:** T6, T7. **Owns:** `feature/settings` `main/root/**`, `main/units/**`, `main/appearance/**`, `main/stats/**`, `main/about/**`, their tests, `strings_settings.xml`.
**Deliver:** settings root (rows for every sub-screen; integration rows show availability from the controller interfaces), units and day start (through `ChangeDayStartUseCase`, with a confirmation explaining the transition day), appearance, stats settings, about (version, open-source licences, disclaimer, privacy policy link).
**Acceptance:** `SettingsViewModelTest`, `UnitsViewModelTest`, `SettingsScreenshotTest`. `./gradlew :feature:settings:testDebugUnitTest --tests '*root*' --tests '*units*' --tests '*appearance*'` ✔ and the module's `verifyRoborazziDebug` ✔.

#### T13 — Reminder engine (Phase 2)

**Depends on:** T2, T4, T7. **Owns:** all of `core/reminders` (including its manifest and `strings.xml`).
**Deliver:** §8.6–8.10; `BuildReminderStateUseCase` lives here.
**May touch:** nothing. `:app` already calls `ReminderNotifier.ensureChannels()`, which T0 created as an empty stub in this module.
**Acceptance:** the Robolectric tests listed at the end of §8.10, plus `BuildReminderStateUseCaseTest` and `MissedAlarmTest`; a manual test matrix `docs/testing/reminders-manual.md` (Doze, reboot, time-zone change, DST, app update, notification permission revoked) for the PLAN's Phase 2 exit criterion. `./gradlew :core:reminders:testDebugUnitTest :core:reminders:lintDebug` ✔; the merged manifest contains no exact-alarm permission (asserted by `ManifestPermissionsTest`).

#### T14 — Reminder settings UI (Phase 2)

**Depends on:** T2 (curve preview), T4, T6. **Owns:** all of `feature/reminders`.
**Deliver:** enable switch with permission handling, wake and sleep pickers with the window warnings, interval floor, tolerance, escalation, end-of-day summary, calendar opt-in (requests `READ_CALENDAR`), curve chooser and custom curve editor with validation, "next reminder at" line. Every change calls `ReminderScheduler.reschedule(SETTINGS_CHANGED)`.
**Acceptance:** `RemindersViewModelTest`, `PaceCurveEditorTest`, `RemindersScreenshotTest`. `./gradlew :feature:reminders:testDebugUnitTest :feature:reminders:verifyRoborazziDebug` ✔.

#### T15 — Insights (Phase 3)

**Depends on:** T3, T4, T6, T7. **Owns:** all of `feature/insights` (including `main/domain/LoadStatsRangeUseCase.kt` and `ObserveInsightsUseCase.kt`).
**Deliver:** Day (hourly bars against the expected-pace line), Week and Month (daily bars, goal line, result colours, average, days met, rolling overlays), Year (heatmap), Custom (range picker, optional comparison with the previous equal-length period), metric cards (attainment buckets, streaks, trend sentence, consistency, time-of-day, day-of-week), insights feed linking to charts. Charts use Vico; the correlation screen is a placeholder until T-COR.
**Acceptance:** `InsightsViewModelTest` (range arithmetic, caching, empty state, today excluded where §9.1 says so), `LoadStatsRangeUseCaseTest`, `InsightTextTest` (every `InsightKind` formats), `RangePickerTest` (Compose), `InsightsScreenshotTest` (each range × 3 variants, using dataset A). `./gradlew :feature:insights:testDebugUnitTest :feature:insights:verifyRoborazziDebug` ✔.

#### T16 — Export and local restore (Phase 3)

**Depends on:** T4, T5. **Owns:** `core/data` `main/backup/**` (`OfflineBackupRepository`, mappers between entities and DTOs, safety snapshots) and tests; `feature/settings` `main/export/**`, `main/backupfile/**`, tests, `strings_export.xml`.
**Deliver:** CSV (entries, daily totals) and JSON export through `ACTION_CREATE_DOCUMENT`; "Restore from file" through `ACTION_OPEN_DOCUMENT` with replace or merge, passphrase prompt for encrypted files, safety snapshot and "Undo last restore".
**Acceptance:** `OfflineBackupRepositoryTest` (export then replace-restore into an empty database reproduces every table row for row, which is the PLAN's "100% of the data" criterion; merge rules against a seeded database; settings sanitising; `sync_state` and `hc_pending_op` never exported), `ExportViewModelTest`, `ExportScreenshotTest`. `./gradlew :core:data:testDebugUnitTest --tests '*backup*' :feature:settings:testDebugUnitTest --tests '*export*'` ✔.

### 15.6 Wave 3

#### T17 — Integration, end-to-end tests, first PR (Phases 1–3)

**Depends on:** T1–T16. **Owns:** `app/src/androidTest/**`, `README.md`, `docs/testing/**` (except files other tasks created).
**May touch:** `app` `DhmoNavHost.kt` and `MainActivity.kt` for integration fixes only; any module for bug fixes found by the end-to-end tests (each listed in the PR description).
**Deliver:** instrumented flows of §14.1; an accessibility pass (content descriptions, 48 dp targets, font scale 200%) on the Phase 1–3 screens; removal of any leftover `TODO("T…")`; the pull request.
**Acceptance:** full CI command set of §14.2 ✔; `./gradlew pixel6Api34DebugAndroidTest` ✔ where an emulator is available (otherwise through `instrumented.yml`); `grep -R 'TODO("T' --include=*.kt .` finds only Phase 4–6 stubs.

### 15.7 Wave 4 — Phase 4, Drive backup (separate PR)

#### T-DR1 — Google auth, Drive REST client, key store

**Depends on:** T17 merged. **Owns:** `sync/drive` `main/auth/**`, `main/api/**`, `main/crypto/BackupKeyStore.kt`, tests, the module's `build.gradle.kts` and manifest.
**Deliver:** §11.5 (`GoogleAuthManager` with the Credential Manager sign-in and `AuthorizationClient` flows, exposing a `rememberDriveAuthLauncher()` composable), §11.6 (`DriveApi`), Keystore wrapping of the backup key (§11.4), `BuildConfig.GOOGLE_WEB_CLIENT_ID`.
**Acceptance:** `DriveApiTest` (`MockWebServer`: each of the five requests byte-for-byte on path, query and multipart structure; pagination; 401 retry once; quota, 404-on-delete and 5xx mapping), `BackupKeyStoreTest` (Robolectric), `GoogleAuthManagerTest` (resolution required → `NeedsReconnect`; blank client id → `NotConfigured`). `./gradlew :sync:drive:testDebugUnitTest` ✔. The requested scope list contains only `drive.appdata` (asserted).

#### T-DR2 — Backup worker, retention, controller

**Depends on:** T-DR1. **Owns:** `sync/drive` `main/work/**`, `main/DriveBackupControllerImpl.kt`, `main/notification/**`, tests.
**Deliver:** §11.7, pruning with `Retention.select`, the "Reconnect Google Drive" notification, restore download + decode + `BackupRepository.restore`.
**Acceptance:** `DriveBackupWorkerTest` (`TestListenableWorkerBuilder` + `MockWebServer`: success path uploads then prunes; network failure → retry; auth failure → failure + notification + `needsReconnect`; encrypted upload decrypts with the passphrase), `DriveBackupControllerTest` (schedule matches settings: daily / weekly, unmetered / any; cancel when disabled), `DriveRoundTripTest` (back up from a seeded database, restore into an empty one, tables equal). `./gradlew :sync:drive:testDebugUnitTest` ✔.

#### T-DR3 — Backup and restore UI, onboarding restore offer

**Depends on:** T17 merged (uses the `DriveBackupController` interface and `FakeDriveBackupController`; no dependency on DR1 or DR2 code except the `rememberDriveAuthLauncher()` composable, which T0 already created as a stub in `sync/drive` `main/auth/`). **Owns:** `sync/drive` `main/ui/**` and tests, `feature/settings` `strings_backup.xml`, `feature/onboarding` `main/restore/**`.
**May touch:** `app/…/DhmoNavHost.kt`: replace the default `SettingsIntegrations()` arguments `driveBackupContent` and `driveRestoreContent`, and the `OnboardingIntegrations` restore slot (three lines).
**Acceptance:** `DriveBackupViewModelTest`, `RestoreListViewModelTest` (encrypted prompt, newer-version rows disabled, replace versus merge confirmation), `DriveBackupScreenshotTest`. `./gradlew :sync:drive:testDebugUnitTest :sync:drive:verifyRoborazziDebug :app:assembleDebug` ✔.

### 15.8 Wave 5 — Phase 5, Health Connect and weather (separate PR)

#### T-HC1 — Health Connect sync engine

**Depends on:** T17 merged. **Owns:** `sync/healthconnect` `main/engine/**`, `main/mapping/**`, tests, the module's `build.gradle.kts`.
**Deliver:** §10.5 and §10.6 engine logic (push, pull, reconcile, dedupe, adoption rules, weight, exercise, daily aggregates).
**Acceptance:** `HcPushTest`, `HcPullTest`, `HcTokenExpiryTest`, `HcAdoptionTest`, `HcDedupeTest`, `HcWeightExerciseTest`, `HcPermissionTest` (§10.8). `./gradlew :sync:healthconnect:testDebugUnitTest` ✔.
**Notes:** `connect-testing` is declared as `testImplementation(libs.androidx.health.connect.testing) { exclude(group = "androidx.health.connect", module = "connect-client") }` (§2.3).

#### T-HC2 — Workers, controller, manifest, rationale activity

**Depends on:** T-HC1. **Owns:** `sync/healthconnect` `main/work/**`, `main/HealthConnectControllerImpl.kt`, `main/rationale/**`, `main/HcEntryListener.kt`, the manifest (enable the §10.3 entries), tests.
**Acceptance:** `HealthConnectSyncWorkerTest`, `HealthConnectControllerTest` (state machine transitions of §10.4, periodic work only with background permission, backfill enqueues every live non-import entry), `ManifestTest` (rationale intent filter and activity-alias present). `./gradlew :sync:healthconnect:testDebugUnitTest :app:lintRelease` ✔.

#### T-HC3 — Health Connect settings UI

**Depends on:** T17 merged (interface + fake only). **Owns:** `sync/healthconnect` `main/ui/**` and tests, `feature/settings` `strings_healthconnect.xml`, `docs/testing/health-connect-manual.md`.
**May touch:** `app/…/DhmoNavHost.kt`: set `healthConnectContent` (one line).
**Deliver:** availability states, one switch per row of §10.1 with its own permission request, source-app list, sync status and "Sync now", install / update prompt.
**Acceptance:** `HealthConnectSettingsViewModelTest` (denied permission reverts the switch; excluded app; unavailable and update-required states), `HealthConnectScreenshotTest`. `./gradlew :sync:healthconnect:testDebugUnitTest :sync:healthconnect:verifyRoborazziDebug` ✔.

#### T-WX1 — Weather provider, location, worker

**Depends on:** T17 merged. **Owns:** all of `core/weather`.
**Deliver:** §12.2–12.5 (`OpenMeteoProvider`, `LocationRefresher`, `WeatherWorker`, `WeatherControllerImpl`).
**Acceptance:** the tests listed at the end of §12.5. `./gradlew :core:weather:testDebugUnitTest` ✔; the merged manifest has `ACCESS_COARSE_LOCATION` and neither fine nor background location (asserted).

#### T-WX2 — Weather settings UI, city search, onboarding rationale

**Depends on:** T17 merged (interfaces + fakes only). **Owns:** `feature/settings` `main/weather/**`, tests, `strings_weather.xml`; `feature/onboarding` `main/weather/**`.
**May touch:** `app/…/DhmoNavHost.kt`: set `weatherLocationPermission` and the onboarding weather slot (two lines); `app/…/MainActivity.kt`: call `WeatherController.refreshIfStale()` and `LocationRefresher.refreshIfForeground()` in `onStart` (two lines; T0 leaves a marked hook).
**Acceptance:** `WeatherSettingsViewModelTest` (mode changes, permission denied keeps mode off, city search debounce and selection stores rounded coordinates only), `CitySearchScreenshotTest`, `WeatherRationaleScreenshotTest`. `./gradlew :feature:settings:testDebugUnitTest --tests '*weather*'` ✔.

#### T-COR — Correlations

**Depends on:** T-HC2, T-WX1. **Owns:** `feature/insights` `main/correlation/**` and tests.
**Deliver:** the correlation screen (scatter plot, plain-language description, n and strength) and wiring of the two correlation insights.
**Acceptance:** `CorrelationViewModelTest` (dataset G → strong positive; fewer than 14 pairs → "not enough data"), `CorrelationScreenshotTest`. `./gradlew :feature:insights:testDebugUnitTest --tests '*correlation*'` ✔.

### 15.9 Later phases (outline only)

Phase 6: `T-W1` Glance widgets (2×1, 4×2) and `WidgetUpdater`; `T-W2` Quick Settings tile and app shortcuts; `T-PDF` PDF report in `:feature:settings` `export/pdf`; `T-A11Y` Accessibility Scanner pass and localization scaffolding. Phase 7: Play declarations, privacy policy, OAuth verification. Wear OS stays out of scope (issue #1).
