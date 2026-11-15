package casa.peterson.dhmo.core.data.work

import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.BackfillDailyGoalsUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Called at startup and at a hydration-day rollover. */
class DayRolloverCoordinator @Inject constructor(
    private val backfill: BackfillDailyGoalsUseCase,
    private val snapshot: SnapshotDailyGoalUseCase,
    private val ticker: DayTicker,
) {
    suspend fun refresh() {
        backfill()
        snapshot(ticker.today().first())
    }
}
