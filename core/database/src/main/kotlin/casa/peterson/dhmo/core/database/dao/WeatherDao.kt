package casa.peterson.dhmo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import casa.peterson.dhmo.core.database.entity.WeatherDayEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Upsert suspend fun upsert(day: WeatherDayEntity)

    @Upsert suspend fun upsertAll(days: List<WeatherDayEntity>)

    @Query("SELECT * FROM weather_day WHERE date = :day")
    fun observeDay(day: LocalDate): Flow<WeatherDayEntity?>

    @Query("SELECT * FROM weather_day WHERE date = :day")
    suspend fun getDay(day: LocalDate): WeatherDayEntity?

    @Query("SELECT * FROM weather_day WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<WeatherDayEntity>

    @Query("SELECT * FROM weather_day")
    suspend fun getAll(): List<WeatherDayEntity>

    @Query("DELETE FROM weather_day")
    suspend fun deleteAll()
}
