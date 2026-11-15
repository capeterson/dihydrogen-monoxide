package casa.peterson.dhmo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import casa.peterson.dhmo.core.database.entity.HealthDailyEntity
import java.time.LocalDate

@Dao
interface HealthDailyDao {
    @Upsert suspend fun upsertAll(days: List<HealthDailyEntity>)

    @Query("SELECT * FROM health_daily WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<HealthDailyEntity>

    @Query("SELECT * FROM health_daily")
    suspend fun getAll(): List<HealthDailyEntity>

    @Query("DELETE FROM health_daily")
    suspend fun deleteAll()
}
