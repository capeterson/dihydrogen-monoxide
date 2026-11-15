package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.DayOverride
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Stub: the Room-backed implementation arrives with T4. */
class OfflineGoalRepository @Inject constructor() : GoalRepository {
    override fun observeGoal(day: LocalDate): Flow<DailyGoal?> = TODO("T4")

    override fun observeGoals(from: LocalDate, to: LocalDate): Flow<List<DailyGoal>> = TODO("T4")

    override suspend fun goals(from: LocalDate, to: LocalDate): List<DailyGoal> = TODO("T4")

    override suspend fun goal(day: LocalDate): DailyGoal? = TODO("T4")

    override suspend fun saveSnapshot(goal: DailyGoal) = TODO("T4")

    override fun observeDayOverride(day: LocalDate): Flow<DayOverride?> = TODO("T4")

    override suspend fun setDayOverride(day: LocalDate, targetMl: Int, reason: String?) = TODO("T4")

    override suspend fun clearDayOverride(day: LocalDate) = TODO("T4")
}
