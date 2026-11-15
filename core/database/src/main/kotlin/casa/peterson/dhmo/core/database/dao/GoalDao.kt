package casa.peterson.dhmo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import casa.peterson.dhmo.core.database.entity.DailyGoalEntity
import casa.peterson.dhmo.core.database.entity.DayOverrideEntity
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Upsert suspend fun upsertGoal(goal: DailyGoalEntity)

    @Upsert suspend fun upsertGoals(goals: List<DailyGoalEntity>)

    @Query("SELECT * FROM daily_goal WHERE date = :day")
    fun observeGoal(day: LocalDate): Flow<DailyGoalEntity?>

    @Query("SELECT * FROM daily_goal WHERE date = :day")
    suspend fun getGoal(day: LocalDate): DailyGoalEntity?

    @Query("SELECT * FROM daily_goal WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeGoals(from: LocalDate, to: LocalDate): Flow<List<DailyGoalEntity>>

    @Query("SELECT * FROM daily_goal WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getGoals(from: LocalDate, to: LocalDate): List<DailyGoalEntity>

    @Query("SELECT * FROM daily_goal")
    suspend fun getAllGoals(): List<DailyGoalEntity>

    @Query("DELETE FROM daily_goal")
    suspend fun deleteAllGoals()

    @Upsert suspend fun upsertOverride(override: DayOverrideEntity)

    @Upsert suspend fun upsertOverrides(overrides: List<DayOverrideEntity>)

    @Query("SELECT * FROM day_override WHERE date = :day AND deleted_at IS NULL")
    fun observeOverride(day: LocalDate): Flow<DayOverrideEntity?>

    @Query("SELECT * FROM day_override WHERE date = :day AND deleted_at IS NULL")
    suspend fun getOverride(day: LocalDate): DayOverrideEntity?

    @Query("UPDATE day_override SET deleted_at = :now, updated_at = :now WHERE date = :day")
    suspend fun softDeleteOverride(day: LocalDate, now: Instant)

    @Query("SELECT * FROM day_override")
    suspend fun getAllOverridesIncludingDeleted(): List<DayOverrideEntity>

    @Query("DELETE FROM day_override")
    suspend fun deleteAllOverrides()
}
