package casa.peterson.dhmo.core.domain.pace

import casa.peterson.dhmo.core.domain.time.hydrationDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

private const val PERCENT = 100
private const val SUMMARY_LEAD_MINUTES = 30L
private const val ESCALATION_LEAD_MINUTES = 120L
private const val ESCALATION_SHARE = 0.40

/** The quantities of §8.4 derived from a state; [candidate] is shared by firing and scheduling. */
private class Schedule(val state: ReminderState, val window: WakeWindow) {
    val summaryAt: Instant = maxOf(window.wake, window.sleep.minus(SUMMARY_LEAD_MINUTES, ChronoUnit.MINUTES))
    val nudgeEnd: Instant = if (state.config.endOfDaySummaryEnabled) summaryAt else window.sleep
    private val tolMl = (state.goalMl * state.config.tolerancePercent + PERCENT / 2) / PERCENT

    /** The earliest instant a nudge may fire, given no further drinks; null for "no nudge today". */
    fun candidate(lastReminderAt: Instant?): Instant? {
        val xStar = if (state.consumedMl >= state.goalMl) {
            null
        } else {
            state.config.curve.lastXAtOrBelow((state.consumedMl + tolMl).toDouble() / state.goalMl)
        }
        return xStar?.let { applyConstraints(curveInstant(it), lastReminderAt) }?.takeIf { it < nudgeEnd }
    }

    private fun curveInstant(xStar: Double): Instant {
        val spanMs = Duration.between(window.wake, window.sleep).toMillis()
        return window.wake.plusMillis(Math.round(xStar * spanMs)).truncatedTo(ChronoUnit.MINUTES)
            .plus(1, ChronoUnit.MINUTES)
    }

    private fun applyConstraints(start: Instant, lastReminderAt: Instant?): Instant {
        val floor = state.config.intervalFloor
        var c = start
        state.lastDrinkAt?.let { c = maxOf(c, it.plus(floor)) }
        lastReminderAt?.let { c = maxOf(c, it.plus(floor)) }
        state.snoozedUntil?.let { c = maxOf(c, it) }
        var busy = state.busy.firstOrNull { it.start <= c && c < it.endExclusive }
        while (busy != null) {
            c = busy.endExclusive
            busy = state.busy.firstOrNull { it.start <= c && c < it.endExclusive }
        }
        return c
    }

    fun isEscalation(now: Instant): Boolean {
        val behindMl = state.goalMl * expectedFraction(now, window, state.config.curve) - state.consumedMl
        return state.config.escalationEnabled && !state.escalationSentToday &&
            now >= window.sleep.minus(ESCALATION_LEAD_MINUTES, ChronoUnit.MINUTES) &&
            behindMl > ESCALATION_SHARE * state.goalMl
    }
}

/** What to post now and the lastReminderAt to schedule from afterwards (§8.4 step 3). */
private fun Schedule.fireNow(now: Instant): Pair<ReminderKind?, Instant?> {
    val config = state.config
    val summaryDue = config.endOfDaySummaryEnabled && !state.summarySentToday &&
        summaryAt <= now && now < window.sleep
    val nudgeAt = if (summaryDue || now >= nudgeEnd) null else candidate(state.lastReminderAt)
    val nudgeDue = nudgeAt != null && now >= nudgeAt
    val kind = when {
        summaryDue -> ReminderKind.SUMMARY
        nudgeDue && isEscalation(now) -> ReminderKind.ESCALATION
        nudgeDue -> ReminderKind.NUDGE
        else -> null
    }
    return kind to (if (kind == ReminderKind.NUDGE || kind == ReminderKind.ESCALATION) now else state.lastReminderAt)
}

/** The next alarm, strictly after [now], given what [fireNow] just did (§8.4 step 4). */
private fun Schedule.nextAlarm(now: Instant, fired: ReminderKind?, lastReminder: Instant?): Pair<Instant, AlarmKind> {
    val config = state.config
    val summaryPending = config.endOfDaySummaryEnabled && !state.summarySentToday &&
        fired != ReminderKind.SUMMARY && now < window.sleep
    val nextNudge = if (now < nudgeEnd) candidate(lastReminder) else null
    val nudgeAlarm = nextNudge?.let { if (it > now) it else now.plus(1, ChronoUnit.MINUTES) }
    val summaryAlarm = if (summaryPending) summaryAt else null
    return when {
        nudgeAlarm != null && (summaryAlarm == null || nudgeAlarm <= summaryAlarm) -> nudgeAlarm to AlarmKind.NUDGE

        summaryAlarm != null -> summaryAlarm to AlarmKind.SUMMARY

        else -> {
            val nextDay = state.day.plusDays(1)
            wakeWindow(nextDay, config.wake, config.sleep, state.dayStart, state.zone).wake to AlarmKind.RECHECK
        }
    }
}

fun nextReminder(state: ReminderState, now: Instant): ReminderDecision {
    val config = state.config
    if (!config.enabled) return ReminderDecision(null, null, null, 0, 0)
    val today = hydrationDay(now, state.zone.rules.getOffset(now), state.dayStart)
    require(today == state.day) { "state.day ${state.day} is not the hydration day $today of $now" }

    val window = wakeWindow(state.day, config.wake, config.sleep, state.dayStart, state.zone)
    val schedule = Schedule(state, window)
    val expected = expectedMl(state.goalMl, now, window, config.curve)
    val (fire, lastReminder) = schedule.fireNow(now)
    val (at, kind) = schedule.nextAlarm(now, fire, lastReminder)
    return ReminderDecision(fire, at, kind, expected, expected - state.consumedMl)
}
