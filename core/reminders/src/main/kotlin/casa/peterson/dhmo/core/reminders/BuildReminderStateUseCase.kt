package casa.peterson.dhmo.core.reminders

import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.domain.pace.PaceCurve
import casa.peterson.dhmo.core.domain.pace.ReminderConfig
import casa.peterson.dhmo.core.domain.pace.ReminderState
import casa.peterson.dhmo.core.domain.pace.wakeWindow
import casa.peterson.dhmo.core.domain.time.hydrationDay
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

/**
 * Assembles the [ReminderState] for the hydration day of `now` (§8.6): settings, the reminder runtime, the day's goal
 * snapshot and entries, and (when opted in) calendar busy intervals inside the wake window.
 *
 * Returns null when the day has no goal snapshot with a positive target; callers snapshot the goal first.
 */
class BuildReminderStateUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val deviceState: DeviceStateRepository,
    private val goals: GoalRepository,
    private val intake: IntakeRepository,
    private val calendar: CalendarBusyReader,
    private val zoneProvider: ZoneProvider,
) {
    suspend operator fun invoke(now: Instant): ReminderState? {
        val current = settings.current()
        val runtime = deviceState.current().reminderRuntime
        val zone = zoneProvider.current()
        val dayStart = minutesToTime(current.dayStartMinutes)
        val day = hydrationDay(now, zone.rules.getOffset(now), dayStart)
        val goalMl = goals.goal(day)?.targetMl?.takeIf { it > 0 } ?: return null
        val entries = intake.entries(day, day).filter { it.deletedAt == null }
        val config = current.reminders.toConfig()
        val busy = if (current.reminders.calendarBusyEnabled) {
            val window = wakeWindow(day, config.wake, config.sleep, dayStart, zone)
            calendar.busy(window.wake, window.sleep)
        } else {
            emptyList()
        }
        return ReminderState(
            config = config,
            zone = zone,
            dayStart = dayStart,
            day = day,
            goalMl = goalMl,
            consumedMl = entries.sumOf { it.volumeMl },
            lastDrinkAt = entries.maxOfOrNull { it.instant },
            lastReminderAt = runtime.lastReminderAtMs?.let(Instant::ofEpochMilli),
            // An expired snooze has no effect on the decision, so it is simply dropped.
            snoozedUntil = runtime.snoozedUntilMs?.let(Instant::ofEpochMilli)?.takeIf { it.isAfter(now) },
            busy = busy,
            escalationSentToday = runtime.escalationSentDay == day.toEpochDay(),
            summarySentToday = runtime.summarySentDay == day.toEpochDay(),
        )
    }
}

internal fun ReminderSettings.toConfig() = ReminderConfig(
    enabled = enabled,
    wake = minutesToTime(wakeMinutes),
    sleep = minutesToTime(sleepMinutes),
    intervalFloor = Duration.ofMinutes(intervalFloorMinutes.toLong()),
    tolerancePercent = tolerancePercent,
    curve = PaceCurve.from(curve),
    escalationEnabled = escalationEnabled,
    endOfDaySummaryEnabled = endOfDaySummaryEnabled,
)

internal fun minutesToTime(minutes: Int): LocalTime = LocalTime.ofSecondOfDay(minutes.coerceIn(0, LAST_MINUTE) * SECONDS_PER_MINUTE)

private const val LAST_MINUTE = 1_439
private const val SECONDS_PER_MINUTE = 60L
