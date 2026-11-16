# Reminders: manual test matrix

This matrix backs the PLAN's Phase 2 exit criterion: reminders keep arriving across Doze, reboot, time changes, app
updates and permission changes. The engine is inexact by design (`setAndAllowWhileIdle`, no exact-alarm permission), so
"on time" means **within about 15 minutes** of the scheduled time. Anything more than 30 minutes late is recorded as a
missed alarm (TECHNICAL_PLAN §8.9).

## Setup

- Debug build on a device or the `dhmo-api34` AVD. Complete onboarding with reminders on.
- Settings → Reminders: wake and sleep around the current time, interval floor 15 min, tolerance 0%. With these
  settings an empty day is "behind" one minute after wake, so a nudge is due almost immediately.
- Read the scheduled alarm with:

  ```bash
  adb shell dumpsys alarm | grep -A3 casa.peterson.dhmo
  ```

  The Reminders screen's "Next reminder" line shows the same time (`DeviceState.reminderRuntime.scheduledAtMs`).
- Each row starts from a fresh "behind" state unless it says otherwise. Record the device, the API level and the
  result.

## Matrix

| # | Scenario | Steps | Expected |
|---|---|---|---|
| M1 | Baseline | Wait for the scheduled time with the screen on. | "Time for some water" arrives on time and shows the deficit, two container actions, Snooze and Custom…. The next alarm is one interval later. |
| M2 | Log from the notification | Tap the first container action. | An entry with source NOTIFICATION appears on Today; the notification disappears; the next alarm moves to at least one interval after the drink. |
| M3 | Snooze | Tap Snooze. | The notification disappears; the next alarm is 30 min (the snooze length) from now. |
| M4 | Custom… | Tap Custom…. | The quick-log dialog opens over the current app; logging there reschedules. |
| M5 | Drink resets the timer | Log a drink in the app before the alarm fires. | `dumpsys alarm` shows the alarm moved later; no notification is posted by logging. |
| M6 | Doze | `adb shell dumpsys deviceidle force-idle`, screen off, wait past the scheduled time; then `adb shell dumpsys deviceidle unforce`. | The reminder arrives during an idle maintenance window or on exit, within about 15 min. If it is more than 30 min late, `missedAlarmTimesMs` gains an entry. |
| M7 | Reboot | Note the scheduled time, `adb reboot`, don't open the app. | After boot, `dumpsys alarm` shows the alarm again (BOOT_COMPLETED → reschedule) and the reminder arrives. |
| M8 | Manual time change | Settings → System → Date & time: turn off automatic time, move the clock forward 2 h. | TIME_SET reschedules: the alarm matches the new wall clock and no stale reminder fires. Restore automatic time afterwards. |
| M9 | Time-zone change | Change the zone manually (for example Berlin → New York). | TIMEZONE_CHANGED reschedules. Wake and sleep follow the new local clock. Past days' totals do not change, and only "today" moves. |
| M10 | DST spring forward | Emulator: zone Europe/Berlin, date 2026-03-29 00:50, wake 01:00, sleep 17:00. Let the clock pass 02:00. | The window is 15 h of real time. Reminders continue across the gap, and the wake time inside the gap shifts forward (§8.1). |
| M11 | DST fall back | Same on 2026-10-25 across 03:00 → 02:00. | The window is 17 h. No duplicate reminder in the repeated hour. |
| M12 | App update | With an alarm pending, `adb install -r` a newer build of the same variant. | MY_PACKAGE_REPLACED reschedules; `dumpsys alarm` shows the alarm without opening the app. |
| M13 | Notification permission revoked | System settings → Apps → Dihydrogen Monoxide → Notifications: off (API 33+: revoke the permission). Then log a drink. | The next reschedule cancels the alarm (`dumpsys alarm` has no entry). The Reminders screen shows "Notifications are off". Turning notifications back on and opening Reminders schedules again. |
| M14 | Reminders disabled | Turn the Reminders switch off. | The alarm is cancelled; nothing arrives. |
| M15 | Goal met | Log the full goal. | No further nudges today; the next alarm is tomorrow's wake time (RECHECK). |
| M16 | End-of-day summary | Turn the summary on; wait until 30 min before sleep. | The summary appears on the low-importance "Daily summary" channel; nudges stop at that point. |
| M17 | Calendar busy | Turn on "Avoid calendar events", grant calendar access, create a busy event covering the next due time. | The alarm moves to the end of the event. Revoking calendar access makes the engine ignore the calendar. |
| M18 | Do Not Disturb | Turn DND on while a reminder is due. | The reminder is silenced according to the channel; the app never bypasses DND. |

## Checks not covered here

The decision rules (rows R01–R25 of §8.5) are unit-tested in `:core:domain`. Alarm placement, notification content,
actions, missed-alarm bookkeeping and the manifest are Robolectric-tested in `:core:reminders`. This matrix covers only
what needs a real device: Doze, broadcasts, the clock and the system's permission state.
