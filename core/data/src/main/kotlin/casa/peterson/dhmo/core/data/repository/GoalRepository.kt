package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.DayOverride
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeGoal(day: LocalDate): Flow<DailyGoal?>

    fun observeGoals(from: LocalDate, to: LocalDate): Flow<List<DailyGoal>>

    suspend fun goals(from: LocalDate, to: LocalDate): List<DailyGoal>

    suspend fun goal(day: LocalDate): DailyGoal?

    suspend fun saveSnapshot(goal: DailyGoal)

    fun observeDayOverride(day: LocalDate): Flow<DayOverride?>

    suspend fun setDayOverride(day: LocalDate, targetMl: Int, reason: String?)

    suspend fun clearDayOverride(day: LocalDate)
}
