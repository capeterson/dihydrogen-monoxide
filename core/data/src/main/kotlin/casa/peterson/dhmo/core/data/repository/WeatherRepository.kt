package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.WeatherDay
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    fun observeDay(day: LocalDate): Flow<WeatherDay?>

    suspend fun forDay(day: LocalDate): WeatherDay?

    suspend fun range(from: LocalDate, to: LocalDate): List<WeatherDay>

    suspend fun save(day: WeatherDay)
}
