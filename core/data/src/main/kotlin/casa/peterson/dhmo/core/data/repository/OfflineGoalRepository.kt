package casa.peterson.dhmo.core.data.repository

import android.util.Log
import casa.peterson.dhmo.core.common.suspendRunCatching
import casa.peterson.dhmo.core.data.mapper.toEntity
import casa.peterson.dhmo.core.data.mapper.toModel
import casa.peterson.dhmo.core.database.dao.GoalDao
import casa.peterson.dhmo.core.database.entity.DayOverrideEntity
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.DayOverride
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [GoalRepository]. Day overrides are tombstoned when cleared so the removal survives a merge. */
class OfflineGoalRepository
@Inject
constructor(private val dao: GoalDao, private val clock: Clock, private val widgets: WidgetUpdater) :
    GoalRepository {
    override fun observeGoal(day: LocalDate): Flow<DailyGoal?> = dao.observeGoal(day).map { it?.toModel() }

    override fun observeGoals(from: LocalDate, to: LocalDate): Flow<List<DailyGoal>> =
        dao.observeGoals(from, to).map { rows -> rows.map { it.toModel() } }

    override suspend fun goals(from: LocalDate, to: LocalDate): List<DailyGoal> = dao.getGoals(from, to).map { it.toModel() }

    override suspend fun goal(day: LocalDate): DailyGoal? = dao.getGoal(day)?.toModel()

    override suspend fun saveSnapshot(goal: DailyGoal) {
        dao.upsertGoal(goal.toEntity())
        suspendRunCatching { widgets.updateAll() }.onFailure { Log.w(TAG, "Widget refresh failed", it) }
    }

    override fun observeDayOverride(day: LocalDate): Flow<DayOverride?> = dao.observeOverride(day).map { it?.toModel() }

    override suspend fun setDayOverride(day: LocalDate, targetMl: Int, reason: String?) {
        require(targetMl in MIN_TARGET_ML..MAX_TARGET_ML) { "Day override must be $MIN_TARGET_ML..$MAX_TARGET_ML ml" }
        dao.upsertOverride(DayOverrideEntity(date = day, targetMl = targetMl, reason = reason, updatedAt = clock.instant()))
    }

    override suspend fun clearDayOverride(day: LocalDate) {
        dao.softDeleteOverride(day, clock.instant())
    }

    private companion object {
        const val TAG = "GoalRepository"
        const val MIN_TARGET_ML = 250
        const val MAX_TARGET_ML = 10_000
    }
}
