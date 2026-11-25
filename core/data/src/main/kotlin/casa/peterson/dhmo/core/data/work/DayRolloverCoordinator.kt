package casa.peterson.dhmo.core.data.work

import android.util.Log
import casa.peterson.dhmo.core.common.suspendRunCatching
import casa.peterson.dhmo.core.data.repository.WidgetUpdater
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.BackfillDailyGoalsUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Called at startup and at a hydration-day rollover. Also repaints the widgets, which would otherwise show yesterday's total. */
class DayRolloverCoordinator @Inject constructor(
    private val backfill: BackfillDailyGoalsUseCase,
    private val snapshot: SnapshotDailyGoalUseCase,
    private val ticker: DayTicker,
    private val widgets: WidgetUpdater,
) {
    suspend fun refresh() {
        backfill()
        snapshot(ticker.today().first())
        suspendRunCatching { widgets.updateAll() }.onFailure { Log.w(TAG, "Widget refresh failed", it) }
    }

    private companion object {
        const val TAG = "DayRollover"
    }
}
