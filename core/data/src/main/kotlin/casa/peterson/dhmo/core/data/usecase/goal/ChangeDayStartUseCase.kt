package casa.peterson.dhmo.core.data.usecase.goal

import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.domain.time.hydrationDay
import casa.peterson.dhmo.core.model.RescheduleReason
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Rebucket the transition day and re-snapshot the days whose boundary changed. */
class ChangeDayStartUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val intake: IntakeRepository,
    private val snapshot: SnapshotDailyGoalUseCase,
    private val reminders: ReminderScheduler,
    private val clock: Clock,
    private val zone: ZoneProvider,
) {
    suspend operator fun invoke(newMinutes: Int) {
        require(newMinutes in 0 until MINUTES_PER_DAY)
        val oldMinutes = settings.current().dayStartMinutes
        if (oldMinutes == newMinutes) return
        val now = clock.instant()
        val offset = zone.current().rules.getOffset(now)
        val oldToday = hydrationDay(now, offset, time(oldMinutes))
        val newToday = hydrationDay(now, offset, time(newMinutes))
        val before = intake.entries(oldToday, LocalDate.MAX)
        settings.update { it.copy(dayStartMinutes = newMinutes) }
        intake.rebucketFrom(oldToday, time(newMinutes))
        val after = intake.entries(minOf(oldToday, newToday), LocalDate.MAX).associateBy { it.id }
        val affected = sortedSetOf(oldToday, newToday)
        before.forEach { entry ->
            val newDay = after[entry.id]?.hydrationDay
            if (newDay != null && newDay != entry.hydrationDay) {
                affected.add(entry.hydrationDay)
                affected.add(newDay)
            }
        }
        affected.forEach { snapshot(it, notifyReminders = false) }
        reminders.reschedule(RescheduleReason.SETTINGS_CHANGED)
    }

    private fun time(minutes: Int): LocalTime = LocalTime.ofSecondOfDay(minutes * SECONDS_PER_MINUTE)

    private companion object {
        const val MINUTES_PER_DAY = 1_440
        const val SECONDS_PER_MINUTE = 60L
    }
}
