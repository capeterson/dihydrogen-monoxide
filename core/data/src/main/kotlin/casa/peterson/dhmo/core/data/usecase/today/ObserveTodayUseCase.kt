package casa.peterson.dhmo.core.data.usecase.today

import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.domain.pace.Pace
import casa.peterson.dhmo.core.domain.pace.PaceCurve
import casa.peterson.dhmo.core.domain.pace.pace
import casa.peterson.dhmo.core.domain.pace.wakeWindow
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.settings.AppSettings
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

data class TodaySnapshot(val day: LocalDate, val entries: List<IntakeEntry>, val totalMl: Int, val goal: DailyGoal?, val pace: Pace?)

class ObserveTodayUseCase @Inject constructor(
    private val ticker: DayTicker,
    private val intake: IntakeRepository,
    private val goals: GoalRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zone: ZoneProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<TodaySnapshot> = ticker.today().flatMapLatest { day ->
        combine(intake.observeDay(day), intake.observeDailyTotal(day), goals.observeGoal(day), settings.settings) {
                entries,
                total,
                goal,
                current,
            ->
            val consumed = total?.totalMl ?: 0
            TodaySnapshot(day, entries, consumed, goal, currentPace(day, goal, consumed, current))
        }
    }

    private fun currentPace(day: LocalDate, goal: DailyGoal?, consumed: Int, settings: AppSettings): Pace? {
        if (goal == null) return null
        return try {
            val reminder = settings.reminders
            val window = wakeWindow(
                day,
                time(reminder.wakeMinutes),
                time(reminder.sleepMinutes),
                time(settings.dayStartMinutes),
                zone.current(),
            )
            val now = clock.instant()
            if (now < window.wake || now >= window.sleep) {
                null
            } else {
                pace(goal.targetMl, consumed, reminder.tolerancePercent, now, window, PaceCurve.from(reminder.curve))
            }
        } catch (_: NotImplementedError) {
            null
        }
    }

    private fun time(minutes: Int) = LocalTime.ofSecondOfDay(minutes * SECONDS_PER_MINUTE)

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
