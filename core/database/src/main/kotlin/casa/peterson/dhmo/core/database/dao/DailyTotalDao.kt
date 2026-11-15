package casa.peterson.dhmo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import casa.peterson.dhmo.core.database.entity.DailyTotalView
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTotalDao {
    @Query("SELECT * FROM daily_total WHERE date = :day")
    fun observeDay(day: LocalDate): Flow<DailyTotalView?>

    @Query("SELECT * FROM daily_total WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DailyTotalView>>

    @Query("SELECT * FROM daily_total WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<DailyTotalView>

    @Query("SELECT MIN(date) FROM daily_total")
    suspend fun firstDay(): LocalDate?
}
