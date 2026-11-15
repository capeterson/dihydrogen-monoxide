@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.pace

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class ReminderKind { NUDGE, ESCALATION, SUMMARY }

enum class AlarmKind { NUDGE, SUMMARY, RECHECK }

/** Mapped 1:1 from `ReminderSettings`. */
data class ReminderConfig(
    val enabled: Boolean,
    val wake: LocalTime,
    val sleep: LocalTime,
    val intervalFloor: Duration,
    val tolerancePercent: Int,
    val curve: PaceCurve,
    val escalationEnabled: Boolean,
    val endOfDaySummaryEnabled: Boolean,
)

data class BusyInterval(val start: Instant, val endExclusive: Instant)

data class ReminderState(
    val config: ReminderConfig,
    val zone: ZoneId,
    val dayStart: LocalTime,
    /** Must equal the hydration day of `now`; [nextReminder] throws otherwise. */
    val day: LocalDate,
    /** Effective goal of [day] (> 0). */
    val goalMl: Int,
    val consumedMl: Int,
    val lastDrinkAt: Instant?,
    /** Last NUDGE or ESCALATION actually posted (any day). */
    val lastReminderAt: Instant?,
    val snoozedUntil: Instant?,
    /** Calendar events marked busy; empty unless the opt-in is on. */
    val busy: List<BusyInterval>,
    val escalationSentToday: Boolean,
    val summarySentToday: Boolean,
)

data class ReminderDecision(
    /** Post this notification now, or null. */
    val fireNow: ReminderKind?,
    /** Null only when reminders are disabled. */
    val nextAlarmAt: Instant?,
    val nextAlarmKind: AlarmKind?,
    val expectedMl: Int,
    val deficitMl: Int,
)

fun nextReminder(state: ReminderState, now: Instant): ReminderDecision = TODO("T2")
