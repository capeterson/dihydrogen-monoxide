# Dihydrogen Monoxide

A free Android app that logs how much water you drink, works out a daily goal from personal factors (which you can override), reminds you when you fall behind, and analyses your history. No ads, no purchases, no account.

The product plan is [`docs/PLAN.md`](docs/PLAN.md); the engineering plan is [`docs/TECHNICAL_PLAN.md`](docs/TECHNICAL_PLAN.md).

## What works today (Phases 1–3)

| Area | Behaviour |
|---|---|
| Onboarding | Disclaimer, units and day start, a calculated or self-set goal, optional reminders. |
| Today | Progress ring, quick-add buttons for your containers, custom amounts, edit and delete with Undo. With TalkBack, each drink offers **Edit** and **Delete** actions instead of swiping. Reminder notifications can log a drink without opening the app. |
| Goal | A calculated goal with its breakdown, a fixed goal (optionally per weekday), or a goal for just today. Adjustments can be switched off one by one. The goal is guidance, not medical advice. |
| Reminders | Pace-aware: a reminder fires only when you are behind the expected curve, inside your waking hours, no more often than your chosen interval. Notifications can log a drink or snooze. |
| History | Every drink grouped by day, with editing and Undo. |
| Insights | Day, week, month, year and custom ranges; goal attainment, streaks, trend, consistency, time of day and day of week; a feed of plain-language insights. |
| Export | Settings → Export saves every drink or the daily totals as CSV (RFC 4180, millilitres, spreadsheet-safe), or all data as JSON. |
| Restore | Settings → Backup and restore opens a JSON export or a downloaded backup (`.json`, `.json.gz`, or passphrase-encrypted `.enc`). **Replace** swaps all data and settings for the backup; **Merge** adds what is missing and keeps the newer copy of anything edited on both sides, keeping this phone's settings. A safety copy is saved before every restore (the newest three are kept), and **Undo last restore** puts it back. Health Connect switches are turned off after a replace, because permissions belong to each device. |

| Wear OS | The `:wear` app adds a **tile** (progress ring against today's goal, with quick-add chips for your first two containers) and a **complication** (ranged value or short text; a tap logs your default container). The phone stays the source of truth: the watch sends each drink with its own id and time over the Wearable Data Layer, queues it when the phone is out of reach, and the phone stores it once. The phone publishes today's total, goal, units and containers back, so the tile and complication work offline. Install the phone and watch builds from the same signing key; the watch app needs the phone app. |

Not yet built: Google Drive backups (Phase 4), Health Connect sync and weather (Phase 5), and the home-screen widget (Phase 6).

## Building

Requirements: the Android SDK (set `sdk.dir` in `local.properties` or `ANDROID_HOME`) and any JDK 17 or newer to start Gradle. The Gradle daemon itself runs on Temurin 25, which Gradle downloads on first use (`gradle/gradle-daemon-jvm.properties`).

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug      # with a device or emulator connected
./gradlew :wear:installDebug     # with a Wear OS watch or emulator paired to that phone
```

## Testing

[`docs/TESTING.md`](docs/TESTING.md) covers every test layer. The gate that must pass before a pull request:

```bash
./gradlew ktlintCheck detekt checkModuleGraph lint \
  testDebugUnitTest :core:common:test :core:model:test :core:domain:test :core:backup:test :core:wear:test \
  verifyRoborazziDebug assembleDebug
```

End-to-end flows run on an emulator with `./gradlew :app:connectedDebugAndroidTest` or on a Gradle Managed Device with `./gradlew pixel6Api34DebugAndroidTest`.

## Project layout

`app` (shell and navigation), `wear` (the watch app), `feature/*` (one module per screen group), `core/*` (model, domain rules, database, settings, data layer, backup format, the watch protocol, reminders, design system), `sync/*` (the watch link, and Health Connect and Drive in later phases) and `widget`. Allowed dependencies between modules are enforced by `./gradlew checkModuleGraph`.
