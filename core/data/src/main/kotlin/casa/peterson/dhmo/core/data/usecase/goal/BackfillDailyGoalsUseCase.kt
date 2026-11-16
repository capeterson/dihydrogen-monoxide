package casa.peterson.dhmo.core.data.usecase.goal

import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Fill missing days since the most recent snapshot, without changing past snapshots. */
class BackfillDailyGoalsUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val snapshot: SnapshotDailyGoalUseCase,
    private val ticker: DayTicker,
) {
    suspend operator fun invoke() {
        val today = ticker.today().first()
        val latest = goals.goals(LocalDate.MIN, today).maxOfOrNull { it.date }
        var day = latest?.plusDays(1) ?: today
        while (day <= today) {
            snapshot(day, notifyReminders = day == today)
            day = day.plusDays(1)
        }
    }
}
