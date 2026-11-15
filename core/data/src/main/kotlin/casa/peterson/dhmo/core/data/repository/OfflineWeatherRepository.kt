package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.WeatherDay
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Stub: the Room-backed implementation arrives with T4. */
class OfflineWeatherRepository @Inject constructor() : WeatherRepository {
    override fun observeDay(day: LocalDate): Flow<WeatherDay?> = TODO("T4")

    override suspend fun forDay(day: LocalDate): WeatherDay? = TODO("T4")

    override suspend fun range(from: LocalDate, to: LocalDate): List<WeatherDay> = TODO("T4")

    override suspend fun save(day: WeatherDay) = TODO("T4")
}
