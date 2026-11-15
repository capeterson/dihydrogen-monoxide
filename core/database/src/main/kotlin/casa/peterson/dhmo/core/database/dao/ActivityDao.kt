package casa.peterson.dhmo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import casa.peterson.dhmo.core.database.entity.ActivityAdjustmentEntity
import casa.peterson.dhmo.core.database.entity.DateMinutes
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Upsert suspend fun upsert(adjustment: ActivityAdjustmentEntity)

    @Upsert suspend fun upsertAll(adjustments: List<ActivityAdjustmentEntity>)

    @Query("DELETE FROM activity_adjustment WHERE source = :source AND source_record_id = :sourceRecordId")
    suspend fun delete(source: String, sourceRecordId: String)

    @Query("SELECT * FROM activity_adjustment WHERE date = :day ORDER BY source_record_id ASC")
    fun observeDay(day: LocalDate): Flow<List<ActivityAdjustmentEntity>>

    @Query("SELECT * FROM activity_adjustment WHERE date = :day ORDER BY source_record_id ASC")
    suspend fun getDay(day: LocalDate): List<ActivityAdjustmentEntity>

    @Query("SELECT date, SUM(minutes) AS minutes FROM activity_adjustment WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date ASC")
    suspend fun minutesPerDay(from: LocalDate, to: LocalDate): List<DateMinutes> // data class DateMinutes(date: LocalDate, minutes: Int)

    @Query("SELECT * FROM activity_adjustment")
    suspend fun getAll(): List<ActivityAdjustmentEntity>

    @Query("DELETE FROM activity_adjustment")
    suspend fun deleteAll()
}
