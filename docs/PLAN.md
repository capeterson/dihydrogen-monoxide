# Dihydrogen Monoxide: Android Hydration Tracker Plan

**Dihydrogen Monoxide** (also the public app name) is a free Android app that logs how much water you drink. It works out a daily target from personal factors (which you can override), reminds you when you fall behind, and analyses your history. It backs up to Google Drive and syncs with Health Connect.

## Product decisions (confirmed)

| Topic | Decision |
|---|---|
| Monetisation | **Free.** No ads, no in-app purchases, no Play Billing. |
| What is tracked | **Water only.** No beverage types and no hydration factors. |
| Goal basis | **Drinking water only, not water from food.** |
| Weather adjustment | **In v1, opt-in.** Uses a location permission or a manually chosen city (§4.4). |
| Wear OS | Nice to have, after v1. A first companion (tile and complication, `:wear`) was added for [#1](https://github.com/capeterson/dihydrogen-monoxide/issues/1); a stand-alone watch reminder engine and a full watch app remain out of scope. |
| Multiple devices | **Backup and restore is enough.** No live sync. |
| Name | "Dihydrogen Monoxide" is the public name. |

---

## 1. Goals and non-goals

**Goals**
- Logging water takes one tap from a notification, widget, Quick Settings tile or the app.
- A recommended daily target that shows how it was worked out and responds to age, sex, weight, pregnancy or lactation, activity and the day's weather.
- Manual overrides at every level: a fixed global target, a target for one day, or adjusted inputs.
- Smart reminders that only fire when you're behind pace, respect quiet hours, and can log a drink straight from the notification.
- Reporting and trend analysis over any range: totals, how often the goal is met, streaks, time-of-day patterns, trends and correlations.
- Backup to and restore from Google Drive.
- Two-way hydration sync with Health Connect, plus reading weight and activity data to inform the target.

**Non-goals for v1**
- Medical advice or clinical fluid management. The app says this plainly, and the target is guidance only.
- Live sync between devices. Drive is a backup, not a sync engine, and that is the final scope.
- Tracking coffee, tea, juice or other drinks. The app tracks water only.
- Monetisation: no ads, purchases or subscriptions.
- iOS or web clients.

---

## 2. Platform and tech stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin (K2), coroutines and Flow | |
| UI | Jetpack Compose and Material 3, including dynamic color | Adaptive layouts for tablets and foldables |
| Architecture | MVVM with unidirectional data flow; a domain layer of use cases | |
| DI | Hilt | |
| Persistence | Room (SQLite) for records; Proto DataStore for settings | Room migrations are tested (§10) |
| Background work | WorkManager for backup, sync and rescheduling; AlarmManager for reminder times | See §5 for the exact-alarm policy |
| Serialization | kotlinx.serialization | Backup format and DataStore |
| Charts | Vico (Compose-native) | Fallback: a custom Canvas for the heatmap |
| Widgets | Jetpack Glance | Home-screen quick-add and progress |
| Health | `androidx.health.connect:connect-client` | Health Connect |
| Google auth | Credential Manager (sign-in) and Identity `AuthorizationClient` (Drive scope) | The legacy `GoogleSignIn` API is deprecated, so don't use it |
| Drive | Drive REST API v3 using the `drive.appdata` scope | Backups go to the hidden app-data folder |
| Build | Gradle version catalogs, convention plugins, Kotlin DSL | |
| **minSdk / targetSdk** | **28 / latest stable** | Health Connect needs API 28+. It ships as a Play app on Android 9–13 and is built in from Android 14. |

### Module layout
```
:app                      – entry point, navigation, DI graph
:core:model               – pure Kotlin domain types
:core:database            – Room entities, DAOs, migrations
:core:datastore           – settings (proto)
:core:domain              – use cases: goal calculator, pace, stats
:core:designsystem        – theme, components
:feature:today            – main logging screen
:feature:history          – log list, edit/delete
:feature:insights         – reporting and trend analysis
:feature:profile          – profile and goal configuration
:feature:reminders        – reminder settings
:feature:settings         – units, backup, integrations, about
:sync:healthconnect       – Health Connect adapter and sync worker
:sync:drive               – Drive backup/restore adapter and worker
:widget                   – Glance widget and Quick Settings tile
```
Goal calculation and statistics stay in pure Kotlin (`:core:domain`) so they can be unit-tested quickly without Android.

---

## 3. Data model

All volumes are stored as **integer millilitres**. Units are converted only when displayed (ml, L, US fl oz, imperial fl oz).

### Time handling
- Every entry stores an `Instant` plus the `ZoneOffset` that applied when it was logged. History therefore stays correct after travel or a DST change.
- **The start of the day can be configured** (default 00:00, which can be moved to, say, 04:00 for night owls). The app maps each entry to a "hydration day" with `(instant + offset - dayStart).toLocalDate()`. Each day's goal snapshot records that day's boundary.

### Entities (Room)

```kotlin
IntakeEntry(
  id: UUID,                    // also Health Connect clientRecordId
  instant: Instant,
  zoneOffset: ZoneOffset,
  volumeMl: Int,
  source: Source,              // MANUAL, WIDGET, NOTIFICATION, TILE, HEALTH_CONNECT_IMPORT, WEAR
  hcRecordId: String?,         // Health Connect record id once written/imported
  hcDataOrigin: String?,       // package name for imported records
  note: String?,
  createdAt: Instant,
  updatedAt: Instant,
  deletedAt: Instant?          // tombstone, needed for Health Connect delete propagation and backup merges
)

Container(id, name, volumeMl, icon, sortOrder)   // quick-add presets: "Glass 250", "Bottle 750"

ProfileSnapshot(id, effectiveFrom: LocalDate, birthDate, sex, weightKg, heightCm?,
                activityLevel, climate, pregnancyStatus, lactating, source)
// A new row is added whenever an input changes, so a past goal can be recomputed exactly.
// Health Connect weight imports add rows with source = HEALTH_CONNECT.

DailyGoal(date: LocalDate PK, targetMl, calculatedMl, mode: CALCULATED|GLOBAL_OVERRIDE|DAY_OVERRIDE,
          breakdownJson, dayStart: LocalTime)
// Snapshot written when the day begins and updated if its inputs change during the day.
// Reports always use the snapshot, so raising today's goal never rewrites last month's percentages.

DayOverride(date PK, targetMl, reason?)

ActivityAdjustment(date, sourceRecordId, minutes, extraMl, source)   // from Health Connect exercise sessions

WeatherDay(date PK, latRounded, lonRounded, maxTempC, maxApparentTempC, meanRelHumidity,
           extraMl, fetchedAt)   // used by the weather adjustment (§4.4) and correlations (§7.2)
```

### Settings (Proto DataStore)
Units, day start, global goal override, reminder configuration, backup configuration, Health Connect toggles, weather mode (off, device location, or manual city with its coordinates), theme and onboarding state.

### Derived views
A Room `@DatabaseView` or materialised `DailyTotal(date, totalMl, effectiveMl, entryCount, firstAt, lastAt)` keeps reports fast across years of data. It is rebuilt incrementally whenever entries change.

---

## 4. Recommended intake calculation

### 4.1 Principles
- **Transparent.** The goal screen lists each component, for example *"Base 2,310 ml (33 ml × 70 kg), +500 ml exercise, +300 ml hot weather = 3,110 ml"*.
- **Evidence-anchored, with sources cited in the app.** It uses the EFSA (2010) and US National Academies (2004) adequate intakes and ACSM exercise guidance.
- **Bounded.** Results are clamped to sane limits (for example 1,000–6,000 ml for adults). Anything outside those limits shows a "consult a clinician" note. For people with heart failure, kidney disease or a prescribed fluid restriction, the app recommends manual mode during onboarding.
- **Drinking water only, not food.** Published adequate intakes cover all water, including the roughly 20% that comes from food. The calculator targets **drinking water only**, at about 80% of total water, and says so on screen. There is no setting for total water.

### 4.2 Algorithm (v1)

```
baseline =
  if age < 14: age-band table (EFSA, drinks only)
  else max( weightKg × perKgFactor(age),  sexAgeFloor )        // weight-scaled, but never below the population AI

perKgFactor(age):  14–30 → 35 ml/kg   31–55 → 33 ml/kg   56–65 → 30 ml/kg   >65 → 28 ml/kg
sexAgeFloor (drinks):  female ≈ 1,600 ml   male ≈ 2,000 ml    (EFSA total AI 2.0/2.5 L × 0.8)

+ pregnancy:   +300 ml     (EFSA)
+ lactation:   +700 ml     (EFSA)
+ activity:    planned or detected exercise → +~12 ml per active minute (~350 ml per 30 min),
               scaled by intensity; or a level-based default (sedentary 0 / light +250 / moderate +500 / very active +750)
+ climate:     automatic from the day's forecast when weather is on (§4.4);
               otherwise a manual setting: temperate 0 / warm +250 / hot or humid +500
+ altitude:    optional manual +250 above roughly 2,500 m

goal = roundTo50( clamp(baseline + adjustments, min, max) )
```

All constants live in one `GoalPolicy` object that is versioned, so changing the formula later doesn't silently change historical snapshots. Each snapshot stores its `policyVersion`.

### 4.3 Override hierarchy
Highest priority first:
1. **Day override.** "Today I'm hiking, set 4 L."
2. **Global manual goal.** A fixed number every day, ignoring the calculator. Optionally a separate value for each weekday.
3. **Calculated goal.** The default. Individual inputs can be pinned. For example, if weight sync is turned off, the manually entered weight is always used.
4. **Adjustment toggles.** Each adjustment (activity, weather, pregnancy and so on) can be switched on or off independently.

The UI always shows both the **calculated** and the **effective** goal, so overriding never hides what the calculator suggests.

### 4.4 Weather adjustment (opt-in)
Weather is off by default. Onboarding and Settings offer two ways to turn it on:

1. **Device location.** The app asks for `ACCESS_COARSE_LOCATION` only. It never requests precise or background location. It reads a location with `FusedLocationProviderClient.getCurrentLocation(PRIORITY_BALANCED_POWER_ACCURACY)` **only while the app is in the foreground**, and caches it. The daily worker reuses the cached location, so `ACCESS_BACKGROUND_LOCATION` is never needed.
2. **Manual city.** The user searches for a city and the app saves its coordinates. This needs no permission, and suits people who don't want to grant location access or who live in one place.

**Forecast source.** The [Open-Meteo](https://open-meteo.com) forecast API needs no API key and is free for non-commercial use, which fits a free app. Coordinates are rounded to 0.1° (about 11 km) before they're sent, and nothing else is sent. A thin `WeatherProvider` interface keeps the provider replaceable.

**Fetch schedule.** One request per day, made by a WorkManager job shortly after the day starts and refreshed when the app opens if the data is more than 6 hours old. If the request fails, the app uses the manual climate setting for that day and says so on the goal screen.

**Adjustment,** from the day's maximum *apparent* temperature (which combines heat and humidity):

| Max apparent temp | Extra |
|---|---|
| < 25 °C | +0 ml |
| 25–29 °C | +250 ml |
| 30–34 °C | +500 ml |
| ≥ 35 °C | +750 ml |

The goal breakdown shows the reason, for example *"+500 ml: hot day (feels like 32 °C)"*. The forecast is also saved in `WeatherDay` so the insights can relate intake to temperature.

**Privacy.** The Play Data safety form declares approximate location, shared with a weather service only to get the forecast. The rationale screen explains this before the permission prompt.

---

## 5. Reminders

### 5.1 Behaviour
- **Pace-aware.** The app computes an *expected-by-now* curve between the configured wake and sleep times. The default is a linear ramp with a small front-load, and a custom curve is possible. A reminder fires only if `actual < expected − tolerance`. If you're ahead, it stays quiet.
- **Interval floor.** It never reminds more often than every N minutes (default 60), and it measures from the **last drink logged**, not the last reminder.
- **Quiet hours and calendar awareness.** It respects the wake/sleep window, has an optional "don't remind during calendar events marked busy" setting (needs the calendar permission, so it's opt-in), and respects Do Not Disturb automatically through the notification channel.
- **Goal met.** Once the goal is reached, reminders stop for the day. An optional end-of-day summary can still be sent.
- **Escalation.** An optional gentle nudge goes out if you're more than 40% behind with two hours of wake time left.

### 5.2 Notification actions
- `+ Glass (250 ml)` and `+ Bottle (500 ml)` show the user's top two containers. They log the drink through a `BroadcastReceiver` without opening the app, then update the widget, Health Connect and the next reminder.
- `Snooze 30 min`
- `Custom…` opens a small quick-log dialog activity.

### 5.3 Scheduling implementation
- The app keeps **only the next reminder** scheduled. Each time it fires, or whenever an entry changes, `ReminderScheduler` recomputes the next time.
- It uses `AlarmManager.setAndAllowWhileIdle()` (inexact, Doze-safe). Hydration reminders don't need minute precision, so the app **does not request `SCHEDULE_EXACT_ALARM`**. Play policy restricts `USE_EXACT_ALARM` to alarm-clock and calendar apps anyway.
- It reschedules on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and `MY_PACKAGE_REPLACED`.
- The `POST_NOTIFICATIONS` runtime permission (Android 13+) is requested during onboarding, after a screen explaining why it's needed.
- Notification channels: `reminders` (default importance), `summaries` (low) and `backup` (min, errors only).

---

## 6. Logging UX

- **Today screen.** A large progress ring shows the percentage and the volume. Pace status reads "On track", "250 ml behind" or "Ahead". Quick-add buttons appear for containers, a custom-amount sheet offers a slider and numeric entry, and today's timeline can be swiped to edit or delete with an Undo snackbar.
- **Glance widget,** in two sizes:
  - 2×1: progress, plus one-tap add for the default container.
  - 4×2: progress, the last drink, and three container buttons.
- **Quick Settings tile.** Tapping it logs the default container. A long press opens the app.
- **App shortcuts** for the static and dynamic top containers.
- **Accessibility.** TalkBack labels on every control, a progress ring that announces its percentage, touch targets of at least 48 dp, and support for font scaling up to 200%.
- **Wear OS** (later phase): a tile and a complication with quick-add that syncs through the Data Layer API.

---

## 7. Reporting and trend analysis

### 7.1 Views
| View | Content |
|---|---|
| **Day** | Hourly bars against the expected-pace line, and an entry list |
| **Week / Month** | Daily bars with a goal line, colour-coded by goal met, partly met (≥80%) or missed; average; days met |
| **Year** | Calendar heatmap (GitHub style) of the percentage of goal |
| **Custom range** | Any start and end date, with an optional comparison against the previous equivalent period |

### 7.2 Metrics (each is a pure function in `:core:domain/stats`)
- Total, mean, median and standard deviation of daily intake. Days without logging are excluded from averages by default, with a setting to change that.
- **Goal attainment rate:** the percentage of days with at least 100% of the goal, plus the distribution in 0–50 / 50–80 / 80–100 / ≥100% buckets.
- **Streaks:** current and longest run of days meeting the goal, plus a logging streak (days with any entry). An optional "freeze" per week keeps a streak alive.
- **Rolling averages:** 7-day and 30-day, drawn as overlays on the charts.
- **Trend:** the slope in ml/day per week, using a Theil–Sen estimator because it resists outliers better than ordinary least squares. It is shown as plain language, for example "Up 120 ml/day over the last 8 weeks". The direction only counts as significant when the Mann–Kendall test gives p < 0.05. Otherwise the app reports "Stable".
- **Consistency score:** 1 − the coefficient of variation of daily intake over 30 days, shown on a scale of 0–100.
- **Time-of-day profile:** the average cumulative curve and the median time to reach 50% and 100% of the goal. Flags patterns like "You drink 45% of your water after 6 pm".
- **Day-of-week pattern:** mean intake per weekday, highlighting the weakest day.
- **Correlations,** when the data exists: intake against active minutes, steps and weight trend (from Health Connect), and against the day's temperature (from the weather adjustment). They are shown as scatter plots with a plain-language description, and the app avoids implying causation.
- **Anomalies:** days more than 2.5 robust standard deviations from the 30-day median (using MAD), flagged in the history so logging mistakes stand out, such as a 5 L entry that was meant to be 500 ml.

### 7.3 Insights feed
Short, rule-based observations with no ML in v1, for example:
"Your weekend average is 22% lower than weekdays." "You've hit your goal 6 of the last 7 days." "Afternoons (2–5 pm) are your biggest gap." Each insight comes from the metrics above and links to the chart that supports it.

### 7.4 Export
- **CSV:** raw entries and daily totals, through the Storage Access Framework (`ACTION_CREATE_DOCUMENT`).
- **PDF report:** a summary for a chosen range with charts, rendered with `PdfDocument`. Useful for sharing with a clinician or coach.
- **JSON:** the full export, in the same format as the Drive backup.

### 7.5 Performance
- Aggregations run on `DailyTotal` rather than raw entries. Ten years is only about 3,650 rows, so everything can be computed in memory.
- Statistics are computed on `Dispatchers.Default` and cached in each ViewModel by range.

---

## 8. Google Drive backup

### 8.1 Approach
Two complementary layers:

1. **Android Auto Backup.** On by default, needs no setup, and uses the user's device backup to Google. Configured with `dataExtractionRules`, it includes the Room DB and DataStore and excludes caches. It gives free disaster recovery when the user moves to a new phone, but it's opaque and the app can't control it.
2. **Explicit Drive backup** (the feature the user turns on). This is the main backup:
   - **Auth.** Credential Manager handles Google sign-in. `Identity.getAuthorizationClient(context).authorize(...)` then requests only `https://www.googleapis.com/auth/drive.appdata`.
   - **Storage.** Files go in the **appDataFolder**. They're hidden from the user's Drive UI, count towards their quota, and are readable only by this app's OAuth client. `drive.appdata` is a non-restricted scope, so the app avoids the expensive security assessment that full-Drive scopes need. The OAuth consent screen still needs to be verified before public launch.
   - **Format.** `hydration-backup-v{schema}-{timestamp}.json.gz` holds a versioned JSON document with every entity, including tombstones and settings (minus device-local items such as tokens). JSON is portable across schema versions and doubles as the export format. A raw SQLite file copy isn't.
   - **Optional encryption.** When the user sets a passphrase, the app derives a key with PBKDF2 or Argon2 and encrypts with AES-GCM. The app warns that a forgotten passphrase means the backup can't be recovered.
   - **Schedule.** A `PeriodicWorkRequest` runs daily (configurable to weekly) with the `NetworkType.UNMETERED` constraint (configurable) and the `requiresBatteryNotLow` constraint. "Back up now" is available on demand.
   - **Retention.** The app keeps the last 7 daily, 4 weekly and 6 monthly backups, configurable, and prunes older files.
   - **Restore.** The app lists the backups it finds, showing date, device and number of entries. Restore can **replace** local data or **merge** it, where merge takes the union by UUID and the later `updatedAt` wins, with tombstones respected. Before any restore the app takes a local safety snapshot.
   - **New-device flow.** If onboarding finds backups on the signed-in account, it offers to restore one.
- **Failure handling.** Exponential backoff. A token revoked by `UserRecoverableAuthException` or by a `PendingIntent` from the authorization result produces a "Reconnect Google Drive" notification. The time of the last successful backup is shown in Settings.

### 8.2 Libraries
Either `google-api-services-drive` with `google-http-client-android`, or (lighter) a thin Ktor or OkHttp client calling the REST endpoints with the access token from `AuthorizationResult`. **Recommendation: the thin REST client.** The Java client brings a heavy dependency tree and its own credential model, and the app only needs about five endpoints: list, create (multipart upload), get media, delete, and get about/quota.

---

## 9. Health Connect integration

Health Connect has replaced the Google Fit APIs, whose Android and REST APIs are deprecated and being shut down. It is the supported on-device health data hub on Android.

### 9.1 Data types and permissions
| Record | Access | Purpose |
|---|---|---|
| `HydrationRecord` | Read and write | Two-way sync of drink entries |
| `WeightRecord` | Read | Keep the profile weight up to date |
| `ExerciseSessionRecord` | Read | Activity adjustment to the goal |
| `StepsRecord` / `ActiveCaloriesBurnedRecord` | Read (optional) | Fallback activity signal; correlations |
| `READ_HEALTH_DATA_IN_BACKGROUND` | Optional | Sync in the background, not only while the app is open (where the platform supports it) |
| `READ_HEALTH_DATA_HISTORY` | Optional | Import hydration from more than 30 days before the permission was granted |

Each permission is requested in context and on its own. For example, the weight permission is requested only when the user turns on "sync weight". The app declares the rationale activity with `ACTION_SHOW_PERMISSIONS_RATIONALE`, plus the `VIEW_PERMISSION_USAGE` / `HEALTH_PERMISSIONS` activity alias for Android 14+.

### 9.2 Sync design
- **Writing.** Every local entry that is created, edited or deleted is queued. A `HealthConnectSyncWorker` inserts or updates `HydrationRecord(startTime = endTime = instant, volume)`, using `Metadata` with `clientRecordId = entry.id` and `clientRecordVersion = updatedAt.toEpochMilli()`. Health Connect then upserts idempotently. Deletes go through `deleteRecords` by `clientRecordId`.
- **Reading.** The worker uses the **Changes API**. It stores a changes token, and each sync calls `getChanges(token)`. Upserts from other apps (`dataOrigin != ourPackage`) are imported as `IntakeEntry(source = HEALTH_CONNECT_IMPORT)`. Records written by this app are ignored. Deletions remove the matching imports. If the token has expired, the worker falls back to a time-range read and a reconciliation.
- **Triggers.** Sync runs when the app comes to the foreground, after each local change (debounced), and periodically every 15 minutes or more through WorkManager. The periodic run only applies when the background-read permission has been granted.
- **Non-water imports.** A `HydrationRecord` doesn't say what was drunk, so another app's records might include coffee or juice. Imports are therefore **off by default**. A setting ("Count water logged in other apps") turns them on, with a list of the source apps so each can be included or excluded.
- **Conflicts.** Imported entries are read-only in the app by default and show the source app's name. The user can choose to "adopt" one and make it editable.
- **Availability.** `HealthConnectClient.getSdkStatus()` decides whether to show "Install or update Health Connect" (which opens the Play Store on Android 9–13) or to hide the feature.

### 9.3 "Google Health"
Health Connect is the integration point on the device. Google's consumer health apps (Fitbit, Pixel Watch and others) read and write through it, so supporting Health Connect is how this app's data reaches them. Google's newer cloud health APIs aim at server-side access and add little for an app that runs on the device. The plan is to watch for a hydration-specific cloud API and not build against one in v1.

### 9.4 Testing
`androidx.health.connect:connect-testing` provides `FakeHealthConnectClient` for unit tests of the sync worker, covering inserts, the token loop, expired tokens and deletion propagation.

---

## 10. Quality, privacy and release

### Testing strategy
- **Unit tests (JVM):** the goal calculator (a table-driven test per profile archetype, with boundary ages and weights), the pace curve, the reminder decision logic, every statistic (golden datasets that include DST transitions and changes to the day start), and backup serialization round-trips across schema versions.
- **Room:** migration tests with `MigrationTestHelper` and exported schemas.
- **Workers:** `WorkManagerTestInitHelper` for backup and sync.
- **UI:** Compose UI tests for logging, override flows and the insights range picker, plus screenshot tests (Roborazzi or Paparazzi) in light, dark and large-font modes.
- **CI:** GitHub Actions running lint, detekt, ktlint, unit tests and assembleRelease, with a managed-device job for instrumented tests.

### Privacy
- No analytics, ads or purchases. Crash reporting, if added, is opt-in.
- All data stays on the device, in the user's own Drive appDataFolder, or in Health Connect. No server belongs to the app. The only other network call is the opt-in weather request, which sends rounded coordinates.
- Before release: the Play **Data safety** form, the **Health apps declaration**, the **Health Connect permissions declaration form** (Play reviews each requested data type), and a hosted privacy policy linked from the rationale activity.
- A medical disclaimer appears at onboarding and on the goal screen.

### Localization
All strings go in resources from day one. Formatting is locale-aware for numbers, units, dates and first day of the week. US fluid ounces are the default when the locale is en-US.

---

## 11. Phased roadmap

| Phase | Scope | Exit criteria |
|---|---|---|
| **0. Foundation** (~1 wk) | Project scaffold, modules, CI, design system, Room schema v1, DataStore | CI green; empty app runs |
| **1. MVP logging** (~2 wks) | Onboarding and profile, goal calculator and overrides, Today screen, containers, history with edit, units, day start | Can log a day end to end; calculator fully unit-tested |
| **2. Reminders** (~1 wk) | Pace-aware scheduler, actionable notifications, quiet hours, reboot handling | Reminders behave correctly across Doze, reboot and timezone changes (manual test matrix) |
| **3. Insights** (~2 wks) | Day/week/month/year views, metrics, trends, heatmap, insights feed, CSV/JSON export | Stats verified against golden datasets |
| **4. Drive backup** (~1.5 wks) | Auth, appDataFolder backup and restore, retention, encryption, new-device restore | Round-trip restore on a clean device keeps 100% of the data |
| **5. Health Connect and weather** (~2 wks) | Hydration two-way sync, weight import, exercise-based goal adjustment, opt-in weather adjustment (location or manual city), correlations | Fake-client tests pass; verified against the Fitbit and Samsung Health apps on real devices; weather falls back cleanly when offline or permission is denied |
| **6. Surfaces and polish** (~1.5 wks) | Glance widgets, QS tile, shortcuts, PDF report, accessibility pass, localization scaffolding | Accessibility Scanner clean; screenshot tests across themes |
| **7. Release** (~1 wk) | Play declarations, privacy policy, OAuth verification, closed testing track | Approved for production |
| **Later** | Wear OS tile and complication ([#1](https://github.com/capeterson/dihydrogen-monoxide/issues/1)); smart-bottle BLE integrations | |

Rough total for v1: **about 12 weeks for one experienced Android developer.**

---

## 12. Key risks and mitigations

| Risk | Mitigation |
|---|---|
| Play review of Health Connect permissions is strict | Request the minimum set and ship hydration-only first if review stalls. Weight and exercise are additive. |
| OAuth verification delays the Drive feature | The non-restricted `drive.appdata` scope helps. Start verification early, in parallel with phase 3. Auto Backup covers the gap. |
| OEM battery killers (Xiaomi, Huawei, Samsung) suppress reminders | Detect missed alarms (a fire time much later than scheduled), show a guided "allow background activity" screen, and link to dontkillmyapp-style instructions. |
| The calculator is mistaken for medical advice | Show the breakdown, cite sources, add disclaimers, and steer users with relevant conditions to manual mode. |
| Duplicates between the app and other apps in Health Connect | `clientRecordId` idempotency, ignoring the app's own data origin on read, and a dedupe heuristic for near-identical imports. |
| Timezones and DST corrupt day totals | Store the offset with every entry, snapshot each day's boundary, and test with golden data covering DST transitions. |
| The weather service is down or changes its terms | Fall back to the manual climate setting. The `WeatherProvider` interface makes it easy to switch providers. |

---

## 13. Open questions

None at the moment. The product decisions are listed at the top of this document.
